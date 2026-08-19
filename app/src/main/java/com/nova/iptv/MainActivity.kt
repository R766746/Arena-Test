package com.nova.iptv

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.nav.NovaNav
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var playerManager: PlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()
        lifecycleScope.launch {
            playerManager.preferredDisplayModeId.collect { modeId ->
                if (Build.VERSION.SDK_INT >= 23 && window.attributes.preferredDisplayModeId != modeId) {
                    window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
                }
            }
        }
        setContent { NovaApp() }
    }

    override fun onStop() {
        super.onStop()
        playerManager.onActivityStop(isChangingConfigurations)
        if (!isChangingConfigurations && !isInPictureInPictureMode) {
            playerManager.releasePreview()
            playerManager.releaseMultiView()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (playerManager.shouldEnterPip()) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerManager.setPip(isInPictureInPictureMode)
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@androidx.compose.runtime.Composable
fun NovaApp() {
    NovaNav()
}
