package com.despertador.domain.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.despertador.data.local.DespertadorDatabase
import com.despertador.data.repository.AlarmRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val pendingResult = goAsync()
            val database = Room.databaseBuilder(
                context.applicationContext,
                DespertadorDatabase::class.java,
                "despertador.db"
            ).build()

            val repository = AlarmRepositoryImpl(database.alarmDao())
            val scheduler = AndroidAlarmScheduler(context.applicationContext)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val alarms = repository.getAllAlarms()
                    alarms.filter { it.isEnabled }.forEach { alarm ->
                        scheduler.schedule(alarm)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
