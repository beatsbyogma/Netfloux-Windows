package com.cinetv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.DriveTheme
import com.cinetv.data.model.Node
import com.cinetv.data.model.Volume
import com.cinetv.ui.theme.DriveArt
import com.cinetv.ui.theme.FolderArt
import com.cinetv.ui.theme.Type
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Radius
import com.cinetv.ui.theme.ThemeSkin
import com.cinetv.ui.theme.Tiles
import com.cinetv.ui.theme.skinFor

fun iconFor(theme: DriveTheme): Int = when (theme) {
    DriveTheme.FILMS -> R.drawable.ic_movie
    DriveTheme.SERIES -> R.drawable.ic_tv
    DriveTheme.ANIMATION -> R.drawable.ic_sparkle
    DriveTheme.DOCS -> R.drawable.ic_globe
    DriveTheme.CONCERTS -> R.drawable.ic_music
    DriveTheme.JEUNESSE -> R.drawable.ic_child
    DriveTheme.AUTRE -> R.drawable.ic_disc
}

/**
 * Pastille de type, en haut à gauche des cartes : un petit rond blanc,
 * le picto plein en noir dedans.
 */
@Composable
fun TypeBadge(icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Tiles.badge)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        CineIcon(
            // La télé au trait devient pleine, pour rester lisible à cette taille
            if (icon == R.drawable.ic_tv) R.drawable.ic_tv_solid else icon,
            tint = Ink.Void,
            modifier = Modifier.size(Tiles.badgeGlyph)
        )
    }
}

// ------------------------------------------------- carte disque (verticale)

@Composable
fun DriveCard(
    volume: Volume,
    posters: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    width: Dp = Tiles.driveWidth,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val skin = skinFor(volume.theme)
    val dim = FocusStyle.dim(focused, resting = Tiles.restingDimDrive)

    Column(modifier.width(width)) {
    SpaceGauge(volume, skin, Modifier.height(DriveArt.gaugeHeight))
    Box {
        // La lueur reprend la couleur du nom du disque
        FocusHalo(focused, RoundedCornerShape(Radius.tile), color = skin.primary)
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = FocusStyle.shape(Radius.tile),
            colors = FocusStyle.colors(container = Ink.Surface),
            scale = FocusStyle.lift(Tiles.driveScale),
            glow = FocusStyle.glow(),
            border = FocusStyle.noBorder(),
            modifier = Modifier
                .width(width)
                .height(width * Tiles.driveAspect)
                .dpadPress(onClick = onClick, onLongClick = onLongClick)
        ) {
            Box(Modifier.fillMaxSize().alpha(dim)) {

                // Une seule jaquette, qui glisse en continu vers la suivante
                PosterCarousel(
                    posters = posters,
                    animated = volume.isConnected,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (volume.isConnected) 1f else 0.40f)
                )

                // Voile noir vers transparent, plus appuyé que sur les dossiers
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                *DriveArt.scrim.map { (stop, alpha) ->
                                    stop to Ink.Void.copy(alpha = alpha)
                                }.toTypedArray()
                            )
                        )
                )

                // Filet aux couleurs du disque, au pied de la carte
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Brush.horizontalGradient(listOf(skin.primary, skin.secondary)))
                )

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(13.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        TypeBadge(iconFor(volume.theme))
                        StatusDot(volume.isConnected)
                    }

                    Column {
                        // « NETFLOUX » en blanc, le reste du nom dans le dégradé du disque
                        FitText(
                            text = driveTitle(volume.label, skin),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = DriveArt.title,
                                lineHeight = DriveArt.title * Type.tightLine
                            ),
                            maxLines = 2,
                            minScale = 0.6f
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append("${volume.videoCount} vidéos")
                                if (volume.folderCount > 0) append(" · ${volume.folderCount} dossiers")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.TextMid
                        )
                        if (!volume.isConnected) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                if (volume.id.startsWith("smb:")) "HORS LIGNE" else "DÉBRANCHÉ",
                                style = MaterialTheme.typography.labelSmall,
                                color = Ink.Warn
                            )
                        }
                    }
                }

                // Disque débranché ou partage hors ligne : un voile noir sur toute la carte
                if (!volume.isConnected) {
                    Box(Modifier.matchParentSize().background(Ink.Void.copy(alpha = DriveArt.offlineVeil)))
                }
            }
        }
    }
    }
}

