package com.nova.iptv.data.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nova.iptv.MainActivity
import com.nova.iptv.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * MediaSessionService so audio/video policy is correct. Notification is
 * hidden on pure leanback devices.
 */
@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playerManager: PlayerManager

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val player: ExoPlayer = playerManager.mainPlayer()
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(pi)
            .build()
        if (!isLeanbackOnly()) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF, buildNotification())
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = playerManager.mainPlayer()
        if (!p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    private fun isLeanbackOnly(): Boolean {
        val pm = packageManager
        val leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val touch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        return leanback && !touch
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL)
        .setContentTitle(getString(R.string.app_name))
        .setContentText("Playing")
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true)
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val CHANNEL = "nova_playback"
        private const val NOTIF = 11
    }
}
