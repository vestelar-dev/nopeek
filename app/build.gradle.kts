plugins {
    id("com.android.application")
}

android {
    namespace = "com.vestelar.nopeek"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vestelar.nopeek"
        minSdk = 29
        // Writing privacy_display_level with targetSdk 36 was verified on a Galaxy S26 Ultra.
        targetSdk = 36
        versionCode = 7
        versionName = "1.6"
    }

    // The release key lives outside the repo: its path and passwords come from
    // ~/.gradle/gradle.properties. Without them (e.g. a fresh clone) release
    // builds fall back to the debug key so the project still builds anywhere.
    val keystore = providers.gradleProperty("VESTELAR_KEYSTORE").orNull
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.gradleProperty("VESTELAR_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("VESTELAR_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("VESTELAR_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
