package `in`.daasavaani.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
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

class LearningUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val page get() = context.resources.getInteger(R.integer.trusted_max_page_size)
    private fun id(i: Int) = "10000000-0000-4000-8000-${i.toString().padStart(12, '0')}"
    private fun reference(i: Int) = PersonalReference.lyric(id(i))
    private inner class Source : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000, 3, 10, 100, 5, 128, 16))
        var requests = 0
        val summaries = (1..3).map { LyricSummary(id(it), "Synthetic learning [$it]", emptyList(),
            ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20)) }
        override suspend fun catalog(): LyricCatalog {
            requests++
            return LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"), summaries)
        }
        override suspend fun item(summary: LyricSummary): PreviewLyric {
            requests++
            return PreviewLyric("daasavaani-lyric", 1, summary.lyricId, "kn", summary.title, summary.tags,
                listOf(PreviewLine("line", "  Synthetic ಕ 😀 literal  ", "2${summary.lyricId.drop(1)}"),
                    PreviewLine("layout", " \t")))
        }
    }
    @After fun cleanup() { context.deleteDatabase("personal.db") }
    private fun seed(count: Int = 1): PersonalSnapshot {
        context.deleteDatabase("personal.db")
        return PersonalStore(context).use { store ->
            var s = store.change(store.load(), language = "en")
            repeat(count) { i -> s = store.learning(s, reference(i + 1), LearningLabels.labels[i % 3]) }
            s = store.learning(s, PersonalReference.legacy(id(1)), "practising")
            s = store.learning(s, PersonalReference.legacy("synthetic-unresolved"), "comfortableReciting")
            s
        }
    }
    private fun saved(model: LibraryViewModel, condition: (PersonalSnapshot) -> Boolean = { true }) {
        compose.waitUntil(15000) { !model.state.value.saving && model.state.value.personal?.let(condition) == true }
    }
    private fun row(reference: PersonalReference) = "learning-row-${reference.kind}-${reference.id}"
    private fun read(reference: PersonalReference) = "learning-read-${reference.kind}-${reference.id}"
    private fun learningControl(reference: PersonalReference, label: String?) {
        val match = hasTestTag(row(reference))
        compose.onNodeWithTag("learning-entries").performScrollToNode(match)
        compose.onNode(hasTestTag("learning-label") and hasAnyAncestor(match)).performClick()
        compose.onNodeWithTag("learning-set-${label ?: "none"}").performClick()
    }
    private fun filter(label: String?) {
        compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag("learning-filter"))
        compose.onNodeWithTag("learning-filter").performClick()
        compose.onNodeWithTag("learning-filter-${label ?: "all"}").performClick()
    }
    private fun settings() {
        compose.onNode(hasText(context.getString(R.string.settings)) and hasClickAction()).performClick()
        compose.onNode(hasText(context.getString(R.string.transfer_title)) and hasClickAction()).assertIsDisplayed()
    }
    private fun back(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun readerControl(label: String?) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("learning-label"))
        compose.onNodeWithTag("learning-label").performClick()
        compose.onNodeWithTag("learning-set-${label ?: "none"}").performClick()
    }
    private fun expose(model: LibraryViewModel, real: LyricPreviewViewModel) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
        compose.onNodeWithTag("lyric-text").assertTextEquals("  Synthetic ಕ 😀 literal  \n \t")
        compose.waitUntil(10000) { real.state.value.readerOpen == null && !model.state.value.saving }
    }

    @Test fun prePreparationPagingFiltersTypedUnresolvedManagementNoNetworkAndRestart() {
        val before = seed(page * 2 + 1)
        val source = Source()
        var expected = before
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                val real = LyricPreviewViewModel(it.application, source)
                it.setContent { MaterialTheme { LibraryScreen(model, real) } }
                model.section("learning")
            }
            saved(model)
            compose.onNodeWithTag("learning-count").assertTextEquals(context.getString(R.string.result_count)
                .replace("{shown}", page.toString()).replace("{total}", (page * 2 + 3).toString()))
            repeat(2) {
                compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag("learning-more"))
                compose.onNodeWithTag("learning-more").performClick()
            }
            val tail = reference(page * 2 + 1)
            compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag(row(tail)))
            compose.onNodeWithTag(row(tail)).assertExists()
            learningControl(tail, "comfortableReciting")
            saved(model) { it.learning[page * 2].label == "comfortableReciting" }
            assertEquals(before.learning.map { it.reference }, model.state.value.personal?.learning?.map { it.reference })
            val changed = requireNotNull(model.state.value.personal)
            learningControl(tail, "comfortableReciting")
            saved(model)
            assertEquals(changed, model.state.value.personal)
            LearningLabels.labels.forEach { label ->
                filter(label)
                assertEquals(changed, model.state.value.personal)
                val count = changed.learning.count { it.label == label }
                compose.onNodeWithTag("learning-count").assertTextEquals(context.getString(R.string.result_count)
                    .replace("{shown}", minOf(page, count).toString()).replace("{total}", count.toString()))
            }
            filter("practising")
            learningControl(PersonalReference.legacy(id(1)), null)
            saved(model) { it.learning.none { a -> a.reference == PersonalReference.legacy(id(1)) } }
            filter(null)
            repeat(2) {
                compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag("learning-more"))
                compose.onNodeWithTag("learning-more").performClick()
            }
            expected = requireNotNull(model.state.value.personal)
            assertEquals(before.recentHistory, expected.recentHistory)
            assertEquals(0, source.requests)
            assertNull(model.state.value.prepared)
            compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag(read(reference(1))))
            compose.onNodeWithTag(read(reference(1))).performClick()
            compose.onNodeWithTag("back-to-learning").assertIsDisplayed().performClick()
            assertEquals("learning", model.state.value.section)
            assertEquals(expected, model.state.value.personal)
            compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag(read(PersonalReference.legacy("synthetic-unresolved"))))
            compose.onNodeWithTag(read(PersonalReference.legacy("synthetic-unresolved"))).performClick()
            assertEquals("synthetic-unresolved", model.state.value.pendingFixture)
            settings()
            back(scenario)
            assertEquals("synthetic-unresolved", model.state.value.pendingFixture)
            compose.onNodeWithTag("back-to-learning").performClick()
            assertEquals(0, source.requests)
            assertEquals(expected, PersonalStore(context).use { it.load() })
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java]; model.section("learning") }
            saved(model) { it == expected }
            assertNull(model.state.value.prepared)
        }
    }

    @Test fun pendingCanonicalPreparationMountedLabelFailureNoHistoryReplayAndRepeatedAnotherSettingsLearningOrigin() {
        seed()
        val source = Source()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source) { size -> size - 1 }
                it.setContent { MaterialTheme { LibraryScreen(model, real) } }
                model.section("learning")
            }
            saved(model)
            compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag(read(reference(1))))
            compose.onNodeWithTag(read(reference(1))).performClick()
            assertEquals(id(1), real.state.value.pendingLyricId)
            assertEquals(0, source.requests)
            compose.onNodeWithTag("back-to-learning").assertIsDisplayed()
            settings()
            back(scenario)
            assertEquals(id(1), real.state.value.pendingLyricId)
            assertEquals(0, source.requests)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-load"))
            compose.onNodeWithTag("lyric-load").performClick()
            compose.waitUntil(15000) { real.state.value.item != null }
            expose(model, real)
            saved(model) { it.recentHistory.entries == listOf(reference(1)) }
            val cleared = PersonalStore(context).use { store -> store.history(store.load(), clear = true) }
            readerControl("practising")
            compose.waitUntil(10000) { model.state.value.learningError == R.string.personal_stale && !model.state.value.saving }
            compose.onNodeWithTag("learning-error").assertTextEquals(context.getString(R.string.personal_stale))
            compose.onNodeWithText(context.getString(R.string.learning_saved)).assertDoesNotExist()
            assertEquals(cleared, PersonalStore(context).use { it.load() })
            scenario.onActivity { model.retryPersonal() }
            saved(model) { it == cleared }
            readerControl("practising")
            saved(model) { it.learning.first().label == "practising" }
            expose(model, real)
            assertTrue(requireNotNull(model.state.value.personal).recentHistory.entries.isEmpty())
            val after = requireNotNull(model.state.value.personal)
            readerControl("practising")
            saved(model)
            assertEquals(after, model.state.value.personal)
            repeat(2) {
                compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("show-another-lyric"))
                compose.onNodeWithTag("show-another-lyric").performClick()
                expose(model, real)
                val opened = model.state.value.personal
                settings()
                back(scenario)
                expose(model, real)
                assertEquals(opened, model.state.value.personal)
                compose.onNodeWithTag("back-to-learning").assertIsDisplayed()
            }
            back(scenario)
            assertEquals("learning", model.state.value.section)
            assertEquals(4, source.requests)
            assertEquals(after.learning, model.state.value.personal?.learning)
            assertEquals(after.favorites, model.state.value.personal?.favorites)
            assertEquals(after.lists, model.state.value.personal?.lists)
            compose.onNodeWithText(context.getString(R.string.learning_practice_unavailable)).assertExists()
        }
    }

    @Test fun fixtureReaderLabelsSettingsBackAndActualLocaleStayIndependentOfSuccessfulOpenHistory() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java]; model.prepare() }
            compose.waitUntil(60000) { model.state.value.prepared != null && !model.state.value.saving }
            val item = requireNotNull(model.state.value.prepared).items.first()
            val ref = PersonalReference.legacy(item.videoId)
            scenario.onActivity { model.changeLearning(ref, "wantToLearn") }
            saved(model) { it.learning.any { a -> a.reference == ref } }
            scenario.onActivity { model.section("learning") }
            compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag(read(ref)))
            compose.onNodeWithTag(read(ref)).performClick()
            saved(model) { it.recentHistory.entries.firstOrNull() == ref }
            val cleared = PersonalStore(context).use { store -> store.history(store.load(), clear = true) }
            scenario.onActivity { model.retryPersonal() }
            saved(model) { it == cleared }
            compose.onNodeWithTag("learning-label").performClick()
            compose.onNodeWithTag("learning-set-comfortableReciting").performClick()
            saved(model) { it.learning.last().label == "comfortableReciting" }
            val after = requireNotNull(model.state.value.personal)
            settings()
            back(scenario)
            assertEquals(item, model.state.value.selected)
            assertEquals(after, model.state.value.personal)
            back(scenario)
            assertEquals("learning", model.state.value.section)
            val unresolved = PersonalReference.legacy("synthetic-unresolved")
            compose.onNodeWithTag("learning-entries").performScrollToNode(hasTestTag(read(unresolved)))
            compose.onNodeWithTag(read(unresolved)).performClick()
            compose.onNodeWithText(context.getString(R.string.unavailable_favorite)).assertIsDisplayed()
            assertEquals(unresolved.id, model.state.value.pendingFixture)
            settings()
            back(scenario)
            assertEquals(unresolved.id, model.state.value.pendingFixture)
            compose.onNodeWithTag("back-to-learning").assertIsDisplayed().performClick()
            assertEquals(after, model.state.value.personal)
            scenario.onActivity { model.changePersonal(language = "kn") }
            saved(model) { it.language == "kn" }
            val kn = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag("kn"))
            })
            compose.onNodeWithTag("learning-entries").performScrollToNode(hasText(kn.getString(R.string.learning_notice)))
            compose.onNodeWithText(kn.getString(R.string.learning_notice)).assertIsDisplayed()
            compose.onNodeWithText(kn.getString(R.string.learning_practice_unavailable)).assertIsDisplayed()
            assertEquals(after.learning, model.state.value.personal?.learning)
            assertTrue(requireNotNull(model.state.value.personal).recentHistory.entries.isEmpty())
        }
    }
}
