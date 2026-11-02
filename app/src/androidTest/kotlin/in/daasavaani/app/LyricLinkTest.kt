package `in`.daasavaani.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricLinkTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun id(i: Int) = "10000000-0000-4000-8000-${i.toString().padStart(12, '0')}"
    private fun reference(i: Int) = PersonalReference.lyric(id(i))
    private fun link(i: Int) = context.getString(R.string.canonical_base_url) + "#/lyrics/" + id(i)
    private fun literal(i: Int) = "  Synthetic literal ಕ [$i] <b> 😀  \n \t\n"
    private inner class Source : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000,
            10, 10, 100, 5, 128, 16))
        var requests = 0
        val summaries = (1..2).map { LyricSummary(id(it), "Synthetic private title [$it]", listOf("private"),
            ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20)) }
        val catalog = LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"), summaries)
        val items = summaries.mapIndexed { index, summary ->
            PreviewLyric("daasavaani-lyric", 1, summary.lyricId, "kn", summary.title, summary.tags,
                listOf(PreviewLine("line", literal(index + 1).substringBefore('\n'), "2${summary.lyricId.drop(1)}"),
                    PreviewLine("layout", " \t"), PreviewLine("layout", "")))
        }.associateBy { it.lyricId }
        override suspend fun catalog(): LyricCatalog { requests++; return catalog }
        override suspend fun item(summary: LyricSummary): PreviewLyric { requests++; return items.getValue(summary.lyricId) }
        val prepared get() = PreparedLyricPreview(catalog, items)
        fun state(i: Int) = LyricPreviewState(prepared = prepared, selected = summaries[i - 1], item = items.getValue(id(i)))
    }

    @After fun cleanup() { context.deleteDatabase("personal.db") }
    private fun seed(): PersonalSnapshot {
        context.deleteDatabase("personal.db")
        return PersonalStore(context).use { store ->
            val initial = store.change(store.load(), language = "en", theme = "warm", reference = reference(2))
            store.saveList(initial.revision, initial.epoch,
                RecitationList.create("Synthetic private list", listOf(reference(1).entry())))
        }
    }
    private fun scroll(tag: String) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag(tag))
    }
    private fun click(tag: String) { scroll(tag); compose.onNodeWithTag(tag).performClick() }

    @Suppress("DEPRECATION")
    private fun assertChooser(chooser: Intent, expected: String) {
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertNull(chooser.data); assertNull(chooser.component); assertNull(chooser.`package`)
        assertFalse(chooser.hasExtra(Intent.EXTRA_INITIAL_INTENTS))
        val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(setOf(Intent.EXTRA_TEXT), send.extras?.keySet())
        assertEquals(expected, send.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(send.data); assertNull(send.clipData); assertNull(send.component); assertNull(send.`package`)
        assertEquals(0, send.flags)
    }
    private class ChooserMonitor : Instrumentation.ActivityMonitor() {
        val sent = mutableListOf<Intent>()
        var failure: RuntimeException? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            sent += Intent(intent)
            failure?.let { throw it }
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    @Test fun strictPlainTextChooserCopyFailuresAndUnexpectedBugs() {
        for (i in 1..2) {
            var message: Int? = null
            var sent: Intent? = null
            shareCanonicalLink(link(i), { sent = it }, R.string.lyric_link_shared, R.string.lyric_share_failed) { message = it }
            assertChooser(requireNotNull(sent), link(i))
            assertEquals(R.string.lyric_link_shared, message)
            var copied: String? = null
            copyLyricLink(link(i), { copied = it }) { message = it }
            assertEquals(link(i), copied)
            assertEquals(R.string.lyric_link_copied, message)
            val clip = lyricLinkClip(context, link(i))
            assertEquals(context.getString(R.string.lyric_link), clip.description.label.toString())
            assertEquals(1, clip.itemCount)
            assertEquals(link(i), clip.getItemAt(0).text.toString())
            assertNull(clip.getItemAt(0).htmlText); assertNull(clip.getItemAt(0).intent); assertNull(clip.getItemAt(0).uri)
            assertEquals(listOf("text/plain"), (0 until clip.description.mimeTypeCount).map { clip.description.getMimeType(it) })
        }
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            var message: Int? = null
            shareCanonicalLink(link(1), { throw failure }, R.string.lyric_link_shared, R.string.lyric_share_failed) { message = it }
            assertEquals(R.string.lyric_share_failed, message)
        }
        for (failure in listOf(SecurityException(), IllegalStateException())) {
            var message: Int? = null
            copyLyricLink(link(1), { throw failure }) { message = it }
            assertEquals(R.string.lyric_share_failed, message)
        }
        assertThrows(IllegalArgumentException::class.java) {
            copyLyricLink(link(1), { throw IllegalArgumentException("Synthetic bug") }) { fail("False feedback") }
        }
        assertThrows(IllegalStateException::class.java) {
            shareCanonicalLink(link(1), { throw IllegalStateException("Synthetic bug") },
                R.string.lyric_link_shared, R.string.lyric_share_failed) { fail("False feedback") }
        }
    }

    @Test fun explicitPreparationListOriginCopyCancelledChooserFailuresAndBackNeverWriteOrFetch() {
        val before = seed()
        val source = Source()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source)
                it.setContent { MaterialTheme { LibraryScreen(model, real) } }
            }
            compose.waitUntil(10000) { model.state.value.personal != null }
            scenario.onActivity { model.selectList(before.lists.single().id); model.section("lists") }
            compose.onNodeWithText(context.getString(R.string.open)).performClick()
            compose.onNodeWithTag("lyric-link-controls").assertDoesNotExist()
            assertEquals(id(1), real.state.value.pendingLyricId)
            assertEquals(0, source.requests)
            assertEquals(before, model.state.value.personal)
            click("lyric-load")
            compose.waitUntil(10000) { real.state.value.item != null }
            scroll("lyric-text")
            compose.onNodeWithTag("lyric-text").assertTextEquals(literal(1))
            compose.waitUntil(10000) { real.state.value.readerOpen == null && !model.state.value.saving &&
                model.state.value.personal?.recentHistory?.entries?.firstOrNull() == reference(1) }
            val opened = requireNotNull(model.state.value.personal)
            assertEquals(before.revision + 1, opened.revision)
            val monitor = ChooserMonitor()
            instrumentation.addMonitor(monitor)
            try {
                click("copy-lyric-link")
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                scenario.onActivity {
                    val clip = requireNotNull(clipboard.primaryClip)
                    assertEquals(1, clip.itemCount)
                    assertEquals(link(1), clip.getItemAt(0).text.toString())
                    assertNull(clip.getItemAt(0).intent); assertNull(clip.getItemAt(0).uri); assertNull(clip.getItemAt(0).htmlText)
                    assertFalse(clip.description.label.toString().contains(source.summaries[0].title))
                    assertFalse(clip.description.label.toString().contains(before.lists.single().name))
                }
                scroll("lyric-link-feedback")
                compose.onNodeWithTag("lyric-link-feedback").assertTextEquals(context.getString(R.string.lyric_link_copied))
                assertTrue(monitor.sent.isEmpty())
                click("share-lyric-link")
                compose.waitForIdle()
                assertChooser(monitor.sent.single(), link(1))
                scroll("lyric-link-feedback")
                compose.onNodeWithTag("lyric-link-feedback").assertTextEquals(context.getString(R.string.lyric_link_shared))
                compose.onNodeWithText(context.getString(R.string.lyric_share_cancelled)).assertDoesNotExist()
                for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
                    monitor.failure = failure
                    click("share-lyric-link")
                    scroll("lyric-link-feedback")
                    compose.onNodeWithTag("lyric-link-feedback").assertTextEquals(context.getString(R.string.lyric_share_failed))
                    compose.onNodeWithTag("lyric-link").assertTextEquals(link(1))
                }
                assertEquals(3, source.requests)
                assertEquals(opened, model.state.value.personal)
                PersonalStore(context).use { assertEquals(opened, it.load()) }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitUntil(10000) { model.state.value.section == "lists" }
                assertEquals(before.lists.single().id, model.state.value.selectedListId)
                compose.onNodeWithTag("lyric-link-controls").assertDoesNotExist()
                assertEquals(opened, model.state.value.personal)
            } finally { instrumentation.removeMonitor(monitor) }
        }
    }

    @Test fun resetOnReplacementAndFocusedRecitePreserveLiteralTextAndPreferences() {
        val before = seed()
        val source = Source()
        var state by mutableStateOf(source.state(1))
        val recite = ReciteState()
        val reading = before.reading.copy(density = "relaxed")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { MaterialTheme {
                LyricPreviewContent(state, {}, {}, {}, {}, reading = reading, recite = recite)
            } } }
            click("copy-lyric-link")
            scroll("lyric-link-feedback")
            compose.onNodeWithTag("lyric-link-feedback").assertTextEquals(context.getString(R.string.lyric_link_copied))
            scenario.onActivity { state = source.state(2) }
            scroll("lyric-link")
            compose.onNodeWithTag("lyric-link").assertTextEquals(link(2))
            compose.onNodeWithTag("lyric-link-feedback").assertDoesNotExist()
            scroll("lyric-text")
            val originalLayout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("lyric-text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(originalLayout) }
            scenario.onActivity { recite.active = true; recite.controls = false }
            compose.waitForIdle()
            compose.onNodeWithTag("lyric-link-controls").assertDoesNotExist()
            scroll("lyric-text")
            compose.onNodeWithTag("lyric-text").assertTextEquals(literal(2))
            val focusedLayout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("lyric-text").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(focusedLayout) }
            assertEquals(originalLayout.single().layoutInput.style, focusedLayout.single().layoutInput.style)
            scenario.onActivity { recite.controls = true }
            scroll("lyric-link")
            compose.onNodeWithTag("lyric-link").assertTextEquals(link(2))
            scenario.onActivity { recite.exit(); state = LyricPreviewState(prepared = source.prepared) }
            compose.waitForIdle()
            compose.onNodeWithTag("lyric-link-controls").assertDoesNotExist()
            scenario.onActivity { state = source.state(1) }
            scroll("lyric-link")
            compose.onNodeWithTag("lyric-link-feedback").assertDoesNotExist()
            assertEquals(0, source.requests)
        }
        PersonalStore(context).use { assertEquals(before, it.load()) }
    }

    @Test fun unpreparedMismatchedAndLegacySyntheticIdentitiesNeverExposeSharing() {
        seed()
        val source = Source()
        val normal = source.state(1)
        val synthetic = source.summaries[0].copy(lyricId = "SYNTHETIC_01")
        val syntheticItem = source.items.getValue(id(1)).copy(lyricId = synthetic.lyricId)
        val cases = listOf(LyricPreviewState(), normal.copy(prepared = null),
            normal.copy(item = source.items.getValue(id(2))),
            normal.copy(prepared = source.prepared.copy(items = emptyMap())),
            normal.copy(prepared = source.prepared.copy(catalog = source.catalog.copy(catalog = emptyList()))),
            normal.copy(selected = synthetic, item = syntheticItem, prepared = PreparedLyricPreview(
                source.catalog.copy(catalog = listOf(synthetic)), mapOf(synthetic.lyricId to syntheticItem))))
        var state by mutableStateOf(cases.first())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { MaterialTheme { LyricPreviewContent(state, {}, {}, {}, {}) } } }
            for (invalid in cases) {
                scenario.onActivity { state = invalid }
                compose.waitForIdle()
                if (invalid.item != null) {
                    scroll("lyric-text")
                    compose.onNodeWithTag("lyric-preview").performTouchInput { swipeUp() }
                }
                compose.onNodeWithTag("lyric-link-controls").assertDoesNotExist()
                compose.onNodeWithTag("share-lyric-link").assertDoesNotExist()
                compose.onNodeWithTag("copy-lyric-link").assertDoesNotExist()
            }
        }
        assertEquals(0, source.requests)
    }

    @Test fun actualSystemChooserBackAndManualSelectionKeepAvailableLinkWithoutDeliveryClaims() {
        val before = seed()
        val source = Source()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.setContent { MaterialTheme { LyricPreviewContent(source.state(1), {}, {}, {}, {}) } } }
            click("share-lyric-link")
            val automation = instrumentation.uiAutomation
            val originalFlags = automation.serviceInfo.flags
            try {
                automation.serviceInfo = automation.serviceInfo.apply {
                    flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                }
                compose.waitUntil(10000) {
                    val root = automation.rootInActiveWindow
                    root?.packageName?.toString()?.let { it != context.packageName } == true &&
                        root.findAccessibilityNodeInfosByViewId("android:id/resolver_list").any { it.isVisibleToUser }
                }
            } finally {
                automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            }
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(10000) { automation.rootInActiveWindow?.packageName?.toString() == context.packageName }
            scroll("lyric-link-feedback")
            compose.onNodeWithTag("lyric-link-feedback").assertTextEquals(context.getString(R.string.lyric_link_shared))
            compose.onNodeWithText(context.getString(R.string.lyric_share_cancelled)).assertDoesNotExist()
            scroll("lyric-link")
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("lyric-link").assertTextEquals(link(1))
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            val glyph = layout.single().getBoundingBox(0).center
            val copyCaption = context.getString(android.R.string.copy)
            scenario.onActivity {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Synthetic sentinel", "Synthetic unrelated clipboard"))
            }
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            try {
                diagnosticUiCase("LyricLink-manual-selection", { "windows=${automation.windows.size}" }) {
                    fun copyAction() = automation.windows.asSequence().mapNotNull { it.root }
                        .flatMap { it.findAccessibilityNodeInfosByText(copyCaption).asSequence() }
                        .firstOrNull { it.isVisibleToUser && it.text?.toString() == copyCaption }
                    compose.onNodeWithTag("lyric-link").performTouchInput { longClick(glyph) }
                    compose.waitUntil(10000) { copyAction() != null }
                    val bounds = Rect()
                    requireNotNull(copyAction()).getBoundsInScreen(bounds)
                    assertFalse(bounds.isEmpty)
                    val downTime = SystemClock.uptimeMillis()
                    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                            bounds.exactCenterX(), bounds.exactCenterY(), 0)
                        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                        try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
                    }
                    compose.waitForIdle()
                    scenario.onActivity {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        assertEquals(link(1), clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                    }
                }
            } finally {
                automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
            }
            assertEquals(0, source.requests)
        }
        PersonalStore(context).use { assertEquals(before, it.load()) }
    }
}
