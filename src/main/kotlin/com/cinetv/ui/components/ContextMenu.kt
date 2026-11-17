@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.cinetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import com.cinetv.data.storage.TechInfo
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetv.desktop.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Node
import com.cinetv.ui.theme.GenreType
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cinetv.ui.theme.Sheet
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.cinetv.ui.theme.TitleArt
import com.cinetv.ui.theme.Radius

/** Une entrée du menu contextuel. */
data class MenuAction(
    val icon: Int,
    val label: String,
    val hint: String = "",
    val onSelect: () -> Unit
)

/**
 * Les actions proposées sur un titre, les mêmes partout dans l'app.
 * Chaque écran ne fournit que ce qu'il sait faire : une action absente (null)
 * n'apparaît pas.
 */
fun titleActions(
    node: Node,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onForgetResume: (() -> Unit)? = null,
    /** Vrai si le titre figure dans la rangée « Reprendre » sans avoir été entamé (épisode suivant). */
    inResume: Boolean = false,
    onFix: (() -> Unit)? = null,
    onOpenFolder: (() -> Unit)? = null
): List<MenuAction> = buildList {
    val started = node.lastPositionMs > 0
    // Disque débranché : « Lecture » reste proposée — elle dit alors quel disque rebrancher
    add(MenuAction(R.drawable.ic_play, if (started) "Reprendre" else "Lecture", onSelect = onPlay))
    if (started && node.isAvailable) {
        add(MenuAction(R.drawable.ic_replay, "Reprendre depuis le début", onSelect = onRestart))
    }
    if ((started || inResume) && onForgetResume != null) {
        add(MenuAction(R.drawable.ic_close, "Retirer de « Reprendre »", onSelect = onForgetResume))
    }
    if (onFix != null) {
        add(MenuAction(R.drawable.ic_image, "Corriger la fiche", "Jaquette, titre, résumé", onSelect = onFix))
    }
    if (onOpenFolder != null) {
        add(MenuAction(R.drawable.ic_folder_open, "Voir dans son dossier", onSelect = onOpenFolder))
    }
}

/** Ce que montre la fiche : les actions, une demande de nom, ou une confirmation. */
private enum class SheetMode { ACTIONS, RENAME, DELETE }

/**
 * Fiche d'un titre ou d'un dossier, ouverte d'un simple appui : résumé complet,
 * distribution, pastilles techniques, saga, puis les actions. Renommer et
 * supprimer s'y font sur place. Elle s'ouvre au centre en zoomant, sur la page
 * floutée, et se referme en dézoomant.
 */
