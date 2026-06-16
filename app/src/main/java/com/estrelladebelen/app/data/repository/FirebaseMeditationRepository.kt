package com.estrelladebelen.app.data.repository

import com.estrelladebelen.app.data.local.dao.MeditationDao
import com.estrelladebelen.app.data.local.entity.DownloadedMeditation
import com.estrelladebelen.app.data.model.Meditation
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await

class FirebaseMeditationRepository(
    private val dao: MeditationDao
) : MeditationRepository {

    private val collection = Firebase.firestore.collection("meditations")

    override suspend fun getAll(): List<Meditation> =
        collection
            .orderBy("order")
            .get()
            .await()
            .documents
            .mapNotNull { it.toMeditation() }

    override suspend fun getById(id: String): Meditation? =
        collection.document(id).get().await().toMeditation()

    override fun getDownloads(): Flow<List<DownloadedMeditation>> =
        dao.getAllDownloads()

    override suspend fun isDownloaded(id: String): Boolean =
        dao.findById(id) != null

    override suspend fun saveDownload(meditation: Meditation, localPath: String) {
        dao.insert(
            DownloadedMeditation(
                meditationId    = meditation.id,
                title           = meditation.title,
                durationSeconds = meditation.durationSeconds,
                localFilePath   = localPath,
                haloColor       = meditation.haloColor,
                isFree          = meditation.isFree
            )
        )
    }

    override suspend fun removeDownload(id: String) {
        dao.deleteById(id)
    }

    override suspend fun getNonFreeDownloads(): List<DownloadedMeditation> =
        dao.getNonFreeDownloads()

    override suspend fun deleteNonFreeDownloads() {
        dao.deleteNonFreeDownloads()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toMeditation(): Meditation? {
        if (!exists()) return null
        return Meditation(
            id              = id,
            title           = getString("title") ?: return null,
            description     = getString("description") ?: "",
            durationSeconds = getLong("durationSeconds")?.toInt() ?: 0,
            audioUrl        = getString("audioUrl") ?: "",
            imageUrl        = getString("imageUrl") ?: "",
            haloColor       = getString("haloColor") ?: "#9890B8",
            category        = getString("category") ?: "",
            order           = getLong("order")?.toInt() ?: 0,
            createdAt       = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            isFree          = getBoolean("isFree") ?: false
        )
    }
}
