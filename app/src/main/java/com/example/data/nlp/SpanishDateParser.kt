package com.example.data.nlp

import java.util.Calendar
import java.util.Locale

/**
 * Resuelve referencias de fecha en español natural a timestamps SIEMPRE futuros.
 * Regla de negocio: un usuario nunca agenda algo en el pasado; "viernes" significa
 * el próximo viernes por venir (aunque hoy sea viernes, se entiende el siguiente).
 */
object SpanishDateParser {

    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    private val weekdays = mapOf(
        "domingo" to Calendar.SUNDAY,
        "lunes" to Calendar.MONDAY,
        "martes" to Calendar.TUESDAY,
        "miercoles" to Calendar.WEDNESDAY,
        "jueves" to Calendar.THURSDAY,
        "viernes" to Calendar.FRIDAY,
        "sabado" to Calendar.SATURDAY
    )

    private val months = mapOf(
        "enero" to 0, "febrero" to 1, "marzo" to 2, "abril" to 3, "mayo" to 4, "junio" to 5,
        "julio" to 6, "agosto" to 7, "septiembre" to 8, "setiembre" to 8, "octubre" to 9,
        "noviembre" to 10, "diciembre" to 11
    )

    private val numberWords = mapOf(
        "un" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "quince" to 15, "veinte" to 20, "treinta" to 30
    )

    fun resolveDueTimestamp(text: String, nowMillis: Long = System.currentTimeMillis()): Long? {
        val lower = stripAccents(text.lowercase(Locale.getDefault()))

        // 1) Día de la semana: "viernes", "el viernes", "próximo viernes", "viernes que viene"
        val weekdayRegex = Regex(
            "\\b(?:el\\s+|este\\s+|este\\s+proximo\\s+|proximo\\s+|proxima\\s+|siguiente\\s+|la\\s+|los\\s+)?" +
                "(domingo|lunes|martes|miercoles|jueves|viernes|sabado)" +
                "(?:\\s+que\\s+viene|\\s+proximo|\\s+siguiente)?\\b"
        )
        weekdayRegex.find(lower)?.let { m ->
            weekdays[m.groupValues[1]]?.let { target -> return nextOccurrence(target, nowMillis) }
        }

        // 2) hoy / mañana / pasado mañana
        when {
            lower.contains(Regex("\\bpasado\\s+manana\\b")) -> return nowMillis + 2 * DAY
            lower.contains(Regex("\\bhoy\\b")) -> return nowMillis + 4 * HOUR
            lower.contains(Regex("\\bmanana\\b")) -> return nowMillis + DAY
        }

        // 3) "en N días" / "en N semanas" (dígitos o palabras)
        Regex(
            "\\ben\\s+(\\d+|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|quince|veinte|treinta)\\s+(dias?|semanas?)\\b"
        ).find(lower)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: numberWords[m.groupValues[1]] ?: 1
            val mult = if (m.groupValues[2].startsWith("semana")) 7L else 1L
            return nowMillis + n * mult * DAY
        }

        // 4) "la próxima semana"
        if (Regex("\\b(?:la\\s+)?proxima\\s+semana\\b").containsMatchIn(lower)) return nowMillis + 7 * DAY

        // 5) "fin de mes"
        if (lower.contains("fin de mes")) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            }
            return cal.timeInMillis.coerceAtLeast(nowMillis + HOUR)
        }

        // 6) "25 de diciembre"
        Regex(
            "\\b(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b"
        ).find(lower)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            val month = months[m.groupValues[2]] ?: return@let
            return nextYearlyDate(day, month, nowMillis)
        }

        // 7) "25/12", "25-12-2026"
        Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").find(lower)?.let { m ->
            val day = m.groupValues[1].toIntOrNull() ?: return@let
            val month = (m.groupValues[2].toIntOrNull() ?: 1) - 1
            if (month !in 0..11 || day !in 1..31) return@let
            return nextYearlyDate(day, month, nowMillis, yearHint = m.groupValues[3].toIntOrNull())
        }

        return null
    }

    /** Próxima ocurrencia estrictamente futura del día de la semana dado (hoy no cuenta). */
    private fun nextOccurrence(targetDayOfWeek: Int, nowMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            val diff = (targetDayOfWeek - get(Calendar.DAY_OF_WEEK) + 7) % 7
            add(Calendar.DAY_OF_YEAR, if (diff == 0) 7 else diff)
        }
        return cal.timeInMillis
    }

    private fun nextYearlyDate(day: Int, month: Int, nowMillis: Long, yearHint: Int? = null): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val year = yearHint?.let { if (it < 100) 2000 + it else it } ?: cal.get(Calendar.YEAR)
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, day)
        if (cal.timeInMillis < nowMillis) {
            if (yearHint != null) return null // fecha explícita en el pasado: inválida
            cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun stripAccents(s: String): String = s
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u").replace("ü", "u")
}
