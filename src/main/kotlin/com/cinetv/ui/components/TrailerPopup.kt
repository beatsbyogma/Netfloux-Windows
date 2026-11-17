package com.cinetv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import java.awt.Desktop
import java.net.URI

/** La bande-annonce s'ouvre sur YouTube, dans le navigateur du PC. */
@Composable
fun TrailerPopup(videoKey: String, onDismiss: () -> Unit) {
    LaunchedEffect(videoKey) {
        runCatching {
            Desktop.getDesktop().browse(URI("https://www.youtube.com/watch?v=" + java.net.URLEncoder.encode(videoKey, Charsets.UTF_8)))
        }.onFailure { android.widget.Toast.makeText(null, "Impossible d'ouvrir le navigateur", android.widget.Toast.LENGTH_SHORT).show() }
        onDismiss()
    }
}
