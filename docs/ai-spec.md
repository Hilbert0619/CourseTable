# AI helpers — spec and plan (iOS 0.4, then Android)

Status: proposed 2026-10-12 from the owner's request: "configure custom API access to various LLMs for supplementary
learning and everyday use; define the most suitable solution". iOS is built first; Android ports it after its stage 7.

## 1. Decisions

| Topic | Decision |
|---|---|
| Who pays, where the key lives | **Bring your own key.** The user adds an "AI connection" (provider, API key, model). Requests go straight from the phone to that provider; nothing passes through our Supabase and no key ships in the app. This replaces the "Edge Function with our key" idea in `vocabulary-spec.md` (no cost or abuse risk for the owner, no server work). |
| Optional | Every AI button is optional. Without a connection the app works exactly as before; AI buttons lead to the setup page. |
| Providers | Two wire formats cover nearly everything: **OpenAI-compatible Chat Completions** and the **Anthropic Messages API**. Presets for providers usable in mainland China without a VPN come first (DeepSeek, Qwen, Kimi, GLM, Doubao, SiliconFlow), then OpenAI, Claude, Gemini, OpenRouter, a local server (Ollama / LM Studio on the same Wi-Fi), and Custom (any OpenAI-compatible URL). |
| Models | "Load models" lists the provider's models (`GET …/models`); typing a name always works. Each preset suggests a default. Model names change often, so presets live in one table (`AIProvider.presets`). |
| No SDKs | One small HTTP client per platform (URLSession; Ktor on Android, already there through supabase-kt). The protocol logic (request bodies, streaming parser, errors, prompts, parsing AI output) is pure code in `AIKit` with tests. |
| Keys | iOS Keychain, this device only (`AfterFirstUnlockThisDeviceOnly`, not synced, not in backups). Android: encrypted with an Android Keystore key, excluded from backup. Shown masked, never logged, never exported, deleted with the connection. |
| Privacy | Each feature sends only what it needs (the word and its dictionary entry; the pasted sentence; a to-do sentence plus course names). Never the account email; the schedule or weak words only when the user turns that on in the chat. A one-time notice per connection: "Your text goes to <provider> under their terms; CourseTable's server never sees it." |
| Honesty | Every answer is labelled "AI-generated · <model>". The dictionary stays the source of truth for meanings and for grading. |
| Cost | Nothing runs in the background: every request is a tap. Answers are cached on the device (re-opening a word's explanation is free). Token usage per connection per month is shown in settings. |
| Safety | AI output never acts on its own: the only structured output (a to-do) fills the editor for the user to check and save. Pasted text is treated as data in the prompts. |
| UI | The five tabs stay. AI enters through buttons where it helps (word page, wrong answers, to-do editor) and an **Assistant** screen (sparkle button in the Today and Words toolbars). Settings gets an **AI** page. English + Simplified Chinese; answers follow the app language. |

## 2. Features (priority order)

1. **AI settings** (Settings → AI): connections list (add from a preset → paste key → pick model → Test), the
   default connection, monthly token usage, privacy note, "Get a key" link per provider.
2. **Word help** (word page): chips **Explain** (meaning nuances for 考研, two example sentences with translations),
   **Memory aid** (roots, affixes, a mnemonic), **Compare** (synonyms and look-alikes, e.g. *affect / effect*),
   **Collocations**. Streamed, cached per word and kind, "Regenerate".
3. **"Why?" after a wrong answer** (study session): explains the right meaning against the picked option (choice) or
   the misspelling (spelling).
4. **Sentence analysis (长难句)**: paste or scan text (camera / photo, on-device text recognition) → main clause,
   clauses and modifiers, a translation, key words with "Add to my words".
5. **Writing feedback (作文)**: paste an essay, pick the task (英语一/二, 小作文/大作文) → a score band, errors with
   corrections, a better version, phrases to learn.
6. **Quick add to-do**: in the to-do editor, "Describe it" (e.g. 明天高数课后交论文) fills title, date and timing
   (any time / at a time / after a class); the user checks and saves.
7. **Assistant**: a general chat, streamed, conversations kept on the device (latest 30), optional context switches
   "Include this week's schedule" and "Include my weak words" (off by default).
8. Later: a weak-word report from local statistics.

## 3. Protocol details (AIKit)

OpenAI-compatible: `POST {base}/chat/completions`, `Authorization: Bearer <key>`, body `model`, `messages`
(`system` first), `stream: true`. Per preset flags: `stream_options.include_usage` (only where supported), JSON mode
`response_format: {"type": "json_object"}` (only where supported), the output-limit field. No sampling parameters
(several current models reject them). `max_tokens` is left out (provider defaults; some reject values above their
own cap); the prompts ask for short answers. Stream: `data: {json}` lines, text in `choices[0].delta.content`,
reasoning models' `delta.reasoning_content` shown as "Thinking…", end at `data: [DONE]`, usage in the final chunk.

Anthropic: `POST https://api.anthropic.com/v1/messages`, headers `x-api-key`, `anthropic-version: 2023-06-01`, body
`model`, `max_tokens` (required; 16000, a ceiling, not a target), `system`, `messages`, `stream: true`. Stream events
`message_start` (input tokens), `content_block_delta` with `text_delta` (or `thinking_delta`, not shown),
`message_delta` (stop reason, output tokens), `message_stop`, `ping`, `error`. Models: `GET /v1/models?limit=100`.
Default model `claude-opus-5-5`; the picker lists the others (e.g. `claude-haiku-5-5` costs less).

The SSE parser works on bytes, not `URLSession.AsyncBytes.lines` (that skips the blank lines that end an event).

Errors map to plain messages: 401/403 wrong key, 402 or "insufficient balance" no credit left, 404 unknown model or
URL, 429 too many requests, 5xx/529 the provider is busy, network failure, an answer cut off by the length limit.

## 4. Testing

- AIKit tests on Windows/Linux CI, plus shared fixtures (`spec/fixtures/ai-*.json`: recorded streams of both
  formats, error bodies, to-do drafts) that the Kotlin port must pass too.
- UI tests and screenshots use a fake transport (launch argument `-aiDemo`) with canned streamed answers; CI never
  holds a real key.
- The owner tests with a real key on the phone (any preset; GLM-4.7-Flash on BigModel is free, DeepSeek or Qwen
  cost a few yuan to try).

## 5. Stages (iOS)

1. AIKit: presets, request building, SSE parser, stream decoding, errors, prompts, to-do drafts; tests and fixtures.
2. AI settings (Keychain, test, models), the transport, Word help and "Why?".
3. Sentence analysis (with scanning), writing feedback, quick add to-do.
4. Assistant chat, screenshots, strings, IPA 0.4.0 → the owner's device test.

Android: the same features as a stage after its release (stage 7), porting AIKit to `android/core`.

## 6. Notes

- Provider URLs and suggested models were checked on 2026-10-12 (e.g. DeepSeek's `deepseek-chat` is deprecated in
  favour of `deepseek-v4-flash`; Gemini uses the `gemini-flash-latest` alias). Kimi's and OpenAI's suggestions come
  from secondary sources; "Load models" and Custom cover anything that moved.
- With bring-your-own-key, the user deals with the provider directly; the app is a client. It labels AI content and
  sends no personal data it doesn't need. (Not legal advice.)
