# Session Handoff — CourseTable (v0.2 done → v0.3: class Live Activity, first draft pushed, unverified)

Date: 2026-10-08 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (private)

## Status

- **v0.2.0 (build 3) runs on my iPhone** (iloader, free Apple ID) against the cloud project. The device test passed on 2026-10-08 with no issues. Record: [`docs/v2-development-record.md`](docs/v2-development-record.md). v0.1 record: [`docs/v1-development-record.md`](docs/v1-development-record.md).
- Tests, all green:
  - packages: CourseKit 110 (122 with the v0.3 draft) and VocabKit 77, on Windows and Linux CI;
  - backend: 58 pgTAP checks and 16 HTTP checks, on Linux CI;
  - app: 17 timetable tests and 12 Words tests in the Simulator (+1 Live Activity test in the draft);
  - UI: 3 timetable tests and 4 Words tests.
- **v0.3 starts with a class Live Activity in the Dynamic Island** (my request, decided 2026-10-08, see below). After that comes the spec's v0.3 list ([`docs/vocabulary-spec.md`](docs/vocabulary-spec.md) §2).
- **A first draft of the Live Activity is already on `main` (commit `25273e3`), but it is unverified.** Claude wrote it at the end of the v0.2 session before I asked for a clean handover. Its CourseKit logic has 12 new tests (122 CourseKit tests pass), it builds, and CI is green: [iOS run 37802243711](https://github.com/Hilbert0619/CourseTable/actions/runs/37802243711) (app + widget, 30 app tests including the new one) and CourseKit run 37802243444. No screenshots or device test yet. The next session reviews it, finishes it or changes it. To start from scratch instead: `git revert 25273e3`.

## Handoff prompt for the next session

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] Native iOS app "CourseTable" (timetable + Words tab) — v0.3, starting with a class Live Activity

## Read first
README.md, HANDOFF.md, docs/v2-development-record.md (what v0.2 is and how it was tested), docs/vocabulary-spec.md
(the vocabulary spec; §2 lists v0.3). Repo D:\Projects\CourseTable → github.com/Hilbert0619/CourseTable (private), branch main.

## v0.3 feature 1: class Live Activity (Dynamic Island + Lock Screen), decided with me 2026-10-08
(A first draft exists: see "State of the draft" below. Review it before building on it.)
- My iPhone has a Dynamic Island and runs iOS 26. The app supports iOS 17+.
- One Live Activity per class, shown from the reminder lead time (Settings → Reminders, 0–60 min) until the class ends:
  a countdown to the start with course and room, then "until 09:40" with a progress bar. Switch the layout at the start
  with the content's staleDate (the view reads context.isStale); timers use Text(timerInterval:) / ProgressView(timerInterval:).
- iOS 26: schedule them ahead with Activity.request(attributes:content:pushType:nil, style: .standard,
  alertConfiguration:start:). An AlertConfiguration is required, and the system starts the activity even if the app is
  closed. iOS 17/18: no scheduled start; start one when the class reminder notification is tapped (the app comes to
  the foreground) or when the app is opened inside a class's window.
- KEEP the existing reminder notifications too (my choice: both).
- It can't end itself without a server push (needs the paid program + APNs; not now). So: a Close button on it (a
  LiveActivityIntent), the app ends finished ones whenever it comes to the foreground, and iOS ends any after 8 h.
- The system limits how many Live Activities exist at once, and scheduled ones count. Schedule only the next few
  classes and top up on every foreground, like ClassReminders.sync does for notifications.
- A Settings switch and zh-Hans strings. Logic (which classes, windows, ids) goes in CourseKit with tests
  first; ActivityAttributes in Shared/; the views go in the widget extension's WidgetBundle;
  NSSupportsLiveActivities = YES in the app's Info.plist.
- Expected to work with a free Apple ID: no push, no capability needed. Confirm on my iPhone.

