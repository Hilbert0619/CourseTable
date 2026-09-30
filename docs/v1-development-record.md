# CourseTable v0.1 — Development and Testing Record

A native iOS class-timetable app (课程表), built on a Windows PC with no Mac, from an empty repo to a working app on a real iPhone.

| | |
|---|---|
| Version | **0.1.1 (build 2)**, running on the owner's iPhone since 2026-09-30 |
| Period | 2026-09-24 → 2026-09-30 |
| Repository | `D:\Projects\CourseTable` → github.com/Hilbert0619/CourseTable (private) |
| Stack | Swift 6 · SwiftUI · SwiftData · WidgetKit · iOS 17+ · English and Simplified Chinese |
| Code | ≈ 5,600 lines of Swift: logic 1,540 · logic tests 1,200 · app 1,860 · widget 410 · shared 180 · app tests 250 · UI tests 190 |
| Tests | 110 logic tests · 17 app tests · 3 UI tests: all passing |

---

## 1. Constraints that shaped the project

- **No Mac.** Development happened on Windows 11 with Swift 6.4. Only the logic library (CourseKit) can be built and tested locally. The iOS app is compiled, tested and packaged by GitHub Actions on `macos-26` runners with Xcode 26.
- **CI budget.** The repo is private, so the free plan gives 2,000 Actions minutes a month, and macOS minutes count 10×: about 200 macOS minutes. Normal iOS runs are path-filtered; the expensive workflows are manual only.
- **Keep app code thin.** All rules live in CourseKit (pure Swift, no UIKit/SwiftUI/SwiftData) with tests written first. The app and widget only store and display.
- **Hand-written Xcode project** in Xcode 16's synced-folder format, so new files need no project edits. The one exception was adding the UI-test target.
- **Network:** GitHub is reached through a local proxy (`git -c http.proxy=http://127.0.0.1:7892 …`), set per command, never globally.

## 2. Architecture

```
CourseKit/            Swift package: all logic, tests run on Windows + Linux
  Sources/CourseKit/    LocalDate, TimeOfDay, Semester, PeriodSchedule, Course, Timetable, WeekGrid,
                        ScheduleSnapshot, CSV, CourseImport, ImportPlan, CourseTemplate, Reminders
CourseTable/          iOS app: Views/, Persistence/ (SwiftData), Import/, Reminders/
CourseTableWidget/    WidgetKit extension: Home Screen (small, medium) + Lock Screen (rectangular, inline, circular)
Shared/               Compiled into app and widget: palette, SnapshotStore, localized names, Localizable.xcstrings
CourseTableTests/     App tests (Swift Testing, run in the Simulator on CI)
CourseTableUITests/   XCUITest flows + screenshot tour
.github/workflows/    coursekit.yml · ios.yml · screenshots.yml · ipa.yml
```

- **Data:** SwiftData models `CourseRecord`, `SettingsRecord` (term and period times) and `CourseTemplateRecord` (course list). They're converted to a `CourseKit.Timetable` for all calculations.
- **Widget:** never opens the database. The app writes a versioned JSON `ScheduleSnapshot` into the App Group folder, and the widget builds its timeline from it.
- **Dates:** term dates are time-zone-free `LocalDate` values, and weeks run Monday–Sunday, so week numbers never shift with time zones or daylight saving.

## 3. Development phases

### Phase 1: environment (2026-09-24)
Installed and verified the Swift 6.4 toolchain, VS 2022 C++ tools, the Windows SDK, Git, GitHub CLI and VS Code. See `C:\Users\AI\Downloads\ios-dev-setup\ENVIRONMENT-REPORT.md`.

### Phase 2: project setup and MVP (2026-09-24)
- CourseKit: dates, weeks, odd/even weeks, period schedule, clashes, current and next class, grid layout, widget snapshot. 52 → 54 tests.
- App: Today view, weekly grid (week switcher, swipe, tap to add), course editor with clash warnings, term and period-time settings.
- Widget: small (current or next class) and medium (rest of today).
- CI: Linux CourseKit tests, plus a macOS build with Simulator tests.

