package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("omniwork_app_prefs", Context.MODE_PRIVATE)

    private val _retentionDaysFlow = MutableStateFlow(getRetentionDays())
    val retentionDaysFlow: StateFlow<Int> = _retentionDaysFlow.asStateFlow()

    private val _isPinEnabledFlow = MutableStateFlow(getPin().isNotBlank())
    val isPinEnabledFlow: StateFlow<Boolean> = _isPinEnabledFlow.asStateFlow()

    fun getRetentionDays(): Int = prefs.getInt("vault_retention_days", 10)

    fun setRetentionDays(days: Int) {
        prefs.edit().putInt("vault_retention_days", days).apply()
        _retentionDaysFlow.value = days
    }

    fun getPin(): String = prefs.getString("security_pin_code", "") ?: ""

    fun setPin(pin: String) {
        prefs.edit().putString("security_pin_code", pin.trim()).apply()
        _isPinEnabledFlow.value = pin.trim().isNotBlank()
    }

    fun getAdvanceNoticeMinutes(): Int = prefs.getInt("advance_notice_min", 30)

    fun setAdvanceNoticeMinutes(minutes: Int) {
        prefs.edit().putInt("advance_notice_min", minutes).apply()
    }

    // --- Ajustes de voz (TTS) ---
    fun getTtsRate(): Float = prefs.getFloat("tts_rate", 1.0f)

    fun setTtsRate(rate: Float) {
        prefs.edit().putFloat("tts_rate", rate.coerceIn(0.5f, 2.0f)).apply()
    }

    fun getTtsPitch(): Float = prefs.getFloat("tts_pitch", 1.0f)

    fun setTtsPitch(pitch: Float) {
        prefs.edit().putFloat("tts_pitch", pitch.coerceIn(0.5f, 2.0f)).apply()
    }

    fun getTtsVoiceName(): String = prefs.getString("tts_voice_name", "") ?: ""

    fun setTtsVoiceName(name: String) {
        prefs.edit().putString("tts_voice_name", name).apply()
    }

    // --- Banner informativo del asistente (solo se muestra la primera vez) ---
    fun isAssistantBannerSeen(): Boolean = prefs.getBoolean("assistant_banner_seen", false)

    fun setAssistantBannerSeen(seen: Boolean) {
        prefs.edit().putBoolean("assistant_banner_seen", seen).apply()
    }

    // --- Tema claro/oscuro (oscuro por defecto) ---
    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)

    fun setDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean("dark_theme", dark).apply()
    }
}
