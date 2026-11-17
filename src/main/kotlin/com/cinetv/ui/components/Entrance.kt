package com.cinetv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinetv.ui.theme.Motion

/**
 * Entrée en scène : l'élément apparaît en fondu, en glissant depuis un léger
 * décalage. Sert aux panneaux (depuis la droite), aux rangées (depuis le bas)
 * et aux lignes de menu (l'une après l'autre, avec [delayMs]).
 */
fun Modifier.entrance(
    fromX: Dp = 0.dp,
    fromY: Dp = 0.dp,
    delayMs: Int = 0,
    durationMs: Int = Motion.enterMs
): Modifier = composed {
    if (!Motion.entrances) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, delayMs, FastOutSlowInEasing))
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationX = fromX.toPx() * (1f - p)
        translationY = fromY.toPx() * (1f - p)
    }
}
