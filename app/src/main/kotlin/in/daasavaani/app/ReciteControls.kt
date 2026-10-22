package `in`.daasavaani.app

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal enum class AwakeStatus(val caption: Int) {
    OFF(R.string.screen_awake_inactive), ACTIVE(R.string.screen_awake_active),
    RELEASED(R.string.screen_awake_released), UNSUPPORTED(R.string.screen_awake_unsupported),
    FAILED(R.string.screen_awake_failed),
}

internal class ViewScreenAwake(private val view: View) {
    var requested = false
        private set
    private var ownsFlag = false

    fun request(): AwakeStatus {
        if (!view.isAttachedToWindow) return AwakeStatus.UNSUPPORTED
        return try {
            if (!view.keepScreenOn) {
                ownsFlag = true
                view.keepScreenOn = true
            }
            if (view.keepScreenOn) {
                requested = true
                AwakeStatus.ACTIVE
            } else AwakeStatus.FAILED
        } catch (_: SecurityException) {
            AwakeStatus.FAILED
        }
    }

    fun release(): AwakeStatus {
        return try {
            if (ownsFlag) {
                view.keepScreenOn = false
                if (view.keepScreenOn) return AwakeStatus.FAILED
                ownsFlag = false
            }
            requested = false
            AwakeStatus.RELEASED
        } catch (_: SecurityException) {
            AwakeStatus.FAILED
        }
    }
}

class ReciteState {
    var active by mutableStateOf(false)
    var controls by mutableStateOf(false)
    internal var release: (() -> Unit)? = null
    fun exit() { release?.invoke(); active = false; controls = false }
}

@Composable
internal fun ReciteControls(state: ReciteState, exposed: Boolean, view: View = LocalView.current) {
    if (!exposed) return
    if (!state.active) {
        OutlinedButton(onClick = { state.controls = false; state.active = true },
            modifier = Modifier.testTag("enter-recite")) { Text(stringResource(R.string.recite)) }
        return
    }
    BackHandler { state.exit() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val adapter = remember(view) { ViewScreenAwake(view) }
    var status by remember(adapter) { mutableStateOf(AwakeStatus.OFF) }
    val awakeLabel = stringResource(R.string.keep_screen_awake)
    DisposableEffect(adapter, lifecycle) {
        state.release = { status = adapter.release() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && (adapter.requested || status == AwakeStatus.FAILED)) {
                status = adapter.release()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            adapter.release()
            state.release = null
        }
    }
    Column(Modifier.testTag("recite-controls")) {
        Row {
            OutlinedButton(onClick = { state.exit() }, modifier = Modifier.testTag("exit-recite")) {
                Text(stringResource(R.string.exit_recite))
            }
            OutlinedButton(onClick = { state.controls = !state.controls },
                modifier = Modifier.testTag("toggle-reading-controls")) {
                Text(stringResource(if (state.controls) R.string.hide_reading_controls else R.string.show_reading_controls))
            }
        }
        Text(stringResource(R.string.recite_notice), Modifier.testTag("recite-notice"))
        Row {
            Switch(checked = adapter.requested,
                onCheckedChange = { requested ->
                    status = if (requested && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) adapter.request()
                        else if (requested) AwakeStatus.UNSUPPORTED else adapter.release()
                }, modifier = Modifier.testTag("screen-awake").semantics { contentDescription = awakeLabel })
            Text(awakeLabel)
        }
        Text(stringResource(status.caption), Modifier.testTag("screen-awake-status"))
    }
}
