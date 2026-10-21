package `in`.daasavaani.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class PersonalStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "personal-test.db"
    @After fun clean() { context.deleteDatabase(name) }

    private fun raw(version: Int, extra: String = "", language: String = "kn") {
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE preferences (id INTEGER NOT NULL PRIMARY KEY, language TEXT NOT NULL, revision INTEGER NOT NULL$extra)")
            db.execSQL("CREATE TABLE favorites (videoId TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("INSERT INTO preferences (id, language, revision) VALUES (1, ?, 7)", arrayOf(language))
            db.execSQL("INSERT INTO favorites VALUES ('UNAVAILABLE')")
            db.version = version
        }
    }
    private fun failure(label: Int, action: () -> Unit) {
        try { action(); fail("Expected refusal") }
        catch (error: PersonalFailure) { assertEquals(label, error.label) }
    }
    private fun bytes() = context.getDatabasePath(name).readBytes()

    @Test fun preferencesFavoritesSurviveCloseAndUnavailableIdsRemain() {
        var expected = PersonalSnapshot()
        PersonalStore(context, name).use { store ->
            expected = store.load()
            expected = store.change(expected.revision, language = "kn", theme = "dark", favorite = "UNAVAILABLE")
        }
        PersonalStore(context, name).use { store ->
            assertEquals(expected, store.load())
            val removed = store.change(expected.revision, favorite = "UNAVAILABLE")
            assertTrue(removed.favorites.isEmpty())
            assertEquals("kn", removed.language)
            assertEquals("dark", removed.theme)
        }
    }

    @Test fun transactionalCountAndByteBoundsRejectWithoutChangingAnyPersonalData() {
        PersonalStore(context, name, maxFavorites = 1).use { store ->
            val first = store.change(store.load().revision, favorite = "UNAVAILABLE")
            failure(R.string.personal_capacity) { store.change(first.revision, theme = "dark", favorite = "TESTONLY002") }
            assertEquals(first, store.load())
        }
        PersonalStore(context, name, maxBytes = 1).use { store ->
            failure(R.string.personal_capacity) { store.load() }
        }
        PersonalStore(context, name).use { assertEquals(setOf("UNAVAILABLE"), it.load().favorites) }
    }

    @Test fun staleWriterAndInvalidInputDoNotChangeOtherCollections() {
        PersonalStore(context, name).use { first ->
            val before = first.load()
            PersonalStore(context, name).use { second ->
                val after = second.change(before.revision, language = "kn")
                failure(R.string.personal_stale) { first.change(before.revision, favorite = "TESTONLY001") }
                failure(R.string.personal_invalid) { second.change(after.revision, theme = "bogus", favorite = "TESTONLY002") }
                failure(R.string.personal_invalid) { second.change(after.revision, favorite = "bad") }
                assertEquals(after, first.load())
            }
        }
    }

    @Test fun v1MigrationPreservesLanguageFavoritesAndRevision() {
        raw(1)
        PersonalStore(context, name).use { store ->
            assertEquals(PersonalSnapshot("kn", "device", setOf("UNAVAILABLE"), 7), store.load())
        }
        PersonalStore(context, name).use { assertEquals("device", it.load().theme) }
    }

    @Test fun newerVersionRefusedBeforeRoomWithoutChangingFile() {
        raw(99, ", unknown TEXT")
        val original = bytes()
        PersonalStore(context, name).use { store ->
            failure(R.string.personal_future) { store.load() }
            failure(R.string.personal_future) { store.change(7, language = "en") }
        }
        assertArrayEquals(original, bytes())
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(99, it.version)
        }
    }

    @Test fun unfamiliarMigrationSchemaPreservedByteForByte() {
        raw(1, ", unknown TEXT")
        val original = bytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(original, bytes())
    }

    @Test fun invalidV1RecordRollsBackMigration() {
        raw(1, language = "unsupported")
        val original = bytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(original, bytes())
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(1, it.version)
            it.rawQuery("SELECT language, revision FROM preferences", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("unsupported", cursor.getString(0))
                assertEquals(7, cursor.getLong(1))
            }

        }
    }

    @Test fun exactEnvelopeByteBoundaryIncludesPreferenceAndRevisionOverhead() {
        val expected = PersonalSnapshot(language = "kn", favorites = setOf("TESTONLY001"), revision = 1)
        val limit = PersonalStore.storedBytes(expected)
        PersonalStore(context, name, maxBytes = limit).use { store ->
            assertEquals(expected, store.change(store.load().revision, language = "kn", favorite = "TESTONLY001"))
        }
        PersonalStore(context, name, maxBytes = limit - 1).use { store ->
            failure(R.string.personal_capacity) { store.load() }
        }
        PersonalStore(context, name).use { assertEquals(expected, it.load()) }
    }

    @Test fun actualConfiguredFavoriteCeilingAndOneExtraAreTransactional() {
        raw(1)
        val limit = context.resources.getInteger(R.integer.max_favorites)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.beginTransaction()
            try {
                db.compileStatement("INSERT INTO favorites VALUES (?)").use { statement ->
                    repeat(limit - 1) { index ->
                        statement.bindString(1, "F" + index.toString().padStart(10, '0'))
                        statement.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        PersonalStore(context, name).use { store ->
            val before = store.load()
            assertEquals(limit, before.favorites.size)
            failure(R.string.personal_capacity) { store.change(before.revision, theme = "dark", favorite = "TESTONLY999") }
            assertEquals(before, store.load())
            assertEquals(limit - 1, store.change(before.revision, favorite = "UNAVAILABLE").favorites.size)
        }
    }

    @Test fun corruptFileAndInvalidStoredScalarArePreserved() {
        raw(1)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            it.execSQL("UPDATE preferences SET revision = 'not-an-integer'")
        }
        var original = bytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(original, bytes())
        context.deleteDatabase(name)
        context.getDatabasePath(name).writeText("synthetic damaged personal database")
        original = bytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(original, bytes())
    }

    @Test fun missingSingletonNeverReinitialized() {
        raw(1)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("DELETE FROM preferences") }
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery("SELECT count(*) FROM preferences", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun failedRoomMigrationRollsBackVersionSchemaAndRecords() {
        raw(1, ", FOREIGN KEY (language) REFERENCES favorites(videoId)")
        PersonalStore(context, name).use { store -> failure(R.string.personal_schema) { store.load() } }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(1, db.version)
            db.rawQuery("PRAGMA table_info(preferences)", null).use { assertEquals(3, it.count) }
            db.rawQuery("SELECT language, revision FROM preferences", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("kn", it.getString(0))
                assertEquals(7, it.getLong(1))
            }
            db.rawQuery("SELECT videoId FROM favorites", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("UNAVAILABLE", it.getString(0))
                assertEquals(1, it.count)
            }
        }

    }

    @Test fun actualSqlWriteFailureRollsBackFavoriteAndPreferencesTogether() {
        PersonalStore(context, name).use { store ->
            val before = store.load()
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TRIGGER synthetic_failure BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
                failure(R.string.personal_failed) { store.change(before.revision, language = "kn", favorite = "TESTONLY001") }
                assertEquals(before, store.load())
                db.execSQL("DROP TRIGGER synthetic_failure")
            }
        }
        PersonalStore(context, name).use { assertEquals(PersonalSnapshot(), it.load()) }
    }
}
