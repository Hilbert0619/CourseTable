# Session Handoff — CourseTable (Android: StudyHour 0.6.0 built, waiting for the owner's phone test; next 0.7.0, the glass look · iOS 0.7.1 waiting for the owner's device test)

Date: 2026-10-22 · Repo: `D:\Projects\CourseTable` → https://github.com/Hilbert0619/CourseTable (public), branch `main`

## Android: StudyHour 0.6.0 built (2026-10-22); next 0.7.0, the glass look (handoff prompt B)

The owner's choices for the catch-up with iOS 0.6.0–0.7.1 ([`docs/android-port-0.6-0.7.md`](docs/android-port-0.6-0.7.md)
§3): **Haze** for the glass, **glass by default**; **two APKs**; the Countdown Island **stays apart**; the **iOS icon**.

**StudyHour 0.6.0 (versionCode 8)** = everything of iOS 0.6.0–0.7.0: the new icon, Focus wallpapers (one downloaded
ahead), deleting focus sessions and All Sessions, Settings → Storage, the visual pass (warm paper, soft course blocks,
the now-line, the Now / Next card, the Focus plan card and full screen, the Words ring, AI Help's privacy lines) and
Settings → Courses → Timetable Appearance. `:core` got `CourseColors`, `gridPosition`, `FocusWallpapers`,
`FolderUsage`, `SoftWallpaper`/`BackgroundChoice` with the shared fixtures (new: `backgrounds.json`, also read by a
Swift test). CI: branch 39661839070 and 39674220972 (screenshots of every new screen checked), main 39674638097,
CourseKit 39674638104. **APK** `C:\Users\AI\Downloads\CourseTable\StudyHour-0.6.0-debug-48.apk`. Checklist below.
**Next (prompt B): StudyHour 0.7.0 (9)**, the glass look with Haze and Settings → Appearance. The iOS 0.7.1 device
test (below) runs in parallel; its results go to prompt A.

## iOS 0.7.1 (waiting for the owner's device test)

The owner's 0.7.0 device test: **no issues** (2026-10-21). Then two requests, built as **0.7.1 (build 11)** on branch
`ios-0.7.1` (worktree `D:\Projects\CourseTable-ios07`):
1. **The glass look** ([`docs/design-0.7.md`](docs/design-0.7.md) §5): Today, Timetable, Focus and Words in glass
   over a wallpaper, every option button of glass, wallpapers to import or pick, the original look kept. The owner's
   recipe (blur + saturation, a 10 % white layer, a 1-pixel bright edge, refraction at the edges, large soft shapes
   behind) is Liquid Glass on iOS 26 plus our white layer and edge (`Views/Glass.swift`). The owner chose **glass as
   the default**; Settings → Appearance switches to Classic (0.7.0's paper). Six built-in wallpapers of soft shapes
   (CourseKit `SoftWallpaper`, readability tested), your photo, or a Focus wallpaper you've seen
   (`BackgroundChoice`, `Views/Backdrop.swift`, `Views/AppearanceView.swift`, which replaced Timetable Appearance).
2. **Users' report: the class Live Activity doesn't start in the background, and class reminders don't appear.**
   Cause: on iOS 26 the next 4 classes get a Live Activity scheduled ahead and, by the 0.3 rule "never both", no
   notification; when iOS doesn't start a scheduled activity, nothing comes, and the app remembered the class as
   handled, so it never tried again. The owner chose **a notification for every class again** (when Class reminders
   is on); the scheduled activity starts **silently** next to it (`Silence.wav`, one sound). A scheduled activity
   that is gone although nobody saw it is created again next time the app is open (CourseKit `ClassActivityPlan`
   `scheduled`/`seen`, the same in `android/core`; Android's behaviour is unchanged). Settings → Reminders →
   **Test in 1 Minute** schedules a pretend class: in the Simulator iOS started it by itself 60 s later with the app
   in the background (screenshots run 39634923580), so the scheduling call works; whether iOS starts real ones
   hours ahead on the owner's iPhone is what the device test shows. `docs/todos-focus-spec.md` §1 records the change.
- CI: iOS 39634905603, CourseKit 39634913626 (CourseKit 87 + 173 + 51 tests, Android core 18 activity tests
  locally). Screenshots (LookUITests in glass light/dark, Chinese dark, large text, classic light/dark, Focus
  running, Appearance, PlannerUITests): 39638343064 (attempt 2; the first stopped without running a step), then
  testAppearance 39644533138 (thicker frosting on the timetable). Before: 0.7.0's 39309546075. **IPA 0.7.1
  (build 11)**: run 39644539965 (commit ad09f09), saved as `C:\Users\AI\Downloads\CourseTable\CourseTable-0.7.1.ipa`;
  on `main`. Checklist below.

### 0.7.0

**0.7.0 (build 10), 2026-10-19: the visual pass** ([`docs/design-0.7.md`](docs/design-0.7.md)). The owner asked what
could still be improved in the app's look; the review and plan were approved with two choices: **soft pastel course
colours by default, Solid as a setting**, and **both phases in one round**. Built on branch `ios-0.7` (worktree
`D:\Projects\CourseTable-ios07`, since the main checkout is the Android session's):
- CourseKit `CourseColors` (solid ≥ 3.5:1 with white, soft ≥ 7:1 light and dark, distinct tints; shared cases
  `spec/fixtures/course-colors.json`), `PeriodSchedule.gridPosition` (the current-time line); tests on Windows.
- Warm paper background from the icon on every list (`Theme`, `.paperBackground()`); soft timetable blocks with a
  stripe, the class in progress outlined, a red now-line, word-fitting block text; Settings → Courses → Timetable
  Appearance (style, background photo, fade, block opacity; the photo in Storage); Today's Now / Next card; Focus
  plan card with presets, the rhythm folded away, full-screen mode, a haptic at phase switches; Words ring with
  streak and exam chips; AI Help privacy in three lines; the Assistant bar on glass (iOS 26); Storage's Clear
  Selected pinned.
- UI test `LookUITests` (every tab in English light/dark, Chinese dark, large text; Focus running and full screen;
  the timetable photo in both styles). Before: run 39308662887 (commit aa57bfb); after: 39309546075 (8e00f1c). CI:
  iOS 39309539606, CourseKit 39302615616 (CourseKit unchanged since). **IPA 0.7.0 (build 10)**: run 39312168978
  (commit 8f354f9), saved as `C:\Users\AI\Downloads\CourseTable\CourseTable-0.7.0.ipa`; on `main`. Checklist below.
- 0.6.1 (below): the owner went on to the design review without reporting its device test.

### 0.6.1

**0.6.1 (build 9), 2026-10-16.** The wallpapers are live: Backend deploy run 39010206400 (started by Claude with
`gh workflow run` at the owner's request; all steps green; all 30 photo links download as JPEGs without a proxy).
The owner's 0.6.0 test: the photos show, but took ~15 s and often appeared only after reopening the app. Cause,
confirmed in the Simulator (repro run 39011498293, unfixed code: the colour only, 3 s after the photo arrived; fixed: 39011531274): the photo was a List
`listRowBackground`, which isn't redrawn when the photo arrives. The network was fine (from the owner's network
without a proxy: the list 0.7–1.4 s, a photo 1.4–2.3 s, Cloudflare Singapore cache hits). Fixed on branch
`ios-0.6.1`: the card draws its own background, and one photo is downloaded ahead (Focus tab opens, after each shown
wallpaper; not in Low Data Mode) so a session starts with a photo at once (`FocusWallpaperPicker.ready` /
`prefetchCandidates`, shared cases `ahead`). CI: iOS 39011527731, CourseKit 39011534450, screenshots 39011531274. **IPA 0.6.1
(build 9)**: run 39012255211 (commit a2adf11), saved as `C:\Users\AI\Downloads\CourseTable\CourseTable-0.6.1.ipa`. Checklist below.

### What 0.6.0 brought

The owner's iOS 0.5.0 device test (the AI agent) passed with no major bugs (2026-10-16). Then the owner passed on
users' requests (after the Chinese app 不背单词), all built as **iOS 0.6.0 (build 8)**
([`docs/todos-focus-spec.md`](docs/todos-focus-spec.md) §7):
1. **A new app icon** (the owner's picture, made full-bleed: the tile's rim replaced by its surface colour).
2. **Focus wallpapers**: each session shows a random high-resolution photo (not one shown recently) behind the
   countdown, with the photographer's credit and a shuffle button; a Wallpaper switch on the Focus setup. 30
   Unsplash photos (Unsplash License; `content/wallpapers/`) cropped to 1290×2796 in the public Storage bucket
   `wallpapers`, listed in the new table `public.focus_wallpapers` (anyone reads it; no account needed).
3. **Deleting records**: swipe a Focus history session to delete it, **All Sessions** (by day, Clear History), and
   **Settings → Storage** to pick and clear focus history, completed to-dos, Assistant chats, saved AI answers, AI
   usage counts, downloaded wallpapers and other cached files.
- Code: CourseKit `FocusWallpapers.swift` + `FolderUsage.swift` (12 new tests, shared cases
  `spec/fixtures/focus-wallpapers.json`); app `Focus/WallpaperStore.swift`, `FocusView`, `Views/StorageView.swift`,
  `PlannerModel` and `AIStore` deletes; backend migrations `20261016100000/100100`, `config.toml`
  `[storage.buckets.wallpapers]`, pgTAP `04_wallpapers.test.sql`, smoke and deploy checks; `tools/make_wallpapers.py`.
- CI: backend 38939355402 (local stack: bucket seeded, 30 rows, picture served), iOS 38945143245, CourseKit
  38939889921 (Linux), screenshots 38945148761 (PlannerUITests, en + zh). **IPA 0.6.0 (build 8)**: run 38945155263
  (commit 5c3dda8), saved as
  `C:\Users\AI\Downloads\CourseTable\CourseTable-0.6.0.ipa`.
- Deploying the wallpapers: **Backend deploy** (`backend-deploy.yml`, job "Deploy to Supabase Cloud"), not
  **Backend** (`backend.yml`, the local-stack test). Without the table the app shows the plain countdown.

Handoff prompt A below picks up the owner's results.

## Status

- **Android (StudyHour / 学时)**: stages 1–6 of [`docs/android-plan.md`](docs/android-plan.md) §7 are on `main`.
  **The catch-up with iOS 0.6.0–0.7.1** ([`docs/android-port-0.6-0.7.md`](docs/android-port-0.6-0.7.md)): **0.6.0 (8)
  built 2026-10-22** (iOS 0.6.0–0.7.0; APK `StudyHour-0.6.0-debug-48.apk`, checklist below); **next 0.7.0 (9), the glass
  look** (prompt B). `android/core` already has 0.7.1's `ClassActivityPlan` change (unused on Android).
  - Stage 4 (alerts, Focus, Reliable Reminders): APK 0.3.0, **tested on the phone by the owner: no issues**.
  - Stage 5: VocabKit ported to `android/core` with shared cases `spec/fixtures/vocab-*.json`.
  - Stage 6 (the Words tab): APK 0.4.0, **tested on the phone by the owner (2026-10-15): no major issues**.
  - **Users report** (second-hand, 2026-10-15; the owner hasn't seen it on their own phone) that the Atom Island (原子岛)
    on vivo/iQOO "doesn't display correctly". Phone models, OriginOS versions and the exact symptom are not known yet.
  - **0.4.1 (versionCode 5), 2026-10-15: a fix for both likely causes, not yet confirmed on a phone.** APK
    `C:\Users\AI\Downloads\CourseTable\StudyHour-0.4.1-debug-28.apk` (installs over 0.4.0). (1) *Nothing in the
    island*: Android 16 has two promotion rule sets (platform flag `ui_rich_ongoing`: must not be colorized vs must be
    colorized) and our notifications only met the not-colorized one; `LiveUpdates.build` now lets the phone's
    `hasPromotableCharacteristics()` pick. (2) *The app name instead of the countdown*: vivo/OPPO/Xiaomi islands ignore
    the chronometer, so they now get "25 min" text refreshed each minute. Settings → Reliable Reminders → Send a Test
    Live Update reports what the system did. CI: branch 38784060888 (`android-island`), main 38784460423. Details:
    `docs/android-plan.md` §8. Checklist below.
  - **Island result (owner, 2026-10-16): still nothing, on 0.4.1 and 0.5.0** (iQOO Neo 9, China OriginOS 6
    `PD2338_A_16.2.13.2.W10`). vivo's documents (dev.vivo.com.cn docs 894–896) say third-party apps reach the Atom Island
    only through vivo's own 原子通知 API, **granted by vivo per app and scene**, and only for apps already on the vivo App
    Store (email `oosyztz@vivo.com`, 7–10 working days, design review, staged launch). StudyHour (a debug APK, not on the
    store) has no such grant; plain Live Updates reach the island only on global OriginOS builds. **The owner chose
    (2026-10-16) to stop here**: no more island work and no vivo application; the standard Live Update code stays (it
    works on other brands and on global OriginOS). vivo's route would need stage 7, 软著, App 备案 and, for the AI
    features, vivo's deep-synthesis qualification. Full findings: `docs/android-plan.md` §8, the 0.4.1 entry.
  - **0.5.0 (versionCode 6), 2026-10-15: AI help and the agent, moved ahead of stage 7 at the owner's choice.** All of
    iOS 0.4/0.5's AI: AIKit ported to `android/core` (package `aikit`, tested against every shared `ai-*.json` case),
    keys encrypted with an Android Keystore key, Settings → AI Help, word help, "Why?", Long Sentences / Writing
    Feedback, Quick Add, the Assistant with the tools (cards, Undo, photos, earlier chats), Import → From a Photo (AI),
    Analyse My Progress. Text in photos: ML Kit bundled Chinese+Latin (the owner's choice: 64-bit ARM phones only; APK
    31 → 40 MB). CI: branch 38828978533 (`android-ai`), main 38829317137 (APK
    `C:\Users\AI\Downloads\CourseTable\StudyHour-0.5.0-debug-39.apk`); screenshots of every AI screen checked
    (English and Chinese).
    **Not yet tried with a real key, nor the Keystore or ML Kit on a phone**: checklist below. Code map:
    `android/README.md` "AI help and the Assistant".
- **iOS**: 0.3.1 passed the owner's device test (2026-10-11); the owner published GitHub Releases themselves (IPAs
  0.1.0–0.3.1 and the Android 0.4.0 *debug* APK renamed `StudyHour-0.3.1.apk`; every tag points at e6479e7, not the
  build commits; no LICENSE yet). Those release fixes wait for the owner's OK. `docs/v3-development-record.md` is still
  unwritten.
- **AI help** ([`docs/ai-spec.md`](docs/ai-spec.md), bring your own key), built on iOS 2026-10-12 as 0.4.0 (build 6);
  **the owner's device test with a real key passed** (2026-10-15).
- **AI agent** ([`docs/ai-agent-spec.md`](docs/ai-agent-spec.md)), built 2026-10-15 as 0.5.0 (build 7) (IPA run
  38774424881); **the owner's device test passed with no major bugs** (2026-10-16). Android has both since 0.5.0.

## iOS 0.7.1 test (owner)

Install `CourseTable-0.7.1.ipa` over 0.7.0 with iloader (everything stays). Light and dark mode, and once with a
larger text size.
1. **Glass**: Today, Timetable, Focus and Words open on the Dawn wallpaper (or on your 0.7.0 timetable photo, if
   you chose one): cards and buttons of glass (they bend the wallpaper at their edges), list rows frosted. Titles,
   section headers and the date stay easy to read.
2. **Settings → Appearance**: Glass / Classic (Classic is 0.7.0 exactly; the background then shows behind the
   timetable only). Pick each built-in wallpaper; Choose a Photo… (yours) and a Focus wallpaper you've seen; Fade
   for a photo; Soft / Solid blocks and Block opacity. Storage lists the photo as Background Photo.
3. **Today**: the Now / Next card is glass tinted in the class's colour; the day arrows and Today are glass.
4. **Timetable**: glass blocks in each course's colour, the day row and period times frosted, the now-line.
5. **Focus**: the plan card and the 25 / 50 / 90 / 2 hr chips of glass, Start Focusing; while running, Pause, Stop,
   Full Screen.
6. **Words**: the Today card, its chips and Start.
7. **Class reminders and Live Activity** (Settings → Reminders, both on): **Test in 1 Minute**, lock the iPhone
   right away: a minute later the reminder should sound and the Live Activity appear (expanded in the Dynamic
   Island) without opening the app. Then the real thing: leave the app closed overnight and see that tomorrow's first
   classes bring their reminder (one sound) and their Live Activity. If a Live Activity doesn't come but the
   reminder does, tap the reminder: the activity starts.
8. Anything off: a screenshot, the time, and whether the app had been opened since the evening before.

## Android 0.6.0 test (owner)

Install `C:\Users\AI\Downloads\CourseTable\StudyHour-0.6.0-debug-48.apk` over 0.5.x (everything stays; over the
Countdown Island APK 0.5.1 the island goes away, as planned). Light and dark mode.
1. **Icon**: the new picture on the Home Screen (and in Recents). With themed icons on, a one-colour calendar, clock
   and Z.
2. **Look**: warm paper behind every screen in light mode (black in dark), white cards.
3. **Today**: during a class, the Now card in that course's colour with the time left and a progress bar; between
   classes "Next · in 25 min"; in the evening "Next · Tomorrow"; "3 to-dos left today". Tick a to-do: it bounces and
   the phone gives a short vibration.
4. **Timetable**: soft blocks with a coloured stripe; the class in progress outlined; a red line with a dot at the
   current time in today's column; long English names shrink instead of breaking. Settings → Courses → **Timetable
   Appearance**: Solid; **Choose a Photo…** (the system picker, no permission asked) → the photo behind the
   timetable; Fade and Block opacity; Remove Photo.
5. **Focus setup**: the plan card (ring, length, end time) and 25 min / 50 min / 90 min / 2 hr; Rhythm opens the
   steppers; Wallpaper switch on. **Start**: within a few seconds a photo behind the countdown (the first time needs the
   internet), "Photo by … on Unsplash" opens the photo's page, the shuffle button shows another. **Full Screen**: the
   photo edge to edge, status and navigation bars hidden, the screen stays on; the arrows go back. A short vibration
   at each switch between focus and break while the app is open. Close StudyHour from Recents and open it again: the
   same photo. The next session's photo should appear at once (downloaded ahead).
6. **History**: swipe a session left to delete it; **All Sessions** by day, Clear History (asks first).
7. Timetable Appearance now shows the Focus wallpapers you've seen: pick one for the timetable.
8. **Settings → Storage**: counts and sizes; pick some, **Clear Selected**, confirm: they go; the timetable,
   unfinished to-dos, Words and AI connections stay.
9. **Words**: the ring with today's percentage, the numbers as lines, the streak and exam chips. **Settings → AI
   Help**: privacy in three lines, What's Sent opens the full text.
10. Anything off: a screenshot and what you did.

## Android 0.5.0 AI test (owner, with a real key)

Install `C:\Users\AI\Downloads\CourseTable\StudyHour-0.5.0-debug-39.apk` over 0.4.x (timetable, to-dos, Focus and Words stay). Keys don't come over from the iPhone: add the
connection on the phone itself (the same key works on both). Use a model that calls tools: DeepSeek
(`deepseek-v4-flash`), GLM (`glm-4.7-flash`, free) or Qwen.
1. 设置 → AI 帮助 → 添加连接: provider, key, model → **测试连接**: connected, tools work, and with a Photo Model (GLM
   `glm-4.6v-flash`, Qwen `qwen3-vl-plus`) photos work. Close StudyHour from Recents and open it again: the connection
   is still there and the key field's label says a key is saved (the Keystore works).
2. Words: a word page → the AI buttons stream an answer; open the word again: the answer is saved (no new charge). In a
   study session, a wrong choice or spelling → **为什么？**.
3. Words → AI 工具 → **长难句**: **相机** or **照片** reads the text on the phone (ML Kit, works offline) → analyse.
   **作文批改**: a paragraph → feedback.
4. A new to-do → **快速添加**: "明天高数课后交论文" → the fields fill in.
5. Today → ✨ (助手): the iOS checklist's steps 2–4 above (New To-Do card → Confirm → Undo; Change To-Do; a delete you
   Cancel; a lookup with no card; Start Focus → the Focus tab and its notification run it).
6. Words → 统计 → **分析我的进度**: lookup lines, then an analysis whose numbers match the statistics.
7. 课程表 → Import → **从照片导入（AI）**: a 教务系统 photo or screenshot → courses to check (period times too, if the
   photo shows them) → Import. Once with DeepSeek (text read on the phone), once with a photo model.
8. 助手 → photo → "把这些课加进课程表" → an Add Courses card → Review… → Import → the card counts them; Undo removes them.
9. **以前的对话**: reopen a chat, the cards keep their state. Airplane mode: a clear error and Try Again.
10. Anything odd: provider and model, what you asked, a screenshot of the card or answer. Never send the key itself.

## Android Atom Island (vivo's: closed 2026-10-16) and the Countdown Island (0.5.1, branch)

Tested by the owner on 0.4.1 and 0.5.0: nothing in the China OriginOS island, because vivo grants it per app
(see Status). The owner chose to stop; don't reopen it unless the owner asks or vivo's rules change.

Options weighed with the owner (2026-10-16): **A** keep the standard Live Update and re-test after OriginOS updates
(on `main`, no code); B apply to vivo (store listing first, not planned); C a floating countdown pill below the status
bar (悬浮窗, not planned); **D** StudyHour's own island drawn over the camera hole through an accessibility service:
**built as 0.5.1 (versionCode 7) on branch `android-island-overlay`** (2026-10-16; CI 38954791068, screenshots of the
pill, the card, the settings section and the dialog checked). APK
`C:\Users\AI\Downloads\CourseTable\StudyHour-0.5.1-debug-44.apk` (installs over 0.5.0). **Not merged into `main`
until the owner has tried it** on the iQOO Neo 9: then merge `main` into the branch (it has only docs on `main` since),
run CI, fast-forward `main`. Design: `android/README.md` ("The Countdown Island"); progress: `docs/android-plan.md`
§8 on the branch.

**Countdown Island test (owner, 0.5.1 branch APK):**
1. Settings → Reminders → Reliable Reminders → at the bottom **倒计时岛** (未开启) → read the dialog → **打开无障碍设置**
   → find 学时 / 倒计时岛 (maybe under 已下载的应用 or 已安装的服务) → turn it on. If Android says 受限制的设置: App info
   → ⋮ → 允许受限制的设置, then again. Back in StudyHour the row says 已开启.
2. **试一试：2 分钟倒计时** → go to the Home Screen: a black pill around the camera, icon on the left, "2 分钟" on the
   right, the camera hole in the middle of it. **Screenshot it** (does the hole sit centered, top and bottom?).
3. Tap the pill: a card with 测试实时更新, the time counting down to the second, and 关闭. Tap outside: it closes.
   Tap the card's text: StudyHour opens (the island hides while it's open). Tap 关闭 in the card: the test ends and
   the island goes away.
4. Focus: start a session → Home Screen: "25 分钟", a minute at a time. In the card, 暂停 → the pill says 已暂停;
   继续; 停止 ends it. Swipe the pill up: it hides, and comes back when you pause or resume.
5. A class with the live class notification: from the reminder time the pill shows the minutes to the start, then
   to the end. Turn the phone sideways in a video: no island.
6. Over a day: does the island still work after the screen was off for a while, and does 倒计时岛 stay 已开启 (some
   phones turn accessibility services off)? Does it clash with vivo's own island (music, charging)?
7. Report: the screenshots, anything odd; whether to merge it into `main`.

**Re-test after each OriginOS update** (e.g. OriginOS 7 on the iQOO Neo 9), no new APK needed: Settings → Reminders →
Reliable Reminders → **发送测试实时更新** → go to the Home Screen and look at the island; come back and screenshot the
result line under the button. If the island shows "2 分钟", the class and Focus countdowns work there too: tell
Claude the OriginOS build (设置 → 系统升级) so the docs can record it.

## Handoff prompts

> Copy one whole block into a new Claude Code session opened in `D:\Projects\CourseTable`. **B** is next: Android
> 0.7.0, the glass look (and fixes from the 0.6.0 test). **A** follows the owner's iOS 0.7.1 device test (independent;
> in its own worktree).

### A. The owner's results for iOS 0.7.1 (glass, class reminders)

```
[PROJECT HANDOFF] CourseTable — the owner's device test of iOS 0.7.1 (the glass look; class reminders that always come): fix what I report

## Read first
HANDOFF.md (status, the 0.7.1 checklist, facts, gotchas), docs/design-0.7.md (§5 the glass look),
docs/todos-focus-spec.md §1 (class alerts, changed 2026-10-21) and §7, README, spec/fixtures/README.md.

## Goal
1. I tell you how the 0.7.1 checklist went (light, dark, large text; Test in 1 Minute; real classes overnight) with
   screenshots. Fix what I report: logic in CourseKit with tests first (CourseColors and SoftWallpaper readability
   stay tested; ClassActivityPlan also in android/core), then the app; compare LookUITests screenshots before and
   after every UI change.
2. Bump to 0.7.2 (build 12) if anything changed, build the IPA, save it to C:\Users\AI\Downloads\CourseTable\, give
   me a short checklist for what changed.

## Decided with me — don't reverse; ask if unsure
The glass look by default (Liquid Glass on iOS 26 + a 10 % white layer + a 1-pixel bright edge, frosted list
rows), Classic (0.7.0's warm paper) one switch away; built-in soft-shape wallpapers, your photo or a Focus
wallpaper. Every class gets its reminder notification; a scheduled Live Activity starts silently next to it.
Soft pastel course blocks by default, Solid as a setting; the accent colour unchanged. Wallpapers come from Supabase
(table focus_wallpapers + public bucket wallpapers). Storage never clears the timetable, unfinished to-dos, Words
progress or AI connections. Everything in docs/ai-spec.md §1 and
docs/ai-agent-spec.md §2 still holds. Swift + SwiftUI only; logic in CourseKit with tests that run on Windows
(`swift test` in CourseKit/); the app compiles on GitHub Actions only. Never put a real API key, token or password in
code, tests, CI or chat.

## How to work
The main checkout D:\Projects\CourseTable may be on the Android session's branch: work in a git worktree from main.
Iterate on a branch: push, `gh workflow run ios.yml --ref <branch>`, then screenshots.yml with
only=CourseTableUITests/LookUITests (plus the test classes of what you touched; the whole suite no longer fits the
job's 40 minutes); download the screenshots and look at them; when green, fast-forward main. Backend changes:
backend.yml on the branch first, then backend-deploy.yml (gh workflow run, when I say so). Use the Write/Edit tools
(or Python scripts written with Write) for code, never Python with backslashes through a bash heredoc. Short report
at the end with run links.
```

### B. Android: the glass look (StudyHour 0.7.0), and the owner's 0.6.0 results

```
[PROJECT HANDOFF] CourseTable — Android: the glass look of iOS 0.7.1 as StudyHour 0.7.0 (Haze, glass by default, Settings → Appearance); fix what I report from 0.6.0

## Read first
docs/android-port-0.6-0.7.md (§1 "0.7.1 — the glass look", §3 my answers, §4 progress), HANDOFF.md (status, the
0.6.0 checklist, facts, gotchas), docs/design-0.7.md §5 (the glass look), docs/android-plan.md (§1 decisions, §8
progress), android/README.md ("The look, Focus wallpapers and Storage"), spec/fixtures/README.md. The iOS reference:
CourseTable/Views/Glass.swift, Backdrop.swift, AppearanceView.swift, and the glass parts of TodayView, WeekView,
FocusView, WordsHomeView.

## Goal
1. If I report results of the 0.6.0 checklist, fix those first (logic in android/core with tests; screenshots before
   and after).
2. StudyHour 0.7.0 (versionCode 9): Settings → Appearance replacing Timetable Appearance (Glass / Classic; Background:
   Paper, the six built-in wallpapers, your photo, a seen Focus wallpaper; Fade for a photo; Soft / Solid and Block
   opacity), the built-in wallpapers drawn as on iOS (elliptical gradients; `SoftWallpaper` is already in :core with
   backgrounds.json), and the glass look on Today, Timetable, Focus and Words: Haze (dev.chrisbanes.haze; look up the
   current version) blurring what's behind on Android 12+, a frosted tint before, plus a 10 % white layer, a 1 px
   bright edge and a soft glow inside the rim; frosted list rows; glass chips and buttons; the timetable's day row
   and period times on thicker frosting; glass course blocks (soft 75 %, solid 95 % of Block opacity). Pushed screens,
   dialogs and Settings keep the paper. Screenshots (English + Chinese, light + dark, classic too) checked at each step.
3. APK to C:\Users\AI\Downloads\CourseTable\, a short checklist for my phone, docs (android-plan.md §8,
   android-port-0.6-0.7.md §4, android/README.md, HANDOFF).

## Decided with me — don't reverse; ask if unsure
Haze, glass as the default, Classic (0.6.0's paper) one switch away. Two APKs: 0.6.0 is out; this is 0.7.0. The
Countdown Island (0.5.1, branch android-island-overlay) stays apart until I report on it. Everything in
docs/android-plan.md §1 (native Kotlin + Compose, core port, the same Supabase project and schema unchanged,
StudyHour/学时 on Android only). iOS is the reference and stays unchanged. A 0.6.0 timetable photo and settings carry
over (BackgroundChoice.resolve). iOS 0.7.1's class-alert change is not ported. Storage never clears the timetable,
unfinished to-dos, Words progress or AI connections. Keep the repo secret ANDROID_DEBUG_KEYSTORE; don't create or
change secrets without asking. No Android Studio/SDK on this PC: the app builds on CI only. Never put a real API key,
token or password in code, tests, CI or chat. Still open on the Android track, when I report: the Countdown Island,
the 0.5.0 AI test, then stage 7 (a signed release APK: ask me first about the release keystore, version and notes).

## How to work
Work on a branch from main (e.g. android-0.7); main is checked out in D:\Projects\CourseTable (the iOS worktree
D:\Projects\CourseTable-ios07 is the iOS session's). Logic in android/core with tests first (./gradlew :core:test,
with the proxy flags in HANDOFF's gotchas). Iterate the app on the branch: push, `gh workflow run android.yml --ref
<branch>`, download the screenshots and look at them, fix, repeat; when green, fast-forward main and push (main builds
the APK). Strings: same English as iOS, `python tools/xcstrings_to_android.py` for Chinese. Use the Write/Edit tools
for code (or Python scripts written with Write), never Python with backslashes through a bash heredoc. Short report
at the end of each step with run links.
```

## Facts and constraints

- Versions (checked 2026-10-09/11): Kotlin 2.4.21, AGP 9.4.1 (built-in Kotlin: no kotlin-android plugin), Gradle 9.8.1,
  compileSdk/targetSdk 37 (Android 17 stable), minSdk 26, Compose BOM 2026.09.00 (material3 1.4.0, ui 1.12.1), KSP
  2.3.12, Room 2.8.5, Lifecycle 2.11.0, Glance 1.2.0, core-ktx 1.19.1, Media3 1.11.1, kotlinx-serialization 1.11.0,
  kotlinx-coroutines 1.11, supabase-kt 3.8.0 (BOM; auth-kt + postgrest-kt), Ktor 3.5.1 (OkHttp engine), Robolectric
  4.17, Roborazzi 1.76.0, Espresso pinned 3.7.0. GitHub Actions: checkout v7, setup-java v6 (Temurin 21),
  gradle/actions v6, upload-artifact v7. `ubuntu-latest` moves to Ubuntu 26 from 2026-10-19.
- Layout: `android/core` — packages `io.github.hilbert0619.coursekit` and `…vocabkit` (with `vocabkit.backend`:
  `VocabBackend` interface, row types, `DemoBackend`/`VocabDemo`; `vocabkit.store`: `VocabApp`, `SyncEngine`,
  `VocabModel`, `VocabStore`/`InMemoryVocabStore`), 434 tests incl. shared fixtures (and `aikit`; since 0.6
  `CourseColors`, `FocusWallpapers`, `FolderUsage`, `Backgrounds`). `android/app` packages `data`
  (Room `AppDatabase` v1), `alerts`, `focus`, `words` (`SupabaseBackend` with the URL + publishable key, Room
  `VocabDatabase` v1 → `vocabulary.db`, `RoomVocabStore`, `Pronunciation`), `ui` (… `words`: `WordsTab` +
  `WordsController`/`LocalWordsController`, sign-in, study session, word/search/list screens, books + import,
  settings, statistics), `widget`, `appearance` (Timetable Appearance settings, the background photo),
  `focus/WallpaperStore.kt` (Focus wallpapers). Room schema JSON for both databases in `app/schemas/`: bump the
  version and add a migration for any change.
- App tests (`app/src/test`): `AlertPlannerTest` (plain JVM), `FocusEngineTest`, `RepositoryTest`, `VocabRoomTest`
  (Robolectric), `ScreenshotTests` (34 screens, incl. a drawn `DemoWallpaper` and the launcher icon),
  `WordsScreenshotTests` (13 Words screens with `DemoBackend` + `InMemoryVocabStore` at a fixed clock).
- Strings: English in `app/src/main/res/values/strings.xml` (same English as iOS where possible);
  `python tools/xcstrings_to_android.py` generates `values-zh-rCN` (689 strings) from `Shared/Localizable.xcstrings` +
  `android/strings-zh.json`; CI runs it with `--check`.
- Shared test cases: `spec/fixtures/` (README there). A logic change goes into CourseKit/VocabKit *and*
  `android/core`, with the fixtures updated.
- CI `android.yml`: on push to main (paths android/**, spec/**, content/**, CourseTable/Sounds/**, the strings tool)
  and `workflow_dispatch`; runs the strings check, `:core:test :app:recordRoborazziDebug :app:assembleDebug`; artifacts
  StudyHour-debug-apk, StudyHour-screenshots, StudyHour-room-schema, StudyHour-test-reports (on failure). ~4–8 min.
- Supabase: project prkvpkxrugvbmrwxehhs; Site URL = `io.github.hilbert0619.coursetable://auth-callback` (both apps
  use it); built-in email (team addresses only, a few an hour); the 6-digit-code templates in
  `supabase/remote-auth-code-emails.json` wait for a custom SMTP sender. Never put secret keys, DB passwords, tokens or
  keystore passwords in chat or code.
- Release signing (stage 7): the owner decides how the release keystore is made and kept; switching from the debug
  key to it needs one uninstall on the phone (data can move by CSV export).

## Gotchas learned

- Local Gradle: there is no global Gradle. Use `android/gradlew` (it downloads Gradle 9.8.1 into `~/.gradle`) and pass
  the proxy as Gradle args, not `GRADLE_OPTS`:
  `./gradlew -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7892 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7892 :core:test`
  (only `:core` builds here). TLS hiccups to dl.google.com / raw.githubusercontent.com happen: retry.
  `google()` is limited to androidx/com.android/com.google groups in `settings.gradle.kts`.
- A local Gradle build that hangs leaves daemons holding `core/build/kotlin`: run `./gradlew --stop`, stop leftover
  `KotlinCompileDaemon` java processes, delete `android/core/build/kotlin`, and build with `--console=plain`.
  `swift test` in `CourseKit/` takes ~20 s here.
- When the home Wi-Fi drops, every TLS handshake fails (GitHub through Clash and direct sites alike): wait and retry.
- Robolectric on JDK 21 needs `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` and
  `--add-opens=java.base/java.io=ALL-UNNAMED` (set in `app/build.gradle.kts`). In Robolectric, POST_NOTIFICATIONS is
  not granted and `canScheduleExactAlarms()` is false unless a test sets them (`ScreenshotTests.allowEverything`).
- supabase-kt: `parseFragmentAndImportSession` is `@SupabaseInternal`; `SupabaseBackend` parses the link's fragment
  itself (`fragmentParameters`), checks the token with `retrieveUser` and calls `importSession`. The stored session is
  restored offline with `sessionManager.loadSessionOrNull()`. Errors map to `VocabAuthError` codes as on iOS.
- Words times: created at millisecond precision; Room keeps epoch microseconds (the server's precision).
- Compose draws a tab with no width in text fields: import fields use `TabsAsSpaces`. `DateUtils` says
  "0 minutes ago" under a minute: the sync row says "Synced just now".
- Material Symbols drawables carry `android:tint="?attr/colorControlNormal"`: remove it after downloading and put the
  `>` back where it ended the tag (check every drawable parses).
- Android 16 APIs behind feature flags may be missing from the SDK stubs: use the action string.
- Live Updates: `hasPromotableCharacteristics()` has two rule sets (flag `ui_rich_ongoing`, see AOSP
  `android16-qpr2-release` Notification.java): not colorized vs colorized, so never hard-code either.
  `canPostPromotedNotifications()` is a diagnostic, not a gate. vivo/OPPO/Xiaomi islands ignore the chronometer and
  hide while the app is in front. Device findings: github.com/tigerduck-app/android-dynamic-island-test.
- Don't name a Kotlin property `java` (it shadows the `java.time` package); Glance `defaultWeight()` is scoped to
  Row/Column (no import).
- Room ordering uses `createdAt`; `Repository.now()` hands out strictly increasing stamps.
- `SwipeToDismissBox` rows are keyed (`key(id)`), so a deleted row's swipe state doesn't move to the next one.
- Bash heredocs with quotes and `\'` broke once: write helper scripts with the Write tool, run them with python.
  Python through a heredoc also turns `\n` in Kotlin strings into real line breaks (broke three files in the AI work):
  change code containing backslashes with the Edit/Write tools only.
- AI on Android: ML Kit's `libmlkit_google_ocr_pipeline.so` for all ABIs made the APK 76 MB; `packaging` excludes all
  but arm64-v8a and `useLegacyPackaging = true` compresses native libraries (40 MB). Kotlin `data object` makes its own
  `readResolve`: don't write one. A test fake's `var goal` clashes with `override fun setGoal` (JVM signature).
  Compose screenshots with two scrollables need `onAllNodes(hasScrollAction()).onFirst()`. The saved-key hint is in
  the key field's label, which always shows (as a placeholder it didn't).
- Auto mode once blocked plain reads right after a secret was written (`gh secret set`); ask before secret changes.
- Gotchas from Android 0.6: no Pillow/numpy here; images are made with .NET System.Drawing from PowerShell
  (`tools/make_android_icon.ps1`, run as a script block). In PowerShell, `New-Object T(a * b, c)` parses the comma
  first: compute arguments into variables; a `[string]` parameter turns a later assignment of the same name into a
  string. A Compose `Dialog` is its own window, so Roborazzi's `onRoot()` doesn't capture it: screenshot its content
  composable (`FullScreenFocusContent`). Two Kotlin functions differing only in return nullability clash (JVM
  signature). Screens that need app-wide state read it from composition locals (`LocalAppearance`,
  `LocalWallpapers`) so tests pass stand-ins.

## Environment notes (Windows 11, no Mac)

- Installed: Swift 6.4 (`swift test` in `CourseKit/` works), JDK 25, Git, gh (signed in as Hilbert0619), Node,
  Python 3.10 (default python; don't install into it). Not installed: Android Studio, Android SDK.
- GitHub needs Clash: `git -c http.proxy=http://127.0.0.1:7892 push`, `HTTPS_PROXY=http://127.0.0.1:7892` for gh/curl,
  per command only. In Git Bash, gh is at `/c/Program Files/GitHub CLI/gh.exe`.
- Never round-trip UTF-8 files through PowerShell 5.1; write helper scripts with the Write tool and run them with python.
- Commit email for this repo is set per repo (253035987+Hilbert0619@users.noreply.github.com).

## The iOS app (change it only when I ask)

- Swift 6 + SwiftUI + SwiftData + WidgetKit + ActivityKit, iOS 17+. Tabs: Today, Timetable, Focus, Words, Settings.
- AI code: `CourseKit/Sources/AIKit` (presets with tools/photos flags, requests, SSE parser, stream decoding with tool
  calls, errors, prompts, TodoDraft, MarkdownBlock, SentenceWords; the agent: JSONValue, AITool, AIAgentSession,
  AppTools, TimetablePhoto; 51 test groups) and `CourseTable/AI` (AIStore with Keychain keys, usage, saved answers and
  chats with cards; AITransport with `DemoAITransport` for `-aiDemo`, scripted tool calls; AIRun with a generation
  token; AgentRun + AgentContext; settings, word help, study tools, TextScanner, TimetablePhotoReader, AssistantView).
  UI tests `CourseTableUITests/AIUITests.swift`, `AgentUITests.swift` (`-aiDemoPhoto` gives a drawn sample timetable).
- Agent gotchas: Anthropic thinking blocks are bound to the conversation prefix (edited or trimmed history → 400 on
  new accounts), so they live only within a turn and are never saved; DeepSeek needs every earlier
  `reasoning_content` back once tools are on (400 otherwise); the iOS workflow now also runs on `CourseKit/Sources/AIKit/**`.
- Wallpapers and Storage (0.6): `CourseKit/Sources/CourseKit/FocusWallpapers.swift` (rows, candidates, the card's
  crop around `focus_y`, cache limits) and `FolderUsage.swift`; `CourseTable/Focus/WallpaperStore.swift` (list in
  `Caches/Wallpapers/catalog.json`, refreshed after 12 h; pictures `<id>.jpg` there; UI tests draw `DemoWallpaper` and
  never use the network); `Views/StorageView.swift`. The wallpaper list is a plain URLSession GET with the publishable
  key in `apikey` (no Supabase SDK, no account). UI test `PlannerUITests.testWallpaperHistoryAndStorage`.
  CourseKit code using CGSize/CGRect needs `import CoreGraphics` on Apple platforms (Windows' Foundation has them).
- Gotchas from the 0.6 work: a List `.listRowBackground` isn't redrawn when what it shows changes (the 0.6.0
  wallpaper appeared only after reopening the app): draw changing backgrounds inside the row (zero row insets, clear
  row background); UI tests should make async content arrive late, as on a phone. `gh workflow run
  backend-deploy.yml` went through when the owner asked for it (2026-10-16). `supabase start` already seeds `[storage.buckets]`; a second `seed buckets` asks before
  overwriting the bucket's settings (`--yes`). Unsplash's site refuses scripts (403); its image CDN
  (images.unsplash.com, reachable from China without the proxy) doesn't, so `make_wallpapers.py` only uses the CDN.
- Gotchas from the 0.7 work: the whole UI-test suite plus LookUITests no longer fits screenshots.yml's 40 minutes
  (it gets cut off and saves nothing): pass `only=`. A second screenshots run on the same branch cancels the first
  (concurrency group): run a "before" on another branch. `XCUIDevice.shared.appearance = .dark` didn't take on CI:
  the app reads `-uiColorScheme dark` in UI tests. In a List, `Label` keeps a wide icon column: use an HStack for
  inline chips. A colour `.background` reaches into the safe area unless `ignoresSafeAreaEdges: []`. Designing
  colours: `CourseColors` values come from `tools/course_colors.py` (OKLCH); keep the contrast and distinctness
  tests green.
- Gotchas from the 0.7.1 work: `.blur(radius:opaque: true)` + `.drawingGroup()` drew the wallpaper's shapes sharp
  with black where the base should be (iOS 26 Simulator): the wallpapers are elliptical gradients now. A list
  section can't be one glass shape: rows get `listRowBackground(FrostedFill())`, cards draw their own glass with zero
  row insets. Several buttons in one list row: keep them borderless and put the glass on the label (other styles
  make the row tap them all). `-appStyle classic` picks the look in UI tests (a launch argument with the setting's
  own key would hide changes made in the app). In the Simulator a scheduled Live Activity (`request(…start:)`)
  started on time with the app in the background. A screenshots job once stopped after 15 minutes without running a
  step (a runner hiccup): `gh run rerun`. LookUITests now takes ~16 minutes.
- iOS release: bump MARKETING_VERSION/CURRENT_PROJECT_VERSION (16 places in project.pbxproj; 0.7.1 / 11 now),
  `gh workflow run ipa.yml`, download to `C:\Users\AI\Downloads\CourseTable\CourseTable-<version>.ipa`, installed
  with iloader.
- Gotchas from the AI work: List rows off screen aren't in the XCUITest tree (scroll first; the first cell can be a
  section header, so tap rows by identifier); a sheet's `.task` can run twice; `@AppStorage` drafts survive between
  UI-test launches (`AIStore.makeForLaunch` clears them); PowerShell's execution policy blocks `.ps1` scripts (CI
  waits use a bash loop calling gh).
- Loose ends (when I ask): `docs/v3-development-record.md` (and v0.4/v0.5 records); the release fixes (debug APK in the
  public release, tags at the wrong commit, no LICENSE, no third-party notices); later Words items and the timetable
  backlog (long words in grid blocks, dark-mode screenshots, "All weeks" view, per-course reminder mute, school
  import, multiple terms).

## How I like to work

- Communicate in English. I give goals; do as much as you can and keep my manual steps minimal.
- Short report at the end of each stage, with run links. Verify for real (tests/CI/screenshots), don't trust
  "should work".
- Don't bypass security mechanisms; if something needs my approval or sign-in, say exactly what to do.
- "Complete the handover" means: finish the docs so a new session starts the next thing; don't start it.
