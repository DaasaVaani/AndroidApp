package `in`.daasavaani.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import `in`.daasavaani.core.ReadingPolicy
import `in`.daasavaani.core.ReadingPreferences

@Composable
fun ReadingControls(reading: ReadingPreferences, enabled: Boolean, step: (Int) -> Unit, density: (String) -> Unit) {
    Text(stringResource(R.string.reading_preferences), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.lyric_font_size), style = MaterialTheme.typography.labelLarge)
    val decrease = stringResource(R.string.decrease_lyric_font_size)
    val increase = stringResource(R.string.increase_lyric_font_size)
    Row {
        OutlinedButton(onClick = { step(-1) }, enabled = enabled && reading.fontSizeSp > ReadingPolicy.readerFontMinSp,
            modifier = Modifier.testTag("font-decrease").semantics { contentDescription = decrease }) { Text("A−") }
        Text(stringResource(R.string.font_size_value).replace("{size}", reading.fontSizeSp.toString()).replace("{unit}", "sp"),
            Modifier.testTag("font-value"))
        OutlinedButton(onClick = { step(1) }, enabled = enabled && reading.fontSizeSp < ReadingPolicy.readerFontMaxSp,
            modifier = Modifier.testTag("font-increase").semantics { contentDescription = increase }) { Text("A+") }
    }
    Text(stringResource(R.string.reading_density), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth()) {
        ReadingPolicy.supportedDensities.forEach { value ->
            val label = when (value) {
                "compact" -> R.string.density_compact
                "normal" -> R.string.density_normal
                "relaxed" -> R.string.density_relaxed
                else -> error("Unsupported reading density")
            }
            FilterChip(selected = reading.density == value, onClick = { density(value) }, enabled = enabled,
                label = { Text(stringResource(label)) }, modifier = Modifier.weight(1f).testTag("density-$value"))
        }
    }
}
