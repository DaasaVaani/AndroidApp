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
import java.util.UUID

data class LyricPreviewState(
    val prepared: PreparedLyricPreview? = null, val visible: List<LyricSummary> = emptyList(),
    val query: String = "", val selected: LyricSummary? = null, val item: PreviewLyric? = null,
    val loading: Boolean = false, val failed: Boolean = false, val completed: Int = 0, val total: Int = 0,
    val pendingLyricId: String? = null,
    val readerOpen: String? = null,
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
                mutable.update {
                    val destination = prepared.catalog.catalog.find { entry -> entry.lyricId == it.pendingLyricId }
                    it.copy(prepared = prepared, visible = repository.codec.filter(prepared.catalog, it.query),
                        selected = destination, item = destination?.let { entry -> prepared.items.getValue(entry.lyricId) }, loading = false,
                        readerOpen = destination?.let { UUID.randomUUID().toString() }, pendingLyricId = if (destination != null) null else it.pendingLyricId)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(loading = false, failed = true) } }
        }
    }
    fun cancel() { work?.cancel(); mutable.update { it.copy(loading = false) } }
    fun request(lyricId: String) {
        require(PersonalReference.validLyricId(lyricId))
        val prepared = mutable.value.prepared
        val destination = prepared?.catalog?.catalog?.find { it.lyricId == lyricId }
        mutable.update { it.copy(pendingLyricId = if (destination == null) lyricId else null, selected = destination,
            item = destination?.let { entry -> requireNotNull(prepared).items.getValue(entry.lyricId) },
            readerOpen = destination?.let { UUID.randomUUID().toString() }) }
    }
    fun select(summary: LyricSummary?) {
        val prepared = mutable.value.prepared ?: return
        require(summary == null || summary in prepared.catalog.catalog) { "Lyric is not prepared" }
        mutable.update { it.copy(selected = summary, pendingLyricId = null, item = summary?.let { entry -> prepared.items.getValue(entry.lyricId) },
            readerOpen = summary?.let { UUID.randomUUID().toString() }) }
    }
    fun exposed(token: String): PersonalReference? {
        val state = mutable.value
        if (state.readerOpen != token || state.item == null || state.item.lyricId != state.selected?.lyricId ||
            state.prepared?.items?.get(state.item.lyricId) != state.item) return null
        mutable.update { it.copy(readerOpen = null) }
        return PersonalReference.lyric(state.item.lyricId)
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
