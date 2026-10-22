package `in`.daasavaani.core

import kotlin.test.*

class FavoritesBrowsingTest {
    private val codec = LyricPreviewCodec(PreviewBounds(1, 1, 1, 1, 1, 100, 1, 100, 1, 10, 4))
    private fun id(i: Int) = "10000000-0000-4000-8000-${i.toString().padStart(12, '0')}"
    private val artifact = ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 1, 1)
    private val catalog = LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"),
        (1..41).map { LyricSummary(id(it), "Synthetic [${it}]", emptyList(), artifact) })

    @Test fun literalTypedFavoritesKeepOrderUnknownsAndIndependentQueries() {
        val refs = linkedSetOf(PersonalReference.lyric(id(41)), PersonalReference.legacy(id(41)),
            PersonalReference.lyric(id(99)), PersonalReference.lyric(id(1)))
        assertEquals(refs.toList(), codec.favorites(refs, null, ""))
        assertEquals(refs.toList(), codec.favorites(refs, catalog, ""))
        assertTrue(codec.favorites(refs, null, "synthetic").isEmpty())
        assertEquals(listOf(PersonalReference.lyric(id(41))), codec.favorites(refs, catalog, "[41]"))
        assertEquals(listOf(PersonalReference.lyric(id(41)), PersonalReference.lyric(id(1))),
            codec.favorites(refs, catalog, "sYnThEtIc"))
        assertTrue(codec.favorites(refs, catalog, ".*").isEmpty())
        assertTrue(codec.favorites(refs, catalog, " Synthetic").isEmpty())
        assertFailsWith<IllegalArgumentException> { codec.favorites(refs, catalog, "x".repeat(11)) }
        assertFailsWith<IllegalArgumentException> { codec.favorites(refs, null, "\uD800") }
        assertEquals(41, codec.filter(catalog, "").size)
    }

    @Test fun expandedPagesReachExactBoundariesAndClampAfterChanges() {
        for (page in listOf(1, 20, 31)) {
            for (total in listOf(page, page * 2, page * 2 + 1)) {
                assertEquals(page, expandedResultCount(total, page, 1))
                assertEquals(minOf(total, page * 2), expandedResultCount(total, page, 2))
                assertEquals(total, expandedResultCount(total, page, 3))
            }
        }
        assertEquals(0, expandedResultCount(0, 20, 3))
        assertEquals(1, expandedResultCount(1, 20, 3))
        assertEquals(Int.MAX_VALUE, expandedResultCount(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertFailsWith<IllegalArgumentException> { expandedResultCount(1, 0, 1) }
        assertFailsWith<IllegalArgumentException> { expandedResultCount(-1, 20, 1) }
        assertFailsWith<IllegalArgumentException> { expandedResultCount(1, 20, 0) }
    }
}
