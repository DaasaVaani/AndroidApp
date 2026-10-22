package `in`.daasavaani.core

import java.util.UUID

data class PersonalReference(val kind: String, val id: String) {
    fun valid(): Boolean = when (kind) {
        "lyric" -> validLyricId(id)
        "legacyVideo" -> Regex("[A-Za-z0-9_-]{1,128}").matches(id)
        else -> false
    }
    fun entry(fallback: String? = null) = if (kind == "lyric") ListEntry(kind, lyricId = id, fallback = fallback)
        else ListEntry(kind, videoId = id, fallback = fallback)
    companion object {
        fun validLyricId(id: String) = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(id)
        fun legacy(id: String) = PersonalReference("legacyVideo", id)
        fun lyric(id: String) = PersonalReference("lyric", id)
    }
}

fun ListEntry.reference(): PersonalReference? = when (kind) {
    "lyric" -> lyricId?.let(PersonalReference::lyric)
    "legacyVideo" -> videoId?.let(PersonalReference::legacy)
    else -> null
}

data class Occurrence(val id: String, val entry: ListEntry)
data class RecitationList(val id: String, val name: String, val entries: List<Occurrence>) {
    fun transfer() = ListSnapshot(name, entries.map { it.entry }, 2)
    fun duplicate() = create(name, entries.map { it.entry })
    companion object {
        fun create(name: String, entries: List<ListEntry> = emptyList()) =
            RecitationList(UUID.randomUUID().toString(), name, entries.map { Occurrence(UUID.randomUUID().toString(), it) })
    }
}
