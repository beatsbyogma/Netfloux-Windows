package android.widget

import android.content.Context
import com.cinetv.desktop.Toasts

/** Un petit message qui s'affiche quelques secondes en bas de la fenêtre. */
class Toast private constructor(private val text: CharSequence, private val long: Boolean) {
    fun show() = Toasts.show(text.toString(), long)

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1
        fun makeText(context: Context?, text: CharSequence, duration: Int): Toast = Toast(text, duration == LENGTH_LONG)
    }
}
