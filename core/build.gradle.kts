import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

// 판정 엔진은 안드로이드에 의존하지 않는 순수 Kotlin 모듈이다(단위 테스트를 JVM에서 바로 돌린다).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    testImplementation("junit:junit:4.13.2")
}
