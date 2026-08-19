package com.nova.iptv.data.player

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.nova.iptv.core.perf.LowRam
import com.nova.iptv.core.perf.PlayerBudget
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.domain.model.BufferSize
import com.nova.iptv.nav.PlayTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(markerClass = [UnstableApi::class])
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val budget: PlayerBudget,
    private val okHttp: OkHttpClient,
) {
    private var main: ExoPlayer? = null
    private var preview: ExoPlayer? = null
    private val multi = mutableListOf<ExoPlayer>()
    private var mainHttpFactory: OkHttpDataSource.Factory? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _target = MutableStateFlow<PlayTarget?>(null)
    val target: StateFlow<PlayTarget?> = _target

    private val _preferredDisplayModeId = MutableStateFlow(0)
    val preferredDisplayModeId: StateFlow<Int> = _preferredDisplayModeId

    private val _completed = MutableSharedFlow<PlayTarget>(extraBufferCapacity = 1)
    val completed: SharedFlow<PlayTarget> = _completed

    @Volatile var pip: Boolean = false
        private set

    fun setPip(value: Boolean) {
        pip = value
    }

    fun shouldEnterPip(): Boolean = main != null && (main?.isPlaying == true) && !pip

    fun mainPlayer(): ExoPlayer {
        budget.acquireFullscreen()
        return main ?: buildPlayer(preview = false).also { p ->
            main = p
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "main player error")
                    _error.value = error.message ?: "Playback failed"
                }
                override fun onTracksChanged(tracks: Tracks) {
                    applyAfr(p)
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _target.value?.let { _completed.tryEmit(it) }
                    }
                }
            })
        }
    }

    fun play(target: PlayTarget, headers: Map<String, String>) {
        _error.value = null
        _target.value = target
        val player = mainPlayer()
        mainHttpFactory?.setDefaultRequestProperties(headers)
        applyAspect(player)
        val item = mediaItem(target.url.orEmpty(), target.title.orEmpty(), headers)
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        applyAfr(player)
    }

    fun zap(url: String, title: String, headers: Map<String, String>) {
        _error.value = null
        val player = mainPlayer()
        mainHttpFactory?.setDefaultRequestProperties(headers)
        val item = mediaItem(url, title, headers)
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    fun previewPlayer(
        url: String,
        headers: Map<String, String>,
        autoPlay: Boolean = settings.settings.value.autoplayPreview,
    ): ExoPlayer? {
        if (!settings.settings.value.preview) return null
        if (budget.mode == PlayerBudget.Mode.MULTIVIEW) return null
        if (!budget.acquirePreview()) return null
        val p = preview ?: buildPlayer(preview = true).also { preview = it }
        p.volume = 0f
        p.setMediaItem(mediaItem(url, "preview", headers))
        p.prepare()
        p.playWhenReady = autoPlay
        return p
    }

    fun releasePreview() {
        preview?.runCatching {
            stop()
            release()
        }
        preview = null
        budget.releasePreview()
    }

    fun createMultiView(count: Int): List<ExoPlayer> {
        releasePreview()
        // Multi-view owns the decoder budget. Release a fullscreen decoder first
        // so navigation from Player cannot race the new tile players.
        if (budget.mode == PlayerBudget.Mode.FULLSCREEN) releaseMain()
        val granted = budget.acquireMultiView(count)
        if (granted == 0) return emptyList()
        releaseMultiViewInternal(keepBudget = true)
        repeat(granted) {
            multi += buildPlayer(preview = true)
        }
        return multi.toList()
    }

    fun playMultiView(
        player: ExoPlayer,
        url: String,
        title: String,
        headers: Map<String, String>,
    ) {
        player.setMediaItem(mediaItem(url, title, headers))
        player.prepare()
        player.playWhenReady = true
    }

    fun dropMultiViewTo(n: Int) {
        while (multi.size > n) {
            multi.removeAt(multi.lastIndex).release()
        }
        budget.dropMultiViewTo(multi.size)
    }

    fun releaseMultiView() {
        releaseMultiViewInternal(keepBudget = false)
        budget.releaseMultiView()
    }

    private fun releaseMultiViewInternal(keepBudget: Boolean) {
        multi.forEach { runCatching { it.release() } }
        multi.clear()
        if (!keepBudget) budget.releaseMultiView()
    }

    fun onActivityStop(configChange: Boolean) {
        releasePreview()
        releaseMultiView()
        if (!configChange && !pip) {
            main?.playWhenReady = false
        }
    }

    fun releaseMain() {
        main?.runCatching {
            stop()
            clearVideoSurface()
            clearMediaItems()
            release()
        }
        main = null
        _isPlaying.value = false
        _target.value = null
        _preferredDisplayModeId.value = 0
        budget.releaseFullscreen()
    }

    fun currentPreviewPlayer(): ExoPlayer? = preview

    /** Frees decoder/buffer pressure immediately while navigation disposes the view. */
    fun stopMain() {
        main?.runCatching {
            playWhenReady = false
            stop()
            clearMediaItems()
        }
        _isPlaying.value = false
    }

    fun retry() {
        val t = _target.value ?: return
        val url = t.url.orEmpty()
        if (url.isNotBlank()) {
            zap(url, t.title.orEmpty(), emptyMap())
        } else {
            main?.prepare()
            main?.playWhenReady = true
        }
    }

    fun seekBy(deltaMs: Long) {
        main?.let { it.seekTo((it.currentPosition + deltaMs).coerceAtLeast(0)) }
    }

    fun setAspectFit() = applyAspect(mainPlayer())

    fun applyAspect(player: ExoPlayer) {
        // Surface view scaling is applied by PlayerView / Compose wrapper using videoScalingMode.
        player.videoScalingMode = when (settings.settings.value.aspect) {
            com.nova.iptv.domain.model.AspectMode.FILL -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            else -> C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    fun currentTracks(): Tracks? = main?.currentTracks

    fun selectAudio(groupIndex: Int, trackIndex: Int) {
        val player = main ?: return
        val groups = player.currentTracks.groups
        if (groupIndex !in groups.indices) return
        val group = groups[groupIndex]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
            )
            .build()
    }

    fun selectSubtitle(groupIndex: Int, trackIndex: Int) {
        val player = main ?: return
        val groups = player.currentTracks.groups
        if (groupIndex !in groups.indices) return
        val group = groups[groupIndex]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
            )
            .build()
    }

    fun selectVideo(groupIndex: Int, trackIndex: Int) {
        val player = main ?: return
        val groups = player.currentTracks.groups
        if (groupIndex !in groups.indices) return
        val group = groups[groupIndex]
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
            )
            .build()
    }

    fun clearVideoOverride() {
        val player = main ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()
    }

    fun clearSubtitle() {
        val player = main ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun openExternal(url: String, packageName: String?): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(url), "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!packageName.isNullOrBlank()) setPackage(packageName)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun buildPlayer(preview: Boolean): ExoPlayer {
        val buffer = if (preview) BufferSize.SMALL else settings.settings.value.bufferSize
        val (min, max, play, rebuffer) = loadControlFor(buffer, preview)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(min, max, play, rebuffer)
            .build()
        val http = if (!preview) {
            mainHttpFactory ?: OkHttpDataSource.Factory(okHttp).also { mainHttpFactory = it }
        } else {
            OkHttpDataSource.Factory(okHttp)
        }
        http.setUserAgent("NOVA-IPTV")
        // Recordings use file:// or content:// URIs, while provider streams use HTTP.
        // DefaultDataSource selects the correct local source and delegates network
        // requests to the shared OkHttp factory.
        val dataSourceFactory = DefaultDataSource.Factory(context, http)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ !preview,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(settings.settings.value.seekStepSec * 1000L)
            .setSeekForwardIncrementMs(settings.settings.value.seekStepSec * 1000L)
            .build()
            .apply {
                videoChangeFrameRateStrategy =
                    if (settings.settings.value.afr) C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
                    else C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
                if (preview) volume = 0f
            }
    }

    private fun mediaItem(url: String, title: String, headers: Map<String, String>): MediaItem {
        val mime = when {
            url.contains(".mpd", true) -> MimeTypes.APPLICATION_MPD
            url.contains(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
            url.substringBefore('?').endsWith(".ts", true) -> MimeTypes.VIDEO_MP2T
            else -> null
        }
        val request = MediaItem.RequestMetadata.Builder()
            .setMediaUri(android.net.Uri.parse(url))
            .build()
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .setRequestMetadata(request)
        if (mime != null) builder.setMimeType(mime)
        if (headers.isNotEmpty()) {
            builder.setSubtitleConfigurations(emptyList())
        }
        return builder.build().also {
            // Attach headers via factory default properties for this play.
            applyDefaultHeaders(headers)
        }
    }

    private fun applyDefaultHeaders(headers: Map<String, String>) {
        // OkHttp interceptor in NetworkModule already can attach per-request; here we log only.
        if (headers.isNotEmpty()) Timber.d("stream headers: %s", headers.keys)
    }

    private fun applyAfr(player: ExoPlayer) {
        if (!settings.settings.value.afr) {
            _preferredDisplayModeId.value = 0
            return
        }
        val fmt = player.videoFormat ?: return
        val rate = fmt.frameRate
        if (rate <= 0f) return
        runCatching {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            if (Build.VERSION.SDK_INT >= 23) {
                val mode = display.supportedModes.minByOrNull { kotlin.math.abs(it.refreshRate - rate) }
                Timber.d("AFR prefer mode %s for %.2f fps", mode?.modeId, rate)
                _preferredDisplayModeId.value = mode?.modeId ?: 0
            }
        }
    }

    private fun loadControlFor(size: BufferSize, preview: Boolean): Quad {
        if (preview || LowRam.isLowRam) return Quad(2_000, 5_000, 500, 1_000)
        return when (size) {
            BufferSize.SMALL -> Quad(5_000, 15_000, 1_000, 2_000)
            BufferSize.MEDIUM -> Quad(15_000, 30_000, 1_500, 3_000)
            BufferSize.LARGE -> Quad(30_000, 60_000, 2_500, 5_000)
        }
    }

    private data class Quad(val min: Int, val max: Int, val play: Int, val rebuffer: Int)
}

fun headersFor(userAgent: String?, referrer: String?): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    if (!userAgent.isNullOrBlank()) out["User-Agent"] = userAgent
    if (!referrer.isNullOrBlank()) {
        out["Referer"] = referrer
        out["Origin"] = referrer.trimEnd('/').substringBeforeLast('/').let {
            runCatching { android.net.Uri.parse(referrer).let { u -> "${u.scheme}://${u.host}" } }.getOrDefault(referrer)
        }
    }
    return out
}
