package com.cinetv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.LibraryStats
import com.cinetv.data.model.Node
import com.cinetv.data.tmdb.Tmdb
import com.cinetv.ui.components.ArtworkSearchDialog
import com.cinetv.ui.components.CineIcon
import com.cinetv.ui.components.FocusStyle
import com.cinetv.ui.components.LocalLibrary
import com.cinetv.ui.components.entrance
import com.cinetv.ui.components.genreLabel
import com.cinetv.ui.theme.Grid
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion
import com.cinetv.ui.theme.Radius
import com.cinetv.util.FileNameParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ═════════════════════════════════════════════ FICHIERS NON RECONNUS

/**
 * Toutes les vidéos que TMDB n'a pas identifiées, sur une seule page : un clic
 * ouvre la recherche de fiche, et le fichier quitte la liste une fois corrigé.
 */
@Composable
fun UnmatchedScreen() {
    val library = LocalLibrary.current ?: return
    val scope = rememberCoroutineScope()

    var refresh by remember { mutableIntStateOf(0) }
    val files by produceState<List<Node>?>(null, refresh) { value = library.unmatched() }
    val disks by produceState(emptyMap<String, String>()) {
        value = library.volumes().associate { it.id to it.label }
    }

    // La recherche de fiche pour le fichier choisi
    var target by remember { mutableStateOf<Node?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Tmdb.Hit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun search(text: String) {
        query = text
        searching = true
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(350)
            results = library.searchTmdb(text)
            searching = false
        }
    }

    val first = remember { FocusRequester() }
    LaunchedEffect(files?.size) {
        if (files.isNullOrEmpty() || target != null) return@LaunchedEffect
        repeat(12) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    Box(Modifier.fillMaxSize().background(Ink.Void)) {
        LazyColumn(contentPadding = PaddingValues(top = 44.dp, bottom = 48.dp)) {
            item("title") {
                Column(Modifier.padding(horizontal = Grid.side)) {
                    Text("Fichiers non reconnus", style = MaterialTheme.typography.displayMedium, color = Ink.TextHi)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            files == null -> "Recherche…"
                            files!!.isEmpty() -> "Tout est reconnu : rien à corriger"
                            else -> "${files!!.size} fichier(s) sans fiche — OK pour chercher la bonne"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink.TextMid
                    )
                    Spacer(Modifier.height(18.dp))
                }
            }
            items(files.orEmpty(), key = { it.id }) { node ->
                val index = files.orEmpty().indexOf(node)
                Surface(
                    onClick = {
                        target = node
                        results = emptyList()
                        search(FileNameParser.parse(node.name).title)
                    },
                    shape = FocusStyle.shape(Radius.control),
                    colors = FocusStyle.colors(container = Color.Transparent, content = Ink.TextHi),
                    scale = FocusStyle.lift(1.01f),
                    glow = FocusStyle.glow(Ink.Violet, 12.dp),
                    border = FocusStyle.noBorder(),
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(horizontal = Grid.side, vertical = 2.dp)
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CineIcon(R.drawable.ic_image, tint = Ink.Lilac, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                node.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink.TextHi,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                listOfNotNull(
                                    disks[node.volumeId],
                                    node.relativePath.substringBeforeLast('/', "").takeIf { it.isNotBlank() }
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Ink.TextLow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    target?.let { node ->
        ArtworkSearchDialog(
            initialQuery = query,
            results = results,
            searching = searching,
            onQueryChange = { search(it) },
            onPick = { hit, artworkOnly ->
                scope.launch {
                    library.relink(node.id, hit, artworkOnly)
                    target = null
                    refresh++
                }
            },
            onDismiss = { target = null }
        )
    }
}

// ══════════════════════════════════════════════════════ STATISTIQUES

/** Ce que vous avez regardé, en quelques chiffres. Tout tient sur un écran. */
@Composable
fun StatsScreen() {
    val library = LocalLibrary.current ?: return
    val stats by produceState<LibraryStats?>(null) { value = library.stats() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
            .padding(horizontal = Grid.side, vertical = 40.dp)
    ) {
        Text("Statistiques", style = MaterialTheme.typography.displayMedium, color = Ink.TextHi)
        Spacer(Modifier.height(22.dp))

        val data = stats
        if (data == null) {
            Text("Calcul…", style = MaterialTheme.typography.labelLarge, color = Ink.TextMid)
            return@Column
        }

        // ── Les grands chiffres
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val hours = data.watchedMs / 3_600_000
            Figure(
                value = if (hours >= 1) "$hours h" else "${data.watchedMs / 60_000} min",
                label = "regardées", modifier = Modifier.weight(1f).entrance(fromY = Motion.rowRise)
            )
            Figure("${data.watched}", "titres vus", Modifier.weight(1f).entrance(fromY = Motion.rowRise, delayMs = 60))
            Figure("${data.inProgress}", "en cours", Modifier.weight(1f).entrance(fromY = Motion.rowRise, delayMs = 120))
            Figure(
                value = "${data.files}",
                label = "vidéos · " + sizeLabel(data.totalBytes),
                modifier = Modifier.weight(1f).entrance(fromY = Motion.rowRise, delayMs = 180)
            )
        }

        Spacer(Modifier.height(26.dp))

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(34.dp)) {
            // ── Mois par mois
            Column(Modifier.weight(1f)) {
                Heading("Titres regardés, mois par mois")
                val top = data.months.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.months.forEach { (month, count) ->
                        Column(
                            Modifier.weight(1f).fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Text("$count", style = MaterialTheme.typography.labelLarge, color = Ink.TextHi)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight((count.toFloat() / top * 0.72f).coerceAtLeast(0.012f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(Brush.verticalGradient(listOf(Ink.LogoRed, Ink.Violet)))
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(monthLabel(month), style = MaterialTheme.typography.labelSmall, color = Ink.TextLow)
                        }
                    }
                }
            }

            // ── Genres et réalisateurs
            Column(Modifier.weight(1f)) {
                Heading("Genres les plus regardés")
                if (data.genres.isEmpty()) Quiet("Regardez quelques titres : vos genres apparaîtront ici")
                val most = data.genres.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                data.genres.forEach { (genre, count) ->
                    Bar(genreLabel(genre.toString()) ?: "Autre", count, count.toFloat() / most)
                }
                if (data.directors.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Heading("Réalisateurs les plus vus")
                    Text(
                        data.directors.joinToString("   ·   ") { (name, count) -> "$name ($count)" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.TextMid,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.panel))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(value, style = MaterialTheme.typography.displayMedium, color = Ink.TextHi, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.TextLow, maxLines = 1)
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.Lilac,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink.TextLow)
}

/** Une barre horizontale : le nom à gauche, la barre, le nombre au bout. */
@Composable
private fun Bar(label: String, count: Int, share: Float) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Ink.TextHi,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(118.dp)
        )
        Box(Modifier.weight(1f).height(7.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(share.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Ink.Violet)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("$count", style = MaterialTheme.typography.labelLarge, color = Ink.TextMid)
    }
}

private fun sizeLabel(bytes: Long): String = when {
    bytes >= 1_000_000_000_000L -> String.format("%.1f To", bytes / 1e12)
    else -> "${bytes / 1_000_000_000L} Go"
}

private val MONTHS = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")

/** « 2026-11 » → « nov. » */
private fun monthLabel(key: String): String =
    key.substringAfter('-').toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: key
