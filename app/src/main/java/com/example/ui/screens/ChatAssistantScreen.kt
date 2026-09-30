package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.rag.RAGQueryResult
import com.example.ui.ChatMessage
import com.example.ui.MainViewModel
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun ChatAssistantScreen(viewModel: MainViewModel) {
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessingChat.collectAsStateWithLifecycle()
    val rotatorStatus by viewModel.rotatorStatus.collectAsStateWithLifecycle()
    val assistantProject by viewModel.assistantSelectedProject.collectAsStateWithLifecycle()
    val jobs by viewModel.allJobs.collectAsStateWithLifecycle()
    val sessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()

    var inputPrompt by remember { mutableStateOf("") }
    var autoSpeakEnabled by remember { mutableStateOf(true) }
    var showTtsSettings by remember { mutableStateOf(false) }
    var showDictation by remember { mutableStateOf(false) }
    var bannerOverride by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ─────────────────── Barra superior del Asistente ───────────────────
        val bannerSeen by viewModel.assistantBannerSeen.collectAsStateWithLifecycle()
        val amber = Color(0xFFF59E0B)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Slate900)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Toolbar: identidad + acciones rápidas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(CyanNeon.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Neox Asistente", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = when {
                                isProcessing -> "Procesando tu mensaje..."
                                assistantProject == "Todos" -> "Análisis global de todos tus proyectos"
                                else -> "Trabajando en: $assistantProject"
                            },
                            color = Slate400,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }

                // Toggle de voz
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (autoSpeakEnabled) CyanNeon.copy(alpha = 0.16f) else Slate800)
                        .border(1.dp, if (autoSpeakEnabled) CyanNeon else Slate700, RoundedCornerShape(20.dp))
                        .clickable { autoSpeakEnabled = !autoSpeakEnabled }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("voice_response_toggle")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (autoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "Voz",
                            tint = if (autoSpeakEnabled) CyanNeon else Slate400,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (autoSpeakEnabled) "Voz" else "Silencio",
                            color = if (autoSpeakEnabled) CyanNeon else Slate400,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                ToolbarIconButton(Icons.Default.Add, "Nueva conversación", CyanNeon) { viewModel.startNewSession() }
                ToolbarIconButton(Icons.Default.Tune, "Ajustes de voz", CyanNeon) { showTtsSettings = true }
                ToolbarIconButton(
                    Icons.Default.HelpOutline,
                    "Información del ámbito y la conversación",
                    Slate400
                ) { bannerOverride = !bannerOverride }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selector de proyecto (chips)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    AssistantChip(
                        text = "Todos",
                        icon = Icons.Default.Shield,
                        selected = assistantProject == "Todos",
                        accent = amber,
                        onAccent = Color(0xFF1F1400),
                        onClick = { viewModel.setAssistantProject("Todos") }
                    )
                }

                val projectNames = if (jobs.isEmpty()) listOf("Trabajo Principal") else jobs.map { it.name }
                items(projectNames) { pName ->
                    AssistantChip(
                        text = pName,
                        icon = Icons.Default.Folder,
                        selected = assistantProject == pName,
                        accent = CyanNeon,
                        onAccent = Color(0xFF00363D),
                        onClick = { viewModel.setAssistantProject(pName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selector de conversación (temas separados y persistidos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var sessionMenuOpen by remember { mutableStateOf(false) }
                val currentSession = sessions.firstOrNull { it.id == activeSessionId }

                Text("En:", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800)
                            .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                            .clickable { sessionMenuOpen = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp))
                            Text(
                                text = currentSession?.title?.take(28) ?: "Conversación",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar conversación", tint = Slate400, modifier = Modifier.size(14.dp))
                        }
                    }
                    DropdownMenu(expanded = sessionMenuOpen, onDismissRequest = { sessionMenuOpen = false }) {
                        if (sessions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Sin conversaciones", fontSize = 12.sp) },
                                onClick = {}
                            )
                        }
                        sessions.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        s.title.take(40),
                                        fontSize = 12.sp,
                                        color = if (s.id == activeSessionId) CyanNeon else Color.White,
                                        maxLines = 1
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar conversación",
                                        tint = Slate400,
                                        modifier = Modifier.size(16.dp).clickable { viewModel.deleteSession(s.id) }
                                    )
                                },
                                onClick = {
                                    viewModel.switchSession(s.id)
                                    sessionMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${sessions.size} conversación" + if (sessions.size == 1) "" else "es",
                    color = Slate700,
                    fontSize = 9.sp
                )
            }

            // Banner informativo: solo la primera vez; se re-abre con el botón ?
            if (!bannerSeen || bannerOverride) {
                val isTodos = assistantProject == "Todos"
                val accent = if (isTodos) amber else CyanNeon

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (isTodos) Icons.Default.Shield else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isTodos) "Modo 'Todos los proyectos' protegido" else "Proyecto activo: $assistantProject",
                                    color = accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Ocultar información",
                                tint = Slate400,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clickable {
                                        viewModel.markAssistantBannerSeen()
                                        bannerOverride = false
                                    }
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (isTodos) {
                                "Aquí solo se permite consulta y análisis global de tus proyectos. Para guardar, agendar, editar o borrar algo, selecciona arriba el proyecto específico."
                            } else {
                                "Tus instrucciones directas (\"guarda esta tarea\", \"agenda una junta el viernes\", \"concluye esta tarea\") se aplican a este proyecto. Cambia de conversación abajo si quieres separar temas."
                            },
                            color = Slate400,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )

                        if (!bannerSeen) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        viewModel.markAssistantBannerSeen()
                                        bannerOverride = false
                                    }
                                ) {
                                    Text("Entendido", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chat message history
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                CleanChatBubble(
                    message = msg,
                    onSpeak = { viewModel.speak(msg.text) }
                )
            }

            if (isProcessing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyanNeon, strokeWidth = 2.dp)
                        Text(
                            text = if (assistantProject == "Todos") "Analizando datos globales de todos los proyectos..." else "Consultando memoria RAG y tareas de $assistantProject...",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Dynamic Quick Suggestions
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = if (assistantProject == "Todos") {
                listOf(
                    "¿Qué pendientes tengo en todos mis proyectos?",
                    "¿Cuál es la tarea más urgente?",
                    "Resume el panorama general de mis trabajos",
                    "¿Qué acuerdos recientes tenemos registrados?"
                )
            } else {
                listOf(
                    "Guarda esta tarea: Revisar entregables",
                    "Pon como en concluida esta tarea",
                    "Borra esta tarea",
                    "¿Cuáles son las tareas y acuerdos de $assistantProject?"
                )
            }
            items(suggestions) { sugg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Slate900)
                        .border(1.dp, Slate800, RoundedCornerShape(16.dp))
                        .clickable {
                            inputPrompt = sugg
                            viewModel.sendChatMessage(sugg, autoSpeak = autoSpeakEnabled)
                            inputPrompt = ""
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(sugg, color = Slate400, fontSize = 11.sp)
                }
            }
        }

        // Bottom input bar
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { autoSpeakEnabled = !autoSpeakEnabled },
                    modifier = Modifier.size(36.dp).testTag("chat_voice_speaker_btn")
                ) {
                    Icon(
                        if (autoSpeakEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeMute,
                        contentDescription = "Alternar voz",
                        tint = if (autoSpeakEnabled) CyanNeon else Slate400,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { showDictation = true },
                    modifier = Modifier.testTag("voice_assistant_mic_btn")
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Hablar al asistente",
                        tint = CyanNeon
                    )
                }

                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Escribe o habla al asistente...", color = Slate400, fontSize = 13.sp) },
                    modifier = Modifier.weight(1f).testTag("chat_input_text"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    maxLines = 4
                )

                IconButton(
                    onClick = {
                        if (inputPrompt.isNotBlank() && !isProcessing) {
                            val text = inputPrompt
                            inputPrompt = ""
                            viewModel.sendChatMessage(text, autoSpeak = autoSpeakEnabled)
                        }
                    },
                    enabled = inputPrompt.isNotBlank() && !isProcessing,
                    modifier = Modifier.testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        tint = if (inputPrompt.isNotBlank() && !isProcessing) CyanNeon else Slate700
                    )
                }
            }
        }
    }

    if (showTtsSettings) {
        TtsSettingsDialog(viewModel = viewModel, onDismiss = { showTtsSettings = false })
    }

    if (showDictation) {
        VoiceDictationDialog(
            onDismiss = { showDictation = false },
            onSend = { text ->
                showDictation = false
                viewModel.sendChatMessage(text, autoSpeak = autoSpeakEnabled)
            }
        )
    }
}

