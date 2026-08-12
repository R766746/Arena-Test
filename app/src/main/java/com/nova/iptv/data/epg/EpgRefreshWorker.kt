package com.nova.iptv.data.epg

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nova.iptv.R
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.Playlist
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.util.concurrent.TimeUnit

@HiltWorker
class EpgRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val epg: EpgRepository,
    private val playlists: PlaylistRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        setForeground(notification("Refreshing EPG"))
        val s = settings.settings.value
        playlists.playlists().first().forEach { pl ->
            val sources = epg.sources(pl.id).first()
            if (sources.isEmpty() && pl.epgUrl.isNotBlank()) {
                epg.ingestUrl(pl.id, pl.epgUrl, s.epgTimeShiftHours).onFailure {
                    Timber.e(it, "epg ingest %s", pl.epgUrl)
                }
            } else {
                sources.filter { it.enabled }.forEach { src ->
                    epg.ingestUrl(pl.id, src.url, src.timeShiftHours, src.name)
                }
            }
            if (pl.id == Playlist.DEMO_ID) {
                epg.ingestDemo(pl.id)
            }
        }
        epg.prune(s.epgPastDays)
        return Result.success()
    }

    private fun notification(text: String): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()
        return ForegroundInfo(71, n)
    }

    companion object {
        private const val UNIQUE = "nova-epg-refresh"
        private const val CHANNEL = "nova_updates"

        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<EpgRefreshWorker>().build())
        }

        fun schedule(context: Context, hours: Int) {
            val req = PeriodicWorkRequestBuilder<EpgRefreshWorker>(hours.toLong().coerceAtLeast(6), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE,
                ExistingPeriodicWorkPolicy.UPDATE,
                req,
            )
        }
    }
}
