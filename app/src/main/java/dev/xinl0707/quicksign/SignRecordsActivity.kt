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
    private var listWrap: LinearLayout? = null      // 记录卡片容器（删除后即时移除卡片）
    private var countBadge: TextView? = null       // 顶栏记录计数（删除后即时刷新）

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
        val countBadgeLocal = TextView(this).apply {
            text = "共 ${recordStore.count()} 条"
            textSize = 12f
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
            background = roundedBg(LIGHT, 100)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        countBadge = countBadgeLocal
        titleRow.addView(countBadgeLocal, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 列表区（可滚动） ==========
        val listWrapLocal = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        listWrap = listWrapLocal
        val records = recordStore.list(limit = SignRecord.STATUS_MAX)
        if (records.isEmpty()) {
            showEmpty()
        } else {
            for (rec in records) {
                listWrapLocal.addView(recordCard(rec), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) })
            }
        }
        val scroll = ScrollView(this).apply {
            addView(listWrapLocal, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        setContentView(root)
    }

    /** 单条记录卡片：
     *  行 1 = 标签行：状态徽标 + 课程名（中间、大号）+ 签到方式标签（右侧、放大）；
     *  行 2 = 课程时间（文案「课程时间：MM月dd日 HH:mm」）；
     *  行 3 = 签到时间 + 签到码（统一格式、卡片间对齐）+ 删除按钮；
     *  行 4 = 考勤统计（成功且有数据时显示） */
    private fun recordCard(rec: SignRecord): LinearLayout {
        val timeFmt = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
        val hmFmt = SimpleDateFormat("HH:mm", Locale.CHINA)

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

        // 行 1：状态徽标 + 课程名（中间，大号） + 签到方式标签（右侧，放大）
        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = TextView(this).apply {
            text = badgeText
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = roundedBg(badgeColor, 100)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        row1.addView(badge, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        // 课程名：徽标与方式标签之间，大号加粗、超长截断
        row1.addView(TextView(this).apply {
            text = rec.courseName.ifEmpty { "课程名未知" }
            textSize = if (rec.courseName.isEmpty()) 14f else 18f
            setTextColor(if (rec.courseName.isEmpty()) SUB else TEXT)
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(
                if (rec.courseName.isEmpty()) android.graphics.Typeface.DEFAULT
                else android.graphics.Typeface.DEFAULT_BOLD
            )
        }, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = dp(10); marginEnd = dp(10) })
        val methodTag = TextView(this).apply {
            text = if (rec.method == SignRecord.METHOD_VOICE) "语音签到" else "键盘签到"
            textSize = 15f
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
            background = roundedBg(LIGHT, 100)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        row1.addView(methodTag, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)
        ).apply { marginStart = dp(4) })
        card.addView(row1, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // 行 2：课程时间（带月日，文案「课程时间：MM月dd日 HH:mm - HH:mm」）
        val courseTime = if (rec.courseStart > 0 && rec.courseEnd > 0)
            "课程时间：${timeFmt.format(Date(rec.courseStart))} - ${hmFmt.format(Date(rec.courseEnd))}"
        else "课程时间：未知"
        card.addView(subLine(courseTime), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        // 行 3：签到时间（列表隐藏年月日，仅时分秒，避免冗长）+ 签到码（固定宽度两段，跨卡片对齐）+ 删除按钮
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // 固定宽第一段：签到时间（HH:mm:ss，每张卡片同一列起点，为「签到码」对齐打底）
        row3.addView(TextView(this).apply {
            text = "签到时间：${hmFmt.format(Date(rec.tsResult))}"
            textSize = 13f
            setTextColor(SUB)
            includeFontPadding = false
            setSingleLine(true)
        }, LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT))
        // 固定宽第二段：签到码（所有卡片同一列，标签与值都对齐）
        row3.addView(TextView(this).apply {
            text = "签到码：${rec.code.ifEmpty { "—" }}"
            textSize = 13f
            setTextColor(SUB)
            includeFontPadding = false
            setSingleLine(true)
        }, LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(6) })
        // 弹性占位：把删除按钮推到最右，且不挤动前两段固定列
        row3.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val delBtn = TextView(this).apply {
            text = "删除"
            textSize = 12f
            setTextColor(0xFFE5484D.toInt())
            gravity = Gravity.CENTER
            background = roundedBg(LIGHT, 100)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            isClickable = true
            setOnClickListener { confirmDelete(rec, card) }
        }
        row3.addView(delBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })
        card.addView(row3, LinearLayout.LayoutParams(
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

    /** 删除确认：系统弹窗确认后删除记录（含截图），即时移除卡片并刷新计数 */
    private fun confirmDelete(rec: SignRecord, card: View) {
        val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
        android.app.AlertDialog.Builder(this)
            .setTitle("删除这条签到记录？")
            .setMessage(
                "课程：${rec.courseName.ifEmpty { "课程名未知" }}\n" +
                    "时间：${timeFmt.format(Date(rec.tsResult))}\n" +
                    "码：${rec.code.ifEmpty { "—" }}\n\n删除后不可恢复。"
            )
            .setPositiveButton("删除") { _, _ ->
                val ok = recordStore.deleteRecord(rec.id)
                if (ok) {
                    listWrap?.removeView(card)
                    countBadge?.text = "共 ${recordStore.count()} 条"
                    if (recordStore.count() == 0) showEmpty()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 空态引导（列表无记录时展示；删除至空时也会调用） */
    private fun showEmpty() {
        val empty = TextView(this).apply {
            text = "暂无签到记录\n\n完成一次签到（语音或手动）后，会自动记录到这里"
            textSize = 14f
            setTextColor(SUB)
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        listWrap?.addView(empty, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(120) })
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
