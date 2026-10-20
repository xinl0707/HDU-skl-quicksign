package dev.xinl0707.quicksign

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 小米 MIMO 云端语音识别引擎（OpenAI 兼容接口，零第三方依赖）——持续切片版。
 *
 * 架构（v1.12.6 定版，用户确认改回两步方案）：
 * - 转写层：mimo-v2.5-asr 纯语音转写。每 3 秒切最近 10 秒（重叠 7 秒，降低断句概率），
 *   所有切片一律上传（含静音），转写文本流式回传（onResult）。
 * - 综合层：mimo-v2.6-flash（多模态对话模型）。App 端每收到一片转写后，把累积的
 *   转写文本交给 flash 综合判断 4 位签到码。提示词明确告知切片机制（10s/3s、重叠 7 秒、
 *   数字可能被拆段、内容有重复、同音字干扰），并区分"首次判断"与"后续累积判断"。
 *   以 flash 输出为准（App 端不做激进同音字映射——那曾导致安静环境下误输出数字）。
 *
 * 设计（应对课堂 15 秒签到窗口）：
 * - 录音线程持续写入 10 秒环形缓冲区（PCM 16k 单声道 16bit），全程不中断；
 * - 切片线程每 3 秒截取最近 10 秒 → 所有切片一律上传（含静音，避免轻声漏识别）；
 * - 上传走 2 线程池并行，上传期间录音照常继续，切片不因网络而停顿；
 * - 停止后回调一律作废（stopped 检查），杜绝残留识别结果混入新一轮监听。
 */
