# Session Handoff — CourseTable (v0.3: class Live Activity done; to-dos + focus timer built, 0.3.1 IPA, device test next)

Date: 2026-10-09 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (private)

## Status

- **0.3.0 (build 4) runs on my iPhone** (iloader, free Apple ID): the class Live Activity. Device test 2026-10-09: no
  bugs; the notification and the Live Activity alerted at the same time, so I asked for no duplicates.
- **To-dos, focus timer and class alerts** (spec: [`docs/todos-focus-spec.md`](docs/todos-focus-spec.md), decided
  2026-10-09) are **built and screenshot-tested; 0.3.1 (build 5) is built for the device test.** Commits `0574940`
  (CourseKit logic), `6312246` (app), `7dd33cb` (sounds), `491920b`, `2265f4f` (fixes after screenshots, 0.3.1).
  Green: CourseKit 140 (Windows + Linux 37967845938), iOS 37968070324 (37 app tests), Screenshots 37968936694
  (class Live Activity with to-dos, Today with to-dos, the editor, Focus setup/running/island/Lock Screen; en + zh).
  The fixes in `2265f4f` (focus island/Lock Screen layout, running buttons, Start pinned, "日期") were compiled by
  the IPA run 37971663701 only, no new screenshots (minutes). IPA: `C:\Users\AI\Downloads\CourseTable\CourseTable-0.3.1.ipa` (13 MB).
- Sounds: nine CC0 recordings from Freesound (`content/sounds/SOURCES.md`), made into seamless loops by the manual
  Linux workflow `Sounds` (run 37965538219) and committed in `CourseTable/Sounds/` (8.9 MB).
- Tests: CourseKit 140, VocabKit 77; backend 58 pgTAP + 16 HTTP; app 37; UI 3 timetable + 4 Words + 2 Live Activity
  + 2 planner.
- macOS minutes in October ≈ 170 of ~200. Only the IPA and small fixes until November.

## Handoff prompt for the next session

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] Native iOS app "CourseTable" (timetable + Words tab) — v0.3, starting with a class Live Activity

## Read first
README.md, HANDOFF.md, docs/v2-development-record.md (what v0.2 is and how it was tested), docs/vocabulary-spec.md
(the vocabulary spec; §2 lists v0.3). Repo D:\Projects\CourseTable → github.com/Hilbert0619/CourseTable (private), branch main.

## v0.3 feature 1: class Live Activity (Dynamic Island + Lock Screen), decided with me 2026-10-08
(Built and reviewed: see "State of feature 1" below. Next: the device test.)
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

## State of feature 1 (reviewed and fixed 2026-10-08; device test of 0.3.0 (4) pending)
Files:
- CourseKit/Sources/CourseKit/ClassActivities.swift: ClassActivityWindow (session + showMoment = the reminder time, or
  the end of the class before if that's later, so two classes never show at once; signature = what is shown;
  leadMinutes), Timetable.upcomingActivityWindows (maxClassActivities = 4), ExistingClassActivity, ClassActivityPlan
  (end / forget / startNow / schedule: one activity per class signature, so a closed one doesn't come back; scheduled
  ones the app ends itself are forgotten). Tests: ClassActivityTests (14).
- Shared/ClassActivity.swift: ClassActivityAttributes (name, room, teacher, colour, "08:00"/"09:40", showDate,
  signature); ContentState(start, end).content(now:) = staleDate at the start (only if still ahead) + relevanceScore
  = start time, so a later class takes the island from a finished one; EndClassActivityIntent(signature).
- CourseTableWidget/ClassActivityWidget.swift: compact = name + timer in minutes ("72:30"); expanded = Close on the
  left, timer on the right, the class at full width below; Lock Screen card; started = isStale || start <= now.
- CourseTable/Reminders/ClassLiveActivities.swift: sync on every foreground; iOS 26 schedules with
  alertConfiguration = the reminder text; endFinished() runs as a background app refresh (cleanupTaskID; Info.plist
  BGTaskSchedulerPermittedIdentifiers + UIBackgroundModes fetch) requested for the next class end (iOS decides when);
  CreatedActivities (UserDefaults, by signature); startDemo for UI tests (-liveActivityDemo before|during|switch; a
  start error is shown on screen and attached).
- Settings → Reminders: "Live Activity" switch, a note when iOS has them off, footer; on iOS 17/18 a note that it only
  starts while the app is open.
Findings (Simulator screenshots):
- An activity requested with a stale date in the past didn't show at all. Fixed: no stale date once the class started.
- iOS redraws at the stale date late: not within 20 s in the Simulator; others report ~1 min 45 s when the app is
  suspended. So "Starts in 0:00" can show for up to ~2 minutes after the start. The switch test now waits 160 s and
  answers iOS's question first (not run since: run it only if the device test needs more evidence).
- The widget extension draws in the system language, so Simulator shots show English captions even with
  -AppleLanguages zh-Hans. Chinese captions can only be checked on a device set to Chinese.
- The first activity makes iOS ask "Allow Live Activities from Course Table?" on the Lock Screen.
Next, in order:
1. Device test of 0.3.0 (4) on my iPhone (iOS 26): see the checklist in docs or the session report.
2. Fix what it finds (iOS run ~10 min + IPA ~3 min each round).
3. Docs: README (feature), docs/v3-development-record.md (like v2's), HANDOFF.

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
