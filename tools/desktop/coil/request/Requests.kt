package coil.request

import android.content.Context
import android.graphics.drawable.Drawable

class ImageRequest private constructor(
    val data: Any?,
    val crossfadeMs: Int,
    /** Plus grand côté voulu en pixels ; 0 = taille d'origine (bornée). */
    val sizePx: Int
) {
    val memoryKey: String get() = "$data#$sizePx"

    class Builder(@Suppress("UNUSED_PARAMETER") context: Context?) {
        private var data: Any? = null
        private var crossfade = 0
        private var size = 0
        fun data(value: Any?) = apply { data = value }
        fun crossfade(ms: Int) = apply { crossfade = ms }
        fun crossfade(enable: Boolean) = apply { crossfade = if (enable) 220 else 0 }
        fun size(px: Int) = apply { size = px }
        fun size(width: Int, height: Int) = apply { size = maxOf(width, height) }
        fun allowHardware(@Suppress("UNUSED_PARAMETER") allow: Boolean) = this
        fun build() = ImageRequest(data, crossfade, size)
    }
}

sealed class ImageResult { abstract val drawable: Drawable? ; abstract val request: ImageRequest }
class SuccessResult(override val drawable: Drawable, override val request: ImageRequest) : ImageResult()
class ErrorResult(override val request: ImageRequest) : ImageResult() { override val drawable: Drawable? get() = null }
