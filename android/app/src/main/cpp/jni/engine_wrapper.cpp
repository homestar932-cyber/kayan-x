#include "engine_wrapper.h"
#include <android/log.h>
#include <chrono>

#define LOG_TAG "KayanNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#if KAYAN_HAS_LLAMA
#include "llama.h"
#endif

namespace kayan {

EngineWrapper::EngineWrapper() = default;

EngineWrapper::~EngineWrapper() {
    unload();
}

bool EngineWrapper::loadModel(const std::string& path, const EngineConfig& config) {
    unload();
    config_ = config;
    auto t0 = std::chrono::steady_clock::now();

#if KAYAN_HAS_LLAMA
    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = config.n_gpu_layers;
    mparams.use_mmap = config.use_mmap;
    mparams.use_mlock = config.use_mlock;

    model_ = llama_load_model_from_file(path.c_str(), mparams);
    if (!model_) {
        LOGE("Failed to load model: %s", path.c_str());
        return false;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = config.n_ctx;
    cparams.n_threads = config.n_threads;
    cparams.n_batch = config.n_batch;

    ctx_ = llama_new_context_with_model(static_cast<llama_model*>(model_), cparams);
    if (!ctx_) {
        LOGE("Failed to create context");
        llama_free_model(static_cast<llama_model*>(model_));
        model_ = nullptr;
        return false;
    }

    loaded_ = true;
    auto t1 = std::chrono::steady_clock::now();
    last_bench_.model_load_ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
    LOGI("Model loaded in %.1f ms (n_gpu_layers=%d)", last_bench_.model_load_ms, config.n_gpu_layers);
    return true;
#else
    LOGE("llama.cpp not compiled in. Run setup_llama.sh");
    (void)path;
    return false;
#endif
}

void EngineWrapper::unload() {
#if KAYAN_HAS_LLAMA
    if (ctx_) {
        llama_free(static_cast<llama_context*>(ctx_));
        ctx_ = nullptr;
    }
    if (model_) {
        llama_free_model(static_cast<llama_model*>(model_));
        model_ = nullptr;
    }
    llama_backend_free();
#endif
    loaded_ = false;
}

bool EngineWrapper::isLoaded() const { return loaded_; }

std::string EngineWrapper::complete(const std::string& prompt, int max_tokens, float temperature) {
    if (!loaded_) return "[engine not loaded]";
#if KAYAN_HAS_LLAMA
    // Minimal greedy implementation — full sampling can be expanded later
    auto* model = static_cast<llama_model*>(model_);
    auto* ctx = static_cast<llama_context*>(ctx_);

    std::vector<llama_token> tokens = llama_tokenize(ctx, prompt, true, true);
    if (tokens.empty()) return "";

    llama_batch batch = llama_batch_init(512, 0, 1);
    for (size_t i = 0; i < tokens.size(); ++i) {
        llama_batch_add(batch, tokens[i], i, {0}, false);
    }
    batch.logits[batch.n_tokens - 1] = true;

    if (llama_decode(ctx, batch) != 0) {
        llama_batch_free(batch);
        return "[decode failed]";
    }

    std::string result;
    auto t0 = std::chrono::steady_clock::now();
    bool first = true;
    double first_ms = 0;

    for (int i = 0; i < max_tokens; ++i) {
        auto* logits = llama_get_logits_ith(ctx, batch.n_tokens - 1);
        // Greedy for now
        llama_token next = 0;
        float max_logit = -1e9f;
        int n_vocab = llama_n_vocab(model);
        for (int t = 0; t < n_vocab; ++t) {
            if (logits[t] > max_logit) {
                max_logit = logits[t];
                next = t;
            }
        }
        if (next == llama_token_eos(model)) break;

        char buf[256];
        int n = llama_token_to_piece(model, next, buf, sizeof(buf), 0, true);
        if (n > 0) result.append(buf, n);

        if (first) {
            auto t1 = std::chrono::steady_clock::now();
            first_ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
            first = false;
        }

        llama_batch_clear(batch);
        llama_batch_add(batch, next, tokens.size() + i, {0}, true);
        if (llama_decode(ctx, batch) != 0) break;
    }

    auto t_end = std::chrono::steady_clock::now();
    double total_ms = std::chrono::duration<double, std::milli>(t_end - t0).count();
    int generated = result.empty() ? 0 : 1; // approximate
    last_bench_.first_token_latency_ms = first_ms;
    last_bench_.tokens_per_sec = (total_ms > 0) ? (generated * 1000.0 / total_ms) : 0;

    llama_batch_free(batch);
    return result;
#else
    (void)prompt; (void)max_tokens; (void)temperature;
    return "[llama not available]";
#endif
}

void EngineWrapper::completeStream(const std::string& prompt, int max_tokens, float temperature, TokenCallback cb) {
    // For the first version we call complete and emit once.
    // Real token streaming will be added once the sampling loop is solid.
    std::string full = complete(prompt, max_tokens, temperature);
    cb(full, true);
}

int EngineWrapper::profileGpuLayers(const std::string& modelPath, int maxLayersToTry) {
    // Auto Backend Profiler: binary-search / linear probe for max stable gpu layers.
    // Starts from 0 (CPU) and climbs. On failure falls back to last good value.
    int best = 0;
    for (int layers = 0; layers <= maxLayersToTry; layers += 4) {
        EngineConfig cfg = config_;
        cfg.n_gpu_layers = layers;
        if (loadModel(modelPath, cfg)) {
            best = layers;
            // quick smoke generation
            complete("Hi", 4, 0.0f);
            unload();
        } else {
            break;
        }
    }
    // Reload best
    EngineConfig finalCfg = config_;
    finalCfg.n_gpu_layers = best;
    loadModel(modelPath, finalCfg);
    LOGI("Auto Profiler selected n_gpu_layers=%d", best);
    return best;
}

} // namespace kayan
