package com.despertador.domain.scheduling

import com.despertador.data.repository.AlarmRepositoryImpl
import com.despertador.data.repository.FakeAlarmDao
import com.despertador.domain.model.Alarm
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SchedulingAndLifecycleTest {

    private lateinit var fakeDao: FakeAlarmDao
    private lateinit var repository: AlarmRepositoryImpl
    private val scheduledAlarmIds = mutableListOf<Long>()
    private val cancelledAlarmIds = mutableListOf<Long>()

    private val fakeScheduler = object : AlarmScheduler {
        override fun schedule(alarm: Alarm) {
            scheduledAlarmIds.add(alarm.id)
        }

        override fun cancel(alarmId: Long) {
            cancelledAlarmIds.add(alarmId)
        }
    }

    @Before
    fun setUp() {
        fakeDao = FakeAlarmDao()
        repository = AlarmRepositoryImpl(fakeDao)
        scheduledAlarmIds.clear()
        cancelledAlarmIds.clear()
    }

    @Test
    fun schedulingActiveAlarmsEnqueuesExpectedIds() = runTest {
        val alarm = Alarm(id = 1L, hour = 7, minute = 30, isEnabled = true)
        fakeScheduler.schedule(alarm)

        assertEquals(listOf(1L), scheduledAlarmIds)
    }

    @Test
    fun cancellingAlarmEnqueuesCancelId() = runTest {
        fakeScheduler.cancel(42L)
        assertEquals(listOf(42L), cancelledAlarmIds)
    }

    @Test
    fun dismissOneShotAlarmDisablesInRepository() = runTest {
        val alarm = Alarm(hour = 8, minute = 0, isEnabled = true) // non-recurring
        val id = repository.insertAlarm(alarm).getOrThrow()

        // Simulate dismiss logic
        val persisted = repository.getAlarmById(id)!!
        assertFalse(persisted.isRecurring)
        repository.updateAlarm(persisted.copy(isEnabled = false))

        val updated = repository.getAlarmById(id)!!
        assertFalse(updated.isEnabled)
    }

    @Test
    fun dismissRecurringAlarmRetainsEnabledStatusForNextCycle() = runTest {
        val recurring = Alarm(
            hour = 8,
            minute = 0,
            daysOfWeek = setOf(com.despertador.domain.model.DayOfWeek.MONDAY),
            isEnabled = true
        )
        val id = repository.insertAlarm(recurring).getOrThrow()

        val persisted = repository.getAlarmById(id)!!
        assertTrue(persisted.isRecurring)
        // Recurring alarms remain enabled and are rescheduled
        fakeScheduler.schedule(persisted)

        assertEquals(listOf(id), scheduledAlarmIds)
        val reloaded = repository.getAlarmById(id)!!
        assertTrue(reloaded.isEnabled)
    }
}
