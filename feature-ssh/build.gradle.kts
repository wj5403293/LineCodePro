plugins {
    id("linecode.convention")
}

android {
    namespace = "cn.lineai.ssh"
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":core-model"))
    implementation(project(":core-security"))
    implementation(project(":data"))
    api(libs.jsch)
}
