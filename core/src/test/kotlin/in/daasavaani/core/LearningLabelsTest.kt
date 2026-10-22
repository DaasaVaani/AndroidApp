package `in`.daasavaani.core

import kotlin.test.*
import org.junit.Test
import java.io.File
import kotlinx.serialization.json.*

class LearningLabelsTest {
    private val lyric = PersonalReference.lyric("00000000-0000-4000-8000-000000000001")
    private val legacy = PersonalReference.legacy(lyric.id)

    @Test fun explicitStatesTypedUniquenessOrderingChangeClearAndNoOps() {
        assertEquals(listOf("wantToLearn", "practising", "comfortableReciting"), LearningLabels.labels)
        assertTrue(LearningLabels.valid(emptyList()))
        var entries = LearningLabels.change(emptyList(), lyric, "wantToLearn")
        entries = LearningLabels.change(entries, legacy, "practising")
        assertSame(entries, LearningLabels.change(entries, lyric, "wantToLearn"))
        assertSame(entries, LearningLabels.change(entries, PersonalReference.legacy("absent"), null))
        entries = LearningLabels.change(entries, lyric, "comfortableReciting")
        assertEquals(listOf(lyric, legacy), entries.map { it.reference })
        assertEquals(listOf(entries[1]), LearningLabels.filtered(entries, "practising"))
        assertEquals(entries, LearningLabels.filtered(entries, null))
        assertTrue(LearningLabels.filtered(entries, "wantToLearn").isEmpty())
        assertEquals(listOf(entries[1]), LearningLabels.change(entries, lyric, null))
        assertFalse(LearningLabels.valid(entries + entries.first()))
        assertFalse(LearningLabels.valid(entries + entries.first().copy(label = "wantToLearn")))
        assertFalse(LearningLabels.valid(listOf(LearningAssignment(lyric, "learned"))))
        assertFailsWith<IllegalArgumentException> { LearningLabels.change(entries, lyric, "none") }
        assertFailsWith<IllegalArgumentException> { LearningLabels.filtered(entries, "none") }
        assertFailsWith<IllegalArgumentException> { LearningLabels.change(entries, PersonalReference.lyric("invalid"), "practising") }
    }

    @Test fun completeConfiguredLimitWithoutTrimmingAndLinearTypedDuplicates() {
        assertEquals(10000, PersonalPolicy.maxLearningLabels)
        val entries = (0 until PersonalPolicy.maxLearningLabels).map {
            LearningAssignment(PersonalReference.legacy("synthetic-$it"), LearningLabels.labels[it % 3])
        }

        assertTrue(LearningLabels.valid(entries))
        assertEquals(entries.size, LearningLabels.change(entries, entries.first().reference, "comfortableReciting").size)
        assertSame(entries, LearningLabels.change(entries, entries.last().reference, entries.last().label))
        assertFailsWith<IllegalArgumentException> {
            LearningLabels.change(entries, PersonalReference.legacy("extra"), "practising")
        }
        assertFalse(LearningLabels.valid(entries + LearningAssignment(PersonalReference.legacy("extra"), "practising")))
        assertFalse(LearningLabels.valid(entries.dropLast(1) + entries.first().copy(label = "comfortableReciting")))
        assertEquals(entries.drop(1), LearningLabels.change(entries, entries.first().reference, null))
    }

    @Test fun pinnedSharedAcceptedAndRejectedSemanticVectorsUseExactTypedIdentity() {
        val fixture = Json.parseToJsonElement(File("../shared/fixtures/personal/learning-labels.json").readText()).jsonObject
        fun entries(vector: JsonElement): List<LearningAssignment> =
            vector.jsonObject.getValue("value").jsonObject.getValue("entries").jsonArray.map {
                val value = it.jsonObject
                val kind = value.getValue("kind").jsonPrimitive.content
                LearningAssignment(PersonalReference(kind,
                    value.getValue(if (kind == "lyric") "lyricId" else "videoId").jsonPrimitive.content),
                    value.getValue("label").jsonPrimitive.content)
            }
        fixture.getValue("accepted").jsonArray.forEach { assertTrue(LearningLabels.valid(entries(it))) }
        fixture.getValue("rejectedSemantic").jsonArray.forEach { assertFalse(LearningLabels.valid(entries(it))) }
    }

    @Test fun minimalTransfersNeverIncludeLocalAssignments() {
        val local = LearningLabels.change(emptyList(), lyric, "comfortableReciting")
        val codec = ListTransfer(TransferBounds(1048576, 4194304, 8192, 128, 4096, 10000),
            "https://daasavaani.github.io/", "open/", 6)
        val list = RecitationList.create("Synthetic", listOf(local.single().reference.entry(), legacy.entry()))
        val json = codec.json(list.transfer())
        assertFalse(json.toString(Charsets.UTF_8).contains("comfortableReciting"))
        assertFalse(json.toString(Charsets.UTF_8).contains("learning"))
        assertEquals(list.entries.map { it.entry }, codec.decodeJson(json).entries)
    }
}
