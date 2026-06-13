package com.estrelladebelen.app.ui.screens.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.estrelladebelen.app.data.local.entity.DownloadedMeditation
import com.estrelladebelen.app.data.model.UserProfile
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.data.repository.MeditationRepository
import com.estrelladebelen.app.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileViewModel : ViewModel() {

    private val userRepo: UserRepository = AppContainer.userRepository
    private val meditationRepo: MeditationRepository = AppContainer.meditationRepository

    val userProfile = userRepo.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val downloads = meditationRepo.getDownloads().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
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

    fun removeDownload(context: Context, item: DownloadedMeditation) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(item.localFilePath).delete()
                meditationRepo.removeDownload(item.meditationId)
            }
        }
    }
}
