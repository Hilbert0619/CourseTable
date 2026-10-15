# StudyHour (学时) for Android

The Android version of Course Table: Kotlin + Jetpack Compose (Material 3), the same features as the iOS app. Plan,
decisions and stages: [`docs/android-plan.md`](../docs/android-plan.md).

| Module | What it is |
|---|---|
| `core/` | Plain Kotlin (JVM 17, no Android): the port of `CourseKit` (package `io.github.hilbert0619.coursekit`: dates, term, periods, courses, timetable, week grid, CSV import/export, course list, reminders, live class windows, to-dos, focus) and `VocabKit` (package `io.github.hilbert0619.vocabkit`: FSRS-6 and the scheduler, study days, daily plan, study session, answer grading, multiple choice, word entries, ECDICT word lists, word-list import, search, statistics, sync merge, sign-in links) and `AIKit` (package `io.github.hilbert0619.aikit`: providers and connections, request bodies in both wire formats, the SSE parser and stream decoding with tool calls, errors, prompts, to-do drafts, Markdown blocks, the agent loop, the app's tools, timetable photos), with their tests ported one for one, plus the shared cases in `spec/fixtures/`. JSON (kotlinx.serialization) in the same shapes as Swift's `Codable`. |
| `app/` | The Android app (`io.github.hilbert0619.coursetable`, minSdk 26, targetSdk 37): Compose UI, Room, the Glance widget, alerts and the focus timer, the Words tab; JVM tests, Robolectric + Roborazzi screenshot tests. |

## Alerts and focus (`app/…/alerts`, `app/…/focus`)

- **One exact alarm** instead of a notification per class: `Alerts.sync` asks `AlertPlanner` (pure, JVM-tested) what to
  post, update or remove *now* and when the next event is, then sets one `setExactAndAllowWhileIdle` alarm
  (`USE_EXACT_ALARM`). It runs on app start, after every data or settings change, at the alarm, and after a restart,
  an app update or a clock/time zone change (`SystemEventReceiver`).
- **Live class notification** (setting on by default; the iOS Live Activity): an ongoing notification per class from
  the reminder time, counting down to the start, then "Until 09:40", with the after-class to-dos and a Close button; a
  Live Update on Android 16 (`setRequestPromotedOngoing`). One the person closes or swipes away stays gone; one the
  system removed (a restart) comes back silently. **Class reminders** only for classes without it (the setting off).
  **To-do notifications** when a to-do is due, removed when it's ticked off or deleted.
- **Live Updates and the brands' islands** (`LiveUpdates`): Android 16 promotes by one of two rule sets (platform flag
  `ui_rich_ongoing`): not colorized (the Live Update rules) or colorized (the first Android 16 release). The class and
  focus notifications are built both ways and the phone's own `hasPromotableCharacteristics()` picks. The vivo/iQOO,
  OPPO/OnePlus/realme and Xiaomi islands don't run the countdown chronometer, so on those phones the notifications
  also carry the minutes left as short critical text ("25 min"), posted again at each minute by a non-waking exact
  alarm (`IslandCountdown`; `AlertPlan.keepLive` lists the class notifications to refresh). Settings → Reliable
  Reminders → Send a Test Live Update shows how the phone treats one (promoted or not, colorized, build).
- **Focus**: `FocusEngine` keeps the core `FocusRun` in `SharedPreferences`, so the app, the notification buttons and
  an alarm in a new process share one session; an alarm at each switch posts its notification. The sounds are the iOS
  files (`CourseTable/Sounds`, packed as assets by the variant API), played by Media3 ExoPlayer in a loop without audio
  focus (they mix with music); a `mediaPlayback` foreground service runs only while a session plays a sound.
- **Settings → Reliable Reminders** checks notifications, exact alarms, battery optimisation and Live Updates, links to
  vivo/iQOO's auto-start page, and sends a test reminder. Notifications themselves are checked on the phone.

## Words (`core/…/vocabkit/backend`, `core/…/vocabkit/store`, `app/…/words`, `app/…/ui/words`)

- **The same cloud project as iOS** (Supabase, `docs/vocabulary-spec.md`): one account studies on both. The URL and
  the publishable key (public by design; row-level security protects the data) are in `SupabaseBackend.kt`.
- **Logic in `core`, tested on the JVM**: `VocabApp` (account state, email links, sign-out, deleting the account),
  `SyncEngine` (own books → book list → the current book's words → answers up → answers down → favourites →
  settings; answers are append-only and replayed through FSRS, the rest is newest edit wins; a pull cursor with a
  10-minute overlap, pages of 1000, uploads of 500), `VocabModel` (the state the screens read) and `VocabStore` (the
  storage interface). `DemoBackend` + `InMemoryVocabStore` stand in for the server and Room in the tests and the
  screenshots.
- **The app** adds `SupabaseBackend` (supabase-kt: auth + PostgREST over Ktor/OkHttp), `RoomVocabStore` (its own
  database, `vocabulary.db`; times in microseconds as on the server) and the screens. Studying works offline; the app
  syncs when it comes to the front, after a session, on pull-to-refresh and with Sync Now.
- **Email links** (sign-up confirmation, password reset) use the implicit flow and come back to
  `io.github.hilbert0619.coursetable://auth-callback` (an intent filter on `MainActivity`); the session in the link's
  fragment is imported, so the link must be opened on the phone. The session is kept by supabase-kt and restored
  offline at the next start.
- **Speech**: Android's text-to-speech (`Pronunciation`), US and UK English; a phone without an English voice shows
  a message instead.

## Building

- **CI** (`.github/workflows/android.yml`, Ubuntu): `:core:test`, `:app:recordRoborazziDebug` (JVM tests + screenshots)
  and `:app:assembleDebug`. The artifacts are the debug APK (`StudyHour-debug-apk`) and the screenshots.
- **With only a JDK** (no Android SDK): `./gradlew :core:test` builds and tests `core`; `settings.gradle.kts` leaves
  `app` out when no SDK is found.
- **With the Android SDK** (`ANDROID_HOME` or `sdk.dir` in `local.properties`): everything, as on CI.

## Porting notes

- Swift structs become Kotlin data classes; methods that mutate in Swift return a changed copy (`FocusRun.pause`).
- Swift `Date` becomes `java.time.Instant`, `TimeInterval` a `Double` of seconds.
- `ImportedText.decode` reads GB18030 on Android as on Apple platforms.
- Swift values that normalise in their initializer (`WordEntry`, `ReviewEvent`) are Kotlin classes with the same
  normalisation and a hand-written `copy`; `Card(headword:)` is `Card.of(word)`.
- Seeded randomness matches iOS bit for bit: `SeededRandom` (SplitMix64, FNV-1a seeds) plus Swift's own bounded draws
  (Lemire's method) and `shuffled(using:)` order, so a seed gives the same quiz and session on both platforms
  (`spec/fixtures/vocab-random.json`). Swift's character classes (whitespace, newlines, numbers) are in `vocabkit/Text.kt`.
- Not ported: `SeedSQL` and the `vocab-content` tool, which build the server's word list on the PC from Swift.

## Strings

English texts are in `app/src/main/res/values/strings.xml`. The Chinese file `values-zh-rCN/strings.xml` is generated:
`python tools/xcstrings_to_android.py` looks each English text up in the iOS catalog (`Shared/Localizable.xcstrings`);
Android-only texts are translated in `strings-zh.json`. CI fails if a translation is missing.

## Credits

Tab icons: [Material Symbols](https://github.com/google/material-design-icons) (Apache License 2.0). The launcher icon
is the iOS app's icon redrawn as vectors.
