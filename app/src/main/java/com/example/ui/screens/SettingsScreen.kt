package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.theme.OnCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.VioletAccent
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

private val SETTINGS_TABS = listOf("Rotador", "Wi-Fi PC", "Notif.", "Integr.", "Backup")

/**
 * Sección unificada de Configuración: Rotador de modelos, puente Wi-Fi con la PC
 * y respaldo completo (exportar TODO a un archivo JSON descargable).
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
            SETTINGS_TABS.forEachIndexed { idx, label ->
                val tabIcon: ImageVector = when (idx) {
                    0 -> Icons.Default.RotateRight
                    1 -> Icons.Default.Laptop
                    2 -> Icons.Default.Notifications
                    3 -> Icons.Default.Extension
                    else -> Icons.Default.Download
                }
                val selected = selectedTab == idx
                Tab(
                    selected = selected,
                    onClick = { selectedTab = idx },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(tabIcon, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (selected) CyanNeon else Slate400)
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) CyanNeon else Slate400
                            )
                        }
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> RotatorStatusScreen(viewModel = viewModel)
            1 -> DesktopBridgeScreen(viewModel = viewModel)
            2 -> NotificationsSection(viewModel = viewModel)
            3 -> IntegrationsSection(viewModel = viewModel)
            else -> BackupSection(viewModel = viewModel)
        }
    }
}

/**
 * Integraciones del agente: correo (IMAP con contraseña de aplicación) y Google
 * Calendar (CalDAV, lectura). Los compromisos detectados en correos se revisan con el
 * mismo pop-up de ítems de las juntas antes de entrar a tus tareas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntegrationsSection(viewModel: MainViewModel) {
    val busy by viewModel.isIntegrationsBusy.collectAsStateWithLifecycle()
    val message by viewModel.integrationsMessage.collectAsStateWithLifecycle()
    val meetingItems by viewModel.allMeetingItems.collectAsStateWithLifecycle()

    var user by remember { mutableStateOf(viewModel.prefs.getEmailUser()) }
    var pass by remember { mutableStateOf(viewModel.prefs.getEmailPass()) }
    var showPass by remember { mutableStateOf(false) }
    var showEmailItems by remember { mutableStateOf(false) }
    val emailItems = meetingItems.filter { it.meetingId == 0L }
    val pendingEmailItems = emailItems.count { it.status == "PENDIENTE" }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(8000)
            viewModel.consumeIntegrationsMessage()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(CyanNeon.copy(alpha = 0.35f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(CyanNeon.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Mail, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(19.dp))
                        }
                        Column {
                            Text("Correo (Gmail / IMAP)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("El agente lee tus últimos correos y detecta compromisos", color = Slate400, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Solo lectura. Necesitas una Contraseña de Aplicación: en tu cuenta de Google, Seguridad > Verificación en 2 pasos > Contraseñas de aplicaciones.",
                        color = Slate400, fontSize = 10.sp, lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Correo", color = Slate400) },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanNeon, unfocusedBorderColor = Slate700, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pass,
                        onValueChange = { pass = it },
                        label = { Text("Contraseña de aplicación", color = Slate400) },
                        singleLine = true,
                        visualTransformation = if (showPass) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPass = !showPass }) {
                                Text(if (showPass) "Ocultar" else "Ver", color = CyanNeon, fontSize = 10.sp)
                            }
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CyanNeon, unfocusedBorderColor = Slate700, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.prefs.setEmailUser(user)
                                viewModel.prefs.setEmailPass(pass)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Guardar", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = {
                                viewModel.prefs.setEmailUser(user)
                                viewModel.prefs.setEmailPass(pass)
                                viewModel.testEmailConnection()
                            },
                            enabled = !busy,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Probar conexión", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.prefs.setEmailUser(user)
                            viewModel.prefs.setEmailPass(pass)
                            viewModel.analyzeRecentEmails()
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = OnCyan, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analizando...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Analizar últimos 15 correos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(VioletAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = VioletAccent, modifier = Modifier.size(19.dp))
                        }
                        Column {
                            Text("Google Calendar (CalDAV)", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Importa tus próximos eventos a la agenda de Neox", color = Slate400, fontSize = 11.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Usa el correo y contraseña de aplicación de arriba. Los eventos de los próximos 14 días se importan como Juntas/Eventos sin duplicarse.",
                        color = Slate400, fontSize = 10.sp, lineHeight = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.importGoogleCalendar() },
                        enabled = !busy && user.isNotBlank() && pass.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletAccent, contentColor = TextPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text("Importar próximos 14 días", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (emailItems.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (pendingEmailItems > 0) AmberWarning else Slate800)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Compromisos detectados en correos", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (pendingEmailItems > 0) "$pendingEmailItems pendientes de revisión"
                            else "${emailItems.count { it.status == "AGREGADA" }} ya agregados a tus tareas",
                            color = if (pendingEmailItems > 0) AmberWarning else EmeraldSuccess,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { showEmailItems = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text("Revisar ítems detectados", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        message?.let { msg ->
            item {
                Text(msg, color = AmberWarning, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }

    if (showEmailItems) {
        MeetingItemsDialog(
            headerTitle = "Compromisos detectados en correos",
            headerDateLabel = "",
            items = emailItems,
            onDismiss = { showEmailItems = false },
            onAdd = { viewModel.addMeetingItemToTasks(it) },
            onDiscard = { viewModel.discardMeetingItem(it) },
            onSetSchedule = { item, due, hasTime -> viewModel.updateMeetingItemSchedule(item, due, hasTime) },
            onRename = { item, title -> viewModel.renameMeetingItem(item, title) },
            onSetOwner = { item, owner -> viewModel.setMeetingItemOwner(item, owner) },
            defaultNotifLeads = viewModel.prefs.getNotifLeadDefaults(),
            onSetLeads = { item, csv -> viewModel.setMeetingItemNotifLeads(item, csv) }
        )
    }
}

/**
 * Configuración de notificaciones: anticipación global de los avisos y explicación
 * del funcionamiento en segundo plano (alarmas del sistema que despiertan el móvil).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationsSection(viewModel: MainViewModel) {
    val context = LocalContext.current
    var leads by remember { mutableStateOf(viewModel.prefs.getNotifLeadDefaults()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(CyanNeon.copy(alpha = 0.35f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(CyanNeon.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(19.dp))
                        }
                        Column {
                            Text("Notificaciones en Segundo Plano", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Funcionan aunque cierres o olvides la app", color = Slate400, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Las tareas y alarmas se registran con el programador del sistema Android (AlarmManager): " +
                            "el celular despierta a la hora programada y la notificación suena aunque Neox Admin esté cerrada, " +
                            "incluso después de reiniciar el teléfono. Las ALARMAS suenan con el tono de alarma del sistema y pantalla completa; " +
                            "los eventos recurrentes se re-agendan solos a su siguiente ocurrencia.",
                        color = Slate400,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cuándo avisar (puedes elegir varios)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Estas anticipaciones se aplican por omisión a cada tarea nueva. " +
                            "Personaliza las de una tarea específica desde la Agenda (campanita) o en los ítems de una junta. " +
                            "Si no eliges ninguna, el aviso llega a la hora exacta.",
                        color = Slate400,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    NotifLeadsEditor(
                        selected = leads,
                        onSelectionChange = { newLeads ->
                            leads = newLeads
                            viewModel.prefs.setNotifLeadDefaults(newLeads)
                            com.example.data.notifications.AlarmScheduler.scheduleNext(context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupSection(viewModel: MainViewModel) {
    val context = LocalContext.current
    val jobs by viewModel.allJobs.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val meetings by viewModel.allMeetings.collectAsStateWithLifecycle()
    val documents by viewModel.allDocuments.collectAsStateWithLifecycle()
    val vaultEntries by viewModel.allVaultEntries.collectAsStateWithLifecycle()
    val sessions by viewModel.chatSessions.collectAsStateWithLifecycle()
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.writeBackupTo(context, uri)
    }

    // Carpeta en la nube (Google Drive / Dropbox) vía selector del sistema (SAF)
    var cloudTreeUri by remember { mutableStateOf(viewModel.prefs.getBackupTreeUri()) }
    val cloudFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            cloudTreeUri = uri.toString()
            viewModel.prefs.setBackupTreeUri(uri.toString())
            Toast.makeText(context, "Carpeta en la nube lista para respaldos", Toast.LENGTH_SHORT).show()
        }
    }
    val cloudFolderName = if (cloudTreeUri.isBlank()) null
    else android.net.Uri.parse(cloudTreeUri).lastPathSegment?.substringAfter(":")?.ifBlank { null } ?: "carpeta elegida"

    LaunchedEffect(backupResult) {
        if (backupResult != null) {
            Toast.makeText(context, backupResult, Toast.LENGTH_LONG).show()
            viewModel.clearBackupResult()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Tarjeta principal
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldSuccess.copy(alpha = 0.5f)))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text("Respaldo Completo", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Un archivo .json con absolutamente todo", color = EmeraldSuccess, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    "Descarga un respaldo con TODA tu información: proyectos, tareas, minutas de juntas (con transcripciones), bóveda de capturas, memoria RAG, todas las conversaciones del asistente por proyecto y la configuración del rotador incluyendo tus API keys.",
                    color = Slate400,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Resumen de lo que se respalda
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupStatBox("Proyectos", jobs.size, CyanNeon, Modifier.weight(1f))
                    BackupStatBox("Tareas", tasks.size, AmberWarning, Modifier.weight(1f))
                    BackupStatBox("Juntas", meetings.size, EmeraldSuccess, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupStatBox("Memoria", documents.size, CyanNeon, Modifier.weight(1f))
                    BackupStatBox("Bóveda", vaultEntries.size, EmeraldSuccess, Modifier.weight(1f))
                    BackupStatBox("Chats", sessions.size, AmberWarning, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { saveLauncher.launch(viewModel.backupFileName()) },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Descargar respaldo (.json)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Respaldo directo a carpeta en la nube (Drive/Dropbox vía selector del sistema)
                Text("Respaldo en la nube (Google Drive / Dropbox):", color = Slate400, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { cloudFolderLauncher.launch(null) },
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (cloudFolderName == null) "Elegir carpeta en la nube"
                        else "Carpeta: $cloudFolderName (cambiar)",
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false
                    )
                }
                if (cloudTreeUri.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { viewModel.backupNowToCloud(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = TextPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Respaldar ahora a la nube", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "El archivo incluye tus API keys del rotador: guárdalo en un lugar seguro.",
                    color = AmberWarning,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Próximamente: restauración
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Slate700, modifier = Modifier.size(18.dp))
                Column {
                    Text("Restaurar desde respaldo", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Próximamente podrás importar este archivo en otro dispositivo", color = Slate700, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun BackupStatBox(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Slate800)
            .padding(vertical = 8.dp, horizontal = 10.dp)
    ) {
        Column {
            Text(label, color = Slate400, fontSize = 10.sp)
            Text("$count", color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
