@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.nova.iptv.ui.multiview

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.cachedIn
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.nova.iptv.R
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.data.player.headersFor
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.TvLazyVerticalGrid
import com.nova.iptv.nav.TvLazyColumn
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.nav.glowBorderOnFocus
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private enum class TilePlaybackState { EMPTY, LOADING, READY, ERROR }

@Composable
private fun MultiViewLayoutIcon(
    tileCount: Int,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    val cellColor = if (selected || focused) colors.accent else colors.muted
    Box(
        modifier
            .width(76.dp)
            .height(54.dp)
            .semantics { contentDescription = label }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.surface2 else androidx.compose.ui.graphics.Color.Transparent)
            .glowBorderOnFocus(radius = 8.dp)
            .onFocusChanged { focused = it.isFocused }
            .dpadClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (tileCount == 2) {
            Row(
                Modifier.width(38.dp).height(23.dp).align(Alignment.TopCenter).padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(2) {
                    Box(Modifier.weight(1f).fillMaxSize().background(cellColor, RoundedCornerShape(2.dp)))
                }
            }
        } else {
            Column(
                Modifier.width(38.dp).height(25.dp).align(Alignment.TopCenter).padding(top = 3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(2) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(2) {
                            Box(Modifier.weight(1f).fillMaxSize().background(cellColor, RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        }
        if (selected) {
            Box(Modifier.align(Alignment.Center).width(26.dp).height(2.dp).background(colors.accent, RoundedCornerShape(1.dp)))
        }
        if (focused) {
            Text(label, color = colors.onBackground, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun MultiViewTilePlayer(
    player: androidx.media3.exoplayer.ExoPlayer?,
    modifier: Modifier = Modifier,
    onDecoderFailure: () -> Unit = {},
) {
    val colors = LocalNovaPalette.current
    var state by remember(player) {
        mutableStateOf(if (player == null) TilePlaybackState.EMPTY else TilePlaybackState.LOADING)
    }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                state = when (playbackState) {
                    Player.STATE_READY -> TilePlaybackState.READY
                    Player.STATE_BUFFERING -> TilePlaybackState.LOADING
                    else -> state
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                state = TilePlaybackState.ERROR
                if (
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED
                ) {
                    onDecoderFailure()
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(modifier.background(colors.surface)) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
        }
        val status = when (state) {
            TilePlaybackState.EMPTY -> stringResource(R.string.multiview_empty_tile)
            TilePlaybackState.LOADING -> stringResource(R.string.multiview_loading)
            TilePlaybackState.ERROR -> stringResource(R.string.multiview_stream_error)
            TilePlaybackState.READY -> null
        }
        if (status != null) {
            Text(
                text = status,
                color = if (state == TilePlaybackState.ERROR) colors.danger else colors.muted,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@HiltViewModel
class MultiViewViewModel @Inject constructor(
    val playerManager: PlayerManager,
    private val playlists: PlaylistRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val playlistId = settings.settings
        .map { it.lastPlaylistId }
        .distinctUntilChanged()
    private val pickerQuery = MutableStateFlow("")
    val pickerQueryState = pickerQuery
    private val pickerGroup = MutableStateFlow("all")
    val pickerGroupState = pickerGroup
    val favorites = settings.settings.flatMapLatest {
        playlists.favorites(it.lastPlaylistId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())
    private val _tileChannels = MutableStateFlow<List<Channel?>>(List(4) { null })
    val tileChannels = _tileChannels
    private val _layout = MutableStateFlow(2)
    val layout = _layout
    val channelGroups = playlistId
        .flatMapLatest(playlists::channelGroups)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())
    val allChannels = combine(playlistId, pickerGroup, pickerQuery) { id, group, query -> Triple(id, group, query) }
        .flatMapLatest { (id, group, query) -> playlists.pagedChannels(id, group, query) }
        .cachedIn(viewModelScope)
    val clock24h = settings.settings

    init {
        viewModelScope.launch {
            val saved = settings.settings.first()
            _layout.value = if (saved.multiViewLayout == 4) 4 else 2
            val savedIds = if (saved.multiViewPlaylistId == saved.lastPlaylistId) {
                listOf(
                    saved.multiViewChannel1Id,
                    saved.multiViewChannel2Id,
                    saved.multiViewChannel3Id,
                    saved.multiViewChannel4Id,
                )
            } else emptyList()
            val restored = savedIds.map { id -> id.takeIf(String::isNotBlank)?.let { playlists.getChannel(it) } }
            if (restored.any { it != null }) {
                _tileChannels.value = List(4) { restored.getOrNull(it) }
            } else {
                val initial = favorites.filter { it.isNotEmpty() }.first().take(4)
                if (_tileChannels.value.all { it == null }) {
                    _tileChannels.value = List(4) { initial.getOrNull(it) }
                }
            }
        }
    }

    fun setPickerQuery(value: String) {
        pickerQuery.value = value
    }

    fun setPickerGroup(value: String) {
        pickerGroup.value = value
    }

    fun selectTile(index: Int, channel: Channel) {
        if (index !in 0..3) return
        _tileChannels.value = _tileChannels.value.toMutableList().also { it[index] = channel }
        persistTiles(channel.playlistId)
    }

    fun clearTile(index: Int) {
        if (index !in 0..3) return
        _tileChannels.value = _tileChannels.value.toMutableList().also { it[index] = null }
        persistTiles()
    }

    fun swapTile(index: Int) {
        if (index !in 0..3) return
        val other = if (index % 2 == 0) index + 1 else index - 1
        _tileChannels.value = _tileChannels.value.toMutableList().also {
            val held = it[index]
            it[index] = it[other]
            it[other] = held
        }
        persistTiles()
    }

    fun setLayout(value: Int) {
        _layout.value = if (value == 4) 4 else 2
        viewModelScope.launch {
            settings.update { it.copy(multiViewLayout = _layout.value) }
        }
    }

    private fun persistTiles(playlistId: String = settings.settings.value.lastPlaylistId) {
        val ids = _tileChannels.value.map { it?.id.orEmpty() }
        viewModelScope.launch {
            settings.update { current ->
                current.copy(
                    multiViewPlaylistId = playlistId,
                    multiViewChannel1Id = ids[0],
                    multiViewChannel2Id = ids[1],
                    multiViewChannel3Id = ids[2],
                    multiViewChannel4Id = ids[3],
                )
            }
        }
    }
}

@Composable
fun MultiViewRoute(
    onFullscreen: (PlayTarget) -> Unit,
    onBack: () -> Unit,
    vm: MultiViewViewModel = hiltViewModel(),
) {
    val pickerChannels = vm.allChannels.collectAsLazyPagingItems()
    val pickerQuery by vm.pickerQueryState.collectAsStateWithLifecycle()
    val pickerGroup by vm.pickerGroupState.collectAsStateWithLifecycle()
    val pickerGroups by vm.channelGroups.collectAsStateWithLifecycle()
    val channels by vm.tileChannels.collectAsStateWithLifecycle()
    val layout by vm.layout.collectAsStateWithLifecycle()
    val settings by vm.clock24h.collectAsStateWithLifecycle()
    val colors = LocalNovaPalette.current
    var pickerFor by remember { mutableIntStateOf(-1) }
    var actionsFor by remember { mutableIntStateOf(-1) }
    var players by remember { mutableStateOf<List<androidx.media3.exoplayer.ExoPlayer?>>(emptyList()) }
    var boundChannelIds by remember { mutableStateOf(List(4) { "" }) }
    var pickerSearchEditing by remember { mutableStateOf(false) }
    var decoderFallback by remember { mutableStateOf(false) }

    val visibleChannels = channels.take(layout)
    val occupancyKey = visibleChannels.joinToString(separator = "") { if (it == null) "0" else "1" }
    DisposableEffect(layout, occupancyKey) {
        val activeCount = visibleChannels.count { it != null }
        val created = if (activeCount > 0) {
            vm.playerManager.createMultiView(activeCount)
        } else {
            vm.playerManager.releaseMultiView()
            emptyList()
        }
        var playerIndex = 0
        val assignedPlayers = visibleChannels.map { ch ->
            if (ch == null) null else created.getOrNull(playerIndex++)
        }
        assignedPlayers.forEachIndexed { i, p ->
            val ch = channels.getOrNull(i)
            if (ch != null && p != null) {
                p.volume = 0f
                runCatching {
                    vm.playerManager.playMultiView(
                        player = p,
                        url = ch.streamUrl,
                        title = ch.name,
                        headers = headersFor(ch.userAgent, ch.referrer),
                    )
                }
            }
        }
        players = assignedPlayers
        boundChannelIds = List(4) { visibleChannels.getOrNull(it)?.id.orEmpty() }
        onDispose {
            players = emptyList()
            boundChannelIds = List(4) { "" }
            vm.playerManager.releaseMultiView()
        }
    }

    LaunchedEffect(players, visibleChannels.map { it?.id.orEmpty() }) {
        val updated = boundChannelIds.toMutableList()
        visibleChannels.forEachIndexed { index, channel ->
            val player = players.getOrNull(index)
            if (channel != null && player != null && updated[index] != channel.id) {
                vm.playerManager.playMultiView(
                    player = player,
                    url = channel.streamUrl,
                    title = channel.name,
                    headers = headersFor(channel.userAgent, channel.referrer),
                )
                updated[index] = channel.id
            }
        }
        boundChannelIds = updated
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        NovaTopBar(settings.clock24h) {
            Row {
                MultiViewLayoutIcon(
                    tileCount = 2,
                    selected = layout == 2,
                    label = stringResource(R.string.multiview_two_view),
                    onClick = { vm.setLayout(2) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                MultiViewLayoutIcon(
                    tileCount = 4,
                    selected = layout == 4,
                    label = stringResource(R.string.multiview_four_view),
                    onClick = {
                        decoderFallback = false
                        vm.setLayout(4)
                    },
                )
            }
        }
        if (decoderFallback) {
            Text(
                stringResource(R.string.multiview_decoder_fallback),
                color = colors.danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        TvLazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            items(layout) { idx ->
                val ch = channels.getOrNull(idx)
                var focused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .padding(6.dp)
                        .aspectRatio(if (layout == 4) 1.92f else 16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                        .glowBorderOnFocus(radius = 10.dp)
                        .onFocusChanged {
                            focused = it.isFocused
                            players.getOrNull(idx)?.volume = if (it.isFocused) 1f else 0f
                        }
                        .dpadClickable(
                            onLongPress = { },
                            onClick = {
                                if (ch == null) {
                                    pickerFor = idx
                                } else {
                                    actionsFor = idx
                                }
                            },
                        ),
                ) {
                    MultiViewTilePlayer(
                        player = players.getOrNull(idx).takeIf { ch != null },
                        modifier = Modifier.fillMaxSize(),
                        onDecoderFailure = {
                            if (layout == 4) {
                                decoderFallback = true
                                vm.setLayout(2)
                            }
                        },
                    )
                    Text(
                        ch?.name.orEmpty(),
                        color = colors.onBackground,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    )
                    if (focused) {
                        Text(
                            stringResource(R.string.multiview_audio_active),
                            color = colors.accent,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .background(colors.background.copy(alpha = 0.78f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        if (actionsFor >= 0) {
            val tile = actionsFor
            val channel = channels.getOrNull(tile)
            Dialog(
                onDismissRequest = { actionsFor = -1 },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                BackHandler { actionsFor = -1 }
                Box(Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.width(440.dp).background(colors.surface2, RoundedCornerShape(12.dp)).padding(24.dp),
                    ) {
                        Text(channel?.name.orEmpty(), color = colors.onBackground, fontSize = 20.sp)
                        Text(stringResource(R.string.multiview_tile_actions), color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 14.dp))
                        FocusButton(stringResource(R.string.multiview_fullscreen), onClick = {
                            if (channel != null) {
                                actionsFor = -1
                                vm.playerManager.releaseMultiView()
                                onFullscreen(PlayTarget.live(channel.id, channel.name, channel.number, channel.streamUrl))
                            }
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
                        FocusButton(stringResource(R.string.multiview_replace), onClick = {
                            actionsFor = -1
                            pickerFor = tile
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
                        FocusButton(stringResource(R.string.multiview_retry), onClick = {
                            val player = players.getOrNull(tile)
                            if (player != null && channel != null) {
                                vm.playerManager.playMultiView(player, channel.streamUrl, channel.name, headersFor(channel.userAgent, channel.referrer))
                            }
                            actionsFor = -1
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
                        FocusButton(stringResource(R.string.multiview_swap), onClick = {
                            vm.swapTile(tile)
                            actionsFor = -1
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
                        FocusButton(stringResource(R.string.multiview_remove), onClick = {
                            vm.clearTile(tile)
                            actionsFor = -1
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp))
                        FocusButton(stringResource(R.string.action_cancel), onClick = { actionsFor = -1 }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
        }
        if (pickerFor >= 0) {
            val keyboard = LocalSoftwareKeyboardController.current
            val closePicker = {
                pickerSearchEditing = false
                keyboard?.hide()
                vm.setPickerQuery("")
                vm.setPickerGroup("all")
                pickerFor = -1
            }
            LaunchedEffect(pickerSearchEditing) {
                if (pickerSearchEditing) keyboard?.show()
            }
            Dialog(
                onDismissRequest = closePicker,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                BackHandler {
                    if (pickerSearchEditing) {
                        pickerSearchEditing = false
                        keyboard?.hide()
                    } else closePicker()
                }
                Box(
                    Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.94f)).padding(64.dp),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Text(stringResource(R.string.multiview_pick_all), color = colors.onBackground, fontSize = 21.sp)
                        Text(stringResource(R.string.multiview_picker_hint), color = colors.muted, fontSize = 13.sp)
                        Row(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp)) {
                            Column(Modifier.width(290.dp).fillMaxSize()) {
                                Text(stringResource(R.string.multiview_categories), color = colors.muted, fontSize = 12.sp)
                                TvLazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                                    item {
                                        FocusButton(
                                            label = if (pickerGroup == "all") "✓  ${stringResource(R.string.all_channels)}" else stringResource(R.string.all_channels),
                                            onClick = { vm.setPickerGroup("all") },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        )
                                    }
                                    items(pickerGroups, key = { it.name }) { group ->
                                        val label = "${group.name}  (${group.count})"
                                        FocusButton(
                                            label = if (pickerGroup == "g:${group.name}") "✓  $label" else label,
                                            onClick = { vm.setPickerGroup("g:${group.name}") },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(22.dp))
                            Column(Modifier.weight(1f).fillMaxSize()) {
                            Row(
                            Modifier.fillMaxWidth()
                                .background(colors.surface2, RoundedCornerShape(8.dp))
                                .border(1.dp, colors.muted.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicTextField(
                                value = pickerQuery,
                                onValueChange = vm::setPickerQuery,
                                readOnly = !pickerSearchEditing,
                                singleLine = true,
                                textStyle = TextStyle(color = colors.onBackground, fontSize = 15.sp),
                                cursorBrush = SolidColor(colors.accent),
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                                    .onPreviewKeyEvent { event ->
                                        val activate = event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                                            event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                                        if (activate && event.type == KeyEventType.KeyUp) {
                                            pickerSearchEditing = true
                                            true
                                        } else false
                                    },
                                decorationBox = { inner ->
                                    Box {
                                        if (pickerQuery.isBlank()) {
                                            Text(stringResource(R.string.multiview_search_hint), color = colors.muted, fontSize = 15.sp)
                                        }
                                        inner()
                                    }
                                },
                            )
                            if (pickerQuery.isNotEmpty()) {
                                Box(
                                    Modifier.padding(start = 8.dp).dpadClickable {
                                        vm.setPickerQuery("")
                                        pickerSearchEditing = false
                                        keyboard?.hide()
                                    }.padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search), tint = colors.onBackground)
                                }
                            }
                        }
                        TvLazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) {
                            items(pickerChannels.itemCount) { index ->
                                val ch = pickerChannels[index] ?: return@items
                                FocusButton(
                                    label = if (ch.number > 0) "${ch.number}  ${ch.name}" else ch.name,
                                    onClick = {
                                        val tile = pickerFor
                                        if (tile in channels.indices) {
                                            vm.selectTile(tile, ch)
                                        }
                                        closePicker()
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                )
                            }
                        }
                            }
                        }
                        FocusButton(label = stringResource(R.string.action_cancel), onClick = closePicker)
                    }
                }
            }
        }
    }
}
