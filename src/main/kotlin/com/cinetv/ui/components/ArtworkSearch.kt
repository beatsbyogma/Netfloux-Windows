package com.cinetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinetv.desktop.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.tmdb.Tmdb
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Radius

/**
 * Correction manuelle de l'appariement TMDB.
 *
 * Quand la jaquette est fausse, c'est en général tout l'appariement qui l'est —
 * titre, année, synopsis compris. On propose donc le remplacement complet par
 * défaut, et le remplacement de la seule affiche en second choix.
 */
@Composable
fun ArtworkSearchDialog(
    initialQuery: String,
    results: List<Tmdb.Hit>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onPick: (Tmdb.Hit, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.cinetv.ui.components.SoftNavSound()
    var query by remember { mutableStateOf(initialQuery) }
    var artworkOnly by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }

    // La demande de focus échoue tant que le contenu n'est pas attaché :
    // on réessaie quelques images de suite plutôt qu'une fois.
    LaunchedEffect(Unit) {
        repeat(12) {
            if (runCatching { fieldFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Void.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.72f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(Radius.panel))
                .background(Brush.verticalGradient(listOf(Ink.Surface, Ink.Deep)))
                .padding(36.dp)
        ) {
            Text("Corriger la fiche", style = MaterialTheme.typography.headlineLarge, color = Ink.TextHi)
            Spacer(Modifier.height(6.dp))
            Text(
                "Cherchez le bon titre, puis validez le résultat qui correspond.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.TextMid
            )

            Spacer(Modifier.height(22.dp))

            // Champ de recherche
            Surface(
                onClick = { runCatching { fieldFocus.requestFocus() } },
                shape = FocusStyle.shape(Radius.control),
                colors = FocusStyle.colors(container = Ink.SurfaceHi),
                scale = FocusStyle.lift(1.0f),
                glow = FocusStyle.glow(Ink.Violet, 10.dp),
                border = FocusStyle.noBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CineIcon(R.drawable.ic_search, tint = Ink.TextMid, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.TextHi),
                        cursorBrush = SolidColor(Ink.Lilac),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
                        modifier = Modifier.weight(1f).focusRequester(fieldFocus)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton("Rechercher", accent = true) { onQueryChange(query) }
                SmallButton(
                    if (artworkOnly) "Remplacer : l'affiche seule" else "Remplacer : toute la fiche"
                ) { artworkOnly = !artworkOnly }
            }

            Spacer(Modifier.height(20.dp))

            when {
                searching -> Text("Recherche en cours…", style = MaterialTheme.typography.bodyMedium, color = Ink.TextMid)
                results.isEmpty() -> Text(
                    "Aucun résultat. Essayez un titre plus court, ou le titre original.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.TextLow
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.tmdbId }) { hit ->
                        ResultRow(hit) { onPick(hit, artworkOnly) }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ResultRow(hit: Tmdb.Hit, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        shape = FocusStyle.shape(Radius.control),
        colors = FocusStyle.colors(container = Ink.SurfaceHi.copy(alpha = 0.6f)),
        scale = FocusStyle.lift(1.015f),
        glow = FocusStyle.glow(Ink.Lilac, 12.dp),
        border = FocusStyle.noBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Poster(
                url = Tmdb.poster(hit.posterPath, "w185"),
                corner = Radius.tile,
                modifier = Modifier.width(58.dp).height(87.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CineIcon(R.drawable.ic_movie, tint = Ink.TextLow, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    hit.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (hit.year > 0) append(hit.year)
                        if (hit.rating > 0f) {
                            if (isNotEmpty()) append(" · ")
                            append("★ ${String.format("%.1f", hit.rating)}")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.Lilac
                )
                if (hit.overview.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        hit.overview,
                        style = MaterialTheme.typography.labelSmall,
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
internal fun SmallButton(label: String, accent: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = FocusStyle.shape(Radius.control),
        colors = FocusStyle.colors(
            container = if (accent) Ink.Violet.copy(alpha = 0.22f) else Ink.SurfaceHi,
            content = Ink.TextHi
        ),
        scale = FocusStyle.lift(1.03f),
        glow = FocusStyle.glow(if (accent) Ink.Lilac else Ink.Violet, 12.dp),
        border = FocusStyle.noBorder()
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Ink.TextHi,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp)
        )
    }
}
