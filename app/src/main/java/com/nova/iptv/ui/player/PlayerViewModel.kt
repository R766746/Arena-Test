package com.nova.iptv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.iptv.core.util.newId
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.player.CatchupUrlBuilder
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.data.player.headersFor
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.data.recordings.RecordingRepository
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.NowNext
import com.nova.iptv.domain.model.WatchHistory
import com.nova.iptv.domain.model.WatchKind
import com.nova.iptv.domain.usecase.ZapChannelUseCase
import com.nova.iptv.nav.PlayTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val position: Long = 0,
    val duration: Long = 0,
    val message: String? = null,
    val zapTransition: Long = 0,
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

    init {
        viewModelScope.launch {
            playerManager.error.collect { err ->
                _state.value = _state.value.copy(error = err)
            }
        }
        viewModelScope.launch {
            playerManager.completed.collect { target ->
                if (target.kind.equals("RECORDING", true) && settings.settings.value.recDeleteWatched) {
                    recordings.delete(target.id)
                }
            }
        }
    }

    fun bind(target: PlayTarget) {
        viewModelScope.launch {
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
            val play = target.copy(url = url, title = target.title ?: channel?.name)
            val headers = headersFor(
                channel?.userAgent?.ifBlank { null } ?: playlists.getPlaylist(channel?.playlistId.orEmpty())?.userAgent,
                channel?.referrer,
            )
            _state.value = _state.value.copy(
                target = play,
                channel = channel,
                nowNext = nn,
                overlay = true,
                isLive = play.kind.equals("LIVE", true),
                isCatchup = play.kind.equals("CATCHUP", true),
                clock24h = s.clock24h,
                seekable = !play.kind.equals("LIVE", true),
                error = null,
            )
            if (!url.isNullOrBlank()) {
                playerManager.play(play, headers)
            }
            settings.update { it.copy(lastChannelId = channel?.id ?: it.lastChannelId) }
            bumpOverlay()
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
        _state.value = _state.value.copy(overlay = true)
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
        viewModelScope.launch {
            val current = _state.value.channel ?: return@launch
            val next = zapUseCase.next(current.playlistId, current.id, delta) ?: return@launch
            _state.value = _state.value.copy(zapTransition = System.nanoTime())
            bind(PlayTarget.live(next.id, next.name, next.number, next.streamUrl))
        }
    }

    fun showTimeshiftUnavailable(message: String) {
        messageJob?.cancel()
        _state.value = _state.value.copy(message = message)
        messageJob = viewModelScope.launch {
            delay(3_000)
            _state.value = _state.value.copy(message = null)
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

    fun recordNow() = viewModelScope.launch {
        val ch = _state.value.channel ?: return@launch
        val p = _state.value.nowNext.now ?: return@launch
        recordings.schedule(p, ch.id, p.title)
    }
}
