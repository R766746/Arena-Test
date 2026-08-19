package com.nova.iptv.core.perf

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object LowRam {
    @Volatile
    var isLowRam: Boolean = false
        private set

    @Volatile
    var totalMemMb: Long = 0
        private set

    @Volatile
    var heapClassMb: Int = 0
        private set

    fun init(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        isLowRam = am.isLowRamDevice ||
            Build.MODEL.contains("AFT", ignoreCase = true) &&
            (Build.MODEL.contains("TANK", ignoreCase = true) ||
                Build.MODEL.contains("OTTER", ignoreCase = true))
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        totalMemMb = info.totalMem / (1024 * 1024)
        heapClassMb = am.memoryClass
        // Some TV firmware reports ample physical RAM but caps each app to a
        // 256 MB heap. Treat the process heap limit as the decisive constraint.
        if (totalMemMb in 1..2048 || heapClassMb <= 256) isLowRam = true
    }

    fun below3Gb(): Boolean = totalMemMb in 1..3072

    fun useRgb565(): Boolean = below3Gb() || isLowRam
}
