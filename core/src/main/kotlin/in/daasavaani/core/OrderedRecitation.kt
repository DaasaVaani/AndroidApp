package `in`.daasavaani.core

/** Process-local traversal of exact saved occurrences, never inferred references. */
data class OrderedRecitation(val list: RecitationList, val position: Int = 0) {
    init { require(position == 0 && list.entries.isEmpty() || position in list.entries.indices) }
    val current: Occurrence? get() = list.entries.getOrNull(position)
    val first: Boolean get() = position == 0
    val last: Boolean get() = list.entries.isEmpty() || position == list.entries.lastIndex
    fun previous() = if (first) this else copy(position = position - 1)
    fun next() = if (last) this else copy(position = position + 1)
    fun valid(currentList: RecitationList?) = list == currentList
}
