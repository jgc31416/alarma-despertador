package com.despertador.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.despertador.domain.model.Alarm
import com.despertador.domain.model.DayOfWeek

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String,
    val recurrenceBitmask: Int,
    val isEnabled: Boolean,
    val cachedAudioPath: String,
    val audioFileName: String,
    val sha256Hash: String
) {
    fun toDomain(): Alarm {
        return Alarm(
            id = id,
            hour = hour,
            minute = minute,
            label = label,
            daysOfWeek = DayOfWeek.fromBitmask(recurrenceBitmask),
            isEnabled = isEnabled,
            cachedAudioPath = cachedAudioPath,
            audioFileName = audioFileName,
            sha256Hash = sha256Hash
        )
    }

    companion object {
        fun fromDomain(alarm: Alarm): AlarmEntity {
            return AlarmEntity(
                id = alarm.id,
                hour = alarm.hour,
                minute = alarm.minute,
                label = alarm.label,
                recurrenceBitmask = DayOfWeek.toBitmask(alarm.daysOfWeek),
                isEnabled = alarm.isEnabled,
                cachedAudioPath = alarm.cachedAudioPath,
                audioFileName = alarm.audioFileName,
                sha256Hash = alarm.sha256Hash
            )
        }
    }
}
