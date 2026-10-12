# Session Handoff — CourseTable (Android stages 1–6 built · next: the owner's test of 0.4.0, then stage 7)

Date: 2026-10-11 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (public), branch `main`

## Status

- **Android (StudyHour / 学时)**: stages 1–6 of [`docs/android-plan.md`](docs/android-plan.md) §7 are on `main`.
  - Stage 4 (alerts, Focus, Reliable Reminders): APK 0.3.0, **tested on the phone by the owner: no issues**.
  - Stage 5: VocabKit ported to `android/core` with shared cases `spec/fixtures/vocab-*.json`.
  - **Stage 6 (the Words tab): built, waiting for the owner's phone test.** APK
    `C:\Users\AI\Downloads\CourseTable\StudyHour-0.4.0-debug-20.apk` (0.4.0, versionCode 4; installs over 0.3.0, timetable data stays).
    CI: branch runs 38113777448 and 38114283979 (`android-stage-6`), main 38116324090. Code map:
    [`android/README.md`](android/README.md) "Words". Checklist below.
- **iOS**: 0.3.1 passed the owner's device test (2026-10-11); the owner published GitHub Releases themselves (IPAs
  0.1.0–0.3.1 and the Android 0.4.0 *debug* APK renamed `StudyHour-0.3.1.apk`; every tag points at e6479e7, not the
  build commits; no LICENSE yet). Those release fixes wait for the owner's OK. `docs/v3-development-record.md` is still
  unwritten.
- **AI help** ([`docs/ai-spec.md`](docs/ai-spec.md), bring your own key), built on iOS 2026-10-12 as 0.4.0 (build 6):
  AIKit + settings, word help, "Why?", long sentences, writing feedback, quick-add to-dos, Assistant. All AI UI tests
  pass (screenshots run 38291069568); IPA run 38293172237 (commit 16570b5), saved as
  `C:\Users\AI\Downloads\CourseTable\CourseTable-0.4.0.ipa`. Waiting for the owner's device test with a real key. Android gets it after its stage 7, porting AIKit to `android/core` against the
  shared cases `spec/fixtures/ai-*.json`.

## Phone test of 0.4.0 (owner)

Use the account you already use on the iPhone (same Supabase project). The built-in Supabase email only reaches the
project team's addresses, a few an hour, so a brand-new account with another address won't get its email.

1. **Install** over 0.3.0. The Today/Timetable/Focus tabs and your courses are unchanged.
2. **Sign in** on the Words tab. The 考研 book downloads (~4,800 words, a few seconds). Today's numbers, "Learned … of …"
   and the streak should match the iPhone after both have synced.
3. **Email link**: Forgot password? → open the email **on the phone** → the link should open StudyHour (if the
   browser asks "Open with StudyHour?", allow it) → choose a new password. A link opened twice says it has expired.
4. **Study** a session (Start on the Today card): new words with "I Know This", recall-and-rate, multiple choice,
   spelling (Settings → Review questions switches the type). Close the session, open Words on the iPhone, pull to
   refresh: the answers are there. Answer on the iPhone, come back to the phone: they arrive (sync runs when the app
   comes to the front, or pull down on the Words home).
5. **Favourites / settings / lists**: star a word on the phone, change "New words a day" on the iPhone, import a short
   list (Books → Import a Word List…, paste or open a .txt/.csv); each shows up on the other device after a sync.
6. **Speech**: US/UK on a word page. If it says there's no English voice, add one in the phone's text-to-speech
   settings (search Settings for "text-to-speech"; or install Google's Speech Services) and try again.
7. **Offline**: airplane mode, study a few words (the Words home shows "… answers waiting to upload"), back online,
   Sync Now → that line disappears.
8. **Sign out** (Study Settings → Sign Out): the words leave the phone, the timetable stays; sign in again and they
   come back. **Don't test Delete Account with your real account** (it deletes the iPhone's progress too).
9. Statistics, search (English, prefix or Chinese), hard words; Chinese and dark mode if you use them.

## Handoff prompt for the next session

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] CourseTable — Android: the owner's results for 0.4.0 (Words), then stage 7 (release)

## Read first
HANDOFF.md (status, the 0.4.0 checklist, facts, gotchas), docs/android-plan.md (§1 decisions, §7 stages, §8 progress),
android/README.md, docs/vocabulary-spec.md for anything about Words.

## Goal
1. I tell you how the 0.4.0 phone test went: fix what I report on a branch (CI green, then fast-forward main, new
   APK, versionCode +1).
2. Stage 7 (release): a signed release APK. Ask me first how the release keystore is made and kept (it will be a repo
   secret I set myself; switching from the debug key means one uninstall, data moves by CSV export and the Words
   account), the version number and the release notes. Then docs: an Android development record
   (docs/android-development-record.md, like docs/v2-development-record.md), README (Android section), HANDOFF.

## Decided with me — don't reverse; ask if unsure
Everything in docs/android-plan.md §1 (native Kotlin + Compose, core port, the same Supabase project and schema
unchanged, supabase-kt, APK, StudyHour/学时 on Android only, iOS unchanged). Keep the repo secret ANDROID_DEBUG_KEYSTORE.
Don't create or change secrets without asking. No Android Studio/SDK on this PC: the app builds on CI only.

