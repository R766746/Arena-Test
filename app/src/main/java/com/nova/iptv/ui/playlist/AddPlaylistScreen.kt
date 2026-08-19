package com.nova.iptv.ui.playlist

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.nova.iptv.R
import com.nova.iptv.data.local.SettingsRepository
import com.nova.iptv.data.playlist.ImportException
import com.nova.iptv.data.playlist.PlaylistImporter
import com.nova.iptv.domain.model.ImportProgress
import com.nova.iptv.nav.dpadClickable
import com.nova.iptv.nav.glowBorderOnFocus
import com.nova.iptv.nav.scaleOnFocus
import com.nova.iptv.ui.components.FocusButton
import com.nova.iptv.ui.components.GlassPanel
import com.nova.iptv.ui.theme.LocalNovaPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AddMode { PICK, M3U, XTREAM, FILE }

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(
    private val importer: PlaylistImporter,
    private val settings: SettingsRepository,
) : ViewModel() {
    var progress by mutableStateOf<ImportProgress?>(null)
    var error by mutableStateOf<String?>(null)

    fun importM3u(name: String, url: String, epg: String, ua: String, onDone: () -> Unit) {
        viewModelScope.launch {
            error = null
            importer.importRemoteM3u(name, url, epg, ua) { progress = it }
                .onSuccess {
                    settings.update { s -> s.copy(firstRunDone = true, lastPlaylistId = it.id) }
                    onDone()
                }
                .onFailure { error = map(it) }
        }
    }

    fun importXtream(name: String, portal: String, user: String, pass: String, onDone: () -> Unit) {
        viewModelScope.launch {
            error = null
            importer.importXtream(name, portal, user, pass) { progress = it }
                .onSuccess {
                    settings.update { s -> s.copy(firstRunDone = true, lastPlaylistId = it.id) }
                    onDone()
                }
                .onFailure { error = map(it) }
        }
    }

    fun importFile(name: String, uri: String, onDone: () -> Unit) {
        viewModelScope.launch {
            error = null
            importer.importLocalFile(name, uri) { progress = it }
                .onSuccess {
                    settings.update { s -> s.copy(firstRunDone = true, lastPlaylistId = it.id) }
                    onDone()
                }
                .onFailure { error = map(it) }
        }
    }

    private fun map(t: Throwable): String {
        val msg = (t as? ImportException)?.message ?: t.message.orEmpty()
        return when {
            msg.contains("unknown_host") -> "unknown_host"
            msg.contains("401") || msg.contains("invalid credentials") -> "401"
            msg.contains("empty") -> "empty"
            msg.contains("ssl", true) -> "ssl"
            else -> msg.ifBlank { t.javaClass.simpleName }
        }
    }
}