/** Carte « ajouter », au bout de la rangée Réseau. */
@Composable
fun AddShareCard(
    onClick: () -> Unit,
    width: Dp = Tiles.driveWidth,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dim = FocusStyle.dim(focused, resting = Tiles.restingDimDrive)

    Box(modifier.padding(top = DriveArt.gaugeHeight)) {
        FocusHalo(focused, RoundedCornerShape(Radius.tile))
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = FocusStyle.shape(Radius.tile),
            colors = FocusStyle.colors(container = Ink.Surface.copy(alpha = 0.55f)),
            scale = FocusStyle.lift(Tiles.driveScale),
            glow = FocusStyle.glow(),
            border = FocusStyle.noBorder(),
            modifier = Modifier
                .width(width)
                .height(width * Tiles.driveAspect)
        ) {
            Column(
                Modifier.fillMaxSize().alpha(dim).padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CineIcon(
                    R.drawable.ic_network,
                    tint = if (focused) Ink.Lilac else Ink.TextLow,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("AJOUTER UN DOSSIER", style = MaterialTheme.typography.labelSmall, color = Ink.TextHi)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Partagé par un PC ou un NAS",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.TextLow
                )
            }
        }
    }
}

/**
 * Jauge d'espace disque, posée au-dessus de la carte : la part occupée en
 * couleur, et l'espace encore libre écrit en clair.
 */
@Composable
private fun SpaceGauge(volume: Volume, skin: ThemeSkin, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Top) {
        if (volume.totalBytes <= 0) return@Column
        val freeShare = (volume.freeBytes.toFloat() / volume.totalBytes).coerceIn(0f, 1f)
        val low = freeShare < DriveArt.lowSpace
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatBytes(volume.freeBytes)} DISPO",
                style = MaterialTheme.typography.labelSmall,
                color = if (low) Ink.Warn else Ink.TextMid,
                maxLines = 1
            )
            Text(
                text = formatBytes(volume.totalBytes),
                style = MaterialTheme.typography.labelSmall,
                color = Ink.TextLow,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Ink.Line)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(1f - freeShare)
                    .alpha(if (volume.isConnected) 1f else 0.5f)
                    .background(
                        if (low) Brush.horizontalGradient(listOf(Ink.Warn, Ink.Warn))
                        else Brush.horizontalGradient(listOf(skin.primary, skin.secondary))
                    )
            )
        }
    }
}

/** 512 Go, 1,8 To… en unités décimales, comme sur l'étiquette des disques. */
private fun formatBytes(bytes: Long): String {
    val go = bytes / 1_000_000_000.0
    return when {
        go >= 1000 -> String.format(java.util.Locale.FRANCE, "%.1f To", go / 1000)
        go >= 10 -> String.format(java.util.Locale.FRANCE, "%.0f Go", go)
        go >= 1 -> String.format(java.util.Locale.FRANCE, "%.1f Go", go)
        else -> String.format(java.util.Locale.FRANCE, "%.0f Mo", bytes / 1_000_000.0)
    }
}

private fun driveTitle(label: String, skin: ThemeSkin): AnnotatedString = buildAnnotatedString {
    val text = label.uppercase()
    val brand = "NETFLOUX"
    val tinted = SpanStyle(brush = Brush.horizontalGradient(listOf(skin.primary, skin.secondary)))
    val white = SpanStyle(color = Color.White)
    var cursor = 0
    while (cursor < text.length) {
        val hit = text.indexOf(brand, cursor)
        if (hit < 0) {
            withStyle(tinted) { append(text.substring(cursor)) }
            break
        }
        if (hit > cursor) withStyle(tinted) { append(text.substring(cursor, hit)) }
        withStyle(white) { append(brand) }
        cursor = hit + brand.length
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(if (connected) Ink.Online else Ink.Offline)
    )
}

