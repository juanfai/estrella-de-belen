package com.estrelladebelen.app.data.model

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val favorites: List<String> = emptyList(),
    val totalSessions: Int = 0,
    val totalMinutes: Int = 0,
    val streak: Int = 0,
    val lastSessionDate: String = "",
    val notificationsEnabled: Boolean = false,
    val notificationTime: String = "08:00",
    val subscriptionStatus: String = "free"
)
