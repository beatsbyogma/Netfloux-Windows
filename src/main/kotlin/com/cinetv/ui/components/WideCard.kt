package com.cinetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Node
import com.cinetv.ui.brand.NetflouxBadge
import com.cinetv.ui.theme.HomePage
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Radius
import com.cinetv.ui.theme.Tiles

/**
 * Carte paysage des rangées d'accueil.
 *
 * Format 16/9, visuel plein cadre, pastille de marque en haut à gauche et
 * titre posé en bas sur un voile dégradé — la carte de la maquette.
 */
@Composable
fun WideCard(
    node: Node,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    onFocused: () -> Unit = {},
    label: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dim = FocusStyle.dim(focused)
    val height = width * HomePage.wideAspect

    LaunchedEffect(focused) { if (focused) onFocused() }

    val art = node.backdropUrl.ifBlank { node.posterUrl }

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
            modifier = Modifier
                .width(width)
                .height(height)
                .dpadPress(onClick = onClick, onLongClick = onLongClick)
        ) {
            Box(Modifier.fillMaxSize().alpha(dim)) {

                Poster(url = art, corner = 0.dp, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CineIcon(R.drawable.ic_movie, tint = Ink.TextLow, modifier = Modifier.size(26.dp))
                    }
                }

                // Voile bas : le titre doit rester lisible quelle que soit l'image
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.30f to Color.Transparent,
                                0.70f to Ink.Void.copy(alpha = 0.52f),
                                1.00f to Ink.Void.copy(alpha = 0.95f)
                            )
                        )
                )

                Row(
                    Modifier.align(Alignment.TopStart).padding(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    NetflouxBadge(size = 16.dp)
                    QualityTag(node.quality)
                }

                if (!node.isAvailable) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(7.dp)
                    ) {
                        CineIcon(
                            R.drawable.ic_cloud_off,
                            tint = Ink.Warn,
                            contentDescription = "Disque débranché",
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 9.dp, vertical = 8.dp)
                ) {
                    if (label != null) {
                        Text(
                            text = label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.LogoRed,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(
                        text = node.displayTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = Ink.TextHi,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (node.progressFraction > 0.01f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.16f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(node.progressFraction)
                                .background(Ink.Brand)
                        )
                    }
                }
            }
        }
    }
}


/**
 * Carte d'un genre, sur l'accueil : une image du genre, son nom en grand, le nombre de titres.
 */
@Composable
fun GenreCard(
    shelf: com.cinetv.data.model.GenreShelf,
    width: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dim = FocusStyle.dim(focused)
    val height = width * HomePage.wideAspect

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
                Poster(url = shelf.backdropUrl, corner = 0.dp, modifier = Modifier.fillMaxSize())
                // Un voile uniforme : c'est le nom du genre qu'on lit, l'image ne fait que l'ambiance
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Ink.Void.copy(alpha = 0.50f),
                                1f to Ink.Void.copy(alpha = 0.82f)
                            )
                        )
                )
                androidx.compose.foundation.layout.Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.tv.material3.Text(
                        shelf.label.uppercase(),
                        style = androidx.tv.material3.MaterialTheme.typography.titleMedium.copy(
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.4f, androidx.compose.ui.unit.TextUnitType.Sp)
                        ),
                        color = Color.White,
                        maxLines = 1
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.height(3.dp))
                    androidx.tv.material3.Text(
                        if (shelf.count > 1) "${shelf.count} titres" else "1 titre",
                        style = androidx.tv.material3.MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.62f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
