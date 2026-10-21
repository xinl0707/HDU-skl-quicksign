package dev.xinl0707.quicksign

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 最新版本检测。
 *
 * 双路径降级：
 * 1. 主路径：GitHub API `/repos/{owner}/{repo}/releases/latest`，可拿到完整信息（更新日志 + APK 直链）。
 *    缺点：未认证限速 60 次/小时/IP，校园网共享出口容易触发 403。
 * 2. 降级路径：HEAD 请求 `github.com/.../releases/latest`，不跟随重定向，从 302 的 Location header
 *    提取 tag（形如 `/releases/tag/v3.1.0`）。这个是网页跳转，不走 API、不限速，但拿不到更新日志。
 *
 * 重试：最多 3 次，间隔 1s / 2s / 4s 递增；只有全部失败才回调 failure，调用方此时才显示「检查失败」。
 * 检查中与无更新时不产生任何 UI 提示。
 *
 * 版本对比：主次补丁三位整数比较，避免字符串比较 "3.0.10" < "3.0.9" 的坑。
 */
data class UpdateInfo(
    val version: String,      // 语义版本号，如 "3.1.0"（已去掉 v 前缀）
    val tagName: String,      // 原始 tag，如 "v3.1.0"
    val changelog: String,    // release body（markdown 纯文本；降级路径为空字符串）
    val htmlUrl: String,      // GitHub Release 页面链接
    val apkUrl: String?       // APK 附件直链（API 路径才有；降级路径为 null）
)

object UpdateChecker {
    private const val API_URL = "https://api.github.com/repos/xinl0707/HDU-skl-quicksign/releases/latest"
    private const val LATEST_PAGE_URL = "https://github.com/xinl0707/HDU-skl-quicksign/releases/latest"
    private const val TIMEOUT_MS = 5000
    private const val USER_AGENT = "QuickSign-Android"
    private const val MAX_RETRY = 3
    private val RETRY_DELAYS = listOf(1000L, 2000L, 4000L)

    /** 异步检测；callback 在子线程回调，调用方需切主线程更新 UI。
     *  success(null) = 已是最新；success(UpdateInfo) = 有更新；failure = 重试耗尽仍失败 */
    fun check(currentVersion: String, callback: (Result<UpdateInfo?>) -> Unit) {
        Thread {
            var lastError: Exception? = null
            for (attempt in 0 until MAX_RETRY) {
                try {
                    val info = checkOnce(currentVersion)
                    callback(Result.success(info))
                    return@Thread
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < MAX_RETRY - 1) Thread.sleep(RETRY_DELAYS[attempt])
                }
            }
            callback(Result.failure(lastError ?: Exception("unknown error")))
        }.start()
    }

    /** 单次检测：先试 API，失败（限速/网络）自动降级到重定向探测 */
    private fun checkOnce(currentVersion: String): UpdateInfo? {
        return try {
            checkViaApi(currentVersion)
        } catch (e: Exception) {
            checkViaRedirect(currentVersion)
        }
    }

    /** 主路径：GitHub API，拿完整信息 */
    private fun checkViaApi(currentVersion: String): UpdateInfo? {
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        val code = conn.responseCode
        if (code != 200) {
            conn.disconnect()
            throw Exception("API HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()

        val json = JSONObject(body)
        val tagName = json.optString("tag_name", "")
        val version = tagName.removePrefix("v")
        val changelog = json.optString("body", "").trim()
        val htmlUrl = json.optString("html_url", "")

        var apkUrl: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name", "").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url", "")
                    break
                }
            }
        }

        if (version.isNotEmpty() && isNewer(version, currentVersion)) {
            return UpdateInfo(version, tagName, changelog, htmlUrl, apkUrl)
        }
        return null
    }

    /** 降级路径：HEAD 请求 latest 页面，从 302 Location 提取 tag（不走 API、不限速） */
    private fun checkViaRedirect(currentVersion: String): UpdateInfo? {
        val conn = URL(LATEST_PAGE_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.instanceFollowRedirects = false   // 关键：不跟随重定向，自己读 Location
        val code = conn.responseCode
        if (code !in 300..399) {
            conn.disconnect()
            throw Exception("redirect HTTP $code")
        }
        val location = conn.getHeaderField("Location")
        conn.disconnect()
        if (location.isNullOrEmpty()) throw Exception("no Location header")

        // Location 形如 https://github.com/xinl0707/HDU-skl-quicksign/releases/tag/v3.1.0
        val tag = location.substringAfterLast("/").trim()
        val version = tag.removePrefix("v")
        if (version.isEmpty()) throw Exception("invalid tag: $tag")

        if (isNewer(version, currentVersion)) {
            val htmlUrl = "https://github.com/xinl0707/HDU-skl-quicksign/releases/tag/$tag"
            return UpdateInfo(version, tag, "", htmlUrl, null)  // 降级路径无更新日志与直链
        }
        return null
    }

    /** 主次补丁三位整数比较：latest > current 返回 true */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until 3) {
            if (l[i] > c[i]) return true
            if (l[i] < c[i]) return false
        }
        return false
    }

    private fun parseVersion(v: String): IntArray {
        val parts = v.removePrefix("v").split(".")
        val result = IntArray(3)
        for (i in 0 until minOf(parts.size, 3)) {
            result[i] = parts[i].toIntOrNull() ?: 0
        }
        return result
    }
}
