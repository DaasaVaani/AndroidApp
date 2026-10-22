package `in`.daasavaani.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ReadingStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "reading-test.db"
    private val lyric = PersonalReference.lyric("10000000-0000-4000-8000-000000000001")
    private val legacy = PersonalReference.legacy(lyric.id)
    @After fun clean() { context.deleteDatabase(name) }
    private fun failure(label: Int, block: () -> Unit) {
        try { block(); fail("Expected refusal") } catch (error: PersonalFailure) { assertEquals(label, error.label) }
    }
    private fun seed(store: PersonalStore): PersonalSnapshot {
        var s = store.change(store.load(), language = "kn", theme = "warm", reference = lyric)
        s = store.change(s, reference = legacy)
        s = store.saveList(s.revision, s.epoch, RecitationList.create("  ಕ 😀 é  ",
            listOf(lyric.entry("raw 😀"), legacy.entry(), lyric.entry(), ListEntry("placeholder", text = " raw ಕ "))))
        s = store.history(s, opened = lyric)
        s = store.history(s, opened = legacy)
        return store.history(s, enabled = false)
    }
    private fun v4(): PersonalSnapshot {
        val s = PersonalStore(context, name).use { seed(it) }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("ALTER TABLE preferences RENAME TO old_preferences")
            db.execSQL("CREATE TABLE preferences (id INTEGER NOT NULL PRIMARY KEY, language TEXT NOT NULL, theme TEXT NOT NULL DEFAULT 'device', revision INTEGER NOT NULL, recentHistoryEnabled INTEGER NOT NULL)")
            db.execSQL("INSERT INTO preferences SELECT id, language, theme, revision, recentHistoryEnabled FROM old_preferences")
            db.execSQL("DROP TABLE old_preferences")
            db.execSQL("DELETE FROM room_master_table")
            db.execSQL("DROP TABLE learning")
            db.version = 4
        }
        return s
    }

    @Test fun exactV4MigrationAddsOnlyConfiguredReadingAndPreservesAllRawIdentitiesAndDisabledHistory() {
        val before = v4()
        var epoch = ""
        PersonalStore(context, name).use { store ->
            val s = store.load()
            assertEquals(before, s)
            assertEquals(ReadingPreferences(), s.reading)
            assertEquals(before.favorites.toList(), s.favorites.toList())
            assertEquals(before.revision, s.revision)
            epoch = s.epoch
            assertEquals(s, store.load())
        }
        PersonalStore(context, name).use { store ->
            val s = store.load()
            assertEquals(before, s)
            assertNotEquals(epoch, s.epoch)
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(6, it.version)
        }
    }

    @Test fun actualBoundsSameDensityAndRestartPreserveUnrelatedTablesWithoutIncidentalRewrites() {
        var expected = PersonalSnapshot()
        PersonalStore(context, name).use { store ->
            val before = seed(store)
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                listOf("favorites", "lists", "occurrences", "recents").forEach { table ->
                    listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                        db.execSQL("CREATE TRIGGER forbid_${table}_$operation BEFORE $operation ON $table BEGIN SELECT RAISE(ABORT, 'incidental rewrite'); END")
                    }
                }
            }
            var s = before
            listOf("compact", "normal", "relaxed").forEach { density ->
                s = store.reading(s, density = density)
                val same = s
                assertEquals(same, store.reading(s, density = density))
                assertEquals(same.revision, store.reading(s, density = density).revision)
            }
            while (s.reading.fontSizeSp > ReadingPolicy.readerFontMinSp) {
                val size = s.reading.fontSizeSp
                s = store.reading(s, direction = -1)
                assertEquals(size - ReadingPolicy.readerFontStepSp, s.reading.fontSizeSp)
            }
            assertEquals(s, store.reading(s, direction = -1))
            while (s.reading.fontSizeSp < ReadingPolicy.readerFontMaxSp) s = store.reading(s, direction = 1)
            assertEquals(s, store.reading(s, direction = 1))
            assertEquals(before.copy(revision = s.revision, reading = s.reading), s)
            expected = s
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                listOf("favorites", "lists", "occurrences", "recents").forEach { table ->
                    listOf("INSERT", "UPDATE", "DELETE").forEach { db.execSQL("DROP TRIGGER forbid_${table}_$it") }
                }
            }
        }
        PersonalStore(context, name).use { assertEquals(expected, it.load()) }
    }

    @Test fun exactFullUtf8AccountingFrozenOldEnvelopeAndProspectiveCapacityRefuseAtomically() {
        val before = v4()
        val old = PersonalStore.storedBytes(before, 4)
        val current = PersonalStore.storedBytes(before)
        val extra = ""","lyricFontSizeSp":${ReadingPolicy.readerFontSizeSp},"readingDensity":"${ReadingPolicy.defaultDensity}"""".toByteArray(Charsets.UTF_8).size
        assertEquals(old + extra, PersonalStore.storedBytes(before, 5))
        assertEquals(old + extra + ""","learning":{"entries":[]}""".toByteArray(Charsets.UTF_8).size, current)
        val bytes = context.getDatabasePath(name).readBytes()
        listOf(old - 1, old, current - 1).forEach { limit ->
            PersonalStore(context, name, maxBytes = limit).use { store -> failure(R.string.personal_capacity) { store.load() } }
            assertArrayEquals(bytes, context.getDatabasePath(name).readBytes())
        }
        PersonalStore(context, name, maxBytes = current).use { store ->
            val s = store.load()
            assertEquals(before, s)
            // compact/relaxed each add one UTF8 byte over normal.
            assertEquals(current + 1, PersonalStore.storedBytes(s.copy(reading = s.reading.copy(density = "compact"))))
            failure(R.string.personal_capacity) { store.reading(s, density = "compact") }
            assertEquals(s, store.load())
        }
    }

    @Test fun invalidOldAndCurrentRowsFutureAndCapacityAreNonDestructive() {
        v4()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("UPDATE preferences SET recentHistoryEnabled = 2") }
        var original = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        listOf("lyricFontSizeSp = 13", "lyricFontSizeSp = 15", "lyricFontSizeSp = 49", "lyricFontSizeSp = 4294967324",
            "lyricFontSizeSp = 'broken'", "readingDensity = 'future'").forEach { value ->
            context.deleteDatabase(name)
            PersonalStore(context, name).use { it.load() }
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("UPDATE preferences SET $value") }
            original = context.getDatabasePath(name).readBytes()
            PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
            assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = 7 }
        original = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_future) { store.load() } }
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
    }

    @Test fun staleFenceInvalidActionsAndFailedCommitNeverRebaseOrPartiallyWrite() {
        PersonalStore(context, name).use { first ->
            val before = seed(first)
            PersonalStore(context, name).use { second ->
                val next = second.reading(second.load(), direction = 1)
                failure(R.string.personal_stale) { first.reading(before, density = "compact") }
                failure(R.string.personal_invalid) { second.reading(next, direction = 0) }
                failure(R.string.personal_invalid) { second.reading(next, direction = 1, density = "normal") }
                failure(R.string.personal_invalid) { second.reading(next, density = "future") }
                failure(R.string.personal_invalid) { second.reading(next) }
                context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                    db.execSQL("CREATE TRIGGER synthetic_failure BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'failure'); END")
                    failure(R.string.personal_failed) { second.reading(next, density = "compact") }
                    assertEquals(next, second.load())
                    db.execSQL("DROP TRIGGER synthetic_failure")
                }
                assertEquals(next, first.load())
                second.close()
                failure(R.string.personal_stale) { second.reading(next, direction = 1) }
            }
        }
    }
}
