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
| Test phone | **iQOO Neo 9 (vivo), OriginOS 6 = Android 16** (confirmed 2026-10-09). No emulator: Android Studio/SDK are not installed on this PC (owner's choice, "not now"). |
| Look | Native Android (Material 3, dynamic colour off; the course palette and layout as on iOS). Same features and texts, Android controls. |
| Languages | English + Simplified Chinese, with the iOS translations reused (§6). |
| Name and icon | **StudyHour / 学时** on Android only (the iOS app stays "Course Table / 课程表"); the iOS icon redrawn as an adaptive icon. `applicationId` stays `io.github.hilbert0619.coursetable`. |
| Builds | The app builds and tests on **Linux CI only** (no SDK here); `:core` also builds and tests on this PC with just the JDK (`settings.gradle.kts` leaves `:app` out without an SDK). |

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
| Class reminders (notification before class) | **One exact alarm** (`setExactAndAllowWhileIdle`, `USE_EXACT_ALARM`) for the next event of any alert; each sync (`AlertPlanner`) posts what is due now and sets the next one. Re-synced on start, every change, boot, app update, clock/time zone change; only for classes without the live notification | ColorOS/OriginOS/MagicOS may delay or drop alarms unless the app may run in the background (§5) |
| Class Live Activity (Dynamic Island, Lock Screen) | An **ongoing notification per class** from the reminder time: countdown to the start (`setUsesChronometer` + `setChronometerCountDown`), at the start re-posted silently as "Until 09:40" counting down to the end, removed at the end; after-class to-dos (`BigTextStyle`) and a Close action. On **Android 16+** it asks to be a promoted **Live Update** (`setRequestPromotedOngoing`), which ColorOS 16 / OriginOS / MagicOS may show in their status-bar capsule (流体云 / 原子岛 / 胶囊); colorized where the build uses Android 16's original promotion rules, and with the minutes left as text for the islands that ignore the chronometer (§8, 0.4.1). One closed or swiped away stays gone; one the system removed (restart) comes back silently | Android can switch and remove it on time (alarms run code), so no stale-date workaround or 8 h limit is needed. How each brand shows it must be checked on the phone |
| No duplicate alerts | The class notification **is** the live notification: one alert per class | Simpler than iOS |
| Widgets (Home: small/medium; Lock Screen widgets) | Jetpack **Glance** app widgets (small: current/next class; medium: rest of today), updated at class starts/ends | Android has no Lock Screen widgets: the ongoing notification covers the Lock Screen |
| Focus timer (Pomodoro, pause/skip/stop, history) | Port of `Focus` (plan + run) in `FocusEngine` (run in `SharedPreferences`); an ongoing notification (chronometer, Pause/Resume and Stop actions; Live Update on Android 16+); an exact alarm at each switch posts its notification. A **foreground service** (type `mediaPlayback`) only while a sound plays | Without a sound the app may be stopped in the background; the alarms and the notification's buttons start it again |
| Background sounds (9 CC0 loops, mix with music) | Media3 ExoPlayer, `REPEAT_MODE_ONE` with the same `.m4a` files (packed as assets from `CourseTable/Sounds`), **no audio focus** (mixes with music, like `.mixWithOthers`), a local wake lock | Check the seam on the phone |
| Words: sign-up/in by email link | `supabase-kt` Auth, implicit flow, deep link `io.github.hilbert0619.coursetable://auth-callback` (intent filter) — the same redirect the backend already allows | Some Chinese browsers ask before opening the app |
| Words: books, study session, modes, FSRS-6, search, favourites, hard words, stats, sync | Port of `VocabKit` (FSRS, Scheduler, StudyClock, DailyPlan, StudySession, AnswerGrader, ChoicePool, WordListImport, WordSearch, VocabStats, SyncMerge, AuthLink); Room for the vocabulary store (`vocabulary.db`); sync when the app comes to the front, after a session, on pull-to-refresh and with Sync Now (as on iOS, no background sync); charts drawn with Compose Canvas | — |
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
- Permissions: `POST_NOTIFICATIONS` (Android 13+), `USE_EXACT_ALARM` (Android 13+, granted at install; `SCHEDULE_EXACT_ALARM` up to 12), `POST_PROMOTED_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `WAKE_LOCK`,
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
  install the APK. The repo is public, so CI on standard GitHub runners is free and unlimited (since 2026-10-09).
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

## 8. Progress

- **Stage 1 (set-up): done 2026-10-09.** `android/` (core + app), `android.yml`, debug APK signed with a fixed debug key (repo secret `ANDROID_DEBUG_KEYSTORE`).
- **Stage 2 (`:core` timetable logic): done 2026-10-09.** All of CourseKit ported with its tests; shared cases in `spec/fixtures/` read by both test suites.
- **Stage 3 (timetable app): done 2026-10-09.** Room storage, Today with to-dos, week grid with placing, course editor, course list, import/export, settings, Glance widget; APK 0.2.0 (10). Tested on the phone with 0.3.0.
- **Stage 4 (alerts and focus): done 2026-10-10** (CI run 38066439361). One exact alarm for classes and to-dos
  (`AlertPlanner`), the live class notification, class reminders, to-do notifications, Settings → Reliable Reminders, the
  Focus tab with notifications, history and the 9 sounds; APK 0.3.0, **tested on the phone by the owner: no issues**.
- **Stage 5 (`:core` vocabulary logic): done 2026-10-10.** VocabKit ported to `android/core` (package
  `io.github.hilbert0619.vocabkit`) with its 74 tests (all but the 3 for `SeedSQL`, a PC-side tool that stays in Swift);
  new shared cases `spec/fixtures/vocab-*.json` (FSRS replays, seeded quizzes and sessions, grading, import, search),
  generated from the Swift implementation and checked by both test suites.
- **Stage 6 (Words app): built 2026-10-11** (CI runs 38113777448, 38114283979 on `android-stage-6`; main 38116324090). The
  Words tab: sign-up / sign-in / password reset by email link (implicit flow, `…://auth-callback`), the 考研 book and own
  lists (TXT/CSV with preview), Today card, study sessions in every question type, search, word page with US/UK
  speech, favourites, hard words, settings, statistics, sync, sign out and delete account. Account and sync logic in
  `android/core` (`VocabApp`, `SyncEngine`, `VocabModel`, `VocabStore`; 340 core tests), Room store `vocabulary.db`
  (v1), `SupabaseBackend` on supabase-kt 3.8.0 + Ktor 3.5.1 (OkHttp); 13 Words screenshots. APK 0.4.0 (4),
  **tested on the phone by the owner (2026-10-15): no major issues**. Users reported (second-hand, phones and OriginOS
  versions unknown) that vivo's Atom Island (原子岛) doesn't display StudyHour correctly; the owner hasn't seen it.
- **0.4.1 (5), 2026-10-15: the Atom Island, both likely causes.** Android 16 has two promotion rule sets, picked by
  the platform flag `ui_rich_ongoing`: the Live Update rules (asks for promotion, *not* colorized) and the first Android
  16 release's (colorized, no style or a promotable one). Our notifications met only the Live Update rules, so a build
  with the original rules never promoted them (nothing in the island). `LiveUpdates.build` now asks the phone's own `hasPromotableCharacteristics()` and posts
  the colorized variant when only that qualifies. The vivo, OPPO and Xiaomi islands ignore the countdown chronometer
  (they show the app name; measured by tigerduck-app/android-dynamic-island-test), so there the class and focus
  notifications carry "25 min" as short critical text, posted again at each minute by a non-waking exact alarm
  (`IslandCountdown`). Settings → Reliable Reminders → Send a Test Live Update shows whether the system promoted it.
  vivo's own 原子通知 API (`notification.superx.*` extras) is not used: it needs access granted by vivo by email.
  **Not yet confirmed on a phone** (checklist in `HANDOFF.md`: the owner's iQOO Neo 9 or a user's phone).
- **AI help and the agent (moved ahead of stage 7 at the owner's choice, 2026-10-15)**, stages in `docs/ai-spec.md` §5:
  **A1 done 2026-10-15**: AIKit ported to `android/core` (package `io.github.hilbert0619.aikit`: providers,
  connections, request bodies for both wire formats, SSE parser, stream decoding with tool calls, errors, prompts, to-do
  drafts, Markdown blocks, sentence words, tools and argument checks, the agent loop, the app's tools over an
  `AgentWorld` snapshot, timetable photos). 52 tests, ported from `AIKitTests`/`AIAgentTests` plus every shared case in
  `spec/fixtures/ai-*.json`; 392 core tests in all. Swift character counts (graphemes, CR LF as one) are kept by
  `characterCount`/`prefixCharacters`.
- **Next: A2 (keys, transport, Settings → AI, word help, "Why?")**; the island check of 0.4.1 and stage 7 (release)
  wait — see `HANDOFF.md`.

## 9. Open questions at the start (answered 2026-10-09)

- The phone: brand, model, Android version, ColorOS/OriginOS/MagicOS version.
- May Android Studio + SDK be installed, and where (D:)?
- Is Claude allowed to run Gradle locally (auto mode), or should CI build?
- Same app name ("Course Table" / 课程表) and icon on Android?
