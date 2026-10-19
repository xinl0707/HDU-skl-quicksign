package dev.xinl0707.quicksign

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 账号密码加密存储。
 * 使用 Android Keystore 生成 AES-256 密钥，AES/GCM 加密后再写入 SharedPreferences，
 * 密钥不出安全芯片，即使设备被 root 也无法直接读出明文密码。
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("quicksign_creds", Context.MODE_PRIVATE)

    private val keyAlias = "quicksign_master_key"

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String? {
        return try {
            val parts = data.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun save(username: String, password: String, autoLogin: Boolean) {
        prefs.edit()
            .putString("username", encrypt(username))
            .putString("password", encrypt(password))
            .putBoolean("autoLogin", autoLogin)
            .apply()
    }

    fun username(): String? = prefs.getString("username", null)?.let { decrypt(it) }
    fun password(): String? = prefs.getString("password", null)?.let { decrypt(it) }
    fun hasCredentials(): Boolean = !username().isNullOrEmpty() && !password().isNullOrEmpty()
    fun autoLogin(): Boolean = prefs.getBoolean("autoLogin", true)

    fun clear() {
        prefs.edit().remove("username").remove("password").apply()
    }

    // ------------------------------------------------------------------ 语音引擎配置

    /** 语音识别引擎：仅 mimo（小米 MIMO 云端，系统识别已移除） */
    fun voiceEngine(): String = prefs.getString("voice_engine", "mimo") ?: "mimo"

    fun setVoiceEngine(engine: String) {
        prefs.edit().putString("voice_engine", engine).apply()
    }

    /** MiMo API Key（加密存储） */
    fun mimoApiKey(): String? = prefs.getString("mimo_key", null)?.let { decrypt(it) }

    fun setMimoApiKey(key: String) {
        prefs.edit().putString("mimo_key", if (key.isBlank()) null else encrypt(key)).apply()
    }

    /** MiMo 接口地址（OpenAI 兼容，默认官方地址） */
    fun mimoBaseUrl(): String =
        prefs.getString("mimo_url", "https://api.xiaomimimo.com/v1") ?: "https://api.xiaomimimo.com/v1"

    fun setMimoBaseUrl(url: String) {
        prefs.edit().putString("mimo_url", url.trim()).apply()
    }

    /** MiMo 综合判断模型（flash 多模态，用于把累积转写文本提取成 4 位签到码） */
    fun mimoModel(): String =
        prefs.getString("mimo_model", "mimo-v2.6-flash") ?: "mimo-v2.6-flash"

    fun setMimoModel(model: String) {
        prefs.edit().putString("mimo_model", model.trim().ifEmpty { "mimo-v2.6-flash" }).apply()
    }

    /** MiMo ASR 转写模型（纯语音转写，官方：mimo-v2.5-asr） */
    fun mimoAsrModel(): String =
        prefs.getString("mimo_asr_model", "mimo-v2.5-asr") ?: "mimo-v2.5-asr"

    fun setMimoAsrModel(model: String) {
        prefs.edit().putString("mimo_asr_model", model.trim().ifEmpty { "mimo-v2.5-asr" }).apply()
    }

    /** 自动语音监听：默认关闭（用户要求后台不要持续监听声音）；开启后登录进入签到页自动开始持续录音 + 切片识别（仅 MiMo 引擎） */
    fun autoListen(): Boolean = prefs.getBoolean("auto_listen", false)

    fun setAutoListen(enabled: Boolean) {
        prefs.edit().putBoolean("auto_listen", enabled).apply()
    }

    /** 调试日志开关（跨界面共享） */
    fun debugEnabled(): Boolean = prefs.getBoolean("debug", false)

    fun setDebugEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("debug", enabled).apply()
    }

    /** 主页识别信息窗口：默认隐藏（用户要求；开启后在签到面板显示语音识别日志区） */
    fun showSpeechInfo(): Boolean = prefs.getBoolean("show_speech_info", false)

    fun setShowSpeechInfo(enabled: Boolean) {
        prefs.edit().putBoolean("show_speech_info", enabled).apply()
    }

    // ------------------------------------------------------------------ 课表（导入 ICS 文件，固定保存离线使用）

    /** 课表原始 ICS 文本缓存（设置页导入后固定保存，离线使用） */
    fun scheduleCacheIcs(): String? = prefs.getString("schedule_cache", null)

    fun setScheduleCacheIcs(ics: String) {
        prefs.edit().putString("schedule_cache", ics).apply()
    }
}
