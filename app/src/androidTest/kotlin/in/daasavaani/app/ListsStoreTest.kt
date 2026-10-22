package `in`.daasavaani.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ListsStoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "lists-test.db"
    private val id = "10000000-0000-4000-8000-000000000001"
    private val lyric = PersonalReference.lyric(id)
    private val legacy = PersonalReference.legacy(id)
    private val entries = listOf(lyric.entry("  User é 😀  "), legacy.entry(), ListEntry("placeholder", text = "  <b>raw</b>  "), lyric.entry())
    @After fun clean() { context.deleteDatabase(name) }
    private fun failure(label: Int, block: () -> Unit) {
        try { block(); fail("Expected refusal") } catch (error: PersonalFailure) { assertEquals(label, error.label) }
    }
    private fun PersonalStore.save(before: PersonalSnapshot, list: RecitationList, duplicate: Boolean = false) =
        saveList(before.revision, before.epoch, list, duplicate)

    @Test fun typedFavoritesKeepKindOrderAndRawIdsAcrossReopen() {
        var expected = PersonalSnapshot()
        PersonalStore(context, name).use { store ->
            var s = store.load()
            listOf(lyric, legacy, PersonalReference.legacy("fixture_1")).forEach {
                s = store.change(s, reference = it)
            }
            assertEquals(listOf(lyric, legacy, PersonalReference.legacy("fixture_1")), s.favorites.toList())
            s = store.change(s, reference = lyric)
            s = store.change(s, reference = lyric)
            assertEquals(listOf(legacy, PersonalReference.legacy("fixture_1"), lyric), s.favorites.toList())
            expected = s
        }
        PersonalStore(context, name).use { assertEquals(expected, it.load()) }
    }

    @Test fun durableOccurrencesEditMoveReplaceRemoveDuplicateAndDeletePreserveIndependentIds() {
        val list = RecitationList.create("  Synthetic é 😀  ", entries)
        var expected = PersonalSnapshot()
        PersonalStore(context, name).use { store ->
            var s = store.save(store.load(), list)
            val changed = list.copy(entries = listOf(list.entries[3], list.entries[0],
                list.entries[2].copy(entry = lyric.entry("replacement"))))
            s = store.save(s, changed)
            assertEquals(changed, s.lists.single())
            val copy = changed.duplicate()
            s = store.save(s, copy, true)
            assertEquals(changed.transfer(), copy.transfer())
            assertTrue(changed.entries.zip(copy.entries).all { (a, b) -> a.id != b.id })
            s = store.deleteList(s.revision, s.epoch, changed.id)
            assertEquals(listOf(copy), s.lists)
            expected = s
        }
        PersonalStore(context, name).use { assertEquals(expected, it.load()) }
    }

    @Test fun duplicateUnicodeNamesRequireExplicitReuseOnSameRevisionNotMergeOrRename() {
        PersonalStore(context, name).use { store ->
            val a = RecitationList.create("Straße é 𐐀", entries)
            val b = RecitationList.create("STRASSE É 𐐨", entries)
            val s = store.save(store.load(), a)
            failure(R.string.duplicate_name) { store.save(s, b) }
            assertEquals(s, store.load())
            val next = store.save(s, b, true)
            assertEquals(listOf(a, b), next.lists)
            failure(R.string.personal_stale) { store.save(s, b.copy(name = "Changed"), true) }
            assertEquals(next, store.load())
            val spaced = RecitationList.create(" STRASSE É 𐐨", entries)
            assertEquals(3, store.save(next, spaced).lists.size)
        }
    }

    @Test fun importsAreConfirmedNewOnlyWithFreshIdsAndSavedV2EvenForOldV1() {
        val codec = transferCodec(context.resources)
        val old = codec.decodeJson("""{"format":"daasavaani-list","formatVersion":1,"name":"  Raw  ","entries":[{"kind":"item","videoId":"$id"},{"kind":"item","videoId":"$id"}]}""".toByteArray())
        PersonalStore(context, name).use { store ->
            val before = store.load()
            failure(R.string.personal_invalid) { store.importList(before.revision, before.epoch, old, false) }
            val first = store.importList(before.revision, before.epoch, old, true)
            val second = store.importList(first.revision, first.epoch, old, true, true)
            assertEquals(2, second.lists.size)
            assertTrue(second.lists.flatMap { it.entries }.all { it.entry.reference() == legacy })
            assertEquals(4, second.lists.flatMap { it.entries }.map { it.id }.toSet().size)
            assertNotEquals(second.lists[0].id, second.lists[1].id)
            second.lists.forEach { list ->
                val exported = codec.decodeFile(codec.encodeFile(list.transfer()).inputStream())
                assertEquals(2, exported.formatVersion)
                assertEquals(list.transfer(), exported)
            }
            failure(R.string.personal_invalid) { store.saveList(second.revision, second.epoch, second.lists.first(), true, true) }
            assertEquals(second, store.load())
        }
    }

    @Test fun exactUtf8StoredBudgetAndCollectionFailuresAreAtomic() {
        val list = RecitationList.create("ಕ 😀", entries)
        val expected = PersonalSnapshot(lists = listOf(list), revision = 1)
        val limit = PersonalStore.storedBytes(expected)
        PersonalStore(context, name, maxBytes = limit, maxLists = 1, maxTotalEntries = entries.size).use { store ->
            val s = store.save(store.load(), list)
            assertEquals(expected, s)
            failure(R.string.personal_capacity) { store.save(s, list.duplicate(), true) }
            failure(R.string.personal_capacity) { store.save(s, list.copy(entries = list.entries + RecitationList.create("x", entries).entries)) }
            failure(R.string.personal_capacity) { store.save(s, list.copy(name = list.name + "ಕ")) }
            assertEquals(s, store.load())
        }
        PersonalStore(context, name, maxBytes = limit - 1).use { store -> failure(R.string.personal_capacity) { store.load() } }
        PersonalStore(context, name).use { assertEquals(expected, it.load()) }
    }

    @Test fun malformedReferencesNamesOccurrenceIdsAndForeignEntriesNeverMutate() {
        PersonalStore(context, name).use { store ->
            val s = store.save(store.load(), RecitationList.create("Synthetic", entries))
            val list = s.lists.single()
            listOf(list.copy(name = "\uD800"), list.copy(name = ""), list.copy(id = "ABCDEF01-0000-4000-8000-000000000001"),
                list.copy(entries = list.entries + list.entries.first()),
                list.copy(entries = listOf(Occurrence(id, ListEntry("lyric", lyricId = "not-uuid")))),
                list.copy(entries = listOf(Occurrence(id, ListEntry("placeholder", text = "a\nb")))),
                list.copy(entries = listOf(Occurrence(id, ListEntry("legacyVideo", videoId = "https://example.invalid"))))
            ).forEach { invalid -> failure(R.string.personal_invalid) { store.save(s, invalid) }; assertEquals(s, store.load()) }
            failure(R.string.personal_invalid) { store.save(s, RecitationList.create("Other").copy(entries = list.entries)) }
            assertEquals(s, store.load())
        }
    }

    @Test fun durableListAboveTransportDecodedCeilingRemainsUsableButCannotExport() {
        val resources = context.resources
        val codec = transferCodec(resources)
        val text = "ಕ".repeat(resources.getInteger(R.integer.max_entry_text_code_points))
        val count = resources.getInteger(R.integer.max_list_decoded_bytes) / text.toByteArray(Charsets.UTF_8).size + 1
        assertTrue(count <= resources.getInteger(R.integer.max_entries_per_list))
        val list = RecitationList.create("Synthetic large local list",
            List(count) { ListEntry("placeholder", text = text) })
        var saved = PersonalSnapshot()
        PersonalStore(context, name).use { store ->
            saved = store.save(store.load(), list)
            try { codec.encodeFile(list.transfer()); fail("Oversized transport accepted") }
            catch (error: TransferFailure) { assertEquals("size", error.reason) }
            assertEquals(saved, store.load())
        }
        PersonalStore(context, name).use { assertEquals(saved, it.load()) }
    }

    @Test fun staleEpochRevisionExhaustionAndSqlFailureKeepEntireSnapshot() {
        PersonalStore(context, name).use { store ->
            val before = store.load()
            failure(R.string.personal_stale) { store.saveList(before.revision, "old", RecitationList.create("Synthetic")) }
            val saved = store.save(before, RecitationList.create("Synthetic", entries))
            failure(R.string.personal_stale) { store.save(before, saved.lists.single()) }
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TRIGGER synthetic_failure BEFORE UPDATE ON preferences BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
                failure(R.string.personal_failed) { store.save(saved, saved.lists.single().copy(name = "Changed")) }
                assertEquals(saved, store.load())
                db.execSQL("DROP TRIGGER synthetic_failure")
                db.execSQL("UPDATE preferences SET revision = ${Long.MAX_VALUE}")
            }
            val exhausted = store.load()
            failure(R.string.personal_capacity) { store.save(exhausted, exhausted.lists.single()) }
            store.close()
            val reopened = store.load()
            assertNotEquals(exhausted.epoch, reopened.epoch)
            failure(R.string.personal_stale) { store.deleteList(reopened.revision, exhausted.epoch, reopened.lists.single().id) }
        }
    }

    @Test fun malformedV3StorageClassesAndFutureVersionsRefuseWithoutChangingBytes() {
        PersonalStore(context, name).use { store -> store.save(store.load(), RecitationList.create("Synthetic", entries)) }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.execSQL("UPDATE occurrences SET position = 'not-integer'") }
        val before = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(before, context.getDatabasePath(name).readBytes())
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { it.version = 99 }
        val future = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_future) { store.load() } }
        assertArrayEquals(future, context.getDatabasePath(name).readBytes())
    }

    @Test fun actualConfiguredListPerListAndAggregateCeilingsRefuseOneMoreAtomically() {
        PersonalStore(context, name).use { it.load() }
        val listLimit = context.resources.getInteger(R.integer.max_lists)
        val perList = context.resources.getInteger(R.integer.max_entries_per_list)
        val total = context.resources.getInteger(R.integer.max_total_entries)
        fun uuid(index: Int) = "30000000-0000-4000-8000-" + index.toString(16).padStart(12, '0')
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.beginTransaction()
            try {
                db.compileStatement("INSERT INTO lists (id, name, position) VALUES (?, 'Synthetic', ?)").use { statement ->
                    repeat(listLimit) { i -> statement.bindString(1, uuid(i)); statement.bindLong(2, i.toLong()); statement.executeInsert() }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        PersonalStore(context, name).use { store ->
            val s = store.load()
            assertEquals(listLimit, s.lists.size)
            failure(R.string.personal_capacity) { store.save(s, RecitationList.create("Extra")) }
            assertEquals(s, store.load())
        }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.beginTransaction()
            try {
                db.compileStatement("INSERT INTO occurrences (id, listId, position, kind, text) VALUES (?, ?, ?, 'placeholder', 'Synthetic')").use { statement ->
                    repeat(total) { i ->
                        statement.bindString(1, uuid(listLimit + i)); statement.bindString(2, uuid(i / perList))
                        statement.bindLong(3, (i % perList).toLong()); statement.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        PersonalStore(context, name).use { store ->
            val s = store.load()
            assertEquals(total, s.lists.sumOf { it.entries.size })
            assertEquals(perList, s.lists.first().entries.size)
            val repeated = s.lists.first().copy(entries = s.lists.first().entries + Occurrence(uuid(999999), ListEntry("placeholder", text = "Extra")))
            failure(R.string.personal_capacity) { store.save(s, repeated) }
            val empty = s.lists.first { it.entries.isEmpty() }
            failure(R.string.personal_capacity) { store.save(s, empty.copy(entries = listOf(Occurrence(uuid(999998), lyric.entry())))) }
            assertEquals(s, store.load())
        }
    }

    @Test fun actualV2SchemaMigratesOnlyStrictOldRecordsAndPreservesBytesOnProjectedCapacityFailure() {
        fun raw(favorite: String = "UNAVAILABLE", defaultTheme: String = "'device'") {
            context.deleteDatabase(name)
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TABLE preferences (id INTEGER NOT NULL PRIMARY KEY, language TEXT NOT NULL, theme TEXT NOT NULL DEFAULT $defaultTheme, revision INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE favorites (videoId TEXT NOT NULL PRIMARY KEY)")
                db.execSQL("INSERT INTO preferences VALUES (1, 'kn', 'warm', 7)")
                db.execSQL("INSERT INTO favorites VALUES (?)", arrayOf(favorite))
                db.version = 2
            }
        }
        raw()
        var original = context.getDatabasePath(name).readBytes()
        val projectedLimit = PersonalStore.storedBytes(PersonalSnapshot("kn", "warm",
            setOf(PersonalReference.legacy("UNAVAILABLE")), 7)) - 1
        PersonalStore(context, name, maxBytes = projectedLimit).use { store -> failure(R.string.personal_capacity) { store.load() } }
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        PersonalStore(context, name).use { store ->
            val s = store.load()
            assertEquals("kn", s.language); assertEquals("warm", s.theme); assertEquals(7, s.revision)
            assertEquals(listOf(PersonalReference.legacy("UNAVAILABLE")), s.favorites.toList())
            assertTrue(s.lists.isEmpty())
        }
        raw(id)
        original = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
        raw(defaultTheme = "'warm'")
        original = context.getDatabasePath(name).readBytes()
        PersonalStore(context, name).use { store -> failure(R.string.personal_invalid) { store.load() } }
        assertArrayEquals(original, context.getDatabasePath(name).readBytes())
    }

    @Test fun actualConfiguredUtf8StoredCeilingAndOneAdditionalBytePreserveUsableData() {
        val limit = context.resources.getInteger(R.integer.max_personal_stored_bytes)
        val count = context.resources.getInteger(R.integer.max_total_entries)
        val textLimit = context.resources.getInteger(R.integer.max_entry_text_code_points)
        val baseText = "ಕ".repeat(400)
        val groups = (0 until count).chunked(2500)
        var snapshot = PersonalSnapshot(revision = 1, lists = groups.mapIndexed { index, values ->
            RecitationList.create("Synthetic byte budget $index", values.map { ListEntry("placeholder", text = baseText) })
        })
        var remaining = limit - PersonalStore.storedBytes(snapshot)
        assertTrue(remaining > 0)
        snapshot = snapshot.copy(lists = snapshot.lists.map { list -> list.copy(entries = list.entries.map { occurrence ->
            val extra = minOf(remaining, (textLimit - 400) * 3)
            remaining -= extra
            val suffix = "ಕ".repeat(extra / 3) + when (extra % 3) { 1 -> "a"; 2 -> "é"; else -> "" }
            if (extra == 0) occurrence else occurrence.copy(entry = occurrence.entry.copy(text = baseText + suffix))
        }) })
        assertEquals(0, remaining)
        assertEquals(limit, PersonalStore.storedBytes(snapshot))
        PersonalStore(context, name).use { it.load() }
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("UPDATE preferences SET revision = 1")
                db.compileStatement("INSERT INTO lists VALUES (?, ?, ?)").use { lists ->
                    db.compileStatement("INSERT INTO occurrences (id, listId, position, kind, text) VALUES (?, ?, ?, 'placeholder', ?)").use { entries ->
                        snapshot.lists.forEachIndexed { i, list ->
                            lists.bindString(1, list.id); lists.bindString(2, list.name); lists.bindLong(3, i.toLong()); lists.executeInsert()
                            list.entries.forEachIndexed { j, occurrence ->
                                entries.bindString(1, occurrence.id); entries.bindString(2, list.id)
                                entries.bindLong(3, j.toLong()); entries.bindString(4, occurrence.entry.text!!)
                                entries.executeInsert()
                            }
                        }
                    }
                }
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
        }
        PersonalStore(context, name).use { store ->
            val before = store.load()
            assertEquals(snapshot, before)
            val last = before.lists.last()
            val final = last.entries.last()
            val after = last.copy(entries = last.entries.dropLast(1) + final.copy(entry = final.entry.copy(text = final.entry.text + "a")))
            assertEquals(limit + 1, PersonalStore.storedBytes(before.copy(revision = 2, lists = before.lists.dropLast(1) + after)))
            failure(R.string.personal_capacity) { store.save(before, after) }
            assertEquals(before, store.load())
        }
    }
}
