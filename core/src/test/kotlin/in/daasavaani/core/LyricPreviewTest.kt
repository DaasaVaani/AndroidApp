package `in`.daasavaani.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.GZIPOutputStream
import kotlin.test.*

class LyricPreviewTest {
    private val bounds = PreviewBounds(65536, 100000, 200000, 100000, 200000, 10, 10, 100, 5, 10, 16)
    private val codec = LyricPreviewCodec(bounds)
    private val id = "10000000-0000-4000-8000-000000000001"
    private val lineId = "20000000-0000-4000-8000-000000000001"
    private val hash = "a".repeat(64)
    private val reference = ArtifactReference("objects/$hash.json.gz", hash, 20, 100)
    private val summary = LyricSummary(id, "  Synthetic 😀  ", listOf("test"), reference)
    private val summaryJson = """{"lyricId":"$id","title":"  Synthetic 😀  ","tags":["test"],"item":${ref()}}"""
    private fun ref() = """{"path":"objects/$hash.json.gz","sha256":"$hash","encodedBytes":20,"decodedBytes":100}"""
    private fun descriptor() = """{"format":"daasavaani-lyrics","formatVersion":1,"manifest":${ref()}}"""
    private fun catalog(entry: String = summaryJson) =
        """{"format":"daasavaani-lyric-catalog","formatVersion":1,"stage":"prelaunch","requiredCapabilities":["lyric-preview-v1"],"catalog":[$entry]}"""
    private fun item(lines: String = """{"kind":"line","lineId":"$lineId","text":"  <b>synthetic</b> 😀  "},{"kind":"layout","text":" \t"},{"kind":"layout","text":""}""") =
        """{"format":"daasavaani-lyric","formatVersion":1,"lyricId":"$id","canonicalScript":"kn","title":"  Synthetic 😀  ","tags":["test"],"lines":[$lines]}"""
    private fun rejected(text: String, parse: (ByteArray) -> Any = { codec.item(it, summary) }) {
        assertFails { parse(text.encodeToByteArray()) }
    }
    @Test fun validDistinctUuidContractsPreserveEverySpaceAndLayout() {
        assertEquals(1, codec.descriptor(descriptor().encodeToByteArray()).formatVersion)
        val catalog = codec.catalog(catalog().encodeToByteArray())
        assertEquals(summary, catalog.catalog.single())
        val lyric = codec.item(item().encodeToByteArray(), summary)
        assertEquals("  <b>synthetic</b> 😀  ", lyric.lines.first().text)
        assertEquals(listOf(" \t", ""), lyric.lines.drop(1).map { it.text })
        assertEquals(listOf("line", "layout", "layout"), lyric.lines.map { it.kind })
        assertEquals(summary.title, lyric.title)
    }
    @Test fun strictFormatsVersionsCapabilitiesAndUnknownProperties() {
        rejected(descriptor().replace("daasavaani-lyrics", "fixture"), codec::descriptor)
        rejected(descriptor().replace("\"formatVersion\":1", "\"formatVersion\":2"), codec::descriptor)
        rejected(descriptor().replace("\"manifest\"", "\"extra\":true,\"manifest\""), codec::descriptor)
        rejected(catalog().replace("lyric-preview-v1", "fixture-catalog-v1"), codec::catalog)
        rejected(catalog().replace("[\"lyric-preview-v1\"]", "[\"lyric-preview-v1\",\"future\"]"), codec::catalog)
        rejected(catalog().replace("prelaunch", "public"), codec::catalog)
        rejected(item().replace("\"canonicalScript\":\"kn\"", "\"canonicalScript\":\"en\""))
        rejected(item().replace("\"formatVersion\":1", "\"formatVersion\":2"))
        rejected(item().replace("\"tags\"", "\"videoId\":\"$id\",\"tags\""))
    }
    @Test fun rejectsDuplicateMembersEvenEscapedKeysAndExcessiveNesting() {
        rejected(descriptor().replace("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1"), codec::descriptor)
        rejected(descriptor().replace("\"formatVersion\":1", "\"formatVersion\":1,\"format\\u0056ersion\":1"), codec::descriptor)
        rejected("[".repeat(30) + "]".repeat(30), codec::descriptor)
    }
    @Test fun idsAreUuidV4NotVideoIdsAndLineIdentitiesUnique() {
        rejected(catalog(summaryJson.replace(id, "legacy_video")), codec::catalog)
        rejected(catalog("$summaryJson,$summaryJson"), codec::catalog)
        rejected(item().replace(lineId, "video"))
        val line = """{"kind":"line","lineId":"$lineId","text":"synthetic"}"""
        rejected(item("$line,$line"))
        rejected(item("""{"kind":"line","text":"synthetic"}"""))
    }
    @Test fun itemMustMatchEverySummaryField() {
        rejected(item().replace("10000000", "30000000"))
        rejected(item().replace("Synthetic", "Different"))
        rejected(item().replace("\"test\"", "\"different\""))
        rejected(item().replace("\"test\"", "\"test\",\"test\""))
    }
    @Test fun layoutCannotCarryLineIdentityNorInferVerses() {
        rejected(item("""{"kind":"layout","lineId":"$lineId","text":""}"""))
        rejected(item("""{"kind":"layout","lineId":null,"text":""}"""))
        rejected(item("""{"kind":"verse","text":"synthetic"}"""))
        rejected(item("""{"kind":"layout","text":"not layout"}"""))
        rejected(item("""{"kind":"line","lineId":"$lineId","text":""}"""))
        rejected(item("""{"kind":"line","lineId":"$lineId","text":"first\nsecond"}"""))
        rejected(item("""{"kind":"line","lineId":"$lineId","text":"first\rsecond"}"""))
    }
    @Test fun utf8AndEscapedScalarValidity() {
        assertFails { codec.descriptor(byteArrayOf(0xc0.toByte(), 0xaf.toByte())) }
        rejected(item().replace("synthetic", "\\ud800"))
        rejected(item().replace("synthetic", "\\udc00"))
        assertTrue(codec.item(item().replace("synthetic", "\\ud83d\\ude00").encodeToByteArray(), summary).lines.first().text.contains("😀"))
    }
    @Test fun declaredReferenceBoundsPathAndHashShape() {
        listOf("../objects/$hash.json.gz", "https://evil.invalid/a", "objects/$hash.json.gz?x", "objects/${"b".repeat(64)}.json.gz").forEach {
            assertFails { codec.reference(reference.copy(path = it), 100, 1000) }
        }
        assertFails { codec.reference(reference.copy(sha256 = "A".repeat(64)), 100, 1000) }
        listOf(0, -1, 101).forEach { size -> assertFails { codec.reference(reference.copy(encodedBytes = size), 100, 1000) } }
        assertFails { codec.reference(reference.copy(decodedBytes = 1001), 100, 1000) }
        rejected(descriptor().replace("\"encodedBytes\":20", "\"encodedBytes\":9007199254740991"), codec::descriptor)
    }
    @Test fun textCatalogLineTagAndQueryBounds() {
        rejected(catalog(List(11) { summaryJson.replace(id, "10000000-0000-4000-8000-${it.toString().padStart(12, '0')}") }.joinToString(",")), codec::catalog)
        rejected(item(List(11) { """{"kind":"layout","text":""}""" }.joinToString(",")))
        rejected(item().replace("synthetic", "x".repeat(101)))
        val catalog = codec.catalog(catalog().encodeToByteArray())
        assertEquals(1, codec.filter(catalog, "SYNTHETIC").size)
        assertEquals(1, codec.filter(catalog, "  ").size)
        assertTrue(codec.filter(catalog, "absent").isEmpty())
        assertFails { codec.filter(catalog, "x".repeat(11)) }
        assertFails { codec.filter(catalog, "\ud800") }
        assertFails { LyricPreviewCodec(bounds.copy(descriptorBytes = 1)).descriptor(descriptor().encodeToByteArray()) }
        assertFails { LyricPreviewCodec(bounds.copy(manifestDecodedBytes = 1)).catalog(catalog().encodeToByteArray()) }
        assertFails { LyricPreviewCodec(bounds.copy(itemDecodedBytes = 1)).item(item().encodeToByteArray(), summary) }
        assertFails { LyricPreviewCodec(bounds.copy(tags = 0)).item(item().encodeToByteArray(), summary) }
    }
    private fun compressed(bytes: ByteArray): Pair<ByteArray, ArtifactReference> {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes) }
        val encoded = output.toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
        return encoded to ArtifactReference("objects/$sha.json.gz", sha, encoded.size, bytes.size)
    }
    @Test fun encodedHashAndBothExactSizesAreMandatory() {
        val bytes = item().encodeToByteArray()
        val (encoded, ref) = compressed(bytes)
        assertContentEquals(bytes, decodePreviewArtifact(encoded, ref))
        assertFails { decodePreviewArtifact(encoded, ref.copy(sha256 = hash)) }
        assertFails { decodePreviewArtifact(encoded, ref.copy(encodedBytes = encoded.size + 1)) }
        assertFails { decodePreviewArtifact(encoded, ref.copy(decodedBytes = bytes.size + 1)) }
        assertFails { decodePreviewArtifact(encoded, ref.copy(decodedBytes = bytes.size - 1)) }
        assertFails { decodePreviewArtifact(encoded.copyOf(encoded.size - 1), ref) }
    }
    @Test fun encodedDecodedOverflowCancellationAndRetry() {
        assertFails { ByteArrayInputStream(ByteArray(100)).readPreviewBounded(99) }
        val (encoded, ref) = compressed(ByteArray(10000))
        assertFails { decodePreviewArtifact(encoded, ref.copy(decodedBytes = 100)) }
        assertFailsWith<CancellationException> { decodePreviewArtifact(encoded, ref) { throw CancellationException() } }
        var checks = 0
        assertFailsWith<CancellationException> {
            ByteArrayInputStream(ByteArray(100000)).readPreviewBounded(100000) { if (++checks == 2) throw CancellationException() }
        }
        assertEquals(10000, decodePreviewArtifact(encoded, ref).size)
    }
}
