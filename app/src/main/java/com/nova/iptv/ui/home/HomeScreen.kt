@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.nova.iptv.ui.home

import android.view.KeyEvent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.nova.iptv.R
import com.nova.iptv.core.perf.LowRam
import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.data.playlist.PlaylistImporter
import com.nova.iptv.data.player.headersFor
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.CatalogSort
import com.nova.iptv.domain.model.HomeCategory
import com.nova.iptv.domain.model.ImportProgress
import com.nova.iptv.domain.model.ListStyle
import com.nova.iptv.domain.model.NowNext
import com.nova.iptv.domain.model.PosterSize
import com.nova.iptv.domain.model.Program
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.Routes
import com.nova.iptv.nav.TvLazyColumn
import com.nova.iptv.nav.TvLazyVerticalGrid
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.nav.glowBorderOnFocus
import com.nova.iptv.nav.rememberFocusRestorer
import com.nova.iptv.nav.scaleOnFocus
import com.nova.iptv.ui.components.CategoryIcon
import com.nova.iptv.ui.components.CenteredProgressIndicator
import com.nova.iptv.ui.components.ChannelRow
import com.nova.iptv.ui.components.ConfirmDialog
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.GlassPanel
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.components.PinDialog
import com.nova.iptv.ui.components.PosterCard
import com.nova.iptv.ui.components.categoryLabel
import com.nova.iptv.ui.theme.LocalNovaPalette
import com.nova.iptv.ui.theme.PaneCategories
import com.nova.iptv.ui.theme.PaneGroups
import com.nova.iptv.ui.theme.PanePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.delay

private enum class VodSort(val label: String) {
    PROVIDER("Provider"), TITLE("A–Z"), NEWEST("Newest")
}

@HiltViewModel
class HomePlayerBridge @Inject constructor(
    val playerManager: PlayerManager,
    val settings: com.nova.iptv.data.local.SettingsRepository,
    val importer: PlaylistImporter,
) : ViewModel()

