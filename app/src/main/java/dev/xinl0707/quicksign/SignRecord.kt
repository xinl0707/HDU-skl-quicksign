package dev.xinl0707.quicksign

/**
 * 签到记录实体（仅本机留存）。
 * 由「成功判定」与「无效判定」两个唯一入口写入，与网页端/其他设备无关。
 *
 * status 取值：
 *  - STATUS_SUCCESS   0：URL 跳 detail，签到成功
 *  - STATUS_INVALID   1：提交后超时未跳转，签到码无效（或网络超时）
 *  - STATUS_INJECT_FAIL 2：自动输入失败（JS 注入失败/轮询超时）
 *
 * method 取值：
 *  - "voice"  语音提取（MIMO 识别路径）
 *  - "manual" 手动键盘输入
 */
data class SignRecord(
    val id: Long = 0,
    val status: Int = STATUS_SUCCESS,
    val courseName: String = "",
    val courseStart: Long = 0,
    val courseEnd: Long = 0,
    val code: String = "",
    val method: String = METHOD_MANUAL,
    val tsSubmit: Long = 0,
    val tsResult: Long = 0,
    val statsText: String = "",
    val hoursText: String = "",
    val ratioText: String = "",
    val shotPath: String = "",
    val appVersion: String = "",
    val note: String = ""
) {
    companion object {
        const val STATUS_SUCCESS = 0
        const val STATUS_INVALID = 1
        const val STATUS_INJECT_FAIL = 2

        const val METHOD_VOICE = "voice"
        const val METHOD_MANUAL = "manual"

        const val STATUS_MAX = 1000   // 本地留存上限：超出清理最旧（连带截图）
    }
}
