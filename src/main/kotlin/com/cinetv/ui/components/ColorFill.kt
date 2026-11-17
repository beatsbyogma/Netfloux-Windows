package com.cinetv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.cinetv.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Un changement de couleur « par remplissage » : la nouvelle couleur [to]
 * vient recouvrir l'ancienne [from], [progress] allant de 0 à 1.
 * Une fois le remplissage fini, [from] rejoint [to].
 */
@Stable
class ColorFillState(initial: Color) {
    var from by mutableStateOf(initial)
    var to by mutableStateOf(initial)
    val progress = Animatable(1f)
}

@Composable
fun rememberColorFill(target: Color): ColorFillState {
    val state = remember { ColorFillState(target) }
    LaunchedEffect(target) {
        if (target == state.to) return@LaunchedEffect
        // Petite attente : la couleur d'un film arrive parfois en deux temps
        delay(Motion.fillDelayMs.toLong())
        state.from = lerp(state.from, state.to, state.progress.value)
        state.to = target
        state.progress.snapTo(0f)
        state.progress.animateTo(1f, tween(Motion.fillMs, easing = FastOutSlowInEasing))
        state.from = target
    }
    return state
}
