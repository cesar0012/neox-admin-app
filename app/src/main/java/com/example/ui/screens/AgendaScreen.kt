package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MeetingNote
import com.example.data.model.TaskTypes
import com.example.data.model.WorkTask
import com.example.ui.MainViewModel
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

enum class AgendaViewTab(val label: String, val icon: ImageVector) {
    LIST("Lista y Filtros", Icons.Default.ViewList),
    CALENDAR("Calendario", Icons.Default.CalendarMonth),
    DIAGRAM("Tablero", Icons.Default.ViewKanban)
}

enum class DateFilterType(val label: String) {
    ALL("Todas"),
    TODAY("Hoy"),
    NEXT_5_DAYS("Próximos 5 días"),
    THIS_WEEK("Esta Semana"),
    OVERDUE("Vencidas")
}

enum class StatusFilterType(val label: String) {
    ALL("Todos"),
    PENDING("Pendientes"),
    IN_PROGRESS("En Proceso"),
    COMPLETED("Terminadas")
}

@Composable
fun AgendaScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val meetings by viewModel.allMeetings.collectAsStateWithLifecycle()
    val jobs by viewModel.allJobs.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedJobFilter.collectAsStateWithLifecycle()

    var activeViewTab by remember { mutableStateOf(AgendaViewTab.LIST) }
    var selectedDateFilter by remember { mutableStateOf(DateFilterType.ALL) }
    var selectedStatusFilter by remember { mutableStateOf(StatusFilterType.ALL) }

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<WorkTask?>(null) }

    // Calendar selected date (start of day timestamp)
    var selectedCalendarTimestamp by remember { mutableStateOf(getStartOfDay(System.currentTimeMillis())) }

    val now = System.currentTimeMillis()
    val oneDayMs = 24 * 3600 * 1000L

    // Filter tasks according to all criteria
    val filteredTasks = remember(tasks, selectedFilter, selectedDateFilter, selectedStatusFilter, activeViewTab, selectedCalendarTimestamp) {
        tasks.filter { task ->
            // 1. Job Filter
            val matchesJob = if (selectedFilter == null || selectedFilter == "Todos") true
            else task.jobTag.equals(selectedFilter, ignoreCase = true)
            if (!matchesJob) return@filter false

            // 2. Status Filter
            val matchesStatus = when (selectedStatusFilter) {
                StatusFilterType.ALL -> true
                StatusFilterType.PENDING -> task.status == "PENDIENTE" && !task.isCompleted
                StatusFilterType.IN_PROGRESS -> task.status == "EN_PROCESO" && !task.isCompleted
                StatusFilterType.COMPLETED -> task.isCompleted || task.status == "TERMINADO"
            }
            if (!matchesStatus) return@filter false

            // 3. Date Filter (if in LIST view)
            if (activeViewTab == AgendaViewTab.LIST) {
                when (selectedDateFilter) {
                    DateFilterType.ALL -> true
                    DateFilterType.TODAY -> task.dueTimestamp in getStartOfDay(now)..(getStartOfDay(now) + oneDayMs)
                    DateFilterType.NEXT_5_DAYS -> task.dueTimestamp in now..(now + 5 * oneDayMs)
                    DateFilterType.THIS_WEEK -> task.dueTimestamp in getStartOfDay(now)..(getStartOfDay(now) + 7 * oneDayMs)
                    DateFilterType.OVERDUE -> task.dueTimestamp in 1..now && !task.isCompleted // 0 = sin fecha, no es vencida
                }
            } else if (activeViewTab == AgendaViewTab.CALENDAR) {
                // Calendar view filters by exact day (las tareas sin fecha nunca aparecen aquí)
                val taskStartOfDay = getStartOfDay(task.dueTimestamp)
                task.dueTimestamp > 0 && taskStartOfDay == selectedCalendarTimestamp
            } else {
                true // Diagram view shows all matching status
            }
        }
    }

    val pendingCount = tasks.count { !it.isCompleted && it.status != "TERMINADO" }
    val inProgressCount = tasks.count { !it.isCompleted && it.status == "EN_PROCESO" }
    val completedCount = tasks.count { it.isCompleted || it.status == "TERMINADO" }

    // Count urgent / upcoming deadlines
    val urgentCount = tasks.count { !it.isCompleted && (it.dueTimestamp < now || it.dueTimestamp - now <= 5 * oneDayMs) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top View Tabs: Lista y Filtros | Calendario | Diagrama
            TabRow(
                selectedTabIndex = activeViewTab.ordinal,
                containerColor = Slate900,
                contentColor = CyanNeon,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeViewTab.ordinal]),
                        color = CyanNeon
                    )
                }
            ) {
                AgendaViewTab.values().forEach { tab ->
                    val isSelected = activeViewTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { activeViewTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (isSelected) CyanNeon else Slate400)
                                Text(
                                    text = tab.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CyanNeon else Slate400
                                )
                            }
                        }
                    )
                }
            }

            AnimatedContent(targetState = activeViewTab, label = "view_tab_content") { tabState ->
                when (tabState) {
                    AgendaViewTab.LIST -> {
                        AgendaListView(
                            context = context,
                            tasks = filteredTasks,
                            allTasksCount = tasks.size,
                            pendingCount = pendingCount,
                            completedCount = completedCount,
                            jobsCount = jobs.size,
                            jobs = jobs.map { it.name },
                            selectedJobFilter = selectedFilter ?: "Todos",
                            onSelectJobFilter = { viewModel.setFilterJob(it) },
                            selectedDateFilter = selectedDateFilter,
                            onSelectDateFilter = { selectedDateFilter = it },
                            selectedStatusFilter = selectedStatusFilter,
                            onSelectStatusFilter = { selectedStatusFilter = it },
                            urgentCount = urgentCount,
                            onTriggerSmartAlerts = {
                                viewModel.checkSmartDeadlines()
                                Toast.makeText(context, "Verificando plazos: alertas enviadas a notificaciones", Toast.LENGTH_SHORT).show()
                            },
                            onToggleCompletion = { viewModel.toggleTaskCompletion(it) },
                            onUpdateStatus = { task, newStatus -> viewModel.updateTaskStatus(task, newStatus) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onEditTask = { taskToEdit = it },
                            onExportReport = { viewModel.exportDailyReport(context) }
                        )
                    }
                    AgendaViewTab.CALENDAR -> {
                        AgendaCalendarView(
                            tasks = tasks,
                            meetings = meetings,
                            selectedDateTimestamp = selectedCalendarTimestamp,
                            onSelectDate = { selectedCalendarTimestamp = it },
                            dayFilteredTasks = filteredTasks,
                            onToggleCompletion = { viewModel.toggleTaskCompletion(it) },
                            onUpdateStatus = { task, newStatus -> viewModel.updateTaskStatus(task, newStatus) },
                            onDeleteTask = { viewModel.deleteTask(it) },
                            onEditTask = { taskToEdit = it }
                        )
                    }
                    AgendaViewTab.DIAGRAM -> {
                        AgendaDiagramView(
                            tasks = tasks,
                            meetings = meetings,
                            onUpdateStatus = { task, newStatus -> viewModel.updateTaskStatus(task, newStatus) },
                            onToggleCompletion = { viewModel.toggleTaskCompletion(it) },
                            onToggleMeetingConcluded = { viewModel.toggleMeetingConcluded(it) }
                        )
                    }
                }
            }
        }

        // Floating Action Button to add task
        FloatingActionButton(
            onClick = { showAddTaskDialog = true },
            containerColor = CyanNeon,
            contentColor = Color(0xFF00363D),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_task_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Agregar tarea")
        }
    }

    if (showAddTaskDialog) {
        AddTaskDialog(
            jobList = jobs.map { it.name },
            initialJob = if (selectedFilter != "Todos") selectedFilter ?: "General" else "General",
            onDismiss = { showAddTaskDialog = false },
            onSave = { title, desc, jobTag, dueTime, priority, type, hasTime ->
                viewModel.addTask(title, desc, jobTag, dueTime, priority, taskType = type, hasTime = hasTime)
                showAddTaskDialog = false
            }
        )
    }

    taskToEdit?.let { task ->
        EditTaskDialog(
            task = task,
            jobList = jobs.map { it.name },
            onDismiss = { taskToEdit = null },
            onSave = { title, desc, type, priority, due, job, hasTime ->
                viewModel.updateTaskDetails(task, title, desc, type, priority, due, job, hasTime)
                taskToEdit = null
            }
        )
    }
}

