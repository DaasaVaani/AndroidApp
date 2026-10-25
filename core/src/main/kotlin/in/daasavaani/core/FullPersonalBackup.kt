package `in`.daasavaani.core

import java.io.InputStream
import java.io.Reader
import java.io.OutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString

data class BackupPreferences(val language: String, val theme: String, val lyricFontSizeSp: Int, val readingDensity: String)
data class BackupPersonalData(
    val preferences: BackupPreferences,
    val favorites: List<PersonalReference>,
    val lists: List<RecitationList>,
    val recentHistory: RecentHistory,
    val learning: List<LearningAssignment>,
    val recitation: RecitationPositionV1,
)
data class FullPersonalBackup(val exportedAt: String, val data: BackupPersonalData) {
    fun duplicateNameGroups(): List<List<String>> = data.lists.groupBy { PersonalNames.key(it.name) }
        .values.filter { it.size > 1 }.map { group -> group.map { it.id } }
}

/** Android-only logical v1 codec. It never opens storage, resolves references or mutates content. */
class FullPersonalBackupCodec(private val listCodec: ListTransfer, private val compressionLevel: Int) {
    private val transport = TransferTransport(BackupPolicy.maxBackupEncodedBytes, BackupPolicy.maxBackupDecodedBytes)
    private fun invalid(): Nothing = throw TransferFailure("invalid")
    private fun size(): Nothing = throw TransferFailure("size")
    fun decode(input: InputStream, check: () -> Unit = {}): FullPersonalBackup = try {
        transport.reader(input, check).use { decodeRoot(StrictBackupJson(it, check).parse(), check) }
    } catch (_: java.nio.charset.CharacterCodingException) { invalid() }

    fun decodeJson(bytes: ByteArray, check: () -> Unit = {}): FullPersonalBackup = try {
        transport.jsonReader(bytes).use { decodeRoot(StrictBackupJson(it, check).parse(), check) }
    } catch (_: java.nio.charset.CharacterCodingException) { invalid() }

    private fun decodeRoot(root: JsonElement, check: () -> Unit): FullPersonalBackup {
        val envelope = fields(root, setOf("format", "formatVersion", "platform", "exportedAt", "data"))
        if (string(envelope["format"]) != "daasavaani-personal-backup" ||
            integer(envelope["formatVersion"]) != 1 || string(envelope["platform"]) != "android") invalid()
        val date = string(envelope["exportedAt"])
        val data = fields(envelope["data"], setOf("preferences", "favorites", "lists", "recentHistory", "learning", "recitation"))
        val prefs = fields(data["preferences"], setOf("language", "theme", "lyricFontSizeSp", "readingDensity"))
        val preferences = BackupPreferences(string(prefs["language"]), string(prefs["theme"]),
            integer(prefs["lyricFontSizeSp"]), string(prefs["readingDensity"]))
        val favorites = array(data["favorites"], BackupPolicy.maxFavorites).map { reference(it) }
        val lists = array(data["lists"], BackupPolicy.maxLists).map { value ->
            check()
            val list = fields(value, setOf("id", "name", "entries"))
            RecitationList(string(list["id"]), string(list["name"]),
                array(list["entries"], BackupPolicy.maxEntriesPerList).map { occurrence ->
                    val entry = occurrence as? JsonObject ?: invalid()
                    val id = string(entry["id"])
                    val valueEntry = JsonObject(entry.filterKeys { it != "id" })
                    Occurrence(id, entry(valueEntry))
                })
        }
        val history = fields(data["recentHistory"], setOf("enabled", "entries"))
        val enabled = history["enabled"] as? JsonPrimitive ?: invalid()
        if (enabled.isString || enabled.booleanOrNull == null) invalid()
        val learning = fields(data["learning"], setOf("entries"))
        val assignments = array(learning["entries"], BackupPolicy.maxLearningLabels).map { value ->
            val item = value as? JsonObject ?: invalid()
            LearningAssignment(reference(JsonObject(item.filterKeys { it != "label" })), string(item["label"]))
        }
        val recitation = fields(data["recitation"], setOf("position"))
        val position = if (recitation["position"] == JsonNull) null else {
            val pair = fields(recitation["position"], setOf("listId", "entryId"))
            ExactRecitationPosition(string(pair["listId"]), string(pair["entryId"]))
        }
        return FullPersonalBackup(date, BackupPersonalData(preferences, favorites, lists,
            RecentHistory(enabled.boolean, array(history["entries"], BackupPolicy.maxRecents).map { reference(it) }),
            assignments, RecitationPositionV1(position))).also { verify(it, check) }
    }

