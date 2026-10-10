package com.despertador.domain.repository

import com.despertador.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    val maxCapacity: Int get() = 5
    val allAlarmsFlow: Flow<List<Alarm>>
    suspend fun getAllAlarms(): List<Alarm>
    suspend fun getAlarmById(id: Long): Alarm?
    suspend fun getAlarmCount(): Int
    suspend fun insertAlarm(alarm: Alarm): Result<Long>
    suspend fun updateAlarm(alarm: Alarm)
    suspend fun toggleAlarm(id: Long, isEnabled: Boolean)
    suspend fun deleteAlarm(alarm: Alarm)
    suspend fun deleteAlarmById(id: Long)
    suspend fun sweepOrphanedAudio()
}
