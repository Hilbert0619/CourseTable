# CourseTable · 课程表

A native iOS class-schedule app with an English vocabulary trainer for 考研: **Swift + SwiftUI + SwiftData + WidgetKit**, iOS 17+, English and Simplified Chinese UI. The timetable stays on the device and needs no account; the **Words** tab signs in and syncs through Supabase.

[![CourseKit](https://github.com/Hilbert0619/CourseTable/actions/workflows/coursekit.yml/badge.svg)](https://github.com/Hilbert0619/CourseTable/actions/workflows/coursekit.yml)
[![iOS](https://github.com/Hilbert0619/CourseTable/actions/workflows/ios.yml/badge.svg)](https://github.com/Hilbert0619/CourseTable/actions/workflows/ios.yml)
[![Backend](https://github.com/Hilbert0619/CourseTable/actions/workflows/backend.yml/badge.svg)](https://github.com/Hilbert0619/CourseTable/actions/workflows/backend.yml)

Tabs: **Today**, **Timetable**, **Focus**, **Words**, **Settings**. Records: [v0.1 (timetable)](docs/v1-development-record.md) · [v0.2 (Words)](docs/v2-development-record.md). Specs: [vocabulary](docs/vocabulary-spec.md) · [to-dos, focus timer and class alerts](docs/todos-focus-spec.md) · [AI help](docs/ai-spec.md) · [AI agent (planned)](docs/ai-agent-spec.md). An Android version (Kotlin + Jetpack Compose, same features) is planned: [docs/android-plan.md](docs/android-plan.md).

## Features

### Timetable

- **Courses**: add, edit and delete by hand. Each course has a name, teacher, room, weekday, start period and length, week range and odd/even weeks. Colours are assigned automatically, and clashes are flagged.
- **Import**: paste CSV text or rows copied from a spreadsheet, or open a `.csv`/`.txt` file (Settings → Import Courses…, or the button on an empty timetable). A preview lists the courses, clashes and lines that can't be read before anything is saved. Courses that are already in the timetable are skipped. You can also replace all existing courses. See [Import format](#import-format).
- **Course list + placing**: when you only know your courses (name, room, teacher), type them one per line, e.g. `高等数学 A101 李老师`, then place them on the grid. Use **+ → Place Courses** or **Type Course List…** on an empty timetable. In placing mode, a tray lists your courses. Pick one, set its length and odd/even weeks, then tap empty slots: each tap adds that course there for the whole term. The block is shortened so it doesn't run into the next course. While placing, the grid shows courses from every week, dimming the ones not in the week shown. The tray's **Weekend** toggle adds Saturday and Sunday. Tap a placed block to adjust weeks or length in the editor. Long-press a course in the tray to remove it from the list.
- **Export**: Settings → Export as CSV shares the courses as a UTF-8 CSV file (Excel-friendly) in the same format, so an export can be imported again.
- **Class Live Activity** (v0.3): each class appears in the Dynamic Island and on the Lock Screen from the reminder time (0–60 minutes before, Settings → Reminders) until it ends: a countdown to the start with the room, then "Until 09:40" with a progress bar, plus the to-dos due after it. On iOS 26 the next 4 classes are scheduled ahead and start on their own, with an alert; on iOS 17/18 it starts when you open the app during the window (for example from the class reminder). Without a server it can't end itself: a ✕ closes it, the app ends finished ones when it opens or gets a background refresh, and iOS ends it after 8 hours. A later class always takes the Dynamic Island from a finished one, and the next class appears when the one before ends.
- **Class reminders**: a local notification 0–60 minutes before each class that has **no** Live Activity (beyond the next 4, with Live Activities off, or on iOS 17/18), so you never get both.
- **Term**: set the first day of term and the number of weeks. The app works out the current week.
- **Period times**: edit how many periods there are per day and each period's start and end time. The default is a 12-period day.
- **Weekly grid**: Mon–Fri, plus Sat/Sun if you have weekend classes. Switch weeks with the arrows, the week menu or a swipe. Tap an empty slot to add a course there.
- **Today**: the day's classes and to-dos in time order; the current and next class are highlighted. The arrows show other days.
- **To-dos** (v0.3): add them for any day with **+**: any time, at a time ("12:00 pick up the package"), or after one of that day's classes ("after Calculus: hand in the paper"). Timed ones send a notification (at the time, or when the class ends), and a class's to-dos also show in its Live Activity. Tick off, swipe to delete, tap to edit.
- **Widgets**: on the Home Screen, the small size shows the current or next class and the medium size shows the rest of today. The small size also works in StandBy. On the Lock Screen, the rectangular widget shows the current or next class with room and teacher, the inline widget sits above the clock ("Calculus until 09:40", "10:00 Physics"), and the circular widget shows a progress ring during class or the next start time.

### Focus (v0.3)

- **Pomodoro timer** on its own tab: a goal ("高数复习"), a length (15 min – 4 h, breaks included), and 25-minute focus blocks with 5-minute breaks and a 15-minute break after every fourth block (all adjustable), or one straight countdown with breaks off. The plan is shown before you start ("5 focus blocks · 2 hr of focus · ends at 21:30").
- **While running**: the phase and time left, Pause/Resume, Skip Break, Stop; the same in the Dynamic Island and on the Lock Screen, with Pause and Stop buttons. A notification at every switch and at the end, also while the app is open.
- **Background sounds**: rain, rain on a roof, campfire, airplane cabin, ocean, stream, night, train, café: recordings from Freesound in the public domain (CC0, [sources](content/sounds/SOURCES.md)), looped without a seam. They keep playing when you leave the app or lock the phone, mix with music, and can be changed while running.
- **History**: today's focus time and the latest sessions.

### Words (English vocabulary, v0.2)

- **Account**: email + password. Creating an account and resetting a password both work through the link in Supabase's email, which opens the app (URL scheme `io.github.hilbert0619.coursetable://auth-callback`) and signs you in. Sign out clears this iPhone's word data; Delete Account removes everything on the server too.
- **Books**: the built-in **考研词汇** list, 4,801 entries that ECDICT (MIT licence) tags as 考研, most frequent first. It downloads once and stays on the device for offline study. Import your own lists: one word per line (meanings are filled in from the dictionary) or CSV (`word, meaning, phonetic, pos, example`). Rename or delete them with a swipe.
- **Today**: new words done out of the daily target, reviews left, a time estimate from your own pace, progress, and an exam countdown with the pace needed to finish the book.
- **Study session**: due learning steps first, then due reviews, then new words. A new word shows its page, then a quick multiple-choice check (**I Know This** skips it). Reviews ask in the mode you choose: **recall and rate** (Again / Hard / Good / Easy, each showing when the word comes back), **multiple choice**, **spelling** (US/UK speech, a hint, and a letter-by-letter diff), or **mixed**. Every answer is saved at once, so closing early loses nothing; a summary ends the session.
- **Scheduling**: FSRS-6 runs on the device (VocabKit), with a 0.9 retention target that can be changed. A study day starts at 04:00, so late-night study counts for the evening before.
- **Search** your downloaded words by English prefix, inflected form, similar spelling or Chinese meaning. A **word page** has speech, meanings, the English definition, forms, the exam lists the word is on and your progress. **Favourites** and **Hard Words** (forgotten twice or more) have their own lists.
- **Statistics**: today, study streak, words a day over 30 days, accuracy trend, learned words by when they're due, and book progress. Every threshold is spelled out.
- **Sync**: answers are kept as an append-only log and uploaded automatically (at launch, after a session, when the app comes back) or with **Sync Now**; card state is replayed from the log, so two devices can't conflict. Studying works offline.

### AI help (v0.4, optional)

Bring your own key ([spec](docs/ai-spec.md)): requests go straight from the iPhone to the provider you choose, nothing passes through CourseTable's server, and without a connection the app works as before.

- **Settings → AI Help**: add a connection from a preset (DeepSeek, Qwen, Kimi, GLM, Doubao and SiliconFlow work in mainland China without a VPN; OpenAI, Claude, Gemini, OpenRouter; Ollama / LM Studio on your Wi-Fi; or any OpenAI-compatible URL), paste the key (kept in this iPhone's Keychain only), pick or load a model, and test it. Monthly requests and tokens are shown per connection.
- **Word page**: Explain, Memory Aid, Compare, Collocations, streamed and saved on the device, so opening one again is free. **"Why?"** after a wrong choice or spelling explains the mistake.
- **Long Sentences** (长难句) and **Writing Feedback** (作文, 英语一/二) in the Words tab and the Assistant: paste, or read text from a photo on the device.
- **Quick Add** in the to-do editor: "明天高数课后交论文" fills in the title, date and "after 高等数学" for you to check.
- **Assistant** (✨ on Today and Words): a chat kept on the device; this week's classes and your hard words are shared only if you switch them on.
- Answers are labelled "AI-generated · model". The dictionary still decides what counts as a correct answer.

## Import format

One course per line. Columns are separated by commas, tabs (spreadsheet paste), semicolons or full-width commas `，`; the separator is detected automatically. Put a field in double quotes if it contains the separator, e.g. `"1-8,10-16"`.

| Column | Header names recognised | Required | Examples |
|---|---|---|---|
| name | `name`, `course`, `课程`, `课程名称`… | yes | `Advanced Mathematics`, `高等数学` |
| teacher | `teacher`, `教师`, `任课教师`… | no | `Li`, `李老师` |
| room | `room`, `location`, `教室`, `上课地点`… | no | `A101` |
| weekday | `weekday`, `day`, `星期`, `周几` | yes | `1`–`7`, `Mon`, `Monday`, `周一`, `星期三`, `日` |
| periods | `periods`, `time`, `节次`, `上课时间` | yes | `3`, `1-2`, `第1-2节`, `1,2`, or a time range `08:00-09:40`, matched to your period times |
| weeks | `weeks`, `周次`, `起止周` | no (default: whole term) | `1-16`, `1-16周`, `1-15单`, `2-16(双)`, `1-16 odd`, `1-8,10-16`, `1,3,5,7` |

- With a header row (English or Chinese), columns can come in any order and unknown columns (notes, credits…) are ignored.
- Without a header row the order must be `name, teacher, room, weekday, periods, weeks`.
- Week lists that don't fit a single range, or non-consecutive periods, become several courses with the same name and colour.
- Blank lines and lines starting with `#` are skipped. Files may be UTF-8, UTF-16 or GB18030 (what Excel on Chinese Windows saves).
- **Insert Example** on the import screen fills in a sample in the app's language.

```csv
课程,教师,教室,星期,节次,周次
高等数学,李老师,A101,周一,1-2,1-16周
物理实验,赵老师,实验楼3,周四,5-7,1-15单周
数据结构,陈老师,C305,周五,1-2,"1-8,10-16周"
```

## Repository layout

```
CourseKit/            Swift package with all timetable and vocabulary logic; builds and tests on Windows, Linux and macOS
  Sources/CourseKit/    LocalDate, TimeOfDay, Semester, PeriodSchedule, Course, Timetable, WeekGrid, ScheduleSnapshot,
                        CSV + CourseImport + ImportPlan (import/export), CourseTemplate (course list + placing), Reminders,
                        ClassActivities (Live Activity windows and plan), Todos, Focus (Pomodoro plan and run)
  Sources/VocabKit/     Vocabulary learning (docs/vocabulary-spec.md): FSRS-6 + Scheduler, StudyClock, DailyPlan,
                        StudySession, AnswerGrader, ChoicePool, WordEntry, ECDICT + ContentFile + SeedSQL,
                        WordListImport, WordSearch, VocabStats, SyncMerge, AuthLink
  Sources/VocabContent/ `vocab-content` tool: ECDICT's ecdict.csv → content/kaoyan-ecdict.csv → the Supabase seed migration
  Sources/AIKit/        AI help (docs/ai-spec.md): AIProvider presets, AIConnection requests (OpenAI-compatible and
                        Anthropic), SSEParser + AIStreamDecoder, AIError, AIPrompts, TodoDraft, MarkdownBlock, SentenceWords
  Tests/                Swift Testing unit tests (CourseKitTests, VocabKitTests, AIKitTests)
content/              Word lists built from ECDICT (MIT, see content/ECDICT-LICENSE); content/.cache/ holds the
                      downloaded source and is not committed
CourseTable/          iOS app target (SwiftUI views, SwiftData records, assets)
  Reminders/            Alerts.sync: class Live Activities (ClassLiveActivities), class and to-do notifications
  Planner/              Planner.store (to-dos, focus history; PlannerSchemaV1) and PlannerModel
  Focus/                FocusEngine (the running session, its Live Activity and notifications), FocusView,
                        SoundPlayer (gapless loops)
  Sounds/               sound-<id>.m4a, built by tools/make_sounds.py from content/sounds/sources.json
  AI/                   AIStore (connections, Keychain keys, usage, saved answers and chats), AITransport (URLSession;
                        DemoAITransport for -aiDemo), AIRun, AISettingsView, WordAIHelp, StudyToolsViews, TextScanner,
                        AssistantView
  Vocabulary/           The Words tab: VocabApp.swift (coordinator: sign-in state, email links, sign-out; speech)
    Backend/              VocabBackend protocol + DTOs; SupabaseBackend (the only file that uses supabase-swift);
                          DemoBackend + VocabDemo (in memory, for tests, previews and UI tests)
    Store/                VocabRecords (SwiftData, its own Vocabulary.store, VocabSchemaV1 + migration plan),
                          VocabModel (cards replayed from the answer log, plan, search), SyncEngine
    Views/                WordsTab (sign-in), WordsHomeView, StudySessionView, WordDetailView (+ search, lists),
                          BooksView (+ import), VocabStatsView, VocabSettingsView, WordComponents
CourseTableWidget/    WidgetKit extension: widgets, the class and focus Live Activities
Shared/               Code and strings compiled into both the app and the widget (palette, snapshot file, Localizable.xcstrings)
CourseTableTests/     App-level tests, run in the iOS Simulator on CI (timetable + VocabTests)
CourseTableUITests/   UI tests that drive the app and take screenshots (Screenshots workflow)
CourseTable.xcodeproj Xcode project (synced folders: new files are picked up automatically)
supabase/             The vocabulary backend: config.toml, migrations (schema + row-level security, generated word
                      content), tests (pgTAP + api/smoke.sh), remote-auth.json (cloud sign-in options), checks/
tools/                check_strings.py, add_strings.py (zh-Hans strings); xcresult_crash.py, xcresult_screenshots.py
                      (crash reports and named screenshots from a downloaded CI results bundle); make_sounds.py
.github/workflows/    CI: coursekit.yml (Linux), ios.yml (macOS + Simulator), backend.yml (Linux), and the manual
                      screenshots.yml, ipa.yml, backend-deploy.yml, sounds.yml (Linux)
```

How the pieces fit together:

1. **CourseKit** holds the timetable model and all the rules: week numbers, odd/even weeks, period times, clashes, the current and next class, and the grid layout. It doesn't use UIKit, SwiftUI or SwiftData, so it compiles anywhere Swift does.
2. **The app** stores courses and settings with SwiftData (`CourseRecord`, `SettingsRecord`), converts them to a `CourseKit.Timetable`, and renders that timetable.
3. **The widget** never opens the database. Whenever the timetable changes, the app writes a small versioned JSON snapshot to the shared App Group folder (`Shared/SnapshotStore.swift`). The widget reads that snapshot and uses CourseKit to build a timeline, with entries at every class start and end.
4. **Import** is `CourseImporter` (text → courses + per-line problems) followed by `Timetable.planningImport` (skips duplicates, assigns colours, gives the resulting timetable for clash checks). `ImportView` only shows the plan and inserts `plan.added`.
5. **Reminders**: `Timetable.upcomingReminders` picks the next classes. `CourseTable/Reminders/ClassReminders.swift` turns them into local notifications and replaces the pending set.
6. **Words**: `VocabApp` owns the sign-in state, a `VocabModel` and a `SyncEngine`. The model keeps word content, books, the answer log, favourites and settings in `Vocabulary.store`, a SwiftData file separate from the timetable's. Cards aren't stored: VocabKit's `Scheduler` replays them from the answer log. The `SyncEngine` talks to an `any VocabBackend`: `SupabaseBackend` in the app, `DemoBackend` in tests and with `-uiTesting`. On the server, Postgres row-level security keeps every learner's rows private, and answers are append-only (client UUIDs, server-stamped `received_at`). Details: [docs/vocabulary-spec.md](docs/vocabulary-spec.md).

## Developing on Windows

Installed on this PC: the Swift 6.4 toolchain, Visual Studio 2022 C++ tools, the Windows 11 SDK, Git, the GitHub CLI, and VS Code with the Swift extension.

```bash
cd CourseKit
swift build
swift test
```

- `swift test` prints a warning: *reference to type 'wchar_t' broken by a context change*. It's a known Swift-on-Windows module warning and is harmless.
- If `swift` isn't found, open a new terminal. PATH changes only reach newly started programs.
- Open the repo folder in VS Code for code completion in `CourseKit`. You can edit the app and widget code on Windows too, but only CI (or a Mac) can compile it.

Day-to-day workflow:

- Add Swift files by dropping them into `CourseTable/` (app), `CourseTableWidget/` (widget), `Shared/` (both) or `CourseTableTests/`. The Xcode project uses synced folders, so `project.pbxproj` never needs editing.
- New UI text: write it in English in the code (`Text("…")` or `String(localized: "…")`) and add the key with its `zh-Hans` translation to `Shared/Localizable.xcstrings`. `python tools/add_strings.py translations.json` adds keys in exactly the format Xcode writes, and `python tools/check_strings.py` must report nothing missing.
- When an app test crashes on CI, download the `TestResults` artifact and run `python tools/xcresult_crash.py <folder>`: it prints each crash's message and crashed thread, no Xcode needed. If the `screenshots` artifact is missing, `python tools/xcresult_screenshots.py <UITestResults folder> <out>` saves the named screenshots from the `UITestResults` artifact.
- Never round-trip UTF-8 files through Windows PowerShell 5.1 `Get-Content`/`Set-Content`: it mangles the Chinese text and adds a BOM.
- Put new logic in CourseKit with a test first. It's the only part you can run locally.
- Line endings are forced to LF by `.gitattributes`, so Windows and Mac edits don't fight.

## Continuous integration

| Workflow | Runs on | When | What |
|---|---|---|---|
| `CourseKit` | Ubuntu, `swift:6.4` container | pushes/PRs touching `CourseKit/` | `swift build` + `swift test` |
| `iOS` | `macos-26` (Xcode 26.x) | pushes/PRs touching the app, widget, shared code, project or CourseKit sources | `xcodebuild test`: builds the app and widget, runs `CourseTableTests` on the newest iPhone simulator |
| `IPA` | `macos-26` | **manual only** | builds a Release build for iPhones, ad-hoc signed with its entitlements, and uploads `CourseTable.ipa` (app + widget, iOS 17+) for sideloading tools (iloader, SideStore, AltStore, LiveContainer) |
| `Screenshots` | `macos-26` | **manual only** | runs the UI tests (`CourseTableUITests` scheme) and uploads a `screenshots` artifact: every main screen in English and Chinese, the course-list, placing and import flows, and the Words tab (sign-up, a whole study session, each question type, every Words screen) |
| `Backend` | Ubuntu | pushes/PRs touching `supabase/` | starts a local Supabase stack, applies the migrations, runs the pgTAP tests (schema, content, row-level security) and an HTTP smoke test |
| `Sounds` | Ubuntu | **manual only** | builds the focus sounds from `content/sounds/sources.json` with ffmpeg (seamless loops, -20 LUFS, AAC) and uploads a `sounds` artifact for `CourseTable/Sounds/` |
| `Backend deploy` | Ubuntu | **manual only** (the owner starts it) | pushes the migrations to the cloud project, applies `supabase/remote-auth.json`, and checks the result; needs the `SUPABASE_ACCESS_TOKEN` and `SUPABASE_DB_PASSWORD` secrets and the `SUPABASE_PROJECT_REF` variable |

The repo is public (since 2026-10-09), so GitHub Actions on standard runners, macOS included, costs nothing and has no monthly limit (a run's billable time shows 0). A normal `iOS` run takes about 5–9 minutes, a `Screenshots` run about 22 (about 15 for selected tests), and an `IPA` run about 3. The iOS job runs only when relevant files change and cancels superseded runs. Security settings: secret scanning and push protection are on, workflows from forks need approval for all external contributors, and the workflow token is read-only.

To see the app without a Mac: run **Actions → Screenshots → Run workflow** (or `gh workflow run screenshots.yml`), then download the artifact with `gh run download <run-id> -n screenshots`. To save minutes, run selected tests only: `gh workflow run screenshots.yml -f only=CourseTableUITests/WordsUITests/testWordsScreenshotTour` (`xcodebuild -only-testing` values, comma-separated). UI tests launch the app with `-uiTesting`, so it uses in-memory stores and the demo vocabulary backend, never touches the network, and never writes the widget snapshot or schedules notifications. `-demoData` adds a demo timetable; `-vocabDemo` signs in a demo learner with two weeks of answers, 30 words and an exam date; `-vocabMode recognition|choice|spelling|mixed` sets that learner's review questions; `-liveActivityDemo before|during|switch` starts a demo class Live Activity; `-focusDemo` opens Focus with a session running. With `-demoData`, today also gets a few demo to-dos. To run it by hand, use **Actions → iOS → Run workflow**, or `gh workflow run ios.yml`. Useful commands: `gh run list`, `gh run watch`, `gh run view --log-failed`.

## Opening the project on a Mac later

1. Install Xcode 26 or newer from the App Store. Sign in with your Apple ID under **Xcode → Settings → Accounts**.
2. Clone and open the project:
   ```bash
   git clone https://github.com/Hilbert0619/CourseTable.git
   open CourseTable/CourseTable.xcodeproj
   ```
3. Select the **CourseTable** target, open **Signing & Capabilities** and choose your **Team**. Do the same for **CourseTableWidgetExtension**. If Xcode says a bundle ID is taken, change the `io.github.hilbert0619` prefix in both targets.
4. Pick an iPhone simulator, or your iPhone over USB, and press **Run** (⌘R). SwiftUI previews work in any view file (**Editor → Canvas**).
5. Free Apple ID notes: apps you install on your own iPhone expire after 7 days (just run again). If Xcode reports that your team doesn't support **App Groups**, remove that capability from both targets. The app still works, but the widget shows "Open Course Table…" until you sign with a paid team. If you change the App Group ID, update it in both `.entitlements` files and in `Shared/SnapshotStore.swift`.
6. TestFlight and the App Store need the paid Apple Developer Program.

Nothing needs to be regenerated on the Mac. The Xcode project in the repo is the real project.

## Design notes

- **Weeks** run Monday–Sunday. Week 1 is the week containing the chosen first day of term. Odd/even is counted from the week number (week 1 is odd).
- **Dates without time zones:** term dates are `LocalDate` values (year, month, day), and day counting is integer arithmetic. Week numbers therefore never shift with time zones or daylight saving.
- **Snapshot versioning:** `ScheduleSnapshot.currentVersion` is bumped when its JSON shape changes. The widget ignores snapshots newer than it understands.
- **Reminder window:** iOS keeps at most 64 pending local notifications per app: classes 40 (`Timetable.maxScheduledReminders`, roughly two weeks), to-dos 12, focus switches 10, 2 spare. Each kind replaces only its own. The app reschedules them each time it comes to the foreground and whenever the timetable or reminder settings change. If you don't open the app for longer than that window, reminders stop until you open it again. Reminders whose time has already passed are skipped. Reminder settings are per device (`UserDefaults`), not part of the SwiftData store.
- **Import colours:** a course whose name matches an existing course keeps that course's colour. Other names get the least-used palette colour. The same rule applies to the course list.
- **SwiftData schemas:** the timetable store holds `CourseRecord`, `SettingsRecord` and `CourseTemplateRecord` (the course list); the Words tab has its own file, `Vocabulary.store` (`VocabSchemaV1` + `VocabMigrationPlan`); to-dos and focus history are in `Planner.store` (`PlannerSchemaV1` + `PlannerMigrationPlan`). A running focus session is kept in `UserDefaults`. The app holds real data on a phone now, so any model change needs a new `VersionedSchema` and a migration stage. The timetable store isn't versioned yet: its first change must start by describing today's models as V1.
