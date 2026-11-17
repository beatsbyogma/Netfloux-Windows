package com.cinetv.desktop

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Native
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy
import java.awt.GraphicsEnvironment
import java.io.File

/**
 * VLC, livré avec l'application (dossier « vlc » à côté du programme).
 * À défaut, un VLC installé sur le PC fait l'affaire.
 */
object Vlc {

    @Volatile private var loaded = false

    /** Le dossier VLC livré avec Netfloux, s'il est là. */
    fun bundledDir(): File? {
        val candidates = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") },
            File(System.getProperty("user.dir"), "build/app-resources/windows/vlc"),
            File(System.getProperty("user.dir"), "vlc")
        )
        return candidates.firstOrNull { File(it, "libvlc.dll").isFile }
    }

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        val dir = bundledDir()
        val found = if (dir != null) {
            runCatching { NativeDiscovery(Bundled(dir.absolutePath)).discover() }.getOrDefault(false)
        } else false
        if (!found) runCatching { NativeDiscovery().discover() }
        Log.w("NFX", "VLC : ${if (found) "livré avec l'application (${dir?.path})" else "celui du PC"}")
        loaded = true
    }

    private class Bundled(private val dir: String) : BaseNativeDiscoveryStrategy(
        arrayOf("libvlc\\.dll", "libvlccore\\.dll"),
        arrayOf("%s\\plugins", "%s\\vlc\\plugins")
    ) {
        override fun supported(): Boolean = true
        override fun discoveryDirectories(): List<String> = listOf(dir)
        override fun setPluginPath(pluginPath: String): Boolean =
            runCatching { Crt.INSTANCE._putenv("VLC_PLUGIN_PATH=$pluginPath") == 0 }.getOrDefault(false)
    }

    @Suppress("FunctionName")
    private interface Crt : Library {
        fun _putenv(assignment: String): Int
        companion object { val INSTANCE: Crt = Native.load("msvcrt", Crt::class.java) }
    }

    // ------------------------------------------------------------- taille des images

    private val screen by lazy {
        runCatching {
            val mode = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.maxOf { it.displayMode.width * 10_000 + it.displayMode.height }
            (mode / 10_000) to (mode % 10_000)
        }.getOrDefault(1920 to 1080)
    }

    /** Les images ne sont jamais copiées plus grandes que l'écran (et jamais au-delà du 1440p). */
    fun maxFrameWidth(): Int = screen.first.coerceIn(1280, 2560)
    fun maxFrameHeight(): Int = screen.second.coerceIn(720, 1440)

    // ------------------------------------------------------------------ veille

    @Suppress("FunctionName")
    private interface Kernel : Library {
        fun SetThreadExecutionState(flags: Int): Int
        companion object { val INSTANCE: Kernel = Native.load("kernel32", Kernel::class.java) }
    }

    private const val ES_CONTINUOUS = 0x80000000.toInt()
    private const val ES_SYSTEM_REQUIRED = 0x00000001
    private const val ES_DISPLAY_REQUIRED = 0x00000002

    private var awake = 0
    private var keeper: Thread? = null

    /** Pendant un film, l'écran ne s'éteint pas et le PC ne se met pas en veille. */
    @Synchronized
    fun keepAwake(on: Boolean) {
        awake = (awake + if (on) 1 else -1).coerceAtLeast(0)
        if (awake > 0 && keeper == null) {
            keeper = Thread {
                // L'état est propre au fil qui le pose : ce fil le garde tant qu'un film est ouvert
                runCatching { Kernel.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS or ES_SYSTEM_REQUIRED or ES_DISPLAY_REQUIRED) }
                try {
                    while (true) Thread.sleep(60_000)
                } catch (_: InterruptedException) {
                }
                runCatching { Kernel.INSTANCE.SetThreadExecutionState(ES_CONTINUOUS) }
            }.apply { isDaemon = true; name = "eveil"; start() }
        } else if (awake == 0) {
            keeper?.interrupt()
            keeper = null
        }
    }
}
