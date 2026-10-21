package dev.xinl0707.quicksign

import android.app.Activity
import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 签到记录详情页（v3.2.0）。
 * 展示单条记录的完整信息：状态横幅 / 课程信息 / 签到信息 / 考勤统计 / 网页截图（点按全屏放大）。
 * 截图缺失时降级显示「文字快照」提示（离屏截图失败的机型兼容）。
 */
class SignRecordDetailActivity : Activity() {

    private lateinit var recordStore: SignRecordStore

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

    private fun roundedBg(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordStore = SignRecordStore(this)
        try {
            window.statusBarColor = BG
            window.navigationBarColor = BG
            window.decorView.systemUiVisibility =
                if (!isNight) window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                else window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        } catch (e: Exception) {}
        val id = intent.getLongExtra("record_id", 0)
        val rec = recordStore.get(id)
        if (rec == null) {
            setContentView(TextView(this).apply {
                text = "记录不存在或已清理"
                textSize = 15f
                setTextColor(SUB)
                gravity = Gravity.CENTER
            })
            return
        }
        buildUi(rec)
    }

    private fun buildUi(rec: SignRecord) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(16), dp(16), dp(16), dp(10))
        }

        // ========== 顶栏 ==========
        val titleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val backBtn = button("返回", LIGHT, ACCENT)
        backBtn.setOnClickListener { finish() }
        titleRow.addView(backBtn, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        val title = TextView(this).apply {
            text = "记录详情"
            textSize = 20f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val idBadge = TextView(this).apply {
            text = "#${rec.id}"
            textSize = 12f
            setTextColor(SUB)
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(idBadge, LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(titleRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 状态横幅 ==========
        val (statusColor, statusText, statusHint) = when (rec.status) {
            SignRecord.STATUS_SUCCESS -> Triple(ACCENT, "签到成功", "网页已确认本次签到完成")
            SignRecord.STATUS_INVALID -> Triple(0xFFE5484D.toInt(), "签到码无效", "提交后未跳转结果页，可能为码错误或网络超时")
            else -> Triple(0xFFE59F0B.toInt(), "自动输入失败", "自动填入未获网页确认，请手动输入")
        }
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBg(LIGHT, 18)
            setPadding(dp(14), dp(16), dp(14), dp(16))
        }
        banner.addView(TextView(this).apply {
            text = statusText
            textSize = 22f
            setTextColor(statusColor)
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        banner.addView(TextView(this).apply {
            text = statusHint
            textSize = 12f
            setTextColor(SUB)
            gravity = Gravity.CENTER
            setPadding(dp(0), dp(4), dp(0), dp(0))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(banner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ========== 课程信息卡 ==========
        val dtFmt = SimpleDateFormat("MM-dd EEE", Locale.CHINA)
        val hmFmt = SimpleDateFormat("HH:mm", Locale.CHINA)
        val secFmt = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
        val courseBlock = block("课程信息")
        val courseTime = if (rec.courseStart > 0 && rec.courseEnd > 0)
            "${dtFmt.format(Date(rec.courseStart))}  ${hmFmt.format(Date(rec.courseStart))} - ${hmFmt.format(Date(rec.courseEnd))}"
        else "上课时间未知"
        courseBlock.addView(TextView(this).apply {
            text = rec.courseName.ifEmpty { "——（页面未提供课程名）" }
            textSize = 18f
            setTextColor(TEXT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        courseBlock.addView(subLine(courseTime), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })
        content.addView(courseBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 签到信息卡 ==========
        val signBlock = block("签到信息")
        val methodText = if (rec.method == SignRecord.METHOD_VOICE) "语音提取" else "手动键盘"
        val submitText = if (rec.tsSubmit > 0) secFmt.format(Date(rec.tsSubmit)) else "—"
        val resultText = if (rec.tsResult > 0) secFmt.format(Date(rec.tsResult)) else "—"
        val rows = listOf(
            "签到码" to rec.code.ifEmpty { "—" },
            "签到方式" to methodText,
            "提交时间" to submitText,
            "结果时间" to resultText,
            "App 版本" to rec.appVersion.ifEmpty { "—" }
        )
        for ((k, v) in rows) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.addView(subLine(k), LinearLayout.LayoutParams(
                dp(76), ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            r.addView(TextView(this).apply {
                text = v
                textSize = 14f
                setTextColor(TEXT)
            }, LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ))
            signBlock.addView(r, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
        }
        content.addView(signBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ========== 考勤统计卡（成功时） ==========
        if (rec.status == SignRecord.STATUS_SUCCESS) {
            val statBlock = block("考勤统计")
            if (rec.statsText.isNotBlank()) {
                statBlock.addView(TextView(this).apply {
                    text = rec.statsText
                    textSize = 14f
                    setTextColor(TEXT)
                    setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ))
            }
            if (rec.hoursText.isNotBlank()) {
                statBlock.addView(subLine(rec.hoursText), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) })
            }
            if (rec.ratioText.isNotBlank()) {
                statBlock.addView(subLine("出勤比例 ${rec.ratioText}"), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2) })
            }
            content.addView(statBlock, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) })
        }

        // ========== 网页截图卡 ==========
        val shotBlock = block("签到网页截图")
        val imgView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
        }
        val shotFile = rec.shotPath.takeIf { it.isNotEmpty() }?.let { recordStore.shotFile(it) }
        if (shotFile != null && shotFile.exists()) {
            try {
                val bmp = BitmapFactory.decodeFile(shotFile.absolutePath)
                if (bmp != null) {
                    imgView.setImageBitmap(bmp)
                    imgView.setOnClickListener { showFullImage(shotFile.absolutePath) }
                    shotBlock.addView(imgView, LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(320)
                    ).apply { topMargin = dp(8) })
                } else {
                    shotBlock.addView(shotPlaceholder(), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(140)
                    ).apply { topMargin = dp(8) })
                }
            } catch (e: Exception) {
                shotBlock.addView(shotPlaceholder(), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(140)
                ).apply { topMargin = dp(8) })
            }
        } else {
            shotBlock.addView(shotPlaceholder(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140)
            ).apply { topMargin = dp(8) })
        }
        content.addView(shotBlock, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        val scroll = ScrollView(this).apply {
            addView(content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // 底部说明
        root.addView(TextView(this).apply {
            text = "记录仅保存在本机，卸载 App 后清除"
            textSize = 11f
            setTextColor(SUB)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        setContentView(root)
    }

    /** 截图缺失占位：说明降级为文字快照 */
    private fun shotPlaceholder(): TextView = TextView(this).apply {
        text = "截图生成失败（该机型不支持离屏截图）\n以上文字信息为完整快照"
        textSize = 13f
        setTextColor(SUB)
        gravity = Gravity.CENTER
        background = roundedBg(LIGHT, 14)
    }

    /** 全屏放大查看截图（点击任意处关闭） */
    private fun showFullImage(path: String) {
        try {
            val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            val img = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(BitmapFactory.decodeFile(path))
            }
            img.setOnClickListener { d.dismiss() }
            d.setContentView(img)
            d.show()
        } catch (e: Exception) {}
    }

    /** 圆角分块卡片（与设置页一致：标题行带主题色条） */
    private fun block(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = roundedBg(CARD, 16)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        val head = LinearLayout(this@SignRecordDetailActivity).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(View(this@SignRecordDetailActivity).apply {
            background = roundedBg(ACCENT, 3)
        }, LinearLayout.LayoutParams(dp(4), dp(14)))
        head.addView(TextView(this@SignRecordDetailActivity).apply {
            text = title
            textSize = 14f
            setTextColor(TEXT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(head, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
    }

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
