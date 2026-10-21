package dev.xinl0707.quicksign

/** 简易 Markdown → Spanned 渲染（设置页免责声明 / 更新日志共用）：
 *  支持 # 标题、**粗体**、`行内代码`、[链接](url)、- 列表、换行；
 *  先转义 HTML 再套标签，避免特殊字符破坏渲染 */
object Markdown {
    fun toSpanned(md: String): android.text.Spanned {
        var s = md
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        s = s.replace(Regex("`([^`]+)`")) { "<code>${it.groupValues[1]}</code>" }
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*")) { "<b>${it.groupValues[1]}</b>" }
        s = s.replace(Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)")) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        s = s.replace(Regex("(?m)^(#{1,6})\\s+(.+)$")) { m ->
            val level = m.groupValues[1].length
            val content = m.groupValues[2]
            when (level) { 1 -> "<h1>$content</h1>"; 2 -> "<h2>$content</h2>"; else -> "<h3>$content</h3>" }
        }
        s = s.replace(Regex("(?m)^\\s*-\\s+(.+)$")) { "• ${it.groupValues[1]}" }
        s = s.replace("\n", "<br>")
        return android.text.Html.fromHtml(s, android.text.Html.FROM_HTML_MODE_COMPACT)
    }
}
