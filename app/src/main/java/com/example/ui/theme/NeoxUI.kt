package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Lenguaje visual Neox: acentos con degradado cian→violeta y franjas de encabezado.
 * Helpers de SOLO presentación — ninguna lógica.
 */
object NeoxStyle {

    /** Degradado de acento principal (franjas, indicadores, botones destacados). */
    @Composable
    fun accentBrush(): Brush = Brush.linearGradient(listOf(CyanNeon, VioletAccent))
}

/** Franja superior fina para tarjetas de encabezado de sección. */
@Composable
fun AccentStrip() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(NeoxStyle.accentBrush())
    )
}
