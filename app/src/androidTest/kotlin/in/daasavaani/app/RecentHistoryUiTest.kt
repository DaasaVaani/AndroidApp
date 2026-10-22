package `in`.daasavaani.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecentHistoryUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "10000000-0000-4000-8000-000000000001"
    private val unavailable = "10000000-0000-4000-8000-000000000099"
    private val reference = PersonalReference.lyric(id)
    @After fun clean() { context.deleteDatabase("personal.db") }
    private fun seed(): PersonalSnapshot {
        context.deleteDatabase("personal.db")
        return PersonalStore(context).use { it.change(it.load(), language = "en", theme = "warm", favorite = "UNAVAILABLE") }
    }
    private inner class Source : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000, 10, 10, 100, 5, 10, 16))
        val summary = LyricSummary(id, "Synthetic history reader", listOf("synthetic"),
            ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20))
        var requests = 0
        var fail = false
        override suspend fun catalog(): LyricCatalog {
            requests++
            check(!fail) { "Synthetic preparation failure" }
            return LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"), listOf(summary))
        }
        override suspend fun item(summary: LyricSummary): PreviewLyric {
            requests++
            return PreviewLyric("daasavaani-lyric", 1, id, "kn", summary.title, summary.tags,
                listOf(PreviewLine("line", "  Synthetic literal ಕ 😀  ", "20000000-0000-4000-8000-000000000001")))
        }
    }
    private fun waitSaved(model: LibraryViewModel, condition: (PersonalSnapshot) -> Boolean) {
        compose.waitUntil(10000) { !model.state.value.saving && model.state.value.personal?.let(condition) == true }
    }
    private fun expose() {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
        compose.onNodeWithTag("lyric-text").assertIsDisplayed()
    }

    @Test fun realSuccessfulExposureOnlyAndMountedClearToggleRecompositionNeverRecreateHistoryOrDownload() {
        val before = seed()
        val source = Source()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source)
                it.setContent { MaterialTheme { LibraryScreen(library, real) } }
            }
            waitSaved(library) { true }
            compose.onNodeWithTag("owner-lyrics").performClick()
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-load"))
            compose.onNodeWithTag("lyric-load").performClick()
            compose.waitUntil(10000) { real.state.value.prepared != null }
            assertEquals(2, source.requests)
            assertEquals(before, library.state.value.personal)
            scenario.onActivity { real.request(unavailable) }
            compose.waitForIdle()
            assertEquals(before, library.state.value.personal)
            scenario.onActivity { real.select(null); real.select(source.summary) }
            expose()
            waitSaved(library) { it.recentHistory.entries == listOf(reference) }
            val recorded = requireNotNull(library.state.value.personal)
            assertEquals(before.copy(revision = before.revision + 1, recentHistory = RecentHistory(entries = listOf(reference))), recorded)
            scenario.onActivity { real.select(source.summary) }
            expose()
            compose.waitUntil(10000) { real.state.value.readerOpen == null && !library.state.value.saving }
            assertEquals(recorded, library.state.value.personal)
            scenario.onActivity { library.section("library") }
            compose.onNodeWithTag("recent-history").performClick()
            compose.onNodeWithText(source.summary.title).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.open)).performClick()
            expose()
            compose.waitUntil(10000) { real.state.value.readerOpen == null && !library.state.value.saving }
            assertEquals(recorded, library.state.value.personal)
            scenario.onActivity { library.changeHistory(clear = true) }
            waitSaved(library) { it.recentHistory.entries.isEmpty() }
            val cleared = requireNotNull(library.state.value.personal)
            scenario.onActivity { library.changeHistory(enabled = false) }
            waitSaved(library) { !it.recentHistory.enabled }
            scenario.onActivity { real.select(source.summary) }
            expose()
            compose.waitUntil(10000) { real.state.value.readerOpen == null && !library.state.value.saving }
            assertTrue(requireNotNull(library.state.value.personal).recentHistory.entries.isEmpty())
            scenario.onActivity { library.changeHistory(enabled = true) }
            waitSaved(library) { it.recentHistory.enabled }
            compose.waitForIdle()
            assertEquals(cleared.revision + 2, library.state.value.personal?.revision)
            assertTrue(requireNotNull(library.state.value.personal).recentHistory.entries.isEmpty())
            scenario.onActivity { library.section("library") }
            compose.onNodeWithTag("owner-lyrics").performClick()
            expose()
            assertTrue(requireNotNull(library.state.value.personal).recentHistory.entries.isEmpty())
            assertEquals(2, source.requests)
            scenario.onActivity { real.select(null); real.select(source.summary) }
            expose()
            waitSaved(library) { it.recentHistory.entries == listOf(reference) }
            assertEquals(before.lists, library.state.value.personal?.lists)
            assertEquals(before.favorites, library.state.value.personal?.favorites)
            assertEquals(before.language, library.state.value.personal?.language)
            assertEquals(before.theme, library.state.value.personal?.theme)
            assertEquals(2, source.requests)
            scenario.onActivity { real.cancel() }
        }
    }

    @Test fun beforePreparationUnresolvedTypedRowsAndSettingsClearKeepDisabledAndOtherPersonalData() {
        seed()
        val initial = PersonalStore(context).use { store ->
            var s = store.history(store.load(), opened = reference)
            s = store.history(s, opened = PersonalReference.legacy(id))
            s = store.history(s, enabled = false)
            s
        }
        val source = Source()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source)
                it.setContent { MaterialTheme { LibraryScreen(library, real) } }
            }
            waitSaved(library) { true }
            compose.onNodeWithTag("recent-history").performClick()
            compose.onNodeWithTag("history-enabled").assertIsOff()
            compose.onAllNodesWithText(id).assertCountEquals(2)
            compose.onNodeWithText(context.getString(R.string.open)).assertDoesNotExist()
            assertEquals(initial, library.state.value.personal)
            assertNull(real.state.value.prepared); assertNull(library.state.value.prepared)
            compose.onNodeWithTag("history-clear").performClick()
            waitSaved(library) { it.recentHistory.entries.isEmpty() }
            compose.onNodeWithTag("history-enabled").assertIsOff()
            assertEquals(initial.copy(revision = initial.revision + 1, recentHistory = RecentHistory(false)), library.state.value.personal)
            compose.onNodeWithText(context.getString(R.string.settings)).performClick()
            compose.onNodeWithTag("history-enabled").assertIsOff()
            compose.onNodeWithTag("history-clear").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(initial.revision + 1, library.state.value.personal?.revision)
            assertEquals(0, source.requests)
            scenario.onActivity { real.cancel() }
        }
        PersonalStore(context).use { assertFalse(it.load().recentHistory.enabled) }
    }

    @Test fun failedPreparationBackgroundSelectionAndHistoryWriteFailureNeverEraseUsableReaderOrPersonalState() {
        val before = seed()
        val source = Source().apply { fail = true }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source)
                it.setContent { MaterialTheme { LibraryScreen(library, real) } }
                real.request(id)
            }
            waitSaved(library) { true }
            // Pending explicit destination and failed preparation while reader is not mounted.
            scenario.onActivity { real.load() }
            compose.waitUntil(10000) { real.state.value.failed }
            assertEquals(before, library.state.value.personal)
            source.fail = false
            scenario.onActivity { real.load() }
            compose.waitUntil(10000) { real.state.value.item != null }
            assertEquals(before, library.state.value.personal)
            context.openOrCreateDatabase("personal.db", android.content.Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TRIGGER synthetic_failure BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
                compose.onNodeWithTag("owner-lyrics").performClick()
                expose()
                compose.waitUntil(10000) { library.state.value.historyError != null && !library.state.value.saving }
                compose.onNodeWithTag("history-error").assertIsDisplayed()
                compose.onNodeWithTag("lyric-text").assertIsDisplayed()
                assertEquals(before, library.state.value.personal)
                db.execSQL("DROP TRIGGER synthetic_failure")
            }
            PersonalStore(context).use { assertEquals(before, it.load()) }
            scenario.onActivity { library.retryPersonal() }
            waitSaved(library) { true }
            compose.waitForIdle()
            assertEquals(before, library.state.value.personal)
            // Retry/recomposition is not a new explicit open.
            scenario.onActivity { real.select(null); real.select(source.summary) }
            expose()
            waitSaved(library) { it.recentHistory.entries == listOf(reference) }
            assertEquals(3, source.requests)
            scenario.onActivity { real.cancel() }
        }
    }

    @Test fun actualFixtureSuccessfulOpenRecordsLegacyOnceAndDoesNotRecordPreparationOrSearch() {
        val before = seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            scenario.onActivity { library = ViewModelProvider(it)[LibraryViewModel::class.java] }
            waitSaved(library) { true }
            compose.onNodeWithText(context.getString(R.string.prepare_library)).performClick()
            compose.waitUntil(60000) { library.state.value.prepared != null }
            assertEquals(before, library.state.value.personal)
            scenario.onActivity { library.query("unmatchedsyntheticquery") }
            compose.waitUntil(10000) { library.state.value.visible.isEmpty() }
            assertEquals(before, library.state.value.personal)
            scenario.onActivity { library.query("") }
            compose.waitUntil(10000) { library.state.value.visible == library.state.value.prepared?.items }
            val item = requireNotNull(library.state.value.prepared).items.first()
            compose.onNodeWithText(item.title).performClick()
            compose.onNodeWithText(item.lines.first().text).assertIsDisplayed()
            waitSaved(library) { it.recentHistory.entries == listOf(PersonalReference.legacy(item.videoId)) }
            val saved = requireNotNull(library.state.value.personal)
            assertEquals(before.copy(revision = before.revision + 1,
                recentHistory = RecentHistory(entries = listOf(PersonalReference.legacy(item.videoId)))), saved)
            scenario.onActivity { library.changeHistory(clear = true) }
            waitSaved(library) { it.recentHistory.entries.isEmpty() }
            compose.waitForIdle()
            assertEquals(saved.revision + 1, library.state.value.personal?.revision)
            compose.onNodeWithText(context.getString(R.string.back)).performClick()
            compose.onNodeWithTag("recent-history").performClick()
            compose.onNodeWithTag("history-enabled").performClick()
            waitSaved(library) { !it.recentHistory.enabled }
            compose.onNodeWithText(context.getString(R.string.library)).performClick()
            compose.onNodeWithText(item.title).performClick()
            compose.onNodeWithText(item.lines.first().text).assertIsDisplayed()
            compose.waitUntil(10000) { library.state.value.readerOpen == null && !library.state.value.saving }
            assertTrue(requireNotNull(library.state.value.personal).recentHistory.entries.isEmpty())
        }
    }
}
