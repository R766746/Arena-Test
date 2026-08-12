package com.nova.iptv.nav

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nova.iptv.ui.theme.FocusGlow
import com.nova.iptv.ui.theme.FocusRing
import com.nova.iptv.ui.theme.LocalAnimMs
import com.nova.iptv.ui.theme.LocalNovaPalette
import com.nova.iptv.ui.theme.novaTween

/**
 * TV focus helpers. Official TvLazy* types from androidx.tv.foundation were
 * folded into Compose Foundation; we keep the names the product spec asked for.
 */
@Composable
fun rememberFocusRestorer(): FocusRequester = remember { FocusRequester() }

fun Modifier.tvFocusable(
    requester: FocusRequester? = null,
): Modifier = composed {
    var m = this.focusable()
    if (requester != null) m = m.focusRequester(requester)
    m
}

fun Modifier.scaleOnFocus(
    focusedScale: Float = 1.04f,
    extraY: Dp = 0.dp,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val anim = LocalAnimMs.current
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = novaTween(anim),
        label = "focusScale",
    )
    val ty by animateFloatAsState(
        targetValue = if (focused) -extraY.value else 0f,
        animationSpec = novaTween(anim),
        label = "focusY",
    )
    onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = ty * density
        }
}

fun Modifier.glowBorderOnFocus(
    accent: Color? = null,
    radius: Dp = 8.dp,
    width: Dp = FocusRing,
    glow: Dp = FocusGlow,
): Modifier = composed {
    val palette = LocalNovaPalette.current
    val color = accent ?: palette.accent
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier
                    .shadow(glow, RoundedCornerShape(radius), ambientColor = color.copy(0.55f), spotColor = color.copy(0.7f))
                    .border(width, color, RoundedCornerShape(radius))
            } else {
                Modifier
            },
        )
}

fun Modifier.drawFocusRing(
    radius: Dp = 8.dp,
): Modifier = composed {
    val palette = LocalNovaPalette.current
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val stroke = width.toPx().let { FocusRing.toPx() }
                drawRoundRect(
                    color = palette.accent.copy(alpha = 0.28f),
                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
                    style = Stroke(width = FocusGlow.toPx()),
                )
                drawRoundRect(
                    color = palette.accent,
                    cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
                    style = Stroke(width = stroke),
                )
            }
        }
}

/**
 * D-pad OK / center / enter click + optional long-press (MENU or long CENTER).
 * Never uses the mobile ripple indication.
 */
fun Modifier.dpadClickable(
    enabled: Boolean = true,
    longPressMs: Long = 450L,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    var downAt by remember { mutableLongStateOf(0L) }
    var longFired by remember { mutableStateOf(false) }
    clickable(
        enabled = enabled,
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
        .focusable(enabled, interaction)
        .onKeyEvent { event ->
            val code = event.nativeKeyCode
            val isOk = code == KeyEvent.KEYCODE_DPAD_CENTER ||
                code == KeyEvent.KEYCODE_ENTER ||
                code == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                code == KeyEvent.KEYCODE_BUTTON_A
            val isMenu = code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_INFO
            when {
                isMenu && event.type == KeyEventType.KeyUp -> {
                    onLongPress?.invoke()
                    onLongPress != null
                }
                isOk && event.type == KeyEventType.KeyDown -> {
                    if (downAt == 0L) {
                        downAt = SystemClock.uptimeMillis()
                        longFired = false
                    } else if (onLongPress != null && !longFired &&
                        SystemClock.uptimeMillis() - downAt >= longPressMs
                    ) {
                        longFired = true
                        onLongPress()
                    }
                    true
                }
                isOk && event.type == KeyEventType.KeyUp -> {
                    val held = SystemClock.uptimeMillis() - downAt
                    downAt = 0L
                    if (!longFired) {
                        if (onLongPress != null && held >= longPressMs) onLongPress()
                        else onClick()
                    }
                    true
                }
                else -> false
            }
        }
        .pointerInput(onClick, onLongPress) {
            detectTapGestures(
                onLongPress = { onLongPress?.invoke() },
                onTap = { onClick() },
            )
        }
}

fun Modifier.restoreFocus(requester: FocusRequester): Modifier =
    focusRequester(requester).focusRestorer()

@Composable
fun TvLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit,
) {
    val restorer = rememberFocusRestorer()
    LazyColumn(
        modifier = modifier
            .focusRequester(restorer)
            .focusRestorer(),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
fun TvLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: LazyListScope.() -> Unit,
) {
    val restorer = rememberFocusRestorer()
    LazyRow(
        modifier = modifier
            .focusRequester(restorer)
            .focusRestorer(),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

@Composable
fun TvLazyVerticalGrid(
    columns: GridCells,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: LazyGridScope.() -> Unit,
) {
    val restorer = rememberFocusRestorer()
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier
            .focusRequester(restorer)
            .focusRestorer(),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}

fun Modifier.leftTo(target: FocusRequester): Modifier =
    focusProperties { left = target }

fun Modifier.rightTo(target: FocusRequester): Modifier =
    focusProperties { right = target }

fun isDpadLeft(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.nativeKeyCode == KeyEvent.KEYCODE_DPAD_LEFT

fun isDpadRight(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.nativeKeyCode == KeyEvent.KEYCODE_DPAD_RIGHT

fun isChannelUp(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.nativeKeyCode == KeyEvent.KEYCODE_CHANNEL_UP

fun isChannelDown(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.nativeKeyCode == KeyEvent.KEYCODE_CHANNEL_DOWN

fun digitFromKey(code: Int): Int? = when (code) {
    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
    else -> null
}
