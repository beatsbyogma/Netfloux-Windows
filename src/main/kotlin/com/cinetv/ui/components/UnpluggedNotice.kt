package com.cinetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetv.desktop.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Sheet
import kotlinx.coroutines.delay

/**
 * Fenêtre affichée quand on lance un titre dont le disque est débranché :
 * elle dit quel disque brancher. OK ou Retour la referme.
 */
@Composable
fun UnpluggedNotice(label: String, network: Boolean, onDismiss: () -> Unit) {
    val ok = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(12) {
            if (runCatching { ok.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.cinetv.ui.components.SoftNavSound()
        Box(
            Modifier.fillMaxSize().background(Ink.Void.copy(alpha = 0.78f)),
            contentAlignment = Alignment.Center
        ) {
            val shape = RoundedCornerShape(Sheet.radius)
            Column(
                Modifier
                    .entrance(fromY = 14.dp)
                    .width(380.dp)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Ink.Surface, Ink.Deep)))
                    .border(0.75.dp, Color.White.copy(alpha = 0.08f), shape)
                    .padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.Offline))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (network) "PARTAGE HORS LIGNE" else "DISQUE DÉBRANCHÉ",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    when {
                        label.isBlank() -> "Veuillez brancher le disque de ce titre"
                        network -> "Veuillez rallumer le partage « $label »"
                        else -> "Veuillez brancher le disque « $label »"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.TextHi,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                ActionPill(
                    MenuAction(R.drawable.ic_check, "OK") { onDismiss() },
                    ok, androidx.compose.ui.graphics.SolidColor(Color.White), Color.White, primary = true
                )
            }
        }
    }
}
