package com.nova.iptv.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nova.iptv.ui.theme.LocalNovaPalette
import com.nova.iptv.ui.theme.NovaFontFamily

@Composable
fun SplashScreen() {
    val colors = LocalNovaPalette.current
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = true }
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(500), label = "splash")
    Box(
        Modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.alpha(alpha), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(18.dp, 48.dp).clip(RoundedCornerShape(2.dp)).background(colors.accent))
            Spacer(Modifier.height(16.dp))
            Text(
                "NOVA",
                color = colors.onBackground,
                fontFamily = NovaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 42.sp,
                letterSpacing = 10.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text("A player. Not a provider.", color = colors.muted, fontSize = 13.sp)
        }
    }
}
