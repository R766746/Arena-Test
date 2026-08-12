package com.nova.iptv.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.Episode
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.domain.model.Program
import com.nova.iptv.domain.model.Recording
import com.nova.iptv.domain.model.RecordingStatus
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.VodKind
import com.nova.iptv.domain.model.WatchHistory
import com.nova.iptv.domain.model.WatchKind

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["id"], unique = true)],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val id: String,
    val name: String,
    val type: String,
    val url: String,
    val username: String,
    val passwordEnc: String,
    val epgUrl: String,
    val userAgent: String,
    val referrer: String = "",
    val lastUpdate: Long,
    val autoUpdate: Boolean,
    val updateIntervalHours: Int,
) {
    fun toModel() = Playlist(
        id = id,
        name = name,
        type = runCatching { PlaylistType.valueOf(type) }.getOrDefault(PlaylistType.M3U),
        url = url,
        username = username,
        passwordEnc = passwordEnc,
        epgUrl = epgUrl,
        userAgent = userAgent,
        referrer = referrer,
        lastUpdate = lastUpdate,
        autoUpdate = autoUpdate,
        updateIntervalHours = updateIntervalHours,
        portal = if (type == PlaylistType.XTREAM.name) url else "",
    )

    companion object {
        fun from(m: Playlist) = PlaylistEntity(
            id = m.id,
            name = m.name,
            type = m.type.name,
            url = m.url,
            username = m.username,
            passwordEnc = m.passwordEnc,
            epgUrl = m.epgUrl,
            userAgent = m.userAgent,
            referrer = m.referrer,
            lastUpdate = m.lastUpdate,
            autoUpdate = m.autoUpdate,
            updateIntervalHours = m.updateIntervalHours,
        )
    }
}

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["id"], unique = true),
        Index("playlistId"),
        Index("groupName"),
        Index("favorite"),
        Index("epgId"),
        Index(value = ["playlistId", "streamUrl", "name"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val id: String,
    val playlistId: String,
    val number: Int,
    val name: String,
    val groupName: String,
    val logoUrl: String,
    val logoText: String,
    val logoColor: Int,
    val streamUrl: String,
    val epgId: String,
    val catchup: Boolean,
    val catchupDays: Int = 0,
    val catchupSource: String = "",
    val timeshift: String = "",
    val userAgent: String = "",
    val referrer: String = "",
    val favorite: Boolean,
    val hidden: Boolean,
    val locked: Boolean,
    val userOrder: Int,
    val xtreamStreamId: String = "",
) {
    fun toModel() = Channel(
        id, playlistId, number, name, groupName, logoUrl, logoText, logoColor, streamUrl,
        epgId, catchup, catchupDays, catchupSource, timeshift, userAgent, referrer,
        favorite, hidden, locked, userOrder, xtreamStreamId,
    )

    companion object {
        fun from(m: Channel) = ChannelEntity(
            id = m.id,
            playlistId = m.playlistId,
            number = m.number,
            name = m.name,
            groupName = m.groupName,
            logoUrl = m.logoUrl,
            logoText = m.logoText,
            logoColor = m.logoColor,
            streamUrl = m.streamUrl,
            epgId = m.epgId,
            catchup = m.catchup,
            catchupDays = m.catchupDays,
            catchupSource = m.catchupSource,
            timeshift = m.timeshift,
            userAgent = m.userAgent,
            referrer = m.referrer,
            favorite = m.favorite,
            hidden = m.hidden,
            locked = m.locked,
            userOrder = m.userOrder,
            xtreamStreamId = m.xtreamStreamId,
        )
    }
}

@Fts4(contentEntity = ChannelEntity::class)
@Entity(tableName = "channel_fts")
data class ChannelFts(
    val name: String,
    val groupName: String,
)

@Entity(
    tableName = "programs",
    indices = [
        Index(value = ["id"], unique = true),
        Index("channelId"),
        Index(value = ["startMs", "endMs"]),
        Index("title"),
    ],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val id: String,
    val channelId: String,
    val title: String,
    val description: String,
    val category: String,
    val startMs: Long,
    val endMs: Long,
    val catchup: Boolean,
) {
    fun toModel() = Program(id, channelId, title, description, category, startMs, endMs, catchup)

    companion object {
        fun from(m: Program) = ProgramEntity(
            id = m.id,
            channelId = m.channelId,
            title = m.title,
            description = m.description,
            category = m.category,
            startMs = m.startMs,
            endMs = m.endMs,
            catchup = m.catchup,
        )
    }
}

@Fts4(contentEntity = ProgramEntity::class)
@Entity(tableName = "program_fts")
data class ProgramFts(
    val title: String,
    val description: String,
)

@Entity(
    tableName = "vod",
    indices = [
        Index(value = ["id"], unique = true),
        Index("playlistId"),
        Index("kind"),
        Index("title"),
    ],
)
data class VodEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val id: String,
    val playlistId: String,
    val kind: String,
    val title: String,
    val year: Int,
    val rating: String,
    val durationMin: Int,
    val genresCsv: String,
    val posterUrl: String,
    val backdropUrl: String,
    val description: String,
    val castCsv: String,
    val director: String,
    val streamUrl: String,
    val tmdbId: String,
    val watchlist: Boolean = false,
    val localRating: Float = 0f,
    val xtreamId: String = "",
) {
    fun toModel() = VodItem(
        id, playlistId,
        runCatching { VodKind.valueOf(kind) }.getOrDefault(VodKind.MOVIE),
        title, year, rating, durationMin,
        genresCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        posterUrl, backdropUrl, description,
        castCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        director, streamUrl, tmdbId, watchlist, localRating, xtreamId,
    )

    companion object {
        fun from(m: VodItem) = VodEntity(
            id = m.id,
            playlistId = m.playlistId,
            kind = m.kind.name,
            title = m.title,
            year = m.year,
            rating = m.rating,
            durationMin = m.durationMin,
            genresCsv = m.genresCsv,
            posterUrl = m.posterUrl,
            backdropUrl = m.backdropUrl,
            description = m.description,
            castCsv = m.castCsv,
            director = m.director,
            streamUrl = m.streamUrl,
            tmdbId = m.tmdbId,
            watchlist = m.watchlist,
            localRating = m.localRating,
            xtreamId = m.xtreamId,
        )
    }
}

