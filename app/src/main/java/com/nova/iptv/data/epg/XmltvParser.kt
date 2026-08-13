package com.nova.iptv.data.epg

import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.core.util.newId
import com.nova.iptv.domain.model.Program
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

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
        val channels = ArrayList<XmltvChannel>(512)
        val programmes = ArrayList<RawProgramme>(4096)
        val shift = timeShiftHours * 3_600_000L
        val handler = object : DefaultHandler() {
            var channelId = ""
            var names = ArrayList<String>()
            var icon = ""
            var programmeChannel = ""
            var start = 0L
            var stop = 0L
            var title = ""
            var description = ""
            var category = ""
            val text = StringBuilder()
            var count = 0

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                text.setLength(0)
                when (qName) {
                    "channel" -> {
                        channelId = attributes.getValue("id").orEmpty()
                        names = ArrayList()
                        icon = ""
                    }
                    "icon" -> icon = attributes.getValue("src").orEmpty()
                    "programme" -> {
                        programmeChannel = attributes.getValue("channel").orEmpty()
                        start = TimeFmt.xmltvToEpoch(attributes.getValue("start").orEmpty()) + shift
                        stop = TimeFmt.xmltvToEpoch(attributes.getValue("stop") ?: attributes.getValue("end").orEmpty()) + shift
                        title = ""; description = ""; category = ""
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                text.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                val value = text.toString().trim()
                when (qName) {
                    "display-name" -> if (value.isNotEmpty()) names += value
                    "title" -> title = value
                    "desc" -> description = value
                    "category" -> if (category.isEmpty()) category = value
                    "channel" -> channels += XmltvChannel(channelId, names.toList(), icon)
                    "programme" -> {
                        if (programmeChannel.isNotBlank() && start > 0 && stop > start) {
                            programmes += RawProgramme(programmeChannel, title.ifBlank { "Programme" }, description, category, start, stop)
                        }
                        count++
                        if (count % 500 == 0) onYield()
                    }
                }
                text.setLength(0)
            }
        }
        stream.use {
            SAXParserFactory.newInstance().apply { isNamespaceAware = false }
                .newSAXParser().parse(it, handler)
        }
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
