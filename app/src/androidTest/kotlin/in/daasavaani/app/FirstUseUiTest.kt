package `in`.daasavaani.app

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.ListEntry
import `in`.daasavaani.core.ListSnapshot
import `in`.daasavaani.core.ReadingPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FirstUseUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() {
        context.deleteDatabase("personal.db")
        PersonalStore(context).use { it.change(it.load(), language = "en") }
    }
    @After fun cleanup() { context.deleteDatabase("personal.db") }
    private fun ready(model: LibraryViewModel) {
        compose.waitUntil(10000) { model.state.value.personal != null && !model.state.value.saving }
    }
    private fun snapshot() = PersonalStore(context).use { it.load() }

    @Test fun skipKeepsPendingListPreviewAndExplicitPreparationGateThroughRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                ViewModelProvider(it)[TransferViewModel::class.java].showSnapshot(
                    ListSnapshot("Synthetic setup destination", listOf(ListEntry("placeholder", text = "  pending  ")), 2))
                model.section("transfer")
            }
            ready(model)
            compose.onNodeWithText("Synthetic setup destination").assertIsDisplayed()
            val before = snapshot()
            compose.onNodeWithTag("first-use-skip").performClick()
            compose.waitUntil(10000) { model.state.value.personal?.setupStatus == "skipped" && !model.state.value.saving }
            compose.onNodeWithText("Synthetic setup destination").assertIsDisplayed()
            assertEquals("transfer", model.state.value.section)
            assertEquals(before.copy(setupStatus = "skipped", revision = before.revision + 1), snapshot())
            assertNull(model.state.value.prepared)
            compose.onNodeWithTag("first-use-panel").assertDoesNotExist()
            scenario.recreate()
            compose.onNodeWithTag("first-use-panel").assertDoesNotExist()
            PersonalStore(context).use { assertEquals("skipped", it.load().setupStatus) }
        }
    }

    @Test fun draftCancelWritesNothingAndAtomicSaveChangesOnlyApprovedPreferences() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java]; model.section("settings") }
            ready(model)
            val before = snapshot()
            compose.onNodeWithTag("first-use-start").performScrollTo().performClick()
            compose.onNodeWithTag("setup-density-relaxed").performScrollTo().performClick()
            compose.onNodeWithTag("setup-theme-warm").performScrollTo().performClick()
            assertEquals(before, snapshot())
            compose.onNodeWithTag("first-use-cancel").performScrollTo().performClick()
            ready(model)
            assertEquals(before, snapshot())
            compose.onNodeWithTag("first-use-start").performScrollTo().performClick()
            compose.onNodeWithTag("setup-font-increase").performScrollTo().performClick()
            compose.onNodeWithTag("setup-density-relaxed").performScrollTo().performClick()
            compose.onNodeWithTag("setup-theme-warm").performScrollTo().performClick()
            compose.onNodeWithTag("setup-language-kn").performScrollTo().performClick()
            assertEquals(before, snapshot())
            compose.onNodeWithTag("first-use-save").performScrollTo().performClick()
            compose.waitUntil(10000) { model.state.value.personal?.setupStatus == "completed" && !model.state.value.saving }
            assertEquals(before.copy(language = "kn", theme = "warm", reading = ReadingPreferences().step(1).copy(density = "relaxed"),
                setupStatus = "completed", revision = before.revision + 1), snapshot())
            compose.onNodeWithTag("first-use-status").assertExists()
            compose.onNodeWithTag("first-use-start").assertExists()
            assertEquals("settings", model.state.value.section)
            assertNull(model.state.value.prepared)
        }
    }

    @Test fun staleDraftShowsFailureAndCancelReopensLatestChoicesWithoutOverwritingPeerData() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java] }
            ready(model)
            compose.onNodeWithTag("first-use-start").performScrollTo().performClick()
            compose.onNodeWithTag("setup-theme-dark").performScrollTo().performClick()
            val latest = PersonalStore(context).use { it.change(it.load(), theme = "warm") }
            compose.onNodeWithTag("first-use-save").performScrollTo().performClick()
            compose.waitUntil(10000) { model.state.value.setupError != null && !model.state.value.saving }
            compose.onNodeWithTag("first-use-error").performScrollTo().assertIsDisplayed()
            assertEquals(latest, snapshot())
            compose.onNodeWithTag("first-use-cancel").performScrollTo().performClick()
            compose.waitUntil(10000) { model.state.value.personal == latest && !model.state.value.saving }
            compose.onNodeWithTag("first-use-start").performScrollTo().performClick()
            compose.onNodeWithTag("setup-theme-warm").performScrollTo().assertIsSelected()
            assertEquals(latest, snapshot())
        }
    }

    @Test fun failedFinalWriteShowsRetryAndNeverClaimsCompletionOrPreparation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java] }
            ready(model)
            val before = snapshot()
            compose.onNodeWithTag("first-use-start").performScrollTo().performClick()
            compose.onNodeWithTag("setup-theme-warm").performScrollTo().performClick()
            context.openOrCreateDatabase("personal.db", Context.MODE_PRIVATE, null).use {
                it.execSQL("CREATE TRIGGER reject_setup BEFORE UPDATE OF setupStatus ON preferences BEGIN SELECT RAISE(ABORT, 'synthetic setup write failure'); END")
            }
            try {
                compose.onNodeWithTag("first-use-save").performScrollTo().performClick()
                compose.waitUntil(10000) { model.state.value.setupError != null && !model.state.value.saving }
                compose.onNodeWithTag("first-use-error").performScrollTo().assertIsDisplayed()
            } finally {
                context.openOrCreateDatabase("personal.db", Context.MODE_PRIVATE, null).use { it.execSQL("DROP TRIGGER reject_setup") }
            }
            assertEquals(before, snapshot())
            compose.onNodeWithTag("first-use-save").performScrollTo().performClick()
            compose.waitUntil(10000) { model.state.value.personal?.setupStatus == "completed" && !model.state.value.saving }
            assertEquals("warm", snapshot().theme)
            assertNull(model.state.value.prepared)
        }
    }
}
