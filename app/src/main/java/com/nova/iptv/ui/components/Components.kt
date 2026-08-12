package com.nova.iptv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
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
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
                error = { Initials(initials) },
                loading = { Initials(initials) },
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
    focused: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNovaPalette.current
    val now = nowNext?.now
    val progress = now?.progress(nowMs) ?: 0f
    val height = when (style) {
        ListStyle.COMPACT -> 48.dp
        ListStyle.LOGOS -> 88.dp
        ListStyle.LIST -> 64.dp
    }
    Row(
        modifier
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
    accentFallback: Color = LocalNovaPalette.current.accent,
) {
    val colors = LocalNovaPalette.current
    Column(
        modifier
            .width(140.dp)
            .scaleOnFocus(1.05f, extraY = 6.dp)
            .glowBorderOnFocus(radius = 12.dp)
            .dpadClickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.radialGradient(
                        listOf(accentFallback.copy(0.55f), colors.surface),
                    ),
                ),
        ) {
            if (posterUrl.isNotBlank()) {
                AsyncImage(
                    model = posterUrl,
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.55f)),
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
                    FocusButton("Cancel", onDismiss)
                }
            }
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
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.5f)), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(420.dp)) {
            Column(Modifier.padding(28.dp)) {
                Text(stringResource(R.string.exit_title), color = colors.onBackground, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.exit_body), color = colors.muted, fontSize = 14.sp)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FocusButton(stringResource(R.string.exit_stay), onStay)
                    FocusButton(stringResource(R.string.exit_confirm), onExit)
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
