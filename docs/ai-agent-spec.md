# AI agent — spec and plan (iOS 0.5, then Android)

Status: requested by the owner on 2026-10-15 and handed over to a new session; **nothing built yet**. It builds on AI
help ([`ai-spec.md`](ai-spec.md), iOS 0.4.0): the user's own key, requests straight from the phone to the provider,
no server. Confirm the three questions in §9 with the owner before coding.

## 1. The request

> "The AI should be able to utilize agent capabilities to perform simple tasks, such as: adding reminders, tracking
> and analyzing vocabulary learning progress, and automatically adding corresponding courses based on imported class
> schedules using image recognition."

So, three jobs for the Assistant, done by the model calling the app's own functions ("tools"):

1. **Reminders**: "remind me to hand in the paper after Calculus tomorrow", "what's on my list this week?", "move the
   package to 18:00", "done with the paper". In this app a reminder is a **to-do** with a time or "after a class"
   (it sends the notification and shows in the class Live Activity); class reminders are a setting.
2. **Vocabulary progress**: "how am I doing?", "which words do I keep forgetting?", "will I finish the book before the
   exam?". The model reads the learner's numbers and explains them; it changes nothing (in v0.5).
3. **Timetable from a picture**: a photo or screenshot of the school timetable (教务系统 page, printed sheet) becomes
   courses, checked by the user in the existing import preview before anything is saved.

## 2. Decisions (proposed; §9 lists what to confirm)

| Topic | Proposal |
|---|---|
| Mechanism | **Tool calling** (function calling) through the user's connection, in both wire formats. No server, no new keys. |
| Reading vs changing | Read tools run on their own and show as a small "Looked at your to-dos" line. **Every change is a proposal card** in the chat (exactly what will be added or changed) with **Confirm / Cancel**; nothing changes without a tap. After Confirm: "Added · Undo". |
| What the model may change (v0.5) | To-dos (add, edit, tick off, delete) and courses (add, through the import preview). Not: deleting courses, settings, the Words account, anything on the network. |
| Limits | At most 8 tool rounds per message; each tool result ≤ 4,000 characters (lists are cut with "…and N more"); arguments validated before anything runs. |
| Models | Needs a model that can call tools. A provider that rejects `tools` gets "This model can't use the app's tools; pick another in Settings → AI Help" and the chat carries on without them. Each connection gets two capability flags, **Tools** and **Images**, with defaults from the preset, a manual switch, and a check in "Test Connection". |
| Photos of timetables | Both ways, chosen automatically: if the connection (or its **photo model**, below) reads images, the photo goes to it (downscaled JPEG, ≤ 1600 px); otherwise the phone reads the text with **on-device OCR (Vision) including each text box's position**, and a text-only model rebuilds the grid from the positions. Either way the model writes the app's **CSV import format** (README "Import format", Chinese headers allowed), which goes through the existing `CourseImporter` + `ImportPlan` into the **existing import preview** (clashes, duplicates, unreadable lines). Period times seen in the picture are offered separately (confirm). |
| Photo model | A connection may name a second model for pictures (e.g. GLM: text `glm-4.7-flash`, photos `glm-4.6v-flash`; DeepSeek: `deepseek-v4-flash-vision-exp`). |
| Privacy | The Assistant's tools let the model read the user's own to-dos, classes and learning numbers, sent only to the provider the user chose, only while a chat uses them. The privacy text in Settings → AI Help and the Assistant's first-run note say so. A photo (or its text) is sent only when the user starts a photo import. |
| Safety | Text from photos, pasted passages and earlier answers is data: a write never happens without the user's tap, so an instruction hidden in a picture can at most produce a proposal card the user sees. |

## 3. Tools (first set)

