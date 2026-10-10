# Session Handoff — CourseTable (Android stages 1–3 done · next: stage 4, alerts and focus)

Date: 2026-10-10 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (public), branch `main`

## Status

- **Android (StudyHour / 学时)**: stages 1–3 of [`docs/android-plan.md`](docs/android-plan.md) §7 are done and on
  `main` (CI green, run 38029525604). The timetable app works: Room storage, Today with to-dos, the week grid with
  placing, course editor, course list, import/export (UTF-8/UTF-16/GB18030), settings (term, period times, CSV
  export), a Glance home-screen widget. APK: `C:\Users\AI\Downloads\CourseTable\StudyHour-0.2.0-debug-10.apk`
  (installs over 0.1.0). **The owner hasn't reported a phone test yet.** No notifications, Focus or Words yet.
- **iOS**: unchanged this session. 0.3.1 (build 5) IPA built (`C:\Users\AI\Downloads\CourseTable\CourseTable-0.3.1.ipa`);
  the owner's device test of 0.3.1 was not reported. `docs/v3-development-record.md` still unwritten.
- The only change outside `android/`: `CourseKit/Tests/CourseKitTests/SharedFixtureTests.swift` (reads `spec/fixtures/`),
  `coursekit.yml` also runs on `spec/**` changes, `.gitattributes` keeps `spec/fixtures/*.csv` byte for byte.

## Handoff prompt for the next session

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] CourseTable — Android stage 4: class reminders, the live class notification, to-do notifications,
"reliable reminders", and the Focus tab (Pomodoro + 9 background sounds)

## Read first
HANDOFF.md (this file: status, design for stage 4, gotchas), docs/android-plan.md (decisions, parity table, stages),
docs/todos-focus-spec.md (the iOS spec for to-dos, focus, alerts), android/README.md. iOS reference code:
CourseTable/Reminders/ (ClassReminders, TodoReminders + Alerts.sync, ClassLiveActivities), CourseTable/Focus/
(FocusEngine, FocusView, SoundPlayer), Shared/ClassActivity.swift, Shared/FocusActivity.swift,
CourseTableWidget/ClassActivityWidget.swift. The logic is already ported in android/core (coursekit package:
upcomingReminders, upcomingActivityWindows, ClassActivityPlan.of, upcomingTodoAlerts, FocusPlan, FocusRun).

## Goal (stage 4 of docs/android-plan.md §7)
On the phone (iQOO Neo 9, OriginOS 6 = Android 16): a reminder or live notification for every class, to-do
notifications, a Focus tab that works like iOS (setup, running timer with Pause/Resume, Skip break, Stop, history,
notifications at every switch, sounds playing in the background and mixing with music), and a "Reliable reminders"
page so vivo's background limits don't eat the alerts. Then APK 0.3.0 for the owner to test.

## Decided with me — don't reverse; ask if unsure
Everything in docs/android-plan.md §1 (native Kotlin + Compose, core port, same Supabase, APK, StudyHour/学时 on
Android only, iOS unchanged). Keep the repo secret ANDROID_DEBUG_KEYSTORE (fixed debug key so APKs install over each
other). Don't create or change secrets without asking (auto mode flags it). No Android Studio/SDK on this PC: the app
builds on CI only.

