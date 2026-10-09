# To-dos, focus timer and class alerts — spec and plan (v0.3)

Decided with the owner on 2026-10-09, after the device test of 0.3.0 (4), which found no bugs but showed that the
class Live Activity and the class reminder notification alert at the same moment. Where this file and older
notes disagree, this file wins. The class Live Activity itself is described in `HANDOFF.md` and, once v0.3 is done,
in `docs/v3-development-record.md`.

## 1. Decisions

| Topic | Decision |
|---|---|
| Class alerts | **No duplicates.** A class gets a Live Activity *or* a notification, never both. Classes with a Live Activity (shown, scheduled, or closed by you) get no notification; the others (beyond the next 4, Live Activities off, iOS 17/18) keep the notification. |
| To-dos | Added and deleted by hand, **for any date** (today by default). Each has a text and, optionally, **a time** ("12:00") or **"after a class"** of that day ("after Calculus"); then a notification comes at that time, or when the class ends. Unfinished to-dos stay on their day (no carry-over). |
| To-dos in the Live Activity | The class's Live Activity lists the unfinished to-dos tied to "after" that class ("After class: pick up package"). |
| Focus timer | A new **Focus** tab. A goal note ("高数复习") and a session length (e.g. 2 h 30 min, breaks included). **Pomodoro**: focus blocks (default 25 min) with short breaks (5 min) and a long break (15 min) after every 4th block, all adjustable; with breaks turned off it is one straight countdown. |
| Focus while running | Shown in the Dynamic Island and on the Lock Screen like the system timer, with Pause/Resume and Stop. A notification at every switch between focus and break, and at the end. |
| Background sounds | **Recorded** sounds, looped seamlessly: rain, rain on a roof, campfire, airplane cabin, ocean, stream, night (crickets), train, café. Only recordings whose licence allows it (CC0 preferred; anything else listed with credit in the app). They keep playing in the background (and during breaks) and mix with other audio; volume and sound can be changed while running. |
| Storage | A new local SwiftData file, `Planner.store` (to-dos, focus history), versioned from the start. No account, no sync, like the timetable. |
| Logic | In `CourseKit`, with tests first (to-do times, the day's order, which to-dos notify, class-alert coverage, the Pomodoro plan and its pause/resume). |

## 2. Scope

**Class alerts**
- The Live Activity sync runs first and reports which classes it covers; the reminder sync then skips them.
- The notification budget (iOS keeps 64 pending per app): classes 40, to-dos 12, focus 10, 2 spare. Each kind only
  replaces its own notifications (today a sync removes them all).
- Settings → Reminders: "Class reminders" now means "a notification for classes without a Live Activity"; the
  footer says so.

**To-dos (Today tab)**
- The Today tab gets a day switcher (previous/next day, a date picker, "Today").
- The day's list in time order: classes, timed to-dos, and to-dos tied to a class right under that class; to-dos
  without a time in an "Anytime" group at the top.
- Add with **+**: text, day, and "When": none / at a time / after a class (the classes of that day).
- Tick off with the circle; swipe to delete; tap to edit. Done to-dos stay, struck through.
- A to-do tied to a class whose meeting no longer exists on that day (timetable edited) keeps its place in
  "Anytime" with a note, and gets no notification.
- Notification: title = the to-do, body = "After Calculus · 09:40" or "12:00". Removed when ticked off or deleted.
- Class Live Activity: up to 3 unfinished "after" to-dos ("and 2 more"); the app updates showing activities when
  to-dos change, and replaces scheduled ones.

**Focus (new tab: Today, Timetable, Focus, Words, Settings)**
- Setup: goal note, length (15 min – 4 h), Pomodoro on/off with focus / short break / long break lengths, the
  sound and volume (with a preview). The plan is shown before starting: "5 focus blocks · 2 h focus ·
  ends 21:30". The last focus block is shortened so the session ends on time; it never ends with a break.
- Running: phase ("Focus 2 of 5" / "Break"), time left in the phase, the session's progress and end time,
  Pause/Resume, Skip break, Stop (asks first). The same in the Live Activity (compact: phase icon + time left).
- At each switch a notification (sound) — also when the app is open.
- History: each session is saved (goal, start, end, minutes focused, finished or stopped). The Focus tab shows
  today's focus time and recent sessions.

**Out of scope for now**: carry-over of unfinished to-dos, repeating to-dos, focus statistics charts, syncing
to-dos or focus history across devices, Apple's own Background Sounds (not available to apps), generated noise.

## 3. Sounds

- Sources: Freesound (CC0 filter), Wikimedia Commons (public domain / CC0) and similar. Each file's page, author
  and licence go in `content/sounds/SOURCES.md` and in Settings → About → Sound credits.
- Processing in a manual Linux workflow with ffmpeg (Linux minutes are cheap; no audio tools on the Windows PC):
  download → cut a clean 60–90 s section → crossfade the end into the start (seamless loop) → loudness to about
  −23 LUFS → AAC (`.m4a`, ~96 kb/s stereo). About 6–8 MB for all.
- Playback: `AVAudioEngine`, each loop decoded into a PCM buffer and scheduled with `.loops` (gapless),
  crossfades when switching, `AVAudioSession` `.playback` + `.mixWithOthers`, Info.plist `UIBackgroundModes`
  `audio` (works with a free Apple ID).

## 4. Data

`Planner.store` (`PlannerSchemaV1`):
- `TodoRecord`: id, title, day (yyyy-MM-dd), timing (`none` / `at` + minutes after midnight / `afterClass` +
  course id), isDone, doneAt, createdAt.
- `FocusSessionRecord`: id, goal, start, end, focusedSeconds, plannedSeconds, finished.
- The running focus session (plan, phase, paused time) is kept in `UserDefaults`, so the app, its Live Activity
  buttons and a relaunch all see the same state.

## 5. Testing

- CourseKit (Windows + Linux CI): to-do times and day order, notifications (budget, done/deleted/past, classes
  removed), "after" to-dos per class, class-alert coverage, the Pomodoro plan (blocks, long breaks, last block,
  no breaks), pause/resume/skip, progress at any moment.
- App tests (Simulator): `Planner.store` round trips, notification payloads, the Live Activity content.
- UI tests + screenshots: Today with to-dos, the add sheet, Focus setup and running, the focus Live Activity.
- Device test: the owner's iPhone (iOS 26).

## 6. Stages

1. This spec; CourseKit logic and tests (to-dos, class alerts, focus plan).
2. The app: `Planner.store`, to-dos on Today, notifications (one sync for classes and to-dos), to-dos in the class
   Live Activity.
3. Focus: the tab, the engine, its Live Activity with buttons, notifications, history.
4. Sounds: find and check the recordings, the processing workflow, playback.
5. CI, screenshots, IPA 0.3.x, device test; then docs (README, v3 record, HANDOFF).

macOS minutes are short this month (about 60 left), so the app code of stages 2–4 is reviewed carefully and built
together; screenshot runs may wait for November.
