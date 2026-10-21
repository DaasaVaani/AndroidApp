package `in`.daasavaani.core

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.net.URI
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

data class TransferBounds(val encoded: Int, val decoded: Int, val url: Int, val name: Int, val text: Int, val entries: Int)
data class ListEntry(val kind: String, val videoId: String? = null, val fallback: String? = null, val text: String? = null)
data class ListSnapshot(val name: String, val entries: List<ListEntry>) {
    val nameKey: PersonalNameKey get() = PersonalNames.key(name)
}
class TransferFailure(val reason: String) : IllegalArgumentException(reason)

/** Transport decoding never trusts a file's reported size, extension or MIME type. */
class ListTransfer(private val bounds: TransferBounds, canonicalBaseUrl: String, listLinkPath: String, private val compressionLevel: Int) {
    private val base = URI(canonicalBaseUrl)
    private val openPath = base.path + listLinkPath
    private val linkPrefix = canonicalBaseUrl + listLinkPath + "#/list/"
    init {
        require(base.scheme == "https" && base.host != null && base.userInfo == null &&
            base.query == null && base.fragment == null && base.path == "/")
        require(listLinkPath.matches(Regex("[A-Za-z0-9_-]+/")) && compressionLevel in 0..9)
        require(listOf(bounds.encoded, bounds.decoded, bounds.url, bounds.name, bounds.text, bounds.entries).all { it > 0 })
    }
    private fun invalid(): Nothing = throw TransferFailure("invalid")
    private fun size(): Nothing = throw TransferFailure("size")
    private fun bounded(input: InputStream, check: () -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            check()
            val count = input.read(buffer, 0, minOf(buffer.size, bounds.encoded - out.size() + 1))
            if (count < 0) return out.toByteArray()
            if (count == 0) continue
            if (count > bounds.encoded - out.size()) size()
            out.write(buffer, 0, count)
        }
    }
    private fun inflate(bytes: ByteArray, offset: Int, gzip: Boolean, check: () -> Unit): ByteArray {
        val inflater = Inflater(true)
        val out = ByteArrayOutputStream()
        try {
            inflater.setInput(bytes, offset, bytes.size - offset)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                check()
                val count = try { inflater.inflate(buffer, 0, minOf(buffer.size, bounds.decoded - out.size() + 1)) }
                    catch (_: java.util.zip.DataFormatException) { invalid() }
                if (count > bounds.decoded - out.size()) size()
                out.write(buffer, 0, count)
                if (count == 0 && !inflater.finished()) invalid()
            }
            val end = bytes.size - inflater.remaining
            if (gzip) {
                if (bytes.size - end != 8) invalid()
                val decoded = out.toByteArray()
                if (uint(bytes, end) != CRC32().apply { update(decoded) }.value ||
                    uint(bytes, end + 4) != decoded.size.toLong()) invalid()
                return decoded
            }
            if (end != bytes.size) invalid()
            return out.toByteArray()
        } finally { inflater.end() }
    }
    private fun uint(bytes: ByteArray, at: Int): Long =
        (0..3).fold(0L) { value, i -> value or ((bytes[at + i].toLong() and 255) shl (8 * i)) }

    fun decodeFile(input: InputStream, check: () -> Unit = {}): ListSnapshot {
        val bytes = bounded(input, check)
        if (bytes.size < 18 || bytes[0] != 0x1f.toByte() || bytes[1] != 0x8b.toByte() ||
            bytes[2] != 8.toByte()) invalid()
        val flags = bytes[3].toInt() and 255
        if (flags and 224 != 0) invalid()
        var at = 10
        fun byte(): Int {
            check()
            if (at >= bytes.size - 8) invalid()
            return bytes[at++].toInt() and 255
        }
        if (flags and 4 != 0) {
            val length = byte() or (byte() shl 8)
            repeat(length) { byte() }
        }
        if (flags and 8 != 0) while (byte() != 0) { /* bounded optional filename */ }
        if (flags and 16 != 0) while (byte() != 0) { /* bounded optional comment */ }
        if (flags and 2 != 0) {
            val crc = CRC32().apply { update(bytes, 0, at) }.value.toInt() and 65535
            if (byte() or (byte() shl 8) != crc) invalid()
        }
        return decodeJson(inflate(bytes, at, true, check), check)
    }

    fun decodeLink(link: String, check: () -> Unit = {}): ListSnapshot {
        if (link.length > bounds.url) throw TransferFailure("url")
        if (link.any { it.code !in 33..126 }) invalid()
        val uri = try { URI(link) } catch (_: java.net.URISyntaxException) { invalid() }
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        if (!uri.scheme.equals("https", ignoreCase = true) || !uri.host.equals(base.host, ignoreCase = true) ||
            (if (uri.port == -1) 443 else uri.port) != (if (base.port == -1) 443 else base.port) || uri.rawUserInfo != null ||
            uri.rawQuery != null || path !in setOf(base.rawPath, openPath) ||
            uri.rawFragment?.startsWith("/list/") != true) invalid()
        val payload = uri.rawFragment.substring(6)
        if (payload.isEmpty() || payload.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '_' }) invalid()
        val bytes = try { Base64.getUrlDecoder().decode(payload) } catch (_: IllegalArgumentException) { invalid() }
        if (Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) != payload) invalid()
        return decodeJson(inflate(bytes, 0, false, check), check)
    }

    fun decodeJson(bytes: ByteArray, check: () -> Unit = {}): ListSnapshot {
        if (bytes.size > bounds.decoded) size()
        check()
        val decoded = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) { invalid() }
        return Parser(decoded.removePrefix("\uFEFF"), check).parse()
    }

    private inner class Parser(val source: String, val check: () -> Unit) {
        var at = 0
        fun whitespace() { while (at < source.length && source[at] in " \t\r\n") { check(); at++ } }
        fun take(char: Char): Boolean { whitespace(); return if (at < source.length && source[at] == char) { at++; true } else false }
        fun need(char: Char) { if (!take(char)) invalid() }
        fun string(): String {
            need('"')
            val out = StringBuilder()
            while (at < source.length) {
                check()
                val char = source[at++]
                if (char == '"') return out.toString()
                if (char.code < 32) invalid()
                if (char != '\\') out.append(char) else {
                    if (at >= source.length) invalid()
                    when (val escape = source[at++]) {
                        '"', '\\', '/' -> out.append(escape)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (at + 4 > source.length) invalid()
                            val hex = source.substring(at, at + 4)
                            if (hex.any { it !in "0123456789abcdefABCDEF" }) invalid()
                            out.append(hex.toInt(16).toChar()); at += 4
                        }
                        else -> invalid()
                    }
                }
                if (out.length > maxOf(bounds.name, bounds.text, 128, 16) * 2) invalid()
            }
            invalid()
        }
        fun fields(allowed: Set<String>, field: (String) -> Unit): Set<String> {
            need('{')
            val seen = mutableSetOf<String>()
            if (take('}')) return seen
            do {
                val key = string()
                if (key !in allowed || !seen.add(key)) invalid()
                need(':'); field(key)
                if (take('}')) return seen
                need(',')
            } while (true)
        }
        fun parse(): ListSnapshot {
            var name: String? = null
            var entries: List<ListEntry>? = null
            val keys = fields(setOf("format", "formatVersion", "name", "entries")) { key ->
                when (key) {
                    "format" -> if (string() != "daasavaani-list") invalid()
                    "formatVersion" -> {
                        whitespace()
                        if (at >= source.length || source[at++] != '1') invalid()
                    }
                    "name" -> name = string().also { text(it, bounds.name) }
                    "entries" -> {
                        need('[')
                        val list = mutableListOf<ListEntry>()
                        if (!take(']')) do {
                            check()
                            if (list.size >= bounds.entries) invalid()
                            var kind: String? = null; var id: String? = null
                            var fallback: String? = null; var value: String? = null
                            val seen = fields(setOf("kind", "videoId", "fallback", "text")) {
                                when (it) {
                                    "kind" -> kind = string()
                                    "videoId" -> id = string()
                                    "fallback" -> fallback = string().also { s -> text(s, bounds.text) }
                                    "text" -> value = string().also { s -> text(s, bounds.text) }
                                }
                            }
                            when (kind) {
                                "item" -> if (id == null || !id!!.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
                                    seen != (if (fallback == null) setOf("kind", "videoId") else setOf("kind", "videoId", "fallback"))) invalid()
                                "placeholder" -> if (seen != setOf("kind", "text")) invalid()
                                else -> invalid()
                            }
                            list.add(ListEntry(kind!!, id, fallback, value))
                            if (take(']')) break
                            need(',')
                        } while (true)
                        entries = list
                    }
                }
            }
            whitespace()
            if (at != source.length || keys.size != 4) invalid()
            return ListSnapshot(name ?: invalid(), entries ?: invalid())
        }
    }
    private fun text(value: String, limit: Int) {
        if (value.isEmpty() || value.length.toLong() > limit.toLong() * 2 ||
            value.codePointCount(0, value.length) > limit) invalid()
        var at = 0
        while (at < value.length) {
            val point = value.codePointAt(at)
            if (point in 0..31 || point in 127..159 || point in 0xd800..0xdfff || point == 0x2028 || point == 0x2029) invalid()
            at += Character.charCount(point)
        }
    }
    fun json(snapshot: ListSnapshot, check: () -> Unit = {}): ByteArray {
        text(snapshot.name, bounds.name)
        if (snapshot.entries.size > bounds.entries) invalid()
        snapshot.entries.forEach {
            check()
            when (it.kind) {
                "item" -> if (it.videoId == null || it.text != null) invalid()
                "placeholder" -> if (it.text == null || it.videoId != null || it.fallback != null) invalid()
                else -> invalid()
            }
            it.videoId?.let { id -> if (!id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) invalid() }
            it.fallback?.let { value -> text(value, bounds.text) }
            it.text?.let { value -> text(value, bounds.text) }
        }
        fun quote(value: String) = Json.encodeToString(value)
        val out = ByteArrayOutputStream()
        fun append(value: String) {
            check()
            val bytes = value.toByteArray(Charsets.UTF_8)
            if (bytes.size > bounds.decoded - out.size()) size()
            out.write(bytes)
        }
        append("{\"format\":\"daasavaani-list\",\"formatVersion\":1,\"name\":${quote(snapshot.name)},\"entries\":[")
        snapshot.entries.forEachIndexed { index, entry ->
            if (index > 0) append(",")
            append("{\"kind\":${quote(entry.kind)}")
            entry.videoId?.let { append(",\"videoId\":${quote(it)}") }
            entry.fallback?.let { append(",\"fallback\":${quote(it)}") }
            entry.text?.let { append(",\"text\":${quote(it)}") }
            append("}")
        }
        append("]}")
        return out.toByteArray().also { decodeJson(it, check) }
    }
    private fun compress(bytes: ByteArray, check: () -> Unit): ByteArray {
        val deflater = Deflater(compressionLevel, true)
        val out = ByteArrayOutputStream()
        try {
            deflater.setInput(bytes); deflater.finish()
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                check()
                val count = deflater.deflate(buffer)
                if (count > bounds.encoded - out.size()) size()
                out.write(buffer, 0, count)
            }
            return out.toByteArray()
        } finally { deflater.end() }
    }
    fun encodeFile(snapshot: ListSnapshot, check: () -> Unit = {}): ByteArray {
        val bytes = json(snapshot, check)
        val compressed = compress(bytes, check)
        if (compressed.size > bounds.encoded - 18) size()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(31, -117, 8, 0, 0, 0, 0, 0, 0, -1))
        out.write(compressed)
        listOf(CRC32().apply { update(bytes) }.value, bytes.size.toLong()).forEach { value ->
            repeat(4) { out.write((value ushr (it * 8)).toInt() and 255) }
        }
        return out.toByteArray()
    }
    fun encodeLink(snapshot: ListSnapshot, check: () -> Unit = {}): String {
        val bytes = compress(json(snapshot, check), check)
        val link = linkPrefix + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        if (link.length > bounds.url) throw TransferFailure("url")
        return link
    }
}
