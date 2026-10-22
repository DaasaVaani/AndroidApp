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
import kotlin.random.Random

data class LyricPreviewState(
    val prepared: PreparedLyricPreview? = null, val visible: List<LyricSummary> = emptyList(),
    val query: String = "", val selected: LyricSummary? = null, val item: PreviewLyric? = null,
    val loading: Boolean = false, val failed: Boolean = false, val completed: Int = 0, val total: Int = 0,
    val pendingLyricId: String? = null,
    val readerOpen: String? = null,
    val pages: Int = 1, val browseFocusId: String? = null, val queryFailed: Boolean = false,
    val favoritesQuery: String = "", val favoritesFailed: Boolean = false, val favoritesPages: Int = 1,
    val lastOpenedLyricId: String? = null, val anotherUnavailable: Boolean = false,
)

class LyricPreviewViewModel(application: Application, private val repository: LyricPreviewSource,
    private val randomIndex: (Int) -> Int = Random.Default::nextInt) : AndroidViewModel(application) {
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
                        withContext(Dispatchers.Main.immediate) { mutable.update { it.copy(completed = done, total = total) } }
                    }
                }
                ensureActive()
                mutable.update {
                    val destination = prepared.catalog.catalog.find { entry -> entry.lyricId == it.pendingLyricId }
                    it.copy(prepared = prepared, visible = repository.codec.filter(prepared.catalog, it.query), favoritesPages = 1, queryFailed = false,
                        selected = destination, item = destination?.let { entry -> prepared.items.getValue(entry.lyricId) }, loading = false,
                        lastOpenedLyricId = null, anotherUnavailable = false,
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
            anotherUnavailable = false,
            item = destination?.let { entry -> requireNotNull(prepared).items.getValue(entry.lyricId) },
            readerOpen = destination?.let { UUID.randomUUID().toString() }) }
    }
    fun select(summary: LyricSummary?) {
        val prepared = mutable.value.prepared ?: return
        require(summary == null || summary in prepared.catalog.catalog) { "Lyric is not prepared" }
        mutable.update { it.copy(selected = summary, pendingLyricId = null, item = summary?.let { entry -> prepared.items.getValue(entry.lyricId) },
            anotherUnavailable = false,
            browseFocusId = summary?.lyricId ?: it.browseFocusId,
            readerOpen = summary?.let { UUID.randomUUID().toString() }) }
    }
    fun showAnother() {
        val state = mutable.value
        val prepared = state.prepared ?: return
        val destination = anotherLyric(prepared.catalog.catalog,
            state.selected?.takeIf { prepared.items[it.lyricId] == state.item }?.lyricId ?: state.lastOpenedLyricId,
            randomIndex)
        if (destination == null) {
            mutable.update { it.copy(anotherUnavailable = true) }
            return
        }
        // Replacing the reader must not replace the original catalog return focus.
        mutable.update { it.copy(selected = destination, item = prepared.items.getValue(destination.lyricId),
            pendingLyricId = null, anotherUnavailable = false, readerOpen = UUID.randomUUID().toString()) }
    }
    fun exposed(token: String): PersonalReference? {
        val state = mutable.value
        if (state.readerOpen != token || state.item == null || state.item.lyricId != state.selected?.lyricId ||
            state.prepared?.items?.get(state.item.lyricId) != state.item) return null
        mutable.update { it.copy(readerOpen = null, lastOpenedLyricId = state.item.lyricId) }
        return PersonalReference.lyric(state.item.lyricId)
    }
    fun query(value: String) {
        try {
            val prepared = mutable.value.prepared ?: return
            val visible = repository.codec.filter(prepared.catalog, value)
            mutable.update { it.copy(query = value, visible = visible, failed = false, queryFailed = false, pages = 1, browseFocusId = null) }
        } catch (error: IllegalArgumentException) {
            mutable.update { it.copy(visible = emptyList(), failed = true, queryFailed = true, pages = 1, browseFocusId = null) }
        }
    }
    fun more() { mutable.update { it.copy(pages = Math.addExact(it.pages, 1)) } }
    fun favoriteReferences(references: Set<PersonalReference>, state: LyricPreviewState): List<PersonalReference> =
        if (state.favoritesFailed) emptyList()
        else repository.codec.favorites(references, state.prepared?.catalog, state.favoritesQuery)

    fun favoritesQuery(value: String) {
        try {
            repository.codec.favorites(emptySet(), null, value)
            mutable.update { it.copy(favoritesQuery = value, favoritesFailed = false, favoritesPages = 1) }
        } catch (_: IllegalArgumentException) {
            mutable.update { it.copy(favoritesFailed = true, favoritesPages = 1) }
        }
    }
    fun moreFavorites() { mutable.update { it.copy(favoritesPages = Math.addExact(it.favoritesPages, 1)) } }
}
