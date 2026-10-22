package dev.xinl0707.quicksign

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 设置页 v4.0（账号与安全 / 签到与语音 / 课表 / 外观 / 关于与调试）。
 * 设计语言：iOS 式分组 + Tesla 式克制深色 + 主题色只出现在该出现的地方。
 * 交互约定：Switch 替代 CheckBox、底部浮层替代 Toast、居中警示卡处理失败、
 * 自绘弹层替代系统 AlertDialog、主题卡/开关带轻震动反馈、高级配置默认折叠。
 */
class SettingsActivity : Activity() {

    private var firstRun = false   // 首次引导模式：隐藏返回按钮、拦截系统返回键，只能点右上角保存离开
    private lateinit var credStore: CredentialStore
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var mimoKeyInput: EditText
    private lateinit var mimoUrlInput: EditText
    private lateinit var mimoAsrInput: EditText
    private lateinit var scheduleStatusText: TextView
    private lateinit var logText: TextView
    private lateinit var rootFrame: FrameLayout
    private var snackBar: View? = null

    private val handler = Handler(Looper.getMainLooper())
    private var icsPickRequested = false   // 防止系统文件选择器返回时 activity 已重建导致重复读取

    // 深色模式跟随系统；配色按「界面风格 × 深浅」从 ThemePalette 取
    private val isNight: Boolean by lazy {
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun themeStyle(): String =
        getSharedPreferences("settings", MODE_PRIVATE)
            .getString(ThemePalette.KEY_STYLE, ThemePalette.STYLE_DEFAULT) ?: ThemePalette.STYLE_DEFAULT

    private val palette: ThemePalette.Palette by lazy { ThemePalette.palette(themeStyle(), isNight) }
    private val ACCENT: Int get() = palette.accent
    private val BG: Int get() = palette.bg
    private val CARD: Int get() = palette.card
    private val TEXT: Int get() = palette.text
    private val SUB: Int get() = palette.sub
    private val LIGHT: Int get() = palette.light
    private val HINT: Int by lazy { if (isNight) 0xFF6B7078.toInt() else 0xFF9AA0A8.toInt() }
    private val TRACK_OFF: Int by lazy { if (isNight) 0xFF4A4E55.toInt() else 0xFFD4D7DC.toInt() }
    private val LOG_INFO: Int by lazy { if (isNight) 0xFF9FB4D0.toInt() else 0xFF4A6FA5.toInt() }
    private val LOG_WARN: Int by lazy { if (isNight) 0xFFE2B93B.toInt() else 0xFFB7791F.toInt() }
    private val LOG_ERR: Int by lazy { if (isNight) 0xFFF2555A.toInt() else 0xFFD93025.toInt() }

    private val REQ_ICS = 9001

    /** 打开系统文件选择器选 .ics 课表文件（Android 11+ SAF，读文件无需存储权限） */
    private fun pickIcsFile() {
        icsPickRequested = true
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/calendar", "text/plain", "application/octet-stream"))
        }
        try {
            startActivityForResult(intent, REQ_ICS)
        } catch (e: Exception) {
            icsPickRequested = false
            snack("无法打开文件选择器：" + (e.message ?: ""))
        }
    }

    /** 导入后的状态展示：已导入门数 / 未导入 */
    private fun refreshScheduleStatus() {
        val ics = credStore.scheduleCacheIcs()
        if (ics.isNullOrBlank()) {
            scheduleStatusText.text = "尚未导入课表"
            scheduleStatusText.setTextColor(LOG_ERR)
        } else {
            val count = ics.split("BEGIN:VEVENT").size - 1
            scheduleStatusText.text = "已导入 $count 门课程"
            scheduleStatusText.setTextColor(ACCENT)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ICS) {
            if (resultCode == Activity.RESULT_OK && data?.data != null) {
                importIcs(data.data!!)
            } else {
                icsPickRequested = false
            }
        }
    }

