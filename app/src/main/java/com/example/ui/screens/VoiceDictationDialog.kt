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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.data.speech.SpeechContextPolisher
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import kotlinx.coroutines.delay

/**
 * Controlador de reconocimiento continuo (emisor puro de segmentos, sin estado de texto).
 *
 * Estrategia anti-cortes: cada segmento de dictado corre en una instancia NUEVA de
 * SpeechRecognizer; al terminar o fallar se destruye y se crea otra tras un backoff.
 * La sesión de dictado nunca muere: el usuario puede pausar todo lo que quiera.
 */
private class DictationController(private val context: Context, private val handler: Handler) {

    var wantsListening = false
    var onListeningChange: ((Boolean) -> Unit)? = null
    var onPartial: ((String) -> Unit)? = null
    var onFinal: ((String) -> Unit)? = null
    /** La sesión terminó sin resultados oficiales: conserva el parcial antes de reiniciar. */
    var onSessionAborted: (() -> Unit)? = null
    var onRms: ((Float) -> Unit)? = null
    var onUnavailable: (() -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var backoffSteps = 0
    private var sessionDeliveredResults = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { onListeningChange?.invoke(true) }
        override fun onBeginningOfSpeech() { backoffSteps = 0; onListeningChange?.invoke(true) }
        override fun onRmsChanged(rmsdB: Float) { onRms?.invoke(rmsdB) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { onListeningChange?.invoke(false) }

        override fun onError(error: Int) {
            onListeningChange?.invoke(false)
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) return
            // Corte por pausa/silencio sin resultados: conservar lo hablado como texto firme
            if (!sessionDeliveredResults) onSessionAborted?.invoke()
            scheduleRestart()
        }

        override fun onResults(results: Bundle?) {
            sessionDeliveredResults = true
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
        sessionDeliveredResults = false
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
        putExtra("android.speech.extra.DICTATION_MODE", true)
        putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_DURATION_MILLIS", 10000)
        putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_DURATION_MILLIS", 10000)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
    }
}

/**
 * Unión de segmentos SIEMPRE con espacio (nunca "proyectoel") y sin duplicar texto.
 * Función pura: nunca encoge el texto acumulado.
 */
private fun appendSegment(current: String, segment: String): String {
    val seg = segment.trim()
    if (seg.isEmpty()) return current
    if (current.isBlank()) return seg
    if (current.endsWith(seg, ignoreCase = true)) return current
    if (current.length > seg.length && current.endsWith(" $seg")) return current
    return current.trim() + " " + seg
}

/**
 * Dictado por voz con sesión persistente.
 *
 * El texto acumulado vive en rememberSaveable: sobrevive a recreaciones del composable
 * (e incluso a rotación de pantalla), por lo que NINGÚN reinicio del reconocedor puede
 * borrar lo ya dictado. Solo el botón Limpiar (con doble confirmación) lo borra.
 */
@Composable
fun VoiceDictationDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    val context = LocalContext.current
    // FUENTE DE VERDAD del dictado: sobrevive cualquier recreación del modal
    var finalizedText by rememberSaveable { mutableStateOf("") }
    var partialText by rememberSaveable { mutableStateOf("") }

    var isListening by remember { mutableStateOf(false) }
    var micLevel by remember { mutableStateOf(0f) }
    var engineAvailable by remember { mutableStateOf(true) }
    var wantsListening by remember { mutableStateOf(true) }
    var confirmClear by remember { mutableStateOf(false) }
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

    // Re-conectar callbacks SIEMPRE que el composable se recomponga: nunca se pierde el vínculo
    DisposableEffect(controller, hasPermission) {
        if (hasPermission) {
            controller.onListeningChange = { listening -> isListening = listening }
            controller.onRms = { dB -> micLevel = ((dB + 2f) / 12f).coerceIn(0f, 1f) }
            controller.onPartial = { p -> partialText = p }
            controller.onFinal = { f ->
                finalizedText = appendSegment(finalizedText, f)
                partialText = ""
            }
            controller.onSessionAborted = {
                if (partialText.isNotBlank()) {
                    finalizedText = appendSegment(finalizedText, partialText)
                    partialText = ""
                }
            }
            controller.onUnavailable = { engineAvailable = false }
            controller.start()
        }
        onDispose {
            controller.destroy()
            controller.onListeningChange = null
            controller.onRms = null
            controller.onPartial = null
            controller.onFinal = null
            controller.onSessionAborted = null
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

    fun clearAll() {
        finalizedText = ""
        partialText = ""
        confirmClear = false
    }

    fun sendNow() {
        wantsListening = false
        controller.pause()
        val toSend = finalizedText.ifBlank { partialText }
        onSend(SpeechContextPolisher.polishDictation(toSend))
    }

    // Auto-cancelación de la confirmación de borrado
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(2500)
            confirmClear = false
        }
    }

