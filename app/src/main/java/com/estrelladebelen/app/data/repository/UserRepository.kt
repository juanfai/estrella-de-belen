package com.estrelladebelen.app.data.repository

import com.estrelladebelen.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UserRepository {
    val currentUser: Flow<UserProfile?>

    // Receives the email-link URL from MainActivity when the user taps the link
    // in their inbox and the OS opens the app via App Links.
    val pendingEmailLink: StateFlow<String?>
    fun updatePendingEmailLink(link: String?)

    suspend fun sendSignInLink(email: String): Result<Unit>
    suspend fun completeSignInWithLink(email: String, link: String): Result<UserProfile>

    // Email persisted to SharedPreferences so it survives a process death between
    // "send link" and "user taps the link".
    fun getSavedEmail(): String?
    fun clearSavedEmail()

    suspend fun signOut()
    suspend fun toggleFavorite(meditationId: String)
    suspend fun recordSession(durationMinutes: Int)
    suspend fun updateNotificationSettings(enabled: Boolean, time: String)
}
