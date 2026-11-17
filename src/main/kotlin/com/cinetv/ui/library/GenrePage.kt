package com.cinetv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cinetv.data.model.Node
import com.cinetv.data.prefs.Settings
import com.cinetv.ui.components.ContextMenu
import com.cinetv.ui.components.FileCard
import com.cinetv.ui.components.LocalLibrary
import com.cinetv.ui.components.titleActions
import com.cinetv.ui.theme.Grid
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Tiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tous les titres d'un genre, tous disques confondus : une affiche par film et une par
 * série (pas une par épisode), les mieux notés en premier.
 *
 * Le classement vient des fiches TMDB, pas des dossiers : un film rangé n'importe où
 * apparaît dans chacun de ses genres.
 */
@Composable
fun GenreScreen(
    genre: String,
    onPlay: (Node) -> Unit,
    onOpenFolderOf: (Node) -> Unit
) {
    val library = LocalLibrary.current ?: return
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    val revision by library.revision.collectAsStateWithLifecycle(initialValue = 0L)
    val titles by produceState<List<Node>?>(null, genre, revision) { value = library.titlesOfGenre(genre) }
    var sheet by remember { mutableStateOf<Node?>(null) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
    ) {
        val columns = settings.gridColumns
        val gap = settings.gridGap.size
        val usable = maxWidth - Grid.side * 2
        val cardWidth = (usable - gap * (columns - 1)) / columns
        val cardHeight = cardWidth * Tiles.posterAspect
        val list = titles
        val first = remember { FocusRequester() }

        LaunchedEffect(list?.size) {
            if (list.isNullOrEmpty()) return@LaunchedEffect
            repeat(15) {
                if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(40)
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 44.dp, bottom = 40.dp)
        ) {
            item("head") {
                Column(Modifier.padding(horizontal = Grid.side)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(HeroInfo.ruleWidth).height(1.dp).background(Ink.LogoRed))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "GENRE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
                            color = Color.White.copy(alpha = HeroInfo.overlineAlpha)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(genre, style = MaterialTheme.typography.displayMedium, color = Ink.TextHi)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            when {
                                list == null -> "Chargement…"
                                list.isEmpty() -> "Aucun titre"
                                list.size == 1 -> "1 titre"
                                else -> "${list.size} titres"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = Ink.TextLow,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                }
            }
            itemsIndexed(list.orEmpty().chunked(columns), key = { _, row -> row.first().id }) { rowIndex, row ->
                Row(
                    Modifier.padding(horizontal = Grid.side, vertical = gap / 2),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    row.forEachIndexed { index, node ->
                        FileCard(
                            node = node,
                            width = cardWidth,
                            height = cardHeight,
                            onClick = { sheet = node },
                            onLongClick = { sheet = node },
                            onFocused = { },
                            modifier = if (rowIndex == 0 && index == 0) Modifier.focusRequester(first) else Modifier
                        )
                    }
                }
            }
        }
    }

    sheet?.let { target ->
        ContextMenu(
            node = target,
            actions = titleActions(
                node = target,
                onPlay = { sheet = null; onPlay(target) },
                onRestart = { sheet = null; scope.launch { library.clearResume(target.id); onPlay(target) } },
                onOpenFolder = { sheet = null; onOpenFolderOf(target) }
            ),
            onDismiss = { sheet = null },
            onOpenRelated = { other -> sheet = other }
        )
    }
}
