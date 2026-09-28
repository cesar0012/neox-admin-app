package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.ui.CrashReportActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OmniWorkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val deviceInfo = "Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BRAND})\n" +
                        "Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "Hardware: ${Build.HARDWARE} / ${Build.DEVICE}\n" +
                        "Fecha y Hora: $timeStr"

                val errorMessage = throwable.message ?: throwable.javaClass.name
                val fullReport = "=== INFORME DE ERROR OMNIWORK ===\n\n" +
                        "$deviceInfo\n\n" +
                        "Thread: ${thread.name} (id: ${thread.id})\n" +
                        "Excepción: ${throwable.javaClass.name}\n" +
                        "Mensaje: $errorMessage\n\n" +
                        "--- STACK TRACE ---\n$stackTrace"

                Log.e("OmniWorkCrash", fullReport, throwable)

                // Persist to SharedPreferences
                val prefs = getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
                prefs.edit()
                    .putString(KEY_LAST_CRASH_REPORT, fullReport)
                    .putString(KEY_LAST_CRASH_MESSAGE, errorMessage)
                    .putString(KEY_LAST_CRASH_STACKTRACE, stackTrace)
                    .putString(KEY_DEVICE_INFO, deviceInfo)
                    .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis())
                    .commit()

                // Persist to internal file
                try {
                    val file = File(filesDir, "crash_log.txt")
                    file.writeText(fullReport)
                } catch (e: Exception) {
                    Log.e("OmniWorkCrash", "No se pudo escribir archivo de log: ${e.message}")
                }

                // Launch CrashReportActivity in a new task
                val crashIntent = Intent(this, CrashReportActivity::class.java).apply {
                    putExtra(CrashReportActivity.EXTRA_CRASH_REPORT, fullReport)
                    putExtra(CrashReportActivity.EXTRA_ERROR_MESSAGE, errorMessage)
                    putExtra(CrashReportActivity.EXTRA_STACKTRACE, stackTrace)
                    putExtra(CrashReportActivity.EXTRA_DEVICE_INFO, deviceInfo)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(crashIntent)

                // Terminate crashing process cleanly
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            } catch (e: Exception) {
                Log.e("OmniWorkCrash", "Falla en CrashHandler: ${e.message}", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        const val PREFS_CRASH = "omniwork_crash_prefs"
        const val KEY_LAST_CRASH_REPORT = "key_last_crash_report"
        const val KEY_LAST_CRASH_MESSAGE = "key_last_crash_message"
        const val KEY_LAST_CRASH_STACKTRACE = "key_last_crash_stacktrace"
        const val KEY_DEVICE_INFO = "key_device_info"
        const val KEY_LAST_CRASH_TIME = "key_last_crash_time"

        fun getLastCrashReport(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
            return prefs.getString(KEY_LAST_CRASH_REPORT, null)
        }

        fun clearLastCrash(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_CRASH, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            try {
                File(context.filesDir, "crash_log.txt").delete()
            } catch (_: Exception) {}
        }
    }
}
