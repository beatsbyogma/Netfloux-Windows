package com.cinetv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.cinetv.ui.theme.FolderArt
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.PosterWall
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Fond de carte « films » : une seule jaquette visible, qui glisse vers le
 * haut pour laisser monter la suivante.
 *
 * Le départ de chaque carte est décalé d'après son image, pour que la grille
 * entière ne bascule pas d'un bloc — c'est ce qui ferait saccader l'écran.
 */
private val CarouselEase = androidx.compose.animation.core.CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

@Composable
fun PosterCarousel(
    posters: List<String>,
    animated: Boolean,
    modifier: Modifier = Modifier
) {
    if (posters.isEmpty()) {
        Box(modifier.background(Ink.SurfaceHi))
        return
    }

    var index by remember(posters) { mutableIntStateOf(0) }

    val motion = LocalAmbientMotion.current
    LaunchedEffect(animated, motion, posters) {
        if (!animated || !motion || posters.size < 2) return@LaunchedEffect
        delay(abs(posters.first().hashCode()) % FolderArt.holdMs)
        while (true) {
            delay(FolderArt.holdMs)
            index += 1
        }
    }

    Box(modifier.background(Ink.Deep).clipToBounds()) {
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                // Glissement lent, accéléré puis freiné en douceur ; le fondu masque le raccord
                val glide = tween<androidx.compose.ui.unit.IntOffset>(FolderArt.slideMs, easing = CarouselEase)
                val fade = tween<Float>(FolderArt.slideMs, easing = CarouselEase)
                (slideInVertically(glide) { height -> height } + fadeIn(fade)) togetherWith
                    (slideOutVertically(glide) { height -> -height } + fadeOut(fade))
            },
            label = "folderArt"
        ) { step ->
            Poster(
                url = posters[step % posters.size],
                corner = 0.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
