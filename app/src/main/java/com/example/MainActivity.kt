package com.example

import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.MainViewModel
import com.example.ui.screens.AgendaScreen
import com.example.ui.screens.ChatAssistantScreen
import com.example.ui.screens.DesktopBridgeScreen
import com.example.ui.screens.KnowledgeRagScreen
import com.example.ui.screens.MeetingsVaultScreen
import com.example.ui.screens.RotatorStatusScreen
import com.example.ui.screens.SecurityLockScreen
import com.example.ui.screens.SecuritySettingsDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.OnCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.AppBgDeep
import com.example.ui.theme.DockSurface
import com.example.ui.theme.Slate950
import com.example.ui.theme.VioletAccent

enum class NavigationTab(val label: String, val icon: ImageVector) {
    AGENDA("Agenda", Icons.Default.CalendarToday),
    MEETINGS("Juntas", Icons.Default.Mic),
    ASSISTANT("Asistente", Icons.Default.Chat),
    KNOWLEDGE("Memoria", Icons.Default.Folder),
    CONFIG("Config", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Intents de notificaciones (capturar junta / minuta lista) que llegan con la app abierta
    private val newIntentState = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        newIntentState.value = intent
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                // Iconos de las barras del sistema con contraste según el tema:
                // blancos en modo oscuro, oscuros en modo claro (antes se perdían en negro)
                SideEffect {
                    val barStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
                }
                val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
                var currentTab by remember { mutableStateOf(NavigationTab.AGENDA) }
                var showSecurityDialog by remember { mutableStateOf(false) }
                var showProjectsDialog by remember { mutableStateOf(false) }
                var prefillCaptureTitle by remember { mutableStateOf<String?>(null) }

                // Enrutar intents de notificaciones: abrir Juntas (y precargar título de captura)
                LaunchedEffect(newIntentState.value, this@MainActivity.intent) {
                    val i = newIntentState.value ?: this@MainActivity.intent
                    if (i?.getBooleanExtra("nav_meetings", false) == true) {
                        currentTab = NavigationTab.MEETINGS
                        i.getStringExtra("capture_title")?.let { prefillCaptureTitle = it }
                        newIntentState.value = null
                    }
                }

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
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Slate950, Slate950, AppBgDeep)
                                )
                            ),
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Slate800),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                                contentDescription = "Neox Admin",
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                        Text(
                                            "Neox Admin",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = TextPrimary
                                        )
                                    }
                                },
                                actions = {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(end = 10.dp)
                                    ) {
                                        ModernTopBarButton(
                                            icon = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                            description = if (isDarkTheme) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                                            tint = AmberWarning
                                        ) { viewModel.toggleTheme() }
                                        ModernTopBarButton(
                                            icon = Icons.Default.Tune,
                                            description = "Configurar Mis Proyectos",
                                            tint = CyanNeon,
                                            onClick = { showProjectsDialog = true }
                                        )
                                        ModernTopBarButton(
                                            icon = Icons.Default.Security,
                                            description = "Seguridad y Bóveda",
                                            tint = VioletAccent,
                                            onClick = { showSecurityDialog = true }
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = Slate950,
                                    titleContentColor = TextPrimary,
                                    navigationIconContentColor = TextPrimary,
                                    actionIconContentColor = TextPrimary
                                )
                            )
                        },
                        bottomBar = {
                            // Dock flotante premium: píldora despegada de los bordes con sombra
                            // suave y hairline de acento; FAB central que sobresale del dock con
                            // halo violeta pulsante y abre el Asistente (5º destino).
                            // Se oculta con el teclado abierto.
                            if (imeBottom == 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 16.dp)
                                        .padding(bottom = 10.dp)
                                ) {
                                    Column {
                                        // Aire superior para que el FAB sobresalga del dock
                                        Box(modifier = Modifier.fillMaxWidth().height(18.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(64.dp)
                                                .shadow(
                                                    22.dp,
                                                    shape = RoundedCornerShape(26.dp),
                                                    ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
                                                    spotColor = VioletAccent.copy(alpha = 0.30f)
                                                )
                                                .clip(RoundedCornerShape(26.dp))
                                                .background(DockSurface)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                NavigationTab.values().forEach { tab ->
                                                    if (tab == NavigationTab.ASSISTANT) {
                                                        // Ranura central reservada al FAB del Asistente
                                                        Spacer(modifier = Modifier.width(84.dp))
                                                    } else {
                                                        DockItem(
                                                            tab = tab,
                                                            isSelected = currentTab == tab,
                                                            onClick = { currentTab = tab },
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }
                                            }
                                            // Etiqueta del Asistente, dentro de la ranura bajo el FAB
                                            Text(
                                                NavigationTab.ASSISTANT.label.uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = if (currentTab == NavigationTab.ASSISTANT) FontWeight.Bold else FontWeight.Medium,
                                                letterSpacing = 0.8.sp,
                                                color = if (currentTab == NavigationTab.ASSISTANT) VioletAccent else Slate400,
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 5.dp)
                                            )
                                            // Hairline de acento sobre el borde superior del dock
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.5.dp)
                                                    .background(
                                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                            listOf(
                                                                androidx.compose.ui.graphics.Color.Transparent,
                                                                CyanNeon.copy(alpha = 0.40f),
                                                                VioletAccent.copy(alpha = 0.55f),
                                                                CyanNeon.copy(alpha = 0.40f),
                                                                androidx.compose.ui.graphics.Color.Transparent
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }
                                    // FAB del Asistente: sobresale por encima del dock, halo pulsante
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .testTag("nav_tab_assistant_fab")
                                    ) {
                                        val assistantSelected = currentTab == NavigationTab.ASSISTANT
                                        val pulse = rememberInfiniteTransition(label = "fab_pulse")
                                        val haloScale by pulse.animateFloat(
                                            initialValue = 0.92f,
                                            targetValue = 1.10f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1500),
                                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                            ),
                                            label = "fab_halo_scale"
                                        )
                                        val haloAlpha by pulse.animateFloat(
                                            initialValue = 0.35f,
                                            targetValue = 0.12f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(1500),
                                                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                            ),
                                            label = "fab_halo_alpha"
                                        )
                                        val fabRing by animateColorAsState(
                                            targetValue = if (assistantSelected) TextPrimary else androidx.compose.ui.graphics.Color.Transparent,
                                            animationSpec = tween(220),
                                            label = "nav_fab_ring"
                                        )
                                        // Halo violeta que respira detrás del botón
                                        Box(
                                            modifier = Modifier
                                                .size(74.dp)
                                                .graphicsLayer {
                                                    scaleX = haloScale
                                                    scaleY = haloScale
                                                    alpha = haloAlpha
                                                }
                                                .background(VioletAccent, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(58.dp)
                                                .shadow(
                                                    16.dp,
                                                    shape = CircleShape,
                                                    ambientColor = VioletAccent,
                                                    spotColor = VioletAccent
                                                )
                                                .clip(CircleShape)
                                                .background(
                                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                                        listOf(VioletAccent, CyanNeon)
                                                    )
                                                )
                                                .border(2.5.dp, fabRing, CircleShape)
                                                .clickable { currentTab = NavigationTab.ASSISTANT },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.AutoAwesome,
                                                contentDescription = NavigationTab.ASSISTANT.label,
                                                tint = androidx.compose.ui.graphics.Color.White,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }
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
                                    NavigationTab.MEETINGS -> MeetingsVaultScreen(
                                        viewModel = viewModel,
                                        prefillCaptureTitle = prefillCaptureTitle,
                                        onPrefillConsumed = { prefillCaptureTitle = null }
                                    )
                                    NavigationTab.ASSISTANT -> ChatAssistantScreen(viewModel = viewModel)
                                    NavigationTab.KNOWLEDGE -> KnowledgeRagScreen(viewModel = viewModel)
                                    NavigationTab.CONFIG -> SettingsScreen(viewModel = viewModel)
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
                                Text("Aviso de Cierre Anterior", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan)
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(CyanNeon.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text("Mis Trabajos / Proyectos", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${jobs.size} registrados · etiquetan tareas, notas y chats", color = Slate400, fontSize = 10.sp)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Alta de proyecto
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nuevo proyecto:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text("Nombre del trabajo *", color = Slate400, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Slate700
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProjectClient,
                        onValueChange = { newProjectClient = it },
                        label = { Text("Empresa o cliente (opcional)", color = Slate400, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Slate700
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (newProjectName.isNotBlank()) {
                                viewModel.addProject(newProjectName, newProjectClient)
                                newProjectName = ""
                                newProjectClient = ""
                            }
                        },
                        enabled = newProjectName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Agregar proyecto", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Listado
                Text("Proyectos registrados:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(jobs, key = { it.id }) { job ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate800)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(parseColorSafe(job.colorHex))
                                )
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(job.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        if (job.isPrimary) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(CyanNeon.copy(alpha = 0.15f))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text("Principal", color = CyanNeon, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    if (job.companyOrClient.isNotBlank()) {
                                        Text(job.companyOrClient, color = Slate400, fontSize = 10.sp)
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.deleteProject(job) }, modifier = Modifier.size(26.dp)) {
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
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Listo", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/** Botón circular moderno para la barra superior (toggle de tema, proyectos, seguridad). */
@Composable
private fun ModernTopBarButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(17.dp))
    }
}

/**
 * Ítem del dock flotante: píldora squircle animada tras el icono (con rebote de
 * resorte al activarse) + etiqueta compacta en versalitas con tracking amplio.
 */
@Composable
private fun DockItem(
    tab: NavigationTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillColor by animateColorAsState(
        targetValue = if (isSelected) CyanNeon.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(220),
        label = "nav_pill_${tab.name}"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) CyanNeon else Slate400,
        animationSpec = tween(220),
        label = "nav_icon_${tab.name}"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
        label = "nav_bounce_${tab.name}"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp)
            .testTag("nav_tab_${tab.name.lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(13.dp))
                .background(pillColor)
                .padding(horizontal = 13.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                tab.icon,
                contentDescription = tab.label,
                tint = iconTint,
                modifier = Modifier
                    .size(21.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            tab.label.uppercase(),
            fontSize = 8.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.6.sp,
            color = if (isSelected) CyanNeon else Slate400
        )
    }
}

private fun parseColorSafe(hex: String): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (_: Exception) {
    CyanNeon
}
