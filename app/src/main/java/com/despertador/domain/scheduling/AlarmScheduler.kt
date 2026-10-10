package com.despertador.domain.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.despertador.MainActivity
import com.despertador.domain.model.Alarm
import java.time.LocalDateTime
import java.time.ZoneId

interface AlarmScheduler {
    fun schedule(alarm: Alarm)
    fun cancel(alarmId: Long)
}

class AndroidAlarmScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
) : AlarmScheduler {

    override fun schedule(alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancel(alarm.id)
            return
        }

        val triggerTime = NextTriggerCalculator.calculateNextTriggerMillis(
            hour = alarm.hour,
            minute = alarm.minute,
            daysOfWeek = alarm.daysOfWeek,
            now = LocalDateTime.now(),
            zoneId = ZoneId.systemDefault()
        )

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_AUDIO_PATH, alarm.cachedAudioPath)
        }

        val operation = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, operation)
    }

    override fun cancel(alarmId: Long) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val operation = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (operation != null) {
            alarmManager.cancel(operation)
            operation.cancel()
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_AUDIO_PATH = "extra_audio_path"
    }
}
