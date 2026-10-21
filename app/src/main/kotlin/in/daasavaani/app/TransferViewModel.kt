package `in`.daasavaani.app

import android.app.Application
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.daasavaani.core.ListSnapshot
import `in`.daasavaani.core.ListTransfer
import `in`.daasavaani.core.TransferBounds
import `in`.daasavaani.core.TransferFailure
import java.io.Closeable
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun transferCodec(resources: Resources) = ListTransfer(
    TransferBounds(resources.getInteger(R.integer.max_list_encoded_bytes), resources.getInteger(R.integer.max_list_decoded_bytes),
        resources.getInteger(R.integer.max_shared_url_ascii), resources.getInteger(R.integer.max_list_name_code_points),
        resources.getInteger(R.integer.max_entry_text_code_points), resources.getInteger(R.integer.max_entries_per_list)),
    resources.getString(R.string.canonical_base_url), resources.getString(R.string.list_link_path),
    resources.getInteger(R.integer.list_compression_level))

data class TransferState(
    val snapshot: ListSnapshot? = null, val input: String = "", val link: String? = null,
    val working: Boolean = false, val message: Int? = null,
)

/** Process-local previews deliberately have no dependency on personal storage or library networking. */
class TransferViewModel(application: Application) : AndroidViewModel(application) {
    private val codec = transferCodec(application.resources)
    private val mutable = MutableStateFlow(TransferState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var activeFailure = R.string.transfer_failed
    @Volatile private var stream: Closeable? = null
    fun input(value: String) {
        if (!mutable.value.working) {
            // Refuse oversized paste before retaining it; never silently truncate a complete link.
            if (value.length > getApplication<Application>().resources.getInteger(R.integer.max_shared_url_ascii)) {
                mutable.update { it.copy(input = "", message = R.string.transfer_link_too_long) }
            } else mutable.update { it.copy(input = value, message = null) }
        }
    }
    private fun run(failureLabel: Int = R.string.transfer_failed, block: suspend () -> Unit) {
        if (mutable.value.working) return
        activeFailure = failureLabel
        mutable.update { it.copy(working = true, message = null) }
        operation = viewModelScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: TransferFailure) {
                currentCoroutineContext().ensureActive()
                mutable.update { it.copy(message = if (failureLabel != R.string.transfer_failed) failureLabel else when (error.reason) {
                    "size" -> R.string.transfer_too_large
                    "url" -> R.string.transfer_link_too_long
                    else -> R.string.transfer_invalid
                }) }
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutable.update { it.copy(message = failureLabel) }
            }
            finally {
                mutable.update { it.copy(working = false) }
                stream = null
                activeFailure = R.string.transfer_failed
            }
        }
    }
    fun previewLink() {
        val input = mutable.value.input
        run {
            val value = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                codec.decodeLink(input) { context.ensureActive() }
            }
            mutable.update { it.copy(snapshot = value, link = null) }
        }
    }
    fun previewFile(uri: Uri) = previewStream {
        require(uri.scheme == "content") { "Unsupported document" }
        getApplication<Application>().contentResolver.openInputStream(uri) ?: error("File unavailable")
    }
    internal fun previewStream(open: () -> InputStream) = run {
        val value = withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            context.ensureActive()
            open().use {
                stream = it
                context.ensureActive()
                codec.decodeFile(it) { context.ensureActive() }
            }
        }
        mutable.update { it.copy(snapshot = value, link = null) }
    }
    fun createLink() {
        val value = mutable.value.snapshot ?: return
        run {
            val link = withContext(Dispatchers.Default) {
                val context = currentCoroutineContext()
                codec.encodeLink(value) { context.ensureActive() }
            }
            mutable.update { it.copy(link = link) }
        }
    }
    fun exportFile(uri: Uri) {
        val value = mutable.value.snapshot ?: return
        run(failureLabel = R.string.transfer_file_failed) {
            var complete = false
            try {
                withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    val bytes = codec.encodeFile(value) { context.ensureActive() }
                    require(uri.scheme == "content") { "Unsupported destination" }
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                        stream = it
                        var at = 0
                        while (at < bytes.size) {
                            context.ensureActive()
                            val count = minOf(8192, bytes.size - at)
                            it.write(bytes, at, count); at += count
                        }
                        it.flush()
                    } ?: error("File unavailable")
                    context.ensureActive()
                    complete = true
                }
                mutable.update { it.copy(message = R.string.transfer_file_saved) }
            } finally {
                if (!complete) {
                    // Only the newly created export document is eligible for best-effort cleanup.
                    withContext(NonCancellable + Dispatchers.IO) {
                        try { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }
                        catch (_: Exception) { /* provider may retain an incomplete destination; never report success */ }
                    }
                }
            }
        }
    }
    fun feedback(label: Int) { mutable.update { it.copy(message = label) } }
    fun cancel() {
        val message = if (activeFailure == R.string.transfer_file_failed) R.string.transfer_file_failed else R.string.transfer_cancelled
        operation?.cancel()
        try { stream?.close() } catch (_: Exception) { /* cancellation is already visible */ }
        mutable.update { it.copy(message = message) }
    }
    override fun onCleared() {
        try { stream?.close() } catch (_: Exception) { /* no success is reported for an abandoned operation */ }
        super.onCleared()
    }
}
