plugins {
    id("linecode.convention")
}

android {
    namespace = "cn.lineai.data"
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-security"))
    implementation(project(":core-api"))
    implementation(project(":ipc"))
    implementation(libs.json)
}
