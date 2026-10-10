import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's logic without Android: the Kotlin port of CourseKit and VocabKit (CourseKit/ in the repo root).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The shared test cases (spec/fixtures/) that the Swift tests read too.
    systemProperty("fixtures.dir", rootProject.file("../spec/fixtures").absolutePath)
    inputs.dir(rootProject.file("../spec/fixtures")).withPropertyName("fixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
