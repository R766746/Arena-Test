package com.nova.iptv.data.recordings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.OutputStream
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id") ?: return START_NOT_STICKY
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(99, notif("Recording…"), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(99, notif("Recording…"))
        }
        job?.cancel()
        job = scope.launch { record(id) }
        return START_NOT_STICKY
    }

    private suspend fun record(id: String) {
        val rec = repo.get(id) ?: return stopSelf()
        val channel = playlists.getChannel(rec.channelId)
        val url = channel?.streamUrl.orEmpty()
        if (url.isBlank()) {
            repo.update(rec.copy(status = RecordingStatus.FAILED))
            stopSelf()
            return
        }
        val tree = settings.settings.value.recTreeUri
        val outUri = openOutput(tree, rec.title)
        if (outUri == null) {
            repo.update(rec.copy(status = RecordingStatus.FAILED))
            stopSelf()
            return
        }
        var bytes = 0L
        runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body ?: error("empty")
                contentResolver.openOutputStream(outUri)?.use { os: OutputStream ->
                    val deadline = rec.endMs
                    val isHls = url.contains(".m3u8", true) ||
                        resp.header("Content-Type").orEmpty().contains("mpegurl", true)
                    if (isHls) {
                        bytes += recordHls(resp.request.url, body.string(), os, deadline)
                    } else {
                        bytes += copyBody(body.byteStream(), os, deadline)
                    }
                    os.flush()
                }
            }
            repo.update(rec.copy(status = RecordingStatus.COMPLETED, fileUri = outUri.toString(), bytes = bytes))
        }.onFailure {
            Timber.e(it, "record failed")
            repo.update(rec.copy(status = RecordingStatus.FAILED, bytes = bytes))
        }
        stopSelf()
    }

    private suspend fun recordHls(
        initialUrl: okhttp3.HttpUrl,
        initialText: String,
        output: OutputStream,
        deadline: Long,
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
                if (seen.add(resolved.toString())) written += download(resolved, output, deadline)
            }
            lines.filter { !it.startsWith('#') }.forEach { uri ->
                val resolved = mediaUrl.resolve(uri) ?: return@forEach
                if (seen.add(resolved.toString())) written += download(resolved, output, deadline)
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

    private fun fetchText(url: okhttp3.HttpUrl): String {
        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HLS playlist HTTP ${response.code}")
            response.body?.string() ?: error("empty HLS playlist")
        }
    }

    private fun download(url: okhttp3.HttpUrl, output: OutputStream, deadline: Long): Long {
        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HLS segment HTTP ${response.code}")
            copyBody(response.body?.byteStream() ?: error("empty HLS segment"), output, deadline)
        }
    }

    private fun copyBody(input: java.io.InputStream, output: OutputStream, deadline: Long): Long {
        var written = 0L
        input.use { source ->
            val buffer = ByteArray(32 * 1024)
            while (System.currentTimeMillis() < deadline) {
                val count = source.read(buffer)
                if (count <= 0) break
                output.write(buffer, 0, count)
                written += count
            }
        }
        return written
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
    }
}