## How to work
Iterate on a branch: push it, `gh workflow run android.yml --ref <branch>`, read the run, fix, repeat; when green,
fast-forward main and push (main builds the APK). Download artifacts with gh run download (APK to
C:\Users\AI\Downloads\CourseTable\, screenshots to check). Short report at the end of the stage with run links and
screenshots; I test on the phone.
```

## Stage 4 design (worked out, not built)

**Settings (per device)** — a small `SharedPreferences`-backed store with `StateFlow`s: class reminders on/off
(default off, as iOS), live class notification on/off (default on; iOS "Live Activity"), lead time (10; choices
0/5/10/15/20/30/45/60), focus settings/goal/sound/volume, the running `FocusRun` (JSON, `CourseKitJson`), the
signatures of live notifications posted (signature → class end, as iOS `CreatedActivities`), ids of reminders and
to-dos already notified (with expiry).

**One "next event" alarm** instead of many: a sync (`AlertSync`, pure decisions in a testable `AlertPlanner`) runs
on app start/foreground, every data change (`Repository.onChange`), settings changes, `BOOT_COMPLETED`, `TIME_SET`,
`TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`, and whenever the alarm fires. It posts/updates/cancels the notifications
due *now*, then sets one exact alarm (`setExactAndAllowWhileIdle`) for the next event: a window's show/start/end, a
reminder's fire time, a to-do's due time, or the next midnight as a safety net. Permissions: `USE_EXACT_ALARM`
(API 33+, granted without asking; fine for a sideloaded app) and `SCHEDULE_EXACT_ALARM` with maxSdkVersion 32;
`POST_NOTIFICATIONS` asked when turning reminders on, saving a timed to-do, or starting focus.
- **Live class notification** (setting on): `timetable.upcomingActivityWindows(now, lead, limit ~16, todos)` →
  `ClassActivityPlan.of(existing = our posted notifications, wanted, created, now, canSchedule = false)`: `startNow`
  → post (alerting, `setOnlyAlertOnce`), add to `created`; `update` → re-post silently; `end` → cancel; re-post open
  ones silently at the class start (content switches from countdown to "Until 09:40"). A notification the person
  swiped away stays in `created`, so it doesn't come back (as iOS). Content: title course name; text "08:00–09:40 ·
  A101 · Li" before the start, "Until 09:40 · A101 · Li" after; chronometer counting down (`setUsesChronometer`,
  `setChronometerCountDown`, `setWhen` = start, then end); "After class: …" to-dos line (BigTextStyle); a Close
  action; ongoing; `setTimeoutAfter` past the class end. **Android 16 Live Update**: `setRequestPromotedOngoing(true)`
  (+ `POST_PROMOTED_NOTIFICATIONS` permission; check the exact androidx.core API and promotion rules on
  developer.android.com "Live Updates" first — that lookup wasn't done), so OriginOS 6 may show it in 原子岛.
- **Class reminders** (live off, reminders on): a normal notification at the fire time ("Calculus" / "Starts in
  10 min · 08:00–09:40 · A101 · Li"), skipping classes in `covered`, posted once (tracked ids), only if the class
  hasn't started; `setTimeoutAfter` to the class end.
- **To-do notifications**: undone to-dos whose due time has come (within a 15-minute grace for late alarms), once
  each; title = to-do, text = "12:00" / "After Calculus · 09:40"; cancelled when ticked off or deleted.
- Channels: Class reminders (high importance), To-dos, Focus (high), Focus running (low, ongoing).
- **Reliable reminders page** (Settings → Reminders): notifications allowed (`areNotificationsEnabled` →
  `ACTION_APP_NOTIFICATION_SETTINGS`), exact alarms (`canScheduleExactAlarms`), battery optimisation
  (`isIgnoringBatteryOptimizations` → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, permission
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), vivo/iQOO auto-start and background power (try the i管家/permission-manager
  activities, fall back to `ACTION_APPLICATION_DETAILS_SETTINGS`, with written steps for OriginOS 6), "lock the app
  in Recents", and a "Send a test reminder" button (alarm in ~10 s).

**Focus** (port of `FocusEngine`): state in prefs; transitions with `FocusRun.pause/resume/skipBreak`; history in
Room `focus_sessions` (table exists; add Repository methods). Notifications at each switch from exact alarms (text as
iOS `FocusReminders.text`). An ongoing focus notification (phase title, chronometer to the phase end, Pause/Resume and
Stop actions via a receiver; Live Update on Android 16). A **foreground service** (type `mediaPlayback`,
permissions `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) only while a sound plays; it owns the focus
notification then. Sound: **Media3 ExoPlayer** (`media3-exoplayer`, 1.11.1 was current) `REPEAT_MODE_ONE`, no audio
focus (mixes with music, like iOS `.mixWithOthers`), files `CourseTable/Sounds/sound-<id>.m4a` added as app assets via
the variant API (`androidComponents.onVariants { it.sources.assets?.addStaticSourceDirectory(...) }`), played as
`asset:///sound-rain.m4a`; check the loop seam on the phone. Sound ids/names and the CC0 credits:
`CourseTable/Focus/SoundPlayer.swift`, `content/sounds/SOURCES.md`. Durations like "2 hr, 30 min" with
`android.icu.text.MeasureFormat` (short width). UI as iOS `FocusView` (setup, running ring, history).

