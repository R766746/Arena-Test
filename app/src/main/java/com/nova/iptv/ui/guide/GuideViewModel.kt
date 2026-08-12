package com.nova.iptv.ui.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.data.recordings.RecordingRepository
import com.nova.iptv.data.recordings.ReminderWorker
import com.nova.iptv.domain.model.AppSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.GuideRow
import com.nova.iptv.domain.model.Program
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuideUiState(
    val now: Long = System.currentTimeMillis(),
    val windowStart: Long = TimeFmt.floorToHalfHour(System.currentTimeMillis()) - 30 * 60_000L,
    val hours: Int = 4,
    val rows: List<GuideRow> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val selected: Program? = null,
    val selectedChannel: Channel? = null,
)

@HiltViewModel
class GuideViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    val settingsRepo: SettingsRepository,
    private val recordings: RecordingRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val now = MutableStateFlow(System.currentTimeMillis())
    private val windowStart = MutableStateFlow(TimeFmt.floorToHalfHour(System.currentTimeMillis()) - 30 * 60_000L)
    private val selected = MutableStateFlow<Program?>(null)
    private val selectedCh = MutableStateFlow<Channel?>(null)
    private val rows = MutableStateFlow<List<GuideRow>>(emptyList())

    val state: StateFlow<GuideUiState> = combine(
        combine(now, windowStart, settingsRepo.settings) { n, w, s -> Triple(n, w, s) },
        combine(selected, selectedCh, rows) { sel, ch, r -> Triple(sel, ch, r) },
    ) { a, b ->
        GuideUiState(a.first, a.second, a.third.epgHours, b.third, a.third, b.first, b.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideUiState())

    init {
        viewModelScope.launch {
            while (true) {
                reload()
                delay(30_000)
                now.value = System.currentTimeMillis()
            }
        }
    }

    private suspend fun reload() {
        val s = settingsRepo.settings.value
        val pid = s.lastPlaylistId.ifBlank { com.nova.iptv.domain.model.Playlist.DEMO_ID }
        val channels = playlists.snapshotChannels(pid).filter { !it.hidden }
        val from = windowStart.value
        val to = from + s.epgHours * 3600_000L
        val out = ArrayList<GuideRow>(channels.size)
        // Only bind what we need; VM still computes all rows but UI virtualizes.
        channels.forEach { ch ->
            val programs = epg.programsFor(ch.id, from, to)
            out += GuideRow(ch, programs, programs.firstOrNull { it.isNow(now.value) })
        }
        rows.value = out
    }

    fun shiftWindow(deltaHours: Int) {
        windowStart.value += deltaHours * 3600_000L
        viewModelScope.launch { reload() }
    }

    fun select(channel: Channel, program: Program?) {
        selectedCh.value = channel
        selected.value = program
    }

    fun dismiss() {
        selected.value = null
    }

    fun recordSelected() = viewModelScope.launch {
        val p = selected.value ?: return@launch
        val ch = selectedCh.value ?: return@launch
        recordings.schedule(p, ch.id, p.title)
        dismiss()
    }

    fun toggleFavorite() = viewModelScope.launch {
        selectedCh.value?.let { playlists.toggleFavorite(it.id) }
    }

    fun remind(channel: Channel, program: Program) {
        ReminderWorker.schedule(context, program.startMs, program.title, channel.name)
    }
}
