package `in`.daasavaani.app

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BackupUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val codec = FullPersonalBackupCodec(transferCodec(context.resources), 6)
    private lateinit var originalLocales: LocaleList
    @Before fun setup() {
        originalLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
        context.deleteDatabase("personal.db")
        PersonalStore(context).use { it.change(it.load(), language = "en", theme = "warm", reference = PersonalReference.legacy("original")) }
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { context.getSystemService(LocaleManager::class.java).applicationLocales = originalLocales }
        context.deleteDatabase("personal.db")
    }
    private fun backup(): FullPersonalBackup {
        val list = RecitationList("11111111-1111-4111-8111-111111111111", "Straße", listOf(
            Occurrence("22222222-2222-4222-8222-222222222222", ListEntry("lyric",
                lyricId = "33333333-3333-4333-8333-333333333333", fallback = "  raw é ಕನ್ನಡ 😀  ")),
            Occurrence("44444444-4444-4444-8444-444444444444", ListEntry("lyric",
                lyricId = "33333333-3333-4333-8333-333333333333")),
            Occurrence("55555555-5555-4555-8555-555555555555", ListEntry("placeholder", text = "  raw placeholder 😀  "))))
        val refs = listOf(PersonalReference.lyric("33333333-3333-4333-8333-333333333333"),
            PersonalReference.legacy("33333333-3333-4333-8333-333333333333"))
        return FullPersonalBackup("2026-10-25T00:00:00.000Z", BackupPersonalData(
            BackupPreferences("device", "device", 30, "compact"), refs,
            listOf(list, RecitationList("66666666-6666-4666-8666-666666666666", "STRASSE", emptyList())),
            RecentHistory(false, refs.reversed()), refs.map { LearningAssignment(it, "practising") },
            RecitationPositionV1(ExactRecitationPosition("77777777-7777-4777-8777-777777777777",
                "88888888-8888-4888-8888-888888888888"))))
    }
    private fun awaitIdle(model: BackupViewModel) {
        compose.waitUntil(10000) { !model.state.value.working }
    }
    private fun screen(scenario: ActivityScenario<MainActivity>): Pair<BackupViewModel, LibraryViewModel> {
        lateinit var backup: BackupViewModel
        lateinit var library: LibraryViewModel
        scenario.onActivity {
            backup = ViewModelProvider(it)[BackupViewModel::class.java]
            library = ViewModelProvider(it)[LibraryViewModel::class.java]
        }
        compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving }
        compose.onNodeWithText(context.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("settings-backup").performScrollTo().performClick()
        awaitIdle(backup)
        return backup to library
    }
    private fun preview(scenario: ActivityScenario<MainActivity>, model: BackupViewModel, bytes: ByteArray) {
        scenario.onActivity { model.previewStream { bytes.inputStream() } }
        awaitIdle(model)
    }

    @Test fun completeReadOnlyPrivacyPreviewExplicitWholeReplacementAndFreshWriters() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (model, library) = screen(scenario)
            diagnosticUiCase("backup-complete-preview", { "backup=${model.state.value.copy(preview = null)} revision=${library.state.value.personal?.revision}" }) {
                compose.onNodeWithText(context.getString(R.string.backup_privacy)).assertExists()
                compose.onNodeWithText(context.getString(R.string.backup_same_platform)).assertExists()
                val before = PersonalStore(context).use { it.load() }
                val candidate = backup()
                preview(scenario, model, codec.encode(candidate))
                assertNotNull(model.state.value.preview)
                assertEquals(before, PersonalStore(context).use { it.load() })
                compose.onNodeWithTag("backup-screen").performScrollToNode(hasTestTag("backup-duplicate-warning"))
                compose.onNodeWithTag("backup-duplicate-warning").assertIsDisplayed()
                compose.onNodeWithTag("backup-screen").performScrollToNode(hasTestTag("backup-entry-${candidate.data.lists[0].entries[0].id}"))
                compose.onNodeWithText("  raw é ಕನ್ನಡ 😀  ").assertExists()
                compose.onNodeWithTag("backup-screen").performScrollToNode(hasText("  raw placeholder 😀  "))
                compose.onNodeWithText("  raw placeholder 😀  ").assertIsDisplayed()
                compose.onNodeWithTag("backup-screen").performScrollToNode(hasTestTag("backup-position-entry"))
                compose.onNodeWithTag("backup-position-entry").assertTextContains(candidate.data.recitation.position!!.entryId, substring = true)
                compose.onNodeWithTag("backup-screen").performScrollToNode(hasTestTag("backup-replace"))
                compose.onNodeWithTag("backup-replace").performClick()
                assertEquals(before, PersonalStore(context).use { it.load() })
                compose.onNodeWithTag("backup-confirm").performClick()
                compose.waitUntil(10000) { model.state.value.committed && !model.state.value.working &&
                    library.state.value.personal?.revision == before.revision + 1 }
                assertEquals(R.string.backup_restored, model.state.value.message)
                val restored = PersonalStore(context).use { it.load() }
                assertEquals(candidate.data.lists, restored.lists)
                assertEquals(candidate.data.favorites, restored.favorites.toList())
                assertEquals(candidate.data.recentHistory, restored.recentHistory)
                assertEquals(candidate.data.learning, restored.learning)
                assertEquals(candidate.data.recitation, restored.recitation)
                assertEquals("device", restored.language)
                assertNull(library.state.value.prepared)
                assertNull(library.state.value.selected)
                scenario.onActivity { assertEquals(0, it.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                scenario.onActivity { library.changePersonal(theme = "warm") }
                compose.waitUntil(10000) { library.state.value.personal?.theme == "warm" && !library.state.value.saving }
                assertEquals(before.revision + 2, PersonalStore(context).use { it.load().revision })
            }
        }
    }

    @Test fun actualPinnedSharedNativeAcceptanceWebRefusalAndCorruptInputNeverWrite() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (model, library) = screen(scenario)
            diagnosticUiCase("backup-native-vectors", { "message=${model.state.value.message} working=${model.state.value.working}" }) {
                val before = PersonalStore(context).use { it.load() }
                val fixture = instrumentation.context.assets.open("full-backup.json").bufferedReader().use {
                    Json.parseToJsonElement(it.readText()).jsonObject
                }
                fixture["accepted"]!!.jsonArray.forEach { item ->
                    val value = item.jsonObject["value"]!!
                    val raw = value.toString().toByteArray()
                    val encoded = TransferTransport(BackupPolicy.maxBackupEncodedBytes, BackupPolicy.maxBackupDecodedBytes).encode(raw, 6)
                    preview(scenario, model, encoded)
                    if (value.jsonObject["platform"]!!.jsonPrimitive.content == "android") assertNotNull(model.state.value.preview)
                    else { assertNull(model.state.value.preview); assertEquals(R.string.backup_invalid, model.state.value.message) }
                    assertEquals(before, PersonalStore(context).use { it.load() })
                }
                val good = codec.encode(backup())
                val text = codec.json(backup()).toString(Charsets.UTF_8)
                val malformed = listOf(good + byteArrayOf(0), good + good, good.copyOf(good.size - 1),
                    TransferTransport(BackupPolicy.maxBackupEncodedBytes, BackupPolicy.maxBackupDecodedBytes).encode(
                        text.replace("\"platform\":\"android\"", "\"platform\":\"android\",\"plat\\u0066orm\":\"android\"").toByteArray(), 6),
                    TransferTransport(BackupPolicy.maxBackupEncodedBytes, BackupPolicy.maxBackupDecodedBytes).encode(byteArrayOf(-64, -81), 6))
                malformed.forEach {
                    preview(scenario, model, it)
                    assertEquals(R.string.backup_invalid, model.state.value.message)
                    assertNull(model.state.value.preview)
                    assertEquals(before, PersonalStore(context).use { it.load() })
                }
                assertNull(library.state.value.prepared)
            }
        }
    }

    @Test fun changedLocalStateRequiresFreshPreviewAndCancelledReadClosesWithoutMutation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (model, library) = screen(scenario)
            diagnosticUiCase("backup-stale-cancel", { "message=${model.state.value.message} working=${model.state.value.working}" }) {
                preview(scenario, model, codec.encode(backup()))
                val changed = PersonalStore(context).use { it.change(it.load(), theme = "dark") }
                scenario.onActivity { model.confirmReplacement { library.refreshAfterPersonalRestore() } }
                awaitIdle(model)
                assertEquals(R.string.backup_stale, model.state.value.message)
                assertNull(model.state.value.preview)
                assertEquals(changed, PersonalStore(context).use { it.load() })
                val started = CountDownLatch(1)
                val closed = CountDownLatch(1)
                scenario.onActivity {
                    model.previewStream {
                        object : InputStream() {
                            override fun read(): Int { started.countDown(); check(closed.await(10, TimeUnit.SECONDS)); return -1 }
                            override fun read(buffer: ByteArray, offset: Int, length: Int) = read()
                            override fun close() { closed.countDown() }
                        }
                    }
                }
                assertTrue(started.await(10, TimeUnit.SECONDS))
                scenario.onActivity { model.cancel() }
                awaitIdle(model)
                assertEquals(0, closed.count)
                assertEquals(R.string.backup_cancelled, model.state.value.message)
                assertEquals(changed, PersonalStore(context).use { it.load() })
            }
        }
    }

    @Test fun committedLocaleFailureIsSpecificRetryNotFailedRestoreAndRetryTouchesNoPersonalRows() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (model, library) = screen(scenario)
            diagnosticUiCase("backup-language-retry", { "committed=${model.state.value.committed} retry=${model.state.value.languageRetry}" }) {
                preview(scenario, model, codec.encode(backup()))
                var calls = 0
                scenario.onActivity {
                    model.applyLanguage = { language -> assertEquals("device", language); calls++; throw IllegalStateException("synthetic OS locale fault") }
                    model.confirmReplacement { library.refreshAfterPersonalRestore() }
                }
                awaitIdle(model)
                assertTrue(model.state.value.committed)
                assertEquals(R.string.backup_language_retry, model.state.value.message)
                assertEquals("device", model.state.value.languageRetry)
                val committed = PersonalStore(context).use { it.load() }
                assertEquals(backup().data.lists, committed.lists)
                compose.onNodeWithTag("backup-screen").performScrollToNode(hasTestTag("backup-retry-language"))
                compose.onNodeWithTag("backup-retry-language").assertIsDisplayed()
                scenario.onActivity { model.applyLanguage = { calls++ } }
                compose.onNodeWithTag("backup-retry-language").performClick()
                awaitIdle(model)
                assertEquals(2, calls)
                assertNull(model.state.value.languageRetry)
                assertEquals(R.string.backup_restored, model.state.value.message)
                assertEquals(committed, PersonalStore(context).use { it.load() })
                preview(scenario, model, codec.encode(backup()))
                scenario.onActivity {
                    model.applyLanguage = { calls++; throw IllegalStateException("synthetic second locale fault") }
                    model.confirmReplacement { library.refreshAfterPersonalRestore() }
                }
                awaitIdle(model)
                assertEquals(3, calls)
                scenario.onActivity { library.changePersonal(language = "en") }
                compose.waitUntil(10000) { library.state.value.personal?.language == "en" && !library.state.value.saving }
                val superseding = PersonalStore(context).use { it.load() }
                scenario.onActivity { model.applyLanguage = { calls++ }; model.retryLanguage() }
                awaitIdle(model)
                assertEquals(3, calls)
                assertNull(model.state.value.languageRetry)
                assertEquals(superseding, PersonalStore(context).use { it.load() })
            }
        }
    }

    @Test fun departureAfterCommitCannotAdvertiseRollbackOrReopenWorkflowAndOldReaderReceiptIsFenced() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (model, library) = screen(scenario)
            diagnosticUiCase("backup-committed-departure", { "committed=${model.state.value.committed} section=${library.state.value.section}" }) {
                val old = requireNotNull(library.state.value.personal)
                preview(scenario, model, codec.encode(backup()))
                val committed = CountDownLatch(1)
                val release = CompletableDeferred<Unit>()
                scenario.onActivity {
                    model.applyLanguage = {}
                    model.confirmReplacement {
                        library.refreshAfterPersonalRestore()
                        committed.countDown()
                        release.await()
                    }
                }
                assertTrue(committed.await(10, TimeUnit.SECONDS))
                scenario.onActivity {
                    library.section("library")
                    model.leave()
                    library.recordExposed(PersonalReference.legacy("late-old-reader"), old.epoch)
                }
                release.complete(Unit)
                awaitIdle(model)
                assertTrue(model.state.value.committed)
                assertEquals(R.string.backup_restored, model.state.value.message)
                assertEquals("library", library.state.value.section)
                assertNull(model.state.value.preview)
                assertFalse(PersonalStore(context).use { it.load() }.recentHistory.entries.any { it.id == "late-old-reader" })
            }
        }
    }

    @Test fun stableManualStreamExportFailuresAndCancellationHaveTruthfulOutcomes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (model, library) = screen(scenario)
            diagnosticUiCase("backup-export-stream", { "message=${model.state.value.message} working=${model.state.value.working}" }) {
                val before = PersonalStore(context).use { it.load() }
                val output = ByteArrayOutputStream()
                scenario.onActivity { model.exportStream({ output }) }
                awaitIdle(model)
                assertEquals(R.string.backup_exported, model.state.value.message)
                assertEquals(before.favorites.toList(), codec.decode(output.toByteArray().inputStream()).data.favorites)
                var discarded = false
                scenario.onActivity { model.exportStream({ throw java.io.IOException("synthetic") }, { discarded = true }) }
                awaitIdle(model)
                assertTrue(discarded)
                assertEquals(R.string.transfer_file_failed, model.state.value.message)
                val started = CountDownLatch(1)
                val closed = CountDownLatch(1)
                discarded = false
                scenario.onActivity {
                    model.exportStream({
                        object : java.io.OutputStream() {
                            override fun write(value: Int) { started.countDown(); check(closed.await(10, TimeUnit.SECONDS)) }
                            override fun write(buffer: ByteArray, offset: Int, length: Int) { write(0) }
                            override fun close() { closed.countDown() }
                        }

                    }, { discarded = true })
                }
                assertTrue(started.await(10, TimeUnit.SECONDS))
                scenario.onActivity { model.cancel() }
                awaitIdle(model)
                assertTrue(discarded)
                assertEquals(R.string.backup_export_cancelled, model.state.value.message)
                assertEquals(before, PersonalStore(context).use { it.load() })
                assertNull(library.state.value.prepared)
            }
        }
    }

    @Test fun cancelledBackupKeepsReaderReturnButCommittedReplacementCannotReplayItOrLosePreparedContent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var model: BackupViewModel
            scenario.onActivity {
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
                model = ViewModelProvider(it)[BackupViewModel::class.java]
            }
            compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving }
            diagnosticUiCase("backup-reader-return", { "section=${library.state.value.section} selected=${library.state.value.selected?.videoId} receipt=${library.state.value.readerOpen} saving=${library.state.value.saving} error=${library.state.value.personalError}" }) {
                scenario.onActivity { library.prepare() }
                compose.waitUntil(60000) { library.state.value.prepared != null }
                val prepared = requireNotNull(library.state.value.prepared)
                val item = prepared.items.first()
                scenario.onActivity { library.select(item) }
                compose.onNodeWithText(item.title).assertExists()
                compose.waitUntil(10000) { library.state.value.readerOpen == null && !library.state.value.saving }
                val before = PersonalStore(context).use { it.load() }
                compose.onNodeWithText(context.getString(R.string.settings)).performClick()
                compose.onNodeWithTag("settings-backup").performScrollTo().performClick()
                compose.onNodeWithTag("backup-export").assertExists()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
                compose.waitUntil(10000) { library.state.value.section == "settings" }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
                compose.waitUntil(10000) { library.state.value.selected?.videoId == item.videoId }
                assertEquals(before, PersonalStore(context).use { it.load() })
                compose.onNodeWithText(item.title).assertExists()
                compose.onNodeWithText(context.getString(R.string.settings)).performClick()
                compose.onNodeWithTag("settings-backup").performScrollTo().performClick()
                compose.onNodeWithTag("backup-export").assertExists()
                preview(scenario, model, codec.encode(backup()))
                scenario.onActivity { model.confirmReplacement { library.refreshAfterPersonalRestore() } }
                compose.waitUntil(10000) { model.state.value.committed && !model.state.value.working }
                assertSame(prepared, library.state.value.prepared)
                assertNull(library.state.value.selected)
                assertNull(library.state.value.readerOpen)
                assertNull(library.state.value.fixtureReturnSection)
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
                compose.waitUntil(10000) { library.state.value.section == "settings" }
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitForIdle()
                assertNull(library.state.value.selected)
                assertEquals(backup().data.recentHistory, PersonalStore(context).use { it.load().recentHistory })
            }
        }
    }

    @Test fun nativePreviewAcceptsDecoded64MiBAndEncoded65MiBInclusiveAndRefusesEachPlusOne() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (model, _) = screen(scenario)
            diagnosticUiCase("backup-inclusive-byte-caps", { "message=${model.state.value.message} working=${model.state.value.working}" }) {
                val raw = codec.json(backup())
                fun paddedGzip(length: Int): ByteArray {
                    val output = ByteArrayOutputStream()
                    GZIPOutputStream(output).use { gzip ->
                        val spaces = ByteArray(8192) { 32 }
                        var remaining = length - raw.size
                        while (remaining > 0) { val count = minOf(remaining, spaces.size); gzip.write(spaces, 0, count); remaining -= count }
                        gzip.write(raw)
                    }
                    return output.toByteArray()
                }
                preview(scenario, model, paddedGzip(BackupPolicy.maxBackupDecodedBytes))
                assertNotNull(model.state.value.preview)
                preview(scenario, model, paddedGzip(BackupPolicy.maxBackupDecodedBytes + 1))
                assertEquals(R.string.backup_too_large, model.state.value.message)
                scenario.onActivity { model.previewStream { exactEncodedStream(raw) } }
                awaitIdle(model)
                assertNotNull(model.state.value.preview)
                scenario.onActivity {
                    model.previewStream {
                        object : InputStream() {
                            var remaining = BackupPolicy.maxBackupEncodedBytes + 1
                            override fun read(): Int = if (remaining-- > 0) 0 else -1
                            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                if (remaining <= 0) return -1
                                val count = minOf(length, remaining)
                                buffer.fill(0, offset, offset + count); remaining -= count; return count
                            }
                        }
                    }
                }
                awaitIdle(model)
                assertEquals(R.string.backup_too_large, model.state.value.message)
            }
        }
    }

    private fun exactEncodedStream(json: ByteArray): InputStream {
        val limit = BackupPolicy.maxBackupEncodedBytes
        val padding = (0..4).first { (limit - 18 - 5 - json.size - it) % 5 == 0 }
        val payload = json + ByteArray(padding) { 32 }
        val emptyEnd = limit - 8 - 5 - payload.size
        val header = byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1)
        val empty = byteArrayOf(0, 0, 0, -1, -1)
        val finalHeader = byteArrayOf(1, payload.size.toByte(), (payload.size ushr 8).toByte(),
            payload.size.inv().toByte(), (payload.size.inv() ushr 8).toByte())
        val crc = CRC32().apply { update(payload) }.value
        val trailer = ByteArray(8).also { bytes ->
            repeat(4) { bytes[it] = (crc ushr (8 * it)).toByte(); bytes[it + 4] = (payload.size ushr (8 * it)).toByte() }
        }
        return object : InputStream() {
            var at = 0
            override fun read(): Int {
                if (at >= limit) return -1
                val index = at++
                return (when {
                    index < 10 -> header[index]
                    index < emptyEnd -> empty[(index - 10) % 5]
                    index < emptyEnd + 5 -> finalHeader[index - emptyEnd]
                    index < limit - 8 -> payload[index - emptyEnd - 5]
                    else -> trailer[index - limit + 8]
                }).toInt() and 255
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (at >= limit) return -1
                val count = minOf(length, limit - at)
                repeat(count) { buffer[offset + it] = read().toByte() }
                return count
            }
        }
    }
}
