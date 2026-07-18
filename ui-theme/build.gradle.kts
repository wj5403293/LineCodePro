plugins {
    id("linecode.convention")
}

android {
    namespace = "cn.lineai.ui.theme"
}

dependencies {
    implementation(project(":core-model"))
}
