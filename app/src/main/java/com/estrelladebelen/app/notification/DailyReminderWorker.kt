package com.estrelladebelen.app.notification

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Recrear el canal por si fue limpiado
        NotificationHelper.createChannel(applicationContext)

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.areNotificationsEnabled()) {
            nm.notify(NOTIFICATION_ID, NotificationHelper.build(applicationContext))
        }

        // Auto-reprogramar para mañana a la misma hora
        val time = inputData.getString(ReminderScheduler.KEY_TIME) ?: "08:00"
        ReminderScheduler.schedule(applicationContext, time)

        return Result.success()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
