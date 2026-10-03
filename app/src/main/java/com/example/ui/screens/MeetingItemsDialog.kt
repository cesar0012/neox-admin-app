package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MeetingNote
import com.example.data.model.MeetingTaskItem
import com.example.data.model.TaskTypes
import com.example.data.notifications.NotificationLeads
import com.example.ui.theme.OnCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.example.ui.theme.VioletAccent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pop-up de pantalla completa para revisar los ítems detectados en una junta:
 *
 * - El usuario decide cuáles agregar a SUS tareas (nada se agrega automáticamente).
 * - Fecha y hora se corrigen con DATE/TIME PICKERS (solo fechas futuras) o chips rápidos.
 * - El título de la detección también es editable.
 * - Los ítems sin fecha aparecen resaltados en ROJO hasta definirla; al agregar,
 *   el ítem queda tachado como "Agregada". También se pueden descartar.
 */
@Composable
fun MeetingItemsDialog(
    meeting: MeetingNote,
    items: List<MeetingTaskItem>,
    onDismiss: () -> Unit,
    onAdd: (MeetingTaskItem) -> Unit,
    onDiscard: (MeetingTaskItem) -> Unit,
    onSetSchedule: (MeetingTaskItem, Long, Boolean) -> Unit,
    onRename: (MeetingTaskItem, String) -> Unit,
    onSetOwner: (MeetingTaskItem, String) -> Unit,
    defaultNotifLeads: Set<Int> = emptySet(),
    onSetLeads: (MeetingTaskItem, String) -> Unit
) {
    val pending = items.filter { it.status == "PENDIENTE" }
    val added = items.filter { it.status == "AGREGADA" }
    val indefinidos = pending.count { it.owner == "INDEFINIDO" }
    // En lote solo se agregan las marcadas mías y con fecha
    val pendingWithDate = pending.filter { it.owner == "YO" && it.dueTimestamp > 0 }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(Slate700))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ───────── Cabecera ─────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate950)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (pending.isNotEmpty()) AmberWarning.copy(alpha = 0.15f)
                                else EmeraldSuccess.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (pending.isNotEmpty()) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (pending.isNotEmpty()) AmberWarning else EmeraldSuccess,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ítems Detectados en la Junta", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${meeting.title} · ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(meeting.dateTimestamp))}",
                            color = Slate400,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Slate400, modifier = Modifier.size(19.dp))
                    }
                }

                // ───────── Resumen ─────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate950)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatChip("${items.size} detectados", CyanNeon)
                    StatChip("${pending.size} pendientes", if (pending.isEmpty()) Slate400 else AmberWarning)
                    StatChip("${added.size} agregadas", EmeraldSuccess)
                    if (indefinidos > 0) StatChip("$indefinidos sin dueño", RoseError)
                }

                // ───────── Lista de ítems ─────────
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (items.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Slate700, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("No se detectaron ítems en esta junta.", color = Slate400, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    items(items, key = { it.id }) { item ->
                        MeetingItemRow(
                            item = item,
                            onAdd = onAdd,
                            onDiscard = onDiscard,
                            onSetSchedule = onSetSchedule,
                            onRename = onRename,
                            onSetOwner = onSetOwner,
                            defaultNotifLeads = defaultNotifLeads,
                            onSetLeads = onSetLeads
                        )
                    }

                    if (pendingWithDate.size >= 2) {
                        item {
                            Button(
                                onClick = { pendingWithDate.forEach(onAdd) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = OnCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Agregar mis tareas con fecha (${pendingWithDate.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    item { Spacer(modifier = Modifier.height(60.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MeetingItemRow(
    item: MeetingTaskItem,
    onAdd: (MeetingTaskItem) -> Unit,
    onDiscard: (MeetingTaskItem) -> Unit,
    onSetSchedule: (MeetingTaskItem, Long, Boolean) -> Unit,
    onRename: (MeetingTaskItem, String) -> Unit,
    onSetOwner: (MeetingTaskItem, String) -> Unit,
    defaultNotifLeads: Set<Int>,
    onSetLeads: (MeetingTaskItem, String) -> Unit
) {
    val isAdded = item.status == "AGREGADA"
    val sinFecha = item.dueTimestamp <= 0L
    val esMia = item.owner == "YO"

    var editingSchedule by remember(item.id) { mutableStateOf(false) }
    var editingTitle by remember(item.id) { mutableStateOf(false) }
    var editingNotif by remember(item.id) { mutableStateOf(false) }
    var titleText by remember(item.id, item.title) { mutableStateOf(item.title) }
    val itemLeads by remember(item.id, item.notifLeadsCsv) {
        mutableStateOf(NotificationLeads.effectiveFor(item.notifLeadsCsv, defaultNotifLeads))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isAdded -> Slate950
                sinFecha -> RoseError.copy(alpha = 0.05f)
                else -> Slate950
            }
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                when {
                    isAdded -> Slate800
                    sinFecha -> RoseError.copy(alpha = 0.6f)
                    else -> Slate800
                }
            )
        )
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // ── Título + tipo + prioridad (editable con el lápiz) ──
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    itemTypeIcon(item.taskType),
                    contentDescription = null,
                    tint = if (isAdded) Slate700 else typeColor(item.taskType),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = item.title,
                    color = if (isAdded) Slate700 else TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isAdded) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when (item.priority) {
                        "ALTA" -> "▲"; "BAJA" -> "▼"; else -> "●"
                    },
                    color = when (item.priority) {
                        "ALTA" -> RoseError; "BAJA" -> Slate400; else -> AmberWarning
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!isAdded) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar título",
                        tint = Slate400,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { editingTitle = !editingTitle }
                            .padding(2.dp)
                    )
                }
            }

            // Cita textual de respaldo (compacta)
            if (item.contextQuote.isNotBlank()) {
                Text(
                    text = "« ${item.contextQuote} »",
                    color = Slate400,
                    fontSize = 9.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ── Notificaciones del ítem (cuándo avisar) ──
            if (!isAdded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (editingNotif) CyanNeon.copy(alpha = 0.15f) else Slate800)
                            .border(1.dp, if (editingNotif) CyanNeon else Slate700, RoundedCornerShape(9.dp))
                            .clickable { editingNotif = !editingNotif }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Cuándo notificar",
                            tint = if (item.notifLeadsCsv.isBlank()) Slate400 else CyanNeon,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            if (item.notifLeadsCsv.isBlank()) "Avisar: omisión (${NotificationLeads.summary(itemLeads)})"
                            else "Avisar: ${NotificationLeads.summary(itemLeads)}",
                            color = if (item.notifLeadsCsv.isBlank()) Slate400 else CyanNeon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                if (editingNotif) {
                    NotifLeadsEditor(
                        selected = itemLeads,
                        onSelectionChange = { newLeads ->
                            onSetLeads(item, NotificationLeads.toCsv(newLeads))
                        }
                    )
                }
            }

            // ── Dueño del compromiso (atribución del agente, corregible con un toque) ──
            if (!isAdded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OwnerChip(
                        label = "Mía",
                        selected = esMia,
                        color = CyanNeon,
                        onClick = { onSetOwner(item, "YO") }
                    )
                    OwnerChip(
                        label = "De otro",
                        selected = item.owner == "OTRO",
                        color = VioletAccent,
                        onClick = { onSetOwner(item, "OTRO") }
                    )
                    if (item.owner == "INDEFINIDO") {
                        Text("¿de quién es?", color = AmberWarning, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Fecha/hora: renglón propio y botón Cambiar SIEMPRE visible ──
            when {
                isAdded -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(13.dp))
                        Text("Agregada a tus tareas", color = EmeraldSuccess, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            if (sinFecha) Icons.Default.Warning else Icons.Default.Event,
                            contentDescription = null,
                            tint = if (sinFecha) RoseError else CyanNeon,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (sinFecha) "Sin fecha definida"
                            else SimpleDateFormat("EEEE d 'de' MMMM · HH:mm", Locale("es", "ES"))
                                .format(Date(item.dueTimestamp))
                                .replaceFirstChar { it.uppercase(Locale.getDefault()) },
                            color = if (sinFecha) RoseError else CyanNeon,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { editingSchedule = !editingSchedule },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (sinFecha) RoseError.copy(alpha = 0.7f) else CyanNeon.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (sinFecha) RoseError else CyanNeon),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (sinFecha) "Definir" else "Cambiar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // Avisos DEBAJO del renglón de fecha (no empujan el botón)
                    when {
                        sinFecha -> Text(
                            "Define la fecha (y hora si aplica) para poder agregarla a tus tareas.",
                            color = RoseError.copy(alpha = 0.85f),
                            fontSize = 9.sp
                        )
                        !item.hasTime -> Text(
                            "Sin hora dicha en la junta — queda a las 09:00 por defecto.",
                            color = AmberWarning,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // ── Editor de título ──
            if (editingTitle && !isAdded) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (titleText.isNotBlank()) onRename(item, titleText)
                            editingTitle = false
                        },
                        enabled = titleText.isNotBlank() && titleText != item.title
                    ) {
                        Text("Guardar", color = if (titleText.isNotBlank() && titleText != item.title) CyanNeon else Slate700, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Editor de fecha/hora con pickers ──
            if (editingSchedule && !isAdded) {
                MeetingScheduleEditor(
                    dueTimestamp = item.dueTimestamp,
                    hasTime = item.hasTime,
                    onApply = { due, hasTime ->
                        onSetSchedule(item, due, hasTime)
                        editingSchedule = false
                    }
                )
            }

            // ── Acciones ──
            if (!isAdded) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val canAdd = esMia && item.dueTimestamp > 0L
                    val addLabel = when {
                        item.owner == "OTRO" -> "Es de otro participante"
                        item.dueTimestamp <= 0L -> "Falta definir fecha"
                        item.owner == "INDEFINIDO" -> "Marca si es tuya"
                        else -> "Agregar a mis tareas"
                    }
                    Button(
                        onClick = { onAdd(item) },
                        enabled = canAdd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanNeon,
                            contentColor = OnCyan,
                            disabledContainerColor = Slate800,
                            disabledContentColor = Slate700
                        ),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            addLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    OutlinedButton(
                        onClick = { onDiscard(item) },
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Descartar ítem", tint = Slate400, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

/** Convierte el midnight-UTC que devuelve DatePicker a fecha local preservando la hora. */
private fun utcDateToLocal(utcMillis: Long, preserveTimeFrom: Long?): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        if (preserveTimeFrom != null && preserveTimeFrom > 0) {
            val t = Calendar.getInstance().apply { timeInMillis = preserveTimeFrom }
            set(t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
        } else {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }
        set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.SECOND, 0)
    }.timeInMillis
}

/**
 * Editor de fecha/hora de un ítem: chips rápidos + calendario y reloj nativos.
 * El calendario SOLO permite fechas futuras (hoy inclusive).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MeetingScheduleEditor(
    dueTimestamp: Long,
    hasTime: Boolean,
    onApply: (Long, Boolean) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }

    val dateShort = if (dueTimestamp <= 0L) "Elegir fecha"
    else SimpleDateFormat("EEE d MMM", Locale("es", "ES")).format(Date(dueTimestamp))
    val timeLabel = if (dueTimestamp <= 0L || !hasTime) "Fijar hora"
    else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dueTimestamp))

    fun quickDue(days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = if (dueTimestamp > 0) dueTimestamp else now
        if (dueTimestamp <= 0 || !hasTime) {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }
        set(Calendar.SECOND, 0)
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        EditorChip("Hoy", Icons.Default.Event) { onApply(quickDue(0), hasTime) }
        EditorChip("Mañana", Icons.Default.Event) { onApply(quickDue(1), true) }
        EditorChip("+7 días", Icons.Default.Event) { onApply(quickDue(7), hasTime) }
        EditorChip(dateShort, Icons.Default.CalendarMonth) { showDatePicker = true }
        EditorChip(timeLabel, Icons.Default.Schedule) { showTimePicker = true }
        if (dueTimestamp > 0L && hasTime) {
            EditorChip("Quitar hora", Icons.Default.Close) { onApply(dueTimestamp, false) }
        }
    }

    // Calendario: solo fechas futuras (hoy inclusive)
    if (showDatePicker) {
        val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (dueTimestamp > 0) dueTimestamp else System.currentTimeMillis(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utc ->
                        onApply(utcDateToLocal(utc, if (hasTime) dueTimestamp else null), hasTime)
                    }
                    showDatePicker = false
                }) { Text("Aceptar", color = CyanNeon) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar", color = Slate400) }
            },
            colors = DatePickerDefaults.colors(containerColor = Slate900)
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Reloj
    if (showTimePicker) {
        val initial = Calendar.getInstance().apply { timeInMillis = if (dueTimestamp > 0) dueTimestamp else now }
        val timePickerState = rememberTimePickerState(
            initialHour = initial.get(Calendar.HOUR_OF_DAY),
            initialMinute = initial.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Slate900,
            title = { Text("Hora del ítem", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = if (dueTimestamp > 0) dueTimestamp else (now + 86_400_000L)
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    onApply(cal.timeInMillis, true)
                    showTimePicker = false
                }) { Text("Aceptar", color = CyanNeon) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar", color = Slate400) }
            }
        )
    }
}

@Composable
private fun OwnerChip(label: String, selected: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) color.copy(alpha = 0.22f) else Slate800)
            .border(
                1.dp,
                if (selected) color else Slate700,
                RoundedCornerShape(9.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (selected) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
        }
        Text(
            label,
            color = if (selected) color else Slate400,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EditorChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Slate800)
            .border(1.dp, CyanNeon.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(11.dp))
        Text(label, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun itemTypeIcon(type: String) = when (type) {
    TaskTypes.JUNTA -> Icons.Default.Groups
    TaskTypes.LLAMADA -> Icons.Default.Call
    TaskTypes.ENTREGA -> Icons.Default.Outbox
    TaskTypes.RECORDATORIO -> Icons.Default.Notifications
    else -> Icons.Default.CheckCircle
}

private fun typeColor(type: String) = when (type) {
    TaskTypes.JUNTA -> CyanNeon
    TaskTypes.LLAMADA -> VioletAccent
    TaskTypes.ENTREGA -> AmberWarning
    TaskTypes.RECORDATORIO -> EmeraldSuccess
    else -> CyanNeon
}
