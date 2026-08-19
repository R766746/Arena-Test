package com.nova.iptv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import com.nova.iptv.R
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.nova.iptv.core.perf.LowRam
import com.nova.iptv.domain.model.AnimSpeed
import com.nova.iptv.domain.model.AppSettings
import com.nova.iptv.domain.model.ThemeName

val NovaBg = Color(0xFF07080D)
val NovaAccent = Color(0xFF3D8BFD)
val NovaAccentDim = Color(0x333D8BFD)
val NovaOnBg = Color(0xFFF2F4F8)
val NovaMuted = Color(0xFF8B93A7)
val NovaSurface = Color(0xFF0E1018)
val NovaSurface2 = Color(0xFF161A26)
val NovaDanger = Color(0xFFFF5C7A)
val NovaLive = Color(0xFFFF3B4E)
val NovaGlass = Color(0xC7141824)

@Immutable
data class NovaPalette(
    val background: Color,
    val surface: Color,
    val surface2: Color,
    val accent: Color,
    val accentDim: Color,
    val onBackground: Color,
    val muted: Color,
    val glass: Color,
    val live: Color,
    val danger: Color,
    val panelOpacity: Float,
    val useBlur: Boolean,
) {
    val isLight: Boolean get() = background.luminance() > 0.4f
}

val LocalNovaPalette = staticCompositionLocalOf {
    NovaPalette(
        background = NovaBg,
        surface = NovaSurface,
        surface2 = NovaSurface2,
        accent = NovaAccent,
        accentDim = NovaAccentDim,
        onBackground = NovaOnBg,
        muted = NovaMuted,
        glass = NovaGlass,
        live = NovaLive,
        danger = NovaDanger,
        panelOpacity = 0.78f,
        useBlur = true,
    )
}

val LocalAnimMs = staticCompositionLocalOf { 220 }
val LocalFontScale = staticCompositionLocalOf { 1f }

val NovaEmphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

fun novaTween(duration: Int = 220) = tween<Float>(
    durationMillis = duration,
    easing = NovaEmphasized,
)

fun novaIntTween(duration: Int = 220) = tween<Int>(
    durationMillis = duration,
    easing = FastOutSlowInEasing,
)

/**
 * Geometric sans close to Outfit. A bundled Outfit.ttf can be dropped into
 * res/font/outfit.ttf; until then we use the device geometric sans.
 */
val NovaFontFamily: FontFamily = FontFamily(
    Font(R.font.outfit_variable, FontWeight.Normal),
    Font(R.font.outfit_variable, FontWeight.Medium),
)

fun novaTypography(scale: Float) = androidx.tv.material3.Typography(
    displayLarge = TextStyle(
        fontFamily = NovaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (28 * scale).sp,
        letterSpacing = (-0.3).sp,
        color = NovaOnBg,
    ),
    headlineMedium = TextStyle(
        fontFamily = NovaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = (24 * scale).sp,
        letterSpacing = (-0.2).sp,
        color = NovaOnBg,
    ),
    titleLarge = TextStyle(
        fontFamily = NovaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (22 * scale).sp,
        color = NovaOnBg,
    ),
    titleMedium = TextStyle(
        fontFamily = NovaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (16 * scale).sp,
        color = NovaOnBg,
    ),
    bodyLarge = TextStyle(
        fontFamily = NovaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (14 * scale).sp,
        lineHeight = (20 * scale).sp,
        color = NovaOnBg,
    ),
    bodyMedium = TextStyle(
        fontFamily = NovaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = (13 * scale).sp,
        color = NovaOnBg,
    ),
    labelSmall = TextStyle(
        fontFamily = NovaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (11 * scale).sp,
        letterSpacing = 1.4.sp,
        color = NovaMuted,
    ),
    labelMedium = TextStyle(
        fontFamily = NovaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = (12 * scale).sp,
        letterSpacing = 0.8.sp,
        color = NovaMuted,
    ),
)

fun paletteFor(settings: AppSettings, lowRam: Boolean): NovaPalette {
    val accent = Color(settings.accentArgb)
    val (bg, surface, surface2, on, muted) = when (settings.theme) {
        ThemeName.MIDNIGHT -> listOf(NovaBg, NovaSurface, NovaSurface2, NovaOnBg, NovaMuted)
        ThemeName.DARK -> listOf(
            Color(0xFF121212), Color(0xFF1C1C1E), Color(0xFF26262A), NovaOnBg, Color(0xFF9AA0AE),
        )
        ThemeName.CINEMA -> listOf(
            Color(0xFF000000), Color(0xFF0A0A0A), Color(0xFF161616), Color(0xFFF5F5F5), Color(0xFF8A8A8A),
        )
        ThemeName.LIGHT -> listOf(
            Color(0xFFF4F6FA), Color(0xFFFFFFFF), Color(0xFFE8ECF4), Color(0xFF12141A), Color(0xFF5C6474),
        )
    }
    val glass = if (settings.theme == ThemeName.LIGHT) {
        Color.White.copy(alpha = settings.panelOpacity)
    } else {
        Color(0xFF141824).copy(alpha = settings.panelOpacity)
    }
    return NovaPalette(
        background = bg,
        surface = surface,
        surface2 = surface2,
        accent = accent,
        accentDim = accent.copy(alpha = 0.22f),
        onBackground = on,
        muted = muted,
        glass = glass,
        live = NovaLive,
        danger = NovaDanger,
        panelOpacity = settings.panelOpacity,
        useBlur = !lowRam,
    )
}

@Composable
fun NovaTheme(
    settings: AppSettings,
    lowRam: Boolean = LowRam.isLowRam,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(settings, lowRam)
    val anim = when (settings.animSpeed) {
        AnimSpeed.OFF -> 0
        AnimSpeed.NORMAL -> 220
        AnimSpeed.FAST -> 140
    }
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onBackground,
            surfaceVariant = palette.surface2,
            border = palette.accent.copy(alpha = 0.4f),
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent,
            background = palette.background,
            onBackground = palette.onBackground,
            surface = palette.surface,
            onSurface = palette.onBackground,
            surfaceVariant = palette.surface2,
            border = palette.accent.copy(alpha = 0.45f),
        )
    }
    CompositionLocalProvider(
        LocalNovaPalette provides palette,
        LocalAnimMs provides anim,
        LocalFontScale provides settings.fontScale,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = novaTypography(settings.fontScale),
            content = content,
        )
    }
}

val PaneCategories = 210.dp
val PaneGroups = 220.dp
val PanePreview = 300.dp
val FocusRing = 2.dp
val FocusGlow = 8.dp
val PosterRadius = 12.dp
val ScreenPad = PaddingValues(horizontal = 28.dp, vertical = 18.dp)

@Composable
fun novaColors(): NovaPalette = LocalNovaPalette.current

fun rowHeight(settings: AppSettings): Dp = when (settings.epgRowHeight) {
    com.nova.iptv.domain.model.EpgRowHeight.COMPACT -> 56.dp
    com.nova.iptv.domain.model.EpgRowHeight.NORMAL -> 72.dp
    com.nova.iptv.domain.model.EpgRowHeight.TALL -> 92.dp
}
