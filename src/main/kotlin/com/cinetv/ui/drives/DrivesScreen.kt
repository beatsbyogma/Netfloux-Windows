@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.cinetv.ui.drives

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import kotlinx.coroutines.Job
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Node
import com.cinetv.data.model.isSuggestion
import androidx.compose.foundation.gestures.scrollBy
import com.cinetv.data.model.Volume
import com.cinetv.data.repo.ScanState
import com.cinetv.data.storage.StorageAccess
import com.cinetv.ui.components.*
import com.cinetv.ui.home.AddShareDialog
import com.cinetv.ui.home.HomeHero
import com.cinetv.ui.home.NavRail
import com.cinetv.ui.home.PillButton
import com.cinetv.ui.home.RailEntry
import com.cinetv.ui.theme.HeroInfo
import com.cinetv.ui.theme.HomePage
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * L'accueil.
 *
 * Rail d'icônes à gauche, barre de navigation posée sur le visuel, grande
 * bannière, puis les rangées : les disques, la reprise de lecture, les
 * nouveautés. La disposition suit la maquette fournie.
 */
@Composable
fun DrivesScreen(
    viewModel: DrivesViewModel,
    onOpenDrive: (Volume) -> Unit,
    /** Ouvre la page d'un genre (rubrique « Genres »). */
    onOpenGenre: (String) -> Unit = {},
    /** Ouvre un dossier présenté comme un disque : identifiants du disque porteur et du dossier. */
    onOpenVirtual: (String, String) -> Unit = { _, _ -> },
    onPlay: (Node) -> Unit,
    onOpenFolderOf: (Node) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val posters by viewModel.posters.collectAsStateWithLifecycle()
    val resume by viewModel.resume.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val myList by viewModel.myList.collectAsStateWithLifecycle()
    val featured by viewModel.featured.collectAsStateWithLifecycle()
    val spotlighting by viewModel.spotlighting.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val metadata by viewModel.metadataProgress.collectAsStateWithLifecycle()

    var permissionDenied by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val heroFocus = remember { FocusRequester() }
    // Titre dont la fiche est ouverte
    var sheet by remember { mutableStateOf<Node?>(null) }
    // Réseau : boîte d'ajout ouverte, et partage dont le menu est ouvert
    var addingShare by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf<Volume?>(null) }
    // Menu d'un disque, puis choix de l'ancien disque dont reprendre la config
    var driveMenu by remember { mutableStateOf<Volume?>(null) }
    var adoptInto by remember { mutableStateOf<Volume?>(null) }
    var forgetAsk by remember { mutableStateOf<Volume?>(null) }
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val shareBusy by viewModel.shareBusy.collectAsStateWithLifecycle()
    val shareError by viewModel.shareError.collectAsStateWithLifecycle()
    val foundHosts by viewModel.hosts.collectAsStateWithLifecycle()
    val foundShares by viewModel.hostShares.collectAsStateWithLifecycle()
    val searchingHosts by viewModel.searching.collectAsStateWithLifecycle()
    // Un partage réseau peut être rangé parmi les disques (menu du partage)
    val shareContext = LocalContext.current
    val sharePrefs = remember { com.cinetv.data.prefs.Settings(shareContext) }
    var sharesAsDrives by remember { mutableStateOf(sharePrefs.sharesAsDrives) }
    val localVolumes = volumes.filter { !it.id.startsWith("smb:") || it.id in sharesAsDrives }
    val virtualVolumes by viewModel.virtual.collectAsStateWithLifecycle()
    val genres by viewModel.genres.collectAsStateWithLifecycle()
    val toWatch by viewModel.toWatch.collectAsStateWithLifecycle()
    val spotlightIds by viewModel.spotlightIds.collectAsStateWithLifecycle()
    val releases by viewModel.releases.collectAsStateWithLifecycle()
    // Fiche d'une proposition (titre absent des disques)
    var suggestion by remember { mutableStateOf<com.cinetv.data.model.Suggestion?>(null) }
    var virtualMenu by remember { mutableStateOf<Volume?>(null) }
    val networkVolumes = volumes.filter { it.id.startsWith("smb:") && it.id !in sharesAsDrives }

    fun startScan() {
        permissionDenied = false
        if (viewModel.hasPermission()) viewModel.scanAll()
        else viewModel.scanAll()
    }

    LaunchedEffect(Unit) {
        viewModel.refresh()
        if (viewModel.hasPermission() && volumes.isEmpty()) viewModel.scanAll()
    }

    // À l'arrivée sur l'app, une fois l'ouverture terminée : on se place sur la
    // bannière, focus sur son bouton. Une seule fois — au retour d'un autre
    // écran, on retrouve l'endroit qu'on avait quitté.
    val introOver = LocalAmbientMotion.current
    var landed by rememberSaveable { mutableStateOf(false) }
    var heroFocused by remember { mutableStateOf(false) }
    LaunchedEffect(introOver, featured != null) {
        if (!introOver || landed) return@LaunchedEffect
        listState.scrollToItem(0)
        // On insiste jusqu'à ce que la bannière ait réellement le focus : la
        // demande peut « réussir » sans effet si le bouton n'est pas encore prêt.
        repeat(50) {
            runCatching { heroFocus.requestFocus() }
            delay(50)
            if (heroFocused) {
                listState.scrollToItem(0)
                landed = true
                return@LaunchedEffect
            }
        }
    }

    // Index des rangées, pour que le rail puisse y conduire
    // La bannière reste toujours en tête ; le reste suit l'ordre choisi dans les
    // Paramètres, sans les rubriques masquées ni celles qui n'ont rien à montrer.
    val homeContext = LocalContext.current
    val homePrefs = remember { com.cinetv.data.prefs.Settings(homeContext) }
    val homeOrder = remember { homePrefs.homeOrder }
    val homeHidden = remember { homePrefs.homeHidden }
    val sections = buildList {
        add("hero")
        for (key in homeOrder) {
            if (key in homeHidden) continue
            val filled = when (key) {
                "list" -> myList.isNotEmpty()
                "resume" -> resume.isNotEmpty()
                "recent" -> recent.isNotEmpty()
                "genres" -> genres.isNotEmpty()
                "towatch" -> toWatch.isNotEmpty()
                "releases" -> releases.isNotEmpty()
                else -> true
            }
            if (filled) add(key)
        }
    }
    fun goTo(key: String) {
        val index = sections.indexOf(key)
        if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
    }

    val connected = volumes.count { it.isConnected }
    val pageLayer = rememberGraphicsLayer()
    val pageFocus = remember { FocusRequester() }
    // Les rangées, elles, gardent leur défilement horizontal habituel
    val rowScroll = LocalBringIntoViewSpec.current

    // ── Navigation de l'accueil
    // Gauche : seul accès au menu. Haut et bas : d'une rubrique à l'autre.
    val focusManager = LocalFocusManager.current
    var railActive by remember { mutableStateOf(false) }
    val railEntry = remember { FocusRequester() }
    val rowFocus = remember {
        mapOf(
            "drives" to FocusRequester(), "network" to FocusRequester(),
            "resume" to FocusRequester(), "recent" to FocusRequester(), "list" to FocusRequester(),
            "genres" to FocusRequester(), "towatch" to FocusRequester(), "releases" to FocusRequester()
        )
    }
    var currentSection by remember { mutableStateOf("hero") }
    // Couleur du bouton « Lire » de la bannière, reprise par le N du menu
    var heroAccent by remember { mutableStateOf(Color.White) }
    // Bande-annonce ouverte dans sa fenêtre, depuis le bouton de la bannière
    var popupTrailer by remember { mutableStateOf<String?>(null) }
    popupTrailer?.let { key -> com.cinetv.ui.components.TrailerPopup(key) { popupTrailer = null } }
    // Une fenêtre ouverte par-dessus l'accueil fige la bannière sur son titre ; elle repart à la fermeture
    val overlayOpen = sheet != null || suggestion != null || popupTrailer != null || addingShare ||
        shareMenu != null || driveMenu != null || virtualMenu != null || adoptInto != null || forgetAsk != null
    LaunchedEffect(overlayOpen) { viewModel.freezeHero(overlayOpen) }
    // « Vider », au-dessus de la rangée Reprendre : on y monte depuis la rangée
    val clearFocus = remember {
        // « towatch » : même principe pour son bouton « Autre sélection »
        mapOf("resume" to FocusRequester(), "recent" to FocusRequester(), "towatch" to FocusRequester())
    }
    // Jaquettes verticales ou cartes horizontales pour les propositions (Paramètres › Affichage)
    val discoverPosters = remember { homePrefs.discoverPosters }
    // Taille des cartes, rubrique par rubrique (Paramètres › Accueil)
    val homeSizes = remember { homePrefs.homeSizes }
    fun sized(key: String, base: Dp): Dp = base * (homeSizes[key]?.scale ?: 1f)
    val driftSpeed = with(androidx.compose.ui.platform.LocalDensity.current) { HomePage.driftSpeed.toPx() }
    var clearFocused by remember { mutableStateOf(false) }
    var sectionJob by remember { mutableStateOf<Job?>(null) }

    fun focusSection(key: String) {
        val index = sections.indexOf(key)
        if (index < 0) return
        sectionJob?.cancel()
        sectionJob = scope.launch {
            // La rubrique vient se caler en haut de l'écran, puis reçoit le focus
            listState.animateScrollToItem(index)
            val target = if (key == "hero") heroFocus else rowFocus.getValue(key)
            repeat(12) {
                runCatching { target.requestFocus() }
                delay(30)
                if (currentSection == key && !clearFocused) return@launch
            }
        }
    }

    fun moveSection(step: Int) {
        // Dans « Reprendre », le bouton Vider fait étape entre la rangée et la rubrique du dessus
        clearFocus[currentSection]?.let { clear ->
            if (step < 0 && !clearFocused) { runCatching { clear.requestFocus() }; return }
            if (step > 0 && clearFocused) { focusSection(currentSection); return }
        }
        val target = sections.indexOf(currentSection) + step
        if (target in sections.indices) focusSection(sections[target])
    }

    // Une rangée vidée disparaît : le focus repart sur la dernière rubrique restante
    LaunchedEffect(sections) {
        if (landed && currentSection !in sections) {
            clearFocused = false
            focusSection(sections.last())
        }
    }

    LaunchedEffect(railActive) {
        if (!railActive) return@LaunchedEffect
        repeat(12) {
            if (runCatching { railEntry.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(30)
        }
    }

    // Depuis le menu : choisir une rubrique y conduit et referme le menu
    fun jumpTo(key: String) {
        if (key !in sections) return
        railActive = false
        focusSection(key)
    }

    // Au retour d'un autre écran, la page reprend le focus là où on l'avait laissée
    LaunchedEffect(Unit) {
        if (!landed) return@LaunchedEffect
        repeat(12) {
            if (runCatching { pageFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    /** Ce que chaque rangée ajoute pour être une étape de la navigation. */
    fun rowStep(key: String) = Modifier
        .focusRequester(rowFocus.getValue(key))
        .focusRestorer()
        .onFocusChanged { if (it.hasFocus) currentSection = key }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
    ) {
        // La page ne défile que sur ordre (haut, bas, menu). Laissée à elle-même,
        // elle se recalait à chaque prise de focus et renvoyait la bannière hors champ.
        CompositionLocalProvider(LocalBringIntoViewSpec provides StillPage) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Revenir du menu ramène là où on était dans la page
                .focusRequester(pageFocus)
                .focusRestorer()
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        // Plus rien à gauche dans la page : on ouvre le menu
                        Key.DirectionLeft -> {
                            if (!focusManager.moveFocus(FocusDirection.Left)) railActive = true
                            true
                        }
                        Key.DirectionDown -> { moveSection(1); true }
                        Key.DirectionUp -> { moveSection(-1); true }
                        else -> false
                    }
                }
                // La page est enregistrée au passage : le menu de gauche s'en
                // sert pour afficher, flouté, ce qui se trouve derrière lui
                .drawWithContent {
                    pageLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(pageLayer)
                }
        ) {

            item("hero") {
                // Série tirée au hasard : la bannière présente la saison, pas l'épisode
                val episode = featured?.takeIf { spotlighting }?.let { EPISODE_TAG.find(it.displayTitle) }
                val season = episode?.groupValues?.getOrNull(1)?.toIntOrNull()
                HomeHero(
                    node = featured,
                    contextLabel = viewModel.volumeOf(featured)?.label ?: "bibliothèque",
                    highlight = if (episode != null) {
                        listOfNotNull(
                            season?.let { if (it == 0) "Épisodes spéciaux" else "Saison $it" },
                            viewModel.volumeOf(featured)?.label
                        ).joinToString(" · ")
                    } else if (featured?.isSuggestion == true) "Sorti cette année · absent de vos disques"
                    else heroHighlight(featured, viewModel.volumeOf(featured)),
                    seriesTitle = episode?.let { featured?.displayTitle?.substring(0, it.range.first)?.trim(' ', '·') },
                    seriesAction = if (episode != null) "Voir la saison" else null,
                    onAccent = { heroAccent = it },
                    steps = spotlightIds.size,
                    step = spotlightIds.indexOf(featured?.id),
                    onTrailer = {
                        val node = featured
                        if (node != null) scope.launch {
                            val found = viewModel.trailerKey(node)
                            if (found != null) popupTrailer = found
                            else android.widget.Toast.makeText(
                                homeContext, "Aucune bande-annonce trouvée pour ce titre", android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    scanning = scanState is ScanState.Running,
                    playRequester = heroFocus,
                    // La bannière prend 80 % de l'écran ; y revenir la remet entière à l'écran
                    modifier = Modifier
                        .fillParentMaxHeight(HomePage.heroScreenFraction)
                        .onFocusChanged {
                            heroFocused = it.hasFocus
                            if (it.hasFocus) currentSection = "hero"
                        },
                    onPlay = {
                        featured?.let {
                            // Titre proposé, absent des disques : le bouton ouvre sa bande-annonce
                            if (it.isSuggestion) scope.launch {
                                val found = viewModel.trailerKey(it)
                                if (found != null) popupTrailer = found
                                else android.widget.Toast.makeText(
                                    homeContext, "Aucune bande-annonce trouvée pour ce titre", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                            else if (episode != null) onOpenFolderOf(it)
                            else onPlay(it)
                        }
                    },
                    onInfo = {
                        val shown = featured
                        if (shown != null && shown.isSuggestion) suggestion = shown.asSuggestion() else sheet = shown
                    },
                    onScan = { startScan() },
                    startInset = HomePage.railWidth
                )
            }

            // Les rubriques, dans l'ordre choisi (Paramètres › Accueil)
            for (sectionKey in sections.drop(1)) { when (sectionKey) {
            "drives" ->
            item("drives") { CompositionLocalProvider(LocalBringIntoViewSpec provides rowScroll) {
                Column(
                    Modifier
                        .entrance(fromY = Motion.rowRise)
                        .padding(top = HomePage.sectionGap)
                ) {
                    SectionHeader(
                        title = "Vos disques",
                        note = if (permissionDenied) {
                            "Accès aux fichiers refusé — relancez l'analyse pour réessayer"
                        } else {
                            statusLine(volumes, scanState)
                        },
                        warn = permissionDenied || scanState is ScanState.Failed
                    )
                    LazyRow(
                        modifier = rowStep("drives"),
                        contentPadding = PaddingValues(
                            start = HomePage.railWidth + HomePage.side,
                            end = HomePage.side,
                            top = HomePage.rowTitleGap,
                            bottom = 10.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(HomePage.wideGap)
                    ) {
                        // Vrais disques puis dossiers présentés comme des disques ; ceux qui sont
                        // débranchés passent toujours à la fin de la rangée.
                        val virtualIds = virtualVolumes.mapTo(HashSet()) { it.id }
                        val driveCards = (localVolumes + virtualVolumes).sortedBy { !it.isConnected }
                        items(driveCards, key = { it.id }) { volume ->
                            val virtual = volume.id in virtualIds
                            DriveCard(
                                volume = volume,
                                posters = posters[volume.id].orEmpty(),
                                width = HomePage.driveCard,
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    if (virtual) viewModel.virtualTarget(volume)?.let { onOpenVirtual(it.volumeId, it.folderId) }
                                    else onOpenDrive(volume)
                                },
                                onLongClick = {
                                    when {
                                        virtual -> virtualMenu = volume
                                        volume.id.startsWith("smb:") -> shareMenu = volume
                                        else -> driveMenu = volume
                                    }
                                }
                            )
                        }
                        item("scan") {
                            ScanDriveCard(
                                busy = scanState is ScanState.Running,
                                firstRun = localVolumes.isEmpty(),
                                width = HomePage.driveCard,
                                onClick = { startScan() }
                            )
                        }
                    }
                }
            } }

            "network" ->
            item("network") { CompositionLocalProvider(LocalBringIntoViewSpec provides rowScroll) {
                Column(Modifier.entrance(fromY = Motion.rowRise).padding(top = HomePage.sectionGap)) {
                    SectionHeader(title = "Réseau", note = null, warn = false)
                    LazyRow(
                        modifier = rowStep("network"),
                        contentPadding = PaddingValues(
                            start = HomePage.railWidth + HomePage.side,
                            end = HomePage.side,
                            top = HomePage.rowTitleGap,
                            bottom = 10.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(HomePage.wideGap)
                    ) {
                        // Les partages hors ligne passent à la fin
                        items(networkVolumes.sortedBy { !it.isConnected }, key = { it.id }) { volume ->
                            DriveCard(
                                volume = volume,
                                posters = posters[volume.id].orEmpty(),
                                width = HomePage.driveCard,
                                onClick = { onOpenDrive(volume) },
                                onLongClick = { shareMenu = volume }
                            )
                        }
                        item("addShare") {
                            AddShareCard(
                                width = HomePage.driveCard,
                                onClick = { viewModel.clearShareError(); addingShare = true }
                            )
                        }
                    }
                }
            } }

            "list" ->
            if (myList.isNotEmpty()) {
                item("list") {
                    CardRow(
                        title = "Ma liste",
                        rowScroll = rowScroll,
                        rowModifier = rowStep("list"),
                        cardWidth = sized("list", HomePage.wideCard),
                        nodes = myList,
                        labelFor = { node -> viewModel.volumeOf(node)?.label },
                        onClick = { node -> sheet = node },
                        onLongClick = { node -> sheet = node },
                        onFocused = viewModel::highlight
                    )
                }
            }

            "resume" ->
            if (resume.isNotEmpty()) {
                item("resume") {
                    CardRow(
                        title = "Reprendre",
                        rowScroll = rowScroll,
                        rowModifier = rowStep("resume"),
                        cardWidth = sized("resume", HomePage.wideCard),
                        clearModifier = Modifier
                            .focusRequester(clearFocus.getValue("resume"))
                            .onFocusChanged { clearFocused = it.hasFocus; if (it.hasFocus) currentSection = "resume" },
                        nodes = resume,
                        // Pas encore entamé : c'est l'épisode qui suit le dernier terminé
                        labelFor = { node ->
                            if (node.lastPositionMs <= 0) "Épisode suivant" else viewModel.volumeOf(node)?.label
                        },
                        onClick = { node -> sheet = node },
                        onLongClick = { node -> sheet = node },
                        onFocused = viewModel::highlight,
                        onClear = { viewModel.clearAllResume() }
                    )
                }
            }

            "recent" ->
            if (recent.isNotEmpty()) {
                item("recent") {
                    CardRow(
                        title = "Récemment ajoutés",
                        rowScroll = rowScroll,
                        rowModifier = rowStep("recent"),
                        cardWidth = sized("recent", HomePage.wideCard),
                        clearModifier = Modifier
                            .focusRequester(clearFocus.getValue("recent"))
                            .onFocusChanged { clearFocused = it.hasFocus; if (it.hasFocus) currentSection = "recent" },
                        nodes = recent,
                        labelFor = { node -> viewModel.volumeOf(node)?.label },
                        onClick = { node -> sheet = node },
                        onLongClick = { node -> sheet = node },
                        onFocused = viewModel::highlight,
                        onClear = { viewModel.clearRecent() }
                    )
                }
            }

            "genres" ->
            if (genres.isNotEmpty()) {
                item("genres") {
                    Column(
                        Modifier.entrance(fromY = Motion.rowRise)
                            .padding(top = HomePage.sectionGap)
                    ) {
                        SectionHeader(title = "Genres", note = null, warn = false)
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowScroll) {
                            LazyRow(
                                modifier = rowStep("genres"),
                                contentPadding = PaddingValues(
                                    start = HomePage.railWidth + HomePage.side, end = HomePage.side,
                                    top = HomePage.rowTitleGap, bottom = 10.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(HomePage.wideGap)
                            ) {
                                items(genres, key = { it.label }) { shelf ->
                                    GenreCard(shelf, sized("genres", HomePage.wideCard)) { onOpenGenre(shelf.label) }
                                }
                            }
                        }
                    }
                }
            }

            "towatch", "releases" -> {
                val shown = if (sectionKey == "towatch") toWatch else releases
                if (shown.isNotEmpty()) {
                    item(sectionKey) {
                        Column(
                            Modifier.entrance(fromY = Motion.rowRise)
                                .padding(top = HomePage.sectionGap)
                        ) {
                            SectionHeader(
                                title = if (sectionKey == "towatch") "À voir" else "Sorties récentes",
                                note = if (sectionKey == "towatch") "Absents de vos disques" else "Les trois derniers mois",
                                warn = false
                            ) {
                                if (sectionKey == "towatch") Box(
                                    Modifier
                                        .focusRequester(clearFocus.getValue("towatch"))
                                        .onFocusChanged { clearFocused = it.hasFocus; if (it.hasFocus) currentSection = "towatch" }
                                ) {
                                    PillButton(
                                        icon = R.drawable.ic_replay,
                                        label = "AUTRE SÉLECTION",
                                        onClick = { viewModel.anotherSelection() }
                                    )
                                }
                            }
                            // Tant qu'on n'est pas dessus, la rangée défile toute seule, tout doucement :
                                // jusqu'au bout, une pause, puis dans l'autre sens. Elle s'arrête dès qu'on y entre.
                                val rowState = androidx.compose.foundation.lazy.rememberLazyListState()
                                val drifting = currentSection != sectionKey && suggestion == null
                                LaunchedEffect(drifting, shown.size) {
                                    if (!drifting) return@LaunchedEffect
                                    delay(HomePage.driftPauseMs)
                                    var direction = 1f
                                    var last = androidx.compose.runtime.withFrameNanos { it }
                                    while (true) {
                                        val now = androidx.compose.runtime.withFrameNanos { it }
                                        val wanted = direction * driftSpeed * ((now - last) / 1_000_000_000f)
                                        last = now
                                        val moved = rowState.scrollBy(wanted)
                                        if (kotlin.math.abs(moved) < kotlin.math.abs(wanted) / 2f) {
                                            direction = -direction
                                            delay(HomePage.driftPauseMs)
                                            last = androidx.compose.runtime.withFrameNanos { it }
                                        }
                                    }
                                }
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowScroll) {
                                LazyRow(
                                    state = rowState,
                                    modifier = rowStep(sectionKey),
                                    contentPadding = PaddingValues(
                                        start = HomePage.railWidth + HomePage.side, end = HomePage.side,
                                        top = HomePage.rowTitleGap, bottom = 10.dp
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(HomePage.wideGap)
                                ) {
                                    itemsIndexed(shown, key = { _, it -> it.key }) { index, pick ->
                                        // Sorties récentes : à quelques cartes de la fin, on charge la suite
                                        if (sectionKey == "releases" && index >= shown.size - 6) {
                                            LaunchedEffect(shown.size) { viewModel.loadMoreReleases() }
                                        }
                                        DiscoverCard(
                                            item = pick,
                                            width = sized(sectionKey, if (discoverPosters) HomePage.posterCard else HomePage.wideCard),
                                            showDate = sectionKey == "releases",
                                            vertical = discoverPosters,
                                            modifier = Modifier.animateItem()
                                        ) {
                                            suggestion = pick
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> {}
            } }

            item("hints") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = HomePage.railWidth)
                        .padding(horizontal = HomePage.side, vertical = 30.dp),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Hint("OK", "Ouvrir la fiche")
                    Hint("Appui long", "Menu du disque")
                    Hint("Gauche", "Menu")
                    Hint("Haut / Bas", "Rubrique suivante")
                }
            }
        }

        }

        NavRail(
            top = listOf(
                RailEntry(R.drawable.ic_rail_home, "Accueil", active = currentSection == "hero") { jumpTo("hero") },
                RailEntry(R.drawable.ic_rail_search, "Rechercher") { onOpenSearch() },
                RailEntry(R.drawable.ic_rail_disc, "Disques", active = currentSection == "drives") { jumpTo("drives") },
                RailEntry(R.drawable.ic_rail_network, "Réseau", active = currentSection == "network") { jumpTo("network") },
                RailEntry(R.drawable.ic_rail_list, "Ma liste", active = currentSection == "list") { jumpTo("list") },
                RailEntry(R.drawable.ic_rail_resume, "Reprendre", active = currentSection == "resume") { jumpTo("resume") },
                RailEntry(R.drawable.ic_rail_new, "Nouveautés", active = currentSection == "recent") { jumpTo("recent") },
                RailEntry(R.drawable.ic_rail_genres, "Genres", active = currentSection == "genres") { jumpTo("genres") },
                RailEntry(
                    R.drawable.ic_rail_discover, "À découvrir",
                    active = currentSection == "towatch" || currentSection == "releases"
                ) { jumpTo(if ("towatch" in sections) "towatch" else "releases") }
            ),
            bottom = listOf(
                RailEntry(R.drawable.ic_rail_settings, "Réglages") { onOpenSettings() }
            ),
            connected = connected > 0,
            badgeTint = heroAccent,
            modifier = Modifier.align(Alignment.CenterStart),
            backdrop = pageLayer,
            active = railActive,
            entry = railEntry,
            onLeave = {
                // On rend d'abord la main à la page, puis le menu redevient inaccessible
                runCatching { pageFocus.requestFocus() }
                railActive = false
            }
        )


        suggestion?.let { item ->
            SuggestionSheet(
                item = item,
                onTrailer = {
                    scope.launch {
                        val found = viewModel.trailerKey(item)
                        if (found != null) popupTrailer = found
                        else android.widget.Toast.makeText(
                            homeContext, "Aucune bande-annonce trouvée pour ce titre", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onNotInterested = { viewModel.dismiss(item); suggestion = null },
                onDismiss = { suggestion = null }
            )
        }

        if (addingShare) {
            AddShareDialog(
                busy = shareBusy,
                error = shareError,
                onSubmit = { share -> viewModel.addShare(share) { addingShare = false } },
                onDismiss = { addingShare = false },
                hosts = foundHosts,
                hostShares = foundShares,
                searching = searchingHosts,
                onFindHosts = { viewModel.findHosts() },
                onFindShares = { host, user, password -> viewModel.findShares(host, user, password) }
            )
        }

        driveMenu?.let { volume ->
            SimpleMenu(
                title = volume.label,
                subtitle = if (volume.isConnected) "Disque branché" else "Disque débranché",
                actions = buildList {
                    add(MenuAction(R.drawable.ic_sparkle, "Changer le thème", volume.theme.label) {
                        driveMenu = null; viewModel.cycleTheme(volume)
                    })
                    add(MenuAction(R.drawable.ic_disc, "Reprendre la config d'un ancien disque", "Jaquettes, fiches, épisodes vus") {
                        driveMenu = null; adoptInto = volume
                    })
                    if (!volume.isConnected) {
                        add(MenuAction(R.drawable.ic_close, "Retirer ce disque de l'app", "Sa config sera effacée") {
                            driveMenu = null; forgetAsk = volume
                        })
                    }
                },
                onDismiss = { driveMenu = null }
            )
        }

        adoptInto?.let { target ->
            val others = volumes.filter { it.id != target.id }
            SimpleMenu(
                title = "Reprendre la config",
                subtitle = if (others.isEmpty()) "Aucun autre disque connu" else "Vers « ${target.label} » — choisissez l'ancien disque",
                actions = others.map { old ->
                    MenuAction(
                        R.drawable.ic_disc, old.label,
                        "${old.theme.label} · ${if (old.isConnected) "branché" else "débranché"}"
                    ) { adoptInto = null; viewModel.adoptConfig(old, target) }
                } + MenuAction(R.drawable.ic_close, "Annuler") { adoptInto = null },
                onDismiss = { adoptInto = null }
            )
        }

        forgetAsk?.let { volume ->
            SimpleMenu(
                title = "Retirer « ${volume.label} » ?",
                subtitle = "Jaquettes choisies, fiches et épisodes vus de ce disque seront effacés de l'app",
                actions = listOf(
                    MenuAction(R.drawable.ic_check, "Annuler") { forgetAsk = null },
                    MenuAction(R.drawable.ic_close, "Retirer définitivement") { forgetAsk = null; viewModel.forgetVolume(volume) }
                ),
                onDismiss = { forgetAsk = null }
            )
        }

        virtualMenu?.let { volume ->
            SimpleMenu(
                title = volume.label,
                subtitle = "Dossier affiché comme un disque",
                actions = listOf(
                    MenuAction(R.drawable.ic_sparkle, "Changer le thème", volume.theme.label) {
                        virtualMenu = null; viewModel.cycleVirtualTheme(volume)
                    },
                    MenuAction(R.drawable.ic_close, "Ne plus afficher comme un disque", "Le dossier retourne dans son disque d'origine") {
                        virtualMenu = null; viewModel.removeVirtual(volume)
                    }
                ),
                onDismiss = { virtualMenu = null }
            )
        }

        notice?.let { message ->
            SimpleMenu(
                title = "Config reprise",
                subtitle = message,
                actions = listOf(MenuAction(R.drawable.ic_check, "OK") { viewModel.clearNotice() }),
                onDismiss = { viewModel.clearNotice() }
            )
        }

        shareMenu?.let { volume ->
            SimpleMenu(
                title = volume.label,
                subtitle = volume.id.removePrefix("smb:"),
                actions = listOf(
                    MenuAction(R.drawable.ic_replay, "Relancer l'analyse") {
                        shareMenu = null; viewModel.rescan(volume)
                    },
                    if (volume.id in sharesAsDrives) {
                        MenuAction(R.drawable.ic_network, "Remettre dans « Réseau »") {
                            shareMenu = null
                            sharesAsDrives = sharesAsDrives - volume.id
                            sharePrefs.sharesAsDrives = sharesAsDrives
                        }
                    } else {
                        MenuAction(R.drawable.ic_disc, "Afficher parmi les disques", "Il quitte la rubrique « Réseau »") {
                            shareMenu = null
                            sharesAsDrives = sharesAsDrives + volume.id
                            sharePrefs.sharesAsDrives = sharesAsDrives
                        }
                    },
                    MenuAction(R.drawable.ic_sparkle, "Changer le thème", volume.theme.label) {
                        shareMenu = null; viewModel.cycleTheme(volume)
                    },
                    MenuAction(R.drawable.ic_disc, "Reprendre la config d'un ancien disque", "Jaquettes, fiches, épisodes vus") {
                        shareMenu = null; adoptInto = volume
                    },
                    MenuAction(R.drawable.ic_close, "Retirer ce dossier", "Les fichiers ne sont pas touchés") {
                        shareMenu = null; viewModel.removeShare(volume)
                    }
                ),
                onDismiss = { shareMenu = null }
            )
        }

        sheet?.let { target ->
            ContextMenu(
                node = target,
                actions = titleActions(
                    node = target,
                    onPlay = { sheet = null; onPlay(target) },
                    onRestart = { sheet = null; viewModel.restart(target) { onPlay(target) } },
                    onForgetResume = { sheet = null; viewModel.clearResume(target) },
                    inResume = resume.any { it.id == target.id },
                    onOpenFolder = { sheet = null; onOpenFolderOf(target) }
                ),
                onDismiss = { sheet = null },
                onOpenRelated = { other -> sheet = other }
            )
        }
    }
}

/** Une page qui ne bouge pas quand le focus change : c'est la navigation qui la fait défiler. */
private val StillPage = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

// ───────────────────────────────────────────────────────── rangée de cartes

@Composable
private fun CardRow(
    title: String,
    nodes: List<Node>,
    labelFor: (Node) -> String?,
    onClick: (Node) -> Unit,
    onLongClick: (Node) -> Unit,
    onFocused: (Node) -> Unit,
    onClear: (() -> Unit)? = null,
    rowModifier: Modifier = Modifier,
    clearModifier: Modifier = Modifier,
    cardWidth: Dp = HomePage.wideCard,
    rowScroll: BringIntoViewSpec = LocalBringIntoViewSpec.current
) {
    Column(Modifier.entrance(fromY = Motion.rowRise).padding(top = HomePage.sectionGap)) {
        SectionHeader(title = title, note = null, warn = false) {
            if (onClear != null) Box(clearModifier) { ClearButton(onClear) }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowScroll) {
        LazyRow(
            modifier = rowModifier,
            contentPadding = PaddingValues(
                start = HomePage.railWidth + HomePage.side,
                end = HomePage.side,
                top = HomePage.rowTitleGap,
                bottom = 10.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(HomePage.wideGap)
        ) {
            items(nodes, key = { it.id }) { node ->
                WideCard(
                    node = node,
                    // Une carte retirée laisse les autres glisser à sa place
                    modifier = Modifier.animateItem(),
                    width = cardWidth,
                    label = labelFor(node),
                    onClick = { onClick(node) },
                    onLongClick = { onLongClick(node) },
                    onFocused = { onFocused(node) }
                )
            }
        }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    note: String?,
    warn: Boolean,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Le titre reste à droite du menu ; les cartes, elles, défilent jusqu'au bord de l'écran
            .padding(start = HomePage.railWidth)
            .padding(horizontal = HomePage.side),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Même écriture que le surtitre de la bannière : filet rouge, petites capitales espacées
        Box(Modifier.width(HeroInfo.ruleWidth).height(1.dp).background(Ink.LogoRed))
        Spacer(Modifier.width(10.dp))
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = HeroInfo.overlineSpacing.sp),
            color = Color.White.copy(alpha = HeroInfo.overlineAlpha)
        )
        if (note != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = if (warn) Ink.Warn else Ink.TextLow
            )
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

/**
 * Vide la rangée « Reprendre ». Deux appuis : le premier demande confirmation,
 * pour qu'un appui distrait n'efface pas toutes les reprises.
 */
@Composable
private fun ClearButton(onConfirm: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    // Sans second appui, la demande retombe d'elle-même
    LaunchedEffect(armed) {
        if (armed) { delay(4000); armed = false }
    }
    PillButton(
        icon = R.drawable.ic_close,
        label = if (armed) "CONFIRMER ?" else "VIDER",
        onClick = { if (armed) { armed = false; onConfirm() } else armed = true }
    )
}

@Composable
private fun Hint(key: String, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Chip(key, tint = Ink.TextHi)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink.TextLow)
    }
}

// ─────────────────────────────────────────────────────────────── textes

/** La marque d'épisode que portent les titres de séries : « · S02E05 ». */
private val EPISODE_TAG = Regex("""\s*·?\s*S(\d{1,2})\s?E(\d{1,3})\s*$""", RegexOption.IGNORE_CASE)

/** La ligne mise en avant dans la bannière, sous les informations. */
private fun heroHighlight(node: Node?, volume: Volume?): String? = when {
    node == null -> null
    node.progressFraction > 0.01f ->
        "Repris à ${(node.progressFraction * 100).toInt()} %" +
            (volume?.let { " · ${it.label}" } ?: "")
    volume != null -> "Sur ${volume.label}"
    else -> null
}

private fun statusLine(volumes: List<Volume>, scan: ScanState): String? = when (scan) {
    is ScanState.Running ->
        "Analyse de ${scan.volumeLabel} — ${scan.filesFound} vidéos trouvées…"
    is ScanState.Done -> null
    is ScanState.Failed -> scan.reason
    ScanState.NeedsPermission ->
        "Netfloux a besoin d'accéder à vos fichiers vidéo"
    ScanState.NoDrives ->
        "Aucun disque détecté — branchez un disque, puis relancez l'analyse"
    ScanState.Idle -> if (volumes.isEmpty()) "Branchez un disque dur, puis lancez l'analyse" else null
}


/** Un titre proposé dans la bannière, rendu à sa forme de proposition pour ouvrir sa fiche. */
private fun Node.asSuggestion() = com.cinetv.data.model.Suggestion(
    tmdbId = tmdbId, mediaType = mediaType, title = displayTitle, overview = synopsis,
    posterUrl = posterUrl, backdropUrl = backdropUrl, date = releaseDate, rating = rating
)
