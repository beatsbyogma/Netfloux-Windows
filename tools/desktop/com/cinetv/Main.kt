package com.cinetv

import android.content.DesktopContext
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cinetv.data.model.Node
import com.cinetv.data.prefs.Settings
import com.cinetv.data.repo.LibraryRepository
import com.cinetv.data.storage.ExternalSubtitle
import com.cinetv.data.storage.StorageAccess
import com.cinetv.desktop.Back
import com.cinetv.desktop.DrivePicker
import com.cinetv.desktop.DrivePickerState
import com.cinetv.desktop.KeyHub
import com.cinetv.desktop.Navigator
import com.cinetv.desktop.Toasts
import com.cinetv.desktop.Toasts.Host
import com.cinetv.desktop.viewModel
import com.cinetv.ui.components.LocalAmbientMotion
import com.cinetv.ui.components.LocalLibrary
import com.cinetv.ui.components.LocalScreenShot
import com.cinetv.ui.components.NavSound
import com.cinetv.ui.components.TopRightNotices
import com.cinetv.ui.drives.DrivesScreen
import com.cinetv.ui.drives.DrivesViewModel
import com.cinetv.ui.files.BrowseScreen
import com.cinetv.ui.files.BrowseViewModel
import com.cinetv.ui.library.GenreScreen
import com.cinetv.ui.library.StatsScreen
import com.cinetv.ui.library.UnmatchedScreen
import com.cinetv.ui.player.PlayerScreen
import com.cinetv.ui.player.isSeasonFolder
import com.cinetv.ui.player.playerInfoOf
import com.cinetv.ui.player.playerSeasonsOf
import com.cinetv.ui.screensaver.Screensaver
import com.cinetv.ui.search.SearchScreen
import com.cinetv.ui.search.SearchViewModel
import com.cinetv.ui.settings.SettingsScreen
import com.cinetv.ui.settings.SettingsViewModel
import com.cinetv.ui.splash.SplashScreen
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion
import com.cinetv.ui.theme.NetflouxTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gardé pour le code partagé avec la TV ; sur le PC, quitter ferme simplement la fenêtre. */
object AppExit {
    @Volatile var expectReturn = false
    const val GRACE_MS = 1200L
}

/** Vrai pendant un film : les pastilles de travail en fond ne s'affichent pas par-dessus l'image. */
private object Playback { var active by mutableStateOf(false) }

/** Largeur de référence des écrans, en dp : celle de la TV. Tout est agrandi à partir de là. */
private const val DESIGN_WIDTH = 960f
private const val DESIGN_HEIGHT = 540f

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main() {
    System.setProperty("skiko.renderApi", System.getProperty("skiko.renderApi") ?: "DIRECT3D")
    val context = DesktopContext
    val settings = Settings(context)
    Ink.apply(settings.accentTheme)
    com.cinetv.data.tmdb.Tmdb.userKey = settings.tmdbKey
    com.cinetv.data.subs.OpenSubtitles.apiKey = settings.subtitlesKey
    val repo = LibraryRepository(context)
    val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    background.launch {
        repo.refreshVolumeAvailability()
        repo.enrichPendingMetadata()
    }
    NavSound.init(context)

    application {
        val windowState = rememberWindowState(placement = WindowPlacement.Maximized, width = 1600.dp, height = 900.dp)
        val lastInteraction = remember { mutableLongStateOf(System.currentTimeMillis()) }
        var splashShowing by remember { mutableStateOf(true) }
        val skipSplash = remember { mutableIntStateOf(0) }
        var restore by remember { mutableStateOf(WindowPlacement.Maximized) }
        KeyHub.onFullscreen = {
            if (windowState.placement == WindowPlacement.Fullscreen) windowState.placement = restore
            else { restore = windowState.placement; windowState.placement = WindowPlacement.Fullscreen }
        }
        KeyHub.onKey = { NavSound.onKey(it) }

        Window(
            onCloseRequest = ::exitApplication,
            title = "Netfloux",
            state = windowState,
            icon = remember { androidx.compose.ui.graphics.painter.BitmapPainter(javax.imageio.ImageIO.read(com.cinetv.desktop.Res.stream("netfloux_icon.png")).toComposeImageBitmap()) },
            onPreviewKeyEvent = { event ->
                lastInteraction.longValue = System.currentTimeMillis()
                if (splashShowing && (event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.Spacebar)) {
                    if (event.type == KeyEventType.KeyUp) skipSplash.intValue++
                    true
                } else KeyHub.preview(event)
            },
            onKeyEvent = { KeyHub.unhandled(it) }
        ) {
            window.background = java.awt.Color.BLACK
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(Ink.Void)
                    .onPointerEvent(PointerEventType.Move) { lastInteraction.longValue = System.currentTimeMillis() }
                    .onPointerEvent(PointerEventType.Press) { e ->
                        lastInteraction.longValue = System.currentTimeMillis()
                        if (e.button == PointerButton.Back) Back.dispatch()
                    }
            ) {
                // Les écrans ont été dessinés pour une TV de 960 × 540 dp : on agrandit pour remplir la fenêtre
                val outer = LocalDensity.current
                val widthPx = with(outer) { maxWidth.toPx() }
                val heightPx = with(outer) { maxHeight.toPx() }
                val scale = minOf(widthPx / DESIGN_WIDTH, heightPx / DESIGN_HEIGHT).coerceAtLeast(0.5f)
                CompositionLocalProvider(LocalDensity provides Density(scale, 1f)) {
                    NetflouxTheme {
                        App(repo, lastInteraction, skipSplash.intValue, onSplash = { splashShowing = it }, onQuit = ::exitApplication)
                    }
                }
            }
        }
    }
}