    val composedText = if (partialText.isBlank()) finalizedText
    else if (finalizedText.isBlank()) partialText
    else "$finalizedText $partialText"
    val wordCount = composedText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    val scrollState = rememberScrollState()

    // Auto-scroll al último texto dictado
    LaunchedEffect(composedText.length) {
        if (composedText.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(if (isListening) CyanNeon.copy(alpha = 0.5f) else Slate700)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ───────── Cabecera ─────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate950)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !engineAvailable -> Slate800
                                    isListening -> CyanNeon.copy(alpha = 0.2f)
                                    wantsListening -> AmberWarning.copy(alpha = 0.15f)
                                    else -> Slate800
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            tint = when {
                                !engineAvailable -> Slate400
                                isListening -> CyanNeon
                                wantsListening -> AmberWarning
                                else -> Slate400
                            },
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                !engineAvailable -> "Dictado no disponible"
                                !hasPermission -> "Permiso de micrófono"
                                isListening -> "Escuchando..."
                            else -> if (wantsListening) "Reconectando micrófono..." else "Micrófono en pausa"
                            },
                            color = when {
                                isListening -> CyanNeon
                                wantsListening -> AmberWarning
                                else -> Color.White
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                !engineAvailable -> "No hay motor de reconocimiento en este dispositivo"
                                !hasPermission -> "Cancela y vuelve a abrir el micrófono para concederlo"
                                else -> "Haz todas las pausas que necesites: nada se pierde ni se envía solo"
                            },
                            color = Slate400,
                            fontSize = 10.sp
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar dictado", tint = Slate400, modifier = Modifier.size(19.dp))
                    }
                }

                // ───────── Medidor de nivel de voz ─────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate950)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(16) { i ->
                        val active = !wantsListening && i == 0 || (wantsListening && micLevel * 16 > i)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (i < 8) (6 + i).dp else (6 + (15 - i)).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        !wantsListening -> Slate800
                                        active -> if (i > 12) AmberWarning else CyanNeon
                                        else -> Slate800
                                    }
                                )
                        )
                    }
                }

                // ───────── Transcripción ─────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Slate950)
                        .border(1.dp, Slate800, RoundedCornerShape(14.dp))
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (composedText.isBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Slate700, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Habla y tu dictado aparecerá aquí...", color = Slate700, fontSize = 12.sp)
                        }
                    } else {
                        Column {
                            if (finalizedText.isNotBlank()) {
                                Text(
                                    text = finalizedText,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                            if (partialText.isNotBlank()) {
                                Text(
                                    text = if (finalizedText.isNotBlank()) " $partialText" else partialText,
                                    color = CyanNeon.copy(alpha = 0.75f),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                // ───────── Contador y ayuda ─────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$wordCount palabra" + if (wordCount == 1) "" else "s",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (confirmClear) "Toca de nuevo en Rojo para confirmar el borrado" else "El texto se conserva entre pausas",
                        color = if (confirmClear) AmberWarning else Slate700,
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ───────── Acciones ─────────
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    Button(
                        onClick = { sendNow() },
                        enabled = composedText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enviar al asistente", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { togglePauseResume() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (wantsListening) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (wantsListening) AmberWarning else CyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (wantsListening) "Pausar micrófono" else "Reanudar",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (confirmClear) clearAll() else confirmClear = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = if (confirmClear) AmberWarning else Slate400,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (confirmClear) "¿Confirmar borrado?" else "Limpiar",
                                color = if (confirmClear) AmberWarning else Slate400,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
