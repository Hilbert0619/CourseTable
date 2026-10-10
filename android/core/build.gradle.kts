import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's logic without Android: the Kotlin port of CourseKit and VocabKit (CourseKit/ in the repo root).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        // Only Java 17 APIs, whatever JDK builds it (Android has no newer ones).
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
