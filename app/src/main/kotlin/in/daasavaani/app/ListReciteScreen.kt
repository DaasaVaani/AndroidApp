package `in`.daasavaani.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `in`.daasavaani.core.*
import kotlinx.coroutines.flow.first

@Composable
internal fun ListReciteScreen(session: OrderedRecitation, recite: ReciteState, state: LibraryState,
    lyrics: LyricPreviewState, model: LibraryViewModel, previous: () -> Unit, next: () -> Unit, validate: () -> Unit) {
    val occurrence = session.current
    val entry = occurrence?.entry
    val reference = entry?.reference()
    val canonical = if (reference?.kind == "lyric") lyrics.prepared?.items?.get(reference.id) else null
    val fixture = if (reference?.kind == "legacyVideo")
        state.prepared?.items?.find { it.videoId == reference.id } else null
    val reading = state.personal?.reading ?: ReadingPreferences()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val check by rememberUpdatedState(validate)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) check() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(model) {
        val observer = model.observeRecitationLists { check() }
        onDispose { observer.cancel() }
    }
    Column(Modifier.fillMaxSize().testTag("list-recite")) {
        ReciteControls(recite, true, notice = R.string.list_recite_notice)
        Text(session.list.name)
        if (occurrence == null) Text(stringResource(R.string.empty_recitation), Modifier.testTag("list-recite-empty"))
        else {
            Text(stringResource(R.string.entry_number).replace("{position}", (session.position + 1).toString())
                .replace("{total}", session.list.entries.size.toString()), Modifier.testTag("list-recite-position"))
            Row {
                OutlinedButton(enabled = !session.first && !state.saving, onClick = previous,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("previous-entry")) {
                    Text(stringResource(R.string.previous_recitation_entry))
                }
                OutlinedButton(enabled = !session.last && !state.saving, onClick = next,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("next-entry")) {
                    Text(stringResource(R.string.next_recitation_entry))
                }
            }
            if (session.first) Text(stringResource(R.string.recitation_start), Modifier.testTag("list-recite-start"))
            if (session.last) Text(stringResource(R.string.recitation_end), Modifier.testTag("list-recite-end"))
        }
        key(occurrence?.id) {
            val scroll = rememberLazyListState()
            LaunchedEffect(Unit) {
                if (canonical != null || fixture != null) {
                    snapshotFlow { scroll.layoutInfo.visibleItemsInfo.any { it.key == "list-lyric" } }.first { it }
                    model.recordExposed(requireNotNull(reference))
                }
            }
            LazyColumn(Modifier.weight(1f).testTag("list-recite-content"), state = scroll) {
                if (recite.controls) item(key = "reading-controls") {
                    ReadingControls(reading, state.personal != null && !state.saving && state.personalError == null,
                        { model.changeReading(direction = it) }, { model.changeReading(density = it) })
                }
                if (entry != null) {
                    item {
                        if (reference == null) {
                            Text(stringResource(R.string.transfer_placeholder))
                            Text(entry.text.orEmpty(), Modifier.testTag("list-recite-placeholder"))
                        } else {
                            Text(stringResource(if (reference.kind == "lyric") R.string.lyric_reference else R.string.legacy_reference))
                            Text(reference.id, Modifier.testTag("list-recite-reference"))
                            entry.fallback?.let { Text(it, Modifier.testTag("list-recite-fallback")) }
                        }
                    }
                    when {
                        canonical != null -> item(key = "list-lyric") {
                            Text(canonical.title)
                            CanonicalLyricText(canonical, reading)
                        }
                        fixture != null -> item(key = "list-lyric") {
                            Text(fixture.title)
                            FixtureLyricText(fixture, reading)
                        }
                        else -> item {
                            Text(stringResource(R.string.lyrics_unavailable), Modifier.testTag("list-recite-unavailable"))
                        }
                    }
                }
            }
        }
    }
}
