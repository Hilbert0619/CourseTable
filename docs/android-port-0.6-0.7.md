# Android catch-up: iOS 0.6.0 – 0.7.1 → StudyHour 0.6.0

Written 2026-10-22 for the session that ports these iOS updates to Android. Android (StudyHour) matched iOS up to
**iOS 0.5.0** (AI help and the agent: Android 0.5.0, versionCode 6, on `main`). Since then iOS shipped four updates
that Android doesn't have. iOS stays the reference ([`android-plan.md`](android-plan.md) §1: "where this plan doesn't
say otherwise, Android does what iOS does").

| iOS | Date | What | Owner's device test |
|---|---|---|---|
| 0.6.0 (8) | 2026-10-16 | New app icon; Focus wallpapers from Supabase; deleting records and Settings → Storage | photos showed late (fixed in 0.6.1) |
| 0.6.1 (9) | 2026-10-17 | The wallpaper shows as soon as it arrives; the next one downloaded ahead | not reported (went on to 0.7) |
| 0.7.0 (10) | 2026-10-19 | The visual pass: course colours (soft/solid), warm paper, Now/Next card, now-line, Focus plan card, full-screen focus, Words ring, Timetable Appearance | **no issues** (2026-10-21) |
| 0.7.1 (11) | 2026-10-21 | The glass look (default) + built-in wallpapers + Settings → Appearance; class reminders for every class | waiting |

Specs: [`todos-focus-spec.md`](todos-focus-spec.md) §7 (wallpapers, Storage) and §1 (class alerts, changed
2026-10-21), [`design-0.7.md`](design-0.7.md) §1–4 (0.7.0) and §5 (glass).

## 1. What to port, by layer

### `:core` (logic first, with tests and shared fixtures; it builds and tests on this PC)

| iOS (CourseKit) | Shared cases | Android today | To do |
|---|---|---|---|
| `CourseColors.swift`: `RGB` (luminance, contrast, `init(hex:)`, `over(_:alpha:)`), the 10 `CourseColor`s (solid; soft fill/text light; fill/text dark), `CourseBlockStyle` soft/solid | `course-colors.json` (values, minimum contrasts, contrast cases) — made for this port | `app/…/ui/theme/CoursePalette.kt` still has the pre-0.7 mid-tone colours | Port to `coursekit/CourseColors.kt`; test against the fixture; the app's palette reads it |
| `PeriodSchedule.gridPosition(of:)` (the now-line: through a period's row in proportion, on the line between periods in a break, none outside the day) | — (CourseKit `GridPositionTests`) | missing | Port with its tests; consider adding cases to `timetable-alerts.json` or a new fixture |
| `FocusWallpapers.swift`: `FocusWallpaper` rows (lenient decoding, `isUsable`), `FocusWallpaperCatalog` (request URL, decode, 12 h staleness), `FocusWallpaperPicker` (candidates not shown recently, `ready`, `prefetchCandidates`, `remember`), `FocusWallpaperLayout.frame` (crop around `focus_y`), `FocusWallpaperCache.evictions` (40 files / 60 MB) | `focus-wallpapers.json` (`rows`, `usable`, `candidates`, `ahead`, `remember`, `layout`, `evictions`) | missing | Port with the fixture |
| `FolderUsage.swift` (files and bytes in a folder, remove contents) | — | missing | Port (java.io/nio) |
| `Backgrounds.swift`: `SoftWallpaper` (6 wallpapers × light/dark palettes: a base + 4 large ellipses, 3 layouts), `BackgroundChoice` (paper / wallpaper / photo; stored strings; `resolve(stored:glass:hasPhoto:)`) | **none yet**: add `spec/fixtures/backgrounds.json` from the iOS values (palettes, stored strings, resolve cases, readability floors) and a Swift test reading it, so both sides check the same thing | missing | Port with the new fixture |
| `ClassActivities.swift` `ClassActivityPlan` `scheduled`/`seen` (0.7.1) | — | **already ported** (2026-10-21, `android/core` ClassActivities.kt + 2 tests); unused on Android (`canSchedule = false`) | nothing |

`spec/fixtures/README.md` doesn't list `course-colors.json` and `focus-wallpapers.json` yet: add them (and
`backgrounds.json`).