## How to work
Logic in android/core with tests first (it builds and tests here: ./gradlew :core:test); the app stays thin. Iterate the
app on a branch: push, `gh workflow run android.yml --ref <branch>`, read the run, fix, repeat; when green, fast-forward
main and push (main builds the APK). Download artifacts with gh run download (APK to C:\Users\AI\Downloads\CourseTable\,
screenshots to check). Never put secret keys, passwords or tokens in chat or code. Short report at the end with run
links.
```

## Facts and constraints

- Versions (checked 2026-10-09/11): Kotlin 2.4.21, AGP 9.4.1 (built-in Kotlin: no kotlin-android plugin), Gradle 9.8.1,
  compileSdk/targetSdk 37 (Android 17 stable), minSdk 26, Compose BOM 2026.09.00 (material3 1.4.0, ui 1.12.1), KSP
  2.3.12, Room 2.8.5, Lifecycle 2.11.0, Glance 1.2.0, core-ktx 1.19.1, Media3 1.11.1, kotlinx-serialization 1.11.0,
  kotlinx-coroutines 1.11, supabase-kt 3.8.0 (BOM; auth-kt + postgrest-kt), Ktor 3.5.1 (OkHttp engine), Robolectric
  4.17, Roborazzi 1.76.0, Espresso pinned 3.7.0. GitHub Actions: checkout v7, setup-java v6 (Temurin 21),
  gradle/actions v6, upload-artifact v7. `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19.
- Layout: `android/core` — packages `io.github.hilbert0619.coursekit` and `…vocabkit` (with `vocabkit.backend`:
  `VocabBackend` interface, row types, `DemoBackend`/`VocabDemo`; `vocabkit.store`: `VocabApp`, `SyncEngine`,
  `VocabModel`, `VocabStore`/`InMemoryVocabStore`), 340 tests incl. shared fixtures. `android/app` packages `data`
  (Room `AppDatabase` v1), `alerts`, `focus`, `words` (`SupabaseBackend` with the URL + publishable key, Room
  `VocabDatabase` v1 → `vocabulary.db`, `RoomVocabStore`, `Pronunciation`), `ui` (… `words`: `WordsTab` +
  `WordsController`/`LocalWordsController`, sign-in, study session, word/search/list screens, books + import,
  settings, statistics), `widget`. Room schema JSON for both databases in `app/schemas/`: bump the version and add a
  migration for any change.
- App tests (`app/src/test`): `AlertPlannerTest` (plain JVM), `FocusEngineTest`, `RepositoryTest`, `VocabRoomTest`
  (Robolectric), `ScreenshotTests` (22 screens), `WordsScreenshotTests` (13 Words screens with `DemoBackend` +
  `InMemoryVocabStore` at a fixed clock).
- Strings: English in `app/src/main/res/values/strings.xml` (same English as iOS where possible);
  `python tools/xcstrings_to_android.py` generates `values-zh-rCN` (439 strings) from `Shared/Localizable.xcstrings` +
  `android/strings-zh.json`; CI runs it with `--check`.
- Shared test cases: `spec/fixtures/` (README there). A logic change goes into CourseKit/VocabKit *and*
  `android/core`, with the fixtures updated.
- CI `android.yml`: on push to main (paths android/**, spec/**, content/**, CourseTable/Sounds/**, the strings tool)
  and `workflow_dispatch`; runs the strings check, `:core:test :app:recordRoborazziDebug :app:assembleDebug`; artifacts
  StudyHour-debug-apk, StudyHour-screenshots, StudyHour-room-schema, StudyHour-test-reports (on failure). ~4–8 min.
- Supabase: project prkvpkxrugvbmrwxehhs; Site URL = `io.github.hilbert0619.coursetable://auth-callback` (both apps
  use it); built-in email (team addresses only, a few an hour); the 6-digit-code templates in
  `supabase/remote-auth-code-emails.json` wait for a custom SMTP sender. Never put secret keys, DB passwords, tokens or
  keystore passwords in chat or code.
- Release signing (stage 7): the owner decides how the release keystore is made and kept; switching from the debug
  key to it needs one uninstall on the phone (data can move by CSV export).

## Gotchas learned

- Local Gradle: there is no global Gradle. Use `android/gradlew` (it downloads Gradle 9.8.1 into `~/.gradle`) and pass
  the proxy as Gradle args, not `GRADLE_OPTS`:
  `./gradlew -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7892 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7892 :core:test`
  (only `:core` builds here). TLS hiccups to dl.google.com / raw.githubusercontent.com happen: retry.
  `google()` is limited to androidx/com.android/com.google groups in `settings.gradle.kts`.
- A local Gradle build that hangs leaves daemons holding `core/build/kotlin`: run `./gradlew --stop`, stop leftover
  `KotlinCompileDaemon` java processes, delete `android/core/build/kotlin`, and build with `--console=plain`.
  `swift test` in `CourseKit/` takes ~20 s here.
- When the home Wi-Fi drops, every TLS handshake fails (GitHub through Clash and direct sites alike): wait and retry.
- Robolectric on JDK 21 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` and
  `--add-opens=java.base/java.io=ALL-UNNAMED` (set in `app/build.gradle.kts`). In Robolectric, POST_NOTIFICATIONS is
  not granted and `canScheduleExactAlarms()` is false unless a test sets them (`ScreenshotTests.allowEverything`).
- supabase-kt: `parseFragmentAndImportSession` is `@SupabaseInternal`; `SupabaseBackend` parses the link's fragment
  itself (`fragmentParameters`), checks the token with `retrieveUser` and calls `importSession`. The stored session is
  restored offline with `sessionManager.loadSessionOrNull()`. Errors map to `VocabAuthError` codes as on iOS.
- Words times: created at millisecond precision; Room keeps epoch microseconds (the server's precision).
- Compose draws a tab with no width in text fields: import fields use `TabsAsSpaces`. `DateUtils` says
  "0 minutes ago" under a minute: the sync row says "Synced just now".
- Material Symbols drawables carry `android:tint="?attr/colorControlNormal"`: remove it after downloading and put the
  `>` back where it ended the tag (check every drawable parses).
- Android 16 APIs behind feature flags may be missing from the SDK stubs: use the action string.
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
