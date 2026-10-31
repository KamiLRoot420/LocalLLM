// native-bridge.cpp
// JNI bridge between the Kotlin application and the embedded llama.cpp engine.
//
// Design notes
// ------------
// * A single global engine instance is guarded by one mutex. The HTTP server and
//   the in-app chat share the same llama_context, so all access is serialised.
// * Sampling is implemented locally (temperature / top-k / top-p / repetition
//   penalty) instead of relying on llama_sampler chains. This keeps the bridge
//   source-compatible across llama.cpp revisions and avoids per-token heap
//   churn in the sampler chain.
// * The KV-cache occupancy is tracked by the bridge itself, so the UI can show
//   an accurate context-window usage indicator without depending on
//   llama_get_kv_cache_token_count (which is not present in every revision).
//
// llama.cpp tag: b4585 (API uses llama_batch for decode, llama_vocab for tokenize)

#include <jni.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <mutex>
#include <numeric>
#include <random>
#include <string>
#include <vector>

#include "llama.h"

namespace {

std::mutex g_mutex;

llama_context * g_ctx = nullptr;
llama_model * g_model = nullptr;

bool g_generating = false;
std::vector<llama_token> g_context_tokens;  // tokens currently resident in the KV cache
std::vector<llama_token> g_recent_tokens;   // rolling window used by the repetition penalty
std::mt19937 g_rng;

std::string g_last_error;

constexpr size_t kRepetitionWindow = 256;

void set_error(const std::string & message) {
    g_last_error = message;
}

void clear_error() {
    g_last_error.clear();
}

void reset_generation_state() {
    g_generating = false;
    g_context_tokens.clear();
    g_recent_tokens.clear();
}

void release_engine() {
    reset_generation_state();
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
}

// --- sampling helpers ------------------------------------------------------

void apply_repetition_penalty(std::vector<float> & logits,
                               const std::vector<llama_token> & recent,
                               float penalty) {
    if (penalty <= 1.0f) {
        return;
    }
    for (const llama_token token : recent) {
        if (token < 0 || static_cast<size_t>(token) >= logits.size()) {
            continue;
        }
        float & value = logits[token];
        value = (value > 0.0f) ? (value / penalty) : (value * penalty);
    }
}

void apply_top_k(std::vector<float> & logits, int top_k) {
    if (top_k <= 0 || static_cast<size_t>(top_k) >= logits.size()) {
        return;
    }
    std::vector<float> sorted = logits;
    std::nth_element(sorted.begin(), sorted.begin() + (top_k - 1), sorted.end(),
                     std::greater<float>());
    const float threshold = sorted[static_cast<size_t>(top_k) - 1];
    for (float & value : logits) {
        if (value < threshold) {
            value = -std::numeric_limits<float>::infinity();
        }
    }
}

void apply_top_p(std::vector<float> & logits, float top_p) {
    if (top_p <= 0.0f || top_p >= 1.0f) {
        return;
    }
    const size_t n = logits.size();
    std::vector<int> order(n);
    std::iota(order.begin(), order.end(), 0);
    std::sort(order.begin(), order.end(),
              [&logits](int a, int b) { return logits[a] > logits[b]; });

    const float max_logit = logits[order[0]];
    std::vector<float> probs(n);
    float sum = 0.0f;
    for (size_t i = 0; i < n; ++i) {
        probs[i] = std::exp(logits[order[i]] - max_logit);
        sum += probs[i];
    }
    if (sum <= 0.0f) {
        return;
    }

    float cumulative = 0.0f;
    for (size_t i = 0; i < n; ++i) {
        cumulative += probs[i] / sum;
        if (cumulative >= top_p) {
            for (size_t j = i + 1; j < n; ++j) {
                logits[order[j]] = -std::numeric_limits<float>::infinity();
            }
            break;
        }
    }
}

llama_token sample_token(std::vector<float> & logits, float temperature) {
    const size_t n = logits.size();
    if (n == 0) {
        return 0;
    }

    const auto best = std::max_element(logits.begin(), logits.end());
    if (temperature <= 0.0f) {
        return static_cast<llama_token>(std::distance(logits.begin(), best));
    }

    const float max_logit = *best;
    std::vector<float> probs(n);
    float sum = 0.0f;
    for (size_t i = 0; i < n; ++i) {
        probs[i] = std::exp((logits[i] - max_logit) / temperature);
        sum += probs[i];
    }
    if (sum <= 0.0f) {
        return static_cast<llama_token>(std::distance(logits.begin(), best));
    }

    std::uniform_real_distribution<float> dist(0.0f, sum);
    const float target = dist(g_rng);
    float cumulative = 0.0f;
    for (size_t i = 0; i < n; ++i) {
        cumulative += probs[i];
        if (target <= cumulative) {
            return static_cast<llama_token>(i);
        }
    }
    return static_cast<llama_token>(n - 1);
}

std::string token_to_piece(llama_context * ctx, llama_token token) {
    const llama_vocab * vocab = llama_model_get_vocab(llama_get_model(ctx));
    char buffer[512];
    int written = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, true);
    if (written >= 0) {
        return std::string(buffer, static_cast<size_t>(written));
    }