@Composable
private fun App(
    repo: LibraryRepository,
    lastInteraction: MutableLongState,
    skipSignal: Int,
    onSplash: (Boolean) -> Unit,
    onQuit: () -> Unit
) {
    var showSplash by remember { mutableStateOf(true) }
    var contentReady by remember { mutableStateOf(false) }
    SideEffect { onSplash(showSplash) }

    Box(Modifier.fillMaxSize().background(Ink.Void)) {
        if (contentReady) {
            CompositionLocalProvider(
                LocalAmbientMotion provides !showSplash,
                LocalLibrary provides repo
            ) {
                NetflouxNavigation(repo, lastInteraction, onQuit)
            }
            if (!showSplash && !Playback.active) TopRightNotices(repo)
        }

        AnimatedVisibility(visible = showSplash, enter = fadeIn(tween(0)), exit = fadeOut(tween(380))) {
            SplashScreen(
                onFinished = { contentReady = true; showSplash = false },
                onSettled = { contentReady = true },
                skipSignal = skipSignal,
                preload = {
                    repo.volumes()
                    repo.continueWatching()
                    repo.recentlyAdded()
                    repo.randomSpotlight()
                }
            )
        }

        if (!showSplash) DriveWatch(repo)
        if (DrivePickerState.open) DrivePicker(repo) { DrivePickerState.open = false; DrivePickerState.highlight = null }
        with(Toasts) { Host() }
    }
}

/**
 * Surveille les disques du PC : un disque choisi qu'on branche ou qu'on retire met
 * l'accueil à jour ; un disque jamais vu ouvre le choix des disques, mis en avant.
 */
@Composable
private fun DriveWatch(repo: LibraryRepository) {
    LaunchedEffect(Unit) {
        if (!StorageAccess.firstChoiceDone) {
            delay(600)
            DrivePickerState.open = true
        }
        var last: Set<String>? = null
        while (true) {
            val now = withContext(Dispatchers.IO) { runCatching { StorageAccess.drives(force = true).map { it.id }.toSet() }.getOrDefault(emptySet()) }
            if (last != null && now != last) {
                withContext(Dispatchers.IO) { runCatching { repo.refreshVolumeAvailability() } }
                val fresh = now - StorageAccess.known
                if (fresh.isNotEmpty() && StorageAccess.firstChoiceDone && !DrivePickerState.open) {
                    StorageAccess.known = StorageAccess.known + fresh
                    DrivePickerState.highlight = fresh.first()
                    DrivePickerState.open = true
                }
            } else if (last == null && StorageAccess.firstChoiceDone) {
                StorageAccess.known = StorageAccess.known + now
            }
            last = now
            delay(2_000)
        }
    }
}

