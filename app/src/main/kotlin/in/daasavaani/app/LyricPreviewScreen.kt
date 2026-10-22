package `in`.daasavaani.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.daasavaani.core.LyricSummary
import `in`.daasavaani.core.PersonalReference

@Composable
fun LyricPreviewScreen(model: LyricPreviewViewModel = viewModel(), personal: PersonalSnapshot? = null,
    canSave: Boolean = false, favorite: (PersonalReference) -> Unit = {}, add: (PersonalReference) -> Unit = {},
    backToList: (() -> Unit)? = null) {
    val state by model.state.collectAsState()
    BackHandler(enabled = state.selected != null) { backToList?.invoke() ?: model.select(null) }
    DisposableEffect(model) { onDispose { model.cancel() } }
    LyricPreviewContent(state, model::load, model::cancel, model::query, model::select) { summary ->
        val reference = PersonalReference.lyric(summary.lyricId)
        OutlinedButton(enabled = canSave, onClick = { favorite(reference) }) {
            Text(stringResource(if (reference in personal?.favorites.orEmpty()) R.string.remove_favorite else R.string.add_favorite))
        }
        OutlinedButton(enabled = canSave, onClick = { add(reference) }) { Text(stringResource(R.string.add_to_list)) }
    }
}

@Composable
fun LyricPreviewContent(
    state: LyricPreviewState, load: () -> Unit, cancel: () -> Unit,
    query: (String) -> Unit, select: (LyricSummary?) -> Unit,
    personalActions: @Composable (LyricSummary) -> Unit = {},
) {
    val space = integerResource(R.integer.spacing_dp).dp
    LazyColumn(Modifier.fillMaxSize().testTag("lyric-preview"), verticalArrangement = Arrangement.spacedBy(space)) {
        item {
            Text(stringResource(R.string.lyric_preview_notice), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.lyric_personal_limits), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.lyric_no_media), style = MaterialTheme.typography.bodySmall)
        }
        item {
            if (state.selected != null) TextButton(onClick = { select(null) }) { Text(stringResource(R.string.lyric_back)) }
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
        val selected = state.selected
        if (selected != null) {
            item {
                Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                personalActions(selected)
            }
            val lyric = state.item
            if (lyric != null) {
                item {
                    SelectionContainer {
                        Text(lyric.lines.joinToString("\n") { it.text }, Modifier.testTag("lyric-text"),
                            fontSize = integerResource(R.integer.reader_font_size_sp).sp,
                            lineHeight = integerResource(R.integer.reader_line_height_sp).sp)
                    }
                }
            }
        } else if (state.prepared != null) {
            item {
                OutlinedTextField(value = state.query, onValueChange = query, singleLine = true,
                    label = { Text(stringResource(R.string.lyric_title_filter)) },
                    modifier = Modifier.fillMaxWidth().testTag("lyric-filter"))
                if (state.visible.isEmpty()) Text(stringResource(R.string.no_results))
            }
            items(state.visible, key = { it.lyricId }) { summary ->
                OutlinedButton(onClick = { select(summary) }, modifier = Modifier.fillMaxWidth()) { Text(summary.title) }
            }
        }
    }
}
