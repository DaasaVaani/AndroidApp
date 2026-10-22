package `in`.daasavaani.app

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.io.ByteArrayInputStream
import java.util.Base64
import kotlin.random.Random
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ListLinkShareTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val codec get() = transferCodec(context.resources)
    private val legacy = ListSnapshot("  ಕೃತಕ 😀  ", listOf(
        ListEntry("legacyVideo", "10000000-0000-4000-8000-000000000001", fallback = "  Literal  "),
        ListEntry("placeholder", text = "<b>ಕೃತಕ</b>"),
        ListEntry("placeholder", text = "<b>ಕೃತಕ</b>")))
    private val typed get() = legacy.copy(formatVersion = 2, entries = legacy.entries +
        ListEntry("lyric", lyricId = "10000000-0000-4000-8000-000000000001"))

    @After fun clean() { context.deleteDatabase("personal.db") }

    @Suppress("DEPRECATION")
    private fun assertChooser(chooser: Intent, link: String) {
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertNull(chooser.data)
        assertNull(chooser.component)
        assertNull(chooser.`package`)
        assertFalse(chooser.hasExtra(Intent.EXTRA_INITIAL_INTENTS))
        val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/plain", send.type)
        assertEquals(setOf(Intent.EXTRA_TEXT), send.extras?.keySet())
        assertEquals(link, send.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(send.data)
        assertNull(send.clipData)
        assertNull(send.component)
        assertNull(send.`package`)
        assertEquals(0, send.flags)
    }

    @Test fun exactCanonicalV1V2AndCeilingPayloadsHaveOnlyPlainTextInStandardChooser() {
        val links = listOf(legacy, typed).map { snapshot ->
            codec.encodeLink(snapshot).also { assertEquals(snapshot, codec.decodeLink(it)) }
        }.toMutableList()
        val json = codec.json(ListSnapshot("ಕ", emptyList()))
        val bytes = json + ByteArray(1500 - json.size) { 32 }
        val length = bytes.size
        val raw = byteArrayOf(1, length.toByte(), (length ushr 8).toByte(),
            (length xor 65535).toByte(), ((length xor 65535) ushr 8).toByte()) + bytes
        val boundary = context.getString(R.string.canonical_base_url) +
            context.getString(R.string.list_link_path) + "#/list/" +
            Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        assertEquals(context.resources.getInteger(R.integer.max_shared_url_ascii), boundary.length)
        assertEquals(ListSnapshot("ಕ", emptyList()), codec.decodeLink(boundary))
        links += boundary
        for (link in links) {
            var outgoing: Intent? = null
            var feedback: Int? = null
            shareListLink(link, { outgoing = it }, { feedback = it })
            assertChooser(requireNotNull(outgoing), link)
            assertEquals(R.string.list_shared, feedback)
        }
    }

    @Test fun unavailableAndSecurityFailuresAreExplicitButUnexpectedBugsAreNotSwallowed() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            var feedback: Int? = null
            shareListLink(codec.encodeLink(typed), { throw failure }, { feedback = it })
            assertEquals(R.string.list_share_failed, feedback)
        }
        var feedback: Int? = null
        val bug = IllegalStateException("Synthetic unexpected bug")
        try {
            shareListLink(codec.encodeLink(typed), { throw bug }, { feedback = it })
            fail("Unexpected bugs must propagate")
        } catch (error: IllegalStateException) { assertSame(bug, error) }
        assertNull(feedback)
    }

    private class CancelledChooser : Instrumentation.ActivityMonitor() {
        val sent = mutableListOf<Intent>()
        var failure: RuntimeException? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            sent += Intent(intent)
            failure?.let { throw it }
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    private fun transferClick(label: Int) {
        compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(label)))
        compose.onNodeWithText(context.getString(label)).performClick()
    }

    @Test fun explicitPreviewAndSavedListSharingCancellationAndFailuresPreserveEveryFallbackAndPersonalRow() {
        context.deleteDatabase("personal.db")
        val saved = RecitationList.create(typed.name, typed.entries)
        val before = PersonalStore(context).use { store ->
            var state = store.change(store.load(), language = "en", theme = "warm", favorite = "UNAVAILABLE")
            state = store.saveList(state.revision, state.epoch, saved)
            store.history(state, opened = PersonalReference.lyric("10000000-0000-4000-8000-000000000099"))
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: TransferViewModel
            lateinit var library: LibraryViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[TransferViewModel::class.java]
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
            }
            compose.waitUntil(10000) { library.state.value.personal != null }
            compose.onNodeWithText(context.getString(R.string.settings)).performClick()
            compose.onNodeWithText(context.getString(R.string.transfer_title)).performClick()
            compose.onNodeWithTag("transfer-share-link").assertDoesNotExist()
            val monitor = CancelledChooser()
            instrumentation.addMonitor(monitor)
            try {
                for (snapshot in listOf(legacy, typed)) {
                    for (file in listOf(false, true)) {
                        scenario.onActivity {
                            if (file) model.previewStream { ByteArrayInputStream(codec.encodeFile(snapshot)) }
                            else { model.input(codec.encodeLink(snapshot)); model.previewLink() }
                        }
                        compose.waitUntil(10000) { !model.state.value.working }
                        assertEquals(snapshot, model.state.value.snapshot)
                        compose.onNodeWithTag("transfer-share-link").assertDoesNotExist()
                        val previous = monitor.sent.size
                        transferClick(R.string.transfer_create_link)
                        compose.waitUntil(10000) { model.state.value.link != null && !model.state.value.working }
                        assertEquals(previous, monitor.sent.size)
                        transferClick(R.string.share_list_link)
                        compose.waitForIdle()
                        assertEquals(previous + 1, monitor.sent.size)
                        assertChooser(monitor.sent.last(), requireNotNull(model.state.value.link))
                        assertEquals(snapshot, codec.decodeLink(requireNotNull(model.state.value.link)))
                        assertEquals(R.string.list_shared, model.state.value.message)
                        compose.onNodeWithTag("transfer-output-link").assertTextEquals(requireNotNull(model.state.value.link))
                        compose.onNodeWithText(context.getString(R.string.copy_list_link)).assertExists()
                        compose.onNodeWithText(context.getString(R.string.transfer_download)).assertExists()
                    }
                }
                compose.onNodeWithText(context.getString(R.string.lists)).performClick()
                compose.onNodeWithText(saved.name).performClick()
                compose.onNodeWithTag("saved-lists").performScrollToNode(hasText(context.getString(R.string.transfer_download)))
                compose.onNodeWithText(context.getString(R.string.transfer_download)).performClick()
                assertEquals(saved.transfer(), model.state.value.snapshot)
                compose.onNodeWithTag("transfer-share-link").assertDoesNotExist()
                transferClick(R.string.transfer_create_link)
                compose.waitUntil(10000) { model.state.value.link != null && !model.state.value.working }
                val link = requireNotNull(model.state.value.link)
                for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
                    monitor.failure = failure
                    transferClick(R.string.share_list_link)
                    compose.waitUntil(10000) { model.state.value.message == R.string.list_share_failed }
                    compose.onNodeWithText(context.getString(R.string.list_share_failed)).assertExists()
                    assertEquals(link, model.state.value.link)
                    assertEquals(saved.transfer(), model.state.value.snapshot)
                }
                monitor.failure = null
                transferClick(R.string.copy_list_link)
                scenario.onActivity {
                    val clipboard = it.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    assertEquals(link, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
                }
                val prior = monitor.sent.size
                transferClick(R.string.transfer_download)
                compose.waitUntil(10000) { model.state.value.message == R.string.transfer_cancelled }
                assertEquals(prior + 1, monitor.sent.size)
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, monitor.sent.last().action)
                assertEquals("application/gzip", monitor.sent.last().type)
                assertEquals(link, model.state.value.link)
                assertEquals(saved.transfer(), model.state.value.snapshot)
                transferClick(R.string.share_list_link)
                compose.waitForIdle()
                assertChooser(monitor.sent.last(), link)
                assertNull(library.state.value.prepared)
                assertEquals(before, library.state.value.personal)
            } finally { instrumentation.removeMonitor(monitor) }
        }
        PersonalStore(context).use { assertEquals(before, it.load()) }
    }

    @Test fun overCeilingSnapshotNeverOffersSharingAndRetainsCompleteGzipFallback() {
        context.deleteDatabase("personal.db")
        val before = PersonalStore(context).use { it.change(it.load(), language = "en") }
        val random = Random(31)
        val textLimit = context.resources.getInteger(R.integer.max_entry_text_code_points)
        val large = typed.copy(entries = List(20) {
            ListEntry("placeholder", text = CharArray(textLimit) { ('!'..'~').random(random) }.concatToString())
        })
        assertEquals(large, codec.decodeFile(ByteArrayInputStream(codec.encodeFile(large))))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: TransferViewModel
            lateinit var library: LibraryViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[TransferViewModel::class.java]
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
            }
            compose.waitUntil(10000) { library.state.value.personal != null }
            scenario.onActivity { model.showSnapshot(large); library.section("transfer") }
            val monitor = CancelledChooser()
            instrumentation.addMonitor(monitor)
            try {
                transferClick(R.string.transfer_create_link)
                compose.waitUntil(10000) { !model.state.value.working && model.state.value.message != null }
                assertEquals(R.string.transfer_link_too_long, model.state.value.message)
                assertNull(model.state.value.link)
                compose.onNodeWithTag("transfer-share-link").assertDoesNotExist()
                assertTrue(monitor.sent.isEmpty())
                transferClick(R.string.transfer_download)
                compose.waitUntil(10000) { model.state.value.message == R.string.transfer_cancelled }
                assertEquals(Intent.ACTION_CREATE_DOCUMENT, monitor.sent.single().action)
                assertEquals(large, model.state.value.snapshot)
                assertNull(library.state.value.prepared)
                assertEquals(before, library.state.value.personal)
            } finally { instrumentation.removeMonitor(monitor) }
        }
        PersonalStore(context).use { assertEquals(before, it.load()) }
    }

    @Test fun actualSystemChooserBackRetainsSnapshotLinkFallbacksAndPersonalState() {
        context.deleteDatabase("personal.db")
        val before = PersonalStore(context).use { it.change(it.load(), language = "en", favorite = "UNAVAILABLE") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: TransferViewModel
            lateinit var library: LibraryViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[TransferViewModel::class.java]
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
            }
            compose.waitUntil(10000) { library.state.value.personal != null }
            scenario.onActivity { model.showSnapshot(typed); library.section("transfer") }
            transferClick(R.string.transfer_create_link)
            compose.waitUntil(10000) { model.state.value.link != null && !model.state.value.working }
            val link = requireNotNull(model.state.value.link)
            transferClick(R.string.share_list_link)
            compose.waitUntil(10000) {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.let {
                    it != context.packageName
                } == true
            }
            assertEquals(R.string.list_shared, model.state.value.message)
            assertTrue(instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            compose.waitUntil(10000) {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName
            }
            compose.onNodeWithTag("transfer-screen").assertIsDisplayed()
            assertEquals(link, model.state.value.link)
            assertEquals(typed, model.state.value.snapshot)
            compose.onNodeWithTag("transfer-output-link").assertTextEquals(link)
            compose.onNodeWithText(context.getString(R.string.copy_list_link)).assertExists()
            compose.onNodeWithText(context.getString(R.string.transfer_download)).assertExists()
            assertNull(library.state.value.prepared)
            assertEquals(before, library.state.value.personal)
        }
        PersonalStore(context).use { assertEquals(before, it.load()) }
    }
}