@Composable
fun HomeRoute(
    onNavigate: (String) -> Unit,
    onPlay: (PlayTarget) -> Unit,
    onDetail: (String, String) -> Unit,
    openFavoritesOnStart: Boolean = false,
    vm: HomeViewModel = hiltViewModel(),
    bridge: HomePlayerBridge = hiltViewModel(),
) {
    var startupDestinationApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openFavoritesOnStart, startupDestinationApplied) {
        if (openFavoritesOnStart && !startupDestinationApplied) {
            startupDestinationApplied = true
            vm.openLiveFavorites()
        }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val movieItems = vm.moviesPaging.collectAsLazyPagingItems()
    val seriesItems = vm.seriesPaging.collectAsLazyPagingItems()
    val liveItems = vm.livePaging.collectAsLazyPagingItems()
    val syncProgress by bridge.importer.syncProgress.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        movieItems = movieItems,
        seriesItems = seriesItems,
        liveItems = liveItems,
        onMovieBrowserChange = vm::updateMovieBrowser,
        onSeriesBrowserChange = vm::updateSeriesBrowser,
        onLiveBrowserChange = vm::updateLiveBrowser,
        onCategory = { cat ->
            when (cat) {
                HomeCategory.GUIDE -> onNavigate(Routes.Guide)
                HomeCategory.RECORDINGS -> onNavigate(Routes.Recordings)
                HomeCategory.MULTIVIEW -> onNavigate(Routes.MultiView)
                HomeCategory.SEARCH -> onNavigate(Routes.Search)
                HomeCategory.SETTINGS -> onNavigate(Routes.Settings)
                else -> vm.selectCategory(cat)
            }
        },
        onGroup = vm::selectGroup,
        onChannelFocus = vm::focusChannel,
        onChannelVisible = vm::prefetchChannelEpg,
        onPreviewChannel = vm::selectPreviewChannel,
        onWatch = { ch ->
            onPlay(PlayTarget.live(ch.id, ch.name, ch.number, ch.streamUrl, state.groupId))
        },
        onCatchup = { ch, program ->
            onPlay(PlayTarget.catchup(ch.id, program.id, program.startMs, program.endMs, program.title, null))
        },
        onRecordProgram = vm::recordProgram,
        onStopRecording = vm::stopRecording,
        onRemindProgram = vm::remindProgram,
        onVod = { item ->
            onDetail(if (item.kind.name == "SERIES") "series" else "movie", item.id)
        },
        onToggleFav = vm::toggleFavorite,
        onHide = vm::hideChannel,
        onAssignEpg = vm::assignEpg,
        onSearchEpg = vm::searchEpgNames,
        onAddPlaylist = { onNavigate(Routes.AddPlaylist) },
        playerManager = bridge.playerManager,
        verifyPin = { pin -> bridge.settings.verifyPin(pin) },
        syncProgress = syncProgress,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    movieItems: LazyPagingItems<VodItem>? = null,
    seriesItems: LazyPagingItems<VodItem>? = null,
    liveItems: LazyPagingItems<Channel>? = null,
    onMovieBrowserChange: (String, CatalogSort) -> Unit = { _, _ -> },
    onSeriesBrowserChange: (String, CatalogSort) -> Unit = { _, _ -> },
    onLiveBrowserChange: (String, CatalogSort) -> Unit = { _, _ -> },
    onCategory: (HomeCategory) -> Unit,
    onGroup: (String) -> Unit,
    onChannelFocus: (String?) -> Unit,
    onChannelVisible: (String) -> Unit,
    onPreviewChannel: (String) -> Unit,
    onWatch: (Channel) -> Unit,
    onCatchup: (Channel, Program) -> Unit,
    onRecordProgram: (Channel, Program) -> Unit,
    onStopRecording: (Channel, Program) -> Unit,
    onRemindProgram: (Channel, Program) -> Unit,
    onVod: (VodItem) -> Unit,
    onToggleFav: (String) -> Unit,
    onHide: (String) -> Unit,
    onAssignEpg: (String, String) -> Unit,
    onSearchEpg: (String, (List<Pair<String, String>>) -> Unit) -> Unit,
    onAddPlaylist: () -> Unit,
    playerManager: PlayerManager,
    verifyPin: (String) -> Boolean,
    syncProgress: ImportProgress?,
) {
    val colors = LocalNovaPalette.current
    val catFocus = rememberFocusRestorer()
    val groupFocus = rememberFocusRestorer()
    val contentFocus = rememberFocusRestorer()
    var actionsFor by remember { mutableStateOf<Channel?>(null) }
    var assignFor by remember { mutableStateOf<Channel?>(null) }
    var pinFor by remember { mutableStateOf<Channel?>(null) }
    var pinError by remember { mutableStateOf(false) }
    var selectedProgram by remember { mutableStateOf<Pair<Channel, Program>?>(null) }
    var panelsHidden by rememberSaveable { mutableStateOf(false) }
    var restoreGroupRequest by rememberSaveable { mutableStateOf(0) }
    var returnChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnChannelIndex by rememberSaveable { mutableIntStateOf(-1) }
    // Keep the actively selected channel independent from the currently visible
    // category. Browsing another Live group must not stop or replace playback.
    var previewVodId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingVodId by remember { mutableStateOf<String?>(null) }
    var returnVodId by rememberSaveable { mutableStateOf<String?>(null) }
    var returnVodIndex by rememberSaveable { mutableIntStateOf(-1) }
    var vodQuery by rememberSaveable { mutableStateOf("") }
    var vodSort by rememberSaveable { mutableStateOf(VodSort.PROVIDER) }
    var liveQuery by rememberSaveable { mutableStateOf("") }
    var liveSort by rememberSaveable { mutableStateOf(VodSort.PROVIDER) }
    val groupListState = rememberLazyListState()
    LaunchedEffect(pendingVodId) {
        val id = pendingVodId ?: return@LaunchedEffect
        // Focus is already on the card. Defer hero artwork and network/database
        // work until key-repeat pauses so navigation always wins the frame.
        delay(350)
        previewVodId = id
    }
    LaunchedEffect(state.category, state.groupId, vodQuery, vodSort) {
        when (state.category) {
            HomeCategory.MOVIES -> onMovieBrowserChange(vodQuery, CatalogSort.valueOf(vodSort.name))
            HomeCategory.SERIES -> onSeriesBrowserChange(vodQuery, CatalogSort.valueOf(vodSort.name))
            else -> Unit
        }
    }
    LaunchedEffect(state.category, state.groupId, liveQuery, liveSort) {
        if (state.category == HomeCategory.LIVE) {
            onLiveBrowserChange(liveQuery, CatalogSort.valueOf(liveSort.name))
        }
    }
    val activeVodItems = if (state.category == HomeCategory.MOVIES) movieItems else seriesItems
    val focusedVod = activeVodItems?.itemSnapshotList?.items?.firstOrNull { it.id == previewVodId }
        ?: activeVodItems?.itemSnapshotList?.items?.firstOrNull()
    val showPreview = state.settings.preview && state.category == HomeCategory.LIVE

    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (!LowRam.isLowRam && focusedVod != null && focusedVod.backdropUrl.isNotBlank()) {
            AsyncImage(
                model = focusedVod.backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(colors.background.copy(0.55f), colors.background)),
            ),
        )
        Column(Modifier.fillMaxSize()) {
            NovaTopBar(clock24h = state.settings.clock24h)
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (!panelsHidden) {
                    CategoryPane(
                        selected = state.category,
                        collapsed = false,
                        recordingActive = state.recordingInProgress,
                        onSelect = onCategory,
                        modifier = Modifier
                            .width(156.dp)
                            .fillMaxHeight()
                            .focusRequester(catFocus)
                            .focusProperties { right = groupFocus },
                    )
                    GroupPane(
                        groups = state.groups,
                        selected = state.groupId,
                        onSelect = onGroup,
                        isLoading = state.isLoading,
                        restoreRequest = restoreGroupRequest,
                        listState = groupListState,
                        modifier = Modifier
                            .width(PaneGroups)
                            .fillMaxHeight()
                            .focusRequester(groupFocus)
                            .focusProperties {
                                left = catFocus
                                right = contentFocus
                            },
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { if (it.hasFocus) panelsHidden = true }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                                panelsHidden = false
                                restoreGroupRequest++
                                true
                            } else false
                        }
                        .focusRequester(contentFocus)
                        .focusProperties { left = FocusRequester.Cancel },
                ) {
                    if (state.isLoading && state.groups.isEmpty()) {
                        CenteredProgressIndicator()
                    } else {
                        when (state.category) {
                            HomeCategory.MOVIES, HomeCategory.SERIES -> Column(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxWidth()) {
                                    VodInfoHeader(focusedVod)
                                    VodToolbar(
                                        query = vodQuery,
                                        onQueryChange = { vodQuery = it },
                                        sort = vodSort,
                                        onSort = { vodSort = it },
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    )
                                }
                                val pagedItems = if (state.category == HomeCategory.MOVIES) movieItems else seriesItems
                                if (pagedItems != null) {
                                    PagedVodPane(
                                        items = pagedItems,
                                        history = state.history,
                                        posterSize = state.settings.posterSize,
                                        restoreFocusId = returnVodId,
                                        restoreFocusIndex = returnVodIndex,
                                        onClick = { item, index ->
                                            returnVodIndex = index
                                            returnVodId = item.id
                                            onVod(item)
                                        },
                                        onFocus = {
                                            pendingVodId = it
                                            if (it == returnVodId) {
                                                returnVodId = null
                                                returnVodIndex = -1
                                            }
                                        },
                                        onExitLeft = {
                                            panelsHidden = false
                                            restoreGroupRequest++
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            else -> Column(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxWidth().height(if (showPreview) 224.dp else 58.dp)) {
                                    if (showPreview) {
                                        PreviewPane(
                                            channel = state.previewChannel,
                                            nowNext = state.previewChannel?.let { state.nowNext[it.id] },
                                            playerManager = playerManager,
                                            clock24h = state.settings.clock24h,
                                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                                        )
                                    }
                                    VodToolbar(
                                        query = liveQuery,
                                        onQueryChange = { liveQuery = it },
                                        sort = liveSort,
                                        onSort = { liveSort = it },
                                        liveMode = true,
                                        modifier = Modifier.align(Alignment.TopEnd),
                                    )
                                }
                                if (state.settings.listStyle == ListStyle.LIST) {
                                    LiveGuideHeader(state.settings.clock24h)
                                }
                                PagedChannelPane(
                                    channels = liveItems,
                                    state = state,
                                    restoreFocusId = returnChannelId,
                                    restoreFocusIndex = returnChannelIndex,
                                    onWatch = { ch, index ->
                                        val locked = state.settings.parentalEnabled &&
                                            (ch.locked || ch.groupName in state.settings.lockedGroups)
                                        if (locked) pinFor = ch else {
                                            returnChannelIndex = index
                                            returnChannelId = ch.id
                                            if (state.previewChannel?.id == ch.id) onWatch(ch)
                                            else onPreviewChannel(ch.id)
                                        }
                                    },
                                    onLongPress = { actionsFor = it },
                                    onProgram = { channel, program -> selectedProgram = channel to program },
                                    onFocus = { id, index ->
                                        // This ID only exists for focus restoration. Keeping it
                                        // local avoids recomposing HomeUiState on every key repeat.
                                        returnChannelId = id
                                        returnChannelIndex = index
                                        onChannelFocus(id)
                                    },
                                    onVisible = onChannelVisible,
                                    onExitLeft = {
                                        panelsHidden = false
                                        restoreGroupRequest++
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.firstRun) {
            FirstRunCard(onAdd = onAddPlaylist)
        }

        syncProgress?.takeUnless {
            it.stage == ImportProgress.Stage.DONE || it.stage == ImportProgress.Stage.ERROR
        }?.let {
            SyncStatusBanner(it, Modifier.align(Alignment.TopCenter).padding(top = 58.dp))
        }

        actionsFor?.let { ch ->
            ChannelActionsDialog(
                channel = ch,
                onWatch = { actionsFor = null; returnChannelId = ch.id; onWatch(ch) },
                onFavorite = { onToggleFav(ch.id); actionsFor = null },
                onGuide = { actionsFor = null },
                onHide = { onHide(ch.id); actionsFor = null },
                onAssign = { actionsFor = null; assignFor = ch },
                onDismiss = { actionsFor = null },
            )
        }
        assignFor?.let { ch ->
            AssignEpgDialog(
                channel = ch,
                onSearch = onSearchEpg,
                onPick = { id -> onAssignEpg(ch.id, id); assignFor = null },
                onDismiss = { assignFor = null },
            )
        }
        selectedProgram?.let { (channel, program) ->
            LiveProgramDialog(
                channel = channel,
                program = program,
                recording = program.id in state.activeRecordingProgramIds,
                clock24h = state.settings.clock24h,
                onWatch = {
                    selectedProgram = null
                    onWatch(channel)
                },
                onCatchup = {
                    selectedProgram = null
                    onCatchup(channel, program)
                },
                onRecord = {
                    selectedProgram = null
                    onRecordProgram(channel, program)
                },
                onStopRecording = {
                    selectedProgram = null
                    onStopRecording(channel, program)
                },
                onRemind = {
                    selectedProgram = null
                    onRemindProgram(channel, program)
                },
                onDismiss = { selectedProgram = null },
            )
        }
        pinFor?.let {
            PinDialog(
                title = stringResource(R.string.parental_enter_pin),
                error = pinError,
                onSubmit = { pin ->
                    if (verifyPin(pin)) {
                        pinError = false
                        val ch = pinFor
                        pinFor = null
                        if (ch != null) {
                            returnChannelId = ch.id
                            onWatch(ch)
                        }
                    } else pinError = true
                },
                onDismiss = { pinFor = null },
            )
        }
    }
}

@Composable
private fun CategoryPane(
    selected: HomeCategory,
    collapsed: Boolean,
    recordingActive: Boolean,
    onSelect: (HomeCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    val cats = listOf(
        HomeCategory.LIVE, HomeCategory.MOVIES, HomeCategory.SERIES,
        HomeCategory.RECORDINGS, HomeCategory.MULTIVIEW,
        HomeCategory.SETTINGS,
    )
    TvLazyColumn(
        modifier = modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(cats, key = { it.name }, contentType = { "category" }) { cat ->
            val active = cat == selected
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .scaleOnFocus(1.015f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            focused -> colors.accent
                            active -> colors.accent.copy(alpha = 0.18f)
                            else -> Color.Transparent
                        },
                    )
                    .border(
                        1.dp,
                        if (active && !focused) colors.accent.copy(alpha = 0.55f) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .dpadClickable { onSelect(cat) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(if (active && !focused) colors.accent else Color.Transparent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Icon(CategoryIcon(cat), contentDescription = stringResource(categoryLabel(cat)), tint = if (focused) colors.background else if (active) colors.accent else colors.muted, modifier = Modifier.size(18.dp))
                if (!collapsed) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(categoryLabel(cat)),
                        color = if (focused) colors.background else colors.onBackground,
                        fontSize = 13.sp,
                        fontWeight = if (active || focused) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                if (cat == HomeCategory.RECORDINGS && recordingActive) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Color(0xFFFF3B30), CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveProgramDialog(
    channel: Channel,
    program: Program,
    recording: Boolean,
    clock24h: Boolean,
    onWatch: () -> Unit,
    onCatchup: () -> Unit,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onRemind: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    var confirmStopRecording by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.width(860.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text(channel.name, color = colors.accent, fontSize = 13.sp, maxLines = 1)
                    Spacer(Modifier.height(5.dp))
                    Text(program.title, color = colors.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(TimeFmt.range(program.startMs, program.endMs, clock24h), color = colors.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(program.description.ifBlank { "No programme description available." }, color = colors.onBackground, fontSize = 14.sp, maxLines = 5)
                    Spacer(Modifier.height(18.dp))
                    Row {
                        FocusButton("Watch live", onWatch)
                        if (program.isPast(System.currentTimeMillis()) && channel.catchup) {
                            Spacer(Modifier.width(10.dp))
                            FocusButton("Play catch-up", onCatchup)
                        }
                        Spacer(Modifier.width(10.dp))
                        FocusButton(
                            if (recording) "Stop recording" else "Record",
                            if (recording) ({ confirmStopRecording = true }) else onRecord,
                        )
                        Spacer(Modifier.width(10.dp))
                        FocusButton("Remind", onRemind)
                        Spacer(Modifier.width(10.dp))
                        FocusButton("Close", onDismiss)
                    }
                }
            }
        }
        if (confirmStopRecording) {
            ConfirmDialog(
                title = stringResource(R.string.recording_stop_title),
                body = stringResource(R.string.recording_stop_body, channel.name),
                confirmLabel = stringResource(R.string.recording_stop_action),
                onConfirm = {
                    confirmStopRecording = false
                    onStopRecording()
                },
                onDismiss = { confirmStopRecording = false },
            )
        }
    }
}

@Composable
private fun LiveGuideHeader(clock24h: Boolean) {
    val colors = LocalNovaPalette.current
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val halfHour = TimeFmt.floorToHalfHour(now)
    val position = ((now - halfHour).toFloat() / (30 * 60_000f)).coerceIn(0.01f, 0.99f)
    Row(
        Modifier.fillMaxWidth().height(38.dp).padding(end = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("CHANNEL", color = colors.muted, fontSize = 10.sp, letterSpacing = 1.1.sp, modifier = Modifier.weight(0.42f).padding(start = 18.dp))
        Box(Modifier.weight(0.34f).fillMaxHeight()) {
            Text(TimeFmt.clock(halfHour, clock24h), color = colors.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart))
            Text(TimeFmt.clock(halfHour + 30 * 60_000L, clock24h), color = colors.muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopEnd))
            Row(Modifier.fillMaxSize().padding(top = 20.dp)) {
                Spacer(Modifier.weight(position))
                Box(Modifier.width(2.dp).fillMaxHeight().background(colors.live))
                Spacer(Modifier.weight(1f - position))
            }
        }
        Spacer(Modifier.width(3.dp))
        Text(
            TimeFmt.clock(halfHour + 60 * 60_000L, clock24h),
            color = colors.muted,
            fontSize = 11.sp,
            modifier = Modifier.weight(0.24f).padding(start = 8.dp),
        )
    }
}

@Composable
private fun VodToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: VodSort,
    onSort: (VodSort) -> Unit,
    liveMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    val keyboard = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchEditing by rememberSaveable { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
    val searchFieldFocus = remember { FocusRequester() }
    val searchIconFocus = remember { FocusRequester() }
    val clearSearchFocus = remember { FocusRequester() }
    val sortIconFocus = remember { FocusRequester() }
    val firstSortFocus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            withFrameNanos { }
            runCatching { searchFieldFocus.requestFocus() }
        }
    }
    LaunchedEffect(searchEditing) {
        if (searchEditing) {
            withFrameNanos { }
            keyboard?.show()
        }
    }
    BackHandler(enabled = sortOpen || searchOpen) {
        when {
            sortOpen -> {
                sortOpen = false
                runCatching { sortIconFocus.requestFocus() }
            }
            searchOpen -> {
                if (query.isBlank()) {
                    searchEditing = false
                    keyboard?.hide()
                    searchOpen = false
                    runCatching { searchIconFocus.requestFocus() }
                } else {
                    // Keep a live query visible until the user clears it.
                    searchEditing = false
                    keyboard?.hide()
                    runCatching { searchFieldFocus.requestFocus() }
                }
            }
        }
    }
    LaunchedEffect(sortOpen) {
        if (sortOpen) {
            delay(20)
            runCatching { firstSortFocus.requestFocus() }
        }
    }
    Box(
        modifier
            .padding(top = 10.dp, end = 18.dp)
            .onPreviewKeyEvent { event ->
                val code = event.nativeKeyEvent.keyCode
                when {
                    searchOpen && code == KeyEvent.KEYCODE_BACK -> {
                        if (event.type == KeyEventType.KeyDown) {
                            searchEditing = false
                            keyboard?.hide()
                            if (query.isBlank()) {
                                searchOpen = false
                                runCatching { searchIconFocus.requestFocus() }
                            }
                        }
                        true
                    }
                    searchFocused && code == KeyEvent.KEYCODE_DPAD_RIGHT && event.type == KeyEventType.KeyDown -> {
                        runCatching { if (query.isNotEmpty()) clearSearchFocus.requestFocus() else searchIconFocus.requestFocus() }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (searchOpen) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    readOnly = !searchEditing,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.onBackground, fontSize = 14.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier
                        .width(if (query.isNotEmpty()) 208.dp else 250.dp)
                        .focusRequester(searchFieldFocus)
                        .focusProperties { right = if (query.isNotEmpty()) clearSearchFocus else searchIconFocus }
                        .onPreviewKeyEvent { event ->
                            val code = event.nativeKeyEvent.keyCode
                            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && event.type == KeyEventType.KeyDown) {
                                runCatching { if (query.isNotEmpty()) clearSearchFocus.requestFocus() else searchIconFocus.requestFocus() }
                                return@onPreviewKeyEvent true
                            }
                            val activate = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                                code == KeyEvent.KEYCODE_ENTER ||
                                code == KeyEvent.KEYCODE_NUMPAD_ENTER
                            if (activate && event.type == KeyEventType.KeyUp) {
                                searchEditing = true
                                true
                            } else false
                        }
                        .onFocusChanged { searchFocused = it.isFocused }
                        .background(colors.surface2.copy(alpha = 0.94f), RoundedCornerShape(8.dp))
                        .border(1.dp, if (searchFocused) colors.accent else colors.muted.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) Text(stringResource(R.string.search_posters_hint), color = colors.muted, fontSize = 14.sp)
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    ToolbarIconButton(
                        icon = Icons.Default.Close,
                        description = "Clear search",
                        onClick = {
                            onQueryChange("")
                            runCatching { searchFieldFocus.requestFocus() }
                        },
                        modifier = Modifier
                            .focusRequester(clearSearchFocus)
                            .focusProperties { left = searchFieldFocus; right = searchIconFocus },
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            ToolbarIconButton(
                icon = Icons.Default.Search,
                description = "Search",
                active = searchOpen,
                onClick = {
                    if (searchOpen) {
                        searchEditing = false
                        keyboard?.hide()
                        searchOpen = false
                    } else {
                        searchOpen = true
                    }
                    sortOpen = false
                },
                modifier = Modifier
                    .focusRequester(searchIconFocus)
                    .focusProperties {
                        if (searchOpen) left = searchFieldFocus
                        right = sortIconFocus
                    },
            )
            Spacer(Modifier.width(8.dp))
            ToolbarIconButton(
                icon = Icons.Default.MoreVert,
                description = stringResource(R.string.sort_posters, if (liveMode && sort == VodSort.NEWEST) "Channel number" else sort.label),
                active = sortOpen,
                onClick = { sortOpen = !sortOpen },
                modifier = Modifier
                    .focusRequester(sortIconFocus)
                    .focusProperties { left = searchIconFocus }
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.type == KeyEventType.KeyDown) {
                            runCatching { searchIconFocus.requestFocus() }
                            true
                        } else false
                    },
            )
        }
        if (sortOpen) {
            GlassPanel(Modifier.align(Alignment.TopEnd).padding(top = 46.dp).width(180.dp)) {
                Column(Modifier.padding(6.dp)) {
                    VodSort.entries.forEachIndexed { index, option ->
                        SortMenuItem(
                            label = if (liveMode && option == VodSort.NEWEST) "Channel number" else option.label,
                            selected = option == sort,
                            onClick = {
                                onSort(option)
                                sortOpen = false
                                runCatching { sortIconFocus.requestFocus() }
                            },
                            modifier = if (index == 0) Modifier.focusRequester(firstSortFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(21.dp))
            .background(
                if (focused) colors.accent
                else if (active) colors.surface2.copy(alpha = 0.72f)
                else Color.Transparent,
            )
            .dpadClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (focused) colors.background else colors.onBackground,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SortMenuItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) colors.accent else Color.Transparent)
            .dpadClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "✓  $label" else label, color = if (focused) colors.background else colors.onBackground, fontSize = 13.sp)
    }
}

@Composable
private fun SyncStatusBanner(progress: ImportProgress, modifier: Modifier = Modifier) {
    val label = when (progress.stage) {
        ImportProgress.Stage.CONNECTING -> "Connecting to playlist"
        ImportProgress.Stage.DOWNLOADING -> "Downloading ${progress.message.ifBlank { "playlist" }}"
        ImportProgress.Stage.PARSING -> if (progress.parsed > 0) "Indexing ${progress.parsed} items" else "Indexing playlist"
        ImportProgress.Stage.SAVING -> "Saving library"
        else -> "Synchronizing library"
    }
    GlassPanel(modifier = modifier.width(260.dp).height(48.dp)) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CenteredProgressIndicator(Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, color = LocalNovaPalette.current.onBackground, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun GroupPane(
    groups: List<GroupItem>,
    selected: String,
    onSelect: (String) -> Unit,
    isLoading: Boolean,
    restoreRequest: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    val selectedRequester = remember { FocusRequester() }
    var pendingGroup by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingGroup) {
        val id = pendingGroup ?: return@LaunchedEffect
        // Category focus always wins. Do not replace the right pane while a held
        // D-pad is still traversing folders: every focus change cancels this
        // effect, and content is selected only after navigation has settled.
        // OK/click below bypasses the delay and remains immediate.
        delay(280)
        if (id != selected) onSelect(id)
    }
    LaunchedEffect(restoreRequest, groups, selected) {
        if (restoreRequest > 0) {
            val index = groups.indexOfFirst { it.id == selected }.coerceAtLeast(0)
            val selectedIsVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (!selectedIsVisible) listState.scrollToItem(index)
            delay(40)
            // The pane can disappear again during this delay. A detached requester
            // must never be allowed to crash the app.
            runCatching { selectedRequester.requestFocus() }
        }
    }
    if (groups.isEmpty() && !isLoading) {
        EmptyState(stringResource(R.string.empty_channels), stringResource(R.string.empty_channels_hint), modifier)
        return
    }
    TvLazyColumn(state = listState, modifier = modifier.padding(end = 8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(groups, key = { it.id }, contentType = { "group" }) { g ->
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (g.id == selected) Modifier.focusRequester(selectedRequester) else Modifier)
                    .padding(vertical = 2.dp)
                    .onFocusChanged { focusState ->
                        focused = focusState.isFocused
                        if (focusState.isFocused && g.id != selected) pendingGroup = g.id
                    }
                    .scaleOnFocus(1.015f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (focused) colors.accent else if (g.id == selected) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (g.id == selected && !focused) colors.accent.copy(alpha = 0.55f) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .dpadClickable {
                        pendingGroup = null
                        onSelect(g.id)
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(if (g.id == selected && !focused) colors.accent else Color.Transparent, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(g.label, color = if (focused) colors.background else colors.onBackground, fontSize = 14.sp, fontWeight = if (g.id == selected || focused) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1)
                Text(g.count.toString(), color = if (focused) colors.background.copy(alpha = 0.7f) else colors.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PagedChannelPane(
    channels: LazyPagingItems<Channel>?,
    state: HomeUiState,
    restoreFocusId: String?,
    restoreFocusIndex: Int,
    onWatch: (Channel, Int) -> Unit,
    onLongPress: (Channel) -> Unit,
    onProgram: (Channel, Program) -> Unit,
    onFocus: (String?, Int) -> Unit,
    onVisible: (String) -> Unit,
    onExitLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels == null || (channels.loadState.refresh is LoadState.NotLoading && channels.itemCount == 0)) {
        val title = when (state.groupId) {
            "favorites" -> stringResource(R.string.empty_favorites)
            "recent" -> stringResource(R.string.empty_history)
            else -> stringResource(R.string.empty_channels)
        }
        EmptyState(title, stringResource(R.string.empty_channels_hint), modifier.fillMaxSize())
        return
    }
    if (channels.loadState.refresh is LoadState.Loading && channels.itemCount == 0) {
        CenteredProgressIndicator(modifier.fillMaxSize())
        return
    }
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    val restoreRequester = remember { FocusRequester() }
    val style = state.settings.listStyle
    if (style == ListStyle.LOGOS) {
        val gridState = rememberLazyGridState()
        LaunchedEffect(restoreFocusId, restoreFocusIndex, channels.itemCount) {
            if (restoreFocusId != null && restoreFocusIndex in 0 until channels.itemCount) {
                gridState.scrollToItem(restoreFocusIndex)
                withFrameNanos { }
                runCatching { restoreRequester.requestFocus() }
            }
        }
        TvLazyVerticalGrid(columns = GridCells.Fixed(6), state = gridState, modifier = modifier.fillMaxSize().padding(8.dp), contentPadding = PaddingValues(8.dp)) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }, contentType = channels.itemContentType { "channel-logo" }) { idx ->
                val ch = channels[idx] ?: return@items
                LaunchedEffect(ch.id) { onVisible(ch.id) }
                ChannelRow(
                    channel = ch, nowNext = state.nowNext[ch.id], style = style,
                    showNumber = state.settings.showNumbers, clock24h = state.settings.clock24h, nowMs = nowMs,
                    recording = state.nowNext[ch.id]?.now?.id in state.activeRecordingProgramIds,
                    onClick = { onWatch(ch, idx) }, onLongPress = { onLongPress(ch) },
                    onProgramClick = { onProgram(ch, it) },
                    modifier = Modifier
                        .then(if (ch.id == restoreFocusId) Modifier.focusRequester(restoreRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            val leftEdge = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx }?.column == 0
                            if (leftEdge && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                onExitLeft(); true
                            } else false
                        }
                        .onFocusChanged { if (it.isFocused) onFocus(ch.id, idx) },
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        LaunchedEffect(restoreFocusId, restoreFocusIndex, channels.itemCount) {
            if (restoreFocusId != null && restoreFocusIndex in 0 until channels.itemCount) {
                listState.scrollToItem(restoreFocusIndex)
                withFrameNanos { }
                runCatching { restoreRequester.requestFocus() }
            }
        }
        TvLazyColumn(state = listState, modifier = modifier.fillMaxSize().padding(end = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }, contentType = channels.itemContentType { "channel-row" }) { idx ->
                val ch = channels[idx] ?: return@items
                LaunchedEffect(ch.id) { onVisible(ch.id) }
                ChannelRow(
                    channel = ch, nowNext = state.nowNext[ch.id], style = style,
                    showNumber = state.settings.showNumbers, clock24h = state.settings.clock24h, nowMs = nowMs,
                    recording = state.nowNext[ch.id]?.now?.id in state.activeRecordingProgramIds,
                    onClick = { onWatch(ch, idx) }, onLongPress = { onLongPress(ch) },
                    onProgramClick = { onProgram(ch, it) },
                    modifier = Modifier
                        .then(if (ch.id == restoreFocusId) Modifier.focusRequester(restoreRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                onExitLeft(); true
                            } else false
                        }
                        .onFocusChanged { if (it.isFocused) onFocus(ch.id, idx) },
                )
            }
        }
    }
}

@Composable
private fun ChannelPane(
    channels: List<Channel>,
    state: HomeUiState,
    restoreFocusId: String?,
    onWatch: (Channel) -> Unit,
    onLongPress: (Channel) -> Unit,
    onFocus: (String?) -> Unit,
    onExitLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    if (channels.isEmpty()) {
        val title = when (state.groupId) {
            "favorites" -> stringResource(R.string.empty_favorites)
            "recent" -> stringResource(R.string.empty_history)
            else -> stringResource(R.string.empty_channels)
        }
        EmptyState(title, stringResource(R.string.empty_channels_hint), modifier.fillMaxSize())
        return
    }
    val style = state.settings.listStyle
    val restoreRequester = remember { FocusRequester() }
    if (style == ListStyle.LOGOS) {
        val gridState = rememberLazyGridState()
        LaunchedEffect(restoreFocusId, channels) {
            val index = channels.indexOfFirst { it.id == restoreFocusId }
            if (index >= 0) {
                gridState.scrollToItem(index)
                delay(40)
                runCatching { restoreRequester.requestFocus() }
            }
        }
        TvLazyVerticalGrid(
            columns = GridCells.Fixed(6),
            state = gridState,
            modifier = modifier.fillMaxSize().padding(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(channels.size, key = { channels[it].id }, contentType = { "channel-logo" }) { idx ->
                val ch = channels[idx]
                ChannelRow(
                    channel = ch,
                    nowNext = state.nowNext[ch.id],
                    style = style,
                    showNumber = state.settings.showNumbers,
                    clock24h = state.settings.clock24h,
                    nowMs = nowMs,
                    recording = state.nowNext[ch.id]?.now?.id in state.activeRecordingProgramIds,
                    onClick = { onWatch(ch) },
                    onLongPress = { onLongPress(ch) },
                    modifier = Modifier
                        .then(if (ch.id == restoreFocusId) Modifier.focusRequester(restoreRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            val atLeftEdge = gridState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == idx }?.column == 0
                            if (atLeftEdge && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                onExitLeft()
                                true
                            } else false
                        }
                        .onFocusChanged {
                            if (it.isFocused) onFocus(ch.id)
                        },
                )
            }
        }
    } else {
        val listState = rememberLazyListState()
        LaunchedEffect(restoreFocusId, channels) {
            val index = channels.indexOfFirst { it.id == restoreFocusId }
            if (index >= 0) {
                listState.scrollToItem(index)
                delay(40)
                runCatching { restoreRequester.requestFocus() }
            }
        }
        TvLazyColumn(state = listState, modifier = modifier.fillMaxSize().padding(end = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(channels, key = { it.id }, contentType = { "channel-row" }) { ch ->
                ChannelRow(
                    channel = ch,
                    nowNext = state.nowNext[ch.id],
                    style = style,
                    showNumber = state.settings.showNumbers,
                    clock24h = state.settings.clock24h,
                    nowMs = nowMs,
                    recording = state.nowNext[ch.id]?.now?.id in state.activeRecordingProgramIds,
                    onClick = { onWatch(ch) },
                    onLongPress = { onLongPress(ch) },
                    modifier = Modifier
                        .then(if (ch.id == restoreFocusId) Modifier.focusRequester(restoreRequester) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                                onExitLeft()
                                true
                            } else false
                        }
                        .onFocusChanged {
                            if (it.isFocused) onFocus(ch.id)
                        },
                )
            }
        }
    }
}

@Composable
private fun VodPane(
    items: List<VodItem>,
    history: List<com.nova.iptv.domain.model.WatchHistory>,
    posterSize: PosterSize,
    restoreFocusId: String?,
    restoreFocusIndex: Int,
    onClick: (VodItem, Int) -> Unit,
    onFocus: (String) -> Unit,
    onExitLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        EmptyState(stringResource(R.string.empty_movies), stringResource(R.string.empty_channels_hint), modifier.fillMaxSize())
        return
    }
    val progress = history.associate { it.refId to it.fraction }
    val width = when (posterSize) {
        PosterSize.SMALL -> 112.dp
        PosterSize.MEDIUM -> 132.dp
        PosterSize.LARGE -> 156.dp
    }
    val gridState = rememberLazyGridState()
    var navigationIndex by rememberSaveable { mutableIntStateOf(restoreFocusIndex.coerceAtLeast(0)) }
    var pendingFocusIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pendingFocusIndex) {
        if (pendingFocusIndex in items.indices) gridState.scrollToItem(pendingFocusIndex)
    }
    LaunchedEffect(restoreFocusId, restoreFocusIndex, items.size) {
        if (restoreFocusId != null && restoreFocusIndex in items.indices) {
            navigationIndex = restoreFocusIndex
            pendingFocusIndex = restoreFocusIndex
            gridState.scrollToItem(restoreFocusIndex)
        }
    }
    TvLazyVerticalGrid(
        columns = GridCells.Adaptive(width),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (event.type == KeyEventType.KeyDown &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                ) {
                    val columns = (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
                    val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) columns else -columns
                    val target = (navigationIndex + delta).coerceIn(0, items.lastIndex)
                    if (target != navigationIndex) {
                        navigationIndex = target
                        pendingFocusIndex = target
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .padding(8.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        items(items.size, key = { items[it].id }, contentType = { "poster" }) { idx ->
            val item = items[idx]
            val returnFocus = remember { FocusRequester() }
            if (item.id == restoreFocusId || idx == pendingFocusIndex) {
                LaunchedEffect(item.id, restoreFocusId, pendingFocusIndex) {
                    if (idx == pendingFocusIndex) gridState.scrollToItem(idx)
                    withFrameNanos { }
                    runCatching { returnFocus.requestFocus() }
                }
            }
            PosterCard(
                title = item.title,
                year = item.year,
                posterUrl = item.posterUrl,
                progress = progress[item.id] ?: 0f,
                onClick = { onClick(item, idx) },
                posterWidth = width,
                loadImage = true,
                modifier = Modifier
                    .then(if (item.id == restoreFocusId || idx == pendingFocusIndex) Modifier.focusRequester(returnFocus) else Modifier)
                    .onPreviewKeyEvent { event ->
                        val atLeftEdge = gridState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == idx }?.column == 0
                        if (atLeftEdge && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            onExitLeft()
                            true
                        } else false
                    }
                    .onFocusChanged {
                    if (it.isFocused) {
                        navigationIndex = idx
                        pendingFocusIndex = -1
                        onFocus(item.id)
                    }
                },
            )
        }
    }
}

@Composable
private fun PreviewPane(
    channel: Channel?,
    nowNext: NowNext?,
    playerManager: PlayerManager,
    clock24h: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val current = nowNext?.now
    var previewPlayer by remember { mutableStateOf(playerManager.currentPreviewPlayer()) }
    LaunchedEffect(channel?.id) {
        previewPlayer = channel?.let {
            playerManager.previewPlayer(it.streamUrl, headersFor(it.userAgent, it.referrer), autoPlay = true)
        }
    }
    DisposableEffect(Unit) {
        onDispose { playerManager.releasePreview() }
    }
    GlassPanel(modifier) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = previewPlayer
                    }
                },
                update = { view -> view.player = previewPlayer },
                modifier = Modifier.width(230.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("LIVE PREVIEW", color = colors.accent, fontSize = 11.sp, letterSpacing = 1.4.sp)
                Spacer(Modifier.height(6.dp))
                Text(channel?.name.orEmpty(), color = colors.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                channel?.groupName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(current?.title ?: "No programme information", color = colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                current?.let { programme ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        TimeFmt.range(programme.startMs, programme.endMs, clock24h),
                        color = colors.muted,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth(0.62f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(colors.surface2),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(programme.progress(nowMs)).height(3.dp).background(colors.accent),
                        )
                    }
                    programme.description.takeIf { it.isNotBlank() }?.let { description ->
                        Spacer(Modifier.height(6.dp))
                        Text(description, color = colors.muted, fontSize = 11.sp, maxLines = 2)
                    }
                }
                nowNext?.next?.title?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(5.dp))
                    Text("Next: $it", color = colors.onBackground.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PagedVodPane(
    items: LazyPagingItems<VodItem>,
    history: List<com.nova.iptv.domain.model.WatchHistory>,
    posterSize: PosterSize,
    restoreFocusId: String?,
    restoreFocusIndex: Int,
    onClick: (VodItem, Int) -> Unit,
    onFocus: (String) -> Unit,
    onExitLeft: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.loadState.refresh is LoadState.Loading && items.itemCount == 0) {
        CenteredProgressIndicator(modifier.fillMaxSize())
        return
    }
    if (items.loadState.refresh is LoadState.Error && items.itemCount == 0) {
        EmptyState("Could not load movies", "Try this category again.", modifier.fillMaxSize())
        return
    }
    if (items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0) {
        EmptyState(stringResource(R.string.empty_movies), stringResource(R.string.empty_channels_hint), modifier.fillMaxSize())
        return
    }
    val progress = remember(history) { history.associate { it.refId to it.fraction } }
    val width = when (posterSize) {
        PosterSize.SMALL -> 112.dp
        PosterSize.MEDIUM -> 132.dp
        PosterSize.LARGE -> 156.dp
    }
    val gridState = rememberLazyGridState()
    var navigationIndex by rememberSaveable { mutableIntStateOf(restoreFocusIndex.coerceAtLeast(0)) }
    var pendingFocusIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(pendingFocusIndex, items.itemCount) {
        if (pendingFocusIndex >= 0 && pendingFocusIndex < items.itemCount) {
            gridState.scrollToItem(pendingFocusIndex)
        }
    }
    LaunchedEffect(restoreFocusId, restoreFocusIndex, items.itemCount) {
        if (restoreFocusId != null && restoreFocusIndex >= 0 && restoreFocusIndex < items.itemCount) {
            navigationIndex = restoreFocusIndex
            pendingFocusIndex = restoreFocusIndex
            gridState.scrollToItem(restoreFocusIndex)
        }
    }
    TvLazyVerticalGrid(
        columns = GridCells.Adaptive(width),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                if (event.type == KeyEventType.KeyDown &&
                    (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
                ) {
                    val columns = (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
                    val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) columns else -columns
                    val lastIndex = (items.itemCount - 1).coerceAtLeast(0)
                    val target = (navigationIndex + delta).coerceIn(0, lastIndex)
                    if (target != navigationIndex) {
                        navigationIndex = target
                        pendingFocusIndex = target
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
            .padding(8.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey { it.id },
            contentType = items.itemContentType { "poster" },
        ) { idx ->
            val item = items[idx] ?: return@items
            val returnFocus = remember(item.id) { FocusRequester() }
            if (item.id == restoreFocusId || idx == pendingFocusIndex) {
                LaunchedEffect(item.id, restoreFocusId, pendingFocusIndex) {
                    withFrameNanos { }
                    runCatching { returnFocus.requestFocus() }
                }
            }
            PosterCard(
                title = item.title,
                year = item.year,
                posterUrl = item.posterUrl,
                progress = progress[item.id] ?: 0f,
                onClick = { onClick(item, idx) },
                posterWidth = width,
                loadImage = true,
                modifier = Modifier
                    .then(if (item.id == restoreFocusId || idx == pendingFocusIndex) Modifier.focusRequester(returnFocus) else Modifier)
                    .onPreviewKeyEvent { event ->
                        val atLeftEdge = gridState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == idx }?.column == 0
                        if (atLeftEdge && event.type == KeyEventType.KeyDown && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            onExitLeft()
                            true
                        } else false
                    }
                    .onFocusChanged {
                        if (it.isFocused) {
                            navigationIndex = idx
                            pendingFocusIndex = -1
                            onFocus(item.id)
                        }
                    },
            )
        }
    }
}

@Composable
private fun VodInfoHeader(item: VodItem?) {
    val colors = LocalNovaPalette.current
    GlassPanel(Modifier.fillMaxWidth().height(236.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
        if (item == null) return@GlassPanel
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.46f).padding(horizontal = 10.dp)) {
                Text(item.title, color = colors.onBackground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                val facts = listOfNotNull(
                    item.year.takeIf { it > 0 }?.toString(),
                    item.rating.takeIf { it.isNotBlank() },
                    item.durationMin.takeIf { it > 0 }?.let { "$it min" },
                ).joinToString("  •  ")
                if (facts.isNotBlank()) Text(facts, color = colors.accent, fontSize = 13.sp)
                if (item.cast.isNotEmpty()) {
                    Text("Cast: ${item.cast.take(4).joinToString(", ")}", color = colors.muted, fontSize = 12.sp, maxLines = 1)
                }
                if (item.director.isNotBlank()) Text("Director: ${item.director}", color = colors.muted, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Text(item.description, color = colors.onBackground.copy(alpha = 0.86f), fontSize = 13.sp, maxLines = 5)
            }
            Spacer(Modifier.width(12.dp))
            AsyncImage(
                model = item.backdropUrl.ifBlank { item.posterUrl },
                contentDescription = null,
                modifier = Modifier.weight(0.54f).fillMaxHeight().clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun FirstRunCard(onAdd: () -> Unit) {
    val colors = LocalNovaPalette.current
    val addFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = { /* A playlist is required to leave first run. */ },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { addFocus.requestFocus() }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.width(520.dp)) {
                Column(Modifier.padding(28.dp)) {
                    Text(stringResource(R.string.first_run_title), color = colors.onBackground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.first_run_body), color = colors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.first_run_legal), color = colors.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(20.dp))
                    FocusButton(stringResource(R.string.first_run_add), onAdd, Modifier.focusRequester(addFocus))
                }
            }
        }
    }
}

@Composable
fun ChannelActionsDialog(
    channel: Channel,
    onWatch: () -> Unit,
    onFavorite: () -> Unit,
    onGuide: () -> Unit,
    onHide: () -> Unit,
    onAssign: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.width(360.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(channel.name, color = colors.onBackground, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        stringResource(R.string.action_watch) to onWatch,
                        stringResource(if (channel.favorite) R.string.action_favorite_remove else R.string.action_favorite_add) to onFavorite,
                        stringResource(R.string.action_open_guide) to onGuide,
                        stringResource(R.string.action_record) to onDismiss,
                        stringResource(R.string.action_hide) to onHide,
                        stringResource(R.string.action_assign_epg) to onAssign,
                    ).forEachIndexed { index, (label, act) ->
                        FocusButton(label, act, Modifier.then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier).fillMaxWidth().padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AssignEpgDialog(
    channel: Channel,
    onSearch: (String, (List<Pair<String, String>>) -> Unit) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    var q by remember { mutableStateOf(channel.name) }
    var hits by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(q) { onSearch(q) { hits = it } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { cancelFocus.requestFocus() }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.width(480.dp).height(360.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.assign_epg_title), color = colors.onBackground, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().background(colors.surface2, RoundedCornerShape(6.dp)).padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = q,
                            onValueChange = { q = it },
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = colors.onBackground, fontSize = 14.sp),
                        )
                        if (q.isNotEmpty()) {
                            ToolbarIconButton(Icons.Default.Close, "Clear search", onClick = { q = "" })
                        }
                    }
                    TvLazyColumn(Modifier.weight(1f)) {
                        items(hits, key = { it.first + it.second }, contentType = { "epg-hit" }) { (id, name) ->
                            FocusButton("$name  ($id)", { onPick(id) }, Modifier.fillMaxWidth().padding(vertical = 3.dp))
                        }
                    }
                    FocusButton(stringResource(R.string.action_cancel), onDismiss, Modifier.focusRequester(cancelFocus))
                }
            }
        }
    }
}
