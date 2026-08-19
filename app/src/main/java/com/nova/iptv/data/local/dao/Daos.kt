package com.nova.iptv.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.RawQuery
import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import com.nova.iptv.data.local.entity.ChannelEntity
import com.nova.iptv.data.local.entity.DiagnosticsEntity
import com.nova.iptv.data.local.entity.EpgSourceEntity
import com.nova.iptv.data.local.entity.EpisodeEntity
import com.nova.iptv.data.local.entity.PlaylistEntity
import com.nova.iptv.data.local.entity.ProgramEntity
import com.nova.iptv.data.local.entity.RecordingEntity
import com.nova.iptv.data.local.entity.VodEntity
import com.nova.iptv.data.local.entity.WatchHistoryEntity
import com.nova.iptv.data.local.entity.XmltvChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name")
    suspend fun all(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun count(): Int
}

@Dao
interface ChannelDao {
    data class GroupCount(val name: String, val count: Int)

    @RawQuery(observedEntities = [ChannelEntity::class, WatchHistoryEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND hidden = 0 ORDER BY userOrder, number, name")
    fun observeByPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND hidden = 0 ORDER BY userOrder, number, name")
    suspend fun byPlaylist(playlistId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND hidden = 0 ORDER BY userOrder, number, name LIMIT :limit")
    suspend fun byPlaylistLimited(playlistId: String, limit: Int): List<ChannelEntity>

    @Query("SELECT groupName AS name, COUNT(*) AS count FROM channels WHERE playlistId = :playlistId AND hidden = 0 AND groupName != '' GROUP BY groupName ORDER BY MIN(pk)")
    fun observeGroupCounts(playlistId: String): Flow<List<GroupCount>>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND hidden = 0")
    fun observeCount(playlistId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND favorite = 1 AND hidden = 0")
    fun observeFavoriteCount(playlistId: String): Flow<Int>

    @Query("SELECT COUNT(DISTINCT c.id) FROM channels c INNER JOIN watch_history h ON h.refId = c.id WHERE c.playlistId = :playlistId AND c.hidden = 0 AND h.kind = 'LIVE'")
    fun observeRecentCount(playlistId: String): Flow<Int>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupName = :group AND hidden = 0 ORDER BY userOrder, number, name")
    fun observeByGroup(playlistId: String, group: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND favorite = 1 AND hidden = 0 ORDER BY userOrder, number, name")
    fun observeFavorites(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND favorite = 1 AND hidden = 0 ORDER BY userOrder, number, name")
    suspend fun favorites(playlistId: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number AND hidden = 0 LIMIT 1")
    suspend fun byNumber(playlistId: String, number: Int): ChannelEntity?

    @Query(
        """
        SELECT c.* FROM channels c
        JOIN channel_fts fts ON c.pk = fts.rowid
        WHERE c.playlistId = :playlistId AND channel_fts MATCH :q
        LIMIT 50
        """,
    )
    suspend fun search(playlistId: String, q: String): List<ChannelEntity>

    @Query("SELECT DISTINCT groupName FROM channels WHERE playlistId = :playlistId AND hidden = 0 ORDER BY groupName")
    fun observeGroups(playlistId: String): Flow<List<String>>

    @Query("SELECT DISTINCT groupName FROM channels WHERE playlistId = :playlistId AND hidden = 0 ORDER BY groupName")
    suspend fun groups(playlistId: String): List<String>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND groupName = :group AND hidden = 0")
    suspend fun countInGroup(playlistId: String, group: String): Int

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND hidden = 0")
    suspend fun count(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ChannelEntity>)

    @Update
    suspend fun update(entity: ChannelEntity)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("UPDATE channels SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: String, fav: Boolean)

    @Query("UPDATE channels SET hidden = 1 WHERE id = :id")
    suspend fun hide(id: String)

    @Query("UPDATE channels SET epgId = :epgId WHERE id = :id")
    suspend fun setEpgId(id: String, epgId: String)

    @Query("UPDATE channels SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: String, locked: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId AND id NOT IN (:keptIds)")
    suspend fun deleteOrphans(playlistId: String, keptIds: List<String>)

    @Query("DELETE FROM channels WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id FROM channels WHERE playlistId = :playlistId")
    suspend fun idsByPlaylist(playlistId: String): List<String>

    @Transaction
    suspend fun replacePlaylistChannels(playlistId: String, items: List<ChannelEntity>) {
        val keep = items.mapTo(HashSet(items.size)) { it.id }
        val obsolete = idsByPlaylist(playlistId).filterNot { it in keep }
        items.chunked(SQL_BATCH_SIZE).forEach { upsertAll(it) }
        obsolete.chunked(SQL_BATCH_SIZE).forEach { deleteByIds(it) }
    }
}

@Dao
interface ProgramDao {
    @Query("DELETE FROM programs")
    suspend fun deleteAll()
    @Query(
        """
        SELECT * FROM programs
        WHERE channelId = :channelId AND startMs < :toMs AND endMs > :fromMs
        ORDER BY startMs
        """,
    )
    suspend fun overlapping(channelId: String, fromMs: Long, toMs: Long): List<ProgramEntity>

    @Query(
        """
        SELECT * FROM programs
        WHERE channelId = :channelId AND startMs < :toMs AND endMs > :fromMs
        ORDER BY startMs
        """,
    )
    fun observeOverlapping(channelId: String, fromMs: Long, toMs: Long): Flow<List<ProgramEntity>>

    @Query(
        """
        SELECT * FROM programs
        WHERE channelId = :channelId AND startMs <= :now AND endMs > :now
        LIMIT 1
        """,
    )
    suspend fun now(channelId: String, now: Long): ProgramEntity?

    @Query(
        """
        SELECT * FROM programs
        WHERE channelId = :channelId AND startMs > :now
        ORDER BY startMs LIMIT 1
        """,
    )
    suspend fun next(channelId: String, now: Long): ProgramEntity?

    @Query(
        """
        SELECT p.* FROM programs p
        JOIN program_fts fts ON p.pk = fts.rowid
        WHERE program_fts MATCH :q AND p.startMs < :toMs AND p.endMs > :fromMs
        LIMIT 40
        """,
    )
    suspend fun searchTitles(q: String, fromMs: Long, toMs: Long): List<ProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProgramEntity>)

    @Query(
        """
        DELETE FROM programs
        WHERE sourceId = :sourceId AND syncToken != :syncToken AND startMs >= :fromMs
        """,
    )
    suspend fun deleteStaleFutureForSource(sourceId: String, syncToken: String, fromMs: Long): Int

    @Query("DELETE FROM programs WHERE channelId = :channelId")
    suspend fun deleteForChannel(channelId: String)

    @Query("DELETE FROM programs WHERE endMs < :cutoff")
    suspend fun pruneBefore(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM programs")
    suspend fun count(): Int

    @Query("SELECT * FROM programs WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): ProgramEntity?
}

@Dao
interface VodDao {
    data class GroupCount(val name: String, val count: Int)

    @RawQuery(observedEntities = [VodEntity::class])
    fun pagingSource(query: SupportSQLiteQuery): PagingSource<Int, VodEntity>

    @Query(
        """
        SELECT genresCsv AS name, COUNT(*) AS count
        FROM vod
        WHERE playlistId = :playlistId AND kind = :kind AND genresCsv != ''
        GROUP BY genresCsv
        ORDER BY MIN(pk)
        """,
    )
    fun observeGroupCounts(playlistId: String, kind: String): Flow<List<GroupCount>>

    @Query("SELECT COUNT(*) FROM vod WHERE playlistId = :playlistId AND kind = :kind AND watchlist = 1")
    fun observeWatchlistCount(playlistId: String, kind: String): Flow<Int>

    @Query("SELECT id FROM vod WHERE playlistId = :playlistId AND watchlist = 1")
    suspend fun watchlistIds(playlistId: String): List<String>

    @Query("SELECT * FROM vod WHERE playlistId = :playlistId AND kind = :kind ORDER BY pk")
    fun observe(playlistId: String, kind: String): Flow<List<VodEntity>>

    @Query("SELECT * FROM vod WHERE playlistId = :playlistId AND kind = :kind ORDER BY pk")
    suspend fun byKind(playlistId: String, kind: String): List<VodEntity>

    @Query("SELECT * FROM vod WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): VodEntity?

    @Query(
        """
        SELECT v.* FROM vod v
        JOIN vod_fts fts ON v.pk = fts.rowid
        WHERE v.playlistId = :playlistId AND vod_fts MATCH :q
        LIMIT 40
        """,
    )
    suspend fun search(playlistId: String, q: String): List<VodEntity>

    @Query("SELECT * FROM vod WHERE playlistId = :playlistId AND kind = :kind AND genresCsv LIKE '%' || :genre || '%' ORDER BY title")
    suspend fun byGenre(playlistId: String, kind: String, genre: String): List<VodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<VodEntity>)

    @Update
    suspend fun update(entity: VodEntity)

    @Query("DELETE FROM vod WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: String)

    @Query("UPDATE vod SET watchlist = :flag WHERE id = :id")
    suspend fun setWatchlist(id: String, flag: Boolean)

    @Query("UPDATE vod SET localRating = :rating WHERE id = :id")
    suspend fun setRating(id: String, rating: Float)

    @Query("DELETE FROM vod WHERE playlistId = :playlistId AND id NOT IN (:keptIds)")
    suspend fun deleteOrphans(playlistId: String, keptIds: List<String>)

    @Query("DELETE FROM vod WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Transaction
    suspend fun syncVod(playlistId: String, items: List<VodEntity>) {
        val keep = items.mapTo(HashSet(items.size)) { it.id }
        val existing = byKind(playlistId, "MOVIE") + byKind(playlistId, "SERIES")
        val obsolete = existing.map { it.id }.filterNot { it in keep }
        items.chunked(SQL_BATCH_SIZE).forEach { upsertAll(it) }
        obsolete.chunked(SQL_BATCH_SIZE).forEach { deleteByIds(it) }
    }

    @Transaction
    suspend fun replacePlaylistVod(playlistId: String, items: List<VodEntity>) {
        deleteByPlaylist(playlistId)
        items.chunked(SQL_BATCH_SIZE).forEach { chunk ->
            upsertAll(chunk.map { it.copy(pk = 0) })
        }
    }

    @Query("SELECT DISTINCT genresCsv FROM vod WHERE playlistId = :playlistId AND kind = :kind")
    suspend fun genreRows(playlistId: String, kind: String): List<String>
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY season, episode")
    fun observe(seriesId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY season, episode")
    suspend fun bySeries(seriesId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteForSeries(seriesId: String)

    @Query("UPDATE episodes SET progress = :progress WHERE id = :id")
    suspend fun setProgress(id: String, progress: Float)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY startMs DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE channelId = :channelId AND programId = :programId AND status IN ('SCHEDULED', 'RECORDING') LIMIT 1")
    suspend fun activeForProgram(channelId: String, programId: String): RecordingEntity?

    @Query("SELECT * FROM recordings WHERE status = :status ORDER BY startMs")
    suspend fun byStatus(status: String): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecordingEntity)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE recordings SET status = :status, fileUri = :uri, bytes = :bytes WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, uri: String, bytes: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY atMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY atMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE kind = :kind ORDER BY atMs DESC LIMIT :limit")
    fun observeByKind(kind: String, limit: Int): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE refId = :refId LIMIT 1")
    suspend fun byRef(refId: String): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN watch_history h ON h.refId = c.id
        WHERE c.playlistId = :playlistId AND h.kind = 'LIVE'
        ORDER BY h.atMs DESC
        LIMIT 40
        """,
    )
    fun observeRecentChannels(playlistId: String): Flow<List<ChannelEntity>>
}

@Dao
interface EpgSourceDao {
    @Query("SELECT * FROM epg_sources WHERE playlistId = :playlistId")
    fun observe(playlistId: String): Flow<List<EpgSourceEntity>>

    @Query("SELECT * FROM epg_sources WHERE playlistId = :playlistId")
    suspend fun byPlaylist(playlistId: String): List<EpgSourceEntity>

    @Query("SELECT * FROM epg_sources WHERE enabled = 1")
    suspend fun enabled(): List<EpgSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EpgSourceEntity)

    @Query("DELETE FROM epg_sources WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface XmltvChannelDao {
    @Query("DELETE FROM xmltv_channels")
    suspend fun deleteAll()
    @Query("SELECT * FROM xmltv_channels")
    suspend fun listAll(): List<XmltvChannelEntity>

    @Query("SELECT * FROM xmltv_channels WHERE displayName LIKE '%' || :q || '%' LIMIT 40")
    suspend fun search(q: String): List<XmltvChannelEntity>

    @Query("SELECT * FROM xmltv_channels WHERE xmltvId = :id LIMIT 1")
    suspend fun byXmltvId(id: String): XmltvChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<XmltvChannelEntity>)

    @Query("DELETE FROM xmltv_channels WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: String)
}

@Dao
interface DiagnosticsDao {
    @Query("SELECT * FROM diagnostics WHERE id = 1")
    suspend fun get(): DiagnosticsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DiagnosticsEntity)
}

private const val SQL_BATCH_SIZE = 500
