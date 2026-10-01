package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.rag.RAGQueryResult
import com.example.ui.ChatMessage
import com.example.ui.MainViewModel
import com.example.ui.theme.BubbleUserBg
import com.example.ui.theme.OnBubbleUser
import com.example.ui.theme.OnCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

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
                        Text("Neox Asistente", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                        onAccent = OnCyan,
                        onClick = { viewModel.setAssistantProject(pName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Selector de conversación: cada proyecto tiene SUS PROPIAS conversaciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                var sessionMenuOpen by remember { mutableStateOf(false) }
                val projectSessions = sessions.filter { it.jobTag.equals(assistantProject, ignoreCase = true) }
                val currentSession = projectSessions.firstOrNull { it.id == activeSessionId }
                    ?: sessions.firstOrNull { it.id == activeSessionId }

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
                                color = TextPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Cambiar conversación", tint = Slate400, modifier = Modifier.size(14.dp))
                        }
                    }
                    DropdownMenu(expanded = sessionMenuOpen, onDismissRequest = { sessionMenuOpen = false }) {
                        if (projectSessions.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Sin conversaciones en este proyecto", fontSize = 12.sp) },
                                onClick = {}
                            )
                        }
                        projectSessions.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        s.title.take(40),
                                        fontSize = 12.sp,
                                        color = if (s.id == activeSessionId) CyanNeon else TextPrimary,
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
                    text = "${projectSessions.size} en $assistantProject",
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
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
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
    val effectiveVoice = localVoice ?: savedVoice
    val currentVoiceLabel = voices.firstOrNull { it.first == effectiveVoice }?.second
        ?: if (effectiveVoice.isBlank()) "Predeterminada (es-ES)" else effectiveVoice

    fun closeRestoring() {
        viewModel.restoreTtsSettings()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { closeRestoring() },
        containerColor = Slate900,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(CyanNeon.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                }
                Column {
                    Text("Ajustes de Voz", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Prueba antes de guardar; Cancelar revierte todo", color = Slate400, fontSize = 10.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // ── Velocidad ──
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(15.dp))
                            Text("Velocidad", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanNeon.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("x" + "%.2f".format(localRate), color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Slider(
                        value = localRate,
                        onValueChange = { localRate = it },
                        valueRange = 0.5f..2.0f,
                        onValueChangeFinished = { viewModel.applyTtsPreview(localRate, localPitch, localVoice) }
                    )
                }

                // ── Tono ──
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(15.dp))
                            Text("Tono", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanNeon.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("x" + "%.2f".format(localPitch), color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Slider(
                        value = localPitch,
                        onValueChange = { localPitch = it },
                        valueRange = 0.5f..2.0f,
                        onValueChangeFinished = { viewModel.applyTtsPreview(localRate, localPitch, localVoice) }
                    )
                }

                // ── Voz (lista estilo radio) ──
                Text(
                    text = "Voz · ${voices.size} en español",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Slate950)
                        .border(1.dp, Slate800, RoundedCornerShape(10.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        VoiceOptionRow(
                            label = "Predeterminada (es-ES)",
                            subLabel = "Voz del sistema",
                            selected = effectiveVoice.isBlank(),
                            onClick = {
                                localVoice = ""
                                viewModel.applyTtsPreview(localRate, localPitch, localVoice)
                            }
                        )
                    }
                    items(voices) { v ->
                        VoiceOptionRow(
                            label = v.second,
                            subLabel = v.first,
                            selected = effectiveVoice == v.first,
                            onClick = {
                                localVoice = v.first
                                viewModel.applyTtsPreview(localRate, localPitch, localVoice)
                            }
                        )
                    }
                }

                // ── Botón de prueba ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyanNeon.copy(alpha = 0.15f))
                        .border(1.dp, CyanNeon, RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.speakSample(
                                rate = localRate,
                                pitch = localPitch,
                                voiceName = localVoice ?: savedVoice
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                        Text("Probar cómo suena", color = CyanNeon, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.updateTtsSettings(rate = localRate, pitch = localPitch, voiceName = localVoice ?: savedVoice)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Guardar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { closeRestoring() }) {
                Text("Cancelar", color = Slate400)
            }
        }
    )
}

@Composable
private fun VoiceOptionRow(label: String, subLabel: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) CyanNeon.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) CyanNeon else Slate700, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(CyanNeon)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                color = if (selected) CyanNeon else TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
            Text(subLabel, color = Slate700, fontSize = 9.sp, maxLines = 1)
        }
    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CleanChatBubble(message: ChatMessage, onSpeak: () -> Unit) {
    val isUser = message.sender == "USER"
    var expandedCitation by remember { mutableStateOf<RAGQueryResult?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    fun copyMessage() {
        clipboard.setText(AnnotatedString(message.text))
        android.widget.Toast.makeText(context, "Mensaje copiado", android.widget.Toast.LENGTH_SHORT).show()
    }

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
                containerColor = if (isUser) BubbleUserBg else Slate900
            ),
            border = if (isUser) null else CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800)),
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.85f else 0.95f)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { copyMessage() }
                )
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
                        Row {
                            IconButton(onClick = { copyMessage() }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar mensaje", tint = Slate400, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = onSpeak, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Escuchar", tint = Slate400, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    color = if (isUser) OnBubbleUser else TextPrimary,
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
                                    Text("« ${cit.chunk.exactQuote} »", color = TextPrimary, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
