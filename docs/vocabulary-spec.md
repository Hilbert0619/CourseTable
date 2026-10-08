# Vocabulary learning — spec and plan (v0.2 → v0.4)

Agreed 2026-09-29. It builds on the ChatGPT requirements draft
(`postgraduate_vocabulary_app_planning.md`) and the review of that draft. Where this file and the draft
disagree, this file wins.

## 1. Decisions

| Topic | Decision |
|---|---|
| Product | Online and multi-user, for 考研 candidates. It needs accounts and cloud sync, but studying must also work offline. |
| Where | A **Words** tab inside CourseTable. The timetable stays local and needs no account; only the Words tab needs sign-in. |
| Backend | **Supabase Cloud, Singapore region**: Postgres, Auth, and later Edge Functions and Storage. Start on the free plan. |
| Sign-in | **Email + password**. Sign-up is confirmed, and passwords are reset, with the link in Supabase's standard email: it opens the app through its URL scheme (`io.github.hilbert0619.coursetable://auth-callback`, which works with a free Apple ID). Free projects on Supabase's built-in email can't change the email templates (checked 2026-09-30), so 6-digit codes wait for a custom SMTP sender (`supabase/remote-auth-code-emails.json` is ready). Sign in with Apple needs the paid developer program, so it waits until the app is published. |
| Word list | **ECDICT** (MIT). Book 1 is every entry tagged `ky` (考研, roughly 5,000 words). Users can also import their own lists. |
| Scheduling | **FSRS-6**. Default parameters, target retention 0.9. It runs **on the device**, in `VocabKit`, and also works offline. Our own code (no third-party package), checked against the reference implementation's published test values. |
| Pronunciation | On-device `AVSpeechSynthesizer` (US or UK voice). There are no audio files in v0.2–v0.3. |
| Day boundary | A study day starts at **04:00** local time, so late-night study counts for the day before. It can be changed in settings. |
| Out of scope | Images, 真题 passages or questions, word roots, etymology, collocations, an admin CMS, subscriptions, a CDN. |
| AI | Only in v0.4, through a server-side Edge Function so no API key ships in the app. The app must never depend on it. |

Kept from the draft:
- The database holds the facts, and the review algorithm decides what to review. AI is only an optional helper.
- Shared content is separate from personal data.
- Statistics come only from real records, and projections are labelled as estimates.
- We respect copyright.

## 2. Scope

**v0.2: core, no AI**
- Sign up, sign in, confirmation link (opens the app), forgotten password (same), sign out, delete account (the App Store requires in-app deletion).
- Books: the system 考研 book, downloaded once and cached for offline use.
- Your own books: import TXT (one word per line; the meaning is filled in from the dictionary, and unknown words are listed) or CSV (the columns in §6). You can rename and delete them.
- **Today** card: new words done out of the target, reviews due, a time estimate, progress, and the exam countdown.
- **Study session**:
  - Each new word is first shown with its word page, then checked once with multiple choice.
  - Reviews use one of four modes: **recognition** (you rate yourself), **multiple choice**, **spelling**, or **mixed**.
  - A summary is shown at the end.
- **Search** in your downloaded books (English prefix, Chinese meaning), and a **word page** with speech.
- **Favourites** and **Hard words**.
- **Settings**: book, new words per day, review mode, exam date, day start, and advanced retention.
- **Statistics**: today, streak, the last 30 days, accuracy trend, book progress, and review-status buckets.
- **Sync**: automatic when online, plus a manual "Sync now" and a status line.

**v0.3**
- Reverse mode (Chinese → English).
- A Words widget showing due reviews and streak.
- A study reminder in the first free period of the day. It uses the timetable and shares the notification budget (§9).
- Example sentences (Tatoeba EN–ZH, CC BY 2.0 FR) and synonyms/antonyms (Open English WordNet, CC BY 4.0), with licences re-checked first.
- Server-side search of the whole dictionary.
- Weekly and monthly statistics.

**v0.4**
- AI helpers through an Edge Function: explain a word in context, mnemonics, synonym comparison, weak-word report.
- Requests are rate-limited, and each answer is labelled as AI-generated.

## 3. Architecture

