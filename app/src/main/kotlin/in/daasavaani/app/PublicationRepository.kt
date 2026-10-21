package `in`.daasavaani.app

import android.content.res.Resources
import `in`.daasavaani.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PreparedPublication(
    val manifest: Manifest, val items: List<FixtureItem>, val index: SearchIndex, val policy: SearchPolicy,
)
class PublicationRepository(private val resources: Resources) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(resources.getInteger(R.integer.connect_timeout_ms).toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(resources.getInteger(R.integer.read_timeout_ms).toLong(), TimeUnit.MILLISECONDS).build()
    private val identity = Regex("[0-9a-f]{64}")
    private fun InputStream.bounded(limit: Int): ByteArray {
        require(limit > 0) { "Invalid artifact bound" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val size = read(buffer)
            if (size == -1) break
            require(size <= limit - output.size()) { "Artifact exceeds byte safety limit" }
            output.write(buffer, 0, size)
        }
        return output.toByteArray()
    }
    private suspend fun fetch(url: URI, limit: Int): ByteArray = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url.toString()).header("Accept-Encoding", "identity").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val bytes = response.use {
                        check(it.isSuccessful) { "Artifact request failed: HTTP ${it.code}" }
                        it.body.byteStream().bounded(limit)
                    }
                    if (continuation.isActive) continuation.resume(bytes)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
    private suspend fun verified(base: URI, reference: ArtifactReference, encodedLimit: Int, decodedLimit: Int): ByteArray {
        require(identity.matches(reference.sha256) && reference.path == "objects/${reference.sha256}.json.gz") { "Invalid artifact hash/path" }
        require(reference.encodedBytes in 1..encodedLimit && reference.decodedBytes in 1..decodedLimit) { "Artifact declared size exceeds limits" }
        val encoded = fetch(base.resolve(reference.path), reference.encodedBytes)
        require(encoded.size == reference.encodedBytes) { "Artifact encoded size mismatch" }
        val hash = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
        require(hash == reference.sha256) { "Artifact SHA256 mismatch" }
        val decoded = GZIPInputStream(ByteArrayInputStream(encoded)).use { it.bounded(reference.decodedBytes) }
        require(decoded.size == reference.decodedBytes) { "Artifact decoded size mismatch" }
        return decoded
    }
    private inline fun <reified T> decode(bytes: ByteArray): T =
        wireJson.decodeFromString(bytes.decodeToString(throwOnInvalidSequence = true))

    suspend fun prepare(progress: (Int, Int) -> Unit): PreparedPublication {
        val endpoint = URI(resources.getString(R.string.descriptor_url))
        val descriptor = decode<Descriptor>(fetch(endpoint, resources.getInteger(R.integer.max_descriptor_bytes)))
        require(descriptor.formatVersion == 1 && descriptor.fixture) { "Unsupported fixture descriptor" }
        val base = endpoint.resolve("./")
        val manifest = decode<Manifest>(verified(base, descriptor.manifest,
            resources.getInteger(R.integer.max_manifest_encoded_bytes), resources.getInteger(R.integer.max_manifest_decoded_bytes)))
        require(manifest.formatVersion == 1 && manifest.fixture &&
            manifest.requiredCapabilities == listOf("fixture-catalog-v1")) { "Unsupported required capabilities" }
        require(manifest.items.keys == manifest.catalog.map { it.videoId }.toSet() &&
            manifest.catalog.map { it.videoId }.distinct().size == manifest.catalog.size) { "Invalid catalog coverage" }
        suspend fun artifact(hash: String, role: String, encodedLimit: Int, decodedLimit: Int): ByteArray {
            val entry = requireNotNull(manifest.objects[hash]) { "Missing manifest object" }
            require(entry.role == role) { "Wrong artifact role" }
            return verified(base, ArtifactReference(entry.path, hash, entry.encodedBytes, entry.decodedBytes), encodedLimit, decodedLimit)
        }
        val policy = decode<SearchPolicy>(artifact(manifest.named.policy, "policy",
            resources.getInteger(R.integer.max_policy_encoded_bytes), resources.getInteger(R.integer.max_policy_decoded_bytes)))
        require(policy.formatVersion == 1 &&
            policy.maxQueryCodePoints in 1..resources.getInteger(R.integer.trusted_max_query_code_points) &&
            policy.maxTerms in 1..resources.getInteger(R.integer.trusted_max_terms) &&
            policy.pageSize in 1..resources.getInteger(R.integer.trusted_max_page_size) &&
            policy.debounceMs >= 0 && policy.weights["lyrics"] != null &&
            policy.editBands.isNotEmpty() && policy.editBands.all { it.maxLength > 0 && it.edits in 0..2 } &&
            policy.editBands.zipWithNext().all { (a, b) -> a.maxLength < b.maxLength } &&
            policy.editBands.last().maxLength >= policy.maxQueryCodePoints) { "Unsupported search policy" }
        val index = decode<SearchIndex>(artifact(manifest.named.index, "index",
            resources.getInteger(R.integer.max_index_encoded_bytes), resources.getInteger(R.integer.max_index_decoded_bytes)))
        require(index.formatVersion == 1 && index.policyHash == manifest.named.policy) { "Index policy mismatch" }
        require(index.tokens.all { it.itemId in manifest.items && it.field in policy.weights && it.value.isNotEmpty() }) { "Invalid index token" }
        require(index.postings.all { (key, entries) ->
            Regex("[123]:.+").matches(key) && entries.all { it.size == 2 && it[0] in index.tokens.indices && it[1] > 0 }
        }) { "Invalid index posting" }
        val items = mutableListOf<FixtureItem>()
        for ((id, hash) in manifest.items) {
            val item = decode<FixtureItem>(artifact(hash, "item",
                resources.getInteger(R.integer.max_item_encoded_bytes), resources.getInteger(R.integer.max_item_decoded_bytes)))
            validateItem(item, id)
            items.add(item)
            progress(items.size, manifest.items.size)
        }
        return PreparedPublication(manifest, items, index, policy)
    }
}
