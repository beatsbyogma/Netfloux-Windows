package coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.request.SuccessResult
import com.cinetv.desktop.AppDirs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import javax.imageio.ImageIO

/**
 * Le chargeur d'images : télécharge les jaquettes, les garde sur le disque
 * (%LOCALAPPDATA%\Netfloux\cache\posters) et en mémoire, les réduit à la taille demandée.
 */
class ImageLoader private constructor() {

    private val http: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(12))
        .build()
    private val diskDir = File(AppDirs.cache, "posters").apply { mkdirs() }
    private val network = Semaphore(8)
    private val decoding = Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))

    /** Images décodées, la plus ancienne sortie la première ; taille comptée en pixels. */
    private val memory = object : LinkedHashMap<String, BufferedImage>(256, 0.75f, true) {
        var pixels = 0L
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BufferedImage>?): Boolean = false
    }
    private val maxPixels: Long = (Runtime.getRuntime().maxMemory() / 4 / 4).coerceAtLeast(40_000_000L)

    private fun remember(key: String, image: BufferedImage) = synchronized(memory) {
        memory.put(key, image)?.let { memory.pixels -= it.width.toLong() * it.height }
        memory.pixels += image.width.toLong() * image.height
        val it = memory.entries.iterator()
        while (memory.pixels > maxPixels && it.hasNext()) {
            val e = it.next()
            memory.pixels -= e.value.width.toLong() * e.value.height
            it.remove()
        }
    }

    fun cached(request: ImageRequest): BufferedImage? = synchronized(memory) { memory[request.memoryKey] }

    suspend fun execute(request: ImageRequest): ImageResult = withContext(Dispatchers.IO) {
        val image = runCatching { load(request) }.getOrNull()
        if (image != null) SuccessResult(BitmapDrawable(Bitmap.fromImage(image)), request)
        else ErrorResult(request)
    }

    internal suspend fun load(request: ImageRequest): BufferedImage? {
        val key = request.memoryKey
        cached(request)?.let { return it }
        val source = request.data?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val bytes = bytesOf(source) ?: return null
        val image = decoding.withPermit {
            val decoded = ImageIO.read(bytes.inputStream()) ?: return null
            shrink(toArgb(decoded), request.sizePx)
        }
        remember(key, image)
        return image
    }

    private suspend fun bytesOf(source: String): ByteArray? {
        if (source.startsWith("http://") || source.startsWith("https://")) {
            val file = File(diskDir, sha1(source))
            if (file.isFile && file.length() > 0) return runCatching { file.readBytes() }.getOrNull()
            val body = network.withPermit {
                runCatching {
                    val response = http.send(
                        HttpRequest.newBuilder(URI(source)).timeout(Duration.ofSeconds(25))
                            .header("User-Agent", "Netfloux/1.0").GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray()
                    )
                    response.body().takeIf { response.statusCode() in 200..299 && it.isNotEmpty() }
                }.getOrNull()
            } ?: return null
            runCatching {
                val tmp = File(diskDir, file.name + ".tmp")
                tmp.writeBytes(body)
                tmp.renameTo(file)
            }
            trimDisk()
            return body
        }
        val file = if (source.startsWith("file:")) File(URI(source)) else File(source)
        return runCatching { file.readBytes() }.getOrNull()
    }

    @Volatile private var lastTrim = 0L

    /** Le cache disque reste sous 512 Mo : les images les plus anciennes partent. */
    private fun trimDisk() {
        val now = System.currentTimeMillis()
        if (now - lastTrim < 60_000) return
        lastTrim = now
        val files = diskDir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_DISK) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= MAX_DISK * 8 / 10) break
            total -= f.length()
            f.delete()
        }
    }

    private fun shrink(image: BufferedImage, maxSide: Int): BufferedImage {
        val limit = if (maxSide > 0) maxSide else MAX_SIDE
        val longest = maxOf(image.width, image.height)
        if (longest <= limit) return image
        val ratio = limit.toDouble() / longest
        val w = (image.width * ratio).toInt().coerceAtLeast(1)
        val h = (image.height * ratio).toInt().coerceAtLeast(1)
        // Réduction en plusieurs passes : plus net qu'un seul grand saut
        var current = image
        var cw = image.width
        var ch = image.height
        do {
            cw = maxOf(cw / 2, w); ch = maxOf(ch / 2, h)
            val next = BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB)
            next.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                drawImage(current, 0, 0, cw, ch, null)
                dispose()
            }
            current = next
        } while (cw != w || ch != h)
        return current
    }

    private fun toArgb(image: BufferedImage): BufferedImage {
        if (image.type == BufferedImage.TYPE_INT_ARGB) return image
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        out.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        return out
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        val shared: ImageLoader by lazy { ImageLoader() }
        private const val MAX_DISK = 512L * 1024 * 1024
        /** Plus grand côté gardé en mémoire : assez pour une bannière plein écran. */
        private const val MAX_SIDE = 1920
    }
}

val Context.imageLoader: ImageLoader get() = ImageLoader.shared
