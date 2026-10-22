package `in`.daasavaani.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AnotherLyricTest {
    private val catalog = (1..41).map { index ->
        LyricSummary("10000000-0000-4000-8000-${index.toString().padStart(12, '0')}",
            "Synthetic $index", emptyList(), ArtifactReference("unused", "a".repeat(64), 1, 1))
    }
    @Test fun everyCandidateIncludingBeyondPagesAndBothRandomBoundaries() {
        catalog.indices.forEach { index ->
            assertEquals(catalog[index], anotherLyric(catalog, null) { size ->
                assertEquals(catalog.size, size); index
            })
        }
    }
    @Test fun excludesOnlyExactCurrentIdentityWithoutMutatingCatalog() {
        val original = catalog.toList()
        val eligible = catalog.filterNot { it.lyricId == catalog[20].lyricId }
        eligible.indices.forEach { index ->
            assertEquals(eligible[index], anotherLyric(catalog, catalog[20].lyricId) { size ->
                assertEquals(eligible.size, size); index
            })
        }
        assertEquals(original, catalog)
        assertEquals(catalog.last(), anotherLyric(catalog, "unknown") { it - 1 })
    }
    @Test fun emptyAndSoleCurrentNeverInvokeRandom() {
        assertNull(anotherLyric(emptyList(), null) { error("No random call") })
        assertNull(anotherLyric(catalog.take(1), catalog.first().lyricId) { error("No random call") })
        assertEquals(catalog.first(), anotherLyric(catalog.take(1), null) { assertEquals(1, it); 0 })
    }
    @Test fun invalidRandomIndicesFailExplicitly() {
        assertFailsWith<IllegalArgumentException> { anotherLyric(catalog, null) { -1 } }
        assertFailsWith<IllegalArgumentException> { anotherLyric(catalog, null) { it } }
    }
}
