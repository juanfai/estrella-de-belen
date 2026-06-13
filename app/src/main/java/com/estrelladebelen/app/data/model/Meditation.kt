package com.estrelladebelen.app.data.model

data class Meditation(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val durationSeconds: Int = 0,
    val audioUrl: String = "",
    val imageUrl: String = "",
    val haloColor: String = "#9890B8",
    val category: String = "",
    val order: Int = 0,
    val createdAt: Long = 0L
) {
    val durationMinutes: Int get() = durationSeconds / 60
    val isNew: Boolean get() = System.currentTimeMillis() - createdAt < 7 * 24 * 60 * 60 * 1000L
}
