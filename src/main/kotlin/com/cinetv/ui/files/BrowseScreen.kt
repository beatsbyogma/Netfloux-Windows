package com.cinetv.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Crumb
import com.cinetv.data.model.DriveTheme
import com.cinetv.data.model.Node
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.compose.ui.unit.sp
import com.cinetv.ui.components.*
import com.cinetv.ui.theme.Grid
import com.cinetv.ui.theme.Hero
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion
import com.cinetv.ui.theme.Radius
import com.cinetv.ui.theme.Tiles
import com.cinetv.ui.theme.skinFor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SIDE = Grid.side

@Composable
fun BrowseScreen(
    viewModel: BrowseViewModel,
    onOpenFolder: (Node) -> Unit,
    onPlay: (Node) -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val highlighted by viewModel.highlighted.collectAsStateWithLifecycle()
    val columnCount by viewModel.columns.collectAsStateWithLifecycle()
    val gridGap by viewModel.gap.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val hideWatched by viewModel.hideWatched.collectAsStateWithLifecycle()
    val metadata by viewModel.metadataProgress.collectAsStateWithLifecycle()
    val menuTarget by viewModel.menuTarget.collectAsStateWithLifecycle()
    val relink by viewModel.relink.collectAsStateWithLifecycle()
    val spotlightOf by viewModel.spotlightOf.collectAsStateWithLifecycle()

    val theme = ui.volume?.theme ?: DriveTheme.AUTRE
    val accent = skinFor(theme).primary
    // Sur un disque de séries, chaque dossier est une série : on montre son affiche
    val foldersAsPosters = theme == DriveTheme.SERIES
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Arrivée dans un dossier : toujours sur le premier élément de la première ligne.
    // Au retour d'un sous-dossier ou d'une lecture, on retrouve la vignette quittée.
    var lastId by rememberSaveable { mutableStateOf<String?>(null) }
    var landed by remember { mutableStateOf(false) }
    val anchor = remember { FocusRequester() }
    val firstId = ui.folders.firstOrNull()?.id ?: ui.files.firstOrNull()?.id
    val known = lastId?.takeIf { id -> ui.folders.any { it.id == id } || ui.files.any { it.id == id } }
    val anchorId = if (landed) null else (known ?: firstId)
    fun Modifier.anchorIf(id: String): Modifier = if (id == anchorId) focusRequester(anchor) else this

    LaunchedEffect(ui.loading, anchorId) {
        if (landed || ui.loading || anchorId == null) return@LaunchedEffect
        if (known == null) listState.scrollToItem(0)
        repeat(15) {
            if (runCatching { anchor.requestFocus() }.isSuccess) {
                landed = true
                return@LaunchedEffect
            }
            delay(40)
        }
        landed = true
    }

    BackHandler(enabled = menuTarget != null || relink != null) {
        if (relink != null) viewModel.closeRelink() else viewModel.closeMenu()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
    ) {
        val heroHeight = (maxHeight * Hero.heightFraction).coerceIn(Hero.minHeight, Hero.maxHeight)
        val usable = maxWidth - SIDE * 2

        // Colonnes et espacement se règlent dans le dossier (ou dans les Paramètres) ;
        // la largeur des vignettes s'en déduit, pour remplir exactement la ligne.
        val columns = columnCount
        val gap = gridGap.size
        val cardWidth = (usable - gap * (columns - 1)) / columns
        val cardHeight = cardWidth * Tiles.posterAspect

        Column(Modifier.fillMaxSize()) {

            // Bandeau de tête : hauteur constante, contenu variable
            HeroPanel(
                node = highlighted,
                breadcrumb = { Breadcrumb(ui.crumbs, accent) },
                // Sur un disque de séries la bannière présente la série elle-même : pas de mention
                caption = if (foldersAsPosters) null else spotlightOf?.let { "Au hasard dans · $it" },
                modifier = Modifier.fillMaxWidth().height(heroHeight)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (ui.folders.isNotEmpty()) {
                    item("foldersTitle") { SectionHeader("Dossiers", ui.folders.size) }
                    val folderRows = ui.folders.chunked(columns)
                    items(folderRows.size, key = { "fold_$it" }) { rowIndex ->
                        GridRow(gap) {
                            folderRows[rowIndex].forEach { folder ->
                                FolderCard(
                                    node = folder,
                                    posters = ui.folderPosters[folder.id].orEmpty(),
                                    accent = accent,
                                    width = cardWidth,
                                    height = cardHeight,
                                    singlePoster = foldersAsPosters,
                                    onClick = { onOpenFolder(folder) },
                                    // Appui long : la fiche du dossier (jaquette, renommer, supprimer)
                                    onLongClick = { viewModel.openMenu(folder) },
                                    onFocused = {
                                        if (landed) lastId = folder.id
                                        viewModel.highlightFolder(folder)
                                    },
                                    modifier = Modifier.anchorIf(folder.id),
                                    progress = if (foldersAsPosters) ui.folderProgress[folder.id] else null,
                                    seasons = ui.folderSeasons[folder.id]
                                )
                            }
                        }
                    }
                }

                if (ui.files.isNotEmpty()) {
                    item("filesTitle") {
                        // Les boutons s'arrêtent au bord droit de la dernière vignette
                        SectionHeader(
                            "Vidéos", ui.files.size,
                            contentWidth = usable
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ToolButton(R.drawable.ic_grid, "$columns par ligne") { viewModel.cycleColumns() }
                                ToolButton(R.drawable.ic_aspect, gridGap.label) { viewModel.cycleGap() }
                                ToolButton(R.drawable.ic_sort, sort.label) { viewModel.cycleSort() }
                                ToolButton(
                                    icon = R.drawable.ic_eye,
                                    label = if (hideWatched) "Non vus" else "Tous",
                                    active = hideWatched
                                ) { viewModel.toggleHideWatched() }
                            }
                        }
                    }

                    // Saut alphabétique : utile dès que le dossier devient long
                    if (ui.files.size > Grid.alphabetThreshold) {
                        item("alphabet") {
                            AlphabetStrip(
                                files = ui.files,
                                columns = columns,
                                headerRows = if (ui.folders.isNotEmpty()) 2 + ui.folders.chunked(columns).size else 1,
                                onJump = { index -> scope.launch { listState.animateScrollToItem(index) } }
                            )
                        }
                    }

                    val rows = ui.files.chunked(columns)
                    items(rows.size, key = { "row_$it" }) { rowIndex ->
                        GridRow(gap) {
                            rows[rowIndex].forEach { file ->
                                FileCard(
                                    node = file,
                                    width = cardWidth,
                                    height = cardHeight,
                                    // Un appui ouvre la fiche ; la lecture se lance depuis elle
                                    onClick = { viewModel.openMenu(file) },
                                    onLongClick = { viewModel.openMenu(file) },
                                    onFocused = {
                                        if (landed) lastId = file.id
                                        viewModel.highlight(file)
                                    },
                                    modifier = Modifier.anchorIf(file.id)
                                )
                            }
                        }
                    }
                }

                if (!ui.loading && ui.files.isEmpty() && ui.folders.isEmpty()) {
                    item("empty") { EmptyFolder(hideWatched) }
                }

                item("tail") { Spacer(Modifier.height(56.dp)) }
            }
        }


        menuTarget?.let { target ->
            ContextMenu(
                node = target,
                actions = if (target.isDirectory) {
                    listOf(
                        MenuAction(R.drawable.ic_folder_open, "Ouvrir") {
                            viewModel.closeMenu(); onOpenFolder(target)
                        },
                        MenuAction(R.drawable.ic_image, "Choisir la jaquette") { viewModel.openRelink(target) },
                        MenuAction(
                            R.drawable.ic_disc,
                            if (viewModel.isShownAsDrive(target)) "Ne plus afficher comme un disque"
                            else "Afficher comme un disque",
                            "Sur l'accueil, avec son propre thème"
                        ) { viewModel.toggleShownAsDrive(target); viewModel.closeMenu() },
                        MenuAction(
                            R.drawable.ic_eye,
                            "Cacher ce dossier",
                            "Se réaffiche depuis Paramètres › Bibliothèque"
                        ) { viewModel.hideFolder(target); viewModel.closeMenu() }
                    )
                } else {
                    titleActions(
                        node = target,
                        onPlay = { viewModel.closeMenu(); onPlay(target) },
                        onRestart = { viewModel.restartFrom(target) { onPlay(target) } },
                        onForgetResume = { viewModel.forgetResume(target) },
                        onFix = { viewModel.openRelink(target) }
                    )
                },
                onDismiss = { viewModel.closeMenu() },
                onOpenRelated = { other -> viewModel.openMenu(other) }
            )
        }

        relink?.let { state ->
            ArtworkSearchDialog(
                initialQuery = state.query,
                results = state.results,
                searching = state.searching,
                onQueryChange = { viewModel.search(it) },
                onPick = { hit, artworkOnly -> viewModel.applyRelink(hit, artworkOnly) },
                onDismiss = { viewModel.closeRelink() }
            )
        }
    }
}

