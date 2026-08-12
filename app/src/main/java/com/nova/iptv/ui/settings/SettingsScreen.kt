package com.nova.iptv.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.nova.iptv.BuildConfig
import com.nova.iptv.R
import com.nova.iptv.core.perf.LowRam
import com.nova.iptv.core.perf.PlayerBudget
import com.nova.iptv.core.util.formatBytes
import com.nova.iptv.data.backup.BackupManager
import com.nova.iptv.data.epg.EpgRefreshWorker
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistImporter
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.AccentSwatches
import com.nova.iptv.domain.model.AnimSpeed
import com.nova.iptv.domain.model.AppSettings
import com.nova.iptv.domain.model.AspectMode
import com.nova.iptv.domain.model.BufferSize
import com.nova.iptv.domain.model.DiagnosticsSnapshot
import com.nova.iptv.domain.model.EpgRowHeight
import com.nova.iptv.domain.model.ListStyle
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.domain.model.StartupMode
import com.nova.iptv.domain.model.ThemeName
import com.nova.iptv.nav.TvLazyColumn
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.nav.glowBorderOnFocus
import com.nova.iptv.nav.scaleOnFocus
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.GlassPanel
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.components.PinDialog
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SettingsSection { PLAYLISTS, EPG, APPEARANCE, GUIDE, PLAYER, PARENTAL, RECORDINGS, GENERAL, ABOUT }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settings: SettingsRepository,
    val playlists: PlaylistRepository,
    private val importer: PlaylistImporter,
    private val epg: EpgRepository,
    private val backup: BackupManager,
    private val budget: PlayerBudget,
) : ViewModel() {
    val playlistsFlow = playlists.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())

    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { settings.update(t) }

    fun refresh(pl: Playlist) = viewModelScope.launch { importer.refresh(pl) }

    fun delete(id: String) = viewModelScope.launch { playlists.deletePlaylist(id) }

    fun setPin(pin: String) = viewModelScope.launch { settings.setPin(pin) }

    fun export(uri: Uri, omit: Boolean) = viewModelScope.launch { backup.export(uri, omit) }

    fun restore(uri: Uri) = viewModelScope.launch { backup.restore(uri) }

    fun refreshEpg(ctx: Context) = EpgRefreshWorker.enqueueNow(ctx)

    suspend fun diagnostics(): DiagnosticsSnapshot {
        val rt = Runtime.getRuntime()
        val size = playlistsFlow.value.sumOf { kotlinx.coroutines.flow.first(playlists.channels(it.id)).size }
        return DiagnosticsSnapshot(
            heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
            heapMaxMb = rt.maxMemory() / (1024 * 1024),
            decoderCount = budget.activeDecoders,
            lastEpgDurationMs = epg.lastIngestDurationMs(),
            playlistSize = size,
            lowRam = LowRam.isLowRam,
            previewActive = budget.mode.name.contains("PREVIEW"),
            multiViewActive = if (budget.mode.name == "MULTIVIEW") budget.activeDecoders else 0,
        )
    }
}

