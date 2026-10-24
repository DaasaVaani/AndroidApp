package `in`.daasavaani.app

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal class AcceptedQueryInput(private val current: () -> String) {
    private var input by mutableStateOf(current().let { TextFieldValue(it, TextRange(it.length)) })

    fun displayed(): TextFieldValue {
        val accepted = current()
        return if (input.text == accepted) input else TextFieldValue(accepted, TextRange(accepted.length))
    }

    fun edit(next: TextFieldValue, submit: (String) -> Unit) {
        val previous = displayed()
        // Selection updates and IME acknowledgements must not resubmit retained text.
        if (next.text != previous.text) submit(next.text)
        val accepted = current()
        input = when (accepted) {
            next.text -> next
            previous.text -> previous
            else -> TextFieldValue(accepted, TextRange(accepted.length))
        }
    }
}

@Composable
internal fun AcceptedQueryField(
    owner: Any?,
    current: () -> String,
    submit: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val latestCurrent by rememberUpdatedState(current)
    val input = remember(owner) { AcceptedQueryInput { latestCurrent() } }
    OutlinedTextField(value = input.displayed(), onValueChange = { input.edit(it, submit) },
        singleLine = true, label = { Text(label) }, modifier = modifier)
}