@Composable
private fun NetflouxNavigation(repo: LibraryRepository, lastInteraction: MutableLongState, onQuit: () -> Unit) {
    val nav = remember { Navigator("drives") }
    val scope = rememberCoroutineScope()
    val context = DesktopContext
    val settings = remember { Settings(context) }

    var unplugged by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val play: (Node) -> Unit = { node ->
        scope.launch {
            val current = repo.node(node.id) ?: node
            val reachable = current.isAvailable &&
                (current.volumeId.startsWith("smb:") || StorageAccess.fileFor(current.volumeId, current.relativePath)?.isFile == true)
            if (reachable) {
                NavSound.launch()
                nav.navigate("play/${current.id}")
            } else {
                val label = runCatching { repo.volumes() }.getOrDefault(emptyList())
                    .firstOrNull { it.id == current.volumeId }?.label.orEmpty()
                unplugged = label to current.volumeId.startsWith("smb:")
            }
        }
    }
    fun openFolderOf(node: Node) {
        val folder = node.parentId
        nav.navigate("browse/${Uri.encode(node.volumeId)}" + if (folder != null) "?folderId=$folder" else "")
    }

    // Économiseur d'écran
    var idle by remember { mutableStateOf(false) }
    var backdrops by remember { mutableStateOf<List<String>>(emptyList()) }
    var titles by remember { mutableStateOf<List<String>>(emptyList()) }
    var logos by remember { mutableStateOf<List<String>>(emptyList()) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            val minutes = settings.screensaverMinutes
            if (minutes > 0 && !playing) {
                val elapsed = System.currentTimeMillis() - lastInteraction.longValue
                if (elapsed > minutes * 60_000L && !idle) {
                    val art = repo.recentBackdrops()
                    if (art.isNotEmpty()) {
                        backdrops = art.map { it.second }
                        titles = art.map { it.first }
                        logos = art.map { it.third }
                        idle = true
                    }
                }
            }
        }
    }
    LaunchedEffect(lastInteraction.longValue) { if (idle) idle = false }

    val screenLayer = rememberGraphicsLayer()
    val screenShot: suspend () -> ImageBitmap? = remember(screenLayer) {
        { runCatching { screenLayer.toImageBitmap() }.getOrNull() }
    }

    // Retour : l'écran précédent ; depuis l'accueil, « Quitter Netfloux ? »
    var quitAsk by remember { mutableStateOf(false) }
    SideEffect {
        Back.fallback = {
            when {
                idle -> { idle = false; true }
                nav.popBackStack() -> true
                else -> { quitAsk = true; true }
            }
        }
    }

    val holder = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalScreenShot provides screenShot) {
            AnimatedContent(
                targetState = nav.current,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink.Void)
                    .drawWithContent {
                        screenLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(screenLayer)
                    },
                transitionSpec = {
                    val toPlayer = targetState.path.startsWith("play/") || initialState.path.startsWith("play/")
                    when {
                        toPlayer -> fadeIn(tween(Motion.playerFadeMs)) togetherWith fadeOut(tween(Motion.pageOutMs))
                        nav.popping ->
                            (fadeIn(tween(Motion.pageInMs, Motion.pageInDelayMs)) +
                                scaleIn(tween(Motion.pageInMs, Motion.pageInDelayMs, FastOutSlowInEasing), initialScale = Motion.pageNear)) togetherWith
                                (fadeOut(tween(Motion.pageOutMs)) +
                                    scaleOut(tween(Motion.pageOutMs, easing = FastOutSlowInEasing), targetScale = Motion.pageDeep))
                        else ->
                            (fadeIn(tween(Motion.pageInMs, Motion.pageInDelayMs)) +
                                scaleIn(tween(Motion.pageInMs, Motion.pageInDelayMs, FastOutSlowInEasing), initialScale = Motion.pageDeep)) togetherWith
                                (fadeOut(tween(Motion.pageOutMs)) +
                                    scaleOut(tween(Motion.pageOutMs, easing = FastOutSlowInEasing), targetScale = Motion.pageNear))
                    }
                },
                contentKey = { it.id },
                label = "pages"
            ) { entry ->
                holder.SaveableStateProvider(entry.id) {
                    Screen(entry, nav, repo, context, play, ::openFolderOf, settings,
                        onPlaying = { playing = it })
                }
            }
        }

        unplugged?.let { (label, network) ->
            com.cinetv.ui.components.UnpluggedNotice(label, network) { unplugged = null }
        }

        if (quitAsk) CompositionLocalProvider(LocalScreenShot provides screenShot) {
            com.cinetv.ui.components.QuitDialog(onQuit = onQuit, onDismiss = { quitAsk = false })
        }

        if (idle) {
            Screensaver(
                backdrops = backdrops,
                titles = titles,
                logos = logos,
                onWake = { idle = false; lastInteraction.longValue = System.currentTimeMillis() }
            )
        }
    }
}

