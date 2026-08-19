package com.nova.iptv.data.epg

import com.nova.iptv.core.util.newId
import com.nova.iptv.data.local.NovaDatabase
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.local.entity.DiagnosticsEntity
import com.nova.iptv.data.local.entity.EpgSourceEntity
import com.nova.iptv.data.local.entity.ProgramEntity
import com.nova.iptv.data.local.entity.XmltvChannelEntity
import com.nova.iptv.domain.model.EpgSource
import com.nova.iptv.domain.model.NowNext
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.Program
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface EpgRepository {
    suspend fun nowAndNext(channelId: String, nowMs: Long = System.currentTimeMillis()): NowNext
    suspend fun programsFor(channelId: String, fromMs: Long, toMs: Long): List<Program>
    suspend fun searchTitles(query: String, fromMs: Long, toMs: Long): List<Program>
    suspend fun ingestUrl(playlistId: String, url: String, timeShiftHours: Int, sourceName: String = ""): Result<Int>
    suspend fun ingestPrograms(programs: List<Program>): Int
    suspend fun autoMatch(playlistId: String): Int
    suspend fun assignEpg(channelId: String, xmltvId: String)
    suspend fun searchXmltvNames(query: String): List<Pair<String, String>>
    fun sources(playlistId: String): Flow<List<EpgSource>>
    suspend fun upsertSource(source: EpgSource)
    suspend fun deleteSource(id: String)
    suspend fun prune(pastDays: Int): Int
    suspend fun lastIngestDurationMs(): Long
    suspend fun programById(id: String): Program?
    suspend fun clearCache()
}

@Singleton
class EpgRepositoryImpl @Inject constructor(
    private val db: NovaDatabase,
    private val client: OkHttpClient,
    private val matcher: EpgMatcher,
    private val settings: SettingsRepository,
) : EpgRepository {
    private val ingestMutex = Mutex()

    override suspend fun nowAndNext(channelId: String, nowMs: Long): NowNext {
        val now = db.programs().now(channelId, nowMs)?.toModel()
        val next = db.programs().next(channelId, nowMs)?.toModel()
        return NowNext(now, next)
    }

    override suspend fun programsFor(channelId: String, fromMs: Long, toMs: Long): List<Program> =
        db.programs().overlapping(channelId, fromMs, toMs).map { it.toModel() }

    override suspend fun searchTitles(query: String, fromMs: Long, toMs: Long): List<Program> {
        val q = query.trim().replace("\"", "") + "*"
        return db.programs().searchTitles(q, fromMs, toMs).map { it.toModel() }
    }

    override suspend fun ingestUrl(
        playlistId: String,
        url: String,
        timeShiftHours: Int,
        sourceName: String,
    ): Result<Int> = withContext(Dispatchers.IO) {
        ingestMutex.withLock {
            val started = System.currentTimeMillis()
            runCatching {
            val req = Request.Builder().url(url).header("User-Agent", Playlist.DEFAULT_UA).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("EPG HTTP ${resp.code}")
                val body = resp.body ?: error("empty EPG body")
                // A display-name rename must not create a second logical feed.
                val sourceId = "$playlistId\u0000$url".hashCode().toString()
                val syncToken = newId()
                val channels = db.channels().byPlaylist(playlistId)
                val idMap = HashMap<String, String>()
                channels.forEach { ch ->
                    if (ch.epgId.isNotBlank()) idMap[ch.epgId.lowercase()] = ch.id
                }
                var inserted = 0
                XmltvParser.parse(
                    input = body.byteStream(),
                    timeShiftHours = timeShiftHours,
                    urlHint = url,
                    collectProgrammes = false,
                    onChannelsReady = { xml ->
                        runBlocking {
                            db.xmltvChannels().deleteBySource(sourceId)
                            xml.chunked(500).forEach { chunk ->
                                db.xmltvChannels().upsertAll(
                                    chunk.flatMap { xc ->
                                        xc.displayNames.ifEmpty { listOf(xc.id) }.map { name ->
                                            XmltvChannelEntity(
                                                xmltvId = xc.id,
                                                displayName = name,
                                                iconUrl = xc.icon,
                                                sourceId = sourceId,
                                            )
                                        }
                                    },
                                )
                            }
                            val matches = matcher.match(
                                channels.map { it.toModel() },
                                xml,
                                settings.settings.value.nameStripTokens,
                            )
                            matches.forEach { match ->
                                idMap[match.xmltvId.lowercase()] = match.channelId
                                if (match.method != MatchResult.Method.EXACT_ID) {
                                    db.channels().setEpgId(match.channelId, match.xmltvId)
                                }
                            }
                        }
                    },
                    onProgrammeBatch = { batch ->
                        runBlocking {
                            val rows = batch.mapNotNull { programme ->
                                val channelId = idMap[programme.channelId.lowercase()] ?: return@mapNotNull null
                                ProgramEntity(
                                    id = "xmltv:$sourceId:${programme.channelId}:${programme.startMs}",
                                    channelId = channelId,
                                    title = programme.title,
                                    description = programme.description,
                                    category = programme.category,
                                    startMs = programme.startMs,
                                    endMs = programme.endMs,
                                    catchup = programme.endMs < System.currentTimeMillis(),
                                    sourceId = sourceId,
                                    syncToken = syncToken,
                                )
                            }
                            if (rows.isNotEmpty()) db.programs().upsertAll(rows)
                            inserted += rows.size
                        }
                    },
                )
                // Only a fully parsed feed may retire its previous future rows.
                // Failed imports never reach this point, preserving the last cache.
                db.programs().deleteStaleFutureForSource(
                    sourceId = sourceId,
                    syncToken = syncToken,
                    fromMs = System.currentTimeMillis(),
                )
                val past = settings.settings.value.epgPastDays
                prune(past)
                db.diagnostics().upsert(
                    DiagnosticsEntity(
                        lastEpgDurationMs = System.currentTimeMillis() - started,
                        lastEpgAt = System.currentTimeMillis(),
                        lastPlaylistSize = channels.size,
                    ),
                )
                inserted
            }
            }.onFailure { Timber.e(it, "EPG ingest failed for %s", url) }
        }
    }

    override suspend fun ingestPrograms(programs: List<Program>): Int = withContext(Dispatchers.IO) {
        programs.chunked(500).forEach { chunk ->
            db.programs().upsertAll(chunk.map { ProgramEntity.from(it) })
            yield()
        }
        programs.size
    }

    override suspend fun autoMatch(playlistId: String): Int {
        val channels = db.channels().byPlaylist(playlistId).map { it.toModel() }
        val all = db.xmltvChannels().listAll()
            .groupBy { it.xmltvId }
            .map { (id, rows) -> XmltvChannel(id, rows.map { it.displayName }, rows.first().iconUrl) }
        val matches = matcher.match(channels, all, settings.settings.value.nameStripTokens)
        matches.forEach { db.channels().setEpgId(it.channelId, it.xmltvId) }
        return matches.size
    }

    override suspend fun assignEpg(channelId: String, xmltvId: String) {
        db.channels().setEpgId(channelId, xmltvId)
    }

    override suspend fun searchXmltvNames(query: String): List<Pair<String, String>> =
        db.xmltvChannels().search(query).map { it.xmltvId to it.displayName }

    override fun sources(playlistId: String): Flow<List<EpgSource>> =
        db.epgSources().observe(playlistId).map { list ->
            list.map { EpgSource(it.id, it.playlistId, it.url, it.enabled, it.timeShiftHours, it.name) }
        }

    override suspend fun upsertSource(source: EpgSource) {
        db.epgSources().upsert(
            EpgSourceEntity(
                id = source.id.ifBlank { newId() },
                playlistId = source.playlistId,
                url = source.url,
                enabled = source.enabled,
                timeShiftHours = source.timeShiftHours,
                name = source.name,
            ),
        )
    }

    override suspend fun deleteSource(id: String) = db.epgSources().delete(id)

    override suspend fun prune(pastDays: Int): Int {
        val cutoff = System.currentTimeMillis() - pastDays * 24L * 3600_000L
        // DELETE is incremental and keeps Room responsive. VACUUM rewrites and
        // exclusively locks the entire (often 100+ MB) guide database, which can
        // freeze Live TV and Guide navigation during every background refresh.
        return db.programs().pruneBefore(cutoff)
    }

    override suspend fun lastIngestDurationMs(): Long = db.diagnostics().get()?.lastEpgDurationMs ?: 0L

    override suspend fun programById(id: String): Program? = db.programs().byId(id)?.toModel()

    override suspend fun clearCache() {
        db.programs().deleteAll()
        db.xmltvChannels().deleteAll()
    }
}

