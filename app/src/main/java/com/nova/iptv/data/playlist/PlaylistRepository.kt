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
import com.nova.iptv.domain.model.CatalogSort
import com.nova.iptv.domain.model.CatalogGroup
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOn
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import javax.inject.Inject
import javax.inject.Singleton

interface PlaylistRepository {
    fun playlists(): Flow<List<Playlist>>
    fun channels(playlistId: String): Flow<List<Channel>>
    fun channelsByGroup(playlistId: String, group: String): Flow<List<Channel>>
    fun pagedChannels(
        playlistId: String,
        groupId: String,
        query: String = "",
        sort: CatalogSort = CatalogSort.PROVIDER,
    ): Flow<PagingData<Channel>>
    fun favorites(playlistId: String): Flow<List<Channel>>
    fun groups(playlistId: String): Flow<List<String>>
    fun channelGroups(playlistId: String): Flow<List<CatalogGroup>>
    fun channelCount(playlistId: String): Flow<Int>
    fun favoriteCount(playlistId: String): Flow<Int>
    fun recentChannelCount(playlistId: String): Flow<Int>
    fun vod(playlistId: String, kind: String): Flow<List<VodItem>>
    fun pagedVod(
        playlistId: String,
        kind: String,
        genre: String? = null,
        continueWatching: Boolean = false,
        watchlist: Boolean = false,
        query: String = "",
        sort: CatalogSort = CatalogSort.PROVIDER,
    ): Flow<PagingData<VodItem>>
    fun vodGroups(playlistId: String, kind: String): Flow<List<CatalogGroup>>
    fun watchlistCount(playlistId: String, kind: String): Flow<Int>
    suspend fun watchlistIds(playlistId: String): List<String>
    fun episodes(seriesId: String): Flow<List<Episode>>
    fun history(limit: Int = 20): Flow<List<WatchHistory>>
    fun recentChannels(playlistId: String): Flow<List<Channel>>
    fun searchAll(playlistId: String, query: String): Flow<List<SearchHit>>

    suspend fun snapshotChannels(playlistId: String): List<Channel>
    suspend fun snapshotChannelsLimited(playlistId: String, limit: Int): List<Channel>
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
    suspend fun saveVodDetails(item: VodItem, episodes: List<Episode> = emptyList())
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
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.channels().observeByPlaylist(playlistId).map { it.map { e -> e.toModel() } }

    override fun channelsByGroup(playlistId: String, group: String): Flow<List<Channel>> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.channels().observeByGroup(playlistId, group).map { it.map { e -> e.toModel() } }

    override fun pagedChannels(playlistId: String, groupId: String, query: String, sort: CatalogSort): Flow<PagingData<Channel>> {
        if (playlistId.isBlank()) return kotlinx.coroutines.flow.flowOf(PagingData.empty())
        if (groupId == "recent") {
            val queryClause = query.trim().takeIf(String::isNotBlank)?.let { "AND c.name LIKE ? ESCAPE '\\' COLLATE NOCASE" }.orEmpty()
            val args = mutableListOf<Any>(playlistId)
            if (queryClause.isNotEmpty()) args += "%${escapeLike(query.trim())}%"
            val order = when (sort) {
                CatalogSort.PROVIDER -> "h.atMs DESC"
                CatalogSort.TITLE -> "c.name COLLATE NOCASE, h.atMs DESC"
                CatalogSort.NEWEST -> "c.number, c.name COLLATE NOCASE"
            }
            val sql = """
                SELECT c.* FROM channels c
                INNER JOIN watch_history h ON h.refId = c.id
                WHERE c.playlistId = ? AND c.hidden = 0 AND h.kind = 'LIVE'
                $queryClause
                ORDER BY $order
            """.trimIndent()
            return Pager(
                PagingConfig(pageSize = 60, initialLoadSize = 120, prefetchDistance = 15, enablePlaceholders = false, maxSize = 240),
            ) { db.channels().pagingSource(SimpleSQLiteQuery(sql, args.toTypedArray())) }
                .flow.map { page -> page.map { it.toModel() } }
        }
        val args = mutableListOf<Any>(playlistId)
        val filter = when {
            groupId == "favorites" -> "AND favorite = 1"
            groupId.startsWith("g:") -> {
                args += groupId.removePrefix("g:")
                "AND groupName = ?"
            }
            else -> ""
        }
        val queryClause = query.trim().takeIf(String::isNotBlank)?.let {
            args += "%${escapeLike(it)}%"
            "AND name LIKE ? ESCAPE '\\' COLLATE NOCASE"
        }.orEmpty()
        val order = when (sort) {
            CatalogSort.PROVIDER -> "userOrder, number, name"
            CatalogSort.TITLE -> "name COLLATE NOCASE, userOrder"
            CatalogSort.NEWEST -> "number, name COLLATE NOCASE"
        }
        val sql = "SELECT * FROM channels WHERE playlistId = ? AND hidden = 0 $filter $queryClause ORDER BY $order"
        return Pager(
            PagingConfig(pageSize = 60, initialLoadSize = 120, prefetchDistance = 15, enablePlaceholders = false, maxSize = 240),
        ) { db.channels().pagingSource(SimpleSQLiteQuery(sql, args.toTypedArray())) }
            .flow.map { page -> page.map { it.toModel() } }
    }

