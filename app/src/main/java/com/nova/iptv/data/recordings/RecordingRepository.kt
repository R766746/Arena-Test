package com.nova.iptv.data.recordings

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import com.nova.iptv.R
import com.nova.iptv.core.util.newId
import com.nova.iptv.data.local.NovaDatabase
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.local.entity.RecordingEntity
import com.nova.iptv.domain.model.Program
import com.nova.iptv.domain.model.Recording
import com.nova.iptv.domain.model.RecordingStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val db: NovaDatabase,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) {
    fun observe(): Flow<List<Recording>> = db.recordings().observeAll().map { it.map { e -> e.toModel() } }

    suspend fun get(id: String): Recording? = db.recordings().byId(id)?.toModel()

    suspend fun schedule(program: Program, channelId: String, title: String): Result<Recording> {
        val pad = settings.settings.value.recPaddingMin * 60_000L
        val start = program.startMs - pad
        val end = program.endMs + pad
        val durationSec = ((end - start) / 1000L).coerceAtLeast(60)
        val estimated = durationSec * 2_000_000L / 8 // ~2 Mbps
        if (!hasSpace(estimated)) return Result.failure(IllegalStateException("no_space"))
        val rec = Recording(
            id = newId(),
            title = title,
            channelId = channelId,
            programId = program.id,
            startMs = start,
            endMs = end,
            status = RecordingStatus.SCHEDULED,
        )
        db.recordings().upsert(RecordingEntity.from(rec))
        val delay = (start - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<RecordingWorker>()
            .setInputData(workDataOf("id" to rec.id))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .addTag("rec-${rec.id}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("rec-${rec.id}", ExistingWorkPolicy.REPLACE, req)
        return Result.success(rec)
    }

    suspend fun update(rec: Recording) = db.recordings().upsert(RecordingEntity.from(rec))

    suspend fun delete(id: String) {
        val recording = get(id)
        recording?.fileUri?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                val uri = Uri.parse(raw)
                if (uri.scheme == "file") {
                    uri.path?.let { java.io.File(it).delete() }
                } else {
                    DocumentFile.fromSingleUri(context, uri)?.delete()
                        ?: (context.contentResolver.delete(uri, null, null) > 0)
                }
            }
        }
        db.recordings().delete(id)
        WorkManager.getInstance(context).cancelAllWorkByTag("rec-$id")
    }

    private fun hasSpace(need: Long): Boolean {
        val stat = runCatching {
            val path = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
            StatFs(path.absolutePath)
        }.getOrNull() ?: return true
        val free = stat.availableBytes
        return free > (need * 3 / 2)
    }
}

@HiltWorker
class RecordingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: RecordingRepository,
    private val db: NovaDatabase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val rec = repo.get(id) ?: return Result.failure()
        setForeground(info("Recording ${rec.title}"))
        repo.update(rec.copy(status = RecordingStatus.RECORDING))
        // Actual muxing is handled by RecordingService; this worker is the scheduler.
        val intent = android.content.Intent(applicationContext, RecordingService::class.java)
            .putExtra("id", id)
        applicationContext.startForegroundService(intent)
        return Result.success()
    }

    private fun info(text: String): ForegroundInfo {
        val n = NotificationCompat.Builder(applicationContext, "nova_rec")
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            mgr.createNotificationChannel(
                android.app.NotificationChannel("nova_rec", "Recordings", android.app.NotificationManager.IMPORTANCE_LOW),
            )
        }
        return ForegroundInfo(88, n)
    }
}
