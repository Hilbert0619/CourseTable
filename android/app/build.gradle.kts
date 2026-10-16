plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // The AI store's saved connections, usage and answers (JSON).
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "io.github.hilbert0619.coursetable"
    compileSdk = 37

    defaultConfig {
        // The same id and URL scheme as the iOS app (the sign-in email links use io.github.hilbert0619.coursetable://).
        applicationId = "io.github.hilbert0619.coursetable"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "0.5.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // ML Kit's text recognizer (AI help) only for 64-bit ARM, every current phone (owner's choice, 2026-10-15):
            // its library is 7–12 MB per processor type. Elsewhere scanning reports itself unavailable.
            excludes += listOf("x86", "x86_64", "armeabi-v7a").map { "lib/$it/libmlkit_google_ocr_pipeline.so" }
            // Compressed in the APK (installed apps unpack them), so the file to share stays small.
            useLegacyPackaging = true
        }
    }

    androidResources {
        // English and Simplified Chinese, also offered in the system's per-app language setting (Android 13+).
        localeFilters += listOf("en", "zh-rCN")
        generateLocaleConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// The focus timer's background sounds are the iOS app's files (CourseTable/Sounds/sound-<id>.m4a), packed as assets.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory(rootProject.file("../CourseTable/Sounds").path)
    }
}

room {
    // The database schema of each version, committed, for migrations and their tests.
    schemaDirectory("$projectDir/schemas")
}

// Robolectric reaches into JDK internals (file descriptors for the shared memory of Android 16+).
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.media3.exoplayer)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.mlkit.text.recognition.chinese)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.espresso.core)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