    private fun fields(value: JsonElement?, keys: Set<String>): JsonObject =
        (value as? JsonObject ?: invalid()).also { if (it.keys != keys) invalid() }
    private fun string(value: JsonElement?): String =
        (value as? JsonPrimitive ?: invalid()).let { if (!it.isString) invalid() else it.content }
    private fun integer(value: JsonElement?): Int {
        val primitive = value as? JsonPrimitive ?: invalid()
        if (primitive.isString || primitive.content.length > 32) invalid()
        return try { BigDecimal(primitive.content).intValueExact() } catch (_: ArithmeticException) { invalid() }
            catch (_: NumberFormatException) { invalid() }
    }
    private fun array(value: JsonElement?, limit: Int): JsonArray =
        (value as? JsonArray ?: invalid()).also { if (it.size > limit) size() }
    private fun reference(value: JsonElement): PersonalReference {
        val entry = entry(value)
        if (entry.fallback != null) invalid()
        return entry.reference() ?: invalid()
    }
    private fun entry(value: JsonElement): ListEntry {
        val item = value as? JsonObject ?: invalid()
        val kind = string(item["kind"])
        val key = when (kind) { "lyric" -> "lyricId"; "legacyVideo" -> "videoId"; "placeholder" -> "text"; else -> invalid() }
        val allowed = setOf("kind", key) + if (item.containsKey("fallback") && kind != "placeholder") setOf("fallback") else emptySet()
        fields(item, allowed)
        return ListEntry(kind, videoId = if (key == "videoId") string(item[key]) else null,
            lyricId = if (key == "lyricId") string(item[key]) else null,
            text = if (key == "text") string(item[key]) else null,
            fallback = if (item.containsKey("fallback")) string(item["fallback"]) else null)
    }

