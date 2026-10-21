package `in`.daasavaani.app

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.ListEntry
import `in`.daasavaani.core.ListSnapshot
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TransferUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun localPreviewCancellationFailuresAndCatalogStatesPreservePersonalData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("personal.db")
        val before = PersonalStore(context).use { it.change(it.load().revision, language = "en", theme = "warm", favorite = "UNAVAILABLE") }
        val codec = transferCodec(context.resources)
        val snapshot = ListSnapshot("  ಕೃತಕ ಪಟ್ಟಿ 😀  ", listOf(ListEntry("item", "missing_fixture"),
            ListEntry("placeholder", text = "<b>ಕೃತಕ</b>"), ListEntry("item", "missing_fixture")))
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
            compose.onNodeWithText(context.getString(R.string.transfer_preview_only)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.save_list)).assertDoesNotExist()
            compose.onNodeWithTag("transfer-link-input").performTextInput(codec.encodeLink(snapshot))
            compose.onNodeWithText(context.getString(R.string.transfer_preview)).performClick()
            compose.waitUntil(10000) { model.state.value.snapshot != null }
            assertNull(library.state.value.prepared)
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasTestTag("transfer-entry-0"))
            compose.onNodeWithText(context.getString(R.string.reference_pending)).assertIsDisplayed()
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText("<b>ಕೃತಕ</b>"))
            compose.onNodeWithText("<b>ಕೃತಕ</b>").assertIsDisplayed()
            scenario.onActivity { model.createLink() }
            compose.waitUntil(10000) { model.state.value.link != null }
            assertEquals(snapshot, codec.decodeLink(requireNotNull(model.state.value.link)))
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(R.string.copy_list_link)))
            compose.onNodeWithText(context.getString(R.string.copy_list_link)).performClick()
            compose.waitUntil(10000) { model.state.value.message == R.string.list_link_copied }
            scenario.onActivity {
                val clipboard = it.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                assertEquals(model.state.value.link, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            }
            scenario.onActivity { model.previewStream { ByteArrayInputStream(codec.encodeFile(snapshot)) } }
            compose.waitUntil(10000) { !model.state.value.working }
            assertEquals(snapshot, model.state.value.snapshot)
            scenario.onActivity { model.input("https://invalid.example/"); model.previewLink() }
            compose.waitUntil(10000) { model.state.value.message == R.string.transfer_invalid }
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasTestTag("transfer-message"))
            compose.onNodeWithText(context.getString(R.string.transfer_invalid)).assertIsDisplayed()
            assertEquals(snapshot, model.state.value.snapshot)
            val closed = CountDownLatch(1)
            scenario.onActivity {
                model.previewStream {
                    object : InputStream() {
                        override fun read(): Int { closed.await(30, TimeUnit.SECONDS); return -1 }
                        override fun read(bytes: ByteArray, offset: Int, length: Int): Int = read()
                        override fun close() { closed.countDown() }
                    }
                }
            }
            compose.waitUntil(10000) { model.state.value.working }
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasTestTag("transfer-cancel"))
            compose.onNodeWithTag("transfer-cancel").performClick()
            compose.waitUntil(10000) { !model.state.value.working }
            assertEquals(R.string.transfer_cancelled, model.state.value.message)
            assertEquals(0, closed.count)
            assertEquals(snapshot, model.state.value.snapshot)
            scenario.onActivity { model.previewFile(Uri.parse("content://missing.invalid/list.dvlist.json.gz")) }
            compose.waitUntil(10000) { model.state.value.message == R.string.transfer_failed }
            scenario.onActivity { model.exportFile(Uri.parse("content://missing.invalid/new.dvlist.json.gz")) }
            compose.waitUntil(10000) { !model.state.value.working }
            assertEquals(R.string.transfer_file_failed, model.state.value.message)
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasTestTag("transfer-message"))
            compose.onNodeWithText(context.getString(R.string.transfer_file_failed)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.library)).performClick()
            compose.onNodeWithText(context.getString(R.string.prepare_library)).performClick()
            compose.waitUntil(60000) { library.state.value.prepared != null }
            val known = requireNotNull(library.state.value.prepared).items.first().videoId
            scenario.onActivity {
                model.input(codec.encodeLink(snapshot.copy(entries = listOf(ListEntry("item", known), ListEntry("item", "missing_fixture")))))
                model.previewLink()
                library.section("transfer")
            }
            compose.waitUntil(10000) { model.state.value.snapshot?.entries?.first()?.videoId == known }
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(R.string.reference_known)))
            compose.onNodeWithText(context.getString(R.string.reference_known)).assertIsDisplayed()
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(R.string.lyrics_unavailable)))
            compose.onNodeWithText(context.getString(R.string.lyrics_unavailable)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.settings)).performClick()
            compose.onNodeWithTag("language-en").assertIsSelected()
            compose.onNodeWithTag("theme-warm").assertIsSelected()
        }
        PersonalStore(context).use { assertEquals(before, it.load()) }
        context.deleteDatabase("personal.db")
    }
}
