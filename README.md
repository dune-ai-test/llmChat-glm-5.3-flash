# Aster — LLM Chat for Android

A polished, Apple-inspired Android client for **any OpenAI-compatible API** —
built from the ground up against the HTML designs in [`ui/`](ui/) and the
[functional product specification](ui/Complete%20Android%20LLM%20Client%20Functional%20Product%20Specification.md).
Current version: **v1.2.4** · minSdk 26 (Android 8.0) · target 35 · Kotlin + Jetpack Compose.

**Downloads:** every signed build is published automatically to the
[Releases page](../../releases) as `Aster-v<version>-release-signed.apk`.

## Screens

- **Home** — Aster top bar with a time-aware greeting and your display name
  under it, model card with connection status, quick actions (New Chat /
  Voice Chat / Switch Model / Connections), "continue where you left off",
  offline banner and queued-message indicator. Adapts to first-run,
  connected and offline states.
- **Chats** — history grouped by Today / Yesterday / Previous 7 days,
  full-text search, long-press actions: rename, pin, favorite, duplicate,
  archive, delete with Undo.
- **Voice** — full-screen session: pulsing orb, 10-bar waveform, live
  transcript, continuous listen → think → speak loop with barge-in
  interruption, model chip in the control deck, tap a bubble for
  Copy / Regenerate.
- **Chat** — streaming responses with a stop button, markdown + syntax-colored
  code blocks (copy button), **image attachments** (vision requests),
  in-chat search with match jumping, tap for a token/time/TPS details pill,
  long-press actions (copy / regenerate / edit / share / read aloud / delete),
  response variants, model switching mid-conversation, drafts, offline queue.
  Generation survives leaving the screen and reattaches when you return.
- **Connections** — unlimited connections: add, edit, duplicate, rename, set
  default, enable/disable, delete, test; per-URL model caching.
- **Add Connection wizard** — provider → API configuration (URL, key, models
  fetched live via `/models`, multi-select with favorites + default badge) →
  staged connection test (connecting / handshake / model check) → success &
  error screens with recovery actions.
- **Settings** — Chat (streaming, temperature, max tokens, system prompt +
  presets), Voice (STT/TTS, speaking rate, auto-play, continuous, interrupt),
  Appearance (light/dark/system, accent themes, font size, density, bottom
  bar vs. navigation drawer), Advanced API (custom headers, timeout, endpoint
  path, raw JSON parameters, redacted request log), Data (encrypted backup,
  reset, delete all), About.

## Backups & encryption

- **One-time backup password** set in Settings ▸ Data, stored on-device in
  keystore-encrypted prefs — exports never ask again, imports try the stored
  password silently and only prompt when a file needs it.
- Files use Aster's self-made **ASTV1 vault format**: per-file random salt,
  PBKDF2-HMAC-SHA256 (120k iterations), AES-CBC + encrypt-then-MAC — no
  third-party crypto. Wrong-password files are rejected before a byte is
  decrypted.
- Files are password-portable: open them on a new phone by typing the export
  password — nothing is device-bound.
- **Daily automatic backup** (`aster-auto-<date>.llm`) to your chosen export
  folder, newest 7 kept; silent unless a backup password and folder are set.
- Manual exports land as `aster-backup-<stamp>.llm`; imports also accept
  plain-JSON backups and older vault files.

## Architecture

- Jetpack Compose with a custom "Aster" design system (Inter, warm neutrals,
  indigo accent, iOS-style grouped cards, fixed inset bars, top-bar shadow).
- Room database (migrations) for connections, conversations and messages;
  API keys, backup password and secrets live only in EncryptedSharedPreferences.
- Provider-agnostic OkHttp client: `GET /models` + SSE streaming
  `/chat/completions`, image parts as data URLs, typed human-readable errors.
- Navigation: 5-tab pager + optional modal drawer, swipe-back from chat,
  back-stack routes for wizard/settings/search/archived.
- Home-screen **widgets and launcher shortcuts** (New Chat / Voice Chat).
- R8 shrink + optimization: signed APK ≈ 3.5 MB.

## Building (GitHub Actions only)

No local Android SDK is used — everything builds in CI:

| Workflow | Trigger | Output |
| --- | --- | --- |
| `build-apk.yml` | manual | Debug APK artifact |
| `build-signed-release.yml` | manual or `v*` tag | Signed release APK → **Releases page** |
| `verify-keystore.yml` | manual | Keystore credential pre-flight |

Signing needs four repository secrets: `ANDROID_KEYSTORE_BASE64`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
(without them, release builds fall back to debug signing so the repo still
builds). Signed builds upgrade each other; installing a signed APK over a
debug build requires one uninstall (different signing key).

## Configuration notes

- **API key is optional** — many local / self-hosted OpenAI-compatible
  servers (LM Studio, Ollama, etc.) accept unauthenticated requests, so the
  wizard proceeds without a key when the endpoint doesn't need one.
- If the app ever crashes, the next launch shows a crash dialog with the
  captured stack trace and a Copy button (Settings ▸ Advanced keeps a
  redacted request log too).
- Everything user-facing stays on **`main`**; feature flavors (e.g. the
  Aster Judge branch) are never merged back.
