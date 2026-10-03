package com.example.data.notifications

import java.util.Locale

/**
 * Catálogo y utilidades de anticipaciones de notificación (en minutos).
 * El usuario puede elegir VARIAS por omisión (Config) y personalizarlas por tarea
 * (Agenda / ítems de junta). Vacío = solo aviso a la hora exacta.
 */
object NotificationLeads {

    /** Catálogo fijo: el índice identifica la alarma (requestCode) por tarea. */
    val CATALOG = listOf(0, 15, 30, 60, 360, 1440, 4320, 10080, 14400)

    fun label(minutes: Int): String = when (minutes) {
        0 -> "A la hora"
        15 -> "15 min antes"
        30 -> "30 min antes"
        60 -> "1 hora antes"
        360 -> "6 horas antes"
        1440 -> "1 día antes"
        4320 -> "3 días antes"
        10080 -> "1 semana antes"
        14400 -> "10 días antes"
        else -> "${minutes} min antes"
    }

    fun shortLabel(minutes: Int): String = when (minutes) {
        0 -> "hora"
        15 -> "15m"
        30 -> "30m"
        60 -> "1h"
        360 -> "6h"
        1440 -> "1d"
        4320 -> "3d"
        10080 -> "1sem"
        14400 -> "10d"
        else -> "${minutes}m"
    }

    fun isValid(minutes: Int): Boolean = minutes in CATALOG

    /** CSV -> conjunto. Vacío/nulo -> conjunto vacío (= usar omisión o solo a la hora). */
    fun parseCsv(csv: String?): Set<Int> =
        csv?.split(",", ";")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.filter { isValid(it) }
            ?.toSet()
            ?: emptySet()

    fun toCsv(set: Set<Int>): String =
        set.filter { isValid(it) }.sorted().joinToString(",")

    /** Anticipaciones efectivas de una tarea: su override o las omisión globales. */
    fun effectiveFor(taskLeadsCsv: String, globalDefaults: Set<Int>): Set<Int> {
        val own = parseCsv(taskLeadsCsv)
        return if (own.isNotEmpty()) own
        else if (globalDefaults.isNotEmpty()) globalDefaults
        else setOf(0)
    }

    /** Texto compacto para chips: "1h·1d·1sem". */
    fun summary(set: Set<Int>): String =
        set.sorted().joinToString("·") { shortLabel(it) }
            .ifEmpty { shortLabel(0) }

    /** Código de alarma estable por (tarea, anticipación) para el AlarmManager. */
    fun requestCode(taskId: Long, leadMinutes: Int): Int {
        val idx = CATALOG.indexOf(leadMinutes).coerceAtLeast(0)
        return ((taskId and 0x1FFFFF) * CATALOG.size + idx + 50_000).toInt()
    }

}