    fun json(value: FullPersonalBackup, check: () -> Unit = {}): ByteArray {
        val output = BoundedByteOutput(BackupPolicy.maxBackupDecodedBytes)
        serialize(value, check, output)
        return output.toByteArray()
    }
    fun verify(value: FullPersonalBackup, check: () -> Unit = {}) { serialize(value, check, null) }
    private fun serialize(value: FullPersonalBackup, check: () -> Unit, output: OutputStream?) {
        validate(value, check)
        var written = 0
        fun append(text: String) {
            check()
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size > BackupPolicy.maxBackupDecodedBytes - written) size()
            written += bytes.size
            output?.write(bytes)
        }
        fun quote(text: String) = Json.encodeToString(text)
        fun entry(entry: ListEntry, id: String? = null) {
            append("{\"kind\":${quote(entry.kind)}")
            entry.videoId?.let { append(",\"videoId\":${quote(it)}") }
            entry.lyricId?.let { append(",\"lyricId\":${quote(it)}") }
            entry.fallback?.let { append(",\"fallback\":${quote(it)}") }
            entry.text?.let { append(",\"text\":${quote(it)}") }
            id?.let { append(",\"id\":${quote(it)}") }
            append("}")
        }
        append("{\"format\":\"daasavaani-personal-backup\",\"formatVersion\":1,\"platform\":\"android\",\"exportedAt\":${quote(value.exportedAt)},\"data\":")
        val start = written
        val data = value.data
        val p = data.preferences
        append("{\"preferences\":{\"language\":${quote(p.language)},\"theme\":${quote(p.theme)},\"lyricFontSizeSp\":${p.lyricFontSizeSp},\"readingDensity\":${quote(p.readingDensity)}},\"favorites\":[")
        data.favorites.forEachIndexed { index, ref -> if (index > 0) append(","); entry(ref.entry()) }
        append("],\"lists\":[")
        data.lists.forEachIndexed { index, list ->
            if (index > 0) append(",")
            append("{\"id\":${quote(list.id)},\"name\":${quote(list.name)},\"entries\":[")
            list.entries.forEachIndexed { i, occurrence -> if (i > 0) append(","); entry(occurrence.entry, occurrence.id) }
            append("]}")
        }
        append("],\"recentHistory\":{\"enabled\":${data.recentHistory.enabled},\"entries\":[")
        data.recentHistory.entries.forEachIndexed { index, ref -> if (index > 0) append(","); entry(ref.entry()) }
        append("]},\"learning\":{\"entries\":[")
        data.learning.forEachIndexed { index, assignment ->
            if (index > 0) append(",")
            val ref = assignment.reference
            append("{\"kind\":${quote(ref.kind)},\"${if (ref.kind == "lyric") "lyricId" else "videoId"}\":${quote(ref.id)},\"label\":${quote(assignment.label)}}")
        }
        append("]},\"recitation\":{\"position\":")
        val pair = data.recitation.position
        append(if (pair == null) "null" else "{\"listId\":${quote(pair.listId)},\"entryId\":${quote(pair.entryId)}}")
        append("}}")
        if (written - start > BackupPolicy.maxPersonalStoredBytes) size()
        append("}")
    }

    fun encode(value: FullPersonalBackup, check: () -> Unit = {}): ByteArray =
        transport.encodeStream(compressionLevel) { serialize(value, check, it) }

    fun validate(value: FullPersonalBackup, check: () -> Unit = {}) {
        check()
        if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z").matches(value.exportedAt)) invalid()
        try {
            val instant = Instant.parse(value.exportedAt)
            if (DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(instant) != value.exportedAt) invalid()
        } catch (_: java.time.DateTimeException) { invalid() }
        val data = value.data
        val p = data.preferences
        if (p.language !in setOf("device", "en", "kn") || p.theme !in setOf("device", "light", "dark", "warm") ||
            p.lyricFontSizeSp !in BackupPolicy.fontMin..BackupPolicy.fontMax ||
            (p.lyricFontSizeSp - BackupPolicy.fontMin) % BackupPolicy.fontStep != 0 ||
            p.readingDensity !in setOf("compact", "normal", "relaxed") || !data.recitation.valid()) invalid()
        fun references(values: List<PersonalReference>, limit: Int) {
            if (values.size > limit) size()
            val seen = mutableSetOf<PersonalReference>()
            values.forEach { check(); if (!it.valid() || !seen.add(it)) invalid() }
        }
        references(data.favorites, BackupPolicy.maxFavorites)
        references(data.recentHistory.entries, BackupPolicy.maxRecents)
        if (data.learning.size > BackupPolicy.maxLearningLabels) size()
        if (!LearningLabels.valid(data.learning)) invalid()
        if (data.lists.size > BackupPolicy.maxLists || data.lists.sumOf { it.entries.size.toLong() } > BackupPolicy.maxTotalEntries) size()
        val ids = mutableSetOf<String>()
        val occurrences = mutableSetOf<String>()
        data.lists.forEach { list ->
            check()
            if (!PersonalReference.validLyricId(list.id) || !ids.add(list.id)) invalid()
            if (list.entries.size > BackupPolicy.maxEntriesPerList) size()
            listCodec.validate(list.transfer(), check)
            list.entries.forEach { if (!PersonalReference.validLyricId(it.id) || !occurrences.add(it.id)) invalid() }
        }
    }

}

