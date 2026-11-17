package com.cinetv.desktop

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent

/** La souris : un clic joue le petit son de validation, comme la touche OK. */
object Pointer {
    @Volatile var onClick: () -> Unit = {}
    fun clicked() = onClick()
}

/** Clic droit = appui long (ouvre le menu d'un film, d'un disque…). */
@OptIn(ExperimentalComposeUiApi::class)
fun secondaryClick(action: () -> Unit): Modifier =
    Modifier.onPointerEvent(PointerEventType.Release) { e ->
        if (e.button == PointerButton.Secondary) action()
    }

/** Combien de fois la touche tenue s'est répétée (0 au premier appui), comme sur Android. */
object KeyRepeat {
    @Volatile var count: Int = 0
}
