package com.cinetv.ui.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.mutableLongStateOf
import com.cinetv.data.network.NetworkShares
import com.cinetv.desktop.Vlc
import jcifs.smb.SmbRandomAccessFile
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaSlaveType
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.media.callback.seekable.SeekableCallbackMedia
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import javax.swing.SwingUtilities

/**
 * Le moteur de lecture : VLC, embarqué avec l'application (même moteur que sur la TV).
 *
 * VLC décode le fichier et dessine les sous-titres dans l'image ; chaque image arrive ici,
 * réduite au besoin à la taille de l'écran, et [VideoSurface] l'affiche.
 * L'habillage du lecteur ne lui parle que par cette classe.
 */
class VlcEngine(
    private val context: Context,
    private val uri: String,
    private val subtitleSize: Int,
    private val subtitleColor: Int,
    private val subtitleBackground: Int,
    private val preferredAudio: String,
    private val preferredSubtitle: String,
    private var wantSubtitles: Boolean
) {
    interface Listener {
        fun onBuffering(buffering: Boolean)
        fun onPlaying(playing: Boolean)
        fun onLength(durationMs: Long)
        fun onTracks()
        fun onEnded()
        fun onError()
        fun onAudioRoute(direct: Boolean)
    }

    var listener: Listener? = null

    /** L'image en cours, lue par [VideoSurface]. */
    internal val frames = FrameStore()

    private var factory: MediaPlayerFactory? = null
    private var player: EmbeddedMediaPlayer? = null

    private var started = false
    val hasStarted: Boolean get() = started
    @Volatile private var released = false
    private var pendingStartMs = 0L
    private val extraSubtitles = ArrayList<File>()
    private var slavesAdded = false
    private var audioMode = AUDIO_AUTO
    private var routeDirect: Boolean? = null
    private var audioDelayUs = 0L
    private var subtitleDelayUs = 0L

    /** Cadrage : 0 = image entière, 1 = zoom, 2 = étirée. Appliqué à l'affichage. */
    var scale = 0
        private set

    fun setScale(index: Int) { scale = index; frames.touch() }

    // ------------------------------------------------------------ construction

    private fun build() {
        Vlc.ensureLoaded()
        val args = buildList {
            add("--quiet")
            add("--no-video-title-show")
            add("--file-caching=$FILE_CACHE_MS")
            add("--network-caching=$NETWORK_CACHE_MS")
            add("--audio-time-stretch")
            add("--freetype-rel-fontsize=${FONT_SIZES.getOrElse(subtitleSize) { 16 }}")
            add("--freetype-color=${SUB_COLORS[subtitleColor.coerceIn(0, SUB_COLORS.lastIndex)].second and 0xFFFFFF}")
            add("--freetype-outline-thickness=${if (subtitleBackground == 0) 4 else 0}")
            add("--freetype-shadow-opacity=${if (subtitleBackground == 1) 200 else 0}")
            add("--freetype-background-opacity=${if (subtitleBackground == 2) 184 else 0}")
            add("--freetype-background-color=0")
            add("--avcodec-hw=any")
            // Son direct : la piste Dolby / DTS part telle quelle vers l'ampli (HDMI, optique)
            if (audioMode == AUDIO_DIRECT) {
                add("--spdif")
                add("--mmdevice-passthrough=1")
            }
        }
        val f = MediaPlayerFactory(*args.toTypedArray())
        val p = f.mediaPlayers().newEmbeddedMediaPlayer()
        p.videoSurface().set(f.videoSurfaces().newVideoSurface(formatCallback, renderCallback, true))
        p.events().addMediaPlayerEventListener(events)
        factory = f
        player = p
    }

    private val formatCallback = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            // Au-delà de la taille de l'écran, l'image est réduite par VLC : inutile de copier du 4K
            val (w, h) = fit(sourceWidth, sourceHeight, Vlc.maxFrameWidth(), Vlc.maxFrameHeight())
            frames.resize(w, h)
            return RV32BufferFormat(w, h)
        }

        override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
    }

    private val renderCallback = RenderCallback { _, nativeBuffers, format ->
        frames.write(nativeBuffers[0], format.width, format.height)
    }

    private val events = object : MediaPlayerEventAdapter() {
        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) = ui { listener?.onBuffering(newCache < 100f) }
        override fun playing(mediaPlayer: MediaPlayer) {
            ui { listener?.onBuffering(false); listener?.onPlaying(true) }
            mediaPlayer.submit { addSlavesOnce(); refreshTracks() }
        }
        override fun paused(mediaPlayer: MediaPlayer) = ui { listener?.onPlaying(false) }
        override fun stopped(mediaPlayer: MediaPlayer) = ui { listener?.onPlaying(false) }
        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = ui { listener?.onLength(newLength.coerceAtLeast(0L)) }
        override fun elementaryStreamAdded(mediaPlayer: MediaPlayer, type: TrackType, id: Int) { mediaPlayer.submit { refreshTracks() } }
        override fun elementaryStreamDeleted(mediaPlayer: MediaPlayer, type: TrackType, id: Int) { mediaPlayer.submit { refreshTracks() } }
        override fun elementaryStreamSelected(mediaPlayer: MediaPlayer, type: TrackType, id: Int) { mediaPlayer.submit { refreshTracks() } }
        override fun finished(mediaPlayer: MediaPlayer) = ui { listener?.onPlaying(false); listener?.onEnded() }
        override fun error(mediaPlayer: MediaPlayer) {
            Log.w(TAG, "NFX VLC : lecture impossible de $uri")
            ui { listener?.onBuffering(false); listener?.onError() }
        }
    }

    private fun ui(block: () -> Unit) {
        if (released) return
        SwingUtilities.invokeLater { if (!released) block() }
    }

    // ---------------------------------------------------------------- lecture

    val isPlaying: Boolean get() = !released && runCatching { player?.status()?.isPlaying == true }.getOrDefault(false)

    val positionMs: Long
        get() = when {
            !started || released -> pendingStartMs
            queuedSeek >= 0 -> queuedSeek
            lastSeekTarget >= 0 && System.currentTimeMillis() - lastSeekAt < 900 -> lastSeekTarget
            else -> runCatching { player?.status()?.time() ?: 0L }.getOrDefault(0L).coerceAtLeast(0L)
        }

    val durationMs: Long
        get() = if (released) 0L else runCatching { player?.status()?.length() ?: 0L }.getOrDefault(0L).coerceAtLeast(0L)

    fun play() {
        if (released) return
        if (!started) { start(); return }
        runCatching { player?.controls()?.play() }
    }

    fun pause() {
        if (released || !started) return
        runCatching { player?.controls()?.setPause(true) }
    }

    private val handler = android.os.Handler()
    private var lastSeekAt = 0L
    private var lastSeekTarget = -1L
    private var queuedSeek = -1L
    private var seekPosted = false

    /** Déplacement : les demandes en rafale sont regroupées, la dernière l'emporte. */
    fun seekTo(ms: Long) {
        if (released) return
        val target = ms.coerceAtLeast(0L)
        if (!started) { pendingStartMs = target; return }
        val wait = SEEK_GAP_MS - (System.currentTimeMillis() - lastSeekAt)
        if (wait <= 0 && !seekPosted) { seekNow(target); return }
        queuedSeek = target
        if (!seekPosted) {
            seekPosted = true
            handler.postDelayed({
                seekPosted = false
                val pending = queuedSeek
                queuedSeek = -1L
                if (pending >= 0 && !released) seekNow(pending)
            }, wait.coerceAtLeast(40L))
        }
    }

    private fun seekNow(target: Long) {
        lastSeekAt = System.currentTimeMillis()
        lastSeekTarget = target
        runCatching { player?.controls()?.setTime(target) }
    }

    private fun start() {
        started = true
        if (player == null) runCatching { build() }.onFailure {
            Log.w(TAG, "NFX VLC introuvable : $it")
            ui { listener?.onError() }
            return
        }
        val p = player ?: return
        val options = buildList {
            if (pendingStartMs > 1_000) add(":start-time=${pendingStartMs / 1000.0}")
            languages(preferredAudio)?.let { add(":audio-language=$it") }
            if (wantSubtitles) languages(preferredSubtitle)?.let { add(":sub-language=$it") }
            if (!wantSubtitles) add(":no-sub-autodetect-file")
        }.toTypedArray()
        listener?.onBuffering(true)
        val parsed = Uri.parse(uri)
        val ok = runCatching {
            when (parsed.scheme) {
                "smb" -> p.media().play(SmbMedia(context, parsed), *options)
                "file" -> p.media().play(File(java.net.URI(uri)).absolutePath, *options)
                null, "" -> p.media().play(uri, *options)
                else -> p.media().play(uri, *options)
            }
        }.getOrDefault(false)
        if (!ok) ui { listener?.onBuffering(false); listener?.onError() }
        runCatching { p.audio().setDelay(audioDelayUs) }
        runCatching { p.subpictures().setDelay(subtitleDelayUs) }
    }

    /** Un fichier du partage réseau, lu morceau par morceau par la bibliothèque SMB de l'application. */
    private class SmbMedia(private val context: Context, private val address: Uri) : SeekableCallbackMedia(256 * 1024) {
        private var file: SmbRandomAccessFile? = null
        private var smb: jcifs.smb.SmbFile? = null

        private fun openFile(): jcifs.smb.SmbFile {
            val shares = NetworkShares(context).all()
            val share = shares.firstOrNull {
                it.host.equals(address.host, ignoreCase = true) &&
                    address.path.orEmpty().trimStart('/').startsWith(it.share, ignoreCase = true)
            } ?: error("partage inconnu")
            val relative = address.path.orEmpty().trimStart('/').removePrefix(share.share).trimStart('/')
            return NetworkShares.open(share, relative)
        }

        override fun onGetSize(): Long = runCatching { (smb ?: openFile().also { smb = it }).length() }.getOrDefault(0L)
        override fun onOpen(): Boolean = runCatching {
            val f = smb ?: openFile().also { smb = it }
            file = SmbRandomAccessFile(f, "r")
            true
        }.getOrDefault(false)
        override fun onRead(buffer: ByteArray, bufferSize: Int): Int = file?.read(buffer, 0, bufferSize) ?: -1
        override fun onSeek(offset: Long): Boolean = runCatching { file?.seek(offset); true }.getOrDefault(false)
        override fun onClose() {
            runCatching { file?.close() }
            runCatching { smb?.close() }
            file = null; smb = null
        }
    }

    private fun languages(code: String): String? {
        val short = code.trim().lowercase().take(2)
        if (short.length < 2) return null
        val long = when (short) {
            "fr" -> listOf("fre", "fra"); "en" -> listOf("eng"); "es" -> listOf("spa"); "de" -> listOf("ger", "deu")
            "it" -> listOf("ita"); "ja" -> listOf("jpn"); "pt" -> listOf("por"); else -> emptyList()
        }
        return (listOf(short) + long + code.trim().lowercase()).distinct().joinToString(",")
    }

    // ----------------------------------------------------------------- pistes

    @Volatile private var audioList: List<TrackOption> = emptyList()
    @Volatile private var subtitleList: List<TrackOption> = emptyList()

    /** Relit les pistes ; appelé sur le fil de VLC, jamais depuis un événement directement. */
    private fun refreshTracks() {
        val p = player ?: return
        if (released) return
        if (!wantSubtitles) runCatching { if (p.subpictures().track() != -1) p.subpictures().setTrack(-1) }
        val languages = runCatching {
            p.media().info().tracks(TrackType.AUDIO, TrackType.TEXT).associate { it.id() to it.language().orEmpty() }
        }.getOrDefault(emptyMap())
        audioList = runCatching { options(p.audio().trackDescriptions(), p.audio().track(), "Piste", languages) }.getOrDefault(emptyList())
        subtitleList = runCatching { options(p.subpictures().trackDescriptions(), p.subpictures().track(), "Sous-titre", languages) }.getOrDefault(emptyList())
        applyAudioRoute()
        ui { listener?.onTracks() }
    }

    private fun options(tracks: List<TrackDescription>, selectedId: Int, fallback: String, languages: Map<Int, String>): List<TrackOption> =
        tracks.filter { it.id() != -1 }.mapIndexed { index, track ->
            TrackOption(
                groupIndex = track.id(),
                trackIndex = 0,
                label = track.description()?.takeIf { it.isNotBlank() } ?: "$fallback ${index + 1}",
                language = languages[track.id()].orEmpty(),
                selected = track.id() == selectedId
            )
        }

    fun audioOptions(): List<TrackOption> = if (released) emptyList() else audioList
    fun subtitleOptions(): List<TrackOption> = if (released) emptyList() else subtitleList

    fun pickAudio(option: TrackOption) {
        player?.submit { runCatching { player?.audio()?.setTrack(option.groupIndex) } }
    }

    fun pickSubtitle(option: TrackOption) {
        wantSubtitles = true
        player?.submit { runCatching { player?.subpictures()?.setTrack(option.groupIndex) } }
    }

    fun disableSubtitles() {
        wantSubtitles = false
        player?.submit { runCatching { player?.subpictures()?.setTrack(-1) } }
    }

    fun offerSubtitles(files: List<File>) { extraSubtitles += files }

    private fun addSlavesOnce() {
        if (slavesAdded) return
        slavesAdded = true
        extraSubtitles.forEach { file ->
            runCatching { player?.media()?.addSlave(MediaSlaveType.SUBTITLE, file.toURI().toString(), false) }
        }
    }

    fun showSubtitle(file: File) {
        wantSubtitles = true
        player?.submit { runCatching { player?.media()?.addSlave(MediaSlaveType.SUBTITLE, file.toURI().toString(), true) } }
    }

    // -------------------------------------------------------------- décalages

    fun setSubtitleDelayMs(ms: Long) {
        subtitleDelayUs = ms * 1000
        runCatching { player?.subpictures()?.setDelay(subtitleDelayUs) }
    }

    fun setAudioDelayMs(ms: Long) {
        audioDelayUs = ms * 1000
        runCatching { player?.audio()?.setDelay(audioDelayUs) }
    }

    // ------------------------------------------------------------ sortie du son

    /**
     * 0 = automatique (sur un PC : décodé), 1 = toujours décodé, 2 = direct vers l'ampli.
     * Changer de sortie pendant la lecture relance le moteur au même endroit.
     */
    fun setAudioMode(mode: Int) {
        if (mode == audioMode) return
        val before = audioMode
        audioMode = mode
        val needsRebuild = (before == AUDIO_DIRECT) != (mode == AUDIO_DIRECT)
        if (started && needsRebuild && !released) {
            val at = positionMs
            val wasPlaying = isPlaying
            teardown()
            started = false
            slavesAdded = false
            routeDirect = null
            pendingStartMs = at
            start()
            if (!wasPlaying) handler.postDelayed({ pause() }, 600)
        } else if (started) {
            player?.submit { applyAudioRoute(force = true) }
        }
    }

    private fun applyAudioRoute(force: Boolean = false) {
        if (released) return
        val codec = currentAudioCodec()
        if (codec.isEmpty() && !force) return
        val direct = audioMode == AUDIO_DIRECT && DIRECT_FORMATS.any { it in codec }
        if (routeDirect == direct && !force) return
        routeDirect = direct
        Log.w(TAG, "NFX VLC son : piste « $codec » → ${if (direct) "direct" else "décodé"}")
        ui { listener?.onAudioRoute(direct) }
    }

    private fun currentAudioCodec(): String = runCatching {
        val p = player ?: return@runCatching ""
        val current = p.audio().track()
        p.media().info().audioTracks().firstOrNull { it.id() == current }
            ?.let { "${it.codecName().orEmpty()} ${it.codecDescription().orEmpty()}".lowercase().trim() }.orEmpty()
    }.getOrDefault("")

    // ------------------------------------------------------------ informations

    fun techLines(sizeBytes: Long, durationMs: Long): List<Pair<String, String>> = buildList {
        val p = player
        runCatching { p?.media()?.info()?.videoTracks()?.firstOrNull() }.getOrNull()?.let { video ->
            if (video.width() > 0 && video.height() > 0) {
                val name = when {
                    video.width() >= 3800 || video.height() >= 2100 -> "4K"
                    video.width() >= 1900 || video.height() >= 1060 -> "1080p"
                    video.width() >= 1260 || video.height() >= 700 -> "720p"
                    else -> "SD"
                }
                add("Image" to "$name  ·  ${video.width()} × ${video.height()}")
            }
            val rate = if (video.frameRateBase() > 0 && video.frameRate() > 0) {
                "%.2f i/s".format(video.frameRate().toFloat() / video.frameRateBase()).replace(",00", "").replace(".00", "")
            } else null
            add("Vidéo" to listOfNotNull(video.codecDescription()?.takeIf { it.isNotBlank() } ?: video.codecName()?.trim(), rate).joinToString("  ·  "))
        }
        runCatching {
            val current = p?.audio()?.track() ?: return@runCatching
            p.media().info().audioTracks().firstOrNull { it.id() == current }?.let { audio ->
                val channels = when (audio.channels()) { 1 -> "Mono"; 2 -> "Stéréo"; 6 -> "5.1"; 8 -> "7.1"; else -> null }
                val rate = audio.rate().takeIf { it > 0 }?.let { "${it / 1000} kHz" }
                add("Son" to listOfNotNull(audio.codecDescription()?.takeIf { it.isNotBlank() } ?: audio.codecName()?.trim(), channels, rate).joinToString("  ·  "))
            }
        }
        if (sizeBytes > 0) {
            val size = if (sizeBytes >= 1_000_000_000L) "%.1f Go".format(sizeBytes / 1e9) else "${sizeBytes / 1_000_000} Mo"
            val flow = if (durationMs > 0) "%.1f Mb/s".format(sizeBytes * 8.0 / (durationMs / 1000.0) / 1e6) else null
            add("Fichier" to listOfNotNull(size, flow).joinToString("  ·  "))
        }
        add("Moteur" to "VLC")
    }

    // ------------------------------------------------------------------- fin

    private fun teardown() {
        val p = player
        val f = factory
        player = null
        factory = null
        Thread {
            runCatching { p?.events()?.removeMediaPlayerEventListener(events) }
            runCatching { p?.controls()?.stop() }
            runCatching { p?.release() }
            runCatching { f?.release() }
        }.apply { name = "vlc-fin"; isDaemon = true }.start()
    }

    fun release() {
        if (released) return
        released = true
        listener = null
        handler.removeCallbacksAndMessages(null)
        teardown()
        Vlc.keepAwake(false)
    }

    init { Vlc.keepAwake(true) }

    private companion object {
        const val TAG = "NFX"
        const val SEEK_GAP_MS = 250L
        const val AUDIO_AUTO = 0
        const val AUDIO_DECODED = 1
        const val AUDIO_DIRECT = 2
        val DIRECT_FORMATS = listOf("a52", "ac3", "ac-3", "eac3", "e-ac", "dts", "dolby")
        const val FILE_CACHE_MS = 1500
        const val NETWORK_CACHE_MS = 3000
        val FONT_SIZES = listOf(20, 16, 13, 10)

        fun fit(w: Int, h: Int, maxW: Int, maxH: Int): Pair<Int, Int> {
            if (w <= 0 || h <= 0) return 1280 to 720
            if (w <= maxW && h <= maxH) return (w and 1.inv()) to (h and 1.inv())
            val ratio = minOf(maxW.toDouble() / w, maxH.toDouble() / h)
            return ((w * ratio).toInt() and 1.inv()).coerceAtLeast(2) to ((h * ratio).toInt() and 1.inv()).coerceAtLeast(2)
        }
    }
}

