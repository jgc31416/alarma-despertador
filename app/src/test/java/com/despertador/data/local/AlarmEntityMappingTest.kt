package com.despertador.data.local

import com.despertador.data.local.entity.AlarmEntity
import com.despertador.domain.model.Alarm
import com.despertador.domain.model.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmEntityMappingTest {

    @Test
    fun domainToEntityAndBackPreservesAllProperties() {
        val originalAlarm = Alarm(
            id = 42L,
            hour = 7,
            minute = 30,
            label = "Morning Workout",
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            isEnabled = true,
            cachedAudioPath = "/data/user/0/com.despertador/files/alarms/track_abc.mp3",
            audioFileName = "WakeUpTone.mp3",
            sha256Hash = "abc123hash"
        )

        val entity = AlarmEntity.fromDomain(originalAlarm)
        assertEquals(42L, entity.id)
        assertEquals(7, entity.hour)
        assertEquals(30, entity.minute)
        assertEquals("Morning Workout", entity.label)
        assertEquals(true, entity.isEnabled)
        assertEquals("/data/user/0/com.despertador/files/alarms/track_abc.mp3", entity.cachedAudioPath)
        assertEquals("WakeUpTone.mp3", entity.audioFileName)
        assertEquals("abc123hash", entity.sha256Hash)

        val restoredAlarm = entity.toDomain()
        assertEquals(originalAlarm, restoredAlarm)
        assertTrue(restoredAlarm.isRecurring)
        assertEquals("07:30", restoredAlarm.formattedTime)
    }

    @Test
    fun nonRecurringAlarmCalculatesIsRecurringFalse() {
        val alarm = Alarm(
            id = 1L,
            hour = 8,
            minute = 0,
            label = "One-time Reminder",
            daysOfWeek = emptySet(),
            isEnabled = true
        )

        val entity = AlarmEntity.fromDomain(alarm)
        assertEquals(0, entity.recurrenceBitmask)

        val restored = entity.toDomain()
        assertFalse(restored.isRecurring)
        assertEquals("08:00", restored.formattedTime)
    }
}
