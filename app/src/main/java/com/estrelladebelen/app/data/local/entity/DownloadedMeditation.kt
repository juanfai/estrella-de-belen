package com.estrelladebelen.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_meditations")
data class DownloadedMeditation(
    @PrimaryKey val meditationId: String,
    val title: String,
    val durationSeconds: Int,
    val localFilePath: String,
    val haloColor: String,
    val downloadedAt: Long = System.currentTimeMillis()
)
