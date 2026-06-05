package com.example.iluminadordeaudio.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── Estado compartido (Service escribe, ViewModel lee) ────────────────────────

data class ImportState(
    val isLoading:    Boolean   = false,
    val progress:     Float     = 0f,
    val rmsFrames:    FloatArray? = null,
    val displayName:  String?   = null,
    val loadedUri:    Uri?       = null,
    val error:        String?   = null
)

object ImportManager {
    private val _state = MutableStateFlow(ImportState())
    val state = _state.asStateFlow()

    @Volatile var pendingUri:  Uri?    = null
    @Volatile var pendingName: String? = null

    fun updateState(new: ImportState) { _state.value = new }
    fun resetState()                  { _state.value = ImportState() }
}

// ── ForegroundService ─────────────────────────────────────────────────────────

class ImportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_ID      = "import_channel"
        const val NOTIFICATION_ID = 1002

        fun startIntent(context: Context) = Intent(context, ImportService::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(0f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        scope.launch {
            runImport()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun runImport() {
        val uri  = ImportManager.pendingUri  ?: return
        val name = ImportManager.pendingName

        ImportManager.updateState(ImportState(isLoading = true, loadedUri = uri, displayName = name))

        try {
            // Throttle: el codec entrega miles de callbacks por canción.
            // Notificar solo cuando el porcentaje entero cambia (máx 100 updates vs ~7000).
            var lastPct = -1
            val (rms, _) = AudioDecoder().decodeToRms(applicationContext, uri, 30) { p ->
                val pct = (p * 100).toInt()
                if (pct > lastPct) {
                    lastPct = pct
                    ImportManager.updateState(ImportState(
                        isLoading = true, progress = p, loadedUri = uri, displayName = name))
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(p))
                }
            }
            ImportManager.updateState(ImportState(
                isLoading   = false,
                progress    = 1f,
                rmsFrames   = rms,
                displayName = name,
                loadedUri   = uri
            ))
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            ImportManager.updateState(ImportState(error = "Error al cargar audio: ${e.message}"))
        } finally {
            ImportManager.pendingUri  = null
            ImportManager.pendingName = null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Carga de audio",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            notificationManager.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(progress: Float): Notification {
        val pct = (progress * 100).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Iluminador de Audio")
            .setContentText("Cargando audio… $pct %")
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