### Phase 3: import, reminders, Lock Screen (2026-09-24)
- **CSV/text import:** English or Chinese headers in any order, and loose notations such as `1-16单`, `2-16(双)`, `1-8,10-16`, `第1-2节`, `08:00-09:40`, `周三`. Files can be UTF-8, UTF-16 or GB18030. A preview shows clashes and per-line problems; duplicates are skipped; existing courses can optionally be replaced.
- **Export as CSV** (UTF-8 with BOM, Excel-friendly), in the same format, so it round-trips.
- **Class reminders:** a local notification 0–60 minutes before class. There's a rolling window of 60 notifications (iOS allows 64), rescheduled on every foreground and every change.
- **Lock Screen widgets:** rectangular, inline, and circular (a progress ring during class).
- CourseKit reached 97 tests.

### Phase 4: course list and tap-to-place (2026-09-24), the owner's own request
- Type courses one per line as "name room teacher", e.g. `高等数学 A101 李老师`. Spaces, commas or tabs all work, and colours follow course names.
- **Placing mode:** pick a course from a tray, set its length and odd/even weeks, then tap empty slots. Each placement lasts the whole term and is shortened so it doesn't run into the next course. Courses from other weeks show dimmed.
- CourseKit reached 110 tests.

### Phase 4b: seeing the app for the first time (2026-09-24)
- Added a UI-test target and a manual **Screenshots** workflow. It covers the course-list and placing flow, importing the example, and a tour of every main screen in English and Chinese: 26 screenshots per run.
- The screenshots were reviewed and the problems fixed (see section 5).

### Phase 5: onto a real iPhone (2026-09-29 → 30)
- Distribution options considered:
  - **TestFlight** needs the Apple Developer Program ($99/year).
  - **A rented cloud Mac** can't connect to a phone, so it was rejected.
  - **The owner's 2014 Mac mini** tops out at macOS 12 / Xcode 14.2, too old for this project.
  - **LiveContainer** was reviewed: it works, but doesn't support widgets.
- Chosen route: a manual **IPA** workflow builds `CourseTable.ipa`, and the owner installs it with **iloader** using a free Apple ID.
- The first install showed the widget stuck on "打开课程表，添加你的课程。". Fixed in 0.1.1 and **confirmed working on the device**.

## 4. Testing

| Layer | Where it runs | Count | What it covers |
|---|---|---|---|
| CourseKit unit tests | Windows (local) + Linux CI | **110** | dates and weeks, parity, clashes, grid lanes, snapshot versioning, CSV parsing (quoting, delimiters, line numbers), field parsers (table-driven, English and Chinese), import planning, export round trip, text decoding, reminder planning (midnight crossing, limits, end of term), course list parsing, placing (fitting the day, avoiding the next course, parity) |
| App tests | iOS Simulator (`ios.yml`) | **17** | SwiftData round trips, snapshot store, App Group resolution, reminder text, GB18030 files, import error messages, import → database, course list → database |
| UI tests | iOS Simulator (`screenshots.yml`) | **3** | type a course list and place courses (including the Weekend toggle and which swipes change the week); import the example from Settings; screenshot tour in both languages |
| Visual review | Screenshot artifacts | 2 rounds | layout, truncation, translations, empty states |
| Device test | Owner's iPhone | – | install, use, widget |

The CI runs are the evidence for each milestone:

| Milestone | Run |
|---|---|
| MVP | actions/runs/36083740975 |
| Phase 3 | actions/runs/36086710400 |
| Phase 4 | actions/runs/36088743636 |
| All UI tests green | actions/runs/36093341882 |
| 0.1.1 widget fix | actions/runs/36671907943, IPA build 36671920362 |

All of these ran without build warnings.