@Composable
fun SettingsRoute(
    onAddPlaylist: () -> Unit,
    onDiagnostics: () -> Unit,
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settings.settings.collectAsStateWithLifecycle()
    val lists by vm.playlistsFlow.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = settings,
        playlists = lists,
        onUpdate = vm::update,
        onAdd = onAddPlaylist,
        onRefresh = vm::refresh,
        onDelete = vm::delete,
        onPin = vm::setPin,
        verifyPin = vm.settings::verifyPin,
        onExport = vm::export,
        onRestore = vm::restore,
        onEpgNow = vm::refreshEpg,
        onDiagnostics = onDiagnostics,
        onReset = { vm.update { AppSettings() } },
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    playlists: List<Playlist>,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onAdd: () -> Unit,
    onRefresh: (Playlist) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String) -> Unit,
    verifyPin: (String) -> Boolean,
    onExport: (Uri, Boolean) -> Unit,
    onRestore: (Uri) -> Unit,
    onEpgNow: (Context) -> Unit,
    onDiagnostics: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    val ctx = LocalContext.current
    var section by remember { mutableStateOf(SettingsSection.PLAYLISTS) }
    var pinGate by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { onExport(it, true) }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onRestore(it) }
    }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            onUpdate { s -> s.copy(recTreeUri = it.toString()) }
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        NovaTopBar(settings.clock24h)
        Row(Modifier.weight(1f)) {
            TvLazyColumn(Modifier.width(240.dp).fillMaxHeight().padding(12.dp)) {
                items(SettingsSection.entries, key = { it.name }) { sec ->
                    val label = when (sec) {
                        SettingsSection.PLAYLISTS -> stringResource(R.string.sec_playlists)
                        SettingsSection.EPG -> stringResource(R.string.sec_epg)
                        SettingsSection.APPEARANCE -> stringResource(R.string.sec_appearance)
                        SettingsSection.GUIDE -> stringResource(R.string.sec_tv_guide)
                        SettingsSection.PLAYER -> stringResource(R.string.sec_player)
                        SettingsSection.PARENTAL -> stringResource(R.string.sec_parental)
                        SettingsSection.RECORDINGS -> stringResource(R.string.sec_recordings)
                        SettingsSection.GENERAL -> stringResource(R.string.sec_general)
                        SettingsSection.ABOUT -> stringResource(R.string.sec_about)
                    }
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .onFocusChanged { focused = it.isFocused }
                            .scaleOnFocus(1.02f)
                            .glowBorderOnFocus(radius = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (focused || section == sec) colors.accentDim else Color.Transparent)
                            .dpadClickable {
                                if (sec == SettingsSection.PARENTAL && settings.parentalEnabled && settings.lockSettings) {
                                    pinGate = true
                                } else section = sec
                            }
                            .padding(14.dp),
                    ) { Text(label, color = colors.onBackground, fontSize = 14.sp) }
                }
            }
            GlassPanel(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                Column(Modifier.padding(20.dp)) {
                    when (section) {
                        SettingsSection.PLAYLISTS -> PlaylistsPane(playlists, onAdd, onRefresh, onDelete, onUpdate)
                        SettingsSection.EPG -> {
                            ToggleRow("Update on start", settings.updateEpgOnStart) { onUpdate { s -> s.copy(updateEpgOnStart = it) } }
                            ToggleRow("Update on playlist change", settings.updateEpgOnPlaylistChange) { onUpdate { s -> s.copy(updateEpgOnPlaylistChange = it) } }
                            CycleRow("Timeline hours", listOf(2, 4, 6, 12), settings.epgHours) { onUpdate { s -> s.copy(epgHours = it) } }
                            CycleRow("Past days", listOf(1, 2, 3, 7), settings.epgPastDays) { onUpdate { s -> s.copy(epgPastDays = it) } }
                            CycleRow("Time shift (h)", listOf(-6, -3, 0, 1, 3, 6), settings.epgTimeShiftHours) { onUpdate { s -> s.copy(epgTimeShiftHours = it) } }
                            FocusButton("Refresh EPG now") { onEpgNow(ctx) }
                        }
                        SettingsSection.APPEARANCE -> {
                            CycleRow("Theme", ThemeName.entries.toList(), settings.theme) { onUpdate { s -> s.copy(theme = it) } }
                            Text("Accent", color = colors.muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
                            Row {
                                AccentSwatches.forEach { argb ->
                                    Box(
                                        Modifier
                                            .padding(6.dp)
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(argb))
                                            .glowBorderOnFocus(radius = 14.dp)
                                            .dpadClickable { onUpdate { s -> s.copy(accentArgb = argb) } },
                                    )
                                }
                            }
                            CycleRow("List style", ListStyle.entries.toList(), settings.listStyle) { onUpdate { s -> s.copy(listStyle = it) } }
                            ToggleRow("Channel numbers", settings.showNumbers) { onUpdate { s -> s.copy(showNumbers = it) } }
                            ToggleRow("24-hour clock", settings.clock24h) { onUpdate { s -> s.copy(clock24h = it) } }
                            CycleRow("Animation", AnimSpeed.entries.toList(), settings.animSpeed) { onUpdate { s -> s.copy(animSpeed = it) } }
                            CycleRow("Font scale", listOf(0.9f, 1.0f, 1.1f, 1.25f), settings.fontScale) { onUpdate { s -> s.copy(fontScale = it) } }
                        }
                        SettingsSection.GUIDE -> {
                            CycleRow("Hours", listOf(2, 4, 6, 12), settings.epgHours) { onUpdate { s -> s.copy(epgHours = it) } }
                            CycleRow("Row height", EpgRowHeight.entries.toList(), settings.epgRowHeight) { onUpdate { s -> s.copy(epgRowHeight = it) } }
                            ToggleRow("Grid lines", settings.epgGridLines) { onUpdate { s -> s.copy(epgGridLines = it) } }
                            ToggleRow("Preview", settings.preview) { onUpdate { s -> s.copy(preview = it) } }
                            ToggleRow("Autoplay preview", settings.autoplayPreview) { onUpdate { s -> s.copy(autoplayPreview = it) } }
                        }
                        SettingsSection.PLAYER -> {
                            CycleRow("Overlay (s)", listOf(3, 5, 8, 12), settings.overlayTimeoutSec) { onUpdate { s -> s.copy(overlayTimeoutSec = it) } }
                            CycleRow("Buffer", BufferSize.entries.toList(), settings.bufferSize) { onUpdate { s -> s.copy(bufferSize = it) } }
                            ToggleRow("Auto frame rate", settings.afr) { onUpdate { s -> s.copy(afr = it) } }
                            CycleRow("Aspect", AspectMode.entries.toList(), settings.aspect) { onUpdate { s -> s.copy(aspect = it) } }
                            CycleRow("Seek step", listOf(5, 10, 15, 30), settings.seekStepSec) { onUpdate { s -> s.copy(seekStepSec = it) } }
                            CycleRow("Subtitle size", listOf(14, 18, 22, 28), settings.subtitleSize) { onUpdate { s -> s.copy(subtitleSize = it) } }
                        }
                        SettingsSection.PARENTAL -> {
                            ToggleRow("Enable PIN", settings.parentalEnabled) { onUpdate { s -> s.copy(parentalEnabled = it) } }
                            FocusButton("Set PIN 0000") { onPin("0000") }
                            ToggleRow("Lock settings", settings.lockSettings) { onUpdate { s -> s.copy(lockSettings = it) } }
                        }
                        SettingsSection.RECORDINGS -> {
                            FocusButton(stringResource(R.string.rec_pick_folder)) { tree.launch(null) }
                            CycleRow("Padding (min)", listOf(0, 1, 3, 5, 10), settings.recPaddingMin) { onUpdate { s -> s.copy(recPaddingMin = it) } }
                            ToggleRow("Delete watched", settings.recDeleteWatched) { onUpdate { s -> s.copy(recDeleteWatched = it) } }
                            Text(stringResource(R.string.rec_quality_note), color = colors.muted, fontSize = 13.sp)
                        }
                        SettingsSection.GENERAL -> {
                            CycleRow("Startup", StartupMode.entries.toList(), settings.startupMode) { onUpdate { s -> s.copy(startupMode = it) } }
                            FocusButton(stringResource(R.string.backup_export)) { createDoc.launch("nova-backup.json") }
                            FocusButton(stringResource(R.string.backup_import)) { openDoc.launch(arrayOf("application/json", "*/*")) }
                            FocusButton(stringResource(R.string.reset_settings), onReset)
                        }
                        SettingsSection.ABOUT -> {
                            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = colors.onBackground, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.about_player_only), color = colors.muted, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                            Text("RAM ${formatBytes(mi.totalMem)}  lowRam=${LowRam.isLowRam}", color = colors.muted, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            FocusButton(stringResource(R.string.about_diagnostics), onDiagnostics)
                        }
                    }
                }
            }
        }
    }
    if (pinGate) {
        PinDialog(
            title = stringResource(R.string.parental_enter_pin),
            error = pinError,
            onSubmit = {
                if (verifyPin(it)) {
                    pinGate = false
                    section = SettingsSection.PARENTAL
                } else pinError = true
            },
            onDismiss = { pinGate = false },
        )
    }
}