@Composable
private fun TtsSettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val savedRate by viewModel.ttsRate.collectAsStateWithLifecycle()
    val savedPitch by viewModel.ttsPitch.collectAsStateWithLifecycle()
    val savedVoice by viewModel.ttsVoiceName.collectAsStateWithLifecycle()
    var localRate by remember { mutableStateOf(savedRate) }
    var localPitch by remember { mutableStateOf(savedPitch) }
    var localVoice by remember { mutableStateOf<String?>(null) } // null = usar la voz guardada
    val voices = remember { viewModel.getSpanishVoiceOptions() }
    var voiceMenuOpen by remember { mutableStateOf(false) }
    val effectiveVoice = localVoice ?: savedVoice
    val currentVoiceLabel = voices.firstOrNull { it.first == effectiveVoice }?.second
        ?: if (effectiveVoice.isBlank()) "Predeterminada (es-ES)" else effectiveVoice

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                Text("Ajustes de Voz", color = CyanNeon, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Velocidad: x" + "%.2f".format(localRate), color = Slate400, fontSize = 12.sp)
                Slider(
                    value = localRate,
                    onValueChange = { localRate = it },
                    valueRange = 0.5f..2.0f,
                    onValueChangeFinished = { viewModel.applyTtsPreview(localRate, localPitch, localVoice) }
                )
                Text("Tono: x" + "%.2f".format(localPitch), color = Slate400, fontSize = 12.sp)
                Slider(
                    value = localPitch,
                    onValueChange = { localPitch = it },
                    valueRange = 0.5f..2.0f,
                    onValueChangeFinished = { viewModel.applyTtsPreview(localRate, localPitch, localVoice) }
                )
                Text("Voz (${voices.size} disponibles en español):", color = Slate400, fontSize = 12.sp)
                Box {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800)
                            .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                            .clickable { voiceMenuOpen = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(currentVoiceLabel, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir voz", tint = Slate400, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Predeterminada (es-ES)", fontSize = 12.sp) },
                            onClick = {
                                localVoice = ""
                                viewModel.applyTtsPreview(localRate, localPitch, localVoice)
                                voiceMenuOpen = false
                            }
                        )
                        voices.forEach { v ->
                            DropdownMenuItem(
                                text = { Text(v.second, fontSize = 12.sp) },
                                onClick = {
                                    localVoice = v.first
                                    viewModel.applyTtsPreview(localRate, localPitch, localVoice)
                                    voiceMenuOpen = false
                                }
                            )
                        }
                    }
                }

                // Botón de prueba: escucha la voz ANTES de guardar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanNeon.copy(alpha = 0.15f))
                        .border(1.dp, CyanNeon, RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.applyTtsPreview(localRate, localPitch, localVoice)
                            viewModel.speakSample()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                        Text("Probar cómo suena", color = CyanNeon, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.updateTtsSettings(rate = localRate, pitch = localPitch, voiceName = localVoice ?: savedVoice)
                onDismiss()
            }) { Text("Guardar", color = CyanNeon) }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.restoreTtsSettings()
                onDismiss()
            }) { Text("Cancelar", color = Slate400) }
        }
    )
}

