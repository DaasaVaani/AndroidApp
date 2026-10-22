package `in`.daasavaani.app

import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ListReciteUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "10000000-0000-4000-8000-000000000001"
    private val secondId = "10000000-0000-4000-8000-000000000002"
    private val missingId = "10000000-0000-4000-8000-000000000099"
    private val reference = PersonalReference.lyric(id)
    private val literal = "  Synthetic ಕ 😀 <b>  \n \t\n\nline | literal\t "
    private val placeholder = "  repeated <b> 😀  "
    private lateinit var model: LibraryViewModel
    private lateinit var real: LyricPreviewViewModel
    private lateinit var nativeView: View
    private lateinit var source: Source

    private inner class Source : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000, 4, 10, 100, 5, 128, 16))
        var requests = 0
        var offline = false
        val summaries = listOf(id, secondId).mapIndexed { index, lyricId ->
            LyricSummary(lyricId, "Synthetic ordered $index", emptyList(),
                ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20))
        }
        val lines = listOf(PreviewLine("line", "  Synthetic ಕ 😀 <b>  ", "20000000-0000-4000-8000-000000000001"),
            PreviewLine("layout", " \t"), PreviewLine("layout", ""),
            PreviewLine("line", "line | literal\t ", "20000000-0000-4000-8000-000000000002"))
        override suspend fun catalog(): LyricCatalog {
            check(!offline)
            requests++
            return LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"), summaries)
        }
        override suspend fun item(summary: LyricSummary): PreviewLyric {
            check(!offline)
            requests++
            val itemLines = if (summary.lyricId == id) lines else lines.map { line ->
                line.copy(lineId = line.lineId?.replace("20000000", "30000000"))
            }
            return PreviewLyric("daasavaani-lyric", 1, summary.lyricId, "kn", summary.title, summary.tags, itemLines)
        }
    }

    @After fun cleanup() { context.deleteDatabase("personal.db") }
    private fun seed(entries: List<ListEntry>, history: Boolean = false): RecitationList {
        context.deleteDatabase("personal.db")
        val list = RecitationList.create("Synthetic ordered list", entries)
        PersonalStore(context).use { store ->
            var s = store.change(store.load(), language = "en", theme = "warm", reference = reference)
            s = store.history(s, enabled = history)
            s = store.learning(s, reference, "wantToLearn")
            store.saveList(s.revision, s.epoch, list)
        }
        return list
    }
    private fun launch(list: RecitationList): ActivityScenario<MainActivity> {
        source = Source()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            model = ViewModelProvider(activity)[LibraryViewModel::class.java]
            real = LyricPreviewViewModel(activity.application, source)
            activity.setContent {
                val view = LocalView.current
                SideEffect { nativeView = view }
                MaterialTheme { LibraryScreen(model, real) }
            }
            model.section("lists"); model.selectList(list.id)
        }
        saved()
        return scenario
    }
    private fun saved() = compose.waitUntil(10000) { model.state.value.personal != null && !model.state.value.saving }
    private fun prepare(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { real.load() }
        compose.waitUntil(10000) { real.state.value.prepared != null && !real.state.value.loading }
    }
    private fun enter() {
        compose.onNodeWithTag("saved-lists").performScrollToNode(hasTestTag("recite-list"))
        compose.onNodeWithTag("recite-list").performClick()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("list-recite").fetchSemanticsNodes().isNotEmpty() && !model.state.value.saving
        }
        compose.onNodeWithTag("exit-recite").assertIsDisplayed()
        compose.onNodeWithTag("screen-awake").assertIsOff()
        compose.onNodeWithTag("recite-notice").assertTextEquals(context.getString(R.string.list_recite_notice))
    }
    private fun position(index: Int, total: Int) {
        compose.onNodeWithTag("list-recite-position").assertTextEquals(context.getString(R.string.entry_number)
            .replace("{position}", index.toString()).replace("{total}", total.toString()))
    }
    private fun next() { compose.onNodeWithTag("next-entry").performClick(); compose.waitForIdle(); saved() }
    private fun previous() { compose.onNodeWithTag("previous-entry").performClick(); compose.waitForIdle(); saved() }
    private fun expose() {
        compose.onNodeWithTag("list-recite-content").performScrollToNode(hasTestTag("lyric-text"))
        compose.onNodeWithTag("lyric-text").assertTextEquals(literal)
        compose.waitForIdle(); saved()
    }
    private fun click(caption: Int) = compose.onNode(hasText(context.getString(caption)) and hasClickAction()).performClick()
    private fun back(scenario: ActivityScenario<MainActivity>) {
        compose.waitForIdle()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; compose.waitForIdle()
    }
    private fun snapshot() = PersonalStore(context).use { it.load() }
    private fun layout(): TextLayoutResult {
        val result = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("lyric-text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        return result.single()
    }

    @Test fun preparationGatePreservesManagementPendingPreviewAndExplicitEmptyAndSingleBounds() {
        val list = seed(listOf(reference.entry(), ListEntry("placeholder", text = placeholder)))
        launch(list).use { scenario ->
            compose.onNodeWithTag("recite-list").assertIsNotEnabled()
            assertNull(model.state.value.prepared); assertNull(real.state.value.prepared)
            compose.onNodeWithTag("saved-lists").performScrollToNode(hasText(placeholder))
            compose.onNodeWithText(placeholder).assertExists()
            compose.onNodeWithTag("saved-lists").performScrollToNode(hasText(context.getString(R.string.open)) and hasClickAction())
            click(R.string.open)
            compose.waitUntil(10000) { model.state.value.section == "lyrics" && real.state.value.pendingLyricId == id }
            compose.onNodeWithTag("lyric-preview").assertExists()
            assertNull(real.state.value.prepared)
            assertEquals(0, source.requests)
            back(scenario)
            compose.waitUntil(10000) { model.state.value.section == "lists" }
            assertEquals(list.id, model.state.value.selectedListId)
            prepare(scenario)
            val before = snapshot()
            val empty = RecitationList.create("Empty")
            scenario.onActivity { model.saveList(requireNotNull(model.state.value.personal), empty) { model.selectList(empty.id) } }
            compose.waitUntil(10000) { model.state.value.selectedListId == empty.id && !model.state.value.saving }
            val withEmpty = snapshot()
            enter()
            compose.onNodeWithTag("list-recite-empty").assertTextEquals(context.getString(R.string.empty_recitation))
            compose.onNodeWithTag("list-recite-position").assertDoesNotExist()
            compose.onNodeWithTag("previous-entry").assertDoesNotExist()
            compose.onNodeWithTag("next-entry").assertDoesNotExist()
            back(scenario)
            assertEquals(withEmpty, snapshot())
            val one = list.copy(entries = list.entries.take(1))
            scenario.onActivity { model.saveList(requireNotNull(model.state.value.personal), one) { model.selectList(one.id) } }
            compose.waitUntil(10000) { model.state.value.selectedListId == one.id && !model.state.value.saving }
            enter(); expose(); position(1, 1)
            compose.onNodeWithTag("previous-entry").assertIsNotEnabled()
            compose.onNodeWithTag("next-entry").assertIsNotEnabled()
            compose.onNodeWithTag("list-recite-start").assertExists()
            compose.onNodeWithTag("list-recite-end").assertExists()
            assertEquals(before.favorites, snapshot().favorites)
            assertFalse(snapshot().recentHistory.enabled)
        }
    }

    @Test fun orderedConsecutiveCanonicalPlaceholderAndUuidLegacyNeverDeduplicateSkipOrChangePersonalState() {
        val entries = listOf(reference.entry("  fallback  "), reference.entry("  fallback  "),
            ListEntry("placeholder", text = placeholder), ListEntry("placeholder", text = placeholder),
            PersonalReference.legacy(id).entry("  legacy fallback  "), PersonalReference.lyric(missingId).entry("  missing fallback  "),
            PersonalReference.lyric(secondId).entry())
        val list = seed(entries)
        launch(list).use { scenario ->
            prepare(scenario)
            val before = snapshot()
            source.offline = true
            enter(); expose(); position(1, entries.size)
            val style = layout().layoutInput.style
            compose.onNodeWithTag("previous-entry").assertIsNotEnabled()
            next(); expose(); position(2, entries.size)
            assertEquals(style, layout().layoutInput.style)
            compose.onNodeWithTag("list-recite-fallback").assertTextEquals("  fallback  ")
            next(); position(3, entries.size)
            compose.onNodeWithTag("list-recite-placeholder").assertTextEquals(placeholder)
            compose.onNodeWithTag("list-recite-unavailable").assertTextEquals(context.getString(R.string.lyrics_unavailable))
            next(); position(4, entries.size)
            compose.onNodeWithTag("list-recite-placeholder").assertTextEquals(placeholder)
            next(); position(5, entries.size)
            compose.onNodeWithTag("list-recite-reference").assertTextEquals(id)
            compose.onNodeWithTag("list-recite-fallback").assertTextEquals("  legacy fallback  ")
            compose.onNodeWithText(context.getString(R.string.legacy_reference)).assertExists()
            compose.onNodeWithTag("lyric-text").assertDoesNotExist()
            compose.onNodeWithTag("list-recite-unavailable").assertExists()
            next(); position(6, entries.size)
            compose.onNodeWithTag("list-recite-reference").assertTextEquals(missingId)
            compose.onNodeWithTag("list-recite-fallback").assertTextEquals("  missing fallback  ")
            next(); expose(); position(7, entries.size)
            compose.onNodeWithTag("next-entry").assertIsNotEnabled()
            compose.onNodeWithTag("list-recite-end").assertExists()
            previous(); position(6, entries.size)
            back(scenario)
            assertEquals(list.id, model.state.value.selectedListId)
            compose.onNodeWithTag("saved-lists").assertExists()
            assertEquals(before, snapshot())
            assertEquals(3, source.requests)
            assertEquals(source.lines, real.state.value.prepared?.items?.get(id)?.lines)
            enter(); position(1, entries.size)
            compose.onNodeWithTag("exit-recite").performClick()
            assertEquals(before, snapshot())
        }
    }

    @Test fun successfulExplicitOccurrencesUseDistinctBoundedHistoryAndPreferencesNeverReplayClearedHistory() {
        val list = seed(listOf(reference.entry(), reference.entry(), ListEntry("placeholder", text = placeholder),
            PersonalReference.lyric(missingId).entry(), PersonalReference.lyric(secondId).entry()), history = true)
        launch(list).use { scenario ->
            prepare(scenario); enter(); expose()
            compose.waitUntil(10000) { model.state.value.personal?.recentHistory?.entries == listOf(reference) && !model.state.value.saving }
            val first = snapshot()
            next(); expose(); assertEquals(first, snapshot())
            next(); assertEquals(first, snapshot())
            next(); assertEquals(first, snapshot())
            next(); expose()
            val second = PersonalReference.lyric(secondId)
            compose.waitUntil(10000) { model.state.value.personal?.recentHistory?.entries == listOf(second, reference) && !model.state.value.saving }
            val opened = snapshot()
            PersonalStore(context).use { it.history(it.load(), clear = true) }
            scenario.onActivity { model.retryPersonal() }; saved()
            val cleared = snapshot()
            compose.onNodeWithTag("screen-awake").performClick()
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            compose.onNodeWithTag("list-recite-content").performScrollToNode(hasTestTag("font-increase"))
            compose.onNodeWithTag("font-increase").performClick()
            compose.waitUntil(10000) { model.state.value.personal?.reading?.fontSizeSp ==
                cleared.reading.fontSizeSp + ReadingPolicy.readerFontStepSp && !model.state.value.saving }
            compose.onNodeWithTag("list-recite-content").performScrollToNode(hasTestTag("density-relaxed"))
            compose.onNodeWithTag("density-relaxed").performClick()
            compose.waitUntil(10000) { model.state.value.personal?.reading?.density == "relaxed" && !model.state.value.saving }
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            expose()
            val changed = snapshot()
            assertEquals(cleared.copy(revision = changed.revision, reading = changed.reading), changed)
            assertEquals(changed.reading.fontSizeSp.toFloat(), layout().layoutInput.style.fontSize.value)
            assertEquals(changed.reading.lineHeightSp(), layout().layoutInput.style.lineHeight.value)
            assertEquals(opened.lists, changed.lists)
            assertTrue(changed.recentHistory.entries.isEmpty())
            back(scenario)
            assertEquals(changed, snapshot())
        }
    }

    @Test fun oneAwakeOwnerSurvivesEntryReplacementButBackgroundExitSettingsRouteAndRestartRelease() {
        val list = seed(listOf(reference.entry(), reference.entry(), ListEntry("placeholder", text = placeholder)))
        launch(list).use { scenario ->
            prepare(scenario)
            val before = snapshot()
            enter()
            compose.onNodeWithTag("screen-awake").performClick()
            scenario.onActivity { assertTrue(nativeView.keepScreenOn) }
            repeat(2) {
                next()
                compose.onNodeWithTag("screen-awake").assertIsOn()
                compose.onNodeWithTag("screen-awake-status").assertTextEquals(context.getString(R.string.screen_awake_active))
                scenario.onActivity { assertTrue(nativeView.keepScreenOn) }
            }
            previous()
            compose.onNodeWithTag("screen-awake").assertIsOn()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("exit-recite").assertExists()
            compose.onNodeWithTag("screen-awake").assertIsOff()
            scenario.onActivity { assertFalse(nativeView.keepScreenOn) }
            compose.onNodeWithTag("screen-awake").performClick()
            back(scenario)
            scenario.onActivity { assertFalse(nativeView.keepScreenOn) }
            enter(); position(1, 3)
            compose.onNodeWithTag("screen-awake").performClick()
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            click(R.string.settings)
            compose.waitUntil(10000) { model.state.value.section == "settings" }
            compose.onNodeWithTag("exit-recite").assertDoesNotExist()
            scenario.onActivity { assertFalse(nativeView.keepScreenOn); model.section("lists") }
            compose.onNodeWithTag("exit-recite").assertDoesNotExist()
            enter()
            compose.onNodeWithTag("screen-awake").performClick()
            scenario.onActivity { model.section("favorites") }
            compose.onNodeWithTag("exit-recite").assertDoesNotExist()
            scenario.onActivity { assertFalse(nativeView.keepScreenOn); model.section("lists") }
            enter()
            scenario.recreate()
            compose.onNodeWithTag("exit-recite").assertDoesNotExist()
            assertEquals(before, snapshot())
        }
    }

    @Test fun priorNativeOwnerIsPreservedAcrossAllOccurrencesAndExit() {
        val list = seed(listOf(reference.entry(), ListEntry("placeholder", text = placeholder)))
        launch(list).use { scenario ->
            prepare(scenario)
            scenario.onActivity { nativeView.keepScreenOn = true }
            enter(); compose.onNodeWithTag("screen-awake").performClick()
            next()
            compose.onNodeWithTag("screen-awake").assertIsOn()
            back(scenario)
            scenario.onActivity { assertTrue(nativeView.keepScreenOn); nativeView.keepScreenOn = false }
        }
    }

    @Test fun externalContentOrderIdentityChangeOrDeletionEndsExplicitlyWithoutFalseRestoration() {
        val list = seed(listOf(reference.entry(), reference.entry(), ListEntry("placeholder", text = placeholder)))
        launch(list).use { scenario ->
            prepare(scenario); enter(); next()
            compose.onNodeWithTag("screen-awake").performClick()
            val reordered = list.copy(entries = list.entries.reversed())
            PersonalStore(context).use { store ->
                val s = store.load()
                store.saveList(s.revision, s.epoch, reordered)
                compose.waitUntil(10000) { compose.onAllNodesWithTag("list-recite-stale").fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithTag("list-recite-stale").assertExists()
            compose.onNodeWithTag("exit-recite").assertDoesNotExist()
            scenario.onActivity { assertFalse(nativeView.keepScreenOn) }
            assertEquals(list.id, model.state.value.selectedListId)
            enter(); position(1, 3)
            compose.onNodeWithTag("list-recite-placeholder").assertTextEquals(placeholder)
            val replaced = reordered.copy(entries = list.entries)
            PersonalStore(context).use { store ->
                val s = store.load()
                store.saveList(s.revision, s.epoch, replaced)
                compose.waitUntil(10000) { compose.onAllNodesWithTag("list-recite-stale").fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithTag("list-recite-stale").assertExists()
            enter(); position(1, 3)
            PersonalStore(context).use { store ->
                val s = store.load()
                store.deleteList(s.revision, s.epoch, list.id)
                compose.waitUntil(10000) { compose.onAllNodesWithTag("list-recite-stale").fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithTag("list-recite-stale").assertExists()
            compose.onNodeWithTag("exit-recite").assertDoesNotExist()
            compose.onNodeWithTag("saved-lists").assertExists()
            compose.onNodeWithTag("recite-list").assertDoesNotExist()
            assertTrue(snapshot().lists.isEmpty())
            assertTrue(snapshot().recentHistory.entries.isEmpty())
        }
    }

    @Test fun preparedMixedCanonicalAndFixturePreserveLiteralIdsAndOfflineHistoryRules() {
        val list = seed(listOf(reference.entry()), history = true)
        launch(list).use { scenario ->
            prepare(scenario)
            scenario.onActivity { model.prepare() }
            compose.waitUntil(60000) { model.state.value.prepared != null && !model.state.value.loading }
            val fixture = requireNotNull(model.state.value.prepared).items.first()
            val legacy = PersonalReference.legacy(fixture.videoId)
            val mixed = list.copy(entries = RecitationList.create("unused", listOf(reference.entry(),
                legacy.entry("  fixture fallback  "), legacy.entry("  fixture fallback  "),
                ListEntry("placeholder", text = placeholder), reference.entry())).entries)
            scenario.onActivity { model.saveList(requireNotNull(model.state.value.personal), mixed) }
            compose.waitUntil(10000) { model.state.value.personal?.lists?.single() == mixed && !model.state.value.saving }
            source.offline = true
            enter(); expose()
            compose.waitUntil(10000) { model.state.value.personal?.recentHistory?.entries == listOf(reference) && !model.state.value.saving }
            next(); position(2, 5)
            val tag = "fixture-line-${fixture.lines.first().id}"
            compose.onNodeWithTag("list-recite-content").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertTextEquals(fixture.lines.first().text)
            compose.waitUntil(10000) { model.state.value.personal?.recentHistory?.entries == listOf(legacy, reference) && !model.state.value.saving }
            val firstFixture = snapshot()
            next(); position(3, 5)
            compose.onNodeWithTag("list-recite-content").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertTextEquals(fixture.lines.first().text)
            compose.waitForIdle(); saved()
            assertEquals(firstFixture, snapshot())
            next(); assertEquals(firstFixture, snapshot())
            next(); expose()
            compose.waitUntil(10000) { model.state.value.personal?.recentHistory?.entries == listOf(reference, legacy) && !model.state.value.saving }
            assertEquals(mixed, snapshot().lists.single())
            assertEquals(fixture.lines, model.state.value.prepared?.items?.first()?.lines)
            assertEquals(source.lines, real.state.value.prepared?.items?.get(id)?.lines)
            assertEquals(3, source.requests)
        }
    }
}
