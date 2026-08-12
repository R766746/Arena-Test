package com.nova.iptv.ui.guide

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.nova.iptv.R
import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.data.player.CatchupUrlBuilder
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.EpgRowHeight
import com.nova.iptv.domain.model.GuideRow
import com.nova.iptv.domain.model.Program
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.TvLazyColumn
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.nav.glowBorderOnFocus
import com.nova.iptv.nav.scaleOnFocus
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.GlassPanel
import com.nova.iptv.ui.components.LogoTile
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.theme.LocalNovaPalette
import com.nova.iptv.ui.theme.rowHeight

@Composable
fun GuideRoute(
    onPlay: (PlayTarget) -> Unit,
    onBack: () -> Unit,
    vm: GuideViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    GuideScreen(
        state = state,
        onSelect = vm::select,
        onDismiss = vm::dismiss,
        onWatchLive = { ch ->
            vm.dismiss()
            onPlay(PlayTarget.live(ch.id, ch.name, ch.number, ch.streamUrl))
        },
        onCatchup = { ch, p ->
            vm.dismiss()
            onPlay(PlayTarget.catchup(ch.id, p.id, p.startMs, p.endMs, p.title, null))
        },
        onRecord = vm::recordSelected,
        onRemind = { ch, p -> vm.remind(ch, p) },
        onFav = vm::toggleFavorite,
        onShift = vm::shiftWindow,
        onBack = onBack,
    )
}

