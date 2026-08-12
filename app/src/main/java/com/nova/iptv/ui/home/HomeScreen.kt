package com.nova.iptv.ui.home

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.nova.iptv.R
import com.nova.iptv.core.perf.LowRam
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.data.player.headersFor
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.HomeCategory
import com.nova.iptv.domain.model.ListStyle
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
import com.nova.iptv.ui.components.ChannelRow
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

@HiltViewModel
class HomePlayerBridge @Inject constructor(
    val playerManager: PlayerManager,
    val settings: com.nova.iptv.data.local.SettingsRepository,
) : ViewModel()

@Composable
fun HomeRoute(
    onNavigate: (String) -> Unit,
    onPlay: (PlayTarget) -> Unit,
    onDetail: (String, String) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
    bridge: HomePlayerBridge = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
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
        onWatch = { ch ->
            onPlay(PlayTarget.live(ch.id, ch.name, ch.number, ch.streamUrl))
        },
        onVod = { item ->
            onDetail(if (item.kind.name == "SERIES") "series" else "movie", item.id)
        },
        onToggleFav = vm::toggleFavorite,
        onHide = vm::hideChannel,
        onAssignEpg = vm::assignEpg,
        onSearchEpg = vm::searchEpgNames,
        onShowcase = {
            vm.installShowcase()
            onNavigate(Routes.Home)
        },
        onAddPlaylist = { onNavigate(Routes.AddPlaylist) },
        onDismissFirstRun = vm::dismissFirstRun,
        playerManager = bridge.playerManager,
        verifyPin = { pin -> bridge.settings.verifyPin(pin) },
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onCategory: (HomeCategory) -> Unit,
    onGroup: (String) -> Unit,
    onChannelFocus: (String?) -> Unit,
    onWatch: (Channel) -> Unit,
    onVod: (VodItem) -> Unit,
    onToggleFav: (String) -> Unit,
    onHide: (String) -> Unit,
    onAssignEpg: (String, String) -> Unit,
    onSearchEpg: (String, (List<Pair<String, String>>) -> Unit) -> Unit,
    onShowcase: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDismissFirstRun: () -> Unit,
    playerManager: PlayerManager,
    verifyPin: (String) -> Boolean,
) {
    val colors = LocalNovaPalette.current
    val catFocus = rememberFocusRestorer()
    val groupFocus = rememberFocusRestorer()
    val contentFocus = rememberFocusRestorer()
    var actionsFor by remember { mutableStateOf<Channel?>(null) }
    var assignFor by remember { mutableStateOf<Channel?>(null) }
    var pinFor by remember { mutableStateOf<Channel?>(null) }
    var pinError by remember { mutableStateOf(false) }
    val focusedVod = state.vod.firstOrNull { it.id == state.focusedChannelId }
        ?: state.vod.firstOrNull()
    val showPreview = state.settings.preview && !LowRam.isLowRam &&
        state.category == HomeCategory.LIVE && state.focusedChannelId != null

    val focusedChannel = state.channels.firstOrNull { it.id == state.focusedChannelId }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (focusedVod != null && focusedVod.backdropUrl.isNotBlank()) {
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
                CategoryPane(
                    selected = state.category,
                    onSelect = onCategory,
                    modifier = Modifier
                        .width(PaneCategories)
                        .fillMaxHeight()
                        .focusRequester(catFocus)
                        .focusProperties { right = groupFocus },
                )
                GroupPane(
                    groups = state.groups,
                    selected = state.groupId,
                    onSelect = onGroup,
                    modifier = Modifier
                        .width(PaneGroups)
                        .fillMaxHeight()
                        .focusRequester(groupFocus)
                        .focusProperties {
                            left = catFocus
                            right = contentFocus
                        },
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .focusRequester(contentFocus)
                        .focusProperties { left = groupFocus },
                ) {
                    when (state.category) {
                        HomeCategory.MOVIES, HomeCategory.SERIES -> VodPane(
                            items = state.vod,
                            history = state.history,
                            onClick = onVod,
                            onFocus = { onChannelFocus(it) },
                        )
                        else -> ChannelPane(
                            channels = state.channels,
                            state = state,
                            onWatch = { ch ->
                                val locked = state.settings.parentalEnabled &&
                                    (ch.locked || ch.groupName in state.settings.lockedGroups)
                                if (locked) pinFor = ch else onWatch(ch)
                            },
                            onLongPress = { actionsFor = it },
                            onFocus = onChannelFocus,
                        )
                    }
                }
                if (showPreview) {
                    PreviewPane(
                        channel = focusedChannel,
                        playerManager = playerManager,
                        modifier = Modifier.width(PanePreview).fillMaxHeight().padding(12.dp),
                    )
                }
            }
        }

        if (!state.settings.firstRunDone) {
            FirstRunCard(onShowcase = onShowcase, onAdd = onAddPlaylist, onSkip = onDismissFirstRun)
        }

        actionsFor?.let { ch ->
            ChannelActionsDialog(
                channel = ch,
                onWatch = { actionsFor = null; onWatch(ch) },
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
        pinFor?.let {
            PinDialog(
                title = stringResource(R.string.parental_enter_pin),
                error = pinError,
                onSubmit = { pin ->
                    if (verifyPin(pin)) {
                        pinError = false
                        val ch = pinFor
                        pinFor = null
                        if (ch != null) onWatch(ch)
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
    onSelect: (HomeCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    val cats = listOf(
        HomeCategory.LIVE, HomeCategory.MOVIES, HomeCategory.SERIES,
        HomeCategory.GUIDE, HomeCategory.RECORDINGS, HomeCategory.MULTIVIEW,
        HomeCategory.SEARCH, HomeCategory.SETTINGS,
    )
    TvLazyColumn(
        modifier = modifier.padding(start = 12.dp, end = 8.dp, top = 4.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        items(cats, key = { it.name }) { cat ->
            val active = cat == selected
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .scaleOnFocus(1.02f)
                    .glowBorderOnFocus(radius = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused || active) colors.accentDim else Color.Transparent)
                    .dpadClickable { onSelect(cat) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(CategoryIcon(cat), contentDescription = stringResource(categoryLabel(cat)), tint = if (focused) colors.accent else colors.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(categoryLabel(cat)), color = colors.onBackground, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun GroupPane(
    groups: List<GroupItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    if (groups.isEmpty()) {
        EmptyState(stringResource(R.string.empty_channels), stringResource(R.string.empty_channels_hint), modifier)
        return
    }
    TvLazyColumn(modifier = modifier.padding(end = 8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(groups, key = { it.id }) { g ->
            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .onFocusChanged { focused = it.isFocused }
                    .scaleOnFocus(1.02f)
                    .glowBorderOnFocus(radius = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused || g.id == selected) colors.accentDim else Color.Transparent)
                    .dpadClickable { onSelect(g.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(g.label, color = colors.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(g.count.toString(), color = colors.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChannelPane(
    channels: List<Channel>,
    state: HomeUiState,
    onWatch: (Channel) -> Unit,
    onLongPress: (Channel) -> Unit,
    onFocus: (String?) -> Unit,
) {
    if (channels.isEmpty()) {
        val title = when (state.groupId) {
            "favorites" -> stringResource(R.string.empty_favorites)
            "recent" -> stringResource(R.string.empty_history)
            else -> stringResource(R.string.empty_channels)
        }
        EmptyState(title, stringResource(R.string.empty_channels_hint), Modifier.fillMaxSize())
        return
    }
    val style = state.settings.listStyle
    if (style == ListStyle.LOGOS) {
        TvLazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(channels.size, key = { channels[it].id }) { idx ->
                val ch = channels[idx]
                ChannelRow(
                    channel = ch,
                    nowNext = state.nowNext[ch.id],
                    style = style,
                    showNumber = state.settings.showNumbers,
                    clock24h = state.settings.clock24h,
                    nowMs = state.nowMs,
                    focused = state.focusedChannelId == ch.id,
                    onClick = { onWatch(ch) },
                    onLongPress = { onLongPress(ch) },
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus(ch.id) },
                )
            }
        }
    } else {
        TvLazyColumn(modifier = Modifier.fillMaxSize().padding(end = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(channels, key = { it.id }) { ch ->
                ChannelRow(
                    channel = ch,
                    nowNext = state.nowNext[ch.id],
                    style = style,
                    showNumber = state.settings.showNumbers,
                    clock24h = state.settings.clock24h,
                    nowMs = state.nowMs,
                    focused = state.focusedChannelId == ch.id,
                    onClick = { onWatch(ch) },
                    onLongPress = { onLongPress(ch) },
                    modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus(ch.id) },
                )
            }
        }
    }
}

@Composable
private fun VodPane(
    items: List<VodItem>,
    history: List<com.nova.iptv.domain.model.WatchHistory>,
    onClick: (VodItem) -> Unit,
    onFocus: (String) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(stringResource(R.string.empty_movies), stringResource(R.string.empty_channels_hint), Modifier.fillMaxSize())
        return
    }
    val progress = history.associate { it.refId to it.fraction }
    TvLazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier.fillMaxSize().padding(8.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        items(items.size, key = { items[it].id }) { idx ->
            val item = items[idx]
            PosterCard(
                title = item.title,
                year = item.year,
                posterUrl = item.posterUrl,
                progress = progress[item.id] ?: 0f,
                onClick = { onClick(item) },
                modifier = Modifier.onFocusChanged { if (it.isFocused) onFocus(item.id) },
            )
        }
    }
}

@Composable
private fun PreviewPane(channel: Channel?, playerManager: PlayerManager, modifier: Modifier = Modifier) {
    val colors = LocalNovaPalette.current
    DisposableEffect(channel?.id) {
        val player = channel?.let {
            playerManager.previewPlayer(it.streamUrl, headersFor(it.userAgent, it.referrer))
        }
        onDispose { playerManager.releasePreview() }
    }
    GlassPanel(modifier) {
        Column(Modifier.padding(10.dp)) {
            Text("PREVIEW", color = colors.muted, fontSize = 11.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(8.dp))
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        player = playerManager.previewPlayer(channel?.streamUrl.orEmpty(), emptyMap())
                    }
                },
                update = { view ->
                    view.player = playerManager.previewPlayer(channel?.streamUrl.orEmpty(), emptyMap())
                },
                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(channel?.name.orEmpty(), color = colors.onBackground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FirstRunCard(onShowcase: () -> Unit, onAdd: () -> Unit, onSkip: () -> Unit) {
    val colors = LocalNovaPalette.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(520.dp)) {
            Column(Modifier.padding(28.dp)) {
                Text(stringResource(R.string.first_run_title), color = colors.onBackground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.first_run_body), color = colors.muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.first_run_legal), color = colors.muted, fontSize = 12.sp)
                Spacer(Modifier.height(20.dp))
                Row {
                    FocusButton(stringResource(R.string.first_run_showcase), onShowcase)
                    Spacer(Modifier.width(12.dp))
                    FocusButton(stringResource(R.string.first_run_add), onAdd)
                    Spacer(Modifier.width(12.dp))
                    FocusButton(stringResource(R.string.action_cancel), onSkip)
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
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).dpadClickable { onDismiss() }, contentAlignment = Alignment.Center) {
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
                ).forEach { (label, act) ->
                    FocusButton(label, act, Modifier.fillMaxWidth().padding(vertical = 4.dp))
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
    LaunchedEffect(q) { onSearch(q) { hits = it } }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(480.dp).height(360.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.assign_epg_title), color = colors.onBackground, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = q,
                    onValueChange = { q = it },
                    modifier = Modifier.fillMaxWidth().background(colors.surface2, RoundedCornerShape(6.dp)).padding(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.onBackground, fontSize = 14.sp),
                )
                TvLazyColumn(Modifier.weight(1f)) {
                    items(hits, key = { it.first + it.second }) { (id, name) ->
                        FocusButton("$name  ($id)", { onPick(id) }, Modifier.fillMaxWidth().padding(vertical = 3.dp))
                    }
                }
                FocusButton(stringResource(R.string.action_cancel), onDismiss)
            }
        }
    }
}
