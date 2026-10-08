# CourseTable v0.2 — Development and Testing Record (Words)

v0.2 adds a **Words** tab to the timetable app: an online, multi-user English vocabulary trainer for 考研 candidates. It was built on the same Windows PC with no Mac, on top of v0.1 ([v1 record](v1-development-record.md)). The agreed spec is [vocabulary-spec.md](vocabulary-spec.md).

| | |
|---|---|
| Version | **0.2.0 (build 3)**, on the owner's iPhone since 2026-10-08 |
| Period | 2026-09-29 → 2026-10-08 |
| Repository | `D:\Projects\CourseTable` → github.com/Hilbert0619/CourseTable (private) |
| Stack | Swift 6 · SwiftUI · SwiftData · Swift Charts · iOS 17+ · supabase-swift 2.55.3 · Supabase Cloud (Postgres, Auth), Singapore |
| New code | ≈ 8,100 lines: VocabKit logic 2,010 · its tests 1,090 · content tool 75 · app (Words tab) 3,640 · app tests 205 · UI tests 250 · SQL schema 230 · backend tests 280 · tools 340 |
| Tests | 187 package tests (CourseKit 110, VocabKit 77) · 58 pgTAP + 16 HTTP backend checks · 29 app tests · 7 UI tests: all passing |

---

## 1. What v0.2 does

- **Account**: email + password. Creating an account and resetting a password both use the link in Supabase's email; the link opens the app through its URL scheme and signs the learner in. Sign out; delete the account and all its data.
- **Books**: the built-in 考研词汇 list (4,801 ECDICT entries tagged `ky`, most frequent first), downloaded once and kept for offline study; your own lists imported from text or CSV.
- **Today**: new words done out of the target, reviews left, a time estimate from your own pace, the exam countdown.
- **Study session**: learning steps, due reviews, then new words (word page → multiple-choice check, or "I know this"). Review questions: recall and rate, multiple choice, spelling, or mixed. A summary at the end.
- **Scheduling**: FSRS-6 on the device, our own implementation; the day starts at 04:00.
- **Search** (prefix, forms, similar spelling, Chinese meaning), **word pages** with US/UK speech, **Favourites**, **Hard Words**, **Statistics** (Swift Charts), **Settings**.
- **Sync**: an append-only answer log; card state is replayed from it, so devices can't conflict. Everything works offline.

The timetable is unchanged, stays on the device, and needs no account.

## 2. Architecture

```
iPhone — CourseTable app
├─ Timetable (v0.1, its own SwiftData store, local only)
└─ Words tab (CourseTable/Vocabulary)
   ├─ Views/ — SwiftUI, thin
   ├─ VocabApp — sign-in state, email links (.onOpenURL), sign-out / delete
   ├─ Store/ — Vocabulary.store (SwiftData, VocabSchemaV1 + migration plan), VocabModel, SyncEngine
   ├─ Backend/ — VocabBackend protocol: SupabaseBackend (only file using supabase-swift) | DemoBackend (tests)
   └─ VocabKit (Swift package, tested on Windows + Linux): FSRS-6 Scheduler, StudyClock, DailyPlan,
      StudySession, AnswerGrader, ChoicePool, WordEntry, WordListImport, WordSearch, VocabStats, SyncMerge, AuthLink
              │  HTTPS: publishable key + the learner's JWT
              ▼
Supabase Cloud, Singapore (project prkvpkxrugvbmrwxehhs)
├─ Auth: email + password; confirmation and recovery links → io.github.hilbert0619.coursetable://auth-callback
└─ Postgres: words + books (shared, read-only to learners) · review_logs (append-only, server-stamped received_at)
   · favorites · settings · own books — row-level security: every learner sees only their own rows
```

Content pipeline: ECDICT `ecdict.csv` → the `vocab-content` tool → `content/kaoyan-ecdict.csv` (committed) → a generated seed migration.

## 3. Development stages

### Spec (2026-09-29)
The owner brought a requirements draft written with ChatGPT. After a review, the owner chose an online, multi-user product (I had suggested a local-only personal feature). The result is `docs/vocabulary-spec.md`: decisions, scope per version (v0.2 core, v0.3 links with the timetable, v0.4 AI through an Edge Function), data model, study rules, sync, testing and stages.

### Stage 1: VocabKit and the word list (2026-09-30, `28164ab`)
- FSRS-6 written from the published algorithm and checked against the reference implementation's test values. Auto mode blocked running a cloned third-party package, so there is no dependency.
- Study day, daily plan, exam countdown, session order, answer grading, multiple-choice distractors, list import, search, statistics, sync merge rules, auth-link parsing: all with tests first.
- The `vocab-content` tool built `content/kaoyan-ecdict.csv`: 4,801 words, none skipped.

