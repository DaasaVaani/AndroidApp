package `in`.daasavaani.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.daasavaani.core.LyricSummary
import `in`.daasavaani.core.PersonalReference
import `in`.daasavaani.core.ReadingPreferences
import `in`.daasavaani.core.expandedResultCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun LyricPreviewScreen(model: LyricPreviewViewModel = viewModel(), personal: PersonalSnapshot? = null,
    canSave: Boolean = false, favorite: (PersonalReference) -> Unit = {}, add: (PersonalReference) -> Unit = {},
    backToList: (() -> Unit)? = null, opened: (PersonalReference) -> Unit = {},
    fontStep: (Int) -> Unit = {}, density: (String) -> Unit = {},
    browseScroll: LazyListState = remember { LazyListState() }) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    BackHandler(enabled = state.selected != null) { backToList?.invoke() ?: model.select(null) }
    DisposableEffect(model) { onDispose { model.cancel() } }
    LyricPreviewContent(state, model::load, model::cancel, {
        model.query(it); scope.launch { browseScroll.scrollToItem(0) }
    }, { summary -> if (summary == null && backToList != null) backToList() else model.select(summary) },
        exposed = { token -> model.exposed(token)?.let(opened) }, reading = personal?.reading ?: ReadingPreferences(),
        more = model::more, browseScroll = browseScroll, another = model::showAnother,
        catalogBack = backToList == null) { summary ->
        val reference = PersonalReference.lyric(summary.lyricId)
        OutlinedButton(enabled = canSave, onClick = { favorite(reference) }) {
            Text(stringResource(if (reference in personal?.favorites.orEmpty()) R.string.remove_favorite else R.string.add_favorite))
        }
        OutlinedButton(enabled = canSave, onClick = { add(reference) }) { Text(stringResource(R.string.add_to_list)) }
        ReadingControls(personal?.reading ?: ReadingPreferences(), canSave, fontStep, density)
    }
}

@Composable
fun LyricPreviewContent(
    state: LyricPreviewState, load: () -> Unit, cancel: () -> Unit,
    query: (String) -> Unit, select: (LyricSummary?) -> Unit,
    exposed: (String) -> Unit = {},
    reading: ReadingPreferences = ReadingPreferences(),
    more: () -> Unit = {},
    browseScroll: LazyListState = remember { LazyListState() },
    another: () -> Unit = {},
    catalogBack: Boolean = true,
    personalActions: @Composable (LyricSummary) -> Unit = {},
) {
    val space = integerResource(R.integer.spacing_dp).dp
    val readerScroll = rememberLazyListState()
    val scroll = if (state.selected == null) browseScroll else readerScroll
    val focus = remember { FocusRequester() }
    var focusReady by remember(state.selected, state.browseFocusId) { mutableStateOf(false) }
    LaunchedEffect(focusReady) {
        if (focusReady && state.selected == null && state.browseFocusId != null) {
            if (scroll.layoutInfo.visibleItemsInfo.any { it.key == state.browseFocusId } &&
                state.visible.any { it.lyricId == state.browseFocusId }) focus.requestFocus()
        }
    }
    val shown = expandedResultCount(state.visible.size, integerResource(R.integer.trusted_max_page_size), state.pages)
    LaunchedEffect(state.selected?.lyricId) { if (state.selected != null) readerScroll.scrollToItem(0) }
    LaunchedEffect(state.readerOpen, state.item) {
        val token = state.readerOpen
        if (token != null && state.item != null) {
            snapshotFlow { scroll.layoutInfo.visibleItemsInfo.any { it.key == "lyric-text" } }.first { it }
            exposed(token)
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("lyric-preview"), state = scroll,
        verticalArrangement = Arrangement.spacedBy(space)) {
        item {
            Text(stringResource(R.string.lyric_preview_notice), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.lyric_personal_limits), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.lyric_no_media), style = MaterialTheme.typography.bodySmall)
        }
        item {
            if (state.selected != null && catalogBack) TextButton(onClick = { select(null) }) { Text(stringResource(R.string.lyric_back)) }
            if (state.pendingLyricId != null && state.selected == null) {
                Text(state.pendingLyricId)
                Text(stringResource(if (state.prepared == null) R.string.lyric_catalog_pending else R.string.lyrics_unavailable))
            }
            if (state.failed) Text(stringResource(R.string.lyrics_unavailable), color = MaterialTheme.colorScheme.error)
            if (state.loading) {
                Text(stringResource(R.string.loading))
                Text("${state.completed} / ${state.total}")
                OutlinedButton(onClick = cancel) { Text(stringResource(R.string.cancel)) }
            } else {
                OutlinedButton(onClick = load, modifier = Modifier.testTag("lyric-load")) {
                    Text(stringResource(if (state.failed) R.string.retry else R.string.prepare_library))
                }
            }
        }
        if (state.prepared != null && (state.selected == null || state.item?.lyricId == state.selected.lyricId)) item {
            Text(stringResource(R.string.another_lyric_notice), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = another, modifier = Modifier.testTag("show-another-lyric")) {
                Text(stringResource(R.string.show_another_lyric))
            }
            if (state.anotherUnavailable) Text(stringResource(R.string.no_another_lyric),
                modifier = Modifier.testTag("no-another-lyric"))
        }
        val selected = state.selected
        if (selected != null) {
            item {
                Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                personalActions(selected)
            }
            val lyric = state.item
            if (lyric != null) {
                item(key = "lyric-text") {
                    SelectionContainer {
                        Text(lyric.lines.joinToString("\n") { it.text }, Modifier.testTag("lyric-text"),
                            fontSize = reading.fontSizeSp.sp, lineHeight = reading.lineHeightSp().sp)
                    }
                }
            }
        } else if (state.prepared != null) {
            item {
                OutlinedTextField(value = state.query, onValueChange = query, singleLine = true,
                    label = { Text(stringResource(R.string.lyric_title_filter)) },
                    modifier = Modifier.fillMaxWidth().testTag("lyric-filter"))
                if (state.visible.isEmpty() && !state.failed && !state.queryFailed) Text(stringResource(R.string.no_results))
                Text(stringResource(R.string.result_count).replace("{shown}", shown.toString()).replace("{total}", state.visible.size.toString()),
                    modifier = Modifier.testTag("lyric-count"))
            }
            items(state.visible.take(shown), key = { it.lyricId }) { summary ->
                OutlinedButton(onClick = { select(summary) }, modifier = Modifier.fillMaxWidth()
                    .testTag("lyric-title-${summary.lyricId}")
                    .then(if (summary.lyricId == state.browseFocusId) Modifier.focusProperties { canFocus = true }.focusRequester(focus)
                        .onGloballyPositioned { focusReady = true } else Modifier)) { Text(summary.title) }
            }
            if (shown < state.visible.size) item {
                OutlinedButton(onClick = more, modifier = Modifier.testTag("lyric-more")) {
                    Text(stringResource(R.string.show_more_results))
                }
            }
        }
    }
}
