package `in`.daasavaani.app

import android.app.Application
import android.app.LocaleManager
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.daasavaani.core.FullPersonalBackupCodec
import `in`.daasavaani.core.TransferFailure
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BackupState(
    val preview: PersonalBackupPreview? = null,
    val working: Boolean = false,
    val message: Int? = null,
    val languageRetry: String? = null,
    val committed: Boolean = false,
    val recoveringLanguage: Boolean = false,
)

/** A retained candidate is never authorization. Only explicit confirmation calls the replacement adapter. */
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val store = PersonalStore(application)
    private val codec = FullPersonalBackupCodec(transferCodec(application.resources),
        application.resources.getInteger(R.integer.list_compression_level))
    private val mutable = MutableStateFlow(BackupState())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    private var workflow = 0L
    private var exporting = false
    private var recoveryChecked = false
    @Volatile private var stream: Closeable? = null
    internal var applyLanguage: (String) -> Unit = { language ->
        if (Build.VERSION.SDK_INT >= 33) {
            application.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == "device") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
        }
    }

    private fun run(export: Boolean = false, block: suspend (Long) -> Unit) {
        if (mutable.value.working) return
        exporting = export
        val owner = ++workflow
        mutable.update { it.copy(working = true, message = null, committed = false) }
        operation = viewModelScope.launch {
            try { block(owner) }
            catch (error: CancellationException) { throw error }
            catch (error: TransferFailure) {
                currentCoroutineContext().ensureActive()
                if (owner == workflow) mutable.update { it.copy(message =
                    if (error.reason == "size") R.string.backup_too_large else R.string.backup_invalid) }
            } catch (error: PersonalFailure) {
                currentCoroutineContext().ensureActive()
                if (owner == workflow) mutable.update { it.copy(message = when (error.label) {
                    R.string.backup_stale, R.string.personal_stale -> R.string.backup_stale
                    R.string.personal_capacity -> R.string.backup_too_large
                    else -> if (export) R.string.transfer_file_failed else R.string.backup_failed
                }, preview = if (error.label == R.string.backup_stale || error.label == R.string.personal_stale) null else it.preview) }
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                if (owner == workflow) mutable.update { it.copy(message =
                    if (export) R.string.transfer_file_failed else R.string.backup_failed) }
            } finally {
                if (owner == workflow) mutable.update { it.copy(working = false) }
                stream = null
            }
        }
    }

    fun previewFile(uri: Uri) = previewStream {
        require(uri.scheme == "content")
        getApplication<Application>().contentResolver.openInputStream(uri) ?: error("Document unavailable")
    }
    internal fun previewStream(open: () -> InputStream) {
        if (mutable.value.working) return
        mutable.update { it.copy(preview = null, committed = false) }
        run { owner ->
            val candidate = withContext(Dispatchers.IO) {
                val caller = currentCoroutineContext()
                open().use {
                    stream = it
                    val backup = codec.decode(it) { caller.ensureActive() }
                    store.previewBackup(backup) { caller.ensureActive() }
                }
            }
            if (owner == workflow) mutable.update { it.copy(preview = candidate) }
        }
    }

    fun exportFile(uri: Uri) = exportStream({
        require(uri.scheme == "content")
        getApplication<Application>().contentResolver.openOutputStream(uri, "wt") ?: error("Document unavailable")
    }, {
        try { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }
        catch (_: Exception) { /* The provider may retain an incomplete newly selected destination; report that explicitly. */ }
    })

    internal fun exportStream(open: () -> OutputStream, discard: () -> Unit = {}) = run(export = true) { owner ->
        var complete = false
        try {
            withContext(Dispatchers.IO) {
                val caller = currentCoroutineContext()
                val timestamp = DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.now())
                store.exportBackup(timestamp, { caller.ensureActive() }) { bytes ->
                    caller.ensureActive()
                    open().use { output ->
                        stream = output
                        var at = 0
                        while (at < bytes.size) {
                            caller.ensureActive()
                            val count = minOf(8192, bytes.size - at)
                            output.write(bytes, at, count); at += count
                        }
                        output.flush()
                    }
                }
                complete = true
            }
            if (owner == workflow) mutable.update { it.copy(message = R.string.backup_exported) }
        } finally {
            if (!complete) withContext(NonCancellable + Dispatchers.IO) { discard() }
        }
    }

    fun confirmReplacement(onCommitted: suspend () -> Unit) {
        val preview = mutable.value.preview ?: return
        run { owner ->
            val caller = currentCoroutineContext()
            // Cancellation is checked explicitly up to the transaction's commit point. The committed
            // result must still reach the owning models even if departure races with commit.
            withContext(NonCancellable) {
                val restored = withContext(Dispatchers.IO) {
                    store.restoreBackup(preview) { caller.ensureActive() }
                }
                mutable.update { it.copy(preview = null, committed = true, languageRetry = restored.language) }
                try { onCommitted() } catch (_: Exception) {
                    android.util.Log.e("BackupRestore", "Committed personal replacement requires model reload")
                }
                applyCommittedLanguage(owner)
            }
        }
    }

    private suspend fun applyCommittedLanguage(owner: Long) {
        try {
            val pending = withContext(Dispatchers.IO) { store.pendingBackupLanguage() }
            if (pending == null) {
                mutable.update { it.copy(languageRetry = null,
                    message = if (it.committed && owner == workflow) R.string.backup_restored else it.message) }
                return
            }
            mutable.update { it.copy(committed = true, languageRetry = pending.language) }
            if (!store.isCurrent(pending)) return
            applyLanguage(pending.language)
            val acknowledged = try {
                withContext(Dispatchers.IO) { store.acknowledgeBackupLanguage(pending) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                android.util.Log.e("BackupRestore", "Committed language applied; durable recovery acknowledgement retained")
                mutable.update { it.copy(languageRetry = null,
                    message = if (owner == workflow) R.string.backup_restored else it.message) }
                return
            }
            val remaining = if (acknowledged) null else withContext(Dispatchers.IO) { store.pendingBackupLanguage() }
            val unapplied = remaining?.takeIf { it.language != pending.language }
            mutable.update { it.copy(languageRetry = unapplied?.language,
                message = if (unapplied != null) R.string.backup_language_retry
                    else if (owner == workflow) R.string.backup_restored else it.message) }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            mutable.update { it.copy(message = if (it.committed) R.string.backup_language_retry else R.string.personal_failed) }
        }
    }
    fun recoverPendingLanguage() {
        if (recoveryChecked || mutable.value.working) return
        recoveryChecked = true
        launchLanguageRecovery()
    }
    fun retryLanguage() {
        if ((mutable.value.languageRetry == null && mutable.value.message != R.string.personal_failed) ||
            mutable.value.working) return
        launchLanguageRecovery()
    }
    private fun launchLanguageRecovery() {
        val owner = ++workflow
        mutable.update { it.copy(working = true, recoveringLanguage = true) }
        operation = viewModelScope.launch {
            try {
                withContext(NonCancellable) { applyCommittedLanguage(owner) }
            } catch (error: CancellationException) { throw error }
            finally { mutable.update { it.copy(working = false, recoveringLanguage = false) } }
        }
    }

    fun cancel() {
        if (!mutable.value.working || mutable.value.recoveringLanguage) return
        operation?.cancel()
        try { stream?.close() } catch (_: Exception) { /* No completed-file claim follows cancellation. */ }
        mutable.update { it.copy(preview = null,
            message = if (it.committed) it.message else if (exporting) R.string.backup_export_cancelled else R.string.backup_cancelled) }
    }
    fun leave() {
        cancel()
        mutable.update { it.copy(preview = null) }
    }
    override fun onCleared() {
        cancel()
        val running = operation
        if (running != null && !running.isCompleted) running.invokeOnCompletion { store.close() } else store.close()
        super.onCleared()
    }
}
