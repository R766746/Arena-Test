package com.nova.iptv.ui.components

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.nova.iptv.R
import com.nova.iptv.core.util.TimeFmt
import com.nova.iptv.core.util.logoInitials
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.HomeCategory
import com.nova.iptv.domain.model.ListStyle
import com.nova.iptv.domain.model.NowNext
import com.nova.iptv.domain.model.Program
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.nav.glowBorderOnFocus
import com.nova.iptv.nav.scaleOnFocus
import com.nova.iptv.ui.theme.LocalNovaPalette
import com.nova.iptv.ui.theme.NovaFontFamily
import kotlinx.coroutines.delay

@Composable
fun NovaTopBar(
    clock24h: Boolean,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    val colors = LocalNovaPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.app_mark),
            color = colors.onBackground,
            fontFamily = NovaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            letterSpacing = 3.sp,
        )
        Spacer(Modifier.weight(1f))
        trailing()
        Text(
            text = TimeFmt.clockWithDate(now, clock24h),
            color = colors.muted,
            fontSize = 14.sp,
            fontFamily = NovaFontFamily,
        )
    }
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalNovaPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.glass),
    ) { content() }
}

@Composable
fun LogoTile(
    name: String,
    logoUrl: String,
    logoText: String,
    logoColor: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val initials = logoText.ifBlank { logoInitials(name) }
    val bg = Color(logoColor)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(bg.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNotBlank()) {
            Initials(initials)
            AsyncImage(
                model = logoUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Initials(initials)
        }
    }
}

@Composable
private fun Initials(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
}

