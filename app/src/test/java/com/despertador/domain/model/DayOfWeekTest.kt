package com.despertador.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayOfWeekTest {

    @Test
    fun emptySetConvertsToZeroBitmask() {
        val mask = DayOfWeek.toBitmask(emptySet())
        assertEquals(0, mask)
        assertTrue(DayOfWeek.fromBitmask(0).isEmpty())
    }

    @Test
    fun singleDayRoundtripsThroughBitmask() {
        for (day in DayOfWeek.entries) {
            val mask = DayOfWeek.toBitmask(setOf(day))
            val restored = DayOfWeek.fromBitmask(mask)
            assertEquals(setOf(day), restored)
        }
    }

    @Test
    fun multipleDaysRoundtripAccurately() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val mask = DayOfWeek.toBitmask(days)
        val expectedMask = DayOfWeek.MONDAY.bitMask or DayOfWeek.WEDNESDAY.bitMask or DayOfWeek.FRIDAY.bitMask
        assertEquals(expectedMask, mask)

        val restored = DayOfWeek.fromBitmask(mask)
        assertEquals(days, restored)
    }

    @Test
    fun allDaysCombineTo127Bitmask() {
        val allDays = DayOfWeek.entries.toSet()
        val mask = DayOfWeek.toBitmask(allDays)
        assertEquals(127, mask)
        assertEquals(allDays, DayOfWeek.fromBitmask(127))
    }
}