### Stage 2: the backend (2026-09-30, `c4a3b9b` → `b5fcb71`)
- `supabase/`: schema with row-level security, the generated 4,801-word migration, pgTAP tests (schema, content, access rules) and an HTTP smoke test. `backend.yml` runs them on a local Supabase stack on Linux CI.
- Auto mode blocked running the downloaded Supabase CLI on this PC, so the CLI runs only on GitHub runners.
- The owner created the Supabase project (Singapore, free plan) and set the GitHub secrets. Deploys go through the manual `backend-deploy.yml`, which the owner starts.
- Free projects on Supabase's built-in email can't change the email templates, so 6-digit codes were replaced by the standard email link, which opens the app through its URL scheme. The code templates wait for a custom SMTP sender.

### Stage 3: the app (2026-09-30 → 2026-10-02, `121b72f` → `9fae077`)
- The Words tab: sign-in and sign-up, home, study session, word pages, search, books and import, statistics, settings, sync status; 185 new zh-Hans strings.
- App tests against the in-memory `DemoBackend`: first sync, answers up and another device's answers down, newest edit wins, imported lists, sign-out and a second account, email links, error messages, record round trips.
- Four UI tests written without a Mac, run on CI: sign-up notice, a whole session in recall mode, each question type in English and Chinese, and a tour of every Words screen in both languages.
- Two rounds of screenshot review (§5); a third, tour-only run confirmed the last fixes.
- Email links now open the Words tab, so the result of a link is visible even when it launches the app.

### Device test (2026-10-08)
The owner installed 0.2.0 with iloader over 0.1.1 and ran the agreed steps against the cloud project: sign up with the Supabase account's email, open the emailed link on the iPhone, download the book, study a session, check the answers in `review_logs`, sign out and in again, and check that the timetable and widget were unchanged. Reported result: complete, no issues.

## 4. Testing

| Layer | Where it runs | Count | What it covers |
|---|---|---|---|
| VocabKit unit tests | Windows (local) + Linux CI | **77** | FSRS-6 against reference values, retrievability, the 04:00 day boundary, daily plan and estimate, exam countdown, session order and learn-ahead, grading per mode, spelling diff and hint, distractors (same part of speech, never the same meaning), ECDICT parsing and the real 4,801-word file, seed SQL, list import (TXT/CSV, unknown words, duplicates), search ranking, statistics, sync merge, auth links |
| CourseKit unit tests | Windows + Linux CI | **110** | unchanged from v0.1 |
| Database tests (pgTAP) | Linux CI, local Supabase stack | **58** | schema, content counts, row-level security: a learner can't read or change another's rows, logs can't be edited, system books can't be written, account deletion cascades |
| HTTP smoke test | Linux CI | **16** | through the REST API, as the app uses it: sign-up, the book download in pages, answer upload (idempotent) and pull, another learner sees nothing, newest edit wins, account deletion |
| App tests | iOS Simulator (`ios.yml`) | **17 + 12** | timetable as in v0.1; Words: store, model and sync against the demo backend |
| UI tests | iOS Simulator (`screenshots.yml`) | **3 + 4** | timetable as in v0.1; Words: sign-up, a whole session, question types, screenshot tour |
| Visual review | Screenshot artifacts | 2 rounds | layout, Chinese text, empty states, colours |
| Device test | Owner's iPhone, cloud project | – | §3 |

Evidence:

| Milestone | Run |
|---|---|
| Stage 1 (VocabKit + iOS build) | CourseKit 36739044738 · iOS 36739044757 |
| Stage 2 (backend tests) | Backend 36743581767, 36751565864 · deploy 36752241841 |
| Stage 3 app tests green | iOS 36757968830 |
| First Words UI test run (3 of 4 green) | Screenshots 36898662733 |
| All 7 UI tests green after fixes | Screenshots 36903026211 · iOS 36903024922 |
| Last fixes (tour only) | Screenshots 36906594177 · iOS 36906511695 |
| IPA 0.2.0 (3) | IPA 36906513692 |
| Device test | owner's iPhone, 2026-10-08 |

## 5. Problems found and fixed

