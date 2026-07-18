plugins {
    id("linecode.convention")
}

android {
    namespace = "cn.lineai.ui.markdown"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":ui-theme"))
    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)
}
