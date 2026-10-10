# Session Handoff — CourseTable (Android stages 1–4 built · 0.3.0 waiting for the phone test · next: stage 5)

Date: 2026-10-10 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (public), branch `main`

## Status

- **Android (StudyHour / 学时)**: stages 1–4 of [`docs/android-plan.md`](docs/android-plan.md) §7 are built and on
  `main` (CI green, run 38066439361). Stage 4 added the class and to-do alerts, the Focus tab with sounds and Settings →
  Reliable Reminders (design and code map: [`android/README.md`](android/README.md) "Alerts and focus"). APK:
  `C:\Users\AI\Downloads\CourseTable\StudyHour-0.3.0-debug-16.apk` (installs over 0.1.0/0.2.0).
  **The owner hasn't reported a phone test of 0.2.0 or 0.3.0 yet** — the checklist below. No Words yet.
- **iOS**: unchanged. 0.3.1 (build 5) IPA built (`C:\Users\AI\Downloads\CourseTable\CourseTable-0.3.1.ipa`); the
  owner's device test of 0.3.1 was not reported. `docs/v3-development-record.md` still unwritten.
- Outside `android/` only docs changed this session (`docs/android-plan.md` §2, §5, §8; this file).

## Phone test of 0.3.0 (iQOO Neo 9, OriginOS 6) — what to check, what's unknown

1. First launch asks for notifications (once). Settings → Reminders → **Reliable Reminders**: every row green?
   "Open Auto-Start Settings" opens vivo's page or App info? (The vivo component names are guesses from
   dontkillmyapp/AutoStarter; the written OriginOS steps are best guesses too — get the real menu names.)
   **Send a Test Reminder**, lock the phone: does it come after ~10 s?
2. **Live class notification**: add a class starting in ~15 min (Remind me: 10 min). At the reminder time an alert with
   a countdown; at the start it turns into "Until …"; at the end it goes. Does OriginOS show it in **原子岛** / the
   status-bar chip (Live Update)? Close button and swipe-away: it must not come back. A to-do "after" that class shows
   in it. Then turn the live notification off and Class reminders on: a plain reminder at the reminder time instead.
3. **To-dos** at a time and after a class: a notification at the time; ticking it off removes it.
4. **Focus**: start with a sound, lock the phone, play music in another app: both play (mix)? The loop seam audible?
   Pause/Resume and Stop from the notification; a notification at each switch (set Focus block 10 min to test);
   history after Stop / finish. Live Update for the focus notification?
5. Swipe StudyHour away from Recents and wait for an alert: does it still come (vivo background limits)? Reboot: do
   alarms come back (BOOT_COMPLETED needs auto-start on vivo)?

## Handoff prompt for the next session

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] CourseTable — first the owner's phone test of StudyHour 0.3.0 (fix what it finds), then Android
stage 5: port VocabKit to android/core

## Read first
HANDOFF.md (status, the 0.3.0 test checklist, gotchas), docs/android-plan.md (decisions, parity table, stages),
android/README.md, docs/vocabulary-spec.md. Swift: CourseKit/Sources/VocabKit (16 files, ~2,000 lines) and
CourseKit/Tests/VocabKitTests (89 Swift Testing tests). The Kotlin port of CourseKit (android/core, package
io.github.hilbert0619.coursekit) shows the porting style.

## Goal
1. If I report results of the 0.3.0 phone test, fix them first (alerts: android/app/.../alerts, AlertPlanner +
   AlertPlannerTest; focus: .../focus, FocusEngineTest), then APK 0.3.1.
2. Stage 5 (docs/android-plan.md §7): VocabKit in android/core — FSRS-6 (checked against the reference values), Scheduler,
   StudyClock (study day from 04:00), DailyPlan, StudySession, AnswerGrader, Choice, WordListImport, WordSearch,
   VocabStats, SyncMerge, AuthLink, Card/WordEntry — each Swift test ported with the same name and expectations, and the
   cases that matter for "same behaviour" (FSRS reference values, sync merge, grading) moved into spec/fixtures/ for both
   test suites. Decide whether ECDICT/SeedSQL (content tools for the word list) are needed on Android at all; ask if
   unsure. :core builds and tests on this PC (JDK only) — iterate locally, then CI.

## Decided with me — don't reverse; ask if unsure
Everything in docs/android-plan.md §1 (native Kotlin + Compose, core port, same Supabase, APK, StudyHour/学时 on
Android only, iOS unchanged). Keep the repo secret ANDROID_DEBUG_KEYSTORE. Don't create or change secrets without
asking. No Android Studio/SDK on this PC: the app builds on CI only.

## How to work
Iterate on a branch: push it, `gh workflow run android.yml --ref <branch>`, read the run, fix, repeat; when green,
fast-forward main and push (main builds the APK). Download artifacts with gh run download (APK to
C:\Users\AI\Downloads\CourseTable\, screenshots to check). Short report at the end of the stage with run links; I test on
the phone.
```

## Facts and constraints

- Versions (checked 2026-10-09/10): Kotlin 2.4.21, AGP 9.4.1 (built-in Kotlin: no kotlin-android plugin), Gradle 9.8.1,
  compileSdk/targetSdk 37 (Android 17 stable), minSdk 26, Compose BOM 2026.09.00 (material3 1.4.0, ui 1.12.1), KSP
  2.3.12, Room 2.8.5, Lifecycle 2.11.0, Glance 1.2.0, core-ktx 1.19.1, Media3 1.11.1, kotlinx-serialization 1.11.0,
  Robolectric 4.17, Roborazzi 1.76.0, Espresso pinned 3.7.0. GitHub Actions: checkout v7, setup-java v6 (Temurin 21),
  gradle/actions v6, upload-artifact v7. `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19.
- Layout: `android/core` (package `io.github.hilbert0619.coursekit`, 238 tests incl. shared fixtures); `android/app`
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
