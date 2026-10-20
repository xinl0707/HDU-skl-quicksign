package dev.xinl0707.quicksign

/**
 * 界面风格调色板：5 种风格（墨青 / 朱砂·国潮 / 原木·暖阳 / 石墨·极简 / 极光·青绿）× 浅色 / 深色。
 * 所有界面颜色统一从这里取，设置页可切换（theme_style）。
 * v2.0 视觉重建：Palette 扩展为完整设计 token（强调色浅底 / 键盘文字 / 错误 / 成功 / 分隔线）。
 */
object ThemePalette {

    const val KEY_STYLE = "theme_style"
    const val STYLE_DEFAULT = "default"      // 墨青：现代工具感，青蓝强调
    const val STYLE_GUOCHAO = "guochao"      // 朱砂·国潮：暖米底 + 朱砂红，东方克制
    const val STYLE_WOOD = "wood"            // 原木·暖阳：原木棕 + 暖米，桌面质感
    const val STYLE_GRAPHITE = "graphite"    // 石墨·极简：黑白灰，锐利排版
    const val STYLE_AURORA = "aurora"        // 极光·青绿：深底 + 荧光青绿

    val STYLES = listOf(
        STYLE_DEFAULT, STYLE_GUOCHAO, STYLE_WOOD, STYLE_GRAPHITE, STYLE_AURORA
    )

    fun styleLabel(style: String): String = when (style) {
        STYLE_GUOCHAO -> "朱砂"
        STYLE_WOOD -> "原木"
        STYLE_GRAPHITE -> "石墨"
        STYLE_AURORA -> "极光"
        else -> "墨青"
    }

    data class Palette(
        val bg: Int,            // 页面底色
        val card: Int,          // 卡片底色
        val text: Int,          // 主文字
        val sub: Int,           // 次级文字
        val accent: Int,        // 主强调色（按钮底 / 数字 / 成功徽章）
        val accentSoft: Int,    // 强调色浅底（信息条 / 选中态）
        val keyBg: Int,         // 键盘数字键底色
        val keyText: Int,       // 键盘数字键文字
        val light: Int,         // 弱化填充（辅助按钮 / 统计卡 / 功能键）
        val infoBg: Int,        // 识别信息行底色
        val infoListBg: Int,    // 识别展开列表底色
        val danger: Int,        // 错误 / 无效（弹窗标题、清除账号）
        val success: Int,       // 成功状态
        val hairline: Int,      // 分隔线 / 描边
    )

    fun palette(style: String, night: Boolean): Palette = when (style) {
        STYLE_GUOCHAO -> if (night) GUOCHAO_DARK else GUOCHAO_LIGHT
        STYLE_WOOD -> if (night) WOOD_DARK else WOOD_LIGHT
        STYLE_GRAPHITE -> if (night) GRAPHITE_DARK else GRAPHITE_LIGHT
        STYLE_AURORA -> if (night) AURORA_DARK else AURORA_LIGHT
        else -> if (night) DEFAULT_DARK else DEFAULT_LIGHT
    }

    // ---------------------------------------------------------------- 墨青（默认）：现代工具感，青蓝强调
    private val DEFAULT_LIGHT = Palette(
        bg = 0xFFF2F5F9.toInt(), card = 0xFFFFFFFF.toInt(), text = 0xFF1A1F26.toInt(),
        sub = 0xFF5D6672.toInt(), accent = 0xFF3B71FE.toInt(), accentSoft = 0xFFEAF0FF.toInt(),
        keyBg = 0xFFE8ECF1.toInt(), keyText = 0xFF1A1F26.toInt(), light = 0xFFE7EBF0.toInt(),
        infoBg = 0xFFF0F5FF.toInt(), infoListBg = 0xFFF7F9FF.toInt(),
        danger = 0xFFE53935.toInt(), success = 0xFF2FA45C.toInt(), hairline = 0xFFE2E6EC.toInt(),
    )
    private val DEFAULT_DARK = Palette(
        bg = 0xFF0E1116.toInt(), card = 0xFF161B22.toInt(), text = 0xFFE6E9EF.toInt(),
        sub = 0xFF8B949E.toInt(), accent = 0xFF4C8DFF.toInt(), accentSoft = 0xFF1A2436.toInt(),
        keyBg = 0xFF1D242C.toInt(), keyText = 0xFFE6E9EF.toInt(), light = 0xFF212933.toInt(),
        infoBg = 0xFF1F2430.toInt(), infoListBg = 0xFF1A1D24.toInt(),
        danger = 0xFFF0574F.toInt(), success = 0xFF3CCB79.toInt(), hairline = 0xFF262C36.toInt(),
    )

    // ---------------------------------------------------------------- 朱砂·国潮：暖米底 + 朱砂红，东方克制
    private val GUOCHAO_LIGHT = Palette(
        bg = 0xFFF7F1E7.toInt(), card = 0xFFFFFDF8.toInt(), text = 0xFF2B2018.toInt(),
        sub = 0xFF7A6B57.toInt(), accent = 0xFFB8432C.toInt(), accentSoft = 0xFFF7E8E2.toInt(),
        keyBg = 0xFFEFE6D8.toInt(), keyText = 0xFF2B2018.toInt(), light = 0xFFEAE0D0.toInt(),
        infoBg = 0xFFF6ECE3.toInt(), infoListBg = 0xFFFBF5EC.toInt(),
        danger = 0xFFC0392B.toInt(), success = 0xFF2F7D5C.toInt(), hairline = 0xFFE4DACB.toInt(),
    )
    private val GUOCHAO_DARK = Palette(
        bg = 0xFF15110E.toInt(), card = 0xFF201A15.toInt(), text = 0xFFF2E8DC.toInt(),
        sub = 0xFFA8967F.toInt(), accent = 0xFFE05A3D.toInt(), accentSoft = 0xFF33221C.toInt(),
        keyBg = 0xFF2A211B.toInt(), keyText = 0xFFF2E8DC.toInt(), light = 0xFF2E241D.toInt(),
        infoBg = 0xFF2A2018.toInt(), infoListBg = 0xFF211A14.toInt(),
        danger = 0xFFF05A46.toInt(), success = 0xFF43B083.toInt(), hairline = 0xFF372C22.toInt(),
    )

