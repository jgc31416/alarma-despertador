package com.despertador.ui.screens

import com.despertador.domain.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmListUiStateTest {

    @Test
    fun initialStateDefaultsToEmptyAndNotFull() {
        val state = AlarmListUiState()
        assertTrue(state.isEmpty)
        assertFalse(state.isCapacityReached)
        assertEquals(5, state.maxCapacity)
        assertEquals(0, state.alarms.size)
    }

    @Test
    fun underCapacityDoesNotTriggerCapacityReached() {
        val alarms = listOf(
            Alarm(id = 1L, hour = 6, minute = 30),
            Alarm(id = 2L, hour = 7, minute = 0),
            Alarm(id = 3L, hour = 7, minute = 30),
            Alarm(id = 4L, hour = 8, minute = 0)
        )
        val state = AlarmListUiState(alarms = alarms)
        assertFalse(state.isEmpty)
        assertFalse(state.isCapacityReached)
    }

    @Test
    fun fiveAlarmsTriggersCapacityReached() {
        val alarms = (1..5).map { Alarm(id = it.toLong(), hour = it + 5, minute = 0) }
        val state = AlarmListUiState(alarms = alarms)
        assertFalse(state.isEmpty)
        assertTrue(state.isCapacityReached)
    }
}
