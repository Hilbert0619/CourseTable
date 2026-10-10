# CourseTable for Android — decisions and plan

Decided with the owner on 2026-10-09. Goal: **the same app on Android, with the functionality as unchanged as
possible.** The iOS app stays as it is and remains the reference: where this plan doesn't say otherwise, Android
does what iOS does (see `README.md`, `docs/vocabulary-spec.md`, `docs/todos-focus-spec.md`).

## 1. Decisions

| Topic | Decision |
|---|---|
| Approach | **Native Android: Kotlin + Jetpack Compose (Material 3)**, the counterpart of Swift + SwiftUI. Not Kotlin Multiplatform (it would replace the iOS app's tested Swift logic) and not Swift-on-Android (young tooling, JNI bridge, ~30 MB runtime, unproven from Windows). |
| Logic | `CourseKit` and `VocabKit` (~4,100 lines of Swift, ~220 tests) are **ported to a pure Kotlin module, `:core`**, with their tests ported one for one. The two code bases are kept in step by the same tests and shared test cases (§4). |
| Repository | The same repo: a new `android/` folder. Docs, the backend (`supabase/`), the word list and the sounds (`content/`, `CourseTable/Sounds/`) stay shared. |
| Backend | **The same Supabase project and schema, unchanged** (`supabase/migrations`, row-level security). Android uses the official Kotlin client (`supabase-kt`). One account works on both platforms; answers sync between them. |
| Timetable data | Local on the device, as on iOS. Moving between phones: the existing CSV export/import. |
| Distribution | **APK files** first, built and signed by CI and installed directly (like the IPA). No store for now. |
| Test phone | An **OPPO / OnePlus / vivo / Honor** phone (ColorOS / OxygenOS / OriginOS / MagicOS). Exact model and Android version: ask at the start. The Android emulator on this PC for everything else. |
| Look | Native Android (Material 3, dynamic colour off; the course palette and layout as on iOS). Same features and texts, Android controls. |
| Languages | English + Simplified Chinese, with the iOS translations reused (§6). |

## 2. Feature parity

| iOS feature | Android implementation | Difference to expect |
|---|---|---|
| Tabs: Today, Timetable, Focus, Words, Settings | `NavigationBar` with the same five destinations | — |
| Courses, editor, clashes, colours, term, period times | Compose screens; Room database (`courses`, `settings`, `course_templates`) | — |
| Import CSV/text/file (UTF-8, UTF-16, GB18030), preview, export CSV | Port of `CSV`, `CourseImport`, `ImportPlan`; files via the system picker (`ACTION_OPEN_DOCUMENT`), export via share sheet (`ACTION_SEND`, FileProvider) | — |
| Course list + placing on the grid | Port of `CourseTemplate`; the same placing mode | — |
| Weekly grid (swipe weeks, tap a slot) | Compose `HorizontalPager` + custom grid layout (port of `WeekGrid`) | — |
| Today: day switcher, classes + to-dos in order | Port of `DayAgenda`; same screen | — |
| To-dos (any date; any time / at a time / after a class) | Room table `todos`; same editor | — |
| Class reminders (notification before class) | `AlarmManager.setExactAndAllowWhileIdle` per class (permission `SCHEDULE_EXACT_ALARM`, asked for), re-scheduled on boot/time change (`BOOT_COMPLETED`, `TIME_SET`); no 64 limit | ColorOS/OriginOS/MagicOS may delay or drop alarms unless the app may run in the background (§5) |
| Class Live Activity (Dynamic Island, Lock Screen) | An **ongoing notification per class**, posted by an alarm at the reminder time: countdown (`setUsesChronometer` + `setChronometerCountDown`), at the start an alarm switches it to "Until 09:40" + progress, at the end an alarm removes it; Close action. On **Android 16+**: a promoted **Live Update** (`Notification.ProgressStyle`, `setRequestPromotedOngoing`), which ColorOS 16 / OriginOS / MagicOS show in their status-bar capsule (流体云 / 原子岛 / 胶囊) | Android can switch and remove it on time (alarms run code), so no stale-date workaround or 8 h limit is needed. How each brand shows it must be checked on the phone |
| No duplicate alerts | The class notification **is** the live notification: one alert per class | Simpler than iOS |
| Widgets (Home: small/medium; Lock Screen widgets) | Jetpack **Glance** app widgets (small: current/next class; medium: rest of today), updated at class starts/ends | Android has no Lock Screen widgets: the ongoing notification covers the Lock Screen |
| Focus timer (Pomodoro, pause/skip/stop, history) | Port of `Focus` (plan + run); a **foreground service** (type `mediaPlayback` while a sound plays, else `specialUse`) with an ongoing notification (chronometer, Pause/Stop actions; Live Update on Android 16+); exact alarms for phase switches | — |
| Background sounds (9 CC0 loops, mix with music) | Media3 ExoPlayer, `REPEAT_MODE_ONE` (gapless with the same `.m4a` files) or `AudioTrack` with the decoded loop; audio focus "may duck" | Check the seam on the phone |
| Words: sign-up/in by email link | `supabase-kt` Auth, implicit flow, deep link `io.github.hilbert0619.coursetable://auth-callback` (intent filter) — the same redirect the backend already allows | Some Chinese browsers ask before opening the app |
| Words: books, study session, modes, FSRS-6, search, favourites, hard words, stats, sync | Port of `VocabKit` (FSRS, Scheduler, StudyClock, DailyPlan, StudySession, AnswerGrader, ChoicePool, WordListImport, WordSearch, VocabStats, SyncMerge, AuthLink); Room for the vocabulary store; WorkManager for background sync; charts with Compose Canvas or Vico | — |
| Speech (US/UK) | `TextToSpeech` with `Locale.US` / `Locale.UK` | Some phones lack English voices: show a hint |
| Notifications while the app is open | Default on Android | — |

## 3. Architecture

```
android/
  settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradlew (wrapper)
  core/   Kotlin/JVM library, no Android dependencies: the port of CourseKit + VocabKit, with JUnit tests
  app/    the Android app: Compose UI, Room, DataStore, alarms, notifications, the focus service, Glance widgets,
          supabase-kt, Media3; instrumented + Robolectric tests; Roborazzi screenshots
```

- `applicationId` `io.github.hilbert0619.coursetable`; URL scheme as on iOS.
- `minSdk 26` (Android 8.0), `targetSdk` = the newest stable (36 / Android 16 at the time of writing): check at start.
- Versions (Kotlin 2.x, AGP, Compose BOM, Room, WorkManager, Glance, Media3, supabase-kt, Ktor) are looked up at the
  start of the work, not taken from memory.
- Dates and times: `core` keeps the iOS model: `LocalDate` and `TimeOfDay` without time zones (use `java.time`
  `LocalDate`/`LocalTime` underneath or our own types with the same arithmetic), study day starting at 04:00.
- Same rules as on iOS: logic in `core` with tests first; the app stays thin.

## 4. Keeping both platforms the same

- Each Swift test in `CourseKit/Tests` is ported to a Kotlin test with the same name and the same expectations.
- Test cases that matter most for "same behaviour" go into **shared JSON fixtures** (`spec/fixtures/`) that both test
  suites read: FSRS-6 reference values, import samples (CSV/text, GB18030), reminder and Live Activity windows, the
  Pomodoro plan, to-do ordering, sync merge. Start by extracting them from the Swift tests.
- From now on, a feature is specified once (`docs/`), and its logic change lands in both `CourseKit`/`VocabKit` and
  `:core` with the shared fixtures updated.

## 5. Android specifics to handle

- **Background limits on ColorOS / OriginOS / MagicOS**: apps are stopped in the background unless allowed. Add a
  Settings → "Reliable reminders" page that checks and links to: notifications allowed, exact alarms allowed, battery
  optimisation off (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), and the brand's auto-start / background page
  (see dontkillmyapp.com for each brand). Verify on the owner's phone.
- Permissions: `POST_NOTIFICATIONS` (Android 13+), `SCHEDULE_EXACT_ALARM` (Android 12+, user grants it on 14+),
  `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`.
- No Google Play services (most phones in China lack them): no FCM, no Play-only APIs. Everything above works
  without them.
- Strings: Android needs resource ids, not English keys. Write `tools/xcstrings_to_android.py` to look up each
  English string's zh-Hans translation in `Shared/Localizable.xcstrings` while the Android strings are created, and
  a check like `tools/check_strings.py` for `values/` vs `values-zh-rCN/`.
- Sounds: use `CourseTable/Sounds/*.m4a` (copy at build time or move to a shared folder); same credits.
- App icon: reuse the iOS icon as an adaptive icon.

## 6. Environment and CI

- On this PC (checked 2026-10-09): **no Android Studio and no Android SDK**; JDK 25 (Oracle and Adoptium) on PATH.
  Android Studio bundles its own JDK (21) and the SDK manager; the emulator needs virtualisation (Hyper-V is on).
  Installing Android Studio + SDK + an emulator image (~10 GB, best on D:) needs the owner's go-ahead.
- Auto mode blocked Claude from running downloaded third-party binaries before (Supabase CLI, a Swift package).
  Gradle downloads and runs plugins and compilers, so **local Gradle builds by Claude may be blocked**; check early.
  Fallback: build and test on GitHub Actions **Linux** runners and let the owner run the app from Android Studio or
  install the APK. The free plan has one pool of 2,000 minutes a month: Linux counts 1×, macOS 10×. October's iOS
  work used about 1,700 of it, so until the reset only ~250 Linux minutes are left (an Android CI run takes ~3–5).
- CI: `android.yml` on Ubuntu, path-filtered to `android/**` (and `content/`, `CourseTable/Sounds/`, `spec/`):
  `./gradlew :core:test :app:testDebugUnitTest :app:verifyRoborazziDebug :app:assembleDebug`; a manual `apk.yml`
  builds a signed release APK (keystore and passwords as GitHub secrets, created by the owner; never in chat).
- Screenshots without an emulator: **Roborazzi** (Compose on Robolectric, on the JVM) on Linux CI, in English and
  Chinese, light and dark; the emulator/phone for system UI (notifications, Live Updates, widgets).

## 7. Stages

1. **Set-up**: confirm the phone (model, Android version) and what Claude may run locally; Android Studio/SDK
   installed (owner); `android/` skeleton with `core` + `app`, CI, a first debug APK that opens on the phone.
2. **`:core` timetable logic**: port CourseKit (dates, semester, periods, courses, timetable, week grid, CSV import/
   export, course list, reminders, class windows, to-dos, focus) with its tests + the first shared fixtures.
3. **Timetable app**: storage (Room), Today (day switcher, to-dos), Timetable grid + placing, editor, import/export,
   Settings; Glance widgets; screenshots.
4. **Alerts and focus**: exact alarms, the class ongoing notification / Live Update with to-dos, to-do notifications,
   the "reliable reminders" page, the focus service + sounds; tested on the phone.
5. **`:core` vocabulary logic**: port VocabKit (FSRS-6 against the reference values) with its tests.
6. **Words app**: supabase-kt (auth by email link, book download, sync), Room store, all Words screens, speech,
   statistics; tested against the same cloud project with the owner's account.
7. **Release**: signed APK 0.3.x for Android, device test, docs (README, an Android development record, HANDOFF).

Each stage ends with a short report, run links, and screenshots; the owner tests on the phone at the end of stages
1, 4, 6 and 7.

## 8. Open questions for the start

- The phone: brand, model, Android version, ColorOS/OriginOS/MagicOS version.
- May Android Studio + SDK be installed, and where (D:)?
- Is Claude allowed to run Gradle locally (auto mode), or should CI build?
- Same app name ("Course Table" / 课程表) and icon on Android?
