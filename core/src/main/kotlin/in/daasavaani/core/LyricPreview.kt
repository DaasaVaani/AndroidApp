package `in`.daasavaani.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

@Serializable
data class LyricDescriptor(val format: String, val formatVersion: Int, val manifest: ArtifactReference)
@Serializable
data class LyricSummary(val lyricId: String, val title: String, val tags: List<String>, val item: ArtifactReference)
@Serializable
data class LyricCatalog(
    val format: String, val formatVersion: Int, val stage: String,
    val requiredCapabilities: List<String>, val catalog: List<LyricSummary>,
)
@Serializable
data class PreviewLine(val kind: String, val text: String, val lineId: String? = null)
@Serializable
data class PreviewLyric(
    val format: String, val formatVersion: Int, val lyricId: String,
    val canonicalScript: String, val title: String, val tags: List<String>, val lines: List<PreviewLine>,
)
data class PreviewBounds(
    val descriptorBytes: Int, val manifestEncodedBytes: Int, val manifestDecodedBytes: Int,
    val itemEncodedBytes: Int, val itemDecodedBytes: Int, val catalogItems: Int, val lines: Int,
    val textCodePoints: Int, val tags: Int, val queryCodePoints: Int, val jsonDepth: Int,
    val preparedEncodedBytes: Int = manifestEncodedBytes, val preparedDecodedBytes: Int = manifestDecodedBytes,
)

