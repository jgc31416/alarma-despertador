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

class MainActivity : ComponentActivity() {

    private val audioStorageManager by lazy {
        AudioStorageManager(
            filesDir = applicationContext.filesDir,
            validator = Mp3Validator()
        )
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

    private val viewModel by viewModels<AlarmListViewModel> {
        AlarmListViewModel.provideFactory(repository)
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
                AlarmListScreen(
                    uiState = uiState,
                    onToggleAlarm = viewModel::toggleAlarm,
                    onDeleteAlarm = viewModel::deleteAlarm,
                    onAddAlarmClick = { /* Handled in subsequent ticket */ }
                )
            }
        }
    }
}
