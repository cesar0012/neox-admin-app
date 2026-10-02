package com.example.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "job_projects")
data class JobProject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val companyOrClient: String,
    val colorHex: String = "#3B82F6", // Default Blue
    val isPrimary: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "work_tasks")
data class WorkTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val jobTag: String = "General",
    val dueTimestamp: Long,
    val reminderMinutesBefore: Int = 30,
    val isCompleted: Boolean = false,
    val status: String = "PENDIENTE", // PENDIENTE, EN_PROCESO, TERMINADO
    val priority: String = "MEDIA", // ALTA, MEDIA, BAJA
    @ColumnInfo(defaultValue = "TAREA") val taskType: String = TaskTypes.TAREA, // TAREA, JUNTA, LLAMADA, ENTREGA, RECORDATORIO
    @ColumnInfo(defaultValue = "ALTA") val confidence: String = ConfidenceLevels.ALTA, // ALTA, MEDIA, BAJA
    @ColumnInfo(defaultValue = "1") val hasTime: Boolean = true, // false = el usuario no especificó hora
    val originReference: String = "", // e.g. "Junta 28 Sep - Minuta de requerimientos"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "meeting_notes")
data class MeetingNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val jobTag: String = "General",
    val dateTimestamp: Long = System.currentTimeMillis(),
    val rawTranscript: String,
    val executiveSummary: String,
    val myActionItems: String,
    val othersActionItems: String,
    val keyDecisions: String,
    val quotesJson: String = "[]", // Grounded quotes with tags
    val vaultEntryId: Long = 0,
    val isConcluded: Boolean = false
)

/**
 * Ítem (tarea/junta/llamada/entrega/recordatorio) detectado en la transcripción de una
 * junta. Queda PENDIENTE de revisión del usuario en el pop-up de la minuta: él define
 * fechas faltantes, descarta lo que no aplique y agrega a sus tareas lo que sí quiera.
 */
@Entity(tableName = "meeting_task_items", indices = [Index("meetingId")])
data class MeetingTaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meetingId: Long,
    val title: String,
    val contextQuote: String = "",   // frase textual de la junta donde se mencionó
    val taskType: String = "TAREA",  // TAREA / JUNTA / LLAMADA / ENTREGA / RECORDATORIO
    val priority: String = "MEDIA",  // ALTA / MEDIA / BAJA
    val dueTimestamp: Long = 0,      // 0 = sin fecha: el usuario debe definirla antes de agregar
    val hasTime: Boolean = false,    // true si se detectó hora explícita
    val confidence: String = "MEDIA",
    val status: String = "PENDIENTE", // PENDIENTE / AGREGADA / DESCARTADA
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "documents")
data class DocumentItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val jobTag: String = "General",
    val category: String = "DIRECTIVA", // DIRECTIVA, ESPECIFICACION, REPORTE, CONTRATO
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "memory_chunks")
data class MemoryChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val sourceType: String, // MEETING, DOCUMENT, TASK, WEBHOOK
    val title: String,
    val jobTag: String,
    val chunkIndex: Int,
    val content: String,
    val keywords: String, // comma-separated terms for fast inverted retrieval
    val exactQuote: String,
    val dateString: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_entries")
data class VaultEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawContent: String,
    val sourceType: String, // AUDIO_RECORDING, LIVE_DICTATION, WEBHOOK, DOCUMENT
    val jobTag: String = "General",
    val timestamp: Long = System.currentTimeMillis(),
    val retentionDays: Int = 10, // Default 10 days, configurable
    val isProcessed: Boolean = true
)

/** Sesión/conversación independiente del asistente (para separar temas o proyectos). */
@Entity(tableName = "conversation_sessions")
data class ConversationSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val jobTag: String = "General", // proyecto activo al momento de crearla
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)

/** Mensaje de chat persistido, pertenece a una sesión. */
@Entity(tableName = "chat_messages", indices = [Index("sessionId")])
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: Long,
    val sender: String, // USER o ASSISTANT
    val text: String,
    val modelUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "processing_queue")
data class ProcessingQueueItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val payload: String,
    val operationType: String, // TRANSCRIBE_MEETING, SUMMARIZE_DOC, EXTRACT_TASKS
    val status: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED
    val retryCount: Int = 0,
    val lastError: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
