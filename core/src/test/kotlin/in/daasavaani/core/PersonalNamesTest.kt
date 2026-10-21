package `in`.daasavaani.core

import java.io.File
import java.util.Locale
import kotlinx.serialization.json.*
import kotlin.test.*

class PersonalNamesTest {
    private fun scalars(values: JsonElement) = buildString {
        values.jsonArray.forEach { appendCodePoint(it.jsonPrimitive.int) }
    }
    private fun hex(text: String) = buildString {
        text.trim().split(Regex("\\s+")).forEach { appendCodePoint(it.toInt(16)) }
    }
    private fun resource(name: String) = requireNotNull(javaClass.getResource("/unicode16/$name")).readBytes()

    @Test fun sharedIndependentVectorsAndMalformedUtf16() {
        val fixture = Json.parseToJsonElement(File("../shared/fixtures/unicode-name-matching.json").readText()).jsonObject
        assertEquals(PersonalNames.PROFILE, fixture.getValue("profile").jsonPrimitive.content)
        for (value in fixture.getValue("vectors").jsonArray) {
            val vector = value.jsonObject
            val input = scalars(vector.getValue("input"))
            val label = vector.getValue("id").jsonPrimitive.content
            assertEquals(scalars(vector.getValue("nfc")), PersonalNames.nfc(input), "$label NFC")
            assertEquals(scalars(vector.getValue("nfd")), PersonalNames.nfd(input), "$label NFD")
            assertEquals(scalars(vector.getValue("fold")), PersonalNames.fullCaseFold(input), "$label fold")
            val key = PersonalNames.key(input)
            assertEquals(PersonalNames.PROFILE, key.profile)
            assertEquals(scalars(vector.getValue("key")), key.value, "$label key")
            assertEquals(key, PersonalNames.key(key.value), "$label idempotence")
        }
        for (value in fixture.getValue("comparisons").jsonArray) {
            val pair = value.jsonObject
            assertEquals(pair.getValue("equal").jsonPrimitive.boolean,
                PersonalNames.key(scalars(pair.getValue("left"))) == PersonalNames.key(scalars(pair.getValue("right"))))
        }
        for (units in fixture.getValue("invalidUtf16").jsonArray) {
            val text = units.jsonArray.map { it.jsonPrimitive.int.toChar() }.joinToString("")
            for (method in listOf(PersonalNames::nfc, PersonalNames::nfd, PersonalNames::fullCaseFold)) {
                assertFailsWith<IllegalArgumentException> { method(text) }
            }
            assertFailsWith<IllegalArgumentException> { PersonalNames.key(text) }
        }
    }

    @Test fun allOfficialNormalizationEquationsAndUnlistedScalars() {
        val firstColumn = HashSet<Int>()
        var rows = 0
        File("../shared/resources/unicode16/NormalizationTest.txt").forEachLine { line ->
            val body = line.substringBefore('#').trim()
            if (body.isNotEmpty() && !body.startsWith("@")) {
                val columns = body.split(';').take(5).map(::hex)
                if (columns[0].codePointCount(0, columns[0].length) == 1) firstColumn.add(columns[0].codePointAt(0))
                rows++
                for ((input, target) in listOf(0 to 1, 1 to 1, 2 to 1, 3 to 3, 4 to 3)) {
                    assertEquals(columns[target], PersonalNames.nfc(columns[input]), "NFC row $rows column $input")
                }
                for ((input, target) in listOf(0 to 2, 1 to 2, 2 to 2, 3 to 4, 4 to 4)) {
                    assertEquals(columns[target], PersonalNames.nfd(columns[input]), "NFD row $rows column $input")
                }
            }
        }
        assertEquals(19965, rows)
        for (cp in 0..0x10ffff) {
            if (cp in 0xd800..0xdfff || cp in firstColumn) continue
            val text = String(Character.toChars(cp))
            assertEquals(text, PersonalNames.nfc(text), "Unlisted NFC $cp")
            assertEquals(text, PersonalNames.nfd(text), "Unlisted NFD $cp")
        }
    }

    @Test fun exhaustiveOfficialDefaultFullFolding() {
        val official = HashMap<Int, String>()
        File("../shared/resources/unicode16/CaseFolding.txt").forEachLine { line ->
            val fields = line.substringBefore('#').split(';').map { it.trim() }
            if (fields.size >= 3 && fields[1] in setOf("C", "F")) official[fields[0].toInt(16)] = hex(fields[2])
        }
        var count = 0
        for (cp in 0..0x10ffff) {
            if (cp in 0xd800..0xdfff) continue
            val text = String(Character.toChars(cp))
            assertEquals(official[cp] ?: text, PersonalNames.fullCaseFold(text), "Fold $cp")
            count++
        }
        assertEquals(1112064, count)
    }

    @Test fun pinnedResourcesRejectCorruptionAndUnsupportedProfiles() {
        val bytes = resource("unicode16.json")
        val manifest = resource("manifest.json")
        PersonalNames.verifiedData(bytes, manifest)
        val changed = bytes.copyOf().also { it[it.lastIndex] = ' '.code.toByte() }
        assertFailsWith<IllegalArgumentException> { PersonalNames.verifiedData(changed, manifest) }
        assertFailsWith<IllegalArgumentException> {
            PersonalNames.verifiedData(bytes, manifest.toString(Charsets.UTF_8).replace("16.0.0", "17.0.0").toByteArray())
        }
        assertFailsWith<IllegalArgumentException> {
            PersonalNames.verifiedData(bytes, manifest.toString(Charsets.UTF_8).replace(PersonalNames.PROFILE, "other").toByteArray())
        }
        assertTrue(resource("LICENSE.txt").toString(Charsets.UTF_8).contains("UNICODE LICENSE V3"))
        assertContentEquals(File("../shared/generated/unicode/unicode16.json").readBytes(), bytes)
        assertContentEquals(File("../shared/generated/unicode/manifest.json").readBytes(), manifest)
        assertContentEquals(File("../shared/resources/unicode16/LICENSE.txt").readBytes(), resource("LICENSE.txt"))
    }

    @Test fun boundedOrderingStageOrderAndLocaleIndependence() {
        val bounded = "A" + "\u0315".repeat(63) + "\u0300".repeat(64)
        assertEquals("\u00e0" + "\u0300".repeat(63) + "\u0315".repeat(63), PersonalNames.key(bounded).value)
        assertNotEquals(PersonalNames.key("\u0345\u0301").value, PersonalNames.nfc(PersonalNames.fullCaseFold("\u0345\u0301")))
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            assertEquals("ii\u0307\u0131", PersonalNames.key("I\u0130\u0131").value)
            assertEquals("ss\u03c3\u13a0\u1c8a", PersonalNames.key("\u00df\u03c2\uab70\u1c89").value)
        } finally { Locale.setDefault(previous) }
        assertEquals("", PersonalNames.key("").value)
        assertEquals("\u0000\uffff", PersonalNames.key("\u0000\uffff").value)
        val source = File("src/main/kotlin/in/daasavaani/core/PersonalNames.kt").readText()
        assertFalse(Regex("Normalizer|android\\.icu|lowercase|uppercase|\\\\p\\{").containsMatchIn(source))
    }
}
