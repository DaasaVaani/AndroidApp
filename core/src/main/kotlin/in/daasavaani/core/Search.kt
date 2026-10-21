package `in`.daasavaani.core

import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

data class Hit(val itemId: String, val metadata: Boolean, val score: Int, val worst: Int, val edits: Int)
private fun points(text: String) = text.codePoints().toArray()
fun osaFragment(query: String, token: String): Int {
    val a = points(query)
    val b = points(token)
    var previous = IntArray(a.size + 1) { it }
    var beforePrevious = previous
    var best = a.size
    for (j in 1..b.size) {
        val current = IntArray(a.size + 1)
        for (i in 1..a.size) {
            current[i] = minOf(previous[i] + 1, current[i - 1] + 1,
                previous[i - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                current[i] = min(current[i], beforePrevious[i - 2] + 1)
            }
        }
        best = min(best, current[a.size])
        beforePrevious = previous
        previous = current
    }
    return best
}
fun search(query: String, index: SearchIndex, policy: SearchPolicy, checkCancellation: () -> Unit = {}): List<Hit> {
    require(points(query).size <= policy.maxQueryCodePoints) { "Query exceeds code-point limit" }
    require(query.codePoints().toArray().none {
        Character.getType(it) == Character.CONTROL.toInt() || Character.getType(it) == Character.FORMAT.toInt()
    }) { "Unsupported control character" }
    require(!query.contains('~') && !query.contains('^')) { "Technical-profile matching is not implemented in this fixture milestone" }
    val normalized = Regex("[A-Z]").replace(Normalizer.normalize(query, Normalizer.Form.NFC)) { it.value.lowercase(Locale.ROOT) }
    val queries = Regex("[\\p{L}\\p{M}\\p{Nd}]+").findAll(normalized).map { it.value }.distinct().toList()
    require(queries.size <= policy.maxTerms) { "Too many distinct query terms" }
    if (queries.isEmpty()) return emptyList()
    var hits: Map<String, Hit>? = null
    for (term in queries) {
        checkCancellation()
        val letters = points(term)
        val allowed = requireNotNull(policy.editBands.firstOrNull { letters.size <= it.maxLength }).edits
        val bounds = listOf(letters.size - allowed, letters.size - 1 - 3 * allowed, letters.size - 2 - 4 * allowed)
        var candidateIds: Set<Int>? = null
        for (size in 1..3) {
            if (bounds[size - 1] <= 0) continue
            val counts = mutableMapOf<String, Int>()
            for (offset in 0..letters.size - size) {
                val gram = String(letters, offset, size)
                counts[gram] = (counts[gram] ?: 0) + 1
            }
            val overlaps = mutableMapOf<Int, Int>()
            for ((gram, count) in counts) for (posting in index.postings["$size:$gram"].orEmpty()) {
                require(posting.size == 2 && posting[0] in index.tokens.indices && posting[1] > 0) { "Invalid posting" }
                overlaps[posting[0]] = (overlaps[posting[0]] ?: 0) + min(count, posting[1])
            }
            val next = overlaps.filterValues { it >= bounds[size - 1] }.keys
            candidateIds = candidateIds?.intersect(next) ?: next
        }
        data class Match(val weight: Int, val edits: Int)
        val best = mutableMapOf<String, Match>()
        for (id in requireNotNull(candidateIds)) {
            checkCancellation()
            val token = index.tokens[id]
            val edits = osaFragment(term, token.value)
            if (edits > allowed) continue
            val weight = requireNotNull(policy.weights[token.field]) { "Unknown index field" }
            val previous = best[token.itemId]
            if (previous == null || weight > previous.weight || (weight == previous.weight && edits < previous.edits)) {
                best[token.itemId] = Match(weight, edits)
            }
        }
        val next = mutableMapOf<String, Hit>()
        for ((id, match) in best) {
            val previous = hits?.get(id)
            if (hits != null && previous == null) continue
            next[id] = Hit(id, previous?.metadata == true || match.weight > requireNotNull(policy.weights["lyrics"]),
                (previous?.score ?: 0) + match.weight, maxOf(previous?.worst ?: 0, match.edits), (previous?.edits ?: 0) + match.edits)
        }
        hits = next
    }
    return hits.orEmpty().values.sortedWith(compareByDescending<Hit> { it.metadata }
        .thenByDescending { it.score }.thenBy { it.worst }.thenBy { it.edits }.thenBy { it.itemId })
}
