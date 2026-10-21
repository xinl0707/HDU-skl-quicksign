package dev.xinl0707.quicksign

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

/**
 * 上课啦快捷签到 App 主界面。
 *
 * 结构：全屏 WebView（承载签到站点）+ 状态栏 + 底部控制栏（账号设置 / 手动键盘 / 语音签到）
 * 核心能力：
 *  - 账号密码 Keystore 加密保存，自动登录（含验证码时暂停由用户手动完成）
 *  - 登录过期自动重登（轮询检测登录页，自动填表提交）
 *  - 按域名切换 User-Agent：进 SSO 统一认证（sso.hdu.edu.cn）用桌面 UA 显示电脑端登录，
 *    回签到站点恢复移动 UA
 *  - 语音识别 4 位签到码自动输入：MIMO 云端引擎（ASR 转写 + LLM 综合判断）
 *  - 手动键盘全屏弹层兜底（打开时先收起网页自带键盘，不叠加遮挡）
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_PERMS = 1001
        private const val POLL_INTERVAL = 700L
        // 免责声明正文（首次启动引导展示；版本号动态跟随当前版本）
        // 占位符 __VER__ 会被当前版本号替换
        private const val DISCLAIMER_TEXT_TEMPLATE = """
上课啦快捷签到 App 免责声明

生效日期：2026 年 10 月 19 日
适用版本：v1.0 至 v__VER__ 全部历史版本及后续版本

一、非官方声明
本 App（上课啦快捷签到）是由个人开发者独立开发的安卓辅助工具，与杭州电子科技大学及其「上课啦」签到系统（skl.hdu.edu.cn）、数字杭电统一认证系统（cas.hdu.edu.cn / sso.hdu.edu.cn）无任何官方关联，未获得学校任何形式的授权、认可、赞助或合作。学校官方系统的规则、政策、公告、考勤记录等，均以学校正式发布为准，本 App 不构成对任何学校官方文件的解释或替代。

二、使用目的与合规
1. 本 App 仅供使用者本人用于正常的到课签到，目的是辅助使用者本人在规定时间内完成签到操作，不构成对学校签到制度的替代、规避或对抗。
2. 严禁使用本 App 进行代签、替他人签到、伪造签到记录、批量操作或从事任何违反学校规章制度、国家法律法规的行为。使用者须确保本人在场，并自行遵守学校关于到课、签到、课堂管理、考试纪律的全部规定。
3. 因违反学校规定或法律法规使用本 App 而产生的一切后果（包括但不限于考勤作废、通报批评、纪律处分、账号受限、学业影响等），由使用者本人自行承担，开发者不承担任何责任。

三、自动化操作风险
1. 本 App 包含自动登录（自动填写学号密码并提交）、自动识别并输入签到码、自动重登等自动化功能，这些功能基于对学校公开网页的自动化操作实现，存在被学校系统识别为异常行为的可能。
2. 学校系统可能随时改版、更新或调整安全策略（如人机验证、滑块验证、图形验证码、行为风控、频率限制等），可能导致本 App 功能失效、部分失效、误判，或触发学校系统的异常判定、临时限制乃至账号风控措施。由此产生的任何后果，由使用者自行承担。
3. 本 App 不绕过、不破解学校的人机验证、滑块、验证码等防刷设计，相关验证步骤需由使用者本人手动完成；自动登录失败时，使用者应按照页面提示手动完成登录。
4. 自动化操作存在固有的时序与兼容性风险，即使本 App 检测到页面已就绪，学校前端结构变化仍可能导致自动输入错位、重复提交或未提交，使用者应主动核对页面实际状态。

四、签到结果
1. 本 App 不保证签到一定成功。签到是否成功，一律以学校「上课啦」系统的最终记录为准，本 App 的任何提示（包括"签到成功"覆盖页）均仅供参考，不构成签到成功的官方证明。
2. 因网络、设备、定位、系统语音服务、学校系统状态、人机验证或人为操作等原因导致的签到失败、迟到、缺勤、漏签、误签等后果，开发者不承担任何责任。
3. 建议使用者提前开启本 App 并确认登录状态与定位权限，签到后及时在页面确认签到结果，必要时按学校规定手动补签或向任课老师说明情况。

五、账号与数据安全
1. 使用者须自行妥善保管学号、密码等账号信息。本 App 使用设备安全芯片（Keystore）对密码进行加密存储，不会以明文形式写入存储介质，亦不向开发者或任何第三方上传或传输账号密码。
2. 开发者不对因设备丢失、设备被 root/越狱、恶意软件入侵、使用者主动泄露、在不安全设备或公共设备上登录等情形造成的账号信息泄露、被盗用承担责任。
3. 建议使用者不要在他人设备上登录本 App，避免在不可信网络环境中使用，并定期修改密码。账号出现异常时，请及时联系学校信息化部门处理。

六、语音识别与数据处理
1. 语音签到功能可选接小米 MIMO 云端语音识别服务（官网 mimo.mi.com，以下统称"MIMO"）。启用该功能时，本 App 会将录音片段（约每 3 秒一片、每片最长 10 秒的音频）发送至 MIMO 服务进行转写，相关音频数据由 MIMO 服务按其自身规则处理，请在使用前自行阅读该服务的用户协议与隐私政策。
2. 语音识别基于自动语音识别（ASR）与语言模型判断，存在误识别、漏识别、环境噪声干扰、同音字混淆等固有局限。识别结果可能被自动填入并提交签到码，若因误识别导致提交错误的签到码、签到失败或其他后果，由使用者自行承担。
3. 使用语音识别功能需要使用者自行配置 MIMO API Key；该服务按量计费，由此产生的费用由使用者自行承担。开发者不对 MIMO 服务的可用性、准确性、数据安全、计费规则及隐私实践承担责任。
4. 本 App 不会在后台、非签到界面或使用者未授权的情况下持续开启麦克风；离开签到界面或切换至后台时，语音监听会立即停止。

七、定位与位置数据
1. 本 App 会申请定位权限（签到页面校验与定位显示需要），并使用设备定位服务获取经纬度，在本机界面显示（保留一位小数）。
2. 定位结果仅用于本 App 的签到辅助与界面显示，不会上传至开发者服务器；但签到页面自身校验定位时，位置信息会按学校系统既有逻辑处理，使用者应知悉学校系统的相关规则。
3. 定位精度受设备、环境、系统服务影响，存在偏差可能；因定位异常导致的签到失败等后果，开发者不承担责任。

八、课表功能
1. 课表功能通过导入 ICS 格式课表文件实现（可至杭电助手课表订阅获取），导入内容仅保存在本机，用于界面展示"当前课程/下一节课"提醒。
2. 课表数据仅供参考，实际课程安排、上课时间、地点、教师等信息一律以学校教务系统或任课老师通知为准。因课表导入错误、文件格式问题或课程调整导致的显示不准确，开发者不承担责任。

九、第三方服务
1. 本 App 除可选接 MIMO 云端语音识别服务外，还可能依赖系统语音识别引擎、系统定位服务、WebView 内核等操作系统级组件，其行为受设备厂商与系统版本影响。
2. 开发者不对任何第三方服务（包括但不限于 MIMO、系统语音识别引擎、系统定位服务、WebView 组件）的可用性、准确性、数据安全、收费行为及隐私实践承担责任。

十、权限说明
本 App 会申请麦克风权限（用于语音识别签到码）、定位权限（签到校验与定位显示需要）与网络权限（访问签到系统与 MIMO 服务）。上述权限仅用于实现本 App 的核心功能，不用于任何其他用途。拒绝授权可能导致语音签到、自动定位等功能不可用，请在使用前按引导完成授权。

十一、软件质量与维护
1. 本 App 按「现状」（AS IS）提供，开发者不对其可用性、稳定性、准确性、兼容性、安全性作任何明示或暗示的保证，亦不承诺适用于特定用途。
2. 开发者会尽力修复已知问题，但没有义务提供持续更新、维护、技术支持或兼容性适配，并有权在任何时间停止更新、终止服务或下架本 App。
3. 学校前端改版、系统安全策略调整或设备系统升级可能导致自动登录、自动输入、语音识别等功能失效；使用者可通过「设置 - 调试日志 - 导出调试记录」反馈问题，但不保证问题一定得到解决。

十二、知识产权
1. 本 App 的源代码、界面设计、文档、品牌标识等知识产权归开发者所有，未经开发者书面许可，任何个人或组织不得复制、修改、分发、反编译、逆向工程或用于商业用途。
2. 本 App 中涉及的学校名称、系统名称、第三方品牌、商标及服务名称（包括但不限于「上课啦」、数字杭电、MIMO、mimo.mi.com），均归其各自权利人所有，本声明不构成任何授权或关联声明。

十三、法律适用与争议解决
1. 本声明适用中华人民共和国法律。
2. 使用者安装、使用本 App，即视为已阅读、理解并同意本免责声明的全部内容。如本声明部分条款被认定无效或不可执行，不影响其余条款的效力。
3. 因使用本 App 产生的争议，应首先协商解决；协商不成的，提交开发者所在地有管辖权的人民法院处理。

十四、变更与通知
1. 开发者可能随时更新本免责声明，更新后的版本随新版本 App 一同发布，并适用于该版本及之后的使用行为；使用者应定期查看本声明。
2. 本声明适用的版本范围随 App 版本自动更新，最新版本以 App 内展示为准。

请在使用本 App 前仔细阅读本免责声明。若不同意其中任何条款，请立即停止下载、安装和使用。
"""
    }

    // 深色模式跟随系统；配色按「界面风格 × 深浅」从 ThemePalette 取（延迟初始化，属性初始化时机早于系统资源绑定）
    private val isNight: Boolean by lazy {
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    private fun themeStyle(): String =
        getSharedPreferences("settings", MODE_PRIVATE)
            .getString(ThemePalette.KEY_STYLE, ThemePalette.STYLE_DEFAULT) ?: ThemePalette.STYLE_DEFAULT

    // 当前已应用的主题风格（onResume 对比设置值，变化则 recreate）
    private var lastAppliedStyle: String = ThemePalette.STYLE_DEFAULT

    private val palette: ThemePalette.Palette by lazy { ThemePalette.palette(themeStyle(), isNight) }
    private val ACCENT: Int get() = palette.accent
    private val BG: Int get() = palette.bg
    private val CARD: Int get() = palette.card
    private val TEXT: Int get() = palette.text
    private val SUB: Int get() = palette.sub
    private val LIGHT: Int get() = palette.light
    private val KEY_BG: Int get() = palette.keyBg
    private val KEY_TEXT: Int get() = palette.keyText
    private val ACCENT_SOFT: Int get() = palette.accentSoft
    private val INFO_BG: Int get() = palette.infoBg
    private val INFO_LIST_BG: Int get() = palette.infoListBg
    private val DANGER: Int get() = palette.danger
    private val SUCCESS: Int get() = palette.success
    private val HAIRLINE: Int get() = palette.hairline

    private lateinit var webView: WebView
    private var webViewStarted = false   // WebView 是否已开始加载（首次引导流程完成保存后才加载）
    private lateinit var credStore: CredentialStore
    private lateinit var statusText: TextView

    // 顶部栏：返回面板按钮（网页显示时可用）+ 设置入口（右上角圆形按钮）
    private lateinit var backPanelBtn: Button

    // 原生签到面板（默认显示，网页隐藏后台加载；需要时点按钮展示网页）
    private lateinit var signPanel: LinearLayout
    private var signPanelWrap: LinearLayout? = null   // 面板外层容器（隐藏它网页才真正全屏）
    private lateinit var panelCodeDisplay: LinearLayout   // 四位码槽容器（4 个独立槽）
    private lateinit var codeLineViews: MutableList<View>       // 槽内横线（未输入时显示）
    private lateinit var codeDigitViews: MutableList<TextView>  // 槽内数字（输入时弹入）
    private lateinit var toggleWebBtn: Button
    private var signPanelShown = false

    // 当前课程显示（杭电助手订阅课表 ICS 解析）
    private lateinit var courseText: TextView
    private lateinit var courseDetailText: TextView
    private lateinit var clockText: TextView
    private var lastLocReqAt = 0L                 // 原生定位请求节流
    // 面板标题：加载中显示"加载中…"，就绪后显示"签到就绪"
    private lateinit var panelTitleText: TextView
    private var locDisplayText: TextView? = null   // 经纬度显示（时间下方，N/S/E/W + 度数）
    private var statusWrap: LinearLayout? = null   // 底部状态栏容器（设置可隐藏）
    private var scheduleCourses: List<ScheduleParser.Course> = emptyList()
    private var scheduleFetchedAt = 0L
    private var lastScheduleTickAt = 0L

    // 大数字提示浮层（输码成功弹窗，非 toast）
    private lateinit var rootFrame: FrameLayout
    private lateinit var bigOverlay: FrameLayout
    private lateinit var bigCodeText: TextView
    private var bigOverlayShown = false

    // 签到完成覆盖页（输码后 URL 跳 #/sign/in/detail = 签到成功，自动弹出展示考勤统计）
    private var resultOverlay: FrameLayout? = null
    private var resultCourseText: TextView? = null
    private var resultHoursText: TextView? = null
    private var resultRatioText: TextView? = null
    private var resultStatsText: TextView? = null
    private var signResultShown = false     // 覆盖页显示中
    private var lastSubmitAt = 0L           // 最近一次提交签到码时间（结果判定窗口）
    private var resultWatchActive = false   // 输码后结果监听进行中
    private var resultWatchStartAt = 0L     // 结果监听启动时间（5 秒超时判定签到码无效）
    private var resultWaiting = false       // 输码后结果判定中：键盘位显示大大大转圈过渡，键盘/横线隐藏
    // 首次启动引导（免责声明 + 权限 + 进设置），同意一次后不再弹出
    private var guideOverlay: FrameLayout? = null
    private var disclaimerCard: LinearLayout? = null
    private var permCard: LinearLayout? = null
    private var guideNextBtn: Button? = null
    private var guidePermHint: TextView? = null
    private var pendingRecordToast = false   // 引导期间拒绝麦克风：等引导结束后再补弹提示
    private var restartedAfterLogin = false  // 认证成功后已 recreate 一次（避免路由错乱反复重启）
    // 签到码无效弹窗：3 秒未跳 detail 弹出（非 toast），后台继续检测 3 秒缓冲，缓冲内成功则切签到成功
    private var invalidOverlay: FrameLayout? = null
    private var invalidShown = false
    private var invalidShownAt = 0L
    // 内嵌启动层：品牌色开屏，期间后台并行加载网页/探测登录（不纯等待）
    private lateinit var splashLayer: FrameLayout
    private var splashDismissed = false
    // 签到键盘：签到页加载完成（signReady）前隐藏，显示"加载中"；就绪后才展示
    private var panelKeyGrid: GridLayout? = null
    // 加载动画：键盘未就绪（signReady=false）时显示转圈，就绪后隐藏
    private var loadingBar: ProgressBar? = null
    // 定位中加载动画：签到页就绪但定位未拿到时，状态栏显示小转圈
    private var locLoadingBar: ProgressBar? = null

    // 识别信息栏（独立面板，不覆盖网页）：展示 MIMO 识别内容与综合判断
    private lateinit var speechInfoRow: TextView
    private lateinit var speechInfoScroll: ScrollView
    private lateinit var speechInfoList: TextView
    private val speechLog = mutableListOf<String>()
    private var speechLogExpanded = false

    private val handler = Handler(Looper.getMainLooper())
    private var pollRunning = false
    private var filling = false
    private var fillGen = 0
    private var signReady = false
    private var prevSigned = false
    private var signPageFirstSeenAt = 0L
    private var lastSignNavAt = 0L        // 回切签到页路由节流
    private var signNavCount = 0          // 回切签到页连续次数（防站点路由拉回造成无限重载）
    private var lastSignNavResetAt = 0L
    private var lastProbeOkAt = 0L        // 最近一次探测成功时间（登录记忆，防"明明登录了却跳统一认证"误报）
    private var lastLoginPageSeenAt = 0L  // 登录页首次出现时间（超时判定 cookie 是否真失效）
    private var wasOnLoginPage = false    // 页面曾停在登录页（自动登录流程中）；probe ok 时据此决定是否切回面板
    private var loginFlowPending = false  // 登录流程进行中（页面经过登录页）；probe ok 后消费并自动切回签到面板
    private var lastReloadAt = 0L
    private var autoLoginCooldownUntil = 0L
    private var ssoLoadCount = 0          // 连续 SSO 域页面加载计数（防重定向死循环）
    private var autoLoginRetries = 0      // 自动登录提交重试次数（超限转手动）
    private var ssoBlockSeconds = 0       // 连续停在 SSO 域的秒数（pollTick 轮询检测）
    private var ssoBlockedUntil = 0L      // SSO 循环阻断冷却：期间不再自动跳转 SSO
    private var autoLoginSubmitted = false // 登录页已自动提交过一次，避免反复提交干扰验证码
    private var autoLoginSubmittedAt = 0L

    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    // 登录态探测：签到页不触发登录检查，App 原生层直接请求 /api/userinfo，
    // 401 时取后端返回的 SSO 地址跳转（走桌面端登录）；200 视为已登录
    private var loginOk = false
    private var loginProbeCooldownUntil = 0L
    private var probeFailCount = 0

    private var mimoEngine: MiMoSpeechEngine? = null
    private var listening = false
    private var isPaused = false                  // 主界面是否在后台（设置页/课表/多任务等）：后台绝不启动麦克风监听
    private var mimoRestartCount = 0 // 自动监听下瞬时错误自动重启次数
    private var mimoSliceCount = 0  // MIMO 切片序号（识别日志细化）
    private var mimoEpoch = 0L      // 监听代际：每次启动监听 +1，旧代际的回调一律丢弃（防残留识别）
    private var lastAutoListenHintAt = 0L // 自动监听提示去重
    // 两步架构（ASR 转写 + flash 综合判断）：转写累积池（最近 10 片 ≈ 30 秒）+ 判断状态
    private val heardPool = ArrayDeque<String>()
    private var llmExtractBusy = false // flash 综合判断进行中标志，避免并发重复调用
    private var llmExtractFirst = true // 首次综合判断用"首次提示词"，之后用"累积提示词"

    private var manualCode = ""
    private var pendingPanelCode = "" // 网页未就绪时输满的码，就绪后自动补输
    private var lastFilledCode = ""
    private var clearedOnceForPage = false // 每次页面加载后只清一次网页密码框残留（页面加载后才允许执行 JS）

    private var lastToastKey = ""
    private var lastToastAt = 0L

    // 默认移动 UA（SSO 按屏幕尺寸判端，手机视口返回手机版登录页；
    // 手机版已有"用户名密码"选项卡，jsAutoLogin 已适配注入——全部域统一移动 UA）
    private val defaultUA: String by lazy { WebSettings.getDefaultUserAgent(this) }

    // ------------------------------------------------------------------ 生命周期

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 崩溃自上报：任意未捕获异常写入本地，下次启动直接显示，便于定位（无需连电脑）
        Thread.setDefaultUncaughtExceptionHandler { _, t ->
            try {
                getSharedPreferences("crash", MODE_PRIVATE).edit()
                    .putString("last_crash", "$t\n${t.stackTraceToString()}")
                    .commit()
                java.io.File(filesDir, "startup.log").appendText("UNCAUGHT: $t\n${t.stackTraceToString()}\n")
            } catch (e: Exception) {}
        }
        // 上次崩溃过 → 直接显示崩溃内容，不执行正常启动（用户反馈后我来修）
        val lastCrash = getSharedPreferences("crash", MODE_PRIVATE).getString("last_crash", null)
        if (lastCrash != null) {
            trace("showing last crash")
            val crashText = TextView(this).apply {
                text = "上次启动崩溃，请把下面内容发给开发者：\n\n$lastCrash"
                setTextColor(0xFF111111.toInt())
                setTextSize(11f)
                setPadding(24, 24, 24, 24)
                setTextIsSelectable(true)
            }
            val retryBtn = Button(this).apply {
                text = "清除记录并重试"
                setOnClickListener {
                    getSharedPreferences("crash", MODE_PRIVATE).edit().clear().commit()
                    finish()
                    startActivity(Intent(this@MainActivity, MainActivity::class.java))
                }
            }
            val crashView = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                addView(crashText, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                ))
                addView(retryBtn, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            }
            setContentView(crashView)
            return
        }
        trace("onCreate start")
        try {
            credStore = CredentialStore(this)
            lastAppliedStyle = themeStyle()
            // 首帧窗口背景直接跟随主题品牌色（styles.xml 静态色做不到），
            // 在 setContentView 前设置，消除开屏第一帧与 splash 的品牌色差
            try {
                val palette = ThemePalette.palette(lastAppliedStyle, isNight)
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.bg))
            } catch (e: Exception) {}
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            trace("before buildUi")
            buildUi()
            trace("after buildUi")
            // 首次启动：免责声明 + 权限引导（只弹一次；已同意过直接跳过）
            if (!credStore.disclaimerAccepted()) {
                showGuideOverlay()
            }
            setupWebView()
            trace("after setupWebView")
            // 首次引导（未同意免责声明）时不立即请求权限：系统权限弹窗等阅读完免责声明后由引导页激活
            if (credStore.disclaimerAccepted()) {
                requestNeededPermissions()
            }
            // 打开 App 默认显示原生签到面板（网页隐藏后台加载/登录），可立即输入
            showSignPanel()
            trace("after showSignPanel")
            // 首次引导（未同意免责声明）：暂不加载 WebView——等引导进设置页、用户保存后由 onResume 加载，
            // 避免后台加载与免责声明/权限流程互相干扰造成登录时机错乱
            if (!credStore.disclaimerAccepted()) {
                trace("loadUrl deferred: waiting for first-run guide + settings save")
            } else {
                webViewStarted = true
                webView.loadUrl(SignInjector.HOME_URL)
            }
            trace("after loadUrl")
            if (!credStore.hasCredentials()) {
                statusText.text = "请在右上角「设置」中保存学号密码，将自动登录"
            }
        } catch (t: Throwable) {
            // 崩溃兜底：写入文件并直接显示崩溃信息，便于用户反馈定位
            trace("CRASH: $t")
            trace(t.stackTraceToString())
            try {
                setContentView(TextView(this).apply {
                    text = "启动崩溃（请把此内容发给开发者）：\n\n$t\n\n${t.stackTraceToString()}"
                    setTextColor(0xFF111111.toInt())
                    setTextSize(11f)
                    setPadding(24, 24, 24, 24)
                    setTextIsSelectable(true)
                })
            } catch (e: Exception) {}
        }
    }

    /** 启动埋点：逐步写文件，崩溃后可从文件最后一行定位崩在哪一步 */
    private var splashShownAt = 0L
    private var traceStart = 0L
    private fun trace(t: String) {
        try {
            if (traceStart == 0L) traceStart = System.currentTimeMillis()
            val el = System.currentTimeMillis() - traceStart
            val f = java.io.File(filesDir, "startup.log")
            java.io.FileOutputStream(f, true).use { it.write("[$el ms] $t\n".toByteArray()) }
        } catch (e: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        // 从设置页/课表/考勤等界面返回后，立即允许自动登录与登录态探测
        autoLoginCooldownUntil = 0
        loginProbeCooldownUntil = 0
        // 界面风格在设置页被切换时，重建界面让新主题生效（WebView 会自动重跑登录流程）
        val nowStyle = themeStyle()
        DebugLog.append("onResume: style=$nowStyle last=$lastAppliedStyle")
        if (nowStyle != lastAppliedStyle) {
            lastAppliedStyle = nowStyle
            recreate()
            return
        }
        // 回到首页键盘面板时才恢复自动监听（其余界面不监听）
        if (signPanelShown) maybeAutoListen()
        // 底部状态栏可见性按设置开关刷新（设置页切换后返回生效）
        statusWrap?.visibility = if (credStore.hideStatusBar()) View.GONE else View.VISIBLE
        // 首次引导完成后（引导进设置页 → 用户保存/返回主界面）：开始加载 WebView，
        // 至此免责声明与权限流程已结束，避免后台加载的时机问题；
        // 引导层仍可见时（含权限弹窗来回触发的 onResume）绝不允许提前加载
        if (!webViewStarted && credStore.disclaimerAccepted() && guideOverlay?.visibility != View.VISIBLE) {
            webViewStarted = true
            webView.loadUrl(SignInjector.HOME_URL)
        }
        // 从设置页返回：重新读取课表缓存（设置页导入新课表后立即生效，无需重启 App）
        refreshScheduleFromStore()
        // 调试：设置页「弹窗测试」入口触发的各类结果浮层演示（消费后即移除，避免每次 onResume 重放）
        val test = intent.getStringExtra("test_overlay")
        if (test != null) {
            intent.removeExtra("test_overlay")
            runTestOverlay(test)
        }
    }

    /** 调试：弹窗测试入口（设置页「弹窗测试」触发）——演示各类结果浮层，方便核对样式与层级 */
    private fun runTestOverlay(type: String) {
        DebugLog.append("test overlay: $type")
        when (type) {
            "success" -> {
                // 模拟一次真实成功：填好覆盖页文案后弹覆盖页（带彩带），进结果窗口使「返回签到」流程完整
                resultCourseText?.text = "中国近现代史纲要"
                resultHoursText?.text = "旷课学时 0 / 课程总学时 48"
                resultRatioText?.text = "0.00%"
                resultStatsText?.text = "正常 2    请假 0    迟到 0    旷课 0"
                lastSubmitAt = System.currentTimeMillis()
                showSignResultOverlay()
            }
            "invalid" -> {
                // 签到码无效弹窗（快速震动两下 + 或网络超时小字 + 重新输入按钮）
                lastSubmitAt = System.currentTimeMillis()
                showInvalidOverlay()
            }
            "waiting" -> {
                // 结果等待：键盘位大转圈过渡，5 秒后自动收起
                showResultWaiting()
                handler.postDelayed({ hideResultWaiting() }, 5000)
            }
            "bigcode" -> {
                // 输入瞬间大数字 + 彩带，3 秒后自动收起
                showBigCodeOverlay("8888")
                handler.postDelayed({ dismissBigCodeOverlay() }, 3000)
            }
            "toast" -> toast("Toast 提示示例：签到码已提交")
        }
    }

    /** 重新从本地课表缓存解析并刷新显示（设置页导入/清除后返回主界面调用） */
    private fun refreshScheduleFromStore() {
        val cached = credStore.scheduleCacheIcs()
        if (cached.isNullOrBlank()) {
            scheduleCourses = emptyList()
        } else {
            scheduleCourses = parseIcs(cached)
        }
        updateCourseDisplay()
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
        // 离开主界面（设置页/课表/考勤/后台）：立即停止全部监听，任何非键盘界面都不监听
        stopListeningQuiet()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        super.onBackPressed()
    }

    /** singleTask：设置页「弹窗测试」等入口复用本实例时接收新 Intent */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        pollRunning = false
        stopListeningQuiet()
        // 清理全部延迟回调（splash 淡出、轮询链、路由修正延迟等），防泄漏与销毁后回调崩溃
        handler.removeCallbacksAndMessages(null)
        try {
            // WebView 标准释放：先摘回调、清视图、停页面，再 destroy（WebView 不释放会泄漏内存）
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.removeAllViews()
            webView.loadUrl("about:blank")
            webView.destroy()
        } catch (e: Exception) {}
        super.onDestroy()
    }

    // ------------------------------------------------------------------ 界面

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
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (big) 16f else 13f)
        b.background = roundedBg(bg, radius)
        b.setTextColor(fg)
        b.isAllCaps = false
        b.gravity = Gravity.CENTER
        b.minHeight = dp(if (big) 48f else 42f)
        b.setPadding(dp(10), 0, dp(10), 0)
        // 统一按压反馈：按下轻微缩小、松开回弹（不消费事件，不影响点击）
        b.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
            }
            false
        }
        return b
    }

    private fun buildUi() {
        trace("buildUi start")
        // 最外层用 FrameLayout：主内容 + 大数字浮层（浮层在后，天然覆盖全屏）
        val rootFrame = FrameLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            setBackgroundColor(BG)
        }
        this.rootFrame = rootFrame
        // 启动层立即上屏（首帧即品牌色开屏），主 UI 继续在后台构建、WebView 随后加载
        buildSplashLayer()
        // 状态栏/导航栏配色跟随当前主题风格（edge-to-edge 下内容已避开系统栏）
        applySystemBars()

        // 顶部栏：标题 + 返回面板（网页显示时可见）+ 设置入口（右上角圆形按钮）
        backPanelBtn = button("返回", LIGHT, ACCENT)
        backPanelBtn.visibility = View.GONE
        backPanelBtn.setOnClickListener { showSignPanel() }
        val settingsBtn = TextView(this).apply {
            text = "设"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = roundedBg(ACCENT, 17)
            isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        }
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(14), dp(6))
        }
        titleBar.addView(TextView(this).apply {
            text = "上课啦"
            textSize = 22f
            setTextColor(TEXT)
            letterSpacing = 0.08f
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        titleBar.addView(backPanelBtn, LinearLayout.LayoutParams(dp(88), dp(38)).apply { rightMargin = dp(8) })
        titleBar.addView(settingsBtn, LinearLayout.LayoutParams(dp(34), dp(34)))
        root.addView(titleBar)
        // 标题栏下 1dp 主题色分隔线（页面结构更明确）
        root.addView(View(this).apply { background = roundedBg(HAIRLINE, 1) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1))
        trace("buildUi titleBar ok")

        // 原生签到面板：默认显示（网页隐藏后台加载），内置数字键盘直接输码；
        // 输满 4 位自动注入并切回网页显示结果；需要看网页时点「查看网页」
        signPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(CARD, 22)
            visibility = View.VISIBLE
        }
        panelTitleText = TextView(this).apply {
            text = "加载中"
            textSize = 24f
            setTextColor(SUB)
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(0), dp(22), dp(0), dp(4))
        }
        signPanel.addView(panelTitleText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 当前时间（精确到秒，每秒刷新）：主强调色加粗，突出"当下时刻"的签到语境
        clockText = TextView(this).apply {
            textSize = 17f
            setTextColor(ACCENT)
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(0), dp(2), dp(0), dp(2))
        }
        signPanel.addView(clockText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 经纬度显示（时间下方，N/S/E/W + 度数，一位小数）
        locDisplayText = TextView(this).apply {
            text = "定位中"
            textSize = 12f
            setTextColor(SUB)
            gravity = Gravity.CENTER
            setPadding(dp(0), dp(0), dp(0), dp(6))
        }
        signPanel.addView(locDisplayText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 当前课程卡片（依据杭电助手订阅课表动态显示；下课后 5 分钟内仍显示该课，防下课点名）
        courseText = TextView(this).apply {
            text = "当前无课"
            textSize = 19f
            setTextColor(TEXT)
            gravity = Gravity.CENTER
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(0), dp(8), dp(0), dp(2))
        }
        signPanel.addView(courseText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        courseDetailText = TextView(this).apply {
            text = ""
            textSize = 13f
            setTextColor(SUB)
            gravity = Gravity.CENTER
            setPadding(dp(0), dp(0), dp(0), dp(10))
        }
        signPanel.addView(courseDetailText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 四位码槽区：4 个独立槽（横线 + 数字），输码时数字逐个弹入、删除弹走——
        // 签到台的视觉焦点，数字用强调色，横线用正文色（未输入时显示）
        panelCodeDisplay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE   // 签到界面加载完成（signReady）前隐藏，配合键盘区域的加载动画
        }
        codeLineViews = mutableListOf()
        codeDigitViews = mutableListOf()
        for (i in 0 until 4) {
            val slot = FrameLayout(this)
            val line = View(this).apply { background = roundedBg(TEXT, 3) }
            slot.addView(line, FrameLayout.LayoutParams(dp(44), dp(4), Gravity.CENTER))
            val digit = TextView(this).apply {
                textSize = 42f
                setTextColor(ACCENT)
                gravity = Gravity.CENTER
                includeFontPadding = false   // 去掉字体上下内边距，数字与槽更贴近、视觉更居中
                setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                visibility = View.INVISIBLE
            }
            slot.addView(digit, FrameLayout.LayoutParams(dp(52), dp(56), Gravity.CENTER))
            codeLineViews.add(line)
            codeDigitViews.add(digit)
            panelCodeDisplay.addView(slot, LinearLayout.LayoutParams(dp(58), dp(58)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
        signPanel.addView(panelCodeDisplay, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // 键盘容器：加载动画与键盘同位置——签到页未加载完成（signReady=false）时在键盘位置显示转圈动画；
        // 定位等待时也用同一位置显示"定位中"转圈
        val keyWrap = FrameLayout(this)
        loadingBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
        }
        keyWrap.addView(loadingBar, FrameLayout.LayoutParams(
            dp(100), dp(100), Gravity.CENTER
        ))
        locLoadingBar = ProgressBar(this).apply {
            visibility = View.GONE
        }
        keyWrap.addView(locLoadingBar, FrameLayout.LayoutParams(
            dp(100), dp(100), Gravity.CENTER
        ))
        panelKeyGrid = GridLayout(this).apply {
            columnCount = 3
            rowCount = 4
            setPadding(dp(16), dp(6), dp(16), dp(6))
            // 签到界面加载完成（signReady）前不显示键盘：避免未就绪时误输码/误触发
            visibility = View.GONE
        }
        // 数字键盘：清除 + 0 + 退格（0 居中，退格/清除对调：清除在左、退格在右），输满 4 位再注入
        val panelDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "clear", "0", "del")
        for (d in panelDigits) {
            val isDigit = d.length == 1 && d[0].isDigit()
            val b = button(
                when (d) {
                    "del" -> "退格"
                    "clear" -> "清除"
                    else -> d
                },
                if (d == "del" || d == "clear") LIGHT else KEY_BG,
                if (isDigit) KEY_TEXT else TEXT, big = true, radius = 22
            )
            // 数字键大号加粗，退格/清除克制：签到台的主操作是数字
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (isDigit) 28f else 16f)
            b.setTypeface(if (isDigit) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT)
            b.setOnClickListener {
                vibrate(25)   // 输入数字时清脆短震
                onPanelKey(d)
            }
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.height = dp(62)
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f)
            lp.setMargins(dp(5), dp(4), dp(5), dp(4))
            panelKeyGrid!!.addView(b, lp)
        }
        keyWrap.addView(panelKeyGrid, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        signPanel.addView(keyWrap, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        toggleWebBtn = button("查看网页", LIGHT, ACCENT, big = true)
        toggleWebBtn.setOnClickListener { toggleWebView() }
        signPanel.addView(toggleWebBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(6); bottomMargin = dp(8) })

        val signPanelWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(0), dp(12), dp(10))
            addView(signPanel, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        this.signPanelWrap = signPanelWrap
        root.addView(signPanelWrap, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        trace("buildUi signPanel ok")

        // WebView（默认隐藏，后台加载；登录/查看结果时显示）
        webView = WebView(this)
        webView.visibility = View.GONE
        root.addView(webView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        trace("buildUi webView ok")

        // 状态栏（圆角卡片）：签到页未就绪时加载动画显示在键盘位置，这里只显示状态文字
        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(SUB)
            text = "签到界面加载中…"
        }
        val statusBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = roundedBg(CARD, 16)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            addView(statusText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val statusWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(0), dp(12), dp(4))
            addView(statusBar)
            // 隐藏首页底部状态显示（设置里可开关，默认不隐藏）
            visibility = if (credStore.hideStatusBar()) View.GONE else View.VISIBLE
        }
        this.statusWrap = statusWrap
        root.addView(statusWrap)
        trace("buildUi statusBar ok")

        // 识别信息栏：一行实时显示最新识别内容（MIMO/综合），点按展开全部记录；不遮挡网页。
        // 默认隐藏（设置里可开启"显示主页识别窗口"）
        val showSpeechInfo = credStore.showSpeechInfo()
        speechInfoRow = TextView(this).apply {
            textSize = 11f
            setTextColor(ACCENT)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = roundedBg(INFO_BG, 14)
            text = "识别内容：暂无 · 点按展开"
        }
        val infoWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(0), dp(10), dp(6))
            visibility = if (showSpeechInfo) View.VISIBLE else View.GONE
            addView(speechInfoRow)
        }
        root.addView(infoWrap)
        speechInfoList = TextView(this).apply {
            textSize = 11f
            setTextColor(SUB)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setTextIsSelectable(true)
            background = roundedBg(INFO_LIST_BG, 10)
        }
        speechInfoScroll = ScrollView(this).apply {
            visibility = View.GONE
            addView(speechInfoList, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val infoScrollWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(0), dp(10), dp(6))
            visibility = if (showSpeechInfo) View.VISIBLE else View.GONE
            addView(speechInfoScroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(140)
            ))
        }
        root.addView(infoScrollWrap)
        speechInfoRow.setOnClickListener {
            speechLogExpanded = !speechLogExpanded
            speechInfoScroll.visibility = if (speechLogExpanded) View.VISIBLE else View.GONE
            updateSpeechInfoRow()
        }
        trace("buildUi infoBar ok")

        // 大数字提示浮层：输码成功（手动/语音）时全屏弹出大号数字，动画切网页（非 toast）
        bigOverlay = FrameLayout(this)
        bigOverlay.visibility = View.GONE
        bigOverlay.setBackgroundColor(0x66000000.toInt())   // 半透明遮罩
        val bigCard = LinearLayout(this)
        bigCard.orientation = LinearLayout.VERTICAL
        bigCard.gravity = Gravity.CENTER
        bigCard.background = roundedBg(CARD, 30)
        bigCard.setPadding(dp(48), dp(34), dp(48), dp(34))
        bigCodeText = TextView(this)
        // 固定大字号 + 强制单行：80sp 下 4 位数字一行放下，清晰醒目
        bigCodeText.textSize = 80f
        bigCodeText.maxLines = 1
        bigCodeText.setTextColor(ACCENT)
        bigCodeText.gravity = Gravity.CENTER
        bigCodeText.letterSpacing = 0.1f
        bigCodeText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        bigCard.addView(bigCodeText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val hint = TextView(this)
        hint.text = "签到码已提交"
        hint.textSize = 15f
        hint.setTextColor(SUB)
        hint.gravity = Gravity.CENTER
        hint.setPadding(dp(0), dp(16), dp(0), dp(0))
        bigCard.addView(hint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        bigOverlay.addView(bigCard, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        // 浮层加入最外层 FrameLayout（在主内容之上，全屏覆盖）
        rootFrame.addView(root, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        rootFrame.addView(bigOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        trace("buildUi bigOverlay ok")

        // 签到完成覆盖页：输码后 URL 跳 #/sign/in/detail（签到成功）自动弹出，
        // 全屏展示考勤统计（数据从网页正文解析），用户点"返回签到"关闭后回签到页继续签下一门
        resultOverlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0x88000000.toInt())   // 半透明遮罩
        }
        val resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedBg(CARD, 30)
            setPadding(dp(30), dp(30), dp(30), dp(26))
        }
        // 成功徽章：反色印章——外层 SUCCESS 大圆环 + 内圈白圆 + SUCCESS 对勾（真正的印章质感）
        val badgeWrap = FrameLayout(this).apply {
            background = roundedBg(SUCCESS, 100)
        }
        val badge = TextView(this).apply {
            text = "✓"
            textSize = 42f
            gravity = Gravity.CENTER
            setTextColor(SUCCESS)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            background = roundedBg(0xFFFFFFFF.toInt(), 100)
        }
        badgeWrap.addView(badge, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))
        resultCard.addView(badgeWrap, LinearLayout.LayoutParams(dp(86), dp(86)))
        val resultTitle = TextView(this).apply {
            text = "签到成功"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(0), dp(12), dp(0), dp(0))
        }
        resultCard.addView(resultTitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        resultCourseText = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(SUB)
            setPadding(dp(0), dp(8), dp(0), dp(0))
        }
        resultCard.addView(resultCourseText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        // 考勤统计卡片（回执式明细：标题用强调色，数值区层次分明）
        val statsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(LIGHT, 20)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        statsCard.addView(TextView(this).apply {
            text = "学勤预警"
            textSize = 12f
            setTextColor(ACCENT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        resultHoursText = TextView(this).apply {
            textSize = 15f
            setTextColor(TEXT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(0), dp(6), dp(0), dp(2))
        }
        statsCard.addView(resultHoursText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        resultRatioText = TextView(this).apply {
            textSize = 24f
            setTextColor(ACCENT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }
        statsCard.addView(resultRatioText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        resultStatsText = TextView(this).apply {
            textSize = 14f
            setTextColor(TEXT)
            setPadding(dp(0), dp(10), dp(0), dp(0))
        }
        statsCard.addView(resultStatsText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        resultCard.addView(statsCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(18) })
        // 提醒小字（网页真实文案）
        resultCard.addView(TextView(this).apply {
            text = "提醒：旷课累计超过教学时数三分之一及以上者，取消本课程考试资格。"
            textSize = 11f
            setTextColor(SUB)
            setPadding(dp(0), dp(12), dp(0), dp(0))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        // 返回签到按钮：关闭覆盖页 → 回签到页继续
        val backBtn = button("返回签到", ACCENT, 0xFFFFFFFF.toInt(), big = true, radius = 18)
        backBtn.setOnClickListener { dismissSignResultOverlay() }
        resultCard.addView(backBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })
        // 覆盖页卡片：固定宽度（屏宽减边距）避免 WRAP_CONTENT + MATCH_PARENT 组合下长文案
        // （课程名/提醒/考勤统计）换行异常显示不全；高度随内容自适应
        resultOverlay?.addView(resultCard, FrameLayout.LayoutParams(
            resources.displayMetrics.widthPixels - dp(48), FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        rootFrame.addView(resultOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 签到码无效弹窗（类似输入成功的大数字弹窗样式，非 toast）：
        // 输码后 3 秒 URL 未跳 detail 弹出，显示"签到码无效"+ 小字"或网络超时"，点「重新输入」关闭重输
        invalidOverlay = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0x66000000.toInt())   // 半透明遮罩
        }
        val invalidCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedBg(CARD, 30)
            setPadding(dp(48), dp(34), dp(48), dp(32))
        }
        // 警示图标：圆形警示色底 + 白色感叹号（比纯文字更有辨识度）
        invalidCard.addView(TextView(this).apply {
            text = "!"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            background = roundedBg(DANGER, 100)
        }, LinearLayout.LayoutParams(dp(56), dp(56)))
        invalidCard.addView(TextView(this).apply {
            text = "签到码无效"
            textSize = 40f
            maxLines = 1
            gravity = Gravity.CENTER
            setTextColor(DANGER)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            setPadding(dp(0), dp(18), dp(0), dp(0))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        invalidCard.addView(TextView(this).apply {
            text = "或网络超时"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(SUB)
            setPadding(dp(0), dp(10), dp(0), dp(0))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val retryBtn = button("重新输入", ACCENT, 0xFFFFFFFF.toInt(), big = true, radius = 18)
        retryBtn.setOnClickListener { dismissInvalidOverlay() }
        invalidCard.addView(retryBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(22) })
        invalidOverlay?.addView(invalidCard, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        rootFrame.addView(invalidOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // 彩带层最后 addView：盖在所有浮层（大数字/覆盖页/无效弹窗）之上，纯装饰不拦截点击
        buildConfettiLayer()

        // 启动层最后 addView：rootFrame 是 FrameLayout，后加的在最上层——
        // 主界面先铺好，splash 盖在最上面才可见（首帧品牌色由 windowBackground 兜底）
        if (::splashLayer.isInitialized) {
            rootFrame.addView(splashLayer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        setContentView(rootFrame)
        // 秒级时钟：签到面板顶部显示当前时间（精确到秒）
        handler.post(object : Runnable {
            override fun run() {
                if (::clockText.isInitialized) {
                    clockText.text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA)
                        .format(java.util.Date())
                }
                handler.postDelayed(this, 1000)
            }
        })
        trace("buildUi done")
    }

    // ------------------------------------------------------------------ 首次启动引导（免责声明 + 权限 + 进设置）

    /** 当前 App 版本名（免责声明与同意记录用） */
    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    /** 当前 App 版本号（同意记录用） */
    private fun appVersionCode(): Int = try {
        packageManager.getPackageInfo(packageName, 0).versionCode
    } catch (e: Exception) { 0 }

    /** 震动反馈（清脆短震，仅用于：输入数字、签到成功） */
    private fun vibrate(ms: Long = 40) {
        try {
            val v = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (v.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(
                        ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    ))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(ms)
                }
            }
        } catch (e: Exception) {}
    }

    /** 快速震动两下（提示错误：签到码无效） */
    private fun vibrateDouble() {
        try {
            val v = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (v.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 45, 35, 45), -1))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 45, 35, 45), -1)
                }
            }
        } catch (e: Exception) {}
    }

    /** 首次启动引导：免责声明（滚动到底才能同意，不同意自动退出）→ 权限（声明 + 授予按钮，拒绝可重试）→ 进入设置 */
    private fun showGuideOverlay() {
        val ov = FrameLayout(this)
        ov.setBackgroundColor(0xF0000000.toInt())   // 全屏遮罩
        guideOverlay = ov

        // ---------- 第一步：免责声明卡片 ----------
        val dCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(CARD, 24)
            setPadding(dp(22), dp(20), dp(22), dp(18))
        }
        disclaimerCard = dCard
        dCard.addView(TextView(this).apply {
            text = "免责声明"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val dScroll = ScrollView(this)
        dScroll.addView(TextView(this).apply {
            text = DISCLAIMER_TEXT_TEMPLATE.replace("__VER__", appVersion())
            textSize = 14f
            setTextColor(TEXT)
            setLineSpacing(dp(2).toFloat(), 1.2f)
            setTextIsSelectable(true)
        }, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        dCard.addView(dScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(430)
        ))
        val dBtnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val disagreeBtn = button("不同意", LIGHT, 0xFFFA5151.toInt(), big = true, radius = 18)
        disagreeBtn.setOnClickListener {
            finish()   // 不同意：自动退出
        }
        val agreeBtn = button("同意", LIGHT, SUB, big = true, radius = 18)   // 初始灰色；滑到底才变高亮
        agreeBtn.setOnClickListener {
            // 未滑动到底：灰色点击 → 提示滑动到底阅读全文
            val maxReach = (dScroll.getChildAt(0)?.height ?: 0) - dScroll.height
            if (dScroll.scrollY < maxReach - 20) {
                toast("请滑动到底阅读全文")
                return@setOnClickListener
            }
            // 已滑动到底：保存同意记录（时间/版本/设备信息）到本地，只弹一次
            val record = "同意时间:" +
                    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date()) +
                    " | 版本:v" + appVersion() + "(" + appVersionCode() + ")" +
                    " | 设备:" + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                    " | 系统:Android " + android.os.Build.VERSION.RELEASE
            credStore.setDisclaimerAccepted(record)
            showGuidePermStep()
        }
        dBtnRow.addView(disagreeBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { rightMargin = dp(8) })
        dBtnRow.addView(agreeBtn, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { leftMargin = dp(8) })
        dCard.addView(dBtnRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })
        // 滚动到底：同意按钮变高亮（未到底保持灰色，点击时提示滑动到底）
        dScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val max = (dScroll.getChildAt(0)?.height ?: 0) - dScroll.height
            val reached = scrollY >= max - 20
            agreeBtn.background = roundedBg(if (reached) ACCENT else LIGHT, 18)
            agreeBtn.setTextColor(if (reached) 0xFFFFFFFF.toInt() else SUB)
        }
        ov.addView(dCard, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { leftMargin = dp(18); rightMargin = dp(18) })

        // ---------- 第二步：权限卡片（同意后切换） ----------
        val pCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBg(CARD, 24)
            setPadding(dp(22), dp(20), dp(22), dp(18))
            visibility = View.GONE
        }
        permCard = pCard
        pCard.addView(TextView(this).apply {
            text = "权限说明"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(TEXT)
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        pCard.addView(TextView(this).apply {
            text = "本 App 需要以下权限才能正常签到：\n\n· 麦克风：语音识别签到码自动输入\n· 定位：签到功能需要精准定位\n\n权限仅用于核心功能，不会用于其他用途。"
            textSize = 14f
            setTextColor(TEXT)
            setLineSpacing(dp(2).toFloat(), 1.25f)
            setPadding(dp(0), dp(14), dp(0), dp(14))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        val grantBtn = button("授予权限", ACCENT, 0xFFFFFFFF.toInt(), big = true, radius = 18)
        grantBtn.setOnClickListener {
            // 弹系统授权弹窗；被拒绝/失败可再次点击重新激活
            val need = mutableListOf<String>()
            if (!hasRecordPermission()) need.add(Manifest.permission.RECORD_AUDIO)
            if (!hasLocationPermission()) {
                need.add(Manifest.permission.ACCESS_FINE_LOCATION)
                need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_PERMS)
        }
        pCard.addView(grantBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        guidePermHint = TextView(this).apply {
            text = "权限未授予，请点击上方按钮激活系统弹窗"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(SUB)
            setPadding(dp(0), dp(10), dp(0), dp(2))
        }
        pCard.addView(guidePermHint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        guideNextBtn = button("下一步", LIGHT, TEXT, big = true, radius = 18)
        guideNextBtn!!.isEnabled = false
        guideNextBtn!!.setOnClickListener {
            ov.visibility = View.GONE   // 关闭引导层
            // 引导期间被拒绝的麦克风提示补弹（等首次界面结束后显示）
            if (pendingRecordToast) {
                pendingRecordToast = false
                toastOnce("no-record", "未授予麦克风权限，语音签到不可用，可选手动输入")
            }
            startActivity(Intent(this, SettingsActivity::class.java).apply {
                putExtra("first_run", true)   // 首次引导：设置页隐藏返回，只能点右上角保存
            })   // 第三步：直接进入设置界面
        }
        pCard.addView(guideNextBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        ov.addView(pCard, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ).apply { leftMargin = dp(18); rightMargin = dp(18) })

        rootFrame.addView(ov, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        updateGuidePermState()
    }

    /** 切换到权限引导步 */
    private fun showGuidePermStep() {
        disclaimerCard?.visibility = View.GONE
        permCard?.visibility = View.VISIBLE
        updateGuidePermState()
    }

    /** 权限状态刷新：全授予后启用"下一步" */
    private fun updateGuidePermState() {
        if (guideOverlay == null || guideOverlay?.visibility != View.VISIBLE) return
        val ok = hasRecordPermission() && hasLocationPermission()
        guideNextBtn?.isEnabled = ok
        guidePermHint?.text = if (ok) "权限已全部授予，可进入设置" else "权限未授予，请点击上方按钮激活系统弹窗"
    }

    // ------------------------------------------------------------------ 内嵌启动层（开屏）

    /** 花哨开屏动画的元素引用（buildSplashLayer 里创建，playSplashAnimation 里驱动） */
    private lateinit var splashIcon: ImageView
    private lateinit var splashSlotRow: LinearLayout
    private lateinit var splashSlots: List<View>
    private lateinit var splashTitle: TextView
    private var splashAnimSet: AnimatorSet? = null

    /** 全屏渐变启动层：主题色渐变 + 光斑 + 图标弹入 + 打卡雷达波纹 + 粒子扩散 +
     *  四位码槽点亮 + 流光进度条。显示期间 MainActivity 已并行加载网页/探测登录；
     *  动画总长约 2.4 秒，页面/登录就绪也不会早于最短时长淡出。 */
    private fun buildSplashLayer() {
        val palette = ThemePalette.palette(themeStyle(), isNight)
        splashLayer = FrameLayout(this)
        // 背景：主题强调色 → 近黑对角渐变（无论浅色/深色主题，开屏都是深色系，白色元素始终清晰）
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(palette.accent, 0xFF101419.toInt())
        )
        splashLayer.background = bg
        // 两处柔和光斑（左上 / 右下），增加画面层次
        fun glowSpot(size: Int, color: Int, x: Int, y: Int) {
            val spot = View(this)
            spot.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(color, color)).apply {
                shape = GradientDrawable.OVAL
                setSize(size, size)
            }
            splashLayer.addView(spot, FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = x
                topMargin = y
            })
        }
        glowSpot(dp(180), Color.argb(28, 255, 255, 255), -dp(60), -dp(50))
        glowSpot(dp(160), Color.argb(20, 255, 255, 255), dp(240), dp(560))

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        // 图标（单独弹入）
        val iconWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(112))
        }
        splashIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            alpha = 0f
            scaleX = 0.25f
            scaleY = 0.25f
        }
        iconWrap.addView(splashIcon, FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER))
        center.addView(iconWrap, LinearLayout.LayoutParams(dp(112), dp(112)))

        // 四位码槽（4 条横线，逐个点亮 = 输入签到码的仪式感；点亮后整体平移到主页四位横线完成衔接）
        splashSlotRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val slotColors = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFFFE082.toInt(), 0xFFB3E5FC.toInt(), 0xFFC5E1A5.toInt()
        )
        splashSlots = (0 until 4).map { i ->
            View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(slotColors[i], slotColors[i])).apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(3).toFloat()
                }
                alpha = 0f
                scaleX = 0f
            }
        }
        splashSlots.forEach { s ->
            splashSlotRow.addView(s, LinearLayout.LayoutParams(dp(26), dp(6)).apply {
                marginStart = dp(7)
                marginEnd = dp(7)
                topMargin = dp(24)
            })
        }
        center.addView(splashSlotRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        splashTitle = TextView(this).apply {
            text = "上课啦"
            textSize = 30f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = dp(10).toFloat()
        }
        center.addView(splashTitle, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(26) })

        splashLayer.addView(center, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))

        // 注意：splashLayer 不在 buildSplashLayer 里 addView——
        // 必须在主界面 root 之后 addView 才能盖在最上层（FrameLayout 后加的在上）
        // 兜底：动画总长约 2 秒（图标弹入 → 四位码槽点亮 → 槽平移衔接主页横线），期间后台并行加载
        splashShownAt = System.currentTimeMillis()
        handler.post {
            playSplashAnimation()
        }
        handler.postDelayed({ dismissSplash() }, 2000)
    }

    /** 播放开屏动画序列（splash 挂载到 root 之后由 post 触发，保证首帧可渲染） */
    private fun playSplashAnimation() {
        if (!::splashLayer.isInitialized) return
        try {
            val set = AnimatorSet()
            // 1. 图标弹入（缩小放大回弹）
            val iconAlpha = ObjectAnimator.ofFloat(splashIcon, "alpha", 0f, 1f).setDuration(420)
            val iconScale = PropertyValuesHolder.ofFloat("scaleX", 0.25f, 1.12f, 1f)
            val iconScaleY = PropertyValuesHolder.ofFloat("scaleY", 0.25f, 1.12f, 1f)
            val iconZoom = ObjectAnimator.ofPropertyValuesHolder(splashIcon, iconScale, iconScaleY)
                .setDuration(620).apply { interpolator = OvershootInterpolator(1.2f) }
            // 2. 四位码槽逐个点亮（每个槽内 缩放+淡入 并行，槽之间顺序延迟）
            val slotAnim = AnimatorSet()
            splashSlots.forEachIndexed { i, s ->
                val one = AnimatorSet()
                one.playTogether(
                    ObjectAnimator.ofFloat(s, "scaleX", 0f, 1f).setDuration(240),
                    ObjectAnimator.ofFloat(s, "alpha", 0f, 1f).setDuration(240)
                )
                one.startDelay = (i * 230).toLong()
                slotAnim.playTogether(one)
            }
            // 3. 标题上移淡入
            val titleAnim = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(splashTitle, "alpha", 0f, 1f).setDuration(420),
                    ObjectAnimator.ofFloat(splashTitle, "translationY", dp(10).toFloat(), 0f).setDuration(420)
                )
                startDelay = 900
            }

            set.playTogether(iconAlpha, iconZoom, slotAnim, titleAnim)
            splashAnimSet = set
            set.start()
        } catch (e: Exception) {
            // 动画异常不阻塞：直接把关键元素恢复可见
            splashIcon.alpha = 1f
            splashIcon.scaleX = 1f
            splashIcon.scaleY = 1f
            splashTitle.alpha = 1f
        }
    }

    /** 淡出启动层（幂等）；页面/登录就绪也会提前调用，但保证至少展示约 2 秒（开屏动画时长），
     *  之后整层淡出，四位码槽随层一起消失，不做主页平移衔接 */
    private fun dismissSplash() {
        if (splashDismissed || !::splashLayer.isInitialized) return
        val remain = 2000 - (System.currentTimeMillis() - splashShownAt)
        if (remain > 0) {
            handler.postDelayed({ dismissSplash() }, remain)
            return
        }
        splashDismissed = true
        splashAnimSet?.cancel()
        try {
            splashLayer.animate().alpha(0f).setDuration(450)
                .withEndAction { splashLayer.visibility = View.GONE }
                .start()
        } catch (e: Exception) {
            splashLayer.visibility = View.GONE
        }
    }

    // ------------------------------------------------------------------ 原生签到面板

    /** 显示原生签到面板（网页隐藏，后台继续加载/提交）；
     * 注意：启动时 WebView 尚未加载页面，绝不能在这里执行 JS（部分系统 WebView 会 native 崩溃），
     * 网页残留清除移到进入签到页时（signReady 首次 true）执行 */
    private fun showSignPanel() {
        if (signPanelShown) return
        signPanelShown = true
        webView.visibility = View.GONE
        signPanelWrap?.visibility = View.VISIBLE
        signPanel.visibility = View.VISIBLE
        backPanelBtn.visibility = View.GONE
        toggleWebBtn.text = "查看网页"
        manualCode = ""
        updatePanelDisplay()
        appendLog("sign panel shown")
        // 回到键盘面板时，后台网页自动跳回签到页（否则停留在课表/考勤等页面，面板一直显示加载中）。
        // 若正在 SSO 登录页则打断登录流程，不动
        val cur = webView.url ?: ""
        val host = try { Uri.parse(cur).host?.lowercase() ?: "" } catch (e: Exception) { "" }
        val isSso = host.contains("sso") || host.contains("cas")
        if (!isSso && !cur.contains("#/sign/in")) {
            webView.loadUrl(SignInjector.HOME_URL)
        }
    }

    /** 状态栏/导航栏配色跟随主题风格；浅色模式用深色状态栏图标 */
    private fun applySystemBars() {
        try {
            window.statusBarColor = BG
            window.navigationBarColor = BG
            val flags = if (!isNight) {
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
            window.decorView.systemUiVisibility = flags
        } catch (e: Exception) {}
    }

    /** 「查看网页」与「返回签到页」切换 */
    private fun toggleWebView() {
        if (signPanelShown) showWebOnly() else showSignPanel()
    }

    /** 大数字提示浮层：输入瞬间全屏弹出大号签到码 + 彩带庆祝；持续显示，网页确认输入完成后由 dismissBigCodeOverlay 收尾并跳转网页 */
    private fun showBigCodeOverlay(code: String) {
        bigCodeText.text = code
        bigOverlayShown = true
        bigOverlay.visibility = View.VISIBLE
        bigOverlay.alpha = 0f
        bigOverlay.scaleX = 0.92f
        bigOverlay.scaleY = 0.92f
        bigOverlay.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start()
        launchConfetti()   // 彩带喷射
    }

    /** 网页确认输入完成：收掉大数字浮层（彩带残留动画终止）。
     *  不再主动切网页——输码后停留键盘面板，由后台结果监听判定：URL 跳 #/sign/in/detail = 成功（弹覆盖页），
     *  10 秒未跳 = 签到码无效（提示重输）；网页只能通过"查看网页"按钮进入 */
    private fun dismissBigCodeOverlay() {
        if (!bigOverlayShown) return
        bigOverlayShown = false
        stopConfetti()
        bigOverlay.animate().alpha(0f).scaleX(0.88f).scaleY(0.88f).setDuration(220)
            .withEndAction {
                bigOverlay.visibility = View.GONE
                bigOverlay.scaleX = 1f
                bigOverlay.scaleY = 1f
            }.start()
    }

    // ------------------------------------------------------------------ 彩带庆祝动画

    /** 彩带容器（全屏，盖在所有浮层之上，纯装饰不拦截点击） */
    private lateinit var confettiLayer: FrameLayout
    private var confettiActive = false

    private fun buildConfettiLayer() {
        confettiLayer = FrameLayout(this)
        confettiLayer.visibility = View.GONE
        rootFrame.addView(confettiLayer, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    /** 喷射彩带：在浮层背景上撒 3 波彩色小方块，随机颜色/落点/旋转，每波约 0.9 秒 */
    private fun launchConfetti() {
        stopConfetti()
        if (!::confettiLayer.isInitialized) return
        confettiActive = true
        confettiLayer.visibility = View.VISIBLE
        val colors = intArrayOf(
            0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(),
            0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFF00ACC1.toInt(), 0xFFFF6F00.toInt()
        )
        for (wave in 0 until 3) {
            handler.postDelayed({
                if (!confettiActive) return@postDelayed
                val w = rootFrame.width.coerceAtLeast(1)
                val h = rootFrame.height.coerceAtLeast(1)
                for (i in 0 until 10) {
                    val piece = View(this)
                    val sz = dp((6 + (i % 4) * 3)).toFloat()
                    piece.setBackgroundColor(colors[(wave * 3 + i) % colors.size])
                    val lp = FrameLayout.LayoutParams(sz.toInt(), (sz * 0.6f).toInt())
                    lp.gravity = Gravity.TOP or Gravity.START
                    // 从顶部中央偏左/偏右喷出
                    lp.leftMargin = (w / 2) + (Math.random().toFloat() * w * 0.8f - w * 0.4f).toInt()
                    lp.topMargin = (h * 0.12f).toInt()
                    piece.setRotation((Math.random() * 90 - 45).toFloat())
                    confettiLayer.addView(piece, lp)
                    val tx = (Math.random() * w * 1.2 - w * 0.6f).toFloat()
                    val ty = (h * (0.5 + Math.random() * 0.5)).toFloat()
                    val rot = (360 + Math.random() * 540).toFloat() * (if (Math.random() > 0.5) 1 else -1)
                    piece.animate().translationX(tx).translationY(ty).rotation(rot)
                        .alpha(if (Math.random() > 0.5) 0f else 0.6f)
                        .setDuration((700 + Math.random() * 600).toLong())
                        .withEndAction { confettiLayer.removeView(piece) }
                        .start()
                }
            }, wave * 320L)
        }
    }

    private fun stopConfetti() {
        confettiActive = false
        if (::confettiLayer.isInitialized) {
            confettiLayer.removeAllViews()
            confettiLayer.visibility = View.GONE
        }
    }

    /** 切到网页（面板整体隐藏，网页全屏）；顶部显示「返回签到」按钮 */
    private fun showWebOnly() {
        signPanelShown = false
        // 切到网页视图 = 签到码已提交/用户在看网页 → 后台不再需要监听，立即停止全部监听（省电、防误报）
        stopListeningQuiet()
        webView.visibility = View.VISIBLE
        signPanelWrap?.visibility = View.GONE   // 必须隐藏外层容器，否则空容器仍占一半屏
        signPanel.visibility = View.GONE
        backPanelBtn.visibility = View.VISIBLE
        toggleWebBtn.text = "返回签到页"
    }

    /** 刷新四位码槽：已输入位显示数字（弹入动效）+ 横线隐去，未输入位显示横线 */
    private fun updatePanelDisplay() {
        for (i in 0 until 4) {
            val ch = if (i < manualCode.length) manualCode[i].toString() else ""
            val d = codeDigitViews[i]
            val l = codeLineViews[i]
            if (ch.isEmpty()) {
                d.visibility = View.INVISIBLE
                l.alpha = 1f
            } else {
                d.text = ch
                d.visibility = View.VISIBLE
                l.alpha = 0f
                d.scaleX = 1f
                d.scaleY = 1f
                d.alpha = 1f
            }
        }
    }

    /**
     * 面板键盘输入：攒满 4 位后再注入（避免逐位注入被轮询误重置）。
     * 网页未就绪时先缓存，就绪后自动补输；del/clear 就地编辑。
     */
    private fun onPanelKey(d: String) {
        when (d) {
            "del" -> {
                if (manualCode.isNotEmpty()) manualCode = manualCode.dropLast(1)
                updatePanelDisplay()
                return
            }
            "clear" -> {
                manualCode = ""
                pendingPanelCode = ""
                updatePanelDisplay()
                return
            }
        }
        if (manualCode.length >= 4) return
        manualCode += d
        updatePanelDisplay()
        if (manualCode.length == 4) {
            val code = manualCode
            manualCode = ""
            updatePanelDisplay()
            lastFilledCode = code
            addSpeechInfo("输入", "输满 $code，准备提交")
            if (signReady) {
                // 网页就绪：整体注入（jsFillCode 盲输，可靠），成功后自动切网页显示结果
                submitCode(code, force = true)
            } else {
                // 网页未就绪：缓存，就绪后自动补输
                pendingPanelCode = code
                addSpeechInfo("提示", "网页未就绪，就绪后自动补输 $code")
                toast("网页未就绪，将自动补输 $code")
            }
        }
    }

    /** 网页就绪后，把面板上提前输满的码补注入（网页加载慢的容错） */
    private fun flushPendingPanelInput() {
        if (!signPanelShown || !signReady || filling) return
        if (pendingPanelCode.isEmpty()) return
        val code = pendingPanelCode
        pendingPanelCode = ""
        addSpeechInfo("输入", "网页就绪，补输 $code")
        lastFilledCode = code
        submitCode(code, force = true)
    }

    // ------------------------------------------------------------------ WebView

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val s = webView.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.databaseEnabled = true
        s.setGeolocationEnabled(true)
        s.javaScriptCanOpenWindowsAutomatically = true
        s.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    requestNeededPermissions()
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                applyUAFor(url)
                return handleUrl(url)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null) applyUAFor(url)
                // SSO 循环防护：连续加载 SSO 域页面（cas/sso 互相重定向）超过阈值 → 停止自动登录注入，
                // 避免"明明登录过却卡在登录页无限跳转"
                val host = try { Uri.parse(url ?: "").host?.lowercase() ?: "" } catch (e: Exception) { "" }
                val isSsoHost = host.contains("sso") || host.contains("cas") ||
                        host.contains("auth") || host.contains("passport")
                if (isSsoHost) {
                    ssoLoadCount++
                    if (ssoLoadCount >= 6) {
                        ssoLoadCount = 0
                        autoLoginCooldownUntil = System.currentTimeMillis() + 120000 // 2 分钟不再自动提交
                        autoLoginSubmitted = false
                        try { webView.stopLoading() } catch (e: Exception) {}
                        statusText.text = "登录异常（SSO 循环）：请点「查看网页」手动登录"
                        DebugLog.append("SSO循环: 连续 6 次 SSO 页面加载，已停止自动登录")
                    }
                } else {
                    ssoLoadCount = 0
                }
                signReady = false
                // 新页面加载：标题回"加载中…"，四位横线与键盘未就绪，恢复加载动画
                panelTitleText.text = "加载中"
                panelTitleText.setTextColor(SUB)
                panelKeyGrid?.visibility = View.GONE
                panelCodeDisplay.visibility = View.GONE
                loadingBar?.visibility = View.VISIBLE
                locLoadingBar?.visibility = View.GONE
                filling = false
                signPageFirstSeenAt = 0L
                loginOk = false // 新页面重新探测登录态
                autoLoginSubmitted = false
                clearedOnceForPage = false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // 注意：不注入桌面视口——统一认证页面在手机视口下表单完整（实测确认），
                // 注入 width=1440 会让 Angular/Ionic 初始化卡在"跳转中"，表单无法渲染
                startPolling()
                // 登录检测提前：页面一加载完成就探测（不等签到页渲染），
                // 让登录态在用户输码前确认，避免"签到成功后才探测回来"触发后续重载
                maybeProbeLogin()
                // Debug：抓取每个加载完成页面的 URL + 正文文本到调试日志，便于确认签到结果文案
                debugDumpPage("page:" + (url ?: "").take(80))
            }
        }
    }

    /**
     * 按域名切换 User-Agent：
     * - SSO 统一认证域（sso.hdu.edu.cn 等）→ 桌面 UA，让服务端返回电脑端账号密码登录
     * - 签到站点（*.hdu.edu.cn）→ 恢复移动 UA（站点是移动端应用）
     */
    private fun applyUAFor(url: String) {
        // SSO 统一按屏幕尺寸判端（不是 UA）：手机视口 → 手机版登录页（有"用户名密码"选项卡，jsAutoLogin 已适配）。
        // 全部域统一保持移动 UA，避免与手机视口不匹配导致 sso 服务端 302 踢回 cas 形成死循环。
        webView.settings.userAgentString = defaultUA
    }

    private fun handleUrl(url: String): Boolean {
        val host = Uri.parse(url).host ?: return false
        val allowed = host.endsWith("hdu.edu.cn") ||
                host.contains("alicdn.com") ||
                host.contains("aliyuncs.com")
        if (allowed) return false
        if (url.startsWith("http")) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                // 无浏览器可打开时忽略
            }
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ JS 通信

    private fun evalJs(js: String, onResult: (String?) -> Unit) {
        webView.evaluateJavascript(js) { raw ->
            var v: String? = raw
            if (raw != null && raw.isNotEmpty()) {
                v = try {
                    (JSONTokener(raw).nextValue() as? String) ?: raw
                } catch (e: Exception) {
                    raw
                }
            }
            onResult(v)
        }
    }

    // ------------------------------------------------------------------ 轮询状态机

    /** Debug：抓取当前页面 URL + 标题 + 正文全文，写入日志与详细抓取记录（仅调试开关开启时）。用于确认签到成功/失败真实文案、设计覆盖界面 */
    private fun debugDumpPage(tag: String) {
        if (!credStore.debugEnabled()) return
        evalJs(
            "(function(){try{var t=document.title||'';return JSON.stringify({href:location.href,title:t,text:document.body.innerText})}catch(e){return 'err:'+e.message}})()"
        ) { res ->
            val raw = res ?: "null"
            var href: String
            var title: String
            var text: String
            if (raw.startsWith("{") && raw.endsWith("}")) {
                try {
                    val j = JSONObject(raw)
                    href = j.optString("href", "")
                    title = j.optString("title", "")
                    text = j.optString("text", "")
                } catch (e: Exception) {
                    href = ""; title = ""; text = raw
                }
            } else {
                href = ""; title = ""; text = raw
            }
            val now = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
            appendLog("debug[$tag]: $title | ${href.take(120)}")
            // 详细抓取记录（全文保留，导出用）
            DebugLog.addCapture(
                "=== [$tag] $now\nURL: $href\n标题: $title\n正文:\n$text"
            )
        }
    }

    // ------------------------------------------------------------------ 课表：面板动态显示当前课程

    /** 课表：优先用本地已下载的课表；没有才拉取一次并固定保存（之后离线使用，不再刷新） */
    private fun ensureSchedule() {
        // 已在内存 → 直接显示
        if (scheduleCourses.isNotEmpty()) {
            updateCourseDisplay()
            return
        }
        // 本地已导入的 ICS（设置页选择文件导入，固定保存，离线使用）→ 解析显示
        val cached = credStore.scheduleCacheIcs()
        if (!cached.isNullOrBlank()) {
            scheduleCourses = parseIcs(cached)
            scheduleFetchedAt = System.currentTimeMillis()
            updateCourseDisplay()
            return
        }
        // 未导入 → 显示引导（设置页导入 ICS 文件）
        updateCourseDisplay()
    }

    private fun parseIcs(text: String): List<ScheduleParser.Course> = ScheduleParser.parse(text)

    /** 当前课程匹配：正在上课，或下课后 5 分钟内仍显示（老师下课点名场景，宽限 5 分钟）；
     *  无课时若今天接下来还有课，显示下一节课提醒 */
    private fun updateCourseDisplay() {
        val now = System.currentTimeMillis()
        val cur = scheduleCourses.firstOrNull { now >= it.start && now <= it.end + 5 * 60 * 1000L }
        if (cur != null) {
            val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
            val time = "${fmt.format(java.util.Date(cur.start))}-${fmt.format(java.util.Date(cur.end))}"
            courseText.text = "${cur.name}"   // 前缀"当前课程："已按用户要求删除，只显示课程名
            courseDetailText.text = listOfNotNull(
                time,
                cur.location.ifBlank { null },
                cur.teacher.ifBlank { null }
            ).joinToString(" · ")
        } else {
            if (scheduleCourses.isEmpty()) {
                val hasIcs = !credStore.scheduleCacheIcs().isNullOrBlank()
                courseText.text = if (hasIcs) "当前无课" else "当前无课（课表未导入）"
                courseDetailText.text = if (hasIcs) "" else "右上角设置 → 导入课表（ICS 文件）"
            } else {
                // 无当前课：若接下来还有课，做下一节课提醒（当天只显示时间，跨天补日期）
                val next = scheduleCourses
                    .filter { it.start > now }
                    .minByOrNull { it.start }
                if (next != null) {
                    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                    val dayFmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA)
                    val sameDay = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA)
                        .format(java.util.Date(now)) == java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA)
                        .format(java.util.Date(next.start))
                    val time = if (sameDay) {
                        fmt.format(java.util.Date(next.start))
                    } else {
                        "${dayFmt.format(java.util.Date(next.start))} ${fmt.format(java.util.Date(next.start))}"
                    }
                    courseText.text = "当前无课"
                    courseDetailText.text = "${next.name} · $time 开始"   // 前缀"下一节课："已按用户要求删除
                } else {
                    courseText.text = "当前无课"
                    courseDetailText.text = "今天没有更多课了"
                }
            }
        }
    }

    private fun startPolling() {
        if (pollRunning) return
        pollRunning = true
        pollTick()
    }

    private fun pollTick() {
        if (!pollRunning) return
        // 登录检测提前的兜底：每轮轮询尝试探测（有 5 秒冷却与 skl 域检查，不打扰界面）
        maybeProbeLogin()
        // SSO 循环硬拦截：HTTP 302 重定向链在 WebView 内部处理，onPageStarted 不会逐跳回调，
        // 这里每秒轮询 WebView 当前 URL，连续停在 SSO 域（cas/sso/auth）超过阈值 → 判定循环 → 停止
        val curUrl = webView.url ?: ""
        val curHost = try { Uri.parse(curUrl).host?.lowercase() ?: "" } catch (e: Exception) { "" }
        val curIsSso = curHost.contains("sso") || curHost.contains("cas") ||
                curHost.contains("auth") || curHost.contains("passport")
        if (curIsSso) {
            ssoBlockSeconds++
            if (ssoBlockSeconds >= 8) { // 连续约 8 秒未离开 SSO 域 = 重定向死循环
                ssoBlockSeconds = 0
                ssoBlockedUntil = System.currentTimeMillis() + 120000 // 2 分钟不再自动跳 SSO
                autoLoginCooldownUntil = System.currentTimeMillis() + 120000
                autoLoginSubmitted = false
                try { webView.stopLoading() } catch (e: Exception) {}
                statusText.text = "登录异常（SSO 循环）：请点「查看网页」手动登录"
                DebugLog.append("SSO循环: 连续 8 秒停在 SSO 域，已停止自动登录，请手动登录")
            }
        } else {
            ssoBlockSeconds = 0
        }
        // 登录回跳后 skl 站点默认首页是课表(#/call/course)，且站点路由会把外部改的 hash 拉回课表：
        // 签到面板显示时，非签到路由整页导航回签到页（SPA 从 #/sign/in 初始化，登录态在 cookie，稳定停留；
        // 节流 2 秒 + 10 秒内最多 3 次，防站点路由再次拉回造成无限重载；
        // 已签到成功(prevSigned)时不触发，避免冲掉签到完成状态）
        if (signPanelShown && !prevSigned) {
            val u = curUrl
            val onWrongRoute = u.contains("hdu.edu.cn") && u.contains("#/") && !u.contains("#/sign/")
            if (onWrongRoute) {
                if (System.currentTimeMillis() - lastSignNavResetAt > 10000) {
                    signNavCount = 0
                    lastSignNavResetAt = System.currentTimeMillis()
                }
                if (signNavCount < 3 && System.currentTimeMillis() - lastSignNavAt > 2000) {
                    lastSignNavAt = System.currentTimeMillis()
                    signNavCount++
                    webView.loadUrl(SignInjector.HOME_URL)
                } else if (signNavCount >= 3) {
                    statusText.text = "签到页恢复失败，请点「查看网页」"
                }
            }
        }
        // 输码后结果监听（不进网页也能知道签到结果）：
        // URL 跳 #/sign/in/detail = 签到成功 → 提取考勤数据弹覆盖页（带彩带）；
        // 5 秒未跳 → 弹「签到码无效（或网络超时）」弹窗（非 toast），后台继续检测 3 秒缓冲，
        // 缓冲期内成功则关掉无效弹窗切到签到成功；缓冲结束仍无跳转则维持弹窗等用户「重新输入」
        if (resultWatchActive && !signResultShown) {
            if (curUrl.contains("#/sign/in/detail")) {
                resultWatchActive = false
                appendLog("result watch: URL 已跳 detail → 判定成功")
                onSignSucceeded()
            } else if (!invalidShown && System.currentTimeMillis() - resultWatchStartAt > 5000) {
                invalidShown = true
                invalidShownAt = System.currentTimeMillis()
                appendLog("result watch: 5s 未跳 detail → 弹无效弹窗（后台继续缓冲 3s）")
                showInvalidOverlay()
            } else if (invalidShown && System.currentTimeMillis() - invalidShownAt > 3000) {
                // 缓冲 3 秒结束仍无跳转：停止自动检测，维持无效弹窗，等用户点「重新输入」
                resultWatchActive = false
                appendLog("result watch: 缓冲 3s 结束仍无跳转 → 停止检测，维持无效弹窗")
            }
        }
        evalJs(SignInjector.JS_DETECT_STATE) { raw ->
            handleState(raw)
            flushPendingPanelInput()
            // 课表：每 60 秒匹配一次当前课程（本地数据，不重新拉取；无本地课表时首次会自动下载一次）
            if (System.currentTimeMillis() - lastScheduleTickAt >= 60000L) {
                lastScheduleTickAt = System.currentTimeMillis()
                ensureSchedule()
            }
            handler.postDelayed({ pollTick() }, POLL_INTERVAL)
        }
    }

    private fun handleState(raw: String?) {
        val st = try { JSONObject(raw ?: "") } catch (e: Exception) { null } ?: return
        val hasPw = st.optBoolean("hasPw")
        val captcha = st.optBoolean("captcha")
        val sign = st.optBoolean("sign")
        val sign2 = st.optBoolean("sign2")
        val loc = st.optBoolean("loc")
        // 定位中转圈默认隐藏；仅"签到页就绪 + 已登录 + 定位未拿到"时显示
        locLoadingBar?.visibility = View.GONE
        val signed = st.optBoolean("signed")

        when {
            signed -> {
                signReady = false
                signPageFirstSeenAt = 0L
                wasOnLoginPage = false
                prevSigned = true
                // 签到完成检测（URL 已跳 #/sign/in/detail）：
                // 提交后 30 秒内且覆盖页未显示 → 判定本次签到成功，提取考勤数据弹覆盖页。
                // 覆盖页关闭后 lastSubmitAt 清零，之后停在 detail 页也不会再弹（用户可回签到页签下一门）
                val inResultWindow = System.currentTimeMillis() - lastSubmitAt < 30000
                if (inResultWindow && !signResultShown && lastSubmitAt != 0L) {
                    onSignSucceeded()
                }
            }
            hasPw -> {
                signReady = false
                prevSigned = false
                signPageFirstSeenAt = 0L
                wasOnLoginPage = true
                loginFlowPending = true   // 页面在登录页：登录流程进行中，完成后自动切回签到面板
                if (lastLoginPageSeenAt == 0L) lastLoginPageSeenAt = System.currentTimeMillis()
                if (!credStore.hasCredentials()) {
                    // 没保存账号：必须切到网页让用户看到登录页
                    showWebOnly()
                    statusText.text = "登录页：请在右上角「设置」保存学号密码后自动登录"
                } else if (captcha) {
                    // 有验证码：切到网页手动完成登录（自动登录暂停）
                    showWebOnly()
                    statusText.text = "登录页：检测到验证码，请手动完成登录"
                    tryAutoLogin(submit = false)
                } else if (likelyLoggedIn()) {
                    // 最近 30 分钟内登录成功过：页面经过登录页只是 CAS 校验链（cookie 有效，1-2 秒回跳），
                    // 不重复注入避免打断回跳；若 15 秒仍停留登录页说明 cookie 已失效，重新填表并提示手动点击
                    if (System.currentTimeMillis() - lastLoginPageSeenAt > 15000) {
                        statusText.text = "登录页：已填账号密码，请手动点击登录"
                        tryAutoLogin(submit = false)
                        toastOnce("manual-login", "已自动填入账号密码，请手动点击登录按钮")
                    } else {
                        statusText.text = "登录校验中…（页面回跳中）"
                    }
                } else if (autoLoginSubmitted) {
                    // 已自动提交过一次：给页面时间跳转；
                    // 若超过 12 秒仍停留登录页，说明提交未生效，重置标志重新填表提交（最多 2 次，防死循环）
                    if (System.currentTimeMillis() - autoLoginSubmittedAt > 12000) {
                        autoLoginSubmitted = false
                        if (autoLoginRetries >= 2) {
                            autoLoginRetries = 0
                            autoLoginCooldownUntil = System.currentTimeMillis() + 120000
                            showWebOnly()
                            statusText.text = "自动登录多次失败，请在网页中手动完成登录"
                            DebugLog.append("自动登录: 提交 ${autoLoginRetries + 2} 次未生效，转手动")
                        } else {
                            autoLoginRetries++
                            statusText.text = "登录页：重新自动登录中…"
                            tryAutoLogin(submit = true)
                        }
                    } else {
                        statusText.text = "登录页：已自动提交，等待页面处理…"
                    }
                } else {
                    // 后台自动填账号密码（不自动点登录——学校认证页按钮点击不稳定，已放弃自动点击），
                    // toast 提示用户手动点击登录按钮；点登录后回跳签到页，由 loginFlowPending 自动切回面板
                    statusText.text = "登录页：已填账号密码，请手动点击登录"
                    tryAutoLogin(submit = false)
                    toastOnce("manual-login", "已自动填入账号密码，请手动点击登录按钮")
                }
            }
            sign || sign2 -> {
                signReady = true
                lastLoginPageSeenAt = 0L // 离开登录页：重置登录页停留计时
                wasOnLoginPage = false   // 已回到签到页：登录流程结束
                // 登录流程刚完成（页面从登录页回跳到签到页）：立即切回键盘面板，不等 probe（更快）
                if (loginFlowPending && !prevSigned && !signResultShown) {
                    loginFlowPending = false
                    showSignPanel()
                }
                dismissSplash() // 页面就绪：启动层提前淡出（无需等满 1.6 秒）
                if (resultWaiting) {
                    // 输码后结果判定中：键盘位保持大大大转圈过渡，键盘与四位横线隐藏
                    loadingBar?.visibility = View.VISIBLE
                    panelKeyGrid?.visibility = View.GONE
                    panelCodeDisplay.visibility = View.GONE
                } else {
                // 签到界面加载完成：隐藏加载动画，显示四位横线与键盘（加载完成前隐藏，显示"加载中"）
                panelTitleText.text = "签到就绪"
                panelTitleText.setTextColor(TEXT)
                panelKeyGrid?.visibility = View.VISIBLE
                panelCodeDisplay.visibility = View.VISIBLE
                loadingBar?.visibility = View.GONE
                // 页面加载完成后才允许执行 JS：每次页面只清一次密码框残留
                if (!clearedOnceForPage) {
                    clearedOnceForPage = true
                    evalJs(SignInjector.JS_CLEAR_SITE_CODE) { _ -> }
                }
                // （原"签到完成"文字判断为历史残留，槽结构下无此状态，不再执行）
                prevSigned = false
                if (signPageFirstSeenAt == 0L) signPageFirstSeenAt = System.currentTimeMillis()
                if (sign && !loc && loginOk) {
                    // 已登录但定位未拿到：等定位，必要时刷新（未登录时不刷新，避免反复加载）
                    val elapsed = System.currentTimeMillis() - signPageFirstSeenAt
                    // 定位中转圈显示在键盘位置（与加载动画同款大转圈），键盘与四位横线暂隐
                    panelTitleText.text = "定位中"
                    panelTitleText.setTextColor(SUB)
                    panelKeyGrid?.visibility = View.GONE
                    panelCodeDisplay.visibility = View.GONE
                    loadingBar?.visibility = View.GONE
                    locLoadingBar?.visibility = View.VISIBLE
                    statusText.text = "签到就绪：等待定位…"
                    updateLocation()
                    if (!hasLocationPermission() && elapsed > 8000) {
                        statusText.text = "签到就绪：需要定位权限，请在弹窗中允许"
                        requestNeededPermissions()
                    } else if (elapsed > 12000 && System.currentTimeMillis() - lastReloadAt > 60000) {
                        lastReloadAt = System.currentTimeMillis()
                        webView.reload()
                    }
                } else {
                    // 定位已就绪/无需等待：恢复键盘、关闭定位转圈，并显示当前经纬度
                    locLoadingBar?.visibility = View.GONE
                    panelKeyGrid?.visibility = View.VISIBLE
                    panelCodeDisplay.visibility = View.VISIBLE
                    statusText.text = if (loc) "签到就绪（定位正常）" else "签到就绪"
                    updateLocation()
                }
                maybeAutoListen()
                }
                maybeProbeLogin()
            }
            else -> {
                signReady = false
                prevSigned = false
                signPageFirstSeenAt = 0L
                val cur = webView.url ?: ""
                // 登录回跳后页面短暂停在课表/首页等 skl 站内非签到路由：
                // 保持面板就绪（键盘不闪没），路由修正会立刻切回签到页
                if (signPanelShown && cur.contains("hdu.edu.cn") && cur.contains("#/") && !cur.contains("#/sign/")) {
                    panelTitleText.text = "签到就绪"
                    panelTitleText.setTextColor(TEXT)
                    panelKeyGrid?.visibility = View.VISIBLE
                    panelCodeDisplay.visibility = View.VISIBLE
                    loadingBar?.visibility = View.GONE
                    statusText.text = "正在回到签到页…"
                } else {
                    // 显示当前页面地址（截断），便于定位卡在哪一步
                    val brief = try {
                        val u = Uri.parse(cur)
                        (u.host ?: "") + (u.path ?: "") + (u.query?.take(12) ?: "")
                    } catch (e: Exception) {
                        cur.takeLast(50)
                    }
                    statusText.text = "页面加载中…（$brief）"
                }
                maybeProbeLogin() // SSO 登录回跳 skl 后，在这里探测已登录并切回签到页
            }
        }
    }

    // ------------------------------------------------------------------ 自动登录

    private fun tryAutoLogin(submit: Boolean = true) {
        if (!credStore.hasCredentials() || !credStore.autoLogin()) return
        if (submit && System.currentTimeMillis() < autoLoginCooldownUntil) return
        val u = credStore.username() ?: return
        val p = credStore.password() ?: return
        evalJs(SignInjector.jsAutoLogin(u, p, submit)) { res ->
            appendLog("autologin: $res")
            when (res) {
                "will-submit", "submitted", "form-submitted" -> {
                    autoLoginCooldownUntil = System.currentTimeMillis() + 10000
                    autoLoginSubmitted = true
                    autoLoginSubmittedAt = System.currentTimeMillis()
                    statusText.text = "登录页：已自动提交登录…"
                }
                "no-uname" -> {
                    // 账号框还没渲染出来：不设冷却，等下一轮轮询重试
                    appendLog("autologin: no-uname, retrying")
                }
                "filled-only" -> statusText.text = "登录页：已填账号密码，等待页面处理…"
                else -> { /* no-login-page 等无需处理 */ }
            }
        }
    }

    // ------------------------------------------------------------------ 登录态探测（签到页不主动跳 SSO，App 原生层代为探测）

    /**
     * 仅在签到站点（skl.hdu.edu.cn）触发登录探测（SSO 域不探测，避免循环跳转）。
     * App 原生层直接请求 /api/userinfo，绕开 WebView 内 fetch 的不确定性：
     * - 401：后端返回 data.url（CAS → SSO 登录地址），loadUrl 跳转（自动切桌面 UA）
     * - 200：已登录，停止探测，必要时跳回签到页
     */
    /** 登录记忆：最近 30 分钟内探测成功过 → 认为已登录（页面经过登录页只是 CAS 校验链，不重复注入） */
    private fun likelyLoggedIn(): Boolean {
        return lastProbeOkAt > 0 && System.currentTimeMillis() - lastProbeOkAt < 30 * 60 * 1000
    }

    private fun maybeProbeLogin() {
        if (loginOk) return
        val now = System.currentTimeMillis()
        if (now < loginProbeCooldownUntil) return
        val host = webView.url?.let { Uri.parse(it).host } ?: return
        if (!host.endsWith("skl.hdu.edu.cn")) return // 只在签到站点探测
        loginProbeCooldownUntil = now + 5000
        // 后台探测：不覆盖界面状态文字（用户输码/签到不受打扰）
        appendLog("probe login (host=$host)")
        // 先取 WebView 里可能存在的 sessionId（同步 JS）
        evalJs("localStorage.getItem('sessionId') || ''") { tokenRaw ->
            var token = ""
            if (tokenRaw != null) {
                token = try {
                    (JSONTokener(tokenRaw).nextValue() as? String) ?: ""
                } catch (e: Exception) {
                    tokenRaw.trim().trim('"')
                }
            }
            probeLoginHttp(token)
        }
    }

    private fun probeLoginHttp(token: String) {
        Thread {
            var result = "err"
            try {
                val url = "https://skl.hdu.edu.cn/api/userinfo?type=&index=&t=" + System.currentTimeMillis()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (token.isNotEmpty()) conn.setRequestProperty("X-Auth-Token", token)
                // 关键：探测请求必须携带 WebView 的 Cookie。
                // 登录态实际由 Cookie 维持（SSO 302 回跳时 Set-Cookie），localStorage.sessionId 可能为空/过期，
                // 只带 token 会在"网页已登录但 sessionId 丢失"时误判 401，反复跳 SSO 卡死。
                val cookies = try {
                    android.webkit.CookieManager.getInstance().getCookie("https://skl.hdu.edu.cn") ?: ""
                } catch (e: Exception) { "" }
                if (cookies.isNotEmpty()) conn.setRequestProperty("Cookie", cookies)
                val code = conn.responseCode
                val body = if (code in 200..299) {
                    conn.inputStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                } else {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                }
                conn.disconnect()
                result = when {
                    code == 200 -> "ok"
                    code == 401 -> {
                        val loginUrl = try {
                            JSONObject(body).optString("url", "")
                        } catch (e: Exception) {
                            ""
                        }
                        if (loginUrl.isNotBlank()) "url:$loginUrl" else "401-no-url"
                    }
                    else -> "code:$code"
                }
            } catch (e: Exception) {
                result = "err:" + (e.message ?: "网络错误")
            }
            val r = result
            handler.post {
                when {
                    r == "ok" -> {
                        loginOk = true
                        lastProbeOkAt = System.currentTimeMillis()
                        probeFailCount = 0
                        autoLoginRetries = 0      // 登录成功：重置自动登录重试计数
                        ssoLoadCount = 0         // 登录成功：重置 SSO 循环计数
                        ssoBlockSeconds = 0      // 登录成功：重置 SSO 循环轮询计数
                        dismissSplash()          // 已登录：启动层提前淡出
                        appendLog("probe ok")
                        // SSO 桌面端登录已回跳，恢复签到站点的移动 UA
                        applyUAFor(SignInjector.HOME_URL)
                        statusText.text = "签到就绪（已登录）"
                        // 仅当页面刚走完登录流程时切回键盘面板（自动提交或手动点击登录后回跳签到页）；
                        // 用户主动看网页（如提交后看签到结果）时保持网页不切换
                        if (wasOnLoginPage || loginFlowPending) {
                            wasOnLoginPage = false
                            loginFlowPending = false
                            if (!prevSigned && !signResultShown) {
                                showSignPanel()
                            }
                        }
                        // 认证成功：本次刚自动提交登录且页面不在签到页 → 等 1 秒重建 Activity（不退出进程），
                        // cookie 已有效，重建后直接稳定回签到页，避开认证回跳造成的路由错乱
                        val curAfterLogin = webView.url ?: ""
                        if (autoLoginSubmitted && !curAfterLogin.contains("#/sign/") && !restartedAfterLogin) {
                            restartedAfterLogin = true
                            handler.postDelayed({ recreate() }, 1000)
                        }
                        gotoSignPageIfNeeded()
                    }
                    r.startsWith("url:") -> {
                        probeFailCount = 0
                        val loginUrl = r.removePrefix("url:")
                        appendLog("probe url: $loginUrl")
                        if (signReady) {
                            // 页面已渲染签到页 = 登录态有效（未登录不可能看到签到内容）；
                            // probe 401 是探测接口误报（header/cookie 不全或瞬时失败），不跳 SSO
                            loginOk = true
                            appendLog("probe 401 但页面已就绪，视为已登录")
                            statusText.text = "签到就绪（已登录）"
                        } else if (System.currentTimeMillis() < ssoBlockedUntil) {
                            // SSO 循环冷却期内：不再自动跳 SSO，引导手动登录（避免死循环）
                            statusText.text = "登录状态异常，请点「查看网页」手动完成登录"
                            showWebOnly()
                        } else {
                            // 不立即跳 SSO：cookie 有效时页面会自行完成 CAS 校验回跳（1-2 秒）；
                            // 等 8 秒页面仍未渲染签到页（真未登录/校验失败）才主动导航登录页
                            statusText.text = "正在验证登录…"
                            val probeAt = System.currentTimeMillis()
                            handler.postDelayed({
                                if (!signReady && !loginOk &&
                                        System.currentTimeMillis() - probeAt >= 7000) {
                                    applyUAFor(loginUrl)
                                    webView.loadUrl(loginUrl)
                                }
                            }, 8000)
                        }
                    }
                    else -> {
                        probeFailCount++
                        appendLog("probe $r")
                        if (probeFailCount >= 3) {
                            statusText.text = "登录状态检测失败，请到「账号设置」核对账号后重试"
                        }
                    }
                }
            }
        }.start()
    }

    /** 已登录但不在签到页时，导航回签到页；已签到成功（prevSigned）则绝不重载，避免冲掉完成状态 */
    private fun gotoSignPageIfNeeded() {
        if (prevSigned) return
        // 站点路由会把 JS 改的 hash 拉回课表，必须整页导航 #/sign/in（SPA 从签到页初始化）；
        // 已登录但不在签到页（含认证回跳后 URL 无 #/ 片段或停在课表/根路由）→ 整页导航回签到页；
        // 只在 probe ok（登录成功、页面加载完成）时调用，不会循环
        val u = webView.url ?: ""
        val host = try { Uri.parse(u).host?.lowercase() ?: "" } catch (e: Exception) { "" }
        if (host.endsWith("skl.hdu.edu.cn") && !u.contains("#/sign/")) {
            webView.loadUrl(SignInjector.HOME_URL)
        }
    }

    // ------------------------------------------------------------------ 签到码输入

    private fun submitCode(code: String, force: Boolean = false) {
        if (filling && !force) return
        if (!signReady) {
            toast("签到页未就绪，请稍候")
            return
        }
        filling = true
        stopListening()
        appendLog("submit: 提交 $code，已停监听（filling=true）")
        lastFilledCode = code
        lastSubmitAt = System.currentTimeMillis()   // 结果判定窗口起点：URL 跳 detail = 成功，10 秒未跳 = 无效
        resultWatchActive = true
        resultWatchStartAt = System.currentTimeMillis()
        statusText.text = "正在输入 $code…"
        addSpeechInfo("输入", "自动输入 $code")
        appendLog("fill: $code")
        // 输入完成瞬间：立即弹全屏大数字 + 彩带，持续显示直到网页确认后跳转
        showBigCodeOverlay(code)
        val gen = ++fillGen
        evalJs(SignInjector.jsFillCode(code)) { _ ->
            pollFillResult(gen, 0)
        }
    }

    private fun pollFillResult(gen: Int, attempt: Int) {
        if (gen != fillGen) return // 已被更新的输入取代
        if (attempt > 60) {
            filling = false
            resultWatchActive = false   // 输入超时：取消结果监听（不是签到码无效）
            statusText.text = "自动输入超时，请手动输入"
            appendLog("fill 超时: 60 次轮询无网页确认，改手动")
            toast("自动输入超时，请手动输入")
            dismissBigCodeOverlay()
            return
        }
        evalJs(SignInjector.JS_CHECK_FILL) { res ->
            when {
                res == "ok" -> {
                    filling = false
                    statusText.text = "已输入签到码，等待签到结果…"
                    addSpeechInfo("输入", "自动输入 $lastFilledCode 完成，等待签到结果")
                    appendLog("fill ok: $lastFilledCode 网页已确认输入（第${attempt}次轮询）")
                    // 网页已确认输入完成：收起大数字浮层（彩带停止），键盘位显示大转圈过渡等结果判定
                    dismissBigCodeOverlay()
                    showResultWaiting()
                    // Debug：提交后连续多次抓取结果页面（跳转/异步渲染慢，多采样 5 次 × 2 秒，持续约 10 秒），
                    // 保证能抓到「签到成功页」或「签到码不存在」的真实文案
                    for (i in 1..5) {
                        handler.postDelayed({ debugDumpPage("after-submit-$i") }, i * 2000L)
                    }
                }
                res != null && res.startsWith("fail:") -> {
                    // 页面可能已跳 #/sign/in/detail（盲输触发提交后键盘消失，count 检查失真）
                    // → 实际已签到成功，不判失败：静默收尾，成功覆盖页由 URL 检测的 onSignSucceeded 负责
                    val u = webView.url ?: ""
                    if (u.contains("#/sign/in/detail") || signResultShown) {
                        filling = false
                        dismissBigCodeOverlay()
                        appendLog("fill fail 但已跳 detail → 视为成功，静默收尾")
                    } else {
                        filling = false
                        resultWatchActive = false   // 注入失败：取消结果监听
                        val reason = res.removePrefix("fail:")
                        statusText.text = "自动输入失败（$reason），请手动输入"
                        addSpeechInfo("输入", "自动输入失败：$reason")
                        appendLog("fill fail: $reason")
                        toast("自动输入失败，请手动输入或直接在页面上操作")
                        dismissBigCodeOverlay()
                    }
                }
                else -> handler.postDelayed({ pollFillResult(gen, attempt + 1) }, 120)
            }
        }
    }

    // ------------------------------------------------------------------ 签到结果判定与覆盖页

    /** 签到成功：从网页正文提取课程名与考勤统计 → 弹出签到完成覆盖页（带彩带） */
    private fun onSignSucceeded() {
        appendLog("sign succeeded: #/sign/in/detail")
        invalidShown = false                       // 若无效弹窗显示中（缓冲期内成功）→ 先关掉
        invalidOverlay?.visibility = View.GONE
        resultWatchActive = false
        hideResultWaiting()                        // 收掉结果等待转圈
        vibrate(60)                                // 签到成功震动反馈（清脆单震）
        // 提取 detail 页正文解析课程与统计（页面异步渲染，最多重试 3 次）
        fun extract(attempt: Int) {
            evalJs("(function(){try{return document.body.innerText||''}catch(e){return ''}})()") { text ->
                val t = text ?: ""
                if (t.contains("签到完成") || attempt >= 2) {
                    parseSignResult(t)
                    showSignResultOverlay()
                } else if (attempt < 3) {
                    handler.postDelayed({ extract(attempt + 1) }, 600)
                } else {
                    parseSignResult(t)
                    showSignResultOverlay()
                }
            }
        }
        extract(0)
    }

    /** 输码后结果判定中：键盘位显示大大大转圈过渡，键盘与四位横线隐藏 */
    private fun showResultWaiting() {
        resultWaiting = true
        loadingBar?.visibility = View.VISIBLE
        panelKeyGrid?.visibility = View.GONE
        panelCodeDisplay.visibility = View.GONE
        statusText.text = "已输入签到码，等待签到结果…"
    }

    /** 结果判定结束：收起转圈（键盘恢复由签到页状态机处理） */
    private fun hideResultWaiting() {
        resultWaiting = false
        loadingBar?.visibility = View.GONE
    }

    /** 解析 detail 页正文：课程名 + 学勤预警 + 考勤四格（真实结构：签到完成/课程名/考勤统计/学勤预警/旷课学时x/课程总学时y/比例%/正常n/请假n/迟到n/旷课n） */
    private fun parseSignResult(text: String) {
        resultCourseText?.text = ""
        resultHoursText?.text = "旷课学时 0 / 课程总学时 0"
        resultRatioText?.text = "0.00%"
        resultStatsText?.text = "正常 0    请假 0    迟到 0    旷课 0"
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val idx = lines.indexOf("签到完成")
        if (idx >= 0 && idx + 1 < lines.size) {
            resultCourseText?.text = lines[idx + 1]
        }
        val reHours = Regex("旷课学时\\s*(\\d+)\\s*/\\s*课程总学时\\s*(\\d+)")
        reHours.find(text)?.let { m ->
            resultHoursText?.text = "旷课学时 ${m.groupValues[1]} / 课程总学时 ${m.groupValues[2]}"
        }
        val reRatio = Regex("(\\d+\\.\\d+)%")
        reRatio.find(text)?.let { m -> resultRatioText?.text = m.groupValues[1] + "%" }
        val reStats = Regex("正常\\s*(\\d+).*?请假\\s*(\\d+).*?迟到\\s*(\\d+).*?旷课\\s*(\\d+)", RegexOption.DOT_MATCHES_ALL)
        reStats.find(text)?.let { m ->
            resultStatsText?.text = "正常 ${m.groupValues[1]}    请假 ${m.groupValues[2]}    迟到 ${m.groupValues[3]}    旷课 ${m.groupValues[4]}"
        }
    }

    /** 签到码无效：3 秒未跳 detail → 弹「签到码无效（或网络超时）」弹窗（非 toast），后台继续缓冲检测 */
    private fun showInvalidOverlay() {
        appendLog("sign invalid: 3s no detail redirect, show invalid overlay")
        hideResultWaiting()                        // 收掉结果等待转圈
        vibrateDouble()                            // 失败：快速震动两下
        statusText.text = "签到码无效，请重试"
        val ov = invalidOverlay ?: return
        ov.alpha = 0f
        ov.visibility = View.VISIBLE
        ov.animate().alpha(1f).setDuration(180).start()
    }

    /** 关闭无效弹窗 → 恢复键盘，可重输 */
    private fun dismissInvalidOverlay() {
        invalidShown = false
        invalidOverlay?.visibility = View.GONE
        resultWatchActive = false
        lastSubmitAt = 0L
        manualCode = ""
        updatePanelDisplay()
        statusText.text = "签到就绪"
    }

    /** 显示签到完成覆盖页（淡入 + 成功彩带） */
    private fun showSignResultOverlay() {
        if (signResultShown) return
        signResultShown = true
        prevSigned = true
        signReady = false
        launchConfetti()   // 成功彩带（盖在覆盖页之上）
        val ov = resultOverlay ?: return
        ov.alpha = 0f
        ov.scaleX = 0.96f
        ov.scaleY = 0.96f
        ov.visibility = View.VISIBLE
        ov.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    /** 关闭覆盖页 → 回签到页（重新加载 #/sign/in 恢复输入框），可继续签下一门 */
    private fun dismissSignResultOverlay() {
        if (!signResultShown) return
        signResultShown = false
        stopConfetti()
        resultOverlay?.visibility = View.GONE
        lastSubmitAt = 0L     // 结果窗口失效：后续停在 detail 页不再触发覆盖页
        prevSigned = false    // 允许导航回签到页
        statusText.text = "签到就绪"
        val u = webView.url ?: ""
        if (u.contains("#/sign/in/detail") || !u.contains("#/sign/")) {
            webView.loadUrl(SignInjector.HOME_URL)   // 整页导航回签到页，恢复输入框
        }
    }

    // ------------------------------------------------------------------ 语音识别

    /** 小米 MIMO 云端持续切片引擎：持续录音，每 3 秒上传最近 10 秒（重叠 7 秒）；两步方案：ASR 转写 + flash 综合判断；auto=true 为自动监听模式 */
    private fun startMimoListening(auto: Boolean = false) {
        stopListeningQuiet()
        val key = credStore.mimoApiKey() ?: return
        // 两步架构：ASR 专用模型纯转写 + flash 综合判断（用户确认改回，纯 flash 直出在安静环境下误报数字）
        val engine = MiMoSpeechEngine(key, credStore.mimoBaseUrl(), credStore.mimoAsrModel(), credStore.mimoModel())
        mimoEngine = engine
        listening = true
        mimoRestartCount = 0
        mimoSliceCount = 0
        llmExtractBusy = false
        llmExtractFirst = true
        heardPool.clear()
        // 监听代际：本次监听的唯一编号；旧代际（上一轮监听）残留回调一律丢弃
        mimoEpoch++
        val epoch = mimoEpoch
        // 状态栏不再显示监听/识别状态（用户要求左下角保持干净）
        engine.start(object : MiMoSpeechEngine.Callback {
            override fun onState(text: String) {
                if (epoch != mimoEpoch || !listening) return   // 旧代际残留，丢弃
                if (text.contains("监听")) addSpeechInfo("MIMO", "开始持续监听…（每 3 秒切片、最近 10 秒）")
            }

            override fun onResult(text: String) {
                if (epoch != mimoEpoch || !listening || filling) return   // 旧代际残留/已停止/正在输码，一律丢弃
                mimoSliceCount++
                val clean = text.trim()
                if (clean.isEmpty()) {
                    addSpeechInfo("MIMO", "切片 $mimoSliceCount 识别结果：空")
                    maybeLlmExtract()
                    return
                }
                appendLog("mimo slice $mimoSliceCount: ${clean.take(200)}")
                addSpeechInfo("MIMO", "切片 $mimoSliceCount 转写：$clean")
                heardPool.addLast(clean)
                while (heardPool.size > 10) heardPool.removeFirst()
                // 本地快速路径：转写里直接有连续标准数字（阿拉伯/中文数字字）→ 立即注入，省一次 flash 调用
                // 注意：只认标准数字，不做激进同音字映射（那曾导致安静环境误输出数字）
                val quick = extractCode(clean)
                if (quick != null && quick.length == 4) {
                    // 重复报码被切片截断时，尾部片段（如"八一四七八一四"的"七八一四"）恰好也是连续4位，
                    // 但它是第二遍报码的尾部不是完整码。若历史转写里有更长连续段以其结尾 → 取长段前4位（历史已含完整报码）
                    val tailSeg = findTailOfLongerSegment(quick)
                    if (tailSeg != null) {
                        if (repeatPrefixMatch(tailSeg.substring(0, 4), tailSeg.substring(4))) {
                            val full = tailSeg.substring(0, 4)
                            appendLog("extract 命中 $quick 为重复报码尾部 → 历史长段前4位 $full → 直接提交，跳过 LLM")
                            submitCode(full)
                        } else {
                            appendLog("extract 命中 $quick 但历史长段无法确认完整码 → 交 LLM")
                            maybeLlmExtract()
                        }
                        return
                    }
                    appendLog("extract 快速路径命中 $quick → 直接提交，跳过 LLM")
                    submitCode(quick)
                    return
                }
                maybeLlmExtract()
            }

            override fun onError(message: String) {
                if (epoch != mimoEpoch || !listening) return   // 旧代际残留，丢弃
                appendLog("mimo err: $message")
                addSpeechInfo("MIMO", "错误：$message")
                // 自动监听模式：Key 无效 / 麦克风失败等致命错误直接停止；
                // 网络类错误（DNS 解析失败/连接超时/断网）持续自动重试，不限制次数——课堂不能因网络波动漏签
                val fatal = message.contains("API Key") || message.contains("麦克风") ||
                        message.contains("初始化") || message.contains("Key") ||
                        message.contains("401") || message.contains("403") ||
                        message.contains("模型") || message.contains("model")
                val netErr = message.contains("Unable to resolve") || message.contains("connect") ||
                        message.contains("timeout") || message.contains("网络") ||
                        message.contains("Socket") || message.contains("Failed") || message.contains("refused")
                if (auto && !fatal) {
                    if (!netErr && mimoRestartCount >= 3) {
                        mimoEngine = null
                        listening = false
                        toast(message)
                        if (signReady) statusText.text = "签到就绪"
                        return
                    }
                    handler.postDelayed({
                        if (!listening && signReady && credStore.autoListen()) maybeAutoListen()
                    }, 4000)
                    return
                }
                mimoEngine = null
                listening = false
                toast(message)
                if (signReady) statusText.text = "签到就绪"
            }
        })
    }

    /** 把累积的切片转写文本交给 flash 综合判断 4 位签到码（每 3 秒一次；以 flash 输出为准） */
    private fun maybeLlmExtract() {
        if (epochStale()) return
        if (llmExtractBusy || filling) return
        val combined = heardPool.joinToString(" ")
        if (combined.isBlank()) return
        val eng = mimoEngine ?: return
        llmExtractBusy = true
        appendLog("llm extract${if (llmExtractFirst) " (首次)" else ""}: 输入${heardPool.size}条转写 = ${combined.take(300)}")
        eng.extractSignCode(combined, llmExtractFirst) { code, raw ->
            llmExtractBusy = false
            llmExtractFirst = false   // 首次提示词只用于第一轮，之后都用累积提示词
            if (epochStale() || filling) return@extractSignCode
            // 模型原始输出全文必记：4 位码 / "无" / 乱码都可回溯
            appendLog("llm raw: ${raw?.take(200) ?: "(无输出/异常)"}")
            if (code != null) {
                appendLog("llm code: $code")
                addSpeechInfo("综合", "判断为签到码 $code")
                heardPool.clear()
                submitCode(code)
            } else {
                addSpeechInfo("综合", "暂无可信签到码，继续监听")
            }
        }
    }

    /** 当前监听代际是否已过期（监听已停止或被新一轮取代） */
    private fun epochStale(): Boolean = !listening

    /** 自动监听：登录进入签到页后自动开始持续切片识别（仅 MIMO 引擎 + 已配 Key） */
    private fun maybeAutoListen() {
        if (!credStore.autoListen()) return
        // 主界面在后台（设置页/课表/多任务/切走）：绝不启动监听——pollTick 后台仍会轮询，
        // 不加此判断会在用户切到设置页后重新拉起麦克风（曾实测在设置页说话仍被识别提交）
        if (isPaused) return
        // 只在首页键盘面板监听；切到网页/设置/课表等任何其他界面都不监听
        if (!signPanelShown) return
        if (signResultShown) return   // 签到完成覆盖页显示中不监听
        if (invalidShown) return      // 签到码无效弹窗显示中不监听（用户在查看结果/操作弹窗）
        if (listening || filling) return
        if (!signReady) return
        if (!hasRecordPermission()) return
        // 语音识别仅 MIMO 云端引擎（系统识别已移除）：没填 API Key 就禁用语音识别
        if (credStore.mimoApiKey().isNullOrBlank()) {
            if (System.currentTimeMillis() - lastAutoListenHintAt > 15000) {
                lastAutoListenHintAt = System.currentTimeMillis()
                statusText.text = "签到就绪（自动监听需在设置中填 MIMO API Key）"
            }
            return
        }
        startMimoListening(auto = true)
    }

    /**
     * 从识别文本中提取 4 位数字签到码（本地快速规则，提取不出时交给 LLM 综合判断）。
     * 支持：阿拉伯数字、中文数字（零幺二两三四五六七八九洞）、
     * 数位词（千/百/十）直接剔除（如"两千五百八十八"→2588）、
     * 前后任意口语噪声（"签到码是2588"等）、逐位报数（"2 5 8 8"或"二五八八"）。
     * 注意：混合短段（如"2"+"588"跨切片）无法可靠本地拼接，返回 null 交给 LLM 提取。
     */
    /**
     * 从语音转写文本中提取 4 位签到码（本地快速路径，只认标准数字，绝不激进映射）。
     *
     * 原则：安静/杂音环境下宁可提取不到（交给 flash 综合判断），也不能把任意句子的
     * 同音字拼成 4 位数字误注入（v1.12.3 的激进同音映射曾导致"不说话也输出数字"）。
     * 只支持：阿拉伯数字、中文数字字（零/洞/幺/一/二/两/三/四/五/六/七/八/九）、英文数字单词；
     * 剔除语气词；同音字归一化（"五百二斤"→5827 等）全部交给 flash 语义判断。
     */
    private fun extractCode(text: String): String? {
        if (text.isBlank()) {
            appendLog("extract 空输入 → null")
            return null
        }
        appendLog("extract 原文: ${text.take(200)}")
        // 1. 剔除语气词/助词（不参与数字映射）
        var s = text
        for (ch in charArrayOf('啊', '哦', '嗯', '呢', '嘛', '的', '了', '个', '是', '呀', '啦', '哈', '喂', '诶', '好')) {
            s = s.replace(ch.toString(), "")
        }
        appendLog("extract 剔除语气词: ${s.take(200)}")
        // 2. 英文数字单词 → 阿拉伯
        val enMap = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9"
        )
        for ((en, d) in enMap) {
            s = Regex("(?i)\\b$en\\b").replace(s, d)
        }
        appendLog("extract 英文替换: ${s.take(200)}")
        // 3. 提取所有数字段；仅当段间间隔只含标点/空白（逗号顿号等，不含任何实义文字）时拼接，
        //    还原 ASR 断句拆开的连续报码（"一二三，四一二三" → 1234123）。"第一次""签到码是"等文字间隔不可拼。
        val segs = mutableListOf<String>()
        val gaps = mutableListOf<String>()
        var pp = 0
        var cur = StringBuilder()
        var pendingGap = StringBuilder()
        var havePendingGap = false
        while (pp < s.length) {
            if (isDigitChar(s[pp])) {
                // 遇到新数字段时定档上一段尾的间隔；文本开头的非数字前缀（如"听到吗？"）不属于段间，丢弃
                if (havePendingGap) {
                    if (segs.isNotEmpty()) gaps.add(pendingGap.toString())
                    havePendingGap = false
                }
                cur.append(s[pp]); pp++
            } else {
                if (cur.isNotEmpty()) { segs.add(cur.toString()); cur = StringBuilder() }
                pendingGap = StringBuilder()
                while (pp < s.length && !isDigitChar(s[pp])) { pendingGap.append(s[pp]); pp++ }
                havePendingGap = true
            }
        }
        if (cur.isNotEmpty()) segs.add(cur.toString())
        if (segs.isEmpty()) {
            appendLog("extract 无连续数字 → null，交 LLM")
            return null
        }
        // 按可拼间隔合并
        val merged = mutableListOf<String>()
        var curM = segToDigits(segs[0])
        for (i in 1 until segs.size) {
            val gap = if (i - 1 < gaps.size) gaps[i - 1] else ""
            if (isPunctGap(gap)) curM += segToDigits(segs[i]) else { merged.add(curM); curM = segToDigits(segs[i]) }
        }
        merged.add(curM)
        appendLog("extract 数字段: ${segs.joinToString(" | ") { s -> "\"$s\"" }} 间隔: ${gaps.joinToString(" | ") { g -> "\"$g\"" }} → 可拼后: ${merged.joinToString(" | ")}")
        // 4. 判定：优先循环重复长段（强证据）；其次唯一恰好4位段；多个4位段或无法确认 → 交 LLM
        var cycleHit: String? = null
        var fourCandidates = mutableListOf<String>()
        for (m in merged) {
            when {
                m.length == 4 -> fourCandidates.add(m)
                m.length > 4 && repeatPrefixMatch(m.substring(0, 4), m.substring(4)) && cycleHit == null -> {
                    cycleHit = m.substring(0, 4)
                    appendLog("extract 长串${m.length}位循环重复 → 完整码 $cycleHit")
                }
                else -> appendLog("extract 合并段 $m (${m.length}位) 无法本地确认")
            }
        }
        if (cycleHit != null) {
            appendLog("extract 循环命中 → $cycleHit → 本地提交，跳过 LLM")
            return cycleHit
        }
        if (fourCandidates.size == 1) {
            appendLog("extract 连续4位命中 → ${fourCandidates[0]}")
            return fourCandidates[0]
        }
        if (fourCandidates.size > 1) {
            appendLog("extract 多个4位段（${fourCandidates.joinToString("、")}）无法确定 → null，交 LLM")
        } else {
            appendLog("extract 无4位候选 → null，交 LLM")
        }
        return null
    }

    /** 是否标准数字字（阿拉伯+中文数字字） */
    private fun isDigitChar(c: Char): Boolean =
        (c in '0'..'9') || c == '零' || c == '洞' || c == '幺' || c == '一' || c == '二' ||
            c == '两' || c == '三' || c == '四' || c == '五' || c == '六' || c == '七' ||
            c == '八' || c == '九'

    /** 数字段之间的间隔是否只含标点/空白（可拼接），含任何实义文字/字母则不可拼 */
    private fun isPunctGap(gap: String): Boolean {
        val punct = "，。、！？；：,.!?;:'\"“”‘’（）()…～~·—-_=+/\\ \t\n"
        return gap.all { it in punct }
    }

    /** 连续数字字段（可含中文数字字）→ 阿拉伯数字串 */
    private fun segToDigits(seg: String): String {
        return seg.map { c ->
            when (c) {
                in '0'..'9' -> c
                '零', '洞' -> '0'
                '幺', '一' -> '1'
                '二', '两' -> '2'
                '三' -> '3'
                '四' -> '4'
                '五' -> '5'
                '六' -> '6'
                '七' -> '7'
                '八' -> '8'
                '九' -> '9'
                else -> c
            }
        }.joinToString("")
    }

    /** 完整码4位 + 剩余位是否构成循环前缀重复：剩余每个字符依次等于完整码对应循环位（rest[i] == code4[i % 4]） */
    private fun repeatPrefixMatch(code4: String, rest: String): Boolean {
        if (rest.isEmpty()) return true
        for (i in rest.indices) {
            if (rest[i] != code4[i % 4]) return false
        }
        return true
    }

    /** 返回转写中最长连续标准数字字字段（原样，未转阿拉伯），无则 null */
    private fun longestDigitSegment(text: String): String? {
        val digitSet = setOf('0','1','2','3','4','5','6','7','8','9',
            '零','洞','幺','一','二','两','三','四','五','六','七','八','九')
        var bestStart = -1
        var bestLen = 0
        var p = 0
        while (p < text.length) {
            if (text[p] in digitSet) {
                val st = p
                while (p < text.length && text[p] in digitSet) p++
                val len = p - st
                if (len > bestLen) { bestLen = len; bestStart = st }
            } else p++
        }
        return if (bestLen == 0) null else text.substring(bestStart, bestStart + bestLen)
    }

    /** 候选4位码是否为「重复报码被截断的尾部」：heardPool 某条转写里有比它更长、末尾恰为该码（阿拉伯比较）的连续段，返回该长段阿拉伯串 */
    private fun findTailOfLongerSegment(code: String): String? {
        for (t in heardPool) {
            val seg = longestDigitSegment(t) ?: continue
            if (seg.length > 4) {
                val d = segToDigits(seg)
                if (d.endsWith(code)) return d
            }
        }
        return null
    }

    private fun stopListening() {
        try { mimoEngine?.stop() } catch (e: Exception) {}
        mimoEngine = null
        listening = false
    }

    private fun stopListeningQuiet() {
        try { mimoEngine?.stop() } catch (e: Exception) {}
        mimoEngine = null
        listening = false
    }

    // ------------------------------------------------------------------ 权限

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasRecordPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** 原生定位：请求位置并在时间下方显示经纬度（N/S/E/W + 度数，原始精度） */
    private fun updateLocation() {
        if (!hasLocationPermission()) {
            locDisplayText?.text = "定位中"
            return
        }
        try {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                locDisplayText?.text = "定位服务未开启"
                return
            }
            // 节流：每 10 秒注册一次位置更新监听，避免频繁请求
            if (System.currentTimeMillis() - lastLocReqAt > 10000) {
                lastLocReqAt = System.currentTimeMillis()
                try {
                    lm.requestLocationUpdates(provider, 5000L, 0f, locationListener)
                } catch (e: Exception) {}
            }
            // 立即显示最后已知位置
            val last = lm.getLastKnownLocation(provider)
            if (last != null) {
                locDisplayText?.text = formatLocation(last.latitude, last.longitude)
            }
        } catch (e: Exception) {
            locDisplayText?.text = "定位失败"
        }
    }

    /** 经纬度格式化：N 30.0°, E 120.7°（保留一位小数） */
    private fun formatLocation(lat: Double, lng: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lng >= 0) "E" else "W"
        return "$ns ${String.format("%.1f", Math.abs(lat))}°, $ew ${String.format("%.1f", Math.abs(lng))}°"
    }

    /** 定位更新回调：位置变化时刷新经纬度显示 */
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            locDisplayText?.text = formatLocation(location.latitude, location.longitude)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun requestNeededPermissions() {
        // 首次引导显示中不自动弹权限：系统权限弹窗只能由引导第二步"授予权限"按钮激活
        if (guideOverlay?.visibility == View.VISIBLE) return
        val need = mutableListOf<String>()
        if (!hasRecordPermission()) need.add(Manifest.permission.RECORD_AUDIO)
        if (!hasLocationPermission()) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        val cb = pendingGeoCallback
        val origin = pendingGeoOrigin
        if (cb != null) {
            pendingGeoCallback = null
            pendingGeoOrigin = null
            if (hasLocationPermission()) {
                cb.invoke(origin!!, true, false)
                handler.postDelayed({ webView.reload() }, 300)
            } else {
                cb.invoke(origin!!, false, false)
            }
        }
        if (!hasRecordPermission()) {
            // 引导界面期间不弹"未授权麦克风"toast（避免盖在首次引导上），记录等引导结束后补弹
            if (guideOverlay?.visibility == View.VISIBLE) {
                pendingRecordToast = true
            } else {
                toastOnce("no-record", "未授予麦克风权限，语音签到不可用，可选手动输入")
            }
        }
        // 首次引导权限步：全部授予后启用"下一步"
        updateGuidePermState()
    }

    // ------------------------------------------------------------------ 识别信息展示

    /** 记录一条识别信息（来源标签 + 内容），展示在识别信息栏；保留最近 100 条（展开可滚动查看全部） */
    private fun addSpeechInfo(tag: String, msg: String) {
        if (speechLog.size >= 100) speechLog.removeAt(0)
        speechLog.add("[$tag] $msg")
        updateSpeechInfoRow()
        if (speechLogExpanded) speechInfoList.text = speechLog.joinToString("\n")
    }

    private fun updateSpeechInfoRow() {
        val latest = speechLog.lastOrNull() ?: "暂无"
        speechInfoRow.text = "识别：" + latest + (if (speechLogExpanded) " · 点按收起" else " · 点按展开")
        if (!speechLogExpanded) speechInfoList.text = speechLog.joinToString("\n")
    }

    // ------------------------------------------------------------------ 工具

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun toastOnce(key: String, msg: String) {
        val now = System.currentTimeMillis()
        if (key == lastToastKey && now - lastToastAt < 3000) return
        lastToastKey = key
        lastToastAt = now
        toast(msg)
    }

    private fun appendLog(msg: String) {
        if (!credStore.debugEnabled()) return
        val line = "${DateFormat.format("HH:mm:ss", System.currentTimeMillis())} $msg"
        DebugLog.append(line)
    }
}
