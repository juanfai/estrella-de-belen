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
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return Result.success()
        nm.notify(NOTIFICATION_ID, NotificationHelper.build(applicationContext))
        return Result.success()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
