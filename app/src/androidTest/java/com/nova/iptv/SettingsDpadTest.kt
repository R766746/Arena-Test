package com.nova.iptv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.nova.iptv.domain.model.AppSettings
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.ui.settings.SettingsScreen
import com.nova.iptv.ui.theme.NovaTheme
import org.junit.Rule
import org.junit.Test

class SettingsDpadTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun everySectionLabelIsReachable() {
        rule.setContent {
            NovaTheme(AppSettings(firstRunDone = true)) {
                SettingsScreen(
                    settings = AppSettings(firstRunDone = true),
                    playlists = listOf(Playlist(Playlist.DEMO_ID, "Showcase", PlaylistType.DEMO)),
                    groups = emptyList(),
                    onUpdate = {},
                    onAdd = {},
                    onRefresh = {},
                    onDelete = {},
                    onPin = {},
                    verifyPin = { true },
                    onExport = { _, _ -> },
                    onRestore = {},
                    onEpgNow = {},
                    onClearCaches = {},
                    onDiagnostics = {},
                    onReset = {},
                )
            }
        }
        listOf(
            "Playlists", "EPG", "Appearance", "TV Guide", "Player",
            "Parental", "Recordings", "General", "About",
        ).forEach { label ->
            rule.onNodeWithText(label).assertIsDisplayed()
        }
    }
}
