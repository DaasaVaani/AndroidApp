package `in`.daasavaani.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import `in`.daasavaani.core.FixtureItem
import `in`.daasavaani.core.PersonalReference

@Composable
fun RecentHistoryControls(personal: PersonalSnapshot?, canSave: Boolean,
    enabled: (Boolean) -> Unit, clear: () -> Unit) {
    Text(stringResource(R.string.recent_history), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.recent_history_notice), style = MaterialTheme.typography.bodySmall)
    val label = stringResource(R.string.recent_history_enabled)
    Row {
        Switch(checked = personal?.recentHistory?.enabled == true, onCheckedChange = enabled,
            enabled = canSave, modifier = Modifier.testTag("history-enabled").semantics { contentDescription = label })
        Text(label)
    }
    OutlinedButton(onClick = clear, enabled = canSave, modifier = Modifier.testTag("history-clear")) {
        Text(stringResource(R.string.clear_recent_history))
    }
}

@Composable
fun RecentHistoryScreen(personal: PersonalSnapshot?, canSave: Boolean, fixture: PreparedPublication?,
    lyrics: LyricPreviewState, open: (PersonalReference) -> Unit, openFixture: (FixtureItem) -> Unit,
    enabled: (Boolean) -> Unit, clear: () -> Unit) {
    val space = integerResource(R.integer.spacing_dp).dp
    RecentHistoryControls(personal, canSave, enabled, clear)
    if (personal?.recentHistory?.entries?.isEmpty() == true) Text(stringResource(R.string.empty_recent_history))
    LazyColumn(Modifier.fillMaxSize().testTag("history-entries"), verticalArrangement = Arrangement.spacedBy(space)) {
        items(personal?.recentHistory?.entries.orEmpty(), key = { it.kind + ":" + it.id }) { reference ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(space)) {
                    if (reference.kind == "lyric") {
                        val summary = lyrics.prepared?.catalog?.catalog?.find { it.lyricId == reference.id }
                        Text(summary?.title ?: reference.id)
                        if (summary != null && lyrics.prepared?.items?.containsKey(reference.id) == true) {
                            TextButton(onClick = { open(reference) }) { Text(stringResource(R.string.open)) }
                        } else Text(stringResource(if (lyrics.prepared == null) R.string.lyric_catalog_pending else R.string.lyrics_unavailable))
                    } else {
                        val item = fixture?.items?.find { it.videoId == reference.id }
                        Text(item?.title ?: reference.id)
                        Text(stringResource(R.string.legacy_reference))
                        if (item != null) {
                            Text(stringResource(R.string.fixture_notice), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { openFixture(item) }) { Text(stringResource(R.string.open)) }
                        } else Text(stringResource(if (fixture == null) R.string.preparation_notice else R.string.unavailable_favorite))
                    }
                }
            }
        }
    }
}
