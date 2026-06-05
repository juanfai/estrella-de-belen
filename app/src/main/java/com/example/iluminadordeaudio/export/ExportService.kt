package com.example.iluminadordeaudio.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.iluminadordeaudio.R
import com.example.iluminadordeaudio.ui.ExportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val CHANNEL_ID      = "export_channel"
        const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context) = Intent(context, ExportService::class.java)
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
            runExport()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    // ── Export ────────────────────────────────────────────────────────────────

    private suspend fun runExport() {
        val uri  = ExportManager.pendingUri  ?: return
        val name = ExportManager.pendingName
        val rms  = ExportManager.pendingRms

        ExportManager.updateState(ExportState(isExporting = true))

        try {
            val resultUri = VideoExporter(
                context        = applicationContext,
                audioUri       = uri,
                outputName     = name,
                precomputedRms = rms
            ).export { progress ->
                ExportManager.updateState(ExportState(isExporting = true, progress = progress))
                notificationManager.notify(NOTIFICATION_ID, buildNotification(progress))
            }

            ExportManager.updateState(ExportState(
                isExporting = false, progress = 1f, exportedUri = resultUri))
            notificationManager.notify(NOTIFICATION_ID, buildNotification(1f, done = true))

        } catch (e: Exception) {
            ExportManager.updateState(ExportState(
                isExporting = false, error = "Error al exportar: ${e.message}"))
        } finally {
            ExportManager.pendingUri = null
            ExportManager.pendingRms = null
        }
    }

    // ── Notificación ──────────────────────────────────────────────────────────

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "Exportación de video",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setSound(null, null) }
            notificationManager.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(progress: Float, done: Boolean = false): Notification {
        val pct  = (progress * 100).toInt()
        val text = if (done) "¡Video guardado!" else "Exportando… $pct %"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Iluminador de Audio")
            .setContentText(text)
            .setProgress(100, pct, false)
            .setOngoing(!done)
            .setSilent(true)
            .build()
    }
}
