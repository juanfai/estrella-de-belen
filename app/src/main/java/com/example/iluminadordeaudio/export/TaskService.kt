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

/**
 * ForegroundService mínimo que solo evita que el OS mate el proceso mientras
 * hay un import o export en curso. El trabajo real sigue en el ViewModel.
 */
class TaskService : Service() {

    companion object {
        private const val CHANNEL_ID      = "task_channel"
        private const val NOTIFICATION_ID = 2001
        private const val EXTRA_LABEL     = "label"

        fun startImport(ctx: Context) = ctx.startService(
            Intent(ctx, TaskService::class.java).putExtra(EXTRA_LABEL, "Cargando audio…"))

        fun startExport(ctx: Context) = ctx.startService(
            Intent(ctx, TaskService::class.java).putExtra(EXTRA_LABEL, "Exportando video…"))

        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, TaskService::class.java))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Procesando…"
        createChannel()
        val notification = buildNotification(label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Tareas en segundo plano",
                        NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) }
                )
            }
        }
    }

    private fun buildNotification(label: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Iluminador de Audio")
            .setContentText(label)
            .setOngoing(true)
            .setSilent(true)
            .build()
}
