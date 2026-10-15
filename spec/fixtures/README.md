# Shared test cases

Both implementations of the app's logic read these files in their tests and must give the same answers:

- iOS: `CourseKit/Tests/CourseKitTests/SharedFixtureTests.swift`, `CourseKit/Tests/VocabKitTests/VocabSharedFixtureTests.swift`,
  `CourseKit/Tests/AIKitTests/AISharedFixtureTests.swift` and `AIAgentFixtureTests.swift` (`swift test` in `CourseKit/`,
  CI `coursekit.yml`)
- Android: `android/core/src/test/kotlin/io/github/hilbert0619/coursekit/SharedFixtureTests.kt`,
  `.../vocabkit/VocabSharedFixtureTests.kt`, `.../aikit/AISharedFixtureTests.kt` and `.../aikit/AIAgentFixtureTests.kt`
  (`./gradlew :core:test` in `android/`, CI `android.yml`)

| File | What it checks |
|---|---|
| `import-fields.json` | The importer's field parsers: weekdays, periods, weeks (valid and invalid texts) |
| `import-samples.json` | Whole imports: delimiters, headers in English/Chinese, comments, problems per line; two samples are files |
| `import-chinese-gb18030.csv`, `import-chinese-utf16le.csv` | The Chinese sample as Excel on Chinese Windows saves it (GB18030), and as UTF-16 with a byte-order mark. GB18030 is decoded on Apple platforms and Android, not by the Swift tests on Linux/Windows |
| `focus-plans.json` | Pomodoro layouts for various settings |
| `timetable-alerts.json` | One timetable and its to-dos: class reminders, live class windows (with "after class" to-dos), the day agenda, to-do alerts |
| `vocab-scheduling.json` | FSRS-6: the published reference values, answer logs replayed into cards (custom parameters, relearning, late-night study days, simultaneous answers), daily plans |
| `vocab-random.json` | The seeded generator (SplitMix64, FNV-1a seeds, Swift's bounded draws and shuffle), so a seed gives the same multiple-choice question and session order on both platforms |
| `vocab-text.json` | Word keys, spelling checks, hints and differences, meaning lines and part-of-speech tags, word-list imports, search |
| `ai-streams.json` | Streamed AI answers in both wire formats (OpenAI-compatible, Anthropic): text, stop reason, token counts, errors inside a stream; any chunk size |
| `ai-errors.json` | Providers' HTTP error responses and the error kind each maps to (wrong key, no credit, rate limit…) |
| `ai-todo-drafts.json` | AI answers to the quick-add prompt read into to-do drafts (course names matched exactly, partly or by abbreviation) |
| `ai-tool-streams.json` | Streamed answers with tool calls in both formats: arguments split across chunks, two calls in one answer, a call without an index, reasoning text, signed and redacted thinking blocks |
| `ai-tool-requests.json` | Request bodies of conversations with tools (JSON Schema), pictures, tool calls and results, reasoning and thinking blocks sent back, a picture no longer kept |
| `ai-tool-arguments.json` | A tool call's arguments checked and normalised (types, lengths, dates, times, ranges, choices), or the error told to the model |
| `ai-agent-tools.json` | The Assistant's app tools on one snapshot (timetable, to-dos, focus, vocabulary): reading tools' texts, proposed changes, errors |
| `ai-timetable-photo.json` | Text found in a timetable photo turned into position lines for a text model; model answers read into CSV and period times |

JSON values use the Swift `Codable` shapes (dates `"2026-09-28"`, times `"08:05"`, weekdays `1` = Monday, upper-case
UUIDs, to-do timings `{"at": {"_0": "12:00"}}`), which the Kotlin port reads and writes the same way; 64-bit seeds and random
values are decimal strings. Each file's `about` describes its summary strings.

**Rule:** when a feature's logic changes, change it in `CourseKit`/`VocabKit` and in `android/core`, and add or update the
cases here so both test suites check the new behaviour. Expected values come from the iOS behaviour (the reference).
The `.csv` files are stored byte for byte (`.gitattributes`).
