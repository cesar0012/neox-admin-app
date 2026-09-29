package com.example.data.model

/**
 * Tipos de actividad que puede registrar la agenda. Aplica a TODOS los procesos
 * agénticos: asistente conversacional, dictado de juntas y acciones LLM.
 */
object TaskTypes {
    const val TAREA = "TAREA"
    const val JUNTA = "JUNTA"
    const val LLAMADA = "LLAMADA"
    const val ENTREGA = "ENTREGA"
    const val RECORDATORIO = "RECORDATORIO"

    val ALL = listOf(TAREA, JUNTA, LLAMADA, ENTREGA, RECORDATORIO)

    fun labelOf(type: String): String = when (type) {
        JUNTA -> "Junta"
        LLAMADA -> "Llamada"
        ENTREGA -> "Entrega"
        RECORDATORIO -> "Recordatorio"
        else -> "Tarea"
    }

    fun isValid(type: String?): Boolean = type != null && type in ALL

    /** Detección heurística por palabras clave (usada cuando no hay LLM disponible). */
    fun detect(textLower: String): String = when {
        containsAny(textLower, "junta", "reunion", "reunión", "meeting", "reunirse", "reunirnos") -> JUNTA
        containsAny(textLower, "llamada", "llamar", "llámale", "teléfono", "telefono", "marcarle") -> LLAMADA
        containsAny(textLower, "entregable", "entrega", "deadline", "envío", "envio", "enviar") -> ENTREGA
        containsAny(textLower, "recuérdame", "recuerdame", "recordatorio", "no olvidar", "recuérdalo") -> RECORDATORIO
        else -> TAREA
    }

    private fun containsAny(text: String, vararg keys: String) = keys.any { text.contains(it) }
}

object ConfidenceLevels {
    const val ALTA = "ALTA"
    const val MEDIA = "MEDIA"
    const val BAJA = "BAJA"

    fun isValid(level: String?): Boolean = level != null && level in listOf(ALTA, MEDIA, BAJA)
}
