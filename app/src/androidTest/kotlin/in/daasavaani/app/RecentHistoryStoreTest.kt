package `in`.daasavaani.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RecentHistoryStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "recent-history-test.db"
    private val id = "10000000-0000-4000-8000-000000000001"
    private val lyric = PersonalReference.lyric(id)
    private val legacy = PersonalReference.legacy(id)
    @After fun clean() { context.deleteDatabase(name) }
    private fun failure(label: Int, block: () -> Unit) {
        try { block(); fail("Expected refusal") } catch (error: PersonalFailure) { assertEquals(label, error.label) }
    }

    @Test fun durableDefaultOnDistinctExactRollingOrderDisableEnableAndClearAreIndependent() {
        var expected = PersonalSnapshot()
        PersonalStore(context, name).use { store ->
            var s = store.change(store.load(), language = "kn", theme = "warm", reference = lyric)
            s = store.saveList(s.revision, s.epoch, RecitationList.create("  Synthetic 😀  ",
                listOf(lyric.entry(), legacy.entry(), ListEntry("placeholder", text = "ಕ raw"))))
            val unrelated = s
            val codec = transferCodec(context.resources)
            val transfer = codec.json(s.lists.single().transfer())
            assertTrue(s.recentHistory.enabled)
            assertEquals(PersonalPolicy.recentHistoryEnabled, s.recentHistory.enabled)
            s = store.history(s, opened = lyric)
            s = store.history(s, opened = legacy)
            assertEquals(listOf(legacy, lyric), s.recentHistory.entries)
            val first = s
            s = store.history(s, opened = legacy)
            assertEquals(first, s); assertEquals(first.revision, s.revision)
            s = store.history(s, opened = lyric)
            assertEquals(listOf(lyric, legacy), s.recentHistory.entries)
            repeat(PersonalPolicy.maxRecents) { s = store.history(s, opened = PersonalReference.legacy("fixture_$it")) }
            assertEquals(PersonalPolicy.maxRecents, s.recentHistory.entries.size)
            assertEquals("fixture_${PersonalPolicy.maxRecents - 1}", s.recentHistory.entries.first().id)
            assertFalse(lyric in s.recentHistory.entries)
            s = store.history(s, enabled = false)
            val disabled = s
            assertEquals(disabled, store.history(s, opened = lyric))
            store.close()
            s = store.load()
            assertEquals(disabled, s)
            assertFalse(s.recentHistory.enabled)
            s = store.history(s, clear = true)
            assertFalse(s.recentHistory.enabled); assertTrue(s.recentHistory.entries.isEmpty())
            assertEquals(unrelated.favorites, s.favorites)
            assertEquals(unrelated.lists, s.lists)
            assertEquals(unrelated.language, s.language); assertEquals(unrelated.theme, s.theme)
            assertArrayEquals(transfer, codec.json(s.lists.single().transfer()))
            assertFalse(codec.json(s.lists.single().transfer()).toString(Charsets.UTF_8).contains("recentHistory"))
            s = store.history(s, enabled = true)
            s = store.history(s, opened = lyric)
            assertEquals(listOf(lyric), s.recentHistory.entries)
            s = store.history(s, clear = true)
            assertTrue(s.recentHistory.enabled)
            expected = s
        }
        PersonalStore(context, name).use { assertEquals(expected, it.load()) }
    }

    @Test fun actualFiftyAndFiftyOneStorageRefusesNonDestructivelyRatherThanTrimming() {
        PersonalStore(context, name).use { it.load() }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            repeat(PersonalPolicy.maxRecents) { db.execSQL("INSERT INTO recents VALUES ('legacyVideo', ?, ?)", arrayOf<Any>("fixture_$it", it)) }
        }
        PersonalStore(context, name).use { assertEquals(PersonalPolicy.maxRecents, it.load().recentHistory.entries.size) }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("INSERT INTO recents VALUES ('legacyVideo', 'extra', ?)", arrayOf(PersonalPolicy.maxRecents))
        }
        val bytes = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_capacity) { store.load() } }
        assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
    }

    @Test fun exactHistoryEnvelopeAndUtf8BoundaryIncludeEnabledAndAllReferenceFields() {
        val expected = PersonalSnapshot(language = "kn", theme = "warm", revision = 1,
            recentHistory = RecentHistory(entries = listOf(lyric, legacy)))
        val envelope = """{"formatVersion":4,"revision":1,"preferences":{"language":"kn","theme":"warm"},"favorites":[],"lists":[],"recentHistory":{"enabled":true,"entries":[{"kind":"lyric","lyricId":"$id"},{"kind":"legacyVideo","videoId":"$id"}]}}"""
        assertEquals(envelope.toByteArray(Charsets.UTF_8).size, PersonalStore.storedBytes(expected, 4))
        val listId = "30000000-0000-4000-8000-000000000001"
        val occurrenceId = "40000000-0000-4000-8000-000000000001"
        val list = RecitationList(listId, "ಕ 😀", listOf(Occurrence(occurrenceId, lyric.entry("ಕ raw 😀"))))
        val listJson = """[{"id":"$listId","name":"ಕ 😀","entries":[{"kind":"lyric","lyricId":"$id","fallback":"ಕ raw 😀","id":"$occurrenceId"}]}]"""
        assertEquals(envelope.replace("\"lists\":[]", "\"lists\":$listJson").toByteArray(Charsets.UTF_8).size,
            PersonalStore.storedBytes(expected.copy(lists = listOf(list)), 4))
        val limit = PersonalStore.storedBytes(expected)
        PersonalStore(context, name, maxBytes = limit).use { store ->
            var s = store.change(store.load(), language = "kn", theme = "warm")
            s = store.history(s, opened = legacy)
            s = store.history(s, opened = lyric)
            assertEquals(expected.copy(revision = 3), s)
            // "false" has exactly one more UTF8 byte than "true".
            assertEquals(limit + 1, PersonalStore.storedBytes(s.copy(revision = 4, recentHistory = s.recentHistory.copy(enabled = false))))
            failure(R.string.personal_capacity) { store.history(s, enabled = false) }
            assertEquals(s, store.load())
        }
    }

    @Test fun historyTransactionsInvalidateOldImportNameConfirmationsAndEpochsAndRollbackSqlFailure() {
        PersonalStore(context, name).use { store ->
            val original = RecitationList.create("Straße", listOf(lyric.entry()))
            val initial = store.load()
            val base = store.saveList(initial.revision, initial.epoch, original)
            val duplicate = RecitationList.create("STRASSE")
            failure(R.string.duplicate_name) { store.saveList(base.revision, base.epoch, duplicate) }
            val next = store.history(base, opened = lyric)
            failure(R.string.personal_stale) { store.saveList(base.revision, base.epoch, duplicate, true) }
            failure(R.string.personal_stale) { store.importList(base.revision, base.epoch, duplicate.transfer(), true, true) }
            failure(R.string.personal_stale) { store.history(base, clear = true) }
            failure(R.string.personal_invalid) { store.history(next, opened = legacy, clear = true) }
            failure(R.string.personal_invalid) { store.history(next, enabled = false, clear = true) }
            assertEquals(next, store.load())
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TRIGGER synthetic_failure BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
                failure(R.string.personal_failed) { store.history(next, opened = legacy) }
                failure(R.string.personal_failed) { store.history(next, clear = true) }
                assertEquals(next, store.load())
                db.execSQL("DROP TRIGGER synthetic_failure")
                db.execSQL("UPDATE preferences SET revision = ${Long.MAX_VALUE}")
            }
            val exhausted = store.load()
            assertEquals(exhausted, store.history(exhausted, opened = lyric))
            failure(R.string.personal_capacity) { store.history(exhausted, clear = true) }
            store.close()
            val reopened = store.load()
            assertNotEquals(exhausted.epoch, reopened.epoch)
            failure(R.string.personal_stale) { store.history(exhausted, opened = lyric) }
        }
    }

    private fun v3(): PersonalSnapshot {
        val list = RecitationList.create("  Raw é ಕ 😀  ", listOf(lyric.entry("User"), legacy.entry(),
            ListEntry("placeholder", text = "literal <b>text</b>"), lyric.entry()))
        val expected = PersonalSnapshot("kn", "warm", linkedSetOf(lyric, legacy), 7, listOf(list))
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE preferences (id INTEGER NOT NULL PRIMARY KEY, language TEXT NOT NULL, theme TEXT NOT NULL DEFAULT 'device', revision INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE favorites (kind TEXT NOT NULL, value TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(kind, value))")
            db.execSQL("CREATE TABLE lists (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE occurrences (id TEXT NOT NULL PRIMARY KEY, listId TEXT NOT NULL, position INTEGER NOT NULL, kind TEXT NOT NULL, videoId TEXT, lyricId TEXT, fallback TEXT, text TEXT)")
            db.execSQL("INSERT INTO preferences VALUES (1, 'kn', 'warm', 7)")
            expected.favorites.forEachIndexed { i, ref -> db.execSQL("INSERT INTO favorites VALUES (?, ?, ?)", arrayOf<Any>(ref.kind, ref.id, i)) }
            db.execSQL("INSERT INTO lists VALUES (?, ?, 0)", arrayOf(list.id, list.name))
            list.entries.forEachIndexed { i, occurrence ->
                val e = occurrence.entry
                db.execSQL("INSERT INTO occurrences VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>(occurrence.id, list.id, i, e.kind, e.videoId, e.lyricId, e.fallback, e.text))
            }
            db.version = 3
        }
        return expected
    }

    @Test fun actualV3MigrationPreservesExactPersonalRecordsAndCreatesOnlyEmptyConfiguredHistory() {
        val expected = v3()
        var epoch = ""
        PersonalStore(context, name).use { store ->
            val next = store.load()
            assertEquals(expected, next)
            assertTrue(next.recentHistory.enabled); assertTrue(next.recentHistory.entries.isEmpty())
            assertEquals(expected.favorites.toList(), next.favorites.toList())
            epoch = next.epoch
        }
        PersonalStore(context, name).use {
            assertEquals(expected, it.load())
            assertNotEquals(epoch, it.load().epoch)
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(6, it.version)
        }
    }

    @Test fun v3ProjectedCapacityAndInvalidSchemaRollbackBeforeMigrationFutureVersionNeverReset() {
        val expected = v3()
        val original = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name, maxBytes = PersonalStore.storedBytes(expected) - 1).use { store ->
            failure(R.string.personal_capacity) { store.load() }
        }
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("UPDATE occurrences SET kind = 'future'") }
        val invalid = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(invalid, context.getDatabasePath(name).readBytes())
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = 99 }
        val future = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_future) { store.load() } }
        assertArrayEquals(future, context.getDatabasePath(name).readBytes())
    }

    @Test fun malformedEnabledHistoryIdentityAndPositionsRefuseWithoutReset() {
        listOf("UPDATE preferences SET recentHistoryEnabled = 2",
            "UPDATE preferences SET recentHistoryEnabled = 'false'",
            "INSERT INTO recents VALUES ('lyric', 'not-a-uuid', 0)",
            "INSERT INTO recents VALUES ('legacyVideo', 'valid', 1)").forEach { sql ->
            context.deleteDatabase(name)
            PersonalStore(context, name).use { it.load() }
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL(sql) }
            val original = context.getDatabasePath(name).readBytes()
            PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
            assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        }
    }
}
