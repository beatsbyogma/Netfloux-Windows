package com.cinetv.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import java.util.Collections
import java.util.WeakHashMap

/**
 * L'aiguillage des touches, avant qu'elles n'atteignent l'écran :
 * compte des répétitions, Retour, plein écran, et le petit son de navigation.
 * Une même touche peut arriver par la fenêtre et par une boîte de dialogue : elle n'est traitée qu'une fois.
 */
object KeyHub {
    /** Le son de navigation pour une touche (branché par [com.cinetv.ui.components.NavSound]). */
    @Volatile var onKey: (KeyEvent) -> Unit = {}
    @Volatile var onFullscreen: () -> Unit = {}

    private val seen: MutableMap<Any, Boolean> = Collections.synchronizedMap(WeakHashMap())
    private val held = HashSet<Key>()

    /** Vrai si la touche a été consommée ici. */
    fun preview(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        val first = seen.put(native, true) == null
        if (!first) return seen[native] == true && consumedLast.contains(native)
        val consumed = handle(event)
        if (consumed) consumedLast.add(native)
        return consumed
    }

    private val consumedLast: MutableSet<Any> = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))

    private fun handle(event: KeyEvent): Boolean {
        when (event.type) {
            KeyEventType.KeyDown -> {
                KeyRepeat.count = if (held.add(event.key)) 0 else KeyRepeat.count + 1
            }
            KeyEventType.KeyUp -> held.remove(event.key)
        }
        if (event.type == KeyEventType.KeyDown) onKey(event)
        return when (event.key) {
            Key.F11 -> { if (event.type == KeyEventType.KeyDown) onFullscreen(); true }
            Key.Escape, Key.Back -> {
                if (event.type == KeyEventType.KeyDown && KeyRepeat.count == 0) Back.dispatch()
                true
            }
            else -> false
        }
    }

    /**
     * Ce qu'aucun élément n'a pris : Retour arrière revient en arrière
     * (une zone de saisie l'aurait gardé pour effacer).
     */
    fun unhandled(event: KeyEvent): Boolean {
        if (event.key == Key.Backspace && event.type == KeyEventType.KeyDown) {
            val native = event.nativeKeyEvent
            if (backspaced.add(native) && KeyRepeat.count == 0) Back.dispatch()
            return true
        }
        return false
    }

    private val backspaced: MutableSet<Any> = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))
}
