import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's logic without Android: the Kotlin port of CourseKit and VocabKit (CourseKit/ in the repo root), in the
// packages io.github.hilbert0619.coursekit and io.github.hilbert0619.vocabkit.
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
    // The Words store, model and sync engine (StateFlow, Mutex).
    api(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The shared test cases (spec/fixtures/) that the Swift tests read too.
    systemProperty("fixtures.dir", rootProject.file("../spec/fixtures").absolutePath)
    inputs.dir(rootProject.file("../spec/fixtures")).withPropertyName("fixtures").withPathSensitivity(PathSensitivity.RELATIVE)
    // The committed word list (content/kaoyan-ecdict.csv), checked by RealContentTests as on iOS.
    systemProperty("content.dir", rootProject.file("../content").absolutePath)
    inputs.file(rootProject.file("../content/kaoyan-ecdict.csv")).withPropertyName("wordList").withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
