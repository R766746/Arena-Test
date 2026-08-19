package com.nova.iptv.data.recordings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.nova.iptv.R
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.RecordingStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.OutputStream
import java.util.Collections
import javax.inject.Inject

/**
 * Records the live HLS/TS stream to a SAF tree URI using a simple HTTP DataSink.
 * Quality equals the live stream — no transcode on Fire Stick.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var repo: RecordingRepository
    @Inject lateinit var playlists: PlaylistRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var client: OkHttpClient

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private val cancelledIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val stoppedIds = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var activeId: String? = null
    @Volatile private var activeCall: okhttp3.Call? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val id = intent.getStringExtra(EXTRA_ID)
            if (id != null) stopAndKeepRecording(id)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CANCEL) {
            val id = intent.getStringExtra(EXTRA_ID)
            if (id != null) cancelRecording(id)
            return START_NOT_STICKY
        }
        val id = intent?.getStringExtra(EXTRA_ID) ?: return START_NOT_STICKY
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(99, notif("Recording…"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(99, notif("Recording…"))
        }
        cancelledIds.remove(id)
        stoppedIds.remove(id)
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin()
            record(id)
        }
        return START_NOT_STICKY
    }

    private fun cancelRecording(id: String) {
        cancelledIds.add(id)
        if (activeId == id) {
            activeCall?.cancel()
            job?.cancel()
            job = null
        }
        stopSelf()
    }

    private fun stopAndKeepRecording(id: String) {
        stoppedIds.add(id)
        if (activeId == id) {
            activeCall?.cancel()
            job?.cancel()
        } else {
            stopSelf()
        }
    }

    private suspend fun record(id: String) {
        activeId = id
        val rec = repo.get(id) ?: run {
            if (activeId == id) activeId = null
            return stopSelf()
        }
        val channel = playlists.getChannel(rec.channelId)
        val url = channel?.streamUrl.orEmpty()
        if (url.isBlank()) {
            updateIfPresent(rec.copy(status = RecordingStatus.FAILED))
            if (activeId == id) activeId = null
            stopSelf()
            return
        }
        val tree = settings.settings.value.recTreeUri
        val outUri = openOutput(tree, rec.title)
        if (outUri == null) {
            updateIfPresent(rec.copy(status = RecordingStatus.FAILED))
            if (activeId == id) activeId = null
            stopSelf()
            return
        }
        var bytes = 0L
        val outputUri = outUri.toString()
        try {
            repo.updateProgress(id, RecordingStatus.RECORDING, outputUri, 0L)
            val deadline = rec.endMs
            val output = contentResolver.openOutputStream(outUri) ?: error("cannot open recording output")
            output.use { os: OutputStream ->
                var directUrl: okhttp3.HttpUrl? = null
                val sourceRequest = Request.Builder().url(url).build()
                executeWithActive(sourceRequest) { resp ->
                    if (!resp.isSuccessful) error("stream HTTP ${resp.code}")
                    val body = resp.body ?: error("empty")
                    val isHls = url.contains(".m3u8", true) ||
                        resp.header("Content-Type").orEmpty().contains("mpegurl", true)
                    if (isHls) {
                        bytes = recordHls(resp.request.url, body.string(), os, deadline) { current ->
                            bytes = current
                            repo.updateProgress(id, RecordingStatus.RECORDING, outputUri, current)
                        }
                    } else {
                        directUrl = sourceRequest.url
                        val base = bytes
                        val copied = copyBody(body.byteStream(), os, deadline) { current ->
                            bytes = base + current
                            repo.updateProgress(id, RecordingStatus.RECORDING, outputUri, bytes)
                        }
                        bytes = maxOf(bytes, base + copied)
                    }
                }

                // Many IPTV providers rotate or close long-running transport
                // stream sockets. EOF is not completion for live TV: reconnect
                // and append until the programme deadline or explicit cancel.
                while (directUrl != null && System.currentTimeMillis() < deadline && !cancelledIds.contains(id)) {
                    delay(DIRECT_RECONNECT_MS)
                    try {
                        executeWithActive(Request.Builder().url(directUrl!!).build()) { resp ->
                            if (!resp.isSuccessful) error("stream reconnect HTTP ${resp.code}")
                            val body = resp.body ?: error("empty reconnect body")
                            val base = bytes
                            val copied = copyBody(body.byteStream(), os, deadline) { current ->
                                bytes = base + current
                                repo.updateProgress(id, RecordingStatus.RECORDING, outputUri, bytes)
                            }
                            bytes = maxOf(bytes, base + copied)
                        }
                    } catch (t: kotlinx.coroutines.CancellationException) {
                        throw t
                    } catch (t: Throwable) {
                        Timber.w(t, "recording stream interrupted; retrying id=%s", id)
                        repo.updateProgress(id, RecordingStatus.RECORDING, outputUri, bytes)
                    }
                }
                if (!cancelledIds.contains(id)) {
                    repo.updateProgress(id, RecordingStatus.RECORDING, outputUri, bytes)
                }
                os.flush()
            }
            if (!cancelledIds.remove(id)) {
                stoppedIds.remove(id)
                updateIfPresent(rec.copy(status = RecordingStatus.COMPLETED, fileUri = outputUri, bytes = bytes))
            }
        } catch (t: Throwable) {
            if (stoppedIds.remove(id)) {
                updateIfPresent(rec.copy(status = RecordingStatus.COMPLETED, fileUri = outputUri, bytes = bytes))
            } else if (!cancelledIds.remove(id)) {
                Timber.e(t, "record failed")
                updateIfPresent(rec.copy(status = RecordingStatus.FAILED, fileUri = outputUri, bytes = bytes))
            }
        } finally {
            if (activeId == id) {
                activeId = null
                job = null
            }
        }
        stopSelf()
    }

    private suspend fun recordHls(
        initialUrl: okhttp3.HttpUrl,
        initialText: String,
        output: OutputStream,
        deadline: Long,
        onProgress: suspend (Long) -> Unit,
    ): Long {
        var mediaUrl = initialUrl
        var playlist = initialText
        if (playlist.contains("#EXT-X-STREAM-INF")) {
            val variants = playlist.lineSequence().toList().mapIndexedNotNull { index, line ->
                if (!line.startsWith("#EXT-X-STREAM-INF")) return@mapIndexedNotNull null
                val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val uri = playlist.lineSequence().drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                    ?: return@mapIndexedNotNull null
                bandwidth to (mediaUrl.resolve(uri) ?: return@mapIndexedNotNull null)
            }
            mediaUrl = variants.maxByOrNull { it.first }?.second ?: error("HLS master has no variants")
            playlist = fetchText(mediaUrl)
        }

        val seen = LinkedHashSet<String>()
        var written = 0L
        while (System.currentTimeMillis() < deadline) {
            val lines = playlist.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            lines.filter { it.startsWith("#EXT-X-MAP") }.forEach { line ->
                val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: return@forEach
                val resolved = mediaUrl.resolve(uri) ?: return@forEach
                if (seen.add(resolved.toString())) written += download(resolved, output, deadline, written, onProgress)
            }
            lines.filter { !it.startsWith('#') }.forEach { uri ->
                val resolved = mediaUrl.resolve(uri) ?: return@forEach
                if (seen.add(resolved.toString())) written += download(resolved, output, deadline, written, onProgress)
            }
            if (lines.any { it == "#EXT-X-ENDLIST" }) break
            val targetSeconds = lines.firstNotNullOfOrNull {
                it.removePrefix("#EXT-X-TARGETDURATION:").takeIf { _ -> it.startsWith("#EXT-X-TARGETDURATION:") }?.toLongOrNull()
            } ?: 4L
            delay((targetSeconds * 500L).coerceIn(1_000L, 10_000L))
            playlist = fetchText(mediaUrl)
        }
        return written
    }

    private suspend fun fetchText(url: okhttp3.HttpUrl): String {
        return executeWithActive(Request.Builder().url(url).build()) { response ->
            if (!response.isSuccessful) error("HLS playlist HTTP ${response.code}")
            response.body?.string() ?: error("empty HLS playlist")
        }
    }

    private suspend fun download(
        url: okhttp3.HttpUrl,
        output: OutputStream,
        deadline: Long,
        baseBytes: Long,
        onProgress: suspend (Long) -> Unit,
    ): Long {
        return executeWithActive(Request.Builder().url(url).build()) { response ->
            if (!response.isSuccessful) error("HLS segment HTTP ${response.code}")
            copyBody(response.body?.byteStream() ?: error("empty HLS segment"), output, deadline) { segmentBytes ->
                onProgress(baseBytes + segmentBytes)
            }
        }
    }

    private suspend fun copyBody(
        input: java.io.InputStream,
        output: OutputStream,
        deadline: Long,
        onProgress: suspend (Long) -> Unit,
    ): Long {
        var written = 0L
        var lastProgressAt = 0L
        var lastProgressBytes = 0L
        input.use { source ->
            val buffer = ByteArray(32 * 1024)
            while (System.currentTimeMillis() < deadline) {
                val count = source.read(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
                written += count
                val now = System.currentTimeMillis()
                if (written - lastProgressBytes >= PROGRESS_BYTES || now - lastProgressAt >= PROGRESS_MS) {
                    onProgress(written)
                    lastProgressAt = now
                    lastProgressBytes = written
                }
            }
        }
        onProgress(written)
        return written
    }

    private suspend fun <T> executeWithActive(request: Request, block: suspend (Response) -> T): T {
        val call = client.newCall(request)
        activeCall = call
        return try {
            call.execute().use { response -> block(response) }
        } finally {
            if (activeCall === call) activeCall = null
        }
    }

    private suspend fun updateIfPresent(rec: com.nova.iptv.domain.model.Recording) {
        if (repo.get(rec.id) != null) repo.update(rec)
    }

    private fun openOutput(tree: String, title: String): Uri? {
        val safe = title.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(48)
        val name = "${safe}_${System.currentTimeMillis()}.ts"
        return if (tree.isNotBlank()) {
            val root = DocumentFile.fromTreeUri(this, Uri.parse(tree)) ?: return null
            root.createFile("video/MP2T", name)?.uri
        } else {
            val f = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES), name)
            f.parentFile?.mkdirs()
            Uri.fromFile(f)
        }
    }

    private fun notif(text: String) = NotificationCompat.Builder(this, CH)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_save)
        .setOngoing(true)
        .build()
        .also {
            if (Build.VERSION.SDK_INT >= 26) {
                getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CH, "Recordings", NotificationManager.IMPORTANCE_LOW),
                )
            }
        }

    companion object {
        private const val CH = "nova_rec"
        private const val EXTRA_ID = "id"
        private const val ACTION_CANCEL = "com.nova.iptv.action.CANCEL_RECORDING"
        private const val ACTION_STOP = "com.nova.iptv.action.STOP_RECORDING"
        private const val PROGRESS_BYTES = 2L * 1024L * 1024L
        private const val PROGRESS_MS = 5_000L
        private const val DIRECT_RECONNECT_MS = 1_000L

        fun startIntent(context: Context, id: String): Intent =
            Intent(context, RecordingService::class.java).putExtra(EXTRA_ID, id)

        fun cancelIntent(context: Context, id: String): Intent =
            Intent(context, RecordingService::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(EXTRA_ID, id)

        fun stopIntent(context: Context, id: String): Intent =
            Intent(context, RecordingService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_ID, id)
    }
}
