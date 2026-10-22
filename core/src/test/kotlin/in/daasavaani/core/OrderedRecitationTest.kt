package `in`.daasavaani.core

import kotlin.test.*

class OrderedRecitationTest {
    private val id = "10000000-0000-4000-8000-000000000001"
    private val entries = listOf(PersonalReference.lyric(id).entry("  literal  "),
        PersonalReference.lyric(id).entry("  literal  "), PersonalReference.legacy(id).entry(),
        ListEntry("placeholder", text = "  repeat 😀  "), ListEntry("placeholder", text = "  repeat 😀  "))

    @Test fun everyOccurrenceIncludingConsecutiveRepeatsAndTypedUuidLegacyIsDistinct() {
        val list = RecitationList.create("Synthetic", entries)
        var session = OrderedRecitation(list)
        assertTrue(session.first)
        assertSame(session, session.previous())
        val ids = mutableSetOf<String>()
        entries.forEachIndexed { index, entry ->
            assertEquals(index, session.position)
            assertEquals(entry, session.current?.entry)
            assertTrue(ids.add(requireNotNull(session.current).id))
            session = session.next()
        }
        assertTrue(session.last)
        assertSame(session, session.next())
        for (index in entries.lastIndex downTo 0) {
            assertEquals(list.entries[index], session.current)
            session = session.previous()
        }
        assertTrue(session.first)
        assertEquals(list, session.list)
    }

    @Test fun emptyAndSingleHaveNoInventedEntryOrWrap() {
        val empty = OrderedRecitation(RecitationList.create("Empty"))
        assertNull(empty.current)
        assertTrue(empty.first && empty.last)
        assertSame(empty, empty.previous())
        assertSame(empty, empty.next())
        val one = OrderedRecitation(RecitationList.create("One", entries.take(1)))
        assertTrue(one.first && one.last)
        assertSame(one, one.next())
        assertSame(one, one.previous())
        assertFailsWith<IllegalArgumentException> { one.copy(position = -1) }
        assertFailsWith<IllegalArgumentException> { one.copy(position = 1) }
    }

    @Test fun changedDeletedReorderedAndReidentifiedListsNeverSilentlyContinue() {
        val list = RecitationList.create("Synthetic", entries)
        val session = OrderedRecitation(list).next()
        assertTrue(session.valid(list.copy()))
        assertFalse(session.valid(null))
        assertFalse(session.valid(list.duplicate()))
        assertFalse(session.valid(list.copy(entries = list.entries.reversed())))
        assertFalse(session.valid(list.copy(entries = list.entries.drop(1))))
        assertFalse(session.valid(list.copy(entries = list.entries.mapIndexed { index, occurrence ->
            if (index == 1) occurrence.copy(entry = ListEntry("placeholder", text = "changed")) else occurrence
        })))
        assertEquals(list.entries[1], session.current)
    }
}
