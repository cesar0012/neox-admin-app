package com.example.data.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.model.TaskTypes
import com.example.data.model.WorkTask
import com.example.data.nlp.RecurrenceHelper
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Programa la PRÓXIMA notificación con el AlarmManager del sistema (no depende de que
 * la app esté abierta): calcula el vencimiento más cercano menos la anticipación
 * configurada y fija una alarma exacta que despierta el dispositivo.
 *
 * Se re-programa al abrir la app, al crear/editar/completar/borrar tareas, tras
 * dispararse y tras reiniciar el teléfono (BootReceiver).
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_LEAD = "lead_minutes"

    const val ALARM_CHANNEL_ID = "omniwork_alarmas_channel"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // Canal de alarmas: sonido de alarma, vibración y máxima importancia
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarmas OmniWork",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarmas de la agenda: suenan a la hora exacta aunque la app esté cerrada"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(alarmChannel)

        ReminderNotificationHelper.initNotificationChannel(context)
    }

    /**
     * Programa TODAS las alarmas futuras: por cada tarea, una alarma por cada anticipación
     * efectiva (las propias de la tarea o, si no tiene, las omisión de Config). Barato y
     * idempotente: se puede llamar seguido; las alarmas que ya no aplican se cancelan
     * usando el registro persistente de códigos.
     */
    fun scheduleNext(context: Context) {
        initChannels(context)
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val prefs = AppPreferences(appContext)
                val tasks = AppDatabase.getInstance(appContext).taskDao().getAllTasksSync()
                    .filter { !it.isCompleted && it.dueTimestamp > 0L }
                val am = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return@launch
                val defaults = prefs.getNotifLeadDefaults()

                val now = System.currentTimeMillis()
                // code -> (triggerAt, taskId, lead): solo disparos futuros
                val desired = HashMap<Int, Triple<Long, Long, Int>>()
                tasks.forEach { task ->
                    NotificationLeads.effectiveFor(task.notifLeadsCsv, defaults).forEach { lead ->
                        val triggerAt = task.dueTimestamp - lead * 60_000L
                        if (triggerAt > now + 5_000L) {
                            desired[NotificationLeads.requestCode(task.id, lead)] = Triple(triggerAt, task.id, lead)
                        }
                    }
                }

                // Cancelar alarmas registradas que ya no aplican (tarea borrada/completada/editada)
                prefs.getScheduledAlarmCodes().forEach { code ->
                    if (code !in desired) {
                        am.cancel(
                            PendingIntent.getBroadcast(
                                appContext, code,
                                Intent(appContext, TaskAlarmReceiver::class.java),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                        )
                    }
                }

                val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.canScheduleExactAlarms()
                } else true

                desired.forEach { (code, spec) ->
                    val (triggerAt, taskId, lead) = spec
                    val intent = Intent(appContext, TaskAlarmReceiver::class.java).apply {
                        putExtra(EXTRA_TASK_ID, taskId)
                        putExtra(EXTRA_LEAD, lead)
                    }
                    val pending = PendingIntent.getBroadcast(
                        appContext, code, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try {
                        if (canExact) {
                            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                        }
                    } catch (se: SecurityException) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                    }
                }
                prefs.setScheduledAlarmCodes(desired.keys)
                Log.d(TAG, "Alarmas programadas: ${desired.size}")
            } catch (t: Throwable) {
                Log.e(TAG, "scheduleNext error: ${t.message}", t)
            }
        }
    }
}

/**
 * Recibe la alarma del sistema: muestra la notificación (sonando si es ALARMA) y,
 * si la tarea es un evento recurrente, rueda su vencimiento a la siguiente
 * ocurrencia y re-programa la alarma.
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                AlarmScheduler.initChannels(appContext)
                val dao = AppDatabase.getInstance(appContext).taskDao()
                val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
                val task = dao.getAllTasksSync().firstOrNull { it.id == taskId }

                if (task != null && !task.isCompleted && task.dueTimestamp > 0L) {
                    fireNotification(appContext, task)
                    rollIfRecurrent(appContext, dao, task)
                }
                AlarmScheduler.scheduleNext(appContext)
            } catch (t: Throwable) {
                Log.e("TaskAlarmReceiver", "error: ${t.message}", t)
                AlarmScheduler.scheduleNext(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private fun fireNotification(context: Context, task: WorkTask) {
        val now = System.currentTimeMillis()
        val remainingMs = task.dueTimestamp - now
        val remainingText = when {
            remainingMs <= 60_000L -> "es ahora"
            remainingMs < 3_600_000L -> "faltan ${remainingMs / 60_000L} min"
            remainingMs < 86_400_000L -> "faltan ${remainingMs / 3_600_000L} h ${((remainingMs % 3_600_000L) / 60_000L)} min"
            else -> "faltan ${remainingMs / 86_400_000L} días"
        }
        val isAlarm = task.taskType == TaskTypes.ALARMA

        val hasLink = task.meetingLink.isNotBlank()
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (hasLink) {
                putExtra("nav_meetings", true)
                putExtra("capture_title", task.title)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context, task.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, if (isAlarm) AlarmScheduler.ALARM_CHANNEL_ID else ReminderNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (isAlarm) "ALARMA · ${task.title}"
                else task.title
            )
            .setContentText("$remainingText · ${task.jobTag}")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$remainingText\n${task.title}\nProyecto: ${task.jobTag}" +
                        (if (task.description.isNotBlank()) "\n${task.description.take(160)}" else ""))
            )
            .setPriority(if (isAlarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        // Junta en línea: botón directo para capturar la reunión (Meet/Teams/Zoom)
        if (hasLink) {
            builder.addAction(
                0, "Capturar junta",
                PendingIntent.getActivity(
                    context, (task.id + 80000).toInt(),
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }

        if (isAlarm) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            // Pantalla completa (Android <14 directo; 14+ con permiso concedido)
            if (Build.VERSION.SDK_INT < 34 ||
                context.checkSelfPermission(android.Manifest.permission.USE_FULL_SCREEN_INTENT) == PackageManager.PERMISSION_GRANTED) {
                val fullScreen = PendingIntent.getActivity(
                    context, (task.id + 70000).toInt(),
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.setFullScreenIntent(fullScreen, true)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            NotificationManagerCompat.from(context).notify((task.id + 50000).toInt(), builder.build())
        } catch (t: Throwable) {
            Log.e("TaskAlarmReceiver", "notify error: ${t.message}", t)
        }
    }

    /** Evento recurrente: al pasar su hora, rueda a la siguiente ocurrencia (sin completarse). */
    private suspend fun rollIfRecurrent(context: Context, dao: com.example.data.db.TaskDao, task: WorkTask) {
        if (task.recurrenceType == RecurrenceHelper.NONE || task.isCompleted) return
        val next = RecurrenceHelper.nextOccurrenceAfter(task.recurrenceType, task.recurrenceAnchor, task.dueTimestamp)
        if (next != null && next > task.dueTimestamp) {
            try {
                dao.updateTask(task.copy(dueTimestamp = next))
            } catch (t: Throwable) {
                Log.e("TaskAlarmReceiver", "roll error: ${t.message}", t)
            }
        }
    }
}

/** Tras reiniciar el teléfono, las alarmas se pierden: este receiver las re-programa. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            AlarmScheduler.scheduleNext(context)
        }
    }
}
