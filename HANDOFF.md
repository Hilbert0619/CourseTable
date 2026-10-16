# Session Handoff — CourseTable (iOS 0.6.0: new icon, Focus wallpapers, Settings → Storage: waiting for the owner's backend deploy and device test · Android 0.5.0 with AI help and the agent: waiting for the owner's test, plus the 0.4.1 Atom Island check, then stage 7)

Date: 2026-10-16 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (public), branch `main`

## Next: the owner deploys the wallpapers, then tests iOS 0.6.0

The owner's iOS 0.5.0 device test (the AI agent) passed with no major bugs (2026-10-16). Then the owner passed on
users' requests (after the Chinese app 不背单词), all built as **iOS 0.6.0 (build 8)**
([`docs/todos-focus-spec.md`](docs/todos-focus-spec.md) §7):
1. **A new app icon** (the owner's picture, made full-bleed: the tile's rim replaced by its surface colour).
2. **Focus wallpapers**: each session shows a random high-resolution photo (not one shown recently) behind the
   countdown, with the photographer's credit and a shuffle button; a Wallpaper switch on the Focus setup. 30
   Unsplash photos (Unsplash License; `content/wallpapers/`) cropped to 1290×2796 in the public Storage bucket
   `wallpapers`, listed in the new table `public.focus_wallpapers` (anyone reads it; no account needed).
3. **Deleting records**: swipe a Focus history session to delete it, **All Sessions** (by day, Clear History), and
   **Settings → Storage** to pick and clear focus history, completed to-dos, Assistant chats, saved AI answers, AI
   usage counts, downloaded wallpapers and other cached files.
- Code: CourseKit `FocusWallpapers.swift` + `FolderUsage.swift` (12 new tests, shared cases
  `spec/fixtures/focus-wallpapers.json`); app `Focus/WallpaperStore.swift`, `FocusView`, `Views/StorageView.swift`,
  `PlannerModel` and `AIStore` deletes; backend migrations `20261016100000/100100`, `config.toml`
  `[storage.buckets.wallpapers]`, pgTAP `04_wallpapers.test.sql`, smoke and deploy checks; `tools/make_wallpapers.py`.
- CI: backend 38939355402 (local stack: bucket seeded, 30 rows, picture served), iOS 38945143245, CourseKit
  38939889921 (Linux), screenshots 38945148761 (PlannerUITests, en + zh). **IPA 0.6.0 (build 8)**: run 38945155263
  (commit 5c3dda8), saved as
  `C:\Users\AI\Downloads\CourseTable\CourseTable-0.6.0.ipa`.
