package `in`.daasavaani.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val darkBackground = Color(getColor(R.color.theme_background)).luminance() < 0.5f
        val systemBars = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { darkBackground }
        enableEdgeToEdge(statusBarStyle = systemBars, navigationBarStyle = systemBars)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = colorResource(R.color.theme_primary),
                background = colorResource(R.color.theme_background),
                surface = colorResource(R.color.theme_surface),
                onBackground = colorResource(R.color.theme_text),
                onSurface = colorResource(R.color.theme_text),
            )) { LibraryScreen() }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(model: LibraryViewModel = viewModel()) {
    val state by model.state.collectAsState()
    val space = integerResource(R.integer.spacing_dp).dp
    val contentPadding = integerResource(R.integer.content_padding_dp).dp
    val readerFont = integerResource(R.integer.reader_font_size_sp).sp
    val readerLineHeight = integerResource(R.integer.reader_line_height_sp).sp
    BackHandler(enabled = state.selected != null) { model.select(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(contentPadding), verticalArrangement = Arrangement.spacedBy(space * 2)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.fixture_notice), Modifier.padding(space * 1.5f), style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val selected = state.selected
            if (selected != null) {
                TextButton(onClick = { model.select(null) }) { Text(stringResource(R.string.back)) }
                Text(selected.title, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.canonical), style = MaterialTheme.typography.labelSmall)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(space * 2.5f)) {
                    selected.lines.forEach { line -> Text(line.text, fontSize = readerFont, lineHeight = readerLineHeight) }
                }
            } else if (state.prepared == null) {
                Text(stringResource(R.string.preparation_notice))
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("${stringResource(R.string.loading)} ${state.completed}/${state.total}")
                    OutlinedButton(onClick = model::cancel) { Text(stringResource(R.string.cancel)) }
                } else {
                    Button(onClick = model::prepare) { Text(stringResource(R.string.prepare_library)) }
                }
                Text(stringResource(R.string.unavailable_features), style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedTextField(value = state.query, onValueChange = model::query,
                    label = { Text(stringResource(R.string.search)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space * 1.5f)) {
                    items(state.visible.take(requireNotNull(state.prepared).policy.pageSize), key = { it.videoId }) { item ->
                        Card(onClick = { model.select(item) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(space * 2.5f), verticalArrangement = Arrangement.spacedBy(space * 1.5f)) {
                                Text(item.title, style = MaterialTheme.typography.titleLarge)
                                item.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                Text(stringResource(R.string.open), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (state.visible.isEmpty()) item { Text(stringResource(R.string.no_results)) }
                }
            }
        }
    }
}
