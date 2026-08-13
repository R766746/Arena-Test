package com.nova.iptv.ui.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.nova.iptv.R
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.data.remote.XtreamApi
import com.nova.iptv.data.remote.TmdbApi
import com.nova.iptv.domain.model.Episode
import com.nova.iptv.domain.model.VodItem
import com.nova.iptv.domain.model.VodKind
import com.nova.iptv.domain.model.PlaylistType
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.TvLazyColumn
import com.nova.iptv.nav.TvLazyVerticalGrid
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.components.PosterCard
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VodGridViewModel @Inject constructor(
    private val repo: PlaylistRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val movies: StateFlow<List<VodItem>> = settings.settings.flatMapLatest {
        repo.vod(it.lastPlaylistId.ifBlank { "demo" }, VodKind.MOVIE.name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())

    val series: StateFlow<List<VodItem>> = settings.settings.flatMapLatest {
        repo.vod(it.lastPlaylistId.ifBlank { "demo" }, VodKind.SERIES.name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())

    val clock24h = settings.settings
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repo: PlaylistRepository,
    private val xtream: XtreamApi,
    private val tmdb: TmdbApi,
    private val settings: SettingsRepository,
) : ViewModel() {
    var item by mutableStateOf<VodItem?>(null)
    var episodes by mutableStateOf<List<Episode>>(emptyList())
    var similar by mutableStateOf<List<VodItem>>(emptyList())

    fun load(id: String) {
        viewModelScope.launch {
            val vod = repo.getVod(id) ?: return@launch
            item = vod
            episodes = repo.episodes(id).first()
            enrichFromXtream(vod)
            enrichArtworkFromTmdb(item ?: vod)
            val enriched = item ?: vod
            val all = repo.vod(enriched.playlistId, enriched.kind.name).first()
            similar = all.filter { it.id != id && it.genres.any { g -> g in enriched.genres } }.take(8)
        }
    }

    private suspend fun enrichFromXtream(vod: VodItem) {
        if (vod.xtreamId.isBlank()) return
        val playlist = repo.getPlaylist(vod.playlistId) ?: return
        if (playlist.type != PlaylistType.XTREAM) return
        val password = repo.resolvedPassword(playlist)
        if (password.isBlank()) return
        val base = playlist.url.trimEnd('/')
        val api = "$base/player_api.php"
        runCatching {
            if (vod.kind == VodKind.MOVIE) {
                val info = xtream.vodInfo(api, playlist.username, password, vodId = vod.xtreamId).info
                    ?: return@runCatching
                val updated = vod.copy(
                    description = info.plot.orEmpty().ifBlank { vod.description },
                    cast = info.cast.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { vod.cast },
                    director = info.director.orEmpty().ifBlank { vod.director },
                    genres = info.genre.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { vod.genres },
                    durationMin = durationMinutes(info.duration).takeIf { it > 0 } ?: vod.durationMin,
                    posterUrl = info.movie_image.orEmpty().ifBlank { vod.posterUrl },
                    backdropUrl = info.backdrop_path?.firstOrNull().orEmpty().ifBlank { vod.backdropUrl },
                    rating = info.rating.orEmpty().ifBlank { vod.rating },
                )
                repo.saveVodDetails(updated)
                item = updated
            } else {
                val result = xtream.seriesInfo(api, playlist.username, password, seriesId = vod.xtreamId)
                val info = result.info
                val updated = vod.copy(
                    description = info?.plot.orEmpty().ifBlank { vod.description },
                    cast = info?.cast.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { vod.cast },
                    director = info?.director.orEmpty().ifBlank { vod.director },
                    genres = info?.genre.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { vod.genres },
                    posterUrl = info?.cover.orEmpty().ifBlank { vod.posterUrl },
                    backdropUrl = info?.backdrop_path?.firstOrNull().orEmpty().ifBlank { vod.backdropUrl },
                    rating = info?.rating.orEmpty().ifBlank { vod.rating },
                )
                val fetchedEpisodes = result.episodes.orEmpty().flatMap { (seasonKey, list) ->
                    list.mapIndexed { index, ep ->
                        val episodeId = ep.id.orEmpty()
                        val ext = ep.container_extension.orEmpty().ifBlank { "mp4" }
                        Episode(
                            id = "${vod.id}:episode:$episodeId",
                            seriesId = vod.id,
                            season = ep.season ?: seasonKey.toIntOrNull() ?: 0,
                            episode = ep.episodeNum ?: index + 1,
                            title = ep.title.orEmpty().ifBlank { "Episode ${index + 1}" },
                            durationMin = durationMinutes(ep.duration),
                            description = ep.plot.orEmpty(),
                            streamUrl = "$base/series/${playlist.username}/$password/$episodeId.$ext",
                        )
                    }
                }
                repo.saveVodDetails(updated, fetchedEpisodes)
                item = updated
                episodes = fetchedEpisodes
            }
        }
    }

    private fun durationMinutes(raw: String?): Int {
        val parts = raw.orEmpty().split(':')
        return when (parts.size) {
            3 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            2 -> parts[0].toIntOrNull() ?: 0
            else -> raw.orEmpty().filter(Char::isDigit).toIntOrNull() ?: 0
        }
    }

    private suspend fun enrichArtworkFromTmdb(vod: VodItem) {
        if (vod.posterUrl.isNotBlank()) return
        val key = settings.settings.value.tmdbKey.trim()
        if (key.isBlank()) return
        runCatching {
            val result = if (vod.kind == VodKind.MOVIE) {
                tmdb.searchMovies(key, vod.title, vod.year.takeIf { it > 0 })
            } else {
                tmdb.searchSeries(key, vod.title, vod.year.takeIf { it > 0 })
            }.results.firstOrNull { !it.posterPath.isNullOrBlank() } ?: return@runCatching
            val updated = vod.copy(
                posterUrl = "https://image.tmdb.org/t/p/w500${result.posterPath}",
                backdropUrl = result.backdropPath?.let { "https://image.tmdb.org/t/p/w1280$it" }
                    .orEmpty().ifBlank { vod.backdropUrl },
                tmdbId = result.id.toString(),
            )
            repo.saveVodDetails(updated, episodes)
            item = updated
        }
    }

    fun toggleWatchlist() = viewModelScope.launch {
        val v = item ?: return@launch
        repo.setWatchlist(v.id, !v.watchlist)
        item = v.copy(watchlist = !v.watchlist)
    }

    fun rate(r: Float) = viewModelScope.launch {
        val v = item ?: return@launch
        repo.setRating(v.id, r)
        item = v.copy(localRating = r)
    }
}

@Composable
fun MoviesRoute(onDetail: (String) -> Unit, onBack: () -> Unit, vm: VodGridViewModel = hiltViewModel()) {
    val items by vm.movies.collectAsStateWithLifecycle()
    val settings by vm.clock24h.collectAsStateWithLifecycle()
    VodGridScreen(stringResource(R.string.cat_movies), items, settings.clock24h, onDetail, onBack)
}

@Composable
fun SeriesRoute(onDetail: (String) -> Unit, onBack: () -> Unit, vm: VodGridViewModel = hiltViewModel()) {
    val items by vm.series.collectAsStateWithLifecycle()
    val settings by vm.clock24h.collectAsStateWithLifecycle()
    VodGridScreen(stringResource(R.string.cat_series), items, settings.clock24h, onDetail, onBack)
}

@Composable
fun VodGridScreen(
    title: String,
    items: List<VodItem>,
    clock24h: Boolean,
    onDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalNovaPalette.current
    var focused by remember { mutableStateOf<VodItem?>(null) }
    val fade by animateFloatAsState(if (focused?.backdropUrl.isNullOrBlank()) 0f else 1f, label = "bd")
    Box(Modifier.fillMaxSize().background(colors.background)) {
        focused?.backdropUrl?.takeIf { it.isNotBlank() }?.let { url ->
            AsyncImage(url, null, Modifier.fillMaxSize().alpha(0.22f * fade), contentScale = ContentScale.Crop)
        }
        Column {
            NovaTopBar(clock24h)
            Text(title, color = colors.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.empty_movies), stringResource(R.string.empty_channels_hint))
            } else {
                TvLazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(items.size, key = { items[it].id }) { idx ->
                        val item = items[idx]
                        PosterCard(
                            title = item.title,
                            year = item.year,
                            posterUrl = item.posterUrl,
                            progress = 0f,
                            onClick = { onDetail(item.id) },
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRoute(
    kind: String,
    id: String,
    onPlay: (PlayTarget) -> Unit,
    onOpen: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: DetailViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(id) { vm.load(id) }
    val item = vm.item
    val colors = LocalNovaPalette.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        if (item?.backdropUrl?.isNotBlank() == true) {
            AsyncImage(item.backdropUrl, null, Modifier.fillMaxSize().alpha(0.22f), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
        }
        if (item == null) {
            EmptyState("Loading", "")
            return
        }
        Column(Modifier.padding(36.dp)) {
            NovaTopBar(true)
            Row {
                PosterCard(item.title, item.year, item.posterUrl, 0f, onClick = {
                    onPlay(if (item.kind == VodKind.MOVIE) PlayTarget.vod(item.id, item.title, item.streamUrl) else PlayTarget.episode(vm.episodes.firstOrNull()?.id.orEmpty(), item.title, vm.episodes.firstOrNull()?.streamUrl))
                })
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = colors.onBackground, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(item.year.takeIf { it > 0 }?.toString(), item.rating, item.durationMin.takeIf { it > 0 }?.let { "$it min" }, item.director.takeIf { it.isNotBlank() })
                            .joinToString("  ·  "),
                        color = colors.muted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(item.cast.joinToString(", "), color = colors.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(item.description, color = colors.onBackground, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    Row {
                        FocusButton(label = stringResource(R.string.play), onClick = {
                            onPlay(PlayTarget.vod(item.id, item.title, item.streamUrl))
                        })
                        Spacer(Modifier.width(8.dp))
                        FocusButton(label = if (item.watchlist) stringResource(R.string.watchlist_added) else stringResource(R.string.watchlist), onClick = vm::toggleWatchlist)
                        Spacer(Modifier.width(8.dp))
                        FocusButton(label = stringResource(R.string.rate), onClick = { vm.rate(4f) })
                    }
                }
            }
            if (vm.episodes.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.season, 1), color = colors.onBackground, fontSize = 16.sp)
                TvLazyColumn(Modifier.height(220.dp)) {
                    items(vm.episodes, key = { it.id }) { ep ->
                        FocusButton(
                            label = stringResource(R.string.episode_n, ep.episode, ep.title),
                            onClick = { onPlay(PlayTarget.episode(ep.id, ep.title, ep.streamUrl)) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        )
                    }
                }
            }
            if (vm.similar.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.more_like_this), color = colors.onBackground, fontSize = 16.sp)
                Row {
                    vm.similar.take(6).forEach { s ->
                        PosterCard(s.title, s.year, s.posterUrl, 0f, { onOpen(kind, s.id) }, Modifier.padding(6.dp))
                    }
                }
            }
        }
    }
}
