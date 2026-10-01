package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.theme.Slate950
import com.example.ui.theme.OnCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.VioletAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DesktopBridgeScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isRunning by viewModel.webhookServer.isRunning.collectAsStateWithLifecycle()
    val logs by viewModel.webhookServer.logsFlow.collectAsStateWithLifecycle()
    val localIp = viewModel.webhookServer.getLocalIpAddress()
    val port = viewModel.webhookServer.port
    val endpointUrl = "http://$localIp:$port"

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Wi-Fi Webhook Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(if (isRunning) EmeraldSuccess.copy(alpha = 0.5f) else Slate700)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Lan, contentDescription = null, tint = if (isRunning) EmeraldSuccess else Slate400)
                            Text("Servidor Webhook Wi-Fi", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isRunning) EmeraldSuccess.copy(alpha = 0.2f) else RoseError.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (isRunning) "En Línea (Wi-Fi)" else "Detenido",
                                color = if (isRunning) EmeraldSuccess else RoseError,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("URL Endpoint Local:", color = Slate400, fontSize = 11.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = endpointUrl,
                            color = CyanNeon,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("OmniWork Endpoint", endpointUrl))
                                Toast.makeText(context, "URL copiada", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = Slate400, modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (isRunning) viewModel.webhookServer.stop()
                            else viewModel.webhookServer.start()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Slate800 else EmeraldSuccess,
                            contentColor = if (isRunning) RoseError else OnCyan
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("toggle_webhook_btn")
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "Detener Servidor" else "Iniciar Servidor en Wi-Fi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Cloud Code & IDE Direct Code Snippet Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                            Text("Comando para Cloud Code / Terminal", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Ejecuta este cURL en tu terminal o configúralo como hook en Cloud Code / VS Code para enviar instrucciones al celular:",
                        color = Slate400,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    val sampleCurl = """curl -X POST http://$localIp:$port/webhook/context \
  -H "Content-Type: application/json" \
  -d '{"title": "Instrucción Cloud Code", "content": "Refactorizar endpoints y autenticación", "jobTag": "Dev"}'""".trimIndent()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate950)
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("cURL (Bash / Cloud Code)", color = VioletAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("cURL OmniWork", sampleCurl))
                                        Toast.makeText(context, "cURL copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp).testTag("copy_curl_btn")
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = CyanNeon, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = sampleCurl,
                                color = Color(0xFFA5F3FC),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }

        // Integration Exports: ZIP and Markdown Guide
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Laptop, contentDescription = null, tint = CyanNeon)
                        Text("Exportadores para PC y Editores", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Descarga los archivos listos para vincular tu navegador y tus editores de código con esta app en el celular.",
                        color = Slate400,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Button 1: Markdown instructions file
                    Button(
                        onClick = { viewModel.exportIdeIntegrationDoc(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("export_md_doc_btn")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanNeon)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Descargar Guía Completa (OMNIWORK_INTEGRATION.md)", fontSize = 12.sp)
                    }

                    // Button 2: Chrome Extension ZIP
                    Button(
                        onClick = { viewModel.exportChromeExtension(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = OnCyan),
                        modifier = Modifier.fillMaxWidth().testTag("export_chrome_zip_btn")
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar Extensión de Chrome (.ZIP)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Activity Logs
        item {
            Text(
                text = "Registros de Sincronización en Vivo (${logs.size})",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (logs.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    Text("Esperando solicitudes de la extensión o editores...", color = Slate400, fontSize = 12.sp)
                }
            }
        } else {
            items(logs) { log ->
                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate900)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(log.endpoint, color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("• ${log.source}", color = VioletAccent, fontSize = 10.sp)
                            }
                            Text(log.summary, color = Slate400, fontSize = 11.sp)
                        }
                        Text(timeStr, color = Slate700, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
