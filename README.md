# LocalLLM AI Server

**Roots Lab** · `com.rootslab.localllm`

A native Android application that turns your phone into a fully offline,
OpenAI-compatible LLM server. The inference engine is **llama.cpp** compiled
directly into the APK through the Android NDK with **Vulkan GPU acceleration**
(`GGML_VULKAN=ON`) — no Termux, no root, no companion process.

```
http://127.0.0.1:8080/v1
```

Any client that speaks the OpenAI chat-completions protocol can talk to it:
Cherry Studio, Jan.ai, LobeChat, custom Python scripts, `curl`, …

---

## Features

| Area | Details |
| --- | --- |
| **REST API** | `POST /v1/chat/completions` (streaming + non-streaming), `GET /v1/models`, `GET /health` |
| **Streaming** | Server-Sent Events with OpenAI-compatible chunk payloads and a `data: [DONE]` terminator |
| **Engine** | llama.cpp embedded via JNI, Vulkan offload, explicit `llama_free` flush before every model swap |
| **Dashboard** | Traffic-light status (green ready / yellow loading / red stopped), one-tap *Copy API Endpoint* |
| **Onboarding** | Notifications, battery-optimisation exemption, MIUI/HyperOS autostart + battery-saver deep links |
| **Background** | Foreground service with a sticky notification, optional CPU WakeLock, WorkManager watchdog, boot receiver |
| **Model management** | SAF picker, GGUF metadata parser, hardware auto-detection and an OOM guard |
| **Parameters** | GPU offload layers, context size, CPU threads, temperature, top-k, top-p, repetition penalty, max tokens, port |
| **Presets** | Built-in *Max Performance* / *Balanced* / *Battery Saver* plus user-defined profiles |
| **Offline chat** | ChatGPT-style bubbles, live token streaming, Markdown + syntax-highlighted code blocks with *Copy code*, per-message tok/s and prompt-eval metrics, context-usage indicator, export to `.txt` / `.md` |
| **Widget** | Home-screen widget with a master START / STOP button |
| **OTA** | GitHub Releases check, `DownloadManager` download, install prompt |
| **Compliance** | Privacy policy, MIT license (app + llama.cpp), AI-generated-content disclaimer |

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│  Jetpack Compose UI (Dashboard / Chat / Settings / About)     │
│  ViewModels → StateFlow → DataStore (Preferences)             │
└───────────────┬──────────────────────────────┬───────────────┘
                │                              │
        ┌───────▼────────┐            ┌────────▼─────────┐
        │ LlamaServerService│         │  ModelRepository │
        │ (foreground)      │         │  (SAF → internal)│
        └───────┬────────┘            └──────────────────┘
                │
        ┌───────▼───────────────────────────────┐
        │ ApiServer (Ktor CIO, 127.0.0.1:8080)   │
        └───────┬───────────────────────────────┘
                │  JNI
        ┌───────▼───────────────────────────────┐
        │ native-bridge.cpp → llama.cpp + Vulkan │
        └───────────────────────────────────────┘
```

### Module layout

```
app/src/main/
├── cpp/
│   ├── CMakeLists.txt          # llama.cpp + Vulkan + ggml-vulkan
│   └── native-bridge.cpp       # JNI bridge, sampling, KV accounting
├── java/com/rootslab/localllm/
│   ├── MainActivity.kt         # Compose host, navigation, SAF picker
│   ├── ApiServer.kt            # OpenAI-compatible HTTP server
│   ├── LlamaManager.kt         # typed facade over the native engine
│   ├── LlamaServerService.kt   # foreground service
│   ├── PermissionManager.kt    # runtime + MIUI/HyperOS deep links
│   ├── UpdateManager.kt        # GitHub Releases OTA
│   ├── WidgetProvider.kt       # home-screen widget
│   ├── data/                   # DataStore, presets, model repository
│   ├── di/                     # ServiceLocator
│   ├── models/                 # wire + domain models
│   ├── receiver/               # widget + boot receivers
│   ├── ui/                     # theme, screens, components, viewmodels
│   ├── util/                   # GGUF inspector, device probe, exporter
│   └── work/                   # WorkManager watchdog
└── res/                        # Material 3 resources, widget layout
```

---

## Building

### Requirements

* Android Studio Ladybug (2024.2) or newer
* JDK 17
* Android SDK 35 + NDK **r27c** or newer
* CMake 3.22.1+

### 1. Get llama.cpp

Either vendor the sources (offline-friendly):

```bash
git clone --depth 1 --branch b4585 https://github.com/ggerganov/llama.cpp.git \
    app/src/main/cpp/llama.cpp
```

…or let CMake fetch them automatically — `app/src/main/cpp/CMakeLists.txt`
falls back to `FetchContent` when `app/src/main/cpp/llama.cpp` is absent.

### 2. Build

```bash
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/`.

### 3. Run

1. Launch the app and complete the onboarding wizard.
2. Pick a `.gguf` model (it is copied into the app's private storage).
3. Open **Settings → Auto-detect safe parameters** to size the context window
   and GPU offload for your device.
4. Back on the **Dashboard**, press **START**.
5. Point your client at `http://127.0.0.1:8080/v1`.

### Quick test

```bash
curl http://127.0.0.1:8080/v1/models

curl http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"model":"local-gguf","messages":[{"role":"user","content":"Hello"}],"stream":true}'
```

---

## HyperOS / MIUI notes

MIUI and HyperOS aggressively kill background services. For reliable operation:

1. **Autostart** — enable it for LocalLLM in the Xiaomi security centre.
2. **Battery saver** — set the app policy to *No restrictions*.
3. **Battery optimisation** — grant the exemption requested by the wizard.
4. **Recents screen** — lock the app in the task switcher so the system does
   not offer to kill it.

The watchdog worker re-launches the service if the low-memory killer takes the
process down, and the boot receiver restores it after a reboot.

---

## Privacy

LocalLLM AI Server is **100 % offline**. No prompts, responses, identifiers or
telemetry leave the device. The embedded HTTP server binds exclusively to the
loopback interface and is therefore unreachable from other devices. The only
outbound request the application can ever make is the optional GitHub Releases
check in the *About* tab.

---

## License

MIT — see [LICENSE](LICENSE).

The embedded inference engine is [llama.cpp](https://github.com/ggerganov/llama.cpp),
Copyright (c) 2023-2024 The ggml authors, also MIT licensed.
