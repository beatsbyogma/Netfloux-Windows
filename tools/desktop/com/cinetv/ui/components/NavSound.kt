package com.cinetv.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import com.cinetv.R
import com.cinetv.desktop.KeyRepeat
import com.cinetv.desktop.Res
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/**
 * Les sons de navigation : un même accord doux (la majeur septième), décliné selon le geste —
 *  - flèche gauche ou droite : l'accord, court ; haut ou bas : renversé et plus grave ;
 *  - Entrée / clic : l'accord qui monte ; Retour : l'accord qui redescend ;
 *  - lancement d'un film : l'accord déployé sur deux octaves.
 * Rien pendant la lecture d'une vidéo.
 */
object NavSound {
    private const val VOLUME = 0.32f
    private const val GAP_MS = 90L

    @Volatile var muted = false

    /** Plusieurs exemplaires de chaque son, pour qu'un son puisse repartir avant la fin du précédent. */
    private class Voice(resId: Int) {
        private val clips = (0 until 3).mapNotNull { runCatching { open(resId) }.getOrNull() }
        private var next = 0
        fun play() {
            if (clips.isEmpty()) return
            val clip = clips[next]
            next = (next + 1) % clips.size
            clip.stop()
            clip.framePosition = 0
            clip.start()
        }
        private fun open(resId: Int): Clip {
            val stream = AudioSystem.getAudioInputStream(java.io.BufferedInputStream(Res.stream(resId)!!))
            return AudioSystem.getClip().apply {
                open(stream)
                runCatching {
                    val gain = getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                    gain.value = (20f * log10(VOLUME)).coerceIn(gain.minimum, gain.maximum)
                }
            }
        }
    }

    private var side: Voice? = null
    private var vertical: Voice? = null
    private var click: Voice? = null
    private var back: Voice? = null
    private var launch: Voice? = null
    private var lastAt = 0L

    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (side != null) return
        Thread {
            side = Voice(R.raw.nav_side)
            vertical = Voice(R.raw.nav_vertical)
            click = Voice(R.raw.nav_click)
            back = Voice(R.raw.nav_back)
            launch = Voice(R.raw.nav_launch)
        }.apply { isDaemon = true; name = "sons" }.start()
        com.cinetv.desktop.Pointer.onClick = { if (!muted) play(click) }
    }

    /** À l'appui d'une touche (déjà filtré sur l'appui par l'aiguillage). */
    fun onKey(event: KeyEvent) {
        if (muted) return
        when (event.key) {
            Key.DirectionLeft, Key.DirectionRight -> arrow(side)
            Key.DirectionUp, Key.DirectionDown -> arrow(vertical)
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> if (KeyRepeat.count == 0) play(click)
            Key.Escape, Key.Back -> if (KeyRepeat.count == 0) play(back)
        }
    }

    fun launch() = play(launch)

    private fun arrow(sound: Voice?) {
        val now = System.currentTimeMillis()
        if (now - lastAt < GAP_MS) return
        lastAt = now
        play(sound)
    }

    private fun play(sound: Voice?) {
        runCatching { sound?.play() }
    }
}

/** Sur la TV, branchait les sons dans chaque fenêtre ; sur le PC, toutes les touches passent déjà par l'aiguillage commun. */
@Composable
fun SoftNavSound() {}
