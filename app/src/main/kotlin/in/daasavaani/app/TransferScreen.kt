package `in`.daasavaani.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import `in`.daasavaani.core.ListSnapshot
import `in`.daasavaani.core.PersonalReference

@Composable
fun TransferScreen(prepared: PreparedPublication?, model: TransferViewModel = viewModel(),
    personalModel: LibraryViewModel? = null, lyrics: LyricPreviewState = LyricPreviewState(),
    open: (PersonalReference) -> Unit = {}) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val space = integerResource(R.integer.spacing_dp).dp
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it == null) model.cancel() else model.previewFile(it)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) {
        if (it == null) model.cancel() else model.exportFile(it)
    }
    val fileName = stringResource(R.string.list_file_name)
    var importing by remember { mutableStateOf<ListSnapshot?>(null) }
    importing?.let { snapshot -> personalModel?.let { personal ->
        ImportListDialog(personal, snapshot, { importing = null }, { model.feedback(R.string.list_saved); importing = null })
    } }
    LazyColumn(Modifier.fillMaxSize().testTag("transfer-screen"), verticalArrangement = Arrangement.spacedBy(space)) {
        item {
            Text(stringResource(R.string.transfer_title), style = MaterialTheme.typography.titleLarge)
            if (personalModel == null) Text(stringResource(R.string.transfer_preview_only))
            Text(stringResource(R.string.transfer_notice), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.catalog_notice), style = MaterialTheme.typography.bodySmall)
            state.message?.let { Text(stringResource(it), Modifier.testTag("transfer-message"), color = MaterialTheme.colorScheme.primary) }
            OutlinedButton(enabled = !state.working, onClick = {
                try { picker.launch(arrayOf("*/*")) } catch (_: Exception) { model.feedback(R.string.transfer_failed) }
            }) { Text(stringResource(R.string.transfer_file)) }
            OutlinedTextField(value = state.input, onValueChange = model::input,
                enabled = !state.working, singleLine = true, label = { Text(stringResource(R.string.transfer_link)) },
                modifier = Modifier.fillMaxWidth().testTag("transfer-link-input"))
            OutlinedButton(enabled = !state.working, onClick = model::previewLink) { Text(stringResource(R.string.transfer_preview)) }
            if (state.working) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.transfer_working))
                OutlinedButton(onClick = model::cancel, modifier = Modifier.testTag("transfer-cancel")) { Text(stringResource(R.string.cancel)) }
            }
        }
        state.snapshot?.let { snapshot ->
            item {
                Text(snapshot.name, Modifier.testTag("transfer-name"), style = MaterialTheme.typography.titleMedium)
                personalModel?.let { personal ->
                    OutlinedButton(enabled = !state.working, onClick = {
                        personal.clearMutationError(); importing = snapshot
                    }) { Text(stringResource(R.string.save_list)) }
                }
                OutlinedButton(enabled = !state.working, onClick = {
                    try { exporter.launch(fileName) } catch (_: Exception) { model.feedback(R.string.transfer_failed) }
                }) { Text(stringResource(R.string.transfer_download)) }
                OutlinedButton(enabled = !state.working, onClick = model::createLink) { Text(stringResource(R.string.transfer_create_link)) }
                state.link?.let { link ->
                    SelectionContainer { Text(link, Modifier.testTag("transfer-output-link")) }
                    OutlinedButton(onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.transfer_title), link))
                            model.feedback(R.string.list_link_copied)
                        } catch (_: Exception) { model.feedback(R.string.list_share_failed) }
                    }) { Text(stringResource(R.string.copy_list_link)) }
                }
                if (snapshot.entries.isEmpty()) Text(stringResource(R.string.transfer_empty))
            }
            itemsIndexed(snapshot.entries) { index, entry ->
                Card(Modifier.fillMaxWidth().testTag("transfer-entry-$index")) {
                    Column(Modifier.padding(space)) {
                        Text(stringResource(if (entry.kind == "placeholder") R.string.transfer_placeholder else R.string.transfer_reference),
                            style = MaterialTheme.typography.labelMedium)
                        if (entry.kind == "lyric") ReferenceContent(entry, lyrics, open) else if (entry.kind == "placeholder") {
                            Text(requireNotNull(entry.text))
                            Text(stringResource(R.string.lyrics_unavailable))
                        } else {
                            Text(requireNotNull(entry.videoId))
                            val known = prepared?.items?.find { it.videoId == entry.videoId }
                            known?.let { Text(it.title); Text(stringResource(R.string.fixture_notice), style = MaterialTheme.typography.bodySmall) }
                            entry.fallback?.let { Text(it) }
                            Text(stringResource(if (prepared == null) R.string.reference_pending else if (known != null)
                                R.string.reference_known else R.string.lyrics_unavailable))
                        }
                    }
                }
            }
        }
    }
}
