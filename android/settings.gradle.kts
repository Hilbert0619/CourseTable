pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "StudyHour"

// :core is plain Kotlin (the port of CourseKit + VocabKit) and builds with just a JDK.
include(":core")

// :app needs the Android SDK. CI has one; a PC with only a JDK builds and tests :core alone.
val localSdkDir = File(settingsDir, "local.properties").takeIf { it.isFile }
    ?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }
if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null || localSdkDir != null) {
    include(":app")
} else {
    logger.lifecycle("No Android SDK (ANDROID_HOME or sdk.dir in local.properties): building :core only.")
}
