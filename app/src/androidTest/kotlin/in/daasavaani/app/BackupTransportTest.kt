package `in`.daasavaani.app

import android.app.LocaleManager
import android.os.Bundle
import android.os.LocaleList
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BackupTransportTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var originalLocales: LocaleList
    @Before fun setup() {
        originalLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
        context.deleteDatabase("personal.db")
        PersonalStore(context).use { it.change(it.load(), language = "en", theme = "warm", reference = PersonalReference.legacy("synthetic-transport")) }
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { context.getSystemService(LocaleManager::class.java).applicationLocales = originalLocales }
        context.deleteDatabase("personal.db")
    }
    private fun screen(scenario: ActivityScenario<MainActivity>): Pair<BackupViewModel, LibraryViewModel> {
        lateinit var backup: BackupViewModel
        lateinit var library: LibraryViewModel
        scenario.onActivity {
            backup = ViewModelProvider(it)[BackupViewModel::class.java]
            library = ViewModelProvider(it)[LibraryViewModel::class.java]
        }
        compose.waitUntil(10000) { library.state.value.personal != null && !library.state.value.saving }
        compose.onNodeWithText(context.getString(R.string.settings)).performClick()
        compose.onNodeWithTag("settings-backup").performScrollTo().performClick()
        compose.waitUntil(10000) { !backup.state.value.working }
        return backup to library
    }

    @Test fun actualNativeOsLocaleIsOnlyPostcommitAndDeviceSentinelClearsTheOverride() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val (backup, library) = screen(scenario)
            diagnosticUiCase("backup-actual-os-locale", { "working=${backup.state.value.working} committed=${backup.state.value.committed}" }) {
                val locales = context.getSystemService(LocaleManager::class.java)
                val initial = locales.applicationLocales
                var exported: ByteArray? = null
                PersonalStore(context).use { it.exportBackup("2026-10-25T00:00:00.000Z") { bytes -> exported = bytes } }
                scenario.onActivity { backup.previewStream { requireNotNull(exported).inputStream() } }
                compose.waitUntil(10000) { !backup.state.value.working && backup.state.value.preview != null }
                assertEquals(initial, locales.applicationLocales)
                scenario.onActivity { backup.confirmReplacement { library.refreshAfterPersonalRestore() } }
                compose.waitUntil(10000) { !backup.state.value.working && backup.state.value.committed }
                assertEquals("en", locales.applicationLocales.toLanguageTags())
                assertEquals("en", PersonalStore(context).use { it.load().language })
                val codec = FullPersonalBackupCodec(transferCodec(context.resources), 6)
                val value = codec.decode(requireNotNull(exported).inputStream())
                val device = codec.encode(value.copy(data = value.data.copy(preferences = value.data.preferences.copy(language = "device"))))
                scenario.onActivity { backup.previewStream { device.inputStream() } }
                compose.waitUntil(10000) { !backup.state.value.working && backup.state.value.preview != null }
                assertEquals("en", locales.applicationLocales.toLanguageTags())
                scenario.onActivity { backup.confirmReplacement { library.refreshAfterPersonalRestore() } }
                compose.waitUntil(10000) { !backup.state.value.working && backup.state.value.committed }
                assertTrue(locales.applicationLocales.isEmpty)
                assertEquals("device", PersonalStore(context).use { it.load().language })
                assertEquals(R.string.backup_restored, backup.state.value.message)
            }
        }
    }

    @Test fun actualSystemDocumentCreateAndChooseRoundTripAnUnencryptedPersonalBackup() {
        val fileName = "native-backup-${UUID.randomUUID()}.dvbackup.json.gz"
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val (backup, library) = screen(scenario)
                diagnosticUiCase("backup-actual-documents", { "working=${backup.state.value.working} message=${backup.state.value.message}" }) {
                    val before = PersonalStore(context).use { it.load() }
                    compose.onNodeWithTag("backup-export").performClick()
                    downloads()
                    val field = waitUi { it.className == "android.widget.EditText" && it.isVisibleToUser }
                    assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, fileName)
                    }))
                    click(waitUi { it.text?.toString()?.equals("Save", ignoreCase = true) == true && it.isVisibleToUser })
                    compose.waitUntil(10000) { !backup.state.value.working && backup.state.value.message == R.string.backup_exported }
                    assertEquals(before, PersonalStore(context).use { it.load() })
                    val changed = PersonalStore(context).use { it.change(it.load(), theme = "dark") }
                    compose.onNodeWithTag("backup-choose").performClick()
                    downloads()
                    click(waitUi { it.text?.toString() == fileName && it.isVisibleToUser })
                    compose.waitUntil(10000) { !backup.state.value.working && backup.state.value.preview != null }
                    val selected = requireNotNull(backup.state.value.preview).backup
                    assertEquals("warm", selected.data.preferences.theme)
                    assertEquals(before.favorites.toList(), selected.data.favorites)
                    assertEquals(before.lists, selected.data.lists)
                    assertEquals(changed, PersonalStore(context).use { it.load() })
                    assertNull(library.state.value.prepared)
                    compose.onNodeWithText(context.getString(R.string.backup_privacy)).assertExists()
                }
            }
        } finally {
            // Only this test's unique synthetic export in the explicitly selected Downloads root.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("rm -f /sdcard/Download/$fileName")).use { it.readBytes() }
        }
    }

    private fun downloads() {
        waitUi { it.packageName?.toString() == "com.google.android.documentsui" && it.isVisibleToUser }
        click(waitUi { it.contentDescription?.toString() == "Show roots" && it.isVisibleToUser })
        click(waitUi { it.text?.toString() == "Downloads" && it.isVisibleToUser && clickable(it) != null })
    }
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var target: AccessibilityNodeInfo? = node
        while (target != null && (!target.isClickable || !target.isEnabled)) target = target.parent
        return target
    }
    private fun click(node: AccessibilityNodeInfo) {
        val target = clickable(node)
        assertNotNull("Expected clickable document-picker control", target)
        assertTrue(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun waitUi(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (predicate(node)) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        compose.waitUntil(10000) { found = find(instrumentation.uiAutomation.rootInActiveWindow); found != null }
        return requireNotNull(found)
    }
}