@Composable
private fun Screen(
    entry: Navigator.Entry,
    nav: Navigator,
    repo: LibraryRepository,
    context: android.content.Context,
    play: (Node) -> Unit,
    openFolderOf: (Node) -> Unit,
    settings: Settings,
    onPlaying: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    when {
        entry.path == "drives" -> {
            val vm = entry.viewModel { DrivesViewModel(repo) }
            DrivesScreen(
                viewModel = vm,
                onOpenDrive = { volume -> nav.navigate("browse/${Uri.encode(volume.id)}") },
                onOpenGenre = { genre -> nav.navigate("genre/${Uri.encode(genre)}") },
                onOpenVirtual = { volumeId, folderId -> nav.navigate("browse/${Uri.encode(volumeId)}?folderId=$folderId") },
                onPlay = play,
                onOpenFolderOf = openFolderOf,
                onOpenSearch = { nav.navigate("search") },
                onOpenSettings = { nav.navigate("settings") }
            )
        }
        entry.path == "search" -> {
            val vm = entry.viewModel { SearchViewModel(repo) }
            SearchScreen(viewModel = vm, onPlay = play, onOpenFolderOf = openFolderOf)
        }
        entry.path == "settings" -> {
            val vm = entry.viewModel { SettingsViewModel(repo, context) }
            SettingsScreen(
                viewModel = vm,
                onOpenStats = { nav.navigate("stats") },
                onOpenUnmatched = { nav.navigate("unmatched") }
            )
        }
        entry.path.startsWith("genre/") -> GenreScreen(genre = entry.segment(1), onPlay = play, onOpenFolderOf = openFolderOf)
        entry.path == "stats" -> StatsScreen()
        entry.path == "unmatched" -> UnmatchedScreen()
        entry.path.startsWith("browse/") -> {
            val volumeId = entry.segment(1)
            val folderId = entry.arg("folderId")
            val vm = entry.viewModel { BrowseViewModel(repo, context, volumeId, folderId) }
            BrowseScreen(
                viewModel = vm,
                onOpenFolder = { folder -> nav.navigate("browse/${Uri.encode(volumeId)}?folderId=${folder.id}") },
                onPlay = play
            )
        }
        entry.path.startsWith("play/") -> {
            val nodeId = entry.segment(1)
            var node by remember { mutableStateOf<Node?>(null) }
            var next by remember { mutableStateOf<Node?>(null) }
            var subtitles by remember { mutableStateOf<List<ExternalSubtitle>>(emptyList()) }
            var siblings by remember { mutableStateOf<List<Node>>(emptyList()) }

            LaunchedEffect(nodeId) {
                val item = repo.node(nodeId)
                subtitles = item?.let { repo.externalSubtitles(it) }.orEmpty()
                siblings = item?.let { file ->
                    val folder = file.parentId?.let { repo.node(it) }
                    val folders = if (folder != null && isSeasonFolder(folder.name)) {
                        repo.children(file.volumeId, folder.parentId)
                            .filter { it.isDirectory && isSeasonFolder(it.name) }
                            .map { it.id }
                    } else listOf(file.parentId)
                    folders.flatMap { repo.children(file.volumeId, it) }.filter { !it.isDirectory }
                }.orEmpty()
                node = item
                next = item?.let { repo.nextInFolder(it) }
            }

            DisposableEffect(nodeId) {
                onPlaying(true)
                Playback.active = true
                repo.playbackActive = true
                NavSound.muted = true
                onDispose { onPlaying(false); Playback.active = false; repo.playbackActive = false; NavSound.muted = false }
            }

            node?.let { item ->
                // Le fichier là où il est aujourd'hui : la lettre du disque a pu changer depuis l'analyse
                val address = remember(item.id) {
                    if (item.volumeId.startsWith("smb:")) item.documentUri
                    else StorageAccess.fileFor(item.volumeId, item.relativePath)?.let { Uri.fromFile(it).toString() } ?: item.documentUri
                }
                PlayerScreen(
                    uri = address,
                    title = item.displayTitle,
                    startPositionMs = item.lastPositionMs,
                    nextTitle = next?.displayTitle,
                    info = remember(item.id) { playerInfoOf(item) },
                    seasons = remember(item.id, siblings) { playerSeasonsOf(item, siblings) },
                    currentEpisodeId = item.id,
                    nextThumbUrl = next?.let { it.backdropUrl.ifBlank { it.posterUrl } }.orEmpty(),
                    sizeBytes = item.sizeBytes,
                    onPickEpisode = { id -> nav.navigate("play/$id", replaceFrom = entry.route) },
                    externalSubtitles = subtitles,
                    onFindSubtitles = { repo.findOnlineSubtitles(item) },
                    onFetchSubtitle = { found -> repo.fetchOnlineSubtitle(item, found) },
                    onProgress = { pos, dur -> scope.launch { repo.savePosition(item.id, pos, dur) } },
                    onFinished = { scope.launch { repo.setWatched(item.id, true) } },
                    onPlayNext = {
                        val following = next
                        if (following != null) nav.navigate("play/${following.id}", replaceFrom = entry.route)
                        else nav.popBackStack()
                    },
                    onExit = { nav.popBackStack() }
                )
            }
        }
    }
}