/**
 * Le dernier tableau de pixels livré par VLC (BGRA), recopié à part : VLC peut réécrire
 * son tampon pendant que l'écran lit le nôtre.
 */
internal class FrameStore {
    @Volatile var width = 0; private set
    @Volatile var height = 0; private set
    private var pixels = ByteArray(0)
    /** Monte à chaque nouvelle image (et à chaque changement de cadrage). */
    val version = mutableLongStateOf(0L)
    @Volatile private var counter = 0L

    @Synchronized fun resize(w: Int, h: Int) {
        width = w; height = h
        pixels = ByteArray(w * h * 4)
    }

    fun write(buffer: ByteBuffer, w: Int, h: Int) {
        synchronized(this) {
            val size = w * h * 4
            if (pixels.size != size) { width = w; height = h; pixels = ByteArray(size) }
            buffer.rewind()
            buffer.get(pixels, 0, minOf(size, buffer.remaining()))
        }
        counter++
        val v = counter
        SwingUtilities.invokeLater { version.longValue = v }
    }

    fun touch() { counter++; val v = counter; SwingUtilities.invokeLater { version.longValue = v } }

    /** Donne les pixels à [use] sous verrou. */
    @Synchronized fun <T> read(use: (ByteArray, Int, Int) -> T): T = use(pixels, width, height)
}
