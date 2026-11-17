package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.cinetv.desktop.Back

/**
 * Comme la touche Retour de la télécommande : Échap, Retour arrière (hors saisie de texte)
 * ou le bouton « précédent » de la souris. Le dernier écran posé répond en premier.
 */
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val entry = remember { Back.Entry() }
    SideEffect {
        entry.enabled = enabled
        entry.action = onBack
    }
    DisposableEffect(entry) {
        Back.register(entry)
        onDispose { Back.unregister(entry) }
    }
}
