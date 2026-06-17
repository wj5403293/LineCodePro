plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.lineai.terminalprovider"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.lineai.terminalprovider"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
}
