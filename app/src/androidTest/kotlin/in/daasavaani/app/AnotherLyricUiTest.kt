package `in`.daasavaani.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
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

class AnotherLyricUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val page get() = context.resources.getInteger(R.integer.trusted_max_page_size)
    private fun id(i: Int) = "10000000-0000-4000-8000-${i.toString().padStart(12, '0')}"
    private fun reference(i: Int) = PersonalReference.lyric(id(i))
    private fun text(i: Int) = "  Synthetic literal ಕ [$i] <b> 😀  \n \t\n"
    private inner class Source(val count: Int) : LyricPreviewSource {
        override val codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000,
            page * 3, 10, 100, 5, 128, 16))
        var requests = 0
        val summaries = (1..count).map { LyricSummary(id(it), "Synthetic [$it]", emptyList(),
            ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20)) }
        override suspend fun catalog(): LyricCatalog {
            requests++
            return LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"), summaries)
        }
        override suspend fun item(summary: LyricSummary): PreviewLyric {
            requests++
            val i = summaries.indexOf(summary) + 1
            return PreviewLyric("daasavaani-lyric", 1, summary.lyricId, "kn", summary.title, summary.tags,
                listOf(PreviewLine("line", text(i).substringBefore('\n'), "2${summary.lyricId.drop(1)}"),
                    PreviewLine("layout", " \t"), PreviewLine("layout", "")))
        }
    }
    @After fun cleanup() { context.deleteDatabase("personal.db") }
    private fun seed(): PersonalSnapshot {
        context.deleteDatabase("personal.db")
        return PersonalStore(context).use { store ->
            val initial = store.change(store.load(), language = "en", theme = "warm", reference = reference(1))
            val list = store.saveList(initial.revision, initial.epoch,
                RecitationList.create("Synthetic list", listOf(reference(1).entry())))
            store.history(list, opened = reference(1))
        }
    }
    private fun click(label: Int) = compose.onNode(hasText(context.getString(label)) and hasClickAction()).performClick()
    private fun settings() {
        click(R.string.settings)
        compose.onNode(hasText(context.getString(R.string.transfer_title)) and hasClickAction()).assertIsDisplayed()
    }
    private fun another() {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("show-another-lyric"))
        compose.onNodeWithTag("show-another-lyric").performClick()
    }
    private fun expose(model: LibraryViewModel, real: LyricPreviewViewModel, i: Int) {
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
        compose.onNodeWithTag("lyric-text").assertTextEquals(text(i))
        compose.waitUntil(10000) { real.state.value.readerOpen == null && !model.state.value.saving &&
            model.state.value.personal?.recentHistory?.entries?.firstOrNull() == reference(i) }
    }
    private fun position() = compose.onNodeWithTag("lyric-preview").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun gatedFullCatalogIndependentOfFilterPagesAndNoRequestsOrSelectorWrites() {
        val before = seed()
        val source = Source(page * 2 + 1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source) { size -> size - 1 }
                it.setContent { MaterialTheme { LibraryScreen(model, real) } }
            }
            compose.waitUntil(10000) { model.state.value.personal != null && !model.state.value.saving }
            compose.onNodeWithTag("owner-lyrics").performClick()
            compose.onNodeWithTag("show-another-lyric").assertDoesNotExist()
            scenario.onActivity { real.showAnother() }
            assertEquals(0, source.requests)
            assertEquals(before, model.state.value.personal)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-load"))
            compose.onNodeWithTag("lyric-load").performClick()
            compose.waitUntil(10000) { real.state.value.prepared != null }
            assertEquals(before, model.state.value.personal)
            scenario.onActivity { real.query("[1]") }
            compose.waitForIdle()
            assertEquals(1, real.state.value.visible.size)
            another()
            assertEquals(id(source.count), real.state.value.selected?.lyricId)
            expose(model, real, source.count)
            val opened = requireNotNull(model.state.value.personal)
            assertEquals(before.revision + 1, opened.revision)
            another()
            expose(model, real, source.count - 1)
            assertEquals(opened.revision + 1, model.state.value.personal?.revision)
            val after = requireNotNull(model.state.value.personal)
            settings()
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            expose(model, real, source.count - 1)
            assertEquals(after, model.state.value.personal)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            assertNull(real.state.value.selected)
            assertEquals("[1]", real.state.value.query)
            assertEquals(1, real.state.value.pages)
            another()
            expose(model, real, source.count)
            assertEquals(source.count + 1, source.requests)
            assertEquals(before.favorites, model.state.value.personal?.favorites)
            assertEquals(before.lists, model.state.value.personal?.lists)
        }
    }

    @Test fun selectorAloneNeverWritesAndCatalogExcludesOnlySuccessfulSessionOpen() {
        val before = seed()
        val source = Source(2)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                real = LyricPreviewViewModel(it.application, source) { 0 }
                real.load()
            }
            compose.waitUntil(10000) { real.state.value.prepared != null }
            scenario.onActivity {
                real.showAnother()
                assertEquals(id(1), real.state.value.selected?.lyricId)
                val token = requireNotNull(real.state.value.readerOpen)
                assertEquals(reference(1), real.exposed(token))
                assertNull(real.exposed(token))
                real.select(null)
                real.showAnother()
                assertEquals(id(2), real.state.value.selected?.lyricId)
            }
            PersonalStore(context).use { assertEquals(before, it.load()) }
            assertEquals(3, source.requests)
        }
    }

    @Test fun soleCurrentHasHonestLocalizedUnavailableWithoutMutationOrNavigation() {
        seed()
        val source = Source(1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source)
                it.setContent { MaterialTheme { LibraryScreen(model, real) } }
                real.load()
            }
            compose.waitUntil(10000) { real.state.value.prepared != null && model.state.value.personal != null }
            compose.onNodeWithTag("owner-lyrics").performClick()
            another(); expose(model, real, 1)
            val before = requireNotNull(model.state.value.personal)
            another()
            compose.onNodeWithTag("no-another-lyric").assertTextEquals(context.getString(R.string.no_another_lyric))
            assertEquals(id(1), real.state.value.selected?.lyricId)
            assertNull(real.state.value.readerOpen)
            assertEquals(before, model.state.value.personal)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            another()
            compose.onNodeWithTag("no-another-lyric").assertIsDisplayed()
            assertNull(real.state.value.selected)
            assertEquals(before, model.state.value.personal)
            assertEquals(2, source.requests)
        }
    }

    @Test fun replacementsRetainCatalogViewportAndExistingFavoriteListTypographyControls() {
        seed()
        val source = Source(page * 2 + 1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(it.application, source) { 0 }
                it.setContent { MaterialTheme { LibraryScreen(model, real) } }
                real.load()
            }
            compose.waitUntil(10000) { real.state.value.prepared != null && model.state.value.personal != null }
            compose.onNodeWithTag("owner-lyrics").performClick()
            repeat(2) {
                compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-more"))
                compose.onNodeWithTag("lyric-more").performClick()
            }
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-title-${id(source.count)}"))
            val viewport = position()
            compose.onNodeWithTag("lyric-title-${id(source.count)}").performClick()
            expose(model, real, source.count)
            another(); expose(model, real, 1)
            another(); expose(model, real, 2)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasText(context.getString(R.string.add_favorite)))
            click(R.string.add_favorite)
            compose.waitUntil(10000) { model.state.value.personal?.favorites?.contains(reference(2)) == true && !model.state.value.saving }
            click(R.string.add_to_list)
            compose.onNodeWithText("Synthetic list").performClick()
            compose.waitUntil(10000) { model.state.value.personal?.lists?.single()?.entries?.size == 2 && !model.state.value.saving }
            settings()
            scenario.onActivity { model.changeReading(direction = 1); model.changeReading(density = "relaxed") }
            compose.waitUntil(10000) { model.state.value.personal?.reading?.density == "relaxed" && !model.state.value.saving }
            val beforeReturn = model.state.value.personal
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            expose(model, real, 2)
            assertEquals(beforeReturn, model.state.value.personal)
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("lyric-title-${id(source.count)}").assertIsFocused()
            assertEquals(viewport, position(), 1f)
            assertEquals(3, real.state.value.pages)
            assertEquals(source.count + 1, source.requests)
        }
    }

    @Test fun listFavoriteAndRecentOriginsSurviveRepeatedChoicesSettingsAndOneBack() {
        for (origin in listOf("lists", "favorites", "recents")) {
            val before = seed()
            val source = Source(3)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: LibraryViewModel
                lateinit var real: LyricPreviewViewModel
                scenario.onActivity {
                    model = ViewModelProvider(it)[LibraryViewModel::class.java]
                    real = LyricPreviewViewModel(it.application, source) { size -> size - 1 }
                    it.setContent { MaterialTheme { LibraryScreen(model, real) } }
                    real.load()
                }
                compose.waitUntil(10000) { real.state.value.prepared != null && model.state.value.personal != null }
                scenario.onActivity {
                    if (origin == "lists") model.selectList(before.lists.single().id)
                    model.section(origin)
                }
                click(R.string.open)
                expose(model, real, 1)
                another(); expose(model, real, 3)
                another(); expose(model, real, 2)
                compose.onNodeWithText(context.getString(R.string.lyric_back)).assertDoesNotExist()
                settings()
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                expose(model, real, 2)
                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.waitUntil(10000) { model.state.value.section == origin }
                if (origin == "lists") assertEquals(before.lists.single().id, model.state.value.selectedListId)
                assertEquals(before.lists, model.state.value.personal?.lists)
                assertEquals(before.favorites, model.state.value.personal?.favorites)
                assertEquals(4, source.requests)
            }
        }

    }

    @Test fun unsavedTransferPreviewPendingPreparationAndChoicesRetainOriginalSnapshotWithoutSaving() {
    val before = seed()
    val source = Source(2)
    val snapshot = ListSnapshot("Synthetic unsaved preview", listOf(reference(1).entry(),
        ListEntry("placeholder", text = "  Synthetic unsaved literal  ")))
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        lateinit var model: LibraryViewModel
        lateinit var real: LyricPreviewViewModel
        lateinit var transfer: TransferViewModel
        scenario.onActivity {
            model = ViewModelProvider(it)[LibraryViewModel::class.java]
            transfer = ViewModelProvider(it)[TransferViewModel::class.java]
            real = LyricPreviewViewModel(it.application, source) { 0 }
            it.setContent { MaterialTheme { LibraryScreen(model, real, transfer) } }
            transfer.showSnapshot(snapshot)
            model.section("transfer")
        }
        compose.waitUntil(10000) { model.state.value.personal != null && transfer.state.value.snapshot != null }
        compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(R.string.open)))
        click(R.string.open)
        compose.onNodeWithTag("show-another-lyric").assertDoesNotExist()
        assertEquals(0, source.requests)
        assertEquals(id(1), real.state.value.pendingLyricId)
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10000) { model.state.value.section == "transfer" }
        assertEquals(snapshot, transfer.state.value.snapshot)
        assertEquals(before, model.state.value.personal)
        compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(R.string.open)))
        click(R.string.open)
        compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-load"))
        compose.onNodeWithTag("lyric-load").performClick()
        compose.waitUntil(10000) { real.state.value.prepared != null }
        expose(model, real, 1)
        another(); expose(model, real, 2)
        val after = model.state.value.personal
        settings()
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("back-to-transfer").performClick()
        compose.waitUntil(10000) { model.state.value.section == "transfer" }
        assertEquals(snapshot, transfer.state.value.snapshot)
        assertEquals(after, model.state.value.personal)
        assertEquals(before.lists, model.state.value.personal?.lists)
        assertEquals(before.favorites, model.state.value.personal?.favorites)
        assertEquals(3, source.requests)
    }
    }
}
