package `in`.daasavaani.app

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import `in`.daasavaani.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PersonalSnapshot(
    val language: String = "device", val theme: String = "device",
    val favorites: Set<PersonalReference> = emptySet(), val revision: Long = 0,
    val lists: List<RecitationList> = emptyList(),
) {
    // Process-local write fence; deliberately not part of the durable record or equality.
    var epoch: String = ""
        internal set
}

class PersonalFailure(val label: Int, cause: Throwable? = null) : Exception(cause)

/** Every operation validates and replaces one personal snapshot inside one Room transaction. */
class PersonalStore(
    private val context: Context,
    private val name: String = "personal.db",
    private val maxFavorites: Int = context.resources.getInteger(R.integer.max_favorites),
    private val maxBytes: Int = context.resources.getInteger(R.integer.max_personal_stored_bytes),
    private val maxLists: Int = context.resources.getInteger(R.integer.max_lists),
    private val maxTotalEntries: Int = context.resources.getInteger(R.integer.max_total_entries),
) : Closeable {
    private var database: PersonalDatabase? = null
    private var epoch = UUID.randomUUID().toString()
    private val codec = transferCodec(context.resources)

    private fun open(): PersonalDatabase {
        database?.let { return it }
        val file = context.getDatabasePath(name)
        if (!file.exists()) validate(PersonalSnapshot())
        else SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY) {
            throw PersonalFailure(R.string.personal_invalid)
        }.use { db ->
            if (db.version > 3) throw PersonalFailure(R.string.personal_future)
            if (db.version !in 1..3) throw PersonalFailure(R.string.personal_invalid)
            checkSchema(db.version) { db.rawQuery(it, null) }
            if (db.version < 3) validateOld(db.version) { db.rawQuery(it, null) }
            else {
                checkCounts { db.rawQuery(it, null) }
                validate(readRaw(db))
            }
        }
        val value = Room.databaseBuilder(context, PersonalDatabase::class.java, name)
            .openHelperFactory(preservingFactory()).addMigrations(
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        checkSchema(1, db::query); validateOld(1, db::query)
                        MIGRATION_1_2.migrate(db)
                    }
                }, object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        checkSchema(2, db::query); validateOld(2, db::query)
                        MIGRATION_2_3.migrate(db)
                    }
                })
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("INSERT INTO preferences (id, language, theme, revision) VALUES (1, 'device', 'device', 0)")
                }
            }).build()
        try { value.runInTransaction { read(value) } }
        catch (error: Exception) { value.close(); throw error }
        database = value
        return value
    }

    private fun validateOld(version: Int, query: (String) -> Cursor) {
        query("SELECT count(*) FROM favorites").use { c -> c.moveToFirst(); if (c.getLong(0) > maxFavorites) capacity() }
        val preference = query("SELECT * FROM preferences").use { cursor ->
            if (cursor.count != 1 || !cursor.moveToFirst()) invalid()
            integer(cursor, "id"); integer(cursor, "revision"); string(cursor, "language")
            if (version == 2) string(cursor, "theme")
            if (cursor.getInt(cursor.getColumnIndexOrThrow("id")) != 1) invalid()
            PersonalSnapshot(cursor.getString(cursor.getColumnIndexOrThrow("language")),
                if (version == 2) cursor.getString(cursor.getColumnIndexOrThrow("theme")) else "device",
                revision = cursor.getLong(cursor.getColumnIndexOrThrow("revision")))
        }
        val old = linkedSetOf<String>()
        query("SELECT videoId FROM favorites ORDER BY videoId").use { cursor ->
            while (cursor.moveToNext()) {
                string(cursor, "videoId")
                val id = cursor.getString(0)
                if (!Regex("[A-Za-z0-9_-]{11}").matches(id) || !old.add(id)) invalid()
            }
        }
        val projected = preference.copy(favorites = old.map(PersonalReference::legacy).toSet())
        validate(projected)
        val oldBytes = JSONObject().put("formatVersion", 1).put("revision", preference.revision)
            .put("preferences", JSONObject().put("language", preference.language).put("theme", preference.theme))
            .put("favorites", JSONArray(old.toList())).toString().toByteArray(Charsets.UTF_8).size
        if (oldBytes > maxBytes) throw PersonalFailure(R.string.personal_capacity)
    }

    private fun readRaw(db: SQLiteDatabase): PersonalSnapshot {
        val preferences = db.rawQuery("SELECT * FROM preferences", null).use { c ->
            if (c.count != 1 || !c.moveToFirst()) invalid()
            integer(c, "id"); integer(c, "revision"); string(c, "language"); string(c, "theme")
            if (c.getInt(c.getColumnIndexOrThrow("id")) != 1) invalid()
            PersonalSnapshot(c.getString(c.getColumnIndexOrThrow("language")), c.getString(c.getColumnIndexOrThrow("theme")),
                revision = c.getLong(c.getColumnIndexOrThrow("revision")))
        }
        val favorites = linkedSetOf<PersonalReference>()
        db.rawQuery("SELECT * FROM favorites ORDER BY position", null).use { c ->
            while (c.moveToNext()) {
                string(c, "kind"); string(c, "value"); integer(c, "position")
                if (c.getLong(c.getColumnIndexOrThrow("position")) != favorites.size.toLong()) invalid()
                if (!favorites.add(PersonalReference(c.getString(c.getColumnIndexOrThrow("kind")),
                        c.getString(c.getColumnIndexOrThrow("value"))))) invalid()
            }
        }
        val lists = mutableListOf<RecitationList>()
        val entries = linkedMapOf<String, MutableList<Occurrence>>()
        db.rawQuery("SELECT * FROM occurrences ORDER BY position", null).use { c ->
            while (c.moveToNext()) {
                listOf("id", "listId", "kind").forEach { string(c, it) }; integer(c, "position")
                fun nullable(key: String): String? {
                    val index = c.getColumnIndexOrThrow(key)
                    if (c.isNull(index)) return null
                    string(c, key)
                    return c.getString(index)
                }
                val group = entries.getOrPut(c.getString(c.getColumnIndexOrThrow("listId"))) { mutableListOf() }
                if (c.getLong(c.getColumnIndexOrThrow("position")) != group.size.toLong()) invalid()
                group.add(Occurrence(c.getString(c.getColumnIndexOrThrow("id")), ListEntry(
                    c.getString(c.getColumnIndexOrThrow("kind")), nullable("videoId"), nullable("fallback"), nullable("text"), nullable("lyricId"))))
            }
        }
        db.rawQuery("SELECT * FROM lists ORDER BY position", null).use { c ->
            while (c.moveToNext()) {
                listOf("id", "name").forEach { string(c, it) }; integer(c, "position")
                if (c.getLong(c.getColumnIndexOrThrow("position")) != lists.size.toLong()) invalid()
                val id = c.getString(c.getColumnIndexOrThrow("id"))
                lists.add(RecitationList(id, c.getString(c.getColumnIndexOrThrow("name")), entries.remove(id).orEmpty()))
            }
        }
        if (entries.isNotEmpty()) invalid()
        return preferences.copy(favorites = favorites, lists = lists).apply { epoch = this@PersonalStore.epoch }
    }

    private fun read(db: PersonalDatabase): PersonalSnapshot {
        // Raw reads also check SQLite storage classes and positions, not just Room coercions.
        val raw = db.openHelper.readableDatabase
        checkCounts(raw::query)
        val records = db.records().preferences()
        if (records.size != 1) invalid()
        val p = records.single()
        if (p.id != 1) invalid()
        val favorites = db.records().favorites()
        val lists = db.records().lists()
        val occurrences = db.records().entries()
        val grouped = occurrences.groupBy { it.listId }
        val listIds = lists.map { it.id }.toSet()
        val snapshot = PersonalSnapshot(p.language, p.theme, favorites.map { PersonalReference(it.kind, it.value) }.toSet(),
            p.revision, lists.map { list -> RecitationList(list.id, list.name, grouped[list.id].orEmpty()
                .map { Occurrence(it.id, ListEntry(it.kind, it.videoId, it.fallback, it.text, it.lyricId)) }) })
            .apply { epoch = this@PersonalStore.epoch }
        if (favorites.map { it.position } != favorites.indices.toList() ||
            lists.map { it.position } != lists.indices.toList() ||
            grouped.keys.any { it !in listIds } ||
            grouped.values.any { group -> group.map { it.position } != group.indices.toList() }) invalid()
        listOf("preferences", "favorites", "lists", "occurrences").forEach { table ->
            raw.query("SELECT * FROM `$table`").use { c ->
                while (c.moveToNext()) for (i in 0 until c.columnCount) {
                    val column = c.getColumnName(i)
                    val integer = column in setOf("revision", "position") || (table == "preferences" && column == "id")
                    if (c.getType(i) != (if (integer) Cursor.FIELD_TYPE_INTEGER else Cursor.FIELD_TYPE_STRING) &&
                        !(table == "occurrences" && column in setOf("videoId", "lyricId", "fallback", "text") && c.isNull(i))) invalid()
                }
            }
        }
        validate(snapshot)
        return snapshot
    }

    private fun validate(snapshot: PersonalSnapshot) {
        if (snapshot.language !in setOf("device", "en", "kn") || snapshot.theme !in setOf("device", "light", "dark", "warm") ||
            snapshot.revision < 0 || snapshot.favorites.any { !it.valid() }) invalid()
        if (snapshot.favorites.size > maxFavorites || snapshot.lists.size > maxLists ||
            snapshot.lists.sumOf { it.entries.size.toLong() } > maxTotalEntries) capacity()
        val ids = mutableSetOf<String>()
        val listIds = mutableSetOf<String>()
        snapshot.lists.forEach { list ->
            if (!listIds.add(list.id)) invalid()
            validateList(list, ids)
        }
        if (storedBytes(snapshot) > maxBytes) capacity()
    }
    private fun validateList(list: RecitationList, ids: MutableSet<String>) {
        if (!PersonalReference.validLyricId(list.id)) invalid()
        if (list.entries.size > context.resources.getInteger(R.integer.max_entries_per_list)) capacity()
        list.entries.forEach { if (!PersonalReference.validLyricId(it.id) || !ids.add(it.id)) invalid() }
        try { codec.validate(list.transfer()) } catch (error: TransferFailure) {
            if (error.reason == "size") capacity() else invalid()
        }
    }

    @Synchronized fun load(): PersonalSnapshot = guarded {
        val db = open()
        var result: PersonalSnapshot? = null
        db.runInTransaction { result = read(db) }
        requireNotNull(result)
    }

    @Synchronized fun change(expected: PersonalSnapshot, language: String? = null, theme: String? = null,
        favorite: String? = null, reference: PersonalReference? = null): PersonalSnapshot =
        transact(expected.revision, expected.epoch) { before ->
            if (favorite != null && reference != null) invalid()
            if (favorite != null && !Regex("[A-Za-z0-9_-]{11}").matches(favorite)) invalid()
            val target = reference ?: favorite?.let(PersonalReference::legacy)
            val favorites = before.favorites.toMutableSet()
            target?.let { if (!favorites.remove(it)) favorites.add(it) }
            before.copy(language = language ?: before.language, theme = theme ?: before.theme, favorites = favorites)
        }

    @Synchronized fun saveList(expectedRevision: Long, expectedEpoch: String, list: RecitationList,
        confirmedDuplicate: Boolean = false, newOnly: Boolean = false): PersonalSnapshot =
        transact(expectedRevision, expectedEpoch) { before ->
            validateList(list, mutableSetOf())
            val nameKey = PersonalNames.key(list.name)
            val existing = before.lists.find { it.id == list.id }
            if (newOnly && existing != null) invalid()
            val renamed = existing == null || existing.name != list.name
            if (renamed && !confirmedDuplicate && before.lists.any { it.id != list.id &&
                    PersonalNames.key(it.name) == nameKey }) throw PersonalFailure(R.string.duplicate_name)
            val next = before.lists.toMutableList()
            if (existing == null) next.add(list) else next[next.indexOf(existing)] = list
            before.copy(lists = next)
        }

    @Synchronized fun importList(expectedRevision: Long, expectedEpoch: String, snapshot: ListSnapshot,
        confirmed: Boolean, confirmedDuplicate: Boolean = false): PersonalSnapshot = guarded {
        if (!confirmed) invalid()
        codec.json(snapshot)
        saveList(expectedRevision, expectedEpoch, RecitationList.create(snapshot.name, snapshot.entries),
            confirmedDuplicate, newOnly = true)
    }

    @Synchronized fun deleteList(expectedRevision: Long, expectedEpoch: String, id: String): PersonalSnapshot =
        transact(expectedRevision, expectedEpoch) { before ->
            if (before.lists.none { it.id == id }) invalid()
            before.copy(lists = before.lists.filterNot { it.id == id })
        }

    private fun transact(revision: Long, expectedEpoch: String, operation: (PersonalSnapshot) -> PersonalSnapshot): PersonalSnapshot = guarded {
        val db = open()
        if (expectedEpoch != epoch) throw PersonalFailure(R.string.personal_stale)
        var result: PersonalSnapshot? = null
        db.runInTransaction {
            val before = read(db)
            if (before.revision != revision) throw PersonalFailure(R.string.personal_stale)
            if (revision == Long.MAX_VALUE) capacity()
            val after = operation(before).copy(revision = revision + 1)
            validate(after)
            val dao = db.records()
            dao.clearFavorites(); dao.clearEntries(); dao.clearLists()
            after.favorites.forEachIndexed { i, ref -> dao.insertFavorite(PersonalDatabase.Favorite().apply {
                kind = ref.kind; value = ref.id; position = i
            }) }
            after.lists.forEachIndexed { i, list ->
                dao.insertList(PersonalDatabase.SavedList().apply { id = list.id; name = list.name; position = i })
                list.entries.forEachIndexed { j, occurrence -> dao.insertEntry(PersonalDatabase.Entry().apply {
                    id = occurrence.id; listId = list.id; position = j; kind = occurrence.entry.kind
                    videoId = occurrence.entry.videoId; lyricId = occurrence.entry.lyricId
                    fallback = occurrence.entry.fallback; text = occurrence.entry.text
                }) }
            }
            dao.updatePreference(after.language, after.theme, after.revision)
            result = read(db)
        }
        requireNotNull(result)
    }

    private fun <T> guarded(block: () -> T): T = try { block() }
        catch (error: PersonalFailure) { throw error }
        catch (error: IllegalArgumentException) { throw PersonalFailure(R.string.personal_invalid, error) }
        catch (error: IllegalStateException) { throw PersonalFailure(R.string.personal_schema, error) }
        catch (error: Exception) { throw PersonalFailure(R.string.personal_failed, error) }

    @Synchronized override fun close() { database?.close(); database = null; epoch = UUID.randomUUID().toString() }
    private fun invalid(): Nothing = throw PersonalFailure(R.string.personal_invalid)
    private fun capacity(): Nothing = throw PersonalFailure(R.string.personal_capacity)
    private fun string(c: Cursor, column: String) {
        if (c.getType(c.getColumnIndexOrThrow(column)) != Cursor.FIELD_TYPE_STRING) invalid()
    }
    private fun integer(c: Cursor, column: String) {
        if (c.getType(c.getColumnIndexOrThrow(column)) != Cursor.FIELD_TYPE_INTEGER) invalid()
    }
    private fun checkCounts(query: (String) -> Cursor) {
        mapOf("favorites" to maxFavorites, "lists" to maxLists, "occurrences" to maxTotalEntries).forEach { (table, limit) ->
            query("SELECT count(*) FROM `$table`").use { c -> c.moveToFirst(); if (c.getLong(0) > limit) capacity() }
        }
        query("SELECT count(*) FROM occurrences GROUP BY listId").use { c ->
            while (c.moveToNext()) if (c.getLong(0) > context.resources.getInteger(R.integer.max_entries_per_list)) capacity()
        }
    }

    companion object {
        fun storedBytes(snapshot: PersonalSnapshot): Int {
            var bytes = 0L
            fun append(value: String) { bytes += value.toByteArray(Charsets.UTF_8).size }
            fun quote(value: String) = Json.encodeToString(value)
            fun entry(value: ListEntry, id: String? = null) {
                append("{\"kind\":${quote(value.kind)}")
                value.videoId?.let { append(",\"videoId\":${quote(it)}") }
                value.lyricId?.let { append(",\"lyricId\":${quote(it)}") }
                value.fallback?.let { append(",\"fallback\":${quote(it)}") }
                value.text?.let { append(",\"text\":${quote(it)}") }
                id?.let { append(",\"id\":${quote(it)}") }
                append("}")
            }
            append("{\"formatVersion\":3,\"revision\":${snapshot.revision},\"preferences\":{\"language\":${quote(snapshot.language)},\"theme\":${quote(snapshot.theme)}},\"favorites\":[")
            snapshot.favorites.forEachIndexed { index, ref -> if (index > 0) append(","); entry(ref.entry()) }
            append("],\"lists\":[")
            snapshot.lists.forEachIndexed { index, list ->
                if (index > 0) append(",")
                append("{\"id\":${quote(list.id)},\"name\":${quote(list.name)},\"entries\":[")
                list.entries.forEachIndexed { i, occurrence -> if (i > 0) append(","); entry(occurrence.entry, occurrence.id) }
                append("]}")
            }
            append("]}")
            return bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        private fun preservingFactory() = SupportSQLiteOpenHelper.Factory { configuration ->
            val delegate = configuration.callback
            val callback = object : SupportSQLiteOpenHelper.Callback(delegate.version) {
                override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)
                override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = delegate.onUpgrade(db, oldVersion, newVersion)
                override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = delegate.onDowngrade(db, oldVersion, newVersion)
                override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)
                override fun onCorruption(db: SupportSQLiteDatabase) { throw PersonalFailure(R.string.personal_invalid) }
            }
            FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name).callback(callback).noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false).build())
        }
        private fun checkSchema(version: Int, query: (String) -> Cursor) {
            val tables = if (version < 3) mapOf(
                "favorites" to listOf("videoId:TEXT:1:1"),
                "preferences" to listOf("id:INTEGER:1:1", "language:TEXT:1:0", "revision:INTEGER:1:0") +
                    if (version == 2) listOf("theme:TEXT:1:0") else emptyList(),
            ) else mapOf(
                "favorites" to listOf("kind:TEXT:1:1", "value:TEXT:1:2", "position:INTEGER:1:0"),
                "preferences" to listOf("id:INTEGER:1:1", "language:TEXT:1:0", "theme:TEXT:1:0", "revision:INTEGER:1:0"),
                "lists" to listOf("id:TEXT:1:1", "name:TEXT:1:0", "position:INTEGER:1:0"),
                "occurrences" to listOf("id:TEXT:1:1", "listId:TEXT:1:0", "position:INTEGER:1:0", "kind:TEXT:1:0",
                    "videoId:TEXT:0:0", "lyricId:TEXT:0:0", "fallback:TEXT:0:0", "text:TEXT:0:0"),
            )
            query("SELECT name, type, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'").use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    if (c.getString(1) != "table" || name !in tables.keys + setOf("room_master_table", "android_metadata") ||
                        (name in tables && Regex("\\b(CHECK|COLLATE|UNIQUE|GENERATED|VIRTUAL|WITHOUT|STRICT)\\b", RegexOption.IGNORE_CASE)
                            .containsMatchIn(c.getString(2)))) throw PersonalFailure(R.string.personal_invalid)
                }
            }
            tables.forEach { (table, expected) ->
                query("PRAGMA foreign_key_list(`$table`)").use {
                    if (it.count != 0) throw PersonalFailure(R.string.personal_invalid)
                }
                query("PRAGMA table_info(`$table`)").use { c ->
                    val actual = mutableListOf<String>()
                    while (c.moveToNext()) {
                        val column = c.getString(c.getColumnIndexOrThrow("name"))
                        val default = if (table == "preferences" && column == "theme" && version >= 2) "'device'" else null
                        if (c.getString(c.getColumnIndexOrThrow("dflt_value")) != default) throw PersonalFailure(R.string.personal_invalid)
                        actual.add(listOf("name", "type", "notnull", "pk")
                            .joinToString(":") { c.getString(c.getColumnIndexOrThrow(it)) })
                    }
                    if (actual.size != expected.size || actual.toSet() != expected.toSet()) throw PersonalFailure(R.string.personal_invalid)
                }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferences ADD COLUMN theme TEXT NOT NULL DEFAULT 'device'")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites RENAME TO old_favorites")
                db.execSQL("CREATE TABLE favorites (kind TEXT NOT NULL, value TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(kind, value))")
                db.query("SELECT videoId FROM old_favorites ORDER BY videoId").use { c ->
                    var position = 0
                    while (c.moveToNext()) db.execSQL("INSERT INTO favorites VALUES ('legacyVideo', ?, ?)", arrayOf(c.getString(0), position++))
                }
                db.execSQL("DROP TABLE old_favorites")
                db.execSQL("CREATE TABLE lists (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE occurrences (id TEXT NOT NULL PRIMARY KEY, listId TEXT NOT NULL, position INTEGER NOT NULL, kind TEXT NOT NULL, videoId TEXT, lyricId TEXT, fallback TEXT, text TEXT)")
            }
        }
    }
}
