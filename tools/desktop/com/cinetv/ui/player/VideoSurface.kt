package com.cinetv.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * L'image du film : la dernière image livrée par VLC, cadrée selon le réglage
 * (entière, zoomée pour remplir l'écran, ou étirée).
 */
@Composable
fun VideoSurface(engine: VlcEngine, modifier: Modifier = Modifier) {
    val holder = remember(engine) { arrayOfNulls<Bitmap>(1) }
    Canvas(modifier) {
        // Lu ici pour que chaque nouvelle image redessine
        @Suppress("UNUSED_VARIABLE") val v = engine.frames.version.longValue
        val image = engine.frames.read { pixels, w, h ->
            if (w <= 0 || h <= 0 || pixels.size < w * h * 4) return@read null
            var bitmap = holder[0]
            if (bitmap == null || bitmap.width != w || bitmap.height != h) {
                bitmap?.close()
                bitmap = Bitmap().apply { allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)) }
                holder[0] = bitmap
            }
            bitmap.installPixels(pixels)
            bitmap.notifyPixelsChanged()
            bitmap.asComposeImageBitmap()
        } ?: return@Canvas

        val sw = image.width.toFloat()
        val sh = image.height.toFloat()
        val box = size
        val (dw, dh) = when (engine.scale) {
            1 -> { val k = maxOf(box.width / sw, box.height / sh); sw * k to sh * k }
            2 -> box.width to box.height
            else -> { val k = minOf(box.width / sw, box.height / sh); sw * k to sh * k }
        }
        val left = (box.width - dw) / 2f
        val top = (box.height - dh) / 2f
        clipRect {
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(left.toInt(), top.toInt()),
                dstSize = IntSize(dw.toInt(), dh.toInt()),
                filterQuality = FilterQuality.Medium
            )
        }
    }
}
