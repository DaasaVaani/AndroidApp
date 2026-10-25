package `in`.daasavaani.app

import android.app.ActivityManager
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.os.Parcel
import android.os.ResultReceiver
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BackupLanguageRecoveryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val codec = FullPersonalBackupCodec(transferCodec(context.resources), 6)
    private lateinit var originalLocales: LocaleList
    @Before fun setup() {
        originalLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
        context.deleteDatabase("personal.db")
        PersonalStore(context).use { it.change(it.load(), language = "en", theme = "warm",
            reference = PersonalReference.legacy("synthetic-before-crash")) }
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { context.getSystemService(LocaleManager::class.java).applicationLocales = originalLocales }
        context.deleteDatabase("personal.db")
    }
    private fun backup() = FullPersonalBackup("2026-10-25T00:00:00.000Z", BackupPersonalData(
        BackupPreferences("en", "dark", 30, "compact"), listOf(PersonalReference.legacy("synthetic-after-crash")),
        emptyList(), RecentHistory(false, listOf(PersonalReference.legacy("synthetic-retained-history"))),
        emptyList(), RecitationPositionV1()))

    private fun crash(beforeCommit: Boolean, systemRestore: Boolean = false) {
        val started = CountDownLatch(1)
        val pid = AtomicInteger(-1)
        val failed = AtomicBoolean(false)
        val callback = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                if (resultCode == 0) { pid.set(resultData.getInt("pid")); started.countDown() }
                else failed.set(true)
            }
        }
        val parcel = Parcel.obtain()
        val remote = try {
            callback.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            ResultReceiver.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
        val manager = context.getSystemService(ActivityManager::class.java)
        try {
            context.startService(Intent(context, BackupCrashProbeService::class.java)
                .putExtra("backup", codec.encode(backup())).putExtra("beforeCommit", beforeCommit)
                .putExtra("systemRestore", systemRestore).putExtra("callback", remote))
            assertTrue("Owned crash probe started", started.await(10, TimeUnit.SECONDS))
            compose.waitUntil(10000) { manager.runningAppProcesses.none { it.pid == pid.get() } }
            assertFalse("Crash probe completed its selected transaction boundary", failed.get())
        } finally {
            manager.runningAppProcesses.find { (pid.get() == -1 || it.pid == pid.get()) &&
                it.processName == "${context.packageName}:backup_crash_probe" }?.let { android.os.Process.killProcess(it.pid) }
        }
    }

    @Test fun systemRestoreProcessDeathBeforeFinishedCannotActivateStagedDataOrLanguage() {
        ActivityScenario.launch(BackupRecoveryProbeActivity::class.java).use {
            val before = PersonalStore(context).use { it.load() }
            crash(true, systemRestore = true)
            PersonalStore(context).use { store ->
                assertEquals(before, store.load())
                assertNull(store.pendingBackupLanguage())
            }
        }
    }

    @Test fun systemRestoreProcessDeathAfterFinishedRecoversCommittedLanguageOnActualStartup() {
        ActivityScenario.launch(BackupRecoveryProbeActivity::class.java).use { crash(false, systemRestore = true) }
        val committed = PersonalStore(context).use { store ->
            assertNotNull(store.pendingBackupLanguage())
            store.load()
        }
        assertEquals(backup().data.favorites.toSet(), committed.favorites)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: BackupViewModel
            scenario.onActivity { model = ViewModelProvider(it)[BackupViewModel::class.java] }
            compose.waitUntil(10000) { model.state.value.committed && !model.state.value.working }
            assertEquals("en", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
            PersonalStore(context).use { store ->
                assertEquals(committed, store.load())
                assertNull(store.pendingBackupLanguage())
            }
        }
    }

    @Test fun actualAbruptProcessDeathBeforeCommitRollsBackDataAndLanguageReceiptTogether() {
        ActivityScenario.launch(BackupRecoveryProbeActivity::class.java).use {
            val before = PersonalStore(context).use { it.load() }
            diagnosticUiCase("backup-crash-before-commit", { "revision=${before.revision}" }) {
                crash(true)
                PersonalStore(context).use { store ->
                    assertEquals(before, store.load())
                    assertNull(store.pendingBackupLanguage())
                }
            }
        }
    }

    @Test fun actualAbruptProcessDeathAfterCommitReappliesLanguageOnRealMainStartupWithoutPersonalWrites() {
        ActivityScenario.launch(BackupRecoveryProbeActivity::class.java).use { crash(false) }
        val committed = PersonalStore(context).use { store ->
            assertNotNull(store.pendingBackupLanguage())
            store.load()
        }
        assertEquals("dark", committed.theme)
        assertEquals(backup().data.favorites.toSet(), committed.favorites)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: BackupViewModel
            scenario.onActivity { model = ViewModelProvider(it)[BackupViewModel::class.java] }
            diagnosticUiCase("backup-crash-restart-language", { "state=${model.state.value.copy(preview = null)}" }) {
                compose.waitUntil(10000) { model.state.value.committed && !model.state.value.working }
                assertEquals("en", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
                PersonalStore(context).use { store ->
                    assertEquals(committed, store.load())
                    assertNull(store.pendingBackupLanguage())
                }
                assertEquals(R.string.backup_restored, model.state.value.message)
            }
        }
    }

    @Test fun failedLocaleSurvivesTwoFreshViewModelLifetimesWithVisibleTruthfulRetryAndNoPersonalRewrite() {
        PersonalStore(context).use { it.restoreBackup(it.previewBackup(backup())) }
        val committed = PersonalStore(context).use { it.load() }
        repeat(2) { attempt ->
            ActivityScenario.launch(BackupRecoveryProbeActivity::class.java).use { scenario ->
                lateinit var model: BackupViewModel
                lateinit var realSetter: (String) -> Unit
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            BackupViewModel(context.applicationContext as Application).also {
                                realSetter = it.applyLanguage
                                it.applyLanguage = { throw IllegalStateException("synthetic restart locale fault") }
                            } as T
                    })[BackupViewModel::class.java]
                    activity.setContent { MaterialTheme { LibraryScreen(backupModel = model) } }
                }
                diagnosticUiCase("backup-restart-locale-failure-$attempt", { "state=${model.state.value.copy(preview = null)}" }) {
                    compose.waitUntil(10000) { model.state.value.committed && !model.state.value.working }
                    compose.onNodeWithTag("backup-language-recovery").assertIsDisplayed()
                    assertEquals(R.string.backup_language_retry, model.state.value.message)
                    PersonalStore(context).use { store ->
                        assertEquals(committed, store.load())
                        assertNotNull(store.pendingBackupLanguage())
                    }
                    if (attempt == 1) {
                        scenario.onActivity { model.applyLanguage = realSetter }
                        compose.onNodeWithTag("backup-language-recovery-retry").performClick()
                        compose.waitUntil(10000) { !model.state.value.working && model.state.value.languageRetry == null }
                        assertEquals("en", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
                        PersonalStore(context).use { store ->
                            assertEquals(committed, store.load())
                            assertNull(store.pendingBackupLanguage())
                        }
                        assertEquals(R.string.backup_restored, model.state.value.message)
                    }
                }
            }
        }
    }

    @Test fun pendingReceiptIsRevisionFencedSupersededByLanguageChangeAndExcludedFromWire() {
        PersonalStore(context).use { store ->
            store.restoreBackup(store.previewBackup(backup()))
            val pending = requireNotNull(store.pendingBackupLanguage())
            var exported: ByteArray? = null
            store.exportBackup("2026-10-25T00:00:00.000Z") { exported = it }
            assertEquals(backup().data, codec.decode(requireNotNull(exported).inputStream()).data)
            val newer = store.change(store.load(), theme = "warm")
            assertFalse(store.acknowledgeBackupLanguage(pending))
            assertNotNull(store.pendingBackupLanguage())
            store.change(newer, language = "kn")
            assertNull(store.pendingBackupLanguage())
            assertFalse(store.acknowledgeBackupLanguage(pending))
            store.restoreBackup(store.previewBackup(backup()))
            val current = requireNotNull(store.pendingBackupLanguage())
            assertTrue(store.acknowledgeBackupLanguage(current))
            assertEquals(current, store.load())
            assertNull(store.pendingBackupLanguage())
        }
    }

    @Test fun acknowledgementAndRecoveryReadStorageFaultsPreserveTruthAndPersonalState() {
        instrumentation.runOnMainSync {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
        }
        lateinit var committed: PersonalSnapshot
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: BackupViewModel
            lateinit var library: LibraryViewModel
            lateinit var retryModel: BackupViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[BackupViewModel::class.java]
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
                model.recoverPendingLanguage()
            }
            compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving && !model.state.value.working }
            context.openOrCreateDatabase("personal.db", Context.MODE_PRIVATE, null).use {
                it.execSQL("CREATE TRIGGER synthetic_ack_fault BEFORE UPDATE OF backupLanguagePending ON preferences WHEN OLD.backupLanguagePending = 1 AND NEW.backupLanguagePending = 0 BEGIN SELECT RAISE(ABORT, 'synthetic ack fault'); END")
            }
            try {
                scenario.onActivity { model.previewStream { codec.encode(backup()).inputStream() } }
                compose.waitUntil(10000) { !model.state.value.working && model.state.value.preview != null }
                scenario.onActivity { model.confirmReplacement { library.refreshAfterPersonalRestore() } }
                compose.waitUntil(10000) { !model.state.value.working && model.state.value.committed }
                assertEquals(R.string.backup_restored, model.state.value.message)
                assertNull(model.state.value.languageRetry)
                assertEquals("en", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
                committed = requireNotNull(library.state.value.personal)
                context.openOrCreateDatabase("personal.db", Context.MODE_PRIVATE, null).use {
                    it.rawQuery("SELECT backupLanguagePending FROM preferences", null).use { row ->
                        assertTrue(row.moveToFirst()); assertEquals(1, row.getInt(0))
                    }
                }
                scenario.onActivity { activity ->
                    retryModel = ViewModelProvider(activity).get("syntheticRecoveryStorageFault", BackupViewModel::class.java)
                    activity.setContent { MaterialTheme { LibraryScreen(model = library, backupModel = retryModel) } }
                }
                compose.waitUntil(10000) { !retryModel.state.value.working && retryModel.state.value.message == R.string.personal_failed }
                assertFalse(retryModel.state.value.committed)
                assertNull(retryModel.state.value.languageRetry)
                compose.onNodeWithTag("backup-language-recovery").assertIsDisplayed()
            } finally {
                context.openOrCreateDatabase("personal.db", Context.MODE_PRIVATE, null).use {
                    it.execSQL("DROP TRIGGER IF EXISTS synthetic_ack_fault")
                }
            }
            compose.onNodeWithTag("backup-language-recovery-retry").performClick()
            compose.waitUntil(10000) { retryModel.state.value.committed && !retryModel.state.value.working }
            assertEquals(R.string.backup_restored, retryModel.state.value.message)
            PersonalStore(context).use {
                assertEquals(committed, it.load())
                assertNull(it.pendingBackupLanguage())
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: BackupViewModel
            lateinit var library: LibraryViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[BackupViewModel::class.java]
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
                model.recoverPendingLanguage()
            }
            compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving && !model.state.value.working }
            assertFalse(model.state.value.committed)
            PersonalStore(context).use {
                assertEquals(committed, it.load())
                assertNull(it.pendingBackupLanguage())
            }
        }
    }

    @Test fun concurrentFreshThemeWriterPreservesAppliedLanguageTruthAndRetainsReceiptUntilFreshAcknowledgement() {
        instrumentation.runOnMainSync {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags("en")
        }
        PersonalStore(context).use { it.restoreBackup(it.previewBackup(backup())) }
        val committed = PersonalStore(context).use { it.load() }
        val expected = committed.copy(theme = "warm", revision = committed.revision + 1)
        ActivityScenario.launch(BackupRecoveryProbeActivity::class.java).use { scenario ->
            lateinit var model: BackupViewModel
            scenario.onActivity { activity ->
                model = ViewModelProvider(activity, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        BackupViewModel(context.applicationContext as Application).also { vm ->
                            val realSetter = vm.applyLanguage
                            vm.applyLanguage = { language ->
                                realSetter(language)
                                runBlocking(Dispatchers.IO) {
                                    PersonalStore(context).use { it.change(it.load(), theme = "warm") }
                                }
                            }
                        } as T
                })[BackupViewModel::class.java]
                activity.setContent { MaterialTheme { LibraryScreen(backupModel = model) } }
            }
            diagnosticUiCase("backup-language-fresh-writer-ack", { "state=${model.state.value.copy(preview = null)}" }) {
                compose.waitUntil(10000) { model.state.value.committed && !model.state.value.working }
                assertEquals(R.string.backup_restored, model.state.value.message)
                assertNull(model.state.value.languageRetry)
                PersonalStore(context).use {
                    assertEquals(expected, it.load())
                    assertNotNull(it.pendingBackupLanguage())
                }
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: BackupViewModel
            scenario.onActivity { model = ViewModelProvider(it)[BackupViewModel::class.java] }
            compose.waitUntil(10000) { model.state.value.committed && !model.state.value.working }
            PersonalStore(context).use {
                assertEquals(expected, it.load())
                assertNull(it.pendingBackupLanguage())
            }
        }
    }

    @Test fun schemaSevenUpgradePreservesLogicalStateAndDoesNotInventRestoreOrLanguageWork() {
        val before = PersonalStore(context).use { it.load() }
        context.openOrCreateDatabase("personal.db", Context.MODE_PRIVATE, null).use {
            it.execSQL("ALTER TABLE preferences DROP COLUMN backupLanguagePending")
            it.version = 7
        }
        PersonalStore(context).use {
            assertEquals(before, it.load())
            assertNull(it.pendingBackupLanguage())
        }
        context.openOrCreateDatabase("personal.db", Context.MODE_PRIVATE, null).use { assertEquals(8, it.version) }
    }
}
