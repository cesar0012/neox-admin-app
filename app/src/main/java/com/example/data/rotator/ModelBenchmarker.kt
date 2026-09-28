package com.example.data.rotator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ModelBenchmarker(private val httpClient: OkHttpClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun benchmarkCandidate(
        candidate: ModelCandidate,
        apiKey: String,
        apiBase: String
    ): BenchmarkResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var codingPassed = false
        var reasoningPassed = false

        try {
            // 1. Coding probe: isPalindrome(s)
            val codingPrompt = "Write a function isPalindrome(s) in JavaScript that checks if a string is a palindrome, ignoring non-alphanumerics and case. Provide only the function."
            val codingResponse = executeDirectCall(candidate.modelId, codingPrompt, apiKey, apiBase, maxTokens = 400)
            if (codingResponse != null) {
                val matchesFunc = Regex("function\\s+ispalindrome", RegexOption.IGNORE_CASE).containsMatchIn(codingResponse)
                val matchesMethods = Regex("(tolowercase|replace|reverse|join)", RegexOption.IGNORE_CASE).containsMatchIn(codingResponse)
                codingPassed = matchesFunc && matchesMethods
            }

            // 2. Reasoning probe: 17 * 23
            val reasoningPrompt = "How much is 17*23? Answer with the number only."
            val reasoningResponse = executeDirectCall(candidate.modelId, reasoningPrompt, apiKey, apiBase, maxTokens = 50)
            if (reasoningResponse != null && reasoningResponse.contains("391")) {
                reasoningPassed = true
            }
        } catch (_: Exception) {
            // Handled gracefully, will result in low/zero benchmark score
        }

        val totalMs = System.currentTimeMillis() - startTime
        val latencyScore = max(0, (10 - (totalMs / 6000)).toInt())

        var totalScore = 0
        if (codingPassed) totalScore += 60
        if (reasoningPassed) totalScore += 30
        totalScore += latencyScore

        BenchmarkResult(
            coding = codingPassed,
            reasoning = reasoningPassed,
            latencyMs = totalMs,
            score = totalScore,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun executeDirectCall(
        modelId: String,
        prompt: String,
        apiKey: String,
        apiBase: String,
        maxTokens: Int
    ): String? {
        val url = if (apiBase.endsWith("/")) "${apiBase}chat/completions" else "$apiBase/chat/completions"
        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", maxTokens)
            put("temperature", 0.0)
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .addHeader("Content-Type", "application/json")

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        val response = httpClient.newCall(requestBuilder.build()).execute()
        response.use {
            if (!it.isSuccessful) return null
            val body = it.body?.string() ?: return null
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
            return message.optString("content")
        }
    }
}
