package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.rotator.LLMRotator
import com.example.data.speech.AudioSegmentRecorder
import com.example.data.speech.WhisperTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Captura de junta en segundo plano (Meet / Teams / Zoom por parlante o audífonos):
 * servicio en primer plano con microfono que graba por frases (AudioSegmentRecorder),
 * transcribe cada frase con Whisper via Groq y acumula la transcripcio'n en vivo.
 * La app puede cerrarse o cambiarse a la app de la llamada sin que la captura muera.
 *
 * Al terminar: la transcripcion queda en [pendingFinal] y una notificacion lleva a
 * la seccion Juntas para generar la minuta con el pipeline agéntico completo.
 */
class MeetingCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.example.service.meetingcapture.START"
        const val ACTION_STOP = "com.example.service.meetingcapture.STOP"
        const val EXTRA_TITLE = "capture_title"
        const val NOTIF_ID = 9102
        const val CHANNEL_ID = "omniwork_capturas_channel"

        val isRunning = MutableStateFlow(false)
        val liveTranscript = MutableStateFlow("")

        @Volatile var pendingFinalTitle: String? = null
        @Volatile var pendingFinalTranscript: String? = null

        /** La UI lo consume al llegar a Juntas tras "Terminar". */
        fun consumeFinal(): Pair<String, String>? {
            val t = pendingFinalTranscript ?: return null
            val title = pendingFinalTitle ?: ""
            pendingFinalTranscript = null
            pendingFinalTitle = null
            return title to t
        }

        fun start(context: Context, title: String) {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) return
            val intent = Intent(context, MeetingCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, MeetingCaptureService::class.java).apply { action = ACTION_STOP }
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<ByteArray>(Channel.UNLIMITED)
    private val processing = AtomicInteger(0)
    private var recorder: AudioSegmentRecorder? = null
    private var captureTitle: String = ""
    @Volatile private var vaultEntryId: Long = 0L
    @Volatile private var lastVoiceAt: Long = System.currentTimeMillis()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                captureTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                startForeground(NOTIF_ID, buildOngoingNotification(0))
                if (recorder == null) startCapture()
            }
            ACTION_STOP -> stopCapture()
        }
        // STICKY: si el sistema mata el servicio, lo reinicia (la transcripción ya
        // persistida en la bóveda no se pierde y la captura continúa)
        return START_STICKY
    }

    private fun startCapture() {
        isRunning.value = true
        liveTranscript.value = ""
        initChannel()

        val key = runCatching {
            LLMRotator.getInstance(application).config.groqApiKey
        }.getOrDefault("")

        recorder = AudioSegmentRecorder(
            onRms = { /* el medidor vive en la notificación con conteo de palabras */ },
            onSegment = { wav -> queue.trySend(wav) },
            onError = { Log.e(TAG, "recorder: $it") }
        ).apply { start() }

        // Respaldo en bóveda DESDE EL INICIO: si el servicio muere o el teléfono se
        // apaga, todo lo transcrito hasta ese momento ya está guardado.
        serviceScope.launch {
            try {
                vaultEntryId = com.example.data.db.AppDatabase.getInstance(application)
                    .vaultDao().insertVaultEntry(
                        com.example.data.model.VaultEntry(
                            title = captureTitle.ifBlank { "Captura: junta en curso" },
                            rawContent = "",
                            sourceType = "MEET_BG",
                            jobTag = "General",
                            timestamp = System.currentTimeMillis(),
                            retentionDays = 10
                        )
                    )
            } catch (t: Throwable) {
                Log.e(TAG, "vault insert: ${t.message}")
            }
        }

        scope.launch {
            for (wav in queue) {
                processing.incrementAndGet()
                try {
                    if (key.isNotBlank()) {
                        // Reintento con espera: la transcripción nunca se descarta por un
                        // error de red o límite de tasa momentáneo de la API
                        var result: Result<String>? = null
                        for (attempt in 1..3) {
                            val r = WhisperTranscriber.transcribe(wav, key)
                            if (r.isSuccess) { result = r; break }
                            result = r
                            if (attempt < 3) delay(if (attempt == 1) 2_500L else 8_000L)
                        }
                        result?.onSuccess { text ->
                            if (text.isNotBlank()) {
                                lastVoiceAt = System.currentTimeMillis()
                                liveTranscript.value = appendPhrase(liveTranscript.value, text)
                                persistToVault()
                                updateOngoingNotification()
                            }
                        } ?: Log.e(TAG, "transcribe falló tras reintentos: ${result?.exceptionOrNull()?.message}")
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "transcribe: ${t.message}")
                } finally {
                    processing.decrementAndGet()
                }
            }
        }
    }

    /** Persiste la transcripción acumulada en la entrada de la bóveda (en vivo). */
    private fun persistToVault() {
        val id = vaultEntryId
        if (id <= 0L) return
        val content = liveTranscript.value
        serviceScope.launch {
            try {
                com.example.data.db.AppDatabase.getInstance(application)
                    .vaultDao().updateVaultEntryContent(id, content)
            } catch (_: Exception) {}
        }
    }

    private fun stopCapture() {
        val r = recorder
        recorder = null
        scope.launch {
            try { r?.stop() } catch (_: Exception) {}
            delay(400) // margen del flush de la última frase
            withTimeoutOrNull(25_000) {
                while (processing.get() > 0) delay(250)
            }
            val text = liveTranscript.value.trim()
            if (vaultEntryId > 0L) {
                // Título definitivo y contenido final en la bóveda
                runCatching {
                    com.example.data.db.AppDatabase.getInstance(application)
                        .vaultDao().updateVaultEntryContent(vaultEntryId, text)
                }
            }
            if (text.isNotEmpty()) {
                pendingFinalTranscript = text
                pendingFinalTitle = captureTitle
                showFinalNotification(text)
            } else {
                showFinalNotification(null)
            }
            isRunning.value = false
            stopSelf()
        }
    }

    private fun appendPhrase(current: String, phrase: String): String {
        val p = phrase.trim()
        if (p.isEmpty()) return current
        if (current.isBlank()) return p
        if (current.endsWith(p, ignoreCase = true)) return current
        return "$current $p"
    }

    private fun initChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Captura de Juntas", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Grabación y transcripción en segundo plano de juntas en línea"
                }
            )
        }
    }

    private fun openAppExtras(): Intent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        putExtra("nav_meetings", true)
    }

    private fun buildOngoingNotification(words: Int, silentTooLong: Boolean = false): android.app.Notification {
        initChannel()
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, MeetingCaptureService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Grabando junta${if (captureTitle.isNotBlank()) ": ${captureTitle.take(40)}" else ""}")
            .setContentText(
                when {
                    silentTooLong -> "Sin voz detectada en los últimos minutos — revisa parlante/audífonos y permisos de micrófono"
                    words > 0 -> "$words palabras — puedes cambiar de app o bloquear la pantalla"
                    else -> "Transcribiendo con Whisper... puedes cambiar de app"
                }
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(this, 2, openAppExtras(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            .addAction(0, "⏹ Terminar y generar minuta", stopPi)
            .build()
    }

    private fun updateOngoingNotification() {
        val words = liveTranscript.value.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildOngoingNotification(words))
        // Vigía: si pasan más de 3 min sin detectar voz nueva, avisar en la notificación
        serviceScope.launch {
            delay(190_000)
            if (isRunning.value && System.currentTimeMillis() - lastVoiceAt > 180_000) {
                val w2 = liveTranscript.value.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                val nm2 = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm2.notify(NOTIF_ID, buildOngoingNotification(w2, silentTooLong = true))
            }
        }
    }

    private fun showFinalNotification(text: String?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = if (text != null) {
            val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            NotificationCompat.Builder(this, ReminderChannelHolder.REMINDERS_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Junta capturada ($words palabras)")
                .setContentText("Toca para revisarla y generar la minuta")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(this, 3, openAppExtras(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                )
                .build()
        } else {
            NotificationCompat.Builder(this, ReminderChannelHolder.REMINDERS_CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Captura finalizada")
                .setContentText("No se detectó voz suficiente")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        }
        nm.notify(NOTIF_ID + 1, notif)
    }

    override fun onDestroy() {
        try { recorder?.stop() } catch (_: Exception) {}
        recorder = null
        isRunning.value = false
        scope.cancel()
        super.onDestroy()
    }

    private object ReminderChannelHolder {
        val REMINDERS_CHANNEL = "omniwork_reminders_channel"
    }

    private val TAG = "MeetingCaptureSvc"
}
