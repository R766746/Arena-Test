package com.nova.iptv.catchup

import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.data.player.CatchupUrlBuilder
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.domain.model.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchupUrlBuilderTest {
    private val builder = CatchupUrlBuilder()

    private val past = Program(
        id = "p1",
        channelId = "c1",
        title = "Replay",
        startMs = System.currentTimeMillis() - 3 * 3600_000L,
        endMs = System.currentTimeMillis() - 2 * 3600_000L,
    )

    @Test
    fun xtreamPathForm() {
        val ch = Channel(
            id = "c1", playlistId = "pl", number = 1, name = "N", groupName = "G",
            streamUrl = "http://portal/live/u/p/55.ts", catchup = true, catchupDays = 7,
            xtreamStreamId = "55",
        )
        val pl = Playlist(
            id = "pl", name = "X", type = PlaylistType.XTREAM,
            url = "http://portal.example:8080", username = "alice", passwordEnc = "s3cret",
        )
        val url = builder.build(ch, past, pl)
        assertNotNull(url)
        assertTrue(url!!.contains("/timeshift/alice/s3cret/"))
        assertTrue(url.endsWith("/55.m3u8"))
        assertTrue(url.contains(TimeFmt.xtreamStart(past.startMs)))
    }

    @Test
    fun xtreamPhpFormWhenSourceMentionsIt() {
        val ch = Channel(
            id = "c1", playlistId = "pl", number = 1, name = "N", groupName = "G",
            streamUrl = "http://portal/live/u/p/55.ts", catchup = true, catchupDays = 7,
            catchupSource = "http://portal/streaming/timeshift.php",
            xtreamStreamId = "55",
        )
        val pl = Playlist(
            id = "pl", name = "X", type = PlaylistType.XTREAM,
            url = "http://portal.example", username = "alice", passwordEnc = "s3cret",
        )
        val url = builder.build(ch, past, pl)!!
        assertTrue(url.contains("timeshift.php"))
        assertTrue(url.contains("stream=55"))
        assertTrue(url.contains("username=alice"))
    }

    @Test
    fun m3uUtcQuery() {
        val ch = Channel(
            id = "c1", playlistId = "pl", number = 1, name = "N", groupName = "G",
            streamUrl = "http://cdn/live/1.ts", catchup = true, catchupDays = 3,
        )
        val pl = Playlist(id = "pl", name = "M", type = PlaylistType.M3U, url = "http://list.m3u")
        val url = builder.build(ch, past, pl)!!
        assertTrue(url.contains("utc="))
        assertTrue(url.contains("lutc="))
    }

    @Test
    fun catchupSourceTokens() {
        val ch = Channel(
            id = "c1", playlistId = "pl", number = 1, name = "N", groupName = "G",
            streamUrl = "http://cdn/live/1.ts", catchup = true,
            catchupSource = "http://cdn/catchup?from={utc}&to={lutc}",
        )
        val url = builder.build(ch, past, null)!!
        assertEquals(
            "http://cdn/catchup?from=${past.startMs / 1000}&to=${past.endMs / 1000}",
            url,
        )
    }

    @Test
    fun rejectsFutureProgram() {
        val live = past.copy(startMs = System.currentTimeMillis(), endMs = System.currentTimeMillis() + 3600_000)
        val ch = Channel(id = "c", playlistId = "p", number = 1, name = "N", groupName = "G", streamUrl = "u", catchup = true)
        assertFalse(builder.isEligible(ch, live))
    }
}
