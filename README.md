# LLM Chat — Android (iOS-styled)

A professional, iOS-styled Android chat client for **any OpenAI-compatible API**:

- **Chat** — text-only conversations with an Apple Messages-style bubble transcript.
- **Voice** — tap-to-talk voice conversations with continuous *listen → think → speak* rounds, while the full transcript stays visible on screen.
- **Settings** — connect any server with **URL · API key · Model**, test the connection, and pick a model straight from the live `/models` list.

Built with **Kotlin + Jetpack Compose (Material 3)**. The APK is built **entirely on GitHub Actions** — no local Android SDK or build required.

## Screens

| Tab | What it does |
|---|---|
| **Chat** | Text-only chat. Messages render as iOS-style bubbles; history is sent with every request. |
| **Voice** | Voice-only chat with a big tap-to-talk orb (pulses while listening, shows progress while thinking). Replies are spoken aloud via TTS and the transcript is shown below, including live captions of what you're saying. |
| **Settings** | Enter the server base URL, API key and model. *Test Connection* calls `GET /models` and lets you tap a model from the returned list. Everything persists locally on the device. |

The app speaks the **OpenAI Chat Completions** protocol:

```
POST {base-url}/v1/chat/completions        ← chat requests
GET  {base-url}/v1/models                  ← model list (Test Connection)
Authorization: Bearer {api-key}
```

`/v1` is appended automatically, so both `https://api.openai.com` and `https://host/v1` work — ideal for OpenAI, OpenRouter, Groq, Ollama, LM Studio, llama.cpp servers, etc.

## Build the APK on GitHub (no local build)

1. Push this repository to GitHub (branch `main` or `master`).
2. GitHub Actions runs **Build APK** automatically on every push (or via *Run workflow*).
3. Download the APKs from the run's **Artifacts** → `LLM-Chat-APKs`:
   - `app-debug.apk` — installable debug build
   - `app-release.apk` — release build signed with the debug key, so it installs directly
4. Optional: push a tag like `v1.0.0` and the APKs are attached to a GitHub Release automatically.

Workflow file: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)

## Getting started in the app

1. Open the **Settings** tab.
2. Enter your **Server URL** (e.g. `https://api.openai.com`).
3. Enter your **API key**.
4. Tap **Test Connection** — the model list loads from the server.
5. Tap a model from the list, then **Save Settings**.
6. Start chatting in **Chat** or talking in **Voice**.

> The microphone permission is requested the first time you start a voice session. Voice input uses the device's on-device speech recognizer; replies are spoken with the system TTS engine.

## Tech notes

- Min SDK 26 (Android 8.0), target SDK 35, Kotlin 2.0, Compose BOM 2024.09.
- Networking via OkHttp with clear-text traffic allowed so local servers (`http://192.168.x.x`) work.
- Settings persist in `SharedPreferences`; the API key never leaves the device except to the server you configured.
