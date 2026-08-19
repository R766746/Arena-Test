package com.nova.iptv.data.playlist

import com.nova.iptv.core.util.newId
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.CatalogSort
import com.nova.iptv.domain.model.CatalogGroup
import androidx.paging.PagingData
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
    override fun pagedChannels(playlistId: String, groupId: String, query: String, sort: CatalogSort) = channelList.map { source ->
        val filtered = source.filter { channel ->
            channel.playlistId == playlistId && when {
                groupId == "favorites" -> channel.favorite
                groupId.startsWith("g:") -> channel.groupName == groupId.removePrefix("g:")
                else -> true
            } && (query.isBlank() || channel.name.contains(query, true))
        }
        PagingData.from(when (sort) {
            CatalogSort.PROVIDER -> filtered
            CatalogSort.TITLE -> filtered.sortedBy { it.name.lowercase() }
            CatalogSort.NEWEST -> filtered.sortedBy { it.number }
        })
    }
    override fun favorites(playlistId: String) =
        channelList.map { it.filter { c -> c.playlistId == playlistId && c.favorite } }
    override fun groups(playlistId: String) =
        channelList.map { it.filter { c -> c.playlistId == playlistId }.map { c -> c.groupName }.distinct() }
    override fun channelGroups(playlistId: String) = channelList.map { source ->
        source.filter { it.playlistId == playlistId && !it.hidden && it.groupName.isNotBlank() }
            .groupingBy { it.groupName }.eachCount().map { CatalogGroup(it.key, it.value) }
    }
    override fun channelCount(playlistId: String) = channelList.map { it.count { c -> c.playlistId == playlistId && !c.hidden } }
    override fun favoriteCount(playlistId: String) = channelList.map { it.count { c -> c.playlistId == playlistId && c.favorite && !c.hidden } }
    override fun recentChannelCount(playlistId: String) = kotlinx.coroutines.flow.flowOf(0)
    override fun vod(playlistId: String, kind: String) =
        vodList.map { it.filter { v -> v.playlistId == playlistId && v.kind.name == kind } }

    override fun pagedVod(
        playlistId: String,
        kind: String,
        genre: String?,
        continueWatching: Boolean,
        watchlist: Boolean,
        query: String,
        sort: CatalogSort,
    ) = vod(playlistId, kind).map { source ->
        val filtered = source.filter { item ->
            (genre.isNullOrBlank() || genre in item.genres) &&
                (!watchlist || item.watchlist) &&
                (query.isBlank() || item.title.contains(query, ignoreCase = true))
        }
        val sorted = when (sort) {
            CatalogSort.PROVIDER -> filtered
            CatalogSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            CatalogSort.NEWEST -> filtered.sortedWith(compareByDescending<VodItem> { it.year }.thenBy { it.title })
        }
        PagingData.from(sorted)
    }

    override fun vodGroups(playlistId: String, kind: String) =
        vod(playlistId, kind).map { items ->
            items.flatMap { it.genres }.groupingBy { it }.eachCount().map { CatalogGroup(it.key, it.value) }
        }
    override fun watchlistCount(playlistId: String, kind: String) =
        vod(playlistId, kind).map { items -> items.count { it.watchlist } }
    override suspend fun watchlistIds(playlistId: String) =
        vodList.value.filter { it.playlistId == playlistId && it.watchlist }.map { it.id }
    override fun episodes(seriesId: String) = episodeList.map { it.filter { e -> e.seriesId == seriesId } }
    override fun history(limit: Int) = hist.map { it.take(limit) }
    override fun recentChannels(playlistId: String) = favorites(playlistId)
    override fun searchAll(playlistId: String, query: String) = channelList.map { list ->
        list.filter { it.name.contains(query, true) }
            .map { SearchHit("channel", it.id, it.name, it.groupName, it.logoUrl, it.logoColor) }
    }

    override suspend fun snapshotChannels(playlistId: String) = channelList.value.filter { it.playlistId == playlistId }
    override suspend fun snapshotChannelsLimited(playlistId: String, limit: Int) = snapshotChannels(playlistId).take(limit)
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
