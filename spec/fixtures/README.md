# Shared test cases

Both implementations of the app's logic read these files in their tests and must give the same answers:

- iOS: `CourseKit/Tests/CourseKitTests/SharedFixtureTests.swift` (`swift test` in `CourseKit/`, CI `coursekit.yml`)
- Android: `android/core/src/test/kotlin/io/github/hilbert0619/coursekit/SharedFixtureTests.kt` (`./gradlew :core:test`
  in `android/`, CI `android.yml`)

| File | What it checks |
|---|---|
| `import-fields.json` | The importer's field parsers: weekdays, periods, weeks (valid and invalid texts) |
| `import-samples.json` | Whole imports: delimiters, headers in English/Chinese, comments, problems per line; two samples are files |
| `import-chinese-gb18030.csv`, `import-chinese-utf16le.csv` | The Chinese sample as Excel on Chinese Windows saves it (GB18030), and as UTF-16 with a byte-order mark. GB18030 is decoded on Apple platforms and Android, not by the Swift tests on Linux/Windows |
| `focus-plans.json` | Pomodoro layouts for various settings |
| `timetable-alerts.json` | One timetable and its to-dos: class reminders, live class windows (with "after class" to-dos), the day agenda, to-do alerts |

JSON values use the Swift `Codable` shapes (dates `"2026-09-28"`, times `"08:05"`, weekdays `1` = Monday, upper-case
UUIDs, to-do timings `{"at": {"_0": "12:00"}}`), which the Kotlin port reads and writes the same way. Each file's
`about` describes its summary strings.

**Rule:** when a feature's logic changes, change it in `CourseKit`/`VocabKit` and in `android/core`, and add or update the
cases here so both test suites check the new behaviour. Expected values come from the iOS behaviour (the reference).
The `.csv` files are stored byte for byte (`.gitattributes`).
