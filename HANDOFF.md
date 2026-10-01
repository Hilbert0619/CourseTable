# Session Handoff — CourseTable (vocabulary v0.2: stage 3 nearly done)

Date: 2026-10-02 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (private) · last commit `532f492`

## Status

- **v0.1.1 (timetable) runs on my iPhone** (iloader, free Apple ID). Record: [`docs/v1-development-record.md`](docs/v1-development-record.md).
- **Vocabulary phase** (online, Supabase, a Words tab). Agreed spec and plan: [`docs/vocabulary-spec.md`](docs/vocabulary-spec.md).
  - Stage 1 ✅ VocabKit (FSRS-6, plan, session, import, search, stats, sync rules) and the 考研 word list (4,801 words from ECDICT).
  - Stage 2 ✅ Supabase backend, **live**. Project `prkvpkxrugvbmrwxehhs` is in Singapore. It holds the schema, row-level security, the 4,801 words, and the sign-in options (email links into the app).
  - Stage 3 🟡 **Words tab written, compiles, app tests green**: [iOS run 36757968830](https://github.com/Hilbert0619/CourseTable/actions/runs/36757968830). Still to do: the UI tests and screenshot review, an IPA 0.2.0, a real-device test against the cloud project, and the docs.
- Tests, all green:
  - CourseKit 110 and VocabKit 77, on Windows and Linux CI;
  - backend: 58 pgTAP checks and 16 HTTP checks, on Linux CI;
  - app: 17 timetable tests and 12 Words tests, in the Simulator.
  - The 3 old UI tests pass. The **4 new `WordsUITests` have never run yet.**

## Handoff prompt for the next session

> Copy the whole block into a new Claude Code session opened in `D:\Projects\CourseTable`.

```
[PROJECT HANDOFF] Native iOS app "CourseTable" — finish v0.2: the Words tab (English vocabulary, stage 3)

## Read first
README.md, HANDOFF.md, docs/vocabulary-spec.md (the agreed spec; it wins over the old ChatGPT draft).
Repo D:\Projects\CourseTable → github.com/Hilbert0619/CourseTable (private), branch main, clean at 532f492.

## What's where
- CourseKit/ (Swift package, tests on Windows + Linux CI `coursekit.yml`):
  CourseKit = timetable logic (110 tests);
  VocabKit = FSRS-6 Scheduler, StudyClock (day starts 04:00), DailyPlan/ExamCountdown, StudySession, AnswerGrader,
  ChoicePool, WordEntry, ECDICT/ContentFile/SeedSQL, WordListImport, WordSearch, VocabStats, SyncMerge, AuthLink (77 tests);
  VocabContent = `vocab-content` tool (ECDICT → content/kaoyan-ecdict.csv → supabase seed migration).
- content/: kaoyan-ecdict.csv (4,801 words, most frequent first) + ECDICT MIT licence; content/.cache/ is ignored.
- supabase/: config.toml, migrations (schema + generated words), tests (pgTAP + api/smoke.sh), remote-auth.json
  (cloud sign-in options), remote-auth-code-emails.json (6-digit-code templates, need custom SMTP), checks/deployed.sql.
  CI: backend.yml (local stack on Linux, auto). Deploy: backend-deploy.yml (manual) — I start it myself
  (auto mode blocks Claude from starting it); Claude watches the run and checks its log.
- CourseTable/Vocabulary/ (the app's Words tab):
  Backend/ = VocabBackend protocol + DTOs, SupabaseBackend (the ONLY file using supabase-swift, pinned 2.55.3),
             DemoBackend + VocabDemo (in-memory, for tests/previews/UI tests);
  Store/   = VocabRecords (separate SwiftData file Vocabulary.store, VocabSchemaV1 + VocabMigrationPlan — model
             changes need a V2 + stage), VocabModel (holds the ModelContainer: a context doesn't keep it alive —
             that crashed the first test run), SyncEngine;
  Views/   = WordsTab (sign-in/up, new password), WordsHomeView, StudySessionView, WordDetailView (+ search,
             favourites/hard words), BooksView (+ import), VocabStatsView (Swift Charts), VocabSettingsView, WordComponents;
  VocabApp.swift = coordinator (auth state, email links via .onOpenURL, sign-out/delete) + Pronunciation (AVSpeech).
- Tabs: Today 0, Timetable 1, Words 2, Settings 3. Info.plist adds URL scheme io.github.hilbert0619.coursetable.
- Tests: CourseTableTests/VocabTests.swift (Swift Testing, CI only); CourseTableUITests/WordsUITests.swift.
  UI-test launch flags: -uiTesting (in-memory stores + DemoBackend, no network), -vocabDemo (signed-in demo learner
  with two weeks of answers, 30 words, exam date), -vocabMode recognition|choice|spelling|mixed.
- tools/: check_strings.py (must report 0 missing), add_strings.py <json> (adds zh-Hans in Xcode's exact format),
  xcresult_crash.py (reads crash reports from the TestResults artifact on Windows).

## Remaining work, in order
1. Screenshots: `gh workflow run screenshots.yml` → `gh run download <id> -n screenshots`. It now also runs the 4
   new WordsUITests (sign-up notice, a whole session in recognition mode to the summary, mixed question types,
   a tour of every Words screen in en + zh-Hans). They were written without a Mac: expect to fix selectors or
   navigation. Review every screen (layout, Chinese text, dark corners) and fix what looks wrong.
2. Bump MARKETING_VERSION to 0.2.0 and CURRENT_PROJECT_VERSION to 3 (all targets in project.pbxproj),
   `gh workflow run ipa.yml` → `gh run download <id> -n CourseTable-ipa` → C:\Users\AI\Downloads\CourseTable\.
   I install with iloader (same Apple ID keeps my timetable data).
3. Real-device test with me against the cloud project:
   - sign up with the email of my Supabase account (the built-in sender only delivers to the project team's
     addresses, a few per hour);
   - tap the link on the iPhone: the app should open signed in;
   - the book downloads (4,801 words in 5 pages); study a session;
   - check the answers in the Supabase dashboard (Table Editor → review_logs);
   - sign out and back in; the timetable and widget are unchanged.
   Nothing in SupabaseBackend has run against the real project yet (curl smoke tests only) — watch auth links,
   date/numeric decoding, the words(*) embed and upserts.
4. Docs: README (Words feature, layout, tools/), docs/v2-development-record.md (like the v1 record), spec status,
   HANDOFF. Then the stage-3 report with run links.

## Facts and constraints
- Supabase project prkvpkxrugvbmrwxehhs (Singapore); URL + publishable key in Backend/VocabBackend.swift
  (BackendConfig, public by design). GitHub secrets SUPABASE_ACCESS_TOKEN / SUPABASE_DB_PASSWORD and repo variable
  SUPABASE_PROJECT_REF are set. Never put the secret key, DB password or access token in chat or code.
- Sign-in: email + password; sign-up confirmation and password reset use Supabase's standard email LINK (free plan
  can't change templates) → io.github.hilbert0619.coursetable://auth-callback#…&type=signup|recovery (implicit flow).
  6-digit codes later, with a custom SMTP sender (needed anyway before other students join).
- Sync: review_logs are append-only (client UUIDs, server-stamped received_at, pull with a 10-minute overlap);
  cards are replayed from them, so devices can't conflict. Favourites/settings/own books: newest updated_at wins
  (enforced by triggers too).
- CI: iOS runs take ~6 macOS min now (supabase-swift compiles), screenshots probably ~15, IPA ~5. Free plan ≈ 200
  macOS minutes a month (September used ~90). Review code before each macOS run.
- Auto mode blocks Claude from running downloaded third-party binaries (local supabase.exe, cloned packages) and
  from starting backend-deploy.yml; everything else (gh run list/view/download, workflow_dispatch of CI) has worked.

## Environment notes (Windows 11, no Mac)
- Swift 6.4, VS 2022 C++ tools, Windows SDK 26100, Git, gh (signed in as Hilbert0619), VS Code + Swift ext, Docker
  Desktop (not needed so far), Node. If swift/gh aren't found, reload PATH from the registry. In Git Bash, gh is at
  /c/Program Files/GitHub CLI.
- GitHub needs Clash: `git -c http.proxy=http://127.0.0.1:7892 push`, HTTPS_PROXY for gh — per command only. TLS
  hiccups happen: retry.
- Harmless: 'wchar_t' warning in swift build; occasional module-cache "rename … permission denied" → just rebuild.
- Never round-trip UTF-8 files through PowerShell 5.1 Get-Content/Set-Content (mangles Chinese, adds a BOM). The
  Bash tool collapses `\\` inside heredocs: write helper scripts with the Write tool, then `python file.py`.
  `python` on PATH is 3.10 (fine for read-only scripts; don't install into it). Don't change the default Python
  (32-bit 3.12) or global settings.

## Decisions (don't reverse; ask me if unsure)
- Native only: Swift + SwiftUI + SwiftData + WidgetKit; iOS 17+; UI in English + zh-Hans (String Catalogs).
- Logic in Swift packages with tests first; app code stays thin.
- Bundle IDs io.github.hilbert0619.CourseTable(.Widget); App Group group.io.github.hilbert0619.CourseTable (renamed by
  sideloaders, resolved at runtime). Timetable store and vocabulary store are separate files.
- Vocabulary: online + multi-user (my choice), Supabase Cloud Singapore, Words tab inside CourseTable, ECDICT 考研
  list, our own FSRS-6 on the device, sign-in by email link, AI only in v0.4 via an Edge Function.

## How I like to work
- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage. Verify for real (tests/CI/screenshots), don't trust "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.

## Later (v0.3 and after, see the spec)
- Reverse mode; a Words widget (due count, streak); a study reminder in the first free period (shares the 64
  notification slots: class reminders 58 + study 2); example sentences (Tatoeba) and synonyms (Open English
  WordNet) after a licence check; server-side search; weekly/monthly stats; custom SMTP + code emails.
- Watch: the retention slider replays all answers on every tick; DailyPlan is recomputed on each render;
  signing out offline drops unsent answers (the UI warns); own-book entries can't be edited after import.
- Timetable backlog: long English words in grid blocks; dark-mode/iPhone SE screenshots; "All weeks" view;
  per-course reminder mute; school import (needs a 教务 sample); multiple terms; widget deep links; TestFlight ($99/yr).

## Done = v0.2 milestone
- All v0.2 features working on my iPhone against the cloud project; package, backend and app tests green
- UI tests green and screenshots reviewed; IPA 0.2.0 delivered (send me the run links); README/record/HANDOFF updated
```