@Fts4(contentEntity = VodEntity::class)
@Entity(tableName = "vod_fts")
data class VodFts(
    val title: String,
    val description: String,
)

@Entity(
    tableName = "episodes",
    indices = [Index(value = ["id"], unique = true), Index("seriesId")],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val id: String,
    val seriesId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val durationMin: Int,
    val description: String,
    val streamUrl: String,
    val progress: Float,
) {
    fun toModel() = Episode(id, seriesId, season, episode, title, durationMin, description, streamUrl, progress)

    companion object {
        fun from(m: Episode) = EpisodeEntity(
            id = m.id,
            seriesId = m.seriesId,
            season = m.season,
            episode = m.episode,
            title = m.title,
            durationMin = m.durationMin,
            description = m.description,
            streamUrl = m.streamUrl,
            progress = m.progress,
        )
    }
}

@Entity(
    tableName = "recordings",
    indices = [Index(value = ["id"], unique = true), Index("status"), Index("startMs")],
)
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val id: String,
    val title: String,
    val channelId: String,
    val programId: String,
    val startMs: Long,
    val endMs: Long,
    val status: String,
    val fileUri: String,
    val bytes: Long,
) {
    fun toModel() = Recording(
        id, title, channelId, programId, startMs, endMs,
        runCatching { RecordingStatus.valueOf(status) }.getOrDefault(RecordingStatus.SCHEDULED),
        fileUri, bytes,
    )

    companion object {
        fun from(m: Recording) = RecordingEntity(
            id = m.id,
            title = m.title,
            channelId = m.channelId,
            programId = m.programId,
            startMs = m.startMs,
            endMs = m.endMs,
            status = m.status.name,
            fileUri = m.fileUri,
            bytes = m.bytes,
        )
    }
}

@Entity(
    tableName = "watch_history",
    indices = [Index(value = ["id"], unique = true), Index("atMs"), Index("refId")],
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val id: String,
    val kind: String,
    val refId: String,
    val title: String,
    val subtitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val atMs: Long,
    val artwork: String = "",
) {
    fun toModel() = WatchHistory(
        id,
        runCatching { WatchKind.valueOf(kind) }.getOrDefault(WatchKind.LIVE),
        refId, title, subtitle, positionMs, durationMs, atMs, artwork,
    )

    companion object {
        fun from(m: WatchHistory) = WatchHistoryEntity(
            id = m.id,
            kind = m.kind.name,
            refId = m.refId,
            title = m.title,
            subtitle = m.subtitle,
            positionMs = m.positionMs,
            durationMs = m.durationMs,
            atMs = m.atMs,
            artwork = m.artwork,
        )
    }
}

@Entity(
    tableName = "epg_sources",
    indices = [Index(value = ["id"], unique = true), Index("playlistId")],
)
data class EpgSourceEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val id: String,
    val playlistId: String,
    val url: String,
    val enabled: Boolean,
    val timeShiftHours: Int,
    val name: String,
)

@Entity(
    tableName = "xmltv_channels",
    indices = [Index("xmltvId"), Index("displayName")],
)
data class XmltvChannelEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val xmltvId: String,
    val displayName: String,
    val iconUrl: String = "",
    val sourceId: String = "",
)

@Entity(tableName = "diagnostics")
data class DiagnosticsEntity(
    @PrimaryKey val id: Int = 1,
    val lastEpgDurationMs: Long = 0,
    val lastEpgAt: Long = 0,
    val lastPlaylistSize: Int = 0,
)
