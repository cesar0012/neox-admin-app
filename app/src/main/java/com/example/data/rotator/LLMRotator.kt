package com.example.data.rotator

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

class LLMRotator private constructor(private val context: Context) {

    private val tag = "LLMRotator"
    private val prefs: SharedPreferences = context.getSharedPreferences("omniwork_rotator_state", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val benchmarker = ModelBenchmarker(httpClient)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val catalogMutex = Mutex()
    private val candidateList = mutableListOf<ModelCandidate>()
    private var lastCatalogFetchTime: Long = 0L
    private val catalogTtlMs: Long = 10 * 60 * 1000L // 10 minutes TTL

    var config = RotatorConfig()
        private set

    private val _statusFlow: MutableStateFlow<RotatorStatus>
    val statusFlow: StateFlow<RotatorStatus>

    init {
        loadConfig()
        initCuratedCatalog()
        loadPersistedState()
        _statusFlow = MutableStateFlow(getStatusInternal())
        statusFlow = _statusFlow.asStateFlow()
    }

    companion object {
        @Volatile
        private var INSTANCE: LLMRotator? = null

        fun getInstance(context: Context): LLMRotator {
            return INSTANCE ?: synchronized(this) {
                val instance = LLMRotator(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Cleans out any internal reasoning, chain-of-thought and thinking tokens
         * (e.g. from DeepSeek-R1, reasoning models) so they never appear to the user.
         */
        fun cleanModelResponse(raw: String): String {
            if (raw.isBlank()) return ""
            var cleaned = raw
            // 1. Remove complete <think>...</think> blocks
            cleaned = cleaned.replace(Regex("(?s)<think>.*?</think>"), "")
            // 2. Remove open <think> if truncated
            if (cleaned.contains("<think>", ignoreCase = true)) {
                val thinkIdx = cleaned.indexOf("<think>", ignoreCase = true)
                val endThinkIdx = cleaned.indexOf("</think>", ignoreCase = true)
                cleaned = if (endThinkIdx != -1) {
                    cleaned.removeRange(thinkIdx, endThinkIdx + 8)
                } else {
                    cleaned.substring(0, thinkIdx)
                }
            }
            cleaned = cleaned.replace("</think>", "", ignoreCase = true)
            // 3. Remove <thought>...</thought>
            cleaned = cleaned.replace(Regex("(?s)<thought>.*?</thought>"), "")
            cleaned = cleaned.replace("</thought>", "", ignoreCase = true)
            // 4. Remove markdown thought blocks
            cleaned = cleaned.replace(Regex("(?s)```thought.*?```"), "")
            cleaned = cleaned.replace(Regex("(?s)\\[Thinking:.*?\\]"), "")
            cleaned = cleaned.replace(Regex("(?s)\\[Thought Process:.*?\\]"), "")
            return cleaned.trim()
        }
    }

    private fun loadConfig() {
        config = RotatorConfig(
            enabled = prefs.getBoolean("rotator_enabled", true),
            openRouterApiKey = prefs.getString("openrouter_api_key", "") ?: "",
            nvidiaApiKey = prefs.getString("nvidia_api_key", "") ?: "",
            groqApiKey = prefs.getString("groq_api_key", "") ?: "",
            localhostEnabled = prefs.getBoolean("localhost_enabled", false),
            localhostUrl = prefs.getString("localhost_url", "http://localhost:11434/v1") ?: "http://localhost:11434/v1",
            localhostModelId = prefs.getString("localhost_model_id", "llama3:latest") ?: "llama3:latest",
            primaryProvider = prefs.getString("primary_provider", "OPENROUTER") ?: "OPENROUTER",
            fallbackProvider = prefs.getString("fallback_provider", "GROQ") ?: "GROQ",
            activeProviderMode = prefs.getString("active_provider_mode", "ROTATOR") ?: "ROTATOR",
            defaultCooldownMinutes = prefs.getLong("default_cooldown_min", 30L),
            deadModelCooldownHours = prefs.getLong("dead_model_cooldown_hr", 24L)
        )
    }

    fun updateConfig(
        openRouterKey: String? = null,
        nvidiaKey: String? = null,
        groqKey: String? = null,
        localhostEnabled: Boolean? = null,
        localhostUrl: String? = null,
        localhostModelId: String? = null,
        primaryProvider: String? = null,
        fallbackProvider: String? = null,
        activeMode: String? = null
    ) {
        val editor = prefs.edit()
        openRouterKey?.let {
            config.openRouterApiKey = it.trim()
            editor.putString("openrouter_api_key", config.openRouterApiKey)
        }
        nvidiaKey?.let {
            config.nvidiaApiKey = it.trim()
            editor.putString("nvidia_api_key", config.nvidiaApiKey)
        }
        groqKey?.let {
            config.groqApiKey = it.trim()
            editor.putString("groq_api_key", config.groqApiKey)
        }
        localhostEnabled?.let {
            config.localhostEnabled = it
            editor.putBoolean("localhost_enabled", it)
        }
        localhostUrl?.let {
            config.localhostUrl = it.trim()
            editor.putString("localhost_url", config.localhostUrl)
        }
        localhostModelId?.let {
            config.localhostModelId = it.trim()
            editor.putString("localhost_model_id", config.localhostModelId)
        }
        primaryProvider?.let {
            config.primaryProvider = it
            editor.putString("primary_provider", it)
        }
        fallbackProvider?.let {
            config.fallbackProvider = it
            editor.putString("fallback_provider", it)
        }
        activeMode?.let {
            config.activeProviderMode = it
            editor.putString("active_provider_mode", it)
        }
        editor.apply()
        notifyStatusChanged()
    }

    private fun initCuratedCatalog() {
        if (candidateList.isEmpty()) {
            val curated = listOf(
                // OpenRouter free models (solo tiers fuertes/medios)
                createCandidate("openrouter", "deepseek/deepseek-r1:free", 64000, setOf(ModelLane.GENERAL, ModelLane.REASONING, ModelLane.CODING), 100.0),
                createCandidate("openrouter", "deepseek/deepseek-chat-v3-0324:free", 64000, setOf(ModelLane.GENERAL, ModelLane.CODING), 95.0),
                createCandidate("openrouter", "qwen/qwen-2.5-coder-32b-instruct:free", 32000, setOf(ModelLane.GENERAL, ModelLane.CODING), 92.0),
                createCandidate("openrouter", "meta-llama/llama-3.3-70b-instruct:free", 128000, setOf(ModelLane.GENERAL, ModelLane.REASONING), 88.0),
                createCandidate("openrouter", "google/gemma-3-27b-it:free", 96000, setOf(ModelLane.GENERAL), 80.0),
                createCandidate("openrouter", "openrouter/free", 8192, setOf(ModelLane.GENERAL), 20.0),

                // NVIDIA NIM frontier free models
                createCandidate("nvidia", "deepseek-ai/deepseek-r1", 64000, setOf(ModelLane.GENERAL, ModelLane.REASONING, ModelLane.CODING), 99.0),
                createCandidate("nvidia", "meta/llama-3.3-70b-instruct", 128000, setOf(ModelLane.GENERAL, ModelLane.REASONING), 90.0),
                createCandidate("nvidia", "nvidia/llama-3.1-nemotron-70b-instruct", 128000, setOf(ModelLane.GENERAL, ModelLane.REASONING), 89.0),
                createCandidate("nvidia", "qwen/qwen2.5-coder-32b-instruct", 32000, setOf(ModelLane.GENERAL, ModelLane.CODING), 88.0),
                createCandidate("nvidia", "mistralai/mistral-large-2-instruct", 128000, setOf(ModelLane.GENERAL), 82.0),

                // Groq (se activan al poner la API key; muy rápidos y con capa gratuita generosa)
                createCandidate("groq", "llama-3.3-70b-versatile", 128000, setOf(ModelLane.GENERAL, ModelLane.REASONING), 96.0),
                createCandidate("groq", "openai/gpt-oss-120b", 128000, setOf(ModelLane.GENERAL, ModelLane.REASONING), 97.0),
                createCandidate("groq", "openai/gpt-oss-20b", 128000, setOf(ModelLane.GENERAL, ModelLane.REASONING), 90.0),
                createCandidate("groq", "qwen/qwen3-32b", 128000, setOf(ModelLane.GENERAL, ModelLane.CODING), 89.0),
                createCandidate("groq", "moonshotai/kimi-k2-instruct", 128000, setOf(ModelLane.GENERAL), 88.0)
            )
            candidateList.addAll(curated)
        }
    }

    /**
     * Resolves the best available candidate model according to active provider preferences.
     */
    fun resolve(lane: ModelLane = ModelLane.GENERAL, exclude: Set<String> = emptySet()): ResolvedCandidate? {
        synchronized(candidateList) {
            // Localhost explicit mode
            if (config.activeProviderMode == "LOCALHOST" && config.localhostEnabled) {
                return ResolvedCandidate(
                    modelKey = "localhost::${config.localhostModelId}",
                    provider = "localhost",
                    modelId = config.localhostModelId,
                    apiKey = "",
                    apiBase = config.localhostUrl,
                    fallback = false
                )
            }

            val eligibleAll = candidateList.filter { candidate ->
                if (exclude.contains(candidate.modelKey)) return@filter false

                // If candidate is NVIDIA, only consider eligible if an API key is configured
                if (candidate.provider == "nvidia" && config.nvidiaApiKey.isBlank()) {
                    return@filter false
                }
                if (candidate.provider == "groq" && config.groqApiKey.isBlank()) {
                    return@filter false
                }

                when (config.activeProviderMode) {
                    "OPENROUTER_ONLY" -> candidate.provider == "openrouter"
                    "NVIDIA_ONLY" -> candidate.provider == "nvidia" && config.nvidiaApiKey.isNotBlank()
                    "GROQ_ONLY" -> candidate.provider == "groq" && config.groqApiKey.isNotBlank()
                    "LOCALHOST" -> candidate.provider == "localhost" && config.localhostEnabled
                    else -> { // ROTATOR mode
                        when (candidate.provider) {
                            "openrouter" -> true
                            "nvidia" -> config.nvidiaApiKey.isNotBlank()
                            "groq" -> config.groqApiKey.isNotBlank()
                            "localhost" -> config.localhostEnabled
                            else -> true
                        }
                    }
                }
            }

            // FILTRO DE CALIDAD: mientras exista al menos un modelo aceptable (tier >= 1),
            // los modelos débiles (mini/nano/<=9b) jamás participan en la rotación.
            val eligible = eligibleAll
                .filter { ModelQuality.tierOf(it.modelId) >= ModelQuality.TIER_MID }
                .ifEmpty { eligibleAll }

            if (eligible.isEmpty()) return null

            // Prioritize primary provider first, then fallback provider; el score se
            // multiplica por el factor de calidad del modelo (fuertes primero SIEMPRE)
            fun providerWeight(c: ModelCandidate): Double {
                val base = c.effectiveScore() * ModelQuality.factorOf(c.modelId)
                val providerBonus = when (c.provider.uppercase()) {
                    config.primaryProvider.uppercase() -> 200.0
                    config.fallbackProvider.uppercase() -> 50.0
                    else -> 0.0
                }
                return base + providerBonus
            }

            // 1. In lane and healthy
            val healthyInLane = eligible.filter { it.lanes.contains(lane) && !it.isCoolingDown() }
                .sortedByDescending { providerWeight(it) }

            if (healthyInLane.isNotEmpty()) {
                return toResolvedCandidate(healthyInLane.first(), fallback = false)
            }

            // 2. Lane widening: any healthy candidate
            val healthyGeneral = eligible.filter { !it.isCoolingDown() }
                .sortedByDescending { providerWeight(it) }

            if (healthyGeneral.isNotEmpty()) {
                return toResolvedCandidate(healthyGeneral.first(), fallback = true)
            }

            // 3. Fallback: candidate whose cooldown expires earliest
            val earliest = eligible.minByOrNull { it.cooldownUntil }
            return earliest?.let { toResolvedCandidate(it, fallback = true) }
        }
    }

    private fun toResolvedCandidate(candidate: ModelCandidate, fallback: Boolean): ResolvedCandidate {
        val (apiKey, apiBase) = when (candidate.provider) {
            "nvidia" -> Pair(config.nvidiaApiKey, "https://integrate.api.nvidia.com/v1")
            "groq" -> Pair(config.groqApiKey, "https://api.groq.com/openai/v1")
            "localhost" -> Pair("", config.localhostUrl)
            else -> {
                val key = config.openRouterApiKey
                Pair(key, "https://openrouter.ai/api/v1")
            }
        }
        return ResolvedCandidate(
            modelKey = candidate.modelKey,
            provider = candidate.provider,
            modelId = candidate.modelId,
            apiKey = apiKey,
            apiBase = apiBase,
            fallback = fallback
        )
    }

    fun reportFailure(modelKey: String, rawErrorText: String) {
        synchronized(candidateList) {
            val candidate = candidateList.find { it.modelKey == modelKey } ?: return
            val failureType = classifyFailure(rawErrorText)

            candidate.failureCount++
            candidate.lastError = rawErrorText.take(200)

            when (failureType) {
                FailureType.QUOTA_TRANSIENT -> {
                    val cooldownMs = config.defaultCooldownMinutes * 60 * 1000L
                    candidate.cooldownUntil = System.currentTimeMillis() + cooldownMs
                }
                FailureType.MODEL_UNAVAILABLE_DEAD -> {
                    val cooldownMs = config.deadModelCooldownHours * 3600 * 1000L
                    candidate.cooldownUntil = System.currentTimeMillis() + cooldownMs
                }
                FailureType.OTHER -> {}
            }
            persistState()
        }
        notifyStatusChanged()
    }

    fun reportSuccess(modelKey: String) {
        synchronized(candidateList) {
            val candidate = candidateList.find { it.modelKey == modelKey } ?: return
            candidate.successCount++
            candidate.cooldownUntil = 0L
            candidate.lastUsed = System.currentTimeMillis()
            candidate.lastError = null
            persistState()
        }
        notifyStatusChanged()
    }

    fun forceRotate(): ResolvedCandidate? {
        val current = resolve() ?: return null
        synchronized(candidateList) {
            val cand = candidateList.find { it.modelKey == current.modelKey }
            cand?.cooldownUntil = System.currentTimeMillis() + 5 * 60 * 1000L
        }
        val next = resolve()
        notifyStatusChanged()
        return next
    }

    fun resetCooldowns() {
        synchronized(candidateList) {
            for (c in candidateList) {
                c.cooldownUntil = 0L
            }
            persistState()
        }
        notifyStatusChanged()
    }

    /**
     * Execution wrapper with retry loop and lane widening.
     */
    suspend fun executeWithRotation(
        systemPrompt: String,
        userPrompt: String,
        lane: ModelLane = ModelLane.GENERAL,
        maxTokens: Int = 1200
    ): Result<String> = withContext(Dispatchers.IO) {
        val missionDeadModels = mutableSetOf<String>()
        var attempts = 0
        val maxAttempts = 5
        var lastErrorMessage = ""

        while (attempts < maxAttempts) {
            val resolved = resolve(lane = lane, exclude = missionDeadModels)
                ?: break

            attempts++
            val callResult = callChatCompletion(resolved, systemPrompt, userPrompt, maxTokens)

            if (callResult.isSuccess) {
                reportSuccess(resolved.modelKey)
                return@withContext callResult
            }

            val errorMsg = callResult.exceptionOrNull()?.message ?: "Error desconocido"
            lastErrorMessage = "${resolved.modelKey}: $errorMsg"
            reportFailure(resolved.modelKey, errorMsg)
            missionDeadModels.add(resolved.modelKey)
            Log.w(tag, "Rotator fallback (intento $attempts/$maxAttempts): Falló ${resolved.modelKey} ($errorMsg). Rotando al siguiente modelo...")
        }

        val finalError = if (lastErrorMessage.isNotBlank()) {
            "Se rotó a través de $attempts modelo(s) candidatos y no se obtuvo respuesta. Último detalle: $lastErrorMessage"
        } else {
            "No hay modelos disponibles o activos en el rotador en este momento."
        }
        Result.failure(Exception(finalError))
    }

    private fun callChatCompletion(
        resolved: ResolvedCandidate,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int
    ): Result<String> {
        return try {
            val endpoint = if (resolved.apiBase.endsWith("/")) "${resolved.apiBase}chat/completions" else "${resolved.apiBase}/chat/completions"

            val messagesArray = JSONArray().apply {
                if (systemPrompt.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            val requestBodyJson = JSONObject().apply {
                put("model", resolved.modelId)
                put("messages", messagesArray)
                put("max_tokens", maxTokens)
                put("temperature", 0.3)
            }

            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .addHeader("Content-Type", "application/json")

            if (resolved.apiKey.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer ${resolved.apiKey}")
            }
            if (resolved.provider == "openrouter") {
                requestBuilder.addHeader("HTTP-Referer", "https://omniwork.assistant")
                requestBuilder.addHeader("X-Title", "OmniWork Agentic Assistant")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val responseBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Result.failure(Exception("HTTP ${resp.code}: $responseBody"))
                }

                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return Result.failure(Exception("Respuesta vacía del modelo: $responseBody"))
                }
                val msgObj = choices.getJSONObject(0).optJSONObject("message")
                val content = msgObj?.optString("content") ?: ""
                val cleanedContent = cleanModelResponse(content)
                Result.success(cleanedContent.ifBlank { "Listo. He procesado tu solicitud." })
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Dynamic live scraping of OpenRouter free models and NVIDIA NIM models.
     */
    suspend fun refreshCatalog(force: Boolean = false): List<ModelCandidate> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCatalogFetchTime < catalogTtlMs && candidateList.isNotEmpty()) {
            return@withContext candidateList
        }

        catalogMutex.withLock {
            val newlyDiscovered = mutableListOf<ModelCandidate>()

            // 1. Scrap OpenRouter Live Free Models
            try {
                val orRequest = Request.Builder()
                    .url("https://openrouter.ai/api/v1/models")
                    .get()
                    .build()

                val orResp = httpClient.newCall(orRequest).execute()
                orResp.use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val root = JSONObject(body)
                        val dataArray = root.optJSONArray("data") ?: JSONArray()
                        val nonChat = Regex("embed|rerank|guard|safety|moderation|video|audio|tts|ocr|clip|transcri|whisper|lyria|image|flux|diffusion|speech|voice", RegexOption.IGNORE_CASE)

                        for (i in 0 until dataArray.length()) {
                            val modelObj = dataArray.optJSONObject(i) ?: continue
                            val id = modelObj.optString("id", "")
                            if (id.isBlank() || nonChat.containsMatchIn(id)) continue

                            val pricing = modelObj.optJSONObject("pricing")
                            val isZeroCost = pricing?.optString("prompt", "1") == "0" && pricing.optString("completion", "1") == "0"
                            val endsInFree = id.endsWith(":free") || id == "openrouter/free"

                            if (isZeroCost || endsInFree) {
                                val contextLen = modelObj.optInt("context_length", 8192)
                                newlyDiscovered.add(
                                    createCandidate("openrouter", id, contextLen, deriveLanes(id), computeHeuristicScore(id, contextLen))
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed scraping OpenRouter catalog: ${e.message}")
            }

            // 2. Discover NVIDIA NIM models if user configured an API key
            if (config.nvidiaApiKey.isNotBlank()) {
                try {
                    val nvRequest = Request.Builder()
                        .url("https://integrate.api.nvidia.com/v1/models")
                        .addHeader("Authorization", "Bearer ${config.nvidiaApiKey}")
                        .get()
                        .build()

                    val nvResp = httpClient.newCall(nvRequest).execute()
                    nvResp.use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val root = JSONObject(body)
                            val dataArray = root.optJSONArray("data") ?: JSONArray()
                            for (i in 0 until dataArray.length()) {
                                val modelObj = dataArray.optJSONObject(i) ?: continue
                                val id = modelObj.optString("id", "")
                                if (id.isNotBlank()) {
                                    newlyDiscovered.add(
                                        createCandidate("nvidia", id, 64000, deriveLanes(id), computeHeuristicScore(id, 64000))
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed discovering NVIDIA catalog: ${e.message}")
                }
            }

            // 3. Discover Groq models if user configured an API key (tier gratuito, muy rápidos)
            if (config.groqApiKey.isNotBlank()) {
                try {
                    val gqRequest = Request.Builder()
                        .url("https://api.groq.com/openai/v1/models")
                        .addHeader("Authorization", "Bearer ${config.groqApiKey}")
                        .get()
                        .build()

                    val gqResp = httpClient.newCall(gqRequest).execute()
                    gqResp.use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val root = JSONObject(body)
                            val dataArray = root.optJSONArray("data") ?: JSONArray()
                            for (i in 0 until dataArray.length()) {
                                val modelObj = dataArray.optJSONObject(i) ?: continue
                                val id = modelObj.optString("id", "")
                                if (id.isNotBlank()) {
                                    newlyDiscovered.add(
                                        createCandidate("groq", id, 128000, deriveLanes(id), computeHeuristicScore(id, 128000))
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed discovering Groq catalog: ${e.message}")
                }
            }

            // 4. Quality filter: fuera los modelos débiles del catálogo dinámico
            //    (se conservan solo si el catálogo completo fuera débil)
            val qualityFiltered = newlyDiscovered.filter { ModelQuality.tierOf(it.modelId) >= ModelQuality.TIER_MID }
            val catalogToUse = if (qualityFiltered.size >= 4) qualityFiltered else newlyDiscovered

            // 5. Fallback seeds if remote scrape returned empty
            if (catalogToUse.isEmpty()) {
                initCuratedCatalog()
            } else {
                synchronized(candidateList) {
                    val existingMap = candidateList.associateBy { it.modelKey }
                    for (c in catalogToUse) {
                        val prev = existingMap[c.modelKey]
                        if (prev != null) {
                            c.cooldownUntil = prev.cooldownUntil
                            c.benchmark = prev.benchmark
                            c.failureCount = prev.failureCount
                            c.successCount = prev.successCount
                            c.lastUsed = prev.lastUsed
                        }
                    }

                    candidateList.clear()
                    // Cap to 30 best ranked models (ya ponderado por calidad)
                    candidateList.addAll(catalogToUse.sortedByDescending { it.effectiveScore() }.take(30))

                    // Only include localhost if explicitly enabled
                    if (config.localhostEnabled) {
                        candidateList.add(
                            createCandidate("localhost", config.localhostModelId, 8192, setOf(ModelLane.GENERAL, ModelLane.CODING), 75.0)
                        )
                    }
                }
                lastCatalogFetchTime = now
                persistState()
            }

            notifyStatusChanged()
            candidateList
        }
    }

    suspend fun runBenchmarks(limit: Int = 4): Boolean = withContext(Dispatchers.IO) {
        val topToBenchmark = synchronized(candidateList) {
            candidateList.filter { !it.isCoolingDown() }.take(limit)
        }
        for (candidate in topToBenchmark) {
            val resolved = toResolvedCandidate(candidate, fallback = false)
            val result = benchmarker.benchmarkCandidate(candidate, resolved.apiKey, resolved.apiBase)
            synchronized(candidateList) {
                candidate.benchmark = result
            }
        }
        persistState()
        notifyStatusChanged()
        true
    }

    private fun deriveLanes(modelId: String): Set<ModelLane> {
        val lanes = mutableSetOf(ModelLane.GENERAL)
        val codingRegex = Regex("cod(e|er|ing)|codestral|devstral|starcoder|codegemma", RegexOption.IGNORE_CASE)
        val reasoningRegex = Regex("r1|thinking|reason|ultra|-pro|max|opus|omega|plus", RegexOption.IGNORE_CASE)
        val visionRegex = Regex("\\bvl\\b|vision|omni|multimodal|llava", RegexOption.IGNORE_CASE)

        if (codingRegex.containsMatchIn(modelId)) lanes.add(ModelLane.CODING)
        if (reasoningRegex.containsMatchIn(modelId)) lanes.add(ModelLane.REASONING)
        if (visionRegex.containsMatchIn(modelId)) lanes.add(ModelLane.VISION)
        return lanes
    }

    private fun computeHeuristicScore(modelId: String, contextLength: Int): Double {
        val lower = modelId.lowercase()
        val familyBonus = when {
            lower.contains("deepseek") -> 100.0
            lower.contains("qwen") || lower.contains("glm") -> 82.0
            lower.contains("kimi") || lower.contains("moonshot") -> 78.0
            lower.contains("gpt-oss") -> 76.0
            lower.contains("llama") -> 72.0
            lower.contains("nemotron") -> 66.0
            lower.contains("gemma") -> 62.0
            lower.contains("mistral") -> 58.0
            lower.contains("cohere") -> 40.0
            else -> 0.0
        }
        val contextBonus = min((contextLength / 20000.0), 30.0)
        val baseScore = familyBonus + contextBonus
        // El factor de calidad multiplica: los modelos débiles quedan al fondo del catálogo
        val withQuality = if (modelId == "openrouter/free") 20.0 else baseScore * ModelQuality.factorOf(modelId)
        return withQuality
    }

    private fun createCandidate(
        provider: String,
        modelId: String,
        contextLength: Int,
        lanes: Set<ModelLane>,
        score: Double
    ): ModelCandidate {
        return ModelCandidate(
            modelKey = "$provider::$modelId",
            provider = provider,
            modelId = modelId,
            contextLength = contextLength,
            lanes = lanes,
            heuristicScore = score
        )
    }

    fun classifyFailure(text: String): FailureType {
        if (isQuotaError(text)) return FailureType.QUOTA_TRANSIENT
        if (isModelUnavailableError(text)) return FailureType.MODEL_UNAVAILABLE_DEAD
        return FailureType.OTHER
    }

    fun isQuotaError(text: String): Boolean {
        val quotaRegex = Regex(
            "\\b429\\b|\\b503\\b|rate.?limit|too many requests|quota|insufficient|exceeded your|out of (free )?credits|no more free|\\bthrottl|overloaded|temporarily unavailable|service unavailable|try again later",
            RegexOption.IGNORE_CASE
        )
        return quotaRegex.containsMatchIn(text)
    }

    fun isModelUnavailableError(text: String): Boolean {
        val deadRegex = Regex(
            "\\b404\\b|\\b403\\b|not found|does not exist|no longer (available|exists)|unavailable|invalid model|unknown model|model .* not (available|supported)|only available to|forbidden|not (authorized|permitted|entitled)",
            RegexOption.IGNORE_CASE
        )
        return deadRegex.containsMatchIn(text)
    }

    private fun persistState() {
        try {
            val root = JSONObject()
            val entriesObj = JSONObject()
            for (c in candidateList) {
                val entry = JSONObject().apply {
                    put("failures", c.failureCount)
                    put("successes", c.successCount)
                    put("cooldown_until", c.cooldownUntil)
                    put("last_used", c.lastUsed)
                    put("last_error", c.lastError ?: "")
                    c.benchmark?.let { b ->
                        put("bench", JSONObject().apply {
                            put("coding", b.coding)
                            put("reasoning", b.reasoning)
                            put("latencyMs", b.latencyMs)
                            put("score", b.score)
                            put("timestamp", b.timestamp)
                        })
                    }
                }
                entriesObj.put(c.modelKey, entry)
            }
            root.put("entries", entriesObj)
            prefs.edit().putString("persisted_rotator_state", root.toString()).apply()
        } catch (e: Exception) {
            Log.e(tag, "Failed to persist rotator state: ${e.message}")
        }
    }

    private fun loadPersistedState() {
        val jsonStr = prefs.getString("persisted_rotator_state", null) ?: return
        try {
            val root = JSONObject(jsonStr)
            val entriesObj = root.optJSONObject("entries") ?: return
            val keys = entriesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val entry = entriesObj.getJSONObject(key)
                val c = candidateList.find { it.modelKey == key }
                if (c != null) {
                    c.failureCount = entry.optInt("failures", 0)
                    c.successCount = entry.optInt("successes", 0)
                    c.cooldownUntil = entry.optLong("cooldown_until", 0L)
                    c.lastUsed = entry.optLong("last_used", 0L)
                    c.lastError = entry.optString("last_error", null)
                    entry.optJSONObject("bench")?.let { b ->
                        c.benchmark = BenchmarkResult(
                            coding = b.optBoolean("coding"),
                            reasoning = b.optBoolean("reasoning"),
                            latencyMs = b.optLong("latencyMs"),
                            score = b.optInt("score"),
                            timestamp = b.optLong("timestamp")
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to load rotator state: ${e.message}")
        }
    }

    private fun getStatusInternal(): RotatorStatus {
        val current = resolve()?.modelKey
        val available = candidateList.count { !it.isCoolingDown() }
        val cooling = candidateList.count { it.isCoolingDown() }
        return RotatorStatus(
            currentModelKey = current,
            totalCandidates = candidateList.size,
            availableCount = available,
            coolingCount = cooling,
            activeProviderMode = config.activeProviderMode,
            primaryProvider = config.primaryProvider,
            fallbackProvider = config.fallbackProvider,
            candidates = candidateList.toList()
        )
    }

    private fun notifyStatusChanged() {
        _statusFlow.value = getStatusInternal()
    }
}
