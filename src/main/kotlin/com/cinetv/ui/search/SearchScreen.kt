package com.cinetv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Node
import com.cinetv.data.repo.LibraryRepository
import com.cinetv.ui.components.CineIcon
import com.cinetv.ui.components.ContextMenu
import com.cinetv.ui.components.titleActions
import com.cinetv.ui.components.FileCard
import com.cinetv.ui.components.FocusStyle
import com.cinetv.ui.theme.Grid
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Radius
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Node>>(emptyList())
    val results = _results.asStateFlow()

    private var job: Job? = null

    fun onQuery(text: String) {
        _query.value = text
        job?.cancel()
        if (text.length < 2) { _results.value = emptyList(); return }
        job = viewModelScope.launch {
            delay(220)
            _results.value = repo.search(text)
        }
    }

    private fun refresh() = viewModelScope.launch { _results.value = repo.search(_query.value) }

    fun clearResume(node: Node) = viewModelScope.launch { repo.clearResume(node.id); refresh() }

    fun restart(node: Node, then: () -> Unit) = viewModelScope.launch {
        repo.clearResume(node.id)
        then()
    }
}

private val SIDE = Grid.side

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onPlay: (Node) -> Unit,
    onOpenFolderOf: (Node) -> Unit = {}
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }
    var sheet by remember { mutableStateOf<Node?>(null) }

    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.Void)) {
        val cardWidth: Dp = 150.dp
        val gap = 16.dp
        val columns = ((maxWidth - SIDE * 2 + gap) / (cardWidth + gap)).toInt().coerceAtLeast(1)

        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = SIDE, end = SIDE, top = 48.dp, bottom = 18.dp)) {
                Text("Rechercher", style = MaterialTheme.typography.displayLarge, color = Ink.TextHi)
                Spacer(Modifier.height(18.dp))

                Surface(
                    onClick = { runCatching { fieldFocus.requestFocus() } },
                    shape = FocusStyle.shape(Radius.control),
                    colors = FocusStyle.colors(container = Ink.SurfaceHi),
                    scale = FocusStyle.lift(1.0f),
                    glow = FocusStyle.glow(Ink.Lilac, 12.dp),
                    border = FocusStyle.noBorder(),
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CineIcon(R.drawable.ic_search, tint = Ink.TextMid, modifier = Modifier.size(18.dp))
                        BasicTextField(
                            value = query,
                            onValueChange = viewModel::onQuery,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.TextHi),
                            cursorBrush = SolidColor(Ink.Lilac),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.onQuery(query) }),
                            modifier = Modifier.weight(1f).focusRequester(fieldFocus)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        query.length < 2 -> "Tapez au moins deux lettres — la recherche couvre tous vos disques"
                        results.isEmpty() -> "Aucun résultat"
                        else -> "${results.size} résultat(s)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.TextLow
                )
            }

            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                val rows = results.chunked(columns)
                items(rows.size, key = { "srow_$it" }) { rowIndex ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = SIDE, vertical = gap / 2),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        rows[rowIndex].forEach { node ->
                            FileCard(
                                node = node,
                                width = cardWidth,
                                height = cardWidth * 1.5f,
                                onClick = { sheet = node },
                                onLongClick = { sheet = node },
                                onFocused = {}
                            )
                        }
                    }
                }
                item("tail") { Spacer(Modifier.height(48.dp)) }
            }
        }

        sheet?.let { target ->
            ContextMenu(
                node = target,
                actions = titleActions(
                    node = target,
                    onPlay = { sheet = null; onPlay(target) },
                    onRestart = { sheet = null; viewModel.restart(target) { onPlay(target) } },
                    onForgetResume = { sheet = null; viewModel.clearResume(target) },
                    onOpenFolder = { sheet = null; onOpenFolderOf(target) }
                ),
                onDismiss = { sheet = null; viewModel.onQuery(query) },
                onOpenRelated = { other -> sheet = other }
            )
        }
    }
}
