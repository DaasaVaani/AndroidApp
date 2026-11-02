package `in`.daasavaani.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import `in`.daasavaani.core.canonicalLyricLink

internal fun lyricLinkClip(context: Context, link: String): ClipData =
    ClipData.newPlainText(context.getString(R.string.lyric_link), link)

internal fun copyLyricLink(link: String, write: (String) -> Unit, feedback: (Int) -> Unit) {
    try {
        write(link)
        feedback(R.string.lyric_link_copied)
    } catch (_: SecurityException) {
        feedback(R.string.lyric_share_failed)
    } catch (_: IllegalStateException) {
        feedback(R.string.lyric_share_failed)
    }
}

@Composable
internal fun LyricLinkControls(lyricId: String) {
    val context = LocalContext.current
    val base = stringResource(R.string.canonical_base_url)
    val link = remember(base, lyricId) { canonicalLyricLink(base, lyricId) }
    var message by remember(link) { mutableStateOf<Int?>(null) }
    // RESULT_CANCELED also occurs after some successful handoffs; it proves neither outcome.
    val sharer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    Column(Modifier.testTag("lyric-link-controls")) {
        Text(stringResource(R.string.lyric_link))
        Text(stringResource(R.string.lyric_link_notice))
        SelectionContainer { Text(link, Modifier.testTag("lyric-link")) }
        OutlinedButton(onClick = {
            shareCanonicalLink(link, { sharer.launch(it) }, R.string.lyric_link_shared,
                R.string.lyric_share_failed) { message = it }
        }, modifier = Modifier.testTag("share-lyric-link")) { Text(stringResource(R.string.share_lyric_link)) }
        OutlinedButton(onClick = {
            copyLyricLink(link, { value ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: throw IllegalStateException("Clipboard unavailable")
                clipboard.setPrimaryClip(lyricLinkClip(context, value))
            }) { message = it }
        }, modifier = Modifier.testTag("copy-lyric-link")) { Text(stringResource(R.string.copy_lyric_link)) }
        message?.let { Text(stringResource(it), Modifier.testTag("lyric-link-feedback")) }
    }
}
