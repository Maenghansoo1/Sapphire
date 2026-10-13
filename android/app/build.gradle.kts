plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sapphire.hawe"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sapphire.hawe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    // 기기 테스트(그림 인식 모델)가 단위 테스트와 같은 골든 예시를 쓰도록 test/resources 를 androidTest 자산으로도 묶는다
    sourceSets { getByName("androidTest").assets.srcDir("src/test/resources") }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "API_BASE_URL",
                "\"${findProperty("haweApiBaseUrl") ?: "http://10.0.2.2:8080"}\"")
        }
        getByName("release") {
            // Placeholder until the production API domain is chosen; override with -PhaweApiBaseUrl.
            buildConfigField("String", "API_BASE_URL",
                "\"${findProperty("haweApiBaseUrl") ?: "https://api.hawe.app"}\"")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.browser)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.litert)  // 그림 인식(A8): TFLite 의 새 이름. 기기 안에서만 추론
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.coroutines.test)
}
