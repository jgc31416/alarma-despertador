package com.despertador.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.despertador.R
import com.despertador.domain.scheduling.AndroidAlarmScheduler
import com.despertador.ui.ringing.RingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AlarmPlaybackService : Service() {

    private var exoPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var volumeRampJob: Job? = null
    private var autoSilenceJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopSelf()
            return START_NOT_STICKY
        }

        val alarmId = intent?.getLongExtra(AndroidAlarmScheduler.EXTRA_ALARM_ID, -1L) ?: -1L
        val alarmLabel = intent?.getStringExtra(AndroidAlarmScheduler.EXTRA_ALARM_LABEL) ?: ""
        val audioPath = intent?.getStringExtra(AndroidAlarmScheduler.EXTRA_AUDIO_PATH) ?: ""

        val notification = buildForegroundNotification(alarmId, alarmLabel)
        startForeground(NOTIFICATION_ID, notification)

        startPlayback(audioPath)
        launchRingingActivity(alarmId, alarmLabel)
        startAutoSilenceTimer()

        return START_STICKY
    }

    private fun startPlayback(audioPath: String) {
        exoPlayer?.release()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_ALARM)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ONE

        val file = File(audioPath)
        val mediaItem = if (file.exists() && file.isFile) {
            MediaItem.fromUri(file.toURI().toString())
        } else {
            // Default system alarm / raw fallback URI if no file chosen
            MediaItem.fromUri(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
        }

        player.setMediaItem(mediaItem)
        player.volume = 0.2f // Gentle wake initial volume (20%)
        player.prepare()
        player.play()
        exoPlayer = player

        startGentleWakeRamp(player)
    }

    private fun startGentleWakeRamp(player: ExoPlayer) {
        volumeRampJob?.cancel()
        volumeRampJob = serviceScope.launch {
            // 30 seconds linear ramp from 0.2f to 1.0f in 10 steps (3 seconds each)
            var currentVolume = 0.2f
            val targetVolume = 1.0f
            val totalSteps = 10
            val stepDelayMs = 3000L
            val stepIncrement = (targetVolume - currentVolume) / totalSteps

            for (i in 1..totalSteps) {
                delay(stepDelayMs)
                if (!isActive) break
                currentVolume = (currentVolume + stepIncrement).coerceAtMost(targetVolume)
                player.volume = currentVolume
            }
        }
    }

    private fun startAutoSilenceTimer() {
        autoSilenceJob?.cancel()
        autoSilenceJob = serviceScope.launch {
            // 10 minutes safety timeout (600,000 ms)
            delay(10 * 60 * 1000L)
            if (isActive) {
                stopSelf()
            }
        }
    }

    private fun launchRingingActivity(alarmId: Long, alarmLabel: String) {
        val ringingIntent = Intent(this, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(AndroidAlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AndroidAlarmScheduler.EXTRA_ALARM_LABEL, alarmLabel)
        }
        startActivity(ringingIntent)
    }

    private fun buildForegroundNotification(alarmId: Long, alarmLabel: String): Notification {
        val fullScreenIntent = Intent(this, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(AndroidAlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AndroidAlarmScheduler.EXTRA_ALARM_LABEL, alarmLabel)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = alarmLabel.ifBlank { getString(R.string.app_name) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Ringing Service",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Foreground playback channel for active alarms"
                setSound(null, null)
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        volumeRampJob?.cancel()
        autoSilenceJob?.cancel()
        serviceScope.cancel()
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "alarm_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_ALARM = "com.despertador.ACTION_STOP_ALARM"
    }
}
