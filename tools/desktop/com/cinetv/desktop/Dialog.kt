package com.cinetv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.window.DialogProperties

/**
 * Une boîte de dialogue, posée par-dessus l'écran dans la même fenêtre.
 * Retour (Échap, Retour arrière, bouton « précédent » de la souris) la ferme
 * comme la touche Retour de la télécommande, sauf si son contenu gère Retour lui-même.
 */
@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val entry = remember { Back.Entry() }
    SideEffect {
        entry.enabled = properties.dismissOnBackPress
        entry.action = onDismissRequest
    }
    DisposableEffect(entry) {
        Back.register(entry)
        Dialogs.open++
        onDispose { Back.unregister(entry); Dialogs.open-- }
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = properties.dismissOnClickOutside,
            usePlatformDefaultWidth = properties.usePlatformDefaultWidth
        )
    ) {
        // Les touches tapées dans la boîte passent aussi par l'aiguillage commun (sons, Retour…)
        Box(Modifier.onPreviewKeyEvent { KeyHub.preview(it) }.onKeyEvent { KeyHub.unhandled(it) }) { content() }
    }
}

object Dialogs {
    @Volatile var open: Int = 0
}
