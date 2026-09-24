# CourseTable · 课程表

A native iOS class-schedule app: **Swift + SwiftUI + SwiftData + WidgetKit**, iOS 17+, English and Simplified Chinese UI.

[![CourseKit](https://github.com/Hilbert0619/CourseTable/actions/workflows/coursekit.yml/badge.svg)](https://github.com/Hilbert0619/CourseTable/actions/workflows/coursekit.yml)
[![iOS](https://github.com/Hilbert0619/CourseTable/actions/workflows/ios.yml/badge.svg)](https://github.com/Hilbert0619/CourseTable/actions/workflows/ios.yml)

## Features (MVP)

- **Courses**: add, edit and delete by hand. Each course has a name, teacher, room, weekday, start period and length, week range and odd/even weeks. Colours are assigned automatically, and clashes are flagged.
- **Term**: set the first day of term and the number of weeks. The app works out the current week.
- **Period times**: edit how many periods there are per day and each period's start and end time. The default is a 12-period day.
- **Weekly grid**: Mon–Fri, plus Sat/Sun if you have weekend classes. Switch weeks with the arrows, the week menu or a swipe. Tap an empty slot to add a course there.
- **Today**: today's classes with times; the current and next class are highlighted.
- **Home-screen widget**: the small size shows the current or next class, and the medium size shows the rest of today.

## Repository layout

```
CourseKit/            Swift package with all timetable logic; builds and tests on Windows, Linux and macOS
  Sources/CourseKit/    LocalDate, TimeOfDay, Semester, PeriodSchedule, Course, Timetable, WeekGrid, ScheduleSnapshot
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