@Composable
fun ChannelRow(
    channel: Channel,
    nowNext: NowNext?,
    style: ListStyle,
    showNumber: Boolean,
    clock24h: Boolean,
    nowMs: Long,
    recording: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onProgramClick: (Program) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (style == ListStyle.LIST) {
        LiveGuideChannelRow(
            channel = channel,
            nowNext = nowNext,
            showNumber = showNumber,
            clock24h = clock24h,
            nowMs = nowMs,
            recording = recording,
            onClick = onClick,
            onLongPress = onLongPress,
            onProgramClick = onProgramClick,
            modifier = modifier,
        )
        return
    }
    val colors = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    val now = nowNext?.now
    val progress = now?.progress(nowMs) ?: 0f
    val height = when (style) {
        ListStyle.COMPACT -> 48.dp
        ListStyle.LOGOS -> 88.dp
        ListStyle.LIST -> 64.dp
    }
    Row(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .fillMaxWidth()
            .height(height)
            .scaleOnFocus(1.02f)
            .glowBorderOnFocus(radius = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.accentDim else Color.Transparent)
            .dpadClickable(onLongPress = onLongPress, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showNumber) {
            Text(
                text = channel.number.toString().padStart(3, ' '),
                color = colors.muted,
                fontSize = 13.sp,
                modifier = Modifier.width(40.dp),
            )
        }
        LogoTile(channel.name, channel.logoUrl, channel.logoText, channel.logoColor, if (style == ListStyle.LOGOS) 64.dp else 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name,
                color = colors.onBackground,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            if (style != ListStyle.COMPACT) {
                Text(
                    now?.title ?: " ",
                    color = colors.muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (recording) {
            Text("● REC", color = Color(0xFFFF4D4D), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
        }
        if (now != null && style == ListStyle.LIST) {
            Column(horizontalAlignment = Alignment.End) {
                Text(TimeFmt.range(now.startMs, now.endMs, clock24h), color = colors.muted, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(72.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.surface2),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(colors.accent),
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    val colors = LocalNovaPalette.current
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = colors.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(body, color = colors.muted, fontSize = 14.sp)
    }
}

@Composable
fun PosterCard(
    title: String,
    year: Int,
    posterUrl: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    posterWidth: Dp = 140.dp,
    focusable: Boolean = true,
    loadImage: Boolean = true,
    accentFallback: Color = LocalNovaPalette.current.accent,
) {
    val colors = LocalNovaPalette.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val posterWidthPx = remember(posterWidth, density) {
        with(density) { posterWidth.roundToPx() }
    }
    val posterRequest = remember(posterUrl, posterWidthPx) {
        if (posterUrl.isBlank()) null else ImageRequest.Builder(context)
            .data(posterUrl)
            .size(posterWidthPx, (posterWidthPx * 1.5f).toInt())
            .precision(Precision.INEXACT)
            .build()
    }
    Column(
        modifier
            .width(posterWidth)
            .then(
                if (focusable) {
                    Modifier
                        .scaleOnFocus(1.025f)
                        .glowBorderOnFocus(radius = 12.dp)
                        .dpadClickable(onClick = onClick)
                } else Modifier,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(posterWidth * 1.5f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.radialGradient(
                        listOf(accentFallback.copy(0.55f), colors.surface),
                    ),
                ),
        ) {
            if (loadImage && posterRequest != null) {
                AsyncImage(
                    model = posterRequest,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 3)
                    if (year > 0) Text(year.toString(), color = Color.White.copy(0.7f), fontSize = 12.sp)
                }
            }
            if (progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(0.4f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(colors.accent),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = colors.onBackground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CategoryIcon(cat: HomeCategory): ImageVector = when (cat) {
    HomeCategory.LIVE -> Icons.Outlined.LiveTv
    HomeCategory.MOVIES -> Icons.Outlined.Movie
    HomeCategory.SERIES -> Icons.Outlined.VideoLibrary
    HomeCategory.GUIDE -> Icons.Outlined.Tv
    HomeCategory.RECORDINGS -> Icons.Outlined.FiberManualRecord
    HomeCategory.MULTIVIEW -> Icons.Outlined.ViewModule
    HomeCategory.SEARCH -> Icons.Outlined.Search
    HomeCategory.SETTINGS -> Icons.Outlined.Settings
}

@Composable
fun PinDialog(
    title: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
    error: Boolean,
) {
    val colors = LocalNovaPalette.current
    var value = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { cancelFocus.requestFocus() }
        }
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            GlassPanel(Modifier.width(360.dp).padding(8.dp)) {
                Column(Modifier.padding(24.dp)) {
                Text(title, color = colors.onBackground, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "•".repeat(value.value.length).padEnd(4, '·'),
                    color = colors.accent,
                    fontSize = 28.sp,
                    letterSpacing = 8.sp,
                )
                if (error) {
                    Text(stringResource(R.string.parental_wrong), color = colors.danger, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = value.value,
                    onValueChange = {
                        val digits = it.filter { c -> c.isDigit() }.take(4)
                        value.value = digits
                        if (digits.length == 4) onSubmit(digits)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp),
                    singleLine = true,
                )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusButton("Cancel", onDismiss, Modifier.focusRequester(cancelFocus))
                    }
                }
            }
        }
    }
}

@Composable
fun CenteredProgressIndicator(modifier: Modifier = Modifier) {
    val colors = LocalNovaPalette.current
    val transition = rememberInfiniteTransition(label = "loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            drawArc(
                color = colors.accent.copy(alpha = 0.2f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx())
            )
            drawArc(
                color = colors.accent,
                startAngle = rotation,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx())
            )
        }
    }
}

@Composable
fun FocusButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNovaPalette.current
    Box(
        modifier
            .scaleOnFocus(1.04f)
            .glowBorderOnFocus(radius = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface2)
            .dpadClickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(label, color = colors.onBackground, fontSize = 14.sp)
    }
}

@Composable
fun ExitConfirm(onExit: () -> Unit, onStay: () -> Unit) {
    val colors = LocalNovaPalette.current
    val stayFocus = remember { FocusRequester() }
    val exitFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onStay,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BackHandler(onBack = onStay)
        LaunchedEffect(Unit) {
            // Wait until the dialog focus target is attached. A dialog can be
            // dismissed during composition, so a stale requester must be safe.
            withFrameNanos { }
            runCatching { stayFocus.requestFocus() }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.width(420.dp)) {
                Column(Modifier.padding(28.dp)) {
                    Text(stringResource(R.string.exit_title), color = colors.onBackground, fontSize = 22.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.exit_body), color = colors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusButton(
                            stringResource(R.string.exit_stay),
                            onStay,
                            Modifier.focusRequester(stayFocus).focusProperties {
                                left = FocusRequester.Cancel
                                right = exitFocus
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            },
                        )
                        FocusButton(
                            stringResource(R.string.exit_confirm),
                            onExit,
                            Modifier.focusRequester(exitFocus).focusProperties {
                                left = stayFocus
                                right = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveGuideChannelRow(
    channel: Channel,
    nowNext: NowNext?,
    showNumber: Boolean,
    clock24h: Boolean,
    nowMs: Long,
    recording: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onProgramClick: (Program) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    var selectedCell by remember { mutableIntStateOf(0) }
    val now = nowNext?.now
    val next = nowNext?.next
    Row(
        Modifier
            .onPreviewKeyEvent { event ->
                if (!focused || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (selectedCell < 2) { selectedCell++; true } else false
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (selectedCell > 0) { selectedCell--; true } else false
                    }
                    else -> false
                }
            }
            // Programme-cell Left/Right must run before the parent list's
            // Left-to-categories handler supplied through [modifier].
            .then(modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) selectedCell = 0
            }
            .fillMaxWidth()
            .height(72.dp)
            .glowBorderOnFocus(radius = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) colors.accentDim else Color.Transparent)
            .dpadClickable(
                onLongPress = onLongPress,
                onClick = {
                    when (selectedCell) {
                        1 -> now?.let(onProgramClick) ?: onClick()
                        2 -> next?.let(onProgramClick) ?: onClick()
                        else -> onClick()
                    }
                },
            )
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(6.dp))
                .background(if (focused && selectedCell == 0) colors.accent.copy(alpha = 0.18f) else Color.Transparent)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showNumber) {
                Text(channel.number.toString(), color = colors.muted, fontSize = 12.sp, modifier = Modifier.width(34.dp))
            }
            LogoTile(channel.name, channel.logoUrl, channel.logoText, channel.logoColor, 40.dp)
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    color = colors.onBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = if (recording) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (recording) {
                    Text("● REC", color = Color(0xFFFF4D4D), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        GuideCell(
            title = now?.title ?: "No programme information",
            time = now?.let { TimeFmt.range(it.startMs, it.endMs, clock24h) }.orEmpty(),
            progress = now?.progress(nowMs),
            active = focused && selectedCell == 1,
            modifier = Modifier.weight(0.34f).fillMaxHeight(),
        )
        Spacer(Modifier.width(3.dp))
        GuideCell(
            title = next?.title ?: "No information",
            time = next?.let { TimeFmt.range(it.startMs, it.endMs, clock24h) }.orEmpty(),
            progress = null,
            active = focused && selectedCell == 2,
            modifier = Modifier.weight(0.24f).fillMaxHeight(),
        )
    }
}

@Composable
private fun GuideCell(
    title: String,
    time: String,
    progress: Float?,
    active: Boolean,
    modifier: Modifier,
) {
    val colors = LocalNovaPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) colors.accent.copy(alpha = 0.2f) else colors.surface2.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Column {
            Text(title, color = colors.onBackground, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (time.isNotBlank()) Text(time, color = colors.muted, fontSize = 10.sp, maxLines = 1)
        }
        if (progress != null) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(progress).height(3.dp).background(colors.accent),
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { cancelFocus.requestFocus() }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)), contentAlignment = Alignment.Center) {
            GlassPanel(Modifier.width(440.dp)) {
                Column(Modifier.padding(28.dp)) {
                    Text(title, color = colors.onBackground, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(body, color = colors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FocusButton(
                            dismissLabel ?: stringResource(R.string.action_cancel),
                            onDismiss,
                            Modifier.focusRequester(cancelFocus).focusProperties {
                                left = FocusRequester.Cancel
                                right = confirmFocus
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            },
                        )
                        FocusButton(
                            confirmLabel,
                            onConfirm,
                            Modifier.focusRequester(confirmFocus).focusProperties {
                                left = cancelFocus
                                right = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            },
                        )
                    }
                }
            }
        }
    }
}

fun categoryLabel(cat: HomeCategory): Int = when (cat) {
    HomeCategory.LIVE -> R.string.cat_live
    HomeCategory.MOVIES -> R.string.cat_movies
    HomeCategory.SERIES -> R.string.cat_series
    HomeCategory.GUIDE -> R.string.cat_guide
    HomeCategory.RECORDINGS -> R.string.cat_recordings
    HomeCategory.MULTIVIEW -> R.string.cat_multiview
    HomeCategory.SEARCH -> R.string.cat_search
    HomeCategory.SETTINGS -> R.string.cat_settings
}
