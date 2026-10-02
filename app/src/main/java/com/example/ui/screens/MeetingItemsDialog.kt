package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MeetingNote
import com.example.data.model.MeetingTaskItem
import com.example.data.model.TaskTypes
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
import java.util.Date
import java.util.Locale

/**
 * Pop-up de pantalla completa para revisar los ítems detectados en una junta:
 *
 * - El usuario decide cuáles agregar a SUS tareas (nada se agrega automáticamente).
 * - Los ítems sin fecha aparecen resaltados en ROJO: hay que definirla antes de agregar
 *   (se escribe en lenguaje natural, p.ej. "viernes a las 10 am", con el mismo parser
 *   del asistente).
 * - Al agregar, el ítem queda tachado como "Agregada". También se pueden descartar.
 */
@Composable
fun MeetingItemsDialog(
    meeting: MeetingNote,
    items: List<MeetingTaskItem>,
    onDismiss: () -> Unit,
    onAdd: (MeetingTaskItem) -> Unit,
    onDiscard: (MeetingTaskItem) -> Unit,
    onSetDate: (MeetingTaskItem, String) -> Unit
) {
    val pending = items.filter { it.status == "PENDIENTE" }
    val added = items.filter { it.status == "AGREGADA" }
    val pendingWithDate = pending.filter { it.dueTimestamp > 0 }

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
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ítems Detectados en la Junta", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${meeting.title} · ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(meeting.dateTimestamp))}",
                            color = Slate400,
                            fontSize = 10.sp,
                            maxLines = 1
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
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatChip("${items.size} detectados", CyanNeon)
                    StatChip("${pending.size} pendientes", if (pending.isEmpty()) Slate400 else AmberWarning)
                    StatChip("${added.size} agregadas", EmeraldSuccess)
                }

                // ───────── Lista de ítems ─────────
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            onSetDate = onSetDate
                        )
                    }

                    if (pendingWithDate.size >= 2) {
                        item {
                            Button(
                                onClick = { pendingWithDate.forEach(onAdd) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = OnCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Text("Agregar todas las que ya tienen fecha (${pendingWithDate.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    item { Spacer(modifier = Modifier.height(70.dp)) }
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
    onSetDate: (MeetingTaskItem, String) -> Unit
) {
    val isAdded = item.status == "AGREGADA"
    val sinFecha = item.dueTimestamp <= 0L

    // El editor de fecha se abre por defecto cuando falta la fecha
    var editingDate by remember(item.id, item.dueTimestamp) { mutableStateOf(sinFecha && !isAdded) }
    var dateText by remember(item.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (sinFecha && !isAdded) RoseError.copy(alpha = 0.06f) else Slate950),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                when {
                    isAdded -> Slate800
                    sinFecha -> RoseError.copy(alpha = 0.65f)
                    else -> Slate800
                }
            )
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Título + tipo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    itemTypeIcon(item.taskType),
                    contentDescription = null,
                    tint = if (isAdded) Slate700 else typeColor(item.taskType),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = item.title,
                    color = if (isAdded) Slate700 else TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isAdded) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = when (item.priority) {
                        "ALTA" -> "▲ Alta"; "BAJA" -> "▼ Baja"; else -> "● Media"
                    },
                    color = when (item.priority) {
                        "ALTA" -> RoseError; "BAJA" -> Slate400; else -> AmberWarning
                    },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Cita textual de respaldo
            if (item.contextQuote.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "« ${item.contextQuote} »",
                    color = Slate400,
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Estado de fecha/hora
            when {
                isAdded -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(14.dp))
                        Text("Agregada a tus tareas", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                sinFecha -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RoseError, modifier = Modifier.size(14.dp))
                        Text(
                            "SIN FECHA — defínela para poder agregarla",
                            color = RoseError,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Event, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                        Text(
                            SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale("es", "ES")).format(Date(item.dueTimestamp))
                                .replaceFirstChar { it.uppercase(Locale.getDefault()) },
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!item.hasTime) {
                            Text("· sin hora dicha (09:00 por defecto)", color = AmberWarning, fontSize = 9.sp)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "Cambiar",
                            color = Slate400,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { editingDate = !editingDate }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Editor de fecha en lenguaje natural
            if (!isAdded && editingDate) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        placeholder = { Text("Ej: viernes a las 10 de la mañana", color = Slate700, fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Slate700,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (dateText.isNotBlank()) {
                                onSetDate(item, dateText)
                                editingDate = false
                            }
                        },
                        enabled = dateText.isNotBlank()
                    ) {
                        Text("Aplicar", color = if (dateText.isNotBlank()) CyanNeon else Slate700, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    "Mismo intérprete del asistente: entiende \"mañana 10 am\", \"el próximo viernes 3 y media\", \"en 3 días\"...",
                    color = Slate700,
                    fontSize = 8.sp
                )
            }

            // Acciones
            if (!isAdded) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { onAdd(item) },
                        enabled = item.dueTimestamp > 0L,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanNeon,
                            contentColor = OnCyan,
                            disabledContainerColor = Slate800,
                            disabledContentColor = Slate700
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (item.dueTimestamp > 0L) "Agregar a mis tareas" else "Falta definir fecha",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    OutlinedButton(
                        onClick = { onDiscard(item) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Descartar ítem", tint = Slate400, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
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
