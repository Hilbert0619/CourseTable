# Session Handoff — CourseTable (iOS v0.3 built · next: the Android version)

Date: 2026-10-09 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (public)

## Status

- **iOS**: 0.3.0 (build 4, class Live Activity) runs on my iPhone; device test passed. **0.3.1 (build 5)** adds
  to-dos, the Focus tab (Pomodoro + 9 CC0 background sounds) and class alerts without duplicates
  ([`docs/todos-focus-spec.md`](docs/todos-focus-spec.md)). It's built (IPA run 37971663701,
  `C:\Users\AI\Downloads\CourseTable\CourseTable-0.3.1.ipa`), **but my device test of it isn't reported yet.**
  Green: CourseKit 140 + VocabKit 77 tests (Windows + Linux), iOS run 37968070324 (37 app tests), Screenshots run
  37968936694. The layout fixes after those screenshots (`2265f4f`) were only compiled. Not written yet:
  `docs/v3-development-record.md`.
- **Android**: decided on 2026-10-09, nothing built yet. Plan: [`docs/android-plan.md`](docs/android-plan.md).
- **The repo is public since 2026-10-09** (after a security check of all history: no secrets), so GitHub Actions on
  standard runners (Linux, macOS) is free and unlimited; checked: run 38021692623 billed 0 ms. Secret scanning + push
  protection on, fork PR workflows need approval for all external contributors, workflow token read-only.

## Handoff prompt for the next session

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] CourseTable — start the Android version: Kotlin + Jetpack Compose, the same features as the iOS app

## Read first
docs/android-plan.md (decisions, feature parity table, architecture, stages, open questions), README.md (every feature),
docs/todos-focus-spec.md and docs/vocabulary-spec.md (the specs), HANDOFF.md (status, facts). The iOS app is the
reference: its logic is in CourseKit/ (Swift package: CourseKit = timetable, VocabKit = vocabulary; ~4,100 lines,
~220 tests) and its UI in CourseTable/ and CourseTableWidget/. Repo D:\Projects\CourseTable → github.com/Hilbert0619/CourseTable
(public), branch main.

## Goal
Android users get the same app with the functionality as unchanged as possible: timetable (courses, import/export,
course list + placing, grid, Today with to-dos, widgets), class reminders + a live class notification (the Android
counterpart of the Live Activity), to-dos, the Focus tab with Pomodoro and the 9 background sounds, and the Words tab
(Supabase account, FSRS-6 study, sync). The iOS app stays as it is.

## Decided with me (2026-10-09) — don't reverse; ask if unsure
- Native Android: Kotlin + Jetpack Compose (Material 3). Not Kotlin Multiplatform, not Swift-on-Android.
- Port CourseKit + VocabKit to a pure Kotlin module `android/core` with the tests ported one for one; shared JSON
  test fixtures (spec/fixtures/) keep both platforms behaving the same. Logic in `core` with tests first; app thin.
- Same repo (`android/`), same Supabase project and schema (no backend change), supabase-kt, the same email-link
  redirect io.github.hilbert0619.coursetable://auth-callback (already allowed by supabase/remote-auth.json).
- Distribution: signed APK files built by CI, installed directly. No store for now.
- Test phone: OPPO / OnePlus / vivo / Honor (ColorOS / OriginOS / MagicOS); the emulator on this PC otherwise.
- English + Simplified Chinese, reusing the zh-Hans translations in Shared/Localizable.xcstrings.

## First steps (stage 1 of docs/android-plan.md §7)
1. Ask me the open questions (§8): exact phone model + Android/OS version; may Android Studio + SDK be installed
   (D: has room); app name/icon. Check whether auto mode lets you run Gradle locally (it blocked downloaded binaries
   before); if not, build on Linux CI and I run/install.
2. Look up current versions (Kotlin, AGP, Compose BOM, Room, WorkManager, Glance, Media3, supabase-kt); don't
   rely on memory. minSdk 26, targetSdk = newest stable.
3. Create android/ (core + app), android.yml (Ubuntu, path-filtered), a first debug APK that opens on my phone.
4. Then stage 2: port CourseKit to core with its tests, extracting the first shared fixtures.
Report at the end of each stage with run links and screenshots (Roborazzi on Linux CI; the phone for system UI).

## Facts and constraints
- Supabase project prkvpkxrugvbmrwxehhs (Singapore); URL + publishable key in
  CourseTable/Vocabulary/Backend/VocabBackend.swift (public by design; reuse on Android). Tables: books,
  book_entries, review_logs (append-only, client UUIDs, server received_at), favorites, user_settings; RPC
  delete_my_account (see supabase/migrations and SupabaseBackend.swift for the exact calls). Auth: email + password,
  implicit flow. GitHub secrets SUPABASE_ACCESS_TOKEN / SUPABASE_DB_PASSWORD, variable SUPABASE_PROJECT_REF are set.
  Never put the secret key, DB password, access token or an APK keystore password in chat or code. The built-in
  email sender only delivers to the project team's addresses (custom SMTP needed before other students join).
