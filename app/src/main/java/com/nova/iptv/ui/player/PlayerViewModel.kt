package com.nova.iptv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import com.nova.iptv.core.util.newId
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.player.CatchupUrlBuilder
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.data.player.headersFor
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.data.recordings.RecordingRepository
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.AspectMode
import com.nova.iptv.domain.model.Episode
import com.nova.iptv.domain.model.NowNext
import com.nova.iptv.domain.model.Recording
import com.nova.iptv.domain.model.RecordingStatus
import com.nova.iptv.domain.model.WatchHistory
import com.nova.iptv.domain.model.WatchKind
import com.nova.iptv.domain.usecase.ZapChannelUseCase
import com.nova.iptv.nav.PlayTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val target: PlayTarget? = null,
    val channel: Channel? = null,
    val nowNext: NowNext = NowNext(null, null),
    val overlay: Boolean = true,
    val sheet: Boolean = false,
    val error: String? = null,
    val zapDigits: String = "",
    val isLive: Boolean = true,
    val isCatchup: Boolean = false,
    val clock24h: Boolean = true,
    val seekable: Boolean = false,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val bufferedPosition: Long = 0,
    val isBuffering: Boolean = false,
    val aspect: AspectMode = AspectMode.FIT,
    val subtitleSize: Int = 18,
    val hasPreviousEpisode: Boolean = false,
    val hasNextEpisode: Boolean = false,
    val nextEpisodeTitle: String = "",
    val nextEpisodeCountdown: Int? = null,
    val message: String? = null,
    val zapTransition: Long = 0,
    val quickZapVisible: Boolean = false,
    val activeRecordingId: String? = null,
    val recordingActionPending: Boolean = false,
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager,
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    val settings: SettingsRepository,
    private val zapUseCase: ZapChannelUseCase,
    private val catchupBuilder: CatchupUrlBuilder,
    private val recordings: RecordingRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state
    private var hideJob: Job? = null
    private var zapJob: Job? = null
    private var messageJob: Job? = null
    private var positionJob: Job? = null
    private var channelZapJob: Job? = null
    private var quickZapJob: Job? = null
    private var bindJob: Job? = null
    private var zapCursorChannel: Channel? = null
    private var pendingZapDelta = 0
    private var episodeQueue: List<Episode> = emptyList()
    private var episodeIndex: Int = -1
    private var autoNextJob: Job? = null
    private var recordingSnapshot: List<Recording> = emptyList()

    init {
        viewModelScope.launch {
            playerManager.error.collect { err ->
                _state.value = _state.value.copy(error = err)
            }
        }
        viewModelScope.launch {
            playerManager.isPlaying.collect { playing ->
                _state.value = _state.value.copy(isPlaying = playing)
            }
        }
        viewModelScope.launch {
            playerManager.completed.collect { target ->
                if (target.kind.equals("RECORDING", true) && settings.settings.value.recDeleteWatched) {
                    recordings.delete(target.id)
                } else if (target.kind.equals("SERIES", true) && settings.settings.value.autoPlayNextEpisode) {
                    startNextEpisodeCountdown()
                }
            }
        }
        viewModelScope.launch {
            recordings.observe().collect { items ->
                recordingSnapshot = items
                refreshRecordingState()
            }
        }
    }

    fun bind(target: PlayTarget, fromZap: Boolean = false) {
        autoNextJob?.cancel()
        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            val s = settings.settings.value
            var url = target.url
            var channel: Channel? = null
            if (!target.channelId.isNullOrBlank()) {
                channel = playlists.getChannel(target.channelId)
                if (url.isNullOrBlank()) url = channel?.streamUrl
            }
            if (target.kind.equals("MOVIE", true) || target.kind.equals("SERIES", true)) {
                if (url.isNullOrBlank()) {
                    url = playlists.getVod(target.id)?.streamUrl ?: playlists.getEpisode(target.id)?.streamUrl
                }
            }
            if (target.kind.equals("SERIES", true)) {
                val episode = playlists.getEpisode(target.id)
                if (episode != null) {
                    episodeQueue = playlists.episodes(episode.seriesId).first()
                    episodeIndex = episodeQueue.indexOfFirst { it.id == episode.id }
                } else {
                    episodeQueue = emptyList()
                    episodeIndex = -1
                }
            } else {
                episodeQueue = emptyList()
                episodeIndex = -1
            }
            if (target.kind.equals("CATCHUP", true) && channel != null && target.startMs != null) {
                val program = epg.programById(target.id)
                val pl = playlists.getPlaylist(channel.playlistId)
                val resolved = pl?.copy(passwordEnc = playlists.resolvedPassword(pl))
                if (program != null) {
                    url = catchupBuilder.build(channel, program, resolved) ?: url
                }
            }
            if (target.kind.equals("RECORDING", true) && url.isNullOrBlank()) {
                url = target.url
            }
            val nn = channel?.let { epg.nowAndNext(it.id) } ?: NowNext(null, null)
            if (!fromZap || zapCursorChannel == null) zapCursorChannel = channel
            val play = target.copy(url = url, title = target.title ?: channel?.name)
            val headers = headersFor(
                channel?.userAgent?.ifBlank { null } ?: playlists.getPlaylist(channel?.playlistId.orEmpty())?.userAgent,
                channel?.referrer,
            )
            _state.value = _state.value.copy(
                target = play,
                channel = channel,
                nowNext = nn,
                overlay = !fromZap,
                quickZapVisible = fromZap,
                isLive = play.kind.equals("LIVE", true),
                isCatchup = play.kind.equals("CATCHUP", true),
                clock24h = s.clock24h,
                seekable = !play.kind.equals("LIVE", true),
                aspect = s.aspect,
                subtitleSize = s.subtitleSize,
                hasPreviousEpisode = episodeIndex > 0,
                hasNextEpisode = episodeIndex >= 0 && episodeIndex < episodeQueue.lastIndex,
                nextEpisodeTitle = episodeQueue.getOrNull(episodeIndex + 1)?.title.orEmpty(),
                nextEpisodeCountdown = null,
                activeRecordingId = activeRecordingFor(channel, nn.now)?.id,
                error = null,
            )
            if (!url.isNullOrBlank()) {
                playerManager.play(play, headers)
                startPositionUpdates()
            }
            settings.update { it.copy(lastChannelId = channel?.id ?: it.lastChannelId) }
            if (fromZap) scheduleQuickZapHide() else bumpOverlay()
            playlists.recordWatch(
                WatchHistory(
                    id = newId(),
                    kind = play.watchKind,
                    refId = channel?.id ?: play.id,
                    title = play.title ?: channel?.name.orEmpty(),
                    subtitle = nn.now?.title.orEmpty(),
                    atMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun bumpOverlay() {
        quickZapJob?.cancel()
        _state.value = _state.value.copy(overlay = true, quickZapVisible = false)
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(settings.settings.value.overlayTimeoutSec * 1000L)
            if (!_state.value.sheet) _state.value = _state.value.copy(overlay = false)
        }
    }

    fun toggleSheet() {
        val open = !_state.value.sheet
        _state.value = _state.value.copy(sheet = open, overlay = true)
        if (!open) bumpOverlay()
    }

    fun zap(delta: Int) {
        pendingZapDelta += delta
        channelZapJob?.cancel()
        channelZapJob = viewModelScope.launch {
            // Collect remote key-repeat into one category-relative jump. This
            // avoids preparing and tearing down a decoder for every repeat event.
            delay(100)
            val jump = pendingZapDelta
            pendingZapDelta = 0
            val current = zapCursorChannel ?: _state.value.channel ?: return@launch
            val groupId = _state.value.target?.groupId
            val next = zapUseCase.next(current.playlistId, current.id, jump, groupId) ?: return@launch
            zapCursorChannel = next
            _state.value = _state.value.copy(zapTransition = System.nanoTime())
            bind(PlayTarget.live(next.id, next.name, next.number, next.streamUrl, groupId), fromZap = true)
        }
    }

    private fun scheduleQuickZapHide() {
        quickZapJob?.cancel()
        quickZapJob = viewModelScope.launch {
            delay(2_500)
            _state.value = _state.value.copy(quickZapVisible = false)
        }
    }

    fun hideOverlay() {
        hideJob?.cancel()
        _state.value = _state.value.copy(overlay = false)
    }

    fun togglePlayback() {
        val player = playerManager.mainPlayer()
        if (player.isPlaying) player.pause() else player.play()
        bumpOverlay()
    }

    fun seek(deltaMs: Long) {
        if (_state.value.seekable) {
            playerManager.seekBy(deltaMs)
            showTemporaryMessage(if (deltaMs < 0) "−${-deltaMs / 1000}s" else "+${deltaMs / 1000}s")
        }
        bumpOverlay()
    }

    fun previous() {
        if (_state.value.isLive) zap(-1)
        else if (episodeIndex > 0) playEpisodeAt(episodeIndex - 1)
        else playerManager.mainPlayer().seekTo(0)
        bumpOverlay()
    }

    fun next() {
        if (_state.value.isLive) zap(1)
        else if (episodeIndex in 0 until episodeQueue.lastIndex) playEpisodeAt(episodeIndex + 1)
        bumpOverlay()
    }

    fun cancelAutoNext() {
        autoNextJob?.cancel()
        _state.value = _state.value.copy(nextEpisodeCountdown = null)
        bumpOverlay()
    }

    fun playNextNow() {
        autoNextJob?.cancel()
        _state.value = _state.value.copy(nextEpisodeCountdown = null)
        if (episodeIndex in 0 until episodeQueue.lastIndex) playEpisodeAt(episodeIndex + 1)
    }

    private fun playEpisodeAt(index: Int) {
        val episode = episodeQueue.getOrNull(index) ?: return
        bind(PlayTarget.episode(episode.id, episode.title, episode.streamUrl))
    }

    private fun startNextEpisodeCountdown() {
        if (episodeIndex !in 0 until episodeQueue.lastIndex) return
        autoNextJob?.cancel()
        autoNextJob = viewModelScope.launch {
            for (seconds in 10 downTo 1) {
                _state.value = _state.value.copy(nextEpisodeCountdown = seconds, overlay = true)
                delay(1_000)
            }
            _state.value = _state.value.copy(nextEpisodeCountdown = null)
            playEpisodeAt(episodeIndex + 1)
        }
    }

    fun cycleAspect() {
        val current = _state.value.aspect
        val next = AspectMode.entries[(current.ordinal + 1) % AspectMode.entries.size]
        _state.value = _state.value.copy(aspect = next)
        viewModelScope.launch { settings.update { it.copy(aspect = next) } }
        playerManager.applyAspect(playerManager.mainPlayer())
        bumpOverlay()
    }

    fun showTimeshiftUnavailable(message: String) {
        showTemporaryMessage(message)
    }

    fun startOver(unavailableMessage: String) = viewModelScope.launch {
        val channel = _state.value.channel ?: return@launch showTemporaryMessage(unavailableMessage)
        val program = _state.value.nowNext.now ?: return@launch showTemporaryMessage(unavailableMessage)
        val playlist = playlists.getPlaylist(channel.playlistId)
        val resolved = playlist?.copy(passwordEnc = playlists.resolvedPassword(playlist))
        val url = catchupBuilder.build(channel, program, resolved, allowInProgress = true)
            ?: return@launch showTemporaryMessage(unavailableMessage)
        bind(
            PlayTarget.catchup(
                channelId = channel.id,
                programId = program.id,
                start = program.startMs,
                end = program.endMs,
                title = program.title,
                url = url,
            ),
        )
    }

    private fun showTemporaryMessage(message: String) {
        messageJob?.cancel()
        _state.value = _state.value.copy(message = message)
        messageJob = viewModelScope.launch {
            delay(3_000)
            _state.value = _state.value.copy(message = null)
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                val player = playerManager.mainPlayer()
                val duration = player.duration.takeUnless { it == C.TIME_UNSET || it < 0 } ?: 0L
                _state.value = _state.value.copy(
                    position = player.currentPosition.coerceAtLeast(0L),
                    duration = duration,
                    bufferedPosition = player.bufferedPosition.coerceAtLeast(0L),
                    isBuffering = player.playbackState == Player.STATE_BUFFERING,
                )
                delay(500)
            }
        }
    }

    fun digit(d: Int) {
        val next = (_state.value.zapDigits + d).take(4)
        _state.value = _state.value.copy(zapDigits = next, overlay = true)
        zapJob?.cancel()
        zapJob = viewModelScope.launch {
            delay(1200)
            val num = _state.value.zapDigits.toIntOrNull() ?: return@launch
            val pid = _state.value.channel?.playlistId ?: settings.settings.value.lastPlaylistId
            val ch = zapUseCase.byNumber(pid, num)
            _state.value = _state.value.copy(zapDigits = "")
            if (ch != null) bind(PlayTarget.live(ch.id, ch.name, ch.number, ch.streamUrl))
        }
    }

    fun toggleFavorite() = viewModelScope.launch {
        _state.value.channel?.id?.let { playlists.toggleFavorite(it) }
    }

    fun retry() = playerManager.retry()

    fun startRecording() = viewModelScope.launch {
        val current = _state.value
        if (current.recordingActionPending || !current.isLive) return@launch
        val channel = current.channel ?: return@launch
        val program = current.nowNext.now
            ?: return@launch showTemporaryMessage("No programme information available")

        _state.value = current.copy(recordingActionPending = true)
        try {
            val active = recordings.activeFor(channel.id, program.id)
            if (active != null) {
                _state.value = _state.value.copy(activeRecordingId = active.id)
                showTemporaryMessage("Already recording")
            } else {
                recordings.schedule(program, channel.id, program.title)
                    .onSuccess { recording ->
                        _state.value = _state.value.copy(activeRecordingId = recording.id)
                        showTemporaryMessage("Recording scheduled")
                    }
                    .onFailure {
                        showTemporaryMessage(
                            if (it.message == "no_space") "Not enough storage for this recording"
                            else "Could not start recording",
                        )
                    }
            }
        } finally {
            _state.value = _state.value.copy(recordingActionPending = false)
        }
    }

    fun stopRecording() = viewModelScope.launch {
        val current = _state.value
        if (current.recordingActionPending || !current.isLive) return@launch
        val channel = current.channel ?: return@launch
        val program = current.nowNext.now ?: return@launch
        _state.value = current.copy(recordingActionPending = true)
        try {
            val active = recordings.activeFor(channel.id, program.id) ?: return@launch
            recordings.stop(active.id)
            _state.value = _state.value.copy(activeRecordingId = null)
            showTemporaryMessage("Recording stopped")
        } finally {
            _state.value = _state.value.copy(recordingActionPending = false)
        }
    }

    private fun activeRecordingFor(channel: Channel?, program: com.nova.iptv.domain.model.Program?): Recording? {
        if (channel == null || program == null) return null
        return recordingSnapshot.firstOrNull {
            it.channelId == channel.id &&
                it.programId == program.id &&
                (it.status == RecordingStatus.SCHEDULED || it.status == RecordingStatus.RECORDING)
        }
    }

    private fun refreshRecordingState() {
        val current = _state.value
        val activeId = activeRecordingFor(current.channel, current.nowNext.now)?.id
        if (activeId != current.activeRecordingId) {
            _state.value = current.copy(activeRecordingId = activeId)
        }
    }
}
