# Hertz-DS

**Local, agentic AI assistant for Android powered by DeepSeek.**
Your private on-device agent — no account, no cloud, no tracking.

> **Download:** **[Releases](../../releases)** → grab `Hertz-DS-<version>-debug.apk` (Android 8.0+, arm64 / armv7) and allow "Install from unknown sources".

## Features

- **Agentic loop** — DeepSeek with tool calling:
  - 🔎 Web search & page fetch
  - 📁 Local file workspace (text, JSON, CSV…)
  - 🖼️ Vision & OCR via DeepSeek OCR + image generation
  - ⏰ Background scheduled tasks (WorkManager + notifications)
  - 🧠 Long-term memory (offline FTS retrieval, `remember` / `recall`)
- **Ghost chats** — multiple isolated conversations, each with its own system prompt, model and temperature. Ghost mode is ephemeral — nothing is stored and it never touches memory.
- **Streaming + voice** — answers stream token-by-token; TTS reads them aloud sentence-by-sentence as they arrive. Offline Piper voices (Czech + English) and Whisper STT via sherpa-onnx.
- **Two voice modes:**
  - **Dictation** — record while you type, live waveform in the field; on stop the transcript is inserted into the text and waits for you to send.
  - **Call mode** — full-duplex voice chat with fog visuals, barge-in (true speech only), streaming user bubble → immediate TTS on the reply.
- **Key chain & billing** — add multiple DeepSeek keys; on 401/402/429 the agent transparently rotates to the next key. Live balance from `/user/balance`, local usage in $ and %, low-credit warning.
- **Privacy first** — chats, memory, files and encrypted keys (Android Keystore AES-GCM) never leave the device. Only model requests go to `api.deepseek.com` under your own key.

## Quick start

1. Install the APK and launch the app
2. Accept the EULA
3. Add your DeepSeek API key (Settings → Keys & Credits)
4. Start chatting — or tap the mic for dictation / call mode

## Build from source

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17+. The sherpa-onnx runtime is fetched automatically on first build; voice models are downloaded on demand inside the app (nothing is bundled in the APK).

## Architecture

```
com.hertzds
├── deepseek/      SSE client, tool-call accumulation, peak/off-peak pricing
├── agent/         agentic loop + tool registry (web/files/OCR/memory/schedule/time)
├── voice/         system + sherpa-onnx (Piper / Whisper / VAD), downloader, fog & waveform
├── work/          WorkManager for scheduled tasks
├── data/          Room (chats, messages, FTS memory, keys, usage) + DataStore
├── core/crypto/   Android Keystore SecretStore
└── ui/            Compose UI — Material 3 Expressive, pure-black canvas
```

## Links

- **kuclab.org** — https://kuclab.org
- Legal documents — see Settings → Legal & Privacy inside the app
- Check for updates — Settings → About → Check for updates

---
License: MIT