class MiMoSpeechEngine(
    private val apiKey: String,
    private val baseUrl: String = "https://api.xiaomimimo.com/v1",
    private val asrModel: String = "mimo-v2.5-asr",
    private val textModel: String = "mimo-v2.6-flash"
) {
    interface Callback {
        fun onState(text: String)
        /** 每个切片识别出的文本（可能为空串，表示该段无有效语音内容） */
        fun onResult(text: String)
        fun onError(message: String)
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val BYTES_PER_SEC = SAMPLE_RATE * 2
        private const val RING_SECONDS = 10
        private const val SLICE_MS = 3000L    // 每 3 秒切一片
        private const val FIRST_SLICE_MS = 6000L  // 首个切片等 6 秒（多攒音频，提高首片捕获率）
        private const val WINDOW_MS = 10000   // 每片取最近 10 秒（重叠 7 秒，降低断句概率）
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ring = ByteArray(RING_SECONDS * BYTES_PER_SEC)
    private var writePos = 0
    private var writtenTotal = 0L
    private val lock = Object()

    @Volatile
    private var stopped = false
    private var recThread: Thread? = null
    private var sliceThread: Thread? = null
    private val uploadPool = Executors.newFixedThreadPool(2)

    fun start(cb: Callback) {
        if (recThread?.isAlive == true) return
        stopped = false
        // 重置环形缓冲水位：杜绝上一轮监听的残留音频混入新一轮切片（语音切片残留问题）
        synchronized(lock) {
            writePos = 0
            writtenTotal = 0
        }
        startRecThread(cb)
        startSliceThread(cb)
    }

    /** 手动停止（识别到码 / 用户点停止 / 页面切换时） */
    fun stop() {
        stopped = true
        try { recThread?.interrupt() } catch (e: Exception) {}
        try { sliceThread?.interrupt() } catch (e: Exception) {}
        uploadPool.shutdownNow()
    }

    /**
     * flash 综合判断：把累积的切片转写文本整体交给模型提取 4 位签到码。
     * 解决跨切片断码（"2"与"588"分属两片）、同音字干扰（"五百二斤"→5827）——模型理解完整上下文后输出。
     * first=true 使用"首次判断"提示词；之后用"累积判断"提示词（都写清 10s/3s 重叠 7 秒切片机制）。
     * 成功返回 4 位纯数字，否则返回 null（异步回调在主线程）。
     */
    /**
     * flash 综合判断 4 位签到码。回调第一个参数为校验通过的 4 位码（无则 null）；
     * 第二个参数为模型原始输出全文（用于调试日志——即使模型输出"无"或乱码也要能查到）。
     */
    fun extractSignCode(context: String, first: Boolean, cb: (String?, String?) -> Unit) {
        Thread {
            try {
                val sys = if (first) {
                    "你是课堂签到码提取助手。老师在课堂上报 4 位数字签到码，下面是实时语音转写。" +
                            "转写每 3 秒一片、相邻重叠 7 秒，同一句话可能重复，数字可能被切片截断。" +
                            "规则：1. 取老师报出的完整连续 4 位数字；「第X次/测试/记一下/下一次」等口语里的数字不是签到码。" +
                            "2. 老师可能重复报码，若听到「8517851」这类，完整一组是 8517，后面的「851」是第二遍被截断的前缀，取完整的第一组，不要把截断片段当独立码。" +
                            "3. 只听到 2-3 位不完整数字时输出「无」，不要猜。" +
                            "只输出 4 位数字或「无」，不要其他文字。"
                } else {
                    "继续判断签到码。下面是累计转写（每 3 秒新增一段，内容可能重复，数字可能跨段拆分或被截断）。" +
                            "规则同上：取老师报出的完整连续 4 位；重复报码取完整的第一组；不完整输出「无」；口语数字忽略。" +
                            "只输出 4 位数字或「无」。"
                }
                val payload = JSONObject().apply {
                    put("model", textModel)
                    put("thinking", false)
                    put("enable_thinking", false)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", sys)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", context)
                        })
                    })
                }
                val conn = URL("${baseUrl.trimEnd('/')}/chat/completions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                var out: String? = null
                var raw: String? = null   // 模型原始输出全文（调试日志用：模型输出"无"/乱码也要可查）
                try {
                    conn.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))
                    val code = conn.responseCode
                    val body = if (code in 200..299) {
                        conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                    } else {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    }
                    if (code in 200..299) {
                        val json = try { JSONObject(body) } catch (e: Exception) { null }
                        val content = json
                            ?.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content", "")
                            ?.trim()
                        raw = content
                        out = if (content != null && content.length == 4 && content.all { it.isDigit() }) content else null
                    }
                } finally {
                    conn.disconnect()
                }
                val final = out
                mainHandler.post { cb(final, raw) }
            } catch (e: Exception) {
                mainHandler.post { cb(null, null) }
            }
        }.apply { start() }
    }

    // ------------------------------------------------------------------ 录音线程

    private fun startRecThread(cb: Callback) {
        recThread = Thread {
            var rec: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    if (minBuf > 0) minBuf * 2 else 6400
                )
                if (rec!!.state != AudioRecord.STATE_INITIALIZED) {
                    cbSafe(cb) { it.onError("麦克风初始化失败") }
                    return@Thread
                }
                rec!!.startRecording()
                cbSafe(cb) { it.onState("持续监听中…") }
                val shortBuf = ShortArray(320) // 20ms @16k
                while (!stopped) {
                    val n = rec!!.read(shortBuf, 0, shortBuf.size)
                    if (n <= 0) continue
                    synchronized(lock) {
                        for (i in 0 until n) {
                            val v = shortBuf[i].toInt()
                            ring[writePos] = (v and 0xFF).toByte()
                            ring[(writePos + 1) % ring.size] = ((v shr 8) and 0xFF).toByte()
                            writePos = (writePos + 2) % ring.size
                            writtenTotal++
                        }
                    }
                }
            } catch (e: Exception) {
                if (!stopped) cbSafe(cb) { it.onError("录音异常：" + (e.message ?: "未知错误")) }
            } finally {
                try { rec?.stop() } catch (e: Exception) {}
                try { rec?.release() } catch (e: Exception) {}
            }
        }.apply { start() }
    }

    // ------------------------------------------------------------------ 切片线程

    private fun startSliceThread(cb: Callback) {
        sliceThread = Thread {
            var sliceNo = 0
            var firstSlice = true
            while (!stopped) {
                // 首个切片等 6 秒再上传（多攒 6 秒音频，提高首片识别捕获率）；
                // 之后每 3 秒往前切一次，每片取最近 10 秒（重叠 7 秒）
                try { Thread.sleep(if (firstSlice) FIRST_SLICE_MS else SLICE_MS) } catch (e: InterruptedException) { break }
                if (stopped) break
                sliceNo++
                firstSlice = false
                val data = readWindow(WINDOW_MS) ?: continue
                if (data.size < BYTES_PER_SEC / 2) continue        // 少于 0.5 秒不上传
                // 所有切片一律上传（含静音），不做 VAD 跳过——避免轻声/安静环境下漏识别
                upload(data, cb, sliceNo)
            }
        }.apply { start() }
    }

    /** 读最近 windowMs 毫秒的 PCM（环形绕回安全）；有效数据不足时返回 null */
    private fun readWindow(ms: Int): ByteArray? {
        synchronized(lock) {
            // writtenTotal 是采样点计数（每 sample +1），转字节数需 ×2；
            // 修复前把采样点数当字节数与 ring.size（字节数）min，导致每片最多只读 5 秒而非 10 秒
            val have = min(writtenTotal * 2L, ring.size.toLong()).toInt()
            if (have < BYTES_PER_SEC / 2) return null
            val readLen = min(ms * BYTES_PER_SEC / 1000, have)
            val start = (writePos - readLen + ring.size) % ring.size
            val out = ByteArray(readLen)
            if (start + readLen <= ring.size) {
                System.arraycopy(ring, start, out, 0, readLen)
            } else {
                val p1 = ring.size - start
                System.arraycopy(ring, start, out, 0, p1)
                System.arraycopy(ring, 0, out, p1, readLen - p1)
            }
            return out
        }
    }

    /** PCM 能量检测（已不再用于跳过静音切片，保留备用） */
    @Suppress("unused")
    private fun hasVoice(data: ByteArray): Boolean {
        var sum = 0.0
        var count = 0
        var i = 0
        while (i + 1 < data.size) {
            val lo = data[i].toInt() and 0xFF
            val hi = data[i + 1].toInt()
            val v = ((hi shl 8) or lo).toShort().toDouble()
            sum += v * v
            count++
            i += 2
        }
        return count > 0 && sqrt(sum / count) > 500.0
    }

    // ------------------------------------------------------------------ 上传与识别

    private fun upload(pcm: ByteArray, cb: Callback, sliceNo: Int) {
        uploadPool.execute {
            if (stopped) return@execute
            try {
                val wav = pcmToWav(pcm, SAMPLE_RATE)
                val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
                val text = recognize(b64, cb)
                if (stopped) return@execute
                cbSafe(cb) { it.onResult(text ?: "") }
            } catch (e: Exception) {
                if (!stopped) cbSafe(cb) { it.onError("识别失败：" + (e.message ?: "未知错误")) }
            }
        }
    }

    private fun recognize(b64: String, cb: Callback): String? {
        val payload = JSONObject().apply {
            put("model", asrModel)
            // ASR 专用模型（mimo-v2.5-asr）：纯转写，只输出听到的文本
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "input_audio")
                            put("input_audio", JSONObject().apply {
                                put("data", "data:audio/wav;base64,$b64")
                            })
                        })
                    })
                })
            })
            put("asr_options", JSONObject().apply { put("language", "zh") })
        }

        val conn = URL("${baseUrl.trimEnd('/')}/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 8000
        conn.readTimeout = 20000
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        try {
            conn.outputStream.write(payload.toString().toByteArray(Charsets.UTF_8))
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            } else {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            }
            if (code !in 200..299) {
                val detail = body.take(200).replace('\n', ' ')
                cbSafe(cb) {
                    it.onError(
                        "识别服务错误($code)" +
                                (if (code == 401) "：API Key 无效" else if (code == 429) "：请求过于频繁" else "") +
                                (if (detail.isNotBlank()) "：$detail" else "")
                    )
                }
                return null
            }
            val json = try { JSONObject(body) } catch (e: Exception) { null }
            return json
                ?.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }

    /** 回调安全投递：投递前/执行时都校验 stopped，杜绝旧监听已排队的结果在停止后仍回调（语音切片残留） */
    private fun cbSafe(cb: Callback, block: (Callback) -> Unit) {
        mainHandler.post {
            if (stopped) return@post
            block(cb)
        }
    }

    /** PCM 16k 单声道 16bit → WAV 文件字节 */
    private fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val dataSize = pcm.size
        val wav = ByteArray(44 + dataSize)
        fun putStr(off: Int, s: String) {
            for (i in s.indices) wav[off + i] = s[i].code.toByte()
        }
        fun putIntLE(off: Int, v: Int) {
            wav[off] = (v and 0xFF).toByte()
            wav[off + 1] = ((v shr 8) and 0xFF).toByte()
            wav[off + 2] = ((v shr 16) and 0xFF).toByte()
            wav[off + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun putShortLE(off: Int, v: Int) {
            wav[off] = (v and 0xFF).toByte()
            wav[off + 1] = ((v shr 8) and 0xFF).toByte()
        }
        putStr(0, "RIFF")
        putIntLE(4, 36 + dataSize)
        putStr(8, "WAVE")
        putStr(12, "fmt ")
        putIntLE(16, 16)
        putShortLE(20, 1)          // PCM
        putShortLE(22, 1)          // 单声道
        putIntLE(24, sampleRate)
        putIntLE(28, sampleRate * 2)
        putShortLE(32, 2)
        putShortLE(34, 16)
        putStr(36, "data")
        putIntLE(40, dataSize)
        System.arraycopy(pcm, 0, wav, 44, dataSize)
        return wav
    }
}
