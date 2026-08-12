package com.nova.iptv.domain.usecase

import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.player.CatchupUrlBuilder
import com.nova.iptv.data.playlist.PlaylistImporter
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.ImportProgress
import com.nova.iptv.domain.model.NowNext
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.domain.model.Program
import com.nova.iptv.domain.model.SearchHit
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetNowNextUseCase @Inject constructor(
    private val epg: EpgRepository,
) {
    suspend operator fun invoke(channelId: String, nowMs: Long = System.currentTimeMillis()): NowNext =
        epg.nowAndNext(channelId, nowMs)
}

@Singleton
class ImportPlaylistUseCase @Inject constructor(
    private val importer: PlaylistImporter,
) {
    suspend fun importM3u(
        name: String,
        url: String,
        epgUrl: String,
        userAgent: String,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = importer.importRemoteM3u(name, url, epgUrl, userAgent, onProgress)

    suspend fun importXtream(
        name: String,
        portal: String,
        username: String,
        password: String,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = importer.importXtream(name, portal, username, password, onProgress)

    suspend fun importFile(
        name: String,
        uri: String,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = importer.importLocalFile(name, uri, onProgress)
}

@Singleton
class MatchEpgUseCase @Inject constructor(
    private val epg: EpgRepository,
) {
    suspend operator fun invoke(playlistId: String): Int = epg.autoMatch(playlistId)

    suspend fun assign(channelId: String, xmltvId: String) = epg.assignEpg(channelId, xmltvId)

    suspend fun searchDisplayNames(query: String) = epg.searchXmltvNames(query)
}

@Singleton
class ZapChannelUseCase @Inject constructor(
    private val playlists: PlaylistRepository,
) {
    suspend fun next(playlistId: String, currentId: String, delta: Int): Channel? {
        val list = playlists.snapshotChannels(playlistId).filter { !it.hidden }
        if (list.isEmpty()) return null
        val idx = list.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        val next = (idx + delta).mod(list.size)
        return list[next]
    }

    suspend fun byNumber(playlistId: String, number: Int): Channel? =
        playlists.snapshotChannels(playlistId).firstOrNull { it.number == number && !it.hidden }
}

@Singleton
class BuildCatchupUrlUseCase @Inject constructor(
    private val playlists: PlaylistRepository,
    private val builder: CatchupUrlBuilder,
) {
    suspend operator fun invoke(channel: Channel, program: Program, playlist: Playlist?): String? {
        val pl = playlist ?: playlists.getPlaylist(channel.playlistId)
        return builder.build(channel, program, pl)
    }
}

@Singleton
class SearchCatalogUseCase @Inject constructor(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
) {
    fun query(playlistId: String, raw: String): Flow<List<SearchHit>> =
        playlists.searchAll(playlistId, raw)
}

fun PlaylistType.label(): String = name
