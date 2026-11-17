@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cinetv.ui.home

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import com.cinetv.ui.components.rememberColorFill
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Node
import com.cinetv.data.model.isSuggestion
import com.cinetv.data.model.logoIsForeign
import com.cinetv.ui.brand.NetflouxBadge
import com.cinetv.ui.components.CineIcon
import com.cinetv.ui.components.CreditsBlock
import com.cinetv.ui.components.FitText
import com.cinetv.ui.components.FocusHalo
import com.cinetv.ui.components.FocusStyle
import com.cinetv.ui.components.KenBurnsImage
import com.cinetv.ui.components.rememberLogoAccent
import com.cinetv.ui.components.rememberTitleAccent
import com.cinetv.ui.theme.TitleTint
import androidx.compose.animation.animateColorAsState
import com.cinetv.ui.components.genreLabel
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.ui.components.TitleLogo
import com.cinetv.ui.theme.TitleArt
import com.cinetv.ui.components.rememberTitleBrush
import com.cinetv.ui.theme.Fonts
import com.cinetv.ui.theme.GenreType
import com.cinetv.ui.theme.HomePage
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion

/**
 * La grande bannière d'accueil.
 *
 * Le visuel occupe toute la largeur et passe sous la barre de navigation ;
 * un dégradé l'efface vers la gauche pour laisser le texte respirer, un autre
 * le fond dans la première rangée de cartes. Le bloc de texte reprend la
 * maquette : signature, titre en capitales, informations, résumé, deux boutons.
 */
