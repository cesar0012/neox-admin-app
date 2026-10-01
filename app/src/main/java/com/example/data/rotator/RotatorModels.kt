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

/**
 * Sistema de calidad de modelos: clasifica cada modelo en tiers para que el rotador
 * SIEMPRE prefiera LLMs capaces de procesos agénticos y jamás rote a modelos mini/nano
 * que confunden instrucciones (borrar en vez de re-agendar, etc.).
 *
 *  - Tier 2 (FUERTE): modelos frontier o de ≥27B de familias probadas → factor x1.6
 *  - Tier 1 (MEDIO):  modelos medianos razonables → factor x1.0
 *  - Tier 0 (DÉBIL):  minis/nanos/≤9B y variantes lite → factor x0.25 y se excluyen
 *                     del catálogo dinámico mientras existan opciones de tier ≥1.
 */
object ModelQuality {

    private val STRONG = Regex(
        "deepseek[-_ ]?(r1|v3|chat|reasoner)|llama-?(3\\.3|4)[-.]?70b|llama-4-(maverick|scout)|qwen[23]?[-_:.]?.*(72b|235b|32b|30b-a3b)|(gpt-oss)-(120b|20b|32b)|nemotron[-_ ]?(70|ultra|super)|mistral-(large|medium|small3|small-3)|gemma-3-(27b|12b)|kimi-(k2|dev)|glm-4|command-a|marco|granite-[34]\\d|phi-4|(gemini|claude|gpt-4|gpt-5|o[34])[-.]",
        RegexOption.IGNORE_CASE
    )

    private val WEAK = Regex(
        "mini|nano|lite|tiny|flash-lite|\\bmini\\b|\\b[1-9]b\\b|\\b(1\\.5|0\\.5|1|2|3|4|5|6|7|8|9)b\\b|small|distil|8x7b|4b-instruct|hybrid-\\d",
        RegexOption.IGNORE_CASE
    )

    const val TIER_STRONG = 2
    const val TIER_MID = 1
    const val TIER_WEAK = 0

    fun tierOf(modelId: String): Int {
        val id = modelId.lowercase()
        return when {
            STRONG.containsMatchIn(id) && !WEAK.containsMatchIn(id) -> TIER_STRONG
            WEAK.containsMatchIn(id) -> TIER_WEAK
            else -> TIER_MID
        }
    }

    /** Multiplicador aplicado al score del candidato en la selección. */
    fun factorOf(modelId: String): Double = when (tierOf(modelId)) {
        TIER_STRONG -> 1.6
        TIER_WEAK -> 0.25
        else -> 1.0
    }
}

data class RotatorConfig(
    var enabled: Boolean = true,
    var openRouterApiKey: String = "",
    var nvidiaApiKey: String = "",
    var groqApiKey: String = "",
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
