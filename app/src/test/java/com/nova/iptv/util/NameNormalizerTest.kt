package com.nova.iptv.util

import com.nova.iptv.core.util.NameNormalizer
import com.nova.iptv.core.util.TimeFmt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameNormalizerTest {
    @Test
    fun stripsQualityAndCountryBrackets() {
        assertEquals("CNN", NameNormalizer.normalize("CNN HD"))
        assertEquals("BBC WORLD", NameNormalizer.normalize("BBC World FHD [UK]"))
        assertEquals("SKY SPORTS", NameNormalizer.normalize("Sky Sports 4K HEVC"))
    }

    @Test
    fun similarNamesMatch() {
        assertTrue(NameNormalizer.similar("CNN HD", "CNN"))
        assertTrue(NameNormalizer.similar("Discovery (US)", "Discovery"))
    }
}

class TimeFmtTest {
    @Test
    fun xmltvUtcAndOffset() {
        val utc = TimeFmt.xmltvToEpoch("20240101120000 +0000")
        val plus1 = TimeFmt.xmltvToEpoch("20240101120000 +0100")
        assertEquals(3_600_000L, utc - plus1)
        assertTrue(utc > 0)
    }

    @Test
    fun durationFormat() {
        assertEquals("1:01", TimeFmt.duration(61_000))
        assertEquals("1:00:00", TimeFmt.duration(3_600_000))
    }
}