@Composable
fun HomeHero(
    node: Node?,
    contextLabel: String,
    highlight: String?,
    scanning: Boolean,
    playRequester: FocusRequester,
    onPlay: () -> Unit,
    onInfo: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    /** Retrait du texte à gauche : l'image, elle, passe sous le menu. */
    startInset: Dp = 0.dp,
    /**
     * Pour une série tirée au hasard : on présente la série et sa saison,
     * pas un épisode précis. Titre de remplacement et libellé du bouton.
     */
    seriesTitle: String? = null,
    seriesAction: String? = null,
    /** La couleur du bouton « Lire », pour que le N du menu la reprenne. */
    onAccent: (Color) -> Unit = {},
    /** « Bande-annonce » : l'ouvre dans sa fenêtre. Null = pas de bouton. */
    onTrailer: (() -> Unit)? = null,
    /** Repère de défilement : nombre de titres du tirage, et rang de celui qui est affiché (-1 : hors tirage). */
    steps: Int = 0,
    step: Int = -1
) {
    // La couleur du film affiché, partagée entre le bouton « Lire » et le repère de défilement
    var filmColor by remember { mutableStateOf(Color.White) }
    Box(
        modifier
            // La hauteur est donnée par la page : une part de l'écran
            .fillMaxWidth()
            .clipToBounds()
    ) {
        // D'une image à l'autre : un effet de cinéma, différent selon l'image qui arrive
        AnimatedContent(
            targetState = node?.backdropUrl?.takeIf { it.isNotBlank() }
                ?: node?.posterUrl?.takeIf { it.isNotBlank() }
                ?: "",
            transitionSpec = { heroCut(targetState) },
            label = "homeArt"
        ) { url ->
            // Les raccords « découpés » (iris, biais, stores, rideaux, losange) : l'image qui
            // arrive se dévoile dans une forme qui grandit, par-dessus l'ancienne.
            val kind = heroKind(url)
            val reveal by transition.animateFloat(
                transitionSpec = { tween(Motion.heroCutMs, easing = FastOutSlowInEasing) },
                label = "reveal"
            ) { state -> if (state == EnterExitState.PreEnter) 0f else 1f }
            val art = if (Motion.heroCuts && kind >= HERO_SHAPED_FROM) {
                Modifier.fillMaxSize().heroReveal(kind) { reveal }
            } else Modifier.fillMaxSize()
            if (url.isNotBlank()) KenBurnsImage(url, art)
            else Box(
                art
                    .background(
                        Brush.linearGradient(
                            listOf(Ink.VioletDeep.copy(alpha = 0.55f), Ink.Void)
                        )
                    )
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        *HomePage.scrimLeft.map { (stop, alpha) ->
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
                        *HomePage.scrimVertical.map { (stop, alpha) ->
                            stop to Ink.Void.copy(alpha = alpha)
                        }.toTypedArray()
                    )
                )
        )

        // Le titre prend les couleurs du visuel affiché
        val titleBrush = rememberTitleBrush(
            node?.backdropUrl?.takeIf { it.isNotBlank() } ?: node?.posterUrl.orEmpty()
        )

        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(HomePage.heroTextFraction)
                    .padding(start = startInset + HomePage.side, end = 20.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Le texte suit l'image : l'ancien s'efface, le nouveau monte à sa place.
                // Les boutons, eux, ne bougent pas — ils doivent garder le focus.
                AnimatedContent(
                    targetState = HeroText(node, seriesTitle, highlight ?: contextLabel),
                    contentKey = { it.node?.id },
                    transitionSpec = {
                        (fadeIn(tween(Motion.heroTextInMs, delayMillis = Motion.heroTextOutMs)) +
                            slideInVertically(tween(Motion.heroTextInMs, delayMillis = Motion.heroTextOutMs)) { it / 7 })
                            .togetherWith(fadeOut(tween(Motion.heroTextOutMs)))
                            .using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.BottomStart,
                    label = "homeText"
                ) { shown ->
                    @Suppress("NAME_SHADOWING") val node = shown.node
                    @Suppress("NAME_SHADOWING") val seriesTitle = shown.seriesTitle
                    Column {
                    // Surtitre : un filet rouge, puis d'où vient le titre
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(HeroInfo.ruleWidth).height(1.dp).background(Ink.LogoRed))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            shown.overline.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
                            color = Color.White.copy(alpha = HeroInfo.overlineAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(HeroInfo.gap))

                    // La police du titre suit le genre du film
                    val face = GenreType.faceFor(node?.genres.orEmpty())
                    val titleSize = HomePage.heroTitle * face.scale
                    // Le logo officiel s'il existe, sinon le titre écrit
                    TitleLogo(
                        url = node?.logoUrl.orEmpty(),
                        maxWidth = TitleArt.heroWidth,
                        maxHeight = TitleArt.heroHeight
                    ) {
                        FitText(
                            text = (seriesTitle ?: node?.displayTitle ?: "Bienvenue").let {
                                if (HomePage.heroTitleUppercase && face.uppercase) it.uppercase() else it
                            },
                            style = TextStyle(
                                fontFamily = face.family,
                                fontWeight = FontWeight.Black,
                                fontSize = titleSize,
                                lineHeight = titleSize * 1.06f,
                                letterSpacing = face.spacing.sp,
                                brush = titleBrush
                            ),
                            maxLines = 2
                        )
                    }

                    // Sous un logo (souvent en version originale) : le titre français, en toutes lettres
                    if (node != null && node.logoIsForeign) {
                        Spacer(Modifier.height(HeroInfo.gapSmall))
                        Text(
                            text = seriesTitle ?: node.displayTitle,
                            style = TextStyle(
                                fontFamily = com.cinetv.ui.theme.Fonts.Display,
                                fontWeight = FontWeight.Bold,
                                fontSize = HeroInfo.writtenTitleSize,
                                letterSpacing = 0.3.sp
                            ),
                            color = Color.White.copy(alpha = HeroInfo.writtenTitleAlpha),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(HeroInfo.gap))

                    MetaLine(node)

                    val blurb = node?.synopsis?.takeIf { it.isNotBlank() }
                        ?: "Branchez un disque dur USB, lancez l'analyse : Netfloux lit votre " +
                        "arborescence telle quelle et va chercher les affiches tout seul."
                    Spacer(Modifier.height(HeroInfo.gap))
                    Text(
                        text = blurb,
                        style = MaterialTheme.typography.bodyMedium.let {
                            val size = it.fontSize * HeroInfo.synopsisScale
                            it.copy(fontSize = size, lineHeight = size * HeroInfo.synopsisLine)
                        },
                        color = Color.White.copy(alpha = HeroInfo.synopsisAlpha),
                        maxLines = HeroInfo.synopsisLines,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (node != null) {
                        CreditsBlock(
                            node,
                            Modifier.padding(top = HeroInfo.gapSmall),
                            accent = Color.White.copy(alpha = HeroInfo.creditLabelAlpha),
                            compact = true
                        )
                    }
                    }
                }

                Spacer(Modifier.height(HeroInfo.gapButtons))

                // « Lire » prend la couleur du logo du film s'il en a une, sinon celle de l'image
                val logoAccent = rememberLogoAccent(node?.logoUrl.orEmpty())
                val artAccent = rememberTitleAccent(
                    node?.backdropUrl?.takeIf { it.isNotBlank() } ?: node?.posterUrl.orEmpty()
                )
                // Pas de fondu : la nouvelle couleur vient remplir le bouton (voir HeroAction)
                val playColor = deepen(logoAccent ?: artAccent)
                LaunchedEffect(playColor) { onAccent(playColor); filmColor = playColor }

                // Les boutons peuvent dépasser la colonne de texte : sinon le dernier s'écrase
                Row(
                    Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Un seul et même bouton principal, quel que soit le titre affiché :
                    // le recréer à chaque changement lui faisait perdre le focus.
                    val series = node != null && seriesAction != null
                    // Titre proposé, absent des disques : rien à lire, le bouton ouvre sa bande-annonce
                    val idea = node != null && node.isSuggestion
                    HeroAction(
                        icon = when {
                            node == null -> if (scanning) R.drawable.ic_disc else R.drawable.ic_play
                            idea -> R.drawable.ic_movie
                            series -> R.drawable.ic_folder_open
                            else -> R.drawable.ic_play
                        },
                        label = when {
                            node == null -> if (scanning) "Analyse en cours…" else "Analyser les disques"
                            idea -> "Voir la bande-annonce"
                            series -> seriesAction.orEmpty()
                            node.progressFraction > 0.01f -> "Reprendre"
                            else -> "Lire"
                        },
                        primary = true,
                        tint = playColor,
                        onClick = if (node == null) onScan else onPlay,
                        modifier = Modifier.focusRequester(playRequester)
                    )
                    if (node != null && !series) {
                        HeroAction(
                            icon = R.drawable.ic_info,
                            label = "Plus d'infos",
                            primary = false,
                            onClick = onInfo
                        )
                        if (onTrailer != null && node.tmdbId > 0 && !idea) {
                            HeroAction(
                                icon = R.drawable.ic_movie,
                                label = "Bande-annonce",
                                primary = false,
                                onClick = onTrailer
                            )
                        }
                    }
                }
            }
        }

        // Repère de défilement, en bas à droite : où en est la bannière dans son tirage
        HeroSteps(
            count = steps, current = step, color = filmColor,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = HomePage.side, bottom = HeroInfo.stepBottom)
        )
    }
}

