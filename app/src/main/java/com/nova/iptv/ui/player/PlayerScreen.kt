package com.nova.iptv.ui.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.nova.iptv.R
import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.domain.model.AspectMode
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.digitFromKey
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.GlassPanel
import com.nova.iptv.ui.components.LogoTile
import com.nova.iptv.ui.theme.LocalNovaPalette

@Composable
fun PlayerRoute(
    target: PlayTarget,
    onBack: () -> Unit,
    onGuide: () -> Unit,
    onMulti: () -> Unit,
    onZapTarget: (PlayTarget) -> Unit,
    vm: PlayerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(target.id, target.kind, target.url) { vm.bind(target) }
    PlayerScreen(
        state = state,
        playerView = {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = when (vm.settings.settings.value.aspect) {
                            AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            AspectMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        player = vm.playerManager.mainPlayer()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
        onKey = { code, down ->
            if (!down) return@PlayerScreen false
            when (code) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(1); true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); true }
                KeyEvent.KEYCODE_DPAD_UP -> { vm.zap(1); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { vm.zap(-1); true }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { vm.toggleSheet(); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (state.seekable) vm.bumpOverlay() else vm.toggleSheet()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (state.seekable) vm.playerManager.seekBy(-vm.settings.settings.value.seekStepSec * 1000L)
                    vm.bumpOverlay()
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (state.seekable) vm.playerManager.seekBy(vm.settings.settings.value.seekStepSec * 1000L)
                    vm.bumpOverlay()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    val p = vm.playerManager.mainPlayer()
                    if (p.isPlaying) {
                        if (!state.seekable && p.isCurrentMediaItemLive && !p.isCurrentMediaItemSeekable) {
                            // toast via overlay message
                            vm.bumpOverlay()
                        } else p.pause()
                    } else p.play()
                    true
                }
                else -> {
                    val d = digitFromKey(code)
                    if (d != null) {
                        vm.digit(d); true
                    } else {
                        vm.bumpOverlay()
                        false
                    }
                }
            }
        },
        onBack = onBack,
        onFavorite = vm::toggleFavorite,
        onGuide = onGuide,
        onMulti = onMulti,
        onRecord = vm::recordNow,
        onRetry = vm::retry,
        onExternal = {
            val url = state.target?.url.orEmpty()
            vm.playerManager.openExternal(url, vm.settings.settings.value.externalPlayerPackage.ifBlank { null })
        },
        onCloseSheet = { vm.toggleSheet() },
    )
}

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    playerView: @Composable () -> Unit,
    onKey: (Int, Boolean) -> Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onGuide: () -> Unit,
    onMulti: () -> Unit,
    onRecord: () -> Unit,
    onRetry: () -> Unit,
    onExternal: () -> Unit,
    onCloseSheet: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                onKey(ev.nativeKeyCode, ev.type == KeyEventType.KeyDown)
            },
    ) {
        playerView()

        AnimatedVisibility(state.overlay, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.65f), Color.Transparent))),
                )
                Row(
                    Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FocusButton(stringResource(R.string.cd_back), onBack)
                    Spacer(Modifier.width(12.dp))
                    val pill = when {
                        state.isCatchup -> stringResource(R.string.catchup_pill)
                        state.isLive -> stringResource(R.string.live_pill)
                        else -> stringResource(R.string.vod_pill)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (state.isLive) colors.live else colors.accent)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(pill, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f))))
                        .padding(24.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.channel?.let {
                            LogoTile(it.name, it.logoUrl, it.logoText, it.logoColor, 48.dp)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(state.channel?.name ?: state.target?.title.orEmpty(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            val now = state.nowNext.now
                            if (now != null) {
                                Text(now.title, color = Color.White.copy(0.85f), fontSize = 14.sp)
                                Text(
                                    "${TimeFmt.range(now.startMs, now.endMs, state.clock24h)}  ·  ${TimeFmt.remaining(now.endMs, System.currentTimeMillis())} left",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 12.sp,
                                )
                            }
                            state.nowNext.next?.let {
                                Text(stringResource(R.string.next_program, it.title), color = Color.White.copy(0.55f), fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    val progress = state.nowNext.now?.progress(System.currentTimeMillis()) ?: 0f
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.2f))) {
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(colors.accent))
                    }
                }
            }
        }

        if (state.zapDigits.isNotBlank()) {
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(0.7f)).padding(horizontal = 28.dp, vertical = 16.dp)) {
                Text(state.zapDigits, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (state.error != null) {
            Box(Modifier.align(Alignment.Center)) {
                GlassPanel(Modifier.width(420.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.player_error), color = colors.onBackground, fontSize = 20.sp)
                        Text(state.error, color = colors.muted, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FocusButton(stringResource(R.string.player_retry), onRetry)
                            FocusButton(stringResource(R.string.player_external), onExternal)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(state.sheet, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterEnd)) {
            GlassPanel(Modifier.width(280.dp).fillMaxHeight().padding(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("OPTIONS", color = colors.muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
                    FocusButton(stringResource(R.string.overlay_favorite), onFavorite, Modifier.fillMaxWidth())
                    FocusButton(stringResource(R.string.overlay_multiview), onMulti, Modifier.fillMaxWidth())
                    FocusButton(stringResource(R.string.overlay_guide), onGuide, Modifier.fillMaxWidth())
                    FocusButton(stringResource(R.string.overlay_record), onRecord, Modifier.fillMaxWidth())
                    FocusButton(stringResource(R.string.overlay_audio), onCloseSheet, Modifier.fillMaxWidth())
                    FocusButton(stringResource(R.string.overlay_subtitles), onCloseSheet, Modifier.fillMaxWidth())
                    FocusButton(stringResource(R.string.overlay_aspect), onCloseSheet, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