    // ---------------------------------------------------------------- 原木·暖阳：原木棕 + 暖米，桌面质感
    private val WOOD_LIGHT = Palette(
        bg = 0xFFFBF6EE.toInt(), card = 0xFFFFFFFF.toInt(), text = 0xFF33291D.toInt(),
        sub = 0xFF8A7C68.toInt(), accent = 0xFFA8732F.toInt(), accentSoft = 0xFFF6EDDE.toInt(),
        keyBg = 0xFFF0E8DA.toInt(), keyText = 0xFF33291D.toInt(), light = 0xFFEFE7D8.toInt(),
        infoBg = 0xFFF8F0E2.toInt(), infoListBg = 0xFFFDFAF4.toInt(),
        danger = 0xFFC4522C.toInt(), success = 0xFF3E7D4C.toInt(), hairline = 0xFFE8DFD0.toInt(),
    )
    private val WOOD_DARK = Palette(
        bg = 0xFF171310.toInt(), card = 0xFF211B16.toInt(), text = 0xFFF0E9DE.toInt(),
        sub = 0xFFA79A88.toInt(), accent = 0xFFD29A54.toInt(), accentSoft = 0xFF332A1E.toInt(),
        keyBg = 0xFF2A221A.toInt(), keyText = 0xFFF0E9DE.toInt(), light = 0xFF2E261C.toInt(),
        infoBg = 0xFF2B2319.toInt(), infoListBg = 0xFF1F1A14.toInt(),
        danger = 0xFFF06545.toInt(), success = 0xFF4CB877.toInt(), hairline = 0xFF382E21.toInt(),
    )

    // ---------------------------------------------------------------- 石墨·极简：黑白灰，锐利排版
    private val GRAPHITE_LIGHT = Palette(
        bg = 0xFFF4F4F5.toInt(), card = 0xFFFFFFFF.toInt(), text = 0xFF18181B.toInt(),
        sub = 0xFF6E6E73.toInt(), accent = 0xFF23272E.toInt(), accentSoft = 0xFFE9EAEC.toInt(),
        keyBg = 0xFFECECEE.toInt(), keyText = 0xFF18181B.toInt(), light = 0xFFE7E7E9.toInt(),
        infoBg = 0xFFEFEFF1.toInt(), infoListBg = 0xFFFAFAFA.toInt(),
        danger = 0xFFB3261E.toInt(), success = 0xFF1F7A44.toInt(), hairline = 0xFFE2E2E4.toInt(),
    )
    private val GRAPHITE_DARK = Palette(
        bg = 0xFF0E0F11.toInt(), card = 0xFF17181B.toInt(), text = 0xFFF2F2F4.toInt(),
        sub = 0xFF9A9AA0.toInt(), accent = 0xFF50555E.toInt(), accentSoft = 0xFF23262B.toInt(),
        keyBg = 0xFF1F2126.toInt(), keyText = 0xFFF2F2F4.toInt(), light = 0xFF24262B.toInt(),
        infoBg = 0xFF202226.toInt(), infoListBg = 0xFF191A1D.toInt(),
        danger = 0xFFF25A51.toInt(), success = 0xFF3BC072.toInt(), hairline = 0xFF2A2C31.toInt(),
    )

    // ---------------------------------------------------------------- 极光·青绿：深底 + 荧光青绿
    private val AURORA_LIGHT = Palette(
        bg = 0xFFF0F6F3.toInt(), card = 0xFFFFFFFF.toInt(), text = 0xFF14211C.toInt(),
        sub = 0xFF52756A.toInt(), accent = 0xFF0FAF7E.toInt(), accentSoft = 0xFFE1F1EA.toInt(),
        keyBg = 0xFFE2ECE7.toInt(), keyText = 0xFF14211C.toInt(), light = 0xFFDEE9E3.toInt(),
        infoBg = 0xFFE9F3EE.toInt(), infoListBg = 0xFFF5FAF7.toInt(),
        danger = 0xFFD6412C.toInt(), success = 0xFF0F8A5F.toInt(), hairline = 0xFFDFE9E4.toInt(),
    )
    private val AURORA_DARK = Palette(
        bg = 0xFF0B0F0E.toInt(), card = 0xFF131917.toInt(), text = 0xFFE4F0EC.toInt(),
        sub = 0xFF7FA69A.toInt(), accent = 0xFF22C98F.toInt(), accentSoft = 0xFF16241F.toInt(),
        keyBg = 0xFF1A211E.toInt(), keyText = 0xFFE4F0EC.toInt(), light = 0xFF1F2723.toInt(),
        infoBg = 0xFF18211D.toInt(), infoListBg = 0xFF101513.toInt(),
        danger = 0xFFF05945.toInt(), success = 0xFF2EDB9E.toInt(), hairline = 0xFF22302A.toInt(),
    )
}
