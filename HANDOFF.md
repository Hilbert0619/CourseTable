# Session Handoff — CourseTable (v0.1.1 done → next: English vocabulary learning)

Date: 2026-09-30 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (private)

## Status

- **v0.1.1 runs on my iPhone** (sideloaded with iloader, free Apple ID). The widget works since the App Group fix.
- Full history, test results, bugs found and fixed, and open items: **[`docs/v1-development-record.md`](docs/v1-development-record.md)**.
- Tests: CourseKit 110 (Windows + Linux) · app 17 (Simulator) · UI 3 (Screenshots workflow): all green.
- Next phase: an **English word-learning feature**. I'll provide a requirements draft (Markdown, written with ChatGPT) to refine together before any code is written.

## Handoff prompt for the next phase

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`, then attach the requirements draft.

```
[PROJECT HANDOFF] Native iOS course-table app "CourseTable" — next phase: English vocabulary learning

## Where things stand (v0.1.1, 2026-09-30)
Repo D:\Projects\CourseTable → github.com/Hilbert0619/CourseTable (private).
Read README.md, HANDOFF.md and docs/v1-development-record.md first.
- CourseKit/ = all timetable logic (dates/weeks/periods, Course, Timetable, WeekGrid, ScheduleSnapshot, CSV import/
  export, CourseTemplate course list + placing, Reminders). 110 tests; `cd CourseKit && swift test` must stay green.
- CourseTable/ (app: Views/, Persistence/, Import/, Reminders/), CourseTableWidget/ (Home + Lock Screen widget),
  Shared/ (both targets: palette, SnapshotStore, LocalizedNames, Localizable.xcstrings), CourseTableTests/ (17 app
  tests, CI only), CourseTableUITests/ (3 UI tests + screenshot tour; own scheme).
- SwiftData models: CourseRecord, SettingsRecord, CourseTemplateRecord. The app holds my REAL data on my iPhone:
  model changes need a VersionedSchema + migration plan (or strictly additive changes with defaults). Never ship a
  change that could wipe the store; test the upgrade path.
- CourseTable.xcodeproj is hand-written in Xcode 16+ synced-folder format: drop new files into the folders; edit
  project.pbxproj only to add a target/package/setting (a new local Swift package needs a package reference).
- CI workflows: coursekit.yml (Linux, auto), ios.yml (macos-26, app tests, auto, path-filtered), screenshots.yml
  (manual: UI tests + `screenshots` artifact, ~8 macOS min), ipa.yml (manual: CourseTable.ipa for sideloading,
  ~3 macOS min). Free plan: 2,000 min/month, macOS ×10 → ~200 macOS minutes. Review app code before each run.
- See UI changes: `gh workflow run screenshots.yml` → `gh run download <id> -n screenshots` → look at the PNGs.
  Add a snapshot step to CourseTableUITests for every new screen.
- Ship to my phone: `gh workflow run ipa.yml` → `gh run download <id> -n CourseTable-ipa` → put it in
  C:\Users\AI\Downloads\CourseTable\ → I install it with iloader (same Apple ID keeps my data; re-sign every 7 days).
  iloader renames the app to <bundle id>.<TEAMID> and the App Group to group.<that id>; SnapshotStore resolves it.
  Bump MARKETING_VERSION / CURRENT_PROJECT_VERSION in project.pbxproj for each build I install.

## This phase: English vocabulary learning
1. I attach a requirements draft. First review and improve it with me: scope for a first version, screens,
   data model, word-list source and licence, offline use, pronunciation (AVSpeechSynthesizer vs audio files),
   study scheduling (spaced repetition), links with the timetable (e.g. study reminders in free periods), widget.
2. Agree on the final spec and a phased plan with me before coding.
3. Then build it the same way as v0.1: logic in a Swift package with tests first (new package or a module next to
   CourseKit, testable on Windows), thin SwiftUI views, zh-Hans for every string, app tests + UI tests + screenshot
   review, then an IPA for my phone.

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
- New logic goes into a Swift package with tests first; app code stays thin.
- Bundle IDs io.github.hilbert0619.CourseTable(.Widget); App Group group.io.github.hilbert0619.CourseTable
  (renamed by sideloaders; resolved at runtime).
- Import format = README "Import format"; course list lines are name, room, teacher.
- Reminders: rolling window of 60 local notifications, settings in UserDefaults (per device).

## How I like to work
- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage. Verify for real (tests/CI/screenshots), don't trust "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.

## Leftovers to remind me about at the start
1. Revoke the exposed GitHub token github_pat_11B4KQLU… (GitHub → Settings → Developer settings → Tokens)
2. Delete OneDrive\Desktop\.claude\Desktop\claude-review and Downloads\claude-fix, empty the OneDrive recycle bin
3. Check on the iPhone: class reminders (Settings → Reminders) and the Lock Screen widgets — not yet confirmed

## Backlog for CourseTable itself (after the vocabulary phase, or when I ask)
- Long single English words still break inside grid blocks; more screenshot coverage (dark mode, iPhone SE)
- "All weeks" view outside placing mode; per-course reminder mute; background refresh of reminders
- School-specific import once I have a 教务 export sample; multiple terms (~January)
- Widget deep links; TestFlight via the Apple Developer Program ($99/year) to end the 7-day re-sign

## Done = next milestone
- Agreed features implemented with package tests; swift test green locally
- iOS CI green, screenshots reviewed, IPA delivered (send me the run links); README/HANDOFF/record updated
```
