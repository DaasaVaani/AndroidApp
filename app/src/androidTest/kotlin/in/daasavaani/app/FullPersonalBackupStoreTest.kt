package `in`.daasavaani.app

import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.util.concurrent.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class FullPersonalBackupStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "full-personal-backup-test.db"
    private val codec = FullPersonalBackupCodec(transferCodec(context.resources),
        context.resources.getInteger(R.integer.list_compression_level))
    private val date = "2026-10-25T00:00:00.000Z"
    private val lyric = PersonalReference.lyric("11111111-1111-4111-8111-111111111111")
    private val legacy = PersonalReference.legacy(lyric.id)
    @After fun cleanup() { context.deleteDatabase(name) }
    private fun failure(label: Int, action: () -> Unit) {
        try { action(); fail("Expected refusal") } catch (error: PersonalFailure) { assertEquals(label, error.label) }
    }
    private fun seed(store: PersonalStore): PersonalSnapshot {
        var s = store.change(store.load(), language = "device", theme = "device", reference = lyric)
        s = store.change(s, reference = legacy)
        s = store.reading(s, direction = 1)
        s = store.reading(s, density = "relaxed")
        s = store.saveList(s.revision, s.epoch, RecitationList.create(" Straße ",
            listOf(lyric.entry("  raw é ಕನ್ನಡ 😀  "), lyric.entry(), legacy.entry(),
                ListEntry("placeholder", text = "  raw 😀  "))))
        s = store.saveList(s.revision, s.epoch, RecitationList.create(" STRASSE "), confirmedDuplicate = true)
        s = store.history(s, opened = legacy)
        s = store.history(s, opened = lyric)
        s = store.history(s, enabled = false)
        s = store.learning(s, legacy, "wantToLearn")
        s = store.learning(s, lyric, "comfortableReciting")
        return store.rememberRecitation(s, s.lists[0], s.lists[0].entries[1].id)
    }
    private fun exported(store: PersonalStore, check: () -> Unit = {}): FullPersonalBackup {
        var bytes: ByteArray? = null
        store.exportBackup(date, check) { bytes = it }
        return codec.decode(requireNotNull(bytes).inputStream())
    }
    private fun empty() = FullPersonalBackup(date, BackupPersonalData(
        BackupPreferences("device", "device", 28, "normal"), emptyList(), emptyList(),
        RecentHistory(false, emptyList()), emptyList(), RecitationPositionV1()))

    @Test fun stableLogicalExportAndWholeReplacementPreserveEveryAllowlistedValueAcrossReopen() {
        lateinit var restored: PersonalSnapshot
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            val backup = exported(store)
            assertEquals(before, store.load())
            val wire = codec.json(backup).toString(Charsets.UTF_8)
            listOf("\"revision\"", "\"epoch\"", "\"prepared\"", "\"cache\"", "\"formatVersion\":7").forEach {
                assertFalse(wire.contains(it))
            }
            assertEquals(before.favorites.toList(), backup.data.favorites)
            assertEquals(before.lists, backup.data.lists)
            assertEquals(before.learning, backup.data.learning)
            assertEquals(before.recentHistory, backup.data.recentHistory)
            assertEquals(before.recitation, backup.data.recitation)
            assertEquals(listOf(before.lists.map { it.id }), backup.duplicateNameGroups())
            val changed = store.change(before, language = "en", theme = "dark", reference = lyric)
            val preview = store.previewBackup(backup)
            assertEquals(changed, store.load())
            restored = store.restoreBackup(preview)
            assertEquals(before.copy(revision = changed.revision + 1), restored)
            assertEquals(before.favorites.toList(), restored.favorites.toList())
            assertNotEquals(before.epoch, restored.epoch)
            assertEquals("device", restored.language)
            assertEquals("device", restored.theme)
            assertNull(restored.lists[0].entries[1].entry.fallback)
            assertFalse(restored.recentHistory.enabled)
        }
        PersonalStore(context, name).use { assertEquals(restored, it.load()) }
    }

    @Test fun unavailableExactPairSurvivesAndEmptyBackupRemovesAllExistingCollections() {
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            val original = exported(store)
            val missing = original.copy(data = original.data.copy(lists = emptyList()))
            val restored = store.restoreBackup(store.previewBackup(missing))
            assertEquals(original.data.recitation, restored.recitation)
            assertEquals("listMissing", restored.recitation.resolve(restored.lists).kind)
            val cleared = store.restoreBackup(store.previewBackup(empty()))
            assertTrue(cleared.favorites.isEmpty()); assertTrue(cleared.lists.isEmpty())
            assertTrue(cleared.learning.isEmpty()); assertTrue(cleared.recentHistory.entries.isEmpty())
            assertNull(cleared.recitation.position)
            assertEquals(before.revision + 2, cleared.revision)
        }
    }

    @Test fun changedRevisionInvalidatesRetainedPreviewWithoutAnyReplacement() {
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            val preview = store.previewBackup(empty())
            val changed = store.history(before, clear = true)
            failure(R.string.backup_stale) { store.restoreBackup(preview) }
            assertEquals(changed, store.load())
            assertEquals(empty(), preview.backup)
            val fresh = store.restoreBackup(store.previewBackup(empty()))
            assertEquals(changed.revision + 1, fresh.revision)
        }
    }

    @Test fun cancellationBeforeCommitRollsBackEvenAfterReplacementStatements() {
        PersonalStore(context, name).use { store ->
            seed(store)
            val before = store.load()
            val preview = store.previewBackup(empty())
            var checks = 0
            // Empty target has no collection-loop checks: the last callback follows SQL replacement/readback.
            val total = run {
                val independent = "full-personal-backup-check-count.db"
                try {
                    PersonalStore(context, independent).use { probe ->
                        val p = probe.previewBackup(empty())
                        probe.restoreBackup(p) { checks++ }
                    }
                } finally { context.deleteDatabase(independent) }
                checks
            }
            checks = 0
            try {
                store.restoreBackup(preview) { if (++checks == total) throw CancellationException("synthetic") }
                fail("Expected cancellation")
            } catch (_: CancellationException) { }
            assertEquals(before, store.load())
            assertTrue(store.isCurrent(before))
        }
    }

    @Test fun storageFaultAfterDeletesAndPreferenceUpdatesRollsBackAllTablesAndFence() {
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            val preview = store.previewBackup(empty())
            context.openOrCreateDatabase(name, 0, null).use { db ->
                db.execSQL("CREATE TRIGGER synthetic_failure BEFORE UPDATE OF recitationListId ON preferences BEGIN SELECT RAISE(ABORT, 'synthetic storage fault'); END")
                failure(R.string.personal_failed) { store.restoreBackup(preview) }
                assertEquals(before, store.load())
                assertTrue(store.isCurrent(before))
                db.execSQL("DROP TRIGGER synthetic_failure")
            }
            assertEquals(before.revision + 1, store.restoreBackup(preview).revision)
        }
    }

    @Test fun exactProspectiveRevisionOverheadCapacityAndOverflowRefuseBeforeDeletingOldData() {
        PersonalStore(context, name).use { seed(it) }
        context.openOrCreateDatabase(name, 0, null).use { it.execSQL("UPDATE preferences SET revision = 9") }
        val before = PersonalStore(context, name).use { it.load() }
        val limit = PersonalStore.storedBytes(before)
        PersonalStore(context, name, maxBytes = limit).use { store ->
            val preview = store.previewBackup(exported(store))
            failure(R.string.personal_capacity) { store.restoreBackup(preview) }
            assertEquals(before, store.load())
        }
        PersonalStore(context, name, maxBytes = limit + 1).use { store ->
            assertEquals(10L, store.restoreBackup(store.previewBackup(exported(store))).revision)
        }
        context.openOrCreateDatabase(name, 0, null).use { it.execSQL("UPDATE preferences SET revision = ${Long.MAX_VALUE}") }
        PersonalStore(context, name).use { store ->
            val full = store.load()
            failure(R.string.personal_capacity) { store.restoreBackup(store.previewBackup(empty())) }
            assertEquals(full, store.load())
        }
    }

    @Test fun everyOldWriterAndLateReadFenceIsRefusedAcrossOtherLiveStoresAndFreshWritersWork() {
        PersonalStore(context, name).use { owner ->
            seed(owner)
            PersonalStore(context, name).use { peer ->
                val oldOwner = owner.load()
                val lateRead = peer.load()
                val list = lateRead.lists[0]
                val preview = peer.previewBackup(empty())
                val after = owner.restoreBackup(owner.previewBackup(exported(owner)))
                assertFalse(owner.isCurrent(oldOwner))
                assertFalse(peer.isCurrent(lateRead))
                failure(R.string.backup_stale) { peer.restoreBackup(preview) }
                failure(R.string.personal_stale) { peer.change(lateRead, theme = "dark") }
                failure(R.string.personal_stale) { peer.reading(lateRead, density = "compact") }
                failure(R.string.personal_stale) { peer.history(lateRead, opened = legacy) }
                failure(R.string.personal_stale) { peer.learning(lateRead, legacy, null) }
                failure(R.string.personal_stale) { peer.saveList(lateRead.revision, lateRead.epoch, list.copy(name = "changed")) }
                failure(R.string.personal_stale) { peer.deleteList(lateRead.revision, lateRead.epoch, list.id) }
                failure(R.string.personal_stale) { peer.rememberRecitation(lateRead, list, list.entries[0].id) }
                assertEquals(after, owner.load())
                val fresh = peer.load()
                assertTrue(peer.isCurrent(fresh))
                val written = peer.change(fresh, theme = "warm")
                assertEquals(after.revision + 1, written.revision)
                assertEquals("warm", owner.load().theme)
            }
        }
    }

    @Test fun stableExportRechecksRevisionAfterEncodingAndNeverDeliversChangedState() {
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            PersonalStore(context, name).use { peer ->
                var once = false
                var delivered = false
                failure(R.string.backup_stale) {
                    store.exportBackup(date, {
                        if (!once) { once = true; peer.change(peer.load(), theme = "warm") }
                    }) { delivered = true }
                }
                assertFalse(delivered)
                assertEquals(before.revision + 1, store.load().revision)
                assertEquals("warm", exported(store).data.preferences.theme)
            }
        }
    }

    @Test fun exportDeliveryFaultAndCancellationNeverMutatePersonalDatabase() {
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            failure(R.string.personal_failed) { store.exportBackup(date) { throw java.io.IOException("synthetic provider fault") } }
            assertEquals(before, store.load())
            try { store.exportBackup(date, { throw CancellationException("synthetic") }) { fail("No delivery") }; fail("Expected cancellation") }
            catch (_: CancellationException) { }
            assertEquals(before, store.load())
        }
    }

    @Test fun restoreNotifiesExistingListObserversAndClosedStorePreviewCannotBeReplayed() {
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            var notifications = 0
            val observation = store.observeLists { notifications++ }
            try {
                store.restoreBackup(store.previewBackup(empty()))
                assertTrue(notifications > 0)
            } finally { observation.close() }
            val preview = store.previewBackup(empty())
            store.close()
            failure(R.string.backup_stale) { store.restoreBackup(preview) }
            assertTrue(store.load().revision > before.revision)
        }
    }

    @Test fun retainedPreviewDetachesCallerOwnedCollectionsBeforeConfirmation() {
        PersonalStore(context, name).use { store ->
            seed(store)
            val favorites = mutableListOf(legacy)
            val history = mutableListOf(lyric)
            val learning = mutableListOf(LearningAssignment(legacy, "practising"))
            val entries = mutableListOf(Occurrence("22222222-2222-4222-8222-222222222222", legacy.entry()))
            val lists = mutableListOf(RecitationList("33333333-3333-4333-8333-333333333333", "Retained", entries))
            val source = empty().copy(data = empty().data.copy(favorites = favorites, lists = lists,
                recentHistory = RecentHistory(false, history), learning = learning))
            val preview = store.previewBackup(source)
            favorites.clear(); history.clear(); learning.clear(); entries.clear(); lists.clear()
            val restored = store.restoreBackup(preview)
            assertEquals(listOf(legacy), restored.favorites.toList())
            assertEquals(listOf(lyric), restored.recentHistory.entries)
            assertEquals(1, restored.lists.single().entries.size)
            assertEquals(listOf(LearningAssignment(legacy, "practising")), restored.learning)
        }
    }

    @Test fun observerFailureAfterCommitNeverBecomesFailedOrRolledBackReplacement() {
        PersonalStore(context, name).use { store ->
            seed(store)
            store.observeLists { throw IllegalStateException("synthetic observer failure") }.use {
                val restored = store.restoreBackup(store.previewBackup(empty()))
                assertTrue(restored.lists.isEmpty())
                assertEquals(restored, store.load())
                assertTrue(store.isCurrent(restored))
            }
        }
    }
}
