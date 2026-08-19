package com.nova.iptv.data.playlist

import android.content.Context
import android.net.Uri
import com.nova.iptv.core.util.colorFromName
import com.nova.iptv.core.util.logoInitials
import com.nova.iptv.core.util.newId
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.remote.XtreamApi
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.Episode
import com.nova.iptv.domain.model.ImportProgress
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.domain.model.Program
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.VodKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.source
import timber.log.Timber
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

@Singleton
class PlaylistImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val xtreamApi: XtreamApi,
    private val repo: PlaylistRepository,
    private val epg: EpgRepository,
    private val settings: SettingsRepository,
) {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _syncProgress = MutableStateFlow<ImportProgress?>(null)
    val syncProgress: StateFlow<ImportProgress?> = _syncProgress.asStateFlow()

    private fun tracked(callback: (ImportProgress) -> Unit): (ImportProgress) -> Unit = { progress ->
        _syncProgress.value = progress
        callback(progress)
    }

    suspend fun importRemoteM3u(
        name: String,
        url: String,
        epgUrl: String,
        userAgent: String,
        existingId: String? = null,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        val onProgress = tracked(onProgress)
        val id = existingId ?: newId()
        runCatching {
            onProgress(ImportProgress(ImportProgress.Stage.CONNECTING))
            val ua = userAgent.ifBlank { Playlist.DEFAULT_UA }
            val req = Request.Builder().url(url).header("User-Agent", ua).build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) error("401")
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("empty")
                var downloaded = 0
                val parsed = body.source().use { src ->
                    onProgress(ImportProgress(ImportProgress.Stage.DOWNLOADING, downloadedKb = 0))
                    M3uParser.parse(src, id) { n ->
                        downloaded = n
                        onProgress(ImportProgress(ImportProgress.Stage.PARSING, parsed = n, total = 0))
                    }
                }
                persist(
                    Playlist(
                        id = id,
                        name = name.ifBlank { hostOf(url) },
                        type = PlaylistType.M3U,
                        url = url,
                        epgUrl = epgUrl.ifBlank { parsed.headerEpgUrl },
                        userAgent = parsed.headerUserAgent.ifBlank { ua },
                    ),
                    parsed.channels,
                    parsed.vod,
                    onProgress,
                )
            }
        }.recoverCatching {
            onProgress(ImportProgress(ImportProgress.Stage.ERROR, message = it.message.orEmpty()))
            mapError(it)
        }
    }

    suspend fun importLocalFile(
        name: String,
        uri: String,
        existingId: String? = null,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        val onProgress = tracked(onProgress)
        val id = existingId ?: newId()
        runCatching {
            onProgress(ImportProgress(ImportProgress.Stage.CONNECTING))
            val parsedUri = Uri.parse(uri)
            context.contentResolver.openInputStream(parsedUri)?.source()?.buffer().use { src ->
                if (src == null) error("Could not open file")
                onProgress(ImportProgress(ImportProgress.Stage.PARSING))
                val parsed = M3uParser.parse(src, id) { n ->
                    onProgress(ImportProgress(ImportProgress.Stage.PARSING, parsed = n))
                }
                persist(
                    Playlist(
                        id = id,
                        name = name.ifBlank { parsedUri.lastPathSegment ?: "File playlist" },
                        type = PlaylistType.FILE,
                        url = uri,
                        epgUrl = parsed.headerEpgUrl,
                        userAgent = parsed.headerUserAgent.ifBlank { Playlist.DEFAULT_UA },
                    ),
                    parsed.channels,
                    parsed.vod,
                    onProgress,
                )
            } ?: error("Could not open file")
        }.recoverCatching {
            onProgress(ImportProgress(ImportProgress.Stage.ERROR, message = it.message.orEmpty()))
            mapError(it)
        }
    }

    suspend fun importXtream(
        name: String,
        portal: String,
        username: String,
        password: String,
        existingId: String? = null,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        val onProgress = tracked(onProgress)
        val normalizedPortal = portal.trim().trimEnd('/')
        val matching = if (existingId == null) {
            repo.playlists().first().filter {
                it.type == PlaylistType.XTREAM &&
                    it.url.trim().trimEnd('/').equals(normalizedPortal, ignoreCase = true) &&
                    it.username == username
            }
        } else emptyList()
        val id = existingId ?: matching.firstOrNull()?.id ?: newId()
        runCatching {
            onProgress(ImportProgress(ImportProgress.Stage.CONNECTING))
            val base = normalizedPortal
            val api = "$base/player_api.php"
            val auth = xtreamApi.authenticate(api, username, password)
            val status = auth.userInfo?.status.orEmpty()
            val ok = auth.userInfo?.auth == 1 || status.equals("Active", true)
            if (!ok && auth.userInfo != null) {
                error("invalid credentials")
            }
            onProgress(ImportProgress(ImportProgress.Stage.DOWNLOADING, message = "live"))
            val (categoryResults, streamResults) = coroutineScope {
                val liveCats = async { runCatching { xtreamApi.liveCategories(api, username, password) } }
                val vodCats = async { runCatching { xtreamApi.vodCategories(api, username, password) } }
                val seriesCats = async { runCatching { xtreamApi.seriesCategories(api, username, password) } }
                val live = async { runCatching { xtreamApi.liveStreams(api, username, password) } }
                val vod = async { runCatching { xtreamApi.vodStreams(api, username, password) } }
                val series = async { runCatching { xtreamApi.series(api, username, password) } }
                Triple(liveCats.await(), vodCats.await(), seriesCats.await()) to
                    Triple(live.await(), vod.await(), series.await())
            }
            val liveCats = categoryResults.first.getOrDefault(emptyList())
            val vodCats = categoryResults.second.getOrDefault(emptyList())
            val seriesCats = categoryResults.third.getOrDefault(emptyList())
            val liveMap = liveCats.associate { it.categoryId.orEmpty() to it.categoryName.orEmpty() }
            val vodMap = vodCats.associate { it.categoryId.orEmpty() to it.categoryName.orEmpty() }
            val serMap = seriesCats.associate { it.categoryId.orEmpty() to it.categoryName.orEmpty() }
            val liveOrder = liveCats.mapIndexed { index, category -> category.categoryId.orEmpty() to index }.toMap()
            val vodOrder = vodCats.mapIndexed { index, category -> category.categoryId.orEmpty() to index }.toMap()
            val seriesOrder = seriesCats.mapIndexed { index, category -> category.categoryId.orEmpty() to index }.toMap()

            val live = streamResults.first.getOrElse {
                Timber.w(it, "live streams failed, trying m3u_plus fallback")
                return@runCatching importM3uPlus(id, name, base, username, password, onProgress)
            }
            onProgress(ImportProgress(ImportProgress.Stage.PARSING, parsed = live.size))
            val vods = streamResults.second.getOrDefault(emptyList())
            val series = streamResults.third.getOrDefault(emptyList())

            val proto = auth.serverInfo?.server_protocol?.takeIf { it.isNotBlank() }
                ?: Uri.parse(base).scheme.orEmpty().ifBlank { "http" }
            val serverUrl = auth.serverInfo?.url.orEmpty().trim().trimEnd('/')
            val port = auth.serverInfo?.port ?: ""
            val serverUri = Uri.parse(
                if (serverUrl.startsWith("http://", true) || serverUrl.startsWith("https://", true)) serverUrl
                else "$proto://$serverUrl",
            )
            val host = serverUri.host.orEmpty().ifBlank { Uri.parse(base).host.orEmpty() }
            val root = when {
                host.isBlank() -> base
                port.isNotBlank() -> "$proto://$host:$port"
                serverUrl.isNotBlank() -> "$proto://$host"
                else -> base
            }

            val channels = live.mapIndexed { idx, s ->
                val sid = s.streamId?.toString().orEmpty()
                val title = s.name.orEmpty().ifBlank { "Channel ${s.num}" }
                Channel(
                    id = "$id:live:$sid",
                    playlistId = id,
                    number = s.num ?: (idx + 1),
                    name = title,
                    groupName = liveMap[s.categoryId.orEmpty()].orEmpty().ifBlank { "Live" },
                    logoUrl = s.streamIcon.orEmpty(),
                    logoText = logoInitials(title),
                    logoColor = colorFromName(title),
                    streamUrl = "$root/live/$username/$password/$sid.ts",
                    epgId = s.epgChannelId.orEmpty(),
                    catchup = (s.tvArchive ?: 0) > 0,
                    catchupDays = s.tvArchiveDuration ?: 0,
                    userOrder = (liveOrder[s.categoryId.orEmpty()] ?: liveCats.size) * 1_000_000 + idx,
                    xtreamStreamId = sid,
                )
            }
            val vodItems = vods.sortedBy { vodOrder[it.categoryId.orEmpty()] ?: vodCats.size }.map { v ->
                val sid = v.streamId?.toString().orEmpty()
                val title = v.name.orEmpty()
                val ext = v.container?.ifBlank { "mp4" } ?: "mp4"
                VodItem(
                    id = "$id:vod:$sid",
                    playlistId = id,
                    kind = VodKind.MOVIE,
                    title = title,
                    year = v.year?.take(4)?.toIntOrNull() ?: 0,
                    rating = v.rating.orEmpty(),
                    durationMin = parseMinutes(v.duration),
                    genres = vodMap[v.categoryId.orEmpty()]?.let { listOf(it) }.orEmpty(),
                    posterUrl = v.streamIcon.orEmpty(),
                    description = v.plot.orEmpty(),
                    director = v.director.orEmpty(),
                    streamUrl = "$root/movie/$username/$password/$sid.$ext",
                    xtreamId = sid,
                )
            } + series.sortedBy { seriesOrder[it.categoryId.orEmpty()] ?: seriesCats.size }.map { s ->
                val sid = s.seriesId?.toString().orEmpty()
                VodItem(
                    id = "$id:series:$sid",
                    playlistId = id,
                    kind = VodKind.SERIES,
                    title = s.name.orEmpty(),
                    year = s.year?.take(4)?.toIntOrNull() ?: 0,
                    rating = s.rating.orEmpty(),
                    genres = serMap[s.categoryId.orEmpty()]?.let { listOf(it) }.orEmpty(),
                    posterUrl = s.cover.orEmpty(),
                    backdropUrl = s.backdrop_path?.firstOrNull().orEmpty(),
                    description = s.plot.orEmpty(),
                    cast = s.cast.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    director = s.director.orEmpty(),
                    xtreamId = sid,
                )
            }
            persist(
                Playlist(
                    id = id,
                    name = name.ifBlank { username },
                    type = PlaylistType.XTREAM,
                    url = base,
                    username = username,
                    passwordEnc = "",
                    // Xtream-compatible servers normally expose the complete
                    // guide in one request. Persisting it also lets WorkManager
                    // refresh EPG later without issuing a request per channel.
                    epgUrl = "$base/xmltv.php?username=${Uri.encode(username.trim())}&password=${Uri.encode(password.trim())}",
                    userAgent = Playlist.DEFAULT_UA,
                ),
                channels,
                vodItems,
                onProgress,
                password,
            ).also {
                matching.drop(1).forEach { duplicate -> repo.deletePlaylist(duplicate.id) }
            }
        }.recoverCatching {
            onProgress(ImportProgress(ImportProgress.Stage.ERROR, message = it.message.orEmpty()))
            mapError(it)
        }
    }

    private suspend fun importM3uPlus(
        id: String,
        name: String,
        base: String,
        user: String,
        pass: String,
        onProgress: (ImportProgress) -> Unit,
    ): Playlist {
        val url = "$base/get.php?username=$user&password=$pass&type=m3u_plus&output=ts"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 401) error("401")
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val parsed = M3uParser.parse(resp.body!!.source(), id) { n ->
                onProgress(ImportProgress(ImportProgress.Stage.PARSING, parsed = n))
            }
            return persist(
                Playlist(id = id, name = name, type = PlaylistType.XTREAM, url = base, username = user),
                parsed.channels,
                parsed.vod,
                onProgress,
                pass,
            )
        }
    }

    suspend fun refresh(playlist: Playlist, onProgress: (ImportProgress) -> Unit = {}): Result<Playlist> {
        return when (playlist.type) {
            PlaylistType.M3U -> importRemoteM3u(
                playlist.name, playlist.url, playlist.epgUrl, playlist.userAgent,
                existingId = playlist.id, onProgress = onProgress,
            )
            PlaylistType.FILE -> importLocalFile(
                playlist.name, playlist.url, existingId = playlist.id, onProgress = onProgress,
            )
            PlaylistType.XTREAM -> {
                val pass = repo.resolvedPassword(playlist)
                importXtream(
                    playlist.name, playlist.url, playlist.username, pass,
                    existingId = playlist.id, onProgress = onProgress,
                )
            }
        }
    }

    private suspend fun persist(
        playlist: Playlist,
        channels: List<Channel>,
        vod: List<VodItem>,
        onProgress: (ImportProgress) -> Unit,
        password: String = "",
        episodes: List<Episode> = emptyList(),
    ): Playlist {
        if (channels.isEmpty() && vod.isEmpty()) error("empty playlist")
        onProgress(ImportProgress(ImportProgress.Stage.SAVING, parsed = channels.size, total = channels.size))
        val unique = channels.distinctBy { it.streamUrl + "|" + it.name }
        repo.saveImported(playlist, unique, vod, episodes, password)
        onProgress(ImportProgress(ImportProgress.Stage.DONE, parsed = unique.size, total = unique.size))
        // The saved catalog is usable immediately. XMLTV parsing can be much
        // slower than the channel import and must not hold the UI on "Saving".
        if (playlist.epgUrl.isNotBlank() && settings.settings.value.updateEpgOnPlaylistChange) {
            val shiftHours = settings.settings.value.epgTimeShiftHours
            backgroundScope.launch {
                val xmltv = epg.ingestUrl(playlist.id, playlist.epgUrl, shiftHours)
                xmltv.onFailure { failure ->
                    Timber.w(failure, "XMLTV background ingest failed")
                    if (playlist.type == PlaylistType.XTREAM && password.isNotBlank()) {
                        val api = "${playlist.url.trimEnd('/')}/player_api.php"
                        runCatching { ingestXtreamEpg(api, playlist.username, password, unique) }
                            .onFailure { Timber.w(it, "Xtream short EPG fallback failed") }
                    }
                }
            }
        }
        return playlist
    }

    private fun mapError(t: Throwable): Playlist {
        Timber.e(t, "import failed")
        val msg = when (t) {
            is UnknownHostException -> "unknown_host"
            is SSLException -> "ssl"
            else -> t.message.orEmpty()
        }
        throw ImportException(msg, t)
    }

    private fun hostOf(url: String): String = runCatching { Uri.parse(url).host ?: url }.getOrDefault(url)

    private fun parseMinutes(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val parts = raw.split(':')
        return when (parts.size) {
            3 -> parts[0].toIntOrNull()?.times(60)?.plus(parts[1].toIntOrNull() ?: 0) ?: 0
            2 -> parts[0].toIntOrNull() ?: 0
            else -> raw.filter { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    private suspend fun ingestXtreamEpg(
        api: String,
        username: String,
        password: String,
        channels: List<Channel>,
    ) = coroutineScope {
        val permits = Semaphore(8)
        val programs = channels.map { channel ->
            async(Dispatchers.IO) {
                permits.withPermit {
                    val sid = channel.xtreamStreamId.takeIf { it.isNotBlank() } ?: return@withPermit emptyList()
                    val response = runCatching { xtreamApi.simpleEpg(api, username, password, streamId = sid) }
                        .getOrElse {
                            runCatching { xtreamApi.shortEpg(api, username, password, streamId = sid) }.getOrNull()
                                ?: return@withPermit emptyList()
                        }
                    response.listings.orEmpty().mapNotNull { listing ->
                        val start = listing.startTs?.toLongOrNull()?.times(1000L) ?: return@mapNotNull null
                        val end = listing.stopTs?.toLongOrNull()?.times(1000L) ?: return@mapNotNull null
                        if (end <= start) return@mapNotNull null
                        Program(
                            id = "xtream:${channel.id}:$start",
                            channelId = channel.id,
                            title = decodeXtreamText(listing.title).ifBlank { "Programme" },
                            description = decodeXtreamText(listing.description),
                            startMs = start,
                            endMs = end,
                            catchup = channel.catchup && end < System.currentTimeMillis(),
                        )
                    }
                }
            }
        }.awaitAll().flatten()
        if (programs.isNotEmpty()) epg.ingestPrograms(programs)
    }

    private fun decodeXtreamText(value: String?): String {
        val raw = value.orEmpty()
        if (raw.isBlank()) return ""
        return runCatching {
            val decoded = android.util.Base64.decode(raw, android.util.Base64.DEFAULT).decodeToString()
            decoded.takeIf { it.all { c -> !c.isISOControl() || c == '\n' || c == '\r' || c == '\t' } } ?: raw
        }.getOrDefault(raw)
    }
}

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
