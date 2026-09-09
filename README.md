# Aster — LLM Chat for Android

A polished, Apple-inspired Android client for **any OpenAI-compatible API** —
built to the HTML designs in [`ui/`](ui/) and the
[functional product specification](ui/Complete%20Android%20LLM%20Client%20Functional%20Product%20Specification.md).

## Screens

- **Home** — greeting, current model card with connection status, quick actions
  (New Chat / Voice Chat / Switch Model / Connections), recent chats and a
  minimal activity summary. Adapts to first-run (no connection), connected and
  offline states.
- **Chats** — full history grouped by Today / Yesterday / Previous 7 days,
  search, long-press actions: rename, pin, star, duplicate, archive, delete
  with Undo.
- **Voice** — full-screen voice session: pulsing orb, 10-bar waveform, live
  transcript, continuous listen → think → speak loop with barge-in
  interruption, mute / stop / end-session controls.
- **Chat** — streaming responses with a stop button, markdown + syntax-colored
  code blocks (copy button), long-press message actions (copy / regenerate /
  edit / branch / share / read aloud / delete), response variants
  ("Response 2 of 3"), model switching mid-conversation, drafts, offline queue.
- **Connections** — unlimited connections: add, edit, duplicate, rename, set
  default, enable/disable, delete, test.
- **Add Connection wizard** — provider → API configuration (URL, key, models
  fetched live via `/models`, manual entry) → staged connection test
  (connecting / handshake / model check) → success & error screens with
  recovery actions.
- **Settings** — Chat (streaming, temperature, max tokens, system prompt +
  presets), Voice (STT/TTS, voice, speaking rate, auto-play, continuous,
  interrupt), Appearance (light/dark/system theme, font size, chat density),
  Advanced API (custom headers, timeout, endpoint path, raw JSON parameters,
  redacted debug logging), Data (export / import JSON, reset, delete all).

## Architecture

- Kotlin + Jetpack Compose (Material 3 custom "Aster" design system: Inter,
  warm neutrals, indigo accent, iOS-style grouped cards and floating tab bar).
- Room database for conversations, messages and connections (migrations
  supported); API keys stored in EncryptedSharedPreferences only.
- Provider-agnostic OkHttp client: OpenAI-compatible `GET /models` and
  `POST /v1/chat/completions` with SSE streaming, typed human-readable errors
  and recovery actions.
- Connectivity monitor drives the offline banner; messages sent while offline
  are queued locally and flushed automatically on reconnect.

## Building the APK (GitHub Actions only)

The workflow builds debug + release APKs on every push and publishes them as
the `LLM-Chat-APKs` artifact; pushing a tag like `v1.0.0` also attaches them
to a GitHub Release. No local Android SDK needed.

```
.github/workflows/build-apk.yml
```
