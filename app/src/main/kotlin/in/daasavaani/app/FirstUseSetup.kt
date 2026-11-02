package `in`.daasavaani.app

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.daasavaani.core.BackupPreferences
import `in`.daasavaani.core.ReadingPreferences
import `in`.daasavaani.core.uiLanguage
import java.util.Locale

private data class SetupDraft(val observed: PersonalSnapshot, val preferences: BackupPreferences)

@Composable
fun FirstUseSetup(model: LibraryViewModel, state: LibraryState) {
    val personal = state.personal ?: return
    var draft by remember(state.personalRestoreGeneration) { mutableStateOf<SetupDraft?>(null) }
    if (personal.setupStatus != "pending" && state.section != "settings" && draft == null) return
    val enabled = !state.saving && state.personalError == null
    val current = draft
    fun cancel() {
        draft = null
        model.clearSetupError()
        model.retryPersonal()
    }
    BackHandler(enabled = current != null && !state.saving) { cancel() }
    val base = LocalContext.current
    val config = LocalConfiguration.current
    val devices = Resources.getSystem().configuration.locales
    val language = uiLanguage((0 until devices.size()).map { devices[it].toLanguageTag() },
        current?.preferences?.language ?: personal.language)
    val configuration = Configuration(config).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
    val localized = base.createConfigurationContext(configuration)
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration) {
        val scroll = rememberScrollState()
        val panel = if (state.section == "settings") Modifier.fillMaxWidth()
            else Modifier.fillMaxWidth().heightIn(max = (config.screenHeightDp / 2).dp).verticalScroll(scroll)
        Column(panel.testTag("first-use-panel")) {
            Text(stringResource(R.string.first_use_title), style = MaterialTheme.typography.titleSmall)
            if (current == null) {
                if (state.section == "settings") {
                    val status = when (personal.setupStatus) {
                        "pending" -> R.string.first_use_pending
                        "completed" -> R.string.first_use_completed
                        "skipped" -> R.string.first_use_skipped_status
                        else -> error("Invalid setup status")
                    }
                    Text("${stringResource(R.string.first_use_status)}: ${stringResource(status)}",
                        Modifier.testTag("first-use-status"))
                }
                Row {
                    TextButton(enabled = enabled, modifier = Modifier.weight(1f).testTag("first-use-start"), onClick = {
                        model.clearSetupError()
                        draft = SetupDraft(personal, BackupPreferences(personal.language, personal.theme,
                            personal.reading.fontSizeSp, personal.reading.density))
                    }) { Text(stringResource(R.string.first_use_start)) }
                    if (personal.setupStatus == "pending") TextButton(enabled = enabled,
                        modifier = Modifier.weight(1f).testTag("first-use-skip"),
                        onClick = { model.finishFirstUse(personal, true) }) { Text(stringResource(R.string.first_use_skip)) }
                }
            } else {
                Text(stringResource(R.string.first_use_native_notice))
                Text(stringResource(R.string.first_use_limits))
                Text(stringResource(R.string.first_use_native_offline))
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
                listOf("device" to stringResource(R.string.follow_device), "en" to "English", "kn" to "ಕನ್ನಡ")
                    .forEach { (value, label) ->
                        FilterChip(selected = current.preferences.language == value, enabled = enabled,
                            onClick = { draft = current.copy(preferences = current.preferences.copy(language = value)) },
                            modifier = Modifier.testTag("setup-language-$value"), label = { Text(label) })
                    }
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleSmall)
                listOf("device" to R.string.follow_device, "light" to R.string.light,
                    "dark" to R.string.dark, "warm" to R.string.warm).forEach { (value, caption) ->
                    FilterChip(selected = current.preferences.theme == value, enabled = enabled,
                        onClick = { draft = current.copy(preferences = current.preferences.copy(theme = value)) },
                        modifier = Modifier.testTag("setup-theme-$value"), label = { Text(stringResource(caption)) })
                }
                val reading = ReadingPreferences(current.preferences.lyricFontSizeSp, current.preferences.readingDensity)
                ReadingControls(reading, enabled, { direction ->
                    draft = current.copy(preferences = current.preferences.copy(lyricFontSizeSp = reading.step(direction).fontSizeSp))
                }, { density ->
                    draft = current.copy(preferences = current.preferences.copy(readingDensity = density))
                }, tagPrefix = "setup-")
                Button(enabled = enabled, modifier = Modifier.testTag("first-use-save"), onClick = {
                    model.finishFirstUse(current.observed, false, current.preferences) { draft = null }
                }) { Text(stringResource(R.string.first_use_save)) }
                OutlinedButton(enabled = enabled, modifier = Modifier.testTag("first-use-skip"), onClick = {
                    model.finishFirstUse(current.observed, true) { draft = null }
                }) { Text(stringResource(R.string.first_use_skip)) }
                TextButton(enabled = !state.saving, modifier = Modifier.testTag("first-use-cancel"),
                    onClick = ::cancel) { Text(stringResource(R.string.first_use_cancel)) }
            }
            state.setupError?.let {
                Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("first-use-error"))
            }
        }
    }
}
