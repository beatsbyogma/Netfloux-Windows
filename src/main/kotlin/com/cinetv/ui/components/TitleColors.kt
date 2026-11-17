package com.cinetv.ui.components

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.TitleTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/*
 * Les titres de bannière prennent les couleurs de leur visuel.
 *
 * On lit une miniature de l'image, on y cherche ses deux teintes vives
 * dominantes, puis on les éclaircit jusqu'à ce qu'elles restent bien lisibles
 * sur fond noir. Le titre est peint dans le dégradé de l'une à l'autre.
 * Une image grise ou trop terne laisse le titre en blanc.
 */

private val cache = ConcurrentHashMap<String, Pair<Color, Color>>()
private val NEUTRAL = Ink.TextHi to Ink.TextHi

/** Le dégradé du titre pour ce visuel. Change en fondu d'un visuel à l'autre. */
@Composable
fun rememberTitleBrush(imageUrl: String): Brush {
    val colors = rememberTitleColors(imageUrl)
    val start by animateColorAsState(colors.first, tween(TitleTint.fadeMs), label = "titleStart")
    val end by animateColorAsState(colors.second, tween(TitleTint.fadeMs), label = "titleEnd")
    return Brush.horizontalGradient(listOf(start, end))
}

/** La teinte principale de ce visuel, lisible sur noir ; blanc si l'image est terne. */
@Composable
fun rememberTitleAccent(imageUrl: String): Color = rememberTitleColors(imageUrl).first

@Composable
private fun rememberTitleColors(imageUrl: String): Pair<Color, Color> {
    val context = LocalContext.current
    val colors by produceState(cache[imageUrl] ?: NEUTRAL, imageUrl) {
        if (!TitleTint.enabled || imageUrl.isBlank()) {
            value = NEUTRAL
            return@produceState
        }
        cache[imageUrl]?.let { value = it; return@produceState }
        val found = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(64)                   // une miniature suffit, et elle est déjà en cache
                    .allowHardware(false)       // il faut pouvoir lire les pixels
                    .build()
                val bitmap = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { dominantPair(it) }
            }.getOrNull()
        } ?: NEUTRAL
        cache[imageUrl] = found
        value = found
    }
    return colors
}

/** Les deux teintes vives les plus présentes, rendues lisibles sur noir. */
private fun dominantPair(bitmap: android.graphics.Bitmap): Pair<Color, Color>? {
    val bins = 18                                   // tranches de 20° sur le cercle des teintes
    val weight = FloatArray(bins)
    val hueX = FloatArray(bins)
    val hueY = FloatArray(bins)
    val sat = FloatArray(bins)
    val hsv = FloatArray(3)

    val stepX = maxOf(1, bitmap.width / 48)
    val stepY = maxOf(1, bitmap.height / 48)
    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            android.graphics.Color.colorToHSV(bitmap.getPixel(x, y), hsv)
            // Les gris et les zones sombres n'ont pas de teinte à offrir
            if (hsv[1] > 0.22f && hsv[2] > 0.20f) {
                val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
                val w = hsv[1] * hsv[2]
                weight[bin] += w
                val angle = Math.toRadians(hsv[0].toDouble())
                hueX[bin] += (Math.cos(angle) * w).toFloat()
                hueY[bin] += (Math.sin(angle) * w).toFloat()
                sat[bin] += hsv[1] * w
            }
            x += stepX
        }
        y += stepY
    }

    val total = weight.sum()
    if (total < 6f) return null                     // image presque sans couleur

    fun colorOf(bin: Int): Color {
        val hue = ((Math.toDegrees(Math.atan2(hueY[bin].toDouble(), hueX[bin].toDouble())) + 360) % 360).toFloat()
        return legible(hue, (sat[bin] / weight[bin]).coerceIn(0f, 1f))
    }

    val first = weight.indices.maxByOrNull { weight[it] } ?: return null
    // La seconde teinte doit être franchement différente, et peser un minimum
    val second = weight.indices
        .filter { minOf((it - first + bins) % bins, (first - it + bins) % bins) >= 2 }
        .maxByOrNull { weight[it] }
        ?.takeIf { weight[it] > total * 0.08f }

    val a = colorOf(first)
    val b = if (second != null) colorOf(second) else {
        // Image d'une seule teinte : on dégrade vers sa voisine, plus claire
        val hue = ((Math.toDegrees(Math.atan2(hueY[first].toDouble(), hueX[first].toDouble())) + 360 + 28) % 360).toFloat()
        legible(hue, (sat[first] / weight[first] * 0.6f).coerceIn(0f, 1f))
    }
    return a to b
}

/**
 * Une teinte, poussée en pleine lumière puis désaturée juste ce qu'il faut
 * pour rester nette sur fond noir : un bleu profond devient un bleu clair,
 * un jaune reste tel quel.
 */
private fun legible(hue: Float, saturation: Float): Color {
    var s = saturation.coerceIn(TitleTint.minSaturation, TitleTint.maxSaturation)
    var color = Color.hsv(hue, s, 1f)
    while (luminance(color) < TitleTint.minLuminance && s > 0.05f) {
        s -= 0.05f
        color = Color.hsv(hue, s.coerceAtLeast(0f), 1f)
    }
    return color
}

private fun luminance(c: Color): Float = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
