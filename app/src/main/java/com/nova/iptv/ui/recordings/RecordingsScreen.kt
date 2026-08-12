package com.nova.iptv.ui.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.nova.iptv.R
import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.core.util.formatBytes
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.recordings.RecordingRepository
import com.nova.iptv.domain.model.Recording
import com.nova.iptv.domain.model.RecordingStatus
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.TvLazyColumn
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val repo: RecordingRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val items = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())
    val clock24h = settings.settings
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
}

@Composable
fun RecordingsRoute(
    onPlay: (PlayTarget) -> Unit,
    onBack: () -> Unit,
    vm: RecordingsViewModel = hiltViewModel(),
) {
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.clock24h.collectAsStateWithLifecycle()
    val colors = LocalNovaPalette.current
    Column(Modifier.fillMaxSize().background(colors.background)) {
        NovaTopBar(settings.clock24h)
        Text(stringResource(R.string.cat_recordings), color = colors.onBackground, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        Text(stringResource(R.string.rec_quality_note), color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 28.dp))
        if (items.isEmpty()) {
            EmptyState(stringResource(R.string.empty_recordings), "")
        } else {
            TvLazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
                items(items, key = { it.id }) { rec ->
                    RecordingRow(rec, settings.clock24h, { onPlay(PlayTarget.recording(rec.id, rec.title, rec.fileUri)) }, { vm.delete(rec.id) })
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(rec: Recording, clock24h: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalNovaPalette.current
    val status = when (rec.status) {
        RecordingStatus.SCHEDULED -> stringResource(R.string.rec_scheduled)
        RecordingStatus.RECORDING -> stringResource(R.string.rec_recording)
        RecordingStatus.COMPLETED -> stringResource(R.string.rec_completed)
        RecordingStatus.FAILED -> stringResource(R.string.rec_failed)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(rec.title, color = colors.onBackground, fontSize = 15.sp)
            Text(
                "$status  ·  ${TimeFmt.range(rec.startMs, rec.endMs, clock24h)}  ·  ${formatBytes(rec.bytes)}",
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
        if (rec.status == RecordingStatus.COMPLETED) {
            FocusButton(stringResource(R.string.play), onPlay)
        }
        Spacer(Modifier.padding(4.dp))
        FocusButton(stringResource(R.string.playlists_delete), onDelete)
    }
}