@Composable
fun ContextMenu(
    node: Node,
    actions: List<MenuAction>,
    onDismiss: () -> Unit,
    /** Appui sur un autre volet de la saga : la fiche bascule dessus. */
    onOpenRelated: (Node) -> Unit = {}
) {
    val library = LocalLibrary.current
    val scope = rememberCoroutineScope()
    // Bande-annonce demandée depuis la fiche : identifiant YouTube, tant que la fenêtre est ouverte
    var trailer by remember { mutableStateOf<String?>(null) }
    val toastContext = androidx.compose.ui.platform.LocalContext.current
    trailer?.let { key -> TrailerPopup(key) { trailer = null } }
    var listed by remember(node.id) { mutableStateOf(node.listedAt > 0) }

    var mode by remember(node.id) { mutableStateOf(SheetMode.ACTIONS) }
    var working by remember(node.id) { mutableStateOf(false) }
    var failure by remember(node.id) { mutableStateOf<String?>(null) }
    var newName by remember(node.id) {
        mutableStateOf(if (node.isDirectory) node.name else node.name.substringBeforeLast('.'))
    }

    // Lus en arrière-plan : la fiche s'affiche tout de suite, les pastilles suivent
    val tech by produceState<TechInfo?>(null, node.id) {
        value = if (node.isDirectory) null else library?.techInfo(node)
    }
    val saga by produceState(emptyList<Node>(), node.id, node.collectionId) {
        value = if (node.collectionId > 0) library?.saga(node).orEmpty() else emptyList()
    }

    // Une boîte de dialogue, et non une simple surcouche : sans fenêtre propre,
    // la télécommande continuait de naviguer dans la grille restée derrière.
    // La page derrière : photographiée à l'ouverture, puis floutée
    val screenShot = LocalScreenShot.current
    val frosted by produceState<ImageBitmap?>(null) {
        value = screenShot?.invoke()?.let { shot -> withContext(Dispatchers.Default) { soften(shot) } }
    }

    // Ouverture en zoom ; Retour dézoome avant de fermer pour de bon
    val zoom = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            zoom.animateTo(0f, tween(Sheet.zoomOutMs, easing = FastOutSlowInEasing))
            onDismiss()
        } else {
            zoom.animateTo(1f, spring(dampingRatio = Sheet.zoomBounce, stiffness = Sheet.zoomStiffness))
        }
    }

    Dialog(
        onDismissRequest = { if (mode == SheetMode.ACTIONS) closing = true else { mode = SheetMode.ACTIONS; failure = null } },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        com.cinetv.ui.components.SoftNavSound()
    val firstItem = remember { FocusRequester() }
    // Le système assombrit d'office ce qui est derrière une fenêtre : on lui
    // retire ce voile pour doser nous-mêmes la transparence.
    val dialogView = LocalView.current
    SideEffect { (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
    // La demande de focus échoue tant que le contenu n'est pas attaché :
    // on réessaie quelques images de suite plutôt qu'une fois.
    LaunchedEffect(node.id, mode) {
        repeat(12) {
            if (runCatching { firstItem.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Fond : la page floutée, puis un voile noir — les deux arrivent en fondu
        frosted?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) }
                    .blur(Sheet.blur)
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = zoom.value.coerceIn(0f, 1f) }
                .background(Ink.Void.copy(alpha = Sheet.pageDim))
        )

        // Les couleurs du film : en dégradé sur certains textes et sur le bouton sélectionné
        val art = node.backdropUrl.ifBlank { node.posterUrl }
        val filmBrush = rememberTitleBrush(art)
        val filmAccent = rememberTitleAccent(art)
        val shape = RoundedCornerShape(Sheet.radius)
        Column(
            Modifier
                // Le zoom : la fenêtre grossit depuis son centre en apparaissant
                .graphicsLayer {
                    val z = zoom.value
                    val s = Sheet.zoomFrom + (1f - Sheet.zoomFrom) * z
                    scaleX = s; scaleY = s
                    alpha = (z * 1.6f).coerceIn(0f, 1f)
                }
                .width(Sheet.width)
                .heightIn(max = Sheet.maxHeight)
                .clip(shape)
                .background(Ink.Void.copy(alpha = Sheet.panelAlpha))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            // En haut : la jaquette entière à gauche, le titre et son résumé à droite
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.width(Sheet.posterWidth)) {
                    Poster(
                        url = node.posterUrl,
                        corner = Radius.tile,
                        modifier = Modifier.width(Sheet.posterWidth).height(Sheet.posterWidth * 1.5f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CineIcon(
                                if (node.isDirectory) R.drawable.ic_folder_open else R.drawable.ic_movie,
                                tint = Ink.TextLow,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        node.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink.TextLow,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    val face = GenreType.faceFor(node.genres)
                    val base = MaterialTheme.typography.headlineSmall
                    TitleLogo(
                        url = node.logoUrl,
                        maxWidth = TitleArt.sheetWidth,
                        maxHeight = TitleArt.sheetHeight
                    ) {
                        FitText(
                            text = if (face.uppercase) node.displayTitle.uppercase() else node.displayTitle,
                            style = base.copy(
                                fontFamily = face.family,
                                fontSize = base.fontSize * face.scale * 1.15f,
                                lineHeight = base.fontSize * face.scale * 1.15f * 1.10f,
                                letterSpacing = face.spacing.sp,
                                brush = filmBrush
                            ),
                            maxLines = 3,
                            minScale = 0.6f
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // L'essentiel en cartouches, comme sur les bannières
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (node.rating > 0f) OutlineTag(String.format("%.1f", node.rating), star = true)
                        if (node.year > 0) OutlineTag(node.year.toString())
                        genreLabel(node.genres)?.let { OutlineTag(it) }
                        if (node.isDirectory) OutlineTag("${node.childCount} éléments")
                        if (node.progressFraction > 0.01f) {
                            OutlineTag("Repris à ${(node.progressFraction * 100).toInt()} %", tint = filmAccent)
                        }
                        if (!node.isAvailable) OutlineTag("Débranché", tint = Ink.Warn)
                    }

                    // Pastilles techniques : ce que contient réellement le fichier
                    tech?.takeIf { !it.isEmpty }?.let { info ->
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (info.resolution.isNotBlank()) OutlineTag(info.resolution, tint = filmAccent)
                            if (info.hdr.isNotBlank()) OutlineTag(info.hdr, tint = filmAccent)
                            info.audio.take(3).forEach { OutlineTag(it) }
                            if (info.audio.size > 3) OutlineTag("+${info.audio.size - 3}")
                        }
                        if (info.subtitles.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "SOUS-TITRES  " + info.subtitles.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Ink.TextLow,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (node.synopsis.isNotBlank() && mode == SheetMode.ACTIONS) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            node.synopsis,
                            style = MaterialTheme.typography.bodyMedium.let { it.copy(lineHeight = it.fontSize * 1.42f) },
                            color = Color.White.copy(alpha = 0.74f),
                            maxLines = if (saga.isEmpty()) Sheet.synopsisLines else Sheet.synopsisLines - 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (mode == SheetMode.ACTIONS) CreditsBlock(node, Modifier.padding(top = 10.dp), accent = filmAccent, compact = true)
                }
            }

            Spacer(Modifier.height(18.dp))

            // En bas, sur toute la largeur : les actions
            when (mode) {
                SheetMode.ACTIONS -> {
                    // Une rangée de boutons, de gauche à droite ; elle passe à la ligne au besoin
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        actions.forEachIndexed { index, action ->
                            ActionPill(action, if (index == 0) firstItem else null, filmBrush, filmAccent, primary = index == 0)
                        }
                        // Mettre de côté : le titre rejoint la rangée « Ma liste » de l'accueil
                        if (library != null) {
                            ActionPill(
                                MenuAction(
                                    if (listed) R.drawable.ic_check else R.drawable.ic_sparkle,
                                    if (listed) "Dans ma liste" else "Ma liste"
                                ) {
                                    listed = !listed
                                    val now = listed
                                    scope.launch { library.setListed(node.id, now) }
                                },
                                null, filmBrush, filmAccent
                            )
                        }
                        // La bande-annonce, dans sa propre fenêtre
                        if (library != null && node.tmdbId > 0) {
                            ActionPill(
                                MenuAction(R.drawable.ic_movie, "Bande-annonce") {
                                    scope.launch {
                                        val found = library.trailerKey(node)
                                        if (found != null) trailer = found
                                        else android.widget.Toast.makeText(
                                            toastContext, "Aucune bande-annonce trouvée pour ce titre", android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                null, filmBrush, filmAccent
                            )
                        }
                        // Renommer et supprimer : proposés partout, sur tout élément joignable
                        if (library != null && node.isAvailable) {
                            ActionPill(
                                MenuAction(R.drawable.ic_sort, "Renommer") { failure = null; mode = SheetMode.RENAME },
                                if (actions.isEmpty()) firstItem else null, filmBrush, filmAccent
                            )
                            ActionPill(
                                MenuAction(R.drawable.ic_close, "Supprimer") { failure = null; mode = SheetMode.DELETE },
                                null, filmBrush, filmAccent
                            )
                        }
                    }

                    if (saga.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "SAGA  ·  " + node.collectionName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.Lilac,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(saga, key = { it.id }) { other -> SagaPoster(other) { onOpenRelated(other) } }
                        }
                    }
                }

                SheetMode.RENAME -> {
                    Text("NOUVEAU NOM", style = MaterialTheme.typography.labelSmall, color = Ink.Lilac)
                    Spacer(Modifier.height(5.dp))
                    BasicTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.TextHi),
                        cursorBrush = SolidColor(Ink.Lilac),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(firstItem)
                            .clip(RoundedCornerShape(Radius.control))
                            .background(Ink.SurfaceHi)
                            .border(1.dp, Ink.Violet, RoundedCornerShape(Radius.control))
                            .padding(horizontal = 11.dp, vertical = 9.dp)
                    )
                    if (!node.isDirectory) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "L'extension .${node.name.substringAfterLast('.', "")} est conservée",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.TextLow
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    MenuRow(MenuAction(R.drawable.ic_check, if (working) "Renommage…" else "Valider") {
                        if (!working && library != null) {
                            working = true
                            scope.launch {
                                failure = library.renameForReal(node, newName)
                                working = false
                                if (failure == null) onDismiss()
                            }
                        }
                    })
                    MenuRow(MenuAction(R.drawable.ic_close, "Annuler") { mode = SheetMode.ACTIONS; failure = null })
                }

                SheetMode.DELETE -> {
                    Text(
                        if (node.isDirectory)
                            "Supprimer ce dossier et tout ce qu'il contient (${node.childCount} éléments directs) ?"
                        else "Supprimer ce fichier du disque ?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink.TextHi
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "C'est définitif : il n'y a pas de corbeille.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.Warn
                    )
                    Spacer(Modifier.height(10.dp))
                    // « Annuler » en premier : c'est lui qui reçoit le focus
                    MenuRow(MenuAction(R.drawable.ic_check, "Annuler") { mode = SheetMode.ACTIONS; failure = null }, firstItem)
                    MenuRow(MenuAction(R.drawable.ic_close, if (working) "Suppression…" else "Supprimer définitivement") {
                        if (!working && library != null) {
                            working = true
                            scope.launch {
                                failure = library.deleteForReal(node)
                                working = false
                                if (failure == null) onDismiss()
                            }
                        }
                    })
                }
            }

            failure?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelLarge, color = Ink.Warn)
                // Android n'accorde l'écriture sur les disques qu'après un réglage fait à la main :
                // on y conduit directement, plutôt que de laisser chercher dans les menus de la TV.
                if (it == com.cinetv.data.storage.FileOps.NEEDS_ACCESS) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Activez « Accès à tous les fichiers » pour Netfloux, puis revenez ici et recommencez.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink.TextMid
                    )
                    Spacer(Modifier.height(8.dp))
                    MenuRow(MenuAction(R.drawable.ic_settings, "Ouvrir le réglage de la TV") {
                        openAllFilesAccess(toastContext)
                    })
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "RETOUR POUR " + if (mode == SheetMode.ACTIONS) "FERMER" else "REVENIR",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.TextLow
            )
        }
    }
    }
}