```
iPhone — CourseTable app
├─ Timetable (unchanged; default SwiftData store; local only)
└─ Words tab
   ├─ SwiftUI views (thin)
   ├─ VocabStore — its own SwiftData file "Vocabulary.store", VersionedSchema from day one
   ├─ SyncEngine — push/pull queue (supabase-swift for Auth + PostgREST)
   └─ VocabKit (Swift package target, pure logic, tested on Windows/Linux)
        FSRS scheduling · daily plan · session builder · answer grading · import parsers ·
        search · statistics · sync merge
                │  HTTPS (publishable key + user JWT)
                ▼
Supabase Cloud (Singapore)
├─ Auth: email + password; confirmation and reset by email link into the app
├─ Postgres: shared content (read-only to users) + user data (row-level security: own rows only)
└─ later: Edge Functions (AI proxy), Storage (media, if ever)
```

- The course store is never touched: vocabulary lives in a separate SwiftData file. Adding it needs no
  migration of the existing store, and a vocabulary bug can't damage your timetable data.
- Signing out, or signing in as someone else, wipes `Vocabulary.store` after unsynced reviews have been pushed
  (or after you confirm discarding them).

## 4. Content pipeline (ECDICT)

- ECDICT's `ecdict.csv` (about 60 MB; not committed; downloaded into the git-ignored `content/.cache/`) → the
  Swift tool `vocab-content` (an executable target in the package) → `content/kaoyan-ecdict.csv`, which is committed
  (a few MB).
- Fields kept, with ECDICT column names: `word`, `phonetic`, `definition` (English), `translation`
  (Chinese), `pos`, `collins`, `oxford`, `tag`, `bnc`, `frq`, `exchange` (word forms, e.g. `p:abandoned/i:abandoning`).
- Selection: `tag` contains `ky`. The tool reports the count and odd entries (empty translation, not a word, duplicates).
- Order: most frequent first (the lower of the BNC and contemporary-corpus ranks), so the words met most often in reading
  come first. The first words are basic (government, system, state…), so the new-word page has an **I know this** button.
- Six entries that ECDICT only has capitalised although they mean the ordinary word (CORE, FAX, Pole, Polish,
  Conservative, Saint) are shown in lower case (`ECDICT.displayOverrides`).
- Result (2026-09-30): 4,801 words, none skipped; 8 proper names keep their capitals (God, TV, Christmas…).
  Rebuild: `cd CourseKit && swift run -c release vocab-content ../content/.cache/ecdict.csv ../content/kaoyan-ecdict.csv`.
- `content/ECDICT-LICENSE` holds the MIT notice. The app's About screen credits ECDICT.
- `ky` is ECDICT's own label, not the official 大纲, so the count will differ from 5,500. The UI calls
  the book "考研词汇 (ECDICT)".
- The seed goes into Postgres as a migration-managed seed file (`supabase db push --include-seed`).

## 5. Data model

### 5.1 Server (Postgres, schema `public`)

Shared content: every signed-in user can read it; only migrations write it.

| Table | Columns (main) |
|---|---|
| `words` | `id bigint` pk · `headword text` unique (lower-case key) · `display text` · `phonetic` · `pos` · `translation_zh` · `definition_en` · `collins smallint` · `oxford bool` · `tags text[]` · `bnc int` · `frq int` · `exchange text` · `source text` |
| `books` | `id uuid` pk · `owner_id uuid null` (null = system book) · `name` · `description` · `source` · `licence` · `word_count` · `updated_at` · `deleted_at` |
| `book_entries` | `id uuid` pk · `book_id` · `position int` · `headword text` · custom fields for imported rows (`phonetic`, `pos`, `meaning`, `example`, `example_translation`) · `updated_at` · `deleted_at` |

User data: row-level security limits every row to `auth.uid()`.

| Table | Columns (main) | Write rule |
|---|---|---|
| `review_logs` | `id uuid` pk (created on the device) · `user_id` · `headword` · `rating smallint` 1–4 · `mode text` · `reviewed_at timestamptz` (device clock) · `study_day int` (fixed when answered; elapsed days are counted in study days) · `duration_ms int` · `device_id uuid` · `received_at timestamptz default now()` | insert + select only (append-only) |
| `favorites` | `user_id` · `headword` · `is_favorite bool` · `updated_at` | upsert; newest `updated_at` wins |
| `user_settings` | `user_id` pk · `book_id` · `daily_new_words` · `review_mode` · `exam_date date` · `day_start_minutes` · `desired_retention` · `updated_at` | upsert; newest wins |
| private `books` / `book_entries` | `owner_id = auth.uid()` | upsert; newest wins; soft delete |

