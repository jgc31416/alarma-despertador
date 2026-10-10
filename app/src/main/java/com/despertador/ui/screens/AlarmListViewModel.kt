package com.despertador.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.despertador.domain.model.Alarm
import com.despertador.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.despertador.domain.scheduling.AlarmScheduler

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler? = null
) : ViewModel() {

    val uiState: StateFlow<AlarmListUiState> = repository.allAlarmsFlow
        .map { alarms ->
            AlarmListUiState(
                alarms = alarms,
                maxCapacity = repository.maxCapacity,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AlarmListUiState(maxCapacity = repository.maxCapacity)
        )

    fun toggleAlarm(alarm: Alarm, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleAlarm(alarm.id, isEnabled)
            val updated = alarm.copy(isEnabled = isEnabled)
            if (isEnabled) {
                scheduler?.schedule(updated)
            } else {
                scheduler?.cancel(alarm.id)
            }
        }
    }

    fun saveAlarm(alarm: Alarm) {
        viewModelScope.launch {
            if (alarm.id == 0L) {
                val res = repository.insertAlarm(alarm)
                val id = res.getOrNull()
                if (id != null && alarm.isEnabled) {
                    scheduler?.schedule(alarm.copy(id = id))
                }
            } else {
                repository.updateAlarm(alarm)
                if (alarm.isEnabled) {
                    scheduler?.schedule(alarm)
                } else {
                    scheduler?.cancel(alarm.id)
                }
            }
        }
    }

    fun deleteAlarm(alarm: Alarm) {
        viewModelScope.launch {
            repository.deleteAlarm(alarm)
            scheduler?.cancel(alarm.id)
        }
    }

    companion object {
        fun provideFactory(
            repository: AlarmRepository,
            scheduler: AlarmScheduler? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AlarmListViewModel(repository, scheduler) as T
                }
            }
    }
}
