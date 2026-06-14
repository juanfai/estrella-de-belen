package com.estrelladebelen.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estrelladebelen.app.ui.navigation.AppNavGraph
import com.estrelladebelen.app.ui.theme.AppThemeViewModel
import com.estrelladebelen.app.ui.theme.EstrellaDeBelénTheme

class MainActivity : ComponentActivity() {

    private val themeVm: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by themeVm.isDark.collectAsStateWithLifecycle()
            EstrellaDeBelénTheme(darkTheme = isDark) {
                AppNavGraph()
            }
        }
    }
}
