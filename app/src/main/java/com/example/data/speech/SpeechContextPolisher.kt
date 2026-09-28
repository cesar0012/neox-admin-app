package com.example.data.speech

import java.util.Locale

object SpeechContextPolisher {

    private val phoneticReplacements = mapOf(
        Regex("(?i)\\b(clao|claud|clau)\\s*(cod|code)\\b") to "Cloud Code",
        Regex("(?i)\\b(git\\s*ja|guijab|gijab)\\b") to "GitHub",
        Regex("(?i)\\b(pi\\s*ar|piar)\\b") to "Pull Request",
        Regex("(?i)\\b(ves\\s*cod|be\\s*ese\\s*code|vs\\s*cod)\\b") to "VS Code",
        Regex("(?i)\\b(curzor|cúrsor)\\b") to "Cursor",
        Regex("(?i)\\b(web\\s*juk|guebjuc|gueb\\s*juc|güebjuc)\\b") to "Webhook",
        Regex("(?i)\\b(fron\\s*end|froten|fronten)\\b") to "Frontend",
        Regex("(?i)\\b(bac\\s*end|baquen|baquend)\\b") to "Backend",
        Regex("(?i)\\b(doquer|dokers)\\b") to "Docker",
        Regex("(?i)\\b(open\\s*ruter|openrouter)\\b") to "OpenRouter",
        Regex("(?i)\\b(en\\s*vidia|envidia)\\b") to "Nvidia NIM",
        Regex("(?i)\\b(olama|o\\s*lama)\\b") to "Ollama",
        Regex("(?i)\\b(deep\\s*sik|dipsik)\\b") to "DeepSeek",
        Regex("(?i)\\b(llama\\s*tres|yama\\s*3)\\b") to "Llama 3",
        Regex("(?i)\\b(junta\\s*tecnica|junta\\s*de\\s*tech)\\b") to "Junta Técnica"
    )

    fun polishDictation(rawText: String): String {
        var polished = rawText.trim()
        if (polished.isEmpty()) return ""

        for ((regex, replacement) in phoneticReplacements) {
            polished = regex.replace(polished, replacement)
        }

        // Capitalize first letter if needed
        if (polished.isNotEmpty() && polished[0].isLowerCase()) {
            polished = polished.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }

        return polished
    }
}
