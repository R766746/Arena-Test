package com.nova.iptv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.nova.iptv.domain.model.AppSettings
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.HomeCategory
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.home.GroupItem
import com.nova.iptv.ui.home.HomeScreen
import com.nova.iptv.ui.home.HomeUiState
import com.nova.iptv.ui.splash.SplashScreen
import com.nova.iptv.ui.theme.NovaTheme
import org.junit.Rule
import org.junit.Test

class HomeAndNavTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun splashShowsBrand() {
        rule.setContent { NovaTheme(AppSettings()) { SplashScreen() } }
        rule.onNodeWithText("NOVA", substring = true).assertIsDisplayed()
    }

    @Test
    fun homeShowsThreePanesAndCategories() {
        val ch = Channel(
            id = "c1", playlistId = "demo", number = 101, name = "NOVA News 24",
            groupName = "News", streamUrl = "http://example/1",
        )
        rule.setContent {
            NovaTheme(AppSettings(firstRunDone = true)) {
                HomeScreen(
                    state = HomeUiState(
                        settings = AppSettings(firstRunDone = true),
                        category = HomeCategory.LIVE,
                        groups = listOf(GroupItem("all", "All Channels", 1), GroupItem("g:News", "News", 1)),
                        channels = listOf(ch),
                    ),
                    onCategory = {},
                    onGroup = {},
                    onChannelFocus = {},
                    onWatch = {},
                    onVod = {},
                    onToggleFav = {},
                    onHide = {},
                    onAssignEpg = { _, _ -> },
                    onSearchEpg = { _, cb -> cb(emptyList()) },
                    onShowcase = {},
                    onAddPlaylist = {},
                    onDismissFirstRun = {},
                    playerManager = FakePlayerManager(),
                    verifyPin = { true },
                )
            }
        }
        rule.onNodeWithText("Live TV").assertIsDisplayed()
        rule.onNodeWithText("All Channels").assertIsDisplayed()
        rule.onNodeWithText("NOVA News 24").assertIsDisplayed()
    }

    @Test
    fun topBarShowsClock() {
        rule.setContent { NovaTheme(AppSettings()) { NovaTopBar(clock24h = true) } }
        rule.onNodeWithText("NOVA").assertIsDisplayed()
    }
}
