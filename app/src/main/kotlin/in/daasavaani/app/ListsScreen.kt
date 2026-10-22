package `in`.daasavaani.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.daasavaani.core.*
import java.util.UUID

private data class ListDraft(val base: PersonalSnapshot, val list: RecitationList, val newOnly: Boolean)

@Composable
fun ListsScreen(model: LibraryViewModel, lyrics: LyricPreviewState, open: (PersonalReference) -> Unit,
    export: (ListSnapshot) -> Unit, scrollState: LazyListState = rememberLazyListState(),
    recite: ReciteState = remember { ReciteState() }) {
    val state by model.state.collectAsState()
    val personal = state.personal
    val enabled = personal != null && state.personalError == null && !state.saving
    val selectedId = state.selectedListId
    val selected = personal?.lists?.find { it.id == selectedId }
    var draft by remember { mutableStateOf<ListDraft?>(null) }
    var deleting by remember { mutableStateOf<ListDraft?>(null) }
    var editing by remember { mutableStateOf<Pair<PersonalSnapshot, Pair<RecitationList, Occurrence?>>?>(null) }
    var session by remember { mutableStateOf<OrderedRecitation?>(null) }
    var stale by remember { mutableStateOf(false) }
    val currentSession = session
    val validSession = currentSession?.valid(selected) == true && state.personalError == null
    fun verify(active: OrderedRecitation, accept: () -> Unit) {
        model.checkRecitationList(active.list) { valid ->
            if (session == active && recite.active) {
                if (valid) accept() else {
                    recite.exit()
                    session = null
                    stale = true
                }
            }
        }
    }
    LaunchedEffect(recite.active, validSession) {
        if (recite.active && currentSession != null && !validSession) {
            recite.exit()
            session = null
            stale = true
        }
    }
    val space = integerResource(R.integer.spacing_dp).dp
    if (recite.active && currentSession != null && validSession) {
        ListReciteScreen(currentSession, recite, state, lyrics, model,
            previous = { verify(currentSession) { session = currentSession.previous() } },
            next = { verify(currentSession) { session = currentSession.next() } },
            validate = { verify(currentSession) {} })
    } else LazyColumn(Modifier.fillMaxSize().testTag("saved-lists"), state = scrollState, verticalArrangement = Arrangement.spacedBy(space)) {
        item {
            Text(stringResource(R.string.lists), style = MaterialTheme.typography.titleLarge)
            if (stale) Text(stringResource(R.string.personal_stale), Modifier.testTag("list-recite-stale"),
                color = MaterialTheme.colorScheme.error)
            state.mutationError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (selected == null) {
                OutlinedButton(enabled = enabled, onClick = {
                    model.clearMutationError()
                    draft = ListDraft(requireNotNull(personal), RecitationList.create(""), true)
                }) { Text(stringResource(R.string.create_list)) }
                if (personal?.lists?.isEmpty() == true) Text(stringResource(R.string.empty_lists))
            } else {
                TextButton(onClick = { model.selectList(null) }) { Text(stringResource(R.string.back)) }
                Text(selected.name, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(enabled = enabled && (state.prepared != null || lyrics.prepared != null), onClick = {
                    model.checkRecitationList(selected) { valid ->
                        if (valid) {
                            session = OrderedRecitation(selected)
                            stale = false
                            recite.controls = false
                            recite.active = true
                        } else stale = true
                    }
                }, modifier = Modifier.testTag("recite-list")) { Text(stringResource(R.string.recite_list)) }
                OutlinedButton(enabled = enabled, onClick = {
                    model.clearMutationError(); draft = ListDraft(requireNotNull(personal), selected, false)
                }) { Text(stringResource(R.string.rename_list)) }
                OutlinedButton(enabled = enabled, onClick = {
                    model.clearMutationError(); draft = ListDraft(requireNotNull(personal), selected.duplicate(), true)
                }) { Text(stringResource(R.string.duplicate_list)) }
                OutlinedButton(enabled = enabled, onClick = { deleting = ListDraft(requireNotNull(personal), selected, false) }) {
                    Text(stringResource(R.string.delete_list))
                }
                OutlinedButton(enabled = enabled, onClick = { export(selected.transfer()) }) { Text(stringResource(R.string.transfer_download)) }
                OutlinedButton(enabled = enabled, onClick = {
                    model.clearMutationError(); editing = requireNotNull(personal) to (selected to null)
                }) { Text(stringResource(R.string.add_occurrence)) }
                if (selected.entries.isEmpty()) Text(stringResource(R.string.transfer_empty))
            }
        }
        if (selected == null) itemsIndexed(personal?.lists.orEmpty(), key = { _, list -> list.id }) { _, list ->
            OutlinedButton(onClick = { model.selectList(list.id) }, modifier = Modifier.fillMaxWidth()) { Text(list.name) }
        } else itemsIndexed(selected.entries, key = { _, entry -> entry.id }) { index, occurrence ->
            Card(Modifier.fillMaxWidth().testTag("occurrence-$index")) {
                Column(Modifier.padding(space)) {
                    Text("${index + 1} / ${selected.entries.size}", style = MaterialTheme.typography.labelMedium)
                    ReferenceContent(occurrence.entry, lyrics, open)
                    TextButton(enabled = enabled, onClick = {
                        model.clearMutationError(); editing = requireNotNull(personal) to (selected to occurrence)
                    }) { Text(stringResource(R.string.edit_entry)) }
                    Row {
                        TextButton(enabled = enabled && index > 0, onClick = {
                            val next = selected.entries.toMutableList()
                            next[index] = next[index - 1].also { next[index - 1] = next[index] }
                            model.saveList(requireNotNull(personal), selected.copy(entries = next))
                        }) { Text(stringResource(R.string.move_up)) }
                        TextButton(enabled = enabled && index < selected.entries.lastIndex, onClick = {
                            val next = selected.entries.toMutableList()
                            next[index] = next[index + 1].also { next[index + 1] = next[index] }
                            model.saveList(requireNotNull(personal), selected.copy(entries = next))
                        }) { Text(stringResource(R.string.move_down)) }
                    }
                    TextButton(enabled = enabled, onClick = {
                        model.saveList(requireNotNull(personal), selected.copy(entries = selected.entries.filterNot { it.id == occurrence.id }))
                    }) { Text(stringResource(R.string.remove_entry)) }
                }
            }
        }
    }
    draft?.let { value ->
        ListNameDialog(value.base, value.list, state.saving, state.mutationError, { draft = null }) { list, duplicate ->
            model.saveList(value.base, list, duplicate, value.newOnly) { model.selectList(list.id); draft = null }
        }
    }
    deleting?.let { value ->
        AlertDialog(onDismissRequest = { if (!state.saving) deleting = null }, title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Column { Text(stringResource(R.string.delete_notice)); state.mutationError?.let { Text(stringResource(it)) } } },
            confirmButton = { TextButton(enabled = !state.saving, onClick = {
                model.deleteList(value.base, value.list.id) { deleting = null; model.selectList(null) }
            }) { Text(stringResource(R.string.delete_list)) } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } })
    }
    editing?.let { (base, pair) ->
        val (list, occurrence) = pair
        EntryDialog(occurrence?.entry, state.saving, state.mutationError, { editing = null }) { entry ->
            val entries = list.entries.toMutableList()
            if (occurrence == null) entries.add(Occurrence(UUID.randomUUID().toString(), entry))
            else entries[entries.indexOfFirst { it.id == occurrence.id }] = occurrence.copy(entry = entry)
            model.saveList(base, list.copy(entries = entries)) { editing = null }
        }
    }
}

