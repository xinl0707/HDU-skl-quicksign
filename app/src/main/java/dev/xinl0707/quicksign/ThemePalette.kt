package dev.xinl0707.quicksign

/**
 * 界面风格调色板：5 种风格（默认 / Apple / Claude / Tesla / Spotify）× 浅色 / 深色。
 * 色值取自 design-md 品牌设计规范；所有界面颜色统一从这里取，设置页可切换（theme_style）。
 */
object ThemePalette {

    const val KEY_STYLE = "theme_style"
    const val STYLE_DEFAULT = "default"
    const val STYLE_APPLE = "apple"
    const val STYLE_CLAUDE = "claude"
    const val STYLE_TESLA = "tesla"
    const val STYLE_SPOTIFY = "spotify"

    val STYLES = listOf(
        STYLE_DEFAULT, STYLE_APPLE, STYLE_CLAUDE, STYLE_TESLA, STYLE_SPOTIFY
    )

    fun styleLabel(style: String): String = when (style) {
        STYLE_APPLE -> "Apple"
        STYLE_CLAUDE -> "Claude"
        STYLE_TESLA -> "Tesla"
        STYLE_SPOTIFY -> "Spotify"
        else -> "默认"
    }

    data class Palette(
        val bg: Int,
        val card: Int,
        val text: Int,
        val sub: Int,
        val accent: Int,
        val keyBg: Int,
        val light: Int,
        val infoBg: Int,
        val infoListBg: Int,
    )

    fun palette(style: String, night: Boolean): Palette = when (style) {
        STYLE_APPLE -> if (night) APPLE_DARK else APPLE_LIGHT
        STYLE_CLAUDE -> if (night) CLAUDE_DARK else CLAUDE_LIGHT
        STYLE_TESLA -> if (night) TESLA_DARK else TESLA_LIGHT
        STYLE_SPOTIFY -> if (night) SPOTIFY_DARK else SPOTIFY_LIGHT
        else -> if (night) DEFAULT_DARK else DEFAULT_LIGHT
    }

    // ---------------------------------------------------------------- 默认（现状，主色 #3B71FE）
    private val DEFAULT_LIGHT = Palette(
        bg = 0xFFF5F7FA.toInt(), card = 0xFFFFFFFF.toInt(), text = 0xFF1F2329.toInt(),
        sub = 0xFF646A73.toInt(), accent = 0xFF3B71FE.toInt(), keyBg = 0xFFF2F3F5.toInt(),
        light = 0xFFEEF2FF.toInt(), infoBg = 0xFFF0F5FF.toInt(), infoListBg = 0xFFF7F9FF.toInt(),
    )
    private val DEFAULT_DARK = Palette(
        bg = 0xFF141519.toInt(), card = 0xFF1E2026.toInt(), text = 0xFFE6E7EB.toInt(),
        sub = 0xFF9AA0A8.toInt(), accent = 0xFF3B71FE.toInt(), keyBg = 0xFF262930.toInt(),
        light = 0xFF2A2D34.toInt(), infoBg = 0xFF1F2430.toInt(), infoListBg = 0xFF1A1D24.toInt(),
    )

    // ---------------------------------------------------------------- Apple：白色画布 + Action Blue #0066cc（深色用 #2997ff），无装饰
    private val APPLE_LIGHT = Palette(
        bg = 0xFFFFFFFF.toInt(), card = 0xFFF5F5F7.toInt(), text = 0xFF1D1D1F.toInt(),
        sub = 0xFF86868B.toInt(), accent = 0xFF0066CC.toInt(), keyBg = 0xFFF5F5F7.toInt(),
        light = 0xFFF0F0F0.toInt(), infoBg = 0xFFF5F5F7.toInt(), infoListBg = 0xFFFAFAFC.toInt(),
    )
    private val APPLE_DARK = Palette(
        bg = 0xFF000000.toInt(), card = 0xFF1D1D1F.toInt(), text = 0xFFFFFFFF.toInt(),
        sub = 0xFFA1A1A6.toInt(), accent = 0xFF2997FF.toInt(), keyBg = 0xFF2A2A2C.toInt(),
        light = 0xFF333336.toInt(), infoBg = 0xFF1D1D1F.toInt(), infoListBg = 0xFF272729.toInt(),
    )

    // ---------------------------------------------------------------- Claude：奶油画布 + 珊瑚 #cc785c，暖色人文感
    private val CLAUDE_LIGHT = Palette(
        bg = 0xFFFAF9F5.toInt(), card = 0xFFF5F0E8.toInt(), text = 0xFF141413.toInt(),
        sub = 0xFF6C6A64.toInt(), accent = 0xFFCC785C.toInt(), keyBg = 0xFFF5F0E8.toInt(),
        light = 0xFFEFE9DE.toInt(), infoBg = 0xFFF0EBE2.toInt(), infoListBg = 0xFFFAF9F5.toInt(),
    )
    private val CLAUDE_DARK = Palette(
        bg = 0xFF181715.toInt(), card = 0xFF252320.toInt(), text = 0xFFFAF9F5.toInt(),
        sub = 0xFFA09D96.toInt(), accent = 0xFFCC785C.toInt(), keyBg = 0xFF252320.toInt(),
        light = 0xFF2E2B27.toInt(), infoBg = 0xFF1F1E1B.toInt(), infoListBg = 0xFF252320.toInt(),
    )

    // ---------------------------------------------------------------- Tesla：纯白/碳灰极简 + Electric Blue #3e6ae1，小圆角
    private val TESLA_LIGHT = Palette(
        bg = 0xFFFFFFFF.toInt(), card = 0xFFF4F4F4.toInt(), text = 0xFF171A20.toInt(),
        sub = 0xFF5C5E62.toInt(), accent = 0xFF3E6AE1.toInt(), keyBg = 0xFFF4F4F4.toInt(),
        light = 0xFFEEEEEE.toInt(), infoBg = 0xFFF4F4F4.toInt(), infoListBg = 0xFFFBFBFB.toInt(),
    )
    private val TESLA_DARK = Palette(
        bg = 0xFF171A20.toInt(), card = 0xFF21262F.toInt(), text = 0xFFFFFFFF.toInt(),
        sub = 0xFF8E8E8E.toInt(), accent = 0xFF3E6AE1.toInt(), keyBg = 0xFF21262F.toInt(),
        light = 0xFF2A3038.toInt(), infoBg = 0xFF1F232C.toInt(), infoListBg = 0xFF242A33.toInt(),
    )

    // ---------------------------------------------------------------- Spotify：近黑沉浸 + Spotify Green #1ed760（浅色用 #1db954）
    private val SPOTIFY_LIGHT = Palette(
        bg = 0xFFF7F7F7.toInt(), card = 0xFFFFFFFF.toInt(), text = 0xFF121212.toInt(),
        sub = 0xFF6E6E6E.toInt(), accent = 0xFF1DB954.toInt(), keyBg = 0xFFEEEEEE.toInt(),
        light = 0xFFEEEEEE.toInt(), infoBg = 0xFFF4F8F2.toInt(), infoListBg = 0xFFFFFFFF.toInt(),
    )
    private val SPOTIFY_DARK = Palette(
        bg = 0xFF121212.toInt(), card = 0xFF181818.toInt(), text = 0xFFFFFFFF.toInt(),
        sub = 0xFFB3B3B3.toInt(), accent = 0xFF1ED760.toInt(), keyBg = 0xFF1F1F1F.toInt(),
        light = 0xFF252525.toInt(), infoBg = 0xFF181818.toInt(), infoListBg = 0xFF1F1F1F.toInt(),
    )
}