- Background sounds: CourseTable/Sounds/sound-<id>.m4a (CC0, content/sounds/SOURCES.md; tools/make_sounds.py +
  the manual Linux workflow `Sounds` rebuild them).
- Android on Chinese brands: no Google Play services (no FCM); aggressive background killing — plan a "reliable
  reminders" settings page (docs/android-plan.md §5).
- The repo is public: Actions minutes are free (standard runners), but everything committed and every workflow log
  is visible to anyone. Never commit secrets (push protection blocks known key formats); keep keystores, passwords
  and tokens in GitHub secrets. Docs-only commits and `[skip ci]` commits don't run workflows.
- Auto mode blocks Claude from running downloaded third-party binaries and from starting backend-deploy.yml; gh run
  list/view/download and workflow_dispatch of the other workflows work.

## Environment notes (Windows 11, no Mac)
- Installed: Swift 6.4 (CourseKit builds and tests here), VS 2022 C++ tools, Windows SDK, Git, gh (signed in as
  Hilbert0619), Node, Python 3.10 (default python; don't install into it or change the default 32-bit 3.12),
  JDK 25 (Oracle + Adoptium). Not installed: Android Studio, Android SDK, Gradle. Hyper-V is on (emulator OK).
- If swift/gh aren't found, reload PATH from the registry. In Git Bash, gh is at /c/Program Files/GitHub CLI.
- GitHub needs Clash: `git -c http.proxy=http://127.0.0.1:7892 push`, HTTPS_PROXY for gh, per command only. TLS
  hiccups (EOF) happen: retry.
- Never round-trip UTF-8 files through PowerShell 5.1 Get-Content/Set-Content. The Bash tool collapses `\\` in
  heredocs (and GNU sed treats \U in a replacement as "uppercase"): write helper scripts with the Write tool, then
  `python file.py`. No global settings changes.
- WebSearch failed in the last session (model error); the built-in browser with Bing works. Apple docs as JSON:
  https://developer.apple.com/tutorials/data/documentation/<path>.json (via the proxy).

## The iOS app (reference; change it only when I ask)
- Swift 6 + SwiftUI + SwiftData + WidgetKit + ActivityKit, iOS 17+. Tabs: Today, Timetable, Focus, Words, Settings.
- CourseKit/Sources/CourseKit: LocalDate/TimeOfDay/Moment (no time zones), Semester, PeriodSchedule, Course,
  Timetable, WeekGrid, ScheduleSnapshot, CSV + CourseImport + ImportPlan, CourseTemplate, Reminders,
  ClassActivities (Live Activity windows + plan), Todos (agenda, alerts), Focus (Pomodoro plan + run).
  CourseKit/Sources/VocabKit: FSRS, Scheduler, StudyClock, DailyPlan, StudySession, AnswerGrader, Choice,
  WordEntry, ECDICT, WordListImport, WordSearch, VocabStats, SyncMerge, AuthLink, SeedSQL.
- CourseTable/: Views/ (RootView, TodayView, TodoEditorView, WeekView, CourseEditorView, SettingsView…),
  Reminders/ (Alerts.sync: ClassLiveActivities, ClassReminders, TodoReminders), Planner/ (Planner.store),
  Focus/ (FocusEngine, FocusView, SoundPlayer), Vocabulary/ (Words tab), Persistence/.
- Decisions: logic in Swift packages with tests first; bundle IDs io.github.hilbert0619.CourseTable(.Widget); App
  Group group.io.github.hilbert0619.CourseTable; URL scheme io.github.hilbert0619.coursetable. Both SwiftData
  stores hold my data (model changes need migrations).
- iOS release: bump MARKETING_VERSION/CURRENT_PROJECT_VERSION (16 places in project.pbxproj; 0.3.1 / 5 now),
  `gh workflow run ipa.yml`, download to C:\Users\AI\Downloads\CourseTable\CourseTable-<version>.ipa, I install
  with iloader.

## iOS loose ends (do when I ask or report)
- My device test of 0.3.1: to-dos (time + "after a class" notifications, shown in the class Live Activity), only one
  alert per class, Focus (sound in the background, Pause from the Dynamic Island, switch notifications, loop seams).
- Then docs/v3-development-record.md (v0.3: Live Activity, to-dos, focus, sounds; like docs/v2-development-record.md).
- Later: spec v0.3 Words items (reverse mode, Words widget, study reminder, example sentences after a licence
  check, server-side search, weekly/monthly stats, custom SMTP); timetable backlog (long words in grid blocks,
  dark-mode screenshots, "All weeks" view, per-course reminder mute, school import, multiple terms).

## How I like to work
- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage, with run links. Verify for real (tests/CI/screenshots), don't trust
  "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.
- "Complete the handover" means: finish the docs so a new session starts the next thing; don't start it.
```
