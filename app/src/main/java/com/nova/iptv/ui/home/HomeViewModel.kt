package com.nova.iptv.ui.home

import android.content.Context

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.nova.iptv.data.epg.EpgRepository
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.data.recordings.RecordingRepository
import com.nova.iptv.data.recordings.ReminderWorker
import com.nova.iptv.data.remote.XtreamApi
import com.nova.iptv.domain.model.AppSettings
import com.nova.iptv.domain.model.Channel
import com.nova.iptv.domain.model.CatalogSort
import com.nova.iptv.domain.model.HomeCategory
import com.nova.iptv.domain.model.NowNext
import com.nova.iptv.domain.model.Playlist
import com.nova.iptv.domain.model.Program
import com.nova.iptv.domain.model.RecordingStatus
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.VodKind
import com.nova.iptv.domain.model.WatchHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Immutable
data class GroupItem(val id: String, val label: String, val count: Int)

private data class RecordingActivity(
    val programIds: Set<String> = emptySet(),
    val recordingNow: Boolean = false,
)

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
    val previewChannel: Channel? = null,
    val activeRecordingProgramIds: Set<String> = emptySet(),
    val recordingInProgress: Boolean = false,
    val firstRun: Boolean = false,
    val isLoading: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    private val settingsRepo: SettingsRepository,
    private val recordings: RecordingRepository,
    private val xtreamApi: XtreamApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val category = MutableStateFlow(HomeCategory.LIVE)
    private val groupId = MutableStateFlow("all")
    private val playlistId = MutableStateFlow("")
    private val focused = MutableStateFlow<String?>(null)
    private val previewChannel = MutableStateFlow<Channel?>(null)
    private val nowNext = MutableStateFlow<Map<String, NowNext>>(emptyMap())
    private val isLoading = MutableStateFlow(true)
    private var focusedEpgJob: Job? = null
    private val shortEpgAttemptedAt = HashMap<String, Long>()

    private val liveGroupCounts = playlistId.flatMapLatest(playlists::channelGroups)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val liveChannelCount = playlistId.flatMapLatest(playlists::channelCount)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val liveFavoriteCount = playlistId.flatMapLatest(playlists::favoriteCount)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val liveRecentCount = playlistId.flatMapLatest(playlists::recentChannelCount)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val cachedHistory = playlists.history(30)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val recordingActivity = recordings.observe()
        .map { rows ->
            RecordingActivity(
                programIds = rows.asSequence()
                .filter { it.status == RecordingStatus.SCHEDULED || it.status == RecordingStatus.RECORDING }
                .map { it.programId }
                .toSet(),
                recordingNow = rows.any { it.status == RecordingStatus.RECORDING },
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RecordingActivity())
    private val movieQuery = MutableStateFlow("")
    private val movieSort = MutableStateFlow(CatalogSort.PROVIDER)
    private val movieGroups = playlistId.flatMapLatest { playlists.vodGroups(it, VodKind.MOVIE.name) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val movieWatchlistCount = playlistId.flatMapLatest { playlists.watchlistCount(it, VodKind.MOVIE.name) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val seriesQuery = MutableStateFlow("")
    private val seriesSort = MutableStateFlow(CatalogSort.PROVIDER)
    private val seriesGroups = playlistId.flatMapLatest { playlists.vodGroups(it, VodKind.SERIES.name) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val seriesWatchlistCount = playlistId.flatMapLatest { playlists.watchlistCount(it, VodKind.SERIES.name) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    private val liveQuery = MutableStateFlow("")
    private val liveSort = MutableStateFlow(CatalogSort.PROVIDER)

    val moviesPaging = combine(playlistId, groupId, movieQuery, movieSort) { pid, gid, query, sort ->
        MoviePageRequest(pid, gid, query, sort)
    }.flatMapLatest { request ->
        playlists.pagedVod(
            playlistId = request.playlistId,
            kind = VodKind.MOVIE.name,
            genre = request.groupId.removePrefix("genre:").takeIf { request.groupId.startsWith("genre:") },
            continueWatching = request.groupId == "continue",
            watchlist = request.groupId == "watchlist",
            query = request.query,
            sort = request.sort,
        )
    }.cachedIn(viewModelScope)

    val seriesPaging = combine(playlistId, groupId, seriesQuery, seriesSort) { pid, gid, query, sort ->
        MoviePageRequest(pid, gid, query, sort)
    }.flatMapLatest { request ->
        playlists.pagedVod(
            playlistId = request.playlistId,
            kind = VodKind.SERIES.name,
            genre = request.groupId.removePrefix("genre:").takeIf { request.groupId.startsWith("genre:") },
            continueWatching = request.groupId == "continue",
            watchlist = request.groupId == "watchlist",
            query = request.query,
            sort = request.sort,
        )
    }.cachedIn(viewModelScope)

    val livePaging = combine(playlistId, groupId, liveQuery, liveSort) { pid, gid, query, sort ->
        MoviePageRequest(pid, gid, query, sort)
    }
        .flatMapLatest { request -> playlists.pagedChannels(request.playlistId, request.groupId, request.query, request.sort) }
        .cachedIn(viewModelScope)

    private val liveGroups = combine(liveGroupCounts, liveChannelCount, liveFavoriteCount, liveRecentCount) { groups, all, favs, recent ->
        buildList {
            add(GroupItem("favorites", "Favorites", favs))
            add(GroupItem("all", "All Channels", all))
            add(GroupItem("recent", "Recently Watched", recent))
            groups.forEach { group -> add(GroupItem("g:${group.name}", group.name, group.count)) }
        }
    }

    private val watchlistCounts = combine(movieWatchlistCount, seriesWatchlistCount, ::Pair)
    private val vodGroups = combine(
        category, movieGroups, seriesGroups, cachedHistory, watchlistCounts
    ) { cat, movies, series, hist, watchlist ->
        if (cat != HomeCategory.MOVIES && cat != HomeCategory.SERIES) return@combine emptyList<GroupItem>()
        withContext(Dispatchers.Default) {
            val kind = if (cat == HomeCategory.MOVIES) VodKind.MOVIE.name else VodKind.SERIES.name
            if (cat == HomeCategory.MOVIES) {
                return@withContext buildList {
                    add(GroupItem("all", "All", movies.sumOf { it.count }))
                    add(GroupItem("watchlist", "Watchlist", watchlist.first))
                    add(GroupItem("continue", "Continue Watching", hist.count { it.kind.name == kind }))
                    movies.forEach { add(GroupItem("genre:${it.name}", it.name, it.count)) }
                }
            }
            buildList {
                add(GroupItem("all", "All", series.sumOf { it.count }))
                add(GroupItem("watchlist", "Watchlist", watchlist.second))
                add(GroupItem("continue", "Continue Watching", hist.count { it.kind.name == kind }))
                series.forEach { add(GroupItem("genre:${it.name}", it.name, it.count)) }
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

    private val contentFlow: Flow<Pair<List<Channel>, List<VodItem>>> = category
        .map { emptyList<Channel>() to emptyList<VodItem>() }
        .distinctUntilChanged()

    val state: StateFlow<HomeUiState> = combine(
        combine(settingsRepo.settings, playlists.playlists(), category, groupId, playlistId) { a, b, c, d, e -> listOf(a, b, c, d, e) },
        combine(nowNext, cachedHistory, focused, previewChannel, recordingActivity) { a, b, c, d, e -> listOf(a, b, c, d, e) },
        combine(groupsFlow, contentFlow, isLoading) { a, b, c -> listOf(a, b, c) }
    ) { s1, s2, s3 ->
        val settings = s1[0] as AppSettings
        val pls = s1[1] as List<Playlist>
        val cat = s1[2] as HomeCategory
        val gid = s1[3] as String
        val pid = s1[4] as String
        
        val nn = s2[0] as Map<String, NowNext>
        val hist = s2[1] as List<WatchHistory>
        val foc = s2[2] as String?
        val preview = s2[3] as Channel?
        val recordingActivity = s2[4] as RecordingActivity
        
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
            previewChannel = preview,
            activeRecordingProgramIds = recordingActivity.programIds,
            recordingInProgress = recordingActivity.recordingNow,
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
                refreshNowNext()
            }
        }
    }

    private suspend fun refreshNowNext() {
        if (playlistId.value.isBlank()) return
        val ch = playlists.snapshotChannelsLimited(playlistId.value, 80)
        val now = System.currentTimeMillis()
        val map = LinkedHashMap<String, NowNext>()
        ch.forEach { map[it.id] = epg.nowAndNext(it.id, now) }
        // Never discard rows prefetched for the currently visible category.
        // Replacing the map here made EPG cells briefly turn into "No info"
        // every 30 seconds until focus caused another database lookup.
        nowNext.value = nowNext.value + map
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

    fun openLiveFavorites() {
        category.value = HomeCategory.LIVE
        groupId.value = "favorites"
        viewModelScope.launch {
            settingsRepo.update { it.copy(lastCategory = HomeCategory.LIVE, lastGroup = "favorites") }
        }
    }

    fun focusChannel(id: String?) {
        // Focus movement is always immediate. Guide lookup follows after a very
        // short settle period and a newer D-pad move cancels the previous lookup.
        focusedEpgJob?.cancel()
        if (id == null || hasUsableEpg(id)) return
        focusedEpgJob = viewModelScope.launch {
            delay(90)
            var value = epg.nowAndNext(id, System.currentTimeMillis())
            if (value.now == null && value.next == null) {
                fetchXtreamShortEpg(id)
                value = epg.nowAndNext(id, System.currentTimeMillis())
            }
            nowNext.value = nowNext.value + (id to value)
        }
    }

    fun prefetchChannelEpg(id: String) {
        if (hasUsableEpg(id)) return
        viewModelScope.launch {
            val value = epg.nowAndNext(id, System.currentTimeMillis())
            nowNext.value = nowNext.value + (id to value)
        }
    }

    fun recordProgram(channel: Channel, program: Program) = viewModelScope.launch {
        val active = recordings.activeFor(channel.id, program.id)
        if (active == null) recordings.schedule(program, channel.id, program.title)
    }

    fun stopRecording(channel: Channel, program: Program) = viewModelScope.launch {
        recordings.activeFor(channel.id, program.id)?.let { recordings.stop(it.id) }
    }

    fun remindProgram(channel: Channel, program: Program) {
        ReminderWorker.schedule(context, program.startMs, program.title, channel.name)
    }

    private fun hasUsableEpg(id: String, now: Long = System.currentTimeMillis()): Boolean {
        val value = nowNext.value[id] ?: return false
        return value.now?.isNow(now) == true || value.next?.endMs?.let { it > now } == true
    }

    private suspend fun fetchXtreamShortEpg(channelId: String) {
        val now = System.currentTimeMillis()
        val lastAttempt = shortEpgAttemptedAt[channelId] ?: 0L
        if (now - lastAttempt < 15 * 60_000L) return
        shortEpgAttemptedAt[channelId] = now
        val channel = playlists.getChannel(channelId) ?: return
        val streamId = channel.xtreamStreamId.takeIf { it.isNotBlank() } ?: return
        val playlist = playlists.getPlaylist(channel.playlistId)?.takeIf { it.type == PlaylistType.XTREAM } ?: return
        val password = playlists.resolvedPassword(playlist).takeIf { it.isNotBlank() } ?: return
        val api = "${playlist.url.trim().trimEnd('/')}/player_api.php"
        val response = runCatching {
            xtreamApi.simpleEpg(api, playlist.username.trim(), password.trim(), streamId = streamId)
        }.recoverCatching {
            xtreamApi.shortEpg(api, playlist.username.trim(), password.trim(), streamId = streamId)
        }.getOrNull() ?: return
        val programs = response.listings.orEmpty().mapNotNull { listing ->
            val start = listing.startTs?.toLongOrNull()?.times(1000L) ?: return@mapNotNull null
            val end = listing.stopTs?.toLongOrNull()?.times(1000L) ?: return@mapNotNull null
            if (end <= start) return@mapNotNull null
            Program(
                id = "xtream:${channel.id}:$start",
                channelId = channel.id,
                title = decodeXtreamEpgText(listing.title).ifBlank { "Programme" },
                description = decodeXtreamEpgText(listing.description),
                startMs = start,
                endMs = end,
                catchup = channel.catchup && end < now,
            )
        }
        if (programs.isNotEmpty()) epg.ingestPrograms(programs)
    }

    private fun decodeXtreamEpgText(value: String?): String {
        val raw = value.orEmpty()
        if (raw.isBlank()) return ""
        return runCatching {
            val decoded = android.util.Base64.decode(raw, android.util.Base64.DEFAULT).decodeToString()
            decoded.takeIf { text -> text.all { !it.isISOControl() || it in "\n\r\t" } } ?: raw
        }.getOrDefault(raw)
    }

    fun updateMovieBrowser(query: String, sort: CatalogSort) {
        if (movieQuery.value != query) movieQuery.value = query
        if (movieSort.value != sort) movieSort.value = sort
    }

    fun updateSeriesBrowser(query: String, sort: CatalogSort) {
        if (seriesQuery.value != query) seriesQuery.value = query
        if (seriesSort.value != sort) seriesSort.value = sort
    }

    fun updateLiveBrowser(query: String, sort: CatalogSort) {
        if (liveQuery.value != query) liveQuery.value = query
        if (liveSort.value != sort) liveSort.value = sort
    }

    fun selectPreviewChannel(id: String) {
        focusChannel(id)
        viewModelScope.launch { previewChannel.value = playlists.getChannel(id) }
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

private data class MoviePageRequest(
    val playlistId: String,
    val groupId: String,
    val query: String,
    val sort: CatalogSort,
)
