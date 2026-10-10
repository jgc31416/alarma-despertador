package com.despertador.ui.screens

import com.despertador.data.repository.FakeAlarmDao
import com.despertador.data.repository.AlarmRepositoryImpl
import com.despertador.domain.model.Alarm
import com.despertador.domain.model.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeAlarmDao
    private lateinit var repository: AlarmRepositoryImpl
    private lateinit var viewModel: AlarmListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeAlarmDao()
        repository = AlarmRepositoryImpl(fakeDao, maxCapacity = 5)
        viewModel = AlarmListViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateReflectsEmptyRepository() = runTest {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.isEmpty)
        assertFalse(state.isCapacityReached)
        assertEquals(0, state.alarms.size)
        assertEquals(5, state.maxCapacity)
    }

    @Test
    fun repositoryEmissionsUpdateUiState() = runTest {
        advanceUntilIdle()

        repository.insertAlarm(
            Alarm(id = 1L, hour = 7, minute = 30, label = "Work", isEnabled = true)
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isEmpty)
        assertEquals(1, state.alarms.size)
        assertEquals("Work", state.alarms.first().label)
        assertFalse(state.isCapacityReached)
    }

    @Test
    fun fiveAlarmsSetsCapacityReachedTrue() = runTest {
        advanceUntilIdle()

        for (i in 1..5) {
            repository.insertAlarm(Alarm(id = i.toLong(), hour = i + 6, minute = 0))
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(5, state.alarms.size)
        assertTrue(state.isCapacityReached)
    }

    @Test
    fun toggleAlarmUpdatesRepositoryState() = runTest {
        val id = repository.insertAlarm(Alarm(hour = 8, minute = 0, isEnabled = true)).getOrThrow()
        advanceUntilIdle()

        val alarm = viewModel.uiState.value.alarms.first()
        assertTrue(alarm.isEnabled)

        viewModel.toggleAlarm(alarm, false)
        advanceUntilIdle()

        val updatedAlarm = repository.getAlarmById(id)
        assertFalse(updatedAlarm!!.isEnabled)
    }

    @Test
    fun deleteAlarmRemovesFromRepository() = runTest {
        val id = repository.insertAlarm(Alarm(hour = 8, minute = 0)).getOrThrow()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.alarms.size)

        val alarm = viewModel.uiState.value.alarms.first()
        viewModel.deleteAlarm(alarm)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.alarms.size)
        assertTrue(viewModel.uiState.value.isEmpty)
    }
}
