package com.cinetv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cinetv.data.repo.LibraryRepository
import com.cinetv.data.repo.ScanState
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.ui.theme.Ink

/** Largeur de la pastille d'avancement, en haut à droite. */
private val WORK_WIDTH = 236.dp

/**
 * Tout ce que l'app annonce en haut à droite, sur n'importe quel écran, sans jamais
 * prendre le focus : un disque qui passe en ligne, puis l'avancement des travaux de
 * fond (analyse d'un disque, recherche des jaquettes).
 */
@Composable
fun BoxScope.TopRightNotices(repo: LibraryRepository) {
    Column(
        Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 44.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DriveNotice(repo)
        BackgroundWork(repo)
    }
}

/** Petite pastille : ce qui tourne en arrière-plan, et où ça en est. */
@Composable
private fun ColumnScope.BackgroundWork(repo: LibraryRepository) {
    val scan by repo.scanState.collectAsStateWithLifecycle()
    val metadata by repo.metadataProgress.collectAsStateWithLifecycle()

    val running = scan as? ScanState.Running
    val meta = metadata
    // L'analyse d'un disque passe devant : les jaquettes viennent après elle
    val title: String
    val count: String
    val detail: String
    val fraction: Float?
    when {
        running != null -> {
            title = "ANALYSE"
            count = "${running.filesFound}"
            detail = running.volumeLabel
            fraction = null
        }
        meta != null -> {
            val finished = meta.current.isBlank()
            title = if (finished) "${meta.stage} · TERMINÉ" else meta.stage
            count = "${meta.done} / ${meta.total}"
            detail = if (finished) "${meta.found} trouvées" else meta.current
            fraction = meta.fraction
        }
        else -> { title = ""; count = ""; detail = ""; fraction = null }
    }

    AnimatedVisibility(
        visible = running != null || meta != null,
        enter = fadeIn(tween(240)) + slideInVertically(tween(300)) { -it / 2 },
        exit = fadeOut(tween(260)) + slideOutVertically(tween(260)) { -it / 2 }
    ) {
        val shape = RoundedCornerShape(12.dp)
        Column(
            Modifier
                .width(WORK_WIDTH)
                .clip(shape)
                .background(Ink.Void.copy(alpha = 0.78f))
                .border(0.75.dp, Color.White.copy(alpha = 0.14f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
                    color = Color.White.copy(alpha = 0.60f),
                    maxLines = 1
                )
                Text(
                    count,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(7.dp))
            val shown by animateFloatAsState(fraction ?: 0f, tween(320), label = "workFraction")
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                if (fraction != null) {
                    Box(Modifier.fillMaxWidth(shown).fillMaxHeight().background(Ink.Accent))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                color = Color.White.copy(alpha = 0.50f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
