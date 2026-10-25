package `in`.daasavaani.app

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import `in`.daasavaani.core.FullPersonalBackupCodec
import java.io.File

class BackupRecoveryProbeActivity : ComponentActivity()

/** Debug-only abrupt-process probes; neither component is packaged in release. */
class BackupCrashProbeService : Service() {
    override fun onBind(intent: Intent): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val callback = requireNotNull(intent.getParcelableExtra<ResultReceiver>("callback"))
        callback.send(0, Bundle().apply { putInt("pid", android.os.Process.myPid()) })
        Thread({
            try {
                val codec = FullPersonalBackupCodec(transferCodec(resources), resources.getInteger(R.integer.list_compression_level))
                val bytes = requireNotNull(intent.getByteArrayExtra("backup"))
                if (intent.getBooleanExtra("systemRestore", false)) {
                    val agent = object : PersonalBackupAgent() {
                        init { attachBaseContext(this@BackupCrashProbeService) }
                    }
                    val file = File(noBackupFilesDir, "system-restore-crash-probe")
                    try {
                        file.writeBytes(bytes)
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use {
                            agent.onRestoreFile(it, bytes.size.toLong(), File(filesDir, PersonalBackupAgent.SNAPSHOT),
                                android.app.backup.BackupAgent.TYPE_FILE, 0, 0)
                        }
                    } finally { file.delete() }
                    if (!intent.getBooleanExtra("beforeCommit", false)) agent.onRestoreFinished()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                val backup = codec.decode(bytes.inputStream())
                val store = PersonalStore(this)
                val preview = store.previewBackup(backup)
                store.restoreBackup(preview) {
                    if (intent.getBooleanExtra("beforeCommit", false) &&
                        store.pendingBackupLanguage()?.revision == preview.observed.revision + 1) {
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }
            } catch (_: Exception) {
                callback.send(1, Bundle())
            } finally {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }, "backup-crash-probe").start()
        return START_NOT_STICKY
    }
}
