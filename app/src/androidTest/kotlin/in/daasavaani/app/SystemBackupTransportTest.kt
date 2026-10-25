package `in`.daasavaani.app

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Phases let the owning host run real bmgr callbacks between separate instrumentation processes. */
class SystemBackupTransportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val expected = File(context.noBackupFilesDir, "system-transport-test-expected")
    private val control = File(context.filesDir, "system-transport-test-control")
    private val codec = FullPersonalBackupCodec(transferCodec(context.resources), 6)
    private class Agent(context: Context) : PersonalBackupAgent() {
        init { attachBaseContext(context) }
    }
    private fun seed() {
        context.deleteDatabase("personal.db")
        PersonalStore(context).use { store ->
            val ref = PersonalReference.lyric("11111111-1111-4111-8111-111111111111")
            var s = store.change(store.load(), language = "en", theme = "dark", reference = ref)
            s = store.change(s, reference = PersonalReference.legacy(ref.id))
            s = store.saveList(s.revision, s.epoch, RecitationList.create(" raw ಕನ್ನಡ 😀 ",
                listOf(ref.entry(), ref.entry("  é 😀  "), ListEntry("placeholder", text = "  raw 😀  "))))
            s = store.history(s, opened = ref)
            s = store.history(s, enabled = false)
            s = store.learning(s, ref, "practising")
            s = store.reading(s, direction = 1)
            s = store.reading(s, density = "compact")
            store.rememberRecitation(s, s.lists[0], s.lists[0].entries[1].id)
            store.exportBackup("2026-10-25T00:00:00.000Z") { expected.writeBytes(it) }
        }
    }
    private fun mutate() {
        PersonalStore(context).use { store ->
            val changed = store.change(store.load(), language = "device", theme = "warm")
            control.writeText(changed.revision.toString())
        }
    }
    private fun verify() {
        val original = codec.decode(expected.inputStream())
        PersonalStore(context).use { store ->
            val restored = store.load()
            assertEquals(control.readText().toLong() + 1, restored.revision)
            assertNotNull(store.pendingBackupLanguage())
            store.exportBackup(original.exportedAt) { assertEquals(original, codec.decode(it.inputStream())) }
        }
        assertFalse(File(context.filesDir, PersonalBackupAgent.SNAPSHOT).exists())
    }
    private fun cleanup() {
        context.deleteDatabase("personal.db")
        expected.delete()
        control.delete()
    }
    @Test fun originalLogicalStateAndLocalControlSurviveReplacement() {
        val phase = InstrumentationRegistry.getArguments().getString("systemBackupTransportPhase")
        try {
            when (phase) {
                "preflight" -> {
                    val info = context.packageManager.getApplicationInfo(context.packageName, 0)
                    assertTrue("System-backup activation requires explicit platform failure-clear approval",
                        info.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
                    assertEquals(PersonalBackupAgent::class.java.name, info.backupAgentName)
                }
                "seed" -> seed()
                "mutate" -> mutate()
                "verify" -> verify()
                "cleanup" -> cleanup()
                null -> {
                    seed()
                    mutate()
                    val agent = Agent(context)
                    ParcelFileDescriptor.open(expected, ParcelFileDescriptor.MODE_READ_ONLY).use {
                        agent.onRestoreFile(it, expected.length(), File(context.filesDir, PersonalBackupAgent.SNAPSHOT),
                            android.app.backup.BackupAgent.TYPE_FILE, 0, 0)
                    }
                    agent.onRestoreFinished()
                    verify()
                }
                else -> fail("Unknown transport fixture phase")
            }
        } finally {
            if (phase != "seed" && phase != "mutate" && phase != "preflight") cleanup()
        }
    }
}