@Composable
fun ScanDriveCard(
    busy: Boolean,
    firstRun: Boolean,
    onClick: () -> Unit,
    width: Dp = Tiles.driveWidth,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dim = FocusStyle.dim(focused, resting = Tiles.restingDimDrive)

    val spin = rememberInfiniteTransition(label = "scan")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "scanAngle"
    )

    Box(modifier.padding(top = DriveArt.gaugeHeight)) {
        FocusHalo(focused, RoundedCornerShape(Radius.tile))
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = FocusStyle.shape(Radius.tile),
            colors = FocusStyle.colors(container = Ink.Surface.copy(alpha = 0.55f)),
            scale = FocusStyle.lift(Tiles.driveScale),
            glow = FocusStyle.glow(),
            border = FocusStyle.noBorder(),
            modifier = Modifier
                .width(width)
                .height(width * Tiles.driveAspect)
        ) {
            Column(
                Modifier.fillMaxSize().alpha(dim).padding(14.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CineIcon(
                    if (busy) R.drawable.ic_disc else R.drawable.ic_add,
                    tint = if (focused || busy) Ink.Lilac else Ink.TextLow,
                    modifier = Modifier
                        .size(26.dp)
                        .then(if (busy) Modifier.rotate(angle) else Modifier)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        busy -> "ANALYSE EN COURS…"
                        firstRun -> "ANALYSER LES DISQUES"
                        else -> "RELANCER L'ANALYSE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.TextHi
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (firstRun) "Branchez un disque, puis validez"
                    else "Pour prendre en compte les ajouts",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.TextLow
                )
            }
        }
    }
}

// ------------------------------------------------ carte dossier (verticale)

/**
 * Deux habillages, selon le disque.
 *
 * Sur un disque de séries, le dossier EST la série : son affiche remplit la
 * carte et le nom se pose dessous. Partout ailleurs, une seule jaquette tient
 * le fond et glisse vers le haut pour laisser monter la suivante, le nom
 * s'inscrivant dans le voile noir du bas.
 */
