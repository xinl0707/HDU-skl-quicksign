package dev.xinl0707.quicksign

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * 启动画面：品牌色背景跟随「主题风格 × 系统深浅」，
 * 0.6 秒后 fade 淡入主界面——消除冷启动闪白/闪错色，且不增加等待。
 */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var started = false

    private val isNight: Boolean by lazy {
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun themeStyle(): String =
        getSharedPreferences("settings", MODE_PRIVATE)
            .getString(ThemePalette.KEY_STYLE, ThemePalette.STYLE_DEFAULT) ?: ThemePalette.STYLE_DEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = ThemePalette.palette(themeStyle(), isNight)

        // 首帧即品牌色：状态栏/导航栏同步，浅色模式用深色图标
        try {
            window.statusBarColor = palette.bg
            window.navigationBarColor = palette.bg
            window.decorView.systemUiVisibility =
                if (!isNight) window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                else window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } catch (e: Exception) {}

        val root = FrameLayout(this).apply {
            setBackgroundColor(palette.bg)
            fitsSystemWindows = true
        }

        // 居中：图标 + 名称
        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val icon = ImageView(this).apply { setImageResource(R.drawable.ic_launcher) }
        center.addView(icon, LinearLayout.LayoutParams(dp(88), dp(88)))
        val name = TextView(this).apply {
            text = "上课啦签到"
            textSize = 19f
            setTextColor(palette.text)
            gravity = Gravity.CENTER
        }
        center.addView(name, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        root.addView(center, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))

        // 底部小转圈
        val spinner = ProgressBar(this, null, android.R.attr.progressBarStyleSmall)
        val spinnerWrap = FrameLayout(this).apply {
            addView(spinner, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        }
        root.addView(spinnerWrap, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(120), Gravity.BOTTOM
        ))

        setContentView(root)

        // 0.6 秒后淡入主界面（主界面在 Splash 期间照常构建，不增加等待）
        handler.postDelayed({
            if (started) return@postDelayed
            started = true
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 600)
    }

    private fun dp(v: Number): Int =
        android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
        ).toInt()
}