/** Ce que le bloc de texte affiche pour un titre donné. */
private data class HeroText(val node: Node?, val seriesTitle: String?, val overline: String)

/**
 * Le raccord entre deux images de la bannière. Six effets, choisis d'après
 * l'image qui arrive : la même image revient toujours avec le même effet,
 * mais d'un film à l'autre ils alternent.
 */
/** Nombre de raccords, et rang à partir duquel ils sont « découpés » dans une forme. */
private const val HERO_KINDS = 11
private const val HERO_SHAPED_FROM = 6

/** Le raccord d'une image : toujours le même pour la même image, différent d'un film à l'autre. */
private fun heroKind(url: String): Int = (url.hashCode() and 0x7FFFFFFF) % HERO_KINDS

/**
 * Dévoile l'image dans une forme qui grandit avec [progress] (0 → 1) :
 * 6. Iris — un cercle s'ouvre depuis le cœur de l'image ;
 * 7. Biais — une lame en diagonale balaie l'écran ;
 * 8. Stores — des bandes verticales s'ouvrent l'une après l'autre ;
 * 9. Rideaux — l'image s'ouvre du centre vers les bords ;
 * 10. Losange — un losange grandit depuis le centre.
 */
private fun Modifier.heroReveal(kind: Int, progress: () -> Float): Modifier = drawWithContent {
    val p = progress()
    if (p >= 0.999f) { drawContent(); return@drawWithContent }
    val w = size.width
    val h = size.height
    val shape = Path()
    when (kind) {
        6 -> {
            val focus = Offset(w * 0.66f, h * 0.42f)
            shape.addOval(Rect(center = focus, radius = kotlin.math.hypot(w, h) * p))
        }
        7 -> {
            val slant = h * 0.55f
            val edge = (w + slant) * p
            shape.moveTo(0f, 0f); shape.lineTo(edge, 0f); shape.lineTo(edge - slant, h); shape.lineTo(0f, h); shape.close()
        }
        8 -> {
            val blinds = 8
            val band = w / blinds
            for (i in 0 until blinds) {
                // Chaque bande démarre un peu après sa voisine
                val open = (p * 1.6f - i * 0.6f / blinds).coerceIn(0f, 1f)
                shape.addRect(Rect(i * band, 0f, i * band + band * open + 1f, h))
            }
        }
        9 -> {
            val half = w / 2f * p
            shape.addRect(Rect(w / 2f - half, 0f, w / 2f + half, h))
        }
        else -> {
            val reach = (w + h) * 0.62f * p
            val cx = w / 2f
            val cy = h / 2f
            shape.moveTo(cx, cy - reach); shape.lineTo(cx + reach, cy); shape.lineTo(cx, cy + reach); shape.lineTo(cx - reach, cy); shape.close()
        }
    }
    clipPath(shape) { this@drawWithContent.drawContent() }
}