    /** 读取所选 ICS 文件内容并保存（固定保存，离线使用；主界面面板会解析显示当前课程） */
    private fun importIcs(uri: Uri) {
        icsPickRequested = false
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val text = input.bufferedReader(Charsets.UTF_8).readText()
                if (!text.contains("BEGIN:VEVENT")) {
                    snack("文件内容不是有效的 ICS 课表")
                    return
                }
                credStore.setScheduleCacheIcs(text)
                refreshScheduleStatus()
                val cnt = text.split("BEGIN:VEVENT").size - 1
                DebugLog.info("设置", "导入课表成功（$cnt 门课程）")
                snack("课表已导入（$cnt 门课程）")
            } ?: snack("无法读取所选文件")
        } catch (e: Exception) {
            snack("导入失败：" + (e.message ?: "未知错误"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credStore = CredentialStore(this)
        buildUi()
        // Android 13+ 手势返回/预测性返回不走 onBackPressed：首次引导模式下手势返回也要禁用
        if (firstRun && Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(0) {
                snack("请点击右上角「保存」完成设置")
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 基础绘制工具

    private fun dp(v: Number): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun roundedBg(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }

    private fun vibrate(ms: Long = 15) {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: Exception) {}
    }

    private fun button(label: String, bg: Int, fg: Int, big: Boolean = false, radius: Int = 14): TextView =
        TextView(this).apply {
            text = label
            textSize = if (big) 15f else 13f
            setTextColor(fg)
            gravity = Gravity.CENTER
            background = roundedBg(bg, radius)
            minHeight = dp(if (big) 46 else 40)
            setPadding(dp(14), 0, dp(14), 0)
            isClickable = true
        }

    private fun label(text: String, size: Float = 13f, color: Int = SUB): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
        }

    private fun blockTitle(text: String): TextView = label(text, 15f, TEXT).apply {
        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
    }

    /** 圆角输入框（深色下文字/提示配色适配；聚焦时主题色描边 + 淡光晕） */
    private fun edit(hint: String, singleLine: Boolean = true, password: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = if (password)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else if (singleLine)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(HINT)
            background = roundedBg(CARD, 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            // 聚焦态：主题色描边 + 恢复常态
            onFocusChangeListener = View.OnFocusChangeListener { v, has ->
                val bg = roundedBg(CARD, 14)
                if (has) bg.setStroke(dp(2), ACCENT)
                v.background = bg
            }
        }

    /** 自绘 Switch（iOS 风格）：44×26 胶囊 + 22 滑块，点击切换 + 180ms 动画 + 轻震动 */
    private fun switchView(initial: Boolean, onChange: (Boolean) -> Unit): FrameLayout {
        var state = initial
        val track = FrameLayout(this).apply {
            background = roundedBg(if (state) ACCENT else TRACK_OFF, 100)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            isClickable = true
        }
        val knob = View(this).apply {
            background = roundedBg(0xFFFFFFFF.toInt(), 100)
            elevation = dp(1).toFloat()
        }
        track.addView(knob, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER_VERTICAL or Gravity.START))
        track.setOnClickListener {
            state = !state
            vibrate(15)
            track.background = roundedBg(if (state) ACCENT else TRACK_OFF, 100)
            val targetX = if (state) track.width - knob.width - dp(4) else 0f
            knob.animate().translationX(targetX.toFloat()).setDuration(180)
                .setInterpolator(DecelerateInterpolator()).start()
            onChange(state)
        }
        return track
    }

    /** 开关行：左标题（+可选副文案），右 Switch */
    private fun switchRow(label: String, sub: String? = null, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(2), dp(6))
        }
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(label(label, 15f, TEXT))
        if (sub != null) textCol.addView(label(sub, 12f, SUB))
        row.addView(textCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(switchView(initial, onChange))
        return row
    }

    /** 分块卡片容器（圆角背景 + 内边距，视觉分组；标题行带主题色条） */
    private fun block(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(CARD, 18)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        val titleRow = LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(View(this@SettingsActivity).apply {
            background = roundedBg(ACCENT, 100)
        }, LinearLayout.LayoutParams(dp(4), dp(16)))
        titleRow.addView(blockTitle(title), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })
        addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })
    }

    // ---------------------------------------------------------------- 弹层系统（替代 Toast / AlertDialog）

    /** 底部浮层提示（成功/普通信息）：上滑淡入，2.2s 后自动下滑消失 */
    private fun snack(msg: String) {
        snackBar?.let { rootFrame.removeView(it) }
        val bar = TextView(this).apply {
            text = msg
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = roundedBg(0xE6323A3F.toInt(), 16)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        )
        lp.leftMargin = dp(20); lp.rightMargin = dp(20); lp.bottomMargin = dp(24)
        rootFrame.addView(bar, lp)
        bar.alpha = 0f
        bar.translationY = dp(40).toFloat()
        bar.animate().alpha(1f).translationY(0f).setDuration(200)
            .setInterpolator(DecelerateInterpolator()).start()
        bar.postDelayed({
            bar.animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(200)
                .withEndAction { rootFrame.removeView(bar) }.start()
        }, 2200)
        snackBar = bar
    }

    /** 居中警示卡（失败/需确认场景）：半透明遮罩 + 圆角卡，点按钮关闭 */
    private fun warn(title: String, msg: String) {
        val mask = View(this).apply { setBackgroundColor(0x66000000.toInt()) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(CARD, 20)
            setPadding(dp(24), dp(20), dp(24), dp(18))
        }
        card.addView(label(title, 17f, TEXT).apply { setTypeface(android.graphics.Typeface.DEFAULT_BOLD) })
        card.addView(label(msg, 14f, TEXT).apply { setLineSpacing(1.2f, 1.0f) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
        val ok = button("知道了", ACCENT, 0xFFFFFFFF.toInt(), radius = 16)
        ok.setOnClickListener { rootFrame.removeView(mask); rootFrame.removeView(card) }
        card.addView(ok, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ).apply { topMargin = dp(16) })
        rootFrame.addView(mask, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootFrame.addView(card, FrameLayout.LayoutParams(
            resources.displayMetrics.widthPixels - dp(56), ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        card.alpha = 0f; card.scaleX = 0.94f; card.scaleY = 0.94f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    /** 自绘菜单弹层（居中卡片 + 条目行，替代 AlertDialog items） */
    private fun sheetMenu(title: String, items: List<Pair<String, () -> Unit>>) {
        val mask = View(this).apply { setBackgroundColor(0x66000000.toInt()) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(CARD, 20)
            setPadding(dp(10), dp(12), dp(10), dp(10))
        }
        card.addView(label(title, 16f, TEXT).apply {
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(0), dp(6), dp(0), dp(8))
        })
        for ((i, item) in items.withIndex()) {
            val it = TextView(this).apply {
                text = item.first
                textSize = 15f
                setTextColor(TEXT)
                gravity = Gravity.CENTER
                background = roundedBg(0x00000000.toInt(), 12)
                minHeight = dp(46)
                isClickable = true
                if (i > 0) elevation = 0f
            }
            it.setOnClickListener {
                rootFrame.removeView(mask); rootFrame.removeView(card)
                item.second()
            }
            card.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { if (i > 0) topMargin = dp(4) })
        }
        val cancel = TextView(this).apply {
            text = "取消"
            textSize = 15f
            setTextColor(SUB)
            gravity = Gravity.CENTER
            minHeight = dp(44)
            isClickable = true
        }
        cancel.setOnClickListener { rootFrame.removeView(mask); rootFrame.removeView(card) }
        card.addView(cancel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })
        rootFrame.addView(mask, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootFrame.addView(card, FrameLayout.LayoutParams(
            resources.displayMetrics.widthPixels - dp(56), ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        card.alpha = 0f; card.scaleX = 0.94f; card.scaleY = 0.94f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    /** 帮助弹层（ⓘ 说明） */
    private fun help(title: String, text: String) {
        val mask = View(this).apply { setBackgroundColor(0x66000000.toInt()) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(CARD, 20)
            setPadding(dp(22), dp(18), dp(22), dp(16))
        }
        card.addView(label(title, 16f, TEXT).apply { setTypeface(android.graphics.Typeface.DEFAULT_BOLD) })
        card.addView(label(text, 13f, SUB).apply { setLineSpacing(1.25f, 1.0f) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        val ok = button("知道了", LIGHT, ACCENT, radius = 16)
        ok.setOnClickListener { rootFrame.removeView(mask); rootFrame.removeView(card) }
        card.addView(ok, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(42)
        ).apply { topMargin = dp(14) })
        rootFrame.addView(mask, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootFrame.addView(card, FrameLayout.LayoutParams(
            resources.displayMetrics.widthPixels - dp(56), ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        card.alpha = 0f; card.scaleX = 0.94f; card.scaleY = 0.94f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    /** 免责声明全文弹层（Markdown 渲染 + 可滚动） */
    private fun showDisclaimer() {
        val mask = View(this).apply { setBackgroundColor(0x66000000.toInt()) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(CARD, 20)
            setPadding(dp(22), dp(18), dp(22), dp(16))
        }
        card.addView(label("免责声明", 17f, TEXT).apply {
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        })
        val body = TextView(this).apply {
            text = Markdown.toSpanned(Disclaimer.TEXT_TEMPLATE.replace("__VER__", appVersion()))
            textSize = 13f
            setTextColor(TEXT)
            setLineSpacing(1.3f, 1.0f)
            setVerticalScrollBarEnabled(true)
            movementMethod = android.text.method.ScrollingMovementMethod()
        }
        val maxH = (resources.displayMetrics.heightPixels * 0.52f).toInt()
        card.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, maxH
        ).apply { topMargin = dp(10) })
        val close = button("关闭", ACCENT, 0xFFFFFFFF.toInt(), radius = 16)
        close.setOnClickListener { rootFrame.removeView(mask); rootFrame.removeView(card) }
        card.addView(close, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(44)
        ).apply { topMargin = dp(12) })
        rootFrame.addView(mask, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        rootFrame.addView(card, FrameLayout.LayoutParams(
            resources.displayMetrics.widthPixels - dp(40), ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        card.alpha = 0f; card.scaleX = 0.94f; card.scaleY = 0.94f
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    private fun appVersion(): String =
        try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }

    // ---------------------------------------------------------------- 页面构建

    private fun buildUi() {
        firstRun = intent.getBooleanExtra("first_run", false)
        DebugLog.info("设置", "打开设置页（${if (firstRun) "首次引导模式" else "常规模式"}）")
        // 状态栏/导航栏配色跟随主题风格，内容避开系统栏（edge-to-edge 适配）
        try {
            window.statusBarColor = BG
            window.navigationBarColor = BG
            window.decorView.systemUiVisibility =
                if (!isNight) window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                else window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } catch (e: Exception) {}

        // 根容器：浮层（snack/警示卡/弹层）都挂在这层之上
        rootFrame = FrameLayout(this).apply { setBackgroundColor(BG) }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(dp(20), dp(10), dp(20), dp(28))
        }
        scroll.addView(root)
        rootFrame.addView(scroll, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // ---------- 大标题行：返回胶囊 + 「设置」28sp 粗 + 首次引导时右侧保存 ----------
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (firstRun) {
            titleRow.addView(View(this), LinearLayout.LayoutParams(dp(64), dp(40)))
        } else {
            val backBtn = TextView(this).apply {
                text = "‹"
                textSize = 22f
                setTextColor(TEXT)
                gravity = Gravity.CENTER
                background = roundedBg(CARD, 100)
                setPadding(0, 0, 0, dp(2))
                isClickable = true
            }
            backBtn.setOnClickListener { finish() }
            titleRow.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        titleRow.addView(label("设置", 28f, TEXT).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(12), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (firstRun) {
            val saveTop = button("保存", ACCENT, 0xFFFFFFFF.toInt(), radius = 16)
            saveTop.setOnClickListener { save() }
            titleRow.addView(saveTop, LinearLayout.LayoutParams(dp(64), dp(40)))
        }
        root.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        // ========== 账号与安全 ==========
        val accountBlock = block("账号与安全")
        accountBlock.addView(label("学号（数字杭电账号）", 13f, SUB))
        userInput = edit("请输入学号").apply { setText(credStore.username() ?: "") }
        autoSave(userInput)
        accountBlock.addView(userInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        accountBlock.addView(label("密码", 13f, SUB), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
        val passRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        passInput = edit("请输入密码", password = true).apply { setText(credStore.password() ?: "") }
        autoSave(passInput)
        passRow.addView(passInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val eyeBtn = TextView(this).apply {
            text = "显示"
            textSize = 12f
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
            background = roundedBg(LIGHT, 100)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isClickable = true
        }
        eyeBtn.setOnClickListener {
            val show = passInput.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            passInput.inputType = if (!show)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            eyeBtn.text = if (!show) "隐藏" else "显示"
            passInput.setSelection(passInput.text.length)
        }
        passRow.addView(eyeBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })
        accountBlock.addView(passRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        accountBlock.addView(switchRow("自动登录", "下次打开自动登录数字杭电", credStore.autoLogin()) { _ -> autoSaveAll() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(6) })
        root.addView(accountBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // ========== 签到与语音 ==========
        val voiceBlock = block("签到与语音")
        voiceBlock.addView(switchRow("自动语音监听", "进入签到页自动开始监听", credStore.autoListen()) { c -> credStore.setAutoListen(c) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        voiceBlock.addView(switchRow("主页识别日志", "开发者选项 · 签到页显示识别过程", credStore.showSpeechInfo()) { c -> credStore.setShowSpeechInfo(c) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // MIMO 高级配置折叠组
        val mimoCollapse = collapsible("MIMO 高级配置", "语音识别引擎：Xiaomi-MIMO（云端）", listOf(
            "API Key" to "请到 mimo.mi.com 控制台注册获取；左下角填入邀请码 ZPAF4Y 可领 10 元免费额度",
            "接口地址" to "默认 https://api.xiaomimimo.com/v1，一般无需修改",
            "ASR 转写模型" to "语音转写专用模型（约 3 秒切片）",
            "LLM 判断模型" to "对转写片段做综合判断、提取四位签到码的模型"
        )) { content ->
            // API Key（密码态 + 显示切换）
            content.addView(label("API Key", 13f, SUB))
            val keyRow = LinearLayout(this@SettingsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            mimoKeyInput = edit("sk-xxx", password = true).apply {
                setText(credStore.mimoApiKey() ?: "")
                textSize = 14f
            }
            autoSave(mimoKeyInput)
            keyRow.addView(mimoKeyInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val showKey = TextView(this@SettingsActivity).apply {
                text = "显示"
                textSize = 12f
                setTextColor(ACCENT)
                gravity = Gravity.CENTER
                background = roundedBg(LIGHT, 100)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                isClickable = true
            }
            showKey.setOnClickListener {
                val show = mimoKeyInput.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                mimoKeyInput.inputType = if (!show)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                showKey.text = if (!show) "隐藏" else "显示"
                mimoKeyInput.setSelection(mimoKeyInput.text.length)
            }
            keyRow.addView(showKey, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) })
            content.addView(keyRow, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
            // 接口地址 / ASR / LLM
            content.addView(label("接口地址", 13f, SUB), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
            mimoUrlInput = edit("接口地址").apply { setText(credStore.mimoBaseUrl()); textSize = 13f }
            autoSave(mimoUrlInput)
            content.addView(mimoUrlInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
            content.addView(label("ASR 转写模型", 13f, SUB), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
            mimoAsrInput = edit("ASR 转写模型").apply { setText(credStore.mimoAsrModel()); textSize = 13f }
            autoSave(mimoAsrInput)
            content.addView(mimoAsrInput, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
        }
        voiceBlock.addView(mimoCollapse, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        root.addView(voiceBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // ========== 课表 ==========
        val scheduleBlock = block("课表")
        val schedRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scheduleStatusText = TextView(this).apply {
            textSize = 13f
            setTextColor(ACCENT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        schedRow.addView(scheduleStatusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val importBtn = button("导入课表 (.ICS)", LIGHT, ACCENT, radius = 16)
        importBtn.setOnClickListener { pickIcsFile() }
        schedRow.addView(importBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })
        scheduleBlock.addView(schedRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        scheduleBlock.addView(label("杭电助手 → 课表 → 订阅课表，下载 ICS 文件后导入", 11f, SUB),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(4) })
        refreshScheduleStatus()
        root.addView(scheduleBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // ========== 外观 ==========
        val styleBlock = block("外观")
        styleBlock.addView(label("选择界面风格", 13f, SUB))
        // 主题 2×3 网格（5 主题 → 3 + 2 两行）；切换主题只重建网格容器，不动块内其它视图
        val gridContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun buildStyleGrid() {
            gridContainer.removeAllViews()
            val styles = ThemePalette.STYLES
            val cur = themeStyle()
            val gridRows = mutableListOf<LinearLayout>()
            for (i in styles.indices) {
                if (i % 3 == 0) {
                    gridRows.add(LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    })
                }
                val s = styles[i]
                val sel = s == cur
                val accent = ThemePalette.palette(s, isNight).accent
                val card = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = roundedBg(if (sel) accent else LIGHT, 18)
                    elevation = if (sel) dp(3).toFloat() else 0f
                    setPadding(dp(4), dp(10), dp(4), dp(8))
                    isClickable = true
                }
                card.addView(View(this@SettingsActivity).apply {
                    background = roundedBg(if (sel) 0xFFFFFFFF.toInt() else accent, 100)
                }, LinearLayout.LayoutParams(dp(32), dp(32)))
                card.addView(label(ThemePalette.styleLabel(s), 11f, if (sel) 0xFFFFFFFF.toInt() else TEXT).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(0), dp(6), dp(0), dp(0))
                    setSingleLine(true)
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                card.setOnClickListener {
                    vibrate(15)
                    getSharedPreferences("settings", MODE_PRIVATE).edit()
                        .putString(ThemePalette.KEY_STYLE, s).commit()
                    DebugLog.info("设置", "切换主题风格为「${ThemePalette.styleLabel(s)}」（返回主界面后生效）")
                    snack("已切换「${ThemePalette.styleLabel(s)}」，返回主界面后生效")
                    buildStyleGrid()
                }
                val row = gridRows.last()
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (row.childCount > 0) lp.leftMargin = dp(8)
                row.addView(card, lp)
            }
            for (r in gridRows) {
                gridContainer.addView(r, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { if (gridContainer.childCount > 0) topMargin = dp(8) })
            }
        }
        styleBlock.addView(gridContainer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        buildStyleGrid()
        styleBlock.addView(label("深色模式随系统自动切换", 11f, SUB), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })
        styleBlock.addView(switchRow("隐藏首页底部状态显示", null, credStore.hideStatusBar()) { c ->
            credStore.setHideStatusBar(c)
            snack("已保存")
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(4) })
        root.addView(styleBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })

        // ========== 关于与调试 ==========
        val aboutBlock = block("关于与调试")
        aboutBlock.addView(switchRow("调试日志", "记录运行过程，导出可定位问题", credStore.debugEnabled()) { checked ->
            credStore.setDebugEnabled(checked)
            logText.visibility = if (checked) View.VISIBLE else View.GONE
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        // 日志区：等宽字体 + 等级着色（I 信息青 / W 警告黄 / E 错误红）
        logText = TextView(this).apply {
            textSize = 11f
            setTextIsSelectable(true)
            setTextColor(SUB)
            visibility = if (credStore.debugEnabled()) View.VISIBLE else View.GONE
            background = roundedBg(LIGHT, 12)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            typeface = android.graphics.Typeface.MONOSPACE
            setLineSpacing(0f, 1.1f)
            movementMethod = android.text.method.ScrollingMovementMethod()
            isVerticalScrollBarEnabled = true
        }
        aboutBlock.addView(logText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(180)
        ).apply { topMargin = dp(6) })
        // 日志区右上角「清空」小按钮
        val clearRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val clearBtn = TextView(this).apply {
            text = "清空日志"
            textSize = 11f
            setTextColor(SUB)
            gravity = Gravity.CENTER
            background = roundedBg(LIGHT, 100)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            isClickable = true
        }
        clearBtn.setOnClickListener {
            DebugLog.clear()
            logText.text = "（日志已清空）"
            snack("日志已清空")
        }
        clearRow.addView(clearBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        aboutBlock.addView(clearRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        // 导出 + 弹窗测试 双按钮行
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val exportBtn = button("导出调试记录", LIGHT, ACCENT, radius = 16)
        exportBtn.setOnClickListener { exportDebug() }
        btnRow.addView(exportBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val testBtn = button("动画测试", LIGHT, ACCENT, radius = 16)
        testBtn.setOnClickListener {
            sheetMenu("动画与弹窗测试", listOf(
                "开屏动画（重新进入主界面）" to { launchTest("splash") },
                "签到成功覆盖页 + 彩带" to { launchTest("success") },
                "大数字 + 彩带" to { launchTest("bigcode") },
                "签到码无效弹窗" to { launchTest("invalid") },
                "结果等待转圈" to { launchTest("waiting") },
                "底部浮层提示（本页演示）" to { snack("这是底部浮层示例，2 秒后自动消失") },
                "居中警示卡（本页演示）" to { warn("居中警示卡示例", "失败或需确认的场景使用这种卡片，需手动点「知道了」关闭。") }
            ))
        }
        btnRow.addView(testBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(8) })
        aboutBlock.addView(btnRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        // 免责声明 + 版本 行
        val disclaimerRow = linkRow("免责声明", "查看完整版（随版本更新）") { showDisclaimer() }
        aboutBlock.addView(disclaimerRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        val versionRow = linkRow("版本 ${appVersion()}", "GitHub 仓库 · xinl0707/HDU-skl-quicksign") {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/xinl0707/HDU-skl-quicksign")))
            } catch (e: Exception) { snack("无法打开链接") }
        }
        aboutBlock.addView(versionRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })
        root.addView(aboutBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(rootFrame)

        // 日志定时刷新（1 秒）：内容未变化不重设（避免打断用户正在滚动查看的位置）
        handler.post(object : Runnable {
            override fun run() {
                if (logText.visibility == View.VISIBLE) {
                    val dump = DebugLog.dump()
                    if (dump != logText.text.toString()) {
                        val sy = logText.scrollY
                        logText.text = colorizeLog(dump)
                        logText.scrollTo(0, sy)
                    }
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    /** 链接行：左侧标题 + 右侧 ›，点击触发动作 */
    private fun linkRow(title: String, sub: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(title, 15f, TEXT))
        col.addView(label(sub, 11f, SUB))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("›", 22f, SUB).apply { setPadding(dp(6), 0, dp(2), dp(2)) })
        row.setOnClickListener { onClick() }
        return row
    }

    /** 折叠组：标题行（▾ 箭头旋转 + 标题 + 副文案）+ 可展开内容，默认折叠 */
    private fun collapsible(title: String, subtitle: String, helps: List<Pair<String, String>>, body: (LinearLayout) -> Unit): LinearLayout {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(if (isNight) 0xFF23262B.toInt() else 0xFFF3F5F7.toInt(), 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        var expanded = false
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
        }
        val arrow = TextView(this).apply {
            text = "▸"
            textSize = 14f
            setTextColor(SUB)
            setPadding(dp(4), 0, dp(8), 0)
        }
        header.addView(arrow)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(title, 15f, TEXT))
        col.addView(label(subtitle, 11f, SUB))
        header.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(label("ⓘ", 14f, ACCENT).apply {
            setPadding(dp(8), 0, 0, 0)
            isClickable = true
        }.also { infoBtn ->
            infoBtn.setOnClickListener { help(title, helps.joinToString("\n") { (t, d) -> "$t：$d" }) }
        })
        val bodyBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        header.setOnClickListener {
            expanded = !expanded
            vibrate(10)
            arrow.animate().rotation(if (expanded) 90f else 0f).setDuration(180).start()
            if (expanded) {
                bodyBox.visibility = View.VISIBLE
                bodyBox.alpha = 0f
                bodyBox.animate().alpha(1f).setDuration(220).start()
            } else {
                bodyBox.animate().alpha(0f).setDuration(180)
                    .withEndAction { bodyBox.visibility = View.GONE }.start()
            }
        }
        outer.addView(header)
        outer.addView(bodyBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        body(bodyBox)
        return outer
    }

    /** 日志着色：按行匹配 [I]/[W]/[E] 等级，给整行染对应颜色 */
    private fun colorizeLog(dump: String): Spanned {
        val sp = SpannableString(dump)
        val levelColor = mapOf('I' to LOG_INFO, 'W' to LOG_WARN, 'E' to LOG_ERR)
        try {
            for (m in Regex("(?m)^(.*)\\[([IWED])\\]\\[([^\\]]+)\\] (.*)$").findAll(dump)) {
                val level = m.groupValues[2][0]
                levelColor[level]?.let { c ->
                    sp.setSpan(ForegroundColorSpan(c), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } catch (e: Exception) {}
        return sp
    }

    private fun launchTest(type: String) {
        if (type == "splash") {
            // 开屏动画：直接启动一个新的主界面实例（onCreate 会播放开屏），关闭设置页回到主界面
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        startActivity(Intent(this, MainActivity::class.java)
            .putExtra("test_overlay", type)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    // ---------------------------------------------------------------- 保存逻辑

    /** 自动保存防抖：输入/开关改动 500ms 后统一落盘（账号密码加密保存，避免逐字符触发 Keystore） */
    private val credSaveRunnable = Runnable {
        val u = userInput.text.toString().trim()
        val p = passInput.text.toString()
        if (u.isNotEmpty() && p.isNotEmpty()) {
            credStore.save(u, p, credStore.autoLogin())
        }
        credStore.setMimoApiKey(mimoKeyInput.text.toString().trim())
        credStore.setMimoBaseUrl(mimoUrlInput.text.toString().trim().ifEmpty { "https://api.xiaomimimo.com/v1" })
        credStore.setMimoAsrModel(mimoAsrInput.text.toString().trim())
        credStore.setAutoListen(credStore.autoListen())
        credStore.setShowSpeechInfo(credStore.showSpeechInfo())
    }

    private fun autoSaveAll() {
        handler.removeCallbacks(credSaveRunnable)
        handler.postDelayed(credSaveRunnable, 500)
    }

    /** 给输入框挂自动保存（改动即存，无需点保存按钮） */
    private fun autoSave(view: EditText) {
        view.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { autoSaveAll() }
        })
    }

    /** 保存全部设置；close=true 时保存后直接返回主界面 */
    private fun save(close: Boolean = true): Boolean {
        val u = userInput.text.toString().trim()
        val p = passInput.text.toString()
        if (u.isEmpty() || p.isEmpty()) {
            warn("无法保存", "学号和密码不能为空")
            return false
        }
        credStore.save(u, p, credStore.autoLogin())
        credStore.setMimoApiKey(mimoKeyInput.text.toString().trim())
        credStore.setMimoBaseUrl(mimoUrlInput.text.toString().trim().ifEmpty { "https://api.xiaomimimo.com/v1" })
        credStore.setMimoAsrModel(mimoAsrInput.text.toString().trim())
        credStore.setAutoListen(credStore.autoListen())
        credStore.setShowSpeechInfo(credStore.showSpeechInfo())
        DebugLog.info("设置", "保存设置（自动登录=${credStore.autoLogin()}，自动监听=${credStore.autoListen()}）")
        snack("设置已保存")
        if (close) finish()
        return true
    }

    /** 导出调试记录：日志全文 + 页面抓取详情，保存到本机并调起分享（发到微信/备忘录均可） */
    private fun exportDebug() {
        DebugLog.info("设置", "导出调试记录")
        try {
            val sb = StringBuilder()
            sb.append("上课啦 调试导出 ")
                .append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date()))
                .append("\n")
            sb.append("========================================\n")
            sb.append("【运行日志】\n").append(DebugLog.dump())
            sb.append("\n========================================\n")
            sb.append("【页面抓取详情】（覆盖界面设计依据）\n").append(DebugLog.capturesText())
            val content = sb.toString()

            // 1) 保存到安卓「下载」目录（Download/上课啦），API 29+ 用 MediaStore 免权限；低版本写应用目录兜底
            var savedPath = ""
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "debug_${System.currentTimeMillis()}.txt")
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/上课啦")
                        put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
                        values.clear()
                        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        contentResolver.update(uri, values, null, null)
                        savedPath = "下载/上课啦/debug_xxx.txt（相册 App 或文件管理器中查看）"
                    }
                } else {
                    val dir = getExternalFilesDir("logs") ?: filesDir
                    if (!dir.exists()) dir.mkdirs()
                    val f = java.io.File(dir, "debug_${System.currentTimeMillis()}.txt")
                    f.writeText(content)
                    savedPath = f.absolutePath
                }
            } catch (e: Exception) {
                savedPath = ""
            }

            // 2) 调起系统分享（微信/备忘录等）
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "上课啦调试导出")
                putExtra(Intent.EXTRA_TEXT, content)
            }
            startActivity(Intent.createChooser(send, "导出调试记录"))
            snack(if (savedPath.isNotEmpty()) "已保存：$savedPath" else "已调起分享")
        } catch (e: Exception) {
            snack("导出失败：${e.message}")
        }
    }

    /** 首次引导模式下拦截系统返回键：只能通过右上角「保存」离开设置页 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (firstRun) {
            snack("请点击右上角「保存」完成设置")
        } else {
            super.onBackPressed()
        }
    }
}