**Strings**: most texts already exist in the iOS catalog (Class reminders, Remind me, At start time, %lld minutes
before, Starting now, Starts in %lld min, Until %@, After class: %@, Close, Open Settings, all Focus texts, sound
names). Android-only texts (live notification, reliable-reminders page, channel names) go in
`android/strings-zh.json`. iOS texts that say "iPhone" or "Live Activity" need Android wording + a zh override.

**Tests**: JVM tests for `AlertPlanner` (what to post/cancel and the next alarm for given times) and the focus engine
transitions; screenshot tests for the Focus tab (setup, running), Settings with reminders, the reliable-reminders page.
Notifications themselves are checked on the phone.

## Facts and constraints

- Versions (checked 2026-10-09): Kotlin 2.4.21, AGP 9.4.1 (built-in Kotlin: no kotlin-android plugin), Gradle 9.8.1,
  compileSdk/targetSdk 37 (Android 17 stable), minSdk 26, Compose BOM 2026.09.00 (material3 1.4.0, ui 1.12.1), KSP
  2.3.12, Room 2.8.5, Lifecycle 2.11.0, Glance 1.2.0, kotlinx-serialization 1.11.0, Robolectric 4.17, Roborazzi 1.76.0,
  Espresso pinned 3.7.0. GitHub Actions: checkout v7, setup-java v6 (Temurin 21), gradle/actions v6, upload-artifact
  v7. `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19.
- Layout: `android/core` (package `io.github.hilbert0619.coursekit`, 238 tests incl. shared fixtures);
  `android/app` packages `data` (Room `AppDatabase` v1 — schema JSON in `app/schemas/`, bump the version + add a
  migration for any change; `Repository` with `onChange`; `DemoData`), `ui` (`AppViewModel`/`AppActions`/`AppData`,
  `NowSource`/`currentMoment()` — screenshots use a fixed clock, `Names` for localized texts, `components`, `today`,
  `timetable`, `settings`, `theme`), `widget` (Glance `ScheduleWidget`, `ScheduleWidgetUpdates` inexact alarm).
- Strings: English in `app/src/main/res/values/strings.xml` (same English as iOS where possible);
  `python tools/xcstrings_to_android.py` generates `values-zh-rCN` from `Shared/Localizable.xcstrings` +
  `android/strings-zh.json`; CI runs it with `--check`.
- Shared test cases: `spec/fixtures/` (README there). A logic change goes into CourseKit *and* `android/core`, with the
  fixtures updated.
- CI `android.yml`: on push to main (paths android/**, spec/**, content/**, CourseTable/Sounds/**, the strings tool)
  and `workflow_dispatch`; runs the strings check, `:core:test :app:recordRoborazziDebug :app:assembleDebug`; artifacts
  StudyHour-debug-apk, StudyHour-screenshots, StudyHour-room-schema, StudyHour-test-reports (on failure).
- Supabase (stage 6): project prkvpkxrugvbmrwxehhs, URL + publishable key in
  `CourseTable/Vocabulary/Backend/VocabBackend.swift`; redirect `io.github.hilbert0619.coursetable://auth-callback`.
  Never put secret keys, DB passwords, tokens or keystore passwords in chat or code.
- Release signing (stage 7): the owner decides how the release keystore is made and kept; switching from the debug
  key to it needs one uninstall on the phone (data can move by CSV export).

## Gotchas learned

- Local Gradle: there is no global Gradle. Use `android/gradlew` (it downloads Gradle 9.8.1 into `~/.gradle`) and pass
  the proxy as Gradle args, not `GRADLE_OPTS`:
  `./gradlew -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7892 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7892 :core:test`
  (only `:core` builds here). TLS hiccups to dl.google.com happen: retry. `google()` is limited to androidx/com.android/
  com.google groups in `settings.gradle.kts`.
- Robolectric on JDK 21 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` and
  `--add-opens=java.base/java.io=ALL-UNNAMED` (set in `app/build.gradle.kts`).
- Material Symbols drawables from google/material-design-icons carry `android:tint="?attr/colorControlNormal"`
  (AppCompat): delete that line after downloading.
- Don't name a Kotlin property `java` (it shadows the `java.time` package); Glance `defaultWeight()` is scoped to
  Row/Column (no import).
- Room ordering uses `createdAt`; `Repository.now()` hands out strictly increasing stamps.
- `SwipeToDismissBox` rows are keyed (`key(id)`), so a deleted row's swipe state doesn't move to the next one.
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
