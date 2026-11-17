package android.os

import javax.swing.SwingUtilities
import javax.swing.Timer

/** Version d'Android annoncée au code partagé : la plus récente, pour qu'il prenne les chemins modernes. */
object Build {
    object VERSION { const val SDK_INT: Int = 34 }
    object VERSION_CODES {
        const val O = 26; const val P = 28; const val Q = 29; const val R = 30
        const val S = 31; const val TIRAMISU = 33; const val UPSIDE_DOWN_CAKE = 34
    }
    const val MANUFACTURER: String = "PC"
    const val MODEL: String = "Windows"
}

object SystemClock {
    private val start = System.nanoTime()
    fun uptimeMillis(): Long = (System.nanoTime() - start) / 1_000_000
    fun elapsedRealtime(): Long = uptimeMillis()
}

object Process {
    fun myPid(): Int = ProcessHandle.current().pid().toInt()
    fun killProcess(pid: Int) { kotlin.system.exitProcess(0) }
}

object Environment {
    fun isExternalStorageManager(): Boolean = true
}

/** Le fil d'affichage : celui de la fenêtre. */
class Looper private constructor() {
    companion object {
        private val main = Looper()
        fun getMainLooper(): Looper = main
        fun myLooper(): Looper = main
    }
}

/** Pour lancer un bout de code sur le fil d'affichage, tout de suite ou après un délai. */
class Handler(@Suppress("UNUSED_PARAMETER") looper: Looper = Looper.getMainLooper()) {
    private val timers = HashMap<Runnable, MutableList<Timer>>()

    fun post(r: Runnable): Boolean { SwingUtilities.invokeLater(r); return true }

    @Synchronized
    fun postDelayed(r: Runnable, delayMillis: Long): Boolean {
        lateinit var timer: Timer
        timer = Timer(delayMillis.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()) {
            synchronized(this) { timers[r]?.remove(timer) }
            r.run()
        }
        timer.isRepeats = false
        timers.getOrPut(r) { mutableListOf() } += timer
        timer.start()
        return true
    }

    fun postDelayed(r: () -> Unit, delayMillis: Long): Boolean = postDelayed(Runnable { r() }, delayMillis)

    @Synchronized
    fun removeCallbacks(r: Runnable) { timers.remove(r)?.forEach { it.stop() } }

    @Synchronized
    fun removeCallbacksAndMessages(@Suppress("UNUSED_PARAMETER") token: Any?) {
        timers.values.flatten().forEach { it.stop() }
        timers.clear()
    }
}
