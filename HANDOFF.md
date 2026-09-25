# Session Handoff — CourseTable (after Phase 3: import, reminders, Lock Screen widgets)

Date: 2026-09-25 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (private)

## Phase 3 results

- **Import** (CourseKit `CSV`, `CourseImporter`, `Timetable.planningImport`): paste CSV/TSV (spreadsheet rows) or open a file. English or Chinese headers can come in any order. The parser accepts loose notations (`1-16单`, `2-16(双)`, `1-8,10-16`, `第1-2节`, `08:00-09:40`, `周三`, `Wed`). Files can be UTF-8, UTF-16 or GB18030. The preview shows clashes and per-line problems; duplicates are skipped; there's an optional "replace existing". Format documented in README → *Import format*.
- **Export as CSV** (UTF-8 with BOM), in the same format, so exports can be imported back.
- **Class reminders**: a local notification 0–60 min before class. The next 60 classes are scheduled (iOS allows 64 pending) and rescheduled on every foreground and every timetable/setting change. Settings are per device in `UserDefaults`.
- **Widgets**: Lock Screen `accessoryRectangular`, `accessoryInline`, `accessoryCircular` (progress ring during class). StandBy uses the existing small widget.
- **Tests**: CourseKit 97 (43 new), green on Windows and Linux. App tests add reminder payloads, GB18030 decoding, import messages and import → SwiftData.
- **CI** (all green, no build warnings, 14 app tests in the Simulator, first-try pass, 4 min): iOS https://github.com/Hilbert0619/CourseTable/actions/runs/36086710400 · CourseKit https://github.com/Hilbert0619/CourseTable/actions/runs/36086710465
- Heads-up from GitHub: `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19. `coursekit.yml` runs inside the `swift:6.4` container, so it should be unaffected, but check the next run.
- Still **never seen running**: the new screens, notifications and Lock Screen widgets are only compiled and unit-tested.

## Handoff prompt for the next phase

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] Native iOS course-table app "CourseTable" — Phase 4

## Where things stand (Phase 3 done, 2026-09-25)
Repo D:\Projects\CourseTable → github.com/Hilbert0619/CourseTable (private). Read README.md and HANDOFF.md first.
- CourseKit/ = all logic: dates/weeks/periods, Course, Timetable, WeekGrid, ScheduleSnapshot, CSV + CourseImporter +
  ImportPlan (import/export), Reminders. 97 tests; `cd CourseKit && swift test` must stay green on Windows.
- CourseTable/ (app: Views/, Persistence/, Import/, Reminders/), CourseTableWidget/ (Home + Lock Screen widget),
  Shared/ (both targets: palette, SnapshotStore, LocalizedNames, Localizable.xcstrings), CourseTableTests/ (CI only).
- CourseTable.xcodeproj is hand-written in Xcode 16+ synced-folder format: drop new files into the folders,
  never edit project.pbxproj unless adding a target/setting.
- CI: .github/workflows/coursekit.yml (Linux) and ios.yml (macos-26, xcodebuild test, CODE_SIGNING_ALLOWED=NO).
  Private repo → macOS minutes count 10× (~200/month, one iOS run ≈ 4 min ≈ 40 billed): keep ios.yml
  path-filtered, no matrix builds, review app code carefully and aim for one iOS run per phase.
- Nothing has been run on a device or seen in a Simulator yet (no Mac).

## Environment notes (Windows 11, no Mac)
- Swift 6.4, VS 2022 C++ tools, Windows SDK 26100, Git 2.54, gh 2.101 (signed in as Hilbert0619), VS Code + Swift ext.
- If swift/gh aren't found, the process has a stale PATH: reload it from the registry
  ($env:Path = Machine + User PATH) or open a new terminal. In Git Bash, gh is at /c/Program Files/GitHub CLI.
- GitHub from this network needs Clash: `git -c http.proxy=http://127.0.0.1:7892 push`, and HTTPS_PROXY for gh
  (per command only; don't set it globally).
- Harmless warning in swift build/test: 'wchar_t' modularization — ignore.
- Don't change global system settings or the default Python (32-bit 3.12 on purpose).
- Shared/Localizable.xcstrings is Xcode JSON (2-space indent, " : " separators, keys sorted by code point).
  A script can add keys if it reproduces that format exactly; check every new UI literal has a zh-Hans entry.

## Decisions (don't reverse; ask me if unsure)
- Native only: Swift + SwiftUI + SwiftData + WidgetKit; iOS 17+; UI in English + zh-Hans (String Catalogs).
- New logic goes into CourseKit with tests first; app code stays thin.
- Every new UI string gets a zh-Hans entry in Shared/Localizable.xcstrings.
- Bundle IDs io.github.hilbert0619.CourseTable(.Widget); App Group group.io.github.hilbert0619.CourseTable.
- Import format = README "Import format" (headerless order: name, teacher, room, weekday, periods, weeks).
  Week lists that don't fit one range become several courses; duplicates are skipped; same name → same colour.
- Reminders: rolling window of 60 local notifications, rescheduled from RootView; settings in UserDefaults
  (per device), not SwiftData. No background refresh yet.

## How I like to work
- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage. Verify for real (tests/CI), don't trust "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.

## Leftovers to remind me about at the start
1. Revoke the exposed GitHub token github_pat_11B4KQLU… (GitHub → Settings → Developer settings → Tokens)
2. Delete OneDrive\Desktop\.claude\Desktop\claude-review and Downloads\claude-fix, empty the OneDrive recycle bin

## Phase 4 candidates (confirm scope with me first)
1. School-specific import: I provide a real export from my school's 教务 system (often a periods × weekdays
   grid with cells like "高等数学 / 李老师 / 1-16周 / A101"); add a parser for it in CourseKit + tests
2. Multiple terms (archive old term, switch between terms) — needs a SwiftData VersionedSchema + migration plan
3. Grid options: show/hide weekends, show non-current-week courses greyed out
4. Reminders: per-course mute; top up the window in the background (BGAppRefreshTask) so it survives weeks unopened
5. Widgets: tap a class to open it in the app (deep link); configurable widget (e.g. show tomorrow)
6. First Mac session checklist: run on device, check import/export, notifications and Lock Screen widgets,
   fix anything only visible in the Simulator, screenshots

## Done = next milestone
- Agreed features implemented with CourseKit tests; swift test green locally
- iOS CI green (send me the run link); README/HANDOFF.md updated for the phase after
```
