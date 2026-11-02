package `in`.daasavaani.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.daasavaani.core.FixtureItem
import `in`.daasavaani.core.search
import `in`.daasavaani.core.PersonalReference
import `in`.daasavaani.core.RecitationList
import `in`.daasavaani.core.ListSnapshot
import `in`.daasavaani.core.PersonalPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LibraryState(
    val prepared: PreparedPublication? = null, val loading: Boolean = false,
    val completed: Int = 0, val total: Int = 0, val error: String? = null,
    val query: String = "", val visible: List<FixtureItem> = emptyList(), val selected: FixtureItem? = null,
    val personal: PersonalSnapshot? = null, val personalError: Int? = null,
    val saving: Boolean = false, val section: String = "library",
    val mutationError: Int? = null,
    val selectedListId: String? = null,
    val readerOpen: String? = null, val historyError: Int? = null,
    val learningError: Int? = null,
    val setupError: Int? = null,
    val rememberingRecitation: Boolean = false,
    val pendingFixture: String? = null, val fixtureReturnSection: String? = null,
    val personalRestoreGeneration: Long = 0,
)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PublicationRepository(application.resources)
    private val personalStore = PersonalStore(application)
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var preparation: Job? = null
    private var searching: Job? = null
    private val opens = Channel<Pair<PersonalReference, String>>(PersonalPolicy.maxRecents)
    private val readingWrites = Mutex()
    init {
        retryPersonal()
        viewModelScope.launch {
            for ((reference, fence) in opens) {
                val state = mutable.first { !it.saving }
                val before = state.personal
                if (before != null && (before.epoch != fence || !personalStore.isCurrent(before))) continue
                if (before == null || state.personalError != null) {
                    mutable.update { it.copy(historyError = state.personalError ?: R.string.personal_failed) }
                    continue
                }
                mutable.update { it.copy(saving = true, historyError = null) }
                try {
                    val next = withContext(Dispatchers.IO) { personalStore.history(before, opened = reference) }
                    acceptPersonal(next)
                } catch (error: PersonalFailure) { if (personalStore.isCurrent(before)) mutable.update { it.copy(historyError = error.label) } }
                finally { if (personalStore.isCurrent(before)) mutable.update { it.copy(saving = false) } }
            }
        }
    }
    fun section(value: String) { mutable.update { it.copy(section = value, selected = null, readerOpen = null,
        pendingFixture = null, fixtureReturnSection = null) } }
    fun fixtureRequest(reference: PersonalReference) {
        require(reference.kind == "legacyVideo" && reference.valid())
        mutable.update { before ->
            val item = before.prepared?.items?.find { it.videoId == reference.id }
            before.copy(section = "library", selected = item, readerOpen = item?.let { UUID.randomUUID().toString() },
                pendingFixture = reference.id, fixtureReturnSection = before.section)
        }
    }
    fun fixtureBack() {
        val origin = mutable.value.fixtureReturnSection
        if (origin != null) section(origin) else select(null)
    }
    fun resumeFixture(before: LibraryState) {
        if (before.personal?.let { !personalStore.isCurrent(it) } == true) return
        mutable.update {
            val selected = before.selected ?: before.pendingFixture?.let { id -> it.prepared?.items?.find { item -> item.videoId == id } }
            it.copy(section = before.section, selected = selected,
                readerOpen = if (before.selected != null) before.readerOpen else selected?.let { UUID.randomUUID().toString() },
                pendingFixture = before.pendingFixture, fixtureReturnSection = before.fixtureReturnSection)
        }
    }
    fun changeLearning(reference: PersonalReference, label: String?) {
        val before = mutable.value.personal ?: return
        if (mutable.value.saving || mutable.value.personalError != null) return
        mutable.update { it.copy(saving = true, learningError = null) }
        viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.IO) { personalStore.learning(before, reference, label) }
                acceptPersonal(next)
            } catch (error: PersonalFailure) { if (personalStore.isCurrent(before)) mutable.update { it.copy(learningError = error.label) } }
            finally { if (personalStore.isCurrent(before)) mutable.update { it.copy(saving = false) } }
        }
    }
    fun recordExposed(reference: PersonalReference, expectedEpoch: String? = mutable.value.personal?.epoch) {
        val personal = mutable.value.personal ?: return
        if (personal.epoch != expectedEpoch || !personalStore.isCurrent(personal) || !personal.recentHistory.enabled) return
        if (opens.trySend(reference to personal.epoch).isFailure) mutable.update { it.copy(historyError = R.string.personal_failed) }
    }
    fun fixtureExposed(token: String) {
        val state = mutable.value
        if (state.readerOpen != token) return
        val item = state.selected ?: return
        if (state.prepared?.items?.any { it == item } != true) return
        mutable.update { it.copy(readerOpen = null) }
        recordExposed(PersonalReference.legacy(item.videoId))
    }
    fun changeHistory(enabled: Boolean? = null, clear: Boolean = false) {
        val before = mutable.value.personal ?: return
        if (mutable.value.saving || mutable.value.personalError != null) return
        mutable.update { it.copy(saving = true, historyError = null) }
        viewModelScope.launch {
            try {
                val next = withContext(Dispatchers.IO) { personalStore.history(before, enabled = enabled, clear = clear) }
                acceptPersonal(next)
            } catch (error: PersonalFailure) { if (personalStore.isCurrent(before)) mutable.update { it.copy(historyError = error.label) } }
            finally { if (personalStore.isCurrent(before)) mutable.update { it.copy(saving = false) } }
        }
    }
    fun selectList(id: String?) { mutable.update { it.copy(selectedListId = id) } }
    suspend fun rememberRecitation(list: RecitationList, entryId: String,
        isCurrent: () -> Boolean = { true }): Boolean {
        val before = mutable.value.personal ?: return false
        if (mutable.value.saving || mutable.value.personalError != null) return false
        mutable.update { it.copy(saving = true, rememberingRecitation = true, mutationError = null) }
        return try {
            val next = withContext(Dispatchers.IO) {
                val job = currentCoroutineContext()
                personalStore.rememberRecitation(before, list, entryId) {
                    job.ensureActive()
                    if (!isCurrent()) throw CancellationException("Recitation selection departed")
                }
            }
            acceptPersonal(next)
        } catch (error: PersonalFailure) {
            if (personalStore.isCurrent(before)) mutable.update { it.copy(mutationError = error.label) }
            false
        } finally { if (personalStore.isCurrent(before)) mutable.update { it.copy(saving = false, rememberingRecitation = false) } }
    }
    fun observeRecitationLists(changed: () -> Unit): Job = viewModelScope.launch {
        val observerScope = this
        var observer: AutoCloseable? = null
        try {
            withContext(Dispatchers.IO) {
                observer = personalStore.observeLists { observerScope.launch { changed() } }
            }
            changed()
            awaitCancellation()
        } catch (error: PersonalFailure) {
            mutable.update { it.copy(personalError = error.label) }
        } finally { withContext(NonCancellable + Dispatchers.IO) { observer?.close() } }
    }
    fun checkRecitationList(list: RecitationList, done: (Boolean) -> Unit) {
        val generation = mutable.value.personalRestoreGeneration
        viewModelScope.launch {
            mutable.first { !it.saving }
            if (generation != mutable.value.personalRestoreGeneration) return@launch
            mutable.update { it.copy(saving = true) }
            try {
                val current = withContext(Dispatchers.IO) { personalStore.load() }
                val accepted = acceptPersonal(current)
                if (generation == mutable.value.personalRestoreGeneration) done(accepted && mutable.value.section == "lists" && mutable.value.selectedListId == list.id &&
                    current.lists.find { it.id == list.id } == list)
            } catch (error: PersonalFailure) {
                if (generation == mutable.value.personalRestoreGeneration) {
                    mutable.update { it.copy(personalError = error.label) }
                    done(false)
                }
            } finally { if (generation == mutable.value.personalRestoreGeneration) mutable.update { it.copy(saving = false) } }
        }
    }
    fun retryPersonal() {
        if (mutable.value.saving) return
        val generation = mutable.value.personalRestoreGeneration
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) { personalStore.load() }
                if (acceptPersonal(value)) mutable.update { it.copy(personalError = null, mutationError = null, historyError = null, learningError = null, setupError = null) }
            } catch (error: PersonalFailure) { if (generation == mutable.value.personalRestoreGeneration) mutable.update { it.copy(personalError = error.label) } }
            finally { if (generation == mutable.value.personalRestoreGeneration) mutable.update { it.copy(saving = false) } }
        }
    }
    fun changePersonal(language: String? = null, theme: String? = null, favorite: String? = null, reference: PersonalReference? = null) {
        val current = mutable.value.personal ?: return
        if (mutable.value.saving || mutable.value.personalError != null) return
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) {
                    personalStore.change(current, language, theme, favorite, reference)
                }
                acceptPersonal(value)
            } catch (error: PersonalFailure) { if (personalStore.isCurrent(current)) mutable.update { it.copy(personalError = error.label) } }
            finally { if (personalStore.isCurrent(current)) mutable.update { it.copy(saving = false) } }
        }
    }
    fun changeReading(direction: Int? = null, density: String? = null) {
        val requestedFence = mutable.value.personal?.epoch
        // Each queued action uses the last committed local snapshot, never a captured UI value.
        viewModelScope.launch {
            readingWrites.withLock {
                mutable.first { !it.saving }
                val state = mutable.value
                val before = state.personal ?: return@withLock
                if (before.epoch != requestedFence || !personalStore.isCurrent(before)) return@withLock
                if (state.personalError != null) return@withLock
                mutable.update { it.copy(saving = true) }
                try {
                    val next = withContext(Dispatchers.IO) { personalStore.reading(before, direction, density) }
                    acceptPersonal(next)
                } catch (error: PersonalFailure) { if (personalStore.isCurrent(before)) mutable.update { it.copy(personalError = error.label) } }
                finally { if (personalStore.isCurrent(before)) mutable.update { it.copy(saving = false) } }
            }
        }
    }
    fun clearSetupError() { mutable.update { it.copy(setupError = null) } }
    fun finishFirstUse(expected: PersonalSnapshot, skipped: Boolean,
        preferences: `in`.daasavaani.core.BackupPreferences? = null, done: () -> Unit = {}) {
        if (mutable.value.saving || mutable.value.personalError != null) {
            mutable.update { it.copy(setupError = R.string.first_use_failed) }
            return
        }
        val generation = mutable.value.personalRestoreGeneration
        mutable.update { it.copy(saving = true, setupError = null) }
        viewModelScope.launch {
            try {
                val value = withContext(NonCancellable + Dispatchers.IO) {
                    personalStore.finishFirstUse(expected, skipped, preferences)
                }
                if (acceptPersonal(value) && generation == mutable.value.personalRestoreGeneration) done()
            } catch (error: PersonalFailure) {
                if (generation == mutable.value.personalRestoreGeneration) {
                    mutable.update { it.copy(setupError = R.string.first_use_failed) }
                }
            } finally {
                if (generation == mutable.value.personalRestoreGeneration) mutable.update { it.copy(saving = false) }
            }
        }
    }
    fun clearMutationError() { mutable.update { it.copy(mutationError = null) } }
    private fun listWrite(operation: () -> PersonalSnapshot, done: () -> Unit) {
        if (mutable.value.saving || mutable.value.personalError != null) return
        val generation = mutable.value.personalRestoreGeneration
        mutable.update { it.copy(saving = true, mutationError = null) }
        viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) { operation() }
                if (acceptPersonal(value) && generation == mutable.value.personalRestoreGeneration) done()
            } catch (error: PersonalFailure) { if (generation == mutable.value.personalRestoreGeneration) mutable.update { it.copy(mutationError = error.label) } }
            finally { if (generation == mutable.value.personalRestoreGeneration) mutable.update { it.copy(saving = false) } }
        }
    }
    fun saveList(base: PersonalSnapshot, list: RecitationList, duplicate: Boolean = false, newOnly: Boolean = false,
        done: () -> Unit = {}) = listWrite({
            personalStore.saveList(base.revision, base.epoch, list, duplicate, newOnly)
        }, done)
    fun importList(base: PersonalSnapshot, snapshot: ListSnapshot, duplicate: Boolean, done: () -> Unit) =
        listWrite({ personalStore.importList(base.revision, base.epoch, snapshot, true, duplicate) }, done)
    fun deleteList(base: PersonalSnapshot, id: String, done: () -> Unit) =
        listWrite({ personalStore.deleteList(base.revision, base.epoch, id) }, done)

    private fun acceptPersonal(value: PersonalSnapshot): Boolean {
        if (!personalStore.isCurrent(value)) return false
        var accepted = false
        mutable.update {
            if (it.personal != null && it.personal.revision > value.revision) it else {
                accepted = true
                it.copy(personal = value, personalError = null)
            }
        }
        return accepted
    }

    suspend fun refreshAfterPersonalRestore() {
        if (!viewModelScope.coroutineContext.isActive) return
        while (opens.tryReceive().isSuccess) { /* old successful-open receipts belong to the replaced fence */ }
        val value = try { withContext(Dispatchers.IO) { personalStore.load() } }
        catch (error: PersonalFailure) {
            mutable.update { it.copy(personal = null, personalError = error.label, saving = false, selected = null,
                selectedListId = null, readerOpen = null, pendingFixture = null, fixtureReturnSection = null,
                personalRestoreGeneration = it.personalRestoreGeneration + 1) }
            return
        }
        if (!personalStore.isCurrent(value)) return
        acceptPersonal(value)
        mutable.update { it.copy(selected = null, selectedListId = null, readerOpen = null,
            pendingFixture = null, fixtureReturnSection = null, personalError = null, mutationError = null,
            historyError = null, learningError = null, setupError = null, saving = false, rememberingRecitation = false,
            personalRestoreGeneration = it.personalRestoreGeneration + 1) }
    }
    override fun onCleared() { opens.close(); personalStore.close(); super.onCleared() }
    fun prepare() {
        preparation?.cancel()
        mutable.update { it.copy(loading = true, error = null, completed = 0, total = 0) }
        preparation = viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) {
                    repository.prepare { done, total ->
                        withContext(Dispatchers.Main.immediate) { mutable.update { it.copy(completed = done, total = total) } }
                    }
                }
                mutable.update {
                    val pending = it.pendingFixture?.let { id -> value.items.find { item -> item.videoId == id } }
                    it.copy(prepared = value, visible = value.items, loading = false, selected = pending,
                        readerOpen = pending?.let { UUID.randomUUID().toString() })
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(loading = false, error = error.message ?: error.javaClass.simpleName) } }
        }
    }
    fun cancel() { preparation?.cancel(); mutable.update { it.copy(loading = false) } }
    fun select(item: FixtureItem?) {
        require(item == null || mutable.value.prepared?.items?.any { it == item } == true) { "Fixture is not prepared" }
        mutable.update { it.copy(selected = item, readerOpen = item?.let { UUID.randomUUID().toString() }) }
    }
    fun query(value: String) {
        searching?.cancel()
        mutable.update { it.copy(query = value, error = null) }
        val prepared = mutable.value.prepared ?: return
        searching = viewModelScope.launch {
            try {
                delay(prepared.policy.debounceMs.toLong())
                val result = if (value.isBlank()) prepared.items else withContext(Dispatchers.Default) {
                    val context = currentCoroutineContext()
                    search(value, prepared.index, prepared.policy) { context.ensureActive() }.map { hit ->
                        requireNotNull(prepared.items.find { it.videoId == hit.itemId }) { "Unavailable search result" }
                    }
                }
                mutable.update { it.copy(visible = result) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = error.message ?: error.javaClass.simpleName) } }
        }
    }
}