- **Not yet in the cloud**: the table and the pictures arrive when the owner runs **Backend deploy** (auto mode
  doesn't let Claude start it). Until then the app shows the plain countdown (it handles a missing list).

Handoff prompt A below picks up the owner's results.

## Status

- **Android (StudyHour / 学时)**: stages 1–6 of [`docs/android-plan.md`](docs/android-plan.md) §7 are on `main`.
  - Stage 4 (alerts, Focus, Reliable Reminders): APK 0.3.0, **tested on the phone by the owner: no issues**.
  - Stage 5: VocabKit ported to `android/core` with shared cases `spec/fixtures/vocab-*.json`.
  - Stage 6 (the Words tab): APK 0.4.0, **tested on the phone by the owner (2026-10-15): no major issues**.
  - **Users report** (second-hand, 2026-10-15; the owner hasn't seen it on their own phone) that the Atom Island (原子岛)
    on vivo/iQOO "doesn't display correctly". Phone models, OriginOS versions and the exact symptom are not known yet.
  - **0.4.1 (versionCode 5), 2026-10-15: a fix for both likely causes, not yet confirmed on a phone.** APK
    `C:\Users\AI\Downloads\CourseTable\StudyHour-0.4.1-debug-28.apk` (installs over 0.4.0). (1) *Nothing in the
    island*: Android 16 has two promotion rule sets (platform flag `ui_rich_ongoing`: must not be colorized vs must be
    colorized) and our notifications only met the not-colorized one; `LiveUpdates.build` now lets the phone's
    `hasPromotableCharacteristics()` pick. (2) *The app name instead of the countdown*: vivo/OPPO/Xiaomi islands ignore
    the chronometer, so they now get "25 min" text refreshed each minute. Settings → Reliable Reminders → Send a Test
    Live Update reports what the system did. CI: branch 38784060888 (`android-island`), main 38784460423. Details:
    `docs/android-plan.md` §8. Checklist below.
  - **Island result (owner, 2026-10-16): still nothing, on 0.4.1 and 0.5.0** (iQOO Neo 9, China OriginOS 6
    `PD2338_A_16.2.13.2.W10`). vivo's documents (dev.vivo.com.cn docs 894–896) say third-party apps reach the Atom Island
    only through vivo's own 原子通知 API, **granted by vivo per app and scene**, and only for apps already on the vivo App
    Store (email `oosyztz@vivo.com`, 7–10 working days, design review, staged launch). StudyHour (a debug APK, not on the
    store) has no such grant; plain Live Updates reach the island only on global OriginOS builds. **No further island
    work until the owner decides** whether to pursue vivo's route (needs stage 7, 软著, App 备案 and, for the AI
    features, vivo's deep-synthesis qualification). Full findings: `docs/android-plan.md` §8, the 0.4.1 entry.
  - **0.5.0 (versionCode 6), 2026-10-15: AI help and the agent, moved ahead of stage 7 at the owner's choice.** All of
    iOS 0.4/0.5's AI: AIKit ported to `android/core` (package `aikit`, tested against every shared `ai-*.json` case),
    keys encrypted with an Android Keystore key, Settings → AI Help, word help, "Why?", Long Sentences / Writing
    Feedback, Quick Add, the Assistant with the tools (cards, Undo, photos, earlier chats), Import → From a Photo (AI),
    Analyse My Progress. Text in photos: ML Kit bundled Chinese+Latin (the owner's choice: 64-bit ARM phones only; APK
    31 → 40 MB). CI: branch 38828978533 (`android-ai`), main 38829317137 (APK
    `C:\Users\AI\Downloads\CourseTable\StudyHour-0.5.0-debug-39.apk`); screenshots of every AI screen checked
    (English and Chinese).
    **Not yet tried with a real key, nor the Keystore or ML Kit on a phone**: checklist below. Code map:
    `android/README.md` "AI help and the Assistant".
- **iOS**: 0.3.1 passed the owner's device test (2026-10-11); the owner published GitHub Releases themselves (IPAs
  0.1.0–0.3.1 and the Android 0.4.0 *debug* APK renamed `StudyHour-0.3.1.apk`; every tag points at e6479e7, not the
  build commits; no LICENSE yet). Those release fixes wait for the owner's OK. `docs/v3-development-record.md` is still
  unwritten.
- **AI help** ([`docs/ai-spec.md`](docs/ai-spec.md), bring your own key), built on iOS 2026-10-12 as 0.4.0 (build 6);
  **the owner's device test with a real key passed** (2026-10-15).
- **AI agent** ([`docs/ai-agent-spec.md`](docs/ai-agent-spec.md)), built 2026-10-15 as 0.5.0 (build 7) (IPA run
  38774424881); **the owner's device test passed with no major bugs** (2026-10-16). Android has both since 0.5.0.

## iOS 0.6.0 test (owner)

**First, put the wallpapers in the cloud (one click):** GitHub → Actions → **Backend deploy** → Run workflow (branch
`main`, defaults) — or `gh workflow run backend-deploy.yml`. It applies the two wallpaper migrations, uploads the 30
pictures to the bucket `wallpapers` ("Upload the wallpapers": 30 × `Uploading: …`) and checks them ("Check the
database" shows `"wallpapers": 30`, `"wallpapers_bucket_public": true` and `Picture: 200 image/jpeg …`).

Then install `CourseTable-0.6.0.ipa` over 0.5.0 with iloader (everything stays).
1. The Home Screen shows the **new icon** (also in Settings and the app switcher).
2. Focus → scroll down: **Wallpaper** is on. Start a session: within a few seconds a photo fills the countdown card
   (its colour first, then the picture), the text is white and easy to read, and at the bottom "Photo by … on
   Unsplash" and a shuffle button. Shuffle: another photo. Tap the credit: Safari opens the photo's page.
3. During the session, close Course Table from the app switcher and open it again: the same photo.
4. Stop, start again a few times: a different photo each time (no repeats among the last ten).
5. Airplane mode, start a session: one of the photos already seen (they're kept); turn Wallpaper off: the plain
   countdown as before.
6. Focus → History: swipe left on a session → Delete. **All Sessions**: sessions by day with each day's total; Edit
   deletes several; **Clear History** asks first.
7. Settings → **Storage**: counts and sizes (Focus Wallpapers ≈ 0.5 MB per photo seen). Select a few (or Select All)
   → Clear Selected → the confirmation names them → Clear: the counts drop. The timetable, open to-dos, Words and AI
   connections stay. Cleared wallpapers download again in the next session.
8. Anything odd: a screenshot, and what you did.

## Android 0.5.0 AI test (owner, with a real key)

Install `C:\Users\AI\Downloads\CourseTable\StudyHour-0.5.0-debug-39.apk` over 0.4.x (timetable, to-dos, Focus and Words stay). Keys don't come over from the iPhone: add the
connection on the phone itself (the same key works on both). Use a model that calls tools: DeepSeek
(`deepseek-v4-flash`), GLM (`glm-4.7-flash`, free) or Qwen.
1. 设置 → AI 帮助 → 添加连接: provider, key, model → **测试连接**: connected, tools work, and with a Photo Model (GLM
   `glm-4.6v-flash`, Qwen `qwen3-vl-plus`) photos work. Close StudyHour from Recents and open it again: the connection
   is still there and the key field's label says a key is saved (the Keystore works).
2. Words: a word page → the AI buttons stream an answer; open the word again: the answer is saved (no new charge). In a
   study session, a wrong choice or spelling → **为什么？**.
3. Words → AI 工具 → **长难句**: **相机** or **照片** reads the text on the phone (ML Kit, works offline) → analyse.
   **作文批改**: a paragraph → feedback.
4. A new to-do → **快速添加**: "明天高数课后交论文" → the fields fill in.
5. Today → ✨ (助手): the iOS checklist's steps 2–4 above (New To-Do card → Confirm → Undo; Change To-Do; a delete you
   Cancel; a lookup with no card; Start Focus → the Focus tab and its notification run it).
6. Words → 统计 → **分析我的进度**: lookup lines, then an analysis whose numbers match the statistics.
7. 课程表 → Import → **从照片导入（AI）**: a 教务系统 photo or screenshot → courses to check (period times too, if the
   photo shows them) → Import. Once with DeepSeek (text read on the phone), once with a photo model.
8. 助手 → photo → "把这些课加进课程表" → an Add Courses card → Review… → Import → the card counts them; Undo removes them.
9. **以前的对话**: reopen a chat, the cards keep their state. Airplane mode: a clear error and Try Again.
10. Anything odd: provider and model, what you asked, a screenshot of the card or answer. Never send the key itself.

## Android 0.4.1 Atom Island check (the owner's iQOO Neo 9, or a user's vivo/iQOO phone)

Before updating, on 0.4.0: start a Focus session, go to the Home Screen and note what the island shows (this
reproduces the users' report on a known phone). Then install `StudyHour-0.4.1-debug-28.apk` over 0.4.0 (everything
stays; 0.5.0 has the same island code, so the check works on it too). The island hides while StudyHour itself is on screen, so always go to the Home Screen to look. From a user, also
get the phone model and the OriginOS version (设置 → 系统升级, or 关于手机).
1. Settings → Reminders → Reliable Reminders: the **实时更新 / Live Updates** row says 已允许 / Allowed (if not, tap it).
2. At the bottom: **发送测试实时更新 / Send a Test Live Update** → go to the Home Screen: the island should show the
   StudyHour icon and "2 分钟". Come back: the line under the button says whether the phone made it a Live Update, plus
   a details line (`promoted · colorized/standard · build`). **Screenshot that page** either way.
3. Focus: start a session → Home Screen: the island shows "25 分钟" and counts down a minute at a time; Pause from the
   notification → "已暂停". Tap the island: does it open the card / the app?
4. A class with the live class notification on: from the reminder time the island shows the minutes to the start,
   then the minutes to the end.
5. If the island stays empty: the screenshot from step 2, and whether Settings → 通知与状态栏 → 原子岛 lists
   anything for 学时. (vivo's own 原子通知 API needs access granted by vivo by email: last resort.)

## Handoff prompts

> Copy one whole block into a new Claude Code session opened in `D:\Projects\CourseTable`. **A** follows the owner's
> 0.6.0 device test; **B** is the Android track (independent): the 0.5.0 AI test and the 0.4.1 island check, then stage 7.

### A. The owner's results for iOS 0.6.0 (icon, Focus wallpapers, Storage)

```
[PROJECT HANDOFF] CourseTable — the owner's device test of iOS 0.6.0 (icon, Focus wallpapers, Settings → Storage): fix what I report

## Read first
HANDOFF.md (status, the 0.6.0 checklist, facts, gotchas), docs/todos-focus-spec.md §7 (wallpapers, history, storage),
README "Focus" and "Storage", content/wallpapers/SOURCES.md, spec/fixtures/README.md.

## Goal
1. I tell you whether Backend deploy went through and how the 0.6.0 checklist went. Fix what I report: logic in
   CourseKit with tests and shared fixtures first, then the app; screenshots for UI changes.
2. If I ask for more or different wallpapers: edit content/wallpapers/sources.json (Unsplash License photos only,
   checked on each photo's page; never Unsplash+), run tools/make_wallpapers.py, put the new rows in a new migration
   (don't edit an applied one), and I run Backend deploy again.
3. Bump to 0.6.1 (build 9) if anything changed, build the IPA, save it to C:\Users\AI\Downloads\CourseTable\, give me a
   short checklist for what changed.

## Decided with me — don't reverse; ask if unsure
Wallpapers come from Supabase (table focus_wallpapers + public bucket wallpapers), readable without an account; the
app picks one at random per session and keeps downloaded ones. Storage never clears the timetable, unfinished to-dos,
Words progress or AI connections. Everything in docs/ai-spec.md §1 and docs/ai-agent-spec.md §2 still holds. Swift +
SwiftUI only; logic in CourseKit with tests that run on Windows (`swift test` in CourseKit/); the app compiles on
GitHub Actions only. Never put a real API key, token or password in code, tests, CI or chat.

## How to work
Iterate on a branch: push, `gh workflow run ios.yml --ref <branch>`, then screenshots.yml with
only=CourseTableUITests/PlannerUITests (and AIUITests/AgentUITests if touched); download the screenshots and look at
them; when green, fast-forward main. Backend changes: backend.yml on the branch first; I start backend-deploy.yml.
Use the Write/Edit tools (or Python scripts written with Write) for code, never Python with backslashes through a
bash heredoc. Short report at the end with run links.
```

### B. Android: the 0.5.0 AI test and the Atom Island check, then stage 7

```
[PROJECT HANDOFF] CourseTable — Android: the owner's 0.5.0 AI test and the Atom Island check, then stage 7 (release)

## Read first
HANDOFF.md (status, the Android 0.5.0 AI and 0.4.1 island checklists, facts, gotchas), docs/android-plan.md (§1
decisions, §7 stages, §8 progress incl. the 0.4.1 and AI entries), android/README.md ("Live Updates and the brands'
islands", "AI help and the Assistant"), docs/ai-spec.md and docs/ai-agent-spec.md (the iOS behaviour Android matches).

## Goal
1. I tell you how the 0.5.0 AI checklist went (provider, model, what happened) and how the island check went, on my
   phone or from users (the island report came from users; I may only have their words and screenshots, and their
   phone model and OriginOS version). Fix what's reported on a branch (CI green, then fast-forward main, new APK,
   versionCode +1): AI logic in android/core (package aikit) with tests first, matching iOS; if a fix belongs in the
   shared logic, change iOS AIKit too only after asking me.
2. Stage 7 (release): a signed release APK. Ask me first how the release keystore is made and kept (it will be a repo
   secret I set myself; switching from the debug key means one uninstall, data moves by CSV export and the Words
   account), the version number and the release notes. Then docs: an Android development record
   (docs/android-development-record.md, like docs/v2-development-record.md), README (Android section), HANDOFF.

## Decided with me — don't reverse; ask if unsure
Everything in docs/android-plan.md §1 (native Kotlin + Compose, core port, the same Supabase project and schema
unchanged, supabase-kt, APK, StudyHour/学时 on Android only, iOS unchanged). Keep the repo secret ANDROID_DEBUG_KEYSTORE.
Don't create or change secrets without asking. No Android Studio/SDK on this PC: the app builds on CI only. AI: bring
your own key, nothing through our server, keys only on the phone (Android Keystore, out of backups), every change the
agent makes confirmed with a tap; text in photos by ML Kit bundled, 64-bit ARM only. Never put a real API key in code,
tests, CI or chat.

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
- Live Updates: `hasPromotableCharacteristics()` has two rule sets (flag `ui_rich_ongoing`, see AOSP
  `android16-qpr2-release` Notification.java): not colorized vs colorized, so never hard-code either.
  `canPostPromotedNotifications()` is a diagnostic, not a gate. vivo/OPPO/Xiaomi islands ignore the chronometer and
  hide while the app is in front. Device findings: github.com/tigerduck-app/android-dynamic-island-test.
- Don't name a Kotlin property `java` (it shadows the `java.time` package); Glance `defaultWeight()` is scoped to
  Row/Column (no import).
- Room ordering uses `createdAt`; `Repository.now()` hands out strictly increasing stamps.
- `SwipeToDismissBox` rows are keyed (`key(id)`), so a deleted row's swipe state doesn't move to the next one.
- Bash heredocs with quotes and `\'` broke once: write helper scripts with the Write tool, run them with python.
  Python through a heredoc also turns `\n` in Kotlin strings into real line breaks (broke three files in the AI work):
  change code containing backslashes with the Edit/Write tools only.
- AI on Android: ML Kit's `libmlkit_google_ocr_pipeline.so` for all ABIs made the APK 76 MB; `packaging` excludes all
  but arm64-v8a and `useLegacyPackaging = true` compresses native libraries (40 MB). Kotlin `data object` makes its own
  `readResolve`: don't write one. A test fake's `var goal` clashes with `override fun setGoal` (JVM signature).
  Compose screenshots with two scrollables need `onAllNodes(hasScrollAction()).onFirst()`. The saved-key hint is in
  the key field's label, which always shows (as a placeholder it didn't).
- Auto mode once blocked plain reads right after a secret was written (`gh secret set`); ask before secret changes.

## Environment notes (Windows 11, no Mac)

- Installed: Swift 6.4 (`swift test` in `CourseKit/` works), JDK 25, Git, gh (signed in as Hilbert0619), Node,
  Python 3.10 (default python; don't install into it). Not installed: Android Studio, Android SDK.
- GitHub needs Clash: `git -c http.proxy=http://127.0.0.1:7892 push`, `HTTPS_PROXY=http://127.0.0.1:7892` for gh/curl,
  per command only. In Git Bash, gh is at `/c/Program Files/GitHub CLI/gh.exe`.
- Never round-trip UTF-8 files through PowerShell 5.1; write helper scripts with the Write tool and run them with python.
- Commit email for this repo is set per repo (253035987+Hilbert0619@users.noreply.github.com).

## The iOS app (change it only when I ask)

- Swift 6 + SwiftUI + SwiftData + WidgetKit + ActivityKit, iOS 17+. Tabs: Today, Timetable, Focus, Words, Settings.
- AI code: `CourseKit/Sources/AIKit` (presets with tools/photos flags, requests, SSE parser, stream decoding with tool
  calls, errors, prompts, TodoDraft, MarkdownBlock, SentenceWords; the agent: JSONValue, AITool, AIAgentSession,
  AppTools, TimetablePhoto; 51 test groups) and `CourseTable/AI` (AIStore with Keychain keys, usage, saved answers and
  chats with cards; AITransport with `DemoAITransport` for `-aiDemo`, scripted tool calls; AIRun with a generation
  token; AgentRun + AgentContext; settings, word help, study tools, TextScanner, TimetablePhotoReader, AssistantView).
  UI tests `CourseTableUITests/AIUITests.swift`, `AgentUITests.swift` (`-aiDemoPhoto` gives a drawn sample timetable).
- Agent gotchas: Anthropic thinking blocks are bound to the conversation prefix (edited or trimmed history → 400 on
  new accounts), so they live only within a turn and are never saved; DeepSeek needs every earlier
  `reasoning_content` back once tools are on (400 otherwise); the iOS workflow now also runs on `CourseKit/Sources/AIKit/**`.
- Wallpapers and Storage (0.6): `CourseKit/Sources/CourseKit/FocusWallpapers.swift` (rows, candidates, the card's
  crop around `focus_y`, cache limits) and `FolderUsage.swift`; `CourseTable/Focus/WallpaperStore.swift` (list in
  `Caches/Wallpapers/catalog.json`, refreshed after 12 h; pictures `<id>.jpg` there; UI tests draw `DemoWallpaper` and
  never use the network); `Views/StorageView.swift`. The wallpaper list is a plain URLSession GET with the publishable
  key in `apikey` (no Supabase SDK, no account). UI test `PlannerUITests.testWallpaperHistoryAndStorage`.
  CourseKit code using CGSize/CGRect needs `import CoreGraphics` on Apple platforms (Windows' Foundation has them).
- Gotchas from the 0.6 work: `supabase start` already seeds `[storage.buckets]`; a second `seed buckets` asks before
  overwriting the bucket's settings (`--yes`). Unsplash's site refuses scripts (403); its image CDN
  (images.unsplash.com, reachable from China without the proxy) doesn't, so `make_wallpapers.py` only uses the CDN.
- iOS release: bump MARKETING_VERSION/CURRENT_PROJECT_VERSION (16 places in project.pbxproj; 0.6.0 / 8 now),
  `gh workflow run ipa.yml`, download to `C:\Users\AI\Downloads\CourseTable\CourseTable-<version>.ipa`, installed
  with iloader.
- Gotchas from the AI work: List rows off screen aren't in the XCUITest tree (scroll first; the first cell can be a
  section header, so tap rows by identifier); a sheet's `.task` can run twice; `@AppStorage` drafts survive between
  UI-test launches (`AIStore.makeForLaunch` clears them); PowerShell's execution policy blocks `.ps1` scripts (CI
  waits use a bash loop calling gh).
- Loose ends (when I ask): `docs/v3-development-record.md` (and v0.4/v0.5 records); the release fixes (debug APK in the
  public release, tags at the wrong commit, no LICENSE, no third-party notices); later Words items and the timetable
  backlog (long words in grid blocks, dark-mode screenshots, "All weeks" view, per-course reminder mute, school
  import, multiple terms).

## How I like to work

- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage, with run links. Verify for real (tests/CI/screenshots), don't trust
  "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.
- "Complete the handover" means: finish the docs so a new session starts the next thing; don't start it.