// -----------------------------------------------------------------------------------------
// 1. LIST VIEW & ADVANCED FILTERS
// -----------------------------------------------------------------------------------------

@Composable
fun AgendaListView(
    context: android.content.Context,
    tasks: List<WorkTask>,
    allTasksCount: Int,
    pendingCount: Int,
    completedCount: Int,
    jobsCount: Int,
    jobs: List<String>,
    selectedJobFilter: String,
    onSelectJobFilter: (String) -> Unit,
    selectedDateFilter: DateFilterType,
    onSelectDateFilter: (DateFilterType) -> Unit,
    selectedStatusFilter: StatusFilterType,
    onSelectStatusFilter: (StatusFilterType) -> Unit,
    urgentCount: Int,
    onTriggerSmartAlerts: () -> Unit,
    onToggleCompletion: (WorkTask) -> Unit,
    onUpdateStatus: (WorkTask, String) -> Unit,
    onDeleteTask: (WorkTask) -> Unit,
    onEditTask: (WorkTask) -> Unit,
    onExportReport: () -> Unit
) {
    // Orden: activas antes que completadas; con fecha asc (más próxima primero); sin fecha al final
    val sortedTasks = remember(tasks) {
        tasks.sortedWith(
            compareBy({ it.isCompleted || it.status == "TERMINADO" }, { it.dueTimestamp <= 0 }, { it.dueTimestamp })
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")).format(Date()).replaceFirstChar { it.uppercase() },
                                color = Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Mi Agenda Diaria",
                                color = TextPrimary,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = onExportReport, modifier = Modifier.testTag("export_agenda_btn")) {
                            Icon(Icons.Default.Share, contentDescription = "Exportar reporte", tint = CyanNeon)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate800)
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                        ) {
                            Column {
                                Text("Pendientes", color = Slate400, fontSize = 11.sp)
                                Text("$pendingCount", color = AmberWarning, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate800)
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                        ) {
                            Column {
                                Text("Completadas", color = Slate400, fontSize = 11.sp)
                                Text("$completedCount", color = EmeraldSuccess, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Slate800)
                                .padding(vertical = 8.dp, horizontal = 10.dp)
                        ) {
                            Column {
                                Text("Proyectos", color = Slate400, fontSize = 11.sp)
                                Text("$jobsCount", color = CyanNeon, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Smart Proactive Alerts Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (urgentCount > 0) AmberWarning else Slate800))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (urgentCount > 0) AmberWarning else CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (urgentCount > 0) "Alertas de entrega activas ($urgentCount)" else "Supervisión proactiva al día",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Avisos de 5 días y regla del 50% de tiempo de entrega",
                                color = Slate400,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Button(
                        onClick = onTriggerSmartAlerts,
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = CyanNeon),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Notificar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Compact & Collapsible Filters Section
        item {
            var isFiltersExpanded by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFiltersExpanded = !isFiltersExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Text("Filtros", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            // Compact summary pills when collapsed
                            if (!isFiltersExpanded) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(start = 4.dp)
                                ) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (selectedDateFilter != DateFilterType.ALL) CyanNeon.copy(alpha = 0.2f) else Slate800)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(selectedDateFilter.label, color = if (selectedDateFilter != DateFilterType.ALL) CyanNeon else Slate400, fontSize = 10.sp)
                                        }
                                    }
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (selectedStatusFilter != StatusFilterType.ALL) AmberWarning.copy(alpha = 0.2f) else Slate800)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(selectedStatusFilter.label, color = if (selectedStatusFilter != StatusFilterType.ALL) AmberWarning else Slate400, fontSize = 10.sp)
                                        }
                                    }
                                    if (selectedJobFilter != "Todos") {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(VioletAccent.copy(alpha = 0.2f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(selectedJobFilter, color = VioletAccent, fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Icon(
                            imageVector = if (isFiltersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isFiltersExpanded) "Ocultar filtros" else "Mostrar filtros",
                            tint = CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isFiltersExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Filter by Time / Date Range
                        Text("Plazo / Fecha:", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            items(DateFilterType.values()) { df ->
                                FilterChip(
                                    label = df.label,
                                    isSelected = selectedDateFilter == df,
                                    onClick = { onSelectDateFilter(df) }
                                )
                            }
                        }

                        // Filter by Status
                        Text("Estado:", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            items(StatusFilterType.values()) { sf ->
                                FilterChip(
                                    label = sf.label,
                                    isSelected = selectedStatusFilter == sf,
                                    onClick = { onSelectStatusFilter(sf) }
                                )
                            }
                        }

                        // Filter by Project
                        Text("Proyecto / Trabajo:", color = Slate400, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            item {
                                FilterChip(
                                    label = "Todos",
                                    isSelected = selectedJobFilter == "Todos",
                                    onClick = { onSelectJobFilter("Todos") }
                                )
                            }
                            items(jobs) { jobName ->
                                FilterChip(
                                    label = jobName,
                                    isSelected = selectedJobFilter == jobName,
                                    onClick = { onSelectJobFilter(jobName) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Task count indicator
        item {
            Text(
                text = "Tareas (${tasks.size})",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (tasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Slate700, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No hay tareas que coincidan con estos filtros", color = Slate400, fontSize = 13.sp)
                        Text("Prueba cambiando los filtros o agrega una nueva tarea con el botón +", color = Slate700, fontSize = 11.sp)
                    }
                }
            }
        } else {
            items(sortedTasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onToggle = { onToggleCompletion(task) },
                    onUpdateStatus = { newStatus -> onUpdateStatus(task, newStatus) },
                    onDelete = { onDeleteTask(task) },
                    onEdit = { onEditTask(task) }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 2. INTERACTIVE CALENDAR VIEW
// -----------------------------------------------------------------------------------------

@Composable
fun AgendaCalendarView(
    tasks: List<WorkTask>,
    meetings: List<MeetingNote>,
    selectedDateTimestamp: Long,
    onSelectDate: (Long) -> Unit,
    dayFilteredTasks: List<WorkTask>,
    onToggleCompletion: (WorkTask) -> Unit,
    onUpdateStatus: (WorkTask, String) -> Unit,
    onDeleteTask: (WorkTask) -> Unit,
    onEditTask: (WorkTask) -> Unit
) {
    var calendarYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var calendarMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) } // 0-based

    val monthCalendar = remember(calendarYear, calendarMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, calendarYear)
            set(Calendar.MONTH, calendarMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val daysInMonth = monthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = (monthCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Convert Sunday=1 to Monday=0

    val monthName = remember(calendarYear, calendarMonth) {
        SimpleDateFormat("MMMM yyyy", Locale("es", "ES")).format(monthCalendar.time).replaceFirstChar { it.uppercase() }
    }

    // Map tasks and meetings to day numbers for dots
    val tasksByDay = remember(tasks, calendarYear, calendarMonth) {
        val map = mutableMapOf<Int, MutableList<WorkTask>>()
        val cal = Calendar.getInstance()
        tasks.forEach { t ->
            cal.timeInMillis = t.dueTimestamp
            if (cal.get(Calendar.YEAR) == calendarYear && cal.get(Calendar.MONTH) == calendarMonth) {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                map.getOrPut(day) { mutableListOf() }.add(t)
            }
        }
        map
    }

    val meetingsByDay = remember(meetings, calendarYear, calendarMonth) {
        val map = mutableMapOf<Int, MutableList<MeetingNote>>()
        val cal = Calendar.getInstance()
        meetings.forEach { m ->
            cal.timeInMillis = m.dateTimestamp
            if (cal.get(Calendar.YEAR) == calendarYear && cal.get(Calendar.MONTH) == calendarMonth) {
                val day = cal.get(Calendar.DAY_OF_MONTH)
                map.getOrPut(day) { mutableListOf() }.add(m)
            }
        }
        map
    }

    val selectedDayNumber = remember(selectedDateTimestamp, calendarYear, calendarMonth) {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateTimestamp }
        if (cal.get(Calendar.YEAR) == calendarYear && cal.get(Calendar.MONTH) == calendarMonth) {
            cal.get(Calendar.DAY_OF_MONTH)
        } else -1
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Month Selector Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (calendarMonth == 0) {
                                calendarMonth = 11
                                calendarYear -= 1
                            } else {
                                calendarMonth -= 1
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Mes anterior", tint = CyanNeon)
                        }

                        Text(
                            text = monthName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = {
                            if (calendarMonth == 11) {
                                calendarMonth = 0
                                calendarYear += 1
                            } else {
                                calendarMonth += 1
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Mes siguiente", tint = CyanNeon)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Days of week header (Lun, Mar, Mié, Jue, Vie, Sáb, Dom)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do").forEach { dayName ->
                            Text(
                                text = dayName,
                                color = Slate400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(36.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar Days Grid
                    val totalSlots = ((firstDayOfWeek + daysInMonth + 6) / 7) * 7
                    for (row in 0 until (totalSlots / 7)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            for (col in 0 until 7) {
                                val slotIndex = row * 7 + col
                                val dayNum = slotIndex - firstDayOfWeek + 1

                                if (dayNum in 1..daysInMonth) {
                                    val isSelected = dayNum == selectedDayNumber
                                    val dayTasks = tasksByDay[dayNum].orEmpty()
                                    val dayMeetings = meetingsByDay[dayNum].orEmpty()

                                    val hasPending = dayTasks.any { !it.isCompleted }
                                    val hasCompleted = dayTasks.any { it.isCompleted }
                                    val hasMeetings = dayMeetings.isNotEmpty()

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) CyanNeon else Color.Transparent)
                                            .clickable {
                                                val clickCal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, calendarYear)
                                                    set(Calendar.MONTH, calendarMonth)
                                                    set(Calendar.DAY_OF_MONTH, dayNum)
                                                    set(Calendar.HOUR_OF_DAY, 0)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                onSelectDate(clickCal.timeInMillis)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "$dayNum",
                                                color = if (isSelected) Color(0xFF00363D) else TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            // Dots indicator
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                if (hasPending) {
                                                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) Color(0xFF00363D) else AmberWarning))
                                                }
                                                if (hasMeetings) {
                                                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) Color(0xFF00363D) else VioletAccent))
                                                }
                                                if (hasCompleted && !hasPending) {
                                                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(if (isSelected) Color(0xFF00363D) else EmeraldSuccess))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // Selected Day Details Header
        item {
            val selectedDateStr = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")).format(Date(selectedDateTimestamp)).replaceFirstChar { it.uppercase() }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Eventos del $selectedDateStr",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${dayFilteredTasks.size} tareas",
                    color = CyanNeon,
                    fontSize = 12.sp
                )
            }
        }

        if (dayFilteredTasks.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                    Text("No hay tareas programadas para este día seleccionado.", color = Slate400, fontSize = 12.sp)
                }
            }
        } else {
            items(dayFilteredTasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onToggle = { onToggleCompletion(task) },
                    onUpdateStatus = { newStatus -> onUpdateStatus(task, newStatus) },
                    onDelete = { onDeleteTask(task) },
                    onEdit = { onEditTask(task) }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// 3. TABLERO KANBAN VIEW
// -----------------------------------------------------------------------------------------

@Composable
fun AgendaDiagramView(
    tasks: List<WorkTask>,
    meetings: List<MeetingNote>,
    onUpdateStatus: (WorkTask, String) -> Unit,
    onToggleCompletion: (WorkTask) -> Unit,
    onToggleMeetingConcluded: (MeetingNote) -> Unit
) {
    var selectedTaskForModal by remember { mutableStateOf<WorkTask?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        AgendaKanbanBoardView(
            tasks = tasks,
            onUpdateStatus = onUpdateStatus,
            onTaskClick = { selectedTaskForModal = it }
        )
    }

    // Modal / Pop-up de Detalle de Tarea (Exclusiva de la vista de tablero Kanban)
    if (selectedTaskForModal != null) {
        val task = selectedTaskForModal!!
        KanbanTaskDetailModal(
            task = task,
            onDismiss = { selectedTaskForModal = null },
            onUpdateStatus = { newStatus ->
                onUpdateStatus(task, newStatus)
                selectedTaskForModal = task.copy(
                    status = newStatus,
                    isCompleted = (newStatus == "TERMINADO")
                )
            }
        )
    }
}

@Composable
fun AgendaKanbanBoardView(
    tasks: List<WorkTask>,
    onUpdateStatus: (WorkTask, String) -> Unit,
    onTaskClick: (WorkTask) -> Unit
) {
    val pendingTasks = tasks.filter { (it.status == "PENDIENTE" || it.status.isBlank()) && !it.isCompleted }
    val inProgressTasks = tasks.filter { it.status == "EN_PROCESO" && !it.isCompleted }
    val completedTasks = tasks.filter { it.status == "TERMINADO" || it.isCompleted }

    var selectedColumnFilter by remember { mutableStateOf("ALL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp, bottom = 80.dp)
    ) {
        // Quick column filter tabs with smooth horizontal scroll and zero text wrapping
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    label = "Todo (${tasks.size})",
                    isSelected = selectedColumnFilter == "ALL",
                    onClick = { selectedColumnFilter = "ALL" }
                )
            }
            item {
                FilterChip(
                    label = "🟡 Pendiente (${pendingTasks.size})",
                    isSelected = selectedColumnFilter == "PENDIENTE",
                    onClick = { selectedColumnFilter = "PENDIENTE" }
                )
            }
            item {
                FilterChip(
                    label = "🔵 En Proceso (${inProgressTasks.size})",
                    isSelected = selectedColumnFilter == "EN_PROCESO",
                    onClick = { selectedColumnFilter = "EN_PROCESO" }
                )
            }
            item {
                FilterChip(
                    label = "🟢 Concluido (${completedTasks.size})",
                    isSelected = selectedColumnFilter == "TERMINADO",
                    onClick = { selectedColumnFilter = "TERMINADO" }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (selectedColumnFilter == "ALL") {
            // Horizontal scrollable multi-column board (Trello style)
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    KanbanColumnContainer(
                        title = "Al Pendiente",
                        count = pendingTasks.size,
                        color = AmberWarning,
                        icon = Icons.Default.HourglassEmpty,
                        tasks = pendingTasks,
                        onTaskClick = onTaskClick,
                        onMoveForward = { onUpdateStatus(it, "EN_PROCESO") },
                        onMoveBackward = null
                    )
                }
                item {
                    KanbanColumnContainer(
                        title = "En Proceso",
                        count = inProgressTasks.size,
                        color = CyanNeon,
                        icon = Icons.Default.Autorenew,
                        tasks = inProgressTasks,
                        onTaskClick = onTaskClick,
                        onMoveForward = { onUpdateStatus(it, "TERMINADO") },
                        onMoveBackward = { onUpdateStatus(it, "PENDIENTE") }
                    )
                }
                item {
                    KanbanColumnContainer(
                        title = "Concluida",
                        count = completedTasks.size,
                        color = EmeraldSuccess,
                        icon = Icons.Default.CheckCircle,
                        tasks = completedTasks,
                        onTaskClick = onTaskClick,
                        onMoveForward = null,
                        onMoveBackward = { onUpdateStatus(it, "EN_PROCESO") }
                    )
                }
            }
        } else {
            // Single focused column view
            val (title, count, color, icon, columnTasks) = when (selectedColumnFilter) {
                "PENDIENTE" -> Quintuple("Al Pendiente", pendingTasks.size, AmberWarning, Icons.Default.HourglassEmpty, pendingTasks)
                "EN_PROCESO" -> Quintuple("En Proceso", inProgressTasks.size, CyanNeon, Icons.Default.Autorenew, inProgressTasks)
                else -> Quintuple("Concluida", completedTasks.size, EmeraldSuccess, Icons.Default.CheckCircle, completedTasks)
            }

            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                KanbanColumnContainer(
                    title = title,
                    count = count,
                    color = color,
                    icon = icon,
                    tasks = columnTasks,
                    modifier = Modifier.fillMaxSize(),
                    onTaskClick = onTaskClick,
                    onMoveForward = {
                        val next = if (selectedColumnFilter == "PENDIENTE") "EN_PROCESO" else "TERMINADO"
                        onUpdateStatus(it, next)
                    },
                    onMoveBackward = {
                        val prev = if (selectedColumnFilter == "TERMINADO") "EN_PROCESO" else "PENDIENTE"
                        onUpdateStatus(it, prev)
                    }
                )
            }
        }
    }
}

data class Quintuple<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

@Composable
fun KanbanColumnContainer(
    title: String,
    count: Int,
    color: Color,
    icon: ImageVector,
    tasks: List<WorkTask>,
    modifier: Modifier = Modifier.width(280.dp),
    onTaskClick: (WorkTask) -> Unit,
    onMoveForward: ((WorkTask) -> Unit)?,
    onMoveBackward: ((WorkTask) -> Unit)?
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.5f)))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Column Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(color)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("$count", color = Color(0xFF00363D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Sin elementos en esta columna", color = Slate400, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tasks, key = { it.id }) { task ->
                        KanbanItemCard(
                            task = task,
                            columnColor = color,
                            onClick = { onTaskClick(task) },
                            onMoveForward = if (onMoveForward != null) { { onMoveForward(task) } } else null,
                            onMoveBackward = if (onMoveBackward != null) { { onMoveBackward(task) } } else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KanbanItemCard(
    task: WorkTask,
    columnColor: Color,
    onClick: () -> Unit,
    onMoveForward: (() -> Unit)?,
    onMoveBackward: (() -> Unit)?
) {
    val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    val priorityColor = when (task.priority) {
        "ALTA" -> RoseError
        "MEDIA" -> AmberWarning
        else -> CyanNeon
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyanNeon.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(task.jobTag, color = CyanNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(priorityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(task.priority, color = priorityColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = task.title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vence: ${dateFormat.format(Date(task.dueTimestamp))}",
                    color = Slate400,
                    fontSize = 10.sp
                )

                // Quick Move Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (onMoveBackward != null) {
                        IconButton(onClick = onMoveBackward, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Mover atrás", tint = Slate400, modifier = Modifier.size(14.dp))
                        }
                    }
                    if (onMoveForward != null) {
                        IconButton(onClick = onMoveForward, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Mover adelante", tint = columnColor, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KanbanTaskDetailModal(
    task: WorkTask,
    onDismiss: () -> Unit,
    onUpdateStatus: (String) -> Unit
) {
    val fullDateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val priorityColor = when (task.priority) {
        "ALTA" -> RoseError
        "MEDIA" -> AmberWarning
        else -> CyanNeon
    }

    val currentStatus = when (task.status) {
        "EN_PROCESO" -> "EN_PROCESO"
        "TERMINADO" -> "TERMINADO"
        else -> "PENDIENTE"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyanNeon.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(task.jobTag, color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(priorityColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("Prioridad: ${task.priority}", color = priorityColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(task.title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Deadline and origin
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate800)
                        .padding(10.dp)
                ) {
                    Text("Fecha de Entrega:", color = Slate400, fontSize = 11.sp)
                    Text(fullDateFormat.format(Date(task.dueTimestamp)), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

                    if (task.originReference.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Origen:", color = Slate400, fontSize = 11.sp)
                        Text(task.originReference, color = CyanNeon, fontSize = 12.sp)
                    }
                }

                // Description
                Text("Descripción de la Tarea:", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Slate950)
                        .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (task.description.isNotBlank()) task.description else "Sin descripción detallada registrada.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                // Interactive Status Manager with REAL BUTTONS
                Text("Gestionar Estado del Proceso:", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Button 1: Pendiente
                    val isPendiente = currentStatus == "PENDIENTE"
                    Button(
                        onClick = { onUpdateStatus("PENDIENTE") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPendiente) AmberWarning else Slate800,
                            contentColor = if (isPendiente) Color(0xFF00363D) else AmberWarning
                        ),
                        border = if (!isPendiente) ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AmberWarning.copy(alpha = 0.5f))) else null,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pendiente", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Button 2: En Proceso
                    val isInProceso = currentStatus == "EN_PROCESO"
                    Button(
                        onClick = { onUpdateStatus("EN_PROCESO") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isInProceso) CyanNeon else Slate800,
                            contentColor = if (isInProceso) Color(0xFF00363D) else CyanNeon
                        ),
                        border = if (!isInProceso) ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(CyanNeon.copy(alpha = 0.5f))) else null,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("En Proceso", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // Button 3: Concluida
                    val isTerminado = currentStatus == "TERMINADO"
                    Button(
                        onClick = { onUpdateStatus("TERMINADO") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTerminado) EmeraldSuccess else Slate800,
                            contentColor = if (isTerminado) TextPrimary else EmeraldSuccess
                        ),
                        border = if (!isTerminado) ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(EmeraldSuccess.copy(alpha = 0.5f))) else null,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Concluida", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = TextPrimary)
            ) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
fun AgendaCollapsibleTreeView(
    tasks: List<WorkTask>,
    meetings: List<MeetingNote>,
    onUpdateStatus: (WorkTask, String) -> Unit,
    onToggleMeetingConcluded: (MeetingNote) -> Unit
) {
    val pendingTasks = tasks.filter { (it.status == "PENDIENTE" || it.status.isBlank()) && !it.isCompleted }
    val inProgressTasks = tasks.filter { it.status == "EN_PROCESO" && !it.isCompleted }
    val completedTasks = tasks.filter { it.status == "TERMINADO" || it.isCompleted }

    val total = tasks.size.coerceAtLeast(1)
    val progressFraction = completedTasks.size.toFloat() / total.toFloat()

    // INICIALMENTE TODO RETRAÍDO (colapsado) por defecto
    var isEnProcesoExpanded by remember { mutableStateOf(false) }
    var isPendientesExpanded by remember { mutableStateOf(false) }
    var isTerminadasExpanded by remember { mutableStateOf(false) }
    var isJuntasExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // High-level progress diagram
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Diagrama Jerárquico de Entregas", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toca cada bloque para desplegar las tareas y acuerdos correspondientes:",
                        color = Slate400,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = EmeraldSuccess,
                        trackColor = Slate800
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Avance: ${(progressFraction * 100).toInt()}% completado", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${completedTasks.size} de ${tasks.size} concluidas", color = Slate400, fontSize = 11.sp)
                    }
                }
            }
        }

        // Section 1: En Proceso (Collapsible)
        item {
            CollapsibleSectionCard(
                title = "En Proceso",
                count = inProgressTasks.size,
                color = CyanNeon,
                isExpanded = isEnProcesoExpanded,
                onToggleExpand = { isEnProcesoExpanded = !isEnProcesoExpanded }
            ) {
                if (inProgressTasks.isEmpty()) {
                    Text("No hay tareas en proceso activo actualmente.", color = Slate400, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        inProgressTasks.forEach { task ->
                            DiagramTaskCard(
                                task = task,
                                onMoveToPending = { onUpdateStatus(task, "PENDIENTE") },
                                onMoveToCompleted = { onUpdateStatus(task, "TERMINADO") }
                            )
                        }
                    }
                }
            }
        }

        // Section 2: Pendiente (Collapsible)
        item {
            CollapsibleSectionCard(
                title = "Pendientes de Iniciar",
                count = pendingTasks.size,
                color = AmberWarning,
                isExpanded = isPendientesExpanded,
                onToggleExpand = { isPendientesExpanded = !isPendientesExpanded }
            ) {
                if (pendingTasks.isEmpty()) {
                    Text("¡Excelente! No tienes tareas esperando inicio.", color = Slate400, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        pendingTasks.forEach { task ->
                            DiagramTaskCard(
                                task = task,
                                onMoveToInProgress = { onUpdateStatus(task, "EN_PROCESO") },
                                onMoveToCompleted = { onUpdateStatus(task, "TERMINADO") }
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Concluidas (Collapsible)
        item {
            CollapsibleSectionCard(
                title = "Terminadas / Concluidas",
                count = completedTasks.size,
                color = EmeraldSuccess,
                isExpanded = isTerminadasExpanded,
                onToggleExpand = { isTerminadasExpanded = !isTerminadasExpanded }
            ) {
                if (completedTasks.isEmpty()) {
                    Text("Aún no tienes tareas marcadas como terminadas.", color = Slate400, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        completedTasks.forEach { task ->
                            DiagramTaskCard(
                                task = task,
                                onMoveToInProgress = { onUpdateStatus(task, "EN_PROCESO") },
                                onMoveToPending = { onUpdateStatus(task, "PENDIENTE") }
                            )
                        }
                    }
                }
            }
        }

        // Section 4: Juntas (Collapsible)
        item {
            CollapsibleSectionCard(
                title = "Seguimiento de Juntas Ejecutivas",
                count = meetings.size,
                color = VioletAccent,
                isExpanded = isJuntasExpanded,
                onToggleExpand = { isJuntasExpanded = !isJuntasExpanded }
            ) {
                if (meetings.isEmpty()) {
                    Text("No hay juntas registradas aún.", color = Slate400, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        meetings.take(6).forEach { meeting ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Slate800)
                                    .clickable { onToggleMeetingConcluded(meeting) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(meeting.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(meeting.jobTag, color = CyanNeon, fontSize = 10.sp)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (meeting.isConcluded) EmeraldSuccess.copy(alpha = 0.2f) else Slate700)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
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
                    }
                }
            }
        }
    }
}

@Composable
fun CollapsibleSectionCard(
    title: String,
    count: Int,
    color: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(if (isExpanded) color.copy(alpha = 0.6f) else Slate800))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                    Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("$count", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Contraer" else "Expandir",
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
fun DiagramTaskCard(
    task: WorkTask,
    onMoveToPending: (() -> Unit)? = null,
    onMoveToInProgress: (() -> Unit)? = null,
    onMoveToCompleted: (() -> Unit)? = null
) {
    var isDetailExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isDetailExpanded = !isDetailExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(task.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Slate900)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(task.jobTag, color = CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }

            if (isDetailExpanded && task.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(task.description, color = Slate400, fontSize = 11.sp, lineHeight = 16.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons to move between statuses
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isDetailExpanded) "▲ Ocultar detalle" else "▼ Ver detalle",
                    color = Slate400,
                    fontSize = 10.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (onMoveToPending != null) {
                        TextButton(onClick = onMoveToPending, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("← Pendiente", color = AmberWarning, fontSize = 10.sp)
                        }
                    }
                    if (onMoveToInProgress != null) {
                        TextButton(onClick = onMoveToInProgress, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("→ En Proceso", color = CyanNeon, fontSize = 10.sp)
                        }
                    }
                    if (onMoveToCompleted != null) {
                        TextButton(onClick = onMoveToCompleted, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) {
                            Text("✓ Concluir", color = EmeraldSuccess, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// REUSABLE COMPONENTS: TaskCard & Dialogs
// -----------------------------------------------------------------------------------------

private data class TypeVisual(val label: String, val icon: ImageVector, val color: Color)

private fun typeVisualOf(type: String): TypeVisual = when (type) {
    TaskTypes.JUNTA -> TypeVisual("Junta", Icons.Default.Groups, VioletAccent)
    TaskTypes.LLAMADA -> TypeVisual("Llamada", Icons.Default.Phone, EmeraldSuccess)
    TaskTypes.ENTREGA -> TypeVisual("Entrega", Icons.Default.Flag, RoseError)
    TaskTypes.RECORDATORIO -> TypeVisual("Recordatorio", Icons.Default.NotificationsActive, AmberWarning)
    else -> TypeVisual("Tarea", Icons.Default.CheckCircle, CyanNeon)
}

private fun confidenceVisual(level: String): Triple<String, Color, Int> = when (level) {
    "BAJA" -> Triple("Baja", RoseError, 1)
    "MEDIA" -> Triple("Media", AmberWarning, 2)
    else -> Triple("Alta", EmeraldSuccess, 3)
}

@Composable
private fun TypeBadge(taskType: String) {
    val tv = typeVisualOf(taskType)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tv.color.copy(alpha = 0.18f))
            .border(1.dp, tv.color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(tv.icon, contentDescription = tv.label, tint = tv.color, modifier = Modifier.size(12.dp))
        Text(tv.label, color = tv.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConfidenceIndicator(level: String) {
    val (label, color, dots) = confidenceVisual(level)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (i < dots) color else Slate700)
                )
            }
        }
        Text("Confianza $label", color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TaskCard(
    task: WorkTask,
    onToggle: () -> Unit,
    onUpdateStatus: (String) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit = {}
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    val priorityColor = when (task.priority) {
        "ALTA" -> RoseError
        "MEDIA" -> AmberWarning
        else -> CyanNeon
    }
    val typeVisual = typeVisualOf(task.taskType)
    val noDate = task.dueTimestamp <= 0L

    var showStatusDropdown by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
    val isOverdue = !noDate && task.dueTimestamp < now && !task.isCompleted
    val isDueToday = !noDate && task.dueTimestamp in getStartOfDay(now)..(getStartOfDay(now) + 24 * 3600 * 1000L) && !task.isCompleted

    val currentStatusDisplay = when (task.status) {
        "EN_PROCESO" -> "En Proceso"
        "TERMINADO" -> "Terminado"
        else -> "Pendiente"
    }

    val statusColor = when (task.status) {
        "EN_PROCESO" -> CyanNeon
        "TERMINADO" -> EmeraldSuccess
        else -> AmberWarning
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                when {
                    isOverdue -> RoseError.copy(alpha = 0.55f)
                    task.taskType == TaskTypes.JUNTA -> typeVisual.color.copy(alpha = 0.45f)
                    else -> Slate800
                }
            )
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Barra de acento lateral según tipo de actividad
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(typeVisual.color.copy(alpha = 0.85f))
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggle, modifier = Modifier.testTag("toggle_task_${task.id}")) {
                    Icon(
                        imageVector = if (task.isCompleted || task.status == "TERMINADO") Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Completar tarea",
                        tint = if (task.isCompleted || task.status == "TERMINADO") EmeraldSuccess else Slate400,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Fila 1: Tipo + estado + prioridad
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        TypeBadge(task.taskType)

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(statusColor.copy(alpha = 0.18f))
                                    .clickable { showStatusDropdown = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(currentStatusDisplay, color = statusColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
                            }

                            DropdownMenu(
                                expanded = showStatusDropdown,
                                onDismissRequest = { showStatusDropdown = false },
                                modifier = Modifier.background(Slate800)
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp)) },
                                    text = { Text("Al Pendiente", color = AmberWarning, fontSize = 12.sp) },
                                    onClick = { onUpdateStatus("PENDIENTE"); showStatusDropdown = false }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Autorenew, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp)) },
                                    text = { Text("En Proceso", color = CyanNeon, fontSize = 12.sp) },
                                    onClick = { onUpdateStatus("EN_PROCESO"); showStatusDropdown = false }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp)) },
                                    text = { Text("Concluida", color = EmeraldSuccess, fontSize = 12.sp) },
                                    onClick = { onUpdateStatus("TERMINADO"); showStatusDropdown = false }
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(priorityColor.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(task.priority, color = priorityColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = task.title,
                        color = if (task.isCompleted || task.status == "TERMINADO") Slate400 else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.isCompleted || task.status == "TERMINADO") TextDecoration.LineThrough else TextDecoration.None
                    )

                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            color = Slate400,
                            fontSize = 11.sp,
                            maxLines = 2
                        )
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    // Fecha (o falta de fecha) + confianza
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = when {
                                    noDate -> Slate400
                                    isOverdue -> RoseError
                                    isDueToday -> AmberWarning
                                    else -> Slate400
                                },
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = when {
                                    noDate -> "Sin día ni hora especificados"
                                    isOverdue -> "Vencida: ${dateFormat.format(Date(task.dueTimestamp))}" +
                                        (if (task.hasTime) " ${timeFormat.format(Date(task.dueTimestamp))}" else "")
                                    isDueToday -> if (task.hasTime) "Vence hoy a las ${timeFormat.format(Date(task.dueTimestamp))}" else "Vence hoy · sin hora específica"
                                    else -> "${dateFormat.format(Date(task.dueTimestamp))}" +
                                        (if (task.hasTime) " · ${timeFormat.format(Date(task.dueTimestamp))}" else " · sin hora específica")
                                },
                                color = when {
                                    noDate -> Slate400
                                    isOverdue -> RoseError
                                    isDueToday -> AmberWarning
                                    else -> Slate400
                                },
                                fontSize = 10.sp,
                                fontWeight = if (isOverdue || isDueToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        ConfidenceIndicator(task.confidence)
                    }

                    if (task.jobTag.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = task.jobTag,
                            color = CyanNeon.copy(alpha = 0.8f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar tarea", tint = Slate400, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Eliminar tarea", tint = Slate700, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) CyanNeon else Slate900
    val textColor = if (isSelected) Color(0xFF00363D) else Slate400
    val border = if (isSelected) CyanNeon else Slate700

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun AddTaskDialog(
    jobList: List<String>,
    initialJob: String,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, jobTag: String, dueTime: Long, priority: String, taskType: String, hasTime: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedJob by remember { mutableStateOf(initialJob) }
    var selectedPriority by remember { mutableStateOf("MEDIA") }
    var selectedType by remember { mutableStateOf(TaskTypes.TAREA) }

    val now = remember { System.currentTimeMillis() }
    var selectedDue by remember { mutableStateOf(now + 86400_000L) } // Mañana por defecto
    var selectedHasTime by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Text("Nueva Tarea / Indicación", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("¿Qué tienes que hacer?", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("task_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Detalle o contexto adicional", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Text("Tipo de actividad:", color = Slate400, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskTypes.ALL.forEach { type ->
                        val tv = typeVisualOf(type)
                        FilterChip(
                            label = tv.label,
                            isSelected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                    }
                }

                DueDateTimeEditor(
                    dueTimestamp = selectedDue,
                    hasTime = selectedHasTime,
                    onDueChange = { selectedDue = it },
                    onHasTimeChange = { selectedHasTime = it }
                )

                Text("Proyecto / Trabajo:", color = Slate400, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val displayJobs = if (jobList.isEmpty()) listOf("General", "Trabajo 1", "Freelance") else jobList
                    items(displayJobs) { job ->
                        FilterChip(
                            label = job,
                            isSelected = selectedJob == job,
                            onClick = { selectedJob = job }
                        )
                    }
                }

                Text("Prioridad:", color = Slate400, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALTA", "MEDIA", "BAJA").forEach { prio ->
                        FilterChip(
                            label = prio,
                            isSelected = selectedPriority == prio,
                            onClick = { selectedPriority = prio }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, description, selectedJob, selectedDue, selectedPriority, selectedType, selectedHasTime)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D)),
                modifier = Modifier.testTag("save_task_btn")
            ) {
                Text("Guardar Tarea", fontWeight = FontWeight.Bold)
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
fun EditTaskDialog(
    task: WorkTask,
    jobList: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, type: String, priority: String, due: Long, jobTag: String, hasTime: Boolean) -> Unit
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var description by remember(task.id) { mutableStateOf(task.description) }
    var selectedType by remember(task.id) { mutableStateOf(if (TaskTypes.isValid(task.taskType)) task.taskType else TaskTypes.TAREA) }
    var selectedPriority by remember(task.id) { mutableStateOf(task.priority) }
    var selectedJob by remember(task.id) { mutableStateOf(task.jobTag) }
    var selectedDue by remember(task.id) { mutableStateOf(task.dueTimestamp) }
    var selectedHasTime by remember(task.id) { mutableStateOf(task.hasTime) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                Text("Editar ${TaskTypes.labelOf(selectedType)}", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción", color = Slate400) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Text("Tipo de actividad:", color = Slate400, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskTypes.ALL.forEach { type ->
                        FilterChip(
                            label = TaskTypes.labelOf(type),
                            isSelected = selectedType == type,
                            onClick = { selectedType = type }
                        )
                    }
                }

                Text("Prioridad:", color = Slate400, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALTA", "MEDIA", "BAJA").forEach { prio ->
                        FilterChip(
                            label = prio,
                            isSelected = selectedPriority == prio,
                            onClick = { selectedPriority = prio }
                        )
                    }
                }

                DueDateTimeEditor(
                    dueTimestamp = selectedDue,
                    hasTime = selectedHasTime,
                    onDueChange = { selectedDue = it },
                    onHasTimeChange = { selectedHasTime = it }
                )

                if (jobList.isNotEmpty()) {
                    Text("Proyecto / Trabajo:", color = Slate400, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(jobList) { job ->
                            FilterChip(
                                label = job,
                                isSelected = selectedJob == job,
                                onClick = { selectedJob = job }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, description, selectedType, selectedPriority, selectedDue, selectedJob, selectedHasTime)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D))
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

/** Convierte el midnight-UTC que devuelve DatePicker a fecha local preservando la hora actual. */
private fun utcDateToLocal(utcMillis: Long, preserveTimeFrom: Long?): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        if (preserveTimeFrom != null && preserveTimeFrom > 0) {
            val t = Calendar.getInstance().apply { timeInMillis = preserveTimeFrom }
            set(t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE))
        }
        set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH))
        set(Calendar.SECOND, 0)
    }.timeInMillis
}

/**
 * Editor de fecha y hora: chips rápidos + selección exacta con calendario y reloj,
 * con la opción de marcar "sin hora específica".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDateTimeEditor(
    dueTimestamp: Long,
    hasTime: Boolean,
    onDueChange: (Long) -> Unit,
    onHasTimeChange: (Boolean) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val now = remember { System.currentTimeMillis() }
    val dateLabel = if (dueTimestamp <= 0L) "Sin fecha"
    else SimpleDateFormat("EEE d MMM yyyy", Locale("es", "ES")).format(Date(dueTimestamp))
    val timeLabel = if (dueTimestamp <= 0L || !hasTime) "Sin hora"
    else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dueTimestamp))

    fun quickDue(days: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (dueTimestamp > 0) dueTimestamp else now
            add(Calendar.DAY_OF_YEAR, days.toInt())
        }
        return cal.timeInMillis
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Fecha:", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(label = "Hoy", isSelected = false, onClick = { onDueChange(quickDue(0)) })
            FilterChip(label = "Mañana", isSelected = false, onClick = { onDueChange(quickDue(1)) })
            FilterChip(label = "+7 días", isSelected = false, onClick = { onDueChange(quickDue(7)) })
            FilterChip(label = "Sin fecha", isSelected = dueTimestamp <= 0L, onClick = { onDueChange(0L) })
            FilterChip(label = "📅 $dateLabel".replace("📅 ", ""), isSelected = dueTimestamp > 0L, onClick = { showDatePicker = true })
        }

        Text("Hora:", color = Slate400, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                label = "⏰ $timeLabel".replace("⏰ ", ""),
                isSelected = hasTime && dueTimestamp > 0L,
                onClick = { showTimePicker = true }
            )
            FilterChip(
                label = "Sin hora específica",
                isSelected = !hasTime || dueTimestamp <= 0L,
                onClick = { onHasTimeChange(false) }
            )
        }
        if (!hasTime && dueTimestamp > 0L) {
            Text(
                "La tarea queda agendada al día; sin notificación de hora exacta.",
                color = Slate700,
                fontSize = 9.sp
            )
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (dueTimestamp > 0) dueTimestamp else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { utc ->
                        onDueChange(utcDateToLocal(utc, if (hasTime) dueTimestamp else null))
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
            title = { Text("Hora de la tarea", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePicker(state = timePickerState)
                    TextButton(onClick = {
                        onHasTimeChange(false)
                        showTimePicker = false
                    }) { Text("Quitar hora (sin hora específica)", color = Slate400, fontSize = 11.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = if (dueTimestamp > 0) dueTimestamp else (now + 86400_000L)
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    onDueChange(cal.timeInMillis)
                    onHasTimeChange(true)
                    showTimePicker = false
                }) { Text("Aceptar", color = CyanNeon) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar", color = Slate400) }
            }
        )
    }
}

private fun getStartOfDay(timestamp: Long): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}
