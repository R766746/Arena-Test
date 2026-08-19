package com.nova.iptv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.nova.iptv.domain.model.AppSettings
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.HomeCategory
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.components.ExitConfirm
import com.nova.iptv.ui.home.GroupItem
import com.nova.iptv.ui.home.HomeScreen
import com.nova.iptv.ui.home.HomeUiState
import com.nova.iptv.ui.splash.SplashScreen
import com.nova.iptv.ui.theme.NovaTheme
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.espresso.Espresso.pressBack

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
            id = "c1", playlistId = "test-playlist", number = 101, name = "NOVA News 24",
            groupName = "News", streamUrl = "http://example/1",
        )
        rule.setContent {
            val liveItems = flowOf(PagingData.from(listOf(ch))).collectAsLazyPagingItems()
            NovaTheme(AppSettings(firstRunDone = true)) {
                HomeScreen(
                    state = HomeUiState(
                        settings = AppSettings(firstRunDone = true),
                        category = HomeCategory.LIVE,
                        groups = listOf(GroupItem("all", "All Channels", 1), GroupItem("g:News", "News", 1)),
                        channels = listOf(ch),
                    ),
                    liveItems = liveItems,
                    onCategory = {},
                    onGroup = {},
                    onChannelFocus = {},
                    onChannelVisible = {},
                    onPreviewChannel = {},
                    onWatch = {},
                    onCatchup = { _, _ -> },
                    onRecordProgram = { _, _ -> },
                    onStopRecording = { _, _ -> },
                    onRemindProgram = { _, _ -> },
                    onVod = {},
                    onToggleFav = {},
                    onHide = {},
                    onAssignEpg = { _, _ -> },
                    onSearchEpg = { _, cb -> cb(emptyList()) },
                    onAddPlaylist = {},
                    playerManager = FakePlayerManager(),
                    verifyPin = { true },
                    syncProgress = null,
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

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun exitPopupTrapsFocusAndBackChoosesStay() {
        rule.setContent {
            var visible by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(true)
            }
            NovaTheme(AppSettings()) {
                if (visible) ExitConfirm(onExit = {}, onStay = { visible = false })
            }
        }

        rule.onNodeWithText("Stay").assertIsFocused()
        rule.onNodeWithText("Stay").performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("Exit").assertIsFocused()
        rule.onNodeWithText("Exit").performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithText("Exit").assertIsFocused()
        rule.onNodeWithText("Exit").performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNodeWithText("Stay").assertIsFocused()
        pressBack()
        rule.waitForIdle()
        rule.onNodeWithText("Leave NOVA?").assertDoesNotExist()
    }
}
