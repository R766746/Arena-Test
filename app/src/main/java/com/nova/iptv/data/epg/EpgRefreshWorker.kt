package com.nova.iptv.data.epg

import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.nova.iptv.R
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
        var attempted = 0
        var failed = 0
        var imported = 0
        playlists.playlists().first().forEach { pl ->
            val sources = epg.sources(pl.id).first()
            val defaultUrl = when {
                pl.epgUrl.isNotBlank() -> pl.epgUrl
                pl.type == PlaylistType.XTREAM -> {
                    val password = playlists.resolvedPassword(pl)
                    if (password.isBlank()) "" else {
                        "${pl.url.trim().trimEnd('/')}/xmltv.php?username=${Uri.encode(pl.username.trim())}" +
                            "&password=${Uri.encode(password.trim())}"
                    }
                }
                else -> ""
            }
            if (sources.isEmpty() && defaultUrl.isNotBlank()) {
                attempted++
                setProgress(workDataOf(KEY_MESSAGE to "Syncing ${pl.name}", KEY_ATTEMPTED to attempted))
                epg.ingestUrl(pl.id, defaultUrl, s.epgTimeShiftHours).onSuccess { count ->
                    imported += count
                    if (pl.epgUrl.isBlank()) playlists.upsertPlaylist(pl.copy(epgUrl = defaultUrl))
                }.onFailure {
                    failed++
                    Timber.e(it, "default EPG ingest failed for playlist %s", pl.name)
                }
            } else {
                sources.filter { it.enabled }.forEach { src ->
                    attempted++
                    setProgress(workDataOf(KEY_MESSAGE to "Syncing ${pl.name}", KEY_ATTEMPTED to attempted))
                    epg.ingestUrl(pl.id, src.url, src.timeShiftHours, src.name).onSuccess { count ->
                        imported += count
                    }.onFailure {
                        failed++
                        Timber.e(it, "epg ingest %s", src.url)
                    }
                }
            }
        }
        epg.prune(s.epgPastDays)
        return when {
            failed == 0 -> Result.success(
                workDataOf(
                    KEY_MESSAGE to if (attempted == 0) "No EPG source available" else "EPG refresh complete",
                    KEY_ATTEMPTED to attempted,
                    KEY_IMPORTED to imported,
                ),
            )
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure(
                androidx.work.workDataOf("attempted" to attempted, "failed" to failed),
            )
        }
    }

    private fun notification(text: String): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(71, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(71, n)
        }
    }

    companion object {
        private const val UNIQUE = "nova-epg-refresh"
        private const val UNIQUE_NOW = "nova-epg-refresh-now"
        private const val CHANNEL = "nova_updates"
        const val KEY_MESSAGE = "message"
        const val KEY_ATTEMPTED = "attempted"
        const val KEY_IMPORTED = "imported"

        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NOW,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<EpgRefreshWorker>().build(),
            )
        }

        fun observeNow(context: Context) = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(UNIQUE_NOW)
            .map { it.lastOrNull() }

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
