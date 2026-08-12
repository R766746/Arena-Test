package com.nova.iptv.data.epg

import com.nova.iptv.core.util.newId
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.Program
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoEpg @Inject constructor() {

    fun generate(
        channels: List<Channel>,
        fromOffsetH: Int = -6,
        toOffsetH: Int = 16,
        now: Long = System.currentTimeMillis(),
    ): List<Program> {
        val from = now + TimeUnit.HOURS.toMillis(fromOffsetH.toLong())
        val to = now + TimeUnit.HOURS.toMillis(toOffsetH.toLong())
        val out = ArrayList<Program>(channels.size * 24)
        channels.forEachIndexed { idx, ch ->
            val titles = titlesFor(ch.groupName)
            var t = align30(from) + (idx % 3) * 10 * 60_000L
            var n = 0
            while (t < to) {
                val durMin = DURATIONS[(idx + n) % DURATIONS.size]
                val end = t + durMin * 60_000L
                val title = titles[(idx + n) % titles.size]
                out += Program(
                    id = "demo:p:${ch.id}:$n",
                    channelId = ch.id,
                    title = title,
                    description = "$title on ${ch.name}. Synthetic showcase guide data.",
                    category = ch.groupName,
                    startMs = t,
                    endMs = end,
                    catchup = ch.catchup && end < now,
                )
                t = end
                n++
            }
        }
        return out
    }

    private fun align30(ms: Long): Long {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.set(java.util.Calendar.MINUTE, (cal.get(java.util.Calendar.MINUTE) / 30) * 30)
        return cal.timeInMillis
    }

    private fun titlesFor(group: String): List<String> = when (group) {
        "News" -> listOf("World Briefing", "Markets Open", "Capitol Hour", "Weather Desk", "Nightly Recap", "Local Report")
        "Sports" -> listOf("Live Match", "Studio Preview", "Highlights", "Press Conference", "Classic Replay", "Countdown")
        "Movies" -> listOf("Evening Feature", "Matinee", "Director's Cut", "Short Film Hour", "Encore")
        "Kids" -> listOf("Morning Cartoons", "Puzzle Time", "Story Boat", "Science Club", "Lullaby Hour")
        "Documentary" -> listOf("Deep Dive", "Field Notes", "Archive Hour", "Planet Watch", "Inventors")
        "Music" -> listOf("Live Session", "Guest Mix", "Chart Heat", "Unplugged", "After Hours")
        else -> listOf("Prime Time", "Talk Back", "Double Bill", "Late Window", "Morning Block")
    }

    companion object {
        private val DURATIONS = intArrayOf(30, 45, 60, 30, 90, 30, 60)
    }
}
