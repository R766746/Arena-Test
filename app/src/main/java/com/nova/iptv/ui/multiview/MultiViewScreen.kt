@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.nova.iptv.ui.multiview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.nova.iptv.R
import com.nova.iptv.core.perf.LowRam
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.data.player.headersFor
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.TvLazyVerticalGrid
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.nav.glowBorderOnFocus
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MultiViewViewModel @Inject constructor(
    val playerManager: PlayerManager,
    playlists: PlaylistRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val favorites = settings.settings.flatMapLatest {
        playlists.favorites(it.lastPlaylistId.ifBlank { "demo" })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())
    val clock24h = settings.settings
}

@Composable
fun MultiViewRoute(
    onFullscreen: (PlayTarget) -> Unit,
    onBack: () -> Unit,
    vm: MultiViewViewModel = hiltViewModel(),
) {
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val settings by vm.clock24h.collectAsStateWithLifecycle()
    val colors = LocalNovaPalette.current
    var layout by remember { mutableIntStateOf(if (LowRam.isLowRam) 4 else 4) }
    var pickerFor by remember { mutableIntStateOf(-1) }
    var channels by remember(favs, layout) {
        val base = favs.take(layout).toMutableList()
        while (base.size < layout && favs.isNotEmpty()) base += favs[base.size % favs.size]
        mutableStateOf(base.toList())
    }
    var warning by remember { mutableStateOf(false) }
    var players by remember { mutableStateOf(emptyList<androidx.media3.exoplayer.ExoPlayer>()) }

    DisposableEffect(layout, channels.map { it.id }.joinToString()) {
        val requested = if (layout == 9 && LowRam.isLowRam) {
            warning = true
            4
        } else layout
        val created = vm.playerManager.createMultiView(requested)
        created.forEachIndexed { i, p ->
            val ch = channels.getOrNull(i)
            if (ch != null) {
                p.volume = 0f
                runCatching {
                    p.setMediaItem(androidx.media3.common.MediaItem.fromUri(ch.streamUrl))
                    p.prepare()
                    p.playWhenReady = true
                }.onFailure {
                    if (layout == 9) {
                        vm.playerManager.dropMultiViewTo(4)
                        layout = 4
                    }
                }
            }
        }
        players = created
        onDispose {
            players = emptyList()
            vm.playerManager.releaseMultiView()
        }
    }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        NovaTopBar(settings.clock24h) {
            Row {
                listOf(2, 4, 9).forEach { n ->
                    FocusButton(label = "${n}-up", onClick = {
                        if (n == 9 && LowRam.isLowRam) {
                            warning = true
                            layout = 4
                        } else layout = n
                    })
                }
            }
        }
        if (warning) {
            Text(stringResource(R.string.multiview_lowram), color = colors.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
        }
        val cols = when (layout) {
            2 -> 2
            9 -> 3
            else -> 2
        }
        TvLazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxSize().padding(12.dp),
        ) {
            items(layout) { idx ->
                val ch = channels.getOrNull(idx)
                var focused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .padding(6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .glowBorderOnFocus(radius = 10.dp)
                        .onFocusChanged {
                            focused = it.isFocused
                            players.getOrNull(idx)?.volume = if (it.isFocused) 1f else 0f
                        }
                        .dpadClickable(
                            onLongPress = { pickerFor = idx },
                            onClick = {
                                ch?.let { onFullscreen(PlayTarget.live(it.id, it.name, it.number, it.streamUrl)) }
                            },
                        ),
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                player = players.getOrNull(idx)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(0.dp),
                    )
                    Text(
                        ch?.name.orEmpty(),
                        color = colors.onBackground,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    )
                }
            }
        }
        if (pickerFor >= 0) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(0.85f))
                    .padding(48.dp),
            ) {
                Column {
                    Text(stringResource(R.string.multiview_pick), color = colors.onBackground, fontSize = 18.sp)
                    favs.forEach { ch ->
                        FocusButton(label = ch.name, onClick = {
                            val tile = pickerFor
                            if (tile in channels.indices) {
                                channels = channels.toMutableList().also { it[tile] = ch }
                            }
                            pickerFor = -1
                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    }
                    FocusButton(label = stringResource(R.string.action_cancel), onClick = { pickerFor = -1 })
                }
            }
        }
    }
}
