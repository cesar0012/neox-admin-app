package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.ui.MainViewModel
import com.example.ui.screens.AgendaScreen
import com.example.ui.screens.ChatAssistantScreen
import com.example.ui.screens.DesktopBridgeScreen
import com.example.ui.screens.KnowledgeRagScreen
import com.example.ui.screens.MeetingsVaultScreen
import com.example.ui.screens.RotatorStatusScreen
import com.example.ui.screens.SecurityLockScreen
import com.example.ui.screens.SecuritySettingsDialog
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

enum class NavigationTab(val label: String, val icon: ImageVector) {
    AGENDA("Agenda", Icons.Default.CalendarToday),
    MEETINGS("Juntas", Icons.Default.Mic),
    ASSISTANT("Asistente", Icons.Default.Chat),
    KNOWLEDGE("Memoria", Icons.Default.Folder),
    ROTATOR("Rotador", Icons.AutoMirrored.Filled.RotateRight),
    BRIDGE("Wi-Fi PC", Icons.Default.Laptop)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
                var currentTab by remember { mutableStateOf(NavigationTab.AGENDA) }
                var showSecurityDialog by remember { mutableStateOf(false) }
                var showProjectsDialog by remember { mutableStateOf(false) }

                val context = androidx.compose.ui.platform.LocalContext.current
                var previousCrashLog by remember { mutableStateOf<String?>(null) }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        try {
                            viewModel.checkSmartDeadlines()
                        } catch (t: Throwable) {
                            android.util.Log.e("MainActivity", "checkSmartDeadlines error: ${t.message}", t)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val report = OmniWorkApp.getLastCrashReport(context)
                    if (!report.isNullOrBlank()) {
                        previousCrashLog = report
                    }

                    kotlinx.coroutines.delay(1000)
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.checkSmartDeadlines()
                        }
                    } catch (t: Throwable) {
                        android.util.Log.e("MainActivity", "Permission launch error: ${t.message}", t)
                    }
                }

                if (!isUnlocked) {
                    SecurityLockScreen(
                        viewModel = viewModel,
                        onUnlocked = { /* unlocked */ }
                    )
                } else {
                    // Detectar teclado abierto para ocultar la barra inferior (evita la franja
                    // oscura que quedaba flotando entre el contenido y el teclado)
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val imeBottom = WindowInsets.ime.getBottom(density)

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(CyanNeon.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Mic,
                                                contentDescription = null,
                                                tint = CyanNeon,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Neox Admin",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color.White
                                        )
                                    }
                                },
                                actions = {
                                    IconButton(
                                        onClick = { showProjectsDialog = true },
                                        modifier = Modifier.testTag("manage_projects_btn")
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = "Configurar Mis Proyectos", tint = Slate400)
                                    }
                                    IconButton(
                                        onClick = { showSecurityDialog = true },
                                        modifier = Modifier.testTag("security_settings_btn")
                                    ) {
                                        Icon(Icons.Default.Security, contentDescription = "Seguridad y Bóveda", tint = Slate400)
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = Slate900
                                )
                            )
                        },
                        bottomBar = {
                            // La barra de navegación se oculta con el teclado abierto
                            if (imeBottom == 0) {
                                NavigationBar(
                                    containerColor = Slate900,
                                    contentColor = CyanNeon
                                ) {
                                    NavigationTab.values().forEach { tab ->
                                        val isSelected = currentTab == tab
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { currentTab = tab },
                                            icon = {
                                                Icon(tab.icon, contentDescription = tab.label)
                                            },
                                            label = {
                                                Text(
                                                    tab.label,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = CyanNeon,
                                                selectedTextColor = CyanNeon,
                                                indicatorColor = Slate800,
                                                unselectedIconColor = Slate400,
                                                unselectedTextColor = Slate400
                                            ),
                                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        // consumeWindowInsets evita pagar dos veces la altura de la barra inferior
                        // al sumarse el inset del teclado; así el input queda pegado al teclado
                        // sin franja oscura de por medio
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .consumeWindowInsets(innerPadding)
                                .imePadding()
                        ) {
                            AnimatedContent(targetState = currentTab, label = "tab_switch") { target ->
                                when (target) {
                                    NavigationTab.AGENDA -> AgendaScreen(viewModel = viewModel)
                                    NavigationTab.MEETINGS -> MeetingsVaultScreen(viewModel = viewModel)
                                    NavigationTab.ASSISTANT -> ChatAssistantScreen(viewModel = viewModel)
                                    NavigationTab.KNOWLEDGE -> KnowledgeRagScreen(viewModel = viewModel)
                                    NavigationTab.ROTATOR -> RotatorStatusScreen(viewModel = viewModel)
                                    NavigationTab.BRIDGE -> DesktopBridgeScreen(viewModel = viewModel)
                                }
                            }
                        }
                    }

                    if (showSecurityDialog) {
                        SecuritySettingsDialog(
                            viewModel = viewModel,
                            onDismiss = { showSecurityDialog = false }
                        )
                    }

                    if (showProjectsDialog) {
                        ProjectsManagementDialog(
                            viewModel = viewModel,
                            onDismiss = { showProjectsDialog = false }
                        )
                    }

                    if (previousCrashLog != null) {
                        AlertDialog(
                            onDismissRequest = { previousCrashLog = null },
                            containerColor = Slate900,
                            title = {
                                Text("Aviso de Cierre Anterior", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Se detectó un reporte de error de una ejecución previa. Puedes copiarlo para pegarlo en el chat o descartarlo:",
                                        color = Slate400,
                                        fontSize = 12.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Slate950)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            previousCrashLog!!,
                                            color = RoseError,
                                            fontSize = 10.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            maxLines = 8
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("OmniWork Crash Log", previousCrashLog!!)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "¡Copiado al portapapeles!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D))
                                ) {
                                    Text("Copiar Error", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        OmniWorkApp.clearLastCrash(context)
                                        previousCrashLog = null
                                    }
                                ) {
                                    Text("Descartar", color = Slate400)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectsManagementDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val jobs by viewModel.allJobs.collectAsStateWithLifecycle()
    var newProjectName by remember { mutableStateOf("") }
    var newProjectClient by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Text("Configurar Mis Trabajos / Proyectos", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Agrega tus trabajos o proyectos para etiquetar tus notas y tareas personalizadas:",
                    color = Slate400,
                    fontSize = 12.sp
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        placeholder = { Text("Nombre del Trabajo", color = Slate700, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Slate700
                        )
                    )

                    Button(
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                viewModel.addProject(newProjectName, newProjectClient)
                                newProjectName = ""
                                newProjectClient = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Agregar")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text("Proyectos Registrados:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(jobs, key = { it.id }) { job ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(job.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                if (job.companyOrClient.isNotBlank()) {
                                    Text(job.companyOrClient, color = Slate400, fontSize = 10.sp)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteProject(job) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = RoseError, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D))
            ) {
                Text("Listo", fontWeight = FontWeight.Bold)
            }
        }
    )
}
