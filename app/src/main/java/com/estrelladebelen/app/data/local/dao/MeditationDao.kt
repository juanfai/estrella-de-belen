package com.estrelladebelen.app.data.local.dao

import androidx.room.*
import com.estrelladebelen.app.data.local.entity.DownloadedMeditation
import kotlinx.coroutines.flow.Flow

@Dao
interface MeditationDao {
    @Query("SELECT * FROM downloaded_meditations ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedMeditation>>

    @Query("SELECT * FROM downloaded_meditations WHERE meditationId = :id LIMIT 1")
    suspend fun findById(id: String): DownloadedMeditation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(meditation: DownloadedMeditation)

    @Delete
    suspend fun delete(meditation: DownloadedMeditation)

    @Query("DELETE FROM downloaded_meditations WHERE meditationId = :id")
    suspend fun deleteById(id: String)
}
