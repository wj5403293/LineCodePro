plugins {
    id("linecode.convention")
}

android {
    namespace = "cn.lineai.share"
}

dependencies {
    implementation(project(":core-model"))
}