### `:app`

**0.6.0 / 0.6.1**
- **Icon**: iOS `CourseTable/Assets.xcassets/AppIcon.appiconset` has the owner's picture made full-bleed (the tile's
  rim replaced by its surface colour). Android: redraw the adaptive icon from it (foreground + background layers,
  plus a monochrome layer for themed icons); keep StudyHour / 学时.
- **Focus wallpapers**: a random photo (not one shown recently) behind the running countdown, the photographer's
  credit ("Photo by … on Unsplash", opens the page) and a shuffle button; a Wallpaper switch on the Focus setup. The
  list: a plain GET of `public.focus_wallpapers` with the publishable key in `apikey` (no account; RLS lets anon read
  active rows), cached 12 h; pictures from the public bucket `wallpapers`, kept in the cache folder as `<id>.jpg`;
  one downloaded ahead when Focus opens and after each shown wallpaper, **not on a metered network / Data Saver**
  (iOS: Low Data Mode). iOS code: `CourseTable/Focus/WallpaperStore.swift`, `FocusView.swift`
  (`WallpaperBackground`, `WallpaperCredit`, darkened towards the bottom for white text). The backend is live (Backend
  deploy run 39010206400); nothing to change there. Compose redraws on state, so 0.6.0's iOS bug (a List row
  background not redrawn) doesn't apply, but keep the photo inside the card.
- **Deleting records + Settings → Storage**: swipe a focus session to delete it; **All Sessions** grouped by day with
  Clear History; Settings → Storage lists focus history, completed to-dos, Assistant chats, saved AI answers, AI usage
  counts, downloaded wallpapers, the background photo and other cached files with counts/sizes; pick and **Clear
  Selected** (pinned at the bottom), with a confirmation. **Never** the timetable, unfinished to-dos, Words progress or
  AI connections. iOS: `Views/StorageView.swift`, `PlannerModel` and `AIStore` deletes.

**0.7.0 — the visual pass** ([`design-0.7.md`](design-0.7.md) §1–3)
- Tokens: warm paper `#F4F2EE` behind lists in light mode (black in dark), ink `#2B2E33` / `#ECECEF`, cards in the
  system's row colour, the accent unchanged.
- Timetable: soft blocks (tint + same-hue text + 3 dp stripe) by default, Solid as a setting; the class in progress
  outlined and lifted; a red now-line with a dot in today's column; block text that shrinks long English words
  instead of breaking them (`CourseBlock.fitting`).
- Today: the date row without a card, then a **Now / Next card** tinted in the course's soft colour (time left and a
  progress bar, or "in 25 min" / "Tomorrow" / a weekday) with the to-dos still open today.
- Focus setup: a **plan card** (a ring with each block and break to scale, the length, the end time) with preset
  lengths 25 / 50 / 90 min / 2 hr; the Pomodoro steppers folded into a **Rhythm** row ("25 · 5 · 15"); Start pinned
  above the tab bar. Running: a **full-screen** mode (photo edge to edge, 92 sp countdown, the screen kept on, closes
  at the end); a haptic at each focus/break switch.
- Words home: today's **ring** (percentage, a check when done), the three numbers as lines, chips for the streak and
  the days to the exam.
- AI Help: privacy in three lines with "What's Sent" for the full text. To-dos: a bounce + success haptic when ticked.

**0.7.1 — the glass look** ([`design-0.7.md`](design-0.7.md) §5)
- **Settings → Appearance** (replaces Timetable Appearance): Glass (default on iOS) / Classic (the 0.7.0 paper);
  Background: Paper, the six built-in wallpapers, your photo (system photo picker, downscaled to 2400 px, kept in app
  files), or a Focus wallpaper you've seen; Fade for a photo; Soft/Solid blocks and Block opacity. Classic shows the
  background behind the timetable only.
- In glass: Today, Timetable, Focus and Words on the wallpaper; cards, chips and buttons of glass; list rows frosted;
  the timetable's day row and period times on thicker frosting; course blocks tinted glass. Pushed screens, sheets and
  Settings keep the paper.
