package dev.xinl0707.quicksign

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * 账号与语音设置独立页面（主界面点右上角「设」进入）。
 * 内容分块卡片 + 可滚动：账号 / 语音识别（MIMO 云端）/ 课表导入 / 操作 / 调试。
 * 独立页面避免在主界面展开面板挤压网页预览。
 */
class SettingsActivity : Activity() {

    private var firstRun = false   // 首次引导模式：隐藏返回按钮、拦截系统返回键，只能点右上角保存离开
    private lateinit var credStore: CredentialStore
    private lateinit var userInput: EditText
    private lateinit var passInput: EditText
    private lateinit var autoLoginCheck: CheckBox
    private lateinit var engineGroup: RadioGroup
    private lateinit var mimoKeyInput: EditText
    private lateinit var mimoUrlInput: EditText
    private lateinit var mimoAsrInput: EditText
    private lateinit var mimoModelInput: EditText
    private lateinit var autoListenCheck: CheckBox
    private lateinit var showSpeechInfoCheck: CheckBox
    private lateinit var scheduleStatusText: TextView
    private lateinit var mimoBox: LinearLayout
    private lateinit var logText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var icsPickRequested = false   // 防止系统文件选择器返回时 activity 已重建导致重复读取

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
            toast("无法打开文件选择器：" + (e.message ?: ""))
        }
    }

    /** 导入后的状态展示：已导入门数 / 未导入 */
    private fun refreshScheduleStatus() {
        val ics = credStore.scheduleCacheIcs()
        if (ics.isNullOrBlank()) {
            scheduleStatusText.text = "尚未导入课表"
        } else {
            val count = ics.split("BEGIN:VEVENT").size - 1
            scheduleStatusText.text = "已导入课表：$count 门课程"
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
                    toast("文件内容不是有效的 ICS 课表")
                    return
                }
                credStore.setScheduleCacheIcs(text)
                refreshScheduleStatus()
                toast("课表已导入（${text.split("BEGIN:VEVENT").size - 1} 门课程）")
            } ?: toast("无法读取所选文件")
        } catch (e: Exception) {
            toast("导入失败：" + (e.message ?: "未知错误"))
        }
    }

    private val idMimo = View.generateViewId()
    private val REQ_ICS = 9001

    /** 带圆形感叹号提示的标签行：点击感叹号弹出说明对话框 */
    private fun helpRow(label: String, help: String): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(SUB)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        row.addView(TextView(this).apply {
            text = " ⓘ"
            textSize = 13f
            setTextColor(ACCENT)
            setPadding(dp(2), dp(0), dp(0), dp(0))
            setOnClickListener {
                android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle(label)
                    .setMessage(help)
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return row
    }

    companion object {
        // 注意：ACCENT 为实例属性（依赖 palette），见下方
    }

    // 深色模式跟随系统；配色按「界面风格 × 深浅」从 ThemePalette 取（延迟初始化，属性初始化时机早于系统资源绑定）
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credStore = CredentialStore(this)
        buildUi()
        // Android 13+ 手势返回/预测性返回不走 onBackPressed：首次引导模式下手势返回也要禁用
        if (firstRun && Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(0) {
                toast("请点击右上角「保存」完成设置")
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun dp(v: Number): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    /** 圆角矩形背景 */
    private fun roundedBg(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }

    private fun button(label: String, bg: Int, fg: Int, big: Boolean = false, radius: Int = 14): Button {
        val b = Button(this)
        b.text = label
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (big) 15f else 13f)
        b.background = roundedBg(bg, radius)
        b.setTextColor(fg)
        b.isAllCaps = false
        b.gravity = Gravity.CENTER
        b.minHeight = dp(if (big) 46f else 40f)
        b.setPadding(dp(10), 0, dp(10), 0)
        return b
    }

    private fun label(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        t.setTextColor(SUB)
        return t
    }

    /** 圆角输入框（深色下文字/提示配色适配） */
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
            background = roundedBg(CARD, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setText(credStore.username() ?: "")
        }

    /** 分块卡片容器（圆角背景 + 内边距，视觉分组） */
    private fun block(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(CARD, 16)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(TextView(this@SettingsActivity).apply {
            text = title
            textSize = 15f
            setTextColor(TEXT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(0), dp(0), dp(0), dp(4))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun buildUi() {
        // 首次引导模式：从主界面引导第三步进入，隐藏左上角返回、拦截系统返回键，
        // 只能通过右上角「保存」离开（防止用户在引导流程里乱点返回）
        firstRun = intent.getBooleanExtra("first_run", false)
        // 状态栏/导航栏配色跟随主题风格，内容避开系统栏（edge-to-edge 适配）
        try {
            window.statusBarColor = BG
            window.navigationBarColor = BG
            window.decorView.systemUiVisibility =
                if (!isNight) window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                else window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } catch (e: Exception) {}

        // 外层滚动：内容分块后超出屏幕可上下滚动
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        scroll.addView(root)

        // 标题栏：左返回按钮 + 居中标题 + 右侧等宽占位，保证标题视觉真正居中
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (firstRun) {
            // 首次引导：隐藏返回按钮（用等宽占位保持标题居中），只能点右上角保存离开
            titleRow.addView(View(this), LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        } else {
            val backBtn = button("返回", LIGHT, ACCENT)
            backBtn.setOnClickListener { finish() }
            titleRow.addView(backBtn, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val title = TextView(this).apply {
            text = "设置"
            textSize = 18f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // 右上角保存按钮（原"操作"块里的保存设置移到这里，操作块只留登录/清除）
        val saveTop = button("保存", ACCENT, 0xFFFFFFFF.toInt(), radius = 14)
        saveTop.setOnClickListener { save() }
        titleRow.addView(saveTop, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 账号块 ==========
        val accountBlock = block("账号")
        accountBlock.addView(label("学号（数字杭电账号）"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        userInput = edit("请输入学号")
        autoSave(userInput)
        accountBlock.addView(userInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        accountBlock.addView(label("密码"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
        passInput = edit("请输入密码", password = true).apply {
            setText(credStore.password() ?: "")
        }
        autoSave(passInput)
        accountBlock.addView(passInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        val showPw = CheckBox(this).apply {
            text = "显示密码"
            textSize = 13f
            setTextColor(SUB)
            setOnCheckedChangeListener { _, checked ->
                passInput.inputType = if (checked)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                passInput.setSelection(passInput.text.length)
            }
        }
        accountBlock.addView(showPw)
        autoLoginCheck = CheckBox(this).apply {
            text = "自动登录"
            textSize = 13f
            setTextColor(TEXT)
            isChecked = credStore.autoLogin()
            setOnCheckedChangeListener { _, _ -> autoSaveAll() }
        }
        accountBlock.addView(autoLoginCheck)
        root.addView(accountBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 语音识别块 ==========
        val voiceBlock = block("语音识别")
        // 语音识别仅 MIMO 云端引擎（系统识别已移除）：默认 MIMO，没填 Key 禁用语音识别
        voiceBlock.addView(label("语音识别引擎"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        engineGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val rbMimo = RadioButton(this).apply {
            text = "Xiaomi-MIMO"
            textSize = 13f
            setTextColor(TEXT)
            id = idMimo
        }
        engineGroup.addView(rbMimo)
        engineGroup.check(rbMimo.id)
        engineGroup.setOnCheckedChangeListener { _, checkedId ->
            mimoBox.visibility = if (checkedId == rbMimo.id) View.VISIBLE else View.GONE
        }
        voiceBlock.addView(engineGroup)
        autoListenCheck = CheckBox(this).apply {
            text = "自动语音监听"
            textSize = 13f
            setTextColor(TEXT)
            isChecked = credStore.autoListen()
            setOnCheckedChangeListener { _, checked -> credStore.setAutoListen(checked) }
        }
        voiceBlock.addView(autoListenCheck, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        showSpeechInfoCheck = CheckBox(this).apply {
            text = "显示主页识别日志（开发者选项）"
            textSize = 13f
            setTextColor(TEXT)
            isChecked = credStore.showSpeechInfo()
            setOnCheckedChangeListener { _, checked -> credStore.setShowSpeechInfo(checked) }
        }
        voiceBlock.addView(showSpeechInfoCheck, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })

        // MIMO 配置区（语音识别仅 MIMO：配置区恒显示）
        mimoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE   // 语音识别仅 MIMO：配置区恒显示
        }
        mimoBox.addView(helpRow("MIMO API Key", "请到 mimo.mi.com 控制台注册获取 API Key\n在控制台左下角填入邀请码 ZPAF4Y 领取 10 元免费额度"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        mimoKeyInput = edit("sk-xxx", password = true).apply {
            setText(credStore.mimoApiKey() ?: "")
            textSize = 14f
        }
        autoSave(mimoKeyInput)
        mimoBox.addView(mimoKeyInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        val showKey = CheckBox(this).apply {
            text = "显示 Key"
            textSize = 12f
            setTextColor(SUB)
            setOnCheckedChangeListener { _, checked ->
                mimoKeyInput.inputType = if (checked)
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                mimoKeyInput.setSelection(mimoKeyInput.text.length)
            }
        }
        mimoBox.addView(showKey)
        mimoBox.addView(label("接口地址"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        mimoUrlInput = edit("接口地址").apply {
            setText(credStore.mimoBaseUrl())
            textSize = 13f
        }
        autoSave(mimoUrlInput)
        mimoBox.addView(mimoUrlInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        mimoBox.addView(label("ASR 转写模型"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        mimoAsrInput = edit("ASR 转写模型").apply {
            setText(credStore.mimoAsrModel())
            textSize = 13f
        }
        autoSave(mimoAsrInput)
        mimoBox.addView(mimoAsrInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        mimoBox.addView(label("LLM 判断模型"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        mimoModelInput = edit("LLM 判断模型").apply {
            setText(credStore.mimoModel())
            textSize = 13f
        }
        autoSave(mimoModelInput)
        mimoBox.addView(mimoModelInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        voiceBlock.addView(mimoBox)
        root.addView(voiceBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 课表块 ==========
        val scheduleBlock = block("课表")
        scheduleStatusText = TextView(this).apply {
            textSize = 12f
            setTextColor(SUB)
        }
        scheduleBlock.addView(scheduleStatusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })
        val importRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val importBtn = button("导入课表（.ICS）", LIGHT, ACCENT, big = true)
        importBtn.setOnClickListener { pickIcsFile() }
        importRow.addView(importBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        importRow.addView(TextView(this).apply {
            text = " ⓘ"
            textSize = 16f
            setTextColor(ACCENT)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(0), dp(0), dp(0))
            setOnClickListener {
                android.app.AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("导入课表")
                    .setMessage("请到杭电助手 - 课表 - 订阅课表，复制链接到浏览器下载课表 ICS 文件")
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        scheduleBlock.addView(importRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })
        refreshScheduleStatus()
        root.addView(scheduleBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 界面风格块 ==========
        val styleBlock = block("界面风格")
        styleBlock.addView(label("选择界面风格"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })
        val styleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val styleBtns = mutableListOf<Button>()
        val curStyle = themeStyle()
        for (s in ThemePalette.STYLES) {
            val sel = s == curStyle
            val b = button(
                ThemePalette.styleLabel(s),
                if (sel) ACCENT else LIGHT,
                if (sel) 0xFFFFFFFF.toInt() else TEXT,
                radius = 10
            )
            b.setOnClickListener {
                getSharedPreferences("settings", MODE_PRIVATE).edit()
                    .putString(ThemePalette.KEY_STYLE, s).commit()
                // 桌面图标跟随主题动态切换（activity-alias）
                ThemeIcon.apply(this, s)
                // 刷新本页高亮
                for (bb in styleBtns) {
                    val sel2 = ThemePalette.styleLabel(bb.text.toString()) == ThemePalette.styleLabel(s)
                    bb.background = roundedBg(if (sel2) ACCENT else LIGHT, 10)
                    bb.setTextColor(if (sel2) 0xFFFFFFFF.toInt() else TEXT)
                }
                toast("已切换，返回主界面后生效")
            }
            styleBtns.add(b)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            if (styleBtns.size > 1) lp.leftMargin = dp(6)
            styleRow.addView(b, lp)
        }
        styleBlock.addView(styleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        // 隐藏首页底部状态显示：默认关（默认显示状态栏），开启后主界面底部状态文字隐藏
        val hideStatusCheck = CheckBox(this).apply {
            text = "隐藏首页底部状态显示"
            textSize = 13f
            setTextColor(SUB)
            isChecked = credStore.hideStatusBar()
            setOnCheckedChangeListener { _, checked ->
                credStore.setHideStatusBar(checked)
                toast("已保存")
            }
        }
        styleBlock.addView(hideStatusCheck, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        root.addView(styleBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 操作块 ==========
        val actionBlock = block("操作")
        // "保存并登录"已与右上角"保存"合并（保存后返回主界面即自动登录），操作块只保留清除账号
        val clearBtn = button("清除账号", LIGHT, 0xFFFA5151.toInt(), big = true)
        clearBtn.setOnClickListener {
            credStore.clear()
            userInput.setText("")
            passInput.setText("")
            toast("账号已清除")
        }
        actionBlock.addView(clearBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })
        root.addView(actionBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 调试块 ==========
        val debugBlock = block("调试")
        val debugCheck = CheckBox(this).apply {
            text = "调试日志"
            textSize = 13f
            setTextColor(SUB)
            isChecked = credStore.debugEnabled()
            setOnCheckedChangeListener { _, checked ->
                credStore.setDebugEnabled(checked)
                logText.visibility = if (checked) View.VISIBLE else View.GONE
            }
        }
        debugBlock.addView(debugCheck)
        logText = TextView(this).apply {
            textSize = 10f
            setTextIsSelectable(true)
            setTextColor(SUB)
            visibility = if (credStore.debugEnabled()) View.VISIBLE else View.GONE
            background = roundedBg(LIGHT, 10)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        debugBlock.addView(logText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(160)
        ).apply { topMargin = dp(4) })

        // 导出调试记录：日志全文 + 全部页面抓取详情（作为覆盖界面设计依据）
        val exportBtn = button("导出调试记录", LIGHT, ACCENT)
        exportBtn.setOnClickListener { exportDebug() }
        debugBlock.addView(exportBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        // 弹窗测试：演示各类结果浮层（签到成功/签到码无效/等待转圈/大数字+彩带/Toast），便于核对样式与层级
        val testBtn = button("弹窗测试", LIGHT, ACCENT)
        testBtn.setOnClickListener {
            val items = arrayOf("签到成功覆盖页", "签到码无效弹窗", "结果等待转圈", "大数字 + 彩带", "Toast 提示")
            AlertDialog.Builder(this)
                .setTitle("弹窗测试（返回主界面后触发）")
                .setItems(items) { _, which ->
                    val type = when (which) {
                        0 -> "success"
                        1 -> "invalid"
                        2 -> "waiting"
                        3 -> "bigcode"
                        else -> "toast"
                    }
                    startActivity(Intent(this, MainActivity::class.java)
                        .putExtra("test_overlay", type)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                .show()
        }
        debugBlock.addView(testBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        root.addView(debugBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(scroll)

        // 日志定时刷新（1 秒）
        handler.post(object : Runnable {
            override fun run() {
                if (logText.visibility == View.VISIBLE) {
                    logText.text = DebugLog.dump()
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    /** 自动保存防抖：输入/开关改动 500ms 后统一落盘（账号密码加密保存，避免逐字符触发 Keystore） */
    private val credSaveRunnable = Runnable {
        val u = userInput.text.toString().trim()
        val p = passInput.text.toString()
        if (u.isNotEmpty() && p.isNotEmpty()) {
            credStore.save(u, p, autoLoginCheck.isChecked)
        }
        credStore.setMimoApiKey(mimoKeyInput.text.toString().trim())
        credStore.setMimoBaseUrl(mimoUrlInput.text.toString().trim().ifEmpty { "https://api.xiaomimimo.com/v1" })
        credStore.setMimoAsrModel(mimoAsrInput.text.toString().trim())
        credStore.setMimoModel(mimoModelInput.text.toString().trim())
        credStore.setAutoListen(autoListenCheck.isChecked)
        credStore.setShowSpeechInfo(showSpeechInfoCheck.isChecked)
    }

    private fun autoSaveAll() {
        handler.removeCallbacks(credSaveRunnable)
        handler.postDelayed(credSaveRunnable, 500)
    }

    /** 给输入框挂自动保存（改动即存，无需点保存按钮） */
    private fun autoSave(view: android.widget.EditText) {
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
            toast("学号和密码不能为空")
            return false
        }
        credStore.save(u, p, autoLoginCheck.isChecked)
        credStore.setMimoApiKey(mimoKeyInput.text.toString().trim())
        credStore.setMimoBaseUrl(mimoUrlInput.text.toString().trim().ifEmpty { "https://api.xiaomimimo.com/v1" })
        credStore.setMimoAsrModel(mimoAsrInput.text.toString().trim())
        credStore.setMimoModel(mimoModelInput.text.toString().trim())
        credStore.setAutoListen(autoListenCheck.isChecked)
        credStore.setShowSpeechInfo(showSpeechInfoCheck.isChecked)
        toast("设置已保存")
        if (close) finish()
        return true
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    /** 导出调试记录：日志全文 + 页面抓取详情，保存到本机并调起分享（发到微信/备忘录均可） */
    private fun exportDebug() {
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
                if (android.os.Build.VERSION.SDK_INT >= 29) {
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
            toast(if (savedPath.isNotEmpty()) "已保存：$savedPath" else "已调起分享")
        } catch (e: Exception) {
            toast("导出失败：${e.message}")
        }
    }

    /** 首次引导模式下拦截系统返回键：只能通过右上角「保存」离开设置页 */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (firstRun) {
            toast("请点击右上角「保存」完成设置")
        } else {
            super.onBackPressed()
        }
    }
}
