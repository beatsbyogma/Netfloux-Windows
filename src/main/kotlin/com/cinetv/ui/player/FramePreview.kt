package com.cinetv.ui.player

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.cinetv.desktop.Vlc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer

/**
 * L'aperçu au-dessus de la timeline : un second lecteur VLC, muet et invisible, qui va
 * chercher l'image la plus proche de la position visée. Un seul appel à la fois ; si le
 * fichier ne s'y prête pas, la timeline reste simplement sans vignette.
 */
class FramePreview(@Suppress("unused") private val context: Context, private val address: String) {

    private val lock = Mutex()
    private var factory: MediaPlayerFactory? = null
    private var player: EmbeddedMediaPlayer? = null
    private var broken = !address.startsWith("file:")
    @Volatile private var frames = 0L
    private var width = 0
    private var height = 0
    private var pixels = ByteArray(0)

    suspend fun frameAt(positionMs: Long): ImageBitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (broken) return@withLock null
            runCatching {
                val p = player ?: open() ?: return@withLock null
                val before = frames
                p.controls().setTime(positionMs)
                // On attend l'image qui suit le déplacement, sans s'éterniser
                var waited = 0
                while (frames == before && waited < 1_500) { delay(25); waited += 25 }
                if (frames == before) return@withLock null
                snapshot()
            }.getOrElse { broken = true; null }
        }
    }

    private suspend fun open(): EmbeddedMediaPlayer? {
        Vlc.ensureLoaded()
        val f = MediaPlayerFactory("--quiet", "--no-audio", "--no-sub-autodetect-file", "--no-spu", "--avcodec-hw=any")
        val p = f.mediaPlayers().newEmbeddedMediaPlayer()
        p.videoSurface().set(f.videoSurfaces().newVideoSurface(format, render, true))
        factory = f
        player = p
        val path = File(java.net.URI(address)).absolutePath
        if (!p.media().startPaused(path)) { broken = true; return null }
        // Le temps que le fichier s'ouvre
        var waited = 0
        while (p.status().length() <= 0 && waited < 3_000) { delay(50); waited += 50 }
        return p
    }

    private val format = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val w = 320
            val h = if (sourceWidth > 0) (sourceHeight * w / sourceWidth).coerceAtLeast(2) and 1.inv() else 180
            synchronized(this@FramePreview) { width = w; height = h; pixels = ByteArray(w * h * 4) }
            return RV32BufferFormat(w, h)
        }
        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
    }

    private val render = RenderCallback { _, buffers, _ ->
        synchronized(this) {
            val b = buffers[0]
            b.rewind()
            b.get(pixels, 0, minOf(pixels.size, b.remaining()))
        }
        frames++
    }

    @Synchronized
    private fun snapshot(): ImageBitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap()
        bitmap.allocPixels(ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
        bitmap.installPixels(pixels.copyOf())
        return bitmap.asComposeImageBitmap()
    }

    fun release() {
        val p = player
        val f = factory
        player = null
        factory = null
        broken = true
        Thread {
            runCatching { p?.controls()?.stop() }
            runCatching { p?.release() }
            runCatching { f?.release() }
        }.apply { isDaemon = true }.start()
    }
}
