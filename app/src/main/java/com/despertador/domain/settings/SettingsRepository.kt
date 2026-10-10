package com.despertador.domain.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface SettingsRepository {
    val snoozeMinutesFlow: StateFlow<Int>
    suspend fun setSnoozeMinutes(minutes: Int)
}

class SharedPreferencesSettingsRepository(
    context: Context,
    prefsName: String = "despertador_settings"
) : SettingsRepository {

    private val prefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val _snoozeMinutesFlow = MutableStateFlow(
        prefs.getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
    )

    override val snoozeMinutesFlow: StateFlow<Int> = _snoozeMinutesFlow.asStateFlow()

    override suspend fun setSnoozeMinutes(minutes: Int) {
        val validMinutes = if (minutes in ALLOWED_SNOOZE_OPTIONS) minutes else DEFAULT_SNOOZE_MINUTES
        prefs.edit().putInt(KEY_SNOOZE_MINUTES, validMinutes).apply()
        _snoozeMinutesFlow.value = validMinutes
    }

    companion object {
        const val KEY_SNOOZE_MINUTES = "key_snooze_minutes"
        const val DEFAULT_SNOOZE_MINUTES = 10
        val ALLOWED_SNOOZE_OPTIONS = listOf(5, 10, 15, 20)
    }
}
