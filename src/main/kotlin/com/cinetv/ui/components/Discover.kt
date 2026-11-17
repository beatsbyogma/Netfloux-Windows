package com.cinetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetv.desktop.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Suggestion
import com.cinetv.ui.theme.HomePage
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Radius
import com.cinetv.ui.theme.Sheet
import com.cinetv.ui.theme.Tiles
import kotlinx.coroutines.delay

private val MONTHS = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."
)

/** « 14 oct. 2026 » à partir de « 2026-10-14 » ; vide si la date est inconnue. */
private fun frenchDate(date: String): String {
    val parts = date.split('-')
    val month = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..12 } ?: return ""
    val day = parts.getOrNull(2)?.toIntOrNull() ?: return ""
    return "$day ${MONTHS[month - 1]} ${parts[0]}"
}

/**
 * Carte d'un titre proposé par TMDB et absent des disques : sa jaquette en hauteur
 * (ou, au choix dans les Paramètres, une carte horizontale comme le reste de l'accueil).
 * Pas de pastille Netfloux — ce n'est pas un titre de la bibliothèque : à la place,
 * « Film » ou « Série », puis l'année ou la date de sortie.
 */
@Composable
fun DiscoverCard(
    item: Suggestion,
    width: Dp,
    /** Vrai dans « Sorties récentes » : on montre le jour de sortie plutôt que l'année. */
    showDate: Boolean,
    /** Vrai : jaquette verticale. Faux : carte horizontale, [width] étant alors celle d'une carte large. */
    vertical: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dim = FocusStyle.dim(focused)
    val height = if (vertical) width * Tiles.posterAspect else width * HomePage.wideAspect
    val art = if (vertical) item.posterUrl.ifBlank { item.backdropUrl } else item.backdropUrl.ifBlank { item.posterUrl }

    Box(modifier) {
        FocusHalo(focused, RoundedCornerShape(Radius.tile))
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = FocusStyle.shape(Radius.tile),
            colors = FocusStyle.colors(container = Ink.Surface),
            scale = FocusStyle.lift(Tiles.fileScale),
            glow = FocusStyle.glow(),
            border = FocusStyle.noBorder(),
            modifier = Modifier.width(width).height(height)
        ) {
            Box(Modifier.fillMaxSize().alpha(dim)) {
                Poster(url = art, corner = 0.dp, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CineIcon(R.drawable.ic_movie, tint = Ink.TextLow, modifier = Modifier.size(26.dp))
                    }
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                (if (vertical) 0.52f else 0.30f) to Color.Transparent,
                                (if (vertical) 0.80f else 0.70f) to Ink.Void.copy(alpha = 0.52f),
                                1.00f to Ink.Void.copy(alpha = 0.95f)
                            )
                        )
                )
                // La note, discrète, en haut à droite
                if (item.rating > 0f) {
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Ink.Void.copy(alpha = 0.62f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("★", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Ink.LogoRed)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            String.format(java.util.Locale.FRANCE, "%.1f", item.rating),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = Color.White
                        )
                    }
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    val kind = if (item.isSeries) "Série" else "Film"
                    val whenOut = if (showDate) frenchDate(item.date) else item.year.takeIf { it > 0 }?.toString().orEmpty()
                    Text(
                        text = listOf(kind, whenOut).filter { it.isNotBlank() }.joinToString(" · ").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink.LogoRed,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink.TextHi,
                        maxLines = if (vertical) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Fiche d'une proposition : jaquette, résumé, bande-annonce. Le titre n'étant
 * pas sur les disques, il n'y a rien à lire ; « Pas intéressé » le retire des rangées.
 */
@Composable
fun SuggestionSheet(
    item: Suggestion,
    onTrailer: () -> Unit,
    onNotInterested: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(item.key) {
        repeat(12) {
            if (runCatching { firstItem.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.cinetv.ui.components.SoftNavSound()
        Box(
            Modifier.fillMaxSize().background(Ink.Void.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            val art = item.backdropUrl.ifBlank { item.posterUrl }
            val brush = rememberTitleBrush(art)
            val accent = rememberTitleAccent(art)
            Row(
                Modifier
                    .entrance(fromY = 14.dp)
                    .width(Sheet.width)
                    .clip(RoundedCornerShape(Sheet.radius))
                    .background(Brush.verticalGradient(listOf(Ink.Surface, Ink.Deep)))
                    .padding(horizontal = 28.dp, vertical = 24.dp),
                verticalAlignment = Alignment.Top
            ) {
                Poster(
                    url = item.posterUrl,
                    corner = Radius.tile,
                    modifier = Modifier.width(Sheet.posterWidth).height(Sheet.posterWidth * 1.5f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CineIcon(R.drawable.ic_movie, tint = Ink.TextLow, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "ABSENT DE VOS DISQUES",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        color = Ink.TextLow
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink.TextHi,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlineTag(if (item.isSeries) "Série" else "Film", tint = accent)
                        frenchDate(item.date).takeIf { it.isNotBlank() }?.let { OutlineTag(it) }
                        if (item.rating > 0f) {
                            OutlineTag(String.format(java.util.Locale.FRANCE, "%.1f", item.rating), star = true)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        item.overview.ifBlank { "Pas de résumé en français pour ce titre." },
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.TextMid,
                        maxLines = 7,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill(
                            MenuAction(R.drawable.ic_movie, "Bande-annonce") { onTrailer() },
                            firstItem, brush, accent, primary = true
                        )
                        ActionPill(
                            MenuAction(R.drawable.ic_close, "Pas intéressé") { onNotInterested() },
                            null, brush, accent
                        )
                    }
                }
            }
        }
    }
}
