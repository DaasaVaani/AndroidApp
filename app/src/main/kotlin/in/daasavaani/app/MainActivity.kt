package `in`.daasavaani.app

import android.os.Bundle
import android.content.res.Configuration
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import `in`.daasavaani.core.uiLanguage
import `in`.daasavaani.core.PersonalReference
import `in`.daasavaani.core.expandedResultCount

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkBackground = Color(getColor(R.color.theme_background)).luminance() < 0.5f
        val systemBars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkBackground }
        enableEdgeToEdge(statusBarStyle = systemBars, navigationBarStyle = systemBars)
        setContent {
            val model: LibraryViewModel = viewModel()
            val state by model.state.collectAsState()
            val system = LocalConfiguration.current
            val language = state.personal?.language ?: "device"
            val locale = uiLanguage((0 until system.locales.size()).map { system.locales[it].toLanguageTag() }, language)
            val configuration = Configuration(system).apply { setLocales(LocaleList(Locale.forLanguageTag(locale))) }
            val localized = createConfigurationContext(configuration)
            val theme = state.personal?.theme ?: "device"
            val dark = theme == "dark" || (theme == "device" && isSystemInDarkTheme())
            SideEffect {
                val bars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            val colors = if (dark) darkColorScheme() else if (theme == "warm") lightColorScheme(
                primary = colorResource(R.color.theme_primary), background = colorResource(R.color.theme_background),
                surface = colorResource(R.color.theme_surface), onBackground = colorResource(R.color.theme_text),
                onSurface = colorResource(R.color.theme_text)) else lightColorScheme()
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                LocalActivityResultRegistryOwner provides this@MainActivity) {
                MaterialTheme(colorScheme = colors) { LibraryScreen(model) }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(model: LibraryViewModel = viewModel(), lyricModel: LyricPreviewViewModel = viewModel(),
    transferModel: TransferViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val lyrics by lyricModel.state.collectAsState()
    val listScroll = rememberLazyListState()
    val favoritesScroll = rememberLazyListState()
    LaunchedEffect(lyrics.favoritesQuery, lyrics.favoritesFailed) { favoritesScroll.scrollToItem(0) }
    LaunchedEffect(state.selectedListId) { listScroll.scrollToItem(0) }
    var adding by remember { mutableStateOf<PersonalReference?>(null) }
    var fromList by remember { mutableStateOf(false) }
    var fromFavorites by remember { mutableStateOf(false) }
    BackHandler(enabled = state.section == "lyrics" && fromFavorites) { model.section("favorites") }
    LaunchedEffect(state.section) {
        if (state.section != "lyrics") { fromList = false; fromFavorites = false }
    }
    fun openReference(reference: PersonalReference) {
        if (reference.kind == "lyric") {
            fromList = state.section == "lists"
            fromFavorites = state.section == "favorites"
            lyricModel.request(reference.id)
            model.section("lyrics")
        }
    }
    adding?.let { reference -> AddToListDialog(model, reference) { adding = null } }
    val space = integerResource(R.integer.spacing_dp).dp
    val contentPadding = integerResource(R.integer.content_padding_dp).dp
    val readerFont = integerResource(R.integer.reader_font_size_sp).sp
    val readerLineHeight = integerResource(R.integer.reader_line_height_sp).sp
    BackHandler(enabled = state.selected != null) { model.select(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(contentPadding), verticalArrangement = Arrangement.spacedBy(space * 2)) {
            if (state.section == "library" || state.selected != null) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.fixture_notice), Modifier.padding(space * 1.5f), style = MaterialTheme.typography.bodySmall)
            }
            if (state.section != "lyrics") state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.personalError?.let {
                Text(stringResource(if (it == R.string.personal_invalid) R.string.personal_schema else it), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = model::retryPersonal, enabled = !state.saving) { Text(stringResource(R.string.retry)) }
            }
            state.historyError?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("history-error"))
                TextButton(enabled = !state.saving, onClick = model::retryPersonal) { Text(stringResource(R.string.retry)) }
            }
            if (state.personal == null && state.saving) Text(stringResource(R.string.personal_loading))
            if (state.mutationError == R.string.personal_stale) {
                Text(stringResource(R.string.personal_stale), color = MaterialTheme.colorScheme.error)
                TextButton(enabled = !state.saving, onClick = model::retryPersonal) { Text(stringResource(R.string.retry)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space)) {
                TextButton(onClick = { model.section("library") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.library)) }
                TextButton(onClick = { model.section("favorites") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.favorites)) }
                TextButton(onClick = { model.section("lists") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.lists)) }
                TextButton(onClick = { model.section("settings") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.settings)) }
            }
            val personal = state.personal
            val canSave = personal != null && state.personalError == null && !state.saving
            val selected = state.selected
            Row(horizontalArrangement = Arrangement.spacedBy(space)) {
                TextButton(onClick = {
                    fromFavorites = false; fromList = false; model.section("lyrics")
                }, modifier = Modifier.weight(1f).testTag("owner-lyrics")) {
                    Text(stringResource(R.string.owner_lyrics))
                }
                if (state.section == "library" && selected == null) {
                    TextButton(onClick = { model.section("recents") }, modifier = Modifier.weight(1f).testTag("recent-history")) {
                        Text(stringResource(R.string.recent_history))
                    }
                }
            }
            if (state.section == "lyrics") {
                Column(Modifier.weight(1f)) {
                    if (fromList) TextButton(onClick = { model.section("lists"); fromList = false }) { Text(stringResource(R.string.back_to_list)) }
                    if (fromFavorites) TextButton(onClick = { model.section("favorites") }, modifier = Modifier.testTag("back-to-favorites")) {
                        Text("← " + stringResource(R.string.favorites))
                    }
                    LyricPreviewScreen(lyricModel, personal, canSave, { model.changePersonal(reference = it) },
                        { model.clearMutationError(); adding = it },
                        if (fromList) ({ model.section("lists"); fromList = false })
                        else if (fromFavorites) ({ model.section("favorites") }) else null, model::recordExposed)
                }
            } else if (state.section == "recents" && selected == null) {
                Column(Modifier.weight(1f)) {
                    RecentHistoryScreen(personal, canSave, state.prepared, lyrics, ::openReference,
                        model::select, { model.changeHistory(enabled = it) }, { model.changeHistory(clear = true) })
                }
            } else if (state.section == "lists") {
                Column(Modifier.weight(1f)) {
                    ListsScreen(model, lyrics, ::openReference, export = { snapshot ->
                        transferModel.showSnapshot(snapshot); model.section("transfer")
                    }, scrollState = listScroll)
                }
            } else if (state.section == "transfer") {
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = { model.section("settings") }) { Text(stringResource(R.string.back)) }
                    TransferScreen(state.prepared, transferModel, model, lyrics, ::openReference)
                }
            } else if (state.section == "settings") {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    OutlinedButton(onClick = { model.section("transfer") }) { Text(stringResource(R.string.transfer_title)) }
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                    listOf("device" to stringResource(R.string.follow_device), "en" to "English", "kn" to "ಕನ್ನಡ").forEach { (value, label) ->
                        Row {
                            RadioButton(modifier = Modifier.testTag("language-$value"), selected = personal?.language == value, enabled = canSave,
                                onClick = { model.changePersonal(language = value) })
                            TextButton(enabled = canSave, onClick = { model.changePersonal(language = value) }) { Text(label) }
                        }
                    }
                    Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
                    listOf("device" to stringResource(R.string.follow_device), "light" to stringResource(R.string.light),
                        "dark" to stringResource(R.string.dark), "warm" to stringResource(R.string.warm)).forEach { (value, label) ->
                        Row {
                            RadioButton(modifier = Modifier.testTag("theme-$value"), selected = personal?.theme == value, enabled = canSave,
                                onClick = { model.changePersonal(theme = value) })
                            TextButton(enabled = canSave, onClick = { model.changePersonal(theme = value) }) { Text(label) }
                        }
                    }
                    RecentHistoryControls(personal, canSave, { model.changeHistory(enabled = it) }, { model.changeHistory(clear = true) })
                }
            } else if (selected != null) {
                LaunchedEffect(state.readerOpen) { state.readerOpen?.let(model::fixtureExposed) }
                TextButton(onClick = { model.select(null) }) { Text(stringResource(R.string.back)) }
                Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.canonical), style = MaterialTheme.typography.labelSmall)
                OutlinedButton(enabled = canSave, onClick = { model.changePersonal(favorite = selected.videoId) }) {
                    Text(stringResource(if (PersonalReference.legacy(selected.videoId) in personal?.favorites.orEmpty()) R.string.remove_favorite else R.string.add_favorite))
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(space * 2.5f)) {
                    selected.lines.forEach { line -> Text(line.text, fontSize = readerFont, lineHeight = readerLineHeight) }
                }
            } else if (state.section == "favorites") {
                if (personal?.favorites?.isEmpty() == true) Text(stringResource(R.string.empty_favorites))
                val references = lyricModel.favoriteReferences(personal?.favorites.orEmpty())
                val shown = expandedResultCount(references.size, integerResource(R.integer.trusted_max_page_size), lyrics.favoritesPages)
                OutlinedTextField(value = lyrics.favoritesQuery, onValueChange = lyricModel::favoritesQuery,
                    singleLine = true, label = { Text(stringResource(R.string.lyric_title_filter)) },
                    modifier = Modifier.fillMaxWidth().testTag("favorites-filter"))
                if (lyrics.favoritesFailed) Text(stringResource(R.string.lyrics_unavailable), color = MaterialTheme.colorScheme.error)
                else if (references.isEmpty() && personal?.favorites?.isNotEmpty() == true) Text(stringResource(R.string.no_results))
                Text(stringResource(R.string.result_count).replace("{shown}", shown.toString()).replace("{total}", references.size.toString()),
                    modifier = Modifier.testTag("favorites-count"))
                LazyColumn(Modifier.weight(1f).testTag("favorites-results"), state = favoritesScroll, verticalArrangement = Arrangement.spacedBy(space)) {
                    items(references.take(shown), key = { it.kind + ":" + it.id }) { reference ->
                        val item = if (reference.kind == "legacyVideo") state.prepared?.items?.find { it.videoId == reference.id } else null
                        Card(Modifier.fillMaxWidth().testTag("favorite-${reference.kind}-${reference.id}")) {
                            Column(Modifier.padding(space)) {
                                if (reference.kind == "lyric") ReferenceContent(reference.entry(), lyrics, ::openReference)
                                else {
                                    Text(item?.title ?: reference.id)
                                    Text(stringResource(R.string.legacy_reference))
                                    if (item == null) Text(stringResource(if (state.prepared == null) R.string.preparation_notice else R.string.unavailable_favorite))
                                    else {
                                        Text(stringResource(R.string.fixture_notice), style = MaterialTheme.typography.bodySmall)
                                        TextButton(onClick = { model.select(item) }) { Text(stringResource(R.string.open)) }
                                    }
                                }
                                TextButton(enabled = canSave, onClick = { model.changePersonal(reference = reference) }) {
                                    Text(stringResource(R.string.remove_favorite))
                                }
                                TextButton(enabled = canSave, onClick = { model.clearMutationError(); adding = reference }) {
                                    Text(stringResource(R.string.add_to_list))
                                }
                            }
                        }
                    }
                    if (shown < references.size) item {
                        TextButton(onClick = lyricModel::moreFavorites, modifier = Modifier.testTag("favorites-more")) {
                            Text(stringResource(R.string.show_more_results))
                        }
                    }
                }
            } else if (state.prepared == null) {
                Text(stringResource(R.string.preparation_notice))
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${stringResource(R.string.loading)} ${state.completed}/${state.total}")
                    OutlinedButton(onClick = model::cancel) { Text(stringResource(R.string.cancel)) }
                } else {
                    Button(onClick = model::prepare) { Text(stringResource(R.string.prepare_library)) }
                }
                Text(stringResource(R.string.unavailable_features), style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(value = state.query, onValueChange = model::query,
                    label = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space * 1.5f)) {
                    items(state.visible.take(requireNotNull(state.prepared).policy.pageSize), key = { it.videoId }) { item ->
                        Card(onClick = { model.select(item) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(space * 2.5f), verticalArrangement = Arrangement.spacedBy(space * 1.5f)) {
                                Text(item.title, style = MaterialTheme.typography.titleLarge)
                                item.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text(stringResource(R.string.open), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (state.visible.isEmpty()) item { Text(stringResource(R.string.no_results)) }
                }
            }
        }
    }
}
