# Session Handoff — CourseTable (after Phase 4b: UI tests + screenshot review)

Date: 2026-09-25 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (private)

## Phase 4b results (seeing the app for the first time)

- **UI tests** (`CourseTableUITests` target + scheme) drive the app in the iOS Simulator. They cover:
  - typing a course list, placing courses, the Weekend toggle, and which swipes change the week
  - importing the example CSV from Settings
  - a screenshot tour of every main screen in English and Chinese

  The app launched with `-uiTesting` uses an in-memory store (`-demoData` for the demo timetable) and skips widget/notification side effects.
- **Screenshots workflow** (`.github/workflows/screenshots.yml`, manual only): runs the UI tests and uploads a `screenshots` artifact (~8 min). Latest green run with all 3 tests passing: https://github.com/Hilbert0619/CourseTable/actions/runs/36093341882
- **Fixed after reviewing the screenshots**:
  - Swiping the placing tray changed the week.
  - Grid names broke mid-word in narrow columns. Placing mode now keeps 5 columns and has a Weekend toggle, and block type is smaller.
  - Teacher/Room fields in the editor had no labels.
- Known cosmetic leftover: very long single English words (e.g. "Mathematics") can still break inside a 5-column block. Chinese names wrap fine.

## Phase 4 results (course list + placing)

- **Course list**: type courses one per line as name, room, teacher (`高等数学 A101 李老师`; spaces, commas or tabs). The list is stored in SwiftData (`CourseTemplateRecord`) and colours follow course names.
- **Placing mode** (Timetable → **+** → Place Courses, or "Type Course List…" on an empty timetable): a tray of course chips with length and odd/even controls. Each tap on an empty slot adds the selected course there for the whole term, shortened so it doesn't run into the next course. While placing, the grid shows all 7 days and courses from all weeks, with other weeks dimmed. Tapping a block opens the editor.
- CourseKit: `CourseTemplate`, `parseList`, `placementTemplates`, `assigningColors`, `placing`, `gridPlacements(includingOtherWeeks:)`. 110 tests (13 new).
- **CI** (green, 0 warnings, 16 app tests, 4 min): iOS https://github.com/Hilbert0619/CourseTable/actions/runs/36088743636 · CourseKit https://github.com/Hilbert0619/CourseTable/actions/runs/36088743736

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
[PROJECT HANDOFF] Native iOS course-table app "CourseTable" — Phase 5

## Where things stand (Phase 4b done, 2026-09-25)
Repo D:\Projects\CourseTable → github.com/Hilbert0619/CourseTable (private). Read README.md and HANDOFF.md first.
- CourseKit/ = all logic: dates/weeks/periods, Course, Timetable, WeekGrid, ScheduleSnapshot, CSV + CourseImporter +
  ImportPlan (import/export), CourseTemplate (course list + placing), Reminders. 110 tests;
  `cd CourseKit && swift test` must stay green on Windows.
- SwiftData models: CourseRecord, SettingsRecord, CourseTemplateRecord. The app is now installed on my iPhone
  (sideloaded with iloader, 2026-09-29) and holds my real data: schema changes need a VersionedSchema + migration
  plan (or only additive changes with defaults). Never ship a change that could wipe the store.
- CourseTable/ (app: Views/, Persistence/, Import/, Reminders/), CourseTableWidget/ (Home + Lock Screen widget),
  Shared/ (both targets: palette, SnapshotStore, LocalizedNames, Localizable.xcstrings), CourseTableTests/ (CI only),
  CourseTableUITests/ (UI tests + screenshots; own scheme, run by the manual Screenshots workflow).
- CourseTable.xcodeproj is hand-written in Xcode 16+ synced-folder format: drop new files into the folders,
  never edit project.pbxproj unless adding a target/setting.
- CI: .github/workflows/coursekit.yml (Linux), ios.yml (macos-26, unit tests, path-filtered) and screenshots.yml
  (macos-26, UI tests + screenshots, manual only). Free plan: 2,000 minutes/month, macOS counts 10× → ~200 macOS
  minutes. An iOS run ≈ 4 macOS min (40 billed), a Screenshots run ≈ 8–12. Keep filters; no matrix builds.
- IPA for sideloading: `gh workflow run ipa.yml`, then `gh run download <id> -n CourseTable-ipa`
  (~3 macOS min). Sideloaders rename the App Group to group.<bundle id>.<TEAMID>; SnapshotStore finds it at runtime.
- To see UI changes: `gh workflow run screenshots.yml`, then `gh run download <id> -n screenshots` and look at
  the PNGs. Add a snapshot step to CourseTableUITests for any new screen.
- Real device: installed on my iPhone with iloader (free Apple ID, re-sign every 7 days). iloader renames the app
  to <bundle id>.<TEAMID> and the App Group to group.<that id>; SnapshotStore resolves it at runtime (0.1.1).
  Settings → About → Widget shows whether the App Group was found.

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
- Course list lines are name, room, teacher (the user's order). Placing creates whole-term courses; the editor
  refines them. I have no school export sample yet, so the course list is the main way to enter a timetable.

## How I like to work
- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage. Verify for real (tests/CI), don't trust "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.

## Leftovers to remind me about at the start
1. Revoke the exposed GitHub token github_pat_11B4KQLU… (GitHub → Settings → Developer settings → Tokens)
2. Delete OneDrive\Desktop\.claude\Desktop\claude-review and Downloads\claude-fix, empty the OneDrive recycle bin

## Phase 5 candidates (confirm scope with me first)
1. More screenshot coverage: dark mode, a small iPhone (SE), widget views (e.g. rendered in a test), the period-
   times editor; then fix what they show (e.g. long English words in grid blocks)
2. Grid options: an "all weeks" toggle outside placing mode (the dimmed view already exists); weekend always/auto
3. Reminders: per-course mute; top up the window in the background (BGAppRefreshTask) so it survives weeks unopened
4. School-specific import, once I have a sample from my school's 教务 system (Export/导出 Excel, or copy the
   timetable page into a .txt file)
5. Multiple terms (archive old term, switch between terms). Best near the end of term (~January), with a Mac
6. Widgets: tap a class to open it in the app (deep link); configurable widget (e.g. show tomorrow)
7. First Mac session checklist: run on device, check notifications and Home/Lock Screen widgets (not coverable
   in CI screenshots), import from Files, anything only visible on hardware

## Done = next milestone
- Agreed features implemented with CourseKit tests; swift test green locally
- iOS CI green (send me the run link); README/HANDOFF.md updated for the phase after
```