@Composable
fun FolderCard(
    node: Node,
    posters: List<String>,
    accent: Color,
    width: Dp,
    height: Dp,
    /** Sur un disque de séries, un dossier est une série : on montre son affiche. */
    singlePoster: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onFocused: () -> Unit = {},
    /** Épisodes vus et total, pour un dossier de série. */
    progress: Pair<Int, Int>? = null,
    modifier: Modifier = Modifier,
    /** Nombre de saisons, pour un dossier de série : c'est la seule mention de la carte. */
    seasons: Int? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dim = FocusStyle.dim(focused)

    LaunchedEffect(focused) { if (focused) onFocused() }

    // Une jaquette choisie à la main passe devant celles des fichiers
    val chosen = node.posterUrl.takeIf { it.isNotBlank() }
    val art = remember(chosen, posters) {
        (listOfNotNull(chosen) + posters).distinct()
    }

    Column(modifier.width(width)) {
        Box {
            FocusHalo(focused, RoundedCornerShape(Radius.tile))
            Surface(
                onClick = onClick,
                interactionSource = interaction,
                shape = FocusStyle.shape(Radius.tile),
                colors = FocusStyle.colors(container = Ink.Surface),
                scale = FocusStyle.lift(Tiles.folderScale),
                glow = FocusStyle.glow(),
                border = FocusStyle.noBorder(),
                modifier = Modifier
                    .width(width)
                    .height(height)
                    .alpha(if (node.isAvailable) 1f else 0.45f)
                    .dpadPress(onClick = onClick, onLongClick = onLongClick)
            ) {
                Box(Modifier.fillMaxSize().alpha(dim)) {

                    if (singlePoster) {
                        // ── Séries : l'affiche de la série, telle quelle
                        if (art.isNotEmpty()) {
                            Poster(url = art.first(), corner = 0.dp, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(Modifier.fillMaxSize().background(Ink.SurfaceHi))
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .fillMaxHeight(FolderArt.posterScrimHeight)
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        0.55f to Ink.Void.copy(alpha = FolderArt.posterScrim * 0.62f),
                                        1f to Ink.Void.copy(alpha = FolderArt.posterScrim)
                                    )
                                )
                        )
                        if (progress != null && progress.second > 0 && progress.first > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.White.copy(alpha = 0.14f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progress.first.toFloat() / progress.second)
                                        .background(Ink.Accent)
                                )
                            }
                        }
                        Column(
                            Modifier.fillMaxSize().padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            TypeBadge(if (node.isAvailable) R.drawable.ic_tv else R.drawable.ic_folder_off)
                            val seen = progress?.first ?: 0
                            val total = progress?.second ?: 0
                            Text(
                                text = when {
                                    seasons != null -> if (seasons > 1) "$seasons SAISONS" else "1 SAISON"
                                    total == 0 -> "${node.childCount} ÉPISODES"
                                    seen >= total -> "$total ÉPISODES · TOUT VU"
                                    seen > 0 -> "$seen/$total VUS"
                                    else -> "$total ÉPISODES"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (seen > 0 && seasons == null) Ink.Lilac else Ink.TextMid
                            )
                        }
                    } else {
                        // ── Films : une jaquette qui glisse pour en révéler une autre
                        PosterCarousel(
                            posters = art,
                            animated = node.isAvailable,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        *FolderArt.scrim.map { (stop, alpha) ->
                                            stop to Ink.Void.copy(alpha = alpha)
                                        }.toTypedArray()
                                    )
                                )
                        )
                        Column(
                            Modifier.fillMaxSize().padding(11.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            TypeBadge(if (node.isAvailable) R.drawable.ic_folder else R.drawable.ic_folder_off)
                            Column {
                                FitText(
                                    text = node.name.uppercase(),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontSize = FolderArt.title,
                                        lineHeight = FolderArt.title * Type.tightLine
                                    ),
                                    color = Ink.TextHi,
                                    maxLines = 2,
                                    minScale = 0.6f
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "${node.childCount} éléments",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ink.TextMid
                                )
                            }
                        }
                    }
                }
            }
        }

        // Le nom sous la carte n'a de sens que pour les séries : ailleurs il est dedans
        if (singlePoster) {
            Spacer(Modifier.height(Tiles.titleGap))
            Text(
                text = node.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) Ink.TextHi else Ink.TextMid,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ----------------------------------------------------------- carte fichier

@Composable
fun FileCard(
    node: Node,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val dim = FocusStyle.dim(focused)

    LaunchedEffect(focused) { if (focused) onFocused() }

    Column(modifier.width(width)) {
        Box {
            FocusHalo(focused, RoundedCornerShape(Radius.tile))
            Surface(
                onClick = onClick,
                interactionSource = interaction,
                shape = FocusStyle.shape(Radius.tile),
                colors = FocusStyle.colors(),
                scale = FocusStyle.lift(Tiles.fileScale),
                glow = FocusStyle.glow(),
                border = FocusStyle.noBorder(),
                modifier = Modifier
                    .width(width)
                    .height(height)
                    .dpadPress(onClick = onClick, onLongClick = onLongClick)
            ) {
                Box(Modifier.fillMaxSize().alpha(dim)) {
                    Poster(url = node.posterUrl, corner = 0.dp, modifier = Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CineIcon(R.drawable.ic_movie, tint = Ink.TextLow, modifier = Modifier.size(22.dp))
                        }
                    }

                    if (!node.isAvailable) {
                        Box(
                            Modifier.fillMaxSize().background(Ink.Void.copy(alpha = 0.78f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CineIcon(
                                    R.drawable.ic_cloud_off,
                                    tint = Ink.Warn,
                                    contentDescription = "Disque débranché",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    "DÉBRANCHÉ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ink.Warn
                                )
                            }
                        }
                    }

                    if (node.progressFraction > 0.01f && node.isAvailable) {
                        Box(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(Color.Black.copy(alpha = 0.55f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(node.progressFraction)
                                    .background(Ink.Accent)
                            )
                        }
                    }

                    // Définition du fichier, en haut à gauche ; « VU », en haut à droite
                    QualityTag(node.quality, Modifier.align(Alignment.TopStart).padding(Tiles.tagInset))
                    if (node.isWatched) {
                        TinyTag(
                            "VU",
                            Modifier.align(Alignment.TopEnd).padding(Tiles.tagInset),
                            container = Ink.Good,
                            content = Ink.Void
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Tiles.titleGap))
        Text(
            text = node.displayTitle,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) Ink.TextHi else Ink.TextMid,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}


/**
 * Toute petite cartouche posée sur une vignette : la définition du fichier, « VU »…
 * Fond sombre translucide par défaut, pour rester lisible sur n'importe quelle jaquette.
 */
@Composable
fun TinyTag(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = Ink.Void.copy(alpha = Tiles.tagBackdrop),
    content: Color = Color.White
) {
    Box(
        modifier
            .clip(RoundedCornerShape(Tiles.tagRadius))
            .background(container)
            .padding(horizontal = 4.dp, vertical = 1.5.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = Tiles.tagText,
                lineHeight = Tiles.tagText * 1.25f,
                letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            color = content,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** La définition d'une vidéo (« 4K », « 1080p »…) ; rien tant qu'elle n'est pas connue. */
@Composable
fun QualityTag(quality: Int, modifier: Modifier = Modifier) {
    val label = com.cinetv.data.model.qualityLabel(quality)
    if (label.isNotBlank()) TinyTag(label, modifier)
}
