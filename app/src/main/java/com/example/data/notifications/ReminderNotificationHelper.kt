package com.example.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object ReminderNotificationHelper {

    const val CHANNEL_ID = "omniwork_reminders_channel"
    private const val CHANNEL_NAME = "Recordatorios Proactivos OmniWork"
    private const val CHANNEL_DESC = "Notificaciones de tareas y reuniones importantes con anticipación"
    private const val TAG = "NotificationHelper"

    fun initNotificationChannel(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing notification channel: ${t.message}", t)
        }
    }

    fun showTaskReminder(
        context: Context,
        notificationId: Int,
        title: String,
        description: String,
        jobTag: String,
        advanceNoticeText: String
    ) {
        try {
            initNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⏰ [$jobTag] $title")
                .setContentText("$advanceNoticeText: $description")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$advanceNoticeText: $description\n\nProyecto: $jobTag")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (t: Throwable) {
            Log.e(TAG, "Could not show task reminder notification: ${t.message}", t)
        }
    }

    fun showSmartDeadlineAlert(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        alertType: String,
        jobTag: String
    ) {
        try {
            initNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("[$alertType - $jobTag] $title")
                .setContentText(message)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("$message\n\nProyecto: $jobTag\nAlerta proactiva de Neox Admin")
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (t: Throwable) {
            Log.e(TAG, "Could not show deadline alert notification: ${t.message}", t)
        }
    }

    fun checkAndTriggerIntelligentReminders(context: Context, tasks: List<com.example.data.model.WorkTask>) {
        try {
            initNotificationChannel(context)
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 3600 * 1000L
            val fiveDaysMs = 5 * oneDayMs

            // Comparación por DÍAS DE CALENDARIO (no milisegundos crudos): una tarea de
            // mañana a las 10:00 revisada hoy a las 23:00 es "mañana", nunca "vence hoy".
            fun startOfDay(ts: Long): Long {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = ts
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis
            }
            val todayStart = startOfDay(now)

            tasks.filter { !it.isCompleted }.forEach { task ->
                if (task.dueTimestamp <= 0L) return@forEach // sin fecha: nunca alerta
                val remainingMs = task.dueTimestamp - now
                val dayDiff = ((startOfDay(task.dueTimestamp) - todayStart) / oneDayMs).toInt()
                val totalDurationMs = task.dueTimestamp - task.createdAt
                val elapsedMs = now - task.createdAt

                when {
                    // Overdue
                    dayDiff < 0 -> {
                        showSmartDeadlineAlert(
                            context,
                            notificationId = (task.id + 10000).toInt(),
                            title = task.title,
                            message = "TAREA VENCIDA — Esta entrega estaba programada para días anteriores. Conclúyela o actualiza su estado en la agenda.",
                            alertType = "VENCIDA",
                            jobTag = task.jobTag
                        )
                    }
                    // Due Today (por fecha de calendario)
                    dayDiff == 0 -> {
                        showSmartDeadlineAlert(
                            context,
                            notificationId = (task.id + 20000).toInt(),
                            title = task.title,
                            message = "VENCE HOY — Debes concluir esta tarea el día de hoy (${task.jobTag}).",
                            alertType = "VENCE HOY",
                            jobTag = task.jobTag
                        )
                    }
                    // Due in next 5 days
                    dayDiff in 1..5 -> {
                        showSmartDeadlineAlert(
                            context,
                            notificationId = (task.id + 30000).toInt(),
                            title = task.title,
                            message = "PRÓXIMOS DÍAS — Quedan $dayDiff día" + (if (dayDiff == 1) "" else "s") + " para la fecha de entrega.",
                            alertType = "PRÓXIMOS 5 DÍAS",
                            jobTag = task.jobTag
                        )
                    }
                    // Halfway or 10-day remaining effort rule
                    dayDiff in 6..10 || (totalDurationMs > 3 * oneDayMs && elapsedMs >= totalDurationMs / 2) -> {
                        showSmartDeadlineAlert(
                            context,
                            notificationId = (task.id + 40000).toInt(),
                            title = task.title,
                            message = "ALERTA DE AVANCE — Quedan $dayDiff días para concluir este proyecto. Se recomienda iniciar y asegurar el tiempo de desarrollo necesario.",
                            alertType = "REGLA DE ENTREGA",
                            jobTag = task.jobTag
                        )
                    }
                    // Sin ventana activa: no notificar (el remainingMs ya no se usa como criterio)
                    else -> if (remainingMs < 0) { /* cubierto arriba */ }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error checking intelligent reminders: ${t.message}", t)
        }
    }
}
