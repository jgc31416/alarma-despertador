package com.despertador

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.despertador.ui.screens.AlarmListScreen
import com.despertador.ui.theme.DespertadorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DespertadorTheme {
                AlarmListScreen()
            }
        }
    }
}
