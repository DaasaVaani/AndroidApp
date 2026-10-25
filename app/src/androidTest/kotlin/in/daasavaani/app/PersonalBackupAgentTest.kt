package `in`.daasavaani.app

import android.app.backup.BackupAgent
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.xmlpull.v1.XmlPullParser

class PersonalBackupAgentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val codec = FullPersonalBackupCodec(transferCodec(context.resources), 6)
    private val input = File(context.noBackupFilesDir, "system-backup-test-input")
    private class Agent(context: Context) : PersonalBackupAgent() {
        init { attachBaseContext(context) }
    }
    @Before fun setup() { context.deleteDatabase("personal.db") }
    @After fun cleanup() { input.delete(); context.deleteDatabase("personal.db") }
    private fun backup(): FullPersonalBackup {
        val ref = PersonalReference.lyric("11111111-1111-4111-8111-111111111111")
        val list = RecitationList.create("  raw ಕನ್ನಡ 😀  ", listOf(ref.entry(), ref.entry("  é 😀  "),
            ListEntry("placeholder", text = "  raw 😀  ")))
        return FullPersonalBackup("2026-10-25T00:00:00.000Z", BackupPersonalData(
            BackupPreferences("en", "dark", 30, "compact"), listOf(ref, PersonalReference.legacy(ref.id)),
            listOf(list), RecentHistory(false, listOf(ref)), listOf(LearningAssignment(ref, "practising")),
            RecitationPositionV1(ExactRecitationPosition(list.id, list.entries[1].id))))
    }
    private fun deliver(agent: Agent, bytes: ByteArray, destination: File = File(context.filesDir, PersonalBackupAgent.SNAPSHOT),
        type: Int = BackupAgent.TYPE_FILE, declaredSize: Long = bytes.size.toLong()) {
        input.writeBytes(bytes)
        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use {
            agent.onRestoreFile(it, declaredSize, destination, type, 0, 0)
            assertTrue("Framework owns the original descriptor", it.fileDescriptor.valid())
        }
    }
    private fun refused(action: () -> Unit) {
        try { action(); fail("Expected refusal") } catch (_: IOException) { }
    }

    @Test fun legacyCloudAndDeviceTransferHaveExactlyOnePersonalFileAndNoOtherRules() {
        fun rules(id: Int): List<Triple<String, String?, String?>> {
            val result = mutableListOf<Triple<String, String?, String?>>()
            var scope = ""
            context.resources.getXml(id).use { xml ->
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == XmlPullParser.START_TAG) {
                        assertTrue(xml.name in setOf("full-backup-content", "data-extraction-rules",
                            "cloud-backup", "device-transfer", "include"))
                        if (xml.name == "include") {
                            assertEquals(2, xml.attributeCount)
                            result.add(Triple(scope, xml.getAttributeValue(null, "domain"), xml.getAttributeValue(null, "path")))
                        } else {
                            assertEquals(0, xml.attributeCount)
                            scope = xml.name
                        }
                    }
                    xml.next()
                }
            }
            return result
        }
        assertEquals(listOf(Triple("full-backup-content", "file", PersonalBackupAgent.SNAPSHOT)),
            rules(R.xml.personal_backup_rules))
        assertEquals(listOf(Triple("cloud-backup", "file", PersonalBackupAgent.SNAPSHOT),
            Triple("device-transfer", "file", PersonalBackupAgent.SNAPSHOT)), rules(R.xml.personal_extraction_rules))
        val manifest = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertNull("Agent activation remains gated on platform failure-clear policy", manifest.backupAgentName)
        assertEquals(0, manifest.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test fun frameworkFileCallbackStagesOnlyAndFinishedRestoresAllPersonalValuesWithFreshFenceAndDurableReceipt() {
        val expected = backup()
        PersonalStore(context).use { store ->
            val before = store.change(store.load(), theme = "warm")
            val stalePreview = store.previewBackup(backup())
            val agent = Agent(context)
            val content = File(context.filesDir, "synthetic-content-control")
            content.writeText("not readiness")
            try {
                deliver(agent, codec.encode(expected))
                assertEquals(before, store.load())
                assertNull(store.pendingBackupLanguage())
                assertFalse(File(context.filesDir, PersonalBackupAgent.SNAPSHOT).exists())
                agent.onRestoreFinished()
                val restored = store.load()
                assertEquals(expected.data.favorites, restored.favorites.toList())
                assertEquals(expected.data.lists, restored.lists)
                assertEquals(expected.data.recentHistory, restored.recentHistory)
                assertEquals(expected.data.learning, restored.learning)
                assertEquals(expected.data.recitation, restored.recitation)
                assertEquals(expected.data.preferences.language, restored.language)
                assertEquals(expected.data.preferences.theme, restored.theme)
                assertEquals(ReadingPreferences(30, "compact"), restored.reading)
                assertEquals(before.revision + 1, restored.revision)
                assertFalse(store.isCurrent(before))
                assertNotNull(store.pendingBackupLanguage())
                try { store.restoreBackup(stalePreview); fail("Old manual preview must be fenced") }
                catch (error: PersonalFailure) { assertEquals(R.string.backup_stale, error.label) }
                assertEquals("not readiness", content.readText())
            } finally { content.delete() }
        }
        PersonalStore(context).use { assertNotNull(it.pendingBackupLanguage()) }
    }

    @Test fun realLogicalExportExcludesControlMetadataAndAlwaysRemovesProjectionOnQuotaOrDeliveryFailure() {
        PersonalStore(context).use { store ->
            store.restoreBackup(store.previewBackup(backup()))
            val before = store.load()
            val agent = Agent(context)
            agent.captureSnapshot(Long.MAX_VALUE) { file ->
                val projected = codec.decode(file.inputStream())
                assertEquals(backup().data.preferences, projected.data.preferences)
                assertEquals(before.lists, projected.data.lists)
                assertEquals(before.recitation, projected.data.recitation)
                val wire = codec.json(projected).toString(Charsets.UTF_8)
                listOf("revision", "epoch", "backupLanguagePending", "prepared", "readiness",
                    "journal", "room_master_table").forEach { assertFalse(wire.contains("\"$it\"")) }
            }
            assertFalse(File(context.filesDir, PersonalBackupAgent.SNAPSHOT).exists())
            refused { agent.captureSnapshot(1) { fail("Quota must refuse before transport delivery") } }
            refused { agent.captureSnapshot(Long.MAX_VALUE) { throw IOException("synthetic transport failure") } }
            agent.onQuotaExceeded(26L * 1024 * 1024, 25L * 1024 * 1024)
            assertFalse(File(context.filesDir, PersonalBackupAgent.SNAPSHOT).exists())
            assertEquals(before, store.load())
            assertNotNull(store.pendingBackupLanguage())
        }
    }

    @Test fun malformedForeignNewerUnknownMetadataTruncatedAndOversizedSnapshotsNeverReplaceUsableData() {
        val json = codec.json(backup()).toString(Charsets.UTF_8)
        val invalid = listOf("{}", json.replace("\"android\"", "\"web\""),
            json.replace("\"formatVersion\":1", "\"formatVersion\":2"),
            json.replace("\"data\":", "\"revision\":1,\"data\":"),
            json.replace("\"lyricFontSizeSp\":30", "\"lyricFontSizeSp\":999"),
            json + "trailing")
        PersonalStore(context).use { store ->
            val before = store.change(store.load(), theme = "warm")
            invalid.forEach { value ->
                val agent = Agent(context)
                refused { deliver(agent, value.toByteArray()) }
                try { agent.onRestoreFinished(); fail("No false restore completion") } catch (_: IllegalStateException) { }
                assertEquals(before, store.load())
                assertTrue(store.isCurrent(before))
                assertNull(store.pendingBackupLanguage())
            }
            refused { deliver(Agent(context), codec.encode(backup()).dropLast(1).toByteArray()) }
            // The declared framework length alone must not allow an unbounded allocation.
            refused { deliver(Agent(context), byteArrayOf(1), declaredSize = BackupPolicy.maxBackupEncodedBytes.toLong() + 1) }
            assertEquals(before, store.load())
        }
    }

    @Test fun excludedPathsDirectoriesAndDuplicateFilesCannotActivateContentOrCommitPartialRestore() {
        val bytes = codec.encode(backup())
        PersonalStore(context).use { store ->
            val before = store.load()
            listOf(File(context.filesDir, "content-ready"), context.getDatabasePath("personal.db"),
                File(context.filesDir, "../no_backup/control"), File(context.filesDir, "${PersonalBackupAgent.SNAPSHOT}/child"))
                .forEach { path -> refused { deliver(Agent(context), bytes, path) } }
            refused { deliver(Agent(context), bytes, type = BackupAgent.TYPE_DIRECTORY) }
            val agent = Agent(context)
            deliver(agent, bytes)
            refused { deliver(agent, bytes) }
            try { agent.onRestoreFinished(); fail("Duplicate file cannot commit") } catch (_: IllegalStateException) { }
            assertEquals(before, store.load())
            assertNull(store.pendingBackupLanguage())
        }
    }

    @Test fun futureDatabaseIsPreservedAndRestoreNeverRecreatesIt() {
        context.openOrCreateDatabase("personal.db", 0, null).use {
            it.execSQL("CREATE TABLE future_state (value TEXT)")
            it.execSQL("INSERT INTO future_state VALUES ('synthetic future')")
            it.version = 999
        }
        val agent = Agent(context)
        deliver(agent, codec.encode(backup()))
        try { agent.onRestoreFinished(); fail("Future database cannot be erased") } catch (_: IllegalStateException) { }
        context.openOrCreateDatabase("personal.db", 0, null).use {
            assertEquals(999, it.version)
            it.rawQuery("SELECT value FROM future_state", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("synthetic future", c.getString(0))
            }
        }
    }

    @Test fun malformedLocalDatabaseBytesAreNeverErasedOrReplacedByDefaults() {
        val database = context.getDatabasePath("personal.db")
        database.parentFile?.mkdirs()
        val original = ByteArray(256) { it.toByte() }
        database.writeBytes(original)
        val agent = Agent(context)
        deliver(agent, codec.encode(backup()))
        try { agent.onRestoreFinished(); fail("Corrupt local database cannot be replaced") }
        catch (_: IllegalStateException) { }
        assertArrayEquals(original, database.readBytes())
    }

    @Test fun walPeerWriterCannotChangeDeliveredSnapshotWhileItsStableTransactionOwnsTheFence() {
        PersonalStore(context).use { store ->
            val before = store.change(store.load(), theme = "dark")
            PersonalStore(context).use { peer ->
                val observed = peer.load()
                val start = CountDownLatch(1)
                val attempting = CountDownLatch(1)
                val done = CountDownLatch(1)
                val failure = AtomicReference<Throwable>()
                val writer = Thread {
                    try {
                        assertTrue(start.await(5, TimeUnit.SECONDS))
                        attempting.countDown()
                        peer.change(observed, theme = "warm")
                    } catch (error: Throwable) { failure.set(error) }
                    finally { done.countDown() }
                }
                writer.start()
                try {
                    Agent(context).captureSnapshot(Long.MAX_VALUE) { file ->
                        start.countDown()
                        assertTrue(attempting.await(5, TimeUnit.SECONDS))
                        assertFalse("Peer writer must wait for the stable snapshot transaction",
                            done.await(200, TimeUnit.MILLISECONDS))
                        val value = codec.decode(file.inputStream())
                        assertEquals(before.theme, value.data.preferences.theme)
                        assertEquals(before.favorites.toList(), value.data.favorites)
                    }
                } finally { start.countDown(); writer.join(5000) }
                assertFalse("Owned peer writer completed", writer.isAlive)
                failure.get()?.let { throw AssertionError("Synthetic peer writer failed", it) }
                assertEquals("warm", store.load().theme)
            }
        }
    }
}
