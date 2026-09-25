# CourseTable · 课程表

A native iOS class-schedule app: **Swift + SwiftUI + SwiftData + WidgetKit**, iOS 17+, English and Simplified Chinese UI.

[![CourseKit](https://github.com/Hilbert0619/CourseTable/actions/workflows/coursekit.yml/badge.svg)](https://github.com/Hilbert0619/CourseTable/actions/workflows/coursekit.yml)
[![iOS](https://github.com/Hilbert0619/CourseTable/actions/workflows/ios.yml/badge.svg)](https://github.com/Hilbert0619/CourseTable/actions/workflows/ios.yml)

## Features

- **Courses**: add, edit and delete by hand. Each course has a name, teacher, room, weekday, start period and length, week range and odd/even weeks. Colours are assigned automatically, and clashes are flagged.
- **Import**: paste CSV text or rows copied from a spreadsheet, or open a `.csv`/`.txt` file (Settings → Import Courses…, or the button on an empty timetable). A preview lists the courses, clashes and lines that can't be read before anything is saved. Courses that are already in the timetable are skipped. You can also replace all existing courses. See [Import format](#import-format).
- **Course list + placing**: when you only know your courses (name, room, teacher), type them one per line, e.g. `高等数学 A101 李老师`, then place them on the grid. Use **+ → Place Courses** or **Type Course List…** on an empty timetable. In placing mode, a tray lists your courses. Pick one, set its length and odd/even weeks, then tap empty slots: each tap adds that course there for the whole term. The block is shortened so it doesn't run into the next course. While placing, the grid shows all seven days and courses from every week, dimming the ones not in the week shown. Tap a placed block to adjust weeks or length in the editor. Long-press a course in the tray to remove it from the list.
- **Export**: Settings → Export as CSV shares the courses as a UTF-8 CSV file (Excel-friendly) in the same format, so an export can be imported again.
- **Class reminders**: a local notification 0–60 minutes before each class (Settings → Reminders).
- **Term**: set the first day of term and the number of weeks. The app works out the current week.
- **Period times**: edit how many periods there are per day and each period's start and end time. The default is a 12-period day.
- **Weekly grid**: Mon–Fri, plus Sat/Sun if you have weekend classes. Switch weeks with the arrows, the week menu or a swipe. Tap an empty slot to add a course there.
- **Today**: today's classes with times; the current and next class are highlighted.
- **Widgets**: on the Home Screen, the small size shows the current or next class and the medium size shows the rest of today. The small size also works in StandBy. On the Lock Screen, the rectangular widget shows the current or next class with room and teacher, the inline widget sits above the clock ("Calculus until 09:40", "10:00 Physics"), and the circular widget shows a progress ring during class or the next start time.

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
CourseKit/            Swift package with all timetable logic; builds and tests on Windows, Linux and macOS
  Sources/CourseKit/    LocalDate, TimeOfDay, Semester, PeriodSchedule, Course, Timetable, WeekGrid, ScheduleSnapshot,
                        CSV + CourseImport + ImportPlan (import/export), CourseTemplate (course list + placing), Reminders
  Tests/CourseKitTests/ Swift Testing unit tests
CourseTable/          iOS app target (SwiftUI views, SwiftData records, assets)
CourseTableWidget/    WidgetKit extension
Shared/               Code and strings compiled into both the app and the widget (palette, snapshot file, Localizable.xcstrings)
CourseTableTests/     App-level tests, run in the iOS Simulator on CI
CourseTable.xcodeproj Xcode project (synced folders: new files are picked up automatically)
.github/workflows/    CI: coursekit.yml (Linux) and ios.yml (macOS + Simulator)
```

How the pieces fit together:

1. **CourseKit** holds the timetable model and all the rules: week numbers, odd/even weeks, period times, clashes, the current and next class, and the grid layout. It doesn't use UIKit, SwiftUI or SwiftData, so it compiles anywhere Swift does.
2. **The app** stores courses and settings with SwiftData (`CourseRecord`, `SettingsRecord`), converts them to a `CourseKit.Timetable`, and renders that timetable.
3. **The widget** never opens the database. Whenever the timetable changes, the app writes a small versioned JSON snapshot to the shared App Group folder (`Shared/SnapshotStore.swift`). The widget reads that snapshot and uses CourseKit to build a timeline, with entries at every class start and end.
4. **Import** is `CourseImporter` (text → courses + per-line problems) followed by `Timetable.planningImport` (skips duplicates, assigns colours, gives the resulting timetable for clash checks). `ImportView` only shows the plan and inserts `plan.added`.
5. **Reminders**: `Timetable.upcomingReminders` picks the next classes. `CourseTable/Reminders/ClassReminders.swift` turns them into local notifications and replaces the pending set.

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
- New UI text: write it in English in the code (`Text("…")` or `String(localized: "…")`) and add the key with its `zh-Hans` translation to `Shared/Localizable.xcstrings`.
- Put new logic in CourseKit with a test first. It's the only part you can run locally.
- Line endings are forced to LF by `.gitattributes`, so Windows and Mac edits don't fight.

## Continuous integration

| Workflow | Runs on | When | What |
|---|---|---|---|
| `CourseKit` | Ubuntu, `swift:6.4` container | pushes/PRs touching `CourseKit/` | `swift build` + `swift test` |
| `iOS` | `macos-26` (Xcode 26.x) | pushes/PRs touching the app, widget, shared code, project or CourseKit sources | `xcodebuild test`: builds the app and widget, runs `CourseTableTests` on the newest iPhone simulator |

The repo is private. GitHub's free plan counts macOS minutes at 10×, which leaves roughly 200 macOS minutes a month, so the iOS job runs only when relevant files change and cancels superseded runs. To run it by hand, use **Actions → iOS → Run workflow**, or `gh workflow run ios.yml`. Useful commands: `gh run list`, `gh run watch`, `gh run view --log-failed`.

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
- **Reminder window:** iOS keeps at most 64 pending local notifications per app, so only the next 60 classes (`Timetable.maxScheduledReminders`) are scheduled, roughly two to three weeks of classes. The app reschedules them each time it comes to the foreground and whenever the timetable or reminder settings change. If you don't open the app for longer than that window, reminders stop until you open it again. Reminders whose time has already passed are skipped. Reminder settings are per device (`UserDefaults`), not part of the SwiftData store.
- **Import colours:** a course whose name matches an existing course keeps that course's colour. Other names get the least-used palette colour. The same rule applies to the course list.
- **SwiftData schema:** `CourseRecord`, `SettingsRecord`, `CourseTemplateRecord` (the course list). The app hasn't been installed anywhere yet, so the schema can still change freely. After the first install, changes need a `VersionedSchema` and a migration plan.