/** Un autre volet de la saga : son affiche, en petit. */
@Composable
private fun SagaPoster(node: Node, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box {
        FocusHalo(focused, RoundedCornerShape(Radius.tile))
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = FocusStyle.shape(Radius.tile),
            colors = FocusStyle.colors(container = Ink.SurfaceHi),
            scale = FocusStyle.lift(1.06f),
            glow = FocusStyle.glow(),
            border = FocusStyle.noBorder(),
            modifier = Modifier.width(52.dp).height(78.dp)
        ) {
            Poster(url = node.posterUrl, corner = 0.dp, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        node.year.takeIf { it > 0 }?.toString() ?: "?",
                        style = MaterialTheme.typography.labelSmall,
                        color = Ink.TextLow
                    )
                }
            }
        }
    }
}

/** Petit panneau d'actions sans fiche : un titre, une précision, des choix. */
@Composable
fun SimpleMenu(
    title: String,
    subtitle: String,
    actions: List<MenuAction>,
    onDismiss: () -> Unit
) {
    val firstItem = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(12) {
            if (runCatching { firstItem.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.cinetv.ui.components.SoftNavSound()
        Box(
            Modifier.fillMaxSize().background(Ink.Void.copy(alpha = 0.72f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                Modifier
                    .entrance(fromX = Motion.panelSlide)
                    .width(400.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Ink.Surface, Ink.Deep)))
                    .padding(horizontal = 30.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Ink.TextHi)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Ink.TextLow)
                Spacer(Modifier.height(18.dp))
                actions.forEachIndexed { index, action ->
                    MenuRow(action, if (index == 0) firstItem else null, index)
                    Spacer(Modifier.height(3.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("RETOUR POUR FERMER", style = MaterialTheme.typography.labelSmall, color = Ink.TextLow)
            }
        }
    }
}

@Composable
private fun MenuRow(
    action: MenuAction,
    focusRequester: FocusRequester? = null,
    order: Int = 0,
    /** Remplissage de la ligne sélectionnée ; par défaut le dégradé de la maison. */
    brush: Brush = Ink.Brand,
    accent: Color = Ink.Lilac,
    /** Couleur du texte posé sur ce remplissage. */
    onBrush: Color = Color.White
) {
    // Les lignes arrivent l'une après l'autre
    Box(Modifier.entrance(fromX = Motion.menuSlide, delayMs = 90 + order.coerceAtMost(8) * Motion.menuStaggerMs)) {
    MenuRowBody(action, focusRequester, brush, accent, onBrush)
    }
}

/**
 * Le fond de la fiche : la photo de l'écran, réduite à une vignette puis
 * lissée plusieurs fois. Ré-agrandie à l'écran, elle donne un flou profond.
 */
private fun soften(shot: ImageBitmap): ImageBitmap? = runCatching {
    // La photo vit dans la mémoire graphique : on en fait d'abord une copie lisible
    val readable = shot.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    // Réduction en deux temps, pour moyenner les pixels au lieu d'en sauter
    val half = Bitmap.createScaledBitmap(readable, (readable.width / 4).coerceAtLeast(8), (readable.height / 4).coerceAtLeast(8), true)
    val width = Sheet.blurWidth
    val height = (width * readable.height / readable.width.coerceAtLeast(1)).coerceAtLeast(8)
    val small = Bitmap.createScaledBitmap(half, width, height, true).copy(Bitmap.Config.ARGB_8888, true)

    val pixels = IntArray(width * height)
    small.getPixels(pixels, 0, width, 0, 0, width, height)
    repeat(Sheet.blurPasses) { smooth(pixels, width, height, Sheet.blurRadius) }
    small.setPixels(pixels, 0, width, 0, 0, width, height)
    small.asImageBitmap()
}.getOrNull()

/** Un passage de flou : chaque pixel devient la moyenne de ses voisins, en largeur puis en hauteur. */
private fun smooth(pixels: IntArray, width: Int, height: Int, radius: Int) {
    val line = IntArray(maxOf(width, height))
    fun pass(count: Int, length: Int, at: (Int, Int) -> Int) {
        for (row in 0 until count) {
            for (i in 0 until length) line[i] = pixels[at(row, i)]
            for (i in 0 until length) {
                var r = 0; var g = 0; var b = 0; var n = 0
                for (k in (i - radius).coerceAtLeast(0)..(i + radius).coerceAtMost(length - 1)) {
                    val c = line[k]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                pixels[at(row, i)] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
    }
    pass(height, width) { row, i -> row * width + i }
    pass(width, height) { column, i -> i * width + column }
}

/**
 * Un bouton d'action de la fiche : liseré fin au repos, entièrement rempli des
 * couleurs du film une fois sélectionné. Le premier (lecture) a un liseré teinté.
 */
@Composable
internal fun ActionPill(
    action: MenuAction,
    focusRequester: FocusRequester?,
    brush: Brush,
    accent: Color,
    primary: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = action.onSelect,
        interactionSource = interaction,
        shape = FocusStyle.shape(50.dp),
        colors = FocusStyle.colors(container = Color.Transparent, content = Ink.TextHi),
        scale = FocusStyle.lift(Sheet.rowFocusScale + 0.03f),
        glow = FocusStyle.glow(),
        border = FocusStyle.noBorder(),
        modifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
    ) {
        Row(
            Modifier
                .then(
                    if (focused) Modifier.background(brush, shape)
                    else Modifier.border(
                        0.75.dp,
                        if (primary) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = Sheet.tagBorder),
                        shape
                    )
                )
                .padding(horizontal = 15.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CineIcon(action.icon, tint = if (focused) Ink.Void else accent, modifier = Modifier.size(12.dp))
            Text(
                action.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = if (focused) Ink.Void else Color.White,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/** Une cartouche au liseré fin, sans fond. */
@Composable
internal fun OutlineTag(text: String, tint: Color = Color.White, star: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .border(0.75.dp, tint.copy(alpha = Sheet.tagBorder), shape)
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (star) {
            Text("★", style = MaterialTheme.typography.labelSmall, color = Ink.LogoRed)
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
            color = tint.copy(alpha = 0.92f),
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun MenuRowBody(
    action: MenuAction,
    focusRequester: FocusRequester? = null,
    brush: Brush = Ink.Brand,
    accent: Color = Ink.Lilac,
    onBrush: Color = Color.White
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // Sélectionnée, la ligne se remplit entièrement : on ne peut pas la manquer
    val ink = if (focused) onBrush else Ink.TextHi
    Surface(
        onClick = action.onSelect,
        interactionSource = interaction,
        shape = FocusStyle.shape(Radius.control),
        colors = FocusStyle.colors(container = Color.Transparent, content = Ink.TextHi),
        scale = FocusStyle.lift(Sheet.rowFocusScale),
        glow = FocusStyle.glow(Ink.Violet, 12.dp),
        border = FocusStyle.noBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (focused) Modifier.background(brush) else Modifier)
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            CineIcon(action.icon, tint = if (focused) onBrush else accent, modifier = Modifier.size(15.dp))
            Column {
                Text(action.label, style = MaterialTheme.typography.titleMedium, color = ink)
                if (action.hint.isNotBlank()) {
                    Text(
                        action.hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (focused) onBrush.copy(alpha = 0.72f) else Ink.TextLow
                    )
                }
            }
        }
    }
}


/**
 * Ouvre le réglage Android « Accès à tous les fichiers » de Netfloux — celui qui permet
 * de supprimer et de renommer sur un disque USB. Selon la TV, l'écran direct n'existe pas :
 * on tente alors la liste générale, puis la page de l'application.
 */
private fun openAllFilesAccess(context: android.content.Context) {
    android.widget.Toast.makeText(context, "Vérifiez que le disque n'est pas en lecture seule", android.widget.Toast.LENGTH_LONG).show()
}


/**
 * « Quitter Netfloux ? » — demandé quand on appuie sur Retour depuis l'accueil.
 * Toute l'application reste visible derrière, floutée. « Rester » a le focus :
 * un second appui sur Retour referme simplement la fenêtre.
 */
@Composable
fun QuitDialog(onQuit: () -> Unit, onDismiss: () -> Unit) {
    val screenShot = LocalScreenShot.current
    val frosted by produceState<ImageBitmap?>(null) {
        value = screenShot?.invoke()?.let { shot -> withContext(Dispatchers.Default) { soften(shot) } }
    }
    val stay = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(260)) }
    LaunchedEffect(Unit) {
        repeat(12) {
            if (runCatching { stay.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.cinetv.ui.components.SoftNavSound()
        val dialogView = LocalView.current
        SideEffect { (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            frosted?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = appear.value }
                        .blur(Sheet.blur)
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = appear.value }
                    .background(Ink.Void.copy(alpha = 0.55f))
            )
            val shape = RoundedCornerShape(Sheet.radius)
            Column(
                Modifier
                    .graphicsLayer {
                        val z = appear.value
                        val scale = 0.92f + 0.08f * z
                        scaleX = scale; scaleY = scale
                        alpha = z
                    }
                    .width(360.dp)
                    .clip(shape)
                    .background(Ink.Void.copy(alpha = Sheet.panelAlpha))
                    .border(0.75.dp, Color.White.copy(alpha = 0.08f), shape)
                    .padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Quitter Netfloux ?", style = MaterialTheme.typography.headlineSmall, color = Ink.TextHi)
                Spacer(Modifier.height(6.dp))
                Text(
                    "L'application s'arrête complètement.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.TextLow
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionPill(
                        MenuAction(R.drawable.ic_back, "Rester") { onDismiss() },
                        stay, SolidColor(Color.White), Color.White, primary = true
                    )
                    ActionPill(
                        MenuAction(R.drawable.ic_close, "Quitter") { onQuit() },
                        null, SolidColor(Color.White), Color.White
                    )
                }
            }
        }
    }
}
