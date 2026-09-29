package com.example.ui.screens

import android.Manifest
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
import androidx.compose.foundation.text.KeyboardOptions
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

    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun startListeningSafe(recognizer: SpeechRecognizer) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Tolerancia máxima a silencios (extras reconocidos por el motor de Google aunque no sean API pública)
            putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_DURATION_MILLIS", 10000)
            putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_DURATION_MILLIS", 10000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000)
        }
        try {
            recognizer.startListening(intent)
        } catch (t: Throwable) {
            isListening = false
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(hasPermission) {
        var recognizer: SpeechRecognizer? = null
        if (hasPermission) {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                engineAvailable = false
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { isListening = true }
                    override fun onBeginningOfSpeech() { isListening = true }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() { isListening = false }

                    override fun onError(error: Int) {
                        isListening = false
                        val fatal = error == SpeechRecognizer.ERROR_CLIENT ||
                            error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        // Reinicio automático: la sesión JAMÁS se cierra por pausas o timeouts
                        if (wantsListening && !fatal) {
                            val r = recognizer ?: return
                            mainHandler.postDelayed({ if (wantsListening) startListeningSafe(r) }, 300)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!list.isNullOrEmpty() && list[0].isNotBlank()) {
                            finalizedText = if (finalizedText.isBlank()) list[0].trim() else (finalizedText + " " + list[0].trim())
                            partialText = ""
                        }
                        if (wantsListening) {
                            val r = recognizer ?: return
                            mainHandler.postDelayed({ if (wantsListening) startListeningSafe(r) }, 250)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!list.isNullOrEmpty()) partialText = list[0]
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListeningSafe(recognizer)
            }
        }
        onDispose {
            wantsListening = false
            mainHandler.removeCallbacksAndMessages(null)
            recognizer?.destroy()
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
                        text = if (!engineAvailable) "Dictado no disponible" else if (isListening) "Escuchando..." else "En pausa de micrófono",
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
                        "Se necesita permiso de micrófono para el dictado. Toca Cancelar y vuelve a abrir el micrófono para concederlo.",
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
                    ),
                    keyboardOptions = KeyboardOptions.Default
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            wantsListening = !wantsListening
                        }) {
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
                        text = if (wantsListening) "Micrófono activo" else "Micrófono pausado",
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
                onDismiss()
            }) {
                Text("Cancelar", color = Slate400)
            }
        }
    )
}
