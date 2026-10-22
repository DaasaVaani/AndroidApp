package `in`.daasavaani.core

import kotlin.test.*
import org.junit.Test

class RecentHistoryTest {
    private val id = "10000000-0000-4000-8000-000000000001"
    @Test fun configuredDefaultExactTypedIdentityAndRollingOrder() {
        val lyric = PersonalReference.lyric(id)
        val legacy = PersonalReference.legacy(id)
        var history = RecentHistory()
        assertTrue(history.enabled)
        assertEquals(PersonalPolicy.recentHistoryEnabled, history.enabled)
        history = history.opened(lyric).opened(legacy)
        assertEquals(listOf(legacy, lyric), history.entries)
        assertSame(history, history.opened(legacy))
        history = history.opened(lyric)
        assertEquals(listOf(lyric, legacy), history.entries)
        repeat(PersonalPolicy.maxRecents) { history = history.opened(PersonalReference.legacy("fixture_$it")) }
        assertEquals(PersonalPolicy.maxRecents, history.entries.size)
        assertEquals("fixture_${PersonalPolicy.maxRecents - 1}", history.entries.first().id)
        assertFalse(lyric in history.entries)
        assertTrue(history.valid())
    }

    @Test fun disabledRetainsAndReenabledMovesWithoutChangingInput() {
        val a = PersonalReference.lyric(id)
        val b = PersonalReference.legacy(id)
        val history = RecentHistory().opened(a)
        val off = history.copy(enabled = false)
        assertSame(off, off.opened(b))
        assertEquals(listOf(a), off.entries)
        assertEquals(listOf(b, a), off.copy(enabled = true).opened(b).entries)
        assertEquals(listOf(a), history.entries)
        assertEquals(false, off.copy(entries = emptyList()).enabled)
    }

    @Test fun actualConfiguredLimitPlusOneDuplicatesAndMalformedReferencesRefuse() {
        val refs = List(PersonalPolicy.maxRecents) { PersonalReference.legacy("fixture_$it") }
        assertTrue(RecentHistory(entries = refs).valid())
        assertFalse(RecentHistory(entries = refs + PersonalReference.legacy("extra")).valid())
        assertFalse(RecentHistory(entries = listOf(refs.first(), refs.first())).valid())
        assertFalse(RecentHistory(entries = listOf(PersonalReference.lyric("not-a-uuid"))).valid())
        assertFailsWith<IllegalArgumentException> { RecentHistory().opened(PersonalReference("other", id)) }
    }
}
