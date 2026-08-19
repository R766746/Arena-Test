package com.nova.iptv.data.recordings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.CoroutineWorker
import com.nova.iptv.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val title = inputData.getString("title").orEmpty()
        val channel = inputData.getString("channel").orEmpty()
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = applicationContext.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel("nova_remind", "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val n = NotificationCompat.Builder(applicationContext, "nova_remind")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.reminder_title))
            .setContentText(applicationContext.getString(R.string.reminder_body, title, channel))
            .setAutoCancel(true)
            .build()
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(title.hashCode(), n)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context, programStartMs: Long, title: String, channel: String) {
            val whenMs = (programStartMs - 2 * 60_000L - System.currentTimeMillis()).coerceAtLeast(0)
            val req = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(whenMs, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("title" to title, "channel" to channel))
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
