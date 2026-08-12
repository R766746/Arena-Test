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
                    val buf = ByteArray(32 * 1024)
                    val src = body.byteStream()
                    val deadline = rec.endMs
                    while (System.currentTimeMillis() < deadline) {
                        val n = src.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        bytes += n
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
