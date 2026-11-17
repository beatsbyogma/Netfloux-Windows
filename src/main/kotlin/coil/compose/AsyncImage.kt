package coil.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.request.ImageRequest
import java.util.Collections
import java.util.WeakHashMap

/** Une image du web ou du disque, chargée en arrière-plan puis fondue à l'écran. */
@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = FilterQuality.Low
) {
    val request = remember(model) {
        model as? ImageRequest ?: ImageRequest.Builder(null).data(model).crossfade(220).build()
    }
    val loader = ImageLoader.shared
    val ready = remember(request.memoryKey) { loader.cached(request)?.let { bitmapOf(it) } }
    var image by remember(request.memoryKey) { mutableStateOf(ready) }
    val fade = remember(request.memoryKey) { Animatable(if (ready != null || request.crossfadeMs <= 0) 1f else 0f) }

    LaunchedEffect(request.memoryKey) {
        if (image == null) {
            val loaded = runCatching { loader.load(request) }.getOrNull() ?: return@LaunchedEffect
            image = bitmapOf(loaded)
            if (fade.value < 1f) fade.animateTo(1f, tween(request.crossfadeMs))
        }
    }

    val shown = image
    if (shown == null) {
        Box(modifier)
    } else {
        Image(
            bitmap = shown,
            contentDescription = contentDescription,
            modifier = modifier.alpha(fade.value * alpha),
            alignment = alignment,
            contentScale = contentScale,
            colorFilter = colorFilter,
            filterQuality = FilterQuality.Medium
        )
    }
}

/** Une même image décodée n'est convertie qu'une fois pour l'affichage. */
private val converted: MutableMap<java.awt.image.BufferedImage, ImageBitmap> =
    Collections.synchronizedMap(WeakHashMap())

private fun bitmapOf(image: java.awt.image.BufferedImage): ImageBitmap =
    converted.getOrPut(image) { image.toComposeImageBitmap() }
