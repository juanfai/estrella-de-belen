package com.estrelladebelen.app.ui.screens.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.estrelladebelen.app.data.local.entity.DownloadedMeditation
import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.data.model.UserProfile
import com.estrelladebelen.app.data.repository.AppContainer
import com.estrelladebelen.app.data.repository.MeditationRepository
import com.estrelladebelen.app.data.repository.UserRepository
import com.estrelladebelen.app.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepo: UserRepository = AppContainer.userRepository
    private val meditationRepo: MeditationRepository = AppContainer.meditationRepository
    private val subscriptionRepo = AppContainer.subscriptionRepository

    val userProfile = userRepo.currentUser.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null
    )

    val isSubscribed = subscriptionRepo.isSubscribed.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    val downloads = meditationRepo.getDownloads().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val allMeditations = flow {
        emit(runCatching { meditationRepo.getAll() }.getOrDefault(emptyList()))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favoriteMeditations = combine(userProfile, allMeditations) { profile, all ->
        val ids = profile?.favorites ?: emptyList()
        all.filter { it.id in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleFavorite(meditationId: String) {
        viewModelScope.launch { userRepo.toggleFavorite(meditationId) }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            userRepo.signOut()
            onDone()
        }
    }

    fun deleteAccount(onDone: () -> Unit, onError: (needsReauth: Boolean) -> Unit) {
        viewModelScope.launch {
            userRepo.deleteAccount().fold(
                onSuccess = { onDone() },
                onFailure = { e ->
                    val needsReauth = e is com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
                    onError(needsReauth)
                }
            )
        }
    }

    fun updateNotifications(enabled: Boolean, time: String, test: Boolean = false) {
        viewModelScope.launch {
            userRepo.updateNotificationSettings(enabled, time)
            val ctx = getApplication<Application>()
            when {
                !enabled    -> ReminderScheduler.cancel(ctx)
                test        -> ReminderScheduler.scheduleTest(ctx, time)
                else        -> ReminderScheduler.schedule(ctx, time)
            }
        }
    }

    fun removeDownload(context: android.content.Context, item: DownloadedMeditation) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                File(item.localFilePath).delete()
                meditationRepo.removeDownload(item.meditationId)
            }
        }
    }
}
