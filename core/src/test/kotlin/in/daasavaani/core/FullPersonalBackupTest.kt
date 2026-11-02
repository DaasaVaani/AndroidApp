package `in`.daasavaani.core

import java.io.ByteArrayInputStream
import java.util.zip.CRC32
import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class FullPersonalBackupTest {
    private val listCodec = ListTransfer(TransferBounds(9437184, 8388608, 2048, 128, 512, 10000),
        "https://daasavaani.github.io/", "open/", 6)
    private val codec = FullPersonalBackupCodec(listCodec, 6)
    private val fixture = Json.parseToJsonElement(requireNotNull(javaClass.getResource("/full-backup.json")).readText()).jsonObject
    private val accepted get() = fixture.getValue("accepted").jsonArray.map { it.jsonObject.getValue("value") }
    private fun decode(value: JsonElement) = codec.decodeJson(value.toString().toByteArray())
    private fun base() = decode(accepted[2])
    private fun refused(text: String) = assertFailsWith<TransferFailure> { codec.decodeJson(text.toByteArray()) }
    private fun edit(value: JsonElement, path: List<String>, replacement: JsonElement): JsonElement {
        if (path.isEmpty()) return replacement
        val obj = value.jsonObject
        return JsonObject(obj + (path.first() to edit(obj[path.first()] ?: JsonNull, path.drop(1), replacement)))
    }

    @Test fun sharedNativeVectorsAcceptedWebVectorsRefused() {
        accepted.forEach { value ->
            if (value.jsonObject["platform"]!!.jsonPrimitive.content == "android") {
                val decoded = decode(value)
                assertEquals(decoded, codec.decode(codec.encode(decoded).inputStream()))
                assertEquals("device", decoded.data.preferences.language)
                assertFalse(decoded.data.recentHistory.enabled)
                assertEquals("native-unresolved", decoded.data.recentHistory.entries.single().id)
            } else assertFailsWith<TransferFailure> { decode(value) }
        }
        fixture.getValue("rejected").jsonArray.forEach { case ->
            val item = case.jsonObject
            val mutated = edit(accepted[item.getValue("base").jsonPrimitive.int],
                item.getValue("path").jsonArray.map { it.jsonPrimitive.content }, item.getValue("value"))
            assertFailsWith<TransferFailure>(item.getValue("label").jsonPrimitive.content) { decode(mutated) }
        }
    }

    @Test fun rawOrderTypedIdentityOccurrenceIdsFallbackAbsenceAndUnavailablePairRoundTrip() {
        val web = accepted[1].jsonObject
        val webData = web.getValue("data").jsonObject
        val native = JsonObject(web + mapOf("platform" to JsonPrimitive("android"), "data" to JsonObject(
            webData.filterKeys { it != "history" } + mapOf(
                "preferences" to accepted[2].jsonObject["data"]!!.jsonObject["preferences"]!!,
                "recentHistory" to webData.getValue("history")))))
        val value = decode(native)
        val roundTrip = codec.decode(codec.encode(value).inputStream())
        assertEquals(value, roundTrip)
        assertEquals(2, roundTrip.data.favorites.toSet().size)
        assertEquals("  é ಕನ್ನಡ  ", roundTrip.data.lists[0].entries[0].entry.fallback)
        assertNull(roundTrip.data.lists[0].entries[1].entry.fallback)
        assertEquals("  synthetic 😀  ", roundTrip.data.lists[0].entries[2].entry.text)
        assertEquals(listOf(roundTrip.data.lists.map { it.id }), roundTrip.duplicateNameGroups())
        val missing = value.copy(data = value.data.copy(lists = emptyList()))
        assertEquals(missing.data.recitation, codec.decode(codec.encode(missing).inputStream()).data.recitation)
    }

    @Test fun nativeVersion3PreservesEverySetupStateAndFrozenVersion1RemainsPending() {
        val original = base()
        assertEquals("pending", original.data.setupStatus)
        assertEquals(1, original.formatVersion)
        for (status in listOf("pending", "completed", "skipped")) {
            val value = original.copy(data = original.data.copy(setupStatus = status), formatVersion = 3)
            assertEquals(value, codec.decode(codec.encode(value).inputStream()))
            val json = codec.json(value).toString(Charsets.UTF_8)
            assertTrue(json.contains("\"setupStatus\":\"$status\""))
            refused(json.replace(",\"setupStatus\":\"$status\"", ""))
            refused(json.replace("\"setupStatus\":\"$status\"", "\"setupStatus\":\"ready\""))
            refused(json.replace("\"platform\":\"android\"", "\"platform\":\"web\""))
            refused(json.replace("\"formatVersion\":3", "\"formatVersion\":2"))
            refused(json.replace("\"formatVersion\":3", "\"formatVersion\":4"))
            if (status != "pending") assertFailsWith<TransferFailure> {
                codec.encode(value.copy(formatVersion = 1))
            }
        }
        assertEquals(original, codec.decode(codec.encode(original).inputStream()))
        assertFalse(codec.json(original).toString(Charsets.UTF_8).contains("setupStatus"))
        for (invalid in listOf("ready", "", "complete")) assertFailsWith<TransferFailure> {
            codec.encode(original.copy(data = original.data.copy(setupStatus = invalid), formatVersion = 3))
        }
    }

    @Test fun strictJsonDuplicateKeysEscapedEquivalentsUnknownKeysAndScalars() {
        val text = codec.json(base()).toString(Charsets.UTF_8)
        refused(text.replace("\"platform\":\"android\"", "\"platform\":\"android\",\"plat\\u0066orm\":\"android\""))
        refused(text.replace("\"theme\":\"device\"", "\"theme\":\"device\",\"th\\u0065me\":\"warm\""))
        refused(text.dropLast(1) + ",\"cache\":null}")
        refused(text.replace("\"formatVersion\":1", "\"formatVersion\":2"))
        refused(text.replace("\"formatVersion\":1", "\"formatVersion\":\"1\""))
        refused(text + "{}")
        refused(text.replace("\"language\":\"device\"", "\"language\":\"\\ud800\""))
        refused(text.replace("\"language\":\"device\"", "\"language\":\"\\udc00\""))
        assertFailsWith<TransferFailure> { codec.decodeJson(byteArrayOf(0xc0.toByte(), 0xaf.toByte())) }
        assertEquals(base(), codec.decodeJson(byteArrayOf(-17, -69, -65) + codec.json(base())))
        refused("\uFEFF\uFEFF$text")
    }

    @Test fun datesAreExactUtcCalendarMilliseconds() {
        val text = codec.json(base()).toString(Charsets.UTF_8)
        listOf("2026-02-30T00:00:00.000Z", "2026-10-23T00:00:00Z", "2026-10-23T00:00:00.00Z",
            "2026-10-23T00:00:00.000+00:00", "2026-10-23T24:00:00.000Z", "2026-12-31T23:59:60.000Z").forEach {
            refused(text.replace(base().exportedAt, it))
        }
    }

    @Test fun duplicateTypedReferencesAndGlobalIdsAreRefusedButNamespacesCanOverlap() {
        val b = base()
        assertFailsWith<TransferFailure> { codec.encode(b.copy(data = b.data.copy(favorites = b.data.favorites + b.data.favorites))) }
        assertFailsWith<TransferFailure> { codec.encode(b.copy(data = b.data.copy(
            recentHistory = b.data.recentHistory.copy(entries = b.data.recentHistory.entries + b.data.recentHistory.entries)))) }
        assertFailsWith<TransferFailure> { codec.encode(b.copy(data = b.data.copy(learning = b.data.learning + b.data.learning))) }
        val id = "11111111-1111-4111-8111-111111111111"
        val one = RecitationList(id, "  synthetic  ", listOf(Occurrence(id, ListEntry("placeholder", text = "value"))))
        val value = b.copy(data = b.data.copy(lists = listOf(one)))
        assertEquals(value, codec.decode(codec.encode(value).inputStream()))
        assertFailsWith<TransferFailure> { codec.encode(value.copy(data = value.data.copy(lists = listOf(one, one)))) }
        assertFailsWith<TransferFailure> { codec.encode(value.copy(data = value.data.copy(lists =
            listOf(one, one.copy(id = "22222222-2222-4222-8222-222222222222"))))) }
    }

    @Test fun textCodepointAndCollectionBoundsAreNotTrimmingRules() {
        val b = base()
        val list = RecitationList("11111111-1111-4111-8111-111111111111", "😀".repeat(128), emptyList())
        val good = b.copy(data = b.data.copy(lists = listOf(list)))
        assertEquals(good, codec.decode(codec.encode(good).inputStream()))
        listOf("", "😀".repeat(129), "a\u0085b", "a\u2028b", "a\u0000b", "\ud800").forEach { name ->
            assertFailsWith<TransferFailure> { codec.encode(good.copy(data = good.data.copy(lists = listOf(list.copy(name = name))))) }
        }
        assertFailsWith<TransferFailure> { codec.encode(b.copy(data = b.data.copy(lists = List(1001) { list }))) }
        assertFailsWith<TransferFailure> { codec.encode(b.copy(data = b.data.copy(favorites =
            List(10001) { PersonalReference.legacy("id$it") }))) }
        assertFailsWith<TransferFailure> { codec.encode(b.copy(data = b.data.copy(recentHistory =
            RecentHistory(false, List(51) { PersonalReference.legacy("id$it") })))) }
        val text = codec.json(b).toString(Charsets.UTF_8)
        refused(text.replace("\"lyricFontSizeSp\":28", "\"lyricFontSizeSp\":15"))
        refused(text.replace("\"recentHistory\":", "\"history\":"))
    }

    @Test fun cancellationPropagatesAcrossTransportParsingValidationAndEncoding() {
        val b = base()
        val encoded = codec.encode(b)
        val cancelled = java.util.concurrent.CancellationException("synthetic")
        assertSame(cancelled, assertFails { codec.decode(encoded.inputStream()) { throw cancelled } })
        assertSame(cancelled, assertFails { codec.encode(b) { throw cancelled } })
        assertSame(cancelled, assertFails { codec.decodeJson(codec.json(b)) { throw cancelled } })
    }

    @Test fun strictAbsentFallbackRequiredFieldsAndArrayBoundsAreCheckedBeforeRetainingExtraMembers() {
        val good = base()
        val id = "11111111-1111-4111-8111-111111111111"
        val value = good.copy(data = good.data.copy(lists = listOf(RecitationList(id, "Synthetic",
            listOf(Occurrence(id, PersonalReference.legacy("synthetic").entry()))))))
        val text = codec.json(value).toString(Charsets.UTF_8)
        refused(text.replace("\"videoId\":\"synthetic\"", "\"videoId\":\"synthetic\",\"fallback\":null"))
        refused(text.replace("\"videoId\":\"synthetic\"", "\"videoId\":\"synthetic\",\"fallback\":\"\""))
        refused(text.replace(",\"readingDensity\":\"normal\"", ""))
        refused(text.replace("\"readingDensity\":\"normal\"", "\"readingDensity\":\"normal\",\"future\":true"))
        val root = accepted[2].jsonObject
        val data = root["data"]!!.jsonObject
        val entries = JsonArray(List(10001) { buildJsonObject { put("kind", "legacyVideo"); put("videoId", "id$it") } })
        val oversized = JsonObject(root + ("data" to JsonObject(data + ("favorites" to entries))))
        assertEquals("size", assertFailsWith<TransferFailure> { decode(oversized) }.reason)
        val learning = List(10001) { LearningAssignment(PersonalReference.legacy("id$it"), "wantToLearn") }
        assertEquals("size", assertFailsWith<TransferFailure> { codec.verify(good.copy(data = good.data.copy(learning = learning))) }.reason)
    }

    @Test fun aggregateOccurrencesAndCompleteLogicalBytesAreLimitedWithoutConstructingOversizedOutput() {
        fun id(index: Int) = "00000000-0000-4000-8000-" + index.toString().padStart(12, '0')
        val text = "😀".repeat(512)
        val lists = List(5) { listIndex -> RecitationList(id(50000 + listIndex), "Synthetic",
            List(10000) { index -> Occurrence(id(listIndex * 10000 + index), ListEntry("placeholder", text = text)) }) }
        val value = base().let { it.copy(data = it.data.copy(lists = lists)) }
        assertEquals("size", assertFailsWith<TransferFailure> { codec.verify(value) }.reason)
        val extra = RecitationList(id(60000), "Extra", listOf(Occurrence(id(60001), ListEntry("placeholder", text = "Synthetic"))))
        assertEquals("size", assertFailsWith<TransferFailure> { codec.verify(value.copy(data = value.data.copy(lists = lists + extra))) }.reason)
        val tooMany = lists[0].copy(entries = lists[0].entries + Occurrence(id(60001), ListEntry("placeholder", text = "Synthetic")))
        assertEquals("size", assertFailsWith<TransferFailure> { codec.verify(value.copy(data = value.data.copy(lists = listOf(tooMany)))) }.reason)
    }

    @Test fun oneGzipMemberOptionalHeadersCrcIsizeTruncationAndTrailingBytes() {
        val encoded = codec.encode(base())
        listOf(encoded + byteArrayOf(0), encoded + encoded, encoded.copyOf(encoded.size - 1),
            encoded.copyOf().apply { this[size - 8] = (this[size - 8].toInt() xor 1).toByte() },
            encoded.copyOf().apply { this[size - 4] = (this[size - 4].toInt() xor 1).toByte() },
            encoded.copyOf().apply { this[3] = 32 }).forEach {
            assertFailsWith<TransferFailure> { codec.decode(it.inputStream()) }
        }
        val header = encoded.copyOfRange(0, 10).apply { this[3] = 30 } +
            byteArrayOf(3, 0, 1, 2, 3, 'f'.code.toByte(), 0, 'c'.code.toByte(), 0)
        val crc = CRC32().apply { update(header) }.value.toInt()
        val optional = header + byteArrayOf(crc.toByte(), (crc ushr 8).toByte()) + encoded.copyOfRange(10, encoded.size)
        assertEquals(base(), codec.decode(optional.inputStream()))
        assertFailsWith<TransferFailure> { codec.decode(optional.copyOf().apply { this[header.size] = 0 }.inputStream()) }
    }

    @Test fun decoded64MiBInclusiveWithoutRatioLimit() {
        val transport = TransferTransport(BackupPolicy.maxBackupEncodedBytes, BackupPolicy.maxBackupDecodedBytes)
        val bytes = ByteArray(BackupPolicy.maxBackupDecodedBytes) { 32 }
        val encoded = transport.encode(bytes, 6)
        assertTrue(encoded.size < bytes.size / 100)
        assertContentEquals(bytes, transport.decode(encoded))
        val over = TransferTransport(BackupPolicy.maxBackupEncodedBytes, BackupPolicy.maxBackupDecodedBytes + 1)
            .encode(ByteArray(BackupPolicy.maxBackupDecodedBytes + 1), 6)
        assertEquals("size", assertFailsWith<TransferFailure> { transport.decode(over) }.reason)
    }

    @Test fun encoded65MiBInclusiveAndBoundedReadRefusesBeforeRetainingExtraByte() {
        val limit = BackupPolicy.maxBackupEncodedBytes
        val bytes = ByteArray(limit)
        byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1).copyInto(bytes)
        var at = 10
        val payloadSize = (limit - 18) % 5
        while (at < limit - 8 - 5 - payloadSize) {
            bytes[at++] = 0; bytes[at++] = 0; bytes[at++] = 0; bytes[at++] = -1; bytes[at++] = -1
        }
        bytes[at++] = 1; bytes[at++] = payloadSize.toByte(); bytes[at++] = 0
        bytes[at++] = payloadSize.inv().toByte(); bytes[at++] = -1
        val payload = ByteArray(payloadSize)
        payload.copyInto(bytes, at); at += payloadSize
        val crc = CRC32().apply { update(payload) }.value
        repeat(4) { bytes[at++] = (crc ushr (it * 8)).toByte() }
        repeat(4) { bytes[at++] = (payloadSize ushr (it * 8)).toByte() }
        assertEquals(limit, at)
        val transport = TransferTransport(limit, BackupPolicy.maxBackupDecodedBytes)
        assertContentEquals(payload, transport.decode(ByteArrayInputStream(bytes)))
        val extra = object : java.io.InputStream() {
            var read = 0
            override fun read(): Int = if (read++ <= limit) 0 else -1
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (read > limit) return -1
                val count = minOf(length, limit + 1 - read)
                buffer.fill(0, offset, offset + count); read += count
                return count
            }
        }
        assertEquals("size", assertFailsWith<TransferFailure> { transport.read(extra) }.reason)
        assertEquals(limit + 1, extra.read)
    }
}
