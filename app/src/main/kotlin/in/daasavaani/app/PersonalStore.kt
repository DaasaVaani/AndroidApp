package `in`.daasavaani.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable

data class PersonalSnapshot(
    val language: String = "device", val theme: String = "device",
    val favorites: Set<String> = emptySet(), val revision: Long = 0,
)

class PersonalFailure(val label: Int, cause: Throwable? = null) : Exception(cause)

/** A single Room transaction fences every personal write; publication data never enters this file. */
class PersonalStore(
    private val context: Context,
    private val name: String = "personal.db",
    private val maxFavorites: Int = context.resources.getInteger(R.integer.max_favorites),
    private val maxBytes: Int = context.resources.getInteger(R.integer.max_personal_stored_bytes),
) : Closeable {
    private var database: PersonalDatabase? = null

    private fun open(): PersonalDatabase {
        database?.let { return it }
        val file = context.getDatabasePath(name)
        val existed = file.exists()
        if (!existed) validate(PersonalSnapshot())
        if (existed) {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) {
                throw PersonalFailure(R.string.personal_invalid)
            }.use {
                if (it.version > 2) throw PersonalFailure(R.string.personal_future)
                if (it.version !in 1..2) throw PersonalFailure(R.string.personal_invalid)
                checkSchema(it)
                val preference = it.rawQuery("SELECT * FROM preferences", null).use { cursor ->
                    if (cursor.count != 1 || !cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndexOrThrow("id")) != 1)
                        throw PersonalFailure(R.string.personal_invalid)
                    if (cursor.getType(cursor.getColumnIndexOrThrow("id")) != android.database.Cursor.FIELD_TYPE_INTEGER ||
                        cursor.getType(cursor.getColumnIndexOrThrow("revision")) != android.database.Cursor.FIELD_TYPE_INTEGER ||
                        cursor.getType(cursor.getColumnIndexOrThrow("language")) != android.database.Cursor.FIELD_TYPE_STRING ||
                        (it.version == 2 && cursor.getType(cursor.getColumnIndexOrThrow("theme")) != android.database.Cursor.FIELD_TYPE_STRING))
                        throw PersonalFailure(R.string.personal_invalid)
                    PersonalSnapshot(cursor.getString(cursor.getColumnIndexOrThrow("language")),
                        if (it.version == 2) cursor.getString(cursor.getColumnIndexOrThrow("theme")) else "device",
                        revision = cursor.getLong(cursor.getColumnIndexOrThrow("revision")))
                }
                val favorites = mutableSetOf<String>()
                it.rawQuery("SELECT videoId FROM favorites", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getType(0) != android.database.Cursor.FIELD_TYPE_STRING)
                            throw PersonalFailure(R.string.personal_invalid)
                        favorites.add(cursor.getString(0))
                    }
                }
                validate(preference.copy(favorites = favorites))
            }
        }
        val value = Room.databaseBuilder(context, PersonalDatabase::class.java, name)
            .openHelperFactory(preservingFactory())
            .addMigrations(MIGRATION_1_2)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("INSERT INTO preferences (id, language, theme, revision) VALUES (1, 'device', 'device', 0)")
                }
            })
            .build()
        try {
            value.runInTransaction {
                read(value)
            }
        } catch (error: Exception) { value.close(); throw error }
        database = value
        return value
    }

    private fun read(db: PersonalDatabase): PersonalSnapshot {
        val records = db.records().preferences()
        if (records.size != 1 || records.single().id != 1) throw PersonalFailure(R.string.personal_invalid)
        val preference = records.single()
        val snapshot = PersonalSnapshot(preference.language, preference.theme,
            db.records().favorites().map { it.videoId }.toSet(), preference.revision)
        validate(snapshot)
        return snapshot
    }

    private fun validate(snapshot: PersonalSnapshot) {
        if (snapshot.language !in setOf("device", "en", "kn") ||
            snapshot.theme !in setOf("device", "light", "dark", "warm") ||
            snapshot.revision < 0 || snapshot.favorites.any { !VIDEO_ID.matches(it) })
            throw PersonalFailure(R.string.personal_invalid)
        if (snapshot.favorites.size > maxFavorites || storedBytes(snapshot) > maxBytes)
            throw PersonalFailure(R.string.personal_capacity)
    }

    @Synchronized fun load(): PersonalSnapshot = guarded {
        val db = open()
        var result: PersonalSnapshot? = null
        db.runInTransaction { result = read(db) }
        requireNotNull(result)
    }

    @Synchronized fun change(
        expectedRevision: Long, language: String? = null, theme: String? = null, favorite: String? = null,
    ): PersonalSnapshot = guarded {
        val db = open()
        var result: PersonalSnapshot? = null
        db.runInTransaction {
            val before = read(db)
            if (before.revision != expectedRevision) throw PersonalFailure(R.string.personal_stale)
            if (before.revision == Long.MAX_VALUE) throw PersonalFailure(R.string.personal_capacity)
            val favorites = before.favorites.toMutableSet()
            favorite?.let { if (!favorites.remove(it)) favorites.add(it) }
            val after = before.copy(language = language ?: before.language, theme = theme ?: before.theme,
                favorites = favorites, revision = before.revision + 1)
            validate(after)
            if (favorite != null) {
                if (favorite in favorites) db.records().insertFavorite(PersonalDatabase.Favorite().apply { videoId = favorite })
                else db.records().deleteFavorite(favorite)
            }
            db.records().updatePreference(after.language, after.theme, after.revision)
            result = read(db)
        }
        requireNotNull(result)
    }

    private fun <T> guarded(block: () -> T): T = try { block() }
        catch (error: PersonalFailure) { throw error }
        catch (error: IllegalStateException) { throw PersonalFailure(R.string.personal_schema, error) }
        catch (error: Exception) { throw PersonalFailure(R.string.personal_failed, error) }

    @Synchronized override fun close() { database?.close(); database = null }

    companion object {
        private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
        fun storedBytes(snapshot: PersonalSnapshot): Int = JSONObject().put("formatVersion", 1)
            .put("revision", snapshot.revision)
            .put("preferences", JSONObject().put("language", snapshot.language).put("theme", snapshot.theme))
            .put("favorites", JSONArray(snapshot.favorites.sorted())).toString().toByteArray(Charsets.UTF_8).size
        private fun preservingFactory() = SupportSQLiteOpenHelper.Factory { configuration ->
            val delegate = configuration.callback
            val callback = object : SupportSQLiteOpenHelper.Callback(delegate.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)
                override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    delegate.onUpgrade(db, oldVersion, newVersion)
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                    delegate.onDowngrade(db, oldVersion, newVersion)
                override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)
                override fun onCorruption(db: SupportSQLiteDatabase) {
                    // Android's default corruption handler deletes the file, even on a read-only open.
                    throw PersonalFailure(R.string.personal_invalid)
                }
            }
            FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name).callback(callback).noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false).build())
        }
        private fun checkSchema(db: SQLiteDatabase) {
            db.rawQuery("SELECT name, type, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (cursor.getString(1) != "table" ||
                        name !in setOf("preferences", "favorites", "room_master_table", "android_metadata"))
                        throw PersonalFailure(R.string.personal_invalid)
                    if (name in setOf("preferences", "favorites") &&
                        Regex("\\b(CHECK|COLLATE|UNIQUE|GENERATED|VIRTUAL|WITHOUT|STRICT)\\b", RegexOption.IGNORE_CASE)
                            .containsMatchIn(cursor.getString(2)))
                        throw PersonalFailure(R.string.personal_invalid)
                }
            }
            val tables = mapOf(
                "favorites" to listOf(Triple("videoId", "TEXT", 1)),
                "preferences" to listOf(Triple("id", "INTEGER", 1), Triple("language", "TEXT", 0),
                    Triple("revision", "INTEGER", 0)) +
                    if (db.version == 2) listOf(Triple("theme", "TEXT", 0)) else emptyList(),
            )
            tables.forEach { (table, expected) ->
                if (db.version == 2) db.rawQuery("PRAGMA foreign_key_list(`$table`)", null).use {
                    if (it.count != 0) throw PersonalFailure(R.string.personal_invalid)
                }
                db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
                    val actual = mutableListOf<Triple<String, String, Int>>()
                    while (cursor.moveToNext()) {
                        if (cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) != 1)
                            throw PersonalFailure(R.string.personal_invalid)
                        actual.add(Triple(cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            cursor.getString(cursor.getColumnIndexOrThrow("type")),
                            cursor.getInt(cursor.getColumnIndexOrThrow("pk"))))
                    }
                    if (actual.toSet() != expected.toSet() || actual.size != expected.size)
                        throw PersonalFailure(R.string.personal_invalid)
                }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Reject unfamiliar v1 layouts before ALTER, even if final Room validation would pass.
                val expected = mapOf("favorites" to setOf("videoId"),
                    "preferences" to setOf("id", "language", "revision"))
                expected.forEach { (table, columns) ->
                    db.query("PRAGMA table_info(`$table`)").use { cursor ->
                        val actual = mutableSetOf<String>()
                        while (cursor.moveToNext()) actual.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        check(actual == columns) { "Unsupported personal v1 schema" }
                    }
                }
                db.execSQL("ALTER TABLE preferences ADD COLUMN theme TEXT NOT NULL DEFAULT 'device'")
            }
        }
    }
}
