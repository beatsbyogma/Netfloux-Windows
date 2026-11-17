package com.cinetv.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.repo.LibraryRepository
import com.cinetv.data.storage.StorageAccess
import com.cinetv.ui.components.ActionPill
import com.cinetv.ui.components.CineIcon
import com.cinetv.ui.components.FocusStyle
import com.cinetv.ui.components.MenuAction
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Radius
import com.cinetv.ui.theme.Sheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ouverture du choix des disques, depuis les Paramètres ou au premier lancement. */
object DrivePickerState {
    var open by mutableStateOf(false)
    /** Un disque tout juste branché, mis en avant dans la liste. */
    var highlight by mutableStateOf<String?>(null)

    fun summary(): String {
        val shown = StorageAccess.shown
        val plugged = StorageAccess.drives().count { it.id in shown }
        return when (shown.size) {
            0 -> "Aucun"
            else -> "${shown.size} choisi${if (shown.size > 1) "s" else ""} · $plugged branché${if (plugged > 1) "s" else ""}"
        }
    }
}

private val work = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * « Disques affichés » : la liste des disques du PC, chacun à cocher.
 * Seuls les disques cochés entrent dans Netfloux ; décocher un disque le retire
 * de la bibliothèque (le disque lui-même n'est pas touché).
 */
@Composable
fun DrivePicker(repo: LibraryRepository, onClose: () -> Unit) {
    val drives = remember { StorageAccess.drives(force = true).sortedWith(compareBy({ it.isSystem }, { it.letter })) }
    val before = remember { StorageAccess.shown }
    var chosen by remember { mutableStateOf(before) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(12) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    fun apply() {
        StorageAccess.shown = chosen
        StorageAccess.firstChoiceDone = true
        StorageAccess.known = StorageAccess.known + drives.map { it.id }
        val removed = before - chosen
        val added = chosen - before
        work.launch {
            removed.forEach { runCatching { repo.forgetVolume(it) } }
            if (added.isNotEmpty() || removed.isNotEmpty()) {
                runCatching { repo.scanAll() }
                runCatching { repo.enrichPendingMetadata() }
            }
        }
        onClose()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Ink.Void.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(Sheet.radius)
            Column(
                Modifier
                    .width(520.dp)
                    .heightIn(max = 480.dp)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(Ink.Surface, Ink.Deep)))
                    .border(0.75.dp, Color.White.copy(alpha = 0.08f), shape)
                    .padding(horizontal = 28.dp, vertical = 24.dp)
            ) {
                Text(
                    "DISQUES AFFICHÉS",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
                    color = Color.White.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Choisissez les disques que Netfloux montre. Les autres restent invisibles.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.TextHi
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Décocher un disque retire ses films de Netfloux (vus et reprises compris) ; rien n'est effacé du disque.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.TextLow
                )
                Spacer(Modifier.height(14.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    if (drives.isEmpty()) {
                        Text("Aucun disque détecté.", style = MaterialTheme.typography.bodyMedium, color = Ink.TextMid)
                    }
                    drives.forEachIndexed { index, drive ->
                        DriveRow(
                            drive = drive,
                            checked = drive.id in chosen,
                            highlighted = drive.id == DrivePickerState.highlight,
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            onToggle = { chosen = if (drive.id in chosen) chosen - drive.id else chosen + drive.id }
                        )
                    }
                    // Disques choisis mais débranchés en ce moment : ils restent dans la liste de Netfloux
                    val away = chosen.filter { id -> drives.none { it.id == id } }
                    if (away.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${away.size} disque${if (away.size > 1) "s" else ""} choisi${if (away.size > 1) "s" else ""} mais débranché${if (away.size > 1) "s" else ""} : toujours affiché${if (away.size > 1) "s" else ""}, voilé${if (away.size > 1) "s" else ""} de noir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.TextLow
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionPill(
                        MenuAction(R.drawable.ic_check, "Valider") { apply() },
                        if (drives.isEmpty()) first else null, SolidColor(Color.White), Color.White, primary = true
                    )
                    ActionPill(
                        MenuAction(R.drawable.ic_close, "Annuler") { onClose() },
                        null, SolidColor(Color.White), Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun DriveRow(
    drive: StorageAccess.Drive,
    checked: Boolean,
    highlighted: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = onToggle,
        interactionSource = interaction,
        shape = FocusStyle.shape(Radius.control),
        colors = FocusStyle.colors(container = if (highlighted) Ink.SurfaceHi else Color.Transparent, content = Ink.TextHi),
        scale = FocusStyle.lift(1.01f),
        glow = FocusStyle.glow(),
        border = FocusStyle.noBorder(),
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().background(if (focused) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(20.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (checked) Ink.Violet else Color.Transparent)
                    .border(1.5.dp, if (checked) Ink.Violet else Ink.TextLow, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (checked) CineIcon(R.drawable.ic_check, tint = Ink.Void, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(14.dp))
            CineIcon(R.drawable.ic_disc, tint = Ink.TextMid, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${drive.label} (${drive.letter})",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Ink.TextHi,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(
                        drive.total.takeIf { it > 0 }?.let { "${size(it - drive.free)} utilisés sur ${size(it)}" },
                        drive.fileSystem.takeIf { it.isNotBlank() },
                        if (drive.isSystem) "disque système" else null
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.TextLow
                )
            }
            if (highlighted) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.Online))
                Spacer(Modifier.width(6.dp))
                Text("NOUVEAU", style = MaterialTheme.typography.labelSmall, color = Ink.Online)
            }
        }
    }
}

private fun size(bytes: Long): String = when {
    bytes >= 1_000_000_000_000L -> "%.1f To".format(bytes / 1e12)
    bytes >= 1_000_000_000L -> "%.0f Go".format(bytes / 1e9)
    else -> "${bytes / 1_000_000} Mo"
}
