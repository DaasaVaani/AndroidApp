package `in`.daasavaani.app

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class PersonalUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun retainedPreferencesAndFavoritesDoNotPretendContentIsPrepared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("personal.db")
        val kn = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag("kn")))
        })
        val en = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.ENGLISH))
        })
        PersonalStore(context).use { it.change(it.load(), language = "kn", theme = "dark", favorite = "UNAVAILABLE") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            scenario.onActivity { library = ViewModelProvider(it)[LibraryViewModel::class.java] }
            compose.waitUntil(10000) { compose.onAllNodesWithText(kn.getString(R.string.settings)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(kn.getString(R.string.settings)).performClick()
            compose.onNodeWithTag("language-kn").assertIsSelected()
            compose.onNodeWithTag("theme-dark").assertIsSelected()
            compose.onNodeWithTag("theme-warm").performClick()
            compose.waitUntil(10000) { compose.onAllNodes(isSelected() and hasTestTag("theme-warm")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("language-en").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText(en.getString(R.string.settings)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(en.getString(R.string.favorites)).performClick()
            compose.onNodeWithText("UNAVAILABLE").assertIsDisplayed()
            compose.onNodeWithText(en.getString(R.string.preparation_notice)).assertIsDisplayed()
            compose.onNodeWithText(en.getString(R.string.library)).performClick()
            compose.onNodeWithText(en.getString(R.string.prepare_library)).assertIsDisplayed().performClick()
            compose.waitUntil(60000) { library.state.value.prepared != null || library.state.value.error != null }
            assertNull("Fixture preparation must succeed", library.state.value.error)
            compose.onNodeWithText("ಕನ್ನಡ ಓದು — ಪರೀಕ್ಷೆ ೧").assertIsDisplayed()
            compose.onNodeWithText("ಕನ್ನಡ ಓದು — ಪರೀಕ್ಷೆ ೧").performClick()
            compose.onNodeWithText("ರಾಮ ಕೃಷ್ಣ").assertIsDisplayed()
            compose.waitUntil(10000) { library.state.value.readerOpen == null && !library.state.value.saving }
            compose.onNodeWithText(en.getString(R.string.add_favorite)).assertIsEnabled().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText(en.getString(R.string.remove_favorite)).fetchSemanticsNodes().isNotEmpty() }
        }
        PersonalStore(context).use {
            val saved = it.load()
            assertEquals("en", saved.language)
            assertEquals("warm", saved.theme)
            assertEquals(2, saved.favorites.size)
            assertTrue(`in`.daasavaani.core.PersonalReference.legacy("UNAVAILABLE") in saved.favorites)
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(10000) { compose.onAllNodesWithText(en.getString(R.string.prepare_library)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("ರಾಮ ಕೃಷ್ಣ").assertDoesNotExist()
            compose.onNodeWithText(en.getString(R.string.favorites)).performClick()
            compose.onNodeWithText("UNAVAILABLE").assertIsDisplayed()
            compose.onNodeWithText(en.getString(R.string.settings)).performClick()
            compose.onNodeWithTag("language-en").assertIsSelected()
            compose.onNodeWithTag("theme-warm").assertIsSelected()
        }
        context.deleteDatabase("personal.db")
    }
}
