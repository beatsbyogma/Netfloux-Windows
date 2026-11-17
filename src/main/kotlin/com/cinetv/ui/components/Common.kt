package com.cinetv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.foundation.text.BasicText
import androidx.tv.material3.LocalContentColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion

/**
 * Faux pendant l'écran d'ouverture : les animations d'ambiance de l'accueil
 * (zoom de bannière, jaquettes qui défilent) attendent, pour ne pas disputer
 * la puce graphique à l'animation du logo.
 */
val LocalAmbientMotion = compositionLocalOf { true }

/**
 * La bibliothèque, à portée de tout composant : la fiche d'un titre s'en sert
 * pour lire ses pistes, trouver sa saga, renommer ou supprimer.
 */
val LocalLibrary = staticCompositionLocalOf<com.cinetv.data.repo.LibraryRepository?> { null }

/**
 * Photographie l'écran tel qu'il est affiché. Les fenêtres surgissantes s'en
 * servent pour poser derrière elles la page floutée.
 */
val LocalScreenShot = staticCompositionLocalOf<(suspend () -> androidx.compose.ui.graphics.ImageBitmap?)?> { null }

/** Icône du jeu maison, dessinée en vectoriel dans res/drawable. */
@Composable
fun CineIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = Ink.TextHi,
    contentDescription: String? = null
) {
    androidx.compose.foundation.Image(
        painter = painterResource(id),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(24.dp)
    )
}

private val GENRE_LABELS = mapOf(
    28 to "Action", 12 to "Aventure", 16 to "Animation", 35 to "Comédie", 80 to "Crime",
    99 to "Documentaire", 18 to "Drame", 10751 to "Familial", 14 to "Fantastique", 36 to "Histoire",
    27 to "Horreur", 10402 to "Musique", 9648 to "Mystère", 10749 to "Romance",
    878 to "Science-fiction", 53 to "Thriller", 10752 to "Guerre", 37 to "Western",
    10759 to "Action & aventure", 10762 to "Jeunesse", 10765 to "Science-fiction",
    10768 to "Guerre & politique", 10764 to "Téléréalité", 10766 to "Feuilleton"
)

/** Genres voisins rangés sous un même nom, pour le classement par genre de l'accueil. */
private val GENRE_SHELF = mapOf(10759 to "Action", 10765 to "Science-fiction", 10768 to "Guerre", 10762 to "Familial")

/** Tous les genres d'une fiche, en clair et sans doublon. [genres] : identifiants TMDB séparés par des virgules. */
fun genreShelves(genres: String): List<String> =
    genres.split(',').mapNotNull { id ->
        id.trim().toIntOrNull()?.let { GENRE_SHELF[it] ?: GENRE_LABELS[it] }
    }.distinct()

/** Le premier genre d'une fiche, en clair. [genres] : identifiants TMDB séparés par des virgules. */
fun genreLabel(genres: String): String? =
    genres.split(',').firstNotNullOfOrNull { id -> id.trim().toIntOrNull()?.let { GENRE_LABELS[it] } }

/** Agrandissement fluide quand l'élément prend le focus télécommande. */
@Composable
fun rememberFocusScale(focused: Boolean, max: Float = 1.08f): Float {
    val scale by animateFloatAsState(
        targetValue = if (focused) max else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
        label = "focusScale"
    )
    return scale
}

/** Image avec fondu d'apparition et repli propre si l'URL est vide. */
@Composable
fun Poster(
    url: String,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Ink.SurfaceHi)
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(260)
                    .build(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            fallback()
        }
    }
}

/** Effet Ken Burns : lent travelling avant/arrière sur une image de fond. */
@Composable
fun KenBurnsImage(
    url: String,
    modifier: Modifier = Modifier,
    durationMs: Int = Motion.kenBurnsMs,
    /** Cadrage vertical : -1 garde le haut de l'image, 0 le centre, 1 le bas. */
    verticalBias: Float = 0f
) {
    val zoom = if (LocalAmbientMotion.current) {
        val transition = rememberInfiniteTransition(label = "kenburns")
        val animated by transition.animateFloat(
            initialValue = Motion.kenBurnsFrom,
            targetValue = Motion.kenBurnsTo,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "zoom"
        )
        animated
    } else {
        Motion.kenBurnsFrom
    }
    Box(modifier.clipToBounds()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(500)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.BiasAlignment(0f, verticalBias),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    // Le zoom part du même point que le cadrage, pour ne pas rogner les têtes
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, (verticalBias + 1f) / 2f)
                }
        )
    }
}