class FakeEpgRepository : EpgRepository {
    private val programs = MutableStateFlow<List<Program>>(emptyList())
    private val src = MutableStateFlow<List<EpgSource>>(emptyList())
    override suspend fun nowAndNext(channelId: String, nowMs: Long): NowNext {
        val list = programs.value.filter { it.channelId == channelId }.sortedBy { it.startMs }
        return NowNext(list.firstOrNull { it.isNow(nowMs) }, list.firstOrNull { it.startMs > nowMs })
    }

    override suspend fun programsFor(channelId: String, fromMs: Long, toMs: Long) =
        programs.value.filter { it.channelId == channelId && it.startMs < toMs && it.endMs > fromMs }

    override suspend fun searchTitles(query: String, fromMs: Long, toMs: Long) =
        programs.value.filter { it.title.contains(query, true) }

    override suspend fun ingestUrl(playlistId: String, url: String, timeShiftHours: Int, sourceName: String) =
        Result.success(programs.value.size)

    override suspend fun ingestPrograms(programs: List<Program>): Int {
        this.programs.value = (this.programs.value + programs).distinctBy { it.id }
        return programs.size
    }
    override suspend fun autoMatch(playlistId: String) = 0
    override suspend fun assignEpg(channelId: String, xmltvId: String) = Unit
    override suspend fun searchXmltvNames(query: String) = emptyList<Pair<String, String>>()
    override fun sources(playlistId: String) = src
    override suspend fun upsertSource(source: EpgSource) {
        src.value = src.value.filterNot { it.id == source.id } + source
    }
    override suspend fun deleteSource(id: String) {
        src.value = src.value.filterNot { it.id == id }
    }
    override suspend fun prune(pastDays: Int) = 0
    override suspend fun lastIngestDurationMs() = 0L
    override suspend fun programById(id: String) = programs.value.firstOrNull { it.id == id }
    override suspend fun clearCache() { programs.value = emptyList() }
}