@Composable
private fun GridRow(gap: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            // Chaque ligne de la grille se révèle en montant
            .entrance(fromY = Motion.rowRise)
            .fillMaxWidth()
            .padding(horizontal = SIDE, vertical = gap / 2),
        horizontalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}

@Composable
private fun SectionHeader(
    label: String,
    count: Int,
    /** Largeur de la grille en dessous : l'en-tête s'y aligne. Sinon toute la largeur. */
    contentWidth: Dp? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .padding(start = SIDE, end = SIDE, top = 20.dp, bottom = 4.dp)
            .then(if (contentWidth != null) Modifier.width(contentWidth) else Modifier.fillMaxWidth()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Même écriture que les rubriques de l'accueil : filet rouge, petites capitales espacées
        Box(Modifier.width(HeroInfo.ruleWidth).height(1.dp).background(Ink.LogoRed))
        Spacer(Modifier.width(10.dp))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
            color = Color.White.copy(alpha = HeroInfo.overlineAlpha)
        )
        Spacer(Modifier.width(12.dp))
        Text("$count", style = MaterialTheme.typography.labelSmall, color = Ink.TextLow)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Petit bouton de la barre d'outils : blanc très transparent, rouge une fois sélectionné. */
@Composable
private fun ToolButton(icon: Int, label: String, active: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ink = if (focused || active) Color.White else Color.White.copy(alpha = 0.72f)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = FocusStyle.shape(50.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (active) 0.16f else 0.07f),
            contentColor = ink,
            focusedContainerColor = Ink.LogoRed,
            focusedContentColor = Color.White,
            pressedContainerColor = Ink.LogoRed,
            pressedContentColor = Color.White
        ),
        scale = FocusStyle.lift(1.06f),
        glow = FocusStyle.glow(),
        border = FocusStyle.noBorder()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CineIcon(icon, tint = ink, modifier = Modifier.size(10.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 0.9.sp),
                color = ink
            )
        }
    }
}

