package com.nova.iptv.core.util

import android.content.Context
import android.text.format.DateFormat
import com.nova.iptv.domain.model.AppSettings
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.roundToInt

fun newId(): String = UUID.randomUUID().toString()

fun sha256(value: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

fun hashPin(pin: String, salt: String): String = sha256("$salt:$pin")

object TimeFmt {
    fun clock(now: Long, clock24h: Boolean): String {
        val pattern = if (clock24h) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
    }

    fun clockWithDate(now: Long, clock24h: Boolean): String {
        val pattern = if (clock24h) "EEE d MMM   HH:mm" else "EEE d MMM   h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
    }

    fun range(start: Long, end: Long, clock24h: Boolean): String =
        "${clock(start, clock24h)} – ${clock(end, clock24h)}"

    fun remaining(end: Long, now: Long): String {
        val mins = ((end - now) / 60000L).coerceAtLeast(0)
        return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
    }

    fun duration(ms: Long): String {
        val totalSec = (ms / 1000L).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun floorToHalfHour(ms: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = ms }
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val min = cal.get(Calendar.MINUTE)
        cal.set(Calendar.MINUTE, if (min < 30) 0 else 30)
        return cal.timeInMillis
    }

    fun xmltvToEpoch(raw: String): Long {
        // 20240101123000 +0000  or 20240101123000 +0000 or 20240101123000
        val cleaned = raw.trim()
        if (cleaned.length < 14) return 0L
        val body = cleaned.substring(0, 14)
        val tzPart = cleaned.substring(14).trim()
        val tz = when {
            tzPart.isEmpty() -> TimeZone.getTimeZone("UTC")
            tzPart.startsWith("+") || tzPart.startsWith("-") -> {
                val sign = if (tzPart.startsWith("-")) -1 else 1
                val digits = tzPart.drop(1).filter { it.isDigit() }.padEnd(4, '0')
                val hours = digits.take(2).toIntOrNull() ?: 0
                val mins = digits.drop(2).take(2).toIntOrNull() ?: 0
                val offsetMs = sign * ((hours * 60 + mins) * 60 * 1000)
                TimeZone.getTimeZone("GMT").also {
                    // Use a SimpleTimeZone-like offset via "GMT+HH:MM"
                }
                val hh = "%02d".format(hours)
                val mm = "%02d".format(mins)
                TimeZone.getTimeZone("GMT${if (sign < 0) "-" else "+"}$hh:$mm")
            }
            else -> TimeZone.getTimeZone("UTC")
        }
        return try {
            val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = tz }
            fmt.parse(body)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    fun xtreamStart(ms: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(ms))
    }

    fun is24h(context: Context, settings: AppSettings): Boolean =
        settings.clock24h || DateFormat.is24HourFormat(context)
}

object NameNormalizer {
    private val bracket = Regex("""[\[(].*?[\])]""")
    private val extraSpace = Regex("""\s+""")

    fun normalize(name: String, extraTokens: String = ""): String {
        var s = name.uppercase(Locale.US)
        s = bracket.replace(s, " ")
        val tokens = (DEFAULT_TOKENS + extraTokens.split(',').map { it.trim() })
            .filter { it.isNotBlank() }
            .distinct()
        tokens.forEach { token ->
            s = s.replace(Regex("""(?<![A-Z0-9])${Regex.escape(token.uppercase(Locale.US))}(?![A-Z0-9])"""), " ")
        }
        return extraSpace.replace(s, " ").trim()
    }

    fun similar(a: String, b: String, extra: String = ""): Boolean =
        normalize(a, extra) == normalize(b, extra)

    private val DEFAULT_TOKENS = listOf(
        "HD", "FHD", "UHD", "4K", "8K", "HEVC", "H265", "H264", "50FPS", "60FPS",
        "HDR", "SDR", "HQ", "LQ", "BACKUP", "BAK", "VIP",
    )
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.roundToInt()} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

fun logoInitials(name: String): String {
    val parts = name.split(' ', '.', '-', '_').filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.US)
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale.US)
    }
}

fun colorFromName(name: String): Int {
    var hash = 0
    name.forEach { hash = 31 * hash + it.code }
    val palette = intArrayOf(
        0xFF3D8BFD.toInt(),
        0xFF6C5CE7.toInt(),
        0xFFFF6B6B.toInt(),
        0xFF00B894.toInt(),
        0xFFE17055.toInt(),
        0xFF0984E3.toInt(),
        0xFFD63031.toInt(),
        0xFF00CEC9.toInt(),
        0xFFE84393.toInt(),
        0xFFF39C12.toInt(),
    )
    return palette[Math.floorMod(hash, palette.size)]
}