| Tool | Kind | Arguments | Result | App source |
|---|---|---|---|---|
| `list_todos` | read | `from`, `to` (dates, ≤ 31 days) | id, title, date, timing (any time / HH:mm / after course), done | `PlannerModel.todos` |
| `add_todo` | change | `title`, `date`, `time?` (HH:mm), `afterCourse?` | the proposal; after Confirm the new id | `TodoDraft` rules (course matching, class-that-day check), `PlannerModel.save`; notifications via the existing `Alerts.sync` |
| `update_todo` | change | `id`, any of `title`, `date`, `time`, `afterCourse`, `done` | as above | `PlannerModel.save` / `setDone` |
| `delete_todo` | change | `id` | as above | `PlannerModel.delete` |
| `get_schedule` | read | `from`, `to` (≤ 14 days) | class lines like `Tue 2026-10-13 08:00–09:40 高等数学 (A101)`, the term week | `AIPrompts.upcomingClasses`, `Semester.status` |
| `list_courses` | read | — | name, teacher, room, weekday, periods, weeks | `Persistence.timetable` |
| `add_courses` | change | `csv` (the import format) | the import preview's summary; after Confirm the counts | `CourseImporter.parse` → `planningImport` → `ImportView` |
| `get_vocab_overview` | read | — | today's plan (new done/target, reviews left, minutes), streak, learned / book size, retention target, exam countdown and pace | `VocabModel.plan()`, `examCountdown()`, `VocabStats` |
| `get_vocab_history` | read | `days` (≤ 90) | per day: answers, new words, accuracy, minutes | `VocabStats` |
| `get_hard_words` | read | `limit` (≤ 50) | word, meaning, lapses, last answer, chance of recall | `VocabModel.hardWords`, `Scheduler` |
| `get_word_progress` | read | `word` | state, answers, lapses, next review, chance of recall | `VocabModel.cards` |

Words tools need a signed-in Words account; without one they return "not signed in" (the model says so). Later
candidates (not v0.5): `set_daily_new_words`, `add_favourites`, `start_focus`, class-reminder settings.

## 4. Protocol (AIKit)

- **OpenAI-compatible**: request `tools: [{type: "function", function: {name, description, parameters}}]` (JSON
  Schema), `tool_choice: "auto"`. Stream: `choices[0].delta.tool_calls: [{index, id?, function: {name?, arguments}}]`,
  where `arguments` arrives in fragments to be joined per `index`; `finish_reason: "tool_calls"`. Next request: the
  assistant message `{role: "assistant", content, tool_calls: [{id, type: "function", function: {name, arguments}}]}`,
  then one `{role: "tool", tool_call_id, content}` per call.
- **Anthropic**: `tools: [{name, description, input_schema}]`, tool choice `auto` (current models reject forced
  `any`/`tool`). Stream: `content_block_start` with `{type: "tool_use", id, name, input: {}}`, then
  `content_block_delta` `{type: "input_json_delta", partial_json}`, `content_block_stop`; `stop_reason: "tool_use"`.
  Next request: the assistant's content blocks echoed back unchanged (text, tool_use, and thinking blocks), then **one**
  user message with all `{type: "tool_result", tool_use_id, content, is_error?}` blocks.
- **Images**: OpenAI-compatible content parts `[{type: "text", text}, {type: "image_url", image_url: {url:
  "data:image/jpeg;base64,…"}}]`; Anthropic `{type: "image", source: {type: "base64", media_type: "image/jpeg", data}}`.
- **Types**: `AIMessage` grows content parts (text, image), the assistant's tool calls and tool results; saved chats
  stay readable (decode old files with defaults). `AIStreamEvent` gains tool-call start / argument fragment events and
  the stop reason `.toolUse`; `AIAnswer` collects the calls.
- **Agent loop** (pure, in AIKit, testable): send → stream → for each call: check the tool exists, parse the JSON
  arguments, validate them (types, required fields, enums, lengths, date/time formats) → read tools run through an
  injected executor; change tools become **pending proposals** the app shows; the user's Confirm/Cancel becomes the
  tool result ("added", "the user cancelled") → next round, until the model answers in text or the round limit. Bad
  JSON or arguments → an `is_error` result explaining the problem, so the model can fix it.
