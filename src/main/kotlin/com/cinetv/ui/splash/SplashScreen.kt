package com.cinetv.ui.splash

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.ui.platform.LocalContext
import com.cinetv.R
import com.cinetv.ui.brand.NetflouxLogo
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Splash
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Ouverture de l'application, calée sur le jingle.
 *
 * Sur un fond presque noir où deux lueurs dérivent, les lettres du logo
 * montent l'une après l'autre et se calent en rebondissant, pendant que le
 * chat arrive au-dessus ; une pulsation marque le dernier accent du jingle,
 * puis tout s'efface.
 *
 * Pour la fluidité, rien n'est redessiné en cours de route : le logo est
 * une image fixe, et l'animation ne touche qu'à sa position, son échelle et
 * son opacité — des opérations que la puce graphique fait seule. L'ancienne
 * version recalculait un flou à chaque image, d'où les saccades.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    onSettled: () -> Unit = {},
    preload: suspend () -> Unit = {},
    /** Passe à 1, 2, 3… à chaque appui sur OK : l'intro s'efface aussitôt. */
    skipSignal: Int = 0
) {
    val context = LocalContext.current

    // Une valeur par lettre du logo : 0 = pas encore là, 1 = en place (le rebond dépasse un peu 1)
    val letters = remember { List(LETTERS.size) { Animatable(0f) } }
    val catPop = remember { Animatable(0f) }               // arrivée du chat, avec le logo
    // Le fond : deux lueurs qui dérivent, sans jamais rien redessiner
    val drift = rememberInfiniteTransition(label = "glowDrift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(Splash.glowDriftMs, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "glowDriftValue"
    )
    val pulse = remember { Animatable(1f) }                // pulsation sur l'accent final
    val fade = remember { Animatable(1f) }                 // sortie
    val progress = remember { Animatable(0f) }             // barre de chargement
    val loader = remember { Animatable(1f) }               // opacité de la barre
    val catAlpha = remember { Animatable(0f) }             // apparition du chat
    var catSheet by remember { mutableStateOf<ImageBitmap?>(null) }
    // Le fond s'anime au départ du jingle
    var partyOn by remember { mutableStateOf(false) }
    // Secondes écoulées depuis le départ du jingle (-1 tant qu'il n'a pas commencé) : tout le fond s'y cale
    var partyTime by remember { mutableStateOf(-1f) }
    LaunchedEffect(partyOn) {
        if (!partyOn) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) partyTime = (withFrameNanos { it } - start) / 1_000_000_000f
    }

    // Le lecteur du jingle est préparé hors du fil d'affichage : le préparer
    // sur place figeait l'écran pile au départ de l'animation.
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { player[0]?.stop() }
            runCatching { player[0]?.release() }
            player[0] = null
        }
    }

    // Appui sur OK : on coupe le jingle et on file à l'accueil
    val skipped = remember { booleanArrayOf(false) }
    LaunchedEffect(skipSignal) {
        if (skipSignal == 0) return@LaunchedEffect
        skipped[0] = true
        runCatching { player[0]?.stop() }
        onSettled()
        fade.animateTo(0f, tween(220))
        onFinished()
    }

    LaunchedEffect(Unit) {
        // ── 1. Chargement : tout ce qui coûte se fait ici, barre à l'écran
        // Le chat est préparé ici, mais n'entre en scène qu'avec le logo
        catSheet = withContext(Dispatchers.IO) { CatSheet.load(context) }
        progress.animateTo(0.12f, tween(220))
        player[0] = withContext(Dispatchers.IO) {
            runCatching {
                MediaPlayer.create(context, R.raw.netfloux_jingle)?.apply {
                    setVolume(Splash.jingleVolume, Splash.jingleVolume)
                }
            }.getOrNull()
        }
        progress.animateTo(0.34f, tween(200))
        runCatching { preload() }
        progress.animateTo(0.55f, tween(200))
        // L'accueil se construit maintenant, caché derrière cet écran
        onSettled()
        progress.animateTo(1f, tween(Splash.settleMs, easing = LinearOutSlowInEasing))
        loader.animateTo(0f, tween(Splash.loaderFadeMs))

        // ── 2. Le jingle et l'animation, sur une machine au repos
        withFrameNanos { }
        if (!skipped[0]) runCatching { player[0]?.start() }
        partyOn = Splash.party

        // Le chat et les lettres arrivent ensemble, les lettres l'une après l'autre
        val bounce = spring<Float>(dampingRatio = Splash.letterBounce, stiffness = Splash.letterStiffness)
        launch { catAlpha.animateTo(1f, tween(Splash.catFadeMs)) }
        launch { catPop.animateTo(1f, bounce) }
        letters.forEachIndexed { index, letter ->
            launch {
                if (Splash.lettersSplit) delay(index * Splash.letterStaggerMs)
                letter.animateTo(1f, bounce)
            }
        }
        launch {
            delay(Splash.pulseAtMs)
            pulse.animateTo(Splash.pulseScale, tween(Splash.pulseMs / 3, easing = LinearOutSlowInEasing))
            pulse.animateTo(1f, tween(Splash.pulseMs, easing = FastOutSlowInEasing))
        }
        delay(Splash.holdMs)
        fade.animateTo(0f, tween(Splash.fadeOutMs))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
            .graphicsLayer { alpha = fade.value },
        contentAlignment = Alignment.Center
    ) {
        // Deux lueurs sombres, peintes une fois pour toutes ; seules leur
        // position et leur taille bougent, ce que la puce graphique fait seule.
        Glow(
            color = Ink.LogoRed.copy(alpha = Splash.glowRed),
            modifier = Modifier.align(Alignment.BottomStart).graphicsLayer {
                val d = drift.value
                translationX = size.width * (-0.38f + Splash.glowTravel * d)
                translationY = size.height * (0.42f - Splash.glowTravel * 0.6f * d)
                // Sur chaque temps fort du jingle, la lueur gonfle et s'éclaire d'un coup
                val k = beatKick(partyTime)
                val s = (1f + Splash.glowGrow * d) * (1f + Splash.glowKick * k)
                scaleX = s; scaleY = s
                alpha = (Splash.glowRest + (1f - Splash.glowRest) * k).coerceIn(0f, 1f)
            }
        )
        Glow(
            color = Ink.VioletDeep.copy(alpha = Splash.glowViolet),
            modifier = Modifier.align(Alignment.TopEnd).graphicsLayer {
                val d = drift.value
                translationX = size.width * (0.36f - Splash.glowTravel * d)
                translationY = size.height * (-0.44f + Splash.glowTravel * 0.7f * d)
                val k = beatKick(partyTime)
                val s = (1f + Splash.glowGrow * (1f - d)) * (1f + Splash.glowKick * k)
                scaleX = s; scaleY = s
                alpha = (Splash.glowRest + (1f - Splash.glowRest) * k).coerceIn(0f, 1f)
            }
        )

        // Derrière le chat et le logo, qui restent tels quels
        // Deux anneaux de lumière qui tournent en sens inverse autour du centre et battent avec le jingle
        if (Splash.party) {
            LightRing(
                diameter = Splash.ringInner, first = Ink.LogoRed.copy(alpha = Splash.ringAlpha),
                second = Ink.VioletDeep.copy(alpha = Splash.ringAlpha),
                modifier = Modifier.graphicsLayer {
                    val t = partyTime
                    val k = beatKick(t)
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    rotationZ = t.coerceAtLeast(0f) * Splash.ringSpeed
                    val s = 1f + Splash.ringKick * k
                    scaleX = s; scaleY = s
                    alpha = (t / 0.6f).coerceIn(0f, 1f) * (Splash.ringRest + (1f - Splash.ringRest) * k).coerceIn(0f, 1f)
                }
            )
            LightRing(
                diameter = Splash.ringOuter, first = Ink.Violet.copy(alpha = Splash.ringAlpha * 0.8f),
                second = Ink.LogoRed.copy(alpha = Splash.ringAlpha * 0.8f),
                modifier = Modifier.graphicsLayer {
                    val t = partyTime
                    val k = beatKick(t)
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    rotationZ = 70f - t.coerceAtLeast(0f) * Splash.ringSpeed * 0.62f
                    val s = 1f + Splash.ringKick * 0.6f * k
                    scaleX = s; scaleY = s
                    alpha = (t / 0.6f).coerceIn(0f, 1f) * (Splash.ringRest + (1f - Splash.ringRest) * k).coerceIn(0f, 1f)
                }
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingCat(
                sheet = catSheet,
                modifier = Modifier
                    .size(Splash.catSize)
                    .graphicsLayer {
                        val p = catPop.value
                        alpha = catAlpha.value
                        translationY = Splash.catRise.toPx() * (1f - p)
                        val s = Splash.catScaleFrom + (1f - Splash.catScaleFrom) * p
                        scaleX = s; scaleY = s
                    }
            )
            Spacer(Modifier.height(Splash.catGap))

            // Sous le chat : la barre de chargement, puis le logo à la même place
            Box(contentAlignment = Alignment.Center) {
                // Barre de chargement : seule sa largeur varie, par simple mise à l'échelle
                Column(
                    Modifier.graphicsLayer { alpha = loader.value },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .width(Splash.loaderWidth)
                            .height(2.dp)
                            .background(Ink.Line)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = progress.value
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                }
                                .background(Ink.Brand)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "CHARGEMENT  ·  OK POUR PASSER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink.TextLow
                    )
                }

                // Le logo, lettre par lettre : chaque lettre est une image du logo
                // entier où elle seule est dessinée, si bien qu'elles se superposent
                // exactement une fois arrivées.
                Box(
                    Modifier.graphicsLayer {
                        val s = pulse.value
                        scaleX = s; scaleY = s
                    }
                ) {
                    LETTERS.forEachIndexed { index, drawable ->
                        Image(
                            painter = painterResource(drawable),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .height(Splash.logoHeight)
                                .graphicsLayer {
                                    val p = letters[index].value
                                    val away = 1f - p
                                    alpha = (p * 1.8f).coerceIn(0f, 1f)
                                    translationY = size.height * Splash.letterDrop * away
                                    // Écartées au départ, elles se resserrent en arrivant
                                    translationX = size.width * Splash.letterSpread * (index - (LETTERS.size - 1) / 2f) * away
                                    val s = Splash.letterScaleFrom + (1f - Splash.letterScaleFrom) * p
                                    scaleX = s; scaleY = s
                                    // Chaque lettre grossit autour d'elle-même, pas autour du centre du logo
                                    transformOrigin = TransformOrigin(LETTER_CENTERS[index], 0.5f)
                                }
                        )
                    }
                }
            }
        }
    }
}

