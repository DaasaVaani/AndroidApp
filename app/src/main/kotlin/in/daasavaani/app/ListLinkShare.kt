package `in`.daasavaani.app

import android.content.ActivityNotFoundException
import android.content.Intent

/** Only the already encoded complete canonical link leaves the process on explicit consent. */
internal fun shareListLink(link: String, launch: (Intent) -> Unit, feedback: (Int) -> Unit) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    try {
        launch(Intent.createChooser(send, null))
        // A chooser launch has no trustworthy receiver-delivery or cancellation receipt.
        feedback(R.string.list_shared)
    } catch (_: ActivityNotFoundException) {
        feedback(R.string.list_share_failed)
    } catch (_: SecurityException) {
        feedback(R.string.list_share_failed)
    }
}
