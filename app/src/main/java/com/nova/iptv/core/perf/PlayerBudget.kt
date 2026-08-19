package com.nova.iptv.core.perf

import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strict player budget for 1.5–2 GB Android TV devices.
 *
 * Allowed:
 *  - 1 fullscreen player
 *  - 1 fullscreen + 1 preview
 *  - N muted multi-view players
 * Never fullscreen-preview AND multi-view at the same time.
 */
@Singleton
class PlayerBudget @Inject constructor() {

    enum class Mode { IDLE, FULLSCREEN, FULLSCREEN_PLUS_PREVIEW, MULTIVIEW }

    @Volatile
    var mode: Mode = Mode.IDLE
        private set

    private val decoderCount = AtomicInteger(0)

    val activeDecoders: Int get() = decoderCount.get()

    @Synchronized
    fun acquireFullscreen(): Boolean {
        if (mode == Mode.MULTIVIEW) {
            Timber.w("PlayerBudget: refuse fullscreen while multi-view is active")
            return false
        }
        if (mode == Mode.IDLE) {
            mode = Mode.FULLSCREEN
            decoderCount.set(1)
        }
        return true
    }

    @Synchronized
    fun acquirePreview(): Boolean {
        if (mode == Mode.MULTIVIEW) return false
        if (mode == Mode.IDLE) {
            mode = Mode.FULLSCREEN_PLUS_PREVIEW
            decoderCount.set(2)
            return true
        }
        if (mode == Mode.FULLSCREEN) {
            mode = Mode.FULLSCREEN_PLUS_PREVIEW
            decoderCount.set(2)
            return true
        }
        return mode == Mode.FULLSCREEN_PLUS_PREVIEW
    }

    @Synchronized
    fun releasePreview() {
        if (mode == Mode.FULLSCREEN_PLUS_PREVIEW) {
            mode = Mode.FULLSCREEN
            decoderCount.set(1)
        }
    }

    @Synchronized
    fun acquireMultiView(requested: Int): Int {
        if (mode == Mode.FULLSCREEN || mode == Mode.FULLSCREEN_PLUS_PREVIEW) {
            Timber.w("PlayerBudget: refuse multi-view while fullscreen/preview is live")
            return 0
        }
        val cap = if (LowRam.isLowRam) 4 else 9
        val granted = requested.coerceIn(1, cap)
        mode = Mode.MULTIVIEW
        decoderCount.set(granted)
        return granted
    }

    @Synchronized
    fun dropMultiViewTo(n: Int) {
        if (mode == Mode.MULTIVIEW) decoderCount.set(n.coerceAtLeast(1))
    }

    @Synchronized
    fun releaseMultiView() {
        if (mode == Mode.MULTIVIEW) {
            mode = Mode.IDLE
            decoderCount.set(0)
        }
    }

    @Synchronized
    fun releaseFullscreen() {
        if (mode == Mode.FULLSCREEN || mode == Mode.FULLSCREEN_PLUS_PREVIEW) {
            mode = Mode.IDLE
            decoderCount.set(0)
        }
    }

    @Synchronized
    fun emergencyReleaseAll() {
        mode = Mode.IDLE
        decoderCount.set(0)
    }
}
