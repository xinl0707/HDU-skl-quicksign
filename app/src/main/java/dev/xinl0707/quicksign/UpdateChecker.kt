package dev.xinl0707.quicksign

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 最新版本检测。
 *
 * 方案：请求 GitHub API `/repos/{owner}/{repo}/releases/latest`，
 * 解析 tag_name 与当前 versionName 对比（主次补丁三位整数比较，避免字符串比较 "3.0.10" < "3.0.9" 的坑）。
 * 有更新则返回 UpdateInfo（含更新日志 body、release 页链接、APK 附件直链）；
 * 无更新返回 null；网络/解析失败返回 failure。
 *
 * 注意：GitHub API 国内访问可能不稳定，5 秒超时；调用方应在失败时给出"检查失败"提示而非崩溃。
 */
data class UpdateInfo(
    val version: String,      // 语义版本号，如 "3.1.0"（已去掉 v 前缀）
    val tagName: String,      // 原始 tag，如 "v3.1.0"
    val changelog: String,    // release body（markdown 纯文本）
    val htmlUrl: String,      // GitHub Release 页面链接
    val apkUrl: String?       // APK 附件直链（assets 里有 .apk 则取，否则 null）
)

object UpdateChecker {
    private const val API_URL = "https://api.github.com/repos/xinl0707/HDU-skl-quicksign/releases/latest"
    private const val TIMEOUT_MS = 5000
    private const val USER_AGENT = "QuickSign-Android"

    /** 异步检测；callback 在子线程回调，调用方需切主线程更新 UI */
    fun check(currentVersion: String, callback: (Result<UpdateInfo?>) -> Unit) {
        Thread {
            try {
                val conn = URL(API_URL).openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code != 200) {
                    callback(Result.failure(Exception("HTTP $code")))
                    return@Thread
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(body)
                val tagName = json.optString("tag_name", "")
                val version = tagName.removePrefix("v")
                val changelog = json.optString("body", "").trim()
                val htmlUrl = json.optString("html_url", "")

                // 从 assets 里找 APK 直链（发版时上传 APK 附件才有）
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
                    callback(Result.success(UpdateInfo(version, tagName, changelog, htmlUrl, apkUrl)))
                } else {
                    callback(Result.success(null))  // 已是最新或版本号解析失败
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }.start()
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
