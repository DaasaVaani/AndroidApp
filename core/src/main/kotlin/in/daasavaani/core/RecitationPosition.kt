package `in`.daasavaani.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExactRecitationPosition(val listId: String, val entryId: String) {
    fun valid() = PersonalReference.validLyricId(listId) && PersonalReference.validLyricId(entryId)
}

@Serializable
data class RecitationPositionV1(val position: ExactRecitationPosition? = null) {
    fun valid() = position?.valid() != false

    fun resolve(lists: List<RecitationList>): RecitationResolution {
        val pointer = position ?: return RecitationResolution("none")
        val list = lists.find { it.id == pointer.listId } ?: return RecitationResolution("listMissing")
        val index = list.entries.indexOfFirst { it.id == pointer.entryId }
        return if (index < 0) RecitationResolution("entryMissing", list)
            else RecitationResolution("available", list, index)
    }

    companion object {
        fun parse(text: String): RecitationPositionV1 {
            require(kotlinx.serialization.json.Json.parseToJsonElement(text).let {
                it is kotlinx.serialization.json.JsonObject && it.keys == setOf("position")
            })
            return Json.decodeFromString<RecitationPositionV1>(text).also { require(it.valid()) }
        }
    }
}

data class RecitationResolution(val kind: String, val list: RecitationList? = null, val index: Int? = null)
