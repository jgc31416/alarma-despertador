package com.despertador.domain.scheduling

import com.despertador.domain.model.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NextTriggerCalculatorTest {

    private val zoneId = ZoneId.of("UTC")

    @Test
    fun oneShotAlarmLaterTodaySchedulesForToday() {
        // Now is 08:00 on Monday, alarm is set for 09:30
        val now = LocalDateTime.of(2026, 10, 12, 8, 0, 0) // Monday
        val trigger = NextTriggerCalculator.calculateNextTriggerMillis(
            hour = 9,
            minute = 30,
            daysOfWeek = emptySet(),
            now = now,
            zoneId = zoneId
        )

        val expected = LocalDateTime.of(2026, 10, 12, 9, 30, 0)
            .atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun oneShotAlarmEarlierTodaySchedulesForTomorrow() {
        // Now is 10:00 on Monday, alarm is set for 07:00
        val now = LocalDateTime.of(2026, 10, 12, 10, 0, 0) // Monday
        val trigger = NextTriggerCalculator.calculateNextTriggerMillis(
            hour = 7,
            minute = 0,
            daysOfWeek = emptySet(),
            now = now,
            zoneId = zoneId
        )

        val expected = LocalDateTime.of(2026, 10, 13, 7, 0, 0) // Tuesday
            .atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun recurringAlarmMatchesUpcomingTimeToday() {
        // Now is 06:00 on Monday, alarm is recurring on Monday at 07:00
        val now = LocalDateTime.of(2026, 10, 12, 6, 0, 0) // Monday
        val trigger = NextTriggerCalculator.calculateNextTriggerMillis(
            hour = 7,
            minute = 0,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            now = now,
            zoneId = zoneId
        )

        val expected = LocalDateTime.of(2026, 10, 12, 7, 0, 0) // Monday
            .atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun recurringAlarmSkipsPassedTimeTodayAndSelectsNextMatchingDay() {
        // Now is 08:00 on Monday, alarm is Mon & Wed at 07:00
        val now = LocalDateTime.of(2026, 10, 12, 8, 0, 0) // Monday
        val trigger = NextTriggerCalculator.calculateNextTriggerMillis(
            hour = 7,
            minute = 0,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            now = now,
            zoneId = zoneId
        )

        val expected = LocalDateTime.of(2026, 10, 14, 7, 0, 0) // Wednesday
            .atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun recurringAlarmSelectsNextWeekWhenAllDaysPassedThisWeek() {
        // Now is Saturday 12:00, recurring only on Monday & Friday at 07:00
        val now = LocalDateTime.of(2026, 10, 17, 12, 0, 0) // Saturday
        val trigger = NextTriggerCalculator.calculateNextTriggerMillis(
            hour = 7,
            minute = 0,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
            now = now,
            zoneId = zoneId
        )

        // Next target is upcoming Monday: Oct 19
        val expected = LocalDateTime.of(2026, 10, 19, 7, 0, 0)
            .atZone(zoneId).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }
}
