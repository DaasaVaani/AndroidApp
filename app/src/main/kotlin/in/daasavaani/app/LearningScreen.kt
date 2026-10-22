package `in`.daasavaani.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.daasavaani.core.*

private fun learningCaption(label: String?) = when (label) {
    "wantToLearn" -> R.string.learning_want_to_learn
    "practising" -> R.string.learning_practising
    "comfortableReciting" -> R.string.learning_comfortable_reciting
    else -> R.string.learning_none
}

@Composable
fun LearningControls(reference: PersonalReference, personal: PersonalSnapshot?, canSave: Boolean,
    change: (PersonalReference, String?) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    var expanded by remember(reference) { mutableStateOf(false) }
    val label = personal?.learning?.find { it.reference == reference }?.label
    val description = stringResource(R.string.learning_label) + ": " + stringResource(learningCaption(label))
    Box(modifier) {
        OutlinedButton(enabled = canSave, onClick = { expanded = true },
            modifier = Modifier.testTag("learning-label").semantics { contentDescription = description }) {
            Text(if (compact) stringResource(learningCaption(label)) else description,
                maxLines = if (compact) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(stringResource(R.string.learning_notice), Modifier.padding(integerResource(R.integer.spacing_dp).dp),
                style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.learning_practice_unavailable), Modifier.padding(integerResource(R.integer.spacing_dp).dp),
                style = MaterialTheme.typography.bodySmall)
            (listOf<String?>(null) + LearningLabels.labels).forEach { value ->
                DropdownMenuItem(text = { Text(stringResource(learningCaption(value))) },
                    onClick = { expanded = false; change(reference, value) },
                    modifier = Modifier.testTag("learning-set-${value ?: "none"}"))
            }
        }
    }
}

@Composable
fun LearningScreen(personal: PersonalSnapshot?, canSave: Boolean, fixture: PreparedPublication?,
    lyrics: LyricPreviewState, filter: String?, pages: Int, scroll: LazyListState,
    setFilter: (String?) -> Unit, more: () -> Unit, open: (PersonalReference) -> Unit,
    change: (PersonalReference, String?) -> Unit) {
    val space = integerResource(R.integer.spacing_dp).dp
    val entries = LearningLabels.filtered(personal?.learning.orEmpty(), filter)
    val shown = expandedResultCount(entries.size, integerResource(R.integer.trusted_max_page_size), pages)
    // Resolve titles once per available catalog, never by fetching or scanning lyric bodies.
    val canonicalTitles = remember(lyrics.prepared) { lyrics.prepared?.catalog?.catalog.orEmpty().associate { it.lyricId to it.title } }
    val fixtures = remember(fixture) { fixture?.items.orEmpty().associateBy { it.videoId } }
    LazyColumn(Modifier.fillMaxSize().testTag("learning-entries"), state = scroll,
        verticalArrangement = Arrangement.spacedBy(space)) {
        item {
            Text(stringResource(R.string.learning), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.learning_notice), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.learning_practice_unavailable), style = MaterialTheme.typography.bodySmall)
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag("learning-filter")) {
                    Text(stringResource(R.string.learning_filter) + ": " +
                        stringResource(if (filter == null) R.string.learning_all else learningCaption(filter)))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    (listOf<String?>(null) + LearningLabels.labels).forEach { value ->
                        DropdownMenuItem(text = { Text(stringResource(if (value == null) R.string.learning_all else learningCaption(value))) },
                            onClick = { expanded = false; setFilter(value) },
                            modifier = Modifier.testTag("learning-filter-${value ?: "all"}"))
                    }
                }
            }
            if (entries.isEmpty()) Text(stringResource(if (personal?.learning?.isEmpty() == true) R.string.empty_learning else R.string.no_results))
            Text(stringResource(R.string.result_count).replace("{shown}", shown.toString()).replace("{total}", entries.size.toString()),
                modifier = Modifier.testTag("learning-count"))
        }
        items(entries.take(shown), key = { it.reference.kind + ":" + it.reference.id }) { assignment ->
            val reference = assignment.reference
            Card(Modifier.fillMaxWidth().testTag("learning-row-${reference.kind}-${reference.id}")) {
                Column(Modifier.padding(space)) {
                    val title = if (reference.kind == "lyric") canonicalTitles[reference.id] else fixtures[reference.id]?.title
                    Text(title ?: reference.id)
                    if (reference.kind == "legacyVideo") Text(stringResource(R.string.legacy_reference))
                    if (title == null) Text(stringResource(if (reference.kind == "lyric") {
                        if (lyrics.prepared == null) R.string.lyric_catalog_pending else R.string.lyrics_unavailable
                    } else if (fixture == null) R.string.preparation_notice else R.string.unavailable_favorite))
                    TextButton(onClick = { open(reference) }, modifier = Modifier.testTag("learning-read-${reference.kind}-${reference.id}")) {
                        Text(stringResource(R.string.open))
                    }
                    LearningControls(reference, personal, canSave, change)
                }
            }
        }
        if (shown < entries.size) item {
            TextButton(onClick = more, modifier = Modifier.testTag("learning-more")) { Text(stringResource(R.string.show_more_results)) }
        }
    }
}
