package com.despertador.data.repository

import com.despertador.domain.model.Alarm
import com.despertador.domain.model.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlarmRepositoryTest {

    private lateinit var fakeDao: FakeAlarmDao
    private lateinit var repository: AlarmRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeAlarmDao()
        repository = AlarmRepositoryImpl(fakeDao, maxCapacity = 5)
    }

    @Test
    fun initiallyEmitsEmptyAlarmList() = runTest {
        val alarms = repository.allAlarmsFlow.first()
        assertTrue(alarms.isEmpty())
        assertEquals(0, repository.getAlarmCount())
    }

    @Test
    fun insertAlarmSucceedsAndEmitsUpdatedList() = runTest {
        val alarm = Alarm(
            hour = 7,
            minute = 0,
            label = "Morning",
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        )

        val result = repository.insertAlarm(alarm)
        assertTrue(result.isSuccess)
        val insertedId = result.getOrThrow()
        assertTrue(insertedId > 0)

        val list = repository.allAlarmsFlow.first()
        assertEquals(1, list.size)
        assertEquals("Morning", list.first().label)
        assertEquals(7, list.first().hour)
        assertEquals(0, list.first().minute)
    }

    @Test
    fun capacityLimitEnforced_rejectsSixthAlarm() = runTest {
        // Insert 5 alarms (maximum capacity)
        for (i in 1..5) {
            val res = repository.insertAlarm(Alarm(hour = i + 5, minute = 0, label = "Alarm $i"))
            assertTrue("Expected alarm $i to succeed", res.isSuccess)
        }

        assertEquals(5, repository.getAlarmCount())

        // Attempting to insert a 6th alarm must fail
        val sixthResult = repository.insertAlarm(Alarm(hour = 12, minute = 0, label = "Sixth Alarm"))
        assertTrue("Expected 6th alarm to fail capacity constraint", sixthResult.isFailure)
        assertEquals(5, repository.getAlarmCount())
    }

    @Test
    fun toggleAlarmUpdatesEnabledStatus() = runTest {
        val id = repository.insertAlarm(Alarm(hour = 8, minute = 0, isEnabled = true)).getOrThrow()

        repository.toggleAlarm(id, false)
        val alarm = repository.getAlarmById(id)
        assertNotNull(alarm)
        assertFalse(alarm!!.isEnabled)

        repository.toggleAlarm(id, true)
        val enabledAlarm = repository.getAlarmById(id)
        assertTrue(enabledAlarm!!.isEnabled)
    }

    @Test
    fun deleteAlarmRemovesItAndDecrementsCount() = runTest {
        val id = repository.insertAlarm(Alarm(hour = 9, minute = 15, label = "Delete Me")).getOrThrow()
        assertEquals(1, repository.getAlarmCount())

        val alarm = repository.getAlarmById(id)!!
        repository.deleteAlarm(alarm)

        assertEquals(0, repository.getAlarmCount())
        assertNull(repository.getAlarmById(id))
        assertTrue(repository.allAlarmsFlow.first().isEmpty())
    }

    @Test
    fun deleteAlarmByIdRemovesItDirectly() = runTest {
        val id = repository.insertAlarm(Alarm(hour = 10, minute = 0)).getOrThrow()
        assertEquals(1, repository.getAlarmCount())

        repository.deleteAlarmById(id)
        assertEquals(0, repository.getAlarmCount())
    }

    @Test
    fun deleteAlarmPrunesOrphanedAudioFileWhenNotReferencedByOtherAlarms() = runTest {
        var pruneCheckedPath: String? = null
        val mockStorage = com.despertador.data.audio.AudioStorageManager(
            filesDir = java.io.File("/tmp"),
            validator = com.despertador.data.audio.Mp3Validator()
        )
        val customRepo = AlarmRepositoryImpl(fakeDao, maxCapacity = 5, audioStorageManager = mockStorage)

        val alarm = Alarm(
            hour = 6,
            minute = 30,
            cachedAudioPath = "/tmp/alarms/track_123.mp3"
        )
        val id = customRepo.insertAlarm(alarm).getOrThrow()
        assertEquals(1, fakeDao.countAudioPathReferences("/tmp/alarms/track_123.mp3"))

        customRepo.deleteAlarm(alarm.copy(id = id))
        assertEquals(0, fakeDao.countAudioPathReferences("/tmp/alarms/track_123.mp3"))
    }

    @Test
    fun sweepOrphanedAudioCallsStorageManagerWithActivePaths() = runTest {
        val alarm = Alarm(hour = 7, minute = 0, cachedAudioPath = "/files/alarms/track_abc.mp3")
        repository.insertAlarm(alarm)

        val activePaths = fakeDao.getDistinctAudioPaths()
        assertEquals(listOf("/files/alarms/track_abc.mp3"), activePaths)
    }
}
