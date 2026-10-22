package `in`.daasavaani.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryBrowsingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val page get() = context.resources.getInteger(R.integer.trusted_max_page_size)
    private fun id(i: Int) = "10000000-0000-4000-8000-${i.toString().padStart(12, '0')}"
    private fun title(i: Int) = "Synthetic catalog [${i.toString().padStart(3, '0')}]"
    private inner class Source(var count: Int) : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000,
            page * 3, 10, 100, 5, context.resources.getInteger(R.integer.lyric_query_code_points), 16))
        var requests = 0
        var fail = false
        override suspend fun catalog(): LyricCatalog {
            requests++
            check(!fail) { "Synthetic preparation failure" }
            return LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"),
                (1..count).map { LyricSummary(id(it), title(it), listOf("tag-not-title"),
                    ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20)) })
        }
        override suspend fun item(summary: LyricSummary): PreviewLyric {
            requests++
            return PreviewLyric("daasavaani-lyric", 1, summary.lyricId, "kn", summary.title, summary.tags,
                listOf(PreviewLine("line", "  Synthetic permitted text  ", "2${summary.lyricId.drop(1)}"),
                    PreviewLine("layout", " \t"), PreviewLine("layout", "")))
        }
    }
    private fun seed(): PersonalSnapshot {
        context.deleteDatabase("personal.db")
        return PersonalStore(context).use { store ->
            val initial = store.change(store.load(), language = "en", theme = "warm", reference = PersonalReference.lyric(id(1)))
            store.saveList(initial.revision, initial.epoch, RecitationList.create("Synthetic saved list",
                listOf(PersonalReference.legacy(id(1)).entry(), ListEntry("placeholder", text = "permitted placeholder"))))
        }
    }
    private fun count(shown: Int, total: Int) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-count"))
        compose.onNodeWithTag("lyric-count").assertTextEquals(context.getString(R.string.result_count)
            .replace("{shown}", shown.toString()).replace("{total}", total.toString()))
    }
    private fun more() {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-more"))
        compose.onNodeWithTag("lyric-more").performClick()
    }
    private fun position() = compose.onNodeWithTag("lyric-preview").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun scrollTitle(index: Int) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-title-${id(index)}"))
    }
    private fun filter(value: String) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-filter"))
        compose.onNodeWithTag("lyric-filter").performTextReplacement(value)
    }

    @Test fun configuredPagesCompleteTailAndNewSessionWithoutPersonalOrPreparationSideEffects() {
        for (total in listOf(page, page * 2, page * 2 + 1)) {
            val before = seed()
            val source = Source(total)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var library: LibraryViewModel
                lateinit var real: LyricPreviewViewModel
                scenario.onActivity { activity ->
                    library = ViewModelProvider(activity)[LibraryViewModel::class.java]
                    real = LyricPreviewViewModel(activity.application, source)
                    activity.setContent { MaterialTheme { LibraryScreen(library, real) } }
                }
                compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving }
                compose.onNodeWithTag("owner-lyrics").performClick()
                compose.onNodeWithTag("lyric-filter").assertDoesNotExist()
                assertEquals(0, source.requests)
                compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-load"))
                compose.onNodeWithTag("lyric-load").performClick()
                compose.waitUntil(10000) { real.state.value.prepared != null }
                var shown = page
                count(shown, total)
                while (shown < total) {
                    more(); shown = minOf(total, shown + page); count(shown, total)
                }
                scrollTitle(total)
                compose.onNodeWithTag("lyric-more").assertDoesNotExist()
                assertEquals(before, library.state.value.personal)
                assertEquals(total + 1, source.requests)
                filter("[")
                scenario.onActivity { real.cancel() }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var library: LibraryViewModel
                scenario.onActivity { activity ->
                    val fresh = LyricPreviewViewModel(activity.application, source)
                    assertNull(fresh.state.value.prepared)
                    assertEquals("", fresh.state.value.query)
                    assertEquals(1, fresh.state.value.pages)
                    assertNull(fresh.state.value.browseFocusId)
                    library = ViewModelProvider(activity)[LibraryViewModel::class.java]
                    activity.setContent { MaterialTheme { LibraryScreen(library, fresh) } }
                }
                compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving }
                compose.onNodeWithTag("owner-lyrics").performClick()
                compose.onNodeWithTag("lyric-filter").assertDoesNotExist()
                assertEquals(total + 1, source.requests)
            }
            PersonalStore(context).use { assertEquals(before, it.load()) }
        }
        context.deleteDatabase("personal.db")
    }

    @Test fun literalIndependentQueriesResetExpansionAndRejectInvalidWithoutFalseNoMatch() {
        val before = seed()
        val total = page * 2 + 1
        val source = Source(total)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity { activity ->
                library = ViewModelProvider(activity)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(activity.application, source)
                activity.setContent { MaterialTheme { LibraryScreen(library, real) } }
                real.load()
            }
            compose.waitUntil(10000) { real.state.value.prepared != null && library.state.value.personal != null }
            compose.onNodeWithTag("owner-lyrics").performClick()
            filter("SyNtHeTiC")
            more(); more(); count(total, total)
            scenario.onActivity { real.favoritesQuery("[001]"); real.moreFavorites() }
            filter("[")
            count(page, total)
            assertEquals(1, real.state.value.pages)
            assertEquals("[001]", real.state.value.favoritesQuery)
            assertEquals(2, real.state.value.favoritesPages)
            more(); scrollTitle(page + 1)
            filter(".*"); count(0, 0)
            compose.onNodeWithText(context.getString(R.string.no_results)).assertExists()
            filter("tag-not-title"); count(0, 0)
            filter(id(1)); count(0, 0)
            filter("synthetic"); more()
            filter("x".repeat(context.resources.getInteger(R.integer.lyric_query_code_points) + 1))
            assertTrue(real.state.value.failed)
            assertTrue(real.state.value.queryFailed)
            assertEquals("synthetic", real.state.value.query)
            count(0, 0)
            compose.onNodeWithText(context.getString(R.string.no_results)).assertDoesNotExist()
            scenario.onActivity { real.query("\uD800") }
            assertTrue(real.state.value.queryFailed)
            filter("")
            count(page, total)
            assertFalse(real.state.value.failed)
            assertFalse(real.state.value.queryFailed)
            assertEquals(before, library.state.value.personal)
            assertEquals(total + 1, source.requests)
            scenario.onActivity { real.cancel() }
        }
        context.deleteDatabase("personal.db")
    }

    @Test fun actualReaderArrowBackFocusSettingsAndChangedContentKeepValidIndependentContext() {
        val before = seed()
        val total = page * 2 + 1
        val source = Source(total)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity { activity ->
                library = ViewModelProvider(activity)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(activity.application, source)
                activity.setContent { MaterialTheme { LibraryScreen(library, real) } }
                real.load(); real.favoritesQuery("[001]")
            }
            compose.waitUntil(10000) { real.state.value.prepared != null && library.state.value.personal != null && !library.state.value.saving }
            compose.onNodeWithTag("owner-lyrics").performClick()
            filter("[")
            more(); more()
            scrollTitle(total)
            val viewport = position()
            compose.onNodeWithTag("lyric-title-${id(total)}").performClick()
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
            compose.onNodeWithTag("lyric-text").assertTextEquals("  Synthetic permitted text  \n \t\n")
            compose.waitUntil(10000) { library.state.value.personal?.recentHistory?.entries?.size == 1 && !library.state.value.saving }
            val afterOpen = library.state.value.personal!!
            assertEquals(before.revision + 1, afterOpen.revision)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("lyric-title-${id(total)}").assertIsFocused()
            assertEquals(viewport, position(), 1f)
            assertEquals(3, real.state.value.pages)
            assertEquals("[", real.state.value.query)
            assertEquals("[001]", real.state.value.favoritesQuery)
            compose.onNodeWithText(context.getString(R.string.settings)).performClick()
            compose.onNodeWithText(context.getString(R.string.transfer_title)).assertExists()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("lyric-title-${id(total)}").assertIsFocused()
            assertEquals(viewport, position(), 1f)
            assertEquals(afterOpen, library.state.value.personal)
            compose.onNodeWithTag("lyric-title-${id(total)}").performClick()
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
            compose.waitUntil(10000) { real.state.value.readerOpen == null && !library.state.value.saving }
            scenario.onActivity { library.changeHistory(clear = true) }
            compose.waitUntil(10000) { library.state.value.personal?.recentHistory?.entries?.isEmpty() == true && !library.state.value.saving }
            compose.onNodeWithText(context.getString(R.string.settings)).performClick()
            scenario.onActivity { library.changeReading(direction = 1); library.changeReading(density = "relaxed") }
            compose.waitUntil(10000) { !library.state.value.saving && library.state.value.personal?.reading?.density == "relaxed" }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
            compose.onNodeWithTag("lyric-text").assertTextEquals("  Synthetic permitted text  \n \t\n")
            assertTrue(library.state.value.personal!!.recentHistory.entries.isEmpty())
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasText(context.getString(R.string.lyric_back)))
            compose.onNodeWithText(context.getString(R.string.lyric_back)).performClick()
            compose.onNodeWithTag("lyric-title-${id(total)}").assertIsFocused()
            assertEquals(viewport, position(), 1f)
            assertTrue(library.state.value.personal!!.recentHistory.entries.isEmpty())
            val afterPreferences = library.state.value.personal!!
            assertEquals(before.favorites, afterPreferences.favorites)
            assertEquals(before.lists, afterPreferences.lists)
            assertEquals(before.language, afterPreferences.language)
            assertEquals(before.theme, afterPreferences.theme)
            assertEquals(total + 1, source.requests)
            source.fail = true
            scenario.onActivity { real.load() }
            compose.waitUntil(10000) { !real.state.value.loading && real.state.value.failed }
            assertEquals(3, real.state.value.pages)
            assertEquals(total, real.state.value.visible.size)
            source.fail = false; source.count = 1
            scenario.onActivity { real.load() }
            compose.waitUntil(10000) { !real.state.value.loading && !real.state.value.failed }
            count(1, 1)
            scrollTitle(1)
            compose.onNodeWithTag("lyric-more").assertDoesNotExist()
            assertEquals(afterPreferences, library.state.value.personal)
            assertEquals(total + 4, source.requests)
            scenario.onActivity { real.cancel() }
        }
        context.deleteDatabase("personal.db")
    }
}
