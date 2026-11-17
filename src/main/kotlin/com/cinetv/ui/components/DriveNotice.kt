package com.cinetv.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cinetv.data.repo.LibraryRepository
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Durée d'affichage de l'annonce « disque en ligne ». */
private const val NOTICE_MS = 5_000L

/** Au lancement, tous les disques « passent en ligne » d'un coup : on n'annonce rien pendant ce délai. */
private const val QUIET_START_MS = 12_000L

/**
 * Un disque doit être resté hors ligne au moins ce temps pour que son retour soit annoncé.
 * Brancher un disque fait parfois « clignoter » les autres une seconde : ceux-là ne sont pas annoncés.
 */
private const val MIN_OFFLINE_MS = 6_000L

/**
 * Annonce, en haut à droite de n'importe quel écran, le disque qui vient d'être
 * branché : une cartouche par disque, et seulement pour celui-là — jamais la liste
 * de tous les disques en ligne. Elle ne prend jamais le focus et s'efface seule.
 *
 * Un disque USB branché pendant que l'app tourne est repéré tout de suite ;
 * un partage réseau l'est à la prochaine vérification (retour à l'accueil).
 */
@Composable
fun ColumnScope.DriveNotice(repo: LibraryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Les annonces à l'écran : identifiant du disque → son nom
    val notices = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, String>>() }

    // À chaque changement de la bibliothèque : quel disque vient réellement d'être branché ?
    LaunchedEffect(Unit) {
        val started = System.currentTimeMillis()
        var online: Set<String>? = null
        val offlineSince = HashMap<String, Long>()
        repo.revision.collectLatest {
            val volumes = runCatching { repo.volumes() }.getOrDefault(emptyList())
            val now = System.currentTimeMillis()
            val before = online
            val current = volumes.filter { it.isConnected }.map { it.id }.toSet()
            online = current
            volumes.forEach { vol ->
                if (vol.isConnected) return@forEach
                offlineSince.putIfAbsent(vol.id, now)
            }
            if (before != null && now - started > QUIET_START_MS) {
                volumes.filter { it.isConnected && it.id !in before }.forEach { vol ->
                    val since = offlineSince[vol.id]
                    // Inconnu jusqu'ici (disque neuf) ou resté hors ligne assez longtemps : c'est un vrai branchement
                    val real = since == null || now - since >= MIN_OFFLINE_MS
                    if (real && notices.none { it.first == vol.id }) {
                        val entry = vol.id to vol.label
                        notices += entry
                        scope.launch { delay(NOTICE_MS); notices -= entry }
                    }
                }
            }
            current.forEach { offlineSince.remove(it) }
        }
    }

    notices.forEach { (id, label) ->
        androidx.compose.runtime.key(id) { DriveNoticePill(label) }
    }
}

@Composable
private fun DriveNoticePill(label: String) {
    // Apparition en glissant depuis le haut ; la disparition est sèche, la cartouche étant retirée de la liste
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260)) + slideInVertically(tween(320)) { -it / 2 },
        exit = fadeOut(tween(260)) + slideOutVertically(tween(260)) { -it / 2 }
    ) {
        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .clip(shape)
                .background(Ink.Void.copy(alpha = 0.78f))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), shape)
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.Online))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "DISQUE EN LIGNE",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 320.dp)
                )
            }
        }
    }
}
