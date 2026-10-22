package `in`.daasavaani.app

import android.app.Application
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import `in`.daasavaani.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricPreviewTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val id = "10000000-0000-4000-8000-000000000001"
    private val lineId = "20000000-0000-4000-8000-000000000001"
    private val summary = LyricSummary(id, "Synthetic review 😀", listOf("synthetic"),
        ArtifactReference("objects/${"a".repeat(64)}.json.gz", "a".repeat(64), 10, 20))
    private val catalog = LyricCatalog("daasavaani-lyric-catalog", 1, "prelaunch", listOf("lyric-preview-v1"), listOf(summary))
    private val lyric = PreviewLyric("daasavaani-lyric", 1, id, "kn", summary.title, summary.tags,
        listOf(PreviewLine("line", "  <b>synthetic</b> 😀  ", lineId), PreviewLine("layout", " \t"),
            PreviewLine("layout", ""), PreviewLine("line", "literal https://example.invalid", "20000000-0000-4000-8000-000000000002")))
    private inner class Source : LyricPreviewSource {
        override var codec = LyricPreviewCodec(PreviewBounds(65536, 100000, 200000, 100000, 200000, 10, 10, 100, 5, 10, 16))
        var catalogRequests = 0
        var itemRequests = 0
        var catalogWork: suspend () -> LyricCatalog = { catalog }
        var itemWork: suspend () -> PreviewLyric = { lyric }
        override suspend fun catalog(): LyricCatalog { catalogRequests++; return catalogWork() }
        override suspend fun item(summary: LyricSummary): PreviewLyric { itemRequests++; return itemWork() }
    }

    @Test fun realTypedFavoriteAndAddToListPreservePendingDestinationWithoutImplicitDownload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("personal.db")
        val list = RecitationList.create("  Synthetic list 😀  ", listOf(PersonalReference.lyric(id).entry(),
            PersonalReference.legacy(id).entry(), ListEntry("placeholder", text = "  <b>raw</b>  ")))
        PersonalStore(context).use { store ->
            val s = store.change(store.load(), language = "en", theme = "warm")
            store.saveList(s.revision, s.epoch, list)
        }
        val source = Source()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity { activity ->
                library = ViewModelProvider(activity)[LibraryViewModel::class.java]
                real = LyricPreviewViewModel(activity.application, source)
                activity.setContent { MaterialTheme { LibraryScreen(library, real) } }
            }
            compose.waitUntil(10000) { library.state.value.personal != null }
            compose.onNodeWithText(context.getString(R.string.lists)).performClick()
            compose.onNodeWithText(list.name).performClick()
            compose.onNodeWithTag("saved-lists").performScrollToNode(hasText(context.getString(R.string.open)))
            val savedScroll = compose.onNodeWithTag("saved-lists").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
            compose.onNodeWithText(context.getString(R.string.open)).performClick()
            assertEquals(0, source.catalogRequests); assertEquals(0, source.itemRequests)
            assertEquals(id, real.state.value.pendingLyricId)
            assertNull(real.state.value.prepared)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-load"))
            compose.onNodeWithTag("lyric-load").performClick()
            compose.waitUntil(10000) { real.state.value.selected?.lyricId == id }
            assertEquals(1, source.catalogRequests); assertEquals(1, source.itemRequests)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasText(context.getString(R.string.add_favorite)))
            compose.onNodeWithText(context.getString(R.string.add_favorite)).performClick()
            compose.waitUntil(10000) { library.state.value.personal?.favorites?.contains(PersonalReference.lyric(id)) == true }
            assertFalse(requireNotNull(library.state.value.personal).favorites.contains(PersonalReference.legacy(id)))
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasText(context.getString(R.string.add_to_list)))
            compose.onNodeWithText(context.getString(R.string.add_to_list)).performClick()
            compose.onNodeWithText(list.name).performClick()
            compose.waitUntil(10000) { library.state.value.personal?.lists?.single()?.entries?.size == 4 }
            val saved = requireNotNull(library.state.value.personal).lists.single()
            assertEquals(list.entries, saved.entries.take(3))
            assertEquals(PersonalReference.lyric(id), saved.entries.last().entry.reference())
            assertNull(saved.entries.last().entry.fallback)
            scenario.onActivity { library.changeReading(direction = 1); library.changeReading(density = "relaxed") }
            compose.waitUntil(10000) { !library.state.value.saving && library.state.value.personal?.reading?.density == "relaxed" }
            compose.onNodeWithText(context.getString(R.string.back_to_list)).performClick()
            compose.onNodeWithTag("saved-lists").assertIsDisplayed()
            assertEquals(savedScroll, compose.onNodeWithTag("saved-lists").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.01f)
            assertEquals(list.id, library.state.value.selectedListId)
            assertEquals(1, source.catalogRequests); assertEquals(1, source.itemRequests)
            scenario.onActivity { real.cancel() }
        }
        PersonalStore(context).use { store ->
            assertEquals(4, store.load().lists.single().entries.size)
            assertEquals(setOf(PersonalReference.lyric(id)), store.load().favorites)
        }
        context.deleteDatabase("personal.db")
    }

    @Test fun completePreparationRejectsAggregateBudgetsAndCrossItemLineCollisions() = runBlocking {
        val source = Source()
        val bounds = source.codec.bounds
        suspend fun rejects() {
            try {
                source.prepare { _, _ -> }
                fail("Incomplete or invalid library preparation accepted")
            } catch (_: IllegalArgumentException) { }
        }
        source.codec = LyricPreviewCodec(bounds.copy(preparedEncodedBytes = 9))
        rejects()
        assertEquals(0, source.itemRequests)
        source.codec = LyricPreviewCodec(bounds.copy(preparedDecodedBytes = 19))
        rejects()
        assertEquals(0, source.itemRequests)
        source.codec = LyricPreviewCodec(bounds)
        val second = summary.copy(lyricId = "10000000-0000-4000-8000-000000000002", title = "Second synthetic")
        source.catalogWork = { catalog.copy(catalog = listOf(summary, second)) }
        rejects()
        assertEquals(2, source.itemRequests)
    }

    @Test fun syntheticPublicationUsesEncodedHashesAndNoItemPrefetch() = runBlocking {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val endpoint = URI(resources.getString(R.string.lyric_descriptor_url))
        assertEquals("https://daasavaani.github.io/library/lyrics/latest.json", endpoint.toString())
        val base = endpoint.resolve("./")
        val artifacts = mutableMapOf<URI, ByteArray>()
        fun artifact(bytes: ByteArray): ArtifactReference {
            val output = ByteArrayOutputStream()
            GZIPOutputStream(output).use { it.write(bytes) }
            val encoded = output.toByteArray()
            val hash = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
            val reference = ArtifactReference("objects/$hash.json.gz", hash, encoded.size, bytes.size)
            artifacts[base.resolve(reference.path)] = encoded
            return reference
        }
        // Layout deliberately omits lineId; it is not a nullable wire property.
        val lyricBytes = """{"format":"daasavaani-lyric","formatVersion":1,"lyricId":"$id","canonicalScript":"kn","title":"Synthetic review 😀","tags":["synthetic"],"lines":[{"kind":"line","lineId":"$lineId","text":"  <b>synthetic</b> 😀  "},{"kind":"layout","text":""}]}""".encodeToByteArray()
        val expected = catalog.copy(catalog = listOf(summary.copy(item = artifact(lyricBytes))))
        val manifest = artifact(Json.encodeToString(expected).encodeToByteArray())
        artifacts[endpoint] = Json.encodeToString(LyricDescriptor("daasavaani-lyrics", 1, manifest)).encodeToByteArray()
        val requested = mutableListOf<URI>()
        val repository = LyricPreviewRepository(resources) { uri, _ ->
            requested.add(uri)
            requireNotNull(artifacts[uri])
        }
        val verified = repository.catalog()
        assertEquals(expected, verified)
        assertEquals(listOf(endpoint, base.resolve(manifest.path)), requested)
        val item = repository.item(verified.catalog.single())
        assertEquals(id, item.lyricId)
        assertEquals("  <b>synthetic</b> 😀  ", item.lines.first().text)
        assertEquals("", item.lines.last().text)
        assertEquals(3, requested.size)
        requested.clear()
        val prepared = repository.prepare { _, _ -> }
        assertEquals(listOf(endpoint, base.resolve(manifest.path), base.resolve(verified.catalog.single().item.path)), requested)
        assertEquals(item, prepared.items.getValue(id))
        val itemUrl = base.resolve(verified.catalog.single().item.path)
        artifacts[itemUrl] = requireNotNull(artifacts[itemUrl]).clone().also { it[0] = 0 }
        try {
            repository.item(verified.catalog.single())
            fail("Corrupted encoded artifact accepted")
        } catch (_: IllegalArgumentException) { }
        assertEquals(expected, verified)
    }

    @Test fun explicitEntryReadOnlyCanonicalLiteralLayoutAndPersonalIsolation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("personal.db")
        val before = PersonalStore(context).use { it.change(it.load(), language = "en", theme = "warm", favorite = "UNAVAILABLE") }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var library: LibraryViewModel
            lateinit var real: LyricPreviewViewModel
            scenario.onActivity {
                library = ViewModelProvider(it)[LibraryViewModel::class.java]
                real = ViewModelProvider(it)[LyricPreviewViewModel::class.java]
            }
            compose.waitUntil(10000) { library.state.value.personal != null }
            compose.onNodeWithTag("owner-lyrics").performClick()
            compose.onNodeWithText(context.getString(R.string.lyric_preview_notice)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.fixture_notice)).assertDoesNotExist()
            assertNull(real.state.value.prepared)
            assertFalse(real.state.value.loading)
            assertNull(library.state.value.prepared)
            compose.onNodeWithText(context.getString(R.string.add_favorite)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.save_list)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.settings)).performClick()
            compose.onNodeWithTag("theme-warm").assertIsSelected()
        }
        PersonalStore(context).use { assertEquals(before, it.load()) }
        context.deleteDatabase("personal.db")

        val source = Source()
        lateinit var model: LyricPreviewViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            model = LyricPreviewViewModel(context.applicationContext as Application, source)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val state by model.state.collectAsState()
                    MaterialTheme { LyricPreviewContent(state, model::load, model::cancel, model::query, model::select) }
                }
            }
            assertEquals(0, source.catalogRequests)
            compose.onNodeWithTag("lyric-load").performClick()
            compose.waitUntil(10000) { model.state.value.prepared != null }
            assertEquals(1, source.itemRequests)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-filter"))
            compose.onNodeWithTag("lyric-filter").performTextInput("absent")
            compose.onNodeWithText(summary.title).assertDoesNotExist()
            compose.onNodeWithTag("lyric-filter").performTextClearance()
            compose.onNodeWithTag("lyric-filter").performTextInput("SYNTHETIC")
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasText(summary.title))
            compose.onNodeWithText(summary.title).performClick()
            compose.waitUntil(10000) { model.state.value.item != null }
            assertEquals(1, source.itemRequests)
            compose.onNodeWithTag("lyric-preview").performScrollToNode(hasTestTag("lyric-text"))
            compose.onNodeWithTag("lyric-text").assertTextEquals(lyric.lines.joinToString("\n") { it.text })
            compose.onNodeWithText(context.getString(R.string.canonical)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.add_favorite)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.save_list)).assertDoesNotExist()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { model.cancel() }
        }
    }

    @Test fun cancellationRetryAndFailurePreserveVerifiedCatalogAndReader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = Source()
        lateinit var model: LyricPreviewViewModel
        fun main(work: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(work)
        main { model = LyricPreviewViewModel(context.applicationContext as Application, source) }
        val pending = CompletableDeferred<LyricCatalog>()
        source.catalogWork = { pending.await() }
        main { model.load() }
        compose.waitUntil(10000) { source.catalogRequests == 1 }
        main { model.cancel() }
        pending.complete(catalog)
        compose.waitForIdle()
        assertNull(model.state.value.prepared)
        assertFalse(model.state.value.loading)
        source.catalogWork = { throw IllegalArgumentException("synthetic failure") }
        main { model.load() }
        compose.waitUntil(10000) { model.state.value.failed }
        source.catalogWork = { catalog }
        val pendingFirstItem = CompletableDeferred<PreviewLyric>()
        source.itemWork = { pendingFirstItem.await() }
        main { model.load() }
        compose.waitUntil(10000) { source.itemRequests == 1 }
        assertNull(model.state.value.prepared)
        assertTrue(model.state.value.visible.isEmpty())
        main { model.cancel() }
        pendingFirstItem.complete(lyric)
        compose.waitForIdle()
        assertNull(model.state.value.prepared)
        source.itemWork = { throw IllegalStateException("synthetic item failure") }
        main { model.load() }
        compose.waitUntil(10000) { model.state.value.failed }
        assertNull(model.state.value.prepared)
        source.itemWork = { lyric }
        main { model.load() }
        compose.waitUntil(10000) { model.state.value.prepared != null && !model.state.value.loading }
        val prepared = requireNotNull(model.state.value.prepared)
        assertEquals(1, model.state.value.completed)
        assertEquals(1, model.state.value.total)
        source.catalogWork = { throw IllegalStateException("synthetic failure") }
        main { model.load() }
        compose.waitUntil(10000) { model.state.value.failed }
        assertEquals(prepared, model.state.value.prepared)
        main { model.select(summary) }
        compose.waitUntil(10000) { model.state.value.item != null }
        source.catalogWork = { catalog }
        source.itemWork = { throw IllegalStateException("synthetic failure") }
        main { model.load() }
        compose.waitUntil(10000) { model.state.value.failed }
        assertEquals(lyric, model.state.value.item)
        val pendingItem = CompletableDeferred<PreviewLyric>()
        source.itemWork = { pendingItem.await() }
        val previousRequests = source.itemRequests
        main { model.load() }
        compose.waitUntil(10000) { source.itemRequests > previousRequests }
        main { model.cancel(); model.select(null) }
        pendingItem.complete(lyric)
        compose.waitForIdle()
        assertNull(model.state.value.selected)
        assertNull(model.state.value.item)
        assertEquals(prepared, model.state.value.prepared)
        source.itemWork = { lyric }
        main { model.load() }
        compose.waitUntil(10000) { !model.state.value.loading && !model.state.value.failed }
        main { model.select(summary) }
        assertEquals(lyric, model.state.value.item)
        main { model.select(null); model.query("x".repeat(11)) }
        assertTrue(model.state.value.failed)
        assertTrue(model.state.value.visible.isEmpty())
        main { model.query("synthetic") }
        assertFalse(model.state.value.failed)
        assertEquals(1, model.state.value.visible.size)
        main { model.cancel() }
    }
}
