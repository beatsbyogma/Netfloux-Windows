package com.cinetv.ui.splash

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.cinetv.R
import kotlinx.coroutines.delay

/*
 * Le chat de l'écran d'ouverture.
 *
 * L'animation d'origine a été détourée et recolorée dans le dégradé du logo,
 * puis rangée dans une seule planche d'images (res/drawable-nodpi/netfloux_cat.png) :
 * 64 poses en grille de 8 colonnes. On n'en dessine qu'une case à la fois.
 */
object CatSheet {
    const val frames = 64
    const val columns = 8
    const val cell = 180

    /** Durée de chaque pose, en millisecondes — le rythme de l'animation d'origine. */
    val durations = longArrayOf(90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 90, 60, 60)

    /** À appeler hors du fil d'affichage : la planche pèse quelques mégaoctets une fois décodée. */
    fun load(context: Context): ImageBitmap? = runCatching {
        val options = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(context.resources, R.drawable.netfloux_cat, options)?.asImageBitmap()
    }.getOrNull()
}

/**
 * Passe le chat en blanc sans l'aplatir : ses couleurs deviennent des gris
 * très clairs, ses traits sombres restent visibles.
 */
val CatInWhite: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.57f, 1.12f, 0.22f, 0f, 46f,
            0.57f, 1.12f, 0.22f, 0f, 46f,
            0.57f, 1.12f, 0.22f, 0f, 46f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

@Composable
fun LoadingCat(
    sheet: ImageBitmap?,
    modifier: Modifier = Modifier,
    /** Recoloration à l'affichage ; rien = les couleurs d'origine. */
    colorFilter: ColorFilter? = null
) {
    val frame = remember { mutableIntStateOf(0) }

    LaunchedEffect(sheet) {
        if (sheet == null) return@LaunchedEffect
        while (true) {
            delay(CatSheet.durations[frame.intValue])
            frame.intValue = (frame.intValue + 1) % CatSheet.frames
        }
    }

    // La pose courante n'est lue qu'au moment du dessin : changer de pose
    // redessine ce petit carré, sans rien recalculer autour.
    Canvas(modifier) {
        val image = sheet ?: return@Canvas
        val index = frame.intValue
        drawImage(
            image = image,
            srcOffset = IntOffset(
                (index % CatSheet.columns) * CatSheet.cell,
                (index / CatSheet.columns) * CatSheet.cell
            ),
            srcSize = IntSize(CatSheet.cell, CatSheet.cell),
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            colorFilter = colorFilter,
            filterQuality = FilterQuality.Medium
        )
    }
}
