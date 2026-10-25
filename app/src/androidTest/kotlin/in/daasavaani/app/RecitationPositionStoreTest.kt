package `in`.daasavaani.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RecitationPositionStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "recitation-position-test.db"
    private val reference = PersonalReference.lyric("00000000-0000-4000-8000-000000000001")
    @After fun cleanup() { context.deleteDatabase(name) }
    private fun failure(label: Int, operation: () -> Unit) {
        try { operation(); fail("Expected refusal") } catch (error: PersonalFailure) { assertEquals(label, error.label) }
    }
    private fun seed(store: PersonalStore): PersonalSnapshot {
        var s = store.change(store.load(), language = "kn", theme = "warm", reference = reference)
        s = store.change(s, reference = PersonalReference.legacy(reference.id))
        s = store.reading(s, direction = 1)
        s = store.history(s, opened = reference)
        s = store.learning(s, reference, "practising")
        return store.saveList(s.revision, s.epoch, RecitationList.create(" Raw ಕ 😀 ",
            listOf(reference.entry("  fallback  "), reference.entry("  fallback  "),
                ListEntry("placeholder", text = " raw 😀  "), PersonalReference.legacy("UNAVAILABLE").entry())))
    }
    private fun v6(): PersonalSnapshot {
        val before = PersonalStore(context, name).use { seed(it) }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("ALTER TABLE preferences DROP COLUMN recitationListId")
            it.execSQL("ALTER TABLE preferences DROP COLUMN recitationEntryId")
            it.execSQL("ALTER TABLE preferences DROP COLUMN backupLanguagePending")
            it.execSQL("DELETE FROM room_master_table")
            it.version = 6
        }
        return before
    }

    @Test fun preservingV6UpgradeGainsOnlyNullAndValidatesFrozenAndProspectiveCompleteBytes() {
        val before = v6()
        val old = PersonalStore.storedBytes(before, 6)
        val current = PersonalStore.storedBytes(before)
        assertEquals(old + ""","recitation":{"position":null}""".toByteArray(Charsets.UTF_8).size, current)
        val original = context.getDatabasePath(name).readBytes()
        listOf(old - 1, old, current - 1).forEach { limit ->
            PersonalStore(context, name, maxBytes = limit).use { store ->
                failure(R.string.personal_capacity) { store.load() }
            }
            assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        }
        PersonalStore(context, name, maxBytes = current).use { store ->
            val after = store.load()
            assertEquals(before, after)
            assertNull(after.recitation.position)
            assertEquals(before.favorites.toList(), after.favorites.toList())
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(8, it.version)
        }
    }

    @Test fun exactOccurrenceSurvivesRestartReorderManualReplacementAndMissingListOrEntry() {
        var saved: PersonalSnapshot
        PersonalStore(context, name).use { store ->
            val s = seed(store)
            val list = s.lists.single()
            saved = store.rememberRecitation(s, list, list.entries[1].id)
            assertEquals(s.copy(revision = s.revision + 1,
                recitation = RecitationPositionV1(ExactRecitationPosition(list.id, list.entries[1].id))), saved)
            assertEquals(saved, store.rememberRecitation(saved, list, list.entries[1].id))
        }
        PersonalStore(context, name).use { store ->
            var s = store.load()
            assertEquals(saved, s)
            val list = s.lists.single()
            val replaced = list.copy(entries = list.entries.reversed().map {
                if (it.id == list.entries[1].id) it.copy(entry = ListEntry("placeholder", text = " replaced raw ")) else it
            })
            s = store.saveList(s.revision, s.epoch, replaced)
            assertEquals(saved.recitation, s.recitation)
            assertEquals(2, s.recitation.resolve(s.lists).index)
            assertEquals(" replaced raw ", s.recitation.resolve(s.lists).list!!.entries[2].entry.text)
            val removed = replaced.copy(entries = replaced.entries.filterNot { it.id == list.entries[1].id })
            s = store.saveList(s.revision, s.epoch, removed)
            assertEquals("entryMissing", s.recitation.resolve(s.lists).kind)
            assertEquals(saved.recitation, s.recitation)
            s = store.deleteList(s.revision, s.epoch, list.id)
            assertEquals("listMissing", s.recitation.resolve(s.lists).kind)
            assertEquals(saved.recitation, s.recitation)
            saved = s
        }
        PersonalStore(context, name).use { assertEquals(saved, it.load()) }
    }

    @Test fun solePairReplacedByExplicitPlaceholderOrUnresolvedAndEveryUnrelatedWritePreservesIt() {
        PersonalStore(context, name).use { store ->
            var s = seed(store)
            val list = s.lists.single()
            s = store.rememberRecitation(s, list, list.entries[2].id)
            val other = RecitationList.create("Other", listOf(PersonalReference.legacy("missing").entry()))
            s = store.saveList(s.revision, s.epoch, other)
            s = store.rememberRecitation(s, other, other.entries.single().id)
            val pointer = s.recitation
            s = store.history(s, clear = true)
            s = store.reading(s, density = "compact")
            s = store.learning(s, reference, null)
            s = store.change(s, language = "en", theme = "dark", reference = reference)
            assertEquals(pointer, s.recitation)
            assertTrue(s.recentHistory.entries.isEmpty())
            val empty = RecitationList.create("Empty")
            s = store.saveList(s.revision, s.epoch, empty)
            failure(R.string.personal_invalid) { store.rememberRecitation(s, empty, list.entries[0].id) }
            assertEquals(s, store.load())
        }
    }

    @Test fun positionOnlyWriteNeverTouchesListsOrUnrelatedTablesAndIdenticalPairDoesNotWrite() {
        PersonalStore(context, name).use { store ->
            var s = seed(store)
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                listOf("favorites", "lists", "occurrences", "recents", "learning").forEach { table ->
                    listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                        db.execSQL("CREATE TRIGGER forbid_${table}_$operation BEFORE $operation ON $table BEGIN SELECT RAISE(ABORT, 'unrelated'); END")
                    }
                }
            }
            val list = s.lists.single()
            s = store.rememberRecitation(s, list, list.entries.last().id)
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
                it.execSQL("CREATE TRIGGER no_repeat BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'no repeat'); END")
            }
            assertEquals(s, store.rememberRecitation(s, list, list.entries.last().id))
        }
    }

    @Test fun staleEpochRevisionChangedListCancellationAndSqlFailurePreservePreviousWholeState() {
        PersonalStore(context, name).use { store ->
            var s = seed(store)
            val list = s.lists.single()
            s = store.rememberRecitation(s, list, list.entries[0].id)
            val stale = s
            PersonalStore(context, name).use { peer ->
                s = peer.change(peer.load(), theme = "dark")
            }
            failure(R.string.personal_stale) { store.rememberRecitation(stale, list, list.entries[1].id) }
            assertEquals(s, store.load())
            s = store.load()
            failure(R.string.personal_stale) { store.rememberRecitation(s, list.copy(name = "stale"), list.entries[1].id) }
            failure(R.string.personal_invalid) { store.rememberRecitation(s, list, "00000000-0000-4000-8000-000000000099") }
            var checkpoints = 0
            try {
                store.rememberRecitation(s, list, list.entries[1].id) {
                    if (++checkpoints == 2) throw CancellationException("cancel before commit")
                }
                fail("Expected cancellation")
            } catch (_: CancellationException) { assertEquals(2, checkpoints) }
            assertEquals(s, store.load())
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
                it.execSQL("CREATE TRIGGER reject_position BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
            }
            failure(R.string.personal_failed) { store.rememberRecitation(s, list, list.entries[1].id) }
            assertEquals(s, store.load())
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("DROP TRIGGER reject_position") }
            val next = store.rememberRecitation(s, list, list.entries[1].id)
            store.close()
            failure(R.string.personal_stale) { store.rememberRecitation(next, list, list.entries[2].id) }
            assertEquals(next, store.load())
        }
    }

    @Test fun exactByteCeilingPlusOneAndRevisionOverflowAreNonDestructive() {
        val before = PersonalStore(context, name).use { seed(it) }
        val list = before.lists.single()
        val target = before.copy(revision = before.revision + 1,
            recitation = RecitationPositionV1(ExactRecitationPosition(list.id, list.entries[1].id)))
        val bytes = PersonalStore.storedBytes(target)
        PersonalStore(context, name, maxBytes = bytes - 1).use { store ->
            failure(R.string.personal_capacity) { store.rememberRecitation(store.load(), list, list.entries[1].id) }
            assertEquals(before, store.load())
        }
        PersonalStore(context, name, maxBytes = bytes).use { store ->
            assertEquals(target, store.rememberRecitation(store.load(), list, list.entries[1].id))
            assertEquals(bytes, PersonalStore.storedBytes(store.load()))
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("UPDATE preferences SET revision = ?", arrayOf(Long.MAX_VALUE))
        }
        PersonalStore(context, name).use { store ->
            val s = store.load()
            assertEquals(s, store.rememberRecitation(s, list, list.entries[1].id))
            failure(R.string.personal_capacity) { store.rememberRecitation(s, list, list.entries[2].id) }
            assertEquals(s, store.load())
        }
    }

    @Test fun frozenVersion6AndCurrentCompleteEnvelopeHaveExactFieldOrderAndHeaderBytes() {
        val empty = PersonalSnapshot()
        val frozen = """{"formatVersion":6,"revision":0,"preferences":{"language":"device","theme":"device","lyricFontSizeSp":${ReadingPolicy.readerFontSizeSp},"readingDensity":"${ReadingPolicy.defaultDensity}"},"favorites":[],"lists":[],"recentHistory":{"enabled":${PersonalPolicy.recentHistoryEnabled},"entries":[]},"learning":{"entries":[]}}"""
        assertEquals(frozen.toByteArray(Charsets.UTF_8).size, PersonalStore.storedBytes(empty, 6))
        val current = frozen.replace("\"formatVersion\":6", "\"formatVersion\":7").dropLast(1) +
            ""","recitation":{"position":null}}"""
        assertEquals(current.toByteArray(Charsets.UTF_8).size, PersonalStore.storedBytes(empty))
        val listId = "00000000-0000-4000-8000-000000000001"
        val entryId = "00000000-0000-4000-8000-000000000003"
        val pointed = empty.copy(recitation = RecitationPositionV1(ExactRecitationPosition(listId, entryId)))
        val text = current.replace("\"position\":null", """"position":{"listId":"$listId","entryId":"$entryId"}""")
        assertEquals(text.toByteArray(Charsets.UTF_8).size, PersonalStore.storedBytes(pointed))
    }

    @Test fun revisionHeaderOneExtraByteRefusesNewPairAndRetainsPreviousUsablePosition() {
        var previous = PersonalStore(context, name).use { store ->
            val s = seed(store)
            val list = s.lists.single()
            store.rememberRecitation(s, list, list.entries[1].id)
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("UPDATE preferences SET revision = 9")
        }
        previous = previous.copy(revision = 9)
        val list = previous.lists.single()
        val next = previous.copy(revision = 10,
            recitation = RecitationPositionV1(ExactRecitationPosition(list.id, list.entries[2].id)))
        val limit = PersonalStore.storedBytes(previous)
        assertEquals(limit + 1, PersonalStore.storedBytes(next))
        PersonalStore(context, name, maxBytes = limit).use { store ->
            val s = store.load()
            failure(R.string.personal_capacity) { store.rememberRecitation(s, list, list.entries[2].id) }
            assertEquals(previous, store.load())
            assertEquals(s, store.rememberRecitation(s, list, list.entries[1].id))
        }
    }

    @Test fun malformedPartialFutureAndInvalidV6DataNeverUpgradeOrRepair() {
        val before = v6()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("UPDATE preferences SET readingDensity = 'invalid'")
        }
        var original = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("UPDATE preferences SET readingDensity = ?", arrayOf(before.reading.density))
        }
        PersonalStore(context, name).use { assertEquals(before, it.load()) }
        listOf("recitationListId = 'invalid'", "recitationEntryId = '${before.lists.single().entries[0].id}'",
            "recitationListId = '${before.lists.single().id}', recitationEntryId = 5").forEach { assignment ->
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
                it.execSQL("UPDATE preferences SET recitationListId = NULL, recitationEntryId = NULL")
                it.execSQL("UPDATE preferences SET $assignment")
            }
            original = context.getDatabasePath(name).readBytes()
            PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
            assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = 9 }
        original = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_future) { store.load() } }
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
    }

    @Test fun bothMinimalTransferVersionsExcludePositionAndAllLocalIdentity() {
        PersonalStore(context, name).use { store ->
            var s = seed(store)
            val list = s.lists.single()
            s = store.rememberRecitation(s, list, list.entries[1].id)
            val codec = transferCodec(context.resources)
            val projections = listOf(list.transfer(), ListSnapshot(list.name,
                listOf(PersonalReference.legacy("UNAVAILABLE").entry(), ListEntry("placeholder", text = " raw ")), 1))
            projections.forEach { projection ->
                val text = codec.json(projection).toString(Charsets.UTF_8)
                assertFalse(text.contains("recitation"))
                assertFalse(text.contains("position"))
                assertFalse(text.contains(list.id))
                list.entries.forEach { assertFalse(text.contains(it.id)) }
            }
            assertEquals(s, store.load())
        }
    }
}
