package com.nova.iptv.playlist

import com.nova.iptv.data.playlist.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private fun load(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!.bufferedReader().readText()

    @Test
    fun mixedQuotesAndCatchup() {
        val parsed = M3uParser.parseString(load("mixed_quotes.m3u"), "p1")
        assertEquals(4, parsed.channels.size)
        assertEquals("http://epg.example/xmltv.xml", parsed.headerEpgUrl)
        assertEquals("NOVATest/1.0", parsed.headerUserAgent)

        val cnn = parsed.channels[0]
        assertEquals("CNN HD", cnn.name)
        assertEquals("cnn.us", cnn.epgId)
        assertEquals(101, cnn.number)
        assertEquals("News", cnn.groupName)
        assertTrue(cnn.catchup)
        assertEquals(7, cnn.catchupDays)

        val bbc = parsed.channels[1]
        assertEquals("BBC World", bbc.name)
        assertEquals("bbc.uk", bbc.epgId)

        val fox = parsed.channels[2]
        assertEquals("Fox News", fox.name)
        assertEquals(103, fox.number)

        val espn = parsed.channels[3]
        assertEquals("VLC/3.0", espn.userAgent)
        assertEquals("http://portal.example/", espn.referrer)
    }

    @Test
    fun noAttributesUsesNameAfterComma() {
        val parsed = M3uParser.parseString(load("no_attributes.m3u"), "p2")
        assertEquals(2, parsed.channels.size)
        assertEquals("Plain Channel", parsed.channels[0].name)
        assertEquals("Another", parsed.channels[1].name)
        assertEquals("General", parsed.channels[1].groupName)
    }

    @Test
    fun vodDetectedByDurationGroupAndPath() {
        val parsed = M3uParser.parseString(load("vod_mixed.m3u"), "p3")
        assertEquals(2, parsed.channels.size) // live one + cartoon
        assertEquals(3, parsed.vod.size) // movie duration, series group, /movie/ path
        assertTrue(parsed.vod.any { it.title.contains("Example Movie") })
        assertTrue(parsed.vod.any { it.kind.name == "SERIES" })
        assertFalse(parsed.channels.any { it.streamUrl.contains("/movie/") })
    }

    @Test
    fun extInfParserHandlesUnquotedAndSingleQuoted() {
        val a = M3uParser.parseExtInf("#EXTINF:-1 tvg-id=foo group-title=Bar,Name Here")
        assertEquals("Name Here", a.name)
        assertEquals("foo", a.attrs["tvg-id"])
        assertEquals("Bar", a.attrs["group-title"])
        assertEquals(-1.0, a.duration, 0.0)

        val b = M3uParser.parseExtInf("#EXTINF:3600 tvg-id='x' group-title=\"Movies\",Film")
        assertEquals(3600.0, b.duration, 0.0)
        assertEquals("x", b.attrs["tvg-id"])
        assertEquals("Movies", b.attrs["group-title"])
        assertEquals("Film", b.name)
    }

    @Test
    fun isVodRules() {
        val live = M3uParser.parseExtInf("#EXTINF:-1 group-title=\"News\",N")
        assertFalse(M3uParser.isVod(live, "http://x/live/1.ts"))
        val movieGroup = M3uParser.parseExtInf("#EXTINF:-1 group-title=\"VOD Movies\",M")
        assertTrue(M3uParser.isVod(movieGroup, "http://x/1.ts"))
        val path = M3uParser.parseExtInf("#EXTINF:-1 group-title=\"X\",M")
        assertTrue(M3uParser.isVod(path, "http://portal/movie/1.mp4"))
        val timed = M3uParser.parseExtInf("#EXTINF:120 group-title=\"X\",M")
        assertTrue(M3uParser.isVod(timed, "http://x/1.ts"))
    }
}
