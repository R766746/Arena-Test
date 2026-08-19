package com.nova.iptv.data.backup

import android.content.Context
import android.net.Uri
import com.nova.iptv.data.local.PasswordVault
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.data.playlist.PlaylistImporter
import com.nova.iptv.domain.model.AppSettings
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class BackupPayload(
    val version: Int = 2,
    val playlists: List<BackupPlaylist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val watchlist: List<String> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class BackupPlaylist(
    val id: String,
    val name: String,
    val type: String,
    val url: String,
    val username: String,
    val password: String?,
    val epgUrl: String,
    val userAgent: String,
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlists: PlaylistRepository,
    private val settings: SettingsRepository,
    private val vault: PasswordVault,
    private val importer: PlaylistImporter,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter(BackupPayload::class.java)

    suspend fun export(uri: Uri, omitPasswords: Boolean): Result<Unit> = runCatching {
        val pls = playlists.playlists().first().map { p ->
            BackupPlaylist(
                id = p.id,
                name = p.name,
                type = p.type.name,
                url = p.url,
                username = p.username,
                password = if (omitPasswords) null else vault.getPassword(p.id),
                epgUrl = p.epgUrl,
                userAgent = p.userAgent,
            )
        }
        val favs = pls.flatMap { playlists.favorites(it.id).first().map { c -> c.id } }
        val watchlist = pls.flatMap { playlists.watchlistIds(it.id) }
        val s = settings.settings.value
        val payload = BackupPayload(
            playlists = pls,
            favorites = favs,
            watchlist = watchlist,
            settings = mapOf(
                "theme" to s.theme.name,
                "accent" to s.accentArgb.toString(),
                "clock24h" to s.clock24h.toString(),
                "startup" to s.startupMode.name,
                "listStyle" to s.listStyle.name,
            ),
        )
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(adapter.toJson(payload).toByteArray())
        } ?: error("cannot write")
    }.onFailure { Timber.e(it, "backup export") }

    suspend fun restore(uri: Uri): Result<Unit> = runCatching {
        val json = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("cannot read")
        val payload = adapter.fromJson(json) ?: error("bad backup")
        payload.playlists.forEach { bp ->
            val pl = com.nova.iptv.domain.model.Playlist(
                id = bp.id,
                name = bp.name,
                type = runCatching { com.nova.iptv.domain.model.PlaylistType.valueOf(bp.type) }
                    .getOrDefault(com.nova.iptv.domain.model.PlaylistType.M3U),
                url = bp.url,
                username = bp.username,
                epgUrl = bp.epgUrl,
                userAgent = bp.userAgent,
            )
            playlists.upsertPlaylist(pl)
            if (!bp.password.isNullOrBlank()) vault.putPassword(bp.id, bp.password)
            importer.refresh(pl).getOrThrow()
        }
        payload.favorites.forEach { id ->
            val channel = playlists.getChannel(id)
            if (channel != null && !channel.favorite) playlists.toggleFavorite(id)
        }
        payload.watchlist.forEach { id ->
            if (playlists.getVod(id) != null) playlists.setWatchlist(id, true)
        }
        settings.update { current ->
            current.copy(
                theme = payload.settings["theme"]?.let {
                    runCatching { com.nova.iptv.domain.model.ThemeName.valueOf(it) }.getOrNull()
                } ?: current.theme,
                accentArgb = payload.settings["accent"]?.toLongOrNull() ?: current.accentArgb,
                clock24h = payload.settings["clock24h"]?.toBooleanStrictOrNull() ?: current.clock24h,
                startupMode = payload.settings["startup"]?.let {
                    runCatching { com.nova.iptv.domain.model.StartupMode.valueOf(it) }.getOrNull()
                } ?: current.startupMode,
                listStyle = payload.settings["listStyle"]?.let {
                    runCatching { com.nova.iptv.domain.model.ListStyle.valueOf(it) }.getOrNull()
                } ?: current.listStyle,
            )
        }
    }.onFailure { Timber.e(it, "backup restore") }
}
