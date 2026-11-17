package android.graphics

import android.content.res.Resources
import com.cinetv.desktop.Res
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

/** Une image en mémoire dont on peut lire et écrire les pixels (ARGB). */
class Bitmap internal constructor(internal val image: BufferedImage) {
    enum class Config { ARGB_8888, RGB_565, ALPHA_8 }

    val width: Int get() = image.width
    val height: Int get() = image.height

    fun getPixel(x: Int, y: Int): Int = image.getRGB(x, y)
    fun setPixel(x: Int, y: Int, color: Int) = image.setRGB(x, y, color)

    fun getPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        image.getRGB(x, y, width, height, pixels, offset, stride)
    }

    fun setPixels(pixels: IntArray, offset: Int, stride: Int, x: Int, y: Int, width: Int, height: Int) {
        image.setRGB(x, y, width, height, pixels, offset, stride)
    }

    fun copy(config: Config, mutable: Boolean): Bitmap {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        return Bitmap(out)
    }

    fun recycle() {}
    val isRecycled: Boolean get() = false

    companion object {
        fun createBitmap(width: Int, height: Int, config: Config): Bitmap =
            Bitmap(BufferedImage(width.coerceAtLeast(1), height.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB))

        fun createScaledBitmap(src: Bitmap, dstWidth: Int, dstHeight: Int, filter: Boolean): Bitmap {
            val out = BufferedImage(dstWidth.coerceAtLeast(1), dstHeight.coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
            out.createGraphics().apply {
                setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    if (filter) RenderingHints.VALUE_INTERPOLATION_BILINEAR else RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                )
                drawImage(src.image, 0, 0, out.width, out.height, null)
                dispose()
            }
            return Bitmap(out)
        }

        fun fromImage(image: BufferedImage): Bitmap = Bitmap(image)
    }
}

object BitmapFactory {
    class Options {
        @JvmField var inScaled: Boolean = true
        @JvmField var inSampleSize: Int = 1
        @JvmField var inPreferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    }

    fun decodeStream(input: InputStream?): Bitmap? =
        runCatching { input?.let { ImageIO.read(it) }?.let { Bitmap(toArgb(it)) } }.getOrNull()

    fun decodeByteArray(data: ByteArray, offset: Int, length: Int): Bitmap? =
        decodeStream(data.inputStream(offset, length))

    fun decodeResource(res: Resources, id: Int, opts: Options? = null): Bitmap? =
        Res.stream(id)?.use { decodeStream(it) }

    private fun toArgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_ARGB) return image
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        return out
    }
}

object Color {
    const val BLACK: Int = 0xFF000000.toInt()
    const val WHITE: Int = 0xFFFFFFFF.toInt()
    const val TRANSPARENT: Int = 0

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF
    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF
    fun rgb(red: Int, green: Int, blue: Int): Int = argb(255, red, green, blue)
    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    fun colorToHSV(color: Int, hsv: FloatArray) = RGBToHSV(red(color), green(color), blue(color), hsv)

    /** Teinte en degrés (0–360), saturation et valeur entre 0 et 1, comme sur Android. */
    fun RGBToHSV(red: Int, green: Int, blue: Int, hsv: FloatArray) {
        val r = red / 255f; val g = green / 255f; val b = blue / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        hsv[0] = if (h < 0f) h + 360f else h
        hsv[1] = if (max == 0f) 0f else delta / max
        hsv[2] = max
    }

    fun HSVToColor(hsv: FloatArray): Int = HSVToColor(255, hsv)

    fun HSVToColor(alpha: Int, hsv: FloatArray): Int {
        val rgb = java.awt.Color.HSBtoRGB(hsv[0] / 360f, hsv[1], hsv[2])
        return (alpha shl 24) or (rgb and 0xFFFFFF)
    }
}
