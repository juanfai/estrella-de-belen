package com.estrelladebelen.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.estrelladebelen.app.R
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.ui.navigation.AppNavGraph
import com.estrelladebelen.app.ui.theme.AppThemeViewModel
import com.estrelladebelen.app.ui.theme.EstrellaDeBelénTheme
import com.estrelladebelen.app.util.NetworkMonitor
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {

    private val themeVm: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle email link if the app was launched cold from a sign-in email.
        handleEmailLinkIntent(intent)

        setContent {
            val isDark by themeVm.isDark.collectAsStateWithLifecycle()
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

            EstrellaDeBelénTheme(darkTheme = isDark) {
                AppNavGraph()
            }
        }
    }

    // Called when the app is already running and the link is tapped.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleEmailLinkIntent(intent)
    }

    private fun handleEmailLinkIntent(intent: Intent) {
        val link = intent.data?.toString() ?: return
        if (Firebase.auth.isSignInWithEmailLink(link)) {
            AppContainer.userRepository.updatePendingEmailLink(link)
        }
    }
}
