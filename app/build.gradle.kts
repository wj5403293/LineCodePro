import java.security.SecureRandom
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val releaseVersionName = "0.0.2-Alpha"
val releaseApkName = "LineCode Pro $releaseVersionName.APK"
val releaseSigningProperties = Properties()
val releaseSigningFile = rootProject.file("signing.properties")
val hasReleaseSigning = releaseSigningFile.exists()
if (hasReleaseSigning) {
    releaseSigningFile.inputStream().use { releaseSigningProperties.load(it) }
}

val generateReleaseObfuscationDictionary by tasks.registering {
    val outputFile = layout.buildDirectory.file("generated/r8/obfuscation-dictionary.txt")
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doLast {
        val random = SecureRandom()
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        val names = linkedSetOf<String>()

        fun randomName(): String = buildString {
            append('l')
            repeat(15) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }

        while (names.size < 8192) {
            names += randomName()
        }

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(names.joinToString(System.lineSeparator()) + System.lineSeparator())
    }
}

val purgeReleaseSymbolFiles by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.dir("outputs/mapping/release"))
    delete(layout.buildDirectory.dir("outputs/native-debug-symbols/release"))
}

val exportReleaseApk by tasks.registering(Copy::class) {
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(layout.projectDirectory.dir("release"))
    rename { releaseApkName }
}

android {
    namespace = "cn.lineai"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cn.lineai"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = releaseVersionName
    }

    signingConfigs {
        create("lineAiRelease") {
            if (hasReleaseSigning) {
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("lineAiRelease")
            } else {
                signingConfigs.getByName("debug")
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.matching {
    it.name == "minifyReleaseWithR8" || it.name == "minifyReleaseWithProguard"
}.configureEach {
    dependsOn(generateReleaseObfuscationDictionary)
}

tasks.matching {
    it.name == "assembleRelease" || it.name == "bundleRelease"
}.configureEach {
    finalizedBy(purgeReleaseSymbolFiles)
}

tasks.matching {
    it.name == "assembleRelease"
}.configureEach {
    finalizedBy(exportReleaseApk)
}

dependencies {
    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
