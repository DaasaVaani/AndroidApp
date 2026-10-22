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

class ReciteUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "10000000-0000-4000-8000-000000000001"
    private val reference = PersonalReference.lyric(id)
    private val literal = "  Synthetic ಕ 😀 <b>  \n \t\n\nline | literal\t "
    private inner class Source : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000, 3, 10, 100, 5, 128, 16))
        var requests = 0
        val summary = LyricSummary(id, "Synthetic Recite", emptyList(),
            ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20))
        val lines = listOf(PreviewLine("line", "  Synthetic ಕ 😀 <b>  ", "20000000-0000-4000-8000-000000000001"),
            PreviewLine("layout", " \t"), PreviewLine("layout", ""),
            PreviewLine("line", "line | literal\t ", "20000000-0000-4000-8000-000000000002"))
        override suspend fun catalog(): LyricCatalog {
            requests++
            return LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"), listOf(summary))
        }
        override suspend fun item(summary: LyricSummary): PreviewLyric {
            requests++
            return PreviewLyric("daasavaani-lyric", 1, id, "kn", summary.title, summary.tags, lines)
        }
    }
    @After fun cleanup() { context.deleteDatabase("personal.db") }
    private fun seed(): PersonalSnapshot {
        context.deleteDatabase("personal.db")
        return PersonalStore(context).use { store ->
            var s = store.change(store.load(), language = "en", theme = "warm", reference = reference)
            s = store.saveList(s.revision, s.epoch, RecitationList.create("Synthetic list", listOf(reference.entry(), reference.entry())))
            s = store.learning(s, reference, "wantToLearn")
            store.history(s, opened = reference)
        }
    }
    private fun saved(model: LibraryViewModel) {
        compose.waitUntil(10000) { model.state.value.personal != null && !model.state.value.saving }
    }
    private fun expose(model: LibraryViewModel, real: LyricPreviewViewModel) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
        compose.onNodeWithTag("lyric-text").assertTextEquals(literal)
        compose.waitUntil(10000) { real.state.value.readerOpen == null && !model.state.value.saving }
    }
    private fun back(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun click(caption: Int) = compose.onNode(hasText(context.getString(caption)) and hasClickAction()).performClick()
    private fun enter() {
        compose.onNodeWithTag("enter-recite").performClick()
        compose.onNodeWithTag("exit-recite").assertIsDisplayed()
        compose.onNodeWithTag("recite-notice").assertTextEquals(context.getString(R.string.recite_notice))
        compose.onNodeWithTag("screen-awake").assertIsOff()
    }
    private fun layout(): TextLayoutResult {
        val result = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("lyric-text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(result) }
        return result.single()
    }

    @Test fun canonicalExplicitFocusRestoresControlsLiteralIdentityPreferencesAndNoHistoryReplay() {
        seed()
        val source = Source()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source)
                it.setContent { MaterialTheme { LibraryScreen(model, real) } }
            }
            saved(model)
            compose.onNodeWithTag("enter-recite").assertDoesNotExist()
            scenario.onActivity { real.load(); real.request(id); model.section("lyrics") }
            compose.waitUntil(10000) { real.state.value.item != null }
            expose(model, real)
            val original = layout().layoutInput.style
            val cleared = PersonalStore(context).use { store -> store.history(store.load(), clear = true) }
            scenario.onActivity { model.retryPersonal() }
            saved(model)
            enter()
            compose.onNodeWithTag("owner-lyrics").assertDoesNotExist()
            compose.onNodeWithTag("show-another-lyric").assertDoesNotExist()
            compose.onNodeWithTag("font-increase").assertDoesNotExist()
            expose(model, real)
            assertEquals(original, layout().layoutInput.style)
            assertEquals(source.lines, real.state.value.item?.lines)
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("font-increase"))
            compose.onNodeWithTag("font-increase").performClick()
            compose.waitUntil(10000) { !model.state.value.saving &&
                model.state.value.personal?.reading?.fontSizeSp == cleared.reading.fontSizeSp + ReadingPolicy.readerFontStepSp }
            val changed = requireNotNull(model.state.value.personal)
            assertEquals(cleared.copy(revision = changed.revision, reading = changed.reading), changed)
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            expose(model, real)
            back(scenario)
            compose.onNodeWithTag("enter-recite").assertIsDisplayed()
            assertNotNull(real.state.value.selected)
            assertEquals(changed, model.state.value.personal)
            enter()
            compose.onNodeWithTag("exit-recite").performClick()
            expose(model, real)
            assertEquals(changed, PersonalStore(context).use { it.load() })
            assertEquals(2, source.requests)
            back(scenario)
            assertNull(real.state.value.selected)
        }
    }

    @Test fun actualNativeFlagBackgroundExitReplacementNavigationAndBaselineOwnership() {
        seed()
        val source = Source()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            lateinit var nativeView: View
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source)
                it.setContent {
                    val view = LocalView.current
                    SideEffect { nativeView = view }
                    MaterialTheme { LibraryScreen(model, real) }
                }
                real.load(); real.request(id); model.section("lyrics")
            }
            compose.waitUntil(10000) { real.state.value.item != null }
            expose(model, real)
            val before = model.state.value.personal
            scenario.onActivity { assertFalse(nativeView.keepScreenOn) }
            enter()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("screen-awake").assertIsOff()
            compose.onNodeWithTag("screen-awake-status").assertTextEquals(context.getString(R.string.screen_awake_inactive))
            compose.onNodeWithTag("screen-awake").performClick().assertIsOn()
            scenario.onActivity { assertTrue(nativeView.keepScreenOn) }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity { assertFalse(nativeView.keepScreenOn) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("screen-awake").assertIsOff()
            compose.onNodeWithTag("screen-awake-status").assertTextEquals(context.getString(R.string.screen_awake_released))
            scenario.onActivity { assertFalse(nativeView.keepScreenOn) }
            compose.onNodeWithTag("screen-awake").performClick()
            compose.onNodeWithTag("exit-recite").performClick()
            scenario.onActivity { assertFalse(nativeView.keepScreenOn) }
            enter()
            compose.onNodeWithTag("screen-awake").performClick()
            scenario.onActivity { real.select(source.summary) }
            expose(model, real)
            compose.onNodeWithTag("enter-recite").assertIsDisplayed()
            scenario.onActivity { assertFalse(nativeView.keepScreenOn); nativeView.keepScreenOn = true }
            enter()
            compose.onNodeWithTag("screen-awake").performClick().assertIsOn()
            compose.onNodeWithTag("screen-awake").performClick().assertIsOff()
            scenario.onActivity { assertTrue(nativeView.keepScreenOn) }
            compose.onNodeWithTag("screen-awake").performClick()
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            click(R.string.settings)
            compose.onNode(hasText(context.getString(R.string.transfer_title)) and hasClickAction()).assertIsDisplayed()
            scenario.onActivity { assertTrue(nativeView.keepScreenOn); nativeView.keepScreenOn = false }
            back(scenario)
            compose.onNodeWithTag("enter-recite").assertIsDisplayed()
            enter()
            compose.onNodeWithTag("screen-awake").performClick()
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            click(R.string.library)
            compose.onNodeWithTag("exit-recite").assertDoesNotExist()
            scenario.onActivity { assertFalse(nativeView.keepScreenOn) }
            assertEquals(before, model.state.value.personal)
            assertEquals(2, source.requests)
        }
    }

    @Test fun collectionAndUnsavedPreviewOriginsRetainedByFirstBackAndSettings() {
        for (origin in listOf("lists", "favorites", "recents", "learning", "transfer")) {
            val before = seed()
            val source = Source()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: LibraryViewModel
                lateinit var real: LyricPreviewViewModel
                lateinit var transfer: TransferViewModel
                val snapshot = ListSnapshot("Synthetic unsaved", listOf(reference.entry(), ListEntry("placeholder", text = " literal ")))
                scenario.onActivity {
                    model = ViewModelProvider(it)[LibraryViewModel::class.java]
                    real = LyricPreviewViewModel(it.application, source)
                    transfer = ViewModelProvider(it)[TransferViewModel::class.java]
                    it.setContent { MaterialTheme { LibraryScreen(model, real, transfer) } }
                    real.load()
                }
                compose.waitUntil(10000) { real.state.value.prepared != null && model.state.value.personal != null }
                scenario.onActivity {
                    if (origin == "lists") model.selectList(before.lists.single().id)
                    if (origin == "transfer") transfer.showSnapshot(snapshot)
                    model.section(origin)
                }
                if (origin == "transfer") compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(R.string.open)))
                if (origin == "learning") compose.onNodeWithTag("learning-read-lyric-$id").performClick()
                else if (origin == "lists") compose.onAllNodes(
                    hasText(context.getString(R.string.open)) and hasClickAction()).onFirst().performClick()
                else click(R.string.open)
                expose(model, real)
                val opened = model.state.value.personal
                enter()
                back(scenario)
                assertEquals("lyrics", model.state.value.section)
                assertNotNull(real.state.value.selected)
                enter()
                compose.onNodeWithTag("toggle-reading-controls").performClick()
                click(R.string.settings)
                compose.onNode(hasText(context.getString(R.string.transfer_title)) and hasClickAction()).assertIsDisplayed()
                back(scenario)
                compose.onNodeWithTag("enter-recite").assertIsDisplayed()
                back(scenario)
                compose.waitUntil(10000) { model.state.value.section == origin }
                assertEquals(opened, model.state.value.personal)
                assertEquals(before.lists, model.state.value.personal?.lists)
                if (origin == "lists") assertEquals(before.lists.single().id, model.state.value.selectedListId)
                if (origin == "transfer") assertEquals(snapshot, transfer.state.value.snapshot)
                assertEquals(2, source.requests)
            }
        }
    }

    @Test fun fixtureExplicitFocusRestoresControlsLearningOriginAndUnchangedLines() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java] }
            saved(model)
            click(R.string.prepare_library)
            compose.waitUntil(60000) { model.state.value.prepared != null }
            val item = requireNotNull(model.state.value.prepared).items.first()
            val reference = PersonalReference.legacy(item.videoId)
            scenario.onActivity { model.changeLearning(reference, "wantToLearn") }
            compose.waitUntil(10000) { model.state.value.personal?.learning?.any { it.reference == reference } == true && !model.state.value.saving }
            scenario.onActivity { model.section("learning"); model.fixtureRequest(reference) }
            compose.waitUntil(10000) { model.state.value.readerOpen == null && !model.state.value.saving }
            val before = requireNotNull(model.state.value.personal)
            val tag = "fixture-line-${item.lines.first().id}"
            enter()
            compose.onNodeWithTag(tag).assertTextEquals(item.lines.first().text)
            compose.onNodeWithTag("font-increase").assertDoesNotExist()
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            compose.onNodeWithTag("font-increase").performClick()
            compose.waitUntil(10000) { model.state.value.personal?.reading?.fontSizeSp ==
                before.reading.fontSizeSp + ReadingPolicy.readerFontStepSp && !model.state.value.saving }
            val changed = requireNotNull(model.state.value.personal)
            assertEquals(before.copy(revision = changed.revision, reading = changed.reading), changed)
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            back(scenario)
            assertEquals(item, model.state.value.selected)
            enter()
            compose.onNodeWithTag("toggle-reading-controls").performClick()
            click(R.string.settings)
            compose.onNode(hasText(context.getString(R.string.transfer_title)) and hasClickAction()).assertIsDisplayed()
            back(scenario)
            compose.onNodeWithTag("enter-recite").assertIsDisplayed()
            compose.onNodeWithTag(tag).assertTextEquals(item.lines.first().text)
            assertEquals(item.lines, model.state.value.selected?.lines)
            back(scenario)
            compose.waitUntil(10000) { model.state.value.section == "learning" && model.state.value.selected == null }
            assertEquals(changed, PersonalStore(context).use { it.load() })
        }
    }

    @Test fun adapterDetachedDeniedRejectedAndReleaseFailuresNeverReportFalseSuccess() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertEquals(AwakeStatus.UNSUPPORTED, ViewScreenAwake(View(context)).request())
            val view = object : View(context) {
                var deny = false
                var reject = false
                override fun isAttachedToWindow() = true
                override fun setKeepScreenOn(keepScreenOn: Boolean) {
                    if (deny) throw SecurityException("Synthetic refusal")
                    if (!reject) super.setKeepScreenOn(keepScreenOn)
                }
            }
            val adapter = ViewScreenAwake(view)
            view.deny = true
            assertEquals(AwakeStatus.FAILED, adapter.request())
            assertFalse(adapter.requested)
            view.deny = false; view.reject = true
            assertEquals(AwakeStatus.FAILED, adapter.request())
            assertFalse(adapter.requested)
            view.reject = false
            assertEquals(AwakeStatus.ACTIVE, adapter.request())
            assertTrue(view.keepScreenOn)
            view.deny = true
            assertEquals(AwakeStatus.FAILED, adapter.release())
            assertTrue(adapter.requested)
            assertTrue(view.keepScreenOn)
            view.deny = false
            assertEquals(AwakeStatus.RELEASED, adapter.release())
            assertFalse(view.keepScreenOn)
            assertFalse(adapter.requested)
        }
    }

    @Test fun unavailableAndFailedCaptionsRetainReciteAndActualOwnedRequestUntilReleaseRetry() {
        seed()
        val view = object : View(context) {
            var attached = false
            var deny = false
            override fun isAttachedToWindow() = attached
            override fun setKeepScreenOn(keepScreenOn: Boolean) {
                if (deny) throw SecurityException("Synthetic refusal")
                super.setKeepScreenOn(keepScreenOn)
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {
                val state = ReciteState()
                it.setContent {
                    MaterialTheme { ReciteControls(state, exposed = true, view = view) }
                }
            }
            enter()
            compose.onNodeWithTag("screen-awake").performClick().assertIsOff()
            compose.onNodeWithTag("screen-awake-status").assertTextEquals(context.getString(R.string.screen_awake_unsupported))
            scenario.onActivity { view.attached = true; view.deny = true }
            compose.onNodeWithTag("screen-awake").performClick().assertIsOff()
            compose.onNodeWithTag("screen-awake-status").assertTextEquals(context.getString(R.string.screen_awake_failed))
            scenario.onActivity { view.deny = false }
            compose.onNodeWithTag("screen-awake").performClick().assertIsOn()
            scenario.onActivity { view.deny = true }
            compose.onNodeWithTag("screen-awake").performClick().assertIsOn()
            compose.onNodeWithTag("screen-awake-status").assertTextEquals(context.getString(R.string.screen_awake_failed))
            compose.onNodeWithTag("exit-recite").assertIsDisplayed()
            scenario.onActivity { assertTrue(view.keepScreenOn); view.deny = false }
            compose.onNodeWithTag("screen-awake").performClick().assertIsOff()
            compose.onNodeWithTag("screen-awake-status").assertTextEquals(context.getString(R.string.screen_awake_released))
            scenario.onActivity { assertFalse(view.keepScreenOn) }
            compose.onNodeWithTag("exit-recite").performClick()
            compose.onNodeWithTag("enter-recite").assertIsDisplayed()
        }
    }
}
