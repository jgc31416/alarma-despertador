package com.despertador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.room.Room
import com.despertador.data.local.DespertadorDatabase
import com.despertador.data.repository.AlarmRepositoryImpl
import com.despertador.ui.screens.AlarmListScreen
import com.despertador.ui.screens.AlarmListViewModel
import com.despertador.ui.theme.DespertadorTheme

import androidx.lifecycle.lifecycleScope
import com.despertador.data.audio.AudioStorageManager
import com.despertador.data.audio.Mp3Validator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.despertador.domain.model.Alarm
import com.despertador.domain.scheduling.AndroidAlarmScheduler
import com.despertador.domain.settings.SharedPreferencesSettingsRepository
import com.despertador.ui.screens.AlarmEditorBottomSheet
import com.despertador.ui.settings.SettingsDialog

class MainActivity : ComponentActivity() {

    private val audioStorageManager by lazy {
        AudioStorageManager(
            filesDir = applicationContext.filesDir,
            validator = Mp3Validator()
        )
    }

    private val scheduler by lazy {
        AndroidAlarmScheduler(applicationContext)
    }

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            DespertadorDatabase::class.java,
            "despertador.db"
        ).build()
    }

    private val repository by lazy {
        AlarmRepositoryImpl(
            alarmDao = database.alarmDao(),
            audioStorageManager = audioStorageManager
        )
    }

    private val settingsRepository by lazy {
        SharedPreferencesSettingsRepository(applicationContext)
    }

    private val viewModel by viewModels<AlarmListViewModel> {
        AlarmListViewModel.provideFactory(repository, scheduler)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cold-start orphan reconciliation sweep
        lifecycleScope.launch(Dispatchers.IO) {
            repository.sweepOrphanedAudio()
        }
        setContent {
            DespertadorTheme {
                val uiState by viewModel.uiState.collectAsState()
                var showEditor by remember { mutableStateOf(false) }
                var alarmToEdit by remember { mutableStateOf<Alarm?>(null) }
                var showSettings by remember { mutableStateOf(false) }
                val currentSnoozeMinutes by settingsRepository.snoozeMinutesFlow.collectAsState()

                AlarmListScreen(
                    uiState = uiState,
                    onToggleAlarm = viewModel::toggleAlarm,
                    onDeleteAlarm = viewModel::deleteAlarm,
                    onAddAlarmClick = {
                        alarmToEdit = null
                        showEditor = true
                    },
                    onAlarmClick = { alarm ->
                        alarmToEdit = alarm
                        showEditor = true
                    },
                    onSettingsClick = {
                        showSettings = true
                    }
                )

                if (showEditor) {
                    AlarmEditorBottomSheet(
                        alarmToEdit = alarmToEdit,
                        onDismiss = {
                            showEditor = false
                            alarmToEdit = null
                        },
                        onSave = { savedAlarm ->
                            viewModel.saveAlarm(savedAlarm)
                            showEditor = false
                            alarmToEdit = null
                        },
                        onDelete = { deletedAlarm ->
                            viewModel.deleteAlarm(deletedAlarm)
                            showEditor = false
                            alarmToEdit = null
                        },
                        onSelectAudioUri = { uri, onResult ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                val stream = contentResolver.openInputStream(uri)
                                if (stream != null) {
                                    val result = audioStorageManager.importAudioStream(stream, uri.lastPathSegment)
                                    val track = result.getOrNull()
                                    if (track != null) {
                                        launch(Dispatchers.Main) {
                                            onResult(track.filePath, track.originalFileName, track.sha256Hash)
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                if (showSettings) {
                    SettingsDialog(
                        currentSnoozeMinutes = currentSnoozeMinutes,
                        onSnoozeMinutesSelected = { minutes ->
                            lifecycleScope.launch {
                                settingsRepository.setSnoozeMinutes(minutes)
                            }
                        },
                        onDismiss = { showSettings = false }
                    )
                }
            }
        }
    }
}
