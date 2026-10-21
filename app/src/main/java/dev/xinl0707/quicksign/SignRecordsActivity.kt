package dev.xinl0707.quicksign

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 签到记录列表页（v3.2.0）。
 * 风格与设置页统一：BG 底 + 圆角卡片块 + 顶栏（返回/标题）+ 底部固定数据边界说明。
 * 按提交时间倒序展示本机签到记录（仅本 APP 产生，不含网页端/其他设备）。
 */
class SignRecordsActivity : Activity() {

    private lateinit var credStore: CredentialStore
    private lateinit var recordStore: SignRecordStore

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

    private fun dp(v: Number): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    /** 状态栏高度（edge-to-edge 下内容需避让，targetSdk 37 强制） */
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
    }

    private fun roundedBg(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credStore = CredentialStore(this)
        recordStore = SignRecordStore(this)
        try {
            window.statusBarColor = BG
            window.navigationBarColor = BG
            window.decorView.systemUiVisibility =
                if (!isNight) window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                else window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } catch (e: Exception) {}
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(16), dp(16) + statusBarHeight(), dp(16), dp(10))
        }

        // ========== 顶栏：返回 + 标题 + 记录数 ==========
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val backBtn = button("返回", LIGHT, ACCENT)
        backBtn.setOnClickListener { finish() }
        titleRow.addView(backBtn, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        val title = TextView(this).apply {
            text = "签到记录"
            textSize = 20f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val countBadge = TextView(this).apply {
            text = "共 ${recordStore.count()} 条"
            textSize = 12f
            setTextColor(SUB)
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(countBadge, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 列表区（可滚动） ==========
        val listWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val records = recordStore.list(limit = SignRecord.STATUS_MAX)
        if (records.isEmpty()) {
            // 空态引导
            val empty = TextView(this).apply {
                text = "暂无签到记录\n\n完成一次签到（语音或手动）后，会自动记录到这里"
                textSize = 14f
                setTextColor(SUB)
                gravity = Gravity.CENTER
                setLineSpacing(dp(4).toFloat(), 1f)
            }
            listWrap.addView(empty, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(120) })
        } else {
            for (rec in records) {
                listWrap.addView(recordCard(rec), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) })
            }
        }
        val scroll = ScrollView(this).apply {
            addView(listWrap, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // ========== 底部固定数据边界说明（不随列表滚动） ==========
        root.addView(TextView(this).apply {
            text = "仅记录使用本 APP 完成的签到，不含网页端及其他设备操作；记录仅保存在本机"
            textSize = 11f
            setTextColor(SUB)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        setContentView(root)
    }

    /** 单条记录卡片：状态徽标 + 课程名 + 上课时间 + 签到时间/码/方式 + 考勤统计 */
    private fun recordCard(rec: SignRecord): LinearLayout {
        val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        val hmFmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)

        // 状态徽标：颜色与文案按状态区分（成功=主题色 / 码无效=红 / 输入失败=橙）
        val (badgeColor, badgeText) = when (rec.status) {
            SignRecord.STATUS_SUCCESS -> ACCENT to "签到成功"
            SignRecord.STATUS_INVALID -> 0xFFE5484D.toInt() to "码无效"
            else -> 0xFFE59F0B.toInt() to "输入失败"
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(CARD, 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            setOnClickListener {
                startActivity(Intent(this@SignRecordsActivity, SignRecordDetailActivity::class.java)
                    .putExtra("record_id", rec.id))
            }
        }

        // 行 1：徽标 + 课程名
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val badge = TextView(this).apply {
            text = badgeText
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = roundedBg(badgeColor, 100)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        row1.addView(badge, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8) })
        row1.addView(TextView(this).apply {
            text = rec.courseName.ifEmpty { "课程名未知" }
            textSize = if (rec.courseName.isEmpty()) 13f else 16f
            setTextColor(if (rec.courseName.isEmpty()) SUB else TEXT)
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(
                if (rec.courseName.isEmpty()) android.graphics.Typeface.DEFAULT
                else android.graphics.Typeface.DEFAULT_BOLD
            )
        }, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        // 签到方式圆角标签（与状态徽标同风格，放右侧；语音签到 / 键盘签到）
        val methodTag = TextView(this).apply {
            text = if (rec.method == SignRecord.METHOD_VOICE) "语音签到" else "键盘签到"
            textSize = 11f
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
            background = roundedBg(LIGHT, 100)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        row1.addView(methodTag, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })
        card.addView(row1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // 行 2：上课时间（课表匹配；未匹配显示未知）
        val courseTime = if (rec.courseStart > 0 && rec.courseEnd > 0)
            "${timeFmt.format(Date(rec.courseStart))} - ${hmFmt.format(Date(rec.courseEnd)).substring(0, 5)}"
        else "上课时间未知"
        card.addView(subLine("上课：$courseTime"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(5) })

        // 行 3：签到时间 + 码（方式已由行 1 右侧标签展示）
        card.addView(subLine("签到：${hmFmt.format(Date(rec.tsResult))}   码 ${rec.code.ifEmpty { "—" }}"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(3) })

        // 行 4：考勤统计（成功且有数据时显示）
        if (rec.status == SignRecord.STATUS_SUCCESS && rec.statsText.isNotBlank()) {
            card.addView(subLine(rec.statsText), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) })
        }
        return card
    }

    /** 卡片内副文字行（SUB 色，13sp） */
    private fun subLine(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(SUB)
    }

    private fun button(label: String, bg: Int, fg: Int, big: Boolean = false, radius: Int = 14): android.widget.Button {
        val b = android.widget.Button(this)
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
}
