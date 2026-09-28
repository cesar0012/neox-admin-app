package com.example.data.rotator

enum class ModelLane {
    GENERAL,
    CODING,
    REASONING,
    VISION
}

enum class FailureType {
    QUOTA_TRANSIENT,
    MODEL_UNAVAILABLE_DEAD,
    OTHER
}

data class BenchmarkResult(
    val coding: Boolean = false,
    val reasoning: Boolean = false,
    val latencyMs: Long = 0L,
    val score: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

data class ModelCandidate(
    val modelKey: String, // e.g. "openrouter::meta-llama/llama-3.3-70b-instruct:free" or "nvidia::deepseek-ai/deepseek-r1"
    val provider: String, // "openrouter", "nvidia", "localhost"
    val modelId: String,
    val contextLength: Int = 8192,
    val lanes: Set<ModelLane> = setOf(ModelLane.GENERAL),
    val heuristicScore: Double = 0.0,
    var benchmark: BenchmarkResult? = null,
    var cooldownUntil: Long = 0L,
    var failureCount: Int = 0,
    var successCount: Int = 0,
    var lastError: String? = null,
    var lastUsed: Long = 0L
) {
    fun isCoolingDown(): Boolean = System.currentTimeMillis() < cooldownUntil

    fun effectiveScore(): Double {
        val bench = benchmark
        val isFreshBench = bench != null && (System.currentTimeMillis() - bench.timestamp < 48 * 3600 * 1000L)
        return if (isFreshBench) {
            10000.0 + (bench?.score ?: 0)
        } else {
            heuristicScore
        }
    }
}

data class ResolvedCandidate(
    val modelKey: String,
    val provider: String,
    val modelId: String,
    val apiKey: String,
    val apiBase: String,
    val fallback: Boolean = false
)

data class RotatorConfig(
    var enabled: Boolean = true,
    var openRouterApiKey: String = "",
    var nvidiaApiKey: String = "",
    var localhostEnabled: Boolean = false,
    var localhostUrl: String = "http://localhost:11434/v1",
    var localhostModelId: String = "llama3:latest",
    var primaryProvider: String = "OPENROUTER", // OPENROUTER, NVIDIA
    var fallbackProvider: String = "NVIDIA", // NVIDIA, OPENROUTER
    var activeProviderMode: String = "ROTATOR", // ROTATOR, OPENROUTER_ONLY, NVIDIA_ONLY, LOCALHOST
    var defaultCooldownMinutes: Long = 30L,
    var deadModelCooldownHours: Long = 24L
)

data class RotatorStatus(
    val currentModelKey: String?,
    val totalCandidates: Int,
    val availableCount: Int,
    val coolingCount: Int,
    val activeProviderMode: String,
    val primaryProvider: String,
    val fallbackProvider: String,
    val candidates: List<ModelCandidate>
)