    override fun favorites(playlistId: String): Flow<List<Channel>> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.channels().observeFavorites(playlistId).map { it.map { e -> e.toModel() } }

    override fun groups(playlistId: String): Flow<List<String>> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.channels().observeGroups(playlistId)

    override fun channelGroups(playlistId: String): Flow<List<CatalogGroup>> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.channels().observeGroupCounts(playlistId).map { rows -> rows.map { CatalogGroup(it.name, it.count) } }

    override fun channelCount(playlistId: String): Flow<Int> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(0) else db.channels().observeCount(playlistId)

    override fun favoriteCount(playlistId: String): Flow<Int> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(0) else db.channels().observeFavoriteCount(playlistId)

    override fun recentChannelCount(playlistId: String): Flow<Int> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(0) else db.channels().observeRecentCount(playlistId)

    override fun vod(playlistId: String, kind: String): Flow<List<VodItem>> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.vod().observe(playlistId, kind).map { it.map { e -> e.toModel() } }

    override fun pagedVod(
        playlistId: String,
        kind: String,
        genre: String?,
        continueWatching: Boolean,
        watchlist: Boolean,
        query: String,
        sort: CatalogSort,
    ): Flow<PagingData<VodItem>> {
        if (playlistId.isBlank()) return kotlinx.coroutines.flow.flowOf(PagingData.empty())
        val clauses = mutableListOf("playlistId = ?", "kind = ?")
        val args = mutableListOf<Any>(playlistId, kind)
        genre?.takeIf(String::isNotBlank)?.let {
            clauses += "genresCsv LIKE ?"
            args += "%$it%"
        }
        if (continueWatching) {
            clauses += "id IN (SELECT refId FROM watch_history WHERE kind = ?)"
            args += kind
        }
        if (watchlist) clauses += "watchlist = 1"
        query.trim().takeIf(String::isNotBlank)?.let {
            clauses += "title LIKE ? ESCAPE '\\' COLLATE NOCASE"
            args += "%${escapeLike(it)}%"
        }
        val order = when (sort) {
            CatalogSort.PROVIDER -> "pk"
            CatalogSort.TITLE -> "title COLLATE NOCASE, pk"
            CatalogSort.NEWEST -> "year DESC, title COLLATE NOCASE, pk"
        }
        val sql = "SELECT * FROM vod WHERE ${clauses.joinToString(" AND ")} ORDER BY $order"
        return Pager(
            PagingConfig(
                pageSize = 60,
                initialLoadSize = 120,
                prefetchDistance = 15,
                enablePlaceholders = false,
                maxSize = 240,
            ),
        ) { db.vod().pagingSource(SimpleSQLiteQuery(sql, args.toTypedArray())) }
            .flow
            .map { page -> page.map { it.toModel() } }
    }

    override fun vodGroups(playlistId: String, kind: String): Flow<List<CatalogGroup>> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.vod().observeGroupCounts(playlistId, kind).map { groups ->
            groups.map { CatalogGroup(it.name, it.count) }
        }

