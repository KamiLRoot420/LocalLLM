# LocalLLM AI Server — Project Documentation

**Location:** `C:\Cherry\LocalLLM`  
**Repository:** `https://github.com/KamiLRoot420/LocalLLM.git` (remote `origin`)  
**Target release repo:** `RootsLab/LocalLLM` (injected via `BuildConfig.GITHUB_REPO`)  
**Branch:** `main`  
**Last local commit:** `8a32877` — `feat(etap): E2 kontrakty Kotlin …`

---

## 1. Project Goals & Assumptions

| Aspect | Decision |
|--------|----------|
| **Product** | Native Android app embedding `llama.cpp` with Vulkan acceleration, exposing an **OpenAI-compatible REST API** on `127.0.0.1:8080` (loopback only). |
| **Offline-first** | No telemetry, no external model downloads at runtime. Models are GGUF files picked via SAF. |
| **Architecture** | Single-process: UI (Compose) + foreground service (`LlamaServerService`) + embedded Ktor/CIO server + JNI bridge → `llama.cpp`. |
| **Concurrency** | One global `llama_context` guarded by a mutex; HTTP requests and in-app chat share the same engine. |
| **Sampling** | Implemented in C++ (temp / top-k / top-p / repetition penalty) — no `llama_sampler` chain, version-agnostic. |
| **KV-cache tracking** | Bridge maintains `g_context_tokens` / `g_recent_tokens` for accurate context-window UI. |
| **OTA updates** | GitHub Releases API → system `DownloadManager` → `FileProvider` install intent. Repo URL injected at build time via `BuildConfig.GITHUB_REPO`. |
| **Hardware probing** | `DeviceInfo` reads `ActivityManager.MemoryInfo`, inspects GGUF metadata (`GgufInspector`) to recommend safe `ngl`, `ctxSize`, `threads`. OOM guard (`validateConfigOrNull`) blocks unsafe configs. |
| **Foreground service** | Type `specialUse` + subtype `dataSync` (Android 14+). Persistent notification, `stopWithTask=false`, auto-restart on boot / package replace. |
| **ABI targets** | `arm64-v8a`, `x86_64` only. |
| **Min / Target SDK** | 28 / 35. |
| **Kotlin / Gradle** | Kotlin 1.9+, Gradle 8.9 wrapper, KSP not used. |
| **CI** | GitHub Actions (`ubuntu-latest`, JDK 17, NDK r27c, CMake 3.22.1, Vulkan SDK). Job: `./gradlew :app:assembleDebug` → upload APK artifact. |
| **Signing** | Debug only for now; release keystore to be added in **E6**. |

---

## 2. Program Description & Purpose

**LocalLLM AI Server** turns an Android device into a **local, private LLM inference server** compatible with the OpenAI Chat Completions API (`/v1/chat/completions`, streaming via SSE).  

Typical use-cases:
- Run quantised LLMs (Llama 3, Phi-3, Gemma, Qwen, etc.) on-device.
- Serve requests from a desktop client (Electron, Tauri, browser) over LAN / USB tethering / ADB reverse.
- Zero cloud dependency — data never leaves the device.
- OTA updates delivered via GitHub Releases (no Play Store).

---

## 3. Development Stages (Etapy)

| Stage | Scope | Status | Commit |
|-------|-------|--------|--------|
| **E0** | Repo init, Gradle wrapper 8.9, CI workflow, base commit | ✅ Done | `c1beef2` |
| **E1** | Kotlin syntax fixes: `UpdateManager` garbage removal, `MainActivity` `LocalContext` import | ✅ Done | `d7df5ea` |
| **E2** | Kotlin contracts: DataStore flows, `ModelRepository.get()`, `PermissionManager(Activity)`, API models (`repetition_penalty`, renamed DTOs), `ChatMessage`/`ServerConfig`/`PresetRepository` fields | ✅ Done | `8a32877` |
| **E3** | Gradle config: `buildConfig=true`, `buildConfigField(GITHUB_REPO)`, CMake path as String, ABI filters, `packaging.pickFirsts` for Vulkan libs | ✅ Done (uncommitted) | — |
| **E4** | Native layer: CMake options (`LLAMA_BUILD_*=OFF`), `llama.cpp` tag `b4585`, `native-bridge.cpp` updated to `llama_batch` decode, `llama_vocab` tokenize/piece, seed masking, `AndroidManifest` FGS `specialUse` + property, `LlamaManager` load-library guard, `DeviceInfo.validateConfigOrNull` OOM guard | ✅ Done (uncommitted) | — |
| **E5** | **CI / GitHub Actions**: push, iterate until **green build** | ⏳ **NEXT** | — |
| **E6** | Hardening / Release: keystore, signing config, OTA repo unification (`RootsLab/LocalLLM`), ProGuard/R8, final QA | ⬜ Pending | — |

