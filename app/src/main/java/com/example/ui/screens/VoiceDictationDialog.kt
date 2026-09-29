package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.speech.SpeechContextPolisher
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

/**
 * Controlador de reconocimiento continuo.
 *
 * Estrategia anti-cortes: cada segmento de dictado corre en una instancia NUEVA de
 * SpeechRecognizer. Al terminar (resultados) o fallar (timeout, NO_MATCH, ERROR_CLIENT,
 * RECOGNIZER_BUSY...) se destruye la instancia y se crea otra tras un pequeño backoff.
 * Así la sesión de dictado nunca muere: el usuario puede pausar todo lo que quiera
 * y solo se envía con el botón Enviar.
 */
private class DictationController(private val context: Context, private val handler: Handler) {

    var wantsListening = false
    var onListeningChange: ((Boolean) -> Unit)? = null
    var onPartial: ((String) -> Unit)? = null
    var onFinal: ((String) -> Unit)? = null
    var onUnavailable: (() -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var backoffSteps = 0 // evita bucles agresivos cuando hay silencio prolongado

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onListeningChange?.invoke(true) }
        override fun onBeginningOfSpeech() { backoffSteps = 0; onListeningChange?.invoke(true) }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { onListeningChange?.invoke(false) }

        override fun onError(error: Int) {
            onListeningChange?.invoke(false)
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return
            // Cualquier otro error (timeout por pausa, NO_MATCH, CLIENT, BUSY...) reinicia sesión
            scheduleRestart()
        }

        override fun onResults(results: Bundle?) {
            onListeningChange?.invoke(false)
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = list?.firstOrNull()?.trim().orEmpty()
            if (text.isNotEmpty()) onFinal?.invoke(text)
            scheduleRestart(shortDelay = true)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!list.isNullOrEmpty() && list[0].isNotBlank()) {
                backoffSteps = 0
                onPartial?.invoke(list[0].trim())
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun start() {
        wantsListening = true
        handler.removeCallbacksAndMessages(null)
        startSessionNow()
    }

    fun pause() {
        wantsListening = false
        handler.removeCallbacksAndMessages(null)
        destroySession()
    }

    fun destroy() {
        wantsListening = false
        handler.removeCallbacksAndMessages(null)
        destroySession()
    }

    private fun scheduleRestart(shortDelay: Boolean = false) {
        handler.removeCallbacksAndMessages(null)
        if (!wantsListening) return
        val base = if (shortDelay) 250L else 450L
        val delay = base + backoffSteps * 400L
        backoffSteps = (backoffSteps + 1).coerceAtMost(6)
        handler.postDelayed({ startSessionNow() }, delay)
    }

    private fun startSessionNow() {
        if (!wantsListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            wantsListening = false
            onUnavailable?.invoke()
            return
        }
        destroySession()
        try {
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = r
            r.setRecognitionListener(listener)
            r.startListening(listenIntent())
        } catch (t: Throwable) {
            handler.postDelayed({ if (wantsListening) startSessionNow() }, 900L)
        }
    }

    private fun destroySession() {
        recognizer?.let { r ->
            try { r.cancel() } catch (_: Exception) {}
            try { r.destroy() } catch (_: Exception) {}
        }
        recognizer = null
        onListeningChange?.invoke(false)
    }

    private fun listenIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Extras reconocidos por los motores (no API pública) para tolerar silencios largos
        putExtra("android.speech.extra.DICTATION_MODE", true)
        putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_DURATION_MILLIS", 10000)
        putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_DURATION_MILLIS", 10000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
    }
}

/**
 * Dictado por voz con sesión persistente: NO se cierra solo por pausas,
 * acumula todo lo hablado y solo se envía cuando el usuario presiona "Enviar".
 */
@Composable
fun VoiceDictationDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val context = LocalContext.current
    var finalizedText by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var engineAvailable by remember { mutableStateOf(true) }
    var wantsListening by remember { mutableStateOf(true) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val controller = remember {
        DictationController(context.applicationContext, Handler(Looper.getMainLooper()))
    }

    DisposableEffect(controller, hasPermission) {
        if (hasPermission) {
            controller.onListeningChange = { listening -> isListening = listening }
            controller.onPartial = { p -> partialText = p }
            controller.onFinal = { f ->
                finalizedText = if (finalizedText.isBlank()) f else (finalizedText + " " + f)
                partialText = ""
            }
            controller.onUnavailable = { engineAvailable = false }
            controller.start()
        }
        onDispose {
            controller.destroy()
            controller.onListeningChange = null
            controller.onPartial = null
            controller.onFinal = null
            controller.onUnavailable = null
        }
    }

    fun togglePauseResume() {
        if (wantsListening) {
            wantsListening = false
            controller.pause()
        } else {
            wantsListening = true
            partialText = ""
            controller.start()
        }
    }

    val composedText = if (partialText.isBlank()) finalizedText
    else if (finalizedText.isBlank()) partialText
    else "$finalizedText $partialText"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (isListening) CyanNeon.copy(alpha = 0.2f) else Slate800),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (isListening) CyanNeon else Slate400,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        text = when {
                            !engineAvailable -> "Dictado no disponible"
                            !hasPermission -> "Permiso de micrófono"
                            isListening -> "Escuchando..."
                            wantsListening -> "Reconectando micrófono..."
                            else -> "Micrófono en pausa"
                        },
                        color = if (isListening) CyanNeon else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (!engineAvailable) "No hay motor de reconocimiento en este dispositivo"
                        else "Puedes hacer pausas y pensar: nada se envía hasta que presiones Enviar",
                        color = Slate400,
                        fontSize = 10.sp
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasPermission) {
                    Text(
                        "Se necesita permiso de micrófono para el dictado. Cancela, vuelve a abrir el micrófono y concede el permiso.",
                        color = AmberWarning,
                        fontSize = 12.sp
                    )
                }

                OutlinedTextField(
                    value = composedText,
                    onValueChange = { },
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    placeholder = {
                        Text("Lo que hables aparecerá aquí...", color = Slate700, fontSize = 13.sp)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700,
                        focusedContainerColor = Slate800,
                        unfocusedContainerColor = Slate800
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { togglePauseResume() }) {
                            Icon(
                                if (wantsListening) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (wantsListening) "Pausar micrófono" else "Reanudar micrófono",
                                tint = if (wantsListening) AmberWarning else CyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = {
                            finalizedText = ""
                            partialText = ""
                        }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Limpiar texto",
                                tint = Slate400,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = when {
                            !wantsListening -> "Micrófono pausado"
                            isListening -> "Escuchando · habla con libertad"
                            else -> "Preparando escucha..."
                        },
                        color = if (wantsListening) CyanNeon else AmberWarning,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    wantsListening = false
                    controller.pause()
                    val toSend = finalizedText.ifBlank { partialText }
                    onSend(SpeechContextPolisher.polishDictation(toSend))
                },
                enabled = composedText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D))
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("Enviar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                wantsListening = false
                controller.pause()
                onDismiss()
            }) {
                Text("Cancelar", color = Slate400)
            }
        }
    )
}
