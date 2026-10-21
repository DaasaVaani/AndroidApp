package `in`.daasavaani.core

import java.security.MessageDigest
import kotlinx.serialization.json.*

data class PersonalNameKey(val profile: String, val value: String)

/** Derived comparison only: original names and transfer/personal identities are never rewritten. */
object PersonalNames {
    const val PROFILE = "unicode16-default-full-nfc-v1"
    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/unicode16/$name")) { "Missing Unicode16 resource: $name" }
            .use { it.readBytes() }

    internal fun verifiedData(bytes: ByteArray, manifestBytes: ByteArray): JsonObject {
        val manifest = Json.parseToJsonElement(manifestBytes.toString(Charsets.UTF_8)).jsonObject
        val data = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        for (value in listOf(manifest, data)) {
            require(value.getValue("formatVersion").jsonPrimitive.int == 1 &&
                value.getValue("profile").jsonPrimitive.content == PROFILE &&
                value.getValue("unicodeVersion").jsonPrimitive.content == "16.0.0") { "Unsupported Unicode16 profile" }
        }
        val identity = manifest.getValue("data").jsonObject
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(identity.getValue("path").jsonPrimitive.content == "unicode16.json" &&
            identity.getValue("bytes").jsonPrimitive.int == bytes.size &&
            identity.getValue("sha256").jsonPrimitive.content == digest) { "Corrupt Unicode16 data" }
        return data
    }

    private val data by lazy { verifiedData(resource("unicode16.json"), resource("manifest.json")) }
    private fun rows(field: String) = data.getValue(field).jsonArray.map { it.jsonArray }
    private val classes by lazy {
        rows("combiningClasses").associate { it[0].jsonPrimitive.int to it[1].jsonPrimitive.int }
    }
    private fun mappings(field: String) = rows(field).associate { row ->
        row[0].jsonPrimitive.int to row[1].jsonArray.map { it.jsonPrimitive.int }
    }
    private val decompositions by lazy { mappings("canonicalDecompositions") }
    private val folding by lazy { mappings("caseFolding") }
    private val compositions by lazy {
        rows("compositions").associate {
            pair(it[0].jsonPrimitive.int, it[1].jsonPrimitive.int) to it[2].jsonPrimitive.int
        }
    }
    private fun pair(a: Int, b: Int) = (a.toLong() shl 21) or b.toLong()
    private fun ccc(cp: Int) = classes[cp] ?: 0
    private fun scalars(text: String): List<Int> {
        check(data.isNotEmpty())
        val out = ArrayList<Int>(text.length)
        var at = 0
        while (at < text.length) {
            val cp = text.codePointAt(at)
            require(cp !in 0xd800..0xdfff) { "Unpaired surrogate in personal name" }
            out.add(cp)
            at += if (cp > 0xffff) 2 else 1
        }
        return out
    }
    private fun string(points: List<Int>) = buildString { points.forEach { appendCodePoint(it) } }
    private fun decompose(cp: Int, out: MutableList<Int>) {
        val s = cp - 0xac00
        if (s in 0 until 11172) {
            out.add(0x1100 + s / 588)
            out.add(0x1161 + (s % 588) / 28)
            if (s % 28 != 0) out.add(0x11a7 + s % 28)
        } else {
            val children = decompositions[cp]
            if (children == null) out.add(cp) else children.forEach { decompose(it, out) }
        }
    }
    private fun ordered(text: String): MutableList<Int> {
        val out = ArrayList<Int>()
        scalars(text).forEach { decompose(it, out) }
        for (i in 1 until out.size) {
            val cls = ccc(out[i])
            if (cls == 0) continue
            var j = i
            while (j > 0 && ccc(out[j - 1]) > cls) {
                val previous = out[j - 1]
                out[j - 1] = out[j]
                out[j] = previous
                j--
            }
        }
        return out
    }
    private fun composite(a: Int, b: Int): Int? {
        val l = a - 0x1100
        val v = b - 0x1161
        if (l in 0 until 19 && v in 0 until 21) return 0xac00 + (l * 21 + v) * 28
        val s = a - 0xac00
        val t = b - 0x11a7
        if (s in 0 until 11172 && s % 28 == 0 && t in 1 until 28) return a + t
        return compositions[pair(a, b)]
    }
    internal fun nfd(text: String) = string(ordered(text))
    internal fun nfc(text: String): String {
        val out = ArrayList<Int>()
        var starter = -1
        var lastClass = 0
        for (cp in ordered(text)) {
            val cls = ccc(cp)
            val combined = if (starter < 0) null else composite(out[starter], cp)
            if (combined != null && (lastClass == 0 || lastClass < cls)) {
                // A successful composition leaves the last uncomposed class unchanged.
                out[starter] = combined
            } else {
                if (cls == 0) starter = out.size
                out.add(cp)
                lastClass = cls
            }
        }
        return string(out)
    }
    internal fun fullCaseFold(text: String) = string(scalars(text).flatMap { folding[it] ?: listOf(it) })
    fun key(original: String) = PersonalNameKey(PROFILE, nfc(fullCaseFold(nfc(original))))
}
