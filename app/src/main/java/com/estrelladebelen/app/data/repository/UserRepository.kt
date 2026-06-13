package com.estrelladebelen.app.data.repository

import com.estrelladebelen.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface UserRepository {
    val currentUser: Flow<UserProfile?>
    suspend fun signIn(email: String, password: String): Result<UserProfile>
    suspend fun register(name: String, email: String, password: String): Result<UserProfile>
    suspend fun signOut()
    suspend fun toggleFavorite(meditationId: String)
    suspend fun recordSession(durationMinutes: Int)
    suspend fun updateNotificationSettings(enabled: Boolean, time: String)
}

// Stub — replaced by FirebaseUserRepository when Firebase is wired up.
class UserRepositoryStub : UserRepository {

    private val _user = MutableStateFlow<UserProfile?>(null)
    override val currentUser: Flow<UserProfile?> = _user

    override suspend fun signIn(email: String, password: String): Result<UserProfile> {
        val profile = UserProfile(
            uid = "stub-uid",
            displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
            email = email
        )
        _user.value = profile
        return Result.success(profile)
    }

    override suspend fun register(name: String, email: String, password: String): Result<UserProfile> {
        val profile = UserProfile(uid = "stub-uid", displayName = name, email = email)
        _user.value = profile
        return Result.success(profile)
    }

    override suspend fun signOut() { _user.value = null }

    override suspend fun toggleFavorite(meditationId: String) {
        val current = _user.value ?: return
        val favorites = current.favorites.toMutableList()
        if (meditationId in favorites) favorites.remove(meditationId) else favorites.add(meditationId)
        _user.value = current.copy(favorites = favorites)
    }

    override suspend fun recordSession(durationMinutes: Int) {
        val current = _user.value ?: return
        _user.value = current.copy(
            totalSessions = current.totalSessions + 1,
            totalMinutes = current.totalMinutes + durationMinutes
        )
    }

    override suspend fun updateNotificationSettings(enabled: Boolean, time: String) {
        val current = _user.value ?: return
        _user.value = current.copy(notificationsEnabled = enabled, notificationTime = time)
    }
}
