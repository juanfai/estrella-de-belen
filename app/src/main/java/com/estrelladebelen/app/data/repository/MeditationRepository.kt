package com.estrelladebelen.app.data.repository

import com.estrelladebelen.app.data.model.Meditation
import com.estrelladebelen.app.data.local.entity.DownloadedMeditation
import kotlinx.coroutines.flow.Flow

interface MeditationRepository {
    suspend fun getAll(): List<Meditation>
    suspend fun getById(id: String): Meditation?
    fun getDownloads(): Flow<List<DownloadedMeditation>>
    suspend fun isDownloaded(id: String): Boolean
    suspend fun saveDownload(meditation: Meditation, localPath: String)
    suspend fun removeDownload(id: String)
}

// Stub implementation with sample data — replaced by FirebaseMeditationRepository
// when Firebase is wired up.
class MeditationRepositoryStub : MeditationRepository {

    private val samples = listOf(
        Meditation(
            id = "1",
            title = "Paz interior",
            description = "Una meditación guiada para encontrar la calma en tu interior y soltar las tensiones del día.",
            durationSeconds = 720,
            audioUrl = "",
            glowColor = "#d9abff",
            haloColor = "#5e1fff",
            category = "paz",
            order = 1,
            createdAt = System.currentTimeMillis() - 2 * 24 * 3600 * 1000L
        ),
        Meditation(
            id = "2",
            title = "Sueño profundo",
            description = "Deja ir los pensamientos y prepara tu cuerpo y mente para un descanso reparador.",
            durationSeconds = 1200,
            audioUrl = "",
            glowColor = "#a0b4ff",
            haloColor = "#1f3fff",
            category = "sueño",
            order = 2,
            createdAt = System.currentTimeMillis() - 10 * 24 * 3600 * 1000L
        ),
        Meditation(
            id = "3",
            title = "Gratitud",
            description = "Abre tu corazón a la abundancia que ya existe en tu vida con esta práctica de gratitud.",
            durationSeconds = 600,
            audioUrl = "",
            glowColor = "#ffcba0",
            haloColor = "#ff7f1f",
            category = "gratitud",
            order = 3,
            createdAt = System.currentTimeMillis() - 1 * 24 * 3600 * 1000L
        ),
        Meditation(
            id = "4",
            title = "Concentración",
            description = "Enfoca tu mente y aumenta tu claridad para afrontar el día con presencia plena.",
            durationSeconds = 300,
            audioUrl = "",
            glowColor = "#a0ffd9",
            haloColor = "#1fff8a",
            category = "concentración",
            order = 4,
            createdAt = System.currentTimeMillis() - 30 * 24 * 3600 * 1000L
        ),
        Meditation(
            id = "5",
            title = "Amor propio",
            description = "Cultiva una relación amorosa y compasiva con vos mismo en este momento del día.",
            durationSeconds = 900,
            audioUrl = "",
            glowColor = "#ffb0d9",
            haloColor = "#ff1f7f",
            category = "amor",
            order = 5,
            createdAt = System.currentTimeMillis() - 5 * 24 * 3600 * 1000L
        ),
        Meditation(
            id = "6",
            title = "Respiración consciente",
            description = "Vuelve al presente usando solo la respiración como ancla. Ideal para cualquier momento.",
            durationSeconds = 300,
            audioUrl = "",
            glowColor = "#b0e8ff",
            haloColor = "#1faeff",
            category = "respiración",
            order = 6,
            createdAt = System.currentTimeMillis() - 20 * 24 * 3600 * 1000L
        )
    )

    override suspend fun getAll(): List<Meditation> = samples.sortedBy { it.order }
    override suspend fun getById(id: String): Meditation? = samples.find { it.id == id }
    override fun getDownloads(): Flow<List<DownloadedMeditation>> =
        kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun isDownloaded(id: String): Boolean = false
    override suspend fun saveDownload(meditation: Meditation, localPath: String) {}
    override suspend fun removeDownload(id: String) {}
}
