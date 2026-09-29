package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.rotator.ModelCandidate
import com.example.ui.MainViewModel
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.VioletAccent

@Composable
fun RotatorStatusScreen(viewModel: MainViewModel) {
    val status by viewModel.rotatorStatus.collectAsStateWithLifecycle()
    val isBenchmarking by viewModel.benchmarkingActive.collectAsStateWithLifecycle()

    var primaryProvider by remember { mutableStateOf(viewModel.rotator.config.primaryProvider) }
    var fallbackProvider by remember { mutableStateOf(viewModel.rotator.config.fallbackProvider) }
    var localhostEnabled by remember { mutableStateOf(viewModel.rotator.config.localhostEnabled) }
    var localhostUrl by remember { mutableStateOf(viewModel.rotator.config.localhostUrl) }
    var localhostModel by remember { mutableStateOf(viewModel.rotator.config.localhostModelId) }

    // Auto-refresh catalog on entering screen
    LaunchedEffect(Unit) {
        viewModel.rotator.refreshCatalog(force = false)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Main Rotator Overview Card
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                        Text("Rotador de Modelos Gratuitos", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Modelo Seleccionado para Consultas:", color = Slate400, fontSize = 11.sp)
                    Text(
                        text = status.currentModelKey ?: "Buscando modelo disponible...",
                        color = CyanNeon,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("Catálogo Total", color = Slate400, fontSize = 10.sp)
                                Text("${status.totalCandidates}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("Disponibles", color = Slate400, fontSize = 10.sp)
                                Text("${status.availableCount}", color = EmeraldSuccess, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800)
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("En Cooldown", color = Slate400, fontSize = 10.sp)
                                Text("${status.coolingCount}", color = AmberWarning, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.forceRotateModel() },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = CyanNeon),
                            modifier = Modifier.weight(1f).testTag("rotator_force_btn")
                        ) {
                            Icon(Icons.Default.Cached, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotar Ahora", fontSize = 11.sp)
                        }

                        Button(
                            onClick = { viewModel.refreshRotatorCatalog() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D)),
                            modifier = Modifier.weight(1.2f).testTag("rotator_refresh_btn")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Escanear Catálogo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Provider Strategy Configuration (Primary vs Fallback)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Slate900),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate800))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Estrategia de Proveedores Oficial vs Fallback", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                    Text("Proveedor Principal (Oficial):", color = Slate400, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("OPENROUTER", "NVIDIA").forEach { p ->
                            FilterChip(
                                label = if (p == "OPENROUTER") "OpenRouter Free" else "NVIDIA NIM",
                                isSelected = primaryProvider == p,
                                onClick = {
                                    primaryProvider = p
                                    viewModel.rotator.updateConfig(primaryProvider = p)
                                }
                            )
                        }
                    }

                    Text("Proveedor Secundario (Fallback):", color = Slate400, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NVIDIA", "OPENROUTER").forEach { p ->
                            FilterChip(
                                label = if (p == "NVIDIA") "NVIDIA NIM" else "OpenRouter Free",
                                isSelected = fallbackProvider == p,
                                onClick = {
                                    fallbackProvider = p
                                    viewModel.rotator.updateConfig(fallbackProvider = p)
                                }
                            )
                        }
                    }

                    // API keys enmascaradas: nunca quedan visibles una vez guardadas
                    var editingNvidiaKey by remember { mutableStateOf(viewModel.rotator.config.nvidiaApiKey.isBlank()) }
                    var nvidiaKeyInput by remember { mutableStateOf("") }

                    if (!editingNvidiaKey) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NVIDIA NIM: ••••••••" + viewModel.rotator.config.nvidiaApiKey.takeLast(4),
                                color = EmeraldSuccess,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(onClick = { editingNvidiaKey = true; nvidiaKeyInput = "" }) {
                                Text("Cambiar", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = nvidiaKeyInput,
                            onValueChange = {
                                nvidiaKeyInput = it
                                if (it.isNotBlank()) viewModel.rotator.updateConfig(nvidiaKey = it.trim())
                            },
                            label = { Text("NVIDIA NIM API Key (nvapi-...)", color = Slate400, fontSize = 12.sp) },
                            placeholder = { Text("Pega tu nueva clave aquí", color = Slate700, fontSize = 12.sp) },
                            visualTransformation = PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { editingNvidiaKey = false }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Listo", tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Slate700
                            )
                        )
                    }

                    var editingOpenRouterKey by remember { mutableStateOf(viewModel.rotator.config.openRouterApiKey.isBlank()) }
                    var openRouterKeyInput by remember { mutableStateOf("") }

                    if (!editingOpenRouterKey) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewModel.rotator.config.openRouterApiKey.isBlank()) "OpenRouter: sin clave configurada"
                                else "OpenRouter: ••••••••" + viewModel.rotator.config.openRouterApiKey.takeLast(4),
                                color = if (viewModel.rotator.config.openRouterApiKey.isBlank()) Slate400 else EmeraldSuccess,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            TextButton(onClick = { editingOpenRouterKey = true; openRouterKeyInput = "" }) {
                                Text("Cambiar", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = openRouterKeyInput,
                            onValueChange = {
                                openRouterKeyInput = it
                                if (it.isNotBlank()) viewModel.rotator.updateConfig(openRouterKey = it.trim())
                            },
                            label = { Text("OpenRouter API Key (Opcional)", color = Slate400, fontSize = 12.sp) },
                            placeholder = { Text("Pega tu nueva clave aquí", color = Slate700, fontSize = 12.sp) },
                            visualTransformation = PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { editingOpenRouterKey = false }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Listo", tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Slate700
                            )
                        )
                    }

                    // Localhost toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Slate800)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Habilitar Localhost (Ollama en celular/PC)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("Solo actívalo si tienes un servidor LLM local corriendo", color = Slate400, fontSize = 10.sp)
                        }
                        Switch(
                            checked = localhostEnabled,
                            onCheckedChange = {
                                localhostEnabled = it
                                viewModel.rotator.updateConfig(localhostEnabled = it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00363D),
                                checkedTrackColor = CyanNeon
                            )
                        )
                    }

                    if (localhostEnabled) {
                        OutlinedTextField(
                            value = localhostUrl,
                            onValueChange = {
                                localhostUrl = it
                                viewModel.rotator.updateConfig(localhostUrl = it)
                            },
                            label = { Text("URL Localhost (ej: http://localhost:11434/v1)", color = Slate400) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Slate700
                            )
                        )
                    }
                }
            }
        }

        // Active Models Catalog List
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Catálogo de Modelos Activos (${status.candidates.size})",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = { viewModel.resetRotatorCooldowns() },
                    colors = ButtonDefaults.buttonColors(containerColor = Slate800, contentColor = Slate400)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reiniciar Cooldowns", fontSize = 10.sp)
                }
            }
        }

        items(status.candidates, key = { it.modelKey }) { candidate ->
            CleanCandidateCard(candidate = candidate)
        }
    }
}

@Composable
fun CleanCandidateCard(candidate: ModelCandidate) {
    val isCooling = candidate.isCoolingDown()
    val providerColor = when (candidate.provider.lowercase()) {
        "nvidia" -> EmeraldSuccess
        "localhost" -> VioletAccent
        else -> CyanNeon
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Slate900),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isCooling) AmberWarning else Slate800)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(providerColor.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(candidate.provider.uppercase(), color = providerColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = candidate.modelId.substringAfter("/"),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = "Score: ${candidate.effectiveScore().toInt()} | Contexto: ${candidate.contextLength / 1000}k",
                    color = Slate400,
                    fontSize = 10.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isCooling) AmberWarning.copy(alpha = 0.2f) else EmeraldSuccess.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isCooling) "En Enfriamiento" else "Disponible",
                    color = if (isCooling) AmberWarning else EmeraldSuccess,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
