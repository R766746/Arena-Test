package com.nova.iptv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nova.iptv.core.util.hashPin
import com.nova.iptv.domain.model.AnimSpeed
import com.nova.iptv.domain.model.AppSettings
import com.nova.iptv.domain.model.AspectMode
import com.nova.iptv.domain.model.BufferSize
import com.nova.iptv.domain.model.EpgRowHeight
import com.nova.iptv.domain.model.HomeCategory
import com.nova.iptv.domain.model.ListStyle
import com.nova.iptv.domain.model.StartupMode
import com.nova.iptv.domain.model.ThemeName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "nova_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.settingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<AppSettings> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings.value)
        store.edit { p ->
            p[Keys.theme] = next.theme.name
            p[Keys.accent] = next.accentArgb
            p[Keys.fontScale] = next.fontScale
            p[Keys.listStyle] = next.listStyle.name
            p[Keys.showNumbers] = next.showNumbers
            p[Keys.clock24h] = next.clock24h
            p[Keys.animSpeed] = next.animSpeed.name
            p[Keys.panelOpacity] = next.panelOpacity
            p[Keys.logoSize] = next.logoSize
            p[Keys.epgHours] = next.epgHours
            p[Keys.epgRowHeight] = next.epgRowHeight.name
            p[Keys.epgGridLines] = next.epgGridLines
            p[Keys.epgPastDays] = next.epgPastDays
            p[Keys.epgTimeShift] = next.epgTimeShiftHours
            p[Keys.updateEpgOnStart] = next.updateEpgOnStart
            p[Keys.updateEpgOnPlaylist] = next.updateEpgOnPlaylistChange
            p[Keys.preferEpgLogos] = next.preferEpgLogos
            p[Keys.preview] = next.preview
            p[Keys.autoplayPreview] = next.autoplayPreview
            p[Keys.overlayTimeout] = next.overlayTimeoutSec
            p[Keys.bufferSize] = next.bufferSize.name
            p[Keys.afr] = next.afr
            p[Keys.aspect] = next.aspect.name
            p[Keys.seekStep] = next.seekStepSec
            p[Keys.subtitleSize] = next.subtitleSize
            p[Keys.externalPlayer] = next.externalPlayerPackage
            p[Keys.startup] = next.startupMode.name
            p[Keys.lastChannel] = next.lastChannelId
            p[Keys.lastPlaylist] = next.lastPlaylistId
            p[Keys.lastCategory] = next.lastCategory.name
            p[Keys.lastGroup] = next.lastGroup
            p[Keys.parental] = next.parentalEnabled
            p[Keys.pinHash] = next.parentalPinHash
            p[Keys.pinSalt] = next.parentalPinSalt
            p[Keys.lockedGroups] = next.lockedGroups.joinToString("\n")
            p[Keys.lockSettings] = next.lockSettings
            p[Keys.recPadding] = next.recPaddingMin
            p[Keys.recTree] = next.recTreeUri
            p[Keys.recDeleteWatched] = next.recDeleteWatched
            p[Keys.updateOnStart] = next.updateOnStart
            p[Keys.firstRun] = next.firstRunDone
            p[Keys.tmdb] = next.tmdbKey
            p[Keys.strip] = next.nameStripTokens
            p[Keys.debugEpg] = next.debugEpgTimes
        }
    }

    suspend fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString()
        update { it.copy(parentalPinHash = hashPin(pin, salt), parentalPinSalt = salt, parentalEnabled = true) }
    }

    fun verifyPin(pin: String): Boolean {
        val s = settings.value
        if (s.parentalPinHash.isEmpty()) return true
        return hashPin(pin, s.parentalPinSalt) == s.parentalPinHash
    }

    suspend fun reset() {
        store.edit { it.clear() }
    }

    private fun Preferences.toSettings(): AppSettings {
        fun <T> Preferences.getOr(key: Preferences.Key<T>, fallback: T): T = this[key] ?: fallback
        return AppSettings(
            theme = enumValueOfOr(this[Keys.theme], ThemeName.MIDNIGHT),
            accentArgb = getOr(Keys.accent, 0xFF3D8BFD),
            fontScale = getOr(Keys.fontScale, 1f),
            listStyle = enumValueOfOr(this[Keys.listStyle], ListStyle.LIST),
            showNumbers = getOr(Keys.showNumbers, true),
            clock24h = getOr(Keys.clock24h, true),
            animSpeed = enumValueOfOr(this[Keys.animSpeed], AnimSpeed.NORMAL),
            panelOpacity = getOr(Keys.panelOpacity, 0.78f),
            logoSize = getOr(Keys.logoSize, 48),
            epgHours = getOr(Keys.epgHours, 4),
            epgRowHeight = enumValueOfOr(this[Keys.epgRowHeight], EpgRowHeight.NORMAL),
            epgGridLines = getOr(Keys.epgGridLines, true),
            epgPastDays = getOr(Keys.epgPastDays, 2),
            epgTimeShiftHours = getOr(Keys.epgTimeShift, 0),
            updateEpgOnStart = getOr(Keys.updateEpgOnStart, false),
            updateEpgOnPlaylistChange = getOr(Keys.updateEpgOnPlaylist, true),
            preferEpgLogos = getOr(Keys.preferEpgLogos, false),
            preview = getOr(Keys.preview, true),
            autoplayPreview = getOr(Keys.autoplayPreview, false),
            overlayTimeoutSec = getOr(Keys.overlayTimeout, 5),
            bufferSize = enumValueOfOr(this[Keys.bufferSize], BufferSize.MEDIUM),
            afr = getOr(Keys.afr, false),
            aspect = enumValueOfOr(this[Keys.aspect], AspectMode.FIT),
            seekStepSec = getOr(Keys.seekStep, 10),
            subtitleSize = getOr(Keys.subtitleSize, 18),
            externalPlayerPackage = getOr(Keys.externalPlayer, ""),
            startupMode = enumValueOfOr(this[Keys.startup], StartupMode.HOME),
            lastChannelId = getOr(Keys.lastChannel, ""),
            lastPlaylistId = getOr(Keys.lastPlaylist, "demo"),
            lastCategory = enumValueOfOr(this[Keys.lastCategory], HomeCategory.LIVE),
            lastGroup = getOr(Keys.lastGroup, ""),
            parentalEnabled = getOr(Keys.parental, false),
            parentalPinHash = getOr(Keys.pinHash, ""),
            parentalPinSalt = getOr(Keys.pinSalt, ""),
            lockedGroups = getOr(Keys.lockedGroups, "").split('\n').filter { it.isNotBlank() }.toSet(),
            lockSettings = getOr(Keys.lockSettings, false),
            recPaddingMin = getOr(Keys.recPadding, 3),
            recTreeUri = getOr(Keys.recTree, ""),
            recDeleteWatched = getOr(Keys.recDeleteWatched, false),
            updateOnStart = getOr(Keys.updateOnStart, false),
            firstRunDone = getOr(Keys.firstRun, false),
            tmdbKey = getOr(Keys.tmdb, ""),
            nameStripTokens = getOr(Keys.strip, "HD,FHD,4K,UHD,HEVC,50FPS,60FPS,H265,H264"),
            debugEpgTimes = getOr(Keys.debugEpg, false),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOfOr(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = longPreferencesKey("accent")
        val fontScale = floatPreferencesKey("fontScale")
        val listStyle = stringPreferencesKey("listStyle")
        val showNumbers = booleanPreferencesKey("showNumbers")
        val clock24h = booleanPreferencesKey("clock24h")
        val animSpeed = stringPreferencesKey("animSpeed")
        val panelOpacity = floatPreferencesKey("panelOpacity")
        val logoSize = intPreferencesKey("logoSize")
        val epgHours = intPreferencesKey("epgHours")
        val epgRowHeight = stringPreferencesKey("epgRowHeight")
        val epgGridLines = booleanPreferencesKey("epgGridLines")
        val epgPastDays = intPreferencesKey("epgPastDays")
        val epgTimeShift = intPreferencesKey("epgTimeShift")
        val updateEpgOnStart = booleanPreferencesKey("updateEpgOnStart")
        val updateEpgOnPlaylist = booleanPreferencesKey("updateEpgOnPlaylist")
        val preferEpgLogos = booleanPreferencesKey("preferEpgLogos")
        val preview = booleanPreferencesKey("preview")
        val autoplayPreview = booleanPreferencesKey("autoplayPreview")
        val overlayTimeout = intPreferencesKey("overlayTimeout")
        val bufferSize = stringPreferencesKey("bufferSize")
        val afr = booleanPreferencesKey("afr")
        val aspect = stringPreferencesKey("aspect")
        val seekStep = intPreferencesKey("seekStep")
        val subtitleSize = intPreferencesKey("subtitleSize")
        val externalPlayer = stringPreferencesKey("externalPlayer")
        val startup = stringPreferencesKey("startup")
        val lastChannel = stringPreferencesKey("lastChannel")
        val lastPlaylist = stringPreferencesKey("lastPlaylist")
        val lastCategory = stringPreferencesKey("lastCategory")
        val lastGroup = stringPreferencesKey("lastGroup")
        val parental = booleanPreferencesKey("parental")
        val pinHash = stringPreferencesKey("pinHash")
        val pinSalt = stringPreferencesKey("pinSalt")
        val lockedGroups = stringPreferencesKey("lockedGroups")
        val lockSettings = booleanPreferencesKey("lockSettings")
        val recPadding = intPreferencesKey("recPadding")
        val recTree = stringPreferencesKey("recTree")
        val recDeleteWatched = booleanPreferencesKey("recDeleteWatched")
        val updateOnStart = booleanPreferencesKey("updateOnStart")
        val firstRun = booleanPreferencesKey("firstRun")
        val tmdb = stringPreferencesKey("tmdb")
        val strip = stringPreferencesKey("strip")
        val debugEpg = booleanPreferencesKey("debugEpg")
    }
}
