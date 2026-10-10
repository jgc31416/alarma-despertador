package com.despertador.domain.scheduling

import com.despertador.domain.model.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

object NextTriggerCalculator {

    /**
     * Calculates the next epoch millisecond when an alarm should fire.
     *
     * @param hour The hour of day (0-23)
     * @param minute The minute of hour (0-59)
     * @param daysOfWeek The recurrence days of the week (empty means one-shot)
     * @param now The reference local date/time (defaults to LocalDateTime.now())
     * @param zoneId The device ZoneId (defaults to ZoneId.systemDefault())
     */
    fun calculateNextTriggerMillis(
        hour: Int,
        minute: Int,
        daysOfWeek: Set<DayOfWeek>,
        now: LocalDateTime,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        if (daysOfWeek.isEmpty()) {
            // One-shot alarm: today if upcoming, otherwise tomorrow
            var targetDateTime = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (!targetDateTime.isAfter(now)) {
                targetDateTime = targetDateTime.plusDays(1)
            }
            return targetDateTime.atZone(zoneId).toInstant().toEpochMilli()
        }

        // Recurring alarm: find the nearest matching day
        // java.time.DayOfWeek: MONDAY (1) .. SUNDAY (7)
        for (dayOffset in 0..7) {
            val candidateDateTime = now.plusDays(dayOffset.toLong())
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)

            if (dayOffset == 0 && !candidateDateTime.isAfter(now)) {
                // Today's trigger time has already passed
                continue
            }

            val candidateJavaDay = candidateDateTime.dayOfWeek.value // 1 (Mon) .. 7 (Sun)
            val matches = daysOfWeek.any { domainDay ->
                when (domainDay) {
                    DayOfWeek.MONDAY -> candidateJavaDay == 1
                    DayOfWeek.TUESDAY -> candidateJavaDay == 2
                    DayOfWeek.WEDNESDAY -> candidateJavaDay == 3
                    DayOfWeek.THURSDAY -> candidateJavaDay == 4
                    DayOfWeek.FRIDAY -> candidateJavaDay == 5
                    DayOfWeek.SATURDAY -> candidateJavaDay == 6
                    DayOfWeek.SUNDAY -> candidateJavaDay == 7
                }
            }

            if (matches) {
                return candidateDateTime.atZone(zoneId).toInstant().toEpochMilli()
            }
        }

        // Fallback: tomorrow
        return now.plusDays(1)
            .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            .atZone(zoneId).toInstant().toEpochMilli()
    }
}
