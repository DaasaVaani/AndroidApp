package `in`.daasavaani.app

import android.graphics.Bitmap
import android.os.Debug
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

private const val DIAGNOSTIC_TAG = "UiFailureDiagnostics"

internal fun <T> diagnosticUiWait(name: String, state: () -> String, action: () -> T): T {
    val started = SystemClock.elapsedRealtime()
    Log.i(DIAGNOSTIC_TAG, "$name START elapsed=$started state=${state()}")
    try {
        return action().also {
            Log.i(DIAGNOSTIC_TAG, "$name PASS durationMs=${SystemClock.elapsedRealtime() - started} state=${state()}")
        }
    } catch (failure: Throwable) {
        Log.e(DIAGNOSTIC_TAG, "$name FAIL durationMs=${SystemClock.elapsedRealtime() - started} state=${state()}")
        throw failure
    }
}

internal fun <T> diagnosticUiCase(name: String, state: () -> String, action: () -> T): T {
    try {
        return action()
    } catch (failure: Throwable) {
        val snapshot = state()
        Log.e(DIAGNOSTIC_TAG, "$name FAILURE state=$snapshot", failure)
        try {
            captureUiFailure(name, snapshot, failure)
        } catch (captureFailure: Exception) {
            failure.addSuppressed(captureFailure)
            Log.e(DIAGNOSTIC_TAG, "$name diagnostic capture failed", captureFailure)
        }
        throw failure
    }
}

private fun captureUiFailure(name: String, state: String, failure: Throwable) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val output = requireNotNull(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")) {
        "Runner additionalTestOutputDir is required for persistent diagnostics"
    }
    val directory = File(output,
        "rec04-isolated-diagnostics/$name-${SystemClock.elapsedRealtime()}")
    check(directory.mkdirs()) { "Cannot create diagnostic directory" }
    File(directory, "state.txt").writeText(
        "elapsed=${SystemClock.elapsedRealtime()}\n$state\n${failure.stackTraceToString()}")
    Log.i(DIAGNOSTIC_TAG, "CAPTURE directory=${directory.absolutePath}")
    fun capture(file: String, action: (File) -> Unit) {
        try {
            action(File(directory, file))
        } catch (error: Exception) {
            File(directory, "capture-errors.txt").appendText("$file: ${error.stackTraceToString()}\n")
            Log.e(DIAGNOSTIC_TAG, "Capture $file failed", error)
        }
    }
    capture("queue-art.txt") { file ->
        file.writeText("runtime=${Debug.getRuntimeStats()}\nmainStack=\n" +
            Looper.getMainLooper().thread.stackTrace.joinToString("\n"))
        Looper.getMainLooper().dump({ line -> file.appendText("\n$line") }, "main: ")
    }
    val automation = instrumentation.uiAutomation
    capture("hierarchy.xml") { file ->
        file.outputStream().bufferedWriter().use { writer ->
            val xml = Xml.newSerializer()
            xml.setOutput(writer)
            xml.startDocument("UTF-8", true)
            var remaining = 256
            fun node(value: AccessibilityNodeInfo, depth: Int) {
                if (remaining-- <= 0 || depth > 24) return
                xml.startTag(null, "node")
                xml.attribute(null, "package", value.packageName?.toString().orEmpty())
                xml.attribute(null, "class", value.className?.toString().orEmpty())
                xml.attribute(null, "id", value.viewIdResourceName.orEmpty())
                xml.attribute(null, "text", value.text?.toString().orEmpty())
                xml.attribute(null, "description", value.contentDescription?.toString().orEmpty())
                xml.attribute(null, "visible", value.isVisibleToUser.toString())
                for (index in 0 until value.childCount) {
                    if (remaining <= 0) break
                    value.getChild(index)?.let { node(it, depth + 1) }
                }
                xml.endTag(null, "node")
            }
            xml.startTag(null, "hierarchy")
            automation.rootInActiveWindow?.let { node(it, 0) }
            xml.endTag(null, "hierarchy")
            xml.endDocument()
        }
    }
    capture("screen.png") { file ->
        val bitmap = checkNotNull(automation.takeScreenshot()) { "Screenshot unavailable" }
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }
    capture("logcat.txt") { file ->
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("logcat -d -t 400"))
            .use { input -> file.outputStream().use { output -> input.copyTo(output) } }
    }
    capture("gfxinfo.txt") { file ->
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("dumpsys -t 3 gfxinfo ${context.packageName}"))
            .use { input -> file.outputStream().use { output -> input.copyTo(output) } }
    }
}
