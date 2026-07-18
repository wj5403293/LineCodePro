plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("linecodeConvention") {
            id = "linecode.convention"
            implementationClass = "cn.lineai.build.LineCodeConventionPlugin"
        }
        register("linecodeFeatureConvention") {
            id = "linecode.feature.convention"
            implementationClass = "cn.lineai.build.LineCodeFeatureConventionPlugin"
        }
    }
}
