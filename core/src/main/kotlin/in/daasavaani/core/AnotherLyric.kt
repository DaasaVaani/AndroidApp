package `in`.daasavaani.core

fun anotherLyric(
    catalog: List<LyricSummary>, excludedId: String?, randomIndex: (Int) -> Int,
): LyricSummary? {
    val candidates = catalog.filterNot { it.lyricId == excludedId }
    if (candidates.isEmpty()) return null
    val index = randomIndex(candidates.size)
    require(index in candidates.indices) { "Random index is outside the eligible catalog" }
    return candidates[index]
}
