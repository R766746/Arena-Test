package com.nova.iptv.epg

import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.data.epg.XmltvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmltvParserTest {
    @Test
    fun parsesChannelsAndProgrammesWithOffsets() {
        val xml = javaClass.classLoader!!.getResourceAsStream("fixtures/sample.xmltv")!!
        val result = XmltvParser.parse(xml)
        assertEquals(2, result.channels.size)
        assertEquals(3, result.programmes.size)
        val cnn = result.channels.first { it.id == "cnn.us" }
        assertTrue(cnn.displayNames.contains("CNN HD"))

        val first = result.programmes.first { it.title == "World Briefing" }
        assertEquals("cnn.us", first.channelId)
        assertEquals(TimeFmt.xmltvToEpoch("20240101120000 +0000"), first.startMs)
        assertEquals(3_600_000L, first.endMs - first.startMs)

        val bbc = result.programmes.first { it.title == "Outlook" }
        // +0100 should still convert to a coherent epoch
        assertTrue(bbc.startMs > 0)
        assertEquals(3_600_000L, bbc.endMs - bbc.startMs)
    }

    @Test
    fun timeShiftApplied() {
        val xml = javaClass.classLoader!!.getResourceAsStream("fixtures/sample.xmltv")!!
        val shifted = XmltvParser.parse(xml, timeShiftHours = 2)
        val base = TimeFmt.xmltvToEpoch("20240101120000 +0000")
        val first = shifted.programmes.first { it.title == "World Briefing" }
        assertEquals(base + 2 * 3_600_000L, first.startMs)
    }
}

class EpgMatcherTest {
    @Test
    fun exactIdThenNormalizedName() {
        val matcher = EpgMatcher()
        val channels = listOf(
            com.nova.iptv.domain.model.Channel(
                id = "c1", playlistId = "p", number = 1, name = "CNN HD",
                groupName = "News", streamUrl = "u", epgId = "cnn.us",
            ),
            com.nova.iptv.domain.model.Channel(
                id = "c2", playlistId = "p", number = 2, name = "BBC World FHD [UK]",
                groupName = "News", streamUrl = "u2",
            ),
        )
        val xml = listOf(
            XmltvChannel("cnn.us", listOf("CNN"), ""),
            XmltvChannel("bbc.uk", listOf("BBC World"), ""),
        )
        val matches = matcher.match(channels, xml, "HD,FHD,4K")
        assertEquals(2, matches.size)
        assertEquals(MatchResult.Method.EXACT_ID, matches.first { it.channelId == "c1" }.method)
        assertEquals("bbc.uk", matches.first { it.channelId == "c2" }.xmltvId)
        assertEquals(MatchResult.Method.NORMALIZED_NAME, matches.first { it.channelId == "c2" }.method)
    }
}
