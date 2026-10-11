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
 * PALETA OSCURA — "Ciruela Nocturna": fondo ciruela casi negro, tarjetas flotantes sin borde
 * (elevación por contraste real fondo↔tarjeta), acentos azul royal + violeta con glow.
 * Tokens extraídos del diseño de referencia (dashboard violeta).
 */
private object DarkPalette {
    val slate950 = Color(0xFF1A1523)   // fondo de página (ciruela profundo)
    val slate900 = Color(0xFF262031)   // tarjetas (flotan sobre el fondo)
    val slate800 = Color(0xFF332D42)   // chips / superficies elevadas
    val slate700 = Color(0xFF3E3750)   // separadores (sutil, casi invisible)
    val slate600 = Color(0xFF5A5470)
    val slate400 = Color(0xFF9A94B0)   // texto secundario (lila claro, legible)
    val slate200 = Color(0xFFCFC9DE)
    val slate100 = Color(0xFFF1EEF9)
    val cyanNeon = Color(0xFF5B8DEF)   // azul royal firma (botones/activos)
    val cyanAccent = Color(0xFF7BA8F5) // azul claro para iconos/acentos suaves
    val cyanSurface = Color(0xFF1F2C4E) // superficie azul-teñida
    val violet = Color(0xFFA855F7)     // violeta glow (FAB / acento secundario)
    val violetSurface = Color(0xFF372452)
    val emerald = Color(0xFF4ADE80)
    val amber = Color(0xFFFBBF24)
    val rose = Color(0xFFFB7185)
    val textPrimary = Color(0xFFF4F2FA)
}

/**
 * PALETA CLARA — "Lavanda Seda": fondo lavanda muy claro, tarjetas blancas puras flotando,
 * azul royal profundo como acento (contraste AAA sobre blanco), violeta intenso secundario.
 */
private object LightPalette {
    val slate950 = Color(0xFFF2EFF9)   // fondo de página (lavanda claro)
    val slate900 = Color(0xFFFFFFFF)   // tarjetas
    val slate800 = Color(0xFFECE8F5)   // chips / superficies
    val slate700 = Color(0xFFDDD6EA)   // separadores
    val slate600 = Color(0xFF6B6580)
    val slate400 = Color(0xFF4A4460)   // texto secundario (contraste alto)
    val slate200 = Color(0xFF2E2940)
    val slate100 = Color(0xFF1B1626)
    val cyanNeon = Color(0xFF3B6FE0)   // azul royal profundo para claro
    val cyanAccent = Color(0xFF2F5BC7)
    val cyanSurface = Color(0xFFE3EBFC)
    val violet = Color(0xFF7C3AED)
    val violetSurface = Color(0xFFF0E9FD)
    val emerald = Color(0xFF0E9F6E)
    val amber = Color(0xFFB45309)
    val rose = Color(0xFFE11D48)
    val textPrimary = Color(0xFF1B1626)
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

/** Texto principal sobre superficies: blanco-lila en dark, casi-negro ciruela en light. */
val TextPrimary: Color get() = if (NeoxThemeState.isDark) DarkPalette.textPrimary else LightPalette.textPrimary

/** Color "encima del azul royal": blanco en ambos temas (el azul es medio-profundo). */
val OnCyan: Color get() = Color(0xFFFFFFFF)

/** Extremo inferior del degradado del lienzo de la app (profundidad de página). */
val AppBgDeep: Color get() = if (NeoxThemeState.isDark) Color(0xFF120E1A) else Color(0xFFE4E0F0)

/** Burbuja del usuario en el chat: azul royal fijo en ambos temas, con texto blanco fijo. */
val BubbleUserBg: Color get() = Color(0xFF4D8AF0)
val OnBubbleUser: Color get() = Color(0xFFFFFFFF)
