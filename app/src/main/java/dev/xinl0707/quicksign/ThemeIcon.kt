package dev.xinl0707.quicksign

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 应用图标跟随主题动态切换（activity-alias 方案）：
 * Manifest 中为 5 套主题各声明一个指向 MainActivity 的 activity-alias（各自带独立图标），
 * 运行时只启用当前主题对应的 alias、禁用其余 4 个，桌面图标即切换为该主题色系。
 * 设置页切换主题与主界面启动时都会调用 apply()。
 */
object ThemeIcon {

    private const val ALIAS_PREFIX = "dev.xinl0707.quicksign.ThemeIcon"

    private fun aliasSuffix(style: String): String = when (style) {
        ThemePalette.STYLE_GUOCHAO -> "Guochao"
        ThemePalette.STYLE_WOOD -> "Wood"
        ThemePalette.STYLE_GRAPHITE -> "Graphite"
        ThemePalette.STYLE_AURORA -> "Aurora"
        else -> "Default"
    }

    private val ALL_SUFFIXES = listOf("Default", "Guochao", "Wood", "Graphite", "Aurora")

    /** 启用当前主题图标、禁用其余图标（先启用再禁用，保证桌面图标入口不中断） */
    fun apply(context: Context, style: String) {
        try {
            val pm = context.packageManager
            val pkg = context.packageName
            val current = aliasSuffix(style)
            pm.setComponentEnabledSetting(
                ComponentName(pkg, "$ALIAS_PREFIX$current"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            for (s in ALL_SUFFIXES) {
                if (s == current) continue
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$ALIAS_PREFIX$s"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            // 组件状态切换失败不影响主功能
        }
    }
}