/** Petite pastille d'information (année, note, nombre de fichiers…). */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Ink.TextMid,
    background: Color = Color.White.copy(alpha = 0.08f)
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/**
 * Texte qui s'ajuste : si le titre ne tient pas dans la place donnée, sa
 * taille diminue par petits pas jusqu'à ce qu'il rentre en entier — jamais
 * de titre coupé, jamais de mot cassé en deux. En dernier recours seulement,
 * arrivé à la taille plancher, des points de suspension.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
    minScale: Float = 0.5f
) = FitText(AnnotatedString(text), style, modifier, color, maxLines, minScale)

/** Même chose pour un texte à plusieurs couleurs. */
@Composable
fun FitText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 2,
    minScale: Float = 0.5f
) {
    var scale by remember(text, style.fontSize) { mutableFloatStateOf(1f) }
    var settled by remember(text, style.fontSize) { mutableStateOf(false) }

    // BasicText plutôt que Text : ce dernier écrase un éventuel dégradé par une couleur unie
    val resolved = when {
        color.isSpecified -> style.copy(color = color)
        style.brush != null || style.color.isSpecified -> style
        else -> style.copy(color = LocalContentColor.current)
    }

    BasicText(
        text = text,
        style = resolved.merge(
            TextStyle(
                fontSize = style.fontSize * scale,
                lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight
            )
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        // Invisible tant que la bonne taille n'est pas trouvée : pas de saut à l'écran
        modifier = modifier.drawWithContent { if (settled) drawContent() },
        onTextLayout = { result ->
            var brokenWord = false
            for (line in 0 until result.lineCount - 1) {
                val end = result.getLineEnd(line)
                if (end in 1 until text.length &&
                    text.text[end - 1].isLetterOrDigit() && text.text[end].isLetterOrDigit()
                ) brokenWord = true
            }
            if ((result.hasVisualOverflow || brokenWord) && scale > minScale) {
                scale = (scale - 0.06f).coerceAtLeast(minScale)
            } else {
                settled = true
            }
        }
    )
}

/**
 * Réalisation et têtes d'affiche. N'affiche rien tant que la fiche ne les a pas.
 *
 * `compact` : pour les bannières, tout tient dans un seul paragraphe qui passe
 * à la ligne entre deux noms, limité aux trois premiers rôles — aucun nom coupé.
 * Sinon (dans la fiche), réalisation et distribution ont chacune leur paragraphe.
 */
@Composable
fun CreditsBlock(
    node: com.cinetv.data.model.Node,
    modifier: Modifier = Modifier,
    accent: Color = Ink.Lilac,
    compact: Boolean = false
) {
    if (node.director.isBlank() && node.actors.isBlank()) return
    val label = SpanStyle(color = accent, letterSpacing = 1.2.sp)
    val bright = SpanStyle(color = Ink.TextHi)
    val by = if (node.mediaType == "tv") "CRÉÉ PAR  " else "DE  "
    // Espaces insécables : la ligne se coupe entre deux noms, jamais au milieu d'un
    fun names(raw: String, max: Int) =
        raw.split(",").map { it.trim().replace(' ', '\u00A0') }.filter { it.isNotEmpty() }
            .take(max).joinToString(", ")

    if (compact) {
        Text(
            text = buildAnnotatedString {
                if (node.director.isNotBlank()) {
                    withStyle(label) { append(by.replace(' ', '\u00A0')) }
                    withStyle(bright) { append(names(node.director, 2)) }
                }
                if (node.actors.isNotBlank()) {
                    if (node.director.isNotBlank()) append("    ")
                    withStyle(label) { append("AVEC\u00A0\u00A0") }
                    append(names(node.actors, 3))
                }
            },
            style = MaterialTheme.typography.labelLarge,
            color = Ink.TextMid,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (node.director.isNotBlank()) {
            Text(
                text = buildAnnotatedString {
                    withStyle(label) { append(by) }
                    append(names(node.director, 3))
                },
                style = MaterialTheme.typography.labelLarge,
                color = Ink.TextHi,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (node.actors.isNotBlank()) {
            Text(
                text = buildAnnotatedString {
                    withStyle(label) { append("AVEC  ") }
                    append(names(node.actors, 6))
                },
                style = MaterialTheme.typography.labelLarge,
                color = Ink.TextMid,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
