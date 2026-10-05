# -*- coding: utf-8 -*-
import io

# ── MainActivity: routing de intents de notificaciones ──
p = 'app/src/main/java/com/example/MainActivity.kt'
s = io.open(p, encoding='utf-8').read()

old = '''class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
'''
new = '''class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Intents de notificaciones (capturar junta / minuta lista) que llegan con la app abierta
    private val newIntentState = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        newIntentState.value = intent
    }
'''
assert old in s, "ma1"
s = s.replace(old, new, 1)

old = '''                val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
                var currentTab by remember { mutableStateOf(NavigationTab.AGENDA) }
                var showSecurityDialog by remember { mutableStateOf(false) }
                var showProjectsDialog by remember { mutableStateOf(false) }'''
new = '''                val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
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
                }'''
assert old in s, "ma2"
s = s.replace(old, new, 1)

old = '''                                    NavigationTab.MEETINGS -> MeetingsVaultScreen(viewModel = viewModel)'''
new = '''                                    NavigationTab.MEETINGS -> MeetingsVaultScreen(
                                        viewModel = viewModel,
                                        prefillCaptureTitle = prefillCaptureTitle,
                                        onPrefillConsumed = { prefillCaptureTitle = null }
                                    )'''
assert old in s, "ma3"
s = s.replace(old, new, 1)

if 'import androidx.compose.runtime.LaunchedEffect' not in s:
    s = s.replace('import androidx.activity.compose.setContent\n',
                  'import androidx.activity.compose.setContent\nimport androidx.compose.runtime.LaunchedEffect\n', 1)

io.open(p, 'w', encoding='utf-8').write(s)
print("MainActivity OK")