/** Une lueur ronde, peinte une seule fois. */
@Composable
private fun Glow(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Splash.glowSize)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(color, Color.Transparent),
                        center = center,
                        radius = size.minDimension / 2f
                    )
                )
            }
    )
}

/** Les huit lettres du logo, de gauche à droite. */
private val LETTERS = listOf(
    R.drawable.netfloux_letter_0, R.drawable.netfloux_letter_1, R.drawable.netfloux_letter_2,
    R.drawable.netfloux_letter_3, R.drawable.netfloux_letter_4, R.drawable.netfloux_letter_5,
    R.drawable.netfloux_letter_6, R.drawable.netfloux_letter_7
)

/** Position horizontale du centre de chaque lettre, en part de la largeur du logo. */
private val LETTER_CENTERS = floatArrayOf(0.058f, 0.193f, 0.311f, 0.434f, 0.549f, 0.667f, 0.801f, 0.936f)


// ───────────────────────────────────────────────────────────── le fond qui danse

/**
 * Les temps forts du jingle, relevés dans le fichier son : instant (en secondes
 * depuis son départ) et force de 0 à 1. Les lumières du fond battent dessus.
 */
private val BEATS = listOf(
    0.07f to 0.60f, 0.41f to 0.55f, 0.77f to 0.60f, 0.88f to 0.54f, 1.00f to 0.51f,
    1.23f to 0.61f, 1.47f to 1.00f, 1.83f to 0.65f, 1.99f to 0.48f, 2.31f to 0.61f,
    2.67f to 0.62f, 3.15f to 0.58f, 3.38f to 0.80f, 3.74f to 0.49f, 3.89f to 0.31f
)

