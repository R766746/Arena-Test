package com.nova.iptv.nav

import android.net.Uri
import com.nova.iptv.domain.model.WatchKind
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object Routes {
    const val Splash = "splash"
    const val Home = "home"
    const val Guide = "guide"
    const val Player = "player/{targetJson}"
    const val Movies = "movies"
    const val Series = "series"
    const val Detail = "detail/{kind}/{id}"
    const val Recordings = "recordings"
    const val Search = "search"
    const val Settings = "settings"
    const val AddPlaylist = "addPlaylist"
    const val MultiView = "multiView"
    const val Diagnostics = "diagnostics"

    fun player(target: PlayTarget, moshi: Moshi = PlayTarget.moshi): String {
        val json = moshi.adapter(PlayTarget::class.java).toJson(target)
        return "player/${Uri.encode(json)}"
    }

    fun detail(kind: String, id: String): String = "detail/$kind/${Uri.encode(id)}"
}

@JsonClass(generateAdapter = true)
data class PlayTarget(
    val kind: String,
    val id: String,
    val channelId: String? = null,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val title: String? = null,
    val url: String? = null,
    val subtitle: String? = null,
    val number: Int? = null,
) {
    val watchKind: WatchKind
        get() = runCatching { WatchKind.valueOf(kind.uppercase()) }.getOrDefault(WatchKind.LIVE)

    companion object {
        val moshi: Moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        fun parse(raw: String): PlayTarget {
            val json = Uri.decode(raw)
            return moshi.adapter(PlayTarget::class.java).fromJson(json)
                ?: PlayTarget(kind = "LIVE", id = raw)
        }

        fun live(channelId: String, title: String? = null, number: Int? = null, url: String? = null) =
            PlayTarget(kind = "LIVE", id = channelId, channelId = channelId, title = title, number = number, url = url)

        fun vod(id: String, title: String? = null, url: String? = null) =
            PlayTarget(kind = "MOVIE", id = id, title = title, url = url)

        fun episode(id: String, title: String? = null, url: String? = null) =
            PlayTarget(kind = "SERIES", id = id, title = title, url = url)

        fun catchup(channelId: String, programId: String, start: Long, end: Long, title: String?, url: String?) =
            PlayTarget(
                kind = "CATCHUP",
                id = programId,
                channelId = channelId,
                startMs = start,
                endMs = end,
                title = title,
                url = url,
            )

        fun recording(id: String, title: String?, url: String?) =
            PlayTarget(kind = "RECORDING", id = id, title = title, url = url)
    }
}
