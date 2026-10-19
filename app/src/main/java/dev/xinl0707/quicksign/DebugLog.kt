package dev.xinl0707.quicksign

import java.util.ArrayDeque

/**
 * 跨界面共享的调试日志环形缓冲（主界面写入，设置页读取显示）。
 * 同时保存"页面抓取"详细记录（URL + 正文全文），供导出作为覆盖界面设计依据。
 */
object DebugLog {
    private val lines = ArrayDeque<String>()
    private val captures = StringBuilder()
    private const val CAPTURE_MAX = 20000

    @Synchronized
    fun append(line: String) {
        lines.addLast(line)
        while (lines.size > 300) lines.removeFirst()
    }

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