/**
 * Le repère de défilement de la bannière : un trait par titre du tirage. Celui du titre
 * affiché est plus long et se remplit, dans la couleur du film, le temps qu'il reste à l'écran.
 */
@Composable
private fun HeroSteps(count: Int, current: Int, color: Color, modifier: Modifier = Modifier) {
    if (count < 2 || current < 0) return
    val fill = remember { Animatable(0f) }
    LaunchedEffect(current) {
        fill.snapTo(0f)
        fill.animateTo(1f, tween(HomePage.heroRotateMs.toInt(), easing = LinearEasing))
    }
    // La couleur glisse d'un film au suivant
    val tone by animateColorAsState(color, tween(600), label = "stepColor")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(HeroInfo.stepGap), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val active = i == current
            val width by androidx.compose.animation.core.animateDpAsState(
                if (active) HeroInfo.stepActive else HeroInfo.stepWidth, tween(380, easing = FastOutSlowInEasing), label = "stepWidth"
            )
            Box(
                Modifier
                    .width(width)
                    .height(HeroInfo.stepHeight)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (active) 0.26f else HeroInfo.stepRestAlpha))
                    .drawBehind {
                        if (active) drawRect(tone, size = Size(size.width * fill.value, size.height))
                    }
            )
        }
    }
}