@Composable
fun ReferenceContent(entry: ListEntry, lyrics: LyricPreviewState, open: (PersonalReference) -> Unit) {
    val reference = entry.reference()
    if (reference == null) {
        Text(stringResource(R.string.transfer_placeholder), style = MaterialTheme.typography.labelMedium)
        Text(entry.text.orEmpty())
        Text(stringResource(R.string.lyrics_unavailable))
    } else {
        val summary = if (reference.kind == "lyric") lyrics.prepared?.catalog?.catalog?.find { it.lyricId == reference.id } else null
        Text(stringResource(if (reference.kind == "lyric") R.string.lyric_reference else R.string.legacy_reference),
            style = MaterialTheme.typography.labelMedium)
        Text(summary?.title ?: reference.id)
        entry.fallback?.let { Text(it) }
        if (reference.kind == "lyric") TextButton(onClick = { open(reference) }) { Text(stringResource(R.string.open)) }
        if (summary == null) Text(stringResource(if (lyrics.prepared == null)
            if (reference.kind == "legacyVideo") R.string.reference_pending else R.string.lyric_catalog_pending
            else R.string.lyrics_unavailable))
    }
}

@Composable
private fun ListNameDialog(base: PersonalSnapshot, list: RecitationList, working: Boolean, error: Int?,
    dismiss: () -> Unit, save: (RecitationList, Boolean) -> Unit) {
    var name by remember(list.id) { mutableStateOf(list.name) }
    var confirming by remember(name) { mutableStateOf(false) }
    val nameLimit = integerResource(R.integer.max_list_name_code_points)
    val existingKeys = remember(base, list.id) { base.lists.filter { it.id != list.id }.map { PersonalNames.key(it.name) }.toSet() }
    val key = remember(name) { if (name.length > nameLimit * 2) null else runCatching { PersonalNames.key(name) }.getOrNull() }
    val duplicate = key != null && key in existingKeys
    AlertDialog(onDismissRequest = { if (!working) dismiss() }, title = { Text(stringResource(R.string.list_name)) },
        text = { Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !working, singleLine = true,
                label = { Text(stringResource(R.string.list_name)) }, modifier = Modifier.testTag("list-name-input"))
            Text(stringResource(R.string.list_name_hint).replace("{limit}", integerResource(R.integer.max_list_name_code_points).toString()))
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            if (confirming) Text(stringResource(R.string.duplicate_name))
        } },
        confirmButton = { TextButton(enabled = !working, onClick = {
            if (duplicate && !confirming) confirming = true else save(list.copy(name = name), duplicate && confirming)
        }) { Text(stringResource(if (confirming) R.string.confirm_duplicate else R.string.save_list)) } },
        dismissButton = { TextButton(enabled = !working, onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun EntryDialog(original: ListEntry?, working: Boolean, error: Int?, dismiss: () -> Unit, save: (ListEntry) -> Unit) {
    var kind by remember { mutableStateOf(original?.kind ?: "placeholder") }
    var identifier by remember { mutableStateOf(original?.lyricId ?: original?.videoId ?: "") }
    var text by remember { mutableStateOf(original?.text ?: "") }
    var fallback by remember { mutableStateOf(original?.fallback ?: "") }
    AlertDialog(onDismissRequest = { if (!working) dismiss() }, title = { Text(stringResource(R.string.edit_entry)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            listOf("placeholder" to R.string.transfer_placeholder, "lyric" to R.string.lyric_reference,
                "legacyVideo" to R.string.legacy_reference).forEach { (value, label) ->
                Row {
                    RadioButton(selected = kind == value, enabled = !working, onClick = { if (kind != value) { kind = value; identifier = "" } })
                    TextButton(enabled = !working, onClick = { if (kind != value) { kind = value; identifier = "" } }) { Text(stringResource(label)) }
                }
            }
            if (kind == "placeholder") OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
                enabled = !working, label = { Text(stringResource(R.string.placeholder_text)) }, modifier = Modifier.testTag("entry-text"))
            else {
                OutlinedTextField(value = identifier, onValueChange = { identifier = it }, singleLine = true, enabled = !working,
                    label = { Text(stringResource(if (kind == "lyric") R.string.lyric_identifier else R.string.legacy_reference)) },
                    modifier = Modifier.testTag("entry-id"))
                OutlinedTextField(value = fallback, onValueChange = { fallback = it }, singleLine = true, enabled = !working,
                    label = { Text(stringResource(R.string.fallback_label)) }, modifier = Modifier.testTag("entry-fallback"))
            }
            Text(stringResource(R.string.entry_text_hint).replace("{limit}", integerResource(R.integer.max_entry_text_code_points).toString()))
            error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !working, onClick = {
            save(if (kind == "placeholder") ListEntry(kind, text = text) else PersonalReference(kind, identifier).entry(fallback.ifEmpty { null }))
        }) { Text(stringResource(R.string.save_entry)) } },
        dismissButton = { TextButton(enabled = !working, onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun AddToListDialog(model: LibraryViewModel, reference: PersonalReference, dismiss: () -> Unit) {
    val state by model.state.collectAsState()
    val base = remember { state.personal }
    AlertDialog(onDismissRequest = { if (!state.saving) dismiss() }, title = { Text(stringResource(R.string.choose_list)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            if (base?.lists.isNullOrEmpty()) Text(stringResource(R.string.no_lists_for_item))
            state.mutationError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            base?.lists?.forEach { list ->
                TextButton(enabled = !state.saving, onClick = {
                    model.saveList(base, list.copy(entries = list.entries + Occurrence(UUID.randomUUID().toString(), reference.entry())), done = dismiss)
                }) { Text(list.name) }
            }
        } },
        confirmButton = { TextButton(enabled = !state.saving, onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun ImportListDialog(model: LibraryViewModel, snapshot: ListSnapshot, dismiss: () -> Unit, saved: () -> Unit = dismiss) {
    val state by model.state.collectAsState()
    val base = remember { state.personal }
    var name by remember { mutableStateOf(snapshot.name) }
    var duplicateConfirmed by remember(name) { mutableStateOf(false) }
    val nameLimit = integerResource(R.integer.max_list_name_code_points)
    val existingKeys = remember(base) { base?.lists.orEmpty().map { PersonalNames.key(it.name) }.toSet() }
    val key = remember(name) { if (name.length > nameLimit * 2) null else runCatching { PersonalNames.key(name) }.getOrNull() }
    val duplicate = key != null && key in existingKeys
    AlertDialog(onDismissRequest = { if (!state.saving) dismiss() }, title = { Text(stringResource(R.string.save_list)) },
        text = { Column {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, enabled = !state.saving,
                label = { Text(stringResource(R.string.list_name)) }, modifier = Modifier.testTag("import-name"))
            Text(stringResource(R.string.transfer_notice))
            if (duplicate) Text(stringResource(R.string.duplicate_name))
            state.mutationError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = base != null && !state.saving && state.personalError == null, onClick = {
            if (duplicate && !duplicateConfirmed) duplicateConfirmed = true
            else model.importList(requireNotNull(base), snapshot.copy(name = name), duplicateConfirmed, saved)
        }) { Text(stringResource(if (duplicateConfirmed) R.string.confirm_duplicate else R.string.save_list)) } },
        dismissButton = { TextButton(enabled = !state.saving, onClick = dismiss) { Text(stringResource(R.string.cancel)) } })
}
