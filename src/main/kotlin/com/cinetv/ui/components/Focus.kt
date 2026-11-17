package com.cinetv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Glow
import com.cinetv.ui.theme.Halo
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion
import com.cinetv.ui.theme.Tiles
import com.cinetv.ui.theme.Radius

/**
 * Traitement du focus, commun à toutes les tuiles.
 *
 * Pas de contour : un cadre tracé autour d'une affiche écrase l'image et
 * fait bricolage à l'écran. La sélection se lit autrement — la tuile
 * s'éclaircit pendant que les voisines restent en retrait, se soulève
 * légèrement, et projette un halo coloré sous elle. Trois signaux
 * complémentaires, aucun trait.
 */
object FocusStyle {

    /** Les tuiles non sélectionnées sont en retrait ; celle qui l'est revient à pleine valeur. */
    @Composable
    fun dim(focused: Boolean, resting: Float = Tiles.restingDim): Float {
        val alpha by animateFloatAsState(
            targetValue = if (focused) 1f else resting,
            animationSpec = tween(Motion.dimMs, easing = FastOutSlowInEasing),
            label = "focusDim"
        )
        return alpha
    }

    @Composable
    fun shape(radius: androidx.compose.ui.unit.Dp = Radius.tile): ClickableSurfaceShape =
        ClickableSurfaceDefaults.shape(RoundedCornerShape(radius))

    /** Aucun contour, ni au repos ni au focus. */
    @Composable
    fun noBorder(): androidx.tv.material3.ClickableSurfaceBorder =
        ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None
        )

    @Composable
    fun lift(scale: Float = 1.055f): ClickableSurfaceScale =
        // Au clic, l'élément s'enfonce un instant avant de reprendre sa taille
        ClickableSurfaceDefaults.scale(focusedScale = scale, pressedScale = scale * Motion.pressScale)

    /**
     * Le halo est dessiné par [FocusHalo], pas par la Surface : l'ombre native
     * ne prend qu'une couleur unie, or on veut un dégradé.
     */
    @Composable
    fun glow(color: Color = Ink.Violet, elevation: androidx.compose.ui.unit.Dp = 18.dp): ClickableSurfaceGlow =
        ClickableSurfaceDefaults.glow(glow = Glow.None, focusedGlow = Glow.None)

    @Composable
    fun colors(
        container: Color = Ink.SurfaceHi,
        content: Color = Ink.TextHi
    ): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = container,
        focusedContainerColor = container,
        pressedContainerColor = container,
        contentColor = content,
        focusedContentColor = content,
        pressedContentColor = content
    )
}

/**
 * Lueur de sélection : une nappe de lumière diffuse qui tombe sous l'élément,
 * comme s'il était éclairé par-dessous. Aucun contour, aucune arête.
 *
 * Dessinée en dégradé (et non en flou), elle rend pareil sur toutes les
 * versions d'Android. Sa couleur est celle du thème, sauf si on en donne une.
 */
@Composable
fun BoxScope.FocusHalo(
    focused: Boolean,
    @Suppress("UNUSED_PARAMETER") shape: Shape,
    color: Color = Ink.Violet,
    intensity: Float = Halo.intensity
) {
    val presence by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(Motion.haloMs, easing = FastOutSlowInEasing),
        label = "haloPresence"
    )
    if (presence <= 0.01f) return

    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                val rx = size.width * Halo.width / 2f
                val ry = minOf(Halo.reach.toPx(), size.height * Halo.reachMaxFraction)
                val center = Offset(size.width / 2f, size.height + Halo.drop.toPx())
                val strength = presence * intensity
                // Une ellipse plate, pleine au centre et fondue jusqu'à rien sur les bords
                scale(scaleX = 1f, scaleY = ry / rx, pivot = center) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            // Fondu très progressif : aucun bord ne doit se deviner
                            0f to color.copy(alpha = strength),
                            0.2f to color.copy(alpha = strength * 0.70f),
                            0.4f to color.copy(alpha = strength * 0.40f),
                            0.6f to color.copy(alpha = strength * 0.18f),
                            0.8f to color.copy(alpha = strength * 0.05f),
                            1f to color.copy(alpha = 0f),
                            center = center,
                            radius = rx
                        ),
                        radius = rx,
                        center = center
                    )
                }
            }
    )
}

/**
 * Distingue l'appui bref de l'appui long sur la touche OK.
 *
 * La gestion native déclenchait les deux : maintenir OK pour ouvrir le menu
 * lançait aussi la lecture au relâchement. On intercepte donc les touches
 * avant la Surface, et on ne décide qu'au relâchement.
 */
fun Modifier.dpadPress(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    longPressMs: Long = 450L
): Modifier = composed {
    var pressedAt by remember { mutableLongStateOf(0L) }

    // La carte s'enfonce tant que OK est tenu, et rebondit au relâchement
    val held = pressedAt != 0L
    val squash by animateFloatAsState(
        targetValue = if (held) Motion.pressScale else 1f,
        animationSpec = if (held) tween(Motion.pressInMs)
                        else spring(dampingRatio = Motion.pressBounce, stiffness = Motion.pressStiffness),
        label = "pressSquash"
    )

    graphicsLayer { scaleX = squash; scaleY = squash }.then(com.cinetv.desktop.secondaryClick(onLongClick)).onPreviewKeyEvent { event ->
        val isSelect = event.key == Key.DirectionCenter ||
            event.key == Key.Enter ||
            event.key == Key.NumPadEnter
        if (!isSelect) return@onPreviewKeyEvent false

        when (event.type) {
            KeyEventType.KeyDown -> {
                if (pressedAt == 0L) pressedAt = System.currentTimeMillis()
                true
            }
            KeyEventType.KeyUp -> {
                val held = System.currentTimeMillis() - pressedAt
                pressedAt = 0L
                if (held >= longPressMs) onLongClick() else onClick()
                true
            }
            else -> false
        }
    }
}