/** L'élan donné par la musique à l'instant [t] : bondit sur chaque temps fort, puis retombe vite. */
private fun beatKick(t: Float): Float {
    if (t < 0f) return 0f
    var kick = 0f
    for ((at, force) in BEATS) {
        val since = t - at
        if (since >= 0f && since < 0.6f) kick += force * kotlin.math.exp(-since / Splash.beatDecay)
    }
    return kick.coerceAtMost(1.2f)
}

/**
 * Un anneau de lumière : deux traînées de couleur face à face, fondues dans le noir,
 * avec un centre vide. Il est peint une seule fois ; ensuite on ne fait que le tourner
 * et le gonfler, ce que la puce graphique fait seule — aucune saccade possible.
 *
 * Deux anneaux qui tournent en sens inverse, à des vitesses différentes, donnent une
 * lumière fluide qui circule autour du chat et du logo sans jamais passer dessus.
 */
@Composable
private fun LightRing(
    diameter: androidx.compose.ui.unit.Dp,
    first: Color,
    second: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .requiredSize(diameter)
            .drawBehind {
                val radius = size.minDimension / 2f
                // Les deux traînées, puis le noir entre elles
                drawCircle(
                    Brush.sweepGradient(
                        listOf(first, Color.Transparent, Color.Transparent, second, Color.Transparent, Color.Transparent, first),
                        center = center
                    ),
                    radius = radius
                )
                // Le masque : rien au centre, le plein de lumière à mi-rayon, plus rien au bord
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent,
                        Splash.ringHole to Color.Transparent,
                        (Splash.ringHole + 1f) / 2f to Color.Black,
                        1f to Color.Transparent,
                        center = center, radius = radius
                    ),
                    radius = radius,
                    blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                )
            }
    )
}
