package android.media

import android.content.Context
import com.cinetv.desktop.Res
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl
import kotlin.math.log10

/** Un son court de l'application (le jingle d'ouverture), joué par la carte son du PC. */
class MediaPlayer private constructor(private val clip: Clip) {
    fun setVolume(left: Float, right: Float) {
        runCatching {
            val gain = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val db = (20f * log10(maxOf(left, right).coerceIn(0.0001f, 1f)))
            gain.value = db.coerceIn(gain.minimum, gain.maximum)
        }
    }
    fun start() { clip.framePosition = 0; clip.start() }
    fun stop() { clip.stop() }
    fun release() { clip.close() }
    val isPlaying: Boolean get() = clip.isRunning

    companion object {
        fun create(context: Context?, resId: Int): MediaPlayer? = runCatching {
            val stream = AudioSystem.getAudioInputStream(java.io.BufferedInputStream(Res.stream(resId)!!))
            val clip = AudioSystem.getClip()
            clip.open(stream)
            MediaPlayer(clip)
        }.getOrNull()
    }
}
