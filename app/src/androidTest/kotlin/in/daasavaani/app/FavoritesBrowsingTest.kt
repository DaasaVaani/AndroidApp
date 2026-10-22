package `in`.daasavaani.app

import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FavoritesBrowsingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun id(i: Int) = "10000000-0000-4000-8000-${i.toString().padStart(12, '0')}"
    private fun title(i: Int) = "Synthetic favorite [${i.toString().padStart(3, '0')}]"
    private val page get() = context.resources.getInteger(R.integer.trusted_max_page_size)
    private class Source(count: Int, queryLimit: Int) : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000,
            count, 10, 100, 5, queryLimit, 16))
        private val summaries = (1..count).map {
            LyricSummary("10000000-0000-4000-8000-${it.toString().padStart(12, '0')}",
                "Synthetic favorite [${it.toString().padStart(3, '0')}]", emptyList(),
                ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20))
        }
        var requests = 0
        override suspend fun catalog(): LyricCatalog {
            requests++
            return codec.catalog(Json.encodeToString(LyricCatalog("daasavaani-lyric-catalog", 1,
                "prelaunch", listOf("lyric-preview-v1"), summaries)).encodeToByteArray())
        }
        override suspend fun item(summary: LyricSummary): PreviewLyric {
            requests++
            val index = summaries.indexOf(summary) + 1
            return codec.item(Json.encodeToString(PreviewLyric("daasavaani-lyric", 1, summary.lyricId,
                "kn", summary.title, summary.tags, listOf(PreviewLine("line", "Synthetic permitted test text",
                    "20000000-0000-4000-8000-${index.toString().padStart(12, '0')}")))).encodeToByteArray(), summary)
        }
    }

    private fun seed(count: Int, extras: Boolean = false): PersonalSnapshot {
        context.deleteDatabase("personal.db")
        return PersonalStore(context).use { store ->
            var state = store.change(store.load(), language = "en", theme = "warm")
            for (i in 1..count) state = store.change(state, reference = PersonalReference.lyric(id(i)))
            if (extras) {
                state = store.change(state, reference = PersonalReference.lyric(id(999)))
                state = store.change(state, reference = PersonalReference.legacy(id(1)))
            }
            state
        }
    }
    private fun count(shown: Int, total: Int) {
        compose.onNodeWithTag("favorites-count").assertTextEquals(context.getString(R.string.result_count)
            .replace("{shown}", shown.toString()).replace("{total}", total.toString()))
    }
    private fun more() {
        compose.onNodeWithTag("favorites-results").performScrollToNode(hasTestTag("favorites-more"))
        compose.onNodeWithTag("favorites-more").performClick()
    }
    private fun scrollTitle(index: Int) {
        compose.onNodeWithTag("favorites-results").performScrollToNode(hasText(title(index)))
    }
    private fun openTitle(index: Int): Float {
        val card = hasTestTag("favorite-lyric-${id(index)}")
        compose.onNodeWithTag("favorites-results").performScrollToNode(card)
        val position = compose.onNodeWithTag("favorites-results").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNode(hasText(context.getString(R.string.open)) and
            hasAnyAncestor(card)).performClick()
        return position
    }

    @Test fun actualConfiguredPageAndTwoPageBoundariesRemainCompletelyReachable() {
        for (total in listOf(page, page * 2, page * 2 + 1)) {
            val before = seed(total)
            val source = Source(total, context.resources.getInteger(R.integer.lyric_query_code_points))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var library: LibraryViewModel
                lateinit var real: LyricPreviewViewModel
                scenario.onActivity { activity ->
                    library = ViewModelProvider(activity)[LibraryViewModel::class.java]
                    real = LyricPreviewViewModel(activity.application, source)
                    activity.setContent { MaterialTheme { LibraryScreen(library, real) } }
                }
                compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving }
                compose.onNodeWithText(context.getString(R.string.favorites)).performClick()
                count(page, total)
                assertEquals(0, source.requests)
                val progressThreads = mutableListOf<Boolean>()
                val observer = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
                    real.state.collect {
                        if (it.completed > 0) progressThreads.add(Looper.myLooper() == Looper.getMainLooper())
                    }
                }
                scenario.onActivity { real.load() }
                compose.waitUntil(10000) { real.state.value.prepared != null }
                observer.cancel()
                assertTrue(progressThreads.isNotEmpty())
                assertTrue(progressThreads.all { it })
                var shown = page
                while (shown < total) {
                    scrollTitle(shown)
                    more()
                    shown = minOf(total, shown + page)
                    count(shown, total)
                }
                scrollTitle(total)
                compose.onNodeWithTag("favorites-more").assertDoesNotExist()
                assertEquals(before, library.state.value.personal)
                assertEquals(total + 1, source.requests)
                scenario.onActivity { real.cancel() }
            }
        }
        context.deleteDatabase("personal.db")
    }

    @Test fun independentLiteralFilterResetUnknownsReturnAndSuccessfulOpenHistory() {
        val total = page * 2 + 1
        val before = seed(total, extras = true)
        val source = Source(total, context.resources.getInteger(R.integer.lyric_query_code_points))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity { activity ->
                library = ViewModelProvider(activity)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(activity.application, source)
                activity.setContent { MaterialTheme { LibraryScreen(library, real) } }
            }
            compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving }
            compose.onNodeWithText(context.getString(R.string.favorites)).performClick()
            more(); more()
            compose.onNodeWithTag("favorites-results").performScrollToNode(hasText(id(999)))
            compose.onNodeWithText(id(999)).assertExists()
            assertEquals(0, source.requests)
            openTitle(999)
            assertEquals(id(999), real.state.value.pendingLyricId)
            compose.onNodeWithTag("back-to-favorites").assertIsDisplayed()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            count(total + 2, total + 2)
            assertEquals(before, library.state.value.personal)
            assertEquals(0, source.requests)
            compose.onNodeWithTag("favorites-filter").performTextInput("synthetic")
            count(0, 0)
            assertEquals(0, source.requests)
            compose.onNodeWithTag("favorites-filter").performTextClearance()
            count(page, total + 2)
            scenario.onActivity { real.load() }
            compose.waitUntil(10000) { real.state.value.prepared != null }
            scenario.onActivity { real.query("[001]") }
            compose.onNodeWithTag("favorites-filter").performTextInput("[")
            count(page, total)
            more(); more()
            count(total, total)
            compose.onNodeWithText(context.getString(R.string.settings)).performClick()
            compose.onNodeWithText(context.getString(R.string.favorites)).performClick()
            count(total, total)
            val position = openTitle(total)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
            compose.waitUntil(10000) { library.state.value.personal?.recentHistory?.entries?.isNotEmpty() == true && !library.state.value.saving }
            assertEquals(listOf(PersonalReference.lyric(id(total))), library.state.value.personal!!.recentHistory.entries)
            scenario.onActivity { library.changeReading(direction = 1); library.changeReading(density = "compact") }
            compose.waitUntil(10000) { !library.state.value.saving && library.state.value.personal?.reading?.density == "compact" }
            compose.onNodeWithTag("back-to-favorites").performClick()
            count(total, total)
            assertEquals("[", real.state.value.favoritesQuery)
            assertEquals("[001]", real.state.value.query)
            assertEquals(position, compose.onNodeWithTag("favorites-results").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value(), 1f)
            compose.onNodeWithTag("favorites-filter").performTextReplacement(".*")
            count(0, 0)
            compose.onNodeWithText(context.getString(R.string.no_results)).assertExists()
            compose.onNodeWithTag("favorites-filter").performTextReplacement("SyNtHeTiC")
            count(page, total)
            compose.onNodeWithTag("favorites-filter").performTextReplacement("x".repeat(context.resources.getInteger(R.integer.lyric_query_code_points) + 1))
            compose.waitForIdle()
            assertTrue(real.state.value.favoritesFailed)
            assertEquals("SyNtHeTiC", real.state.value.favoritesQuery)
            count(0, 0)
            compose.onNodeWithText(context.getString(R.string.no_results)).assertDoesNotExist()
            compose.onNodeWithTag("favorites-filter").performTextClearance()
            count(page, total + 2)
            more(); more()
            compose.onNodeWithTag("favorites-results").performScrollToNode(hasText(id(999)))
            compose.onNodeWithText(id(999)).assertExists()
            assertEquals(total + 1, source.requests)
            val after = library.state.value.personal!!
            assertEquals(before.favorites, after.favorites)
            assertEquals(before.lists, after.lists)
            assertEquals(before.language, after.language)
            assertEquals(before.theme, after.theme)
            assertEquals(before.revision + 3, after.revision)
            compose.onNodeWithTag("favorites-filter").performTextReplacement("[${total.toString().padStart(3, '0')}]")
            count(1, 1)
            openTitle(total)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
            compose.waitForIdle()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            count(1, 1)
            compose.onNodeWithTag("favorites-results").performScrollToNode(hasText(title(total)))
            compose.onNode(hasText(context.getString(R.string.remove_favorite)) and
                hasAnyAncestor(hasTestTag("favorite-lyric-${id(total)}"))).performClick()
            compose.waitUntil(10000) { library.state.value.personal?.favorites?.size == total + 1 && !library.state.value.saving }
            count(0, 0)
            compose.onNodeWithTag("favorites-filter").performTextClearance()
            count(page, total + 1)
            more(); more()
            count(total + 1, total + 1)
            scenario.onActivity { real.cancel() }
        }
        PersonalStore(context).use { store ->
            assertEquals(before.favorites - PersonalReference.lyric(id(total)), store.load().favorites)
            assertEquals(listOf(PersonalReference.lyric(id(total))), store.load().recentHistory.entries)
        }
        context.deleteDatabase("personal.db")
    }
}
