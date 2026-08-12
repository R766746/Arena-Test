package com.nova.iptv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.DemoCatalog
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupItem(val id: String, val label: String, val count: Int)

data class HomeUiState(
    val settings: AppSettings = AppSettings(),
    val playlists: List<Playlist> = emptyList(),
    val playlistId: String = Playlist.DEMO_ID,
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val settingsRepo: SettingsRepository,
    private val demo: DemoCatalog,
) : ViewModel() {

    private val category = MutableStateFlow(HomeCategory.LIVE)
    private val groupId = MutableStateFlow("all")
    private val playlistId = MutableStateFlow(Playlist.DEMO_ID)
    private val focused = MutableStateFlow<String?>(null)
    private val nowMs = MutableStateFlow(System.currentTimeMillis())
    private val nowNext = MutableStateFlow<Map<String, NowNext>>(emptyMap())

    private val baseState = combine(
        combine(settingsRepo.settings, playlists.playlists(), category) { s, p, c -> Triple(s, p, c) },
        combine(groupId, playlistId, focused) { g, p, f -> Triple(g, p, f) },
        combine(nowMs, nowNext, playlists.history(24)) { n, nn, h -> Triple(n, nn, h) },
    ) { a, b, c ->
        val (settings, pls, cat) = a
        val (gid, pid, foc) = b
        val (now, nn, hist) = c
        HomeUiState(
            settings = settings,
            playlists = pls,
            playlistId = pid,
            category = cat,
            groupId = gid,
            nowNext = nn,
            history = hist,
            focusedChannelId = foc,
            nowMs = now,
            firstRun = !settings.firstRunDone && pls.none { it.type != com.nova.iptv.domain.model.PlaylistType.DEMO },
        )
    }

    val state: StateFlow<HomeUiState> = baseState.combine(contentFlow()) { base, extra ->
        base.copy(groups = extra.groups, channels = extra.channels, vod = extra.vod)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private data class Extra(val groups: List<GroupItem>, val channels: List<Channel>, val vod: List<VodItem>)

    private fun contentFlow() = combine(playlistId, category, groupId) { pid, cat, gid -> Triple(pid, cat, gid) }
        .flatMapLatest { (pid, cat, gid) ->
            when (cat) {
                HomeCategory.LIVE -> combine(
                    playlists.channels(pid),
                    playlists.favorites(pid),
                    playlists.recentChannels(pid),
                    playlists.groups(pid),
                ) { all, favs, recent, groups ->
                    val items = buildList {
                        add(GroupItem("favorites", "Favorites", favs.size))
                        add(GroupItem("all", "All Channels", all.size))
                        add(GroupItem("recent", "Recently Watched", recent.size))
                        groups.filter { it.isNotBlank() }.forEach { g ->
                            add(GroupItem("g:$g", g, all.count { it.groupName == g }))
                        }
                    }
                    val ch = when {
                        gid == "favorites" -> favs
                        gid == "recent" -> recent
                        gid.startsWith("g:") -> all.filter { it.groupName == gid.removePrefix("g:") }
                        else -> all
                    }
                    Extra(items, ch, emptyList())
                }
                HomeCategory.MOVIES, HomeCategory.SERIES -> {
                    val kind = if (cat == HomeCategory.MOVIES) VodKind.MOVIE.name else VodKind.SERIES.name
                    playlists.vod(pid, kind).combine(playlists.history(30)) { vod, hist ->
                        val genres = vod.flatMap { it.genres }.distinct().sorted()
                        val groups = buildList {
                            add(GroupItem("all", "All", vod.size))
                            add(GroupItem("continue", "Continue Watching", hist.count { it.kind.name == kind }))
                            genres.forEach { g -> add(GroupItem("genre:$g", g, vod.count { it.genres.contains(g) })) }
                        }
                        val filtered = when {
                            gid == "continue" -> {
                                val ids = hist.map { it.refId }.toSet()
                                vod.filter { it.id in ids }
                            }
                            gid.startsWith("genre:") -> vod.filter { it.genres.contains(gid.removePrefix("genre:")) }
                            else -> vod
                        }
                        Extra(groups, emptyList(), filtered)
                    }
                }
                else -> flowOf(Extra(emptyList(), emptyList(), emptyList()))
            }
        }

    init {
        viewModelScope.launch {
            demo.ensureSeeded()
            val s = settingsRepo.settings.value
            if (s.lastPlaylistId.isNotBlank()) playlistId.value = s.lastPlaylistId
            if (s.lastCategory == HomeCategory.LIVE || s.lastCategory == HomeCategory.MOVIES || s.lastCategory == HomeCategory.SERIES) {
                category.value = s.lastCategory
            }
            if (s.lastGroup.isNotBlank()) groupId.value = s.lastGroup
            while (true) {
                delay(30_000)
                nowMs.value = System.currentTimeMillis()
                refreshNowNext()
            }
        }
        viewModelScope.launch {
            // initial now/next
            delay(400)
            refreshNowNext()
        }
    }

    private suspend fun refreshNowNext() {
        val ch = playlists.snapshotChannels(playlistId.value).take(80)
        val now = System.currentTimeMillis()
        val map = LinkedHashMap<String, NowNext>()
        ch.forEach { map[it.id] = epg.nowAndNext(it.id, now) }
        nowNext.value = map
    }

    fun selectCategory(cat: HomeCategory) {
        category.value = cat
        groupId.value = "all"
        viewModelScope.launch {
            settingsRepo.update { it.copy(lastCategory = cat) }
        }
    }

    fun selectGroup(id: String) {
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

    fun dismissFirstRun() = viewModelScope.launch {
        settingsRepo.update { it.copy(firstRunDone = true) }
    }

    fun installShowcase() = viewModelScope.launch {
        demo.seed()
        settingsRepo.update { it.copy(firstRunDone = true) }
    }
}