Other quality checks:
- A script checks that every localizable string in the code has a zh-Hans entry. The catalog has 153 keys.
- App code was reviewed line by line before each CI run, and most iOS runs passed on the first try.

## 5. Problems found and fixed

| # | Problem | How it was found | Fix |
|---|---|---|---|
| 1 | Swiping the course tray in placing mode also changed the week | code review while explaining test coverage | the swipe gesture covers only the grid; a UI test checks it (`ca18bc2`) |
| 2 | First UI test timed out while the Simulator was still booting | first Screenshots run | boot the Simulator before testing and retry a failed test once (`0086488`) |
| 3 | Course names broke mid-word in narrow grid columns ("Physic s") | screenshot review | smaller block text, 5 columns in placing mode with a Weekend toggle (`0086488`) |
| 4 | Teacher and room fields in the editor had no labels | screenshot review | labelled rows (`0086488`) |
| 5 | **Widget stuck on "打开课程表，添加你的课程。" after sideloading** | device test on the owner's iPhone | iloader re-signs with the user's team and renames the App Group to `group.<bundle id>.<TEAMID>`, but the app looked only for the fixed name, so it never wrote the snapshot. The app and widget now find the group at runtime, Settings shows the widget's status, and the IPA carries its entitlements (`4e26a7b`, v0.1.1) |
| – | One test assertion didn't compile (a quirk of the Swift Testing `#expect` macro) | local build | rewrote the assertion |
| – | CI cost was misstated 10× in conversation (4 macOS minutes = 40 billed minutes, not 40 of 200) | billing check | corrected in README, HANDOFF and project memory |

## 6. CI and distribution

| Workflow | Trigger | Takes | Purpose |
|---|---|---|---|
| `CourseKit` | push touching CourseKit | ~1 min (Linux) | build and run the 110 logic tests |
| `iOS` | push touching app, widget, shared code or project | ~4 macOS min | build the app and widget, run the 17 app tests |
| `Screenshots` | manual | ~8 macOS min | 3 UI tests and a `screenshots` artifact |
| `IPA` | manual | ~3 macOS min | `CourseTable.ipa`, ad-hoc signed with entitlements, for sideloading |

**To install a new build:** run the IPA workflow, download it with `gh run download <id> -n CourseTable-ipa`, then install it with iloader using the same Apple ID. The app updates in place and keeps its data. Free signing expires after 7 days, so re-install weekly.

## 7. Known limitations and open items

- **Not yet confirmed on the device:** class reminders (notifications) and the Lock Screen widgets.
- Very long single English words (e.g. "Mathematics") can still break inside a grid block. Chinese names wrap fine.
- Reminders cover the next 60 classes and are topped up when the app is opened; there's no background refresh.
- Free-account sideloading means a re-install every 7 days. The Apple Developer Program ($99/year, TestFlight) would remove that.
- **The app now holds real data.** Any change to the SwiftData models needs a `VersionedSchema` and a migration plan (or only additive changes with defaults). Back up with Settings → Export as CSV.
- Owner's security to-dos from Phase 1: revoke the exposed GitHub token `github_pat_11B4KQLU…`, and delete the `claude-review` / `claude-fix` folders.

## 8. Next: English vocabulary learning

The owner will provide a requirements draft, written with ChatGPT, to refine before any code is written. Points to settle while refining it:

- **Where it lives:** a new tab in CourseTable, or a separate app? The logic (spaced repetition, scheduling, scoring) should go in its own Swift package, testable on Windows like CourseKit.
- **Word data source and licence:** built-in word lists (CET-4/6, IELTS…) must be legally usable. Bundle size and offline use also matter.
- **Data model:** new SwiftData models added without breaking the existing store.
- **Pronunciation and audio:** on-device `AVSpeechSynthesizer` versus recorded audio.
- **Links with the timetable:** e.g. study reminders in free periods, or a widget.
- **Testing plan:** logic tests first, then app tests, UI flows and screenshot review, the same pipeline as v0.1.