| # | Problem | How it was found | Fix |
|---|---|---|---|
| 1 | Supabase's free plan can't change email templates, so 6-digit codes were impossible with the built-in sender | deploy run (API 400) | sign-up and reset by email link into the app (`b5fcb71`) |
| 2 | The deploy workflow failed with the two secrets swapped | deploy runs | the workflow now names that mistake and ignores stray whitespace (`6cba5b2`, `e770225`) |
| 3 | **The app crashed in SwiftData at the first test run** | first iOS run | `VocabModel` kept only a `ModelContext`; the container it came from was released. The model now holds its `ModelContainer` (`532f492`) |
| 4 | The UI-test tour couldn't find rows below the fold | first Screenshots run | SwiftUI lists only create the rows on screen: the test scrolls until the link is there (`8322caf`) |
| 5 | The full-session test would have taken a screenshot of every answer | code review before the first run | screenshots only where named (`c25f972`) |
| 6 | The screenshots artifact was rejected: a failure attachment's name contained quotes | first Screenshots run | names are sanitised; `tools/xcresult_screenshots.py` recovers named screenshots from the results bundle on Windows |
| 7 | The session bar showed three icons without their counts | screenshot review | toolbars render labels icon-only; the counts now show with `.titleAndIcon` |
| 8 | Book rows in Books were tinted like links | screenshot review | list-row buttons tint their label; rows use the primary colour |
| 9 | Row separators started mid-row under the sign-in button and the word header | screenshot review | aligned to the row's edge, or hidden on the word page |
| 10 | Quiz options used `,` while word pages used `，` | screenshot review | short meanings use full-width commas everywhere |
| 11 | The demo learner had no hard words, so that screen was never seen with content | screenshot review | the demo forgets two words twice; an app test checks it |
| 12 | **One statistics footer stayed English in Chinese** | screenshot review | in a string with an interpolation, SwiftUI escapes a literal `%` as `%%` in the lookup key, so the catalog key `…90% recall…` never matched. Key fixed; `tools/check_strings.py` now checks this (`9fae077`) |
| 13 | The chart's last date label was cut off | screenshot review | the last tick sits a week before today |
| 14 | Books showed "词书" as both title and section header | screenshot review | the section is "内置词书" |
| 15 | An email link opened the app on whatever tab was showing, hiding the result | code review before the IPA | links switch to the Words tab (`9fae077`) |

## 6. CI and distribution

| Workflow | Trigger | Takes | Purpose |
|---|---|---|---|
| `CourseKit` | push touching CourseKit | ~2 min (Linux) | the 187 package tests |
| `iOS` | push touching the app, widget, shared code, project or package sources | ~6 macOS min (supabase-swift compiles) | build, 29 app tests |
| `Backend` | push touching `supabase/` | ~2 min (Linux) | local Supabase stack, migrations, pgTAP, HTTP smoke test |
| `Backend deploy` | manual (owner) | ~1 min (Linux) | migrations + sign-in settings to the cloud project, then checks |
| `Screenshots` | manual | ~22 macOS min (all), ~16 (one test) | 7 UI tests and the `screenshots` artifact; input `only` runs selected tests |
| `IPA` | manual | ~3 macOS min | `CourseTable.ipa` for sideloading |

macOS minutes count 10× on the free private-repo plan (≈ 200 a month). v0.2 used about 105 of them: 6 iOS runs (≈ 43, one of them the 13-minute crash), 3 Screenshots runs (≈ 59) and one IPA (≈ 3).

## 7. Known limitations and open items

- **Email**: the built-in Supabase sender delivers only to the project team's addresses, a few an hour. Before other students sign up: a custom SMTP sender (then the 6-digit-code templates in `supabase/remote-auth-code-emails.json`).
- **Before any public release**: Sign in with Apple, a privacy policy and PIPL review, an ICP filing for the 中国 App Store, licences checked again.
- Answers upload at launch, when the app comes back, after a session and after changes to books or settings; favourites go with the next sync. The spec's "30 s idle" upload isn't implemented (nothing is lost: unsent answers stay on the device).
- Signing out offline drops answers that haven't been uploaded (the app warns first).
- The retention slider replays all answers on every tick; the daily plan is recomputed on every render. Fine at today's sizes; watch with years of answers.
- Own-book entries can't be edited after import.
- Free signing: re-install every 7 days with iloader.

## 8. Next: v0.3

**First: a class Live Activity in the Dynamic Island** (owner's request, decided 2026-10-08). For each class, it shows from the reminder time to the end of the class: a countdown to the start with the room, then "until 09:40" with a progress bar.
- iOS 26 schedules it ahead (`Activity.request(…, start:)`), so it starts on time without the app running. iOS 17/18 start it when the reminder notification is tapped.
- The usual reminder notifications stay as well.
- Without a server push it can't end itself, so it gets a Close button. The app also clears finished ones when opened, and iOS ends it after 8 hours at the latest.
- Only the next few classes are scheduled (the system limits how many), topped up whenever the app opens.

Then, from the spec: reverse mode (Chinese → English), a Words widget (due count, streak), a study reminder in the first free period of the day (sharing the 64-notification budget with class reminders), example sentences (Tatoeba) and synonyms (Open English WordNet) after a licence check, server-side search of the whole dictionary, weekly and monthly statistics, and the custom SMTP sender.