- **OCR with positions**: Vision observations become lines like `[x 0.12 y 0.31 w 0.08 h 0.02] 高等数学 A101`
  (normalised, top-left origin, sorted top-to-bottom), with an instruction that column headers are weekdays and row
  headers are periods. Pure formatting in AIKit, tested.

## 5. App

- **Assistant**: tool lines and proposal cards inline; a card for a to-do shows title, date and timing; for courses it
  opens the import preview (sheet) and returns "added 12 courses, 1 clash skipped".
- **Entry points**: the Assistant (Today/Words ✨); the import screen gets **From a Photo (AI)** (pick or take a photo
  → reading → preview; `ImportView` needs an initial-text parameter); Words → Statistics gets **Analyse My Progress**
  (opens the Assistant with that request).
- **Settings → AI Help**: per connection the Tools / Images switches, an optional photo model, and Test Connection
  also checks tool calling (a tiny `ping` tool).
- **UI tests** (`-aiDemo`): `DemoAITransport` scripted with tool calls: "remind me…" → `add_todo` card → Confirm → the
  to-do is on Today; "how am I doing" → `get_vocab_overview` → text; photo import with a bundled test picture → canned
  CSV → preview. Screenshots `*-agent-*`.

## 6. Presets: tool calling and images (searched 2026-10-15; verify when building)

| Provider | Tools | Images |
|---|---|---|
| DeepSeek | yes (`deepseek-v4-flash`, `-pro`) | only `deepseek-v4-flash-vision-exp` (experimental, Aug 2026; text models answer 400 to images) |
| Qwen (百炼) | yes (`qwen-plus` etc.) | `qwen3-vl-plus` / `qwen3-vl-flash` (check mainland model names) |
| GLM (智谱) | check `glm-4.7-flash` | `glm-4.6v-flash` (free, native function calls; check the official id on bigmodel.cn) |
| Kimi, Doubao, SiliconFlow | check | check |
| OpenAI, Claude, Gemini | yes | yes |
| Ollama / LM Studio | depends on the model | depends on the model |

## 7. Tests and shared cases

- AIKit tests on Windows; new shared fixtures `spec/fixtures/ai-tools-*.json` for the Android port: streamed tool
  calls in both formats (arguments split across chunks, two calls in one turn, text before a call), the follow-up
  request bodies, argument validation cases, OCR lines → prompt text, and CSV answers → import results (reusing the
  importer's cases).
- App tests: tool executors against in-memory stores (to-dos, timetable, the demo vocabulary).
- The model's own behaviour can't be unit-tested: the owner's device test with a real key covers it.

## 8. Stages (iOS first; Android after its release stage)

1. AIKit: message parts, tool definitions and validation, tool-call stream decoding and follow-up requests (both
   formats), image parts, the agent loop, OCR line formatting; fixtures and tests.
2. App: tool executors, proposal cards and undo, the Assistant with tools, capability flags and the tool check, demo
   transport scripts, UI tests and screenshots.
3. Timetable from a photo: Vision OCR with positions, the image path, CSV → the import preview, period times.
4. Analyse My Progress, polish, strings (zh-Hans), docs, IPA 0.5.0 → the owner's device test.

## 9. Ask the owner first (recommendations first)

1. **Changes**: confirm every change with a tap (recommended), or let the model add to-dos directly?
2. **Photos with a text-only model**: on-device OCR with positions (recommended; works with DeepSeek's text model),
   or require a model that reads images?
3. **Scope of changes in 0.5**: to-dos and adding courses only (recommended); vocabulary settings and focus later?

## 10. Risks

- Small or free models call tools less reliably: validation, error results, confirmation cards and the round limit
  keep mistakes harmless.
- Timetable pictures vary (merged cells, 单/双周, "1-16周", two classes in one cell): the CSV format already handles
  these, and the preview shows every problem line before saving.
- Cost: tool rounds add tokens; the usage line per connection shows it.
