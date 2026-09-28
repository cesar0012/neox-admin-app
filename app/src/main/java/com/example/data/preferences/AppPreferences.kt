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
}
