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
 */
object DebugLog {
    private val lines = ArrayDeque<String>()
    private val captures = StringBuilder()
    private const val CAPTURE_MAX = 20000
    private val ts = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

    /** 追加一条带时间戳的日志；level 取 I/W/E/D，tag 为分类名 */
    @Synchronized
    fun append(line: String, level: Char = 'I', tag: String = "系统") {
        val l = level.uppercaseChar().takeIf { it in "IWED" } ?: 'I'
        lines.addLast("${ts.format(Date())} [$l][$tag] $line")
        while (lines.size > 300) lines.removeFirst()
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

    @Synchronized
    fun dump(): String {
        val sb = StringBuilder()
        for (l in lines) {
            sb.append(l).append('\n')
        }
        return sb.toString()
    }

    /** 全部页面抓取记录（详细，导出用） */
    @Synchronized
    fun capturesText(): String = captures.toString()

    @Synchronized
    fun clear() {
        lines.clear()
        captures.setLength(0)
    }
}
