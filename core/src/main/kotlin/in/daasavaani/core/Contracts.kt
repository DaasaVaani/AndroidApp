package `in`.daasavaani.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val wireJson = Json { ignoreUnknownKeys = true }

@Serializable
data class ArtifactReference(val path: String, val sha256: String, val encodedBytes: Int, val decodedBytes: Int)
@Serializable
data class Descriptor(val formatVersion: Int, val fixture: Boolean, val manifest: ArtifactReference)
@Serializable
data class ObjectRecord(val role: String, val path: String, val encodedBytes: Int, val decodedBytes: Int)
@Serializable
data class NamedRoles(val index: String, val policy: String)
@Serializable
data class Summary(val videoId: String, val title: String, val tags: List<String>, val description: String? = null)
@Serializable
data class Manifest(
    val formatVersion: Int, val fixture: Boolean, val requiredCapabilities: List<String>,
    val objects: Map<String, ObjectRecord>, val items: Map<String, String>,
    val named: NamedRoles, val catalog: List<Summary>,
)
@Serializable
data class LyricLine(val id: String, val text: String)
@Serializable
data class FixtureItem(
    val formatVersion: Int, val fixture: Boolean, val videoId: String, val title: String,
    val tags: List<String>, val lines: List<LyricLine>, val description: String? = null,
)
@Serializable
data class EditBand(val maxLength: Int, val edits: Int)
@Serializable
data class SearchPolicy(
    val formatVersion: Int, val maxQueryCodePoints: Int, val maxTerms: Int, val pageSize: Int,
    val debounceMs: Int, val maxExcerptLines: Int, val maxExcerptCodePoints: Int,
    val editBands: List<EditBand>, val weights: Map<String, Int>,
)
@Serializable
data class SearchToken(val value: String, val itemId: String, val field: String, val lineId: String? = null)
@Serializable
data class SearchIndex(
    val formatVersion: Int, val policyHash: String, val tokens: List<SearchToken>,
    val postings: Map<String, List<List<Int>>>,
)

private val videoIdentity = Regex("[A-Za-z0-9_-]{1,128}")
private val lineIdentity = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
fun validateItem(item: FixtureItem, expectedId: String) {
    require(item.formatVersion == 1 && item.fixture) { "Unsupported fixture item" }
    require(item.videoId == expectedId && videoIdentity.matches(item.videoId)) { "Item identity mismatch" }
    require(item.title.isNotEmpty() && item.lines.isNotEmpty()) { "Incomplete fixture item" }
    require(item.lines.all { lineIdentity.matches(it.id) }) { "Invalid line UUID" }
    require(item.lines.map { it.id }.toSet().size == item.lines.size) { "Duplicate line UUID" }
}
