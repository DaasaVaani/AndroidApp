package `in`.daasavaani.app

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.daasavaani.core.PersonalReference

internal fun systemBackupRegistered(info: ApplicationInfo): Boolean =
    info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0 &&
        info.backupAgentName == PersonalBackupAgent::class.java.name

@Composable
fun BackupScreen(model: BackupViewModel, library: LibraryViewModel, back: () -> Unit) {
    val state by model.state.collectAsState()
    val systemBackupEnabled = systemBackupRegistered(LocalContext.current.applicationInfo)
    var confirming by remember { mutableStateOf(false) }
    val fileName = stringResource(R.string.backup_file_name)
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::previewFile) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { it?.let(model::exportFile) }
    DisposableEffect(model) { onDispose { model.leave() } }
    BackHandler { model.leave(); back() }
    val space = integerResource(R.integer.spacing_dp).dp
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text(stringResource(R.string.backup_replace)) },
        text = { Text(stringResource(R.string.backup_replacement_notice)) },
        confirmButton = { TextButton(enabled = !state.working && state.preview != null, modifier = Modifier.testTag("backup-confirm"),
            onClick = { confirming = false; model.confirmReplacement { library.refreshAfterPersonalRestore() } }) {
            Text(stringResource(R.string.backup_replace))
        } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
    )
    LazyColumn(Modifier.fillMaxSize().testTag("backup-screen"), verticalArrangement = Arrangement.spacedBy(space)) {
        item {
            TextButton(onClick = { model.leave(); back() }) { Text(stringResource(R.string.settings)) }
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.backup_privacy))
            Text(stringResource(R.string.backup_same_platform))
            Text(stringResource(R.string.backup_replacement_notice))
            Text(stringResource(R.string.backup_offer_export))
            OutlinedButton(enabled = !state.working, modifier = Modifier.testTag("backup-export"),
                onClick = { export.launch(fileName) }) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(enabled = !state.working, modifier = Modifier.testTag("backup-choose"),
                onClick = { choose.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.backup_choose)) }
            if (state.working) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!state.recoveringLanguage) OutlinedButton(onClick = model::cancel, modifier = Modifier.testTag("backup-cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            }
            state.message?.let { Text(stringResource(it), Modifier.testTag("backup-message")) }
            if (state.languageRetry != null || state.message == R.string.personal_failed) OutlinedButton(enabled = !state.working,
                onClick = model::retryLanguage, modifier = Modifier.testTag("backup-retry-language")) {
                Text(stringResource(if (state.languageRetry != null) R.string.backup_retry_language else R.string.retry))
            }
        }
        if (systemBackupEnabled) item {
            Text(stringResource(R.string.system_backup_notice), Modifier.testTag("backup-system-privacy"))
            Text(stringResource(R.string.system_backup_manual_notice), Modifier.testTag("backup-system-manual"))
        }
        state.preview?.backup?.let { backup ->
            val data = backup.data
            item {
                Text(stringResource(R.string.backup_preview))
                Text("${stringResource(R.string.backup_platform)}: android")
                Text("${stringResource(R.string.backup_exported_at)}: ${backup.exportedAt}")
                val setupCaption = when (data.setupStatus) {
                    "pending" -> R.string.first_use_pending
                    "completed" -> R.string.first_use_completed
                    "skipped" -> R.string.first_use_skipped_status
                    else -> error("Invalid backup setup status")
                }
                Text("${stringResource(R.string.first_use_status)}: ${stringResource(setupCaption)}",
                    Modifier.testTag("backup-setup-status"))
                Text(stringResource(R.string.backup_counts).replace("{favorites}", data.favorites.size.toString())
                    .replace("{lists}", data.lists.size.toString()).replace("{occurrences}", data.lists.sumOf { it.entries.size }.toString())
                    .replace("{recents}", data.recentHistory.entries.size.toString()).replace("{learning}", data.learning.size.toString()),
                    Modifier.testTag("backup-counts"))
                if (state.preview?.duplicateNameGroups?.isNotEmpty() == true) Text(stringResource(R.string.backup_duplicate_names),
                    Modifier.testTag("backup-duplicate-warning"))
                Text("${stringResource(R.string.language)}: ${data.preferences.language}")
                Text("${stringResource(R.string.appearance)}: ${data.preferences.theme}")
                Text("${stringResource(R.string.lyric_font_size)}: ${data.preferences.lyricFontSizeSp} sp")
                Text("${stringResource(R.string.reading_density)}: ${data.preferences.readingDensity}")
                Button(enabled = !state.working, onClick = { confirming = true }, modifier = Modifier.testTag("backup-replace")) {
                    Text(stringResource(R.string.backup_replace))
                }
            }
            item { Text(stringResource(R.string.favorites), style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(data.favorites, key = { index, _ -> "favorite-$index" }) { index, ref ->
                BackupReference(ref, Modifier.testTag("backup-favorite-$index"))
            }
            item { Text(stringResource(R.string.lists), style = MaterialTheme.typography.titleMedium) }
            data.lists.forEach { list ->
                item(key = "list-${list.id}") {
                    Text(list.name, Modifier.testTag("backup-name-${list.id}"))
                    Text("${stringResource(R.string.backup_list_id)}: ${list.id}")
                }
                itemsIndexed(list.entries, key = { _, occurrence -> "entry-${occurrence.id}" }) { _, occurrence ->
                    Column(Modifier.testTag("backup-entry-${occurrence.id}")) {
                        Text("${stringResource(R.string.backup_occurrence_id)}: ${occurrence.id}")
                        val ref = occurrence.entry
                        if (ref.kind == "placeholder") {
                            Text(stringResource(R.string.placeholder_text)); Text(requireNotNull(ref.text))
                        } else {
                            BackupReference(PersonalReference(ref.kind, ref.lyricId ?: requireNotNull(ref.videoId)))
                            ref.fallback?.let { Text(stringResource(R.string.backup_fallback)); Text(it) }
                        }
                    }
                }
            }
            item {
                Row {
                    Checkbox(checked = data.recentHistory.enabled, onCheckedChange = null)
                    Text(stringResource(R.string.recent_history_enabled))
                }
            }
            itemsIndexed(data.recentHistory.entries, key = { index, _ -> "recent-$index" }) { index, ref ->
                BackupReference(ref, Modifier.testTag("backup-recent-$index"))
            }
            item { Text(stringResource(R.string.learning), style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(data.learning, key = { index, _ -> "learning-$index" }) { index, assignment ->
                Column(Modifier.testTag("backup-learning-$index")) {
                    BackupReference(assignment.reference)
                    Text("${stringResource(R.string.learning_label)}: ${assignment.label}")
                }
            }
            item {
                Text(stringResource(R.string.backup_position))
                val pair = data.recitation.position
                if (pair == null) Text(stringResource(R.string.backup_no_position))
                else {
                    Text("${stringResource(R.string.backup_list_id)}: ${pair.listId}", Modifier.testTag("backup-position-list"))
                    Text("${stringResource(R.string.backup_occurrence_id)}: ${pair.entryId}", Modifier.testTag("backup-position-entry"))
                }
            }
        }
    }
}

@Composable
private fun BackupReference(reference: PersonalReference, modifier: Modifier = Modifier) {
    Text("${stringResource(R.string.backup_reference)}: ${reference.kind}: ${reference.id}", modifier)
}