/** Bounded syntax parser rejects decoded duplicate keys before any map can overwrite them. */
private class StrictBackupJson(private val source: Reader, private val check: () -> Unit) {
    private val buffer = CharArray(4096)
    private var available = 0
    private var at = 0
    private fun character(): Int {
        if (available < 0) return -1
        if (at == available) {
            do { check(); available = source.read(buffer) } while (available == 0)
            at = 0
            if (available < 0) return -1
        }
        return buffer[at++].code
    }
    private var next = character()
    private var occurrences = 0
    init { if (next == 0xfeff) advance() }
    private fun invalid(): Nothing = throw TransferFailure("invalid")
    private fun size(): Nothing = throw TransferFailure("size")
    private fun advance() { check(); next = character() }
    private fun ws() {
        while (next >= 0 && next.toChar() in " \t\r\n") {
            check()
            while (at < available) {
                val char = buffer[at]
                if (char != ' ' && char != '\t' && char != '\r' && char != '\n') break
                at++
            }
            next = character()
        }
    }
    private fun take(char: Char): Boolean { ws(); return if (next == char.code) { advance(); true } else false }
    private fun need(char: Char) { if (!take(char)) invalid() }
    private fun string(): String {
        need('"')
        val out = StringBuilder()
        while (next >= 0) {
            check()
            val char = next.toChar()
            advance()
            if (char == '"') {
                val value = out.toString()
                var index = 0
                while (index < value.length) {
                    val point = value.codePointAt(index)
                    if (point in 0xd800..0xdfff) invalid()
                    index += Character.charCount(point)
                }
                return value
            }
            if (char.code < 32) invalid()
            if (char != '\\') out.append(char) else {
                if (next < 0) invalid()
                val escape = next.toChar()
                advance()
                when (escape) {
                    '"', '\\', '/' -> out.append(escape)
                    'b' -> out.append('\b'); 'f' -> out.append('\u000c'); 'n' -> out.append('\n')
                    'r' -> out.append('\r'); 't' -> out.append('\t')
                    'u' -> {
                        var code = 0
                        repeat(4) {
                            if (next < 0 || next.toChar() !in "0123456789abcdefABCDEF") invalid()
                            code = code * 16 + next.toChar().digitToInt(16)
                            advance()
                        }
                        out.append(code.toChar())
                    }
                    else -> invalid()
                }
            }
            if (out.length > BackupPolicy.maxEntryTextCodePoints * 2) size()
        }
        invalid()
    }
    fun parse(): JsonElement = value(emptyList()).also { ws(); if (next >= 0) invalid() }
    private fun value(path: List<String>): JsonElement {
        check(); ws()
        if (path.size > 8 || next < 0) invalid()
        return when (next.toChar()) {
            '"' -> JsonPrimitive(string())
            '{' -> {
                advance()
                val map = linkedMapOf<String, JsonElement>()
                if (!take('}')) do {
                    if (map.size >= 8) invalid()
                    val key = string()
                    if (map.containsKey(key)) invalid()
                    need(':'); map[key] = value(path + key)
                    if (take('}')) break
                    need(',')
                } while (true)
                JsonObject(map)
            }
            '[' -> {
                advance()
                val limit = when (path.joinToString(".")) {
                    "data.favorites" -> BackupPolicy.maxFavorites
                    "data.lists" -> BackupPolicy.maxLists
                    "data.lists.*.entries" -> BackupPolicy.maxEntriesPerList
                    "data.recentHistory.entries" -> BackupPolicy.maxRecents
                    "data.learning.entries" -> BackupPolicy.maxLearningLabels
                    else -> invalid()
                }
                val values = mutableListOf<JsonElement>()
                if (!take(']')) do {
                    if (values.size >= limit) size()
                    if (path == listOf("data", "lists", "*", "entries") && ++occurrences > BackupPolicy.maxTotalEntries) size()
                    values.add(value(path + "*"))
                    if (take(']')) break
                    need(',')
                } while (true)
                JsonArray(values)
            }
            else -> {
                val token = StringBuilder()
                while (next >= 0 && next.toChar() !in " \t\r\n,]}") {
                    check()
                    if (token.length >= 32) invalid()
                    token.append(next.toChar())
                    advance()
                }
                val literal = token.toString()
                when (literal) {
                    "null" -> JsonNull; "true" -> JsonPrimitive(true); "false" -> JsonPrimitive(false)
                    else -> {
                        if (!Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").matches(literal)) invalid()
                        Json.parseToJsonElement(literal)
                    }
                }
            }
        }
    }
}
