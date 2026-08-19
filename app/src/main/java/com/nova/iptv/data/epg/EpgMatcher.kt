package com.nova.iptv.data.epg

import com.nova.iptv.core.util.NameNormalizer
import com.nova.iptv.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

data class MatchResult(
    val channelId: String,
    val xmltvId: String,
    val method: Method,
) {
    enum class Method { EXACT_ID, NORMALIZED_NAME, MANUAL }
}

@Singleton
class EpgMatcher @Inject constructor() {

    fun match(
        channels: List<Channel>,
        xmltv: List<XmltvChannel>,
        extraTokens: String,
    ): List<MatchResult> {
        val byId = xmltv.associateBy { it.id.lowercase() }
        val byName = HashMap<String, XmltvChannel>(xmltv.size * 2)
        xmltv.forEach { xc ->
            xc.displayNames.forEach { n ->
                byName.putIfAbsent(NameNormalizer.normalize(n, extraTokens), xc)
                byName.putIfAbsent(n.lowercase(), xc)
            }
            byName.putIfAbsent(NameNormalizer.normalize(xc.id, extraTokens), xc)
        }
        val out = ArrayList<MatchResult>(channels.size)
        channels.forEach { ch ->
            val exact = ch.epgId.takeIf { it.isNotBlank() }?.let { byId[it.lowercase()] }
            if (exact != null) {
                out += MatchResult(ch.id, exact.id, MatchResult.Method.EXACT_ID)
                return@forEach
            }
            val norm = NameNormalizer.normalize(ch.name, extraTokens)
            val named = byName[norm] ?: byName[ch.name.lowercase()]
            if (named != null) {
                out += MatchResult(ch.id, named.id, MatchResult.Method.NORMALIZED_NAME)
            }
        }
        return out
    }
}