- The owner's recipe: (1) blur what's behind + raise saturation, (2) a 10 % white layer, (3) a 1-pixel bright edge,
  (4) refraction at the edges, (5) large soft shapes behind. **Android has no Liquid Glass.** Options to weigh (ask
  the owner): a backdrop-blur library such as Haze (`dev.chrisbanes.haze`, Apache-2.0: real blur of what's behind on
  Android 12+, a translucent scrim below) + our white layer + a 1 px gradient edge + an inner glow for (4); or, since
  the wallpaper is ours, draw each card's piece of a pre-blurred, saturated wallpaper bitmap (works on every API
  level, but only blurs the wallpaper, not content scrolling behind). An AGSL `RuntimeShader` (Android 13+) could bend
  the edges for real; optional.
- The built-in wallpapers: draw them as on iOS — each shape an elliptical/radial gradient fading like a blur of 16 %
  of the width (stops 1, 1, .84, .5, .16, 0 at .0, .28, .46, .64, .82, 1; size × 1.56). A real blur came out black
  and sharp on iOS 26; on Android `Modifier.blur` needs API 31 anyway.

**Not to port**
- 0.7.1's class-alert change (every class gets a notification; a scheduled Live Activity starts silently) is about
  iOS starting scheduled activities on its own. Android posts its own live notification on time (exact alarms), so
  its one-alert-per-class design stays ([`todos-focus-spec.md`](todos-focus-spec.md) §1). Android already has its
  test button (Reliable Reminders → Send a Test Live Update).

## 2. Suggested plan

Version **StudyHour 0.6.0, versionCode 8** (7 is the Countdown Island APK 0.5.1 on branch `android-island-overlay`,
which the owner may have installed). One branch from `main` (e.g. `android-0.6`), in steps that each end green on CI
with screenshots (Roborazzi, English + Chinese, light + dark):
1. `:core`: CourseColors, gridPosition, FocusWallpapers, FolderUsage, SoftWallpaper/BackgroundChoice; the fixtures
   (`backgrounds.json` new, README rows); `./gradlew :core:test` here, `swift test` for the new Swift fixture test.
2. The 0.7.0 visual pass (colours, paper, Today card, now-line, Focus plan card + full screen, Words ring, chips).
3. Focus wallpapers (+ prefetch), deleting records, Settings → Storage.
4. Appearance + the glass look + built-in wallpapers / photo; the icon.
5. Strings: new English in `values/strings.xml` (same English as iOS), `python tools/xcstrings_to_android.py`
   (Chinese from `Shared/Localizable.xcstrings` + `android/strings-zh.json`), CI `--check`.
6. APK to `C:\Users\AI\Downloads\CourseTable\`, a checklist for the owner, docs (`android-plan.md` §8, `android/README.md`,
   HANDOFF).

## 3. Questions for the owner at the start

1. The glass look on Android: which approach (a library such as Haze, or our own pre-blurred wallpaper), and glass as
   the default like iOS?
2. One APK with everything (0.6.0), or steps (0.6.0 visual pass + wallpapers + Storage, then 0.7.0 glass)?
3. The Countdown Island (0.5.1, branch `android-island-overlay`, not merged, not yet reported): merge it into `main`
   first, or keep it apart?
4. The new icon on Android: the same full-bleed picture as iOS 0.6?

**Answered by the owner, 2026-10-22:**
1. **Haze** (real blur of what's behind on Android 12+, a frosted tint before) + our white layer, 1 px edge and inner
   glow; **glass is the default**, Classic one switch away, as on iOS.
2. **Two APKs**: **StudyHour 0.6.0 (versionCode 8)** = the 0.7.0 visual pass, Focus wallpapers, deleting records,
   Settings → Storage and the icon; then **StudyHour 0.7.0 (versionCode 9)** = the glass look, the built-in
   wallpapers and Settings → Appearance.
3. **The Countdown Island stays apart** on its branch until the owner has tried it.
4. **The same picture as iOS** for the icon (adaptive, with a monochrome layer).
