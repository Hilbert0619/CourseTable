# Session Handoff — CourseTable (Android stages 1–5 done · next: stage 6, the Words app)

Date: 2026-10-10 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (public), branch `main`

## Status

- **Android (StudyHour / 学时)**: stages 1–5 of [`docs/android-plan.md`](docs/android-plan.md) §7 are done and on `main`.
  - Stage 4 (alerts, Focus, Reliable Reminders; code map in [`android/README.md`](android/README.md) "Alerts and focus"):
    APK `C:\Users\AI\Downloads\CourseTable\StudyHour-0.3.0-debug-16.apk`, **tested on the phone by the owner: no issues**
    (OriginOS 6 menus, Live Updates, sounds and background alerts all fine).
  - Stage 5: VocabKit ported to `android/core` (package `io.github.hilbert0619.vocabkit`, 74 ported tests); new shared
    cases `spec/fixtures/vocab-*.json` (generated from the Swift implementation, checked by both suites — Swift
    `VocabSharedFixtureTests.swift`, Kotlin `VocabSharedFixtureTests.kt`). CI run: STAGE5_RUN. No app changes, so no
    new APK.
- **iOS**: app unchanged. Only `CourseKit/Tests/VocabKitTests/VocabSharedFixtureTests.swift` was added (reads the new
  fixtures). 0.3.1 (build 5) IPA built; the owner's device test of 0.3.1 was not reported. `docs/v3-development-record.md`
  still unwritten.

## Handoff prompt for the next session

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] CourseTable — Android stage 6: the Words tab (sign-in by email link, books, study, stats, sync)

## Read first
HANDOFF.md, docs/android-plan.md (§2 parity table: the Words rows; §7 stage 6), docs/vocabulary-spec.md (the Words
spec: sync §8, study rules §7, data §5), android/README.md. iOS reference: CourseTable/Vocabulary/ (VocabApp.swift;
Backend/ VocabBackend, SupabaseBackend, DemoBackend, VocabDemo; Store/ VocabModel, VocabRecords, SyncEngine; Views/
WordsTab, WordsHomeView, StudySessionView, WordDetailView, BooksView, VocabSettingsView, VocabStatsView,
WordComponents — ~3,500 lines), supabase/ (schema, RLS, seed), docs/v2-development-record.md (how the iOS Words app was
built and tested). The logic is already in android/core (package io.github.hilbert0619.vocabkit).

