package `in`.daasavaani.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReadingUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "10000000-0000-4000-8000-000000000001"
    private val reference = PersonalReference.lyric(id)
    private val literal = "  Synthetic ಕ 😀  \n \t\n\nline | never split\t "
    @After fun clean() { context.deleteDatabase("personal.db") }
    private inner class Source : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000, 10, 10, 100, 5, 10, 16))
        val summary = LyricSummary(id, "Synthetic reading title", listOf("synthetic"),
            ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20))
        val lines = listOf(PreviewLine("line", "  Synthetic ಕ 😀  ", "20000000-0000-4000-8000-000000000001"),
            PreviewLine("layout", " \t"), PreviewLine("layout", ""),
            PreviewLine("line", "line | never split\t ", "20000000-0000-4000-8000-000000000002"))
        var requests = 0
        override suspend fun catalog(): LyricCatalog {
            requests++
            return LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"), listOf(summary))
        }
        override suspend fun item(summary: LyricSummary): PreviewLyric {
            requests++
            return PreviewLyric("daasavaani-lyric", 1, id, "kn", summary.title, summary.tags, lines)
        }
    }
    private fun seed(): PersonalSnapshot {
        context.deleteDatabase("personal.db")
        return PersonalStore(context).use { store ->
            val s = store.change(store.load(), language = "en", theme = "warm", reference = reference)
            store.saveList(s.revision, s.epoch, RecitationList.create("Synthetic", listOf(reference.entry(), reference.entry())))
        }
    }
    private fun saved(model: LibraryViewModel, condition: (PersonalSnapshot) -> Boolean = { true }) {
        compose.waitUntil(10000) { !model.state.value.saving && model.state.value.personal?.let(condition) == true }
    }
    private fun layout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }
    private fun expose() {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
        compose.onNodeWithTag("lyric-text").assertTextEquals(literal).assertIsDisplayed()
    }
    private fun control(tag: String) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performClick()
    }

    @Test fun canonicalMountedPeerClearRapidActionsDensitiesAndRestartKeepLiteralTextAndAllOtherState() {
        val before = seed()
        val source = Source()
        var expected = before
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source)
                it.setContent { MaterialTheme { LibraryScreen(model, real) } }
            }
            saved(model)
            assertEquals(before, model.state.value.personal)
            scenario.onActivity { real.load(); real.request(id); model.section("lyrics") }
            compose.waitUntil(10000) { real.state.value.item != null }
            expose()
            saved(model) { it.recentHistory.entries == listOf(reference) }
            assertEquals(ReadingPolicy.readerFontSizeSp.toFloat(), layout("lyric-text").layoutInput.style.fontSize.value)
            assertEquals(ReadingPolicy.readerLineHeightSp.toFloat(), layout("lyric-text").layoutInput.style.lineHeight.value)
            val lines = requireNotNull(real.state.value.item).lines
            val cleared = PersonalStore(context).use { store -> store.history(store.load(), clear = true) }
            control("font-increase")
            compose.waitUntil(10000) { model.state.value.personalError == R.string.personal_stale && !model.state.value.saving }
            assertEquals(cleared, PersonalStore(context).use { it.load() })
            scenario.onActivity { model.retryPersonal() }
            saved(model) { it.recentHistory.entries.isEmpty() }
            scenario.onActivity { repeat(3) { model.changeReading(direction = 1) } }
            saved(model) { it.reading.fontSizeSp == ReadingPolicy.readerFontSizeSp + 3 * ReadingPolicy.readerFontStepSp }
            listOf("compact", "normal", "relaxed").forEach { density ->
                control("density-$density")
                saved(model) { it.reading.density == density }
                expose()
                val s = requireNotNull(model.state.value.personal)
                assertEquals(s.reading.lineHeightSp(), layout("lyric-text").layoutInput.style.lineHeight.value)
                assertEquals(s.reading.fontSizeSp.toFloat(), layout("lyric-text").layoutInput.style.fontSize.value)
                assertEquals(lines, real.state.value.item?.lines)
                assertTrue(s.recentHistory.entries.isEmpty())
                assertEquals(before.copy(revision = s.revision, reading = s.reading), s)
                assertEquals(2, source.requests)
            }
            val same = requireNotNull(model.state.value.personal)
            control("density-relaxed")
            saved(model)
            assertEquals(same, model.state.value.personal)
            control("font-decrease")
            saved(model) { it.reading.fontSizeSp == same.reading.fontSizeSp - ReadingPolicy.readerFontStepSp }
            expected = requireNotNull(model.state.value.personal)
            scenario.onActivity { model.section("settings") }
            compose.onNodeWithTag("font-value").assertTextEquals(context.getString(R.string.font_size_value)
                .replace("{size}", expected.reading.fontSizeSp.toString()).replace("{unit}", "sp"))
            compose.onNodeWithTag("density-relaxed").assertIsSelected()
            compose.onNodeWithTag("font-increase").assertContentDescriptionEquals(context.getString(R.string.increase_lyric_font_size))
            compose.onNodeWithTag("font-decrease").assertContentDescriptionEquals(context.getString(R.string.decrease_lyric_font_size))
            scenario.onActivity { real.cancel() }
        }
        PersonalStore(context).use { assertEquals(expected, it.load()) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java]; model.section("settings") }
            saved(model) { it == expected }
            compose.onNodeWithTag("density-relaxed").assertIsSelected()
            assertEquals(expected, PersonalStore(context).use { it.load() })
        }
    }

    @Test fun actualFixtureReaderChangesOnlyLyricTypographyAndSettingsPersistWithoutAnotherOpen() {
        val before = seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java] }
            saved(model)
            compose.onNodeWithText(context.getString(R.string.prepare_library)).performClick()
            compose.waitUntil(60000) { model.state.value.prepared != null }
            val item = requireNotNull(model.state.value.prepared).items.first()
            scenario.onActivity { model.select(item) }
            saved(model) { it.recentHistory.entries == listOf(PersonalReference.legacy(item.videoId)) }
            val titleBefore = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(item.title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleBefore) }
            val tag = "fixture-line-${item.lines.first().id}"
            compose.onNodeWithTag(tag).assertTextEquals(item.lines.first().text).assertIsDisplayed()
            assertEquals(ReadingPolicy.readerLineHeightSp.toFloat(), layout(tag).layoutInput.style.lineHeight.value)
            val cleared = PersonalStore(context).use { store -> store.history(store.load(), clear = true) }
            scenario.onActivity { model.retryPersonal() }
            saved(model) { it.revision == cleared.revision }
            listOf("compact", "normal", "relaxed").forEach { density ->
                compose.onNodeWithTag("density-$density").performClick()
                saved(model) { it.reading.density == density }
                val previous = requireNotNull(model.state.value.personal).reading.fontSizeSp
                compose.onNodeWithTag("font-increase").performClick()
                saved(model) { it.reading.density == density && it.reading.fontSizeSp == previous + ReadingPolicy.readerFontStepSp }
                compose.onNodeWithTag(tag).assertTextEquals(item.lines.first().text)
                val s = requireNotNull(model.state.value.personal)
                assertEquals(s.reading.lineHeightSp(), layout(tag).layoutInput.style.lineHeight.value)
                assertEquals(item, model.state.value.selected)
                assertEquals(before.copy(revision = s.revision, reading = s.reading), s)
                val titleAfter = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(item.title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleAfter) }
                assertEquals(titleBefore.single().layoutInput.style, titleAfter.single().layoutInput.style)
            }
            assertTrue(requireNotNull(model.state.value.personal).recentHistory.entries.isEmpty())
            assertEquals(item.lines, model.state.value.selected?.lines)
            assertEquals(before.favorites, model.state.value.personal?.favorites)
            assertEquals(before.lists, model.state.value.personal?.lists)
        }
    }

    @Test fun settingsBeforePreparationBoundsNoOpDefaultAndBilingualLabelsAreIndependent() {
        context.deleteDatabase("personal.db")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java]; model.section("settings") }
            saved(model)
            val initial = requireNotNull(model.state.value.personal)
            assertEquals(0L, initial.revision)
            assertEquals(ReadingPreferences(), initial.reading)
            compose.onNodeWithTag("density-normal").assertIsSelected().performClick()
            saved(model)
            assertEquals(initial, model.state.value.personal)
            assertNull(model.state.value.prepared)
            scenario.onActivity {
                repeat((ReadingPolicy.readerFontSizeSp - ReadingPolicy.readerFontMinSp) / ReadingPolicy.readerFontStepSp) {
                    model.changeReading(direction = -1)
                }
            }
            saved(model) { it.reading.fontSizeSp == ReadingPolicy.readerFontMinSp }
            compose.onNodeWithTag("font-decrease").assertIsNotEnabled()
            scenario.onActivity {
                repeat((ReadingPolicy.readerFontMaxSp - ReadingPolicy.readerFontMinSp) / ReadingPolicy.readerFontStepSp) {
                    model.changeReading(direction = 1)
                }
            }
            saved(model) { it.reading.fontSizeSp == ReadingPolicy.readerFontMaxSp }
            compose.onNodeWithTag("font-increase").assertIsNotEnabled()
            compose.onNodeWithTag("density-compact").performClick()
            saved(model) { it.reading.density == "compact" }
            scenario.onActivity { model.changePersonal(language = "kn") }
            saved(model) { it.language == "kn" }
            val kn = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag("kn"))
            })
            compose.onNodeWithTag("font-decrease").assertContentDescriptionEquals(kn.getString(R.string.decrease_lyric_font_size))
            compose.onNodeWithTag("density-compact").assertIsSelected()
            assertEquals("device", model.state.value.personal?.theme)
            assertTrue(requireNotNull(model.state.value.personal).recentHistory.entries.isEmpty())
            assertNull(model.state.value.prepared)
        }
    }
}
