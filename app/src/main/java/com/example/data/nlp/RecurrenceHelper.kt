package com.example.data.nlp

import java.util.Calendar
import java.util.Locale

/**
 * Motor de eventos recurrentes: detección de recurrencia en español natural
 * y cálculo de ocurrencias futuras (roll automático al pasar o completar).
 *
 * Tipos: NINGUNA / DIARIA / SEMANAL / MENSUAL / ANUAL
 * Ancla: día de semana (LUNES..DOMINGO) para SEMANAL, día del mes (1-31) para
 * MENSUAL, "dd/mm" para ANUAL.
 */
object RecurrenceHelper {

    const val NONE = "NINGUNA"
    const val DAILY = "DIARIA"
    const val WEEKLY = "SEMANAL"
    const val MONTHLY = "MENSUAL"
    const val YEARLY = "ANUAL"
    val ALL = listOf(NONE, DAILY, WEEKLY, MONTHLY, YEARLY)

    private val WEEKDAYS = listOf("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO")
    fun labelOf(type: String): String = when (type) {
        DAILY -> "Diaria"; WEEKLY -> "Semanal"; MONTHLY -> "Mensual"; YEARLY -> "Anual"; else -> ""
    }

    fun isValid(type: String?): Boolean = type != null && type in ALL

    /** Normaliza palabras del modelo ("semanal", "cada semana", "weekly") al tipo interno. */
    fun normalize(raw: String?): String {
        val t = raw?.trim()?.uppercase(Locale.getDefault()) ?: return NONE
        return when {
            t in ALL -> t
            t.contains("DIARI") || t.contains("DAILY") || t == "CADA DIA" || t == "CADA DÍA" -> DAILY
            t.contains("SEMAN") || t.contains("WEEK") -> WEEKLY
            t.contains("MENSU") || t.contains("MONTH") -> MONTHLY
            t.contains("ANU") || t.contains("YEAR") -> YEARLY
            else -> NONE
        }
    }

    /**
     * Detección determinista de recurrencia en texto español del usuario.
     * "todos los miércoles a las 10", "cada lunes", "cada mes", "cada año",
     * "diario", "semanal", "mensual", "anual", "cada día".
     */
    fun detectFromText(lower: String): String {
        val t = lower.normalizeAccents()
        return when {
            Regex("\\b(cada\\s+d[ií]a|diario|diaria|tod[oa]s\\s+los\\s+d[ií]as|diariamente)\\b").containsMatchIn(t) -> DAILY
            Regex("\\b(tod[oa]s\\s+lo[as]\\s+(lunes|martes|miercoles|jueves|viernes|sabado|domingo)|cada\\s+(lunes|martes|miercoles|jueves|viernes|sabado|domingo)|cada\\s+semana|semanal|semanalmente)\\b").containsMatchIn(t) -> WEEKLY
            Regex("\\b(cada\\s+mes|mensual|mensualmente|todos\\s+los\\s+meses)\\b").containsMatchIn(t) -> MONTHLY
            Regex("\\b(cada\\s+a[nñ]o|anual|anualmente|todos\\s+los\\s+a[nñ]os)\\b").containsMatchIn(t) -> YEARLY
            else -> NONE
        }
    }

    private fun String.normalizeAccents(): String =
        lowercase(Locale.getDefault())
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n")

    /** Ancla derivada de la fecha de vencimiento inicial. */
    fun anchorFor(type: String, dueTimestamp: Long): String {
        if (dueTimestamp <= 0L) return ""
        val cal = Calendar.getInstance().apply { timeInMillis = dueTimestamp }
        return when (type) {
            WEEKLY -> WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 6 else it - 2 }]
            MONTHLY -> cal.get(Calendar.DAY_OF_MONTH).toString()
            YEARLY -> String.format(Locale.US, "%02d/%02d", cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
            else -> ""
        }
    }

    private fun weekdayToCalConstant(anchor: String): Int = when (anchor) {
        "LUNES" -> Calendar.MONDAY; "MARTES" -> Calendar.TUESDAY; "MIERCOLES" -> Calendar.WEDNESDAY
        "JUEVES" -> Calendar.THURSDAY; "VIERNES" -> Calendar.FRIDAY; "SABADO" -> Calendar.SATURDAY
        else -> Calendar.SUNDAY
    }

    /**
     * Siguiente ocurrencia ESTRICTAMENTE posterior a [after] (por defecto ahora),
     * preservando la hora del vencimiento anterior. Para SEMANAL con ancla, cae
     * siempre en ese día de semana; para MENSUAL en ese día del mes (tolera fin de
     * mes); para ANUAL en el mismo dd/mm.
     */
    fun nextOccurrenceAfter(type: String, anchor: String, previousDue: Long, after: Long = System.currentTimeMillis()): Long? {
        if (type == NONE || previousDue <= 0L) return null
        val base = Calendar.getInstance().apply {
            timeInMillis = previousDue
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while (base.timeInMillis <= after && guard++ < 600) {
            when (type) {
                DAILY -> base.add(Calendar.DAY_OF_YEAR, 1)
                WEEKLY -> {
                    base.add(Calendar.DAY_OF_YEAR, 1)
                    val target = weekdayToCalConstant(anchor)
                    var g2 = 0
                    while (base.get(Calendar.DAY_OF_WEEK) != target && g2++ < 7) base.add(Calendar.DAY_OF_YEAR, 1)
                }
                MONTHLY -> {
                    base.add(Calendar.MONTH, 1)
                    val day = anchor.toIntOrNull() ?: base.get(Calendar.DAY_OF_MONTH)
                    base.set(Calendar.DAY_OF_MONTH, day.coerceIn(1, base.getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
                YEARLY -> {
                    base.add(Calendar.YEAR, 1)
                    val parts = anchor.split("/")
                    if (parts.size == 2) {
                        val d = parts[0].toIntOrNull() ?: base.get(Calendar.DAY_OF_MONTH)
                        val m = (parts[1].toIntOrNull() ?: (base.get(Calendar.MONTH) + 1)) - 1
                        base.set(Calendar.MONTH, m.coerceIn(0, 11))
                        base.set(Calendar.DAY_OF_MONTH, d.coerceIn(1, base.getActualMaximum(Calendar.DAY_OF_MONTH)))
                    }
                }
                else -> return null
            }
        }
        return if (base.timeInMillis > after) base.timeInMillis else null
    }

    /** Texto de la regla para mostrar al usuario ("🔁 Semanal · miércoles"). */
    fun displayLabel(type: String, anchor: String): String = when (type) {
        WEEKLY -> "Semanal · ${anchor.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase(Locale.getDefault()) }}"
        MONTHLY -> "Mensual · día $anchor"
        YEARLY -> "Anual · $anchor"
        DAILY -> "Diaria"
        else -> ""
    }
}
