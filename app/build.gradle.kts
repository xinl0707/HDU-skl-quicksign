plugins {
    id("com.android.application")
}

android {
    namespace = "dev.xinl0707.quicksign"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.xinl0707.quicksign"
        minSdk = 26
        targetSdk = 37
        versionCode = 74
        versionName = "1.17.15"
    }

    buildTypes {
        release {
            // 自用：debug 签名直接出可安装包
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // 零第三方依赖：WebView / 语音识别 / Keystore 加密均为系统能力
}
