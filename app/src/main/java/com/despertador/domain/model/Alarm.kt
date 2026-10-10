package com.despertador.domain.model

data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val isEnabled: Boolean = true,
    val cachedAudioPath: String = "",
    val audioFileName: String = "",
    val sha256Hash: String = ""
) {
    val isRecurring: Boolean
        get() = daysOfWeek.isNotEmpty()

    val formattedTime: String
        get() = "%02d:%02d".format(hour, minute)
}
