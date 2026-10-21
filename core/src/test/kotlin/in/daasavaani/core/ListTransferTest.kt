package `in`.daasavaani.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.Base64
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.*
import kotlin.test.*

class ListTransferTest {
    private val settings = File("../shared/settings/personal-transfer.yml").readLines().associate {
        val pair = it.split(": ")
        pair[0] to pair[1].toInt()
    }
    private val bounds = TransferBounds(settings.getValue("maxListEncodedBytes"), settings.getValue("maxListDecodedBytes"),
        settings.getValue("maxSharedUrlAscii"), settings.getValue("maxListNameCodePoints"),
        settings.getValue("maxEntryTextCodePoints"), settings.getValue("maxEntriesPerList"))
    private val prefix = "https://daasavaani.github.io/open/#/list/"
    private val codec = ListTransfer(bounds, "https://daasavaani.github.io/", "open/", 6)
    private val sample = ListSnapshot("  ಕ 😀  ", listOf(ListEntry("item", "fixture-1", " ಬಳಕೆ "),
        ListEntry("placeholder", text = "<b>ಕ</b>"), ListEntry("item", "fixture-1")))
    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        GZIPOutputStream(this).use { it.write(bytes) }
    }.toByteArray()
    private fun file(bytes: ByteArray) = codec.decodeFile(ByteArrayInputStream(bytes))
    private fun reject(block: () -> Unit) { assertFailsWith<TransferFailure> { block() } }

    @Test fun literalRepeatedRoundtrips() {
        assertEquals(sample, file(codec.encodeFile(sample)))
        assertEquals(sample, codec.decodeLink(codec.encodeLink(sample)))
        assertTrue(codec.json(sample).toString(Charsets.UTF_8).contains("ಕ"))
        assertContentEquals(codec.encodeFile(sample), codec.encodeFile(sample))
    }
    @Test fun independentNodeZlibVectorIsDeterministicAndLiteral() {
        val fixture = Json.parseToJsonElement(requireNotNull(javaClass.getResource("/list-transfer-v1.json")).readText()).jsonObject
        val json = fixture.getValue("json").jsonPrimitive.content
        val raw = fixture.getValue("rawDeflateBase64url").jsonPrimitive.content
        val snapshot = codec.decodeJson(json.toByteArray())
        assertEquals(json, codec.json(snapshot).toString(Charsets.UTF_8))
        // DEFLATE block selection may differ between zlib implementations; JSON and values must not.
        assertEquals(codec.encodeLink(snapshot), codec.encodeLink(snapshot))
        assertEquals(snapshot, codec.decodeLink(codec.encodeLink(snapshot)))
        assertEquals(snapshot, codec.decodeLink(prefix + raw))
        assertEquals(snapshot, file(Base64.getDecoder().decode(fixture.getValue("gzipBase64").jsonPrimitive.content)))
        listOf("inflateRawSync" to Base64.getUrlDecoder().decode(codec.encodeLink(snapshot).removePrefix(prefix)),
            "gunzipSync" to codec.encodeFile(snapshot)).forEach { (method, bytes) ->
            val process = ProcessBuilder("node", "-e",
                "const z=require('node:zlib');const b=[];process.stdin.on('data',x=>b.push(x));" +
                    "process.stdin.on('end',()=>process.stdout.write(z.$method(Buffer.concat(b))));").start()
            process.outputStream.use { it.write(bytes) }
            assertEquals(json, process.inputStream.bufferedReader(Charsets.UTF_8).readText())
            assertEquals(0, process.waitFor())
        }
    }
    @Test fun entryLimitsAllowlistsAndMalformedStructures() {
        val entry = ListEntry("placeholder", text = "ಕ")
        reject { codec.json(ListSnapshot("ಕ", listOf(ListEntry("unsupported")))) }
        reject { codec.json(ListSnapshot("ಕ", listOf(ListEntry("placeholder", videoId = "fixture-1", text = "ಕ")))) }
        reject { codec.json(ListSnapshot("ಕ", listOf(ListEntry("item", videoId = "fixture-1", text = "ಕ")))) }
        codec.json(ListSnapshot("ಕ", List(bounds.entries) { entry }))
        reject { codec.json(ListSnapshot("ಕ", List(bounds.entries + 1) { entry })) }
        val json = codec.json(ListSnapshot("ಕ", listOf(entry))).toString(Charsets.UTF_8)
        listOf(json.replace("\"text\":\"ಕ\"", "\"text\":\"ಕ\",\"text\":\"ಕ\""),
            json.replace("\"text\":\"ಕ\"", "\"text\":\"ಕ\",\"videoId\":\"fixture-1\""),
            json.replace("\"text\":\"ಕ\"", "\"text\":null"),
            json.replace("\"text\":\"ಕ\"", "\"text\":[[[[]]]]"),
            json.replace("\"text\":\"ಕ\"", "\"text\":\"\\n\""),
            json.replace("\"text\":\"ಕ\"", "\"text\":\"\\udfff\""),
            json.replace("\"text\":\"ಕ\"", "\"text\":\"\\u0085\""),
            json.replace("\"text\":\"ಕ\"", "\"text\":\"\\u2029\""),
            json.replace("\"text\":\"ಕ\"", "\"text\":\"ಕ\","),
            json.replace("\"text\":\"ಕ\"", "\"text\":\"ಕ\"/*x*/"),
            json.replace("\"placeholder\"", "\"other\"")).forEach { reject { codec.decodeJson(it.toByteArray()) } }
    }
    @Test fun sharedAcceptedAndRejectedFixtures() {
        val fixtures = Json.parseToJsonElement(File("../shared/fixtures/personal/list-transfer.json").readText()).jsonObject
        fixtures.getValue("accepted").jsonArray.forEach {
            val bytes = it.jsonObject.getValue("value").toString().toByteArray()
            val value = codec.decodeJson(bytes)
            assertEquals(value, file(codec.encodeFile(value)))
        }
        fixtures.getValue("rejected").jsonArray.forEach {
            reject { codec.decodeJson(it.jsonObject.getValue("value").toString().toByteArray()) }
        }
    }
    @Test fun strictJsonAndUtf8() {
        val valid = codec.json(sample)
        assertEquals(sample, codec.decodeJson(byteArrayOf(-17, -69, -65) + valid))
        listOf(byteArrayOf(-17, -69, -65, -17, -69, -65) + valid, byteArrayOf(-61, 40)).forEach {
            reject { codec.decodeJson(it) }
        }
        val text = valid.toString(Charsets.UTF_8)
        listOf(text.replace("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1"),
            text.replace("\"formatVersion\":1", "\"formatVersion\":1.0"),
            text.replace("\"formatVersion\":1", "\"formatVersion\":2"),
            text.replace("\"formatVersion\":1", "\"unknown\":1"),
            text + "x", text.replace("\"name\":", "\"name\" "), text.replace("\"name\":", "\"name\":null,\"ignored\":"),
            text.replace("\"  ಕ 😀  \"", "\"\\ud800\"")).forEach { reject { codec.decodeJson(it.toByteArray()) } }
        listOf("\n", "\u007f", "\u0085", "\u2028", "\u2029", "\ud800").forEach {
            reject { codec.json(sample.copy(name = it)) }
        }
        assertEquals("😀".repeat(bounds.name), codec.decodeJson(codec.json(sample.copy(name = "😀".repeat(bounds.name)))).name)
        reject { codec.json(sample.copy(name = "😀".repeat(bounds.name + 1))) }
        codec.json(sample.copy(entries = listOf(ListEntry("placeholder", text = "😀".repeat(bounds.text)))))
        reject { codec.json(sample.copy(entries = listOf(ListEntry("placeholder", text = "😀".repeat(bounds.text + 1))))) }
    }
    @Test fun strictSingleGzipMember() {
        val valid = codec.encodeFile(sample)
        for (removed in listOf(1, 8, valid.size - 1)) reject { file(valid.copyOf(valid.size - removed)) }
        reject { file(valid + valid) }
        reject { file(valid + byteArrayOf(0)) }
        reject { file(valid.clone().apply { this[size - 8] = (this[size - 8].toInt() xor 1).toByte() }) }
        reject { file(valid.clone().apply { this[size - 4] = (this[size - 4].toInt() xor 1).toByte() }) }
        reject { file(valid.clone().apply { this[3] = 32 }) }
        val header = valid.copyOfRange(0, 10).apply { this[3] = 2 }
        val crc = java.util.zip.CRC32().apply { update(header) }.value
        val checked = header + byteArrayOf(crc.toByte(), (crc ushr 8).toByte()) + valid.copyOfRange(10, valid.size)
        assertEquals(sample, file(checked))
        reject { file(checked.clone().apply { this[10] = (this[10].toInt() xor 1).toByte() }) }
        assertEquals(sample, file(valid.copyOfRange(0, 10).apply { this[3] = 24 } +
            "synthetic\u0000comment\u0000".toByteArray() + valid.copyOfRange(10, valid.size)))
    }
    @Test fun actualDecodedBoundaryIncludesBomAndWhitespaceWithoutRatioLimit() {
        val json = codec.json(ListSnapshot("ಕ", emptyList()))
        val exact = json + ByteArray(bounds.decoded - json.size) { 32 }
        assertEquals(ListSnapshot("ಕ", emptyList()), file(gzip(exact)))
        assertEquals(ListSnapshot("ಕ", emptyList()), file(gzip(byteArrayOf(-17, -69, -65) + exact.copyOf(exact.size - 3))))
        reject { file(gzip(exact + byteArrayOf(32))) }
        reject { codec.decodeJson(exact + byteArrayOf(32)) }
    }
    @Test fun actualEncodedBoundaryAndOneExtraBeforeUnboundedRead() {
        // Valid DEFLATE empty stored blocks provide transport padding without changing JSON.
        val json = codec.json(ListSnapshot("ಕ", emptyList()))
        val base = codec.encodeFile(ListSnapshot("ಕ", emptyList()))
        val padding = bounds.encoded - base.size
        val extra = padding % 5
        val header = base.copyOfRange(0, 10).apply { this[3] = 4 }
        val optional = ByteArray(2 + extra).apply { this[0] = extra.toByte() }
        val blocks = (padding - optional.size) / 5
        val remaining = padding - optional.size - blocks * 5
        val adjustedOptional = ByteArray(optional.size + remaining).apply { this[0] = (size - 2).toByte() }
        val out = ByteArrayOutputStream()
        out.write(header); out.write(adjustedOptional)
        repeat(blocks) { out.write(byteArrayOf(0, 0, 0, -1, -1)) }
        out.write(base, 10, base.size - 10)
        val exact = out.toByteArray()
        assertEquals(bounds.encoded, exact.size)
        assertEquals(codec.decodeJson(json), file(exact))
        reject { file(exact + byteArrayOf(0)) }
        var reads = 0
        reject { codec.decodeFile(object : java.io.InputStream() {
            override fun read(): Int { reads++; return 0 }
        }) }
        assertEquals(bounds.encoded + 1, reads)
    }
    @Test fun completeUrlBoundaryAndCanonicalBase64() {
        val link = codec.encodeLink(sample)
        // Vary configured HTTPS authority length to exercise the real whole-URL bound.
        val payload = link.removePrefix(prefix)
        val host = "a".repeat(bounds.url - payload.length - "https:///open/#/list/".length)
        val exactCodec = ListTransfer(bounds, "https://$host/", "open/", 6)
        val exact = exactCodec.encodeLink(sample)
        assertEquals(2048, exact.length)
        assertEquals(sample, exactCodec.decodeLink(exact))
        reject { ListTransfer(bounds, "https://${host}a/", "open/", 6).encodeLink(sample) }
        reject { exactCodec.decodeLink(exact + "a") }
        reject { codec.decodeLink(link + "=") }
        reject { codec.decodeLink(link.replace("https:", "http:")) }
        assertEquals(sample, codec.decodeLink(link.replace("/open/", "/")))
        assertEquals(sample, codec.decodeLink(link.replace("/open/", "")))
        assertEquals(sample, codec.decodeLink(link.replace("https://daasavaani.github.io", "HTTPS://DAASAVAANI.GITHUB.IO:443")))
        listOf(" ", "\n", "?q=1", "#").forEach { reject { codec.decodeLink(link + it) } }
        reject { codec.decodeLink(link.replace("https://", "https://user@")) }
        reject { codec.decodeLink(link.replace("/open/", "/other/")) }
        reject { codec.decodeLink(link.replace("github.io/", "github.io:444/")) }
        fun storedLink(decodedSize: Int): String {
            val json = codec.json(ListSnapshot("ಕ", emptyList()))
            val bytes = json + ByteArray(decodedSize - json.size) { 32 }
            val length = bytes.size
            val raw = byteArrayOf(1, length.toByte(), (length ushr 8).toByte(),
                (length xor 65535).toByte(), ((length xor 65535) ushr 8).toByte()) + bytes
            return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        }
        val canonicalExact = storedLink(1500)
        assertEquals(bounds.url, canonicalExact.length)
        assertEquals(ListSnapshot("ಕ", emptyList()), codec.decodeLink(canonicalExact))
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        reject { codec.decodeLink(canonicalExact.dropLast(1) + alphabet[alphabet.indexOf(canonicalExact.last()) or 1]) }
        reject { codec.decodeLink(prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(
            Base64.getUrlDecoder().decode(link.removePrefix(prefix)) + byteArrayOf(0))) }
        val canonicalExtra = storedLink(1501)
        assertEquals(bounds.url + 1, canonicalExtra.length)
        reject { codec.decodeLink(canonicalExtra) }
    }
    @Test fun cancellationIsObservable() {
        assertFailsWith<CancellationException> { codec.decodeFile(ByteArrayInputStream(codec.encodeFile(sample))) { throw CancellationException() } }
        assertFailsWith<CancellationException> { codec.encodeFile(sample) { throw CancellationException() } }
        assertFailsWith<CancellationException> { codec.decodeJson(codec.json(sample)) { throw CancellationException() } }
    }
}
