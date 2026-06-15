package com.estrelladebelen.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estrelladebelen.app.R
import com.estrelladebelen.app.ui.navigation.AppNavGraph
import com.estrelladebelen.app.ui.theme.EstrellaDeBelénTheme
import com.estrelladebelen.app.util.NetworkMonitor

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isOnline by NetworkMonitor.observe(this)
                .collectAsStateWithLifecycle(initialValue = true)

            LaunchedEffect(isOnline) {
                if (!isOnline) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.no_internet),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            EstrellaDeBelénTheme(darkTheme = true) {
                AppNavGraph()
            }
        }
    }
}