@Composable
fun GuideScreen(
    state: GuideUiState,
    onSelect: (Channel, Program?) -> Unit,
    onDismiss: () -> Unit,
    onWatchLive: (Channel) -> Unit,
    onCatchup: (Channel, Program) -> Unit,
    onRecord: () -> Unit,
    onRemind: (Channel, Program) -> Unit,
    onFav: () -> Unit,
    onShift: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    val rowH = rowHeight(state.settings)
    val pxPerHour = 220.dp
    val windowPx = pxPerHour * state.hours
    val nowOffset = ((state.now - state.windowStart).toFloat() / (state.hours * 3600_000f)).coerceIn(0f, 1f)

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.nativeKeyCode) {
                    KeyEvent.KEYCODE_CHANNEL_UP -> { onShift(-1); true }
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> { onShift(1); true }
                    else -> false
                }
            },
    ) {
        NovaTopBar(clock24h = state.settings.clock24h)
        TimeHeader(state.windowStart, state.hours, state.settings.clock24h, pxPerHour)
        if (state.rows.isEmpty()) {
            EmptyState(stringResource(R.string.empty_epg), stringResource(R.string.empty_channels_hint), Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f)) {
                TvLazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.rows, key = { _, r -> r.channel.id }) { _, row ->
                        GuideRowView(
                            row = row,
                            windowStart = state.windowStart,
                            hours = state.hours,
                            now = state.now,
                            rowHeight = rowH,
                            pxPerHour = pxPerHour,
                            grid = state.settings.epgGridLines,
                            clock24h = state.settings.clock24h,
                            onProgram = { onSelect(row.channel, it) },
                        )
                    }
                }
                // now line
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .offset(x = 230.dp + windowPx * nowOffset)
                        .background(colors.live),
                )
            }
        }
    }

    state.selected?.let { program ->
        val ch = state.selectedChannel
        ProgramInfoDialog(
            program = program,
            channel = ch,
            now = state.now,
            clock24h = state.settings.clock24h,
            debug = state.settings.debugEpgTimes,
            onWatch = { ch?.let(onWatchLive) },
            onCatchup = { if (ch != null) onCatchup(ch, program) },
            onRecord = onRecord,
            onRemind = { if (ch != null) onRemind(ch, program) },
            onFav = onFav,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun TimeHeader(start: Long, hours: Int, clock24h: Boolean, pxPerHour: Dp) {
    val colors = LocalNovaPalette.current
    val step = 30 * 60_000L
    Row(Modifier.fillMaxWidth().padding(start = 230.dp, bottom = 6.dp)) {
        var t = start
        val end = start + hours * 3600_000L
        while (t < end) {
            Text(
                TimeFmt.clock(t, clock24h),
                color = colors.muted,
                fontSize = 11.sp,
                modifier = Modifier.width(pxPerHour / 2),
            )
            t += step
        }
    }
}

@Composable
private fun GuideRowView(
    row: GuideRow,
    windowStart: Long,
    hours: Int,
    now: Long,
    rowHeight: Dp,
    pxPerHour: Dp,
    grid: Boolean,
    clock24h: Boolean,
    onProgram: (Program) -> Unit,
) {
    val colors = LocalNovaPalette.current
    val windowMs = hours * 3600_000f
    Row(Modifier.fillMaxWidth().height(rowHeight).padding(vertical = 2.dp)) {
        Row(
            Modifier.width(230.dp).fillMaxHeight().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogoTile(row.channel.name, row.channel.logoUrl, row.channel.logoText, row.channel.logoColor, 36.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(row.channel.name, color = colors.onBackground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(row.channel.number.toString(), color = colors.muted, fontSize = 11.sp)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            if (grid) {
                val n = hours * 2
                Row(Modifier.fillMaxSize()) {
                    repeat(n) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(if (it % 2 == 0) Color.Transparent else colors.surface.copy(0.35f)))
                    }
                }
            }
            row.programs.forEach { p ->
                val startFrac = ((p.startMs - windowStart) / windowMs).coerceIn(-0.1f, 1f)
                val endFrac = ((p.endMs - windowStart) / windowMs).coerceIn(0f, 1.1f)
                val frac = (endFrac - startFrac).coerceAtLeast(0.02f)
                val past = p.isPast(now)
                val minW = 48.dp
                ProgramCell(
                    program = p,
                    past = past,
                    now = now,
                    clock24h = clock24h,
                    catchup = row.channel.catchup && past,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(frac)
                        .align(Alignment.CenterStart)
                        .offset(x = with(LocalDensity.current) { (startFrac * (pxPerHour * hours).toPx()).toDp() })
                        .padding(end = 3.dp),
                    onClick = { onProgram(p) },
                )
            }
        }
    }
}

@Composable
private fun ProgramCell(
    program: Program,
    past: Boolean,
    now: Long,
    clock24h: Boolean,
    catchup: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    Box(
        modifier
            .scaleOnFocus(1.02f)
            .glowBorderOnFocus(radius = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (program.isNow(now)) colors.accentDim else colors.surface2)
            .alpha(if (past) 0.55f else 1f)
            .dpadClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Column {
            Text(program.title, color = colors.onBackground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(TimeFmt.range(program.startMs, program.endMs, clock24h), color = colors.muted, fontSize = 11.sp, maxLines = 1)
        }
        if (program.isNow(now)) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(program.progress(now))
                    .height(2.dp)
                    .background(colors.accent),
            )
        }
        if (catchup) {
            Text(
                "CATCH-UP",
                color = colors.accent,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
private fun ProgramInfoDialog(
    program: Program,
    channel: Channel?,
    now: Long,
    clock24h: Boolean,
    debug: Boolean,
    onWatch: () -> Unit,
    onCatchup: () -> Unit,
    onRecord: () -> Unit,
    onRemind: () -> Unit,
    onFav: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(520.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(program.title, color = colors.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${channel?.name.orEmpty()}  ·  ${TimeFmt.range(program.startMs, program.endMs, clock24h)}",
                    color = colors.muted,
                    fontSize = 13.sp,
                )
                if (debug) {
                    Text("raw ${program.startMs} → ${program.endMs}", color = colors.muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text(program.description.ifBlank { "No description." }, color = colors.onBackground, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))
                Row {
                    FocusButton(stringResource(R.string.watch_live), onWatch)
                    Spacer(Modifier.width(8.dp))
                    if (program.isPast(now) && channel?.catchup == true) {
                        FocusButton(stringResource(R.string.play_catchup), onCatchup)
                        Spacer(Modifier.width(8.dp))
                    }
                    FocusButton(stringResource(R.string.action_record), onRecord)
                    Spacer(Modifier.width(8.dp))
                    FocusButton(stringResource(R.string.remind), onRemind)
                    Spacer(Modifier.width(8.dp))
                    FocusButton(stringResource(R.string.action_favorite_add), onFav)
                    Spacer(Modifier.width(8.dp))
                    FocusButton(stringResource(R.string.action_cancel), onDismiss)
                }
            }
        }
    }
}