private fun heroCut(incoming: String): ContentTransform {
    if (!Motion.heroCuts) {
        return fadeIn(tween(Motion.heroCrossfadeMs)) togetherWith fadeOut(tween(Motion.heroCrossfadeMs))
    }
    val ms = Motion.heroCutMs
    val soft = tween<Float>(ms, easing = FastOutSlowInEasing)
    val kind = heroKind(incoming)
    if (kind >= HERO_SHAPED_FROM) {
        // Raccord découpé : la nouvelle image est posée d'un coup, par-dessus, et c'est sa forme
        // qui grandit (voir heroReveal) ; l'ancienne reste dessous jusqu'à la fin.
        return ContentTransform(
            targetContentEnter = fadeIn(tween(90)),
            initialContentExit = fadeOut(tween(60, delayMillis = ms)),
            targetContentZIndex = 1f,
            sizeTransform = SizeTransform(clip = true) { _, _ -> snap() }
        )
    }
    val (enter, exit) = when (kind) {
        // 1. Zoom avant : la nouvelle image arrive de près, l'ancienne recule
        0 -> (fadeIn(soft) + scaleIn(soft, initialScale = 1.14f)) to
            (fadeOut(soft) + scaleOut(soft, targetScale = 0.95f))
        // 2. Zoom arrière : l'ancienne fonce vers nous, la nouvelle se pose
        1 -> (fadeIn(soft) + scaleIn(soft, initialScale = 0.92f)) to
            (fadeOut(soft) + scaleOut(soft, targetScale = 1.16f))
        // 3. Volet : la nouvelle image se dévoile de gauche à droite
        2 -> (expandHorizontally(tween(ms, easing = FastOutSlowInEasing), expandFrom = Alignment.Start) + fadeIn(tween(ms / 3))) to
            fadeOut(tween(ms / 2, delayMillis = ms / 2))
        // 4. Glissé : les deux images coulissent ensemble vers la gauche
        3 -> (slideInHorizontally(tween(ms, easing = FastOutSlowInEasing)) { it / 9 } + fadeIn(soft)) to
            (slideOutHorizontally(tween(ms, easing = FastOutSlowInEasing)) { -it / 9 } + fadeOut(soft))
        // 5. Fondu au noir : l'ancienne s'éteint, puis la nouvelle s'allume
        4 -> fadeIn(tween(ms / 2, delayMillis = ms / 2)) to fadeOut(tween(ms / 2))
        // 6. Rideau : la nouvelle image descend du haut de l'écran
        else -> (expandVertically(tween(ms, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(ms / 3))) to
            fadeOut(tween(ms / 2, delayMillis = ms / 2))
    }
    // La nouvelle image passe au-dessus, et rien ne redimensionne la bannière pendant l'effet
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        targetContentZIndex = 1f,
        sizeTransform = SizeTransform(clip = true) { _, _ -> snap() }
    )
}

/**
 * Les teintes tirées de l'image sont pastel, pour rester lisibles en texte ;
 * sur un bouton plein, on les veut plus franches. Une teinte neutre reste blanche.
 */
private fun deepen(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(), hsv
    )
    if (hsv[1] < 0.12f) return color
    return Color.hsv(hsv[0], hsv[1].coerceAtLeast(HeroInfo.playSaturation), hsv[2].coerceAtMost(HeroInfo.playBrightness))
}

/** Année, durée, note, genre : une rangée de pastilles de verre. */
@Composable
private fun MetaLine(node: Node?) {
    // Les pastilles passent à la ligne plutôt que de se faire rogner
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val year = node?.year?.takeIf { it > 0 }?.toString()
        val duration = node?.durationMs?.takeIf { it > 0 }?.let { formatDuration(it) }
        val rating = node?.rating?.takeIf { it > 0f }
        val genre = node?.let { genreLabel(it.genres) }

        if (rating != null) GlassTag(String.format("%.1f", rating), star = true)
        if (year != null) GlassTag(year)
        if (duration != null) GlassTag(duration)
        if (genre != null) GlassTag(genre)
        node?.let { com.cinetv.data.model.qualityLabel(it.quality) }?.takeIf { it.isNotBlank() }?.let { GlassTag(it) }
        if (node != null && node.isSuggestion) GlassTag("À télécharger", tint = Ink.Warn)
        else if (node != null && !node.isAvailable) GlassTag("Débranché", tint = Ink.Warn)
        if (node == null) GlassTag("Bibliothèque vide")
    }
}

