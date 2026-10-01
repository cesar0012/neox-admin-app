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

private object DarkPalette {
    val slate950 = Color(0xFF090D16)
    val slate900 = Color(0xFF0F172A)
    val slate800 = Color(0xFF1E293B)
    val slate700 = Color(0xFF334155)
    val slate600 = Color(0xFF475569)
    val slate400 = Color(0xFF94A3B8)
    val slate200 = Color(0xFFE2E8F0)
    val slate100 = Color(0xFFF1F5F9)
    val cyanNeon = Color(0xFF00E5FF)
    val cyanAccent = Color(0xFF0284C7)
    val cyanSurface = Color(0xFF0C4A6E)
    val violet = Color(0xFF8B5CF6)
    val violetSurface = Color(0xFF4C1D95)
    val emerald = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val rose = Color(0xFFEF4444)
    val textPrimary = Color.White
}

private object LightPalette {
    val slate950 = Color(0xFFE8EEF5)   // fondo de página
    val slate900 = Color(0xFFFFFFFF)   // tarjetas
    val slate800 = Color(0xFFF1F5F9)   // chips / superficies elevadas
    val slate700 = Color(0xFFCBD5E1)   // bordes
    val slate600 = Color(0xFF64748B)
    val slate400 = Color(0xFF5B6B7E)   // texto secundario (contraste en blanco)
    val slate200 = Color(0xFF334155)
    val slate100 = Color(0xFF0F172A)
    val cyanNeon = Color(0xFF0891B2)   // cian oscurecido para contraste
    val cyanAccent = Color(0xFF0369A1)
    val cyanSurface = Color(0xFFCFFAFE)
    val violet = Color(0xFF7C3AED)
    val violetSurface = Color(0xFFEDE9FE)
    val emerald = Color(0xFF059669)
    val amber = Color(0xFFB45309)
    val rose = Color(0xFFDC2626)
    val textPrimary = Color(0xFF0F172A)
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

/** Texto principal sobre superficies: blanco en dark, casi-negro en light. */
val TextPrimary: Color get() = if (NeoxThemeState.isDark) DarkPalette.textPrimary else LightPalette.textPrimary
