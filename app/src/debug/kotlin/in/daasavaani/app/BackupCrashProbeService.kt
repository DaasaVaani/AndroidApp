package `in`.daasavaani.app

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import `in`.daasavaani.core.FullPersonalBackupCodec

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
                val backup = codec.decode(requireNotNull(intent.getByteArrayExtra("backup")).inputStream())
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