## Goal (stage 6 of docs/android-plan.md §7)
The Words tab on Android as on iOS: sign up / sign in / password reset by email link (deep link
io.github.hilbert0619.coursetable://auth-callback, AuthLink in core), the system book downloaded and cached, own books
(import TXT/CSV with preview), the Today card, study sessions in all modes (recognition, choice, spelling, mixed, new-word
page with "I know this"), search and word page with speech (TextToSpeech US/UK), favourites and hard words, settings,
statistics, sync (push/pull review logs, newest-wins settings/favourites/books), sign out and delete account. Then APK
0.4.0 for the owner to test with their account against the same Supabase project.

## Decided with me — don't reverse; ask if unsure
Everything in docs/android-plan.md §1 (native Kotlin + Compose, core port, the same Supabase project and schema
unchanged, supabase-kt, APK, StudyHour/学时 on Android only, iOS unchanged). Keep the repo secret ANDROID_DEBUG_KEYSTORE.
Don't create or change secrets without asking. No Android Studio/SDK on this PC: the app builds on CI only. Look up the
current supabase-kt and Ktor versions (and the Ktor engine that works on Android without Google Play services) first.

## How to work
Logic in android/core with tests first (it builds and tests here: ./gradlew :core:test); the app stays thin. Iterate the
app on a branch: push, `gh workflow run android.yml --ref <branch>`, read the run, fix, repeat; when green, fast-forward
main and push (main builds the APK). Download artifacts with gh run download (APK to C:\Users\AI\Downloads\CourseTable\,
screenshots to check). The Supabase publishable key and URL are public (VocabBackend.swift); never put secret keys,
passwords or tokens in chat or code. Short report at the end with run links; I test on the phone with my account.
```

## Facts and constraints

- Versions (checked 2026-10-09/10): Kotlin 2.4.21, AGP 9.4.1 (built-in Kotlin: no kotlin-android plugin), Gradle 9.8.1,
  compileSdk/targetSdk 37 (Android 17 stable), minSdk 26, Compose BOM 2026.09.00 (material3 1.4.0, ui 1.12.1), KSP
  2.3.12, Room 2.8.5, Lifecycle 2.11.0, Glance 1.2.0, core-ktx 1.19.1, Media3 1.11.1, kotlinx-serialization 1.11.0,
  Robolectric 4.17, Roborazzi 1.76.0, Espresso pinned 3.7.0. GitHub Actions: checkout v7, setup-java v6 (Temurin 21),
  gradle/actions v6, upload-artifact v7. `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19.
- Layout: `android/core` (packages `io.github.hilbert0619.coursekit` and `…vocabkit`, 322 tests incl. shared fixtures;
  RealContentTests read `content/kaoyan-ecdict.csv`); `android/app`
  packages `data` (Room `AppDatabase` v1 — schema JSON in `app/schemas/`, bump the version + add a migration for any
  change; `Repository` with `onChange`; focus history `FocusSessionRecord`), `alerts` (`AlertPlanner`, `Alerts`,
  `AlertNotifications`, `AlertReceiver`/`SystemEventReceiver`, `AlertPreferences`, `SystemSettings`), `focus`
  (`FocusEngine`, `FocusNotifications`, `FocusSoundService`, `FocusSound`/`SoundPlayer`, `FocusText`), `ui`
  (`AppViewModel`/`AppActions`/`AppData`, `NowSource`/`currentMoment()` — screenshots use a fixed clock, `Names`,
  `Permissions.kt`, `components`, `today`, `timetable`, `focus`, `settings`, `theme`), `widget` (Glance).
- App tests (`app/src/test`): `AlertPlannerTest` (plain JVM), `FocusEngineTest`, `RepositoryTest` (Robolectric),
  `ScreenshotTests` (22 screens incl. Focus, Settings → Reminders, Reliable Reminders).
- Strings: English in `app/src/main/res/values/strings.xml` (same English as iOS where possible);
  `python tools/xcstrings_to_android.py` generates `values-zh-rCN` from `Shared/Localizable.xcstrings` +
  `android/strings-zh.json`; CI runs it with `--check`.
- Shared test cases: `spec/fixtures/` (README there). A logic change goes into CourseKit *and* `android/core`, with the
  fixtures updated.
- CI `android.yml`: on push to main (paths android/**, spec/**, content/**, CourseTable/Sounds/**, the strings tool)
  and `workflow_dispatch`; runs the strings check, `:core:test :app:recordRoborazziDebug :app:assembleDebug`; artifacts
  StudyHour-debug-apk (~26 MB with the sounds), StudyHour-screenshots, StudyHour-room-schema, StudyHour-test-reports
  (on failure). A run takes ~8 min.
- Supabase (stage 6): project prkvpkxrugvbmrwxehhs, URL + publishable key in
  `CourseTable/Vocabulary/Backend/VocabBackend.swift`; redirect `io.github.hilbert0619.coursetable://auth-callback`.
  Never put secret keys, DB passwords, tokens or keystore passwords in chat or code.
- Release signing (stage 7): the owner decides how the release keystore is made and kept; switching from the debug
  key to it needs one uninstall on the phone (data can move by CSV export).

## Gotchas learned

- Local Gradle: there is no global Gradle. Use `android/gradlew` (it downloads Gradle 9.8.1 into `~/.gradle`) and pass
  the proxy as Gradle args, not `GRADLE_OPTS`:
  `./gradlew -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7892 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7892 :core:test`
  (only `:core` builds here). TLS hiccups to dl.google.com / raw.githubusercontent.com happen: retry.
  `google()` is limited to androidx/com.android/com.google groups in `settings.gradle.kts`.
- A local Gradle build that hangs (TLS stalls) leaves daemons holding `core/build/kotlin`: run `./gradlew --stop`,
  stop leftover `KotlinCompileDaemon` java processes, delete `android/core/build/kotlin`, and build with
  `--console=plain` to see progress. `swift test` in `CourseKit/` takes ~20 s here (VocabKit 87, CourseKit 144 tests).
- Robolectric on JDK 21 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` and
  `--add-opens=java.base/java.io=ALL-UNNAMED` (set in `app/build.gradle.kts`). In Robolectric, POST_NOTIFICATIONS is
  not granted and `canScheduleExactAlarms()` is false unless a test sets them (`ScreenshotTests.allowEverything`).
- Material Symbols drawables from google/material-design-icons carry `android:tint="?attr/colorControlNormal"`
  (AppCompat): remove that attribute after downloading — in some files it ends the `<vector` tag, so put the `>` back
  (check every drawable parses).
- Android 16 APIs behind feature flags may be missing from the SDK stubs (e.g. `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`):
  use the action string; `NotificationManager.canPostPromotedNotifications()` and `setRequestPromotedOngoing` are there.
- Don't name a Kotlin property `java` (it shadows the `java.time` package); Glance `defaultWeight()` is scoped to
  Row/Column (no import).
- Room ordering uses `createdAt`; `Repository.now()` hands out strictly increasing stamps.
- `SwipeToDismissBox` rows are keyed (`key(id)`), so a deleted row's swipe state doesn't move to the next one.
- Bash heredocs with quotes and `\'` broke once: write helper scripts with the Write tool, run them with python.
- Auto mode once blocked plain reads right after a secret was written (`gh secret set`); ask before secret changes.

## Environment notes (Windows 11, no Mac)

- Installed: Swift 6.4 (`swift test` in `CourseKit/` works), JDK 25, Git, gh (signed in as Hilbert0619), Node,
  Python 3.10 (default python; don't install into it). Not installed: Android Studio, Android SDK.
- GitHub needs Clash: `git -c http.proxy=http://127.0.0.1:7892 push`, `HTTPS_PROXY=http://127.0.0.1:7892` for gh/curl,
  per command only. In Git Bash, gh is at `/c/Program Files/GitHub CLI/gh.exe`.
- Never round-trip UTF-8 files through PowerShell 5.1; write helper scripts with the Write tool and run them with python.
- Commit email for this repo is set per repo (253035987+Hilbert0619@users.noreply.github.com).

## The iOS app (reference; change it only when I ask)

- Swift 6 + SwiftUI + SwiftData + WidgetKit + ActivityKit, iOS 17+. Tabs: Today, Timetable, Focus, Words, Settings.
- iOS release: bump MARKETING_VERSION/CURRENT_PROJECT_VERSION (16 places in project.pbxproj; 0.3.1 / 5 now),
  `gh workflow run ipa.yml`, download to `C:\Users\AI\Downloads\CourseTable\CourseTable-<version>.ipa`, installed
  with iloader.
- Loose ends (when I ask): my device test of 0.3.1; `docs/v3-development-record.md`; later Words items and the
  timetable backlog (long words in grid blocks, dark-mode screenshots, "All weeks" view, per-course reminder mute,
  school import, multiple terms).

## How I like to work

- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage, with run links. Verify for real (tests/CI/screenshots), don't trust
  "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.
- "Complete the handover" means: finish the docs so a new session starts the next thing; don't start it.
