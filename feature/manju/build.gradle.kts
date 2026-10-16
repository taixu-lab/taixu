plugins {
    alias(libs.plugins.taixu.android.feature)
}

android {
    namespace = "top.wkbin.taixu.feature.manju"
    resourcePrefix = "fmanju_"
}

dependencies {
    implementation(project(":runtime"))
    // ProviderClient：分镜拆解复用太墟已配置的模型，无需额外 API Key
    implementation(project(":harness"))
    // 出图配置（加密存储）与 HTTP 通道
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.test.robolectric)
    testImplementation(libs.koin.test)
}
