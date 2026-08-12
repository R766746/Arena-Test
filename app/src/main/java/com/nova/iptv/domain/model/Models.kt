package com.nova.iptv.domain.model

/**
 * Domain models for NovaIptv. These are UI/use-case facing copies of Room rows.
 */
enum class PlaylistType { DEMO, M3U, XTREAM, FILE }

enum class VodKind { MOVIE, SERIES }

enum class RecordingStatus { SCHEDULED, RECORDING, COMPLETED, FAILED }

enum class WatchKind { LIVE, MOVIE, SERIES, CATCHUP, RECORDING }

enum class ListStyle { LIST, COMPACT, LOGOS }

enum class ThemeName { MIDNIGHT, DARK, CINEMA, LIGHT }

enum class AnimSpeed { OFF, NORMAL, FAST }

enum class BufferSize { SMALL, MEDIUM, LARGE }

enum class AspectMode { FIT, RATIO_16_9, RATIO_4_3, FILL }

enum class StartupMode { HOME, LAST_CHANNEL, FAVORITES, GUIDE }

enum class EpgRowHeight { COMPACT, NORMAL, TALL }

enum class HomeCategory { LIVE, MOVIES, SERIES, GUIDE, RECORDINGS, MULTIVIEW, SEARCH, SETTINGS }

data class Playlist(
    val id: String,
    val name: String,
    val type: PlaylistType,
    val url: String = "",
    val username: String = "",
    val passwordEnc: String = "",
    val epgUrl: String = "",
    val userAgent: String = DEFAULT_UA,
    val referrer: String = "",
    val lastUpdate: Long = 0L,
    val autoUpdate: Boolean = true,
    val updateIntervalHours: Int = 12,
    val portal: String = "",
) {
    companion object {
        const val DEMO_ID = "demo"
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 12; SHIELD Android TV) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
    }
}

data class Channel(
    val id: String,
    val playlistId: String,
    val number: Int,
    val name: String,
    val groupName: String,
    val logoUrl: String = "",
    val logoText: String = "",
    val logoColor: Int = 0xFF3D8BFD.toInt(),
    val streamUrl: String,
    val epgId: String = "",
    val catchup: Boolean = false,
    val catchupDays: Int = 0,
    val catchupSource: String = "",
    val timeshift: String = "",
    val userAgent: String = "",
    val referrer: String = "",
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val locked: Boolean = false,
    val userOrder: Int = 0,
    val xtreamStreamId: String = "",
)

data class Program(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String = "",
    val category: String = "",
    val startMs: Long,
    val endMs: Long,
    val catchup: Boolean = false,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    fun progress(now: Long): Float {
        if (durationMs <= 0L) return 0f
        return ((now - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
    }
    fun isNow(now: Long): Boolean = now in startMs until endMs
    fun isPast(now: Long): Boolean = endMs <= now
}

data class NowNext(
    val now: Program?,
    val next: Program?,
)

data class VodItem(
    val id: String,
    val playlistId: String,
    val kind: VodKind,
    val title: String,
    val year: Int = 0,
    val rating: String = "",
    val durationMin: Int = 0,
    val genres: List<String> = emptyList(),
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val description: String = "",
    val cast: List<String> = emptyList(),
    val director: String = "",
    val streamUrl: String = "",
    val tmdbId: String = "",
    val watchlist: Boolean = false,
    val localRating: Float = 0f,
    val xtreamId: String = "",
) {
    val genresCsv: String get() = genres.joinToString(",")
    val castCsv: String get() = cast.joinToString(",")
}

data class Episode(
    val id: String,
    val seriesId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val durationMin: Int = 0,
    val description: String = "",
    val streamUrl: String = "",
    val progress: Float = 0f,
)

data class Recording(
    val id: String,
    val title: String,
    val channelId: String,
    val programId: String,
    val startMs: Long,
    val endMs: Long,
    val status: RecordingStatus,
    val fileUri: String = "",
    val bytes: Long = 0L,
)

data class WatchHistory(
    val id: String,
    val kind: WatchKind,
    val refId: String,
    val title: String,
    val subtitle: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val atMs: Long,
    val artwork: String = "",
) {
    val fraction: Float
        get() = if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

data class EpgSource(
    val id: String,
    val playlistId: String,
    val url: String,
    val enabled: Boolean = true,
    val timeShiftHours: Int = 0,
    val name: String = "",
)

data class SearchHit(
    val kind: String,
    val id: String,
    val title: String,
    val subtitle: String = "",
    val artwork: String = "",
    val logoColor: Int = 0,
)

data class ImportProgress(
    val stage: Stage,
    val downloadedKb: Int = 0,
    val parsed: Int = 0,
    val total: Int = 0,
    val message: String = "",
) {
    enum class Stage { CONNECTING, DOWNLOADING, PARSING, SAVING, DONE, ERROR }
}

data class GuideRow(
    val channel: Channel,
    val programs: List<Program>,
    val now: Program?,
)

data class DiagnosticsSnapshot(
    val heapUsedMb: Long,
    val heapMaxMb: Long,
    val decoderCount: Int,
    val lastEpgDurationMs: Long,
    val playlistSize: Int,
    val lowRam: Boolean,
    val previewActive: Boolean,
    val multiViewActive: Int,
)
