package com.cinetv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Node
import com.cinetv.ui.theme.Hero
import com.cinetv.ui.theme.GenreType
import com.cinetv.ui.home.GlassTag
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.data.model.logoIsForeign
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion
import com.cinetv.ui.theme.Radius
import com.cinetv.ui.theme.TitleArt

/**
 * Bandeau de tête, fixe.
 *
 * La version précédente se repliait au défilement, ce qui créait une boucle :
 * la bannière rétrécissait, le contenu remontait, le focus se décalait, la liste
 * se recalait, la bannière se redéployait — l'écran oscillait sans fin.
 * Ici la hauteur ne bouge jamais ; seul le contenu change avec la sélection.
 */
@Composable
fun HeroPanel(
    node: Node?,
    breadcrumb: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /** Mention au-dessus du titre, quand la bannière pioche au hasard dans un dossier. */
    caption: String? = null
) {
    // clipToBounds : sans lui, le lent zoom de l'image débordait du bandeau
    // et laissait apparaître une bande non voilée sous le dégradé.
    Box(modifier.fillMaxWidth().clipToBounds()) {

        // Visuel de fond : occupe toute la largeur, le dégradé se charge
        // de rendre la moitié gauche lisible
        Crossfade(
            targetState = node?.backdropUrl?.takeIf { it.isNotBlank() }
                ?: node?.posterUrl?.takeIf { it.isNotBlank() }
                ?: "",
            animationSpec = tween(Motion.heroCrossfadeMs),
            label = "heroArt"
        ) { url ->
            if (url.isNotBlank()) {
                KenBurnsImage(url, Modifier.fillMaxSize(), verticalBias = Hero.artBias)
            } else {
                Box(Modifier.fillMaxSize())
            }
        }

        // Fondus : vers la gauche pour le texte, vers le bas pour rejoindre la grille
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        *Hero.scrimLeft.map { (stop, alpha) ->
                            stop to Ink.Void.copy(alpha = alpha)
                        }.toTypedArray()
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        *Hero.scrimVertical.map { (stop, alpha) ->
                            stop to Ink.Void.copy(alpha = alpha)
                        }.toTypedArray()
                    )
                )
        )

        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(Hero.textWidthFraction)
                .padding(start = 56.dp, top = 24.dp, bottom = 14.dp, end = 20.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Seul le nom du disque reste fixé en haut…
            breadcrumb()

            // …le reste se centre dans la hauteur qui reste
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Crossfade(
                targetState = node,
                animationSpec = tween(Motion.heroTextMs),
                label = "heroText"
            ) { item ->
                if (item == null) {
                    Text(
                        "Sélectionnez un titre",
                        style = MaterialTheme.typography.displayMedium,
                        color = Ink.TextLow
                    )
                } else {
                    // De haut en bas : d'où vient le titre, le titre (ou son logo),
                    // l'essentiel en pastilles, puis le résumé — rien d'autre.
                    Column {
                        if (caption != null) {
                            Text(
                                caption.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
                                color = Color.White.copy(alpha = HeroInfo.creditLabelAlpha)
                            )
                            Spacer(Modifier.height(Hero.gap))
                        }
                        val face = GenreType.faceFor(item.genres)
                        val base = MaterialTheme.typography.displayMedium
                        val titleSize = base.fontSize * face.scale * Hero.titleScale
                        val titleBrush = rememberTitleBrush(item.backdropUrl.ifBlank { item.posterUrl })
                        TitleLogo(
                            url = item.logoUrl,
                            maxWidth = TitleArt.browseWidth,
                            maxHeight = TitleArt.browseHeight
                        ) {
                            FitText(
                                text = if (face.uppercase) item.displayTitle.uppercase() else item.displayTitle,
                                style = base.copy(
                                    fontFamily = face.family,
                                    fontSize = titleSize,
                                    lineHeight = titleSize * 1.08f,
                                    letterSpacing = face.spacing.sp,
                                    brush = titleBrush
                                ),
                                maxLines = 2
                            )
                        }
                        // Sous un logo (souvent en version originale) : le titre français, en toutes lettres
                        if (item.logoIsForeign) {
                            Spacer(Modifier.height(HeroInfo.gapSmall))
                            Text(
                                text = item.displayTitle,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = com.cinetv.ui.theme.Fonts.Display,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    fontSize = HeroInfo.writtenTitleSize * 0.9f,
                                    letterSpacing = 0.3.sp
                                ),
                                color = Color.White.copy(alpha = HeroInfo.writtenTitleAlpha),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(Hero.gap))
                        MetaRow(item)
                        if (item.synopsis.isNotBlank()) {
                            Spacer(Modifier.height(Hero.gap))
                            Text(
                                text = item.synopsis,
                                style = MaterialTheme.typography.labelLarge.let {
                                    val size = it.fontSize * HeroInfo.synopsisScale
                                    it.copy(fontSize = size, lineHeight = size * HeroInfo.synopsisLine)
                                },
                                color = Color.White.copy(alpha = HeroInfo.synopsisAlpha),
                                maxLines = Hero.synopsisLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun MetaRow(item: Node) {
    // Les mêmes pastilles au liseré fin que sur la bannière de l'accueil
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (!item.isAvailable) GlassTag("Débranché", tint = Ink.Warn)
        // Définition du fichier — pour un dossier ou une série, la meilleure disponible
        com.cinetv.data.model.qualityLabel(item.quality).takeIf { it.isNotBlank() }?.let { GlassTag(it) }
        if (item.year > 0) GlassTag(item.year.toString())
        if (item.durationMs > 0) GlassTag(formatDuration(item.durationMs))
        genreLabel(item.genres)?.let { GlassTag(it) }
        if (item.progressFraction > 0.01f) {
            GlassTag("Repris à ${(item.progressFraction * 100).toInt()} %", tint = Ink.Lilac)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "$m min"
}
