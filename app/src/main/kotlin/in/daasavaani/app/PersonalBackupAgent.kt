package `in`.daasavaani.app

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import `in`.daasavaani.core.BackupPolicy
import `in`.daasavaani.core.FullPersonalBackup
import `in`.daasavaani.core.FullPersonalBackupCodec
import `in`.daasavaani.core.TransferFailure
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeFormatterBuilder

/** Only a disposable logical projection enters the framework archive, never the live Room files. */
open class PersonalBackupAgent : BackupAgent() {
    private var candidate: FullPersonalBackup? = null
    private var restoreRejected = false

    private fun codec() = FullPersonalBackupCodec(transferCodec(resources),
        resources.getInteger(R.integer.list_compression_level))

    override fun onFullBackup(data: FullBackupDataOutput) {
        captureSnapshot(data.quota) { fullBackupFile(it, data) }
    }

    internal fun captureSnapshot(quota: Long, deliver: (File) -> Unit) {
        val projection = File(filesDir, SNAPSHOT)
        try {
            PersonalStore(this).use { store ->
                val date = DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.now())
                store.exportBackup(date) { bytes ->
                    try {
                        if (quota >= 0 && bytes.size.toLong() > quota) {
                            throw IOException("Personal system backup exceeds transport quota")
                        }
                        projection.outputStream().use { it.write(bytes) }
                        deliver(projection)
                    } finally {
                        // Retire this fixed-name projection before releasing the stable transaction,
                        // so another capture cannot have its newly written file deleted by this one.
                        if (projection.exists() && !projection.delete()) {
                            Log.e(TAG, "Could not remove disposable system-backup projection")
                        }
                    }
                }
            }
        } catch (error: PersonalFailure) {
            Log.e(TAG, "Personal system backup refused: local validation, quota or I/O failure")
            throw IOException("Personal system backup refused")
        } catch (error: TransferFailure) {
            Log.e(TAG, "Personal system backup refused: invalid or oversized personal state")
            throw IOException("Personal system backup refused")
        } catch (error: IOException) {
            Log.e(TAG, "Personal system backup failed: transport quota or I/O")
            throw IOException("Personal system backup I/O failed")
        }
    }

    override fun onRestoreFile(data: ParcelFileDescriptor, size: Long, destination: File,
        type: Int, mode: Long, mtime: Long) {
        try {
            receiveFile(data, size, destination, type)
        } catch (error: IOException) {
            restoreRejected = true
            candidate = null
            Log.e(TAG, "Personal system restore refused: invalid file, snapshot or I/O failure")
            throw IOException("Personal system restore refused")
        }
    }

    private fun receiveFile(data: ParcelFileDescriptor, size: Long, destination: File, type: Int) {
        val firstFile = !restoreRejected && candidate == null
        restoreRejected = true
        candidate = null
        val accepted = firstFile && type == TYPE_FILE &&
            destination.canonicalFile == File(filesDir, SNAPSHOT).canonicalFile &&
            size in 1..BackupPolicy.maxBackupEncodedBytes.toLong()
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(data.fileDescriptor)).use { source ->
            val input = RestoreInput(source, size)
            if (!accepted) {
                restoreRejected = true
                candidate = null
                input.drain()
                Log.e(TAG, "Personal system restore refused: unexpected file, type, duplicate or size")
                throw IOException("Personal system restore refused")
            }
            try {
                candidate = codec().decode(input)
                if (input.remaining != 0L) throw IOException("Incomplete personal system restore")
                restoreRejected = false
            } catch (error: TransferFailure) {
                restoreRejected = true
                candidate = null
                Log.e(TAG, "Personal system restore refused: invalid logical snapshot")
                throw IOException("Personal system restore refused")
            }
        }
    }

    override fun onRestoreFinished() {
        val backup = candidate
        candidate = null
        if (restoreRejected || backup == null) {
            Log.e(TAG, "Personal system restore did not commit a snapshot")
            throw IllegalStateException("Personal system restore did not commit")
        }
        try {
            PersonalStore(this).use { store -> store.restoreBackup(store.previewBackup(backup)) }
        } catch (error: PersonalFailure) {
            // No default database recreation, raw-file replacement or language application here.
            Log.e(TAG, "Personal system restore did not commit: local storage validation or transaction failed")
            throw IllegalStateException("Personal system restore did not commit")
        }
        // Room8 owns the durable postcommit language receipt. Normal startup retries it.
        Log.i(TAG, "Personal system restore committed; language recovery pending startup")
    }

    override fun onQuotaExceeded(backupDataBytes: Long, quotaBytes: Long) {
        Log.e(TAG, "Personal system backup exceeded transport quota; keep an explicit backup file")
    }

    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput,
        newState: ParcelFileDescriptor) {
        throw IOException("Key/value backup is not supported")
    }

    override fun onRestore(data: BackupDataInput, appVersionCode: Int,
        newState: ParcelFileDescriptor) {
        throw IOException("Key/value restore is not supported")
    }

    private class RestoreInput(private val source: InputStream, size: Long) : InputStream() {
        var remaining = size
            private set
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = source.read()
            if (value < 0) throw IOException("Truncated restore stream")
            remaining--
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (remaining <= 0) return -1
            val count = source.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count < 0) throw IOException("Truncated restore stream")
            remaining -= count
            return count
        }
        fun drain() {
            val buffer = ByteArray(8192)
            while (read(buffer) != -1) { /* Framework requires consuming exactly this file's bytes. */ }
        }
    }

    companion object {
        internal const val SNAPSHOT = "system-personal-v1.json.gz"
        private const val TAG = "PersonalSystemBackup"
    }
}
