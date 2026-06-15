package com.estrelladebelen.app.data.repository

import com.estrelladebelen.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    val currentUser: Flow<UserProfile?>

    suspend fun signIn(email: String, password: String): Result<UserProfile>
    suspend fun signInWithGoogle(idToken: String): Result<UserProfile>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
    suspend fun toggleFavorite(meditationId: String)
    suspend fun recordSession(durationMinutes: Int)
    suspend fun updateNotificationSettings(enabled: Boolean, time: String)
}
