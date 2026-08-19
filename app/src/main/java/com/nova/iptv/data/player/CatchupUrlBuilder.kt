package com.nova.iptv.data.player

import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.domain.model.Program
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds catch-up / timeshift URLs for Xtream and M3U catchup-source templates.
 */
@Singleton
class CatchupUrlBuilder @Inject constructor() {

    fun build(channel: Channel, program: Program, playlist: Playlist?, allowInProgress: Boolean = false): String? {
        if (!channel.catchup && channel.catchupSource.isBlank() && playlist?.type != PlaylistType.XTREAM) {
            return null
        }
        val now = System.currentTimeMillis()
        if (!allowInProgress && program.endMs >= now) return null
        if (program.startMs >= now) return null
        val windowDays = channel.catchupDays.takeIf { it > 0 } ?: 7
        if (now - program.endMs > TimeUnit.DAYS.toMillis(windowDays.toLong())) return null

        val durationMin = ((program.endMs - program.startMs) / 60000L).coerceAtLeast(1)
        val startToken = TimeFmt.xtreamStart(program.startMs)

        if (playlist?.type == PlaylistType.XTREAM) {
            val portal = playlist.url.trimEnd('/')
            val user = playlist.username
            val pass = playlist.passwordEnc // vault-resolved by caller
            val stream = channel.xtreamStreamId.ifBlank { channel.streamUrl.substringAfterLast('/').substringBefore('.') }
            val php = "$portal/streaming/timeshift.php?username=$user&password=$pass&stream=$stream&start=$startToken&duration=$durationMin"
            val path = "$portal/timeshift/$user/$pass/$durationMin/$startToken/$stream.m3u8"
            return if (channel.catchupSource.contains("timeshift.php", true)) php else path
        }

        val template = channel.catchupSource
        if (template.isNotBlank()) {
            return template
                .replace("{utc}", (program.startMs / 1000).toString())
                .replace("{lutc}", (program.endMs / 1000).toString())
                .replace("{start}", startToken)
                .replace("{duration}", durationMin.toString())
                .replace("{offset}", durationMin.toString())
                .replace("\${start}", startToken)
                .replace("\${duration}", durationMin.toString())
                .replace("{Y-m-d:H-M}", startToken)
        }

        val base = channel.streamUrl
        val sep = if (base.contains('?')) '&' else '?'
        return "${base}${sep}utc=${program.startMs / 1000}&lutc=${program.endMs / 1000}"
    }

    fun isEligible(channel: Channel, program: Program, now: Long = System.currentTimeMillis(), allowInProgress: Boolean = false): Boolean {
        if (!channel.catchup && channel.catchupSource.isBlank()) return false
        if (!allowInProgress && program.endMs >= now) return false
        if (program.startMs >= now) return false
        val days = channel.catchupDays.takeIf { it > 0 } ?: 7
        return now - program.endMs <= TimeUnit.DAYS.toMillis(days.toLong())
    }
}
