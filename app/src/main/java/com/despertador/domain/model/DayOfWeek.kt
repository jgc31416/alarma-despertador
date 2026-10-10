package com.despertador.domain.model

enum class DayOfWeek(val displayName: String, val shortName: String, val bitMask: Int) {
    MONDAY("Monday", "M", 1),
    TUESDAY("Tuesday", "T", 2),
    WEDNESDAY("Wednesday", "W", 4),
    THURSDAY("Thursday", "T", 8),
    FRIDAY("Friday", "F", 16),
    SATURDAY("Saturday", "S", 32),
    SUNDAY("Sunday", "S", 64);

    companion object {
        fun fromBitmask(mask: Int): Set<DayOfWeek> {
            return entries.filter { (it.bitMask and mask) != 0 }.toSet()
        }

        fun toBitmask(days: Set<DayOfWeek>): Int {
            return days.fold(0) { acc, day -> acc or day.bitMask }
        }
    }
}
