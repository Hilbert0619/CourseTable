# StudyHour (学时) for Android

The Android version of Course Table: Kotlin + Jetpack Compose (Material 3), the same features as the iOS app. Plan,
decisions and stages: [`docs/android-plan.md`](../docs/android-plan.md).

| Module | What it is |
|---|---|
| `core/` | Plain Kotlin (JVM 17, no Android): the port of `CourseKit` (package `io.github.hilbert0619.coursekit`: dates, term, periods, courses, timetable, week grid, CSV import/export, course list, reminders, live class windows, to-dos, focus) and later `VocabKit`, with their tests ported one for one, plus the shared cases in `spec/fixtures/`. JSON (kotlinx.serialization) in the same shapes as Swift's `Codable`. |
| `app/` | The Android app (`io.github.hilbert0619.coursetable`, minSdk 26, targetSdk 37): Compose UI, Robolectric + Roborazzi screenshot tests. |

## Building

- **CI** (`.github/workflows/android.yml`, Ubuntu): `:core:test`, `:app:recordRoborazziDebug` (JVM tests + screenshots)
  and `:app:assembleDebug`. The artifacts are the debug APK (`StudyHour-debug-apk`) and the screenshots.
- **With only a JDK** (no Android SDK): `./gradlew :core:test` builds and tests `core`; `settings.gradle.kts` leaves
  `app` out when no SDK is found.
- **With the Android SDK** (`ANDROID_HOME` or `sdk.dir` in `local.properties`): everything, as on CI.

## Porting notes

- Swift structs become Kotlin data classes; methods that mutate in Swift return a changed copy (`FocusRun.pause`).
- Swift `Date` becomes `java.time.Instant`, `TimeInterval` a `Double` of seconds.
- `ImportedText.decode` reads GB18030 on Android as on Apple platforms.

## Strings

English texts are in `app/src/main/res/values/strings.xml`. The Chinese file `values-zh-rCN/strings.xml` is generated:
`python tools/xcstrings_to_android.py` looks each English text up in the iOS catalog (`Shared/Localizable.xcstrings`);
Android-only texts are translated in `strings-zh.json`. CI fails if a translation is missing.

## Credits

Tab icons: [Material Symbols](https://github.com/google/material-design-icons) (Apache License 2.0). The launcher icon
is the iOS app's icon redrawn as vectors.
