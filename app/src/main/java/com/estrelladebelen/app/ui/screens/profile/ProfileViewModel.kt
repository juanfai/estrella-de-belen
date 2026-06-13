package com.estrelladebelen.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estrelladebelen.app.data.model.UserProfile
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.data.repository.UserRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {

    private val userRepo: UserRepository = AppContainer.userRepository

    val userProfile = userRepo.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            userRepo.signOut()
            onDone()
        }
    }

    fun updateNotifications(enabled: Boolean, time: String) {
        viewModelScope.launch { userRepo.updateNotificationSettings(enabled, time) }
    }
}
