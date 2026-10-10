package com.despertador.ui.screens

import com.despertador.domain.model.Alarm

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    val maxCapacity: Int = 5,
    val isLoading: Boolean = false
) {
    val isCapacityReached: Boolean
        get() = alarms.size >= maxCapacity

    val isEmpty: Boolean
        get() = alarms.isEmpty()
}
