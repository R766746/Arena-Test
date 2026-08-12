package com.nova.iptv.data.playlist

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import com.nova.iptv.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class PlaylistUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: PlaylistRepository,
    private val importer: PlaylistImporter,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(notification("Updating playlists"))
        val all = kotlinx.coroutines.flow.first(repo.playlists()) // keep import-free
        val list = repo.playlists().let { flow -> kotlinx.coroutines.flow.first(flow) }
        all.filter { it.autoUpdate && it.type != com.nova.iptv.domain.model.PlaylistType.DEMO }
            .forEach { pl ->
                runCatching { importer.refresh(pl) }
                    .onFailure { Timber.e(it, "playlist refresh failed %s", pl.id) }
            }
        return Result.success()
    }

    private fun notification(text: String): ForegroundInfo {
        val id = 42
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return ForegroundInfo(id, n)
    }

    companion object {
        private const val UNIQUE = "nova-playlist-update"
        private const val CHANNEL = "nova_updates"

        fun schedule(context: Context, alsoNow: Boolean) {
            ensureChannel(context)
            val req = PeriodicWorkRequestBuilder<PlaylistUpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
            if (alsoNow) {
                WorkManager.getInstance(context).enqueue(
                    androidx.work.OneTimeWorkRequestBuilder<PlaylistUpdateWorker>().build(),
                )
            }
        }

        private fun ensureChannel(context: Context) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                mgr.createNotificationChannel(
                    android.app.NotificationChannel(CHANNEL, "Updates", android.app.NotificationManager.IMPORTANCE_LOW),
                )
            }
        }
    }
}