## State of the draft (commit 25273e3, not reviewed by me; builds and app tests green on CI, no screenshots or device test yet)
Files:
- CourseKit/Sources/CourseKit/ClassActivities.swift: ClassActivityWindow (session + showMoment; id, signature = what is
  shown, key = signature + show time), Timetable.upcomingActivityWindows(after:leadMinutes:limit:) (maxClassActivities = 4),
  ExistingClassActivity, ClassActivityPlan (what to end / start now / schedule; a key is created at most once, so a
  closed activity doesn't come back). Tests: CourseKit/Tests/CourseKitTests/ClassActivityTests.swift (12, passing).
- Shared/ClassActivity.swift: ClassActivityAttributes (+ ContentState start/end; content.staleDate = start) and
  EndClassActivityIntent (LiveActivityIntent; calls ClassActivityCloser).
- CourseTableWidget/ClassActivityWidget.swift (Lock Screen + Dynamic Island views, Close button), added to the
  WidgetBundle in ScheduleWidget.swift; CourseTableWidget/ClassActivityCloser.swift = a no-op stub (the system runs the
  intent in the app; the stub avoids calling ActivityKit APIs that may be unavailable in extensions).
- CourseTable/Reminders/ClassLiveActivities.swift: LiveActivitySettings (on by default), ClassLiveActivities.sync
  (run from RootView on every foreground with the reminder lead time; iOS 26 schedules with alertConfiguration = the
  reminder text), CreatedKeys (UserDefaults), startDemo for UI tests, the real ClassActivityCloser.
  ClassReminders.body(for:leadMinutes:) is shared with the notification text.
- Settings → Reminders: "Live Activity" toggle (identifier liveActivityToggle), a note when iOS has Live Activities off,
  footer text. Info.plist: NSSupportsLiveActivities. 6 new zh-Hans strings (check_strings: 0 missing).
- Tests: app test liveActivityDescribesTheClass (CourseTableTests/ImportAndReminderTests.swift); UI test
  CourseTableUITests/LiveActivityUITests.swift with launch flag -liveActivityDemo before|during: Settings, then the Home
  Screen island, a long press to expand it, and Notification Center (Lock Screen layout), in en and zh-Hans.
  SpringBoard gestures are best effort, and the screenshots show what happened.
Next, in order:
1. Review the draft's code (CI for 25273e3 is already green: iOS 37802243711, CourseKit 37802243444).
2. Screenshots of only the new test: `gh workflow run screenshots.yml -f only=CourseTableUITests/LiveActivityUITests`
   (~16 macOS min), then review island / expanded / Lock Screen / Settings in en + zh-Hans.
3. Points to check: compact timer widths; whether the layout really switches at the start (isStale at the stale date);
   the system limit vs maxClassActivities = 4; double sound (notification + Live Activity alert at the same moment).
4. Version 0.3.0 (build 4) in all 16 places, IPA, then a device test on my iPhone (iOS 26): Live Activity on; with the
   app closed it appears at the reminder time with an alert; it switches at the start; Close works and the class doesn't
   come back; opening the app after class ends finished ones; the free Apple ID install allows Live Activities.
5. Docs: README (feature), a v0.3 record (like docs/v2-development-record.md), HANDOFF.

## What's where
- CourseKit/ (Swift package; tests on Windows + Linux CI `coursekit.yml`):
  CourseKit = timetable logic (Timetable, WeekGrid, Reminders: Timetable.upcomingReminders, ClassReminder, Moment…);
  VocabKit = vocabulary logic (FSRS-6, plan, session, import, search, stats, sync rules, AuthLink);
  VocabContent = the `vocab-content` tool.
- CourseTable/ (app): Views/ (RootView = TabView: Today, Timetable, Words, Settings; email links switch to Words),
  Reminders/ClassReminders.swift (notifications, synced from RootView on every foreground), Persistence/,
  Vocabulary/ (the Words tab: Backend/, Store/, Views/, VocabApp.swift).
- CourseTableWidget/: the widgets (ScheduleWidget*), read a JSON ScheduleSnapshot from the App Group.
- Shared/: compiled into app + widget (palette, SnapshotStore, Localizable.xcstrings).
- supabase/: the vocabulary backend (migrations, pgTAP + HTTP tests, remote-auth.json, checks/).
- tools/: check_strings.py (must say 0 missing; it knows that a literal % in an interpolated key is %%),
  add_strings.py <json>, xcresult_crash.py, xcresult_screenshots.py (named PNGs from a downloaded UITestResults bundle).
- UI-test launch flags: -uiTesting (in-memory stores, demo backend, no network/notifications/widget writes),
  -demoData (demo timetable), -vocabDemo, -vocabMode recognition|choice|spelling|mixed.

## Facts and constraints
- Supabase project prkvpkxrugvbmrwxehhs (Singapore); URL + publishable key in Backend/VocabBackend.swift (public by
  design). GitHub secrets SUPABASE_ACCESS_TOKEN / SUPABASE_DB_PASSWORD and variable SUPABASE_PROJECT_REF are set.
  Never put the secret key, DB password or access token in chat or code. The built-in email sender only delivers to
  the project team's addresses; a custom SMTP sender is needed before other students sign up.
- CI: iOS runs ~5–9 macOS min, Screenshots ~22 (all) or ~16 with `-f only=<xcodebuild -only-testing value>`, IPA ~3.
  Free plan ≈ 200 macOS minutes a month (v0.2 used ~105, mostly 2026-09-30 → 10-02). Review code before each macOS run;
  docs-only commits don't trigger macOS runs.
- IPA: `gh workflow run ipa.yml` → `gh run download <id> -n CourseTable-ipa` → C:\Users\AI\Downloads\CourseTable\
  (name it CourseTable-<version>.ipa). I install with iloader (same Apple ID keeps my data). Bump MARKETING_VERSION and
  CURRENT_PROJECT_VERSION in all 16 places in project.pbxproj (0.2.0 / 3 now).
- Auto mode blocks Claude from running downloaded third-party binaries and from starting backend-deploy.yml; gh run
  list/view/download and workflow_dispatch of the other workflows work.
- Both SwiftData stores now hold my data: model changes need a new VersionedSchema + migration stage (the timetable
  store isn't versioned yet: its first change must start by describing today's models as V1).

## Environment notes (Windows 11, no Mac)
- Swift 6.4, VS 2022 C++ tools, Windows SDK, Git, gh (signed in as Hilbert0619), Node. If swift/gh aren't found, reload
  PATH from the registry. In Git Bash, gh is at /c/Program Files/GitHub CLI.
- GitHub needs Clash: `git -c http.proxy=http://127.0.0.1:7892 push`, HTTPS_PROXY for gh, per command only. TLS
  hiccups (EOF) happen: retry.
- Never round-trip UTF-8 files through PowerShell 5.1 Get-Content/Set-Content. The Bash tool collapses `\\` in
  heredocs: write helper scripts with the Write tool, then `python file.py`. `python` is 3.10 (no PIL); don't install
  into it or change the default Python (32-bit 3.12) or global settings.
- Apple docs can be read as JSON: https://developer.apple.com/tutorials/data/documentation/<path>.json (via the proxy).

## Decisions (don't reverse; ask me if unsure)
- Native only: Swift + SwiftUI + SwiftData + WidgetKit (+ ActivityKit now); iOS 17+; UI in English + zh-Hans.
- Logic in Swift packages with tests first; app code stays thin.
- Bundle IDs io.github.hilbert0619.CourseTable(.Widget); App Group group.io.github.hilbert0619.CourseTable (renamed by
  sideloaders, resolved at runtime). URL scheme io.github.hilbert0619.coursetable.
- Vocabulary: online + multi-user, Supabase Cloud Singapore, Words tab, ECDICT 考研 list, FSRS-6 on the device,
  sign-in by email link, AI only in v0.4 via an Edge Function.

## How I like to work
- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage, with run links. Verify for real (tests/CI/screenshots), don't trust "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.

## Later (after the Live Activity; see the spec and docs/v2-development-record.md §7–8)
- Spec v0.3: reverse mode; a Words widget (due count, streak); a study reminder in the first free period (shares the
  64 notification slots: class reminders 58 + study 2); example sentences (Tatoeba) and synonyms (Open English
  WordNet) after a licence check; server-side search; weekly/monthly stats; custom SMTP + code emails.
- Watch: the retention slider replays all answers on every tick; DailyPlan is recomputed on each render; signing out
  offline drops unsent answers (the UI warns); own-book entries can't be edited after import.
- Timetable backlog: long English words in grid blocks; dark-mode/iPhone SE screenshots; "All weeks" view;
  per-course reminder mute; school import (needs a 教务 sample); multiple terms; widget deep links; TestFlight ($99/yr).
```
