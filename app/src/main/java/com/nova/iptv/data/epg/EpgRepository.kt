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
    suspend fun ingestDemo(playlistId: String): Int
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
    private val demoEpg: DemoEpg,
) : EpgRepository {

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
        val started = System.currentTimeMillis()
        runCatching {
            if (url.startsWith("demo://")) {
                return@runCatching ingestDemo(playlistId)
            }
            val req = Request.Builder().url(url).header("User-Agent", Playlist.DEFAULT_UA).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("EPG HTTP ${resp.code}")
                val body = resp.body ?: error("empty EPG body")
                val parsed = XmltvParser.parse(
                    body.byteStream(),
                    timeShiftHours,
                    url,
                ) { /* yield every 500 inside parser */ }
                val sourceId = sourceName.ifBlank { url.hashCode().toString() }
                db.xmltvChannels().deleteBySource(sourceId)
                parsed.channels.chunked(500).forEach { chunk ->
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
                    yield()
                }
                val channels = db.channels().byPlaylist(playlistId)
                val idMap = HashMap<String, String>()
                channels.forEach { ch ->
                    if (ch.epgId.isNotBlank()) idMap[ch.epgId.lowercase()] = ch.id
                }
                val tokens = settings.settings.value.nameStripTokens
                val xml = parsed.channels
                val matches = matcher.match(channels.map { it.toModel() }, xml, tokens)
                matches.forEach { m ->
                    idMap[m.xmltvId.lowercase()] = m.channelId
                    if (m.method != MatchResult.Method.EXACT_ID) {
                        db.channels().setEpgId(m.channelId, m.xmltvId)
                    }
                }
                var inserted = 0
                parsed.programmes.chunked(500).forEach { chunk ->
                    val rows = chunk.mapNotNull { r ->
                        val chId = idMap[r.channelId.lowercase()] ?: return@mapNotNull null
                        ProgramEntity(
                            id = "xmltv:${r.channelId}:${r.startMs}",
                            channelId = chId,
                            title = r.title,
                            description = r.description,
                            category = r.category,
                            startMs = r.startMs,
                            endMs = r.endMs,
                            catchup = r.endMs < System.currentTimeMillis(),
                        )
                    }
                    if (rows.isNotEmpty()) db.programs().upsertAll(rows)
                    inserted += rows.size
                    yield()
                }
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

    override suspend fun ingestDemo(playlistId: String): Int {
        val channels = db.channels().byPlaylist(playlistId).map { it.toModel() }
        val programs = demoEpg.generate(channels)
        programs.chunked(500).forEach { chunk ->
            db.programs().upsertAll(chunk.map { ProgramEntity.from(it) })
        }
        return programs.size
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
        val n = db.programs().pruneBefore(cutoff)
        // Best-effort vacuum: Room has no official vacuum API; ignored on failure.
        runCatching {
            db.openHelper.writableDatabase.execSQL("VACUUM")
        }
        return n
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
    private val demo = DemoEpg()

    fun seed(channels: List<com.nova.iptv.domain.model.Channel>) {
        programs.value = demo.generate(channels)
    }

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

    override suspend fun ingestDemo(playlistId: String) = programs.value.size
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
