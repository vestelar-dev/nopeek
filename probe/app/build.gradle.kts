plugins {
    id("com.android.application")
}

android {
    namespace = "com.vestelar.privacyprobe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vestelar.privacyprobe"
        minSdk = 29
        // 28 keeps hidden Settings keys readable and avoids package-visibility filtering.
        targetSdk = 28
        versionCode = 2
        versionName = "0.2"
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

dependencies {
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
}
