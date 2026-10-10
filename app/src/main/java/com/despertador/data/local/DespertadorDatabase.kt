package com.despertador.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.despertador.data.local.dao.AlarmDao
import com.despertador.data.local.entity.AlarmEntity

@Database(entities = [AlarmEntity::class], version = 1, exportSchema = false)
abstract class DespertadorDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}
