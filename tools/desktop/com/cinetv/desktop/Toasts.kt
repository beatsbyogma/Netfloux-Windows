package com.cinetv.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Les petits messages passagers (« Aucune bande-annonce trouvée… »), en bas de la fenêtre. */
object Toasts {
    private var current by mutableStateOf<Pair<String, Long>?>(null)
    private var longOne = false

    fun show(text: String, long: Boolean = false) {
        javax.swing.SwingUtilities.invokeLater {
            longOne = long
            current = text to System.nanoTime()
        }
    }

    @Composable
    fun BoxScope.Host() {
        val shown = current
        LaunchedEffect(shown) {
            if (shown != null) {
                delay(if (longOne) 3500 else 2200)
                if (current == shown) current = null
            }
        }
        AnimatedVisibility(
            visible = shown != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp)
        ) {
            Box(
                Modifier
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xEE16161C))
                    .padding(horizontal = 22.dp, vertical = 11.dp)
            ) {
                Text(
                    shown?.first.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
