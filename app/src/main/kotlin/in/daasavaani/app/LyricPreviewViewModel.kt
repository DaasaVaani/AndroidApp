package `in`.daasavaani.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.daasavaani.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LyricPreviewState(
    val prepared: PreparedLyricPreview? = null, val visible: List<LyricSummary> = emptyList(),
    val query: String = "", val selected: LyricSummary? = null, val item: PreviewLyric? = null,
    val loading: Boolean = false, val failed: Boolean = false, val completed: Int = 0, val total: Int = 0,
)

class LyricPreviewViewModel(application: Application, private val repository: LyricPreviewSource) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, LyricPreviewRepository(application.resources))
    private val mutable = MutableStateFlow(LyricPreviewState())
    val state = mutable.asStateFlow()
    private var work: Job? = null
    fun load() {
        work?.cancel()
        mutable.update { it.copy(loading = true, failed = false, completed = 0, total = 0) }
        work = viewModelScope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    repository.prepare { done, total ->
                        ensureActive()
                        mutable.update { it.copy(completed = done, total = total) }
                    }
                }
                ensureActive()
                mutable.update { it.copy(prepared = prepared,
                    visible = repository.codec.filter(prepared.catalog, it.query),
                    selected = null, item = null, loading = false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(loading = false, failed = true) } }
        }
    }
    fun cancel() { work?.cancel(); mutable.update { it.copy(loading = false) } }
    fun select(summary: LyricSummary?) {
        val prepared = mutable.value.prepared ?: return
        require(summary == null || summary in prepared.catalog.catalog) { "Lyric is not prepared" }
        mutable.update { it.copy(selected = summary, item = summary?.let { entry -> prepared.items.getValue(entry.lyricId) }) }
    }
    fun query(value: String) {
        try {
            val prepared = mutable.value.prepared ?: return
            val visible = repository.codec.filter(prepared.catalog, value)
            mutable.update { it.copy(query = value, visible = visible, failed = false) }
        } catch (error: IllegalArgumentException) {
            mutable.update { it.copy(visible = emptyList(), failed = true) }
        }
    }
}
