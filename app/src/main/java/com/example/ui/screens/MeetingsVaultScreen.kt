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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MeetingNote
import com.example.data.model.VaultEntry
import com.example.ui.MainViewModel
import com.example.ui.theme.OnCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.VioletAccent
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeetingsVaultScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Captura y Dictado", "Minutas")

    var selectedMinutasSubTab by remember { mutableIntStateOf(0) } // 0: Minutas, 1: Bóveda 10 Días

    val jobs by viewModel.allJobs.collectAsStateWithLifecycle()
    var selectedProjectTag by remember { mutableStateOf("") }
    val effectiveJobTag = remember(jobs, selectedProjectTag) {
        if (selectedProjectTag.isNotBlank() && (jobs.any { it.name == selectedProjectTag } || selectedProjectTag == "General")) {
            selectedProjectTag
        } else {
            jobs.firstOrNull { it.isPrimary }?.name ?: jobs.firstOrNull()?.name ?: "General"
        }
    }

    var minutasProjectFilter by remember { mutableStateOf("Todos") }

    val meetings by viewModel.allMeetings.collectAsStateWithLifecycle()
    val vaultEntries by viewModel.allVaultEntries.collectAsStateWithLifecycle()
    val pendingQueue by viewModel.pendingQueue.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isMeetingProcessing.collectAsStateWithLifecycle()
    val meetingItems by viewModel.allMeetingItems.collectAsStateWithLifecycle()
    val processedMeetingId by viewModel.meetingProcessedEvent.collectAsStateWithLifecycle()

    var liveTranscriptText by remember { mutableStateOf("") }
    var showMeetingDictation by remember { mutableStateOf(false) }
    var meetingToEdit by remember { mutableStateOf<MeetingNote?>(null) }
    var openItemsMeeting by remember { mutableStateOf<MeetingNote?>(null) }

    // Al terminar el procesamiento de una minuta, abre la revisión de ítems detectados
    LaunchedEffect(processedMeetingId) {
        val id = processedMeetingId ?: return@LaunchedEffect
        val meeting = meetings.firstOrNull { it.id == id }
        if (meeting != null) {
            // Espera breve a que los ítems extraídos lleguen por el Flow antes de abrir
            kotlinx.coroutines.withTimeoutOrNull(1500) {
                snapshotFlow { meetingItems.any { it.meetingId == id } }.first { it }
            }
            openItemsMeeting = meeting
        }
        viewModel.consumeMeetingProcessedEvent()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Main Tab selector
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Slate900,
            contentColor = CyanNeon,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = CyanNeon
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == index) CyanNeon else Slate400
                        )
                    }
                )
            }
        }

        if (selectedTab == 0) {
            // TAB 0: Grabadora de Juntas (dictado dual: Whisper IA + Google)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Slate900),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(CyanNeon.copy(alpha = 0.35f))
                        )
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // ── Encabezado ──
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(CyanNeon.copy(alpha = 0.15f))
                                        .border(1.dp, CyanNeon.copy(alpha = 0.45f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(22.dp))
                                }
                                Column {
                                    Text("Grabadora de Juntas", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Whisper IA por frases · Google de respaldo · pausas ilimitadas",
                                        color = Slate400,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── Proyecto asignado ──
                            Text("Proyecto asignado a la junta", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val displayProjects = if (jobs.isEmpty()) listOf("General") else jobs.map { it.name }
                                items(displayProjects) { pName ->
                                    val isSelected = pName == effectiveJobTag
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) CyanNeon else Slate800)
                                            .border(1.dp, if (isSelected) CyanNeon else Slate700, RoundedCornerShape(16.dp))
                                            .clickable { selectedProjectTag = pName }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = pName,
                                            color = if (isSelected) OnCyan else Slate400,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // ── Transcripción ──
                            val wordCount = liveTranscriptText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Transcripción de la junta", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (wordCount == 0) "Sin contenido aún" else "$wordCount palabra" + (if (wordCount == 1) "" else "s"),
                                    color = Slate700,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = liveTranscriptText,
                                onValueChange = { liveTranscriptText = it },
                                placeholder = {
                                    Text(
                                        "Graba la junta con IA o pega aquí lo conversado. La IA detectará temas, extraerá tus acciones y guardará citas textuales...",
                                        color = Slate400,
                                        fontSize = 13.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(150.dp).testTag("transcript_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedBorderColor = CyanNeon,
                                    unfocusedBorderColor = Slate700
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // ── Acciones ──
                            Button(
                                onClick = { showMeetingDictation = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("voice_record_btn")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Dictar / Grabar Junta", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    if (liveTranscriptText.isNotBlank()) {
                                        viewModel.captureMeetingAudioOrText(
                                            jobTag = effectiveJobTag,
                                            rawTranscript = liveTranscriptText
                                        )
                                        liveTranscriptText = ""
                                    }
                                },
                                enabled = liveTranscriptText.isNotBlank() && !isProcessing,
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (liveTranscriptText.isNotBlank() && !isProcessing) CyanNeon.copy(alpha = 0.7f) else Slate700
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (liveTranscriptText.isNotBlank() && !isProcessing) CyanNeon else Slate700
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("process_summary_btn")
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyanNeon, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generando minuta...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Procesar y Generar Minuta con IA",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "La grabación por frases funciona con la pantalla abierta: cada pausa natural cierra una frase y Whisper la transcribe al instante. Nada se pierde aunque pauses mucho tiempo. Al terminar, presiona Procesar y la IA genera la minuta con temas, acciones y citas textuales.",
                                color = Slate700,
                                fontSize = 9.sp,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        } else {
            // TAB 1: Minutas (Contiene las Juntas y Minutas Generadas y la sección de Bóveda 10 Días)
            Column(modifier = Modifier.fillMaxSize()) {
                // Sub-tabs dentro de Minutas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Slate900)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedMinutasSubTab == 0) CyanNeon else Color.Transparent)
                            .clickable { selectedMinutasSubTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Minutas Generadas (${meetings.size})",
                            color = if (selectedMinutasSubTab == 0) OnCyan else Slate400,
                            fontSize = 12.sp,
                            fontWeight = if (selectedMinutasSubTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedMinutasSubTab == 1) EmeraldSuccess else Color.Transparent)
                            .clickable { selectedMinutasSubTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bóveda 10 Días (${vaultEntries.size})",
                            color = if (selectedMinutasSubTab == 1) OnCyan else Slate400,
                            fontSize = 12.sp,
                            fontWeight = if (selectedMinutasSubTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                if (selectedMinutasSubTab == 0) {
                    // Subsección 1: Juntas y Minutas Generadas con su análisis completo
                    val filteredMeetings = remember(meetings, minutasProjectFilter) {
                        if (minutasProjectFilter == "Todos") meetings
                        else meetings.filter { it.jobTag.equals(minutasProjectFilter, ignoreCase = true) }
                    }
                    val itemsByMeeting = remember(meetingItems) { meetingItems.groupBy { it.meetingId } }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Project Filter Chips for Minutas
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Filtrar minutas por proyecto:", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        val isSel = minutasProjectFilter == "Todos"
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSel) CyanNeon else Slate800)
                                                .border(1.dp, if (isSel) CyanNeon else Slate700, RoundedCornerShape(16.dp))
                                                .clickable { minutasProjectFilter = "Todos" }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Todos (${meetings.size})",
                                                color = if (isSel) OnCyan else Slate400,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                    items(jobs) { jobItem ->
                                        val isSel = minutasProjectFilter == jobItem.name
                                        val count = meetings.count { it.jobTag.equals(jobItem.name, ignoreCase = true) }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (isSel) CyanNeon else Slate800)
                                                .border(1.dp, if (isSel) CyanNeon else Slate700, RoundedCornerShape(16.dp))
                                                .clickable { minutasProjectFilter = jobItem.name }
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${jobItem.name} ($count)",
                                                color = if (isSel) OnCyan else Slate400,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (filteredMeetings.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Mic, contentDescription = null, tint = Slate700, modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = if (minutasProjectFilter == "Todos") "No tienes minutas generadas aún." else "No hay minutas registradas para el proyecto \"$minutasProjectFilter\".",
                                            color = Slate400,
                                            fontSize = 13.sp
                                        )
                                        Text("Ve a 'Captura y Dictado' para registrar una reunión.", color = Slate700, fontSize = 11.sp)
                                    }
                                }
                            }
                        } else {
                            items(filteredMeetings, key = { it.id }) { meeting ->
                                MeetingCard(
                                    meeting = meeting,
                                    onToggleConcluded = { viewModel.toggleMeetingConcluded(meeting) },
                                    onEdit = { meetingToEdit = meeting },
                                    pendingItemCount = itemsByMeeting[meeting.id].orEmpty().count { it.status == "PENDIENTE" },
                                    onOpenItems = { openItemsMeeting = meeting }
                                )
                            }
                        }
                    }
                } else {
                    // Subsección 2: Bóveda 10 Días (Cero Pérdida y Cola de Procesamiento)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Slate900),
                                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldSuccess.copy(alpha = 0.5f)))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Box(
                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(EmeraldSuccess.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                        }
                                        Column {
                                            Text("Bóveda Inmutable de Cero Pérdida", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text("Retención garantizada de 10 días", color = EmeraldSuccess, fontSize = 11.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Cada palabra que dictas o capturas en Teams se almacena aquí de inmediato. Si el modelo falla por límite de cuota o falta de red, la información permanece protegida en la bóveda.",
                                        color = Slate400,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }

                        if (pendingQueue.isNotEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Slate900),
                                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AmberWarning))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                                            Text("Cola Pendiente de Procesamiento (${pendingQueue.size})", color = AmberWarning, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("Elementos pendientes de extracción agéntica.", color = Slate400, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        item {
                            Text(
                                text = "Registros Crudos Protegidos (${vaultEntries.size})",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (vaultEntries.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                    Text("No hay registros en la bóveda todavía.", color = Slate400, fontSize = 12.sp)
                                }
                            }
                        } else {
                            items(vaultEntries, key = { it.id }) { entry ->
                                VaultEntryCard(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showMeetingDictation) {
        VoiceDictationDialog(
            onDismiss = { showMeetingDictation = false },
            onSend = { text ->
                showMeetingDictation = false
                liveTranscriptText = if (liveTranscriptText.isBlank()) text
                else liveTranscriptText.trim().trimEnd('.', ' ') + " " + text
            },
            smartTranscribe = viewModel.smartDictationTranscriber,
            sendLabel = "Insertar en la junta",
            idleHint = "Habla la junta con naturalidad, o toca aquí para escribirla..."
        )
    }

    openItemsMeeting?.let { meeting ->
        val itemsForMeeting = meetingItems.filter { it.meetingId == meeting.id }
        MeetingItemsDialog(
            meeting = meeting,
            items = itemsForMeeting,
            onDismiss = { openItemsMeeting = null },
            onAdd = { viewModel.addMeetingItemToTasks(it) },
            onDiscard = { viewModel.discardMeetingItem(it) },
            onSetDate = { item, fecha -> viewModel.updateMeetingItemSchedule(item, fecha) }
        )
    }

    meetingToEdit?.let { meeting ->
        EditMeetingDialog(
            meeting = meeting,
            jobList = jobs.map { it.name },
            onDismiss = { meetingToEdit = null },
            onSave = { newTitle, newJob ->
                viewModel.updateMeetingDetails(meeting, newTitle, newJob)
                meetingToEdit = null
            }
        )
    }
}

@Composable
private fun EditMeetingDialog(
    meeting: MeetingNote,
    jobList: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, jobTag: String) -> Unit
) {
    var title by remember(meeting.id) { mutableStateOf(meeting.title) }
    var selectedJob by remember(meeting.id) { mutableStateOf(meeting.jobTag) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                Text("Editar Junta", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título de la junta", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (jobList.isNotEmpty()) {
                    Text("Proyecto / Trabajo:", color = Slate400, fontSize = 12.sp)
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.height(100.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(jobList) { job ->
                            val isSelected = selectedJob == job
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CyanNeon.copy(alpha = 0.18f) else Slate800)
                                    .border(
                                        1.dp,
                                        if (isSelected) CyanNeon else Slate700,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedJob = job }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(job, color = if (isSelected) CyanNeon else Slate400, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onSave(title, selectedJob) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan)
            ) {
                Text("Guardar Cambios", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Slate400)
            }
        }
    )
}

@Composable
fun MeetingCard(
    meeting: MeetingNote,
    onToggleConcluded: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    pendingItemCount: Int = 0,
    onOpenItems: (() -> Unit)? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(meeting.dateTimestamp))

    val quotesList = remember(meeting.quotesJson) {
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(meeting.quotesJson)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (_: Exception) {}
        list
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(meeting.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))

                // Ítems detectados de la junta: badge ámbar mientras queden pendientes por revisar
                if (onOpenItems != null) {
                    Box {
                        IconButton(onClick = onOpenItems, modifier = Modifier.size(28.dp).testTag("open_items_btn")) {
                            Icon(Icons.Default.Checklist, contentDescription = "Ítems detectados de la junta", tint = CyanNeon, modifier = Modifier.size(16.dp))
                        }
                        if (pendingItemCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(AmberWarning)
                                    .border(1.dp, Slate900, CircleShape)
                            )
                        }
                    }
                }

                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar junta", tint = Slate400, modifier = Modifier.size(15.dp))
                    }
                }
                if (onToggleConcluded != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (meeting.isConcluded) EmeraldSuccess.copy(alpha = 0.2f) else Slate800)
                            .clickable { onToggleConcluded() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (meeting.isConcluded) "✓ Concluida" else "○ En Seguimiento",
                            color = if (meeting.isConcluded) EmeraldSuccess else Slate400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(dateStr, color = Slate400, fontSize = 11.sp)

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = meeting.executiveSummary,
                color = Slate400,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (meeting.myActionItems.isNotBlank() && meeting.myActionItems != "Ninguna asignada") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyanNeon.copy(alpha = 0.1f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Mis Acciones Asignadas:", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(meeting.myActionItems, color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }

                    if (meeting.othersActionItems.isNotBlank() && meeting.othersActionItems != "Ninguna registrada") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(VioletAccent.copy(alpha = 0.1f))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Acciones de Otros:", color = VioletAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(meeting.othersActionItems, color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }

                    if (meeting.keyDecisions.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Decisiones:", color = AmberWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(meeting.keyDecisions, color = Slate400, fontSize = 11.sp)
                            }
                        }
                    }

                    if (quotesList.isNotEmpty()) {
                        Column {
                            Text("Citas Textuales de Respaldo:", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(3.dp))
                            quotesList.forEach { q ->
                                Text(
                                    text = "« $q »",
                                    color = Color(0xFF67E8F9),
                                    fontSize = 11.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "Ocultar detalles" else "Ver acciones y citas textuales",
                    color = CyanNeon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyanNeon,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun VaultEntryCard(entry: VaultEntry) {
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))
    val elapsedDays = ((System.currentTimeMillis() - entry.timestamp) / (1000 * 3600 * 24)).toInt()
    val remainingDays = (entry.retentionDays - elapsedDays).coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                    Text(entry.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(EmeraldSuccess.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("$remainingDays días restantes", color = EmeraldSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Fecha: $dateStr | Origen: ${entry.sourceType}", color = Slate400, fontSize = 10.sp)

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Slate800)
                    .padding(8.dp)
            ) {
                Text(
                    text = entry.rawContent,
                    color = Slate400,
                    fontSize = 11.sp,
                    maxLines = 3
                )
            }
        }
    }
}
