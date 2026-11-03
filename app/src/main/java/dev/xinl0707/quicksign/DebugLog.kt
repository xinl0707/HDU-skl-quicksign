package dev.xinl0707.quicksign

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * 跨界面共享的调试日志环形缓冲（主界面写入，设置页读取显示）。
 * 同时保存"页面抓取"详细记录（URL + 正文全文），供导出作为覆盖界面设计依据。
 *
 * 日志格式（对齐传统 APP / logcat 习惯）：
 *   HH:mm:ss [等级][分类] 内容
 * 等级：I=信息  W=警告  E=错误  D=调试
 * 分类：界面 / 登录 / 监听 / 识别 / 提交 / 更新 / 设置 / 系统，用于快速过滤定位
 *
 * 本轮（v3.2.12）升级：
 * - 每条日志保存完整毫秒时间戳，导出支持按「几月几日几点几分 App 打开」筛选日志范围；
 * - 每次 App 打开由 MainActivity 调 markSessionStart() 登记会话起点，
 *   签到详情页可导出「本次打开到签到完全成功」的日志；
 * - 缓冲扩容至 1000 条（覆盖整场会话），不再只留最近 300 条。
 */
object DebugLog {
    private data class Entry(val ts: Long, val text: String)
    private val entries = ArrayDeque<Entry>()
    private val sessionStarts = ArrayDeque<Long>()
    private val captures = StringBuilder()
    private const val CAPTURE_MAX = 20000
    private const val MAX_LINES = 1000
    private val ts = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    private val fullTs = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

    /** 登记一次 App 打开时刻（MainActivity onCreate 调用）；导出时按此筛选「打开后的所有日志」 */
    @Synchronized
    fun markSessionStart() {
        val now = System.currentTimeMillis()
        // 同一时刻重复启动（如设置页"开屏动画测试"连续触发）只登记一次
        if (sessionStarts.isNotEmpty() && now - sessionStarts.last() < 1500) {
            sessionStarts.removeLast()
        }
        sessionStarts.addLast(now)
        while (sessionStarts.size > 6) sessionStarts.removeFirst()
    }

    /** 最近几次 App 打开时刻（升序，含本次；最多保留 6 次） */
    @Synchronized
    fun sessionStarts(): List<Long> = sessionStarts.toList()

    /** 最近一次 App 打开时刻（尚无登记返回 0） */
    @Synchronized
    fun currentSessionStart(): Long = sessionStarts.lastOrNull() ?: 0L

    /** 打开时刻的人读格式（导出/选择界面用） */
    fun formatSession(ts: Long): String = if (ts > 0) fullTs.format(Date(ts)) else "—"

    /** 追加一条带时间戳的日志；level 取 I/W/E/D，tag 为分类名 */
    @Synchronized
    fun append(line: String, level: Char = 'I', tag: String = "系统") {
        val l = level.uppercaseChar().takeIf { it in "IWED" } ?: 'I'
        val now = System.currentTimeMillis()
        entries.addLast(Entry(now, "${ts.format(Date(now))} [$l][$tag] $line"))
        while (entries.size > MAX_LINES) entries.removeFirst()
    }

    /** 信息（默认分类：系统） */
    @Synchronized
    fun i(msg: String) = append(msg, 'I')

    /** 信息 + 分类 */
    @Synchronized
    fun info(tag: String, msg: String) = append(msg, 'I', tag)

    /** 警告 + 分类 */
    @Synchronized
    fun warn(tag: String, msg: String) = append(msg, 'W', tag)

    /** 错误 + 分类 */
    @Synchronized
    fun error(tag: String, msg: String) = append(msg, 'E', tag)

    /** 追加一条完整页面抓取记录（含分隔头），导出时一并输出 */
    @Synchronized
    fun addCapture(record: String) {
        captures.append(record).append("\n\n")
        while (captures.length > CAPTURE_MAX) {
            val idx = captures.indexOf("\n\n", 0)
            if (idx < 0) break
            captures.delete(0, idx + 2)
        }
    }

    /** 全部日志（显示用，格式 HH:mm:ss [I][tag] 行） */
    @Synchronized
    fun dump(): String {
        val sb = StringBuilder()
        for (e in entries) sb.append(e.text).append('\n')
        return sb.toString()
    }

    /** [fromTs, toTs] 时间范围内的日志（导出用；toTs 默认到最新） */
    @Synchronized
    fun dumpFrom(fromTs: Long, toTs: Long = Long.MAX_VALUE): String {
        val sb = StringBuilder()
        for (e in entries) {
            if (e.ts >= fromTs && e.ts <= toTs) sb.append(e.text).append('\n')
        }
        return sb.toString()
    }

    /** 全部页面抓取记录（详细，导出用） */
    @Synchronized
    fun capturesText(): String = captures.toString()

    @Synchronized
    fun clear() {
        entries.clear()
        captures.setLength(0)
        sessionStarts.clear()
    }
}
