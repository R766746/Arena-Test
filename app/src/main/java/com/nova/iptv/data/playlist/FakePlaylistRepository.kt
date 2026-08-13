package com.nova.iptv.data.playlist

import com.nova.iptv.core.util.newId
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.Episode
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.SearchHit
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.WatchHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory playlist repo so UI work can start before Room/network.
 */
class FakePlaylistRepository : PlaylistRepository {
    private val playlists = MutableStateFlow(emptyList<Playlist>())
    private val channelList = MutableStateFlow(emptyList<Channel>())
    private val vodList = MutableStateFlow(emptyList<VodItem>())
    private val episodeList = MutableStateFlow(emptyList<Episode>())
    private val hist = MutableStateFlow(emptyList<WatchHistory>())

    override fun playlists(): Flow<List<Playlist>> = playlists
    override fun channels(playlistId: String) = channelList.map { it.filter { c -> c.playlistId == playlistId } }
    override fun channelsByGroup(playlistId: String, group: String) =
        channelList.map { it.filter { c -> c.playlistId == playlistId && c.groupName == group } }
    override fun favorites(playlistId: String) =
        channelList.map { it.filter { c -> c.playlistId == playlistId && c.favorite } }
    override fun groups(playlistId: String) =
        channelList.map { it.filter { c -> c.playlistId == playlistId }.map { c -> c.groupName }.distinct() }
    override fun vod(playlistId: String, kind: String) =
        vodList.map { it.filter { v -> v.playlistId == playlistId && v.kind.name == kind } }
    override fun episodes(seriesId: String) = episodeList.map { it.filter { e -> e.seriesId == seriesId } }
    override fun history(limit: Int) = hist.map { it.take(limit) }
    override fun recentChannels(playlistId: String) = favorites(playlistId)
    override fun searchAll(playlistId: String, query: String) = channelList.map { list ->
        list.filter { it.name.contains(query, true) }
            .map { SearchHit("channel", it.id, it.name, it.groupName, it.logoUrl, it.logoColor) }
    }

    override suspend fun snapshotChannels(playlistId: String) = channelList.value.filter { it.playlistId == playlistId }
    override suspend fun getPlaylist(id: String) = playlists.value.firstOrNull { it.id == id }
    override suspend fun getChannel(id: String) = channelList.value.firstOrNull { it.id == id }
    override suspend fun getVod(id: String) = vodList.value.firstOrNull { it.id == id }
    override suspend fun getEpisode(id: String) = episodeList.value.firstOrNull { it.id == id }
    override suspend fun upsertPlaylist(playlist: Playlist) {
        playlists.value = playlists.value.filterNot { it.id == playlist.id } + playlist
    }
    override suspend fun deletePlaylist(id: String) {
        playlists.value = playlists.value.filterNot { it.id == id }
    }
    override suspend fun saveImported(
        playlist: Playlist,
        channels: List<Channel>,
        vod: List<VodItem>,
        episodes: List<Episode>,
        plaintextPassword: String,
    ) {
        upsertPlaylist(playlist)
        channelList.value = channelList.value.filterNot { it.playlistId == playlist.id } + channels
        vodList.value = vodList.value.filterNot { it.playlistId == playlist.id } + vod
        episodeList.value = episodeList.value + episodes
    }
    override suspend fun toggleFavorite(channelId: String) {
        channelList.value = channelList.value.map { if (it.id == channelId) it.copy(favorite = !it.favorite) else it }
    }
    override suspend fun hideChannel(channelId: String) {
        channelList.value = channelList.value.map { if (it.id == channelId) it.copy(hidden = true) else it }
    }
    override suspend fun setEpgId(channelId: String, epgId: String) {
        channelList.value = channelList.value.map { if (it.id == channelId) it.copy(epgId = epgId) else it }
    }
    override suspend fun setWatchlist(id: String, flag: Boolean) {
        vodList.value = vodList.value.map { if (it.id == id) it.copy(watchlist = flag) else it }
    }
    override suspend fun setRating(id: String, rating: Float) {
        vodList.value = vodList.value.map { if (it.id == id) it.copy(localRating = rating) else it }
    }
    override suspend fun saveVodDetails(item: VodItem, episodes: List<Episode>) {
        vodList.value = vodList.value.filterNot { it.id == item.id } + item
        if (episodes.isNotEmpty()) {
            episodeList.value = episodeList.value.filterNot { it.seriesId == item.id } + episodes
        }
    }
    override suspend fun recordWatch(history: WatchHistory) {
        hist.value = listOf(history) + hist.value.filterNot { it.refId == history.refId }
    }
    override suspend fun genres(playlistId: String, kind: String) =
        vodList.value.filter { it.playlistId == playlistId && it.kind.name == kind }.flatMap { it.genres }.distinct()
    override suspend fun resolvedPassword(playlist: Playlist) = playlist.passwordEnc

}
