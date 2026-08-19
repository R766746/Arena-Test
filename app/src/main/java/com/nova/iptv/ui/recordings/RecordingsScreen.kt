package com.nova.iptv.ui.recordings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
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
import com.nova.iptv.ui.components.ConfirmDialog
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    fun stop(id: String) = viewModelScope.launch { repo.stop(id) }
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
    var pendingDelete by remember { mutableStateOf<Recording?>(null) }
    var pendingStop by remember { mutableStateOf<Recording?>(null) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        NovaTopBar(settings.clock24h)
        Text(stringResource(R.string.cat_recordings), color = colors.onBackground, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        Text(stringResource(R.string.rec_quality_note), color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 28.dp))
        if (items.isEmpty()) {
            EmptyState(stringResource(R.string.empty_recordings), "")
        } else {
            TvLazyColumn(Modifier.fillMaxSize().padding(20.dp)) {
                items(items, key = { it.id }, contentType = { "recording" }) { rec ->
                    RecordingRow(
                        rec = rec,
                        clock24h = settings.clock24h,
                        nowMs = nowMs,
                        onPlay = { onPlay(PlayTarget.recording(rec.id, rec.title, rec.fileUri)) },
                        onStop = { pendingStop = rec },
                        onDelete = { pendingDelete = rec },
                    )
                }
            }
        }
    }
    pendingDelete?.let { recording ->
        val active = recording.status == RecordingStatus.SCHEDULED || recording.status == RecordingStatus.RECORDING
        ConfirmDialog(
            title = stringResource(if (active) R.string.recording_cancel_title else R.string.recording_delete_title),
            body = stringResource(if (active) R.string.recording_cancel_body else R.string.recording_delete_body, recording.title),
            confirmLabel = stringResource(if (active) R.string.rec_cancel else R.string.playlists_delete),
            dismissLabel = stringResource(R.string.cd_back),
            onConfirm = {
                pendingDelete = null
                vm.delete(recording.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
    pendingStop?.let { recording ->
        ConfirmDialog(
            title = stringResource(R.string.recording_stop_title),
            body = stringResource(R.string.recording_stop_body, recording.title),
            confirmLabel = stringResource(R.string.recording_stop_action),
            dismissLabel = stringResource(R.string.cd_back),
            onConfirm = {
                pendingStop = null
                vm.stop(recording.id)
            },
            onDismiss = { pendingStop = null },
        )
    }
}

@Composable
private fun RecordingRow(
    rec: Recording,
    clock24h: Boolean,
    nowMs: Long,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    val status = when (rec.status) {
        RecordingStatus.SCHEDULED -> stringResource(R.string.rec_scheduled)
        RecordingStatus.RECORDING -> stringResource(R.string.rec_recording)
        RecordingStatus.COMPLETED -> stringResource(R.string.rec_completed)
        RecordingStatus.FAILED -> stringResource(R.string.rec_failed)
    }
    val active = rec.status == RecordingStatus.SCHEDULED || rec.status == RecordingStatus.RECORDING
    val durationMs = (rec.endMs - rec.startMs).coerceAtLeast(1L)
    val elapsedMs = (nowMs - rec.startMs).coerceIn(0L, durationMs)
    val progress = when (rec.status) {
        RecordingStatus.SCHEDULED -> 0f
        RecordingStatus.RECORDING -> elapsedMs.toFloat() / durationMs.toFloat()
        RecordingStatus.COMPLETED -> 1f
        RecordingStatus.FAILED -> 0f
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.weight(1f)) {
            Text(rec.title, color = colors.onBackground, fontSize = 15.sp)
            Text(
                "$status  ·  ${TimeFmt.range(rec.startMs, rec.endMs, clock24h)}  ·  ${formatBytes(rec.bytes)}",
                color = colors.muted,
                fontSize = 12.sp,
            )
            if (active) {
                val timing = if (rec.status == RecordingStatus.SCHEDULED) {
                    stringResource(R.string.rec_starts_in, TimeFmt.duration((rec.startMs - nowMs).coerceAtLeast(0L)))
                } else {
                    stringResource(R.string.rec_elapsed, TimeFmt.duration(elapsedMs), TimeFmt.duration(durationMs))
                }
                Text(timing, color = colors.muted, fontSize = 12.sp)
            }
            if (active || rec.status == RecordingStatus.COMPLETED) {
                Spacer(Modifier.height(8.dp))
                RecordingProgressBar(progress)
            }
        }
        if (rec.status == RecordingStatus.COMPLETED && rec.fileUri.isNotBlank()) {
            FocusButton(stringResource(R.string.play), onPlay)
        }
        if (rec.status == RecordingStatus.RECORDING) {
            FocusButton(stringResource(R.string.recording_stop_action), onStop)
        }
        Spacer(Modifier.padding(4.dp))
        FocusButton(
            stringResource(if (active) R.string.rec_cancel else R.string.playlists_delete),
            onDelete,
        )
    }
}

@Composable
private fun RecordingProgressBar(progress: Float) {
    val colors = LocalNovaPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(colors.surface2),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .background(colors.accent),
        )
    }
}
