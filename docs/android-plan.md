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
| Words on Android | **Hidden for now** (owner, 2026-10-22: users don't need it), from 0.7.1: `BuildConfig.WORDS = false` in `app/build.gradle.kts`. No Words tab, no sign-in link handling or Words sync, the Assistant without vocabulary tools, texts without Words. The code, its tests (the Words screenshots still run) and each phone's Words data (`vocabulary.db`, the account) stay; `true` brings it all back. The iOS app keeps Words. |

## 2. Feature parity

| iOS feature | Android implementation | Difference to expect |
|---|---|---|
| Tabs: Today, Timetable, Focus, Words, Settings | `NavigationBar` with the same five destinations | Words hidden on Android for now (§1) |
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
   - Added later at the owner's choice: AI help and the agent (0.5.0, done); the Countdown Island (0.5.1, branch);
     **catching up with iOS 0.6.0–0.7.1**, planned in [`android-port-0.6-0.7.md`](android-port-0.6-0.7.md), in two
     APKs: StudyHour 0.6.0 (icon, Focus wallpapers, Storage, the visual pass) and 0.7.0 (the glass look), both built
     2026-10-22.
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
  **Result (owner, 2026-10-16): nothing in the island with 0.4.1 or 0.5.0** on the owner's iQOO Neo 9, China OriginOS 6
  `PD2338_A_16.2.13.2.W10`. What vivo's documents say (dev.vivo.com.cn doc 894 access guide, 895 design rules, 896
  technical spec): the Atom Island / 原子通知 for third-party apps is vivo's own API (`notification.superx.*` extras on a
  local notification, updates also through vivo push), and **vivo must grant it per app and per scene**: the app must
  already be on the vivo App Store; email `oosyztz@vivo.com` (app name, package, contact, scene, a requirements document
  on vivo's template, a mock-up), 7–10 working days' review, a joint requirements and UI review, then vivo opens the
  permission, joint testing, and a launch rolled out model by model. The system reports an app or scene without it as
  "当前应用或场景未开通原子通知权限" (receipt 32008). vivo's documents never mention Android 16 Live Updates. The only
  vivo phone known to put a plain Android 16 Live Update in the island runs the **global** OriginOS 6
  (`PD2512F_EX_A_16.1.13.4`, tigerduck-app/android-dynamic-island-test); on China builds, approved apps only (JD,
  Taobao Shangou, Meituan, 12306, Amap…). So a debug APK from outside the vivo App Store can't use the China island.
  Listing on the vivo App Store would need stage 7's release signing, a software copyright (软著) and App 备案 in the
  owner's name, and for the AI features vivo's deep-synthesis qualification (review rules 3.1.5, 3.2.14). The AOSP
  Live Update code stays: it costs nothing and works on global OriginOS, ColorOS, HyperOS and MagicOS. **The owner chose (2026-10-16) to stop here: no vivo application, no more island work; re-test after OriginOS updates (`HANDOFF.md`). A floating pill or an accessibility-drawn island were considered and not chosen.**
- **AI help and the agent (moved ahead of stage 7 at the owner's choice, 2026-10-15)**, stages in `docs/ai-spec.md` §5:
  **A1 done 2026-10-15**: AIKit ported to `android/core` (package `io.github.hilbert0619.aikit`: providers,
  connections, request bodies for both wire formats, SSE parser, stream decoding with tool calls, errors, prompts, to-do
  drafts, Markdown blocks, sentence words, tools and argument checks, the agent loop, the app's tools over an
  `AgentWorld` snapshot, timetable photos). 52 tests, ported from `AIKitTests`/`AIAgentTests` plus every shared case in
  `spec/fixtures/ai-*.json`; 392 core tests in all. Swift character counts (graphemes, CR LF as one) are kept by
  `characterCount`/`prefixCharacters`.
  **A2 done 2026-10-15** (`app/…/ai`, `app/…/ui/ai`): keys AES-GCM-encrypted with an Android Keystore key
  (`KeystoreAIKeys`, the `ai_keys` preferences left out of backups), `AIStore` (connections, default, monthly usage,
  saved answers in `files/ai/answers.json`), `KtorAITransport` (OkHttp engine; bytes into `SSEParser`, error bodies,
  a whole answer when a proxy doesn't stream) and `DemoAITransport` for tests, `AIRun` (Compose state), Settings → AI
  Help with the connection editor and its test (answer, tool call, red picture), word help on the word page, "Why?"
  after a wrong choice or spelling. **A3 done 2026-10-15**: Words → AI Tools → Long Sentences and Writing Feedback,
  Quick Add in a new to-do, Photo/Camera scanning with **ML Kit** (`com.google.mlkit:text-recognition-chinese`
  16.0.1, bundled model, no Play services; owner's choice: the recognizer's library only for 64-bit ARM, others report
  scanning unavailable; native libraries compressed in the APK). Debug APK 31 → 40 MB.
  **A4 done 2026-10-15** (CI run 38828069559 on `android-ai`): the Assistant (`AssistantScreen` + `AgentRun`; ✨ on
  Today and Words) with the app's tools: lookups as lines, every change as a card to confirm, Undo, photos, earlier
  chats in `files/ai/conversations.json`; `AgentContext` (in `RootScreen`) reads and changes to-dos, courses, focus and
  Words. Import → From a Photo (AI) with the photo's period times (`TimetablePhotoReader`: a picture model, or ML Kit
  text with positions); Words → Statistics → Analyse My Progress. Screenshots of every AI screen, English and Chinese.
  **APK 0.5.0 (6)** (main run 38829317137, 40 MB); real keys, the Keystore and ML Kit can only be checked on the phone (checklist in `HANDOFF.md`).
- **0.6.0 (8), 2026-10-22: catching up with iOS 0.6.0–0.7.0** ([`android-port-0.6-0.7.md`](android-port-0.6-0.7.md);
  the owner chose two APKs: this one, then 0.7.0 with the glass look). `:core`: `CourseColors`,
  `PeriodSchedule.gridPosition`, `FocusWallpapers`, `FolderUsage`, `SoftWallpaper` + `BackgroundChoice` (the last for
  0.7.0), tested against `course-colors.json`, `focus-wallpapers.json` and the new shared `backgrounds.json` (434 core
  tests). The app: warm paper and white cards (`Theme.kt`), soft course blocks with a stripe (Solid as a setting), the
  class in progress outlined, the red now-line, block text that shrinks long words; Today's Now / Next card; the Focus
  plan card (ring, 25 / 50 / 90 / 2 hr), the folded Rhythm row, full screen (screen kept on, bars hidden), a haptic at
  each switch; Focus wallpapers (`WallpaperStore`: the list from `focus_wallpapers` with the publishable key, pictures
  in the cache folder `Wallpapers`, one downloaded ahead, not with Data Saver on a metered network); swipe to delete a
  session, All Sessions with Clear History; Settings → Timetable Appearance (Soft / Solid, a background photo from the
  photo picker or a seen wallpaper, Fade, Block opacity) and Settings → Storage; the Words ring and chips; AI Help's
  privacy in three lines with What's Sent; a bounce and a haptic when a to-do is ticked off; the iOS 0.6 icon as the
  adaptive icon (the picture at 66 of 108 units, its edges repeated outward, a drawn themed glyph).
- **0.7.0 (9), 2026-10-22: the glass look of iOS 0.7.1**, with **Haze 2.0.1** (the owner's choice; glass by default).
  `RootScreen` draws the background once behind Today, Timetable, Focus and Words (behind the timetable alone in the
  classic look) and captures it with `hazeSource`; the tab bar is frosted. `ui/components/Glass.kt`: glass
  (`hazeGlass`: blur, saturation, refraction and specular light on Android 13+, a tint and a soft rim before) plus the
  owner's 10 % white layer and 1-pixel bright edge; frosted sections (`hazeBlur` with a veil of the paper); glass
  buttons (prominent ones on a solid accent so white text reads), chips, the Now / Next card, the Focus plan and
  countdown cards, Words' Today card and glass course blocks (soft 75 %, solid 95 % of Block opacity); the timetable's
  days and times on thicker frosting, and the top bars too over a photo. Screens opt in (`GlassScope`); pushed screens,
  dialogs (`EditorDialog` → `PaperScope`) and Settings keep the paper. Settings → Appearance replaces Timetable
  Appearance: Glass / Classic, Paper, your photo, the six built-in wallpapers (`SoftWallpaperView`: elliptical
  gradients, as on iOS), a seen Focus wallpaper, Fade (its own value in each look), Soft / Solid, Block opacity. A 0.6.0
  photo carries over (`BackgroundChoice.resolve`).
- **0.7.1 (10), 2026-10-22: Words hidden** (the owner's call: users don't need it; §1). `BuildConfig.WORDS = false`:
  `MainActivity` makes no `WordsController` (the Words database and account aren't opened), `RootScreen` shows four
  tabs, a sign-in link just opens the app; the Assistant gets `AppTools.tools(words = false)` and
  `AIPrompts.assistant(…, words = false)` (no vocabulary, tested in `:core`), no Share My Hard Words, no words
  suggestion and no study tools; Appearance, Storage and AI Help texts without Words (`…_no_words` strings).
- **Waiting**: the owner's tests of 0.7.1 / 0.6.0 and of 0.5.0 with a real key, the island check of 0.4.1, then stage 7
  (release) — see `HANDOFF.md`.

## 9. Open questions at the start (answered 2026-10-09)

- The phone: brand, model, Android version, ColorOS/OriginOS/MagicOS version.
- May Android Studio + SDK be installed, and where (D:)?
- Is Claude allowed to run Gradle locally (auto mode), or should CI build?
- Same app name ("Course Table" / 课程表) and icon on Android?
