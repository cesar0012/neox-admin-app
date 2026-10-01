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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /**
     * Dictado inteligente: transcripción con Whisper vía Groq (misma API key del rotador).
     * null cuando no hay API key de Groq configurada — el modal usa entonces el modo directo.
     */
    val smartDictationTranscriber: (suspend (ByteArray) -> Result<String>)?
        get() = if (rotator.config.groqApiKey.isNotBlank()) {
            { wav -> com.example.data.speech.WhisperTranscriber.transcribe(wav, rotator.config.groqApiKey) }
        } else null
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
        const val CHAT_HISTORY_WINDOW = 20
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
                    val project = _assistantSelectedProject.value
                    // El reemplazo se busca DENTRO del proyecto activo; si no hay, se crea uno nuevo
                    val target = sessions.firstOrNull { it.jobTag.equals(project, ignoreCase = true) }
                    if (target != null) {
                        _activeSessionId.value = target.id
                        loadSessionMessages(target.id)
                    } else {
                        val id = db.conversationDao().insertSession(
                            ConversationSession(title = defaultSessionTitle(), jobTag = project)
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

    /** Cada proyecto tiene SU PROPIA conversación: al cambiar de proyecto se cambia de sesión. */
    fun setAssistantProject(project: String) {
        if (_assistantSelectedProject.value == project) return
        _assistantSelectedProject.value = project
        viewModelScope.launch {
            try {
                val sessions = db.conversationDao().getAllSessionsSync()
                val target = sessions.firstOrNull { it.jobTag.equals(project, ignoreCase = true) }
                if (target != null) {
                    _activeSessionId.value = target.id
                    loadSessionMessages(target.id)
                } else {
                    val id = db.conversationDao().insertSession(
                        ConversationSession(title = defaultSessionTitle(), jobTag = project)
                    )
                    _chatSessions.value = db.conversationDao().getAllSessionsSync()
                    _activeSessionId.value = id
                    _chatMessages.value = listOf(greetingMessage())
                }
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "setAssistantProject session switch error: ${t.message}", t)
            }
        }
    }

    // Tema claro/oscuro (oscuro por defecto)
    private val _isDarkTheme = MutableStateFlow(prefs.isDarkTheme())
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        val newDark = !_isDarkTheme.value
        prefs.setDarkTheme(newDark)
        _isDarkTheme.value = newDark
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

    /** Reproduce una muestra de voz. Si se pasan overrides, se aplican SIN persistir:
     *  así se escucha la voz/velocidad/tono en prueba ANTES de guardar. */
    fun speakSample(rate: Float? = null, pitch: Float? = null, voiceName: String? = null) {
        val engine = tts ?: return
        if (!isTtsReady) return
        if (rate != null || pitch != null || voiceName != null) {
            applyTtsPreview(
                rate ?: prefs.getTtsRate(),
                pitch ?: prefs.getTtsPitch(),
                voiceName
            )
        }
        val clean = TtsTextCleaner.clean(
            "Hola, soy tu asistente Neox Admin. Así se escucha esta voz con la velocidad y el tono que elegiste."
        )
        if (clean.isNotBlank()) {
            engine.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "tts_preview")
        }
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
        val viaLLM: Boolean,
        val hasTime: Boolean = true // false = el usuario no especificó hora
    )

    /** Resultado del sub-agente extractor: puede decidir que el mensaje NO es una orden de agenda. */
    sealed class TaskExtraction {
        data class Details(val details: ExtractedTaskDetails) : TaskExtraction()
        object NotATaskRequest : TaskExtraction()
        object Unavailable : TaskExtraction()
    }

    data class NoteDetails(
        val title: String,
        val description: String,
        val category: String // DIRECTIVA, ESPECIFICACION, REPORTE o MEMORIA
    )

    /**
     * Preguntas, quejas y seguimientos ("¿sí la agregaste?", "no veo la tarea") NO son órdenes:
     * deben ir al LLM con historial, nunca ejecutarse como comandos directos.
     */
    private fun isInterrogativeOrFollowUp(text: String): Boolean {
        val t = text.trim()
        val lower = t.lowercase(Locale.getDefault())
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        val imperativeStart = Regex(
            "^(?:por\\s+favor\\s+)?(?:crea|crear|agrega|agregar|anota|anotar|guarda|guardar|agenda|agendar|agendame|borra|borrar|elimina|eliminar|quita|concluye|concluir|termina|terminar|finaliza|finalizar|completa|completar|marca|pon|reagenda|reprograma|mueve|recuerdame|almacena|almacenar|registra|registrar)\\b"
        )
        val isQuestion = t.contains("?") || t.contains("¿")
        if (isQuestion && !imperativeStart.containsMatchIn(lower)) return true
        val followUpStarters = listOf(
            "no veo", "no encuentro", "no aparece", "no aparecio", "no esta", "no se ve",
            "ya la", "ya lo", "si la", "si lo", "se te paso", "hubo un error",
            "que pasa con", "donde esta", "donde quedo", "que paso con", "por que no"
        )
        return followUpStarters.any { lower.startsWith(it) }
    }

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

    private suspend fun extractTaskDetailsViaLLM(userText: String, activeProject: String, history: String): TaskExtraction {
        val systemPrompt = """
            Eres un SUB-AGENTE EXTRACTOR de tareas de un asistente ejecutivo. Recibes el mensaje conversacional del usuario y produces la ficha de lo que pidió agendar.

            REGLA DE CLASIFICACIÓN PREVIA (crítica):
            - Si el mensaje NO es realmente una solicitud de agendar/crear algo —si es una PREGUNTA, queja, corrección de algo ya hablado o seguimiento ("no veo la tarea", "¿sí la agregaste?", "mejora el título")— responde ÚNICAMENTE: ---NO_ES_TAREA---
            - Usa el HISTORIAL para decidir: si el usuario se refiere a algo ya platicado pidiendo aclaración, es NO_ES_TAREA.

            REGLAS DE TÍTULO (CRÍTICAS):
            - Corto (máximo 8 palabras), técnico y orientado a la acción.
            - NUNCA copies frases literales del usuario ni sus opiniones o disgusto ("no me gustó cómo..." JAMÁS va en el título).
            - Ejemplo: "No me gustó cómo quedaron los envíos de los templates HTML, agenda una junta para el viernes" → Título: "Junta: revisar templates HTML de envíos".
            - Si pide junta, inicia con "Junta:". Si es llamada, "Llamada:". Si es entrega, "Entrega:".

            REGLAS DE FECHA:
            - Devuelve la fecha en lenguaje natural tal cual se entiende ("viernes", "mañana", "en 3 días", "25/12/2026") o "sin fecha" si el usuario no mencionó ninguna. NUNCA fechas pasadas.

            REGLAS DE HORA (CRÍTICAS):
            - CUALQUIER hora que el usuario mencione —"a las 3", "15:30", "10 am", "10 de la mañana", "3 y media de la tarde", "10 de la noche", "mediodía"— va en la sección HORA en formato HH:mm de 24h. Conversiones: 10 de la mañana=10:00, 3 de la tarde=15:00, 10 pm=22:00, 12 am=00:00, 3 y media=03:30, mediodía=12:00.
            - La hora que el usuario dice SIEMPRE se agenda en la sección HORA. JAMÁS la consignes únicamente en la DESCRIPCIÓN: si la dijo, es porque quiere que quede agendada a esa hora.
            - Si el usuario dio SOLO hora sin día, escribe "sin fecha" en FECHA pero la hora SÍ va en HORA (el sistema la agendará).
            - Si NO mencionó hora, escribe "sin hora" (el sistema pondrá 9:00 por defecto y avisará al usuario).
            - La DESCRIPCIÓN describe contexto y propósito del pendiente: NUNCA incluyas en ella fechas ni horas.

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
            ---HORA---
            [HH:mm o "sin hora"]
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
        if (!result.isSuccess) return TaskExtraction.Unavailable
        val output = LLMRotator.cleanModelResponse(result.getOrNull().orEmpty())
        if (output.uppercase(Locale.getDefault()).contains("NO_ES_TAREA")) return TaskExtraction.NotATaskRequest

        fun section(marker: String, next: String): String {
            val start = output.indexOf(marker)
            if (start == -1) return ""
            val from = start + marker.length
            val end = output.indexOf(next, from).let { if (it == -1) output.length else it }
            return output.substring(from, end).trim()
        }

        val title = section("---TITULO---", "---DESCRIPCION---").replace(Regex("^[\"'\\[\\]]+|[\"'\\[\\]]+$"), "").trim()
        if (title.isBlank() || title.length > 120) return TaskExtraction.Unavailable

        val description = section("---DESCRIPCION---", "---TIPO---").ifBlank { "Agendado desde el asistente conversacional" }
        val typeRaw = section("---TIPO---", "---PRIORIDAD---").uppercase(Locale.getDefault()).trim()
        val prioRaw = section("---PRIORIDAD---", "---FECHA---").uppercase(Locale.getDefault()).trim()
        val fechaText = section("---FECHA---", "---HORA---").trim()
        val horaText = section("---HORA---", "---CONFIANZA---").trim()
        val confRaw = section("---CONFIANZA---", "---FIN---").uppercase(Locale.getDefault()).trim()

        val due = resolveDateTextToTimestamp(fechaText, horaText)
        return TaskExtraction.Details(
            ExtractedTaskDetails(
                title = title,
                description = description,
                taskType = if (TaskTypes.isValid(typeRaw)) typeRaw else TaskTypes.detect(userText.lowercase(Locale.getDefault())),
                priority = if (prioRaw in listOf("ALTA", "MEDIA", "BAJA")) prioRaw else "MEDIA",
                dueTimestamp = due,
                confidence = if (ConfidenceLevels.isValid(confRaw)) confRaw else ConfidenceLevels.MEDIA,
                viaLLM = true,
                hasTime = hasExplicitHour(horaText)
            )
        )
    }

    /** Sub-agente para directivas/especificaciones/reportes: título y descripción inteligentes. */
    private suspend fun generateNoteDetailsViaLLM(userText: String, activeProject: String, history: String): NoteDetails? {
        val systemPrompt = """
            Eres un SUB-AGENTE que convierte indicaciones conversacionales en documentos normativos.
            El usuario pidió guardar una directiva, lineamiento, especificación o reporte.

            REGLAS:
            - TÍTULO: corto (máx 8 palabras), técnico y descriptivo del tema. NUNCA frases literales del usuario.
            - DESCRIPCIÓN: el contenido normativo COMPLETO y bien redactado de lo que se debe cumplir/recordar, en 2-4 líneas. Incluye los detalles específicos (nombres correctos, reglas, convenciones).
            - CATEGORÍA: DIRECTIVA (regla/convención a cumplir), ESPECIFICACION (requisito técnico), REPORTE (informe/estado), o MEMORIA (apunte general).

            Responde EXACTAMENTE:
            ---TITULO---
            [título]
            ---DESCRIPCION---
            [descripción completa]
            ---CATEGORIA---
            [DIRECTIVA o ESPECIFICACION o REPORTE o MEMORIA]
        """.trimIndent()

        val result = rotator.executeWithRotation(
            systemPrompt = systemPrompt,
            userPrompt = "Proyecto activo: $activeProject\n$history\nMENSAJE DEL USUARIO:\n$userText",
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
        if (title.isBlank()) return null
        val description = section("---DESCRIPCION---", "---CATEGORIA---").ifBlank { userText.trim() }
        val category = section("---CATEGORIA---", "---FIN---").uppercase(Locale.getDefault()).trim()
        return NoteDetails(
            title = title,
            description = description,
            category = if (category in listOf("DIRECTIVA", "ESPECIFICACION", "REPORTE")) category else "MEMORIA"
        )
    }

    private fun naiveNoteExtraction(inputText: String): NoteDetails {
        val lower = inputText.lowercase(Locale.getDefault())
        val content = inputText.replace(
            Regex("^(?:por\\s+favor\\s+)?(?:guarde?[ra]?|almacena(?:r)?|registra(?:r)?|anota(?:r)?|crea(?:r)?|agrega(?:r)?)\\s+(?:la\\s+|el\\s+|lo\\s+|esta\\s+|este\\s+|siguiente\\s+)+(?:directiva|directriz|lineamiento|especificaci[oó]n|reporte|nota|informaci[oó]n)?\\s*", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        val category = when {
            lower.contains("directiva") || lower.contains("directriz") || lower.contains("lineamiento") -> "DIRECTIVA"
            lower.contains("especificaci") -> "ESPECIFICACION"
            lower.contains("reporte") -> "REPORTE"
            else -> "MEMORIA"
        }
        val title = content.take(45).trimEnd('.', ':', ',').ifBlank { "Nota de conversación" }
        return NoteDetails(title = title.replaceFirstChar { it.uppercase(Locale.getDefault()) }, description = inputText.trim(), category = category)
    }

    /** Texto natural de fecha -> timestamp SIEMPRE futuro; 0 cuando no hay fecha válida.
     *  Acepta la hora embebida ("mañana a las 11:00", "a las 10 de la mañana") o separada en horaText;
     *  sin hora -> 9:00 por defecto. */
    private fun resolveDateTextToTimestamp(fechaText: String?, horaText: String? = null): Long {
        if (fechaText.isNullOrBlank()) return 0L
        val clean = fechaText.trim().lowercase(Locale.getDefault())
        if (clean == "-" || clean == "sin fecha" || clean == "sin especificar" || clean == "n/a") return 0L
        val resolved = SpanishDateParser.resolveDueTimestamp(clean) ?: return 0L
        return applyHourToTimestamp(resolved, parseHourMinutes("$clean ${horaText.orEmpty()}"))
    }

    /** Sustituye la parte de hora de un timestamp base (deja intactos día/mes/año). */
    private fun applyHourToTimestamp(base: Long, hm: Pair<Int, Int>?, defaultHour: Int = 9): Long {
        val (h, min) = hm ?: (defaultHour to 0)
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = base
            set(java.util.Calendar.HOUR_OF_DAY, h.coerceIn(0, 23))
            set(java.util.Calendar.MINUTE, min.coerceIn(0, 59))
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /** Siguiente ocurrencia futura de una hora: hoy si aún no pasó, si no mañana. */
    private fun nextOccurrenceAtHour(hm: Pair<Int, Int>): Long {
        val today = SpanishDateParser.resolveDueTimestamp("hoy") ?: return applyHourToTimestamp(System.currentTimeMillis(), hm)
        var due = applyHourToTimestamp(today, hm)
        if (due <= System.currentTimeMillis()) {
            val tomorrow = SpanishDateParser.resolveDueTimestamp("mañana")
                ?: return applyHourToTimestamp(System.currentTimeMillis() + 86_400_000L, hm)
            due = applyHourToTimestamp(tomorrow, hm)
        }
        return due
    }

    private val meridiemMarker =
        "(?:a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|de\\s+la\\s+manana|de\\s+la\\s+tarde|de\\s+la\\s+noche)"

    /**
     * Parser de hora robusto para español. Reconoce: "10:30", "10 am", "10am", "10 p.m.",
     * "a las 10", "a las 10 en punto", "3 y media", "3 y cuarto de la tarde",
     * "10 de la mañana / tarde / noche", "15:00 hrs", "mediodía".
     * @return (hora24, minutos) o null si el texto no menciona hora.
     */
    private fun parseHourMinutes(text: String): Pair<Int, Int>? {
        if (text.isBlank()) return null
        val t = " " + text.lowercase(Locale.getDefault())
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u").replace("ñ", "n") + " "
        if (Regex("\\bmedio\\s*dia\\b").containsMatchIn(t)) return 12 to 0

        // Formato con minutos: "10:30", "10.30", "10:30 pm", "10:30 de la tarde".
        // El lookbehind/lookahead evita capturar decimales ("1.5 horas") o fechas ("25/12").
        Regex("(?<![\\d.,/])(\\d{1,2})\\s*[:.]\\s*(\\d{2})\\s*($meridiemMarker)?(?![\\d/])").find(t)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val min = m.groupValues[2].toIntOrNull() ?: 0
            return applyMeridiem(h, min, m.groupValues[3])
        }
        // Solo hora: "10 am", "a las 10", "3 y media de la tarde", "5 horas".
        // Solo cuenta si viene "a las", un marcador am/pm/mañana/tarde/noche/u "horas":
        // un número suelto ("junta de 3 personas") NO es una hora.
        Regex("(?<![\\d.,/])\\b(a\\s+las?\\s+)?(\\d{1,2})(?:\\s+y\\s+(media|cuarto))?(?:\\s*($meridiemMarker|horas?))?\\b(?![/]\\d)")
            .findAll(t)
            .forEach { m ->
                val hasALas = m.groupValues[1].isNotBlank()
                val hasMarker = m.groupValues[4].isNotBlank()
                val hasFraction = m.groupValues[3].isNotBlank()
                if (!hasALas && !hasMarker && !hasFraction) return@forEach
                val h = m.groupValues[2].toIntOrNull() ?: return@forEach
                val min = when {
                    m.groupValues[3] == "media" -> 30
                    m.groupValues[3] == "cuarto" -> 15
                    else -> 0
                }
                return applyMeridiem(h, min, m.groupValues[4])
            }
        return null
    }

    /** Convierte hora 12h+meridiano a 24h; sin marcador se asume 24h tal cual. */
    private fun applyMeridiem(h: Int, min: Int, marker: String): Pair<Int, Int>? {
        if (h !in 0..23) return null
        val mk = marker.replace(" ", "").replace(".", "")
        return when {
            mk == "pm" || mk == "delatarde" || mk == "delanoche" ->
                (if (h < 12) h + 12 else h) to min.coerceIn(0, 59)
            mk == "am" && h == 12 -> 0 to min.coerceIn(0, 59)
            else -> h to min.coerceIn(0, 59)
        }
    }

    private fun hasExplicitHour(horaText: String?): Boolean = parseHourMinutes(horaText.orEmpty()) != null

    /**
     * Resuelve el nuevo vencimiento para un re-agendado. Reglas:
     * - fechaTexto con fecha y hora -> esa fecha a esa hora.
     * - fechaTexto con solo fecha -> esa fecha a las 9:00 (o la hora que traiga).
     * - fechaTexto con SOLO HORA ("10:00", "10 de la mañana") -> conserva el día actual y mueve la hora;
     *   si la tarea no tenía fecha, la hora aplica hoy (si aún es futura) o mañana.
     * - sin nada interpretable -> (0, false): no se toca la fecha.
     * @return (timestamp, hasTime)
     */
    private fun resolveRescheduleDue(fechaText: String, currentDue: Long): Pair<Long, Boolean> {
        val clean = fechaText.trim().lowercase(Locale.getDefault())
        if (clean.isBlank() || clean == "-" || clean == "sin fecha" || clean == "sin hora" || clean == "sin cambiar") return 0L to false

        val hm = parseHourMinutes(clean)
        if (hm == null) {
            // Sin hora en el texto: requiere una fecha; sin ella no se toca el vencimiento.
            val base = SpanishDateParser.resolveDueTimestamp(clean) ?: return 0L to false
            return applyHourToTimestamp(base, null) to false
        }
        return when {
            !hourOnlyText(clean) -> {
                val base = SpanishDateParser.resolveDueTimestamp(stripHourExpressions(clean))
                if (base != null) applyHourToTimestamp(base, hm) to true
                else nextOccurrenceAtHour(hm) to true // fecha irreconocible pero hora válida
            }
            currentDue > 0L -> applyHourToTimestamp(currentDue, hm) to true
            else -> nextOccurrenceAtHour(hm) to true
        }
    }

    /** Expresiones que denotan HORA (no fecha): se eliminan del texto antes de parsear fechas. */
    private val hourStripRegexes = listOf(
        Regex("(?i)\\ba\\s+las?\\s+\\d{1,2}(?:[:.]\\d{2})?(?:\\s*y\\s+(?:media|cuarto))?(?:\\s*(?:a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|de\\s+la\\s+ma[ñn]ana|de\\s+la\\s+tarde|de\\s+la\\s+noche|en\\s+punto))?\\b"),
        Regex("(?i)\\b\\d{1,2}[:.]\\d{2}\\b(?:\\s*(?:a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|de\\s+la\\s+ma[ñn]ana|de\\s+la\\s+tarde|de\\s+la\\s+noche))?"),
        Regex("(?i)\\b\\d{1,2}(?:\\s*y\\s+(?:media|cuarto))?\\s*(?:a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|de\\s+la\\s+ma[ñn]ana|de\\s+la\\s+tarde|de\\s+la\\s+noche|en\\s+punto|horas?)\\b"),
        Regex("(?i)\\bmedio\\s*d[ií]a\\b")
    )

    private fun stripHourExpressions(text: String): String =
        hourStripRegexes.fold(text) { acc, rx -> rx.replace(acc, " ") }

    /**
     * Red de seguridad determinista sobre la extracción del sub-agente: si el modelo devolvió
     * "sin fecha" o "sin hora" pero el usuario SÍ los dijo en su mensaje original, se rescatan
     * del texto. La hora que el usuario menciona SIEMPRE debe quedar agendada en el campo de
     * vencimiento, nunca perdida ni únicamente en la descripción.
     */
    private fun reconcileWithRawText(d: ExtractedTaskDetails, rawText: String): ExtractedTaskDetails {
        val lower = rawText.lowercase(Locale.getDefault())
        val hm = parseHourMinutes(lower)
        var out = d

        if (out.dueTimestamp <= 0L) {
            // El extractor dijo "sin fecha": quitar la porción de hora del texto (para no
            // confundir al parser: "10 de la mañana" no es una fecha) y volver a intentar.
            val textWoHour = if (hm != null) stripHourExpressions(lower) else lower
            SpanishDateParser.resolveDueTimestamp(textWoHour)?.let { base ->
                out = out.copy(dueTimestamp = applyHourToTimestamp(base, hm))
            }
        } else if (hm != null && !out.hasTime) {
            // El extractor dijo "sin hora" pero el usuario la dijo: fijarla sobre la fecha extraída.
            out = out.copy(dueTimestamp = applyHourToTimestamp(out.dueTimestamp, hm), hasTime = true)
        }

        if (hm != null && out.dueTimestamp <= 0L) {
            // Sin fecha interpretable pero con hora dicha: hoy (si es futura) o mañana a esa hora.
            out = out.copy(dueTimestamp = nextOccurrenceAtHour(hm), hasTime = true)
        }
        return out
    }

    /** ¿El texto de fecha es en realidad solo una hora ("10:00", "10 am", "a las 10 de la mañana")? */
    private fun hourOnlyText(text: String): Boolean {
        // Una fecha numérica ("25/12", "25-12") o mes nombrado ("25 de diciembre") NO es solo-hora
        if (Regex("\\b\\d{1,2}\\s*[/-]\\s*\\d{1,2}").containsMatchIn(text)) return false
        // Quita las expresiones de hora y verifica que no quede ninguna palabra de fecha
        var rest = stripHourExpressions(text)
        rest = rest.replace(Regex("\\d+"), " ").replace(Regex("[^a-záéíóúñ ]", RegexOption.IGNORE_CASE), " ")
        val dateWords = listOf(
            "hoy", "manana", "mañana", "pasado", "lunes", "martes", "miercoles", "miércoles",
            "jueves", "viernes", "sabado", "sábado", "domingo", "semana", "mes", "anos", "años",
            "dia", "día", "dias", "días", "próximo", "proximo", "siguiente", "quincena", "fin",
            "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto",
            "septiembre", "octubre", "noviembre", "diciembre", "cada"
        )
        return rest.split(Regex("\\s+")).none { it.trim() in dateWords }
    }

    /** Fallback sin LLM: extracción ingenua pero con tipo, confianza MEDIA y sin fecha si no se dijo. */
    private fun naiveTaskExtraction(inputText: String, activeProject: String): ExtractedTaskDetails {
        val lower = inputText.lowercase(Locale.getDefault())
        val rawTitle = inputText
            .replace(Regex("^(?:por\\s+favor\\s+)?(?:también\\s+te\\s+comento\\s+(?:que\\s+)?)?(?:te\\s+)?(?:crea(?:r)?|agrega(?:r)?|anota(?:r)?|guarda(?:r)?|agenda(?:r)?|recuérdame|recuerdame|necesito\\s+tener\\s+lista)\\s+(?:una\\s+|la\\s+|esta\\s+|el\\s+)?(?:tarea|recordatorio|pendiente|junta|reunión|reunion)?\\s*(?:de|que|para)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(?:para|el|la)\\s+(?:el\\s+|la\\s+)?(?:pr[oó]xim[oa]\\s+|siguiente\\s+)?(?:lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo|mañana|manana|hoy)(?:\\s+que\\s+viene|\\s+pr[oó]xim[oa])?\\s*$", RegexOption.IGNORE_CASE), "")
            .trim().trimEnd('.', '!', '?')
        val title = rawTitle.ifBlank { "Tarea pendiente" }.replaceFirstChar { it.uppercase(Locale.getDefault()) }
        val hm = parseHourMinutes(lower)
        val due = SpanishDateParser.resolveDueTimestamp(if (hm != null) stripHourExpressions(lower) else lower)
            ?.let { applyHourToTimestamp(it, hm) }
            ?: hm?.let { nextOccurrenceAtHour(it) }
            ?: 0L
        return ExtractedTaskDetails(
            title = title,
            description = "Anotada mediante dictado al asistente (sin refinamiento de título por modelo)",
            taskType = TaskTypes.detect(lower),
            priority = if (lower.contains("urgente") || lower.contains("alta")) "ALTA" else if (lower.contains("baja")) "BAJA" else "MEDIA",
            dueTimestamp = due,
            confidence = ConfidenceLevels.MEDIA,
            viaLLM = false,
            hasTime = hm != null
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
        confidence: String = ConfidenceLevels.ALTA,
        hasTime: Boolean = true
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
                hasTime = hasTime,
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
        newJobTag: String,
        newHasTime: Boolean = true
    ) {
        viewModelScope.launch {
            val updated = task.copy(
                title = newTitle.trim(),
                description = newDescription.trim(),
                taskType = if (TaskTypes.isValid(newType)) newType else task.taskType,
                priority = newPriority,
                dueTimestamp = newDueTimestamp,
                jobTag = newJobTag,
                hasTime = newHasTime
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
                  ---ACTION:CREATE_TASK|titulo|prioridad|fechaTexto|tipo|confianza|hora---
                  * titulo: CORTO (máx 8 palabras), técnico y orientado a acción. NUNCA frases literales del usuario ni sus opiniones ("no me gustó..." jamás va en un título). Junta → "Junta: ...".
                  * prioridad: ALTA / MEDIA / BAJA.
                  * fechaTexto: la fecha en lenguaje natural tal como la entiendes ("viernes", "mañana", "en 3 días", "25/12/2026") o "-" si el usuario NO mencionó fecha.
                  * tipo: TAREA / JUNTA / LLAMADA / ENTREGA / RECORDATORIO (según lo pedido: reunión=junta, llamada telefónica=llamada, deadline o envío=entrega).
                  * confianza: ALTA si el pedido fue claro, MEDIA si ambiguo, BAJA si muy incierto.
                  * hora: HH:mm (24h) si el usuario la mencionó, o "sin hora" si no. Convierte CUALQUIER formato que use ("10 de la mañana"→10:00, "10 am"→10:00, "3 de la tarde"→15:00, "3 y media"→03:30, "10 de la noche"→22:00). La hora SIEMPRE se agenda en este parámetro: NUNCA la dejes únicamente en la descripción o en el texto de tu respuesta.
                - Para re-agendar/cambiar fecha (incluye cuando el usuario corrige una fecha mal agendada):
                  ---ACTION:RESCHEDULE_TASK|titulo|fechaTexto---
                - Para concluir tarea: ---ACTION:COMPLETE_TASK|titulo---
                - Para borrar tarea: ---ACTION:DELETE_TASK|titulo---
                  * REGLA ABSOLUTA: NUNCA emitas DELETE_TASK salvo que el usuario diga EXPLÍCITAMENTE "borra/elimina/quita" esa tarea. Una corrección, cambio de hora, queja o re-agenda JAMÁS se resuelve borrando.
                - Para re-agendar/cambiar fecha de tarea (incluye cuando el usuario corrige una fecha mal agendada):
                  ---ACTION:RESCHEDULE_TASK|titulo|fechaTexto---
                  * Si el usuario pide cambiar la HORA de varias tareas ("pon esas dos a las 11:00"), emite una línea RESCHEDULE_TASK por cada tarea afectada, manteniendo su fecha y cambiando la hora en fechaTexto (ej: "mañana a las 11:00").
                  * Si el usuario SOLO cambia la hora ("déjala a las 10 de la mañana", "muévela a las 11"), escribe en fechaTexto únicamente la hora en HH:mm 24h (ej: "10:00"): el sistema conserva el día actual y mueve solo la hora.
                - Para guardar directiva/especificación/reporte/nota en Memoria:
                  ---ACTION:SAVE_NOTE|titulo|contenido|categoria---
                  * categoria: DIRECTIVA (regla/convención), ESPECIFICACION (requisito técnico), REPORTE (informe) o MEMORIA (apunte general).
                  * titulo y contenido deben ser técnicos y bien redactados, nunca frases literales del usuario.
                - Puedes emitir VARIAS acciones en una misma respuesta (una por línea); todas se ejecutarán.
                - Si el usuario pregunta si algo fue agendado y NO lo fue (revíalo en el historial), discúlpate brevemente y emite la acción para agendarlo de inmediato.
                - Si el usuario corrige un nombre o convención, actualiza el título de la tarea correspondiente borrándola y re-creándola con el nombre correcto, y guarda la corrección como DIRECTIVA con SAVE_NOTE.
            """.trimIndent()
        }

        val nowStr = SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "ES")).format(Date())

        val systemPrompt = """
            Eres Neox Admin, el asistente ejecutivo de alta precisión para gestión multiproyecto.

            $projectDirectives

            CONTEXTO TEMPORAL (REGLA CRÍTICA):
            - FECHA Y HORA ACTUAL: $nowStr.
            - NUNCA agendes nada en fechas pasadas. Cuando el usuario mencione un día de la semana ("viernes"), significa SIEMPRE el PRÓXIMO viernes FUTURO a partir de la FECHA ACTUAL; en fechaTexto escribe el día tal cual ("viernes") y el sistema calcula la fecha exacta.
            - REGLA ABSOLUTA DE HORA: la hora que el usuario menciona ("a las 10 de la mañana", "10 am", "3 y media") SIEMPRE se agenda — en el parámetro hora de CREATE_TASK o en fechaTexto de RESCHEDULE_TASK. JAMÁS queda únicamente mencionada en la descripción del pendiente ni solo en tu texto de respuesta: si el usuario la dijo, es porque quiere que quede agendada a esa hora.
            - Si el usuario no menciona fecha, usa "-" como fechaTexto: la junta/tarea queda pendiente SIN fecha (aparece en tareas, no en calendario). Si menciona SOLO hora sin día, también usa "-" y la hora igualmente: el sistema la agendará a esa hora.
            - Si el usuario corrige una fecha ("no, es para el siguiente viernes"), re-agenda la tarea con RESCHEDULE_TASK en lugar de crear una nueva.

            INSTRUCCIONES CLAVE DE FORMATO Y ESTILO:
            1. NUNCA generes ni incluyas etiquetas de razonamiento interno como <think>, </think>, o similares. Responde directamente con tu análisis o respuesta ejecutiva.
            2. Basa tus respuestas en la memoria RAG, en las tareas listadas y en el historial de la conversación.

            DIRECTIVAS BASE (aplican SIEMPRE, en todos los proyectos):
            A. TÍTULOS: cortos o medios, describiendo QUÉ se va a hacer. Descripción separada, inteligente y bien redactada (contexto, propósito, implicados), nunca frases literales del usuario.
            B. VALORES POR DEFECTO: si el usuario no especificó hora, día exacto (ej. solo dijo "la próxima semana") u otro parámetro y tú elegiste uno, AVÍSALO explícitamente al final de tu respuesta y ofrécele cambiarlo. Ejemplos:
               - "No especificaste hora: quedó a las 9:00 por defecto. ¿La modifico?"
               - "La agendé para el miércoles 7 porque pediste la próxima semana sin día. Dime si la cambio de fecha, o edítala en la Agenda."
            C. Al confirmar agendados, menciona tipo (se agendó una JUNTA/tarea/llamada), fecha completa y hora (o que no fue especificada).
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

    /**
     * Ejecuta TODAS las acciones que el modelo emita en una respuesta (pueden ser varias),
     * con parseo tolerante: acepta "---ACTION:", "ACTION:" o "action:", con o sin guiones,
     * y nunca muestra el protocolo crudo al usuario.
     */
    private suspend fun processLLMActions(reply: String, activeProject: String): String {
        val actionLineRegex = Regex("(?i)^-{0,3}\\s*action\\s*:\\s*([a-zA-Z_]+)\\s*\\|(.*)$")
        val knownActions = setOf("CREATE_TASK", "RESCHEDULE_TASK", "COMPLETE_TASK", "DELETE_TASK", "SAVE_NOTE")

        val confirmations = StringBuilder()
        val keptLines = mutableListOf<String>()

        reply.lines().forEach { line ->
            val trimmed = line.trim()
            val m = actionLineRegex.find(trimmed)
            if (m == null || m.groupValues[1].uppercase(Locale.getDefault()) !in knownActions) {
                keptLines.add(line)
                return@forEach
            }
            val actionType = m.groupValues[1].uppercase(Locale.getDefault())
            val payload = m.groupValues[2].trim().trimEnd('-').trim()
            val conf = executeLLMAction(actionType, payload, activeProject)
            if (conf.isNotBlank()) confirmations.append(conf).append("\n")
        }

        val cleanReply = keptLines.joinToString("\n")
            .replace(Regex("(?m)^\\s*-{3,}\\s*$"), "") // líneas de guiones sueltas
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        if (confirmations.isBlank()) return cleanReply
        return (cleanReply + "\n" + confirmations.toString().trim()).trim()
    }

    private suspend fun executeLLMAction(actionType: String, payload: String, activeProject: String): String {
        if (activeProject == "Todos") {
            return "\n\n*(Nota: En modo 'Todos los proyectos' no se aplican modificaciones. Selecciona el proyecto arriba para ejecutar la acción)*"
        }

        return when (actionType) {
            "CREATE_TASK" -> {
                // ACTION:CREATE_TASK|titulo|prioridad|fechaTexto|tipo|confianza|hora(opcional HH:mm)
                val parts = payload.split("|")
                val title = parts.getOrNull(0)?.trim()?.ifBlank { "Nueva tarea" } ?: "Nueva tarea"
                val priority = parts.getOrNull(1)?.trim()?.uppercase(Locale.getDefault()) ?: "MEDIA"
                val fechaRaw = parts.getOrNull(2)?.trim().orEmpty()
                val horaRaw = parts.getOrNull(5)?.trim().orEmpty()
                // La hora puede venir en el parámetro hora O embebida en fechaTexto:
                // se considera explícita si aparece en cualquiera de los dos.
                val hm = parseHourMinutes("$fechaRaw $horaRaw")
                var due = resolveDateTextToTimestamp(fechaRaw, horaRaw)
                var withTime = hm != null
                if (due <= 0L && hm != null) {
                    // El modelo dejó la fecha vacía pero el usuario SÍ dio hora:
                    // se agenda hoy a esa hora (si aún es futura) o mañana, nunca se pierde.
                    due = nextOccurrenceAtHour(hm)
                    withTime = true
                }
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
                    hasTime = withTime,
                    originReference = "Asistente Conversacional"
                )
                val id = db.taskDao().insertTask(task)
                ragEngine.indexContent(
                    id, "TASK", title, activeProject,
                    "Tarea: $title. Tipo: ${TaskTypes.labelOf(task.taskType)}. Proyecto: $activeProject."
                )
                "\n\n✅ *[Acción ejecutada: ${TaskTypes.labelOf(task.taskType)} \"$title\" agendada en $activeProject para ${formatDueForHumans(due)}]*" +
                    when {
                        due <= 0L -> " *Sin fecha: pendiente de día y hora — dime cuándo la agendo.*"
                        !withTime -> " *Hora no especificada: quedó a las ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(due))} por defecto; dime si la cambio.*"
                        else -> ""
                    }
            }
            "RESCHEDULE_TASK" -> {
                // ACTION:RESCHEDULE_TASK|titulo|fechaTexto
                // fechaTexto puede traer fecha+hora ("mañana a las 10"), solo fecha ("viernes")
                // o SOLO HORA ("10:00", "10 de la mañana"): en ese caso se conserva el día actual
                // y solo se mueve la hora. Nunca se deja la tarea sin fecha por un re-agendado.
                val parts = payload.split("|")
                val titleKeyword = parts.getOrNull(0)?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                val fechaText = parts.getOrNull(1)?.trim().orEmpty()
                val projectTasks = db.taskDao().getTasksByJobSync(activeProject)
                val target = projectTasks.firstOrNull {
                    titleKeyword.isNotBlank() && it.title.lowercase(Locale.getDefault()).contains(titleKeyword)
                } ?: projectTasks.filter { !it.isCompleted }.maxByOrNull { it.createdAt }
                ?: projectTasks.maxByOrNull { it.createdAt }

                if (target != null) {
                    val (due, withTime) = resolveRescheduleDue(fechaText, target.dueTimestamp)
                    if (due > 0L) {
                        db.taskDao().updateTask(
                            target.copy(
                                dueTimestamp = due,
                                hasTime = withTime || target.hasTime
                            )
                        )
                        val horaTxt = if (withTime) "" else " *(sin hora específica: 9:00 por defecto)*"
                        "\n\n✅ *[Acción ejecutada: Tarea \"${target.title}\" re-agendada: ${formatDueForHumans(due)}$horaTxt en $activeProject]*"
                    } else {
                        ""
                    }
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
                // ACTION:SAVE_NOTE|titulo|contenido|categoria(opcional)
                val parts = payload.split("|")
                val title = parts.getOrNull(0)?.trim()?.ifBlank { "Nota de conversación" } ?: "Nota de conversación"
                val content = parts.getOrNull(1)?.trim()?.ifBlank { payload } ?: payload
                val catRaw = parts.getOrNull(2)?.trim()?.uppercase(Locale.getDefault()).orEmpty()
                val category = if (catRaw in listOf("DIRECTIVA", "ESPECIFICACION", "REPORTE")) catRaw else "MEMORIA"

                val doc = DocumentItem(
                    title = title,
                    jobTag = activeProject,
                    category = category,
                    content = content
                )
                val docId = db.documentDao().insertDocument(doc)
                ragEngine.indexContent(docId, "DOCUMENT", title, activeProject, content)
                val catLabel = when (category) {
                    "DIRECTIVA" -> "Directiva"
                    "ESPECIFICACION" -> "Especificación"
                    "REPORTE" -> "Reporte"
                    else -> "Nota"
                }
                "\n\n💾 *[Acción ejecutada: $catLabel \"$title\" guardada en Memoria de $activeProject]*"
            }
            else -> ""
        }
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

        // 0. Preguntas, quejas y seguimientos NUNCA se ejecutan como comandos: van al LLM con historial
        if (isInterrogativeOrFollowUp(inputText)) return null

        // 0.1 Los comandos destructivos (concluir/borrar/re-agendar) SOLO con verbo imperativo
        // al inicio de la frase: evita que "eso no fue lo que te pedí... no te dije que la eliminaras"
        // se interprete como orden de borrar.
        val startsWithImperative = Regex(
            "^(?:por\\s+favor\\s+)?(?:ya\\s+te\\s+)?(?:conclu\\w*|termina\\w*|finaliza\\w*|completa\\w*|borra\\w*|elimina\\w*|quita\\w*|descarta\\w*|reagenda\\w*|reprograma\\w*|mueve|cambia|marca|pon\\b|ya\\s+quedo|listo[,\\s])"
        ).containsMatchIn(lower)

        // 1. Concluir / Terminar / Finalizar Tarea
        val isCompleteIntent = (lower.contains("conclu") || lower.contains("termina") || lower.contains("finaliz") || lower.contains("complet") || lower.contains("hecho")) &&
            (lower.contains("tarea") || lower.contains("esta") || lower.contains("este") || lower.contains("pon como") || lower.contains("marca como"))

        if (isCompleteIntent && startsWithImperative && activeProject != "Todos") {
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

        if (isDeleteIntent && startsWithImperative && activeProject != "Todos") {
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
        val reschedHour = parseHourMinutes(lower)
        val parsedDue = SpanishDateParser.resolveDueTimestamp(if (reschedHour != null) stripHourExpressions(lower) else lower)
        if (isRescheduleIntent && parsedDue != null && startsWithImperative && activeProject != "Todos") {
            val projectTasks = db.taskDao().getTasksByJobSync(activeProject)
            val target = projectTasks.firstOrNull { t -> t.title.lowercase(Locale.getDefault()).isNotBlank() && lower.contains(t.title.lowercase(Locale.getDefault()).take(18)) }
                ?: projectTasks.filter { !it.isCompleted }.maxByOrNull { it.createdAt }
                ?: projectTasks.maxByOrNull { it.createdAt }

            if (target != null) {
                // La hora dicha por el usuario se aplica SIEMPRE; sin hora explícita, 9:00 por defecto
                val due = applyHourToTimestamp(parsedDue, reschedHour)
                db.taskDao().updateTask(
                    target.copy(dueTimestamp = due, hasTime = reschedHour != null || target.hasTime)
                )
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
            val extraction = try {
                extractTaskDetailsViaLLM(inputText, activeProject, history)
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "extractTaskDetails error: ${t.message}", t)
                TaskExtraction.Unavailable
            }
            // El sub-agente determinó que no es una orden de agenda: que responda el LLM con contexto
            if (extraction is TaskExtraction.NotATaskRequest) return null

            val extracted = ((extraction as? TaskExtraction.Details)?.details
                ?: naiveTaskExtraction(inputText, activeProject))
                .let { reconcileWithRawText(it, inputText) }

            val task = WorkTask(
                title = extracted.title,
                description = extracted.description,
                jobTag = activeProject,
                dueTimestamp = extracted.dueTimestamp,
                priority = extracted.priority,
                taskType = extracted.taskType,
                confidence = extracted.confidence,
                hasTime = extracted.hasTime,
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
            val defaultsNotice = buildString {
                if (extracted.dueTimestamp <= 0L) {
                    append("\n\n📌 No especificaste día ni hora: quedó como pendiente sin fecha (no aparece en calendario). Dime la fecha y la re-agendo, o edítala en la Agenda.")
                } else if (!extracted.hasTime) {
                    append("\n\n📌 No especificaste hora: quedó a las ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(extracted.dueTimestamp))} por defecto. Dime si la cambio, o edítala en la Agenda.")
                }
            }
            return "✅ **$typeLabel Agendada en $activeProject:**\n" +
                "• **Título:** ${extracted.title}\n" +
                "• **Tipo:** $typeLabel\n" +
                "• **Prioridad:** ${extracted.priority}\n" +
                "• **Vencimiento:** ${formatDueForHumans(extracted.dueTimestamp)}" + (if (extracted.dueTimestamp > 0 && !extracted.hasTime) " *(sin hora específica)*" else "") + "\n" +
                "• **Confianza:** $confLabel" +
                defaultsNotice +
                "\n\nGuardada en tu agenda y vectorizada en memoria RAG."
        }

        // 5. Guardar directivas / especificaciones / reportes / notas en la Memoria del proyecto
        val storeVerbs = listOf("guarda", "guardar", "almacena", "almacenar", "registra", "registrar", "anota", "anotar", "crea", "crear", "agrega", "agregar")
        val storeNouns = listOf(
            "directiva", "directriz", "lineamiento", "especificaci", "reporte", "nota",
            "informaci", "acuerdo", "resumen", "siguiente", "esta ", "este "
        )
        val isStoreInfoIntent = storeVerbs.any { lower.contains(it) } && storeNouns.any { lower.contains(it) }

        if (isStoreInfoIntent && activeProject != "Todos") {
            // SUB-AGENTE: título y descripción inteligentes + categoría correcta
            val note = try {
                generateNoteDetailsViaLLM(inputText, activeProject, recentHistoryText())
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "generateNoteDetails error: ${t.message}", t)
                null
            } ?: naiveNoteExtraction(inputText)

            val doc = DocumentItem(
                title = note.title,
                jobTag = activeProject,
                category = note.category,
                content = note.description
            )
            val docId = db.documentDao().insertDocument(doc)
            ragEngine.indexContent(docId, "DOCUMENT", doc.title, activeProject, doc.content)

            val catLabel = when (note.category) {
                "DIRECTIVA" -> "Directiva"
                "ESPECIFICACION" -> "Especificación"
                "REPORTE" -> "Reporte"
                else -> "Nota"
            }
            return "💾 **$catLabel guardada en $activeProject:**\n" +
                "• **Título:** ${note.title}\n" +
                "• **Categoría:** $catLabel\n\n" +
                "Ya está disponible en la sección **Memoria** bajo el proyecto **$activeProject**, vectorizada en RAG."
        }

        // 6. Consultar proyectos existentes
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
        groqKey: String? = null,
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
            groqKey = groqKey,
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

    // --- Respaldo completo (Backup) ---
    fun backupFileName(): String =
        "neox-admin-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()) + ".json"

    private val _backupResult = MutableStateFlow<String?>(null)
    val backupResult: StateFlow<String?> = _backupResult.asStateFlow()

    fun clearBackupResult() { _backupResult.value = null }

    /** JSON con TODO: proyectos, tareas, juntas, bóveda, memoria, conversaciones y config del rotador (incluye API keys). */
    suspend fun buildBackupJson(): String = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("app", "Neox Admin")
            root.put("schemaVersion", 1)
            root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date()))

            val jobs = db.jobProjectDao().getAllJobsSync()
            root.put("proyectos", JSONArray(jobs.map { j ->
                JSONObject()
                    .put("name", j.name).put("companyOrClient", j.companyOrClient)
                    .put("colorHex", j.colorHex).put("isPrimary", j.isPrimary).put("createdAt", j.createdAt)
            }))

            val tasks = db.taskDao().getAllTasksSync()
            root.put("tareas", JSONArray(tasks.map { t ->
                JSONObject()
                    .put("title", t.title).put("description", t.description).put("jobTag", t.jobTag)
                    .put("dueTimestamp", t.dueTimestamp).put("priority", t.priority).put("taskType", t.taskType)
                    .put("confidence", t.confidence).put("isCompleted", t.isCompleted).put("status", t.status)
                    .put("originReference", t.originReference).put("createdAt", t.createdAt)
            }))

            val meetings = db.meetingDao().getAllMeetings().first()
            root.put("juntas", JSONArray(meetings.map { m ->
                JSONObject()
                    .put("title", m.title).put("jobTag", m.jobTag).put("dateTimestamp", m.dateTimestamp)
                    .put("executiveSummary", m.executiveSummary).put("myActionItems", m.myActionItems)
                    .put("othersActionItems", m.othersActionItems).put("keyDecisions", m.keyDecisions)
                    .put("rawTranscript", m.rawTranscript).put("quotesJson", m.quotesJson)
                    .put("isConcluded", m.isConcluded)
            }))

            val docs = db.documentDao().getAllDocuments().first()
            root.put("memoria", JSONArray(docs.map { d ->
                JSONObject()
                    .put("title", d.title).put("jobTag", d.jobTag).put("category", d.category)
                    .put("content", d.content).put("createdAt", d.createdAt)
            }))

            val vault = db.vaultDao().getAllVaultEntries().first()
            root.put("boveda", JSONArray(vault.map { v ->
                JSONObject()
                    .put("title", v.title).put("rawContent", v.rawContent).put("sourceType", v.sourceType)
                    .put("jobTag", v.jobTag).put("timestamp", v.timestamp)
            }))

            val sessions = db.conversationDao().getAllSessionsSync()
            val conversations = JSONArray()
            sessions.forEach { s ->
                val msgs = db.chatMsgDao().getMessagesSync(s.id)
                conversations.put(
                    JSONObject()
                        .put("title", s.title).put("jobTag", s.jobTag)
                        .put("createdAt", s.createdAt).put("lastActiveAt", s.lastActiveAt)
                        .put("messages", JSONArray(msgs.map { msg ->
                            JSONObject()
                                .put("sender", msg.sender).put("text", msg.text)
                                .put("modelUsed", msg.modelUsed ?: JSONObject.NULL).put("timestamp", msg.timestamp)
                        }))
                )
            }
            root.put("conversaciones", conversations)

            val cfg = rotator.config
            root.put("rotador", JSONObject()
                .put("enabled", cfg.enabled)
                .put("openRouterApiKey", cfg.openRouterApiKey)
                .put("nvidiaApiKey", cfg.nvidiaApiKey)
                .put("groqApiKey", cfg.groqApiKey)
                .put("localhostEnabled", cfg.localhostEnabled)
                .put("localhostUrl", cfg.localhostUrl)
                .put("localhostModelId", cfg.localhostModelId)
                .put("primaryProvider", cfg.primaryProvider)
                .put("fallbackProvider", cfg.fallbackProvider)
                .put("activeProviderMode", cfg.activeProviderMode)
                .put("defaultCooldownMinutes", cfg.defaultCooldownMinutes)
                .put("deadModelCooldownHours", cfg.deadModelCooldownHours))

            root.put("preferencias", JSONObject()
                .put("retentionDays", prefs.getRetentionDays())
                .put("ttsRate", prefs.getTtsRate().toDouble())
                .put("ttsPitch", prefs.getTtsPitch().toDouble())
                .put("ttsVoiceName", prefs.getTtsVoiceName()))

            root.toString(2)
        } catch (t: Throwable) {
            android.util.Log.e("MainViewModel", "buildBackupJson error: ${t.message}", t)
            "{}"
        }
    }

    fun writeBackupTo(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = buildBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                _backupResult.value = "✅ Respaldo descargado correctamente"
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "writeBackupTo error: ${t.message}", t)
                _backupResult.value = "❌ Error al generar el respaldo: ${t.message}"
            }
        }
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
