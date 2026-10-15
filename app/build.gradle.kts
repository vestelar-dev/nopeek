plugins {
    id("com.android.application")
}

android {
    namespace = "com.vestelar.nopeek"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vestelar.nopeek"
        minSdk = 29
        // Writing privacy_display_level was verified on-device with targetSdk 28.
        targetSdk = 28
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
