package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

private val SETTINGS_TABS = listOf("Rotador", "Wi-Fi PC", "Respaldo")

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
            else -> BackupSection(viewModel = viewModel)
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
                        Text("Respaldo Completo", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Descargar respaldo (.json)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
