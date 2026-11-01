plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

// 화면(Compose)만 담은 모듈. 상태(UiState)를 받아 그리기만 하므로 어떤 상태든 JVM에서 바로 렌더링해 볼 수 있다.
android {
    namespace = "app.kkambbak.ui"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    api(project(":core"))
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    api(composeBom)
    api("androidx.compose.ui:ui")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.material3:material3")
    api("androidx.compose.ui:ui-tooling-preview")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
