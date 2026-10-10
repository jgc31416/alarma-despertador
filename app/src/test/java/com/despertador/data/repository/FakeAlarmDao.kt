package com.despertador.data.repository

import com.despertador.data.local.dao.AlarmDao
import com.despertador.data.local.entity.AlarmEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class FakeAlarmDao : AlarmDao {
    private val alarmsMap = mutableMapOf<Long, AlarmEntity>()
    private val flow = MutableStateFlow<List<AlarmEntity>>(emptyList())
    private var nextId = 1L

    private fun updateFlow() {
        flow.value = alarmsMap.values.sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    override fun getAllAlarmsFlow(): Flow<List<AlarmEntity>> = flow.asStateFlow()

    override suspend fun getAllAlarms(): List<AlarmEntity> =
        alarmsMap.values.sortedWith(compareBy({ it.hour }, { it.minute }))

    override suspend fun getAlarmById(id: Long): AlarmEntity? = alarmsMap[id]

    override suspend fun getAlarmCount(): Int = alarmsMap.size

    override suspend fun insertAlarm(alarm: AlarmEntity): Long {
        val id = if (alarm.id == 0L) nextId++ else alarm.id
        val entity = alarm.copy(id = id)
        alarmsMap[id] = entity
        updateFlow()
        return id
    }

    override suspend fun updateAlarm(alarm: AlarmEntity) {
        alarmsMap[alarm.id] = alarm
        updateFlow()
    }

    override suspend fun deleteAlarm(alarm: AlarmEntity) {
        alarmsMap.remove(alarm.id)
        updateFlow()
    }

    override suspend fun deleteAlarmById(id: Long) {
        alarmsMap.remove(id)
        updateFlow()
    }

    override suspend fun getDistinctAudioPaths(): List<String> =
        alarmsMap.values.map { it.cachedAudioPath }.filter { it.isNotBlank() }.distinct()

    override suspend fun countAudioPathReferences(path: String): Int =
        alarmsMap.values.count { it.cachedAudioPath == path }
}
