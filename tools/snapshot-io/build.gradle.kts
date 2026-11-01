import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("org.jetbrains.kotlin.jvm") }

// 화면 렌더링 도구: Paparazzi 결과를 원본 해상도로 저장(JDK의 java.awt를 쓰므로 별도 JVM 모듈).
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    compileOnly("app.cash.paparazzi:paparazzi:1.3.5")
}