    // Negative return value means the buffer was too small; -written is the
    // required size (including the null terminator).
    std::vector<char> large(static_cast<size_t>(-written));
    written = llama_token_to_piece(vocab, token, large.data(), static_cast<int>(large.size()), 0, true);
    if (written < 0) {
        return {};
    }
    return std::string(large.data(), static_cast<size_t>(written));
}

std::string model_info_json() {
    if (g_model == nullptr) {
        return "{}";
    }

    char description[256] = {0};
    llama_model_desc(g_model, description, sizeof(description));

    char buffer[1024];
    const int written = std::snprintf(
        buffer, sizeof(buffer),
        "{\"description\":\"%s\",\"n_vocab\":%d,\"n_ctx_train\":%d,\"n_embd\":%d,"
        "\"n_layer\":%d,\"n_params\":%llu,\"size_bytes\":%llu,\"n_ctx\":%d}",
        description,
        static_cast<int>(llama_n_vocab(llama_model_get_vocab(g_model))),
        static_cast<int>(llama_model_n_ctx_train(g_model)),
        static_cast<int>(llama_model_n_embd(g_model)),
        static_cast<int>(llama_model_n_layer(g_model)),
        static_cast<unsigned long long>(llama_model_n_params(g_model)),
        static_cast<unsigned long long>(llama_model_size(g_model)),
        g_ctx != nullptr ? static_cast<int>(llama_n_ctx(g_ctx)) : 0);

    if (written <= 0) {
        return "{}";
    }
    return std::string(buffer, static_cast<size_t>(written));
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeInit(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    llama_backend_init();
    clear_error();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeShutdown(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_engine();
    llama_backend_free();
}

JNIEXPORT void JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeFree(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_engine();
    clear_error();
}

JNIEXPORT jboolean JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeLoadModel(
        JNIEnv * env, jobject /*thiz*/, jstring j_path,
        jint j_ngl, jint j_ctx_size, jint j_threads, jint j_seed) {

    if (j_path == nullptr) {
        set_error("model path is null");
        return JNI_FALSE;
    }

    const char * path = env->GetStringUTFChars(j_path, nullptr);
    if (path == nullptr) {
        set_error("cannot access model path");
        return JNI_FALSE;
    }
    const std::string model_path(path);
    env->ReleaseStringUTFChars(j_path, path);

    std::lock_guard<std::mutex> lock(g_mutex);

    // Explicit flush of the previous model before loading a new one.
    release_engine();
    clear_error();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = j_ngl;
    model_params.use_mmap = true;
    model_params.use_mlock = false;

    g_model = llama_load_model_from_file(model_path.c_str(), model_params);
    if (g_model == nullptr) {
        set_error("failed to load model: " + model_path);
        return JNI_FALSE;
    }

    const int ctx_size = j_ctx_size > 0 ? static_cast<int>(j_ctx_size) : 2048;
    const int threads = j_threads > 0 ? static_cast<int>(j_threads) : 4;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(ctx_size);
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    ctx_params.n_gpu_layers = j_ngl;
    ctx_params.offload_kqv = true;
    // seed is uint32_t in llama.cpp b4585; mask negative seeds
    ctx_params.seed = static_cast<uint32_t>(j_seed >= 0 ? j_seed : 0xFFFFFFFFu);

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        llama_free_model(g_model);
        g_model = nullptr;
        set_error("failed to create llama context");
        return JNI_FALSE;
    }

    g_rng.seed(ctx_params.seed);
    reset_generation_state();
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeGetContextSize(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx == nullptr) {
        return 0;
    }
    return static_cast<jint>(llama_n_ctx(g_ctx));
}

JNIEXPORT jint JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeGetTokenCount(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx == nullptr) {
        return 0;
    }
    return static_cast<jint>(g_context_tokens.size());
}

