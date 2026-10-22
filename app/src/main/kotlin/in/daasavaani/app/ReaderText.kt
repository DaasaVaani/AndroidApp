package `in`.daasavaani.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.daasavaani.core.FixtureItem
import `in`.daasavaani.core.PreviewLyric
import `in`.daasavaani.core.ReadingPreferences

@Composable
internal fun CanonicalLyricText(lyric: PreviewLyric, reading: ReadingPreferences) {
    SelectionContainer {
        Text(lyric.lines.joinToString("\n") { it.text }, Modifier.testTag("lyric-text"),
            fontSize = reading.fontSizeSp.sp, lineHeight = reading.lineHeightSp().sp)
    }
}

@Composable
internal fun FixtureLyricText(item: FixtureItem, reading: ReadingPreferences) {
    Column(verticalArrangement = Arrangement.spacedBy(reading.lineSpacingDp().dp)) {
        item.lines.forEach { line ->
            Text(line.text, Modifier.testTag("fixture-line-${line.id}"),
                fontSize = reading.fontSizeSp.sp, lineHeight = reading.lineHeightSp().sp)
        }
    }
}
