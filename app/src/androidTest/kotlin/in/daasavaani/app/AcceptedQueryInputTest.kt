package `in`.daasavaani.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class AcceptedQueryInputTest {
    @Test fun rejectionAndSelectionEchoCannotReplaceTheLatestExplicitEdit() {
        var accepted = "old"
        var failed = false
        val edits = mutableListOf<String>()
        val input = AcceptedQueryInput { accepted }
        val submit: (String) -> Unit = {
            edits.add(it)
            failed = it.length > 3
            if (!failed) accepted = it
        }
        input.edit(TextFieldValue("invalid"), submit)
        assertTrue(failed)
        assertEquals("old", input.displayed().text)
        input.edit(TextFieldValue("old", TextRange(1)), submit)
        assertTrue(failed)
        assertEquals(listOf("invalid"), edits)
        input.edit(TextFieldValue("new", TextRange(3)), submit)
        input.edit(TextFieldValue("two", TextRange(3)), submit)
        assertEquals("two", input.displayed().text)
        assertFalse(failed)
        assertEquals(listOf("invalid", "new", "two"), edits)
        assertEquals(TextRange(3), input.displayed().selection)
    }

    @Test fun restorationAndFreshOwnersDisplayCurrentContextWithoutSubmittingIt() {
        var accepted = "restored"
        val input = AcceptedQueryInput { accepted }
        assertEquals("restored", input.displayed().text)
        accepted = "new context"
        assertEquals("new context", input.displayed().text)
        input.edit(TextFieldValue("new context", TextRange(2))) { fail("Restoration is not an edit") }
        assertEquals(TextRange(2), input.displayed().selection)
        accepted = ""
        assertEquals("", input.displayed().text)
        val fresh = AcceptedQueryInput { "fresh owner" }
        assertEquals("fresh owner", fresh.displayed().text)
        assertEquals("", input.displayed().text)
    }
}
