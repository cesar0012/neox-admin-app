package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.model.VaultEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FloatingMeetingService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRecording = false
    private val recordedBuffer = StringBuilder()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentJobTag: String = "General"
    private var statusTextView: TextView? = null

    companion object {
        const val CHANNEL_ID = "omniwork_floating_channel"
        const val NOTIFICATION_ID = 9021
        const val EXTRA_JOB_TAG = "EXTRA_JOB_TAG"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val extraJob = intent?.getStringExtra(EXTRA_JOB_TAG)
        if (!extraJob.isNullOrBlank()) {
            currentJobTag = extraJob
            updateStatusText(if (isRecording) "Grabando..." else "Listo")
        }
        return START_NOT_STICKY
    }

    private fun updateStatusText(subText: String) {
        statusTextView?.text = "[$currentJobTag] $subText"
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val notification = createNotification("Captura flotante activa sobre Teams / Meet")
            startForeground(NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            android.util.Log.e("FloatingMeetingService", "Error starting foreground service: ${t.message}", t)
        }
        try {
            initFloatingWidget()
        } catch (t: Throwable) {
            android.util.Log.e("FloatingMeetingService", "Error initializing floating widget: ${t.message}", t)
        }
        try {
            initSpeechRecognizer()
        } catch (t: Throwable) {
            android.util.Log.e("FloatingMeetingService", "Error initializing speech recognizer: ${t.message}", t)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Captura Flotante de Juntas",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OmniWork — Modo Junta Flotante")
            .setContentText(text)
            .setSmallIcon(com.example.R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun initFloatingWidget() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 150
        }

        // Programmatic sleek dark pill UI
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            gravity = Gravity.CENTER_VERTICAL

            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A")) // Slate 900
                setStroke(3, Color.parseColor("#00E5FF")) // CyanNeon
                cornerRadius = 40f
            }
            background = bg
        }

        val appIcon = ImageView(this).apply {
            setImageResource(com.example.R.drawable.ic_agenda_nav)
            setColorFilter(Color.parseColor("#00E5FF"))
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                val launchIntent = Intent(this@FloatingMeetingService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launchIntent)
            }
        }

        val statusText = TextView(this).apply {
            text = "[$currentJobTag] Listo"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(12, 0, 16, 0)
            setOnClickListener {
                scope.launch {
                    val db = AppDatabase.getInstance(this@FloatingMeetingService)
                    val jobs = db.jobProjectDao().getAllJobsSync().map { it.name }
                    val allNames = if (jobs.isEmpty()) listOf("General") else jobs
                    val currentIndex = allNames.indexOf(currentJobTag)
                    val nextIndex = if (currentIndex == -1 || currentIndex == allNames.size - 1) 0 else currentIndex + 1
                    currentJobTag = allNames[nextIndex]
                    withContext(Dispatchers.Main) {
                        updateStatusText(if (isRecording) "Grabando..." else "Listo")
                        Toast.makeText(this@FloatingMeetingService, "Proyecto: $currentJobTag", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        statusTextView = statusText

        val recordBtn = Button(this).apply {
            text = "Grabar"
            setTextColor(Color.parseColor("#00363D"))
            textSize = 11f
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor("#00E5FF"))
                cornerRadius = 24f
            }
            background = btnBg
            setPadding(20, 8, 20, 8)

            setOnClickListener {
                if (!isRecording) {
                    startRecording()
                    text = "Grabando..."
                    updateStatusText("Escuchando...")
                    btnBg.setColor(Color.parseColor("#EF4444")) // Red recording
                    setTextColor(Color.WHITE)
                } else {
                    stopRecording()
                    text = "Grabar"
                    updateStatusText("Pausado")
                    btnBg.setColor(Color.parseColor("#00E5FF"))
                    setTextColor(Color.parseColor("#00363D"))
                }
            }
        }

        val saveBtn = Button(this).apply {
            text = "Bóveda"
            setTextColor(Color.WHITE)
            textSize = 11f
            val saveBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 24f
            }
            background = saveBg
            setPadding(16, 8, 16, 8)

            setOnClickListener {
                saveRecordedMeeting()
            }
        }

        val closeBtn = ImageView(this).apply {
            setImageResource(com.example.R.drawable.ic_close_nav)
            setColorFilter(Color.parseColor("#94A3B8"))
            setPadding(12, 8, 4, 8)
            setOnClickListener {
                stopSelf()
            }
        }

        container.addView(appIcon)
        container.addView(statusText)
        container.addView(recordBtn)
        container.addView(saveBtn)
        container.addView(closeBtn)

        // Draggable touch listener
        container.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                            windowManager?.updateViewLayout(floatingView, layoutParams)
                            return true
                        }
                    }
                }
                return false
            }
        })

        floatingView = container
        windowManager?.addView(floatingView, layoutParams)
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (isRecording) {
                        // Restart listening loop
                        startRecording()
                    }
                }
                override fun onError(error: Int) {
                    if (isRecording) {
                        startRecording()
                    }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        if (recordedBuffer.isNotEmpty()) recordedBuffer.append(" ")
                        recordedBuffer.append(text)
                    }
                    if (isRecording) {
                        startRecording()
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
    }

    private fun startRecording() {
        isRecording = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "ES"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {}
    }

    private fun stopRecording() {
        isRecording = false
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
    }

    private fun saveRecordedMeeting() {
        val captured = recordedBuffer.toString().trim()
        val textToSave = if (captured.isBlank()) "Junta grabada en segundo plano sobre Microsoft Teams." else captured

        scope.launch {
            val db = AppDatabase.getInstance(this@FloatingMeetingService)
            val timestamp = System.currentTimeMillis()
            db.vaultDao().insertVaultEntry(
                VaultEntry(
                    title = "Junta Flotante ($currentJobTag)",
                    rawContent = textToSave,
                    sourceType = "LIVE_DICTATION",
                    jobTag = currentJobTag,
                    timestamp = timestamp
                )
            )
        }

        recordedBuffer.clear()
        Toast.makeText(this, "Junta resguardada en Bóveda para $currentJobTag", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        speechRecognizer?.destroy()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
        }
    }
}