/** Bandeau de lettres : saute directement à la première vidéo commençant par là. */
@Composable
private fun AlphabetStrip(
    files: List<Node>,
    columns: Int,
    headerRows: Int,
    onJump: (Int) -> Unit
) {
    val letters = remember(files) {
        files.mapIndexed { index, node ->
            node.displayTitle.firstOrNull()?.uppercaseChar() to index
        }.filter { it.first != null }
            .groupBy { it.first!! }
            .mapValues { entry -> entry.value.first().second }
            .toSortedMap()
    }
    if (letters.size < 3) return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        letters.forEach { (letter, firstIndex) ->
            Surface(
                onClick = { onJump(headerRows + firstIndex / columns) },
                shape = FocusStyle.shape(Radius.tile),
                colors = FocusStyle.colors(container = Color.Transparent, content = Ink.TextMid),
                scale = FocusStyle.lift(1.12f),
                glow = FocusStyle.glow(Ink.Lilac, 10.dp),
                border = FocusStyle.noBorder()
            ) {
                Text(
                    letter.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink.TextMid,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun Breadcrumb(crumbs: List<Crumb>, accent: Color) {
    // Écrit comme le surtitre de l'accueil : filet rouge, petites capitales espacées
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(HeroInfo.ruleWidth).height(1.dp).background(Ink.LogoRed))
        Spacer(Modifier.width(10.dp))
        crumbs.forEachIndexed { index, crumb ->
            val last = index == crumbs.lastIndex
            Text(
                text = crumb.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
                color = Color.White.copy(
                    alpha = if (last) HeroInfo.overlineAlpha else HeroInfo.creditLabelAlpha
                ),
                maxLines = 1
            )
            if (!last) {
                CineIcon(
                    R.drawable.ic_chevron,
                    tint = Color.White.copy(alpha = HeroInfo.creditLabelAlpha),
                    modifier = Modifier.padding(horizontal = 5.dp).size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyFolder(hideWatched: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (hideWatched) "Tout a été vu dans ce dossier"
            else "Ce dossier ne contient aucune vidéo",
            style = MaterialTheme.typography.headlineSmall,
            color = Ink.TextMid
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hideWatched) "Repassez le filtre sur « Tous » pour les revoir"
            else "Les dossiers sans vidéo sont masqués automatiquement",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.TextLow
        )
    }
}
