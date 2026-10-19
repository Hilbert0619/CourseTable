# CourseTable 0.7 — visual design

Decided with the owner on 2026-10-19, after the 0.6.1 device test. The owner asked what could still be improved
in the app's look; the review found no visual identity of its own, loud and hard-to-read course blocks, a plain
Today list, no sense of "now" on the timetable, a dense Focus setup, a plain Words home and a few rough edges.
The owner chose: **soft pastel course colours by default, Solid as a setting**, and **both phases in one round**.

## 1. Tokens (`CourseTable/Views/Theme.swift`)

| Token | Light | Dark | Where |
|---|---|---|---|
| `Theme.paper` | warm paper `#F4F2EE` (the icon's tile) | black (system) | behind every list (`.paperBackground()`) and the timetable |
| `Theme.card` | system grouped row (white) | system grouped row | cards on the paper |
| `Theme.ink` | graphite `#2B2E33` (the icon's Z) | `#ECECEF` | reserved for hero titles |
| Accent | the asset catalog's AccentColor, unchanged | | measured 4.46:1 on white: kept, not worth shifting the brand colour |
| `Theme.cardRadius` | 26 pt on iOS 26, 10 before | | cards that draw their own background match the system rows |

**Rule:** a card whose content changes (the Focus photo, Today's Now/Next card) draws its own background inside
the row (zero row insets, clear row background, `clipShape` with `Theme.cardRadius`). A List
`.listRowBackground` isn't redrawn when what it shows changes; that was the 0.6.0 wallpaper bug.

## 2. Course colours (`CourseKit/Sources/CourseKit/CourseColors.swift`)

The ten hues keep their order (`colorIndex`), so courses keep their colour. Each has:
- **solid**: the hue darkened in OKLCH until white text reaches 3.5:1. Orange (was 2.59:1), mustard (2.79), green
  (3.14), teal (3.47) and pink (3.50) got darker; the rest were already there. Also each course's stripe and dot
  colour, in the widget and the Live Activities too.
- **soft (light)**: a pale tint (OKLCH L 0.925) with dark same-hue text (L 0.40), at least 7.2:1.
- **soft (dark)**: a deep tint (L 0.32) with light same-hue text (L 0.88), at least 8.8:1.

Blue and indigo's pale tints came out nearly identical (and then indigo and purple), so their soft fills are
nudged apart in hue (blue towards sky blue, indigo slightly towards blue): every pair of soft fills now differs by
at least 0.058 on some channel. The tests check the values against `spec/fixtures/course-colors.json`, every
contrast, and the distinctness (0.05); the fixture is for a later Android port.

## 3. Screens

- **Today**: the date in a light row (no card), then a **Now / Next card** tinted in that course's soft colour: the
  class in progress (time left, progress bar, times, room) or the next one ("in 25 min", "Tomorrow", a weekday),
  and the to-dos still open today. The old "Next class" section is folded into it.
- **Timetable**: soft blocks with a 3 pt stripe in the course colour (solid blocks as before, but readable); the
  class in progress outlined and lifted; a red current-time line with a dot in today's column
  (`PeriodSchedule.gridPosition`: through a period's row in proportion, on the line between periods during a
  break, none outside the day); block text follows Dynamic Type up to 14 / 12 pt.
- **Timetable Appearance** (Settings → Courses): block style Soft / Solid with a live preview; a background photo
  (your own via the system photo picker, no permission needed, or one of the downloaded Focus wallpapers),
  downscaled to 2400 px and kept in Application Support; Fade (the paper colour over it) and Block opacity. The
  photo is listed in Settings → Storage.
- **Focus setup**: a plan card (a ring with each focus block and break to scale, the length, when it ends) with
  preset lengths 25 / 50 / 90 min / 2 hr; the Pomodoro steppers folded into a **Rhythm** row ("25 · 5 · 15"); the
  list fades out under the Start button.
- **Focus running**: a **full-screen** button (the photo edge to edge, the countdown at 92 pt, Pause; the screen
  stays on; it closes by itself when the session ends); a haptic tap at each switch between focus and break.
- **Words home**: today's ring (percentage, a check mark when done), the three numbers as lines, and chips for the
  study streak and the days to the exam.
- **AI Help**: privacy in three lines (straight to your provider; key in the Keychain; nothing changes without
  Confirm), the full text one tap away ("What's Sent").
- **Assistant**: the message bar on Liquid Glass on iOS 26.
- **To-dos**: a bounce and a success haptic when ticked off.

## 4. Testing

`CourseTableUITests/LookUITests.swift` takes every tab in English light and dark, Chinese dark and large text,
the running Focus card and full screen in dark, and the timetable with a photo in both block styles. The
2026-10-19 baseline run (before the changes) is screenshots run 39299710426. UI tests reset the appearance
settings at launch (`@AppStorage` survives between launches) and use the drawn demo picture
(`-timetableBackgroundDemo`), never the network.
