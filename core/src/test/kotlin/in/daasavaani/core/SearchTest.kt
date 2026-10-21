package `in`.daasavaani.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchTest {
    @Test fun fragmentsUseCodePointsAndAdjacentSwaps() {
        assertEquals(0, osaFragment("dir", "vadiraja"))
        assertEquals(1, osaFragment("raed", "reading"))
        assertEquals(1, osaFragment("CA", "ABC"))
        assertEquals(1, osaFragment("😀ab", "x😀bay"))
        assertEquals(0, osaFragment("ಕನ್ನಡ", "ಕನ್ನಡ"))
    }
    @Test fun invalidAndDuplicateLineIdentitiesFail() {
        val line = LyricLine("10000000-0000-4000-8000-000000000001", "test")
        val item = FixtureItem(1, true, "test", "Test", emptyList(), listOf(line))
        validateItem(item, "test")
        assertFailsWith<IllegalArgumentException> { validateItem(item.copy(lines = listOf(line, line)), "test") }
        assertFailsWith<IllegalArgumentException> { validateItem(item, "other") }
        assertFailsWith<IllegalArgumentException> { validateItem(item.copy(fixture = false), "test") }
    }
}
