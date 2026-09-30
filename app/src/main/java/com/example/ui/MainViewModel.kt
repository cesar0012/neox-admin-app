package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ConfidenceLevels
import com.example.data.model.ConversationSession
import com.example.data.model.DocumentItem
import com.example.data.model.JobProject
import com.example.data.model.MeetingNote
import com.example.data.model.ProcessingQueueItem
import com.example.data.model.TaskTypes
import com.example.data.model.VaultEntry
import com.example.data.model.WorkTask
import com.example.data.nlp.SpanishDateParser
import com.example.data.notifications.ReminderNotificationHelper
import com.example.data.preferences.AppPreferences
import com.example.data.rag.RAGMemoryEngine
import com.example.data.rag.RAGQueryResult
import com.example.data.rotator.LLMRotator
import com.example.data.rotator.ModelLane
import com.example.data.rotator.RotatorStatus
import com.example.data.speech.SpeechContextPolisher
import com.example.data.speech.TtsTextCleaner
import com.example.data.webhook.ChromeExtensionExporter
import com.example.data.webhook.IdeIntegrationExporter
import com.example.data.webhook.LocalWebhookServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "ASSISTANT"
    val text: String,
    val citations: List<RAGQueryResult> = emptyList(),
    val modelUsed: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val ragEngine = RAGMemoryEngine(db.memoryDao())
    val rotator = LLMRotator.getInstance(application)
    val prefs = AppPreferences(application)
    val webhookServer = LocalWebhookServer(application, db, ragEngine, port = 8765)

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    // App Navigation & Security state
    private val _isUnlocked = MutableStateFlow(prefs.getPin().isBlank())
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _selectedJobFilter = MutableStateFlow<String?>("Todos")
    val selectedJobFilter: StateFlow<String?> = _selectedJobFilter.asStateFlow()

    // Data streams
    val allJobs = db.jobProjectDao().getAllJobs().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allTasks = db.taskDao().getAllTasks().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allMeetings = db.meetingDao().getAllMeetings().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allDocuments = db.documentDao().getAllDocuments().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allVaultEntries = db.vaultDao().getAllVaultEntries().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val pendingQueue = db.queueDao().getPendingQueue().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val rotatorStatus: StateFlow<RotatorStatus> = rotator.statusFlow

    // Chat conversation
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = "ASSISTANT",
                text = "¡Hola! Soy tu asistente OmniWork. Estoy conectado a tu memoria RAG vectorial, tu bóveda segura y al rotador de modelos LLM gratuitos. ¿En qué trabajamos hoy?"
            )
        )
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isProcessingChat = MutableStateFlow(false)
    val isProcessingChat: StateFlow<Boolean> = _isProcessingChat.asStateFlow()

    // --- Sesiones de conversación (temas/proyectos separados, persistidos) ---
    private val _chatSessions = MutableStateFlow<List<ConversationSession>>(emptyList())
    val chatSessions: StateFlow<List<ConversationSession>> = _chatSessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    companion object {
        /** Cantidad de mensajes recientes inyectados en cada prompt (memoria secuencial barata). */
        const val CHAT_HISTORY_WINDOW = 12
        private const val CHAT_HISTORY_MAX_CHARS = 320
    }

    private fun defaultSessionTitle(): String =
        "Conversación del " + SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())

    private fun greetingMessage(): ChatMessage = ChatMessage(
        sender = "ASSISTANT",
        text = "¡Hola! Soy tu asistente OmniWork. Estoy conectado a tu memoria RAG vectorial, tu bóveda segura y al rotador de modelos LLM gratuitos. ¿En qué trabajamos hoy?"
    )

    private suspend fun initChatSessions() {
        try {
            var sessions = db.conversationDao().getAllSessionsSync()
            if (sessions.isEmpty()) {
                val id = db.conversationDao().insertSession(
                    ConversationSession(title = defaultSessionTitle(), jobTag = _assistantSelectedProject.value)
                )
                sessions = db.conversationDao().getAllSessionsSync()
                if (sessions.none { it.id == id } && sessions.isEmpty()) {
                    sessions = listOf(ConversationSession(id = id, title = defaultSessionTitle()))
                }
            }
            _chatSessions.value = sessions
            val active = sessions.firstOrNull() ?: return
            _activeSessionId.value = active.id
            loadSessionMessages(active.id)
        } catch (t: Throwable) {
            android.util.Log.e("MainViewModel", "initChatSessions error: ${t.message}", t)
        }
    }

    private suspend fun loadSessionMessages(sessionId: Long) {
        val stored = db.chatMsgDao().getMessagesSync(sessionId)
        _chatMessages.value = if (stored.isEmpty()) {
            listOf(greetingMessage())
        } else {
            stored.map { ChatMessage(id = it.id, sender = it.sender, text = it.text, modelUsed = it.modelUsed, timestamp = it.timestamp) }
        }
    }

    fun startNewSession() {
        viewModelScope.launch {
            try {
                val id = db.conversationDao().insertSession(
                    ConversationSession(title = defaultSessionTitle(), jobTag = _assistantSelectedProject.value)
                )
                _chatSessions.value = db.conversationDao().getAllSessionsSync()
                _activeSessionId.value = id
                _chatMessages.value = listOf(greetingMessage())
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "startNewSession error: ${t.message}", t)
            }
        }
    }

    fun switchSession(sessionId: Long) {
        if (_activeSessionId.value == sessionId) return
        viewModelScope.launch {
            try {
                _activeSessionId.value = sessionId
                loadSessionMessages(sessionId)
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "switchSession error: ${t.message}", t)
            }
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                db.chatMsgDao().deleteForSession(sessionId)
                db.conversationDao().deleteSessionById(sessionId)
                val sessions = db.conversationDao().getAllSessionsSync()
                _chatSessions.value = sessions
                if (_activeSessionId.value == sessionId) {
                    if (sessions.isNotEmpty()) {
                        _activeSessionId.value = sessions.first().id
                        loadSessionMessages(sessions.first().id)
                    } else {
                        val id = db.conversationDao().insertSession(
                            ConversationSession(title = defaultSessionTitle(), jobTag = _assistantSelectedProject.value)
                        )
                        _chatSessions.value = db.conversationDao().getAllSessionsSync()
                        _activeSessionId.value = id
                        _chatMessages.value = listOf(greetingMessage())
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "deleteSession error: ${t.message}", t)
            }
        }
    }

    // Assistant selected project filter ("Todos" or specific project)
    private val _assistantSelectedProject = MutableStateFlow("Todos")
    val assistantSelectedProject: StateFlow<String> = _assistantSelectedProject.asStateFlow()

    fun setAssistantProject(project: String) {
        _assistantSelectedProject.value = project
    }

    // Banner informativo del asistente: solo la primera vez; se puede reabrir con el botón ?
    private val _assistantBannerSeen = MutableStateFlow(prefs.isAssistantBannerSeen())
    val assistantBannerSeen: StateFlow<Boolean> = _assistantBannerSeen.asStateFlow()

    fun markAssistantBannerSeen() {
        prefs.setAssistantBannerSeen(true)
        _assistantBannerSeen.value = true
    }

    // Meeting capture state
    private val _isMeetingProcessing = MutableStateFlow(false)
    val isMeetingProcessing: StateFlow<Boolean> = _isMeetingProcessing.asStateFlow()

    private val _benchmarkingActive = MutableStateFlow(false)
    val benchmarkingActive: StateFlow<Boolean> = _benchmarkingActive.asStateFlow()

    init {
        // Initialize notifications
        try {
            ReminderNotificationHelper.initNotificationChannel(application)
        } catch (t: Throwable) {
            android.util.Log.e("MainViewModel", "Notification channel init error: ${t.message}", t)
        }

        // Seed default jobs if empty
        viewModelScope.launch {
            try {
                seedInitialJobsAndDemoData()
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "Seed initial data error: ${t.message}", t)
            }
            try {
                initChatSessions()
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "Chat sessions init error: ${t.message}", t)
            }
            try {
                webhookServer.start()
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "Webhook start error: ${t.message}", t)
            }
            try {
                rotator.refreshCatalog(force = false)
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "Rotator refresh error: ${t.message}", t)
            }
        }

        // Initialize TTS
        try {
            initTextToSpeech(application)
        } catch (t: Throwable) {
            android.util.Log.e("MainViewModel", "TTS init error: ${t.message}", t)
        }
    }

    private fun initTextToSpeech(context: Context) {
        try {
            tts = TextToSpeech(context) { status ->
                try {
                    if (status == TextToSpeech.SUCCESS) {
                        isTtsReady = true
                        applyTtsConfig()
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("MainViewModel", "TTS config error: ${t.message}", t)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainViewModel", "TextToSpeech creation error: ${t.message}", t)
        }
    }

    private fun applyTtsConfig() {
        val engine = tts ?: return
        try {
            engine.setSpeechRate(prefs.getTtsRate())
            engine.setPitch(prefs.getTtsPitch())
            val savedVoice = prefs.getTtsVoiceName()
            val match = engine.voices?.firstOrNull { it.name == savedVoice }
            if (match != null) {
                engine.voice = match
            } else {
                engine.language = Locale("es", "ES")
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainViewModel", "applyTtsConfig error: ${t.message}", t)
        }
    }

    // --- Ajustes de voz expuestos a la UI ---
    private val _ttsRate = MutableStateFlow(prefs.getTtsRate())
    val ttsRate: StateFlow<Float> = _ttsRate.asStateFlow()

    private val _ttsPitch = MutableStateFlow(prefs.getTtsPitch())
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()

    private val _ttsVoiceName = MutableStateFlow(prefs.getTtsVoiceName())
    val ttsVoiceName: StateFlow<String> = _ttsVoiceName.asStateFlow()

    fun updateTtsSettings(rate: Float? = null, pitch: Float? = null, voiceName: String? = null) {
        rate?.let { prefs.setTtsRate(it); _ttsRate.value = prefs.getTtsRate() }
        pitch?.let { prefs.setTtsPitch(it); _ttsPitch.value = prefs.getTtsPitch() }
        voiceName?.let { prefs.setTtsVoiceName(it); _ttsVoiceName.value = it }
        applyTtsConfig()
    }

    /** Voces en español disponibles: (nombre técnico, etiqueta para mostrar). */
    fun getSpanishVoiceOptions(): List<Pair<String, String>> {
        val engine = tts ?: return emptyList()
        return try {
            engine.voices
                ?.filter { it.locale.language.equals("es", ignoreCase = true) }
                ?.map { v -> v.name to "${v.name.replace("#", " ").trim()} (${v.locale})" }
                .orEmpty()
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun speak(text: String) {
        if (isTtsReady && tts != null) {
            val clean = TtsTextCleaner.clean(text)
            if (clean.isNotBlank()) {
                applyTtsConfig()
                tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance")
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    /** Aplica velocidad/tono/voz al motor TTS sin persistir (para previsualizar en ajustes). */
    fun applyTtsPreview(rate: Float, pitch: Float, voiceName: String? = null) {
        val engine = tts ?: return
        try {
            engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            engine.setPitch(pitch.coerceIn(0.5f, 2.0f))
            val target = voiceName ?: prefs.getTtsVoiceName()
            val match = if (target.isNotBlank()) engine.voices?.firstOrNull { it.name == target } else null
            if (match != null) engine.voice = match else engine.language = Locale("es", "ES")
        } catch (t: Throwable) {
            android.util.Log.e("MainViewModel", "applyTtsPreview error: ${t.message}", t)
        }
    }

    /** Reproduce una muestra de voz con la configuración actual del motor. */
    fun speakSample() {
        speak("Hola, soy tu asistente Neox Admin. Así se escucha esta voz con la velocidad y el tono que elegiste.")
    }

    /** Restaura la configuración TTS persistida (al cancelar la previsualización). */
    fun restoreTtsSettings() = applyTtsConfig()

    // ---------------------------------------------------------------------------------
    // SUB-AGENTE EXTRACTOR: convierte lenguaje conversacional en fichas de tarea
    // con título técnico (nunca literal), tipo, prioridad, fecha y confianza.
    // ---------------------------------------------------------------------------------

    data class ExtractedTaskDetails(
        val title: String,
        val description: String,
        val taskType: String,
        val priority: String,
        val dueTimestamp: Long, // 0 = sin fecha especificada
        val confidence: String,
        val viaLLM: Boolean
    )

    private fun recentHistoryText(): String {
        val history = _chatMessages.value
            .filter { it.text.isNotBlank() }
            .takeLast(CHAT_HISTORY_WINDOW + 1)
            .dropLast(1)
        if (history.isEmpty()) return ""
        return "HISTORIAL RECIENTE:\n" + history.joinToString("\n") { m ->
            val role = if (m.sender == "USER") "Usuario" else "Asistente"
            "$role: ${if (m.text.length > CHAT_HISTORY_MAX_CHARS) m.text.take(CHAT_HISTORY_MAX_CHARS) + "..." else m.text}"
        } + "\n\n"
    }

    private suspend fun extractTaskDetailsViaLLM(userText: String, activeProject: String, history: String): ExtractedTaskDetails? {
        val systemPrompt = """
            Eres un SUB-AGENTE EXTRACTOR de tareas de un asistente ejecutivo. Recibes el mensaje conversacional del usuario y produces la ficha de lo que pidió agendar.

            REGLAS DE TÍTULO (CRÍTICAS):
            - Corto (máximo 8 palabras), técnico y orientado a la acción.
            - NUNCA copies frases literales del usuario ni sus opiniones o disgusto ("no me gustó cómo..." JAMÁS va en el título).
            - Ejemplo: "No me gustó cómo quedaron los envíos de los templates HTML, agenda una junta para el viernes" → Título: "Junta: revisar templates HTML de envíos".
            - Si pide junta, inicia con "Junta:". Si es llamada, "Llamada:". Si es entrega, "Entrega:".

            REGLAS DE FECHA:
            - Devuelve la fecha en lenguaje natural tal cual se entiende ("viernes", "mañana", "en 3 días", "25/12/2026") o "sin fecha" si el usuario no mencionó ninguna. NUNCA fechas pasadas.

            Responde EXACTAMENTE con este formato (sin texto extra):
            ---TITULO---
            [título técnico corto]
            ---DESCRIPCION---
            [resumen del contexto del pedido en 1-2 líneas]
            ---TIPO---
            [TAREA o JUNTA o LLAMADA o ENTREGA o RECORDATORIO]
            ---PRIORIDAD---
            [ALTA o MEDIA o BAJA]
            ---FECHA---
            [fecha natural o "sin fecha"]
            ---CONFIANZA---
            [ALTA si el pedido fue claro; MEDIA si es ambiguo o implícito; BAJA si es muy incierto]
        """.trimIndent()

        val userPrompt = "Proyecto activo: $activeProject\n$history\nMENSAJE DEL USUARIO:\n$userText"

        val result = rotator.executeWithRotation(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            lane = ModelLane.REASONING,
            maxTokens = 400
        )
        if (!result.isSuccess) return null
        val output = LLMRotator.cleanModelResponse(result.getOrNull().orEmpty())

        fun section(marker: String, next: String): String {
            val start = output.indexOf(marker)
            if (start == -1) return ""
            val from = start + marker.length
            val end = output.indexOf(next, from).let { if (it == -1) output.length else it }
            return output.substring(from, end).trim()
        }

        val title = section("---TITULO---", "---DESCRIPCION---").replace(Regex("^[\"'\\[\\]]+|[\"'\\[\\]]+$"), "").trim()
        if (title.isBlank() || title.length > 120) return null

        val description = section("---DESCRIPCION---", "---TIPO---").ifBlank { "Agendado desde el asistente conversacional" }
        val typeRaw = section("---TIPO---", "---PRIORIDAD---").uppercase(Locale.getDefault()).trim()
        val prioRaw = section("---PRIORIDAD---", "---FECHA---").uppercase(Locale.getDefault()).trim()
        val fechaText = section("---FECHA---", "---CONFIANZA---").trim()
        val confRaw = section("---CONFIANZA---", "---FIN---").uppercase(Locale.getDefault()).trim()

        val due = resolveDateTextToTimestamp(fechaText)
        return ExtractedTaskDetails(
            title = title,
            description = description,
            taskType = if (TaskTypes.isValid(typeRaw)) typeRaw else TaskTypes.detect(userText.lowercase(Locale.getDefault())),
            priority = if (prioRaw in listOf("ALTA", "MEDIA", "BAJA")) prioRaw else "MEDIA",
            dueTimestamp = due,
            confidence = if (ConfidenceLevels.isValid(confRaw)) confRaw else ConfidenceLevels.MEDIA,
            viaLLM = true
        )
    }

    /** Texto natural de fecha -> timestamp SIEMPRE futuro; 0 cuando no hay fecha válida. */
    private fun resolveDateTextToTimestamp(fechaText: String?): Long {
        if (fechaText.isNullOrBlank()) return 0L
        val clean = fechaText.trim().lowercase(Locale.getDefault())
        if (clean == "-" || clean == "sin fecha" || clean == "sin especificar" || clean == "n/a") return 0L
        return SpanishDateParser.resolveDueTimestamp(clean) ?: 0L
    }

    /** Fallback sin LLM: extracción ingenua pero con tipo, confianza MEDIA y sin fecha si no se dijo. */
    private fun naiveTaskExtraction(inputText: String, activeProject: String): ExtractedTaskDetails {
        val lower = inputText.lowercase(Locale.getDefault())
        val rawTitle = inputText
            .replace(Regex("^(?:por\\s+favor\\s+)?(?:también\\s+te\\s+comento\\s+(?:que\\s+)?)?(?:te\\s+)?(?:crea(?:r)?|agrega(?:r)?|anota(?:r)?|guarda(?:r)?|agenda(?:r)?|recuérdame|recuerdame|necesito\\s+tener\\s+lista)\\s+(?:una\\s+|la\\s+|esta\\s+|el\\s+)?(?:tarea|recordatorio|pendiente|junta|reunión|reunion)?\\s*(?:de|que|para)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:para|el|la)\\s+(?:el\\s+|la\\s+)?(?:pr[oó]xim[oa]\\s+|siguiente\\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|mañana|manana|hoy)(?:\\s+que\\s+viene|\\s+pr[oó]xim[oa])?\\s*$", RegexOption.IGNORE_CASE), "")
            .trim().trimEnd('.', '!', '?')
        val title = rawTitle.ifBlank { "Tarea pendiente" }.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        val due = SpanishDateParser.resolveDueTimestamp(lower) ?: 0L
        return ExtractedTaskDetails(
            title = title,
            description = "Anotada mediante dictado al asistente (sin refinamiento de título por modelo)",
            taskType = TaskTypes.detect(lower),
            priority = if (lower.contains("urgente") || lower.contains("alta")) "ALTA" else if (lower.contains("baja")) "BAJA" else "MEDIA",
            dueTimestamp = due,
            confidence = ConfidenceLevels.MEDIA,
            viaLLM = false
        )
    }

    private fun formatDueForHumans(dueTimestamp: Long): String =
        if (dueTimestamp <= 0L) "sin fecha ni hora especificadas"
        else SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es", "ES")).format(Date(dueTimestamp))

    fun unlockApp(pin: String): Boolean {
        val saved = prefs.getPin()
        if (saved.isBlank() || saved == pin.trim()) {
            _isUnlocked.value = true
            return true
        }
        return false
    }

    fun lockApp() {
        if (prefs.getPin().isNotBlank()) {
            _isUnlocked.value = false
        }
    }

    fun setFilterJob(jobName: String?) {
        _selectedJobFilter.value = jobName
    }

    // --- Task Actions ---
    fun addTask(
        title: String,
        description: String,
        jobTag: String,
        dueTimestamp: Long,
        priority: String = "MEDIA",
        originRef: String = "",
        taskType: String = TaskTypes.TAREA,
        confidence: String = ConfidenceLevels.ALTA
    ) {
        viewModelScope.launch {
            val safeType = if (TaskTypes.isValid(taskType)) taskType else TaskTypes.TAREA
            val safeConfidence = if (ConfidenceLevels.isValid(confidence)) confidence else ConfidenceLevels.ALTA
            val task = WorkTask(
                title = title,
                description = description,
                jobTag = jobTag,
                dueTimestamp = dueTimestamp,
                priority = priority,
                taskType = safeType,
                confidence = safeConfidence,
                originReference = originRef
            )
            val id = db.taskDao().insertTask(task)

            // Index in RAG memory
            ragEngine.indexContent(
                sourceId = id,
                sourceType = "TASK",
                title = title,
                jobTag = jobTag,
                content = "$title: $description (Prioridad $priority${if (dueTimestamp > 0) ", Fecha: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(dueTimestamp)) else ", Sin fecha"})"
            )

            // Proactive reminder
            if (dueTimestamp > System.currentTimeMillis()) {
                val reminderTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dueTimestamp))
                ReminderNotificationHelper.showTaskReminder(
                    getApplication(),
                    id.toInt(),
                    title,
                    "Programada para las $reminderTimeStr ($priority)",
                    jobTag,
                    "Recordatorio Proactivo"
                )
            }
        }
    }

    /** Edición completa de una tarea desde la UI de agenda. */
    fun updateTaskDetails(
        task: WorkTask,
        newTitle: String,
        newDescription: String,
        newType: String,
        newPriority: String,
        newDueTimestamp: Long,
        newJobTag: String
    ) {
        viewModelScope.launch {
            val updated = task.copy(
                title = newTitle.trim(),
                description = newDescription.trim(),
                taskType = if (TaskTypes.isValid(newType)) newType else task.taskType,
                priority = newPriority,
                dueTimestamp = newDueTimestamp,
                jobTag = newJobTag
            )
            db.taskDao().updateTask(updated)
            db.memoryDao().deleteChunksBySource(updated.id, "TASK")
            ragEngine.indexContent(
                updated.id, "TASK", updated.title, updated.jobTag,
                "Tarea: ${updated.title}. Tipo: ${TaskTypes.labelOf(updated.taskType)}. ${updated.description}"
            )
        }
    }

    /** Edición de una junta/minuta (título y proyecto). */
    fun updateMeetingDetails(meeting: MeetingNote, newTitle: String, newJobTag: String) {
        viewModelScope.launch {
            val updated = meeting.copy(title = newTitle.trim(), jobTag = newJobTag)
            db.meetingDao().updateMeeting(updated)
            db.memoryDao().deleteChunksBySource(updated.id, "MEETING")
            ragEngine.indexContent(
                updated.id, "MEETING", updated.title, updated.jobTag,
                "${updated.title}\nResumen: ${updated.executiveSummary}"
            )
        }
    }

    fun toggleTaskCompletion(task: WorkTask) {
        viewModelScope.launch {
            val newCompleted = !task.isCompleted
            val newStatus = if (newCompleted) "TERMINADO" else "PENDIENTE"
            db.taskDao().updateTaskStatus(task.id, newStatus, newCompleted)
        }
    }

    fun updateTaskStatus(task: WorkTask, newStatus: String) {
        viewModelScope.launch {
            val completed = newStatus == "TERMINADO"
            db.taskDao().updateTaskStatus(task.id, newStatus, completed)
        }
    }

    fun toggleMeetingConcluded(meeting: MeetingNote) {
        viewModelScope.launch {
            db.meetingDao().setMeetingConcluded(meeting.id, !meeting.isConcluded)
        }
    }

    fun checkSmartDeadlines() {
        viewModelScope.launch {
            try {
                val tasks = allTasks.value
                ReminderNotificationHelper.checkAndTriggerIntelligentReminders(getApplication(), tasks)
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "checkSmartDeadlines execution error: ${t.message}", t)
            }
        }
    }

    fun deleteTask(task: WorkTask) {
        viewModelScope.launch {
            db.taskDao().deleteTask(task)
            db.memoryDao().deleteChunksBySource(task.id, "TASK")
        }
    }

    // --- Meeting Capture & Zero-Loss Vault ---
    fun captureMeetingAudioOrText(
        jobTag: String,
        rawTranscript: String,
        customTitle: String? = null
    ) {
        viewModelScope.launch {
            if (rawTranscript.isBlank()) return@launch
            _isMeetingProcessing.value = true

            val timestamp = System.currentTimeMillis()
            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
            val initialTitle = customTitle ?: "Junta $dateStr ($jobTag)"

            // 1. GUARANTEED ZERO LOSS: Store raw verbatim capture in Vault FIRST
            val vaultEntryId = db.vaultDao().insertVaultEntry(
                VaultEntry(
                    title = initialTitle,
                    rawContent = rawTranscript,
                    sourceType = "LIVE_DICTATION",
                    jobTag = jobTag,
                    timestamp = timestamp,
                    retentionDays = prefs.getRetentionDays()
                )
            )

            // 2. Process with LLM Rotator
            val systemPrompt = """
                Eres un asistente ejecutivo de alta precisión. Analiza la siguiente transcripción de una junta de trabajo.
                Extrae obligatoriamente:
                1. TÍTULO IDENTIFICATIVO: Corto y temático de lo que se habló.
                2. RESUMEN EJECUTIVO: Puntos clave conversados.
                3. MIS ACCIONES (del usuario): Qué le toca hacer exactamente al usuario con fechas/deadlines si se mencionan.
                4. ACCIONES DE OTROS: Qué le toca hacer a los demás participantes.
                5. DECISIONES CLAVE: Resoluciones tomadas.
                6. CITAS EXACTAS (QUOTES): Extrae fragmentos textuales importantes para respaldo fiel.
                
                Responde en formato estructurado:
                ---TITULO---
                [Título corto]
                ---RESUMEN---
                [Resumen]
                ---MIS_ACCIONES---
                [Acciones para mí]
                ---ACCIONES_OTROS---
                [Acciones para los demás]
                ---DECISIONES---
                [Decisiones]
                ---CITAS---
                [Cita 1 | Cita 2 | ...]
            """.trimIndent()

            val rotatorResult = rotator.executeWithRotation(
                systemPrompt = systemPrompt,
                userPrompt = "Transcripción de la junta ($jobTag):\n\n$rawTranscript",
                lane = ModelLane.REASONING,
                maxTokens = 1500
            )

            if (rotatorResult.isSuccess) {
                val output = rotatorResult.getOrNull().orEmpty()
                val parsed = parseMeetingOutput(output, initialTitle, rawTranscript)

                val meeting = MeetingNote(
                    title = parsed.title,
                    jobTag = jobTag,
                    dateTimestamp = timestamp,
                    rawTranscript = rawTranscript,
                    executiveSummary = parsed.summary,
                    myActionItems = parsed.myActions,
                    othersActionItems = parsed.othersActions,
                    keyDecisions = parsed.decisions,
                    quotesJson = parsed.quotesJson,
                    vaultEntryId = vaultEntryId
                )
                val meetingId = db.meetingDao().insertMeeting(meeting)

                // Index in RAG memory
                ragEngine.indexContent(
                    sourceId = meetingId,
                    sourceType = "MEETING",
                    title = parsed.title,
                    jobTag = jobTag,
                    content = "${parsed.title}\nResumen: ${parsed.summary}\nMis acciones: ${parsed.myActions}\nDecisiones: ${parsed.decisions}\nCitas: ${parsed.quotesJson}",
                    timestamp = timestamp
                )

                // If "mis acciones" contains tasks, automatically add them to tasks!
                if (parsed.myActions.isNotBlank() && parsed.myActions != "Ninguna asignada") {
                    parsed.myActions.lines().filter { it.isNotBlank() }.take(3).forEach { actionLine ->
                        val cleanAction = actionLine.replace(Regex("^[-*•0-9.]+\\s*"), "").trim()
                        if (cleanAction.isNotEmpty()) {
                            db.taskDao().insertTask(
                                WorkTask(
                                    title = cleanAction,
                                    jobTag = jobTag,
                                    dueTimestamp = System.currentTimeMillis() + 86400 * 1000L,
                                    priority = "ALTA",
                                    taskType = TaskTypes.detect(cleanAction.lowercase(Locale.getDefault())),
                                    confidence = ConfidenceLevels.MEDIA,
                                    originReference = "Junta: ${parsed.title}"
                                )
                            )
                        }
                    }
                }
            } else {
                // LLM failed or offline: queue for automatic retry, never lose information
                db.queueDao().enqueueItem(
                    ProcessingQueueItem(
                        title = initialTitle,
                        payload = rawTranscript,
                        operationType = "TRANSCRIBE_MEETING",
                        status = "PENDING",
                        lastError = rotatorResult.exceptionOrNull()?.message ?: "Desconocido"
                    )
                )

                // Still save meeting record with raw transcript
                val fallbackMeeting = MeetingNote(
                    title = "$initialTitle (Pendiente de procesar con LLM)",
                    jobTag = jobTag,
                    dateTimestamp = timestamp,
                    rawTranscript = rawTranscript,
                    executiveSummary = "Transcripción original resguardada en bóveda de seguridad. El procesamiento agéntico se reintentará automáticamente.",
                    myActionItems = "Pendiente de extracción automática",
                    othersActionItems = "Pendiente de extracción automática",
                    keyDecisions = "Pendiente de extracción automática",
                    quotesJson = "[]",
                    vaultEntryId = vaultEntryId
                )
                val mId = db.meetingDao().insertMeeting(fallbackMeeting)
                ragEngine.indexContent(mId, "MEETING", initialTitle, jobTag, rawTranscript, timestamp)
            }

            _isMeetingProcessing.value = false
        }
    }

    private data class ParsedMeeting(
        val title: String,
        val summary: String,
        val myActions: String,
        val othersActions: String,
        val decisions: String,
        val quotesJson: String
    )

    private fun parseMeetingOutput(output: String, fallbackTitle: String, raw: String): ParsedMeeting {
        fun extractSection(marker: String, nextMarker: String?): String {
            val start = output.indexOf(marker)
            if (start == -1) return ""
            val contentStart = start + marker.length
            val end = if (nextMarker != null) {
                val nextIdx = output.indexOf(nextMarker, contentStart)
                if (nextIdx != -1) nextIdx else output.length
            } else {
                output.length
            }
            return output.substring(contentStart, end).trim()
        }

        val title = extractSection("---TITULO---", "---RESUMEN---").ifBlank { fallbackTitle }
        val summary = extractSection("---RESUMEN---", "---MIS_ACCIONES---").ifBlank { "Resumen no disponible" }
        val myActions = extractSection("---MIS_ACCIONES---", "---ACCIONES_OTROS---").ifBlank { "Ninguna asignada" }
        val othersActions = extractSection("---ACCIONES_OTROS---", "---DECISIONES---").ifBlank { "Ninguna registrada" }
        val decisions = extractSection("---DECISIONES---", "---CITAS---").ifBlank { "No se registraron decisiones formales" }
        val quotes = extractSection("---CITAS---", null)

        val quotesArray = JSONArray()
        if (quotes.isNotBlank()) {
            quotes.split(Regex("[|\n]")).map { it.trim() }.filter { it.isNotBlank() }.forEach {
                quotesArray.put(it)
            }
        }
        if (quotesArray.length() == 0) {
            quotesArray.put(raw.take(120) + "...")
        }

        return ParsedMeeting(title, summary, myActions, othersActions, decisions, quotesArray.toString())
    }

    // --- Conversational RAG Assistant ---
    fun sendChatMessage(userText: String, autoSpeak: Boolean = false) {
        if (userText.isBlank()) return
        val polishedUserText = SpeechContextPolisher.polishDictation(userText)
        val userMsg = ChatMessage(sender = "USER", text = polishedUserText)
        val current = _chatMessages.value.toMutableList()
        current.add(userMsg)
        _chatMessages.value = current
        _isProcessingChat.value = true

        viewModelScope.launch {
            var responseText = ""
            var modelUsed: String? = null
            var citations: List<RAGQueryResult> = emptyList()
            try {
                val activeProject = _assistantSelectedProject.value
                val (text, model, cites) = produceChatResponse(polishedUserText, activeProject)
                responseText = text
                modelUsed = model
                citations = cites
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "Chat turn error: ${t.message}", t)
                responseText = "Ocurrió un error inesperado al procesar tu mensaje (${t.message}). Tu texto está respaldado."
                modelUsed = "Error Handler"
            }

            val assistantMsg = ChatMessage(
                sender = "ASSISTANT",
                text = responseText,
                citations = citations,
                modelUsed = modelUsed
            )
            val updated = _chatMessages.value.toMutableList()
            updated.add(assistantMsg)
            _chatMessages.value = updated
            _isProcessingChat.value = false

            persistChatTurn(userMsg, assistantMsg, polishedUserText)

            if (autoSpeak) {
                speak(responseText)
            }
        }
    }

    /** Guarda el turno en la sesión activa, indexa el intercambio en RAG y autotitula la sesión. */
    private suspend fun persistChatTurn(userMsg: ChatMessage, assistantMsg: ChatMessage, rawUserText: String) {
        val sessionId = _activeSessionId.value ?: return
        val activeProject = _assistantSelectedProject.value
        try {
            db.chatMsgDao().insert(
                ChatMessageEntity(
                    id = userMsg.id, sessionId = sessionId, sender = "USER",
                    text = userMsg.text, timestamp = userMsg.timestamp
                )
            )
            db.chatMsgDao().insert(
                ChatMessageEntity(
                    id = assistantMsg.id, sessionId = sessionId, sender = "ASSISTANT",
                    text = assistantMsg.text, modelUsed = assistantMsg.modelUsed, timestamp = assistantMsg.timestamp
                )
            )
            db.conversationDao().touchSession(sessionId, System.currentTimeMillis())

            // Autotítulo: la sesión toma el nombre del primer mensaje del usuario
            val session = db.conversationDao().getSessionById(sessionId)
            if (session != null && session.title.startsWith("Conversación del")) {
                val newTitle = rawUserText.take(32).trim().trimEnd(':', '.', ',')
                    .replaceFirstChar { it.uppercase(Locale.getDefault()) }
                    .ifBlank { session.title }
                if (newTitle != session.title) {
                    db.conversationDao().renameSession(sessionId, newTitle)
                    _chatSessions.value = db.conversationDao().getAllSessionsSync()
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainViewModel", "persistChatTurn error: ${t.message}", t)
        }

        // Indexar el intercambio en memoria RAG para contexto a largo plazo (salvo avisos del sistema)
        if (assistantMsg.modelUsed != "Neox Guard Protection") {
            try {
                val exchange = "Usuario: ${userMsg.text}\nAsistente: ${assistantMsg.text.take(600)}"
                ragEngine.indexContent(
                    sourceId = System.currentTimeMillis(),
                    sourceType = "CHAT",
                    title = userMsg.text.take(60).ifBlank { "Intercambio de conversación" },
                    jobTag = if (activeProject == "Todos") "General" else activeProject,
                    content = exchange
                )
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "RAG chat index error: ${t.message}", t)
            }
        }
    }

    private suspend fun produceChatResponse(polishedUserText: String, activeProject: String): Triple<String, String?, List<RAGQueryResult>> {
        // 1. Guard check for "Todos" mode: Protect against write/modify operations
        if (activeProject == "Todos" && isWriteOrModifyIntent(polishedUserText)) {
            val guardWarning = "⚠️ **Modo 'Todos los proyectos' Protegido:**\n\n" +
                "En este modo global únicamente se permite la consulta de información, discusión estratégica y revisión general de tus trabajos.\n\n" +
                "Para evitar confusiones y asegurar que una tarea o apunte no recaiga en un proyecto equivocado, **las acciones de guardar, modificar, concluir o borrar tareas y notas RAG están protegidas**.\n\n" +
                "👉 **Por favor, selecciona arriba el proyecto específico** que mencionaste y vuelve a enviarme este mensaje para realizar la acción de inmediato."
            return Triple(guardWarning, "Neox Guard Protection", emptyList())
        }

        // 2. Direct voice action execution (1-to-1 interactive control)
        val actionResponse = tryExecuteConversationalCommand(polishedUserText, activeProject)
        if (actionResponse != null) {
            return Triple(actionResponse, "Neox Voice Action ($activeProject)", emptyList())
        }

        // 3. Query RAG vector memory (isolated to project if specific project selected)
        val jobFilter = if (activeProject == "Todos") null else activeProject
        val relevantChunks = ragEngine.queryMemory(polishedUserText, topK = 4, jobFilter = jobFilter)

        // 4. Fetch active project tasks to give LLM full context
        val projectTasks = if (activeProject != "Todos") {
            db.taskDao().getTasksByJobSync(activeProject)
        } else {
            db.taskDao().getAllTasksSync()
        }

        val contextBuilder = StringBuilder()
        if (projectTasks.isNotEmpty()) {
            val header = if (activeProject == "Todos") "TAREAS EN TODOS LOS PROYECTOS:" else "TAREAS REGISTRADAS EN EL PROYECTO $activeProject:"
            contextBuilder.append("$header\n")
            projectTasks.take(12).forEach { t ->
                val statusStr = if (t.isCompleted) "[CONCLUIDA]" else "[PENDIENTE]"
                val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(t.dueTimestamp))
                contextBuilder.append("• ${t.title} $statusStr | Vence: $dateStr | Prioridad: ${t.priority} | Proyecto: ${t.jobTag}\n")
            }
            contextBuilder.append("\n")
        }

        if (relevantChunks.isNotEmpty()) {
            contextBuilder.append("FRAGMENTOS RELEVANTES EN MEMORIA VECTORIAL RAG:\n")
            relevantChunks.forEachIndexed { idx, res ->
                contextBuilder.append("[REF ${idx + 1}] Tipo: ${res.chunk.sourceType} | Título: \"${res.chunk.title}\" | Proyecto: ${res.chunk.jobTag}\n")
                contextBuilder.append("Cita: \"${res.chunk.exactQuote}\"\n")
                contextBuilder.append("Contenido: ${res.chunk.content}\n\n")
            }
        }

        // 5. Memoria secuencial: últimos mensajes de ESTA conversación para continuidad
        val recentHistory = _chatMessages.value
            .filter { it.text.isNotBlank() }
            .takeLast(CHAT_HISTORY_WINDOW + 1)
            .dropLast(1) // el último es el mensaje actual que ya va aparte
        val historyBlock = if (recentHistory.isNotEmpty()) {
            val lines = recentHistory.joinToString("\n") { m ->
                val role = if (m.sender == "USER") "Usuario" else "Asistente"
                val body = if (m.text.length > CHAT_HISTORY_MAX_CHARS) m.text.take(CHAT_HISTORY_MAX_CHARS) + "..." else m.text
                "$role: $body"
            }
            "HISTORIAL RECIENTE DE ESTA CONVERSACIÓN (para entender correcciones y referencias como 'esa tarea' o 'está mal'):\n$lines\n\n"
        } else {
            ""
        }

        val allJobs = db.jobProjectDao().getAllJobsSync()
        val projectNames = if (allJobs.isNotEmpty()) allJobs.joinToString(", ") { it.name } else "General"

        val projectDirectives = if (activeProject == "Todos") {
            """
                ESTÁS EN MODO 'TODOS LOS PROYECTOS':
                - Este modo es exclusivamente para consulta general, análisis estratégico y visión holística de todos los proyectos ($projectNames).
                - NO realices acciones de modificación ni guardado en este modo.
                - Si el usuario te pide guardar o agendar una tarea aquí, indícale amablemente que seleccione el proyecto en la barra superior.
            """.trimIndent()
        } else {
            """
                ESTÁS TRABAJANDO EXCLUSIVAMENTE EN EL PROYECTO: "$activeProject".
                - Todas las respuestas, análisis y contexto pertenecen a "$activeProject".
                - Usa el HISTORIAL RECIENTE para entender correcciones: si el usuario dice que algo quedó mal o da una nueva fecha, se refiere a lo hablado antes.
                - Si el usuario pide agendar algo, usa el comando con esta especificación:
                  ---ACTION:CREATE_TASK|titulo|prioridad|fechaTexto|tipo|confianza---
                  * titulo: CORTO (máx 8 palabras), técnico y orientado a acción. NUNCA frases literales del usuario ni sus opiniones ("no me gustó..." jamás va en un título). Junta → "Junta: ...".
                  * prioridad: ALTA / MEDIA / BAJA.
                  * fechaTexto: la fecha en lenguaje natural tal como la entiendes ("viernes", "mañana", "en 3 días", "25/12/2026") o "-" si el usuario NO mencionó fecha.
                  * tipo: TAREA / JUNTA / LLAMADA / ENTREGA / RECORDATORIO (según lo pedido: reunión=junta, llamada telefónica=llamada, deadline o envío=entrega).
                  * confianza: ALTA si el pedido fue claro, MEDIA si ambiguo, BAJA si muy incierto.
                - Para re-agendar/cambiar fecha (incluye cuando el usuario corrige una fecha mal agendada):
                  ---ACTION:RESCHEDULE_TASK|titulo|fechaTexto---
                - Para concluir tarea: ---ACTION:COMPLETE_TASK|titulo---
                - Para borrar tarea: ---ACTION:DELETE_TASK|titulo---
                - Para guardar nota en RAG: ---ACTION:SAVE_NOTE|titulo|contenido---
            """.trimIndent()
        }

        val nowStr = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "ES")).format(Date())

        val systemPrompt = """
            Eres Neox Admin, el asistente ejecutivo de alta precisión para gestión multiproyecto.

            $projectDirectives

            CONTEXTO TEMPORAL (REGLA CRÍTICA):
            - FECHA Y HORA ACTUAL: $nowStr.
            - NUNCA agendes nada en fechas pasadas. Cuando el usuario mencione un día de la semana ("viernes"), significa SIEMPRE el PRÓXIMO viernes FUTURO a partir de la FECHA ACTUAL; en fechaTexto escribe el día tal cual ("viernes") y el sistema calcula la fecha exacta.
            - Si el usuario no menciona fecha, usa "-" como fechaTexto: la junta/tarea queda pendiente SIN fecha (aparece en tareas, no en calendario).
            - Si el usuario corrige una fecha ("no, es para el siguiente viernes"), re-agenda la tarea con RESCHEDULE_TASK en lugar de crear una nueva.

            INSTRUCCIONES CLAVE DE FORMATO Y ESTILO:
            1. NUNCA generes ni incluyas etiquetas de razonamiento interno como <think>, </think>, o similares. Responde directamente con tu análisis o respuesta ejecutiva.
            2. Basa tus respuestas en la memoria RAG, en las tareas listadas y en el historial de la conversación.
            3. Sé proactivo, conciso, ultra-profesional y directo.
        """.trimIndent()

        val userPromptWithRag = """
            $contextBuilder
            $historyBlock
            MENSAJE DEL USUARIO:
            $polishedUserText
        """.trimIndent()

        val rotatorResult = rotator.executeWithRotation(
            systemPrompt = systemPrompt,
            userPrompt = userPromptWithRag,
            lane = ModelLane.REASONING,
            maxTokens = 1400
        )

        var rawReply = if (rotatorResult.isSuccess) {
            rotatorResult.getOrNull().orEmpty()
        } else {
            "El rotador probó los modelos disponibles pero ocurrió una falla temporal de conexión (${rotatorResult.exceptionOrNull()?.message}). Tu mensaje está respaldado."
        }

        // Guarantee zero thinking traces
        rawReply = LLMRotator.cleanModelResponse(rawReply)

        // Parse and execute LLM actions if present
        val finalReply = processLLMActions(rawReply, activeProject)

        return Triple(finalReply, rotator.resolve()?.modelKey, relevantChunks)
    }

    private suspend fun processLLMActions(reply: String, activeProject: String): String {
        val actionRegex = Regex("---ACTION:([A-Z_]+)\\|(.*?)---")
        val match = actionRegex.find(reply) ?: return reply

        val actionType = match.groupValues[1]
        val payload = match.groupValues[2]
        val cleanReply = reply.replace(match.value, "").trim()

        if (activeProject == "Todos") {
            return cleanReply + "\n\n*(Nota: En modo 'Todos los proyectos' no se aplican modificaciones. Selecciona el proyecto arriba para ejecutar la acción)*"
        }

        val confirmationText = when (actionType) {
            "CREATE_TASK" -> {
                // ---ACTION:CREATE_TASK|titulo|prioridad|fechaTexto|tipo|confianza---
                val parts = payload.split("|")
                val title = parts.getOrNull(0)?.trim()?.ifBlank { "Nueva tarea" } ?: "Nueva tarea"
                val priority = parts.getOrNull(1)?.trim()?.uppercase(Locale.getDefault()) ?: "MEDIA"
                val due = resolveDateTextToTimestamp(parts.getOrNull(2))
                val typeRaw = parts.getOrNull(3)?.trim()?.uppercase(Locale.getDefault()).orEmpty()
                val confRaw = parts.getOrNull(4)?.trim()?.uppercase(Locale.getDefault()).orEmpty()

                val task = WorkTask(
                    title = title,
                    description = "Creada vía conversación con Neox Assistant",
                    jobTag = activeProject,
                    dueTimestamp = due,
                    priority = if (priority in listOf("ALTA", "MEDIA", "BAJA")) priority else "MEDIA",
                    taskType = if (TaskTypes.isValid(typeRaw)) typeRaw else TaskTypes.TAREA,
                    confidence = if (ConfidenceLevels.isValid(confRaw)) confRaw else ConfidenceLevels.ALTA,
                    originReference = "Asistente Conversacional"
                )
                val id = db.taskDao().insertTask(task)
                ragEngine.indexContent(
                    id, "TASK", title, activeProject,
                    "Tarea: $title. Tipo: ${TaskTypes.labelOf(task.taskType)}. Proyecto: $activeProject."
                )
                "\n\n✅ *[Acción ejecutada: ${TaskTypes.labelOf(task.taskType)} \"$title\" agendada en $activeProject para ${formatDueForHumans(due)}]*" +
                    (if (due <= 0L) " *Sin fecha: pendiente de día y hora.*" else "")
            }
            "RESCHEDULE_TASK" -> {
                // ---ACTION:RESCHEDULE_TASK|titulo|fechaTexto---
                val parts = payload.split("|")
                val titleKeyword = parts.getOrNull(0)?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val due = resolveDateTextToTimestamp(parts.getOrNull(1))
                val projectTasks = db.taskDao().getTasksByJobSync(activeProject)
                val target = projectTasks.firstOrNull {
                    titleKeyword.isNotBlank() && it.title.lowercase(Locale.getDefault()).contains(titleKeyword)
                } ?: projectTasks.filter { !it.isCompleted }.maxByOrNull { it.createdAt }
                ?: projectTasks.maxByOrNull { it.createdAt }

                if (target != null) {
                    db.taskDao().updateTaskDue(target.id, due)
                    "\n\n✅ *[Acción ejecutada: Tarea \"${target.title}\" re-agendada: ${formatDueForHumans(due)} en $activeProject]*"
                } else {
                    ""
                }
            }
            "COMPLETE_TASK" -> {
                val titleKeyword = payload.trim().lowercase(Locale.getDefault())
                val projectTasks = db.taskDao().getTasksByJobSync(activeProject)
                val target = projectTasks.firstOrNull { it.title.lowercase(Locale.getDefault()).contains(titleKeyword) }
                    ?: projectTasks.firstOrNull { !it.isCompleted }

                if (target != null) {
                    db.taskDao().updateTaskStatus(target.id, "TERMINADO", true)
                    "\n\n✅ *[Acción ejecutada: Tarea \"${target.title}\" marcada como Concluida en $activeProject]*"
                } else {
                    ""
                }
            }
            "DELETE_TASK" -> {
                val titleKeyword = payload.trim().lowercase(Locale.getDefault())
                val projectTasks = db.taskDao().getTasksByJobSync(activeProject)
                val target = projectTasks.firstOrNull { it.title.lowercase(Locale.getDefault()).contains(titleKeyword) }
                if (target != null) {
                    db.taskDao().deleteTask(target)
                    db.memoryDao().deleteChunksBySource(target.id, "TASK")
                    "\n\n🗑️ *[Acción ejecutada: Tarea \"${target.title}\" eliminada de $activeProject]*"
                } else {
                    ""
                }
            }
            "SAVE_NOTE" -> {
                val parts = payload.split("|")
                val title = parts.getOrNull(0)?.trim()?.ifBlank { "Nota de conversación" } ?: "Nota de conversación"
                val content = parts.getOrNull(1)?.trim() ?: payload
                val doc = DocumentItem(
                    title = title,
                    jobTag = activeProject,
                    category = "MEMORIA",
                    content = content
                )
                val docId = db.documentDao().insertDocument(doc)
                ragEngine.indexContent(docId, "DOCUMENT", title, activeProject, content)
                "\n\n💾 *[Acción ejecutada: Nota guardada y vectorizada en memoria RAG de $activeProject]*"
            }
            else -> ""
        }

        return (cleanReply + confirmationText).trim()
    }

    private fun isWriteOrModifyIntent(text: String): Boolean {
        val lower = text.lowercase(Locale.getDefault())
        val actionVerbs = listOf(
            "guarda", "guardar", "crea", "crear", "agrega", "agregar",
            "agenda", "agendar", "anota", "anotar", "borra", "borrar",
            "elimina", "eliminar", "quita", "quitar", "concluye", "concluir",
            "termina", "terminar", "finaliza", "finalizar", "completa", "completar",
            "marca como", "pon como", "almacena", "almacenar", "registra", "registrar",
            "recuérdame", "recuerdame", "agéndamela", "agendame", "necesito tener lista",
            "ponla como", "márcala como"
        )
        return actionVerbs.any { lower.contains(it) }
    }

    private suspend fun tryExecuteConversationalCommand(inputText: String, activeProject: String): String? {
        val lower = inputText.trim().lowercase(Locale.getDefault())

        // 1. Concluir / Terminar / Finalizar Tarea
        val isCompleteIntent = (lower.contains("conclu") || lower.contains("termina") || lower.contains("finaliz") || lower.contains("complet") || lower.contains("hecho")) &&
            (lower.contains("tarea") || lower.contains("esta") || lower.contains("este") || lower.contains("pon como") || lower.contains("marca como"))

        if (isCompleteIntent && activeProject != "Todos") {
            val projectTasks = db.taskDao().getTasksByJobSync(activeProject)
            if (projectTasks.isEmpty()) {
                return "No tienes tareas registradas actualmente en el proyecto **$activeProject** para marcar como concluida."
            }

            val cleanTarget = lower
                .replace(Regex("^(?:por\\s+favor\\s+)?(?:pon\\s+como\\s+en\\s+concluida|pon\\s+como\\s+concluida|marca\\s+como\\s+concluida|marca\\s+como\\s+terminada|concluye|terminé|finaliza|completa)\\s+(?:la\\s+|esta\\s+)?(?:tarea)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace("tarea", "")
                .trim().trimEnd('.', '!', '?')

            val matched = if (cleanTarget.isBlank() || cleanTarget == "esta" || cleanTarget == "la") {
                projectTasks.firstOrNull { !it.isCompleted } ?: projectTasks.first()
            } else {
                projectTasks.firstOrNull { it.title.lowercase(Locale.getDefault()).contains(cleanTarget) }
                    ?: projectTasks.firstOrNull { cleanTarget.contains(it.title.lowercase(Locale.getDefault())) }
                    ?: projectTasks.firstOrNull { !it.isCompleted }
            }

            if (matched != null) {
                db.taskDao().updateTaskStatus(matched.id, "TERMINADO", true)
                return "✅ **Tarea Concluida:**\nHe marcado como **Concluida** la tarea:\n• **\"${matched.title}\"**\nAsociada al proyecto **$activeProject**."
            } else {
                val taskList = projectTasks.filter { !it.isCompleted }.joinToString("\n") { "• ${it.title}" }
                return "No identifiqué qué tarea concluir en **$activeProject**. Tus tareas pendientes son:\n\n$taskList"
            }
        }

        // 2. Borrar / Eliminar tarea
        val isDeleteIntent = (lower.contains("borra") || lower.contains("elimina") || lower.contains("quitar") || lower.contains("descartar")) &&
            (lower.contains("tarea") || lower.contains("esta") || lower.contains("este"))

        if (isDeleteIntent && activeProject != "Todos") {
            val projectTasks = db.taskDao().getTasksByJobSync(activeProject)
            if (projectTasks.isEmpty()) {
                return "No tienes tareas registradas actualmente en el proyecto **$activeProject** para eliminar."
            }

            val cleanTarget = lower
                .replace(Regex("^(?:por\\s+favor\\s+)?(?:borra(?:r)?|elimina(?:r)?|quitar?|descarta(?:r)?)\\s+(?:la\\s+|esta\\s+)?(?:tarea)?\\s*", RegexOption.IGNORE_CASE), "")
                .replace("tarea", "")
                .trim().trimEnd('.', '!', '?')

            val matched = if (cleanTarget.isBlank() || cleanTarget == "esta" || cleanTarget == "la") {
                projectTasks.firstOrNull { !it.isCompleted } ?: projectTasks.first()
            } else {
                projectTasks.firstOrNull { it.title.lowercase(Locale.getDefault()).contains(cleanTarget) }
                    ?: projectTasks.firstOrNull { cleanTarget.contains(it.title.lowercase(Locale.getDefault())) }
            }

            if (matched != null) {
                db.taskDao().deleteTask(matched)
                db.memoryDao().deleteChunksBySource(matched.id, "TASK")
                return "🗑️ **Tarea Eliminada:**\nHe borrado la tarea **\"${matched.title}\"** del proyecto **$activeProject** de tu agenda y memoria RAG."
            } else {
                val taskList = projectTasks.joinToString("\n") { "• ${it.title}" }
                return "No encontré la tarea para eliminar en **$activeProject**. Las tareas disponibles son:\n\n$taskList"
            }
        }

        // 3. Re-agendar / corregir fecha de tarea ("está mal, es para el siguiente viernes")
        val isRescheduleIntent = (lower.contains("reagenda") || lower.contains("reprograma") ||
            lower.contains("cambia la fecha") || lower.contains("cambiale la fecha") || lower.contains("cambiar la fecha") ||
            lower.contains("muevela") || lower.contains("muévela") || lower.contains("mover a") ||
            lower.contains("esta mal") || lower.contains("está mal") || lower.contains("agendaste mal") ||
            lower.contains("mal agendada") || lower.contains("mal agendado") || lower.contains("la agendaste") ||
            lower.contains("deberia ser") || lower.contains("debería ser") || lower.contains("es para el") ||
            lower.contains("es para la") || lower.contains("es para este") || lower.contains("es para ma"))
        val parsedDue = SpanishDateParser.resolveDueTimestamp(lower)
        if (isRescheduleIntent && parsedDue != null && activeProject != "Todos") {
            val projectTasks = db.taskDao().getTasksByJobSync(activeProject)
            val target = projectTasks.firstOrNull { t -> t.title.lowercase(Locale.getDefault()).isNotBlank() && lower.contains(t.title.lowercase(Locale.getDefault()).take(18)) }
                ?: projectTasks.filter { !it.isCompleted }.maxByOrNull { it.createdAt }
                ?: projectTasks.maxByOrNull { it.createdAt }

            if (target != null) {
                db.taskDao().updateTaskDue(target.id, parsedDue)
                val dateFmt = SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es", "ES")).format(Date(parsedDue))
                return "📅 **Tarea Re-agendada:**\nCorregí la fecha de la tarea:\n• **\"${target.title}\"**\nNuevo vencimiento: **$dateFmt** (proyecto **$activeProject**).\n\nSi no era esta tarea, dime su nombre y la fecha correcta."
            }
            return "No encontré ninguna tarea para re-agendar en el proyecto **$activeProject**. Dime el nombre de la tarea y la fecha correcta."
        }

        // 4. Crear / Agendar / Guardar Tarea
        val isCreateTaskIntent = (lower.contains("crea") || lower.contains("agrega") || lower.contains("anota") ||
            lower.contains("guarda") || lower.contains("agenda") || lower.startsWith("recuérdame") ||
            lower.startsWith("recuerdame") || lower.contains("nueva tarea") || lower.contains("necesito tener lista")) &&
            (lower.contains("tarea") || lower.contains("recordatorio") || lower.contains("pendiente") ||
            lower.contains("junta") || lower.contains("reunión") || lower.contains("reunion") ||
            lower.startsWith("recuérdame") || lower.startsWith("recuerdame") || lower.contains("agéndamela") ||
            lower.contains("agendame") || lower.contains("necesito tener lista"))

        if (isCreateTaskIntent && activeProject != "Todos") {
            // SUB-AGENTE EXTRACTOR: título técnico no literal + tipo + prioridad + fecha + confianza
            val history = recentHistoryText()
            val extracted = try {
                extractTaskDetailsViaLLM(inputText, activeProject, history)
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "extractTaskDetails error: ${t.message}", t)
                null
            } ?: naiveTaskExtraction(inputText, activeProject)

            val task = WorkTask(
                title = extracted.title,
                description = extracted.description,
                jobTag = activeProject,
                dueTimestamp = extracted.dueTimestamp,
                priority = extracted.priority,
                taskType = extracted.taskType,
                confidence = extracted.confidence,
                originReference = "Asistente ($activeProject)" + if (extracted.viaLLM) "" else " (sin LLM)"
            )
            val taskId = db.taskDao().insertTask(task)
            ragEngine.indexContent(
                sourceId = taskId,
                sourceType = "TASK",
                title = extracted.title,
                jobTag = activeProject,
                content = "Tarea: ${extracted.title}. Tipo: ${TaskTypes.labelOf(extracted.taskType)}. Prioridad: ${extracted.priority}. Proyecto: $activeProject."
            )
            if (extracted.dueTimestamp > System.currentTimeMillis()) {
                ReminderNotificationHelper.showTaskReminder(
                    getApplication(),
                    taskId.toInt(),
                    extracted.title,
                    "Programada para ${formatDueForHumans(extracted.dueTimestamp)} (${extracted.priority})",
                    activeProject,
                    "Recordatorio Proactivo"
                )
            }

            val typeLabel = TaskTypes.labelOf(extracted.taskType)
            val confLabel = when (extracted.confidence) { "BAJA" -> "Baja"; "MEDIA" -> "Media"; else -> "Alta" }
            return "✅ **$typeLabel Agendada en $activeProject:**\n" +
                "• **Título:** ${extracted.title}\n" +
                "• **Tipo:** $typeLabel\n" +
                "• **Prioridad:** ${extracted.priority}\n" +
                "• **Vencimiento:** ${formatDueForHumans(extracted.dueTimestamp)}\n" +
                "• **Confianza:** $confLabel" +
                (if (extracted.dueTimestamp <= 0L) "\n\n📌 No se especificó día ni hora: aparece en tus tareas pendientes pero no en el calendario. Dime la fecha y la re-agendo." else "") +
                "\n\nGuardada en tu agenda y vectorizada en memoria RAG."
        }

        // 4. Guardar información / Nota / Directriz / Acuerdo en RAG
        val isStoreInfoIntent = (lower.contains("almacena") || lower.contains("almacenar") ||
            lower.contains("guarda esta información") || lower.contains("guarda la información") ||
            lower.contains("guarda esta nota") || lower.contains("guarda este acuerdo") ||
            lower.contains("registra esta información") || lower.contains("guarda este resumen") ||
            lower.contains("anota esta información") || lower.contains("guarda esta directriz"))

        if (isStoreInfoIntent && activeProject != "Todos") {
            val cleanContent = inputText.replace(Regex("^(?:por\\s+favor\\s+)?(?:almacena(?:r)?|guarda(?:r)?|registra(?:r)?|anota(?:r)?)\\s+(?:esta\\s+|la\\s+)?(?:información|info|nota|acuerdo|directriz)?\\s*[:\\-]?\\s*", RegexOption.IGNORE_CASE), "").trim()
            val type = if (lower.contains("directriz") || lower.contains("lineamiento")) "DIRECTIVA"
                       else if (lower.contains("junta") || lower.contains("reunión") || lower.contains("acuerdo")) "JUNTA"
                       else "MEMORIA"

            val previewTitle = cleanContent.take(45).trimEnd('.', ':', ',').ifBlank { "Nota informativa" }
            val doc = DocumentItem(
                title = previewTitle,
                jobTag = activeProject,
                category = type,
                content = inputText.trim()
            )
            val docId = db.documentDao().insertDocument(doc)
            ragEngine.indexContent(docId, "DOCUMENT", doc.title, activeProject, doc.content)
            return "💾 **Información Guardada en $activeProject:**\n\"$previewTitle\"\n\nAlmacenada y vectorizada en memoria RAG bajo este proyecto."
        }

        // 5. Consultar proyectos existentes
        if (lower.contains("qué proyectos") || lower.contains("que proyectos") ||
            lower.contains("cuáles son mis proyectos") || lower.contains("cuales son mis proyectos") ||
            lower.contains("mis trabajos") || lower.contains("cuáles proyectos") || lower.contains("cuales proyectos")) {
            val jobs = db.jobProjectDao().getAllJobsSync()
            return if (jobs.isEmpty()) {
                "Actualmente no tienes ningún proyecto registrado. Puedes decirme \"Crea el proyecto [Nombre]\" y lo crearé de inmediato."
            } else {
                val listStr = jobs.joinToString("\n") { "• ${it.name}${if (it.companyOrClient.isNotBlank()) " (${it.companyOrClient})" else ""}" }
                "Tus proyectos y trabajos actuales son:\n\n$listStr\n\nPuedes seleccionar cualquiera en el menú superior para delimitar tus acciones y memorias RAG."
            }
        }

        return null
    }

    fun addProject(name: String, client: String = "", colorHex: String = "#00E5FF") {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                db.jobProjectDao().insertJob(
                    JobProject(
                        name = name.trim(),
                        companyOrClient = client.trim(),
                        colorHex = colorHex
                    )
                )
            }
        }
    }

    fun deleteProject(job: JobProject) {
        viewModelScope.launch {
            db.jobProjectDao().deleteJob(job)
        }
    }

    fun exportIdeIntegrationDoc(context: Context) {
        val ip = webhookServer.getLocalIpAddress()
        val docFile = IdeIntegrationExporter.generateIntegrationMarkdown(context, ip, port = 8765)
        IdeIntegrationExporter.shareIntegrationDoc(context, docFile)
    }

    // --- Document & Guideline Ingestion ---
    fun addDocument(title: String, jobTag: String, category: String, content: String) {
        viewModelScope.launch {
            val docId = db.documentDao().insertDocument(
                DocumentItem(
                    title = title,
                    jobTag = jobTag,
                    category = category,
                    content = content
                )
            )

            // Guaranteed zero loss in Vault
            db.vaultDao().insertVaultEntry(
                VaultEntry(
                    title = "Documento: $title",
                    rawContent = content,
                    sourceType = "DOCUMENT",
                    jobTag = jobTag,
                    retentionDays = prefs.getRetentionDays()
                )
            )

            // Index in RAG memory
            ragEngine.indexContent(
                sourceId = docId,
                sourceType = "DOCUMENT",
                title = title,
                jobTag = jobTag,
                content = content
            )
        }
    }

    fun updateDocument(doc: DocumentItem) {
        viewModelScope.launch {
            db.documentDao().updateDocument(doc)
            db.memoryDao().deleteChunksBySource(doc.id, "DOCUMENT")
            ragEngine.indexContent(
                sourceId = doc.id,
                sourceType = "DOCUMENT",
                title = doc.title,
                jobTag = doc.jobTag,
                content = doc.content
            )
        }
    }

    fun deleteDocument(doc: DocumentItem) {
        viewModelScope.launch {
            db.documentDao().deleteDocument(doc)
            db.memoryDao().deleteChunksBySource(doc.id, "DOCUMENT")
        }
    }

    // --- Rotator actions ---
    fun forceRotateModel() {
        rotator.forceRotate()
    }

    fun resetRotatorCooldowns() {
        rotator.resetCooldowns()
    }

    fun refreshRotatorCatalog() {
        viewModelScope.launch {
            rotator.refreshCatalog(force = true)
        }
    }

    fun runRotatorBenchmarks() {
        viewModelScope.launch {
            _benchmarkingActive.value = true
            rotator.runBenchmarks(limit = 4)
            _benchmarkingActive.value = false
        }
    }

    fun updateRotatorSettings(
        openRouterKey: String? = null,
        nvidiaKey: String? = null,
        localhostEnabled: Boolean? = null,
        localhostUrl: String? = null,
        localhostModel: String? = null,
        primaryProvider: String? = null,
        fallbackProvider: String? = null,
        activeMode: String? = null
    ) {
        rotator.updateConfig(
            openRouterKey = openRouterKey,
            nvidiaKey = nvidiaKey,
            localhostEnabled = localhostEnabled,
            localhostUrl = localhostUrl,
            localhostModelId = localhostModel,
            primaryProvider = primaryProvider,
            fallbackProvider = fallbackProvider,
            activeMode = activeMode
        )
    }

    // --- Exporting ---
    fun exportChromeExtension(context: Context) {
        val ip = webhookServer.getLocalIpAddress()
        val zip = ChromeExtensionExporter.generateExtensionZip(context, ip, port = 8765)
        ChromeExtensionExporter.shareExtensionZip(context, zip)
    }

    fun exportDailyReport(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = db.taskDao().getAllTasks()
            val dateStr = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("es", "ES")).format(Date())
            val sb = StringBuilder()
            sb.append("# Reporte Diario de Productividad — OmniWork\n")
            sb.append("**Fecha:** $dateStr\n\n")

            val currentTasks = db.taskDao().getAllTasks() // flow snapshot
            sb.append("## Tareas y Compromisos por Proyecto\n")
            // Render text
            sb.append("- Reporte consolidado generado automáticamente desde la memoria y bóveda local de OmniWork.\n")

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Reporte OmniWork — $dateStr")
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir Reporte"))
        }
    }

    private suspend fun seedInitialJobsAndDemoData() {
        if (db.jobProjectDao().getJobCount() > 0) return
        // Check job projects
        val defaultJobs = listOf(
            JobProject(name = "Trabajo Principal", companyOrClient = "Corporativo Tech", colorHex = "#3B82F6", isPrimary = true),
            JobProject(name = "Consultoría Alpha", companyOrClient = "Fintech Group", colorHex = "#8B5CF6"),
            JobProject(name = "Dev Freelance", companyOrClient = "Clientes Varios", colorHex = "#10B981")
        )
        for (job in defaultJobs) {
            db.jobProjectDao().insertJob(job)
        }

        // Demo sample tasks if empty
        val sampleTask = WorkTask(
            title = "Revisar arquitectura de microservicios",
            description = "Validar endpoints y tiempos de respuesta con el equipo de backend.",
            jobTag = "Trabajo Principal",
            dueTimestamp = System.currentTimeMillis() + 3600 * 1000L * 2,
            priority = "ALTA",
            originReference = "Junta de planeación semanal"
        )
        val tId = db.taskDao().insertTask(sampleTask)
        ragEngine.indexContent(tId, "TASK", sampleTask.title, sampleTask.jobTag, sampleTask.description)

        // Demo guideline doc
        val sampleDoc = DocumentItem(
            title = "Delineamientos de Integración Cloud Code",
            jobTag = "Dev Freelance",
            category = "DIRECTIVA",
            content = "Todos los commits deben pasar por el webhook local de OmniWork. Se debe mantener el estándar de TypeScript y Clean Architecture. Las respuestas de API deben estar tipadas."
        )
        val dId = db.documentDao().insertDocument(sampleDoc)
        ragEngine.indexContent(dId, "DOCUMENT", sampleDoc.title, sampleDoc.jobTag, sampleDoc.content)
    }

    override fun onCleared() {
        super.onCleared()
        webhookServer.stop()
        tts?.shutdown()
    }
}
