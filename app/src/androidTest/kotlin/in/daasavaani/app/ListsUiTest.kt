package `in`.daasavaani.app

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

class ListsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @After fun clean() { context.deleteDatabase("personal.db") }
    private fun click(label: Int) {
        val text = hasText(context.getString(label)) and hasClickAction()
        val matcher = if (compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()) text and hasAnyAncestor(isDialog()) else text
        compose.onNode(matcher).performClick()
    }
    private fun scroll(label: Int) {
        compose.onNodeWithTag("saved-lists").performScrollToNode(hasText(context.getString(label)))
    }
    private fun entryClick(index: Int, label: Int) {
        val matcher = hasText(context.getString(label)) and hasClickAction() and
            hasAnyAncestor(hasTestTag("occurrence-$index"))
        compose.onNodeWithTag("saved-lists").performScrollToNode(matcher)
        compose.onNode(matcher).performClick()
    }
    private fun seed() {
        context.deleteDatabase("personal.db")
        PersonalStore(context).use { it.change(it.load(), language = "en", theme = "warm") }
    }

    @Test fun createEditReplaceRepeatMoveRemoveAndDuplicateWithoutAnyPreparation() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            scenario.onActivity { model = ViewModelProvider(it)[LibraryViewModel::class.java] }
            compose.waitUntil(10000) { model.state.value.personal != null }
            click(R.string.lists); click(R.string.create_list)
            compose.onNodeWithTag("list-name-input").performTextInput("  Straße é 😀  ")
            click(R.string.save_list)
            compose.waitUntil(10000) { model.state.value.personal?.lists?.size == 1 }
            repeat(2) {
                scroll(R.string.add_occurrence); click(R.string.add_occurrence)
                compose.onNodeWithTag("entry-text").performTextInput("  <b>raw 😀</b>  ")
                click(R.string.save_entry)
                compose.waitUntil(10000) { model.state.value.personal?.lists?.single()?.entries?.size == it + 1 }
            }
            val first = requireNotNull(model.state.value.personal).lists.single()
            assertNotEquals(first.entries[0].id, first.entries[1].id)
            entryClick(0, R.string.edit_entry)
            click(R.string.lyric_reference)
            compose.onNodeWithTag("entry-id").performTextInput("10000000-0000-4000-8000-000000000001")
            compose.onNodeWithTag("entry-fallback").performTextInput("  User label  ")
            click(R.string.save_entry)
            compose.waitUntil(10000) { model.state.value.personal?.lists?.single()?.entries?.first()?.entry?.kind == "lyric" }
            val changed = requireNotNull(model.state.value.personal).lists.single()
            assertEquals(first.entries[0].id, changed.entries[0].id)
            assertEquals(first.entries[1], changed.entries[1])
            entryClick(0, R.string.move_down)
            compose.waitUntil(10000) { model.state.value.personal?.lists?.single()?.entries?.last()?.id == first.entries[0].id }
            entryClick(0, R.string.remove_entry)
            compose.waitUntil(10000) { model.state.value.personal?.lists?.single()?.entries?.size == 1 }
            scroll(R.string.duplicate_list); click(R.string.duplicate_list); click(R.string.save_list)
            compose.onNodeWithText(context.getString(R.string.duplicate_name)).assertIsDisplayed()
            assertEquals(1, model.state.value.personal?.lists?.size)
            click(R.string.cancel)
            assertEquals(1, model.state.value.personal?.lists?.size)
            click(R.string.duplicate_list); click(R.string.save_list); click(R.string.confirm_duplicate)
            compose.waitUntil(10000) { model.state.value.personal?.lists?.size == 2 }
            val lists = requireNotNull(model.state.value.personal).lists
            assertEquals(lists[0].transfer(), lists[1].transfer())
            assertNotEquals(lists[0].entries.single().id, lists[1].entries.single().id)
            assertNull(model.state.value.prepared)
            assertTrue(requireNotNull(model.state.value.personal).favorites.isEmpty())
        }
        PersonalStore(context).use { assertEquals(2, it.load().lists.size) }
    }

    @Test fun confirmedTypedImportAllowsNameChoiceAndCancellationOrStaleFenceNeverWrites() {
        seed()
        val original = RecitationList.create("Straße é", listOf(ListEntry("placeholder", text = "Existing")))
        PersonalStore(context).use { store -> val s = store.load(); store.saveList(s.revision, s.epoch, original) }
        val snapshot = ListSnapshot("STRASSE É", listOf(
            PersonalReference.lyric("10000000-0000-4000-8000-000000000001").entry("  User  "),
            ListEntry("placeholder", text = "<b>raw</b>"),
            PersonalReference.lyric("10000000-0000-4000-8000-000000000001").entry()), 2)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: LibraryViewModel
            lateinit var transfer: TransferViewModel
            scenario.onActivity {
                model = ViewModelProvider(it)[LibraryViewModel::class.java]
                transfer = ViewModelProvider(it)[TransferViewModel::class.java]
            }
            compose.waitUntil(10000) { model.state.value.personal != null }
            scenario.onActivity { transfer.showSnapshot(snapshot); model.section("transfer") }
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(R.string.save_list)))
            click(R.string.save_list); click(R.string.cancel)
            assertEquals(listOf(original), model.state.value.personal?.lists)
            assertNotEquals(R.string.list_saved, transfer.state.value.message)
            click(R.string.save_list)
            compose.onNodeWithTag("import-name").performTextReplacement("  Chosen name 😀  ")
            click(R.string.save_list)
            compose.waitUntil(10000) { model.state.value.personal?.lists?.size == 2 }
            assertEquals(R.string.list_saved, transfer.state.value.message)
            val saved = requireNotNull(model.state.value.personal).lists.last()
            assertEquals("  Chosen name 😀  ", saved.name)
            assertEquals(snapshot.entries, saved.entries.map { it.entry })
            assertEquals(3, saved.entries.map { it.id }.toSet().size)
            compose.onNodeWithTag("transfer-screen").performScrollToNode(hasText(context.getString(R.string.save_list)))
            click(R.string.save_list)
            scenario.onActivity { model.changePersonal(theme = "dark") }
            compose.waitUntil(10000) { !model.state.value.saving }
            click(R.string.save_list); click(R.string.confirm_duplicate)
            compose.waitUntil(10000) { model.state.value.mutationError == R.string.personal_stale }
            compose.onNodeWithTag("import-name").assertTextContains(snapshot.name)
            click(R.string.cancel)
            assertEquals(2, model.state.value.personal?.lists?.size)
            assertEquals(original, model.state.value.personal?.lists?.first())
            assertNull(model.state.value.prepared)
        }
    }
}
