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
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.VodKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    suspend fun importRemoteM3u(
        name: String,
        url: String,
        epgUrl: String,
        userAgent: String,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        val id = newId()
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
        }.recoverCatching { mapError(it) }
    }

    suspend fun importLocalFile(
        name: String,
        uri: String,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        val id = newId()
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
        }.recoverCatching { mapError(it) }
    }

    suspend fun importXtream(
        name: String,
        portal: String,
        username: String,
        password: String,
        onProgress: (ImportProgress) -> Unit,
    ): Result<Playlist> = withContext(Dispatchers.IO) {
        val id = newId()
        runCatching {
            onProgress(ImportProgress(ImportProgress.Stage.CONNECTING))
            val base = portal.trim().trimEnd('/')
            val api = "$base/player_api.php"
            val auth = xtreamApi.authenticate(api, username, password)
            val status = auth.userInfo?.status.orEmpty()
            val ok = auth.userInfo?.auth == 1 || status.equals("Active", true)
            if (!ok && auth.userInfo != null) {
                error("invalid credentials")
            }
            onProgress(ImportProgress(ImportProgress.Stage.DOWNLOADING, message = "live"))
            val liveCats = runCatching { xtreamApi.liveCategories(api, username, password) }.getOrDefault(emptyList())
            val vodCats = runCatching { xtreamApi.vodCategories(api, username, password) }.getOrDefault(emptyList())
            val seriesCats = runCatching { xtreamApi.seriesCategories(api, username, password) }.getOrDefault(emptyList())
            val liveMap = liveCats.associate { it.categoryId.orEmpty() to it.categoryName.orEmpty() }
            val vodMap = vodCats.associate { it.categoryId.orEmpty() to it.categoryName.orEmpty() }
            val serMap = seriesCats.associate { it.categoryId.orEmpty() to it.categoryName.orEmpty() }

            val live = runCatching { xtreamApi.liveStreams(api, username, password) }.getOrElse {
                Timber.w(it, "live streams failed, trying m3u_plus fallback")
                return@runCatching importM3uPlus(id, name, base, username, password, onProgress)
            }
            onProgress(ImportProgress(ImportProgress.Stage.PARSING, parsed = live.size))
            val vods = runCatching { xtreamApi.vodStreams(api, username, password) }.getOrDefault(emptyList())
            val series = runCatching { xtreamApi.series(api, username, password) }.getOrDefault(emptyList())

            val proto = auth.serverInfo?.server_protocol ?: "http"
            val host = auth.serverInfo?.url ?: base.removePrefix("http://").removePrefix("https://").substringBefore('/')
            val port = auth.serverInfo?.port ?: ""
            val root = if (port.isBlank()) base else "$proto://$host:$port"

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
                    xtreamStreamId = sid,
                )
            }
            val vodItems = vods.map { v ->
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
            } + series.map { s ->
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
                    userAgent = Playlist.DEFAULT_UA,
                ),
                channels,
                vodItems,
                onProgress,
                password,
            )
        }.recoverCatching { mapError(it) }
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
            PlaylistType.DEMO -> Result.success(playlist)
            PlaylistType.M3U -> importRemoteM3u(playlist.name, playlist.url, playlist.epgUrl, playlist.userAgent, onProgress)
                .map { it.copy(id = playlist.id) }
                .onSuccess { saved ->
                    // Re-import used a new id; for refresh we rewrite under existing id.
                }
            PlaylistType.FILE -> importLocalFile(playlist.name, playlist.url, onProgress)
            PlaylistType.XTREAM -> {
                val pass = repo.resolvedPassword(playlist)
                importXtream(playlist.name, playlist.url, playlist.username, pass, onProgress)
            }
        }.mapCatching { imported ->
            // Keep original id so favorites survive. saveImported already merges by streamUrl+name
            // but import* generated a new id. Re-save under old id.
            if (imported.id != playlist.id) {
                val ch = repo.snapshotChannels(imported.id).map { it.copy(playlistId = playlist.id, id = it.id.replace(imported.id, playlist.id)) }
                val movies = emptyList<VodItem>()
                repo.deletePlaylist(imported.id)
                repo.saveImported(playlist.copy(lastUpdate = System.currentTimeMillis()), ch, movies, plaintextPassword = "")
                playlist
            } else imported
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
        if (playlist.epgUrl.isNotBlank() && settings.settings.value.updateEpgOnPlaylistChange) {
            epg.ingestUrl(playlist.id, playlist.epgUrl, settings.settings.value.epgTimeShiftHours)
        }
        onProgress(ImportProgress(ImportProgress.Stage.DONE, parsed = unique.size, total = unique.size))
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
}

class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)
