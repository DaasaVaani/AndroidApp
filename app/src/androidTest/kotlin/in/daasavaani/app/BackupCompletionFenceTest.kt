package `in`.daasavaani.app

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupCompletionFenceTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun actualLateWriterCompletionQueuedReadingAndOldHistoryReceiptCannotReopenOrRewriteReplacement() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("personal.db")
        PersonalStore(context).use { it.change(it.load(), language = "en") }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var library: LibraryViewModel
                scenario.onActivity { library = ViewModelProvider(it)[LibraryViewModel::class.java] }
                compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving }
                diagnosticUiCase("backup-late-personal-completion", {
                    "revision=${library.state.value.personal?.revision} saving=${library.state.value.saving} section=${library.state.value.section}"
                }) {
                    val before = requireNotNull(library.state.value.personal)
                    val pending = RecitationList.create("Synthetic pending old writer")
                    val written = CountDownLatch(1)
                    val finished = CountDownLatch(1)
                    val restored = AtomicReference<PersonalSnapshot>()
                    val failure = AtomicReference<Throwable>()
                    var reopened = false
                    PersonalStore(context).use { peer ->
                        peer.observeLists {
                            if (peer.load().lists.any { it.id == pending.id }) written.countDown()
                        }.use {
                            val worker = Thread({
                                try {
                                    check(written.await(10, TimeUnit.SECONDS))
                                    val candidate = FullPersonalBackup("2026-10-25T00:00:00.000Z", BackupPersonalData(
                                        BackupPreferences("en", "device", 28, "normal"), emptyList(), emptyList(),
                                        RecentHistory(true, emptyList()), emptyList(), RecitationPositionV1()))
                                    restored.set(peer.restoreBackup(peer.previewBackup(candidate)))
                                } catch (error: Throwable) { failure.set(error) }
                                finally { finished.countDown() }
                            }, "synthetic-backup-completion-fence")
                            worker.start()
                            scenario.onActivity {
                                library.section("lists")
                                library.saveList(before, pending) {
                                    reopened = true
                                    library.selectList(pending.id)
                                }
                                library.changeReading(direction = 1)
                                library.recordExposed(PersonalReference.legacy("old-pending-receipt"), before.epoch)
                                // Hold only result dispatch: the real Room write and replacement run on
                                // their normal IO threads. No sleep or synthetic successful DB outcome.
                                assertTrue(finished.await(10, TimeUnit.SECONDS))
                            }
                            worker.join(10000)
                            assertFalse(worker.isAlive)
                            failure.get()?.let { throw AssertionError("Synthetic replacement worker failed", it) }
                        }
                        scenario.onActivity { activity -> activity.lifecycleScope.launch { library.refreshAfterPersonalRestore() } }
                        val committed = requireNotNull(restored.get())
                        compose.waitUntil(10000) { library.state.value.personal?.revision == committed.revision && !library.state.value.saving }
                        assertFalse(reopened)
                        assertNull(library.state.value.selectedListId)
                        assertEquals(before.revision + 2, committed.revision)
                        assertEquals(committed, peer.load())
                        assertTrue(peer.load().recentHistory.entries.isEmpty())
                        assertEquals(28, peer.load().reading.fontSizeSp)
                        scenario.onActivity { library.changeReading(direction = 1) }
                        compose.waitUntil(10000) { library.state.value.personal?.reading?.fontSizeSp == 30 && !library.state.value.saving }
                        scenario.onActivity { library.recordExposed(PersonalReference.legacy("fresh-receipt")) }
                        compose.waitUntil(10000) { library.state.value.personal?.recentHistory?.entries?.firstOrNull()?.id == "fresh-receipt" && !library.state.value.saving }
                        assertEquals(committed.revision + 2, peer.load().revision)
                        assertNull(library.state.value.prepared)
                    }
                }
            }
        } finally { context.deleteDatabase("personal.db") }
    }
}