---

## 4. Current Git State (as of this write)

```bash
$ git status
# Expected: modified files from E3/E4 not yet committed
# Branch: main
# Remote: origin → https://github.com/KamiLRoot420/LocalLLM.git
```

**Uncommitted key files (E3 + E4):**
```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/cpp/CMakeLists.txt
app/src/main/cpp/native-bridge.cpp
app/src/main/java/com/rootslab/localllm/LlamaManager.kt
app/src/main/java/com/rootslab/localllm/util/DeviceInfo.kt
app/src/main/java/com/rootslab/localllm/UpdateManager.kt
... (plus E2 files already committed in 8a32877)
```

---

## 5. Immediate Next Steps (E5 — CI)

1. **Commit E3 + E4**
   ```bash
   git add -A
   git commit -m "feat(etap): E3 Gradle + E4 natywny - CMake opcje, llama.cpp b4585, native-bridge, manifest, loadLibrary guard, OOM guard"
   ```
2. **Push**
   ```bash
   git push -u origin main
   ```
3. **Watch GitHub Actions** (`build.yml`) — must pass (green ✅).
4. **If red:** read logs, fix (likely CMake / Vulkan / NDK), commit, push, repeat until green.
5. **Only after green CI** proceed to **E6**.

---

## 6. Risks & Open Questions

| Risk | Mitigation |
|------|------------|
| No local JDK / Android SDK | CI is the only verification path. |
| `llama.cpp` `b4585` fetched via `FetchContent` | Requires network in CI (available). |
| Vulkan shader compilation (`glslc`) | Workflow installs LunarG Vulkan SDK, sets `VULKAN_SDK`. |
| Remote mismatch (`KamiLRoot420` vs `RootsLab`) | Align `BuildConfig.GITHUB_REPO` or transfer repo before release. |
| `local.properties` (SDK path) not committed | Correct — in `.gitignore`; CI uses `android-actions/setup-android`. |

---

## 7. Key File Index (for quick navigation)

```
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/cpp/CMakeLists.txt
app/src/main/cpp/native-bridge.cpp
app/src/main/java/com/rootslab/localllm/ApiServer.kt
app/src/main/java/com/rootslab/localllm/LlamaManager.kt
app/src/main/java/com/rootslab/localllm/LlamaServerService.kt
app/src/main/java/com/rootslab/localllm/MainActivity.kt
app/src/main/java/com/rootslab/localllm/UpdateManager.kt
app/src/main/java/com/rootslab/localllm/util/DeviceInfo.kt
app/src/main/java/com/rootslab/localllm/models/ServerConfig.kt
app/src/main/java/com/rootslab/localllm/models/ChatMessage.kt
app/src/main/java/com/rootslab/localllm/models/ApiModels.kt
app/src/main/java/com/rootslab/localllm/data/PresetRepository.kt
app/src/main/java/com/rootslab/localllm/ui/viewmodel/SettingsViewModel.kt
app/src/main/java/com/rootslab/localllm/ui/viewmodel/DashboardViewModel.kt
app/src/main/java/com/rootslab/localllm/work/WatchdogWorker.kt
.gradle/workflows/build.yml
gradle.properties
gradle/wrapper/gradle-wrapper.properties
```

---

## 8. Update Log

| Date | Author | Change |
|------|--------|--------|
| 2025-06-17 | AI Assistant | Created initial `PROJECT.md` with full context, stages E0–E6, current git state, and next actions. |

---

> **Rule:** After every completed stage or significant change, update this file (section 3, 4, 5, 8) so the next session can start instantly without context loss.