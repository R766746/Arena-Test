package com.nova.iptv.data.playlist

import com.nova.iptv.core.util.colorFromName
import com.nova.iptv.core.util.logoInitials
import com.nova.iptv.core.util.newId
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.VodKind
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.InputStream

data class ParsedM3u(
    val channels: List<Channel>,
    val vod: List<VodItem>,
    val headerUserAgent: String = "",
    val headerEpgUrl: String = "",
)

data class ExtInf(
    val duration: Double,
    val attrs: Map<String, String>,
    val name: String,
)

/**
 * Streaming M3U parser. Reads line-by-line from an Okio source so 100k-line
 * playlists never sit in a single giant String.
 */
object M3uParser {

    private val attrRegex = Regex("""([\w-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

    fun parse(
        input: InputStream,
        playlistId: String,
        onProgress: (parsed: Int) -> Unit = {},
    ): ParsedM3u {
        input.source().buffer().use { source ->
            return parse(source, playlistId, onProgress)
        }
    }

    fun parse(
        source: BufferedSource,
        playlistId: String,
        onProgress: (parsed: Int) -> Unit = {},
    ): ParsedM3u {
        val channels = ArrayList<Channel>(256)
        val vod = ArrayList<VodItem>(64)
        var headerUa = ""
        var headerEpg = ""
        var pending: ExtInf? = null
        var pendingUa = ""
        var pendingRef = ""
        var pendingGroup = ""
        var count = 0
        var nextNumber = 1

        while (!source.exhausted()) {
            val raw = source.readUtf8Line() ?: break
            val line = raw.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttrs(line.removePrefix("#EXTM3U"))
                    headerUa = attrs["user-agent"].orEmpty()
                    headerEpg = attrs["url-tvg"] ?: attrs["x-tvg-url"] ?: attrs["tvg-url"].orEmpty()
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val parsed = parseExtInf(line)
                    pending = if (parsed.attrs["group-title"].isNullOrBlank() && pendingGroup.isNotBlank()) {
                        parsed.copy(attrs = parsed.attrs + ("group-title" to pendingGroup))
                    } else parsed
                    pendingGroup = ""
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val body = line.substringAfter(':')
                    when {
                        body.startsWith("http-user-agent=", ignoreCase = true) ->
                            pendingUa = body.substringAfter('=')
                        body.startsWith("http-referrer=", ignoreCase = true) ||
                            body.startsWith("http-referer=", ignoreCase = true) ->
                            pendingRef = body.substringAfter('=')
                    }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    val g = line.substringAfter(':').trim()
                    if (pending == null) pendingGroup = g
                    pending = pending?.let {
                        if (it.attrs["group-title"].isNullOrBlank()) {
                            it.copy(attrs = it.attrs + ("group-title" to g))
                        } else it
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    val info = pending
                    pending = null
                    val url = line
                    if (info != null) {
                        if (isVod(info, url)) {
                            vod += toVod(info, url, playlistId)
                        } else {
                            val chno = info.attrs["tvg-chno"]?.toIntOrNull() ?: nextNumber
                            if (info.attrs["tvg-chno"].isNullOrBlank()) nextNumber = chno + 1
                            else nextNumber = maxOf(nextNumber, chno + 1)
                            channels += toChannel(
                                info, url, playlistId, chno,
                                pendingUa.ifBlank { info.attrs["user-agent"].orEmpty() },
                                pendingRef.ifBlank { info.attrs["referrer"] ?: info.attrs["referer"].orEmpty() },
                            )
                        }
                    }
                    pendingUa = ""
                    pendingRef = ""
                    count++
                    if (count % 250 == 0) onProgress(count)
                }
            }
        }
        onProgress(count)
        return ParsedM3u(channels, vod, headerUa, headerEpg)
    }

    fun parseExtInf(line: String): ExtInf {
        // #EXTINF:-1 tvg-id="x" ...,Name
        val afterTag = line.substringAfter(':')
        val comma = afterTag.lastIndexOf(',')
        val name = if (comma >= 0) afterTag.substring(comma + 1).trim() else afterTag.trim()
        val head = if (comma >= 0) afterTag.substring(0, comma) else afterTag
        val durationToken = head.trim().takeWhile { it != ' ' && it != '\t' }
        val duration = durationToken.toDoubleOrNull() ?: -1.0
        val attrPart = head.drop(durationToken.length)
        return ExtInf(duration, parseAttrs(attrPart), name)
    }

    fun parseAttrs(raw: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        attrRegex.findAll(raw).forEach { m ->
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
            out[key] = value
        }
        return out
    }

    fun isVod(info: ExtInf, url: String): Boolean {
        val group = info.attrs["group-title"].orEmpty()
        val g = group.lowercase()
        if (g.contains("movie") || g.contains("vod") || g.contains("series") || g.contains("film")) return true
        val path = url.lowercase()
        if (path.contains("/movie/") || path.contains("/series/") || path.contains("/vod/")) return true
        if (info.duration > 0 && info.duration != -1.0) return true
        return false
    }

    private fun toChannel(
        info: ExtInf,
        url: String,
        playlistId: String,
        number: Int,
        ua: String,
        referrer: String,
    ): Channel {
        val name = info.name.ifBlank { info.attrs["tvg-name"].orEmpty().ifBlank { "Channel $number" } }
        val catchup = !info.attrs["catchup"].isNullOrBlank() ||
            !info.attrs["catchup-days"].isNullOrBlank() ||
            !info.attrs["timeshift"].isNullOrBlank()
        return Channel(
            id = "$playlistId:${stableKey(url, name)}",
            playlistId = playlistId,
            number = number,
            name = name,
            groupName = info.attrs["group-title"].orEmpty().ifBlank { "All Channels" },
            logoUrl = info.attrs["tvg-logo"].orEmpty(),
            logoText = logoInitials(name),
            logoColor = colorFromName(name),
            streamUrl = url,
            epgId = info.attrs["tvg-id"].orEmpty(),
            catchup = catchup,
            catchupDays = info.attrs["catchup-days"]?.toIntOrNull() ?: 0,
            catchupSource = info.attrs["catchup-source"].orEmpty(),
            timeshift = info.attrs["timeshift"].orEmpty(),
            userAgent = ua,
            referrer = referrer,
        )
    }

    private fun toVod(info: ExtInf, url: String, playlistId: String): VodItem {
        val name = info.name.ifBlank { "Untitled" }
        val group = info.attrs["group-title"].orEmpty()
        val kind = if (group.contains("series", true) || url.contains("/series/", true)) {
            VodKind.SERIES
        } else {
            VodKind.MOVIE
        }
        val year = Regex("""((?:19|20)\d{2})""").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return VodItem(
            id = "$playlistId:vod:${stableKey(url, name)}",
            playlistId = playlistId,
            kind = kind,
            title = name.replace(Regex("""\((?:19|20)\d{2}\)"""), "").trim(),
            year = year,
            posterUrl = info.attrs["tvg-logo"].orEmpty(),
            streamUrl = url,
            genres = listOfNotNull(group.takeIf { it.isNotBlank() }),
            description = "",
        )
    }

    fun stableKey(url: String, name: String): String {
        val raw = "$url|$name"
        var h = 1125899906842597L
        raw.forEach { h = 31 * h + it.code }
        return h.toULong().toString(16)
    }

    fun parseString(text: String, playlistId: String = "test"): ParsedM3u =
        parse(text.byteInputStream(), playlistId)
}
