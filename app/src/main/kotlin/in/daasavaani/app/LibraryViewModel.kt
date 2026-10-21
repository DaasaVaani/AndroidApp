package `in`.daasavaani.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.daasavaani.core.FixtureItem
import `in`.daasavaani.core.search
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryState(
    val prepared: PreparedPublication? = null, val loading: Boolean = false,
    val completed: Int = 0, val total: Int = 0, val error: String? = null,
    val query: String = "", val visible: List<FixtureItem> = emptyList(), val selected: FixtureItem? = null,
    val personal: PersonalSnapshot? = null, val personalError: Int? = null,
    val saving: Boolean = false, val section: String = "library",
)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PublicationRepository(application.resources)
    private val personalStore = PersonalStore(application)
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var preparation: Job? = null
    private var searching: Job? = null
    init { retryPersonal() }
    fun section(value: String) { mutable.update { it.copy(section = value, selected = null) } }
    fun retryPersonal() {
        if (mutable.value.saving) return
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) { personalStore.load() }
                mutable.update { it.copy(personal = value, personalError = null) }
            } catch (error: PersonalFailure) { mutable.update { it.copy(personalError = error.label) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
    fun changePersonal(language: String? = null, theme: String? = null, favorite: String? = null) {
        val current = mutable.value.personal ?: return
        if (mutable.value.saving || mutable.value.personalError != null) return
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) { personalStore.change(current.revision, language, theme, favorite) }
                mutable.update { it.copy(personal = value, personalError = null) }
            } catch (error: PersonalFailure) { mutable.update { it.copy(personalError = error.label) } }
            finally { mutable.update { it.copy(saving = false) } }
        }
    }
    override fun onCleared() { personalStore.close(); super.onCleared() }
    fun prepare() {
        preparation?.cancel()
        mutable.update { it.copy(loading = true, error = null, completed = 0, total = 0) }
        preparation = viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) {
                    repository.prepare { done, total -> mutable.update { it.copy(completed = done, total = total) } }
                }
                mutable.update { it.copy(prepared = value, visible = value.items, loading = false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(loading = false, error = error.message ?: error.javaClass.simpleName) } }
        }
    }
    fun cancel() { preparation?.cancel(); mutable.update { it.copy(loading = false) } }
    fun select(item: FixtureItem?) { mutable.update { it.copy(selected = item) } }
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
