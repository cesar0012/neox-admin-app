package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.OmniWorkApp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

class CrashReportActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CRASH_REPORT = "EXTRA_CRASH_REPORT"
        const val EXTRA_ERROR_MESSAGE = "EXTRA_ERROR_MESSAGE"
        const val EXTRA_STACKTRACE = "EXTRA_STACKTRACE"
        const val EXTRA_DEVICE_INFO = "EXTRA_DEVICE_INFO"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val fullReport = intent.getStringExtra(EXTRA_CRASH_REPORT)
            ?: OmniWorkApp.getLastCrashReport(this)
            ?: "No hay detalles del error disponibles."
        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "Error no especificado"
        val stackTrace = intent.getStringExtra(EXTRA_STACKTRACE) ?: fullReport
        val deviceInfo = intent.getStringExtra(EXTRA_DEVICE_INFO) ?: ""

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Slate950
                ) { innerPadding ->
                    CrashReportContent(
                        modifier = Modifier.padding(innerPadding),
                        errorMessage = errorMessage,
                        deviceInfo = deviceInfo,
                        stackTrace = stackTrace,
                        fullReport = fullReport,
                        onCopy = {
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("OmniWork Crash Log", fullReport)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(this, "¡Informe copiado al portapapeles! Puedes pegarlo en el chat.", Toast.LENGTH_LONG).show()
                        },
                        onRestart = {
                            OmniWorkApp.clearLastCrash(this)
                            val intent = Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            startActivity(intent)
                            finish()
                        },
                        onSafeModeReset = {
                            try {
                                OmniWorkApp.clearLastCrash(this)
                                deleteDatabase("omniwork_vault.db")
                                val prefs = getSharedPreferences("omniwork_app_prefs", Context.MODE_PRIVATE)
                                prefs.edit().clear().apply()
                                Toast.makeText(this, "Datos locales reiniciados en Modo Seguro.", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                            val intent = Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CrashReportContent(
    modifier: Modifier = Modifier,
    errorMessage: String,
    deviceInfo: String,
    stackTrace: String,
    fullReport: String,
    onCopy: () -> Unit,
    onRestart: () -> Unit,
    onSafeModeReset: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(RoseError.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    "OmniWork — Informe de Cierre",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Se capturó el error que causó el cierre en tu dispositivo",
                    color = Slate400,
                    fontSize = 12.sp
                )
            }
        }

        if (deviceInfo.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Slate900),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Información del Entorno:",
                        color = CyanNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        deviceInfo,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Slate900),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Causa / Mensaje:",
                    color = RoseError,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    errorMessage,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            "Detalle Técnico (Stack Trace):",
            color = Slate400,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF090D16))
                .border(1.dp, Slate700, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            val traceScrollState = rememberScrollState()
            val traceHScrollState = rememberScrollState()
            Text(
                text = stackTrace,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
                modifier = Modifier
                    .verticalScroll(traceScrollState)
                    .horizontalScroll(traceHScrollState)
            )
        }

        Button(
            onClick = onCopy,
            colors = ButtonDefaults.buttonColors(
                containerColor = CyanNeon,
                contentColor = Color(0xFF00363D)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copiar Todo el Error para el Chat", fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Slate800,
                    contentColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reintentar Abrir", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onSafeModeReset,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = RoseError
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(RoseError.copy(alpha = 0.5f))
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Modo Seguro", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
