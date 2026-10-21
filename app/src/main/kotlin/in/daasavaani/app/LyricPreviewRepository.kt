package `in`.daasavaani.app

import android.content.res.Resources
import `in`.daasavaani.core.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface LyricPreviewSource {
    val codec: LyricPreviewCodec
    suspend fun catalog(): LyricCatalog
    suspend fun item(summary: LyricSummary): PreviewLyric
}

data class PreparedLyricPreview(val catalog: LyricCatalog, val items: Map<String, PreviewLyric>)

suspend fun LyricPreviewSource.prepare(progress: (Int, Int) -> Unit): PreparedLyricPreview {
    val catalog = catalog()
    require(catalog.catalog.sumOf { it.item.encodedBytes.toLong() } <= codec.bounds.preparedEncodedBytes &&
        catalog.catalog.sumOf { it.item.decodedBytes.toLong() } <= codec.bounds.preparedDecodedBytes) {
        "Complete lyric library exceeds preparation limit"
    }
    progress(0, catalog.catalog.size)
    val items = linkedMapOf<String, PreviewLyric>()
    val lineIds = mutableSetOf<String>()
    for (summary in catalog.catalog) {
        currentCoroutineContext().ensureActive()
        val lyric = item(summary)
        require(lyric.lines.filter { it.kind == "line" }.all { lineIds.add(requireNotNull(it.lineId)) }) {
            "Duplicate line identity across lyrics"
        }
        items[summary.lyricId] = lyric
        progress(items.size, catalog.catalog.size)
    }
    currentCoroutineContext().ensureActive()
    return PreparedLyricPreview(catalog, items)
}

class LyricPreviewRepository(
    resources: Resources, private val download: (suspend (URI, Int) -> ByteArray)? = null,
) : LyricPreviewSource {
    override val codec = LyricPreviewCodec(PreviewBounds(
        resources.getInteger(R.integer.lyric_descriptor_bytes),
        resources.getInteger(R.integer.lyric_manifest_encoded_bytes),
        resources.getInteger(R.integer.lyric_manifest_decoded_bytes),
        resources.getInteger(R.integer.lyric_item_encoded_bytes),
        resources.getInteger(R.integer.lyric_item_decoded_bytes),
        resources.getInteger(R.integer.lyric_catalog_items), resources.getInteger(R.integer.lyric_lines),
        resources.getInteger(R.integer.lyric_text_code_points), resources.getInteger(R.integer.lyric_tags),
        resources.getInteger(R.integer.lyric_query_code_points),
        resources.getInteger(R.integer.lyric_json_depth),
        resources.getInteger(R.integer.lyric_prepared_encoded_bytes),
        resources.getInteger(R.integer.lyric_prepared_decoded_bytes),
    ))
    private val endpoint = URI(resources.getString(R.string.lyric_descriptor_url))
    private val base = endpoint.resolve("./")
    private val client = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .callTimeout(resources.getInteger(R.integer.lyric_call_timeout_ms).toLong(), TimeUnit.MILLISECONDS)
        .connectTimeout(resources.getInteger(R.integer.connect_timeout_ms).toLong(), TimeUnit.MILLISECONDS)
        .readTimeout(resources.getInteger(R.integer.read_timeout_ms).toLong(), TimeUnit.MILLISECONDS).build()

    private suspend fun fetch(url: URI, limit: Int): ByteArray {
        require(url.scheme == "https" && url.authority == endpoint.authority &&
            url.userInfo == null && url.query == null && url.fragment == null) { "Unsafe lyric URL" }
        download?.let { fetch ->
            return fetch(url, limit).also {
                currentCoroutineContext().ensureActive()
                require(it.size <= limit) { "Lyric response too large" }
            }
        }
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url.toString()).header("Accept-Encoding", "identity").build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            require(it.code == 200 && (it.header("Content-Encoding") ?: "identity") == "identity") {
                                "Lyric request failed"
                            }
                            val length = it.body.contentLength()
                            require(length == -1L || length <= limit) { "Lyric response too large" }
                            it.body.byteStream().readPreviewBounded(limit) {
                                if (!continuation.isActive) throw IOException("Cancelled lyric request")
                            }
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
    private suspend fun verified(reference: ArtifactReference, encoded: Int, decoded: Int): ByteArray {
        codec.reference(reference, encoded, decoded)
        val bytes = fetch(base.resolve(reference.path), reference.encodedBytes)
        val context = currentCoroutineContext()
        return decodePreviewArtifact(bytes, reference) { context.ensureActive() }
    }
    override suspend fun catalog(): LyricCatalog {
        val descriptor = codec.descriptor(fetch(endpoint, codec.bounds.descriptorBytes))
        return codec.catalog(verified(descriptor.manifest, codec.bounds.manifestEncodedBytes, codec.bounds.manifestDecodedBytes))
    }
    override suspend fun item(summary: LyricSummary): PreviewLyric =
        codec.item(verified(summary.item, codec.bounds.itemEncodedBytes, codec.bounds.itemDecodedBytes), summary)
}
