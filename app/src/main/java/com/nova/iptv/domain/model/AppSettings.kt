package com.nova.iptv.domain.model

import androidx.compose.ui.graphics.Color

data class AppSettings(
    val theme: ThemeName = ThemeName.MIDNIGHT,
    val accentArgb: Long = 0xFF3D8BFD,
    val fontScale: Float = 1.0f,
    val listStyle: ListStyle = ListStyle.LIST,
    val showNumbers: Boolean = true,
    val clock24h: Boolean = true,
    val animSpeed: AnimSpeed = AnimSpeed.NORMAL,
    val panelOpacity: Float = 0.78f,
    val logoSize: Int = 48,
    val epgHours: Int = 4,
    val epgRowHeight: EpgRowHeight = EpgRowHeight.NORMAL,
    val epgGridLines: Boolean = true,
    val epgPastDays: Int = 2,
    val epgTimeShiftHours: Int = 0,
    val updateEpgOnStart: Boolean = false,
    val updateEpgOnPlaylistChange: Boolean = true,
    val preferEpgLogos: Boolean = false,
    val preview: Boolean = true,
    val autoplayPreview: Boolean = false,
    val overlayTimeoutSec: Int = 5,
    val bufferSize: BufferSize = BufferSize.MEDIUM,
    val afr: Boolean = false,
    val aspect: AspectMode = AspectMode.FIT,
    val seekStepSec: Int = 10,
    val subtitleSize: Int = 18,
    val externalPlayerPackage: String = "",
    val startupMode: StartupMode = StartupMode.HOME,
    val lastChannelId: String = "",
    val lastPlaylistId: String = Playlist.DEMO_ID,
    val lastCategory: HomeCategory = HomeCategory.LIVE,
    val lastGroup: String = "",
    val parentalEnabled: Boolean = false,
    val parentalPinHash: String = "",
    val parentalPinSalt: String = "",
    val lockedGroups: Set<String> = emptySet(),
    val lockSettings: Boolean = false,
    val recPaddingMin: Int = 3,
    val recTreeUri: String = "",
    val recDeleteWatched: Boolean = false,
    val updateOnStart: Boolean = false,
    val firstRunDone: Boolean = false,
    val tmdbKey: String = "",
    val nameStripTokens: String = "HD,FHD,4K,UHD,HEVC,50FPS,60FPS,H265,H264",
    val debugEpgTimes: Boolean = false,
) {
    val accent: Color get() = Color(accentArgb)
    val previewAllowed: Boolean get() = preview
}

val AccentSwatches: List<Long> = listOf(
    0xFF3D8BFD,
    0xFF6C5CE7,
    0xFFFF6B6B,
    0xFF00D2A0,
    0xFFFFB020,
    0xFFFF4D8D,
    0xFF29B6F6,
    0xFFE0E6F0,
)
