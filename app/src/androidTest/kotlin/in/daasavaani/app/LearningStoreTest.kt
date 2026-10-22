package `in`.daasavaani.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LearningStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "learning-test.db"
    private val lyric = PersonalReference.lyric("00000000-0000-4000-8000-000000000001")
    private val legacy = PersonalReference.legacy(lyric.id)
    @After fun cleanup() { context.deleteDatabase(name) }
    private fun failure(label: Int, operation: () -> Unit) {
        try { operation(); fail("Expected refusal") } catch (error: PersonalFailure) { assertEquals(label, error.label) }
    }
    private fun seed(store: PersonalStore): PersonalSnapshot {
        var s = store.change(store.load(), language = "kn", theme = "warm", reference = lyric)
        s = store.change(s, reference = legacy)
        s = store.reading(s, direction = 1)
        s = store.reading(s, density = "relaxed")
        s = store.saveList(s.revision, s.epoch, RecitationList.create("  ಕ 😀 é  ",
            listOf(lyric.entry(" raw 😀 "), legacy.entry(), lyric.entry(), ListEntry("placeholder", text = " raw ಕ "))))
        s = store.history(s, opened = lyric)
        s = store.history(s, opened = legacy)
        return store.history(s, enabled = false)
    }
    private fun v5(): PersonalSnapshot {
        val before = PersonalStore(context, name).use { seed(it) }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("DROP TABLE learning")
            it.execSQL("DELETE FROM room_master_table")
            it.version = 5
        }
        return before
    }

    @Test fun preservingV5MigrationFreshFenceAndProcessRestartNeverInferLabels() {
        val before = v5()
        var fence = ""
        var expected = before
        PersonalStore(context, name).use { store ->
            val after = store.load()
            assertEquals(before, after)
            assertEquals(before.favorites.toList(), after.favorites.toList())
            assertTrue(after.learning.isEmpty())
            assertEquals(before.revision, after.revision)
            fence = after.epoch
            expected = store.learning(after, lyric, "wantToLearn")
            store.close()
            failure(R.string.personal_stale) { store.learning(expected, lyric, "practising") }
        }
        PersonalStore(context, name).use {
            assertEquals(expected, it.load())
            assertNotEquals(fence, it.load().epoch)
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(6, it.version)
        }
    }

    @Test fun exactFrozenV5AndProspectiveFullUtf8AccountingRefuseMigrationAtomically() {
        val before = v5()
        val frozen = PersonalStore.storedBytes(before, 5)
        val current = PersonalStore.storedBytes(before)
        assertEquals(frozen + ""","learning":{"entries":[]}""".toByteArray(Charsets.UTF_8).size, current)
        val bytes = context.getDatabasePath(name).readBytes()
        listOf(frozen - 1, frozen, current - 1).forEach { limit ->
            PersonalStore(context, name, maxBytes = limit).use { store ->
                failure(R.string.personal_capacity) { store.load() }
            }
            assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        }
        PersonalStore(context, name, maxBytes = current).use { store ->
            val s = store.load()
            assertEquals(before, s)
            failure(R.string.personal_capacity) { store.learning(s, lyric, "wantToLearn") }
            assertEquals(s, store.load())
        }
    }

    @Test fun stableTypedOrderNoOpNoWriteRevisionAndOnlyLearningTableChanges() {
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            var s = store.learning(before, lyric, "wantToLearn")
            s = store.learning(s, legacy, "practising")
            s = store.learning(s, PersonalReference.legacy("synthetic-unresolved"), "comfortableReciting")
            assertEquals(listOf(lyric, legacy, PersonalReference.legacy("synthetic-unresolved")), s.learning.map { it.reference })
            assertEquals(before.copy(revision = before.revision + 3, learning = s.learning), s)
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                val tables = listOf("favorites", "lists", "occurrences", "recents")
                tables.forEach { table -> listOf("INSERT", "UPDATE", "DELETE").forEach { op ->
                    db.execSQL("CREATE TRIGGER forbid_${table}_$op BEFORE $op ON $table BEGIN SELECT RAISE(ABORT, 'incidental rewrite'); END")
                } }
                val changed = store.learning(s, lyric, "comfortableReciting")
                assertEquals(s.learning.map { it.reference }, changed.learning.map { it.reference })
                assertEquals(s.revision + 1, changed.revision)
                db.execSQL("CREATE TRIGGER fail_learning BEFORE DELETE ON learning BEGIN SELECT RAISE(ABORT, 'write'); END")
                db.execSQL("CREATE TRIGGER fail_revision BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'write'); END")
                assertEquals(changed, store.learning(changed, lyric, "comfortableReciting"))
                assertEquals(changed, store.learning(changed, PersonalReference.legacy("absent"), null))
                failure(R.string.personal_failed) { store.learning(changed, lyric, null) }
                assertEquals(changed, store.load())
                db.execSQL("DROP TRIGGER fail_learning"); db.execSQL("DROP TRIGGER fail_revision")
                s = store.learning(changed, lyric, null)
                assertEquals(changed.learning.drop(1), s.learning)
                s = store.learning(s, lyric, "practising")
                assertEquals(lyric, s.learning.last().reference)
                tables.forEach { table -> listOf("INSERT", "UPDATE", "DELETE").forEach { db.execSQL("DROP TRIGGER forbid_${table}_$it") } }
            }
            assertEquals(before.copy(revision = s.revision, learning = s.learning), s)
        }
    }

    @Test fun staleInvalidQuotaOverflowAndFailedRevisionRollbackPreserveAllTables() {
        PersonalStore(context, name, maxLearningLabels = 1).use { store ->
            val before = seed(store)
            var s = store.learning(before, lyric, "wantToLearn")
            failure(R.string.personal_capacity) { store.learning(s, legacy, "practising") }
            failure(R.string.personal_stale) { store.learning(before, lyric, "wantToLearn") }
            failure(R.string.personal_stale) { store.learning(before, PersonalReference.legacy("absent"), null) }
            failure(R.string.personal_invalid) { store.learning(s, lyric, "none") }
            failure(R.string.personal_invalid) { store.learning(s, PersonalReference.lyric("bad"), null) }
            assertEquals(s, store.load())
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TRIGGER fail_revision BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'failure'); END")
                failure(R.string.personal_failed) { store.learning(s, lyric, "practising") }
                assertEquals(s, store.load())
                db.execSQL("DROP TRIGGER fail_revision")
                db.execSQL("UPDATE preferences SET revision = ${Long.MAX_VALUE}")
                s = store.load()
                assertEquals(s, store.learning(s, lyric, "wantToLearn"))
                failure(R.string.personal_capacity) { store.learning(s, lyric, "practising") }
                assertEquals(s, store.load())
                db.execSQL("UPDATE preferences SET id = 4294967297")
                failure(R.string.personal_invalid) { store.load() }
                db.execSQL("UPDATE preferences SET id = 1")
                db.execSQL("UPDATE learning SET position = 4294967296")
                failure(R.string.personal_invalid) { store.load() }
                db.execSQL("UPDATE learning SET position = 0")
                assertEquals(s, store.load())
            }
        }
    }

    @Test fun configuredTenThousandLimitCompleteTailAndReducedQuotaNeverTruncate() {
        PersonalStore(context, name).use { it.load() }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.beginTransaction()
            try {
                db.compileStatement("INSERT INTO learning VALUES ('legacyVideo', ?, 'wantToLearn', ?)").use { statement ->
                    repeat(PersonalPolicy.maxLearningLabels) { i ->
                        statement.bindString(1, "synthetic-$i"); statement.bindLong(2, i.toLong()); statement.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        PersonalStore(context, name).use { store ->
            val before = store.load()
            assertEquals(10000, before.learning.size)
            assertEquals("synthetic-9999", before.learning.last().reference.id)
            failure(R.string.personal_capacity) { store.learning(before, legacy, "practising") }
            val after = store.learning(before, before.learning.last().reference, "comfortableReciting")
            assertEquals(10000, after.learning.size)
            assertEquals(before.learning.dropLast(1), after.learning.dropLast(1))
        }
        val bytes = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name, maxLearningLabels = 9999).use { store ->
            failure(R.string.personal_capacity) { store.load() }
        }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
    }

    @Test fun invalidNewerAndMalformedOldStorageNeverResetOrRewrite() {
        var bytes: ByteArray
        listOf("readingDensity = 'future'", "id = 4294967297").forEach { value ->
            context.deleteDatabase(name)
            v5()
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("UPDATE preferences SET $value") }
            bytes = context.getDatabasePath(name).readBytes()
            PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
            assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        }
        context.deleteDatabase(name)
        PersonalStore(context, name).use { it.load() }
        listOf("('legacyVideo', 'synthetic', 'none', 0)", "('lyric', 'bad', 'practising', 0)",
            "('legacyVideo', 'synthetic', 'practising', 2)", "('legacyVideo', 'synthetic', 'practising', 'bad')",
            "('legacyVideo', 'synthetic', 'practising', 4294967296)").forEach { row ->
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
                it.execSQL("DELETE FROM learning"); it.execSQL("INSERT INTO learning VALUES $row")
            }
            bytes = context.getDatabasePath(name).readBytes()
            PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
            assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = 7 }
        bytes = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_future) { store.load() } }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
    }

    @Test fun exactAssignmentBytesAtLimitAndMinimalV1V2TransferExclusion() {
        val before = PersonalStore(context, name).use { store ->
            store.learning(seed(store), lyric, "wantToLearn")
        }
        val empty = before.copy(learning = emptyList())
        val entry = """{"kind":"lyric","lyricId":"${lyric.id}","label":"wantToLearn"}"""
        assertEquals(PersonalStore.storedBytes(empty) + entry.toByteArray(Charsets.UTF_8).size, PersonalStore.storedBytes(before))
        val limit = PersonalStore.storedBytes(before)
        PersonalStore(context, name, maxBytes = limit).use { store ->
            val s = store.load()
            failure(R.string.personal_capacity) { store.learning(s, lyric, "comfortableReciting") }
            assertEquals(s, store.load())
            val next = store.learning(s, lyric, "practising")
            // The shorter label exactly offsets the 9 -> 10 revision digit.
            assertEquals(limit, PersonalStore.storedBytes(next))
            val codec = transferCodec(context.resources)
            listOf(ListSnapshot("Synthetic", listOf(ListEntry("legacyVideo", videoId = "synthetic01")), 1),
                next.lists.single().transfer()).forEach { snapshot ->
                val json = codec.json(snapshot).toString(Charsets.UTF_8)
                assertFalse(json.contains("learning")); assertFalse(json.contains("practising"))
                assertEquals(snapshot, codec.decodeFile(codec.encodeFile(snapshot).inputStream()))
                assertEquals(snapshot, codec.decodeLink(codec.encodeLink(snapshot)))
            }
        }
    }
}
