package com.nova.iptv.data.playlist

import com.nova.iptv.core.util.colorFromName
import com.nova.iptv.core.util.logoInitials
import com.nova.iptv.data.epg.DemoEpg
import com.nova.iptv.data.local.NovaDatabase
import com.nova.iptv.data.local.entity.ChannelEntity
import com.nova.iptv.data.local.entity.EpisodeEntity
import com.nova.iptv.data.local.entity.PlaylistEntity
import com.nova.iptv.data.local.entity.ProgramEntity
import com.nova.iptv.data.local.entity.VodEntity
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline showcase catalog: ~40 live channels + synthetic EPG (-6h..+16h)
 * plus a handful of movies/series so VOD panes are never empty.
 */
@Singleton
class DemoCatalog @Inject constructor(
    private val db: NovaDatabase,
    private val demoEpg: DemoEpg,
) {
    suspend fun ensureSeeded() {
        if (db.playlists().byId(Playlist.DEMO_ID) != null &&
            db.channels().count(Playlist.DEMO_ID) > 0
        ) {
            return
        }
        seed()
    }

    suspend fun seed() {
        Timber.i("Seeding demo catalog")
        val playlist = PlaylistEntity(
            id = Playlist.DEMO_ID,
            name = "NOVA Showcase",
            type = PlaylistType.DEMO.name,
            url = "",
            username = "",
            passwordEnc = "",
            epgUrl = "demo://epg",
            userAgent = Playlist.DEFAULT_UA,
            lastUpdate = System.currentTimeMillis(),
            autoUpdate = false,
            updateIntervalHours = 24,
        )
        db.playlists().upsert(playlist)

        val channels = CHANNELS.mapIndexed { index, spec ->
            ChannelEntity(
                id = "demo:ch:${spec.number}",
                playlistId = Playlist.DEMO_ID,
                number = spec.number,
                name = spec.name,
                groupName = spec.group,
                logoUrl = "",
                logoText = logoInitials(spec.name),
                logoColor = colorFromName(spec.name),
                streamUrl = spec.url,
                epgId = spec.epgId,
                catchup = spec.catchup,
                catchupDays = if (spec.catchup) 7 else 0,
                favorite = index < 4,
                hidden = false,
                locked = spec.group == "Adult",
                userOrder = index,
            )
        }
        db.channels().replacePlaylistChannels(Playlist.DEMO_ID, channels)

        val programs = demoEpg.generate(channels.map { it.toModel() })
        programs.chunked(500).forEach { db.programs().upsertAll(it.map { p -> ProgramEntity.from(p) }) }

        db.vod().deleteByPlaylist(Playlist.DEMO_ID)
        db.vod().upsertAll(demoMovies() + demoSeries())
        db.episodes().upsertAll(demoEpisodes())
    }

    private fun demoMovies(): List<VodEntity> {
        val items = listOf(
            Triple("Northline", 2023, "Thriller"),
            Triple("Glass Harbor", 2021, "Drama"),
            Triple("Red Orbit", 2024, "Sci-Fi"),
            Triple("The Last Reel", 2019, "Drama"),
            Triple("Midnight Circuit", 2022, "Action"),
            Triple("Paper Planets", 2020, "Family"),
            Triple("Kite & Anchor", 2018, "Romance"),
            Triple("Static Bloom", 2024, "Horror"),
            Triple("Copper Sky", 2017, "Western"),
            Triple("After Image", 2023, "Mystery"),
            Triple("Low Tide", 2021, "Crime"),
            Triple("Velvet Engine", 2022, "Music"),
        )
        return items.mapIndexed { i, (title, year, genre) ->
            VodEntity(
                id = "demo:movie:$i",
                playlistId = Playlist.DEMO_ID,
                kind = "MOVIE",
                title = title,
                year = year,
                rating = listOf("PG-13", "R", "PG", "12")[i % 4],
                durationMin = 95 + i * 4,
                genresCsv = genre,
                posterUrl = "",
                backdropUrl = "",
                description = "$title is a showcase title. NOVA does not provide streams — this card is visual only.",
                castCsv = "A. Stone, J. Hale, M. Chen",
                director = "L. Navarro",
                streamUrl = BIG_BUCK_BUNNY,
                tmdbId = "",
            )
        }
    }

    private fun demoSeries(): List<VodEntity> = listOf(
        VodEntity(
            id = "demo:series:1",
            playlistId = Playlist.DEMO_ID,
            kind = "SERIES",
            title = "Signal Lost",
            year = 2022,
            rating = "TV-14",
            durationMin = 48,
            genresCsv = "Drama",
            posterUrl = "",
            backdropUrl = "",
            description = "A coastal radio station uncovers a frequency that should not exist.",
            castCsv = "R. Okonkwo, S. Berg",
            director = "P. Iyer",
            streamUrl = "",
            tmdbId = "",
        ),
        VodEntity(
            id = "demo:series:2",
            playlistId = Playlist.DEMO_ID,
            kind = "SERIES",
            title = "Kitchen Ghosts",
            year = 2021,
            rating = "TV-PG",
            durationMin = 28,
            genresCsv = "Comedy",
            posterUrl = "",
            backdropUrl = "",
            description = "Two chefs inherit a restaurant that is slightly haunted and extremely booked.",
            castCsv = "N. Park, D. Alvarez",
            director = "C. Moreau",
            streamUrl = "",
            tmdbId = "",
        ),
    )

    private fun demoEpisodes(): List<EpisodeEntity> {
        val out = ArrayList<EpisodeEntity>()
        listOf("demo:series:1" to 8, "demo:series:2" to 6).forEach { (sid, n) ->
            repeat(n) { i ->
                out += EpisodeEntity(
                    id = "$sid:e$i",
                    seriesId = sid,
                    season = 1,
                    episode = i + 1,
                    title = "Episode ${i + 1}",
                    durationMin = if (sid.endsWith("1")) 48 else 28,
                    description = "Showcase episode ${i + 1}.",
                    streamUrl = BIG_BUCK_BUNNY,
                    progress = if (i == 0) 0.35f else 0f,
                )
            }
        }
        return out
    }

    private data class Spec(
        val number: Int,
        val name: String,
        val group: String,
        val epgId: String,
        val catchup: Boolean,
        val url: String,
    )

    companion object {
        // Public domain / freely usable test streams. Showcase only.
        const val BIG_BUCK_BUNNY =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        private const val Sintel =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
        private const val Elephants =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        private const val Tears =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        private const val AppleHls =
            "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8"

        private val CHANNELS = listOf(
            Spec(101, "NOVA News 24", "News", "nova-news", true, AppleHls),
            Spec(102, "World Desk HD", "News", "world-desk", true, BIG_BUCK_BUNNY),
            Spec(103, "Capitol Wire", "News", "capitol", true, Sintel),
            Spec(104, "Pacific Report", "News", "pacific", false, Elephants),
            Spec(105, "Nightly Brief", "News", "nightly", true, Tears),
            Spec(201, "Gridiron Live", "Sports", "gridiron", true, AppleHls),
            Spec(202, "Center Court", "Sports", "court", true, BIG_BUCK_BUNNY),
            Spec(203, "Pitchside", "Sports", "pitch", true, Sintel),
            Spec(204, "Ice Night", "Sports", "ice", false, Elephants),
            Spec(205, "Motorline", "Sports", "motor", true, Tears),
            Spec(206, "Climb TV", "Sports", "climb", false, AppleHls),
            Spec(301, "Atrium Cinema", "Movies", "atrium", true, BIG_BUCK_BUNNY),
            Spec(302, "Marquee 1", "Movies", "marquee", true, Sintel),
            Spec(303, "Noir Room", "Movies", "noir", true, Elephants),
            Spec(304, "Sunday Matinee", "Movies", "matinee", false, Tears),
            Spec(401, "Harbor Lights", "Entertainment", "harbor", true, AppleHls),
            Spec(402, "Late Show Yard", "Entertainment", "lateshow", false, BIG_BUCK_BUNNY),
            Spec(403, "Kitchen Fire", "Entertainment", "kitchen", true, Sintel),
            Spec(404, "Open Mic", "Entertainment", "openmic", false, Elephants),
            Spec(405, "City Pulse", "Entertainment", "pulse", true, Tears),
            Spec(501, "Paper Kite", "Kids", "kite", true, BIG_BUCK_BUNNY),
            Spec(502, "Little Orbit", "Kids", "orbit", true, Sintel),
            Spec(503, "Puzzle Garden", "Kids", "puzzle", false, Elephants),
            Spec(504, "Story Boat", "Kids", "boat", true, Tears),
            Spec(601, "Blue Earth", "Documentary", "earth", true, AppleHls),
            Spec(602, "Deep Archive", "Documentary", "archive", true, BIG_BUCK_BUNNY),
            Spec(603, "Frontier Notes", "Documentary", "frontier", false, Sintel),
            Spec(604, "Machine Age", "Documentary", "machine", true, Elephants),
            Spec(605, "Quiet Cities", "Documentary", "cities", false, Tears),
            Spec(701, "Signal 9", "Music", "signal9", true, AppleHls),
            Spec(702, "Vinyl Room", "Music", "vinyl", false, BIG_BUCK_BUNNY),
            Spec(703, "Night Frequency", "Music", "freq", true, Sintel),
            Spec(801, "NOVA One", "General", "nova1", true, AppleHls),
            Spec(802, "NOVA Two", "General", "nova2", true, BIG_BUCK_BUNNY),
            Spec(803, "NOVA Extra", "General", "nova3", false, Sintel),
            Spec(901, "Weather Ribbon", "News", "weather", false, Elephants),
            Spec(902, "Market Open", "News", "market", true, Tears),
            Spec(903, "Local 11", "News", "local11", true, AppleHls),
            Spec(1001, "Retro Toons", "Kids", "retro", false, BIG_BUCK_BUNNY),
            Spec(1002, "Stadium 2", "Sports", "stadium2", true, Sintel),
        )
    }
}