- `delete_my_account()` is a `security definer` function that deletes `auth.users` where `id = auth.uid()`.
  Foreign keys use `on delete cascade`, so all of that user's rows go too.
- Indexes:
  - `review_logs (user_id, received_at)` for the pull cursor;
  - `book_entries (book_id, position)`;
  - `words (headword)`;
  - v0.3: a `pg_trgm` index on `headword` and `translation_zh` for server-side search.

### 5.2 Why there is no `user_word_progress` on the server

The review log is the source of truth. FSRS gives the same result every time for the same log, so each device
works out every word's state (the draft's `user_word_progress`) by replaying the logs. Two devices therefore
can't conflict: they merge logs and replay. If something server-side later needs card states (AI weakness
analysis in v0.4), a table can be filled from the logs without changing the sync design.

### 5.3 Device (`Vocabulary.store`, SwiftData, `VocabSchemaV1`)

- `CachedWord`: the rows of `words` for downloaded books.
- `BookRecord` / `BookEntryRecord`: system books (read-only) and your own.
- `ReviewLogRecord`: `pushedAt` stays nil until it has been uploaded.
- `CardRecord`: derived per headword. It holds `state` (new / learning / review / relearning), `stability`,
  `difficulty`, `due`, `reps`, `lapses`, `lastReview`, `firstLearnedAt`.
- `FavoriteRecord`, `VocabSettingsRecord`, and `SyncState` (pull cursor, last sync time, last error).

### 5.4 VocabKit types (no SwiftData or UIKit in the package)

- `FSRS` (the memory model) and `Scheduler` (learning steps, intervals, replay); `Card`, `Rating`, `ReviewMode`,
  `ReviewEvent`, `Headword` (word keys).
- `StudyClock` (the day boundary), `DailyPlan` + `ExamCountdown`, `StudySession`, `AnswerGrader`, `ChoicePool`.
- `WordEntry` (dictionary entry: meanings, parts of speech, word forms), `ECDICT` + `ContentFile`, `WordListImport`,
  `WordSearch`, `VocabStats`, `SyncMerge`.

## 6. Import formats

- **TXT**: one word per line. Blank lines and `#` comments are skipped. A line may be `word<TAB or space>meaning`.
  In that case the meaning is kept as a custom meaning, and otherwise the dictionary fills it in. Words the
  dictionary doesn't know are imported only if they have a meaning; the rest are listed in the preview.
- **CSV**: header row, English or Chinese names. Columns: `word` (required), `meaning`, `phonetic`, `pos`, `example`,
  `example_translation`. We reuse CourseKit's CSV reader (separator detection, UTF-8/UTF-16/GB18030).
- Before anything is saved, a preview shows words matched to the dictionary, words with a custom meaning, duplicates and lines that couldn't be read.
- The app accepts JSON only as a future backup format; no JSON import in v0.2.

## 7. Study rules

**Daily plan**
- New words per day defaults to 20 (you can set 0–200).
- Reviews: everything due, with an optional cap in advanced settings.
- Exam countdown: the words left in the book, divided by the days left, give the "new words per day needed". It is
  labelled as an estimate and doesn't change the setting.

**Session order**
1. Learning/relearning cards whose step is due.
2. Due reviews, shuffled in a fixed order (repeatable in tests).
3. New words in book order.

Cards that FSRS schedules for later the same day (short-term steps) come back within the session.

**Ratings by mode** (one card per word; the mode only changes how the question is asked)

| Mode | Again | Hard | Good | Easy |
|---|---|---|---|---|
| Recognition | button | button | button | button |
| Multiple choice (4 options) | wrong | — | right | — |
| Spelling | wrong or gave up | right after the first-letter hint | right, no hint | — |
| New-word check (multiple choice) | wrong | — | right | — |
| New-word page: **I know this** | — | — | — | tapped |

- Spelling ignores case and surrounding spaces. There is no fuzzy matching, but the word page shows the difference.
- Multiple-choice distractors: 3 other entries from the same book, with the same part of speech where possible,
  never sharing the correct meaning. They come from a seeded random generator, so tests can repeat them.
- We don't invent timing rules: response time is logged, but it doesn't change the rating in v0.2.

**Status and statistics** (display rules, shown in the app next to each number)
- Needs review: due now. Approaching: due within 3 days. Stable: due later than that.
- Learned = at least one review. Mastered = stability ≥ 30 days.
- Hard words = 2 or more lapses.
- Streak: consecutive study days with at least one review.
- Time estimate: median logged answer time × the items left. It is hidden until 50 reviews have been logged.