    override fun watchlistCount(playlistId: String, kind: String): Flow<Int> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(0)
        else db.vod().observeWatchlistCount(playlistId, kind)

    override suspend fun watchlistIds(playlistId: String): List<String> =
        if (playlistId.isBlank()) emptyList() else db.vod().watchlistIds(playlistId)

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    override fun episodes(seriesId: String): Flow<List<Episode>> =
        db.episodes().observe(seriesId).map { it.map { e -> e.toModel() } }

    override fun history(limit: Int): Flow<List<WatchHistory>> =
        db.history().observeRecent(limit).map { it.map { e -> e.toModel() } }

    override fun recentChannels(playlistId: String): Flow<List<Channel>> =
        if (playlistId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else db.history().observeRecentChannels(playlistId).map { it.map { e -> e.toModel() } }

    override fun searchAll(playlistId: String, query: String): Flow<List<SearchHit>> = flow {
        if (playlistId.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val q = ftsQuery(query)
        if (q.isBlank()) {
            emit(emptyList())
            return@flow
        }
        val now = System.currentTimeMillis()
        val hits = coroutineScope {
            val channels = async { db.channels().search(playlistId, q) }
            val programs = async { db.programs().searchTitles(q, now - 8 * 3600_000L, now + 8 * 3600_000L) }
            val vod = async { db.vod().search(playlistId, q) }
            buildList {
                channels.await().forEach {
                    add(SearchHit("channel", it.id, it.name, it.groupName, it.logoUrl, it.logoColor))
                }
                programs.await().forEach {
                    add(SearchHit("program", it.id, it.title, it.channelId))
                }
                vod.await().forEach {
                    add(SearchHit(it.kind.lowercase(), it.id, it.title, it.year.toString(), it.posterUrl))
                }
            }.distinctBy { it.kind to it.id }.take(150)
        }
        emit(hits)
    }.flowOn(Dispatchers.IO)

    override suspend fun snapshotChannels(playlistId: String): List<Channel> =
        db.channels().byPlaylist(playlistId).map { it.toModel() }

    override suspend fun snapshotChannelsLimited(playlistId: String, limit: Int): List<Channel> =
        db.channels().byPlaylistLimited(playlistId, limit).map { it.toModel() }

    override suspend fun getPlaylist(id: String): Playlist? = db.playlists().byId(id)?.toModel()

    override suspend fun getChannel(id: String): Channel? = db.channels().byId(id)?.toModel()

    override suspend fun getVod(id: String): VodItem? = db.vod().byId(id)?.toModel()

    override suspend fun getEpisode(id: String): Episode? = db.episodes().byId(id)?.toModel()

    override suspend fun upsertPlaylist(playlist: Playlist) {
        db.playlists().upsert(PlaylistEntity.from(playlist))
    }

    override suspend fun deletePlaylist(id: String) {
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
                    userOrder = ch.userOrder,
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
            db.vod().replacePlaylistVod(playlist.id, mergedVod)
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

    override suspend fun saveVodDetails(item: VodItem, episodes: List<Episode>) {
        val existing = db.vod().byId(item.id)
        db.vod().upsertAll(
            listOf(
                VodEntity.from(item).copy(
                    pk = existing?.pk ?: 0,
                    genresCsv = existing?.genresCsv ?: item.genresCsv,
                    watchlist = existing?.watchlist ?: item.watchlist,
                    localRating = existing?.localRating ?: item.localRating,
                ),
            ),
        )
        if (episodes.isNotEmpty()) {
            db.episodes().deleteForSeries(item.id)
            db.episodes().upsertAll(episodes.map { EpisodeEntity.from(it) })
        }
    }

    override suspend fun recordWatch(history: WatchHistory) {
        val existing = db.history().byRef(history.refId)
        val entity = WatchHistoryEntity.from(history).copy(pk = existing?.pk ?: 0, id = existing?.id ?: history.id)
        db.history().upsert(entity)
    }

    override suspend fun genres(playlistId: String, kind: String): List<String> {
        if (playlistId.isBlank()) return emptyList()
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
        return Regex("[\\p{L}\\p{N}]+").findAll(raw.trim())
            .map { it.value }
            .take(8)
            .joinToString(" ") { "$it*" }
    }
}
