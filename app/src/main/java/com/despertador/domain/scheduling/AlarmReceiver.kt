package com.despertador.domain.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.despertador.service.AlarmPlaybackService

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AndroidAlarmScheduler.EXTRA_ALARM_ID, -1L)
        val alarmLabel = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_ALARM_LABEL) ?: ""
        val audioPath = intent.getStringExtra(AndroidAlarmScheduler.EXTRA_AUDIO_PATH) ?: ""

        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            putExtra(AndroidAlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AndroidAlarmScheduler.EXTRA_ALARM_LABEL, alarmLabel)
            putExtra(AndroidAlarmScheduler.EXTRA_AUDIO_PATH, audioPath)
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