@Composable
private fun ToolbarIconButton(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Slate800)
            .border(1.dp, Slate700, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun AssistantChip(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) accent else Slate800)
            .border(1.dp, if (selected) accent else Slate700, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) onAccent else accent,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = text,
            color = if (selected) onAccent else Slate400,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun CleanChatBubble(message: ChatMessage, onSpeak: () -> Unit) {
    val isUser = message.sender == "USER"
    var expandedCitation by remember { mutableStateOf<RAGQueryResult?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) Color(0xFF0369A1) else Slate900
            ),
            border = if (isUser) null else CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.95f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(15.dp))
                            Text("OmniWork Asistente", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = onSpeak, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Escuchar", tint = Slate400, modifier = Modifier.size(15.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                if (!isUser && message.citations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Fuentes Referenciadas:", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(message.citations) { cit ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Slate800)
                                    .border(1.dp, Slate700, RoundedCornerShape(6.dp))
                                    .clickable {
                                        expandedCitation = if (expandedCitation == cit) null else cit
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${cit.chunk.title.take(22)} (${cit.chunk.dateString})",
                                    color = Color(0xFF67E8F9),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = expandedCitation != null) {
                        expandedCitation?.let { cit ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = Slate800)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Cita Textual:", color = CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("« ${cit.chunk.exactQuote} »", color = Color.White, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