/** Une pastille de verre : fond et liseré blancs presque transparents. */
@Composable
fun GlassTag(text: String, tint: Color = Color.White, star: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .border(0.75.dp, tint.copy(alpha = HeroInfo.glassBorder), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (star) {
            Text("★", style = MaterialTheme.typography.labelSmall, color = Ink.LogoRed)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.chipSpacing.sp),
            color = tint.copy(alpha = 0.92f),
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Bouton de la bannière : du verre au repos, blanc plein une fois sélectionné,
 * avec le halo de la maison derrière lui.
 */
@Composable
private fun HeroAction(
    icon: Int,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bouton principal : rempli de la couleur du film affiché. */
    tint: Color? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tinted = tint != null
    // À chaque nouveau film, sa couleur remplit le bouton de gauche à droite
    val fill = rememberColorFill(tint ?: Color.Transparent)
    @Suppress("NAME_SHADOWING") val tint = if (tinted) fill.to else null
    // Texte et picto s'adaptent : noirs sur une couleur claire, blancs sur une couleur sombre
    val content = when {
        tint != null -> if (0.2126f * tint.red + 0.7152f * tint.green + 0.0722f * tint.blue > 0.52f) Ink.Void else Color.White
        focused -> Ink.Void
        else -> Color.White
    }
    val glass = if (primary) HeroInfo.buttonFill * 1.5f else HeroInfo.buttonFillSecondary
    // Un reflet léger traverse le bouton principal, de temps en temps
    val glint by rememberInfiniteTransition(label = "glint").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(HeroInfo.glintEveryMs, easing = LinearEasing)),
        label = "glintSweep"
    )

    Box(modifier) {
        // « Lire » n'a ni halo ni contour : seulement une ombre de sa propre couleur
        FocusHalo(focused, CircleShape, color = tint ?: Color.White, intensity = if (tinted) HeroInfo.playShadow else 0.26f)
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                // « Lire » porte sa couleur directement : la peindre par-dessus un fond
                // blanc laissait un liseré crénelé sur le bord arrondi
                containerColor = if (tinted) fill.from else Color.White.copy(alpha = glass),
                contentColor = Color.White,
                focusedContainerColor = if (tinted) fill.from else Color.White,
                focusedContentColor = Ink.Void,
                pressedContainerColor = if (tinted) fill.from else Color.White,
                pressedContentColor = Ink.Void
            ),
            scale = FocusStyle.lift(1.05f),
            glow = FocusStyle.glow(),
            border = ClickableSurfaceDefaults.border(
                border = if (tinted) androidx.tv.material3.Border.None else androidx.tv.material3.Border(
                    androidx.compose.foundation.BorderStroke(0.75.dp, Color.White.copy(alpha = HeroInfo.buttonBorder)),
                    shape = CircleShape
                ),
                focusedBorder = androidx.tv.material3.Border.None,
                pressedBorder = androidx.tv.material3.Border.None
            )
        ) {
            Row(
                Modifier
                    .drawBehind {
                        val p = fill.progress.value
                        // Tant que le fond n'a pas pris la nouvelle couleur, elle reste peinte :
                        // sinon l'ancienne réapparaissait le temps d'une image, d'où le flash.
                        if (tinted && fill.from != fill.to) {
                            drawRect(fill.to, size = Size(size.width * p, size.height))
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        // Le reflet ne passe que pendant une petite part du cycle ; le reste du temps, rien
                        val pass = glint / HeroInfo.glintShare
                        if (tinted && pass < 1f) {
                            val band = size.width * 0.42f
                            val x = -band + (size.width + band * 2f) * pass
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = HeroInfo.glintAlpha), Color.Transparent),
                                    start = Offset(x, 0f), end = Offset(x + band, size.height)
                                )
                            )
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                CineIcon(icon, tint = content, modifier = Modifier.size(12.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                    color = content,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h} h ${m.toString().padStart(2, '0')}" else "$m min"
}
