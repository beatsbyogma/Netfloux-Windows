package com.cinetv.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.cinetv.ui.theme.TitleArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le titre en image : le logo officiel du film ou de la série, à fond
 * transparent, à la place du titre écrit.
 *
 * Tant que l'image arrive, la place reste vide ; si le titre n'a pas de logo
 * (ou s'il ne se charge pas), [fallback] affiche le titre écrit habituel.
 * Un logo trop sombre pour se lire sur le noir est passé en blanc.
 */
@Composable
fun TitleLogo(
    url: String,
    maxWidth: Dp,
    maxHeight: Dp,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit
) {
    if (!TitleArt.enabled || url.isBlank()) {
        fallback()
        return
    }
    val state = rememberLogo(url)

    when (val logo = state) {
        Logo.Loading -> Box(Modifier.height(maxHeight * 0.6f))
        Logo.Missing -> fallback()
        is Logo.Ready -> {
            // La plus grande taille qui tient dans le cadre, sans déformer
            val ratio = logo.image.width.toFloat() / logo.image.height
            val boxRatio = maxWidth / maxHeight
            val (w, h) = if (ratio >= boxRatio) maxWidth to maxWidth / ratio else maxHeight * ratio to maxHeight
            Image(
                bitmap = logo.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = logo.filter,
                modifier = modifier.size(w, h)
            )
        }
    }
}

/** Charge le logo (une seule fois par adresse) et l'analyse : trop sombre ? quelle couleur ? */
@Composable
private fun rememberLogo(url: String): Logo {
    val context = LocalContext.current
    val state by produceState(if (url.isBlank()) Logo.Missing else cache[url] ?: Logo.Loading, url) {
        if (url.isBlank()) { value = Logo.Missing; return@produceState }
        cache[url]?.let { value = it; return@produceState }
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).allowHardware(false).build()
                )
                val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap == null || bitmap.width < 8 || bitmap.height < 8) Logo.Missing
                else Logo.Ready(bitmap.asImageBitmap(), readableFilter(bitmap), accentOf(bitmap))
            }.getOrDefault(Logo.Missing)
        }
        if (cache.size > 60) cache.clear()
        cache[url] = loaded
        value = loaded
    }
    return state
}

/**
 * La couleur d'accent du logo d'un titre : sa teinte vive dominante.
 * Null tant qu'il charge, s'il n'existe pas, ou s'il est blanc, gris ou noir.
 */
@Composable
fun rememberLogoAccent(url: String): Color? {
    if (!TitleArt.enabled) return null
    return (rememberLogo(url) as? Logo.Ready)?.accent
}

private sealed interface Logo {
    data object Loading : Logo
    data object Missing : Logo
    class Ready(val image: ImageBitmap, val filter: ColorFilter?, val accent: Color?) : Logo
}

/** Teinte vive dominante des pixels visibles, ou null si le logo n'a pas vraiment de couleur. */
private fun accentOf(bitmap: Bitmap): Color? {
    val stepX = (bitmap.width / 48).coerceAtLeast(1)
    val stepY = (bitmap.height / 48).coerceAtLeast(1)
    val hsv = FloatArray(3)
    var visible = 0
    var vivid = 0
    var weight = 0.0
    var hx = 0.0
    var hy = 0.0
    var sat = 0.0
    var bright = 0.0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if ((pixel ushr 24) > 96) {
                visible++
                android.graphics.Color.colorToHSV(pixel, hsv)
                if (hsv[1] > 0.32f && hsv[2] > 0.28f) {
                    vivid++
                    val w = (hsv[1] * hsv[2]).toDouble()
                    val angle = Math.toRadians(hsv[0].toDouble())
                    hx += Math.cos(angle) * w
                    hy += Math.sin(angle) * w
                    sat += hsv[1] * w
                    bright += hsv[2] * w
                    weight += w
                }
            }
            x += stepX
        }
        y += stepY
    }
    if (visible == 0 || weight <= 0.0 || vivid < visible * TitleArt.accentMinShare) return null
    val hue = ((Math.toDegrees(Math.atan2(hy, hx)) + 360) % 360).toFloat()
    return Color.hsv(
        hue,
        (sat / weight).toFloat().coerceIn(0.55f, 0.95f),
        (bright / weight).toFloat().coerceIn(0.62f, 1f)
    )
}

private val cache = HashMap<String, Logo>()

/**
 * Ce qu'il faut pour qu'un logo se lise sur le noir.
 *
 * Assez clair : rien. Trop sombre mais coloré : on l'éclaircit en gardant ses couleurs.
 * Trop sombre et sans couleur (un logo noir) : on le passe en blanc, faute de mieux.
 */
private fun readableFilter(bitmap: Bitmap): ColorFilter? {
    val stepX = (bitmap.width / 48).coerceAtLeast(1)
    val stepY = (bitmap.height / 48).coerceAtLeast(1)
    val hsv = FloatArray(3)
    var light = 0.0
    var saturation = 0.0
    var count = 0
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if ((pixel ushr 24) > 96) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                light += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                android.graphics.Color.colorToHSV(pixel, hsv)
                saturation += hsv[1]
                count++
            }
            x += stepX
        }
        y += stepY
    }
    if (count == 0) return null
    val average = (light / count).toFloat()
    if (average >= TitleArt.minLuminance) return null
    if (saturation / count < 0.28) return ColorFilter.tint(Color.White)
    // Coloré mais trop sombre : chaque couleur est multipliée, puis relevée d'un cran
    val gain = (0.55f / average.coerceAtLeast(0.05f)).coerceIn(1.2f, 3.2f)
    return ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(
            floatArrayOf(
                gain, 0f, 0f, 0f, 26f,
                0f, gain, 0f, 0f, 26f,
                0f, 0f, gain, 0f, 26f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}
