package `in`.daasavaani.core

import java.io.File
import kotlin.test.*
import kotlinx.serialization.json.*

class RecitationPositionTest {
    private val fixture = Json.parseToJsonElement(File("../shared/fixtures/personal/recitation-position.json").readText()).jsonObject

    @Test fun everySharedStructuralVectorIsEnforcedWithoutNormalization() {
        fixture.getValue("accepted").jsonArray.forEach {
            assertTrue(RecitationPositionV1.parse(it.jsonObject.getValue("value").toString()).valid())
        }
        fixture.getValue("rejected").jsonArray.forEach {
            assertFails(it.jsonObject.getValue("label").jsonPrimitive.content) {
                RecitationPositionV1.parse(it.jsonObject.getValue("value").toString())
            }
        }
    }

    @Test fun everySharedResolutionVectorFollowsExactOccurrenceAndCurrentContents() {
        fixture.getValue("resolution").jsonArray.forEach { vector ->
            val value = vector.jsonObject
            val lists = value.getValue("lists").jsonArray.map { raw ->
                val list = raw.jsonObject
                RecitationList(list.getValue("id").jsonPrimitive.content, list.getValue("name").jsonPrimitive.content,
                    list.getValue("entries").jsonArray.map {
                        val entry = it.jsonObject
                        fun field(name: String) = entry[name]?.jsonPrimitive?.content
                        Occurrence(requireNotNull(field("id")), ListEntry(requireNotNull(field("kind")),
                            field("videoId"), field("fallback"), field("text"), field("lyricId")))
                    })
            }
            val result = RecitationPositionV1.parse(value.getValue("value").toString()).resolve(lists)
            val expected = value.getValue("expected").jsonObject
            assertEquals(expected.getValue("kind").jsonPrimitive.content, result.kind)
            assertEquals(expected["index"]?.jsonPrimitive?.int, result.index)
            if (result.kind == "available") {
                val pointer = RecitationPositionV1.parse(value.getValue("value").toString()).position!!
                assertEquals(pointer.entryId, result.list!!.entries[result.index!!].id)
            }
        }
    }

    @Test fun minimalTransferHasNoPositionOrLocalIdentities() {
        val list = RecitationList.create("Synthetic", listOf(ListEntry("placeholder", text = " raw ")))
        val transfer = list.transfer()
        assertEquals(listOf(list.entries.single().entry), transfer.entries)
        val duplicated = list.duplicate()
        val saved = RecitationPositionV1(ExactRecitationPosition(list.id, list.entries.single().id))
        assertEquals("listMissing", saved.resolve(listOf(duplicated)).kind)
        assertNotEquals(list.id, duplicated.id)
        assertNotEquals(list.entries.single().id, duplicated.entries.single().id)
    }
}
