package com.nova.iptv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.AppSettings
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.HomeCategory
import com.nova.iptv.domain.model.NowNext
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.VodKind
import com.nova.iptv.domain.model.WatchHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GroupItem(val id: String, val label: String, val count: Int)

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val playlists: List<Playlist> = emptyList(),
    val playlistId: String = "",
    val category: HomeCategory = HomeCategory.LIVE,
    val groupId: String = "all",
    val groups: List<GroupItem> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val nowNext: Map<String, NowNext> = emptyMap(),
    val vod: List<VodItem> = emptyList(),
    val history: List<WatchHistory> = emptyList(),
    val focusedChannelId: String? = null,
    val nowMs: Long = System.currentTimeMillis(),
    val firstRun: Boolean = false,
    val isLoading: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val category = MutableStateFlow(HomeCategory.LIVE)
    private val groupId = MutableStateFlow("all")
    private val playlistId = MutableStateFlow("")
    private val focused = MutableStateFlow<String?>(null)
    private val nowMs = MutableStateFlow(System.currentTimeMillis())
    private val nowNext = MutableStateFlow<Map<String, NowNext>>(emptyMap())
    private val isLoading = MutableStateFlow(true)

    // Keep every main catalog hot while Home exists.
    private val cachedChannels = playlistId.flatMapLatest(playlists::channels)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val cachedFavorites = playlistId.flatMapLatest(playlists::favorites)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val cachedRecent = playlistId.flatMapLatest(playlists::recentChannels)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val cachedMovies = playlistId.flatMapLatest { playlists.vod(it, VodKind.MOVIE.name) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val cachedSeries = playlistId.flatMapLatest { playlists.vod(it, VodKind.SERIES.name) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val cachedHistory = playlists.history(30)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val liveGroups = combine(
        cachedChannels, cachedFavorites, cachedRecent
    ) { all, favs, recent ->
        withContext(Dispatchers.Default) {
            val counts = all.asSequence().filter { it.groupName.isNotBlank() }.groupingBy { it.groupName }.eachCount()
            buildList {
                add(GroupItem("favorites", "Favorites", favs.size))
                add(GroupItem("all", "All Channels", all.size))
                add(GroupItem("recent", "Recently Watched", recent.size))
                counts.toSortedMap().forEach { (group, count) ->
                    add(GroupItem("g:$group", group, count))
                }
            }
        }
    }

    private val vodGroups = combine(
        category, cachedMovies, cachedSeries, cachedHistory
    ) { cat, movies, series, hist ->
        if (cat != HomeCategory.MOVIES && cat != HomeCategory.SERIES) return@combine emptyList<GroupItem>()
        withContext(Dispatchers.Default) {
            val kind = if (cat == HomeCategory.MOVIES) VodKind.MOVIE.name else VodKind.SERIES.name
            val catalog = if (cat == HomeCategory.MOVIES) movies else series
            val counts = catalog.asSequence().flatMap { it.genres.asSequence() }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
            buildList {
                add(GroupItem("all", "All", catalog.size))
                add(GroupItem("continue", "Continue Watching", hist.count { it.kind.name == kind }))
                counts.toSortedMap().forEach { (genre, count) ->
                    add(GroupItem("genre:$genre", genre, count))
                }
            }
        }
    }

    private val groupsFlow: StateFlow<List<GroupItem>> = combine(category, liveGroups, vodGroups) { cat, live, vod ->
        when (cat) {
            HomeCategory.LIVE -> live
            HomeCategory.MOVIES, HomeCategory.SERIES -> vod
            else -> emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val contentFlow: Flow<Pair<List<Channel>, List<VodItem>>> = combine(
        category, groupId, cachedChannels, cachedFavorites, cachedRecent, cachedMovies, cachedSeries, cachedHistory
    ) { args ->
        val cat = args[0] as HomeCategory
        val gid = args[1] as String
        val all = args[2] as List<Channel>
        val favs = args[3] as List<Channel>
        val recent = args[4] as List<Channel>
        val movies = args[5] as List<VodItem>
        val series = args[6] as List<VodItem>
        val hist = args[7] as List<WatchHistory>

        withContext(Dispatchers.Default) {
            when (cat) {
                HomeCategory.LIVE -> {
                    val ch = when {
                        gid == "favorites" -> favs
                        gid == "recent" -> recent
                        gid.startsWith("g:") -> all.filter { it.groupName == gid.removePrefix("g:") }
                        else -> all
                    }
                    ch to emptyList<VodItem>()
                }
                HomeCategory.MOVIES, HomeCategory.SERIES -> {
                    val kind = if (cat == HomeCategory.MOVIES) VodKind.MOVIE.name else VodKind.SERIES.name
                    val catalog = if (cat == HomeCategory.MOVIES) movies else series
                    val filtered = when {
                        gid == "continue" -> {
                            val ids = hist.filter { it.kind.name == kind }.map { it.refId }.toSet()
                            catalog.filter { it.id in ids }
                        }
                        gid.startsWith("genre:") -> catalog.filter { it.genres.contains(gid.removePrefix("genre:")) }
                        else -> catalog
                    }
                    emptyList<Channel>() to filtered
                }
                else -> emptyList<Channel>() to emptyList<VodItem>()
            }
        }
    }.distinctUntilChanged()

    val state: StateFlow<HomeUiState> = combine(
        combine(settingsRepo.settings, playlists.playlists(), category, groupId, playlistId) { a, b, c, d, e -> listOf(a, b, c, d, e) },
        combine(nowMs, nowNext, cachedHistory, focused) { a, b, c, d -> listOf(a, b, c, d) },
        combine(groupsFlow, contentFlow, isLoading) { a, b, c -> listOf(a, b, c) }
    ) { s1, s2, s3 ->
        val settings = s1[0] as AppSettings
        val pls = s1[1] as List<Playlist>
        val cat = s1[2] as HomeCategory
        val gid = s1[3] as String
        val pid = s1[4] as String
        
        val now = s2[0] as Long
        val nn = s2[1] as Map<String, NowNext>
        val hist = s2[2] as List<WatchHistory>
        val foc = s2[3] as String?
        
        val groups = s3[0] as List<GroupItem>
        val content = s3[1] as Pair<List<Channel>, List<VodItem>>
        val loading = s3[2] as Boolean

        HomeUiState(
            settings = settings,
            playlists = pls,
            playlistId = pid,
            category = cat,
            groupId = gid,
            groups = groups,
            channels = content.first,
            vod = content.second,
            nowNext = nn,
            history = hist,
            focusedChannelId = foc,
            nowMs = now,
            firstRun = pls.isEmpty() && !loading,
            isLoading = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        // Restore state as quickly as possible
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            playlistId.value = s.lastPlaylistId
            if (s.lastCategory in listOf(HomeCategory.LIVE, HomeCategory.MOVIES, HomeCategory.SERIES)) {
                category.value = s.lastCategory
            }
            groupId.value = s.lastGroup.ifBlank { "all" }
            
            // Wait for initial cache to be ready
            if (playlistId.value.isEmpty()) {
                val firstPl = playlists.playlists().first().firstOrNull()
                if (firstPl != null) playlistId.value = firstPl.id
            }
            isLoading.value = false
            refreshNowNext()
        }

        viewModelScope.launch {
            while (true) {
                delay(30_000)
                nowMs.value = System.currentTimeMillis()
                refreshNowNext()
            }
        }
    }

    private suspend fun refreshNowNext() {
        if (playlistId.value.isBlank()) return
        val ch = playlists.snapshotChannels(playlistId.value).take(80)
        val now = System.currentTimeMillis()
        val map = LinkedHashMap<String, NowNext>()
        ch.forEach { map[it.id] = epg.nowAndNext(it.id, now) }
        nowNext.value = map
    }

    fun selectCategory(cat: HomeCategory) {
        if (category.value == cat) return
        category.value = cat
        groupId.value = "all"
        viewModelScope.launch {
            settingsRepo.update { it.copy(lastCategory = cat, lastGroup = "all") }
        }
    }

    fun selectGroup(id: String) {
        if (groupId.value == id) return
        groupId.value = id
        viewModelScope.launch { settingsRepo.update { it.copy(lastGroup = id) } }
    }

    fun focusChannel(id: String?) {
        focused.value = id
    }

    fun toggleFavorite(id: String) = viewModelScope.launch { playlists.toggleFavorite(id) }

    fun hideChannel(id: String) = viewModelScope.launch { playlists.hideChannel(id) }

    fun assignEpg(channelId: String, xmltvId: String) = viewModelScope.launch {
        playlists.setEpgId(channelId, xmltvId)
    }

    fun searchEpgNames(q: String, onResult: (List<Pair<String, String>>) -> Unit) = viewModelScope.launch {
        onResult(epg.searchXmltvNames(q))
    }
}
