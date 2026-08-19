package com.nova.iptv.data.epg

import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.core.util.newId
import com.nova.iptv.domain.model.Program
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

private class SizeLimitedInputStream(
    input: InputStream,
    private val maxBytes: Long,
) : FilterInputStream(input) {
    private var consumed = 0L

    private fun account(count: Int): Int {
        if (count > 0) {
            consumed += count
            if (consumed > maxBytes) throw IOException("XMLTV feed exceeds ${maxBytes / (1024 * 1024)} MB limit")
        }
        return count
    }

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) account(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        account(super.read(buffer, offset, length))
}

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
        collectProgrammes: Boolean = true,
        onChannelsReady: (List<XmltvChannel>) -> Unit = {},
        onProgrammeBatch: (List<RawProgramme>) -> Unit = {},
        maxUncompressedBytes: Long = 512L * 1024 * 1024,
        maxProgrammes: Int = 2_000_000,
    ): XmltvResult {
        val stream = SizeLimitedInputStream(openMaybeGzip(input, urlHint), maxUncompressedBytes)
        val channels = ArrayList<XmltvChannel>(512)
        val programmes = ArrayList<RawProgramme>(4096)
        val programmeBatch = ArrayList<RawProgramme>(500)
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
            var channelsDelivered = false

            fun deliverChannels() {
                if (!channelsDelivered) {
                    channelsDelivered = true
                    onChannelsReady(channels.toList())
                }
            }

            fun flushProgrammes() {
                if (programmeBatch.isNotEmpty()) {
                    onProgrammeBatch(programmeBatch.toList())
                    programmeBatch.clear()
                }
            }

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
                        deliverChannels()
                        programmeChannel = attributes.getValue("channel").orEmpty()
                        start = TimeFmt.xmltvToEpoch(attributes.getValue("start").orEmpty()) + shift
                        stop = TimeFmt.xmltvToEpoch(attributes.getValue("stop") ?: attributes.getValue("end").orEmpty()) + shift
                        title = ""; description = ""; category = ""
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                val remaining = (MAX_ELEMENT_TEXT - text.length).coerceAtLeast(0)
                if (remaining > 0) text.append(ch, start, length.coerceAtMost(remaining))
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
                            val programme = RawProgramme(programmeChannel, title.ifBlank { "Programme" }, description, category, start, stop)
                            if (collectProgrammes) programmes += programme
                            programmeBatch += programme
                        }
                        count++
                        if (count > maxProgrammes) throw SAXException("XMLTV programme limit exceeded: $maxProgrammes")
                        if (programmeBatch.size >= 500) {
                            flushProgrammes()
                            onYield()
                        }
                    }
                }
                text.setLength(0)
            }
        }
        stream.use {
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                // Android TV vendors ship different SAX implementations. A
                // security feature being unsupported must not reject a valid
                // guide; the blocking EntityResolver and bounded stream/parser
                // limits below remain enforced on every implementation.
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                runCatching { isXIncludeAware = false }
            }
            factory.newSAXParser().xmlReader.apply {
                contentHandler = handler
                entityResolver = org.xml.sax.EntityResolver { _, _ -> InputSource(StringReader("")) }
                parse(InputSource(it))
            }
        }
        handler.deliverChannels()
        handler.flushProgrammes()
        return XmltvResult(channels, programmes)
    }

    private const val MAX_ELEMENT_TEXT = 64 * 1024

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
