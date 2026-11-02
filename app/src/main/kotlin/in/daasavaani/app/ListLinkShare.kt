package `in`.daasavaani.app

import android.content.ActivityNotFoundException
import android.content.Intent

/** Only the already encoded complete canonical link leaves the process on explicit consent. */
internal fun shareListLink(link: String, launch: (Intent) -> Unit, feedback: (Int) -> Unit) {
    shareCanonicalLink(link, launch, R.string.list_shared, R.string.list_share_failed, feedback)
}

internal fun shareCanonicalLink(link: String, launch: (Intent) -> Unit, handed: Int, failed: Int,
    feedback: (Int) -> Unit) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, link)
    }
    try {
        launch(Intent.createChooser(send, null))
        // A chooser launch has no trustworthy receiver-delivery or cancellation receipt.
        feedback(handed)
    } catch (_: ActivityNotFoundException) {
        feedback(failed)
    } catch (_: SecurityException) {
        feedback(failed)
    }
}
