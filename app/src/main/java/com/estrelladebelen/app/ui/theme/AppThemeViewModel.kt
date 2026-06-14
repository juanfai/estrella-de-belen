package com.estrelladebelen.app.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.estrelladebelen.app.data.prefs.ThemePreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val _isDark = MutableStateFlow(ThemePreferenceStore.isDarkMode(application))
    val isDark: StateFlow<Boolean> = _isDark.asStateFlow()

    fun toggle() {
        val next = !_isDark.value
        _isDark.value = next
        ThemePreferenceStore.setDarkMode(getApplication(), next)
    }
}
