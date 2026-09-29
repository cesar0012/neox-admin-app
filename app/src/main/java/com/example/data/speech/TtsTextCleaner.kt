package com.example.data.speech

/**
 * Limpia markdown, emojis y símbolos que el motor TTS no debe leer
 * (asteriscos, numeral, pipes, bullets, emojis, URLs, etc.).
 */
object TtsTextCleaner {

    private val refRegex = Regex("\\[Ref:[^\\]]*\\]")
    private val urlRegex = Regex("https?://\\S+")
    private val codeFenceRegex = Regex("```[\\s\\S]*?```")
    private val linkRegex = Regex("\\[([^\\]]+)\\]\\([^)]*\\)")
    private val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
    private val strikethroughRegex = Regex("~~(.+?)~~")
    private val underlineRegex = Regex("__([^_]+)__")
    private val italicRegex = Regex("(?<![\\w*])\\*([^*\\n]+)\\*(?![\\w*])")
    private val headerRegex = Regex("(?m)^#{1,6}\\s*")
    private val hrRegex = Regex("(?m)^\\s*[-*_]{3,}\\s*$")
    private val bulletRegex = Regex("(?m)^\\s*[-*•·‣▪◦]\\s+")

    private val allowedExtraChars = ".,;:!?()¡¿\"'%$€+-/=<>°"

    fun clean(raw: String): String {
        var t = raw
        t = refRegex.replace(t, "")
        t = urlRegex.replace(t, " ")
        t = codeFenceRegex.replace(t, " ")
        t = linkRegex.replace(t) { it.groupValues[1] }
        t = boldRegex.replace(t) { it.groupValues[1] }
        t = strikethroughRegex.replace(t) { it.groupValues[1] }
        t = underlineRegex.replace(t) { it.groupValues[1] }
        t = italicRegex.replace(t) { it.groupValues[1] }
        t = t.replace("`", "")
        t = headerRegex.replace(t, "")
        t = hrRegex.replace(t, " ")
        t = bulletRegex.replace(t, "")
        t = t.replace(Regex("\\s*\\|\\s*"), " ")

        // Conserva solo letras, dígitos, espacios y puntuación básica pronunciable
        t = t.filter { c -> c.isLetterOrDigit() || c.isWhitespace() || c in allowedExtraChars }

        t = t.replace(Regex("[ \\t]{2,}"), " ")
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        return t.trim()
    }
}
