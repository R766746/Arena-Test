@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.nova.iptv.ui.player

import android.view.KeyEvent
import android.util.TypedValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import com.nova.iptv.R
import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.domain.model.AspectMode
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.digitFromKey
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.nav.scaleOnFocus
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.ConfirmDialog
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
    val noTimeshift = stringResource(R.string.player_no_timeshift)
    LaunchedEffect(target.id, target.kind, target.url) { vm.bind(target) }
    DisposableEffect(vm.playerManager) {
        onDispose { vm.playerManager.releaseMain() }
    }
    PlayerScreen(
        state = state,
        playerManager = vm.playerManager,
        playerView = {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = when (state.aspect) {
                            AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                            AspectMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, state.subtitleSize.toFloat())
                        player = vm.playerManager.mainPlayer()
                    }
                },
                update = { view ->
                    view.resizeMode = when (state.aspect) {
                        AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                        AspectMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    view.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, state.subtitleSize.toFloat())
                },
                modifier = Modifier.fillMaxSize(),
            )
        },
        onKey = { code, down ->
            if (!down) return@PlayerScreen false
            // Once the overlay or options sheet is visible, the D-pad belongs to
            // Compose focus navigation. Consuming these events here made attempts
            // to move between controls zap channels or seek instead.
            if ((state.overlay || state.sheet) && code in setOf(
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                )
            ) {
                vm.bumpOverlay()
                return@PlayerScreen false
            }
            when (code) {
                KeyEvent.KEYCODE_CHANNEL_UP -> { vm.zap(1); true }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.zap(-1); true }
                KeyEvent.KEYCODE_DPAD_UP -> { vm.zap(1); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { vm.zap(-1); true }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> { vm.toggleSheet(); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    vm.bumpOverlay()
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    vm.seek(-vm.settings.settings.value.seekStepSec * 1000L)
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    vm.seek(vm.settings.settings.value.seekStepSec * 1000L)
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    val p = vm.playerManager.mainPlayer()
                    if (p.isPlaying) {
                        if (!state.seekable && p.isCurrentMediaItemLive && !p.isCurrentMediaItemSeekable) {
                            vm.showTimeshiftUnavailable(noTimeshift)
                        } else p.pause()
                    } else p.play()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> { vm.next(); true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { vm.previous(); true }
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
        onBack = {
            // Stop decoding before Home restores its catalog and image requests.
            vm.playerManager.stopMain()
            onBack()
        },
        onFavorite = vm::toggleFavorite,
        onGuide = onGuide,
        onMulti = onMulti,
        onRecord = vm::startRecording,
        onStopRecording = vm::stopRecording,
        onRetry = vm::retry,
        onExternal = {
            val url = state.target?.url.orEmpty()
            vm.playerManager.openExternal(url, vm.settings.settings.value.externalPlayerPackage.ifBlank { null })
        },
        onCloseSheet = { vm.toggleSheet() },
        onTogglePlayback = vm::togglePlayback,
        onSeekBack = { vm.seek(-vm.settings.settings.value.seekStepSec * 1000L) },
        onSeekForward = { vm.seek(vm.settings.settings.value.seekStepSec * 1000L) },
        onPrevious = vm::previous,
        onNext = vm::next,
        onHideOverlay = vm::hideOverlay,
        onCycleAspect = vm::cycleAspect,
        onStartOver = { vm.startOver(noTimeshift) },
        onPlayNextNow = vm::playNextNow,
        onCancelAutoNext = vm::cancelAutoNext,
    )
}

@Composable
fun PlayerScreen(
    state: PlayerUiState,
    playerManager: PlayerManager,
    playerView: @Composable () -> Unit,
    onKey: (Int, Boolean) -> Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onGuide: () -> Unit,
    onMulti: () -> Unit,
    onRecord: () -> Unit,
    onStopRecording: () -> Unit,
    onRetry: () -> Unit,
    onExternal: () -> Unit,
    onCloseSheet: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onHideOverlay: () -> Unit,
    onCycleAspect: () -> Unit,
    onStartOver: () -> Unit,
    onPlayNextNow: () -> Unit,
    onCancelAutoNext: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    var subMenu by remember { mutableStateOf<String?>(null) }
    val zapFade = remember { Animatable(0f) }
    val playerSurfaceFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val sheetFocus = remember { FocusRequester() }
    var controlFocusInitializing by remember { mutableStateOf(false) }
    var confirmStopRecording by remember { mutableStateOf(false) }

    LaunchedEffect(state.overlay, state.sheet) {
        if (state.overlay && !state.sheet) {
            controlFocusInitializing = true
            // AnimatedVisibility may compose before its controls are placed. Wait
            // for two rendered frames so focus cannot fall back to another action.
            withFrameNanos { }
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        } else if (!state.overlay && !state.sheet) {
            controlFocusInitializing = false
            // The focused control is removed when the overlay hides. Move focus
            // back to the permanent player surface so OK can reopen controls.
            kotlinx.coroutines.delay(30)
            runCatching { playerSurfaceFocus.requestFocus() }
        }
    }

    LaunchedEffect(state.sheet) {
        if (!state.sheet) subMenu = null
    }

    LaunchedEffect(state.sheet, subMenu) {
        if (state.sheet) {
            kotlinx.coroutines.delay(30)
            runCatching { sheetFocus.requestFocus() }
        }
    }

    LaunchedEffect(state.zapTransition) {
        if (state.zapTransition != 0L) {
            zapFade.snapTo(0f)
            zapFade.animateTo(1f, tween(60))
            zapFade.animateTo(0f, tween(60))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerSurfaceFocus)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    when {
                        state.sheet && subMenu != null -> subMenu = null
                        state.sheet -> onCloseSheet()
                        state.overlay -> onHideOverlay()
                        else -> onBack()
                    }
                    true
                } else if (
                    state.overlay && !state.sheet && controlFocusInitializing &&
                    ev.type == KeyEventType.KeyDown &&
                    ev.nativeKeyEvent.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
                ) {
                    // Repeated OK while the overlay is still assigning focus must
                    // never activate a fallback control or escape the player.
                    runCatching { playFocus.requestFocus() }
                    true
                } else onKey(ev.nativeKeyEvent.keyCode, ev.type == KeyEventType.KeyDown)
            },
    ) {
        playerView()

        if (zapFade.value > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = zapFade.value }.background(Color.Black))
        }

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
                    if (state.activeRecordingId != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFD63232))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(stringResource(R.string.player_recording_badge), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
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
                    val progress = if (state.seekable && state.duration > 0L) {
                        (state.position.toFloat() / state.duration).coerceIn(0f, 1f)
                    } else {
                        state.nowNext.now?.progress(System.currentTimeMillis()) ?: 0f
                    }
                    val buffered = if (state.seekable && state.duration > 0L) {
                        (state.bufferedPosition.toFloat() / state.duration).coerceIn(0f, 1f)
                    } else progress
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.2f))) {
                        Box(Modifier.fillMaxWidth(buffered).height(4.dp).background(Color.White.copy(alpha = 0.38f)))
                        Box(Modifier.fillMaxWidth(progress).height(4.dp).background(colors.accent))
                    }
                    if (state.seekable && state.duration > 0L) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(playerTime(state.position), color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
                            Spacer(Modifier.weight(1f))
                            Text("−${playerTime((state.duration - state.position).coerceAtLeast(0L))}", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp)
                        }
                    } else if (state.isBuffering) {
                        Text("BUFFERING…", color = colors.accent, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerControlIcon(Icons.Default.SkipPrevious, "Previous", enabled = state.isLive || state.hasPreviousEpisode || !state.target?.kind.equals("SERIES", true), onClick = onPrevious)
                        Spacer(Modifier.width(12.dp))
                        PlayerControlIcon(Icons.Default.FastRewind, "Rewind", enabled = state.seekable, onClick = onSeekBack)
                        Spacer(Modifier.width(14.dp))
                        PlayerControlIcon(
                            icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            label = if (state.isPlaying) "Pause" else "Play",
                            onClick = onTogglePlayback,
                            modifier = Modifier
                                .focusRequester(playFocus)
                                .onFocusChanged {
                                    if (it.isFocused) controlFocusInitializing = false
                                },
                            primary = true,
                        )
                        Spacer(Modifier.width(14.dp))
                        PlayerControlIcon(Icons.Default.FastForward, "Forward", enabled = state.seekable, onClick = onSeekForward)
                        Spacer(Modifier.width(12.dp))
                        PlayerControlIcon(Icons.Default.SkipNext, "Next", enabled = state.isLive || state.hasNextEpisode, onClick = onNext)
                        Spacer(Modifier.width(28.dp))
                        PlayerControlIcon(Icons.Default.Audiotrack, "Audio", onClick = {
                            subMenu = "audio"
                            if (!state.sheet) onCloseSheet()
                        })
                        Spacer(Modifier.width(12.dp))
                        PlayerControlIcon(Icons.Default.ClosedCaption, "Subtitles", onClick = {
                            subMenu = "sub"
                            if (!state.sheet) onCloseSheet()
                        })
                        Spacer(Modifier.width(12.dp))
                        PlayerControlIcon(Icons.Default.MoreVert, "Options", onClick = {
                            subMenu = null
                            if (!state.sheet) onCloseSheet()
                        })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.quickZapVisible && state.isLive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = 30.dp),
        ) {
            Row(
                Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.channel?.let { channel ->
                    LogoTile(channel.name, channel.logoUrl, channel.logoText, channel.logoColor, 58.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        state.channel?.number?.takeIf { it > 0 }?.let { number ->
                            Text(number.toString(), color = colors.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            state.channel?.name ?: state.target?.title.orEmpty(),
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        state.nowNext.now?.title ?: "No programme information",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                    state.nowNext.next?.title?.takeIf { it.isNotBlank() }?.let { next ->
                        Text("Next: $next", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        if (state.zapDigits.isNotBlank()) {
            Box(Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(0.7f)).padding(horizontal = 28.dp, vertical = 16.dp)) {
                Text(state.zapDigits, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        AnimatedVisibility(
            visible = state.message != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surface2)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(state.message.orEmpty(), color = colors.onBackground, fontSize = 14.sp)
            }
        }

        state.nextEpisodeCountdown?.let { seconds ->
            GlassPanel(Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 150.dp).width(360.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Next episode in $seconds", color = colors.onBackground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(state.nextEpisodeTitle, color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusButton(label = "Play now", onClick = onPlayNextNow)
                        FocusButton(label = "Cancel", onClick = onCancelAutoNext)
                    }
                }
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
                            FocusButton(label = stringResource(R.string.player_retry), onClick = onRetry)
                            FocusButton(label = stringResource(R.string.player_external), onClick = onExternal)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(state.sheet, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterEnd)) {
            GlassPanel(Modifier.width(280.dp).fillMaxHeight().padding(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (subMenu == null) {
                        Text("OPTIONS", color = colors.muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
                        FocusButton(label = stringResource(R.string.overlay_favorite), onClick = onFavorite, modifier = Modifier.focusRequester(sheetFocus).fillMaxWidth())
                        FocusButton(label = stringResource(R.string.overlay_multiview), onClick = onMulti, modifier = Modifier.fillMaxWidth())
                        FocusButton(label = stringResource(R.string.overlay_guide), onClick = onGuide, modifier = Modifier.fillMaxWidth())
                        if (state.isLive && state.nowNext.now != null) {
                            FocusButton(
                                label = stringResource(
                                    if (state.activeRecordingId != null) R.string.overlay_stop_recording
                                    else R.string.overlay_record,
                                ),
                                onClick = {
                                    if (state.activeRecordingId != null) confirmStopRecording = true
                                    else onRecord()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (state.isLive) {
                            FocusButton(label = stringResource(R.string.player_start_over), onClick = onStartOver, modifier = Modifier.fillMaxWidth())
                        }
                        FocusButton(label = stringResource(R.string.overlay_audio), onClick = { subMenu = "audio" }, modifier = Modifier.fillMaxWidth())
                        FocusButton(label = stringResource(R.string.overlay_subtitles), onClick = { subMenu = "sub" }, modifier = Modifier.fillMaxWidth())
                        FocusButton(label = "Video quality", onClick = { subMenu = "video" }, modifier = Modifier.fillMaxWidth())
                        FocusButton(label = "Aspect: ${state.aspect.name.replace('_', ':')}", onClick = onCycleAspect, modifier = Modifier.fillMaxWidth())
                    } else {
                        val isAudio = subMenu == "audio"
                        val isVideo = subMenu == "video"
                        Text(when { isAudio -> "AUDIO"; isVideo -> "VIDEO QUALITY"; else -> "SUBTITLES" }, color = colors.muted, fontSize = 11.sp, letterSpacing = 1.6.sp)
                        FocusButton(
                            label = stringResource(R.string.cd_back),
                            onClick = { subMenu = null },
                            modifier = Modifier.focusRequester(sheetFocus).fillMaxWidth(),
                        )
                        
                        val type = when { isAudio -> C.TRACK_TYPE_AUDIO; isVideo -> C.TRACK_TYPE_VIDEO; else -> C.TRACK_TYPE_TEXT }
                        val groups = playerManager.currentTracks()?.groups ?: emptyList()
                        
                        if (isVideo) {
                            FocusButton(
                                label = "Auto",
                                onClick = {
                                    playerManager.clearVideoOverride()
                                    onCloseSheet()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else if (!isAudio) {
                            FocusButton(
                                label = "Off",
                                onClick = {
                                    playerManager.clearSubtitle()
                                    onCloseSheet()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        groups.forEachIndexed { gIdx, group ->
                            if (group.type == type) {
                                for (tIdx in 0 until group.length) {
                                    val format = group.getTrackFormat(tIdx)
                                    val name = if (isVideo && format.height > 0) {
                                        "${format.height}p${format.bitrate.takeIf { it > 0 }?.let { "  ${it / 1_000} kbps" }.orEmpty()}"
                                    } else format.label ?: format.language ?: "Track ${tIdx + 1}"
                                    val selected = group.isTrackSelected(tIdx)
                                    FocusButton(
                                        label = if (selected) "● $name" else name,
                                        onClick = {
                                            when {
                                                isAudio -> playerManager.selectAudio(gIdx, tIdx)
                                                isVideo -> playerManager.selectVideo(gIdx, tIdx)
                                                else -> playerManager.selectSubtitle(gIdx, tIdx)
                                            }
                                            onCloseSheet()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (confirmStopRecording) {
            ConfirmDialog(
                title = stringResource(R.string.recording_stop_title),
                body = stringResource(R.string.recording_stop_body, state.channel?.name ?: state.target?.title.orEmpty()),
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
private fun PlayerControlIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val colors = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    val diameter = if (primary) 58.dp else 46.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier
                .size(diameter)
                .onFocusChanged { focused = it.isFocused }
                .scaleOnFocus(if (primary) 1.08f else 1.12f)
                .clip(RoundedCornerShape(diameter / 2))
                .background(
                    when {
                        !enabled -> Color.White.copy(alpha = 0.05f)
                        focused -> colors.accent
                        primary -> Color.White.copy(alpha = 0.18f)
                        else -> Color.Black.copy(alpha = 0.28f)
                    },
                )
                .border(
                    1.dp,
                    if (focused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.16f),
                    RoundedCornerShape(diameter / 2),
                )
                .dpadClickable { if (enabled) onClick() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.tv.material3.Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.28f),
                modifier = Modifier.size(if (primary) 32.dp else 25.dp),
            )
        }
        Text(
            label,
            color = if (focused) Color.White else Color.White.copy(alpha = if (enabled) 0.68f else 0.3f),
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun playerTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