class LyricPreviewCodec(val bounds: PreviewBounds) {
    private val json = Json { explicitNulls = true }
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    private inline fun <reified T> decode(bytes: ByteArray): T {
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        preflight(text)
        val tree = json.parseToJsonElement(text)
        validateTree(tree)
        return json.decodeFromJsonElement(tree)
    }
    private fun preflight(text: String) {
        val frames = mutableListOf<Pair<Char, MutableSet<String>>>()
        var i = 0
        while (i < text.length) {
            when (val char = text[i]) {
                '{', '[' -> {
                    require(frames.size < bounds.jsonDepth) { "JSON nesting exceeds limit" }
                    frames.add(char to mutableSetOf())
                }
                '}', ']' -> {
                    require(frames.isNotEmpty() && frames.removeAt(frames.lastIndex).first ==
                        if (char == '}') '{' else '[') { "Invalid JSON container" }
                }
                '"' -> {
                    val start = i++
                    while (i < text.length && text[i] != '"') {
                        if (text[i] == '\\') i++
                        i++
                    }
                    require(i < text.length) { "Unterminated JSON string" }
                    var next = i + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') {
                        require(frames.lastOrNull()?.first == '{') { "Invalid JSON key" }
                        val key = json.decodeFromString<String>(text.substring(start, i + 1))
                        require(frames.last().second.add(key)) { "Duplicate JSON key" }
                    }
                }
            }
            i++
        }
        require(frames.isEmpty()) { "Unterminated JSON container" }
    }
    private fun validateTree(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) -> scalar(key); validateTree(value) }
            is JsonArray -> element.forEach(::validateTree)
            is JsonPrimitive -> if (element.isString) scalar(element.content)
        }
    }
    private fun scalar(value: String) {
        var i = 0
        while (i < value.length) {
            val c = value[i++]
            if (c.isHighSurrogate()) require(i < value.length && value[i++].isLowSurrogate()) { "Invalid Unicode scalar" }
            else require(!c.isLowSurrogate()) { "Invalid Unicode scalar" }
        }
    }
    private fun text(value: String, empty: Boolean = false) {
        require((empty || value.isNotEmpty()) && '\r' !in value && '\n' !in value &&
            value.codePointCount(0, value.length) <= bounds.textCodePoints) { "Invalid or oversized lyric text" }
    }
    private fun tags(values: List<String>) {
        require(values.size <= bounds.tags && values.distinct().size == values.size) { "Invalid tags" }
        values.forEach { text(it) }
    }
    fun reference(value: ArtifactReference, encoded: Int, decoded: Int) {
        require(Regex("[0-9a-f]{64}").matches(value.sha256) &&
            value.path == "objects/${value.sha256}.json.gz" &&
            value.encodedBytes in 1..encoded && value.decodedBytes in 1..decoded) { "Invalid artifact reference" }
    }
    fun descriptor(bytes: ByteArray): LyricDescriptor {
        require(bytes.size in 1..bounds.descriptorBytes) { "Descriptor exceeds limit" }
        return decode<LyricDescriptor>(bytes).also {
            require(it.format == "daasavaani-lyrics" && it.formatVersion == 1) { "Unsupported lyric descriptor" }
            reference(it.manifest, bounds.manifestEncodedBytes, bounds.manifestDecodedBytes)
        }
    }
    fun catalog(bytes: ByteArray): LyricCatalog {
        require(bytes.size in 1..bounds.manifestDecodedBytes)
        return decode<LyricCatalog>(bytes).also {
            require(it.format == "daasavaani-lyric-catalog" && it.formatVersion == 1 && it.stage == "prelaunch" &&
                it.requiredCapabilities == listOf("lyric-preview-v1")) { "Unsupported lyric catalog" }
            require(it.catalog.size in 1..bounds.catalogItems &&
                it.catalog.map { entry -> entry.lyricId }.distinct().size == it.catalog.size) { "Invalid catalog coverage" }
            it.catalog.forEach { entry ->
                require(uuid.matches(entry.lyricId)) { "Invalid lyric UUID" }
                text(entry.title); tags(entry.tags)
                reference(entry.item, bounds.itemEncodedBytes, bounds.itemDecodedBytes)
            }
        }
    }
    fun item(bytes: ByteArray, expected: LyricSummary): PreviewLyric {
        require(bytes.size in 1..bounds.itemDecodedBytes)
        return decode<PreviewLyric>(bytes).also {
            require(it.format == "daasavaani-lyric" && it.formatVersion == 1 && it.canonicalScript == "kn" &&
                uuid.matches(it.lyricId) && it.lyricId == expected.lyricId &&
                it.title == expected.title && it.tags == expected.tags) { "Lyric summary mismatch" }
            text(it.title); tags(it.tags)
            require(it.lines.size in 1..bounds.lines) { "Invalid line count" }
            val ids = mutableSetOf<String>()
            it.lines.forEach { line ->
                when (line.kind) {
                    "line" -> {
                        require(line.lineId != null && uuid.matches(line.lineId) && ids.add(line.lineId)) { "Invalid line UUID" }
                        text(line.text)
                    }
                    "layout" -> {
                        require(line.lineId == null && line.text.all { char -> char == ' ' || char == '\t' }) { "Invalid layout" }
                        text(line.text, empty = true)
                    }
                    else -> error("Unsupported lyric line kind")
                }
            }
            // A layout record cannot carry even an explicit null identity.
            json.parseToJsonElement(bytes.decodeToString()).jsonObject.getValue("lines").jsonArray.forEach { line ->
                if (line.jsonObject.getValue("kind").jsonPrimitive.content == "layout")
                    require("lineId" !in line.jsonObject) { "Layout cannot have line identity" }
            }
        }
    }
    fun filter(catalog: LyricCatalog, query: String): List<LyricSummary> {
        scalar(query)
        require(query.codePointCount(0, query.length) <= bounds.queryCodePoints) { "Query exceeds limit" }
        return catalog.catalog.filter { it.title.contains(query, ignoreCase = true) }
    }
}

fun InputStream.readPreviewBounded(limit: Int, checkCancelled: () -> Unit = {}): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        checkCancelled()
        val count = read(buffer)
        if (count == -1) break
        require(count <= limit - output.size()) { "Artifact exceeds byte limit" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

fun decodePreviewArtifact(encoded: ByteArray, reference: ArtifactReference, checkCancelled: () -> Unit = {}): ByteArray {
    require(encoded.size == reference.encodedBytes) { "Encoded size mismatch" }
    checkCancelled()
    val hash = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
    require(hash == reference.sha256) { "Encoded SHA256 mismatch" }
    return GZIPInputStream(ByteArrayInputStream(encoded)).use { it.readPreviewBounded(reference.decodedBytes, checkCancelled) }
        .also { require(it.size == reference.decodedBytes) { "Decoded size mismatch" } }
}
