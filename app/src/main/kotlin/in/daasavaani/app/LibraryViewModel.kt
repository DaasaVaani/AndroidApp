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
)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PublicationRepository(application.resources)
    private val mutable = MutableStateFlow(LibraryState())
    val state = mutable.asStateFlow()
    private var preparation: Job? = null
    private var searching: Job? = null
    fun prepare() {
        preparation?.cancel()
        mutable.update { it.copy(loading = true, error = null, completed = 0, total = 0) }
        preparation = viewModelScope.launch {
            try {
                val value = withContext(Dispatchers.IO) {
                    repository.prepare { done, total -> mutable.update { it.copy(completed = done, total = total) } }
                }
                mutable.value = LibraryState(prepared = value, visible = value.items)
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
