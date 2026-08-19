package com.nova.iptv.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.work.WorkInfo
import androidx.tv.material3.Text
import com.nova.iptv.BuildConfig
import com.nova.iptv.R
import com.nova.iptv.core.perf.LowRam
import com.nova.iptv.core.perf.PlayerBudget
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
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
import com.nova.iptv.domain.model.PosterSize
import com.nova.iptv.domain.model.AspectMode
import com.nova.iptv.domain.model.BufferSize
import com.nova.iptv.domain.model.DiagnosticsSnapshot
import kotlinx.coroutines.flow.first
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
import com.nova.iptv.ui.components.ConfirmDialog
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.GlassPanel
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.components.PinDialog
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import javax.inject.Inject

enum class SettingsSection { PLAYLISTS, EPG, APPEARANCE, GUIDE, PLAYER, PARENTAL, RECORDINGS, GENERAL, ABOUT }

data class EpgRefreshUi(val message: String = "Ready", val active: Boolean = false)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val settings: SettingsRepository,
    val playlists: PlaylistRepository,
    private val importer: PlaylistImporter,
    private val epg: EpgRepository,
    private val backup: BackupManager,
    private val budget: PlayerBudget,
    private val imageLoader: ImageLoader,
) : ViewModel() {
    private val updateQueue = Channel<AppSettings>(Channel.CONFLATED)
    private val updateLock = Any()
    private var pendingSettings: AppSettings? = null
    val playlistsFlow = playlists.playlists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())
    val groups = settings.settings.flatMapLatest {
        playlists.groups(it.lastPlaylistId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())
    val epgRefresh = EpgRefreshWorker.observeNow(appContext).map { work ->
        when (work?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> EpgRefreshUi("EPG refresh queued…", true)
            WorkInfo.State.RUNNING -> EpgRefreshUi(
                work.progress.getString(EpgRefreshWorker.KEY_MESSAGE) ?: "Downloading and importing EPG…",
                true,
            )
            WorkInfo.State.SUCCEEDED -> {
                val count = work.outputData.getInt(EpgRefreshWorker.KEY_IMPORTED, 0)
                val message = work.outputData.getString(EpgRefreshWorker.KEY_MESSAGE) ?: "EPG refresh complete"
                EpgRefreshUi(if (count > 0) "$message · $count programmes" else message)
            }
            WorkInfo.State.FAILED -> EpgRefreshUi("EPG refresh failed. Check the source or provider.")
            WorkInfo.State.CANCELLED -> EpgRefreshUi("EPG refresh cancelled")
            null -> EpgRefreshUi()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), EpgRefreshUi())

    init {
        viewModelScope.launch {
            for (snapshot in updateQueue) {
                settings.update { snapshot }
                synchronized(updateLock) {
                    if (pendingSettings == snapshot) pendingSettings = null
                }
            }
        }
    }

    fun update(t: (AppSettings) -> AppSettings) {
        // Settings changes can arrive faster than DataStore fsync on TV flash.
        // Merge into one newest complete snapshot instead of accumulating jobs.
        val snapshot = synchronized(updateLock) {
            t(pendingSettings ?: settings.settings.value).also { pendingSettings = it }
        }
        updateQueue.trySend(snapshot)
    }

    fun refresh(pl: Playlist) = viewModelScope.launch { importer.refresh(pl) }

    fun delete(id: String) = viewModelScope.launch { playlists.deletePlaylist(id) }

    fun setPin(pin: String) = viewModelScope.launch { settings.setPin(pin) }

    fun export(uri: Uri, omit: Boolean) = viewModelScope.launch { backup.export(uri, omit) }

    fun restore(uri: Uri) = viewModelScope.launch { backup.restore(uri) }

    fun refreshEpg(ctx: Context) = EpgRefreshWorker.enqueueNow(ctx)

    @OptIn(ExperimentalCoilApi::class)
    fun clearCaches() = viewModelScope.launch {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        epg.clearCache()
    }

    @OptIn(ExperimentalCoilApi::class)
    suspend fun diagnostics(): DiagnosticsSnapshot {
        val rt = Runtime.getRuntime()
        val allPlaylists = playlists.playlists().first()
        val channelCount = allPlaylists.sumOf { playlists.channelCount(it.id).first() }
        val movieCount = allPlaylists.sumOf { playlists.vodGroups(it.id, "MOVIE").first().sumOf { group -> group.count } }
        val seriesCount = allPlaylists.sumOf { playlists.vodGroups(it.id, "SERIES").first().sumOf { group -> group.count } }
        val dbFile = appContext.getDatabasePath("nova.db")
        val databaseBytes = listOf(dbFile, java.io.File(dbFile.path + "-wal"), java.io.File(dbFile.path + "-shm"))
            .sumOf { if (it.exists()) it.length() else 0L }
        val imageBytes = (imageLoader.memoryCache?.size?.toLong() ?: 0L) +
            (imageLoader.diskCache?.size ?: 0L)
        return DiagnosticsSnapshot(
            heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024),
            heapMaxMb = rt.maxMemory() / (1024 * 1024),
            decoderCount = budget.activeDecoders,
            lastEpgDurationMs = epg.lastIngestDurationMs(),
            playlistSize = channelCount + movieCount + seriesCount,
            lowRam = LowRam.isLowRam,
            previewActive = budget.mode.name.contains("PREVIEW"),
            multiViewActive = if (budget.mode.name == "MULTIVIEW") budget.activeDecoders else 0,
            channelCount = channelCount,
            movieCount = movieCount,
            seriesCount = seriesCount,
            databaseMb = databaseBytes / (1024 * 1024),
            imageCacheMb = imageBytes / (1024 * 1024),
            physicalRamMb = LowRam.totalMemMb,
            heapClassMb = LowRam.heapClassMb,
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
    val groups by vm.groups.collectAsStateWithLifecycle()
    val epgRefresh by vm.epgRefresh.collectAsStateWithLifecycle()
    SettingsScreen(
        settings = settings,
        playlists = lists,
        groups = groups,
        epgRefresh = epgRefresh,
        onUpdate = vm::update,
        onAdd = onAddPlaylist,
        onRefresh = vm::refresh,
        onDelete = vm::delete,
        onPin = vm::setPin,
        verifyPin = vm.settings::verifyPin,
        onExport = vm::export,
        onRestore = vm::restore,
        onEpgNow = vm::refreshEpg,
        onClearCaches = vm::clearCaches,
        onDiagnostics = onDiagnostics,
        onReset = { vm.update { AppSettings() } },
    )
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    playlists: List<Playlist>,
    groups: List<String>,
    epgRefresh: EpgRefreshUi,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onAdd: () -> Unit,
    onRefresh: (Playlist) -> Unit,
    onDelete: (String) -> Unit,
    onPin: (String) -> Unit,
    verifyPin: (String) -> Boolean,
    onExport: (Uri, Boolean) -> Unit,
    onRestore: (Uri) -> Unit,
    onEpgNow: (Context) -> Unit,
    onClearCaches: () -> Unit,
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
    val hasFolderPicker = remember(ctx) {
        ctx.packageManager
            .queryIntentActivities(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName != "com.android.tv.frameworkpackagestubs" }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        NovaTopBar(settings.clock24h)
        Row(Modifier.weight(1f)) {
            TvLazyColumn(Modifier.width(240.dp).fillMaxHeight().padding(12.dp)) {
                items(SettingsSection.entries, key = { it.name }, contentType = { "settings-section" }) { sec ->
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
                            ToggleRow(stringResource(R.string.epg_update_start), settings.updateEpgOnStart) { onUpdate { s -> s.copy(updateEpgOnStart = it) } }
                            ToggleRow(stringResource(R.string.epg_update_playlist), settings.updateEpgOnPlaylistChange) { onUpdate { s -> s.copy(updateEpgOnPlaylistChange = it) } }
                            CycleRow(stringResource(R.string.guide_hours), listOf(2, 4, 6, 12), settings.epgHours) { onUpdate { s -> s.copy(epgHours = it) } }
                            CycleRow(stringResource(R.string.epg_past_days), listOf(1, 2, 3, 7), settings.epgPastDays) { onUpdate { s -> s.copy(epgPastDays = it) } }
                            CycleRow(stringResource(R.string.epg_timeshift), listOf(-6, -3, 0, 1, 3, 6), settings.epgTimeShiftHours) { onUpdate { s -> s.copy(epgTimeShiftHours = it) } }
                            FocusButton(label = stringResource(R.string.epg_refresh_now), onClick = { onEpgNow(ctx) })
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (epgRefresh.active) {
                                    com.nova.iptv.ui.components.CenteredProgressIndicator(Modifier.size(28.dp))
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(
                                    epgRefresh.message,
                                    color = if (epgRefresh.active) colors.accent else colors.muted,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        SettingsSection.APPEARANCE -> {
                            CycleRow(stringResource(R.string.appearance_theme), ThemeName.entries.toList(), settings.theme) { onUpdate { s -> s.copy(theme = it) } }
                            Text(stringResource(R.string.accent_color), color = colors.muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
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
                            CycleRow(stringResource(R.string.list_style), ListStyle.entries.toList(), settings.listStyle) { onUpdate { s -> s.copy(listStyle = it) } }
                            CycleRow(stringResource(R.string.poster_size), PosterSize.entries.toList(), settings.posterSize) { onUpdate { s -> s.copy(posterSize = it) } }
                            ToggleRow(stringResource(R.string.show_numbers), settings.showNumbers) { onUpdate { s -> s.copy(showNumbers = it) } }
                            ToggleRow(stringResource(R.string.clock_24h), settings.clock24h) { onUpdate { s -> s.copy(clock24h = it) } }
                            CycleRow(stringResource(R.string.anim_speed), AnimSpeed.entries.toList(), settings.animSpeed) { onUpdate { s -> s.copy(animSpeed = it) } }
                            CycleRow(stringResource(R.string.font_scale), listOf(0.9f, 1.0f, 1.1f, 1.25f), settings.fontScale) { onUpdate { s -> s.copy(fontScale = it) } }
                        }
                        SettingsSection.GUIDE -> {
                            CycleRow(stringResource(R.string.guide_hours), listOf(2, 4, 6, 12), settings.epgHours) { onUpdate { s -> s.copy(epgHours = it) } }
                            CycleRow(stringResource(R.string.guide_row_height), EpgRowHeight.entries.toList(), settings.epgRowHeight) { onUpdate { s -> s.copy(epgRowHeight = it) } }
                            ToggleRow(stringResource(R.string.guide_grid_lines), settings.epgGridLines) { onUpdate { s -> s.copy(epgGridLines = it) } }
                            ToggleRow(stringResource(R.string.guide_preview), settings.preview) { onUpdate { s -> s.copy(preview = it) } }
                            ToggleRow(stringResource(R.string.guide_autoplay), settings.autoplayPreview) { onUpdate { s -> s.copy(autoplayPreview = it) } }
                        }
                        SettingsSection.PLAYER -> {
                            CycleRow(stringResource(R.string.player_overlay_timeout), listOf(3, 5, 8, 12), settings.overlayTimeoutSec) { onUpdate { s -> s.copy(overlayTimeoutSec = it) } }
                            CycleRow(stringResource(R.string.player_buffer), BufferSize.entries.toList(), settings.bufferSize) { onUpdate { s -> s.copy(bufferSize = it) } }
                            ToggleRow(stringResource(R.string.player_afr), settings.afr) { onUpdate { s -> s.copy(afr = it) } }
                            CycleRow(stringResource(R.string.player_aspect), AspectMode.entries.toList(), settings.aspect) { onUpdate { s -> s.copy(aspect = it) } }
                            CycleRow(stringResource(R.string.player_seek), listOf(5, 10, 15, 30), settings.seekStepSec) { onUpdate { s -> s.copy(seekStepSec = it) } }
                            CycleRow(stringResource(R.string.player_sub_size), listOf(14, 18, 22, 28), settings.subtitleSize) { onUpdate { s -> s.copy(subtitleSize = it) } }
                            ToggleRow("Auto-play next episode", settings.autoPlayNextEpisode) { onUpdate { s -> s.copy(autoPlayNextEpisode = it) } }
                        }
                        SettingsSection.PARENTAL -> {
                            ToggleRow(stringResource(R.string.parental_enable), settings.parentalEnabled) { onUpdate { s -> s.copy(parentalEnabled = it) } }
                            FocusButton(label = stringResource(R.string.parental_set_default), onClick = { onPin("0000") })
                            ToggleRow(stringResource(R.string.parental_lock_settings), settings.lockSettings) { onUpdate { s -> s.copy(lockSettings = it) } }
                            Text(stringResource(R.string.parental_locked_groups), color = colors.muted, fontSize = 12.sp)
                            groups.forEach { group ->
                                ToggleRow(group, group in settings.lockedGroups) { locked ->
                                    onUpdate { s ->
                                        s.copy(lockedGroups = if (locked) s.lockedGroups + group else s.lockedGroups - group)
                                    }
                                }
                            }
                        }
                        SettingsSection.RECORDINGS -> {
                            FocusButton(
                                label = stringResource(if (hasFolderPicker) R.string.rec_pick_folder else R.string.rec_use_app_folder),
                                onClick = {
                                    if (hasFolderPicker) tree.launch(null)
                                    else onUpdate { s -> s.copy(recTreeUri = "") }
                                },
                            )
                            Text(
                                stringResource(
                                    when {
                                        settings.recTreeUri.isNotBlank() -> R.string.rec_external_folder_selected
                                        hasFolderPicker -> R.string.rec_app_folder_selected
                                        else -> R.string.rec_folder_picker_unavailable
                                    },
                                ),
                                color = colors.muted,
                                fontSize = 12.sp,
                            )
                            CycleRow(stringResource(R.string.rec_padding), listOf(0, 1, 3, 5, 10), settings.recPaddingMin) { onUpdate { s -> s.copy(recPaddingMin = it) } }
                            ToggleRow(stringResource(R.string.rec_delete_watched), settings.recDeleteWatched) { onUpdate { s -> s.copy(recDeleteWatched = it) } }
                            Text(stringResource(R.string.rec_quality_note), color = colors.muted, fontSize = 13.sp)
                        }
                        SettingsSection.GENERAL -> {
                            CycleRow(stringResource(R.string.startup_mode), StartupMode.entries.toList(), settings.startupMode) { onUpdate { s -> s.copy(startupMode = it) } }
                            FocusButton(label = stringResource(R.string.backup_export), onClick = { createDoc.launch("nova-backup.json") })
                            FocusButton(label = stringResource(R.string.backup_import), onClick = { openDoc.launch(arrayOf("application/json", "*/*")) })
                            FocusButton(label = stringResource(R.string.clear_cache), onClick = onClearCaches)
                            FocusButton(label = stringResource(R.string.reset_settings), onClick = onReset)
                        }
                        SettingsSection.ABOUT -> {
                            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), color = colors.onBackground, fontSize = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.about_player_only), color = colors.muted, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                            Text(stringResource(R.string.about_memory_status, formatBytes(mi.totalMem), LowRam.isLowRam), color = colors.muted, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            FocusButton(label = stringResource(R.string.about_diagnostics), onClick = onDiagnostics)
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
    var pendingDelete by remember { mutableStateOf<Playlist?>(null) }
    FocusButton(label = stringResource(R.string.playlists_add), onClick = onAdd)
    Spacer(Modifier.height(12.dp))
    playlists.forEach { pl ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pl.name, color = colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("${pl.type}  ·  ${pl.url.take(48)}", color = colors.muted, fontSize = 12.sp)
            }
            FocusButton(label = stringResource(R.string.playlists_update_now), onClick = { onRefresh(pl) })
            Spacer(Modifier.width(8.dp))
            FocusButton(label = stringResource(R.string.playlists_delete), onClick = { pendingDelete = pl })
        }
    }
    pendingDelete?.let { playlist ->
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_title),
            body = stringResource(R.string.playlist_delete_body, playlist.name),
            confirmLabel = stringResource(R.string.playlists_delete),
            onConfirm = {
                pendingDelete = null
                onDelete(playlist.id)
            },
            onDismiss = { pendingDelete = null },
        )
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
    var refresh by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(refresh) { snap = vm.diagnostics() }
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
            Text("Channels ${s.channelCount}  Movies ${s.movieCount}  Series ${s.seriesCount}", color = colors.onBackground, fontSize = 14.sp)
            Text("Database ${s.databaseMb} MB  Image cache ${s.imageCacheMb} MB", color = colors.onBackground, fontSize = 14.sp)
            Text("Physical RAM ${s.physicalRamMb} MB  App heap class ${s.heapClassMb} MB", color = colors.onBackground, fontSize = 14.sp)
            Text("lowRam=${s.lowRam}", color = colors.onBackground, fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        FocusButton(label = "Refresh", onClick = { refresh++ })
        FocusButton(label = "Back", onClick = onBack)
    }
}
