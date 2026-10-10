package com.despertador.ui.ringing

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.despertador.R
import com.despertador.data.local.DespertadorDatabase
import com.despertador.data.repository.AlarmRepositoryImpl
import com.despertador.domain.scheduling.AndroidAlarmScheduler
import com.despertador.domain.settings.SharedPreferencesSettingsRepository
import com.despertador.service.AlarmPlaybackService
import com.despertador.ui.theme.DespertadorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RingingActivity : ComponentActivity() {

    private val alarmId by lazy {
        intent.getLongExtra(AndroidAlarmScheduler.EXTRA_ALARM_ID, -1L)
    }

    private val alarmLabel by lazy {
        intent.getStringExtra(AndroidAlarmScheduler.EXTRA_ALARM_LABEL) ?: ""
    }

    private val database by lazy {
        Room.databaseBuilder(applicationContext, DespertadorDatabase::class.java, "despertador.db").build()
    }

    private val repository by lazy {
        AlarmRepositoryImpl(database.alarmDao())
    }

    private val settingsRepository by lazy {
        SharedPreferencesSettingsRepository(applicationContext)
    }

    private val scheduler by lazy {
        AndroidAlarmScheduler(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindowOverLockScreen()
        enableEdgeToEdge()

        setContent {
            DespertadorTheme {
                RingingScreen(
                    alarmLabel = alarmLabel,
                    onSnooze = { handleSnooze() },
                    onDismiss = { handleDismiss() }
                )
            }
        }
    }

    private fun configureWindowOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun handleSnooze() {
        stopAlarmService()
        lifecycleScope.launch(Dispatchers.IO) {
            val snoozeMinutes = settingsRepository.snoozeMinutesFlow.value
            val alarm = repository.getAlarmById(alarmId)
            if (alarm != null) {
                // Schedule snooze directly via AlarmManager for now + snoozeMinutes
                val snoozeTime = LocalTime.now().plusMinutes(snoozeMinutes.toLong())
                val snoozeAlarm = alarm.copy(hour = snoozeTime.hour, minute = snoozeTime.minute)
                scheduler.schedule(snoozeAlarm)
            }
            launch(Dispatchers.Main) {
                finish()
            }
        }
    }

    private fun handleDismiss() {
        stopAlarmService()
        lifecycleScope.launch(Dispatchers.IO) {
            val alarm = repository.getAlarmById(alarmId)
            if (alarm != null) {
                if (alarm.isRecurring) {
                    // Reschedule for next upcoming matching day
                    scheduler.schedule(alarm)
                } else {
                    // One-shot alarm: automatically disable in Room
                    repository.updateAlarm(alarm.copy(isEnabled = false))
                }
            }
            launch(Dispatchers.Main) {
                finish()
            }
        }
    }

    private fun stopAlarmService() {
        val stopIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
    }
}

@Composable
fun RingingScreen(
    alarmLabel: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeStr by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            kotlinx.coroutines.delay(1000L)
        }
    }

    // 30 seconds Gentle Wake progress animation from 0.2 to 1.0
    val gentleWakeProgress = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        gentleWakeProgress.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 30_000, easing = LinearEasing)
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Alarm icon & Time display
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = currentTimeStr,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 72.sp
                )
                if (alarmLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = alarmLabel,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Gentle Wake Escalation Meter
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.gentle_wake_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { gentleWakeProgress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // Snooze & Dismiss buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.snooze_button),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = stringResource(R.string.dismiss_button),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
