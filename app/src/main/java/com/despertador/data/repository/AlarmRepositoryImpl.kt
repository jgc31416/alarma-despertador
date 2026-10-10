package com.despertador.data.repository

import com.despertador.data.local.dao.AlarmDao
import com.despertador.data.local.entity.AlarmEntity
import com.despertador.domain.model.Alarm
import com.despertador.domain.repository.AlarmRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.despertador.data.audio.AudioStorageManager

class AlarmRepositoryImpl(
    private val alarmDao: AlarmDao,
    override val maxCapacity: Int = 5,
    private val audioStorageManager: AudioStorageManager? = null
) : AlarmRepository {

    override val allAlarmsFlow: Flow<List<Alarm>> =
        alarmDao.getAllAlarmsFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllAlarms(): List<Alarm> =
        alarmDao.getAllAlarms().map { it.toDomain() }

    override suspend fun getAlarmById(id: Long): Alarm? =
        alarmDao.getAlarmById(id)?.toDomain()

    override suspend fun getAlarmCount(): Int =
        alarmDao.getAlarmCount()

    override suspend fun insertAlarm(alarm: Alarm): Result<Long> {
        val count = alarmDao.getAlarmCount()
        if (count >= maxCapacity) {
            return Result.failure(IllegalStateException("Maximum capacity of $maxCapacity alarms reached"))
        }
        val rowId = alarmDao.insertAlarm(AlarmEntity.fromDomain(alarm))
        return Result.success(rowId)
    }

    override suspend fun updateAlarm(alarm: Alarm) {
        alarmDao.updateAlarm(AlarmEntity.fromDomain(alarm))
    }

    override suspend fun toggleAlarm(id: Long, isEnabled: Boolean) {
        val entity = alarmDao.getAlarmById(id) ?: return
        alarmDao.updateAlarm(entity.copy(isEnabled = isEnabled))
    }

    override suspend fun deleteAlarm(alarm: Alarm) {
        alarmDao.deleteAlarm(AlarmEntity.fromDomain(alarm))
        pruneAudioIfUnreferenced(alarm.cachedAudioPath)
    }

    override suspend fun deleteAlarmById(id: Long) {
        val entity = alarmDao.getAlarmById(id)
        alarmDao.deleteAlarmById(id)
        if (entity != null) {
            pruneAudioIfUnreferenced(entity.cachedAudioPath)
        }
    }

    override suspend fun sweepOrphanedAudio() {
        val activePaths = alarmDao.getDistinctAudioPaths().toSet()
        audioStorageManager?.sweepOrphanedFiles(activePaths)
    }

    private suspend fun pruneAudioIfUnreferenced(cachedAudioPath: String) {
        if (cachedAudioPath.isNotBlank()) {
            audioStorageManager?.pruneIfUnreferenced(cachedAudioPath) { path ->
                alarmDao.countAudioPathReferences(path) > 0
            }
        }
    }
}