## 8. Sync

- **Push**: upload unsent `ReviewLogRecord`s in batches of 500, then mark them as sent. Retrying is safe: the id
  is the primary key, so duplicates are ignored (`on conflict do nothing`).
- **Pull**: `review_logs where received_at > cursor - 10 minutes`. The overlap catches rows that were
  committed late, and duplicates are skipped by id. Then the affected words are replayed.
- **Favourites, settings and your own books**: upsert with `updated_at`, and the newest edit wins.
- **When**: at app start, when the app comes back to the foreground, after a session, after a change to books,
  when leaving study settings, on pull-to-refresh and when "Sync now" is tapped (favourites go with the next sync). Everything works offline. (The planned upload after 30 s
  of no activity wasn't built in v0.2: unsent answers simply wait on the device.)
- **Auth tokens** come from supabase-swift and are stored in the Keychain. The app only contains the project URL
  and the publishable key, which are public by design and protected by row-level security. The
  secret key never goes into the app or the repo.

## 9. Links with the timetable (v0.3)

- Study reminder: one notification in the first free period of at least 30 minutes, or at a fixed time.
  Class reminders drop from 60 to 58 scheduled slots, keeping 2 for study (iOS allows 64).
- Widget: a new "Words" widget with the due count and streak, read from a snapshot in the App Group.

## 10. Testing and CI

| Layer | How | Where |
|---|---|---|
| VocabKit | `swift test` (Swift Testing) | Windows locally, plus `coursekit.yml` on Linux |
| Content tool | a test on a small CSV sample, plus a real run that reports counts | locally |
| Database | Supabase CLI in Docker locally; `supabase test db` (pgTAP) covers row-level security: A can't read B's rows, logs can't be edited, system books can't be written, account deletion cascades | locally, plus a new `backend.yml` on Linux (path-filtered) |
| App | `VocabStore` + `SyncEngine` tests against a fake backend | `ios.yml` (macOS) |
| UI | new steps in `CourseTableUITests` for every Words screen, using demo data and the fake backend | `screenshots.yml` |
| Real device | an IPA; you sign up, study offline, sync, and sign in again | your iPhone |

macOS CI budget for v0.2: about 3 iOS runs (~12 min), 3 screenshot runs (~30 min) and 2 IPAs (~6 min), roughly 50 of the ~200 monthly macOS minutes.

## 11. Stages (report after each)

1. **VocabKit + content** (done 2026-09-30): scheduling (FSRS), day, plan, session, grading, choices, import, search, stats, sync
   merge; the `vocab-content` tool; `content/kaoyan-ecdict.csv`. No cost, no accounts.
2. **Backend** (done 2026-09-30; project `prkvpkxrugvbmrwxehhs`, deployed by `backend-deploy.yml`): `supabase/` folder (config, migrations, seed, pgTAP tests) and `backend.yml`. Everything
   runs in Docker here.
   **Your step at the end**:
   - create a Supabase account and a project in Singapore (free plan);
   - run `supabase login` once in a terminal (browser sign-in), and type the database password yourself when I link the project;
   - open the project URL in Safari on your iPhone with the VPN off, to check it's reachable.
   I then push the migrations and seed.
3. **App** (done 2026-10-08; 0.2.0 on your iPhone, record in `docs/v2-development-record.md`): the Words tab with every v0.2 screen, zh-Hans strings, app tests, screenshots I review, then an IPA
   (version 0.2.0) for your phone. Then README / HANDOFF / the development record get updated.

## 12. Risks and open items

- **Reachability from China**: the cloud project answered on the iPhone without VPN (2026-09-30), and supabase.com
  answered from the PC without VPN (2026-09-29).
- **Free-plan limits**: idle projects are paused (resume in the dashboard); the database is 500 MB, far above our needs.
- **Email**: Supabase's built-in sender is only for testing: it delivers only to the project team's addresses, a few emails an hour,
  and doesn't allow custom templates.
  Before other students sign up, set up a custom SMTP provider in Supabase.
- **Before any public release**:
  - Sign in with Apple (App Store rule 4.8, if other logins are offered), a privacy policy, and a PIPL review;
  - an ICP filing for the 中国 App Store;
  - licences checked again for everything bundled.
- **Device clocks**: `reviewed_at` comes from the device. A badly wrong clock could misorder reviews; that's accepted
  for v0.2.
