package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.ui.MainViewModel
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

@Composable
fun SecurityLockScreen(
    viewModel: MainViewModel,
    onUnlocked: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate950),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(20.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Slate900),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Slate700))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(CyanNeon.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(28.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Bóveda Protegida", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Ingresa tu PIN de seguridad para acceder a tus juntas, notas y proyectos.", color = Slate400, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = enteredPin,
                    onValueChange = {
                        if (it.length <= 6) {
                            enteredPin = it
                            errorMessage = null
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    placeholder = { Text("PIN de 4 dígitos", color = Slate400) },
                    modifier = Modifier.fillMaxWidth().testTag("pin_input_field"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    )
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(errorMessage!!, color = RoseError, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val success = viewModel.unlockApp(enteredPin)
                        if (success) {
                            onUnlocked()
                        } else {
                            errorMessage = "PIN incorrecto. Intenta nuevamente."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("unlock_submit_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D))
                ) {
                    Text("Desbloquear Asistente", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var newPin by remember { mutableStateOf(viewModel.prefs.getPin()) }
    var selectedRetention by remember { mutableStateOf(viewModel.prefs.getRetentionDays()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        title = { Text("Configuración de Seguridad y Bóveda", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("PIN de Bloqueo de la App:", color = Slate400, fontSize = 12.sp)
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    placeholder = { Text("Dejar vacío para desactivar", color = Slate700) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Slate700
                    )
                )

                Text("Retención Garantizada de la Bóveda:", color = Slate400, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 30, 90, 365).forEach { days ->
                        FilterChip(
                            label = "$days Días",
                            isSelected = selectedRetention == days,
                            onClick = { selectedRetention = days }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.prefs.setPin(newPin)
                    viewModel.prefs.setRetentionDays(selectedRetention)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = Color(0xFF00363D))
            ) {
                Text("Guardar Cambios", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Slate400) }
        }
    )
}
