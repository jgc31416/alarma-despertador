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

class MainActivity : ComponentActivity() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            DespertadorDatabase::class.java,
            "despertador.db"
        ).build()
    }

    private val repository by lazy {
        AlarmRepositoryImpl(database.alarmDao())
    }

    private val viewModel by viewModels<AlarmListViewModel> {
        AlarmListViewModel.provideFactory(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