@Composable
fun AddPlaylistRoute(
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: AddPlaylistViewModel = hiltViewModel(),
) {
    val colors = LocalNovaPalette.current
    val context = LocalContext.current
    var mode by rememberSaveable { mutableStateOf(AddMode.PICK) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            // OpenDocument grants a temporary read permission by default. Persist it
            // before the import so scheduled refreshes still work after process death.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            vm.importFile("File playlist", it.toString(), onDone)
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(colors.background, colors.surface2, colors.background)),
        ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 1040.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("NOVA IPTV", color = colors.accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.welcome_subtitle), color = colors.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.add_playlist), color = colors.onBackground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.welcome_body), color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
                    when (mode) {
                        AddMode.PICK -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val compact = maxWidth < 680.dp
                            if (compact) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ModeTile(stringResource(R.string.add_m3u_url), Modifier.fillMaxWidth()) { mode = AddMode.M3U }
                                    ModeTile(stringResource(R.string.add_xtream), Modifier.fillMaxWidth()) { mode = AddMode.XTREAM }
                                    ModeTile(stringResource(R.string.add_file), Modifier.fillMaxWidth()) {
                                        filePicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*"))
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ModeTile(stringResource(R.string.add_m3u_url), Modifier.weight(1f)) { mode = AddMode.M3U }
                                    ModeTile(stringResource(R.string.add_xtream), Modifier.weight(1f)) { mode = AddMode.XTREAM }
                                    ModeTile(stringResource(R.string.add_file), Modifier.weight(1f)) {
                                        filePicker.launch(arrayOf("audio/x-mpegurl", "application/vnd.apple.mpegurl", "text/plain", "*/*"))
                                    }
                                }
                            }
                        }
                        AddMode.M3U -> M3uForm(vm, onDone) { mode = AddMode.PICK }
                        AddMode.XTREAM -> XtreamForm(vm, onDone) { mode = AddMode.PICK }
                        AddMode.FILE -> Unit
                    }
                    vm.progress?.let { p ->
                Text(
                    when (p.stage) {
                        ImportProgress.Stage.CONNECTING -> stringResource(R.string.progress_connecting)
                        ImportProgress.Stage.DOWNLOADING -> stringResource(R.string.progress_downloading, p.downloadedKb)
                        ImportProgress.Stage.PARSING -> stringResource(R.string.progress_parsing, p.parsed, p.total)
                        ImportProgress.Stage.SAVING -> stringResource(R.string.progress_saving)
                        else -> p.stage.name
                    },
                    color = colors.muted,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            vm.error?.let { err ->
                val mapped = when (err) {
                    "unknown_host" -> stringResource(R.string.error_unknown_host)
                    "401" -> stringResource(R.string.error_unauthorized)
                    "empty" -> stringResource(R.string.error_empty_playlist)
                    "ssl" -> stringResource(R.string.error_ssl)
                    else -> stringResource(R.string.error_generic, err)
                }
                Text(mapped, color = colors.danger, modifier = Modifier.padding(top = 18.dp))
            }
                    Text(stringResource(R.string.first_run_legal), color = colors.muted, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
                    if (mode == AddMode.PICK) {
                        FocusButton(label = stringResource(R.string.action_cancel), onClick = onBack, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalNovaPalette.current
    GlassPanel(
        modifier
            .height(112.dp)
            .scaleOnFocus(1.04f)
            .glowBorderOnFocus(radius = 12.dp)
            .dpadClickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun M3uForm(vm: AddPlaylistViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var epg by rememberSaveable { mutableStateOf("") }
    var ua by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Field(stringResource(R.string.field_name), name) { name = it }
        Field(stringResource(R.string.field_url), url) { url = it }
        Field(stringResource(R.string.field_epg_url), epg) { epg = it }
        Field(stringResource(R.string.field_user_agent), ua) { ua = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton(label = stringResource(R.string.action_import), onClick = { vm.importM3u(name, url, epg, ua, onDone) })
            FocusButton(label = stringResource(R.string.action_cancel), onClick = onBack)
        }
    }
}

@Composable
private fun XtreamForm(vm: AddPlaylistViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var portal by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Field(stringResource(R.string.field_name), name) { name = it }
        Field(stringResource(R.string.field_portal), portal) { portal = it }
        Field(stringResource(R.string.field_username), user) { user = it }
        Field(stringResource(R.string.field_password), pass) { pass = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusButton(label = stringResource(R.string.action_import), onClick = { vm.importXtream(name, portal, user, pass, onDone) })
            FocusButton(label = stringResource(R.string.action_cancel), onClick = onBack)
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    val colors = LocalNovaPalette.current
    var focused by rememberSaveable { mutableStateOf(false) }
    Text(label, color = colors.muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .background(colors.surface2, RoundedCornerShape(10.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) colors.accent else colors.muted.copy(alpha = 0.28f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        textStyle = TextStyle(color = colors.onBackground, fontSize = 14.sp),
        cursorBrush = SolidColor(colors.accent),
        singleLine = true,
    )
}
