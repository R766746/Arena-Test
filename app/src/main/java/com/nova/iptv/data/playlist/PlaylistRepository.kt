package com.nova.iptv.data.playlist

import com.nova.iptv.core.util.newId
import com.nova.iptv.data.local.NovaDatabase
import com.nova.iptv.data.local.PasswordVault
import com.nova.iptv.data.local.entity.ChannelEntity
import com.nova.iptv.data.local.entity.EpisodeEntity
import com.nova.iptv.data.local.entity.PlaylistEntity
import com.nova.iptv.data.local.entity.VodEntity
import com.nova.iptv.data.local.entity.WatchHistoryEntity
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.Episode
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.SearchHit
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.WatchHistory
import com.nova.iptv.domain.model.WatchKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface PlaylistRepository {
    fun playlists(): Flow<List<Playlist>>
    fun channels(playlistId: String): Flow<List<Channel>>
    fun channelsByGroup(playlistId: String, group: String): Flow<List<Channel>>
    fun favorites(playlistId: String): Flow<List<Channel>>
    fun groups(playlistId: String): Flow<List<String>>
    fun vod(playlistId: String, kind: String): Flow<List<VodItem>>
    fun episodes(seriesId: String): Flow<List<Episode>>
    fun history(limit: Int = 20): Flow<List<WatchHistory>>
    fun recentChannels(playlistId: String): Flow<List<Channel>>
    fun searchAll(playlistId: String, query: String): Flow<List<SearchHit>>

    suspend fun snapshotChannels(playlistId: String): List<Channel>
    suspend fun getPlaylist(id: String): Playlist?
    suspend fun getChannel(id: String): Channel?
    suspend fun getVod(id: String): VodItem?
    suspend fun getEpisode(id: String): Episode?
    suspend fun upsertPlaylist(playlist: Playlist)
    suspend fun deletePlaylist(id: String)
    suspend fun saveImported(
        playlist: Playlist,
        channels: List<Channel>,
        vod: List<VodItem>,
        episodes: List<Episode> = emptyList(),
        plaintextPassword: String = "",
    )
    suspend fun toggleFavorite(channelId: String)
    suspend fun hideChannel(channelId: String)
    suspend fun setEpgId(channelId: String, epgId: String)
    suspend fun setWatchlist(id: String, flag: Boolean)
    suspend fun setRating(id: String, rating: Float)
    suspend fun recordWatch(history: WatchHistory)
    suspend fun genres(playlistId: String, kind: String): List<String>
    suspend fun resolvedPassword(playlist: Playlist): String
}

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    private val db: NovaDatabase,
    private val vault: PasswordVault,
) : PlaylistRepository {

    override fun playlists(): Flow<List<Playlist>> =
        db.playlists().observeAll().map { it.map { e -> e.toModel() } }

    override fun channels(playlistId: String): Flow<List<Channel>> =
        db.channels().observeByPlaylist(playlistId).map { it.map { e -> e.toModel() } }

    override fun channelsByGroup(playlistId: String, group: String): Flow<List<Channel>> =
        db.channels().observeByGroup(playlistId, group).map { it.map { e -> e.toModel() } }

    override fun favorites(playlistId: String): Flow<List<Channel>> =
        db.channels().observeFavorites(playlistId).map { it.map { e -> e.toModel() } }

    override fun groups(playlistId: String): Flow<List<String>> = db.channels().observeGroups(playlistId)

    override fun vod(playlistId: String, kind: String): Flow<List<VodItem>> =
        db.vod().observe(playlistId, kind).map { it.map { e -> e.toModel() } }

    override fun episodes(seriesId: String): Flow<List<Episode>> =
        db.episodes().observe(seriesId).map { it.map { e -> e.toModel() } }

    override fun history(limit: Int): Flow<List<WatchHistory>> =
        db.history().observeRecent(limit).map { it.map { e -> e.toModel() } }

    override fun recentChannels(playlistId: String): Flow<List<Channel>> =
        db.history().observeRecentChannels(playlistId).map { it.map { e -> e.toModel() } }

    override fun searchAll(playlistId: String, query: String): Flow<List<SearchHit>> = flow {
        val q = ftsQuery(query)
        if (q.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val hits = ArrayList<SearchHit>()
        db.channels().search(playlistId, q).forEach {
            hits += SearchHit("channel", it.id, it.name, it.groupName, it.logoUrl, it.logoColor)
        }
        val now = System.currentTimeMillis()
        db.programs().searchTitles(q, now - 8 * 3600_000L, now + 8 * 3600_000L).forEach {
            hits += SearchHit("program", it.id, it.title, it.channelId)
        }
        db.vod().search(playlistId, q).forEach {
            hits += SearchHit(it.kind.lowercase(), it.id, it.title, it.year.toString(), it.posterUrl)
        }
        emit(hits)
    }

    override suspend fun snapshotChannels(playlistId: String): List<Channel> =
        db.channels().byPlaylist(playlistId).map { it.toModel() }

    override suspend fun getPlaylist(id: String): Playlist? = db.playlists().byId(id)?.toModel()

    override suspend fun getChannel(id: String): Channel? = db.channels().byId(id)?.toModel()

    override suspend fun getVod(id: String): VodItem? = db.vod().byId(id)?.toModel()

    override suspend fun getEpisode(id: String): Episode? = db.episodes().byId(id)?.toModel()

    override suspend fun upsertPlaylist(playlist: Playlist) {
        db.playlists().upsert(PlaylistEntity.from(playlist))
    }

    override suspend fun deletePlaylist(id: String) {
        if (id == Playlist.DEMO_ID) return
        db.channels().deleteByPlaylist(id)
        db.vod().deleteByPlaylist(id)
        db.playlists().delete(id)
        vault.deletePassword(id)
    }

    override suspend fun saveImported(
        playlist: Playlist,
        channels: List<Channel>,
        vod: List<VodItem>,
        episodes: List<Episode>,
        plaintextPassword: String,
    ) {
        val existing = db.channels().byPlaylist(playlist.id)
        val byKey = existing.associateBy { it.streamUrl + "|" + it.name }
        val merged = channels.map { ch ->
            val prev = byKey[ch.streamUrl + "|" + ch.name]
            if (prev != null) {
                ChannelEntity.from(ch).copy(
                    pk = prev.pk,
                    favorite = prev.favorite,
                    hidden = prev.hidden,
                    userOrder = prev.userOrder,
                    locked = prev.locked,
                    epgId = ch.epgId.ifBlank { prev.epgId },
                )
            } else {
                ChannelEntity.from(ch)
            }
        }
        db.playlists().upsert(PlaylistEntity.from(playlist.copy(lastUpdate = System.currentTimeMillis())))
        db.channels().replacePlaylistChannels(playlist.id, merged)
        if (vod.isNotEmpty()) {
            val prevVod = db.vod().byKind(playlist.id, "MOVIE") + db.vod().byKind(playlist.id, "SERIES")
            val keep = prevVod.associateBy { it.id }
            val mergedVod = vod.map { v ->
                val p = keep[v.id]
                VodEntity.from(v).copy(
                    pk = p?.pk ?: 0,
                    watchlist = p?.watchlist == true,
                    localRating = p?.localRating ?: 0f,
                )
            }
            db.vod().syncVod(playlist.id, mergedVod)
        }
        if (episodes.isNotEmpty()) {
            episodes.groupBy { it.seriesId }.forEach { (sid, eps) ->
                db.episodes().deleteForSeries(sid)
                db.episodes().upsertAll(eps.map { EpisodeEntity.from(it) })
            }
        }
        if (plaintextPassword.isNotBlank()) {
            vault.putPassword(playlist.id, plaintextPassword)
        }
        db.diagnostics().upsert(
            com.nova.iptv.data.local.entity.DiagnosticsEntity(
                lastPlaylistSize = merged.size,
            ),
        )
    }

    override suspend fun toggleFavorite(channelId: String) {
        val ch = db.channels().byId(channelId) ?: return
        db.channels().setFavorite(channelId, !ch.favorite)
    }

    override suspend fun hideChannel(channelId: String) {
        db.channels().hide(channelId)
    }

    override suspend fun setEpgId(channelId: String, epgId: String) {
        db.channels().setEpgId(channelId, epgId)
    }

    override suspend fun setWatchlist(id: String, flag: Boolean) = db.vod().setWatchlist(id, flag)

    override suspend fun setRating(id: String, rating: Float) = db.vod().setRating(id, rating)

    override suspend fun recordWatch(history: WatchHistory) {
        val existing = db.history().byRef(history.refId)
        val entity = WatchHistoryEntity.from(history).copy(pk = existing?.pk ?: 0, id = existing?.id ?: history.id)
        db.history().upsert(entity)
    }

    override suspend fun genres(playlistId: String, kind: String): List<String> {
        return db.vod().genreRows(playlistId, kind)
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    override suspend fun resolvedPassword(playlist: Playlist): String =
        vault.getPassword(playlist.id).ifBlank { playlist.passwordEnc }

    private fun ftsQuery(raw: String): String {
        val cleaned = raw.trim().replace("\"", "").replace("'", "")
        if (cleaned.isBlank()) return ""
        return cleaned.split(Regex("\\s+")).joinToString(" ") { "$it*" }
    }
}
