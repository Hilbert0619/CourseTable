# Session Handoff — CourseTable (after Phase 2: project setup + MVP)

Date: 2026-09-25 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (private)

## Phase 2 results

- **Repo** created and pushed; `.gitattributes` forces LF; commits use `253035987+Hilbert0619@users.noreply.github.com` (repo-local config).
- **CourseKit** (Swift package, all logic): 54 Swift Testing tests, green on Windows (Swift 6.4) and Linux CI.
- **iOS app** (SwiftUI + SwiftData, iOS 17+): Today view, weekly grid (week switcher, swipe, tap-to-add), course editor with clash warnings, term + period-time settings, English + Simplified Chinese.
- **Widget** (WidgetKit): small = current/next class, medium = rest of today; fed by a JSON snapshot in App Group `group.io.github.hilbert0619.CourseTable`.
- **CI**: `CourseKit` (Linux, `swift:6.4`) and `iOS` (`macos-26`, Xcode 26.x, build + Simulator tests). Milestone runs (all green, no warnings): iOS https://github.com/Hilbert0619/CourseTable/actions/runs/36083740975 · CourseKit https://github.com/Hilbert0619/CourseTable/actions/runs/36083301393
- **README.md** explains the Windows workflow and how to open/run on a Mac.

## Handoff prompt for the next phase

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] Native iOS course-table app "CourseTable" — Phase 3: polish + next features

## Where things stand (Phase 2 done, 2026-09-25)
Repo D:\Projects\CourseTable → github.com/Hilbert0619/CourseTable (private). Read README.md and HANDOFF.md first.
- CourseKit/ = all logic (LocalDate, TimeOfDay, Semester, PeriodSchedule, Course with odd/even weeks, Timetable,
  WeekGrid lanes, ScheduleSnapshot). 54 tests; `cd CourseKit && swift test` must stay green on Windows.
- CourseTable/ (app, SwiftUI + SwiftData), CourseTableWidget/ (WidgetKit), Shared/ (both targets:
  palette, SnapshotStore, Localizable.xcstrings), CourseTableTests/ (run on CI only).
- CourseTable.xcodeproj is hand-written in Xcode 16+ synced-folder format: drop new files into the folders,
  never edit project.pbxproj unless adding a target/setting.
- CI: .github/workflows/coursekit.yml (Linux) and ios.yml (macos-26, xcodebuild test, CODE_SIGNING_ALLOWED=NO).
  Private repo → macOS minutes count 10× (~200/month): keep ios.yml path-filtered, don't add matrix builds.

## Environment notes (Windows 11, no Mac)
- Swift 6.4, VS 2022 C++ tools, Windows SDK 26100, Git 2.54, gh 2.101 (signed in as Hilbert0619), VS Code + Swift ext.
- If swift/gh aren't found, the process has a stale PATH: reload it from the registry
  ($env:Path = Machine + User PATH) or open a new terminal.
- GitHub pushes from this network need Clash: `git -c http.proxy=http://127.0.0.1:7892 push` (one-off; don't set it globally).
- Harmless warning in swift build/test: 'wchar_t' modularization — ignore.
- Don't change global system settings or the default Python (32-bit 3.12 on purpose).

## Decisions (don't reverse; ask me if unsure)
- Native only: Swift + SwiftUI + SwiftData + WidgetKit; iOS 17+; UI in English + zh-Hans (String Catalogs).
- New logic goes into CourseKit with tests first; app code stays thin.
- Every new UI string gets a zh-Hans entry in Shared/Localizable.xcstrings.
- Bundle IDs io.github.hilbert0619.CourseTable(.Widget); App Group group.io.github.hilbert0619.CourseTable.

## How I like to work
- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage. Verify for real (tests/CI), don't trust "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.

## Leftovers to remind me about at the start
1. Revoke the exposed GitHub token github_pat_11B4KQLU… (GitHub → Settings → Developer settings → Tokens)
2. Delete OneDrive\Desktop\.claude\Desktop\claude-review and Downloads\claude-fix, empty the OneDrive recycle bin

## Phase 3 candidates (confirm scope with me first)
1. Import a timetable: paste/parse text or a CSV exported from the school's 教务 system (parser in CourseKit + tests)
2. Class reminders: local notifications N minutes before each class (schedule logic in CourseKit)
3. Lock-screen / StandBy widgets (accessoryRectangular, accessoryInline)
4. Multiple terms (archive old term, switch between terms)
5. Grid options: show/hide weekends, show non-current-week courses greyed out
6. First Mac session checklist: run on device, fix anything only visible in the Simulator, screenshots

## Done = next milestone
- Agreed features implemented with CourseKit tests; swift test green locally
- iOS CI green (send me the run link); README/HANDOFF.md updated for the phase after
```
