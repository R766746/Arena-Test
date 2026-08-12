package com.nova.iptv.data.epg

import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.core.util.newId
import com.nova.iptv.domain.model.Program
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

data class XmltvChannel(
    val id: String,
    val displayNames: List<String>,
    val icon: String,
)

data class XmltvResult(
    val channels: List<XmltvChannel>,
    val programmes: List<RawProgramme>,
)

data class RawProgramme(
    val channelId: String,
    val title: String,
    val description: String,
    val category: String,
    val startMs: Long,
    val endMs: Long,
)

/**
 * Streaming XMLTV pull parser. Never DOM-loads multi-hundred-MB files.
 */
object XmltvParser {

    fun openMaybeGzip(input: InputStream, urlHint: String = ""): InputStream {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        if (urlHint.endsWith(".gz", true) || urlHint.endsWith(".gzip", true)) {
            return GZIPInputStream(buffered)
        }
        buffered.mark(4)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered) else buffered
    }

    fun parse(
        input: InputStream,
        timeShiftHours: Int = 0,
        urlHint: String = "",
        onYield: () -> Unit = {},
    ): XmltvResult {
        val stream = openMaybeGzip(input, urlHint)
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(stream, null)

        val channels = ArrayList<XmltvChannel>(512)
        val programmes = ArrayList<RawProgramme>(4096)
        val shift = timeShiftHours * 3_600_000L

        var event = parser.eventType
        var curChannelId = ""
        var curNames = ArrayList<String>()
        val curIcon = StringBuilder()
        var inChannel = false
        var inProgramme = false
        var pChannel = ""
        var pStart = 0L
        var pStop = 0L
        var pTitle = ""
        var pDesc = ""
        var pCat = ""
        var tag = ""
        var count = 0

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name ?: ""
                    when (tag) {
                        "channel" -> {
                            inChannel = true
                            curChannelId = parser.getAttributeValue(null, "id").orEmpty()
                            curNames = ArrayList()
                            curIcon.setLength(0)
                        }
                        "icon" -> if (inChannel) {
                            curIcon.append(parser.getAttributeValue(null, "src").orEmpty())
                        }
                        "programme" -> {
                            inProgramme = true
                            pChannel = parser.getAttributeValue(null, "channel").orEmpty()
                            pStart = TimeFmt.xmltvToEpoch(parser.getAttributeValue(null, "start").orEmpty()) + shift
                            pStop = TimeFmt.xmltvToEpoch(
                                parser.getAttributeValue(null, "stop")
                                    ?: parser.getAttributeValue(null, "end").orEmpty(),
                            ) + shift
                            pTitle = ""; pDesc = ""; pCat = ""
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isEmpty()) {
                        // skip
                    } else if (inChannel && tag == "display-name") {
                        curNames += text
                    } else if (inProgramme) {
                        when (tag) {
                            "title" -> pTitle = text
                            "desc" -> pDesc = text
                            "category" -> if (pCat.isEmpty()) pCat = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> {
                            channels += XmltvChannel(curChannelId, curNames.toList(), curIcon.toString())
                            inChannel = false
                        }
                        "programme" -> {
                            if (pChannel.isNotBlank() && pStart > 0 && pStop > pStart) {
                                programmes += RawProgramme(pChannel, pTitle.ifBlank { "Programme" }, pDesc, pCat, pStart, pStop)
                            }
                            inProgramme = false
                            count++
                            if (count % 500 == 0) onYield()
                        }
                    }
                    tag = ""
                }
            }
            event = parser.next()
        }
        stream.close()
        return XmltvResult(channels, programmes)
    }

    fun toPrograms(raw: List<RawProgramme>, channelIdForXmltv: (String) -> String?): List<Program> {
        val out = ArrayList<Program>(raw.size)
        raw.forEach { r ->
            val ch = channelIdForXmltv(r.channelId) ?: return@forEach
            out += Program(
                id = "xmltv:${r.channelId}:${r.startMs}",
                channelId = ch,
                title = r.title,
                description = r.description,
                category = r.category,
                startMs = r.startMs,
                endMs = r.endMs,
                catchup = r.endMs < System.currentTimeMillis(),
            )
        }
        return out
    }
}