JNIEXPORT jstring JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeGetModelInfo(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string info = model_info_json();
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeGetLastError(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_last_error.empty()) {
        return nullptr;
    }
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeGenerateStart(
        JNIEnv * env, jobject /*thiz*/, jstring j_prompt,
        jfloat /*temperature*/, jint /*top_k*/, jfloat /*top_p*/, jfloat /*repeat_penalty*/) {

    if (j_prompt == nullptr) {
        set_error("prompt is null");
        return JNI_FALSE;
    }

    const char * prompt = env->GetStringUTFChars(j_prompt, nullptr);
    if (prompt == nullptr) {
        set_error("cannot access prompt");
        return JNI_FALSE;
    }
    const std::string text(prompt);
    env->ReleaseStringUTFChars(j_prompt, prompt);

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx == nullptr) {
        set_error("no model loaded");
        return JNI_FALSE;
    }

    reset_generation_state();
    clear_error();

    const llama_vocab * vocab = llama_model_get_vocab(llama_get_model(g_ctx));
    std::vector<llama_token> tokens(text.size() + 1);
    int n_tokens = llama_tokenize(vocab, text.c_str(), static_cast<int>(text.size()),
                                  tokens.data(), static_cast<int>(tokens.size()), true, true);
    if (n_tokens < 0) {
        n_tokens = -n_tokens;
        tokens.resize(n_tokens);
        n_tokens = llama_tokenize(vocab, text.c_str(), static_cast<int>(text.size()),
                                  tokens.data(), static_cast<int>(tokens.size()), true, true);
    }
    if (n_tokens <= 0) {
        set_error("prompt tokenized to an empty sequence");
        return JNI_FALSE;
    }
    tokens.resize(n_tokens);

    const int n_ctx = static_cast<int>(llama_n_ctx(g_ctx));
    if (n_tokens >= n_ctx) {
        set_error("prompt exceeds the context window");
        return JNI_FALSE;
    }

    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(g_ctx, batch) != 0) {
        set_error("llama_decode failed for the prompt");
        return JNI_FALSE;
    }

    g_context_tokens = tokens;
    g_recent_tokens.assign(tokens.end() - std::min<size_t>(tokens.size(), kRepetitionWindow),
                           tokens.end());
    g_generating = true;
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeGenerateNext(
        JNIEnv * env, jobject /*thiz*/,
        jfloat j_temperature, jint j_top_k, jfloat j_top_p, jfloat j_repeat_penalty,
        jint j_max_tokens) {

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx == nullptr || !g_generating) {
        return nullptr;
    }

    const int n_ctx = static_cast<int>(llama_n_ctx(g_ctx));
    if (static_cast<int>(g_context_tokens.size()) >= n_ctx) {
        g_generating = false;
        set_error("context window exhausted");
        return nullptr;
    }
    if (j_max_tokens > 0 && static_cast<int>(g_context_tokens.size()) >= j_max_tokens) {
        g_generating = false;
        return nullptr;
    }

    const float * raw_logits = llama_get_logits(g_ctx);
    if (raw_logits == nullptr) {
        g_generating = false;
        set_error("no logits available");
        return nullptr;
    }

    const size_t n_vocab = static_cast<size_t>(llama_n_vocab(g_model));
    std::vector<float> logits(raw_logits, raw_logits + n_vocab);

    apply_repetition_penalty(logits, g_recent_tokens, j_repeat_penalty);
    apply_top_k(logits, j_top_k);
    apply_top_p(logits, j_top_p);

    const llama_token next = sample_token(logits, j_temperature);
    if (next == llama_vocab_eos(llama_model_get_vocab(g_model))) {
        g_generating = false;
        return nullptr;
    }

    const std::string piece = token_to_piece(g_ctx, next);
    if (piece.empty()) {
        g_generating = false;
        set_error("failed to convert token to text");
        return nullptr;
    }

    llama_batch batch = llama_batch_get_one(&next, 1);
    if (llama_decode(g_ctx, batch) != 0) {
        g_generating = false;
        set_error("llama_decode failed while generating");
        return nullptr;
    }

    g_context_tokens.push_back(next);
    g_recent_tokens.push_back(next);
    if (g_recent_tokens.size() > kRepetitionWindow) {
        g_recent_tokens.erase(g_recent_tokens.begin(),
                              g_recent_tokens.begin() +
                                  static_cast<long>(g_recent_tokens.size() - kRepetitionWindow));
    }

    return env->NewStringUTF(piece.c_str());
}

JNIEXPORT void JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeGenerateStop(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_generating = false;
}

JNIEXPORT jboolean JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeIsGenerating(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return g_generating ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_rootslab_localllm_LlamaManager_nativeIsLoaded(JNIEnv * env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"