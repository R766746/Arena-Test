package com.nova.iptv.data.recordings

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val scheduleMutex = Mutex()

    fun observe(): Flow<List<Recording>> = db.recordings().observeAll().map { it.map { e -> e.toModel() } }

    suspend fun get(id: String): Recording? = db.recordings().byId(id)?.toModel()

    suspend fun activeFor(channelId: String, programId: String): Recording? =
        db.recordings().activeForProgram(channelId, programId)?.toModel()

    suspend fun schedule(program: Program, channelId: String, title: String): Result<Recording> = scheduleMutex.withLock {
        activeFor(channelId, program.id)?.let { return@withLock Result.success(it) }
        val pad = settings.settings.value.recPaddingMin * 60_000L
        val start = program.startMs - pad
        val end = program.endMs + pad
        val durationSec = ((end - start) / 1000L).coerceAtLeast(60)
        val estimated = durationSec * 2_000_000L / 8 // ~2 Mbps
        if (!hasSpace(estimated)) return@withLock Result.failure(IllegalStateException("no_space"))
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
        Result.success(rec)
    }

    suspend fun update(rec: Recording) = db.recordings().upsert(RecordingEntity.from(rec))

    suspend fun updateProgress(id: String, status: RecordingStatus, fileUri: String, bytes: Long) {
        db.recordings().updateProgress(id, status.name, fileUri, bytes)
    }

    suspend fun stop(id: String) {
        val recording = get(id) ?: return
        WorkManager.getInstance(context).cancelAllWorkByTag("rec-$id")
        if (recording.status == RecordingStatus.RECORDING) {
            runCatching { context.startService(RecordingService.stopIntent(context, id)) }
                .onFailure { Timber.w(it, "failed to finalize active recording %s", id) }
            updateProgress(id, RecordingStatus.COMPLETED, recording.fileUri, recording.bytes)
        } else if (recording.status == RecordingStatus.SCHEDULED) {
            db.recordings().delete(id)
        }
    }

    suspend fun delete(id: String) {
        val recording = get(id)
        if (recording?.status == RecordingStatus.RECORDING) {
            runCatching { context.startService(RecordingService.cancelIntent(context, id)) }
                .onFailure { Timber.w(it, "failed to stop active recording %s", id) }
        }
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
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val rec = repo.get(id) ?: return Result.failure()
        repo.update(rec.copy(status = RecordingStatus.RECORDING))
        // Actual muxing is handled by RecordingService; this worker is the scheduler.
        val intent = RecordingService.startIntent(applicationContext, id)
        applicationContext.startForegroundService(intent)
        return Result.success()
    }
}