@Composable
private fun PlaylistsPane(
    playlists: List<Playlist>,
    onAdd: () -> Unit,
    onRefresh: (Playlist) -> Unit,
    onDelete: (String) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val colors = LocalNovaPalette.current
    FocusButton(stringResource(R.string.playlists_add), onAdd)
    Spacer(Modifier.height(12.dp))
    playlists.forEach { pl ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pl.name, color = colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("${pl.type}  ·  ${pl.url.take(48)}", color = colors.muted, fontSize = 12.sp)
            }
            FocusButton(stringResource(R.string.playlists_update_now)) { onRefresh(pl) }
            Spacer(Modifier.width(8.dp))
            if (pl.type != PlaylistType.DEMO) {
                FocusButton(stringResource(R.string.playlists_delete)) { onDelete(pl.id) }
            }
        }
    }
}

@Composable
private fun <T> CycleRow(label: String, options: List<T>, value: T, onChange: (T) -> Unit) {
    val colors = LocalNovaPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .scaleOnFocus(1.02f)
            .glowBorderOnFocus(radius = 8.dp)
            .dpadClickable {
                val i = options.indexOf(value).let { if (it < 0) 0 else it }
                onChange(options[(i + 1) % options.size])
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value.toString(), color = colors.accent, fontSize = 14.sp)
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    CycleRow(label, listOf(false, true), value, onChange)
}

@Composable
fun DiagnosticsRoute(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    var snap by remember { mutableStateOf<DiagnosticsSnapshot?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { snap = vm.diagnostics() }
    val colors = LocalNovaPalette.current
    Column(Modifier.fillMaxSize().background(colors.background).padding(28.dp)) {
        NovaTopBar(true)
        Text("Diagnostics", color = colors.onBackground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        val s = snap
        if (s == null) Text("…", color = colors.muted)
        else {
            Text("Heap ${s.heapUsedMb} / ${s.heapMaxMb} MB", color = colors.onBackground, fontSize = 14.sp)
            Text("Decoders ${s.decoderCount}  preview=${s.previewActive}  multi=${s.multiViewActive}", color = colors.onBackground, fontSize = 14.sp)
            Text("Last EPG ${s.lastEpgDurationMs} ms", color = colors.onBackground, fontSize = 14.sp)
            Text("Playlist size ${s.playlistSize}", color = colors.onBackground, fontSize = 14.sp)
            Text("lowRam=${s.lowRam}", color = colors.onBackground, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        FocusButton("Back", onBack)
    }
}
