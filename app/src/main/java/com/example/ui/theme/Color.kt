package com.example.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Estado global del tema. Los colores son getters respaldados por snapshot state,
 * así TODA la app (que ya importa estos nombres) cambia de dark a light sin tocar pantallas.
 */
object NeoxThemeState {
    var isDark by mutableStateOf(true)
}

/**
 * PALETA OSCURA — "Azul Medianoche": fondo casi negro azulado y tarjetas claramente
 * elevadas (contraste real fondo↔tarjeta = sensación de profundidad, no plano gris).
 */
private object DarkPalette {
    val slate950 = Color(0xFF050912)   // fondo de página (profundo)
    val slate900 = Color(0xFF101A2E)   // tarjetas (elevadas, azul navy)
    val slate800 = Color(0xFF1B2942)   // chips / superficies elevadas
    val slate700 = Color(0xFF2D4064)   // bordes (azulados, visibles)
    val slate600 = Color(0xFF5A6E92)
    val slate400 = Color(0xFF9BB0CC)   // texto secundario (claro, legible)
    val slate200 = Color(0xFFCBD8E8)
    val slate100 = Color(0xFFEDF2F9)
    val cyanNeon = Color(0xFF22D3EE)   // cian firma (neón contenido)
    val cyanAccent = Color(0xFF38BDF8)
    val cyanSurface = Color(0xFF0B3B4A)
    val violet = Color(0xFFA78BFA)
    val violetSurface = Color(0xFF2E1065)
    val emerald = Color(0xFF34D399)
    val amber = Color(0xFFFBBF24)
    val rose = Color(0xFFFB7185)
    val textPrimary = Color(0xFFF3F6FB)
}

/**
 * PALETA CLARA — "Porcelana Fría": fondo gris-azulado suave, tarjetas blancas puras,
 * acento teal profundo (profesional, nunca fluorescente sobre blanco).
 */
private object LightPalette {
    val slate950 = Color(0xFFE7EDF5)   // fondo de página
    val slate900 = Color(0xFFFFFFFF)   // tarjetas
    val slate800 = Color(0xFFE4EBF4)   // chips / superficies
    val slate700 = Color(0xFFC4D2E4)   // bordes
    val slate600 = Color(0xFF64748B)
    val slate400 = Color(0xFF4E6076)   // texto secundario (contraste alto)
    val slate200 = Color(0xFF2E3E52)
    val slate100 = Color(0xFF101A2A)
    val cyanNeon = Color(0xFF0E7490)   // teal profundo para claro
    val cyanAccent = Color(0xFF0B6E99)
    val cyanSurface = Color(0xFFD6F1F9)
    val violet = Color(0xFF6D28D9)
    val violetSurface = Color(0xFFEDE9FE)
    val emerald = Color(0xFF047857)
    val amber = Color(0xFFB45309)
    val rose = Color(0xFFDC2626)
    val textPrimary = Color(0xFF0B1526)
}

val Slate950: Color get() = if (NeoxThemeState.isDark) DarkPalette.slate950 else LightPalette.slate950
val Slate900: Color get() = if (NeoxThemeState.isDark) DarkPalette.slate900 else LightPalette.slate900
val Slate800: Color get() = if (NeoxThemeState.isDark) DarkPalette.slate800 else LightPalette.slate800
val Slate700: Color get() = if (NeoxThemeState.isDark) DarkPalette.slate700 else LightPalette.slate700
val Slate600: Color get() = if (NeoxThemeState.isDark) DarkPalette.slate600 else LightPalette.slate600
val Slate400: Color get() = if (NeoxThemeState.isDark) DarkPalette.slate400 else LightPalette.slate400
val Slate200: Color get() = if (NeoxThemeState.isDark) DarkPalette.slate200 else LightPalette.slate200
val Slate100: Color get() = if (NeoxThemeState.isDark) DarkPalette.slate100 else LightPalette.slate100

val CyanNeon: Color get() = if (NeoxThemeState.isDark) DarkPalette.cyanNeon else LightPalette.cyanNeon
val CyanAccent: Color get() = if (NeoxThemeState.isDark) DarkPalette.cyanAccent else LightPalette.cyanAccent
val CyanSurface: Color get() = if (NeoxThemeState.isDark) DarkPalette.cyanSurface else LightPalette.cyanSurface

val VioletAccent: Color get() = if (NeoxThemeState.isDark) DarkPalette.violet else LightPalette.violet
val VioletSurface: Color get() = if (NeoxThemeState.isDark) DarkPalette.violetSurface else LightPalette.violetSurface

val EmeraldSuccess: Color get() = if (NeoxThemeState.isDark) DarkPalette.emerald else LightPalette.emerald
val AmberWarning: Color get() = if (NeoxThemeState.isDark) DarkPalette.amber else LightPalette.amber
val RoseError: Color get() = if (NeoxThemeState.isDark) DarkPalette.rose else LightPalette.rose

/** Texto principal sobre superficies: blanco-azulado en dark, casi-negro en light. */
val TextPrimary: Color get() = if (NeoxThemeState.isDark) DarkPalette.textPrimary else LightPalette.textPrimary

/** Color "encima del cian": navy profundo sobre el cian (dark), blanco sobre el teal (light). */
val OnCyan: Color get() = if (NeoxThemeState.isDark) Color(0xFF04222B) else Color(0xFFFFFFFF)

/** Extremo inferior del degradado del lienzo de la app (profundidad de página). */
val AppBgDeep: Color get() = if (NeoxThemeState.isDark) Color(0xFF02040A) else Color(0xFFD9E2F0)

/** Burbuja del usuario en el chat: azul fijo en ambos temas, con texto claro fijo. */
val BubbleUserBg: Color get() = Color(0xFF0369A1)
val OnBubbleUser: Color get() = Color(0xFFE0F2FE)
