package com.nova.iptv.nav

import android.app.Activity
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.player.PlayerManager
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.StartupMode
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.ui.components.ExitConfirm
import com.nova.iptv.ui.guide.GuideRoute
import com.nova.iptv.ui.home.HomeRoute
import com.nova.iptv.ui.multiview.MultiViewRoute
import com.nova.iptv.ui.player.PlayerRoute
import com.nova.iptv.ui.playlist.AddPlaylistRoute
import com.nova.iptv.ui.recordings.RecordingsRoute
import com.nova.iptv.ui.search.SearchRoute
import com.nova.iptv.ui.settings.DiagnosticsRoute
import com.nova.iptv.ui.settings.SettingsRoute
import com.nova.iptv.ui.splash.SplashScreen
import com.nova.iptv.ui.theme.NovaTheme
import com.nova.iptv.ui.theme.novaColors
import com.nova.iptv.ui.vod.DetailRoute
import com.nova.iptv.ui.vod.MoviesRoute
import com.nova.iptv.ui.vod.SeriesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import androidx.lifecycle.ViewModel

@HiltViewModel
class RootViewModel @Inject constructor(
    val settings: SettingsRepository,
    val playerManager: PlayerManager,
    val playlists: PlaylistRepository,
) : ViewModel() {
    val startupPlaylists: StateFlow<List<Playlist>?> = playlists.playlists()
        .map<List<Playlist>, List<Playlist>?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun NovaNav(vm: RootViewModel = hiltViewModel()) {
    val settings by vm.settings.settings.collectAsStateWithLifecycle()
    val loadedSettings by vm.settings.loadedSettings.collectAsStateWithLifecycle()
    val playlists by vm.startupPlaylists.collectAsStateWithLifecycle()
    NovaTheme(settings) {
        val nav = rememberNavController()
        val ctx = LocalContext.current
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        var exit by remember { mutableStateOf(false) }

        fun go(dest: String) {
            if (route != dest) nav.navigate(dest) { launchSingleTop = true }
        }

        BackHandler(enabled = true) {
            when {
                route?.startsWith("player") == true -> nav.popBackStack()
                route == Routes.Home || route == Routes.HomeFavorites || route == Routes.Splash -> exit = true
                nav.previousBackStackEntry != null -> nav.popBackStack()
                else -> exit = true
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(novaColors().background)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    val code = event.nativeKeyEvent.keyCode
                    val playing = route?.startsWith("player") == true
                    when (code) {
                        KeyEvent.KEYCODE_GUIDE -> {
                            go(Routes.Guide); true
                        }
                        KeyEvent.KEYCODE_G -> {
                            go(Routes.Guide); true
                        }
                        KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_S -> {
                            go(Routes.Search); true
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            if (playing) false else {
                                go(Routes.Settings); true
                            }
                        }
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> playing
                        else -> false
                    }
                },
        ) {
            NavHost(
                navController = nav,
                startDestination = Routes.Splash,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
            ) {
                composable(Routes.Splash) {
                    SplashScreen()
                    LaunchedEffect(loadedSettings, playlists) {
                        val readySettings = loadedSettings ?: return@LaunchedEffect
                        val readyPlaylists = playlists ?: return@LaunchedEffect
                        withFrameNanos { }
                        if (readyPlaylists.isEmpty()) {
                            nav.navigate(Routes.AddPlaylist) {
                                popUpTo(Routes.Splash) { inclusive = true }
                            }
                            return@LaunchedEffect
                        }
                        val dest = when (readySettings.startupMode) {
                            StartupMode.HOME -> Routes.Home
                            StartupMode.FAVORITES -> Routes.HomeFavorites
                            StartupMode.GUIDE -> Routes.Guide
                            StartupMode.LAST_CHANNEL -> {
                                if (readySettings.lastChannelId.isNotBlank()) {
                                    Routes.player(PlayTarget.live(readySettings.lastChannelId))
                                } else Routes.Home
                            }
                        }
                        nav.navigate(dest) { popUpTo(Routes.Splash) { inclusive = true } }
                    }
                }
                composable(Routes.Home) {
                    HomeRoute(
                        onNavigate = { go(it) },
                        onPlay = { target -> nav.navigate(Routes.player(target)) },
                        onDetail = { kind, id -> nav.navigate(Routes.detail(kind, id)) },
                    )
                }
                composable(Routes.HomeFavorites) {
                    HomeRoute(
                        onNavigate = { go(it) },
                        onPlay = { target -> nav.navigate(Routes.player(target)) },
                        onDetail = { kind, id -> nav.navigate(Routes.detail(kind, id)) },
                        openFavoritesOnStart = true,
                    )
                }
                composable(Routes.Guide) {
                    GuideRoute(
                        onPlay = { nav.navigate(Routes.player(it)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    Routes.Player,
                    arguments = listOf(navArgument("targetJson") { type = NavType.StringType }),
                ) { be ->
                    val raw = be.arguments?.getString("targetJson").orEmpty()
                    PlayerRoute(
                        target = PlayTarget.parse(raw),
                        onBack = { nav.popBackStack() },
                        onGuide = { go(Routes.Guide) },
                        onMulti = { go(Routes.MultiView) },
                        onZapTarget = { nav.navigate(Routes.player(it)) { popUpTo(Routes.Player) { inclusive = true } } },
                    )
                }
                composable(Routes.Movies) {
                    MoviesRoute(
                        onDetail = { nav.navigate(Routes.detail("movie", it)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.Series) {
                    SeriesRoute(
                        onDetail = { nav.navigate(Routes.detail("series", it)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(
                    Routes.Detail,
                    arguments = listOf(
                        navArgument("kind") { type = NavType.StringType },
                        navArgument("id") { type = NavType.StringType },
                    ),
                ) { be ->
                    DetailRoute(
                        kind = be.arguments?.getString("kind").orEmpty(),
                        id = be.arguments?.getString("id").orEmpty(),
                        onPlay = { nav.navigate(Routes.player(it)) },
                        onOpen = { k, i -> nav.navigate(Routes.detail(k, i)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.Recordings) {
                    RecordingsRoute(
                        onPlay = { nav.navigate(Routes.player(it)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.Search) {
                    SearchRoute(
                        onPlay = { nav.navigate(Routes.player(it)) },
                        onDetail = { k, i -> nav.navigate(Routes.detail(k, i)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.Settings) {
                    SettingsRoute(
                        onAddPlaylist = { go(Routes.AddPlaylist) },
                        onDiagnostics = { go(Routes.Diagnostics) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.AddPlaylist) {
                    AddPlaylistRoute(
                        onDone = {
                            nav.navigate(Routes.Home) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onBack = {
                            if (!nav.popBackStack()) {
                                nav.navigate(Routes.Home) { popUpTo(0) { inclusive = true } }
                            }
                        },
                    )
                }
                composable(Routes.MultiView) {
                    MultiViewRoute(
                        onFullscreen = { nav.navigate(Routes.player(it)) },
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.Diagnostics) {
                    DiagnosticsRoute(onBack = { nav.popBackStack() })
                }
            }
            if (exit) {
                ExitConfirm(
                    onExit = { (ctx as? Activity)?.finish() },
                    onStay = { exit = false },
                )
            }
        }
    }
}
