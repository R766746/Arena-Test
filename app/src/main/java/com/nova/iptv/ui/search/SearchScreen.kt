package com.nova.iptv.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.nova.iptv.R
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.PlaylistRepository
import com.nova.iptv.domain.model.SearchHit
import com.nova.iptv.domain.usecase.SearchCatalogUseCase
import com.nova.iptv.nav.PlayTarget
import com.nova.iptv.nav.TvLazyColumn
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.NovaTopBar
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    search: SearchCatalogUseCase,
    settings: SettingsRepository,
) : ViewModel() {
    val query = MutableStateFlow("")
    val clock24h = settings.settings
    val results = query.debounce(200).flatMapLatest { q ->
        if (q.isBlank()) flowOf(emptyList())
        else search.query(settings.settings.value.lastPlaylistId.ifBlank { "demo" }, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(4_000), emptyList())
}

@Composable
fun SearchRoute(
    onPlay: (PlayTarget) -> Unit,
    onDetail: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val hits by vm.results.collectAsStateWithLifecycle()
    val settings by vm.clock24h.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val colors = LocalNovaPalette.current
    val focus = remember { FocusRequester() }
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            text = spoken
            vm.query.value = spoken
        }
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .onPreviewKeyEvent {
                if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_SEARCH) {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    runCatching { voice.launch(intent) }
                    true
                } else false
            },
    ) {
        NovaTopBar(settings.clock24h)
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                vm.query.value = it
            },
            modifier = Modifier
                .padding(horizontal = 28.dp, vertical = 8.dp)
                .fillMaxWidth()
                .background(colors.surface2, RoundedCornerShape(8.dp))
                .padding(14.dp)
                .focusRequester(focus),
            textStyle = TextStyle(color = colors.onBackground, fontSize = 18.sp),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            singleLine = true,
            decorationBox = { inner ->
                if (text.isEmpty()) Text(stringResource(R.string.search_hint), color = colors.muted, fontSize = 16.sp)
                inner()
            },
        )
        when {
            text.isBlank() -> EmptyState(stringResource(R.string.empty_search), "")
            hits.isEmpty() -> EmptyState(stringResource(R.string.empty_search_none, text), "")
            else -> {
                TvLazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                    val groups = hits.groupBy { it.kind }
                    groups.forEach { (kind, list) ->
                        item { Text(kind.uppercase(), color = colors.muted, fontSize = 11.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)) }
                        items(list, key = { it.kind + it.id }) { hit ->
                            FocusButton(
                                label = "${hit.title}  ${hit.subtitle}",
                                onClick = {
                                    when (hit.kind) {
                                        "channel" -> onPlay(PlayTarget.live(hit.id, hit.title))
                                        "movie" -> onDetail("movie", hit.id)
                                        "series" -> onDetail("series", hit.id)
                                        else -> onPlay(PlayTarget.live(hit.id, hit.title))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
