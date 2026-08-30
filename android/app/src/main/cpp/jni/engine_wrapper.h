#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <functional>

namespace kayan {

struct EngineConfig {
    int n_ctx = 4096;
    int n_threads = 6;
    int n_batch = 512;
    int n_gpu_layers = 0;   // 0 = CPU only; Auto Profiler decides real value
    bool use_mmap = true;
    bool use_mlock = false;
};

struct BenchmarkResult {
    double model_load_ms = 0;
    double tokens_per_sec = 0;
    double first_token_latency_ms = 0;
    int64_t memory_bytes = 0;
};

class EngineWrapper {
public:
    EngineWrapper();
    ~EngineWrapper();

    bool loadModel(const std::string& path, const EngineConfig& config);
    void unload();
    bool isLoaded() const;

    // Synchronous completion (caller runs on background thread)
    std::string complete(const std::string& prompt, int max_tokens, float temperature);

    // Streaming callback: (token_str, is_finished)
    using TokenCallback = std::function<void(const std::string&, bool)>;
    void completeStream(const std::string& prompt, int max_tokens, float temperature, TokenCallback cb);

    BenchmarkResult lastBenchmark() const { return last_bench_; }

    // Auto profiler helper: try increasing gpu layers until failure
    int profileGpuLayers(const std::string& modelPath, int maxLayersToTry);

private:
    void* ctx_ = nullptr;   // opaque llama_context*
    void* model_ = nullptr; // opaque llama_model*
    EngineConfig config_;
    BenchmarkResult last_bench_;
    bool loaded_ = false;
};

} // namespace kayan
