package com.estrelladebelen.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.estrelladebelen.app.ui.navigation.AppNavGraph
import com.estrelladebelen.app.ui.theme.EstrellaDeBelénTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EstrellaDeBelénTheme {
                AppNavGraph()
            }
        }
    }
}
