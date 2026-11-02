package `in`.daasavaani.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FirstUseStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "first-use-test.db"
    private val choices = BackupPreferences("kn", "warm", 32, "relaxed")
    private val codec = FullPersonalBackupCodec(transferCodec(context.resources), 6)
    @After fun cleanup() { context.deleteDatabase(name) }
    private fun failure(label: Int, block: () -> Unit) {
        try { block(); fail("Expected setup refusal") }
        catch (error: PersonalFailure) { assertEquals(label, error.label) }
    }
    private fun seed(store: PersonalStore): PersonalSnapshot {
        val ref = PersonalReference.lyric("11111111-1111-4111-8111-111111111111")
        var state = store.change(store.load(), reference = ref)
        state = store.history(state, opened = ref)
        state = store.history(state, enabled = false)
        state = store.learning(state, ref, "practising")
        state = store.saveList(state.revision, state.epoch, RecitationList.create(" Synthetic repeated ",
            listOf(ref.entry(), ref.entry(), ListEntry("placeholder", text = "  synthetic raw  "))))
        return store.rememberRecitation(state, state.lists.single(), state.lists.single().entries[1].id)
    }

    @Test fun completeAndSkipPreserveEveryUnrelatedValueAndSurviveReopen() {
        lateinit var saved: PersonalSnapshot
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            saved = store.finishFirstUse(before, false, choices)
            assertEquals(before.copy(language = "kn", theme = "warm", reading = ReadingPreferences(32, "relaxed"),
                setupStatus = "completed", revision = before.revision + 1), saved)
            val skipped = store.finishFirstUse(saved, true, choices.copy(language = "en", theme = "dark"))
            assertEquals(saved.copy(setupStatus = "skipped", revision = saved.revision + 1), skipped)
            saved = skipped
        }
        PersonalStore(context, name).use { assertEquals(saved, it.load()) }
    }

    @Test fun freshSkipKeepsDeviceSentinelsAndRejectsStaleOrInvalidDraftsWithoutMutation() {
        PersonalStore(context, name).use { store ->
            val before = store.load()
            val skipped = store.finishFirstUse(before, true, choices)
            assertEquals(before.copy(setupStatus = "skipped", revision = before.revision + 1), skipped)
            failure(R.string.personal_stale) { store.finishFirstUse(before, false, choices) }
            failure(R.string.personal_invalid) { store.finishFirstUse(skipped, false) }
            failure(R.string.personal_invalid) { store.finishFirstUse(skipped, false, choices.copy(lyricFontSizeSp = 31)) }
            assertEquals(skipped, store.load())
            PersonalStore(context, name).use { peer ->
                val changed = peer.change(peer.load(), theme = "dark")
                failure(R.string.personal_stale) { store.finishFirstUse(skipped, false, choices) }
                assertEquals(changed, store.load())
            }
        }
    }

    @Test fun finalSetupWriteFailureRollsBackPreferencesAndReadingAndAllowsRetry() {
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
                it.execSQL("CREATE TRIGGER reject_setup BEFORE UPDATE OF setupStatus ON preferences BEGIN SELECT RAISE(ABORT, 'synthetic setup write failure'); END")
            }
            failure(R.string.personal_failed) { store.finishFirstUse(before, false, choices) }
            assertEquals(before, store.load())
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("DROP TRIGGER reject_setup") }
            assertEquals("completed", store.finishFirstUse(before, false, choices).setupStatus)
        }
    }

    @Test fun exactCapacityAndOneByteTooManyIncludeCompletedStatusAndRevision() {
        val before = PersonalStore(context, name).use { store -> store.change(seed(store), language = "en", theme = "light") }
        val after = before.copy(language = "kn", theme = "warm", reading = ReadingPreferences(32, "relaxed"),
            setupStatus = "completed", revision = before.revision + 1)
        val bytes = PersonalStore.storedBytes(after)
        PersonalStore(context, name, maxBytes = bytes - 1).use { store ->
            failure(R.string.personal_capacity) { store.finishFirstUse(store.load(), false, choices) }
            assertEquals(before, store.load())
        }
        PersonalStore(context, name, maxBytes = bytes).use { store ->
            assertEquals(after, store.finishFirstUse(store.load(), false, choices))
        }
    }

    @Test fun version8UpgradePreservesAllRowsAndLanguageReceiptWithoutInferringCompletion() {
        val before = PersonalStore(context, name).use { seed(it) }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("UPDATE preferences SET backupLanguagePending = 1")
            it.execSQL("ALTER TABLE preferences DROP COLUMN setupStatus")
            it.version = 8
        }
        PersonalStore(context, name).use {
            assertEquals(before, it.load())
            assertEquals(before, it.pendingBackupLanguage())
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { assertEquals(9, it.version) }
    }

    @Test fun exportedVersion3RoundTripsSetupAndVersion1RestorationExplicitlyReopensPendingSetup() {
        PersonalStore(context, name).use { store ->
            for (status in listOf("pending", "completed", "skipped")) {
                var current = store.load()
                if (status != "pending") current = store.finishFirstUse(current, status == "skipped", choices)
                lateinit var bytes: ByteArray
                store.exportBackup("2026-11-02T00:00:00.000Z") { bytes = it }
                val backup = codec.decode(bytes.inputStream())
                assertEquals(3, backup.formatVersion)
                assertEquals(status, backup.data.setupStatus)
                assertEquals(status, store.restoreBackup(store.previewBackup(backup)).setupStatus)
                val old = backup.copy(data = backup.data.copy(setupStatus = "pending"), formatVersion = 1)
                assertEquals("pending", store.restoreBackup(store.previewBackup(old)).setupStatus)
                assertEquals(current.lists, store.load().lists)
            }
        }
    }
}
