package com.cinetv.ui.player

import android.util.Log
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinetv.ui.components.FitText
import com.cinetv.ui.components.TitleLogo
import com.cinetv.ui.theme.TitleArt
import com.cinetv.ui.theme.GenreType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinetv.data.model.Node
import com.cinetv.data.storage.ExternalSubtitle
import com.cinetv.data.subs.OpenSubtitles
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.prefs.Settings
import com.cinetv.ui.components.CineIcon
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Radius
import kotlinx.coroutines.delay

/** Une piste sélectionnable, audio ou sous-titre. */
data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val selected: Boolean
)

private enum class Sheet { NONE, AUDIO, SUBTITLES, SETTINGS, EPISODES, ONLINE }

/** Une saison et ses épisodes, pour le menu ouvert depuis la pastille. */
data class PlayerSeason(val number: Int, val episodes: List<PlayerEpisode>)

/** Ce que le lecteur affiche du titre en cours, en bas à gauche. */
data class PlayerInfo(
    val headline: String,          // le grand titre : « Épisode 11 » ou le nom du film
    val overline: String = "",     // au-dessus : le nom de la série
    val meta: String = "",         // saison, année, genre
    val synopsis: String = "",
    val chip: String = "",         // la pastille en haut à droite : « Saison 1 »
    val genres: String = "",       // identifiants TMDB, pour la police du titre
    val isEpisode: Boolean = false,
    val logoUrl: String = ""       // logo officiel du film ou de la série
)

/** Un épisode de la colonne de droite. */
data class PlayerEpisode(
    val id: String,
    val label: String,             // « 11. Épisode 11 »
    val note: String,              // « 27 min restantes », « Vu »…
    val thumbUrl: String,
    val progress: Float
)

/** Les trois cadrages de l'image, dans l'ordre où le bouton les fait défiler. */
private val ASPECTS = listOf(
    0 to "ORIGINAL",
    1 to "ZOOM",
    2 to "ÉTIRÉ"
)

/** Les mesures du lecteur — à ajuster ici. */
internal object PlayerSkin {
    val side = 44.dp               // marges latérales
    val top = 26.dp
    val bottom = 22.dp
    const val scrimTop = 0.62f     // assombrissement du haut, du bas, de la gauche
    const val scrimBottom = 0.94f
    const val scrimSide = 0.50f

    val ring = 74.dp               // bouton lecture central
    val ringStroke = 2.5.dp
    val skipGlyph = 26.dp          // −10 s / +10 s
    val transportGap = 46.dp
    val smallGlyph = 17.dp         // retour, fermer
    val toolGlyph = 17.dp          // audio, sous-titres, image
    const val glyphFocusScale = 1.28f

    val infoWidth = 372.dp         // largeur de la fiche du titre
    val infoGap = 14.dp
    val headline = 27.sp

    val episodeWidth = 262.dp      // cartes du menu des épisodes
    val episodeHeight = 104.dp
    val episodeRadius = 5.dp

    val bar = 3.dp                 // timeline
    val barFocused = 5.dp
    val knob = 13.dp
    val timeBubble = 62.dp
    const val widthFraction = 0.70f    // largeur du bloc réglages + timeline, centré
    val toolGap = 8.dp                 // écart entre les pastilles de réglage
    val loader = 46.dp                 // anneau de chargement, commandes masquées
    val toolsToBar = 10.dp             // écart entre les pastilles de réglage et la barre

    // Réserve de lecture chargée d'avance (en millisecondes, et plafond en octets)
    const val bufferMinMs = 20_000     // on recharge dès qu'il reste moins que ça
    const val bufferMaxMs = 60_000     // on s'arrête de charger au-delà
    const val bufferStartMs = 1_200    // ce qu'il faut pour démarrer, ou repartir après un saut
    const val bufferResumeMs = 2_500   // ce qu'il faut pour repartir après une coupure
    const val bufferBytes = 64 * 1024 * 1024
    val previewWidth = 168.dp      // vignette d'aperçu pendant le déplacement

    // Passer l'intro : proposé au début d'un épisode, saute d'un bloc
    const val introWindowMs = 150_000L   // proposé pendant les 2 min 30 du début
    const val introSkipMs = 85_000L      // longueur du saut
    // Épisode suivant : la carte apparaît à ce temps de la fin
    const val nextCardMs = 30_000L
    // Décalages : pas et bornes
    const val subtitleStepMs = 250L
    const val subtitleMaxMs = 10_000L
    const val audioStepMs = 50L
    const val audioMaxMs = 2_000L
}

@UnstableApi
@Composable
fun PlayerScreen(
    uri: String,
    title: String,
    startPositionMs: Long,
    nextTitle: String?,
    info: PlayerInfo = PlayerInfo(headline = title),
    /** Les saisons de la série ; vide pour un film. */
    seasons: List<PlayerSeason> = emptyList(),
    currentEpisodeId: String = "",
    nextThumbUrl: String = "",
    sizeBytes: Long = 0,
    /** Recherche de sous-titres en ligne : la liste trouvée, et la raison si elle est vide. */
    onFindSubtitles: (suspend () -> Pair<List<OpenSubtitles.Found>, String>)? = null,
    /** Téléchargement d'un sous-titre : le fichier prêt à lire, ou la raison de l'échec. */
    onFetchSubtitle: (suspend (OpenSubtitles.Found) -> Pair<ExternalSubtitle?, String>)? = null,
    onPickEpisode: (String) -> Unit = {},
    /** Sous-titres trouvés à côté du fichier (.srt, .ass, .vtt). */
    externalSubtitles: List<ExternalSubtitle> = emptyList(),
    onProgress: (position: Long, duration: Long) -> Unit,
    onFinished: () -> Unit,
    onPlayNext: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }

    // Reprendre ou recommencer : posé avant la lecture si le film a été entamé
    var resumeChoice by remember { mutableStateOf(startPositionMs > 60_000) }
    var beginAt by remember { mutableLongStateOf(startPositionMs) }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(startPositionMs) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var aspect by remember { mutableIntStateOf(0) }
    // Chaque touche repousse l'effacement des contrôles
    var activity by remember { mutableIntStateOf(0) }
    // Saut rapide, contrôles masqués : le petit rappel « −10 s » au centre
    var skipFlash by remember { mutableStateOf<String?>(null) }
    var skipTick by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    // Quelqu'un regarde encore ? Posé après plusieurs épisodes enchaînés sans une touche
    var stillThere by remember { mutableStateOf(WatchStreak.count >= WatchStreak.limit) }
    var nextDismissed by remember { mutableStateOf(false) }
    var introUsed by remember { mutableStateOf(false) }
    var showTech by remember { mutableStateOf(false) }

    // Sous-titres : apparence retenue, retard propre à cette lecture
    var subSize by remember { mutableIntStateOf(settings.subtitleSize.coerceIn(0, SUB_SIZES.lastIndex)) }
    var subColor by remember { mutableIntStateOf(settings.subtitleColor.coerceIn(0, SUB_COLORS.lastIndex)) }
    var subBackground by remember { mutableIntStateOf(settings.subtitleBackground.coerceIn(0, SUB_BACKGROUNDS.lastIndex)) }
    var subDelayMs by remember { mutableLongStateOf(0L) }

    // Décalage du son : lu par le moteur de lecture à chaque image
    // Le décalage du son est mémorisé : réglé une fois pour l'installation (TV + ampli), il vaut pour tous les films
    val audioOffsetUs = remember { AtomicLong(settings.audioOffsetMs * 1000) }
    var audioOffsetMs by remember { mutableLongStateOf(settings.audioOffsetMs) }

    // Aperçu de l'image pendant le déplacement sur la timeline
    val grabber = remember { FramePreview(context, uri) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    DisposableEffect(grabber) { onDispose { grabber.release() } }

    var audioTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var subtitleTracks by remember { mutableStateOf<List<TrackOption>>(emptyList()) }
    var subtitlesOn by remember { mutableStateOf(false) }

    // Sous-titres en ligne : ceux du fichier, plus ceux téléchargés pendant cette lecture
    var allSubtitles by remember { mutableStateOf(externalSubtitles) }
    var online by remember { mutableStateOf<List<OpenSubtitles.Found>?>(null) }
    var onlineNote by remember { mutableStateOf("") }
    var fetching by remember { mutableStateOf(false) }
    // Le sous-titre tout juste téléchargé, à sélectionner dès que le lecteur l'a pris en compte
    var pendingSubtitle by remember { mutableStateOf<String?>(null) }

    // Déplacement sur la timeline : position provisoire tant qu'on n'a pas validé
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }

    // Sortie du son : décodée par VLC (par défaut, tous formats) ou envoyée telle quelle à l'ampli.
    var audioMode by remember { mutableIntStateOf(settings.audioMode.coerceIn(0, 2)) }
    // Le chemin réellement pris par la piste en cours ; chaque chemin a son propre décalage, retenu à part
    var routeDirect by remember { mutableStateOf(false) }
    // Position à reprendre quand le moteur est recréé en cours de lecture (apparence des sous-titres changée)
    var carryOver by remember { mutableLongStateOf(-1L) }
    var audioNote by remember { mutableStateOf("") }

    // Le moteur de lecture : celui de VLC. L'apparence des sous-titres fait partie de ses réglages
    // de départ : en changer le recrée, et la lecture reprend au même endroit.
    val player = remember(subSize, subColor, subBackground) {
        VlcEngine(
            context = context,
            uri = uri,
            subtitleSize = subSize,
            subtitleColor = subColor,
            subtitleBackground = subBackground,
            preferredAudio = settings.preferredAudioLanguage,
            preferredSubtitle = settings.preferredSubtitleLanguage,
            wantSubtitles = settings.subtitlesEnabled
        ).apply {
            offerSubtitles(allSubtitles.map { it.file })
            setAudioMode(audioMode)
            setAudioDelayMs(audioOffsetMs)
            setSubtitleDelayMs(subDelayMs)
        }
    }

    // Lancement effectif, une fois le choix de reprise tranché
    // (aussi quand le moteur vient d'être recréé : il repart d'où l'ancien s'est arrêté)
    LaunchedEffect(player, resumeChoice, stillThere) {
        if (!resumeChoice && !stillThere) {
            // Le temps que la surface d'image soit en place : VLC doit la connaître avant de démarrer
            delay(80)
            if (!player.hasStarted) player.seekTo(if (carryOver >= 0) carryOver else beginAt)
            player.play()
        }
    }

    DisposableEffect(player) {
        player.listener = object : VlcEngine.Listener {
            override fun onBuffering(buffering_: Boolean) { buffering = buffering_ }

            override fun onPlaying(playing: Boolean) { isPlaying = playing }

            override fun onLength(durationMs: Long) { if (durationMs > 0) duration = durationMs }

            override fun onTracks() {
                audioTracks = player.audioOptions()
                subtitleTracks = player.subtitleOptions()
                subtitlesOn = subtitleTracks.any { it.selected }
            }

            override fun onEnded() {
                onFinished()
                if (nextTitle != null && settings.autoPlayNext && !nextDismissed) {
                    // Enchaînement sans intervention : on le compte
                    WatchStreak.count++
                    onPlayNext()
                } else {
                    controlsVisible = true
                }
            }

            override fun onAudioRoute(direct: Boolean) {
                // Son direct ou son décodé : chacun arrive avec son retard, donc son réglage
                routeDirect = direct
                audioOffsetMs = if (direct) settings.audioOffsetDirectMs else settings.audioOffsetMs
                player.setAudioDelayMs(audioOffsetMs)
            }

            override fun onError() {
                audioNote = "Lecture impossible : ce fichier ne s'ouvre pas"
                controlsVisible = true
            }
        }
        onDispose {
            // Moteur remplacé en cours de lecture : la position vient d'être confiée au suivant
            val at = player.positionMs
            val length = player.durationMs
            if (length > 0) onProgress(at, length)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(400)
            if (!scrubbing) position = player.positionMs
            if (duration <= 0) duration = player.durationMs
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            if (player.isPlaying) onProgress(player.positionMs, player.durationMs)
        }
    }

    // Vignette d'aperçu : une demande par palier de 5 s, la dernière l'emporte
    LaunchedEffect(scrubbing, scrubPosition / 5_000) {
        if (!scrubbing) { preview = null; return@LaunchedEffect }
        delay(160)
        grabber.frameAt(scrubPosition)?.let { preview = it }
    }

    val nextCard = nextTitle != null && settings.autoPlayNext && !nextDismissed && !resumeChoice &&
        duration > 300_000 && position > 0 && duration - position <= PlayerSkin.nextCardMs
    val introOffer = info.isEpisode && !introUsed && !resumeChoice && !nextCard && isPlaying &&
        duration > 600_000 && position < PlayerSkin.introWindowMs

    LaunchedEffect(skipTick) {
        if (skipFlash != null) { delay(900); skipFlash = null }
    }

    fun skip(deltaMs: Long) {
        val target = (player.positionMs + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
        player.seekTo(target)
        position = target
    }

    LaunchedEffect(controlsVisible, isPlaying, sheet, scrubbing, activity) {
        if (controlsVisible && isPlaying && sheet == Sheet.NONE && !scrubbing) {
            delay(5_000)
            controlsVisible = false
        }
    }

    BackHandler(enabled = true) {
        when {
            sheet != Sheet.NONE -> sheet = Sheet.NONE
            scrubbing -> { scrubbing = false; position = player.positionMs }
            nextCard && !controlsVisible -> nextDismissed = true
            controlsVisible -> controlsVisible = false
            else -> onExit()
        }
    }

    val timelineFocus = remember { FocusRequester() }
    val toolsFocus = remember { FocusRequester() }
    // Flèche haut, commandes masquées : elles reviennent avec le curseur déjà sur les réglages
    var landOnTools by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible, sheet) {
        if (controlsVisible && sheet == Sheet.NONE) {
            val target = if (landOnTools) toolsFocus else timelineFocus
            landOnTools = false
            repeat(12) {
                if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
                delay(40)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Touches « média » de la télécommande, où que soit le curseur
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // La moindre touche prouve que quelqu'un regarde
                WatchStreak.count = 0
                if (resumeChoice || stillThere) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.MediaPlayPause -> { if (player.isPlaying) player.pause() else player.play(); controlsVisible = true; true }
                    Key.MediaPlay -> { player.play(); true }
                    Key.MediaPause -> { player.pause(); controlsVisible = true; true }
                    Key.MediaFastForward -> { skip(30_000); skipFlash = "+ 30 s"; skipTick++; true }
                    Key.MediaRewind -> { skip(-30_000); skipFlash = "− 30 s"; skipTick++; true }
                    Key.MediaNext -> if (nextTitle != null) { onPlayNext(); true } else false
                    else -> false
                }
            }
    ) {

        // L'image : une surface que VLC remplit lui-même, sous-titres compris.
        // Une surface neuve pour chaque moteur : l'ancien emporte la sienne en s'arrêtant.
        androidx.compose.runtime.key(player) {
        LaunchedEffect(aspect) { player.setScale(aspect) }
        VideoSurface(player, Modifier.fillMaxSize())
        }

        if (showTech) {
            TechPanel(
                lines = player.techLines(sizeBytes, duration),
                modifier = Modifier.align(Alignment.TopStart).padding(start = PlayerSkin.side, top = 62.dp)
            )
        }

        if (!controlsVisible && sheet == Sheet.NONE) {
            if (nextCard && nextTitle != null) {
                NextCard(
                    title = nextTitle,
                    thumbUrl = nextThumbUrl,
                    secondsLeft = ((duration - position) / 1000).toInt().coerceAtLeast(0),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = PlayerSkin.side, bottom = 34.dp)
                )
            } else if (introOffer) {
                IntroPill(Modifier.align(Alignment.BottomEnd).padding(end = PlayerSkin.side, bottom = 34.dp))
            }
        }

        skipFlash?.let { label ->
            Text(
                label,
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Ink.Void.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }

        // Chargement : commandes masquées, un simple anneau au centre. Commandes affichées,
        // c'est le bouton lecture lui-même qui devient l'anneau (voir PlayRing) — rien ne se superpose.
        if (buffering && !(controlsVisible && sheet == Sheet.NONE)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BufferRing(PlayerSkin.loader)
            }
        }
        if (audioNote.isNotBlank()) {
            LaunchedEffect(audioNote) { delay(6_000); audioNote = "" }
            Text(
                audioNote,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Ink.Void.copy(alpha = 0.6f))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }

        AnimatedVisibility(
            // Un panneau ouvert (pistes, réglages, épisodes) prend toute la télécommande :
            // les commandes disparaissent derrière lui, sinon le curseur s'y promenait.
            visible = controlsVisible && sheet == Sheet.NONE,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(260)),
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) activity++
                false
            }
        ) {
            PlayerControls(
                buffering = buffering,
                toolsFocus = toolsFocus,
                info = info,
                hasEpisodes = seasons.isNotEmpty(),
                preview = preview,
                aspectLabel = ASPECTS[aspect].second,
                onSkip = { delta -> skip(delta) },
                onAspect = { aspect = (aspect + 1) % ASPECTS.size },
                onEpisodes = { sheet = Sheet.EPISODES },
                onSettings = { sheet = Sheet.SETTINGS },
                onTech = { showTech = !showTech },
                position = if (scrubbing) scrubPosition else position,
                duration = duration,
                isPlaying = isPlaying,
                scrubbing = scrubbing,
                hasAudioChoice = audioTracks.size > 1,
                hasSubtitles = subtitleTracks.isNotEmpty(),
                subtitlesOn = subtitlesOn,
                timelineFocus = timelineFocus,
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                    controlsVisible = true
                },
                onScrubStart = {
                    scrubbing = true
                    scrubPosition = player.positionMs
                },
                onScrubBy = { deltaMs ->
                    scrubPosition = (scrubPosition + deltaMs)
                        .coerceIn(0L, duration.coerceAtLeast(0L))
                },
                onScrubCommit = {
                    player.seekTo(scrubPosition)
                    position = scrubPosition
                    scrubbing = false
                },
                onAudio = { sheet = Sheet.AUDIO },
                onSubtitles = { sheet = Sheet.SUBTITLES },
                onExit = onExit
            )
        }

        AnimatedVisibility(
            visible = sheet == Sheet.AUDIO || sheet == Sheet.SUBTITLES,
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(260)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 3 }
        ) {
            TrackSheet(
                title = if (sheet == Sheet.SUBTITLES) "Sous-titres" else "Piste audio",
                options = if (sheet == Sheet.SUBTITLES) subtitleTracks else audioTracks,
                allowDisable = sheet == Sheet.SUBTITLES,
                disabled = sheet == Sheet.SUBTITLES && !subtitlesOn,
                onPick = { option ->
                    val subtitles = sheet == Sheet.SUBTITLES
                    if (subtitles) player.pickSubtitle(option) else player.pickAudio(option)
                    // Le choix devient la préférence par défaut des prochaines lectures
                    val language = option.language
                    if (subtitles) {
                        subtitlesOn = true
                        settings.subtitlesEnabled = true
                        if (language.isNotBlank()) settings.preferredSubtitleLanguage = language
                    } else if (language.isNotBlank()) {
                        settings.preferredAudioLanguage = language
                    }
                    sheet = Sheet.NONE
                },
                onDisable = {
                    player.disableSubtitles()
                    subtitlesOn = false
                    settings.subtitlesEnabled = false
                    sheet = Sheet.NONE
                }
            )
        }

        if (sheet == Sheet.EPISODES) {
            EpisodesPanel(
                seasons = seasons,
                currentId = currentEpisodeId,
                onPick = { id -> sheet = Sheet.NONE; onPickEpisode(id) }
            )
        }

        if (sheet == Sheet.SETTINGS) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                repeat(12) {
                    if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
                    delay(40)
                }
            }
            fun turn(value: Int, step: Int, size: Int) = (value + step + size) % size
            SidePanel(title = "Réglages", note = "◀ ▶ pour changer une valeur") {
                PanelHeading("Sous-titres")
                if (onFindSubtitles != null) {
                    TrackRow("Chercher en ligne…", selected = false) { sheet = Sheet.ONLINE }
                }
                SettingRow(
                    "Taille", SUB_SIZES[subSize].first,
                    onLess = { carryOver = player.positionMs; subSize = turn(subSize, -1, SUB_SIZES.size); settings.subtitleSize = subSize },
                    onMore = { carryOver = player.positionMs; subSize = turn(subSize, 1, SUB_SIZES.size); settings.subtitleSize = subSize },
                    modifier = Modifier.focusRequester(first)
                )
                SettingRow(
                    "Couleur", SUB_COLORS[subColor].first,
                    onLess = { carryOver = player.positionMs; subColor = turn(subColor, -1, SUB_COLORS.size); settings.subtitleColor = subColor },
                    onMore = { carryOver = player.positionMs; subColor = turn(subColor, 1, SUB_COLORS.size); settings.subtitleColor = subColor }
                )
                SettingRow(
                    "Fond", SUB_BACKGROUNDS[subBackground],
                    onLess = { carryOver = player.positionMs; subBackground = turn(subBackground, -1, SUB_BACKGROUNDS.size); settings.subtitleBackground = subBackground },
                    onMore = { carryOver = player.positionMs; subBackground = turn(subBackground, 1, SUB_BACKGROUNDS.size); settings.subtitleBackground = subBackground }
                )
                SettingRow(
                    "Retard", when {
                        subDelayMs == 0L -> "Aucun"
                        subDelayMs > 0 -> "+ %.2f s".format(subDelayMs / 1000f)
                        else -> "− %.2f s".format(-subDelayMs / 1000f)
                    },
                    onLess = {
                        subDelayMs = (subDelayMs - PlayerSkin.subtitleStepMs).coerceAtLeast(-PlayerSkin.subtitleMaxMs)
                        player.setSubtitleDelayMs(subDelayMs)
                    },
                    onMore = {
                        subDelayMs = (subDelayMs + PlayerSkin.subtitleStepMs).coerceAtMost(PlayerSkin.subtitleMaxMs)
                        player.setSubtitleDelayMs(subDelayMs)
                    }
                )
                PanelHeading("Son")
                SettingRow(
                    if (routeDirect) "Décalage · son direct" else "Décalage · son décodé",
                    when {
                        audioOffsetMs == 0L -> "Aucun"
                        audioOffsetMs > 0 -> "Son + $audioOffsetMs ms"
                        else -> "Son − ${-audioOffsetMs} ms"
                    },
                    onLess = {
                        audioOffsetMs = (audioOffsetMs - PlayerSkin.audioStepMs).coerceAtLeast(-PlayerSkin.audioMaxMs)
                        player.setAudioDelayMs(audioOffsetMs)
                        if (routeDirect) settings.audioOffsetDirectMs = audioOffsetMs else settings.audioOffsetMs = audioOffsetMs
                    },
                    onMore = {
                        audioOffsetMs = (audioOffsetMs + PlayerSkin.audioStepMs).coerceAtMost(PlayerSkin.audioMaxMs)
                        player.setAudioDelayMs(audioOffsetMs)
                        if (routeDirect) settings.audioOffsetDirectMs = audioOffsetMs else settings.audioOffsetMs = audioOffsetMs
                    }
                )
                fun audioModeTo(next: Int) {
                    audioMode = next
                    settings.audioMode = next
                    player.setAudioMode(next)
                }
                SettingRow(
                    label = "Sortie du son",
                    value = when (audioMode) {
                        1 -> "Toujours décodée"
                        2 -> "Toujours directe"
                        else -> "Automatique"
                    },
                    onLess = { audioModeTo(turn(audioMode, -1, 3)) },
                    onMore = { audioModeTo(turn(audioMode, 1, 3)) }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    (if (routeDirect) "Ce film : son envoyé tel quel à l'ampli. " else "Ce film : son décodé par Netfloux, en stéréo. ") +
                        "En automatique, le Dolby et le DTS partent directement vers l'ampli ; les autres formats sont décodés. " +
                        "Les deux chemins n'ont pas le même retard : le décalage se règle une fois pour chacun, puis s'applique tout seul. " +
                        "« Son + » fait arriver le son plus tard, « Son − » plus tôt.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.TextLow,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        if (sheet == Sheet.ONLINE && onFindSubtitles != null) {
            // La recherche part à l'ouverture du panneau, une seule fois par lecture
            LaunchedEffect(Unit) {
                if (online == null) {
                    onlineNote = "Recherche…"
                    val (found, failure) = onFindSubtitles()
                    online = found
                    onlineNote = when {
                        found.isNotEmpty() -> "${found.size} résultat(s) — le français d'abord"
                        failure.isNotBlank() -> failure
                        else -> "Aucun sous-titre trouvé pour ce titre"
                    }
                }
            }
            val firstResult = remember { FocusRequester() }
            LaunchedEffect(online?.size) {
                if (online.isNullOrEmpty()) return@LaunchedEffect
                repeat(12) {
                    if (runCatching { firstResult.requestFocus() }.isSuccess) return@LaunchedEffect
                    delay(40)
                }
            }
            SidePanel(title = "Sous-titres en ligne", note = if (fetching) "Téléchargement…" else onlineNote) {
                // Tant qu'il n'y a pas de résultat, le curseur a quand même sa place dans le panneau
                if (online.isNullOrEmpty()) {
                    val waiting = remember { FocusRequester() }
                    LaunchedEffect(Unit) {
                        repeat(15) {
                            if (runCatching { waiting.requestFocus() }.isSuccess) return@LaunchedEffect
                            delay(40)
                        }
                    }
                    Box(Modifier.focusRequester(waiting)) {
                        TrackRow("Retour aux réglages", selected = false) { sheet = Sheet.SETTINGS }
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(online.orEmpty(), key = { it.fileId }) { found ->
                        val index = online.orEmpty().indexOf(found)
                        Box(if (index == 0) Modifier.focusRequester(firstResult) else Modifier) {
                            TrackRow(
                                label = buildString {
                                    append(found.language.uppercase())
                                    if (found.hearingImpaired) append(" · malentendants")
                                    append("  ·  ")
                                    append(found.release.take(46))
                                },
                                selected = false
                            ) {
                                if (fetching || onFetchSubtitle == null) return@TrackRow
                                fetching = true
                                scope.launch {
                                    val (subtitle, failure) = onFetchSubtitle(found)
                                    fetching = false
                                    if (subtitle == null) {
                                        onlineNote = failure
                                    } else {
                                        // Le moteur prend ce sous-titre en plus et l'affiche, sans interrompre la lecture
                                        allSubtitles = allSubtitles + subtitle
                                        player.showSubtitle(subtitle.file)
                                        subtitlesOn = true
                                        settings.subtitlesEnabled = true
                                        sheet = Sheet.NONE
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (stillThere) {
            StillWatchingPrompt(
                onContinue = { WatchStreak.count = 0; stillThere = false },
                onStop = { WatchStreak.count = 0; onExit() }
            )
        }

        if (resumeChoice && !stillThere) {
            ResumePrompt(
                title = title,
                position = startPositionMs,
                onResume = { beginAt = startPositionMs; resumeChoice = false },
                onRestart = { beginAt = 0; resumeChoice = false }
            )
        }

        // Garde le focus quand les contrôles sont masqués : toute touche les rappelle
        if (!controlsVisible && sheet == Sheet.NONE && !resumeChoice && !stillThere) {
            val wake = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { wake.requestFocus() } }
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(wake)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            // Gauche et droite sautent de 10 s sans rappeler tout l'habillage
                            Key.DirectionLeft -> { skip(-10_000); skipFlash = "− 10 s"; skipTick++ }
                            Key.DirectionRight -> { skip(10_000); skipFlash = "+ 10 s"; skipTick++ }
                            Key.Back, Key.Escape -> return@onPreviewKeyEvent false
                            // Haut : droit sur les réglages (audio, sous-titres, image…)
                            Key.DirectionUp -> { landOnTools = true; controlsVisible = true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                                com.cinetv.desktop.KeyRepeat.count > 0 -> Unit
                                nextCard -> onPlayNext()
                                introOffer -> {
                                    skip(PlayerSkin.introSkipMs); introUsed = true
                                    skipFlash = "INTRO PASSÉE"; skipTick++
                                }
                                else -> controlsVisible = true
                            }
                            else -> controlsVisible = true
                        }
                        true
                    }
                    .focusable()
            )
        }
    }
}

// ------------------------------------------------------------- les contrôles

/**
 * L'habillage du lecteur, d'après la maquette : retour et fermeture en haut,
 * lecture et sauts de 10 s au centre, fiche du titre en bas à gauche, épisodes
 * en colonne à droite, et la timeline rouge tout en bas avec ses réglages.
 */
@Composable
private fun PlayerControls(
    info: PlayerInfo,
    hasEpisodes: Boolean,
    preview: ImageBitmap?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    scrubbing: Boolean,
    hasAudioChoice: Boolean,
    hasSubtitles: Boolean,
    subtitlesOn: Boolean,
    aspectLabel: String,
    timelineFocus: FocusRequester,
    onPlayPause: () -> Unit,
    buffering: Boolean,
    toolsFocus: FocusRequester,
    onSkip: (Long) -> Unit,
    onScrubStart: () -> Unit,
    onScrubBy: (Long) -> Unit,
    onScrubCommit: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onAspect: () -> Unit,
    onEpisodes: () -> Unit,
    onSettings: () -> Unit,
    onTech: () -> Unit,
    onExit: () -> Unit
) {
    val playFocus = remember { FocusRequester() }
    val face = remember(info.genres) { GenreType.faceFor(info.genres) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Ink.Void.copy(alpha = PlayerSkin.scrimTop),
                    0.26f to Color.Transparent,
                    0.48f to Color.Transparent,
                    1f to Ink.Void.copy(alpha = PlayerSkin.scrimBottom)
                )
            )
            .background(
                Brush.horizontalGradient(
                    0f to Ink.Void.copy(alpha = PlayerSkin.scrimSide),
                    0.45f to Color.Transparent
                )
            )
    ) {
        // ── Haut : retour à gauche, saison et fermeture à droite
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = PlayerSkin.side, vertical = PlayerSkin.top),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Glyph(R.drawable.ic_back, PlayerSkin.smallGlyph, onClick = onExit)
            Spacer(Modifier.weight(1f))
            if (info.chip.isNotBlank()) {
                // La pastille de saison ouvre le menu des épisodes
                ChipButton(label = info.chip, enabled = hasEpisodes, onClick = onEpisodes)
                Spacer(Modifier.width(18.dp))
            }
            Glyph(R.drawable.ic_close, PlayerSkin.smallGlyph, onClick = onExit)
        }

        // ── Centre : −10 s, lecture, +10 s
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PlayerSkin.transportGap)
        ) {
            Glyph(R.drawable.ic_replay10, PlayerSkin.skipGlyph, onClick = { onSkip(-10_000) })
            PlayRing(isPlaying = isPlaying, loading = buffering, onClick = onPlayPause, modifier = Modifier.focusRequester(playFocus))
            Glyph(R.drawable.ic_forward10, PlayerSkin.skipGlyph, onClick = { onSkip(10_000) })
        }

        // ── Bas : les réglages à droite, juste au-dessus de la timeline. Le tout
        // centré, sur une part de la largeur de l'écran.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(PlayerSkin.widthFraction)
                .padding(bottom = PlayerSkin.bottom)
        ) {
            // Réglages : des pastilles nommées, juste au-dessus de la barre. Une flèche vers le
            // haut depuis la barre y mène, une flèche vers le bas en revient.
            Row(
                Modifier
                    .fillMaxWidth()
                    .focusProperties { down = timelineFocus; up = playFocus },
                horizontalArrangement = Arrangement.spacedBy(PlayerSkin.toolGap, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // La première pastille présente sert de point d'arrivée
                val anchor = Modifier.focusRequester(toolsFocus)
                if (hasAudioChoice) {
                    ToolPill(R.drawable.ic_audio, "Audio", onClick = onAudio, modifier = anchor)
                }
                if (hasSubtitles) {
                    ToolPill(
                        R.drawable.ic_subtitles, "Sous-titres", onClick = onSubtitles,
                        dimmed = !subtitlesOn,
                        modifier = if (hasAudioChoice) Modifier else anchor
                    )
                }
                ToolPill(
                    R.drawable.ic_aspect, "Image", onClick = onAspect,
                    modifier = if (hasAudioChoice || hasSubtitles) Modifier else anchor
                )
                ToolPill(R.drawable.ic_settings, "Réglages", onClick = onSettings)
                ToolPill(R.drawable.ic_info, "Infos", onClick = onTech)
            }

            Spacer(Modifier.height(PlayerSkin.toolsToBar))

            Row(verticalAlignment = Alignment.Bottom) {
                Timeline(
                    position = position,
                    duration = duration,
                    scrubbing = scrubbing,
                    focusRequester = timelineFocus,
                    onScrubStart = onScrubStart,
                    onScrubBy = onScrubBy,
                    onScrubCommit = onScrubCommit,
                    onPlayPause = onPlayPause,
                    preview = preview,
                    modifier = Modifier
                        .weight(1f)
                        .focusProperties { up = toolsFocus }
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    formatTime((duration - position).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            // Rappel discret, seulement pendant un déplacement sur la timeline
            Text(
                text = if (scrubbing) "OK POUR ALLER À CETTE POSITION  ·  RETOUR POUR ANNULER" else "",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.Lilac,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Un pictogramme cliquable, sans fond : au focus il grossit et prend le rouge du logo. */
@Composable
internal fun Glyph(
    icon: Int,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tint by animateColorAsState(
        when {
            focused -> Ink.LogoRed
            dimmed -> Color.White.copy(alpha = 0.42f)
            else -> Color.White
        },
        tween(160), label = "glyphTint"
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = PlayerSkin.glyphFocusScale),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None),
        glow = ClickableSurfaceDefaults.glow(),
        modifier = modifier
    ) {
        CineIcon(icon, tint = tint, modifier = Modifier.padding(4.dp).size(size))
    }
}

/** Le grand bouton lecture / pause : un anneau blanc, plein quand il est sélectionné. */
@Composable
private fun PlayRing(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Chargement : le pictogramme s'efface et l'anneau se met à tourner. */
    loading: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val rim = if (loading) Color.White.copy(alpha = 0.16f) else Color.White
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Ink.Void.copy(alpha = 0.18f),
            focusedContainerColor = if (loading) Ink.Void.copy(alpha = 0.18f) else Color.White,
            pressedContainerColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (loading) 1f else 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(PlayerSkin.ringStroke, rim), shape = CircleShape),
            focusedBorder = Border(BorderStroke(PlayerSkin.ringStroke, rim), shape = CircleShape)
        ),
        glow = ClickableSurfaceDefaults.glow(),
        modifier = modifier.size(PlayerSkin.ring)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                BufferRing(PlayerSkin.ring, stroke = PlayerSkin.ringStroke)
            } else {
                CineIcon(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    tint = if (focused) Ink.Void else Color.White,
                    modifier = Modifier.size(PlayerSkin.ring * 0.46f)
                )
            }
        }
    }
}

/** Pastille de réglage du lecteur : pictogramme et nom, blanche quand le curseur est dessus. */
@Composable
private fun ToolPill(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(50)
    val ink = when {
        focused -> Ink.Void
        dimmed -> Color.White.copy(alpha = 0.55f)
        else -> Color.White
    }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White,
            pressedContainerColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = ClickableSurfaceDefaults.glow(),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CineIcon(icon, tint = ink, modifier = Modifier.size(PlayerSkin.toolGlyph * 0.8f))
            Spacer(Modifier.width(7.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = ink,
                maxLines = 1
            )
        }
    }
}

// ------------------------------------------------------------ les épisodes

/** La pastille de saison, en haut à droite. */
@Composable
private fun ChipButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    if (!enabled) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .border(1.dp, Color.White.copy(alpha = 0.55f), shape)
                .padding(horizontal = 11.dp, vertical = 5.dp)
        )
        return
    }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Ink.Void
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)), shape = shape),
            focusedBorder = Border.None
        )
    ) {
        Text(
            "${label.uppercase()}  ▾",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
        )
    }
}

@Composable
internal fun EpisodeCard(
    episode: PlayerEpisode,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(PlayerSkin.episodeRadius)
    val dim by animateFloatAsState(if (focused) 1f else if (playing) 0.82f else 0.5f, tween(180), label = "episodeDim")

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Ink.SurfaceHi,
            focusedContainerColor = Ink.SurfaceHi,
            pressedContainerColor = Ink.SurfaceHi
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(BorderStroke(1.5.dp, Color.White), shape = shape)
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Ink.Void, 14.dp)),
        modifier = modifier.width(PlayerSkin.episodeWidth).height(PlayerSkin.episodeHeight)
    ) {
        Box(Modifier.fillMaxSize().alpha(dim)) {
            if (episode.thumbUrl.isNotBlank()) {
                AsyncImage(
                    model = episode.thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.25f to Color.Transparent, 1f to Ink.Void.copy(alpha = 0.92f))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 9.dp)) {
                if (playing) {
                    Text("EN LECTURE", style = MaterialTheme.typography.labelSmall, color = Ink.LogoRed)
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    episode.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.note.isNotBlank()) {
                    Text(
                        episode.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.66f),
                        maxLines = 1
                    )
                }
            }
            if (episode.progress > 0f) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.22f))
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(episode.progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(Ink.LogoRed)
                )
            }
        }
    }
}

// ------------------------------------------------------------- la timeline

/**
 * Timeline navigable à la télécommande.
 *
 * Gauche et droite déplacent un curseur sans interrompre la lecture ; le saut
 * n'a lieu qu'à la validation. Les pressions maintenues accélèrent le
 * déplacement. Sans déplacement en cours, OK met en pause ou relance.
 */
@Composable
private fun Timeline(
    position: Long,
    duration: Long,
    scrubbing: Boolean,
    focusRequester: FocusRequester,
    onScrubStart: () -> Unit,
    onScrubBy: (Long) -> Unit,
    onScrubCommit: () -> Unit,
    onPlayPause: () -> Unit,
    preview: ImageBitmap? = null,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    var repeatCount by remember { mutableIntStateOf(0) }
    val step: Long = when {
        repeatCount > 22 -> 120_000
        repeatCount > 12 -> 60_000
        repeatCount > 5 -> 30_000
        else -> 10_000
    }

    val barHeight by animateDpAsState(
        targetValue = if (focused) PlayerSkin.barFocused else PlayerSkin.bar,
        animationSpec = tween(180),
        label = "barHeight"
    )
    val knobSize by animateDpAsState(
        targetValue = if (scrubbing) PlayerSkin.knob * 1.35f else if (focused) PlayerSkin.knob else PlayerSkin.knob * 0.6f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "knobSize"
    )

    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) repeatCount = 0
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (!scrubbing) onScrubStart()
                        repeatCount++
                        onScrubBy(-step)
                        true
                    }
                    Key.DirectionRight -> {
                        if (!scrubbing) onScrubStart()
                        repeatCount++
                        onScrubBy(step)
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (scrubbing) onScrubCommit() else if (com.cinetv.desktop.KeyRepeat.count == 0) onPlayPause()
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
    ) {
        val bubble = PlayerSkin.timeBubble
        val full = maxWidth
        val knobX = full * fraction
        Column {
            // Pendant le déplacement, l'image de l'endroit visé
            if (scrubbing && preview != null) {
                val wide = PlayerSkin.previewWidth
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset(x = (knobX - wide / 2).coerceIn(0.dp, (full - wide).coerceAtLeast(0.dp)))
                        .width(wide)
                        .height(wide * 0.5625f)
                        .clip(RoundedCornerShape(Radius.tile))
                        .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(Radius.tile))
                )
                Spacer(Modifier.height(6.dp))
            }
            // L'heure suit le curseur, comme sur la maquette
            Text(
                formatTime(position),
                style = MaterialTheme.typography.labelLarge,
                color = if (scrubbing) Ink.Lilac else Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .offset(x = (knobX - bubble / 2).coerceIn(0.dp, (full - bubble).coerceAtLeast(0.dp)))
                    .width(bubble)
            )
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = if (focused) 0.34f else 0.24f))
                )
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(barHeight)
                        .clip(RoundedCornerShape(50))
                        .background(Ink.LogoRed)
                )
                Box(
                    Modifier
                        .offset(x = (knobX - knobSize / 2).coerceIn(0.dp, (full - knobSize).coerceAtLeast(0.dp)))
                        .size(knobSize)
                        .clip(CircleShape)
                        .background(if (scrubbing) Color.White else Ink.LogoRed)
                )
            }
        }
    }
}

@Composable
private fun TrackSheet(
    title: String,
    options: List<TrackOption>,
    allowDisable: Boolean,
    disabled: Boolean,
    onPick: (TrackOption) -> Unit,
    onDisable: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Void.copy(alpha = 0.74f)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            Modifier
                .width(460.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Ink.Surface, Ink.Deep)))
                .padding(36.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Ink.TextHi)
            Spacer(Modifier.height(6.dp))
            Text(
                "${options.size} piste(s) dans le fichier",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.TextLow
            )
            Spacer(Modifier.height(22.dp))

            // Le curseur se pose sur la piste en cours, sinon sur la première ligne
            val landing = remember { FocusRequester() }
            val current = if (disabled) -1 else options.indexOfFirst { it.selected }
            LaunchedEffect(Unit) {
                repeat(15) {
                    if (runCatching { landing.requestFocus() }.isSuccess) return@LaunchedEffect
                    delay(40)
                }
            }
            if (allowDisable) {
                Box(if (current < 0) Modifier.focusRequester(landing) else Modifier) {
                    TrackRow(label = "Désactivés", selected = disabled, onClick = onDisable)
                }
            }
            options.forEachIndexed { index, option ->
                val here = index == current || (current < 0 && !allowDisable && index == 0)
                Box(if (here) Modifier.focusRequester(landing) else Modifier) {
                    TrackRow(
                        label = option.label,
                        selected = option.selected && !disabled,
                        onClick = { onPick(option) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.control)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Ink.Violet.copy(alpha = 0.20f) else Color.Transparent,
            contentColor = Ink.TextHi,
            focusedContainerColor = Ink.Violet,
            focusedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Ink.Lilac), shape = shape)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            if (selected) CineIcon(R.drawable.ic_check, modifier = Modifier.size(17.dp))
        }
    }
}

/**
 * Chargement : deux arcs fins qui tournent en sens contraires et respirent,
 * l'un blanc, l'autre aux couleurs du logo. Tout est continu, sans à-coup.
 */
/**
 * Indicateur de chargement : un anneau à peine visible, parcouru par un trait blanc
 * qui s'estompe. Rien d'autre.
 */
@Composable
private fun BufferRing(size: androidx.compose.ui.unit.Dp, stroke: androidx.compose.ui.unit.Dp = 2.dp) {
    val transition = rememberInfiniteTransition(label = "buffer")
    val turn by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
        label = "bufferTurn"
    )
    Canvas(Modifier.size(size)) {
        val line = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
        val inset = line.width / 2f
        val box = Size(this.size.width - line.width, this.size.height - line.width)
        drawArc(
            color = Color.White.copy(alpha = 0.14f), startAngle = 0f, sweepAngle = 360f,
            useCenter = false, topLeft = Offset(inset, inset), size = box, style = line
        )
        rotate(turn) {
            drawArc(
                brush = Brush.sweepGradient(0f to Color.Transparent, 0.30f to Color.White, 1f to Color.White),
                startAngle = 0f, sweepAngle = 108f,
                useCenter = false, topLeft = Offset(inset, inset), size = box, style = line
            )
        }
    }
}

// ------------------------------------------------------------------ helpers

internal fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun formatStep(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 60) "${seconds / 60} min" else "$seconds s"
}

private fun languageName(code: String?): String = when (code?.lowercase()?.take(2)) {
    null, "", "und" -> "Piste"
    "fr" -> "Français"
    "en" -> "Anglais"
    "es" -> "Espagnol"
    "de" -> "Allemand"
    "it" -> "Italien"
    "ja" -> "Japonais"
    "pt" -> "Portugais"
    "nl" -> "Néerlandais"
    "ru" -> "Russe"
    "zh" -> "Chinois"
    "ko" -> "Coréen"
    "ar" -> "Arabe"
    "pl" -> "Polonais"
    "sv" -> "Suédois"
    "da" -> "Danois"
    "fi" -> "Finnois"
    "no" -> "Norvégien"
    "tr" -> "Turc"
    else -> code.uppercase()
}

private fun channelName(count: Int): String = when (count) {
    1 -> "Mono"
    2 -> "Stéréo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count canaux"
}

private fun codecName(mime: String?): String? = when {
    mime == null -> null
    mime.contains("eac3", true) -> "E-AC3"
    mime.contains("ac3", true) -> "AC3"
    mime.contains("dts", true) -> "DTS"
    mime.contains("truehd", true) -> "TrueHD"
    mime.contains("flac", true) -> "FLAC"
    mime.contains("opus", true) -> "Opus"
    mime.contains("vorbis", true) -> "Vorbis"
    mime.contains("aac", true) -> "AAC"
    else -> null
}

// -------------------------------------------------------- surcouches

/** Choix posé avant la lecture d'un titre déjà entamé. */
@Composable
private fun ResumePrompt(
    title: String,
    position: Long,
    onResume: () -> Unit,
    onRestart: () -> Unit
) {
    val resumeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { resumeFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Ink.Void.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge,
                color = Ink.TextHi,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Vous vous étiez arrêté à ${formatTime(position)}",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.TextMid
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChoiceButton("Reprendre", primary = true, focusRequester = resumeFocus, onClick = onResume)
                ChoiceButton("Recommencer", onClick = onRestart)
            }
        }
    }
}

/** Posé après plusieurs épisodes enchaînés sans qu'aucune touche n'ait été pressée. */
@Composable
private fun StillWatchingPrompt(onContinue: () -> Unit, onStop: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(12) {
            if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
    Box(
        Modifier.fillMaxSize().background(Ink.Void.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("VOUS REGARDEZ TOUJOURS ?", style = MaterialTheme.typography.headlineLarge, color = Ink.TextHi)
            Spacer(Modifier.height(10.dp))
            Text(
                "Plusieurs épisodes se sont enchaînés sans que la télécommande soit touchée",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.TextMid
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChoiceButton("Continuer", primary = true, focusRequester = focus, onClick = onContinue)
                ChoiceButton("Arrêter", onClick = onStop)
            }
        }
    }
}

@Composable
internal fun ChoiceButton(
    label: String,
    primary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Radius.control)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Ink.Violet.copy(alpha = 0.26f) else Color.White.copy(alpha = 0.10f),
            contentColor = Ink.TextHi,
            focusedContainerColor = Color.White,
            focusedContentColor = Ink.Void
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = androidx.tv.material3.Glow(Ink.Lilac, 16.dp)
        ),
        modifier = Modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
        )
    }
}

// ------------------------------------------------ fiche et liste d'épisodes

private val EPISODE = Regex("""S(\d{1,2})\s?E(\d{1,3})""", RegexOption.IGNORE_CASE)
private val EPISODE_SUFFIX = Regex("""\s*[·\-–]?\s*S\d{1,2}\s?E\d{1,3}.*$""", RegexOption.IGNORE_CASE)

/** Saison et numéro d'épisode, lus dans le titre ou à défaut dans le nom du fichier. */
private fun episodeOf(node: Node): Pair<Int, Int>? =
    (EPISODE.find(node.title) ?: EPISODE.find(node.name))?.let {
        (it.groupValues[1].toIntOrNull() ?: 0) to (it.groupValues[2].toIntOrNull() ?: 0)
    }

private val GENRE_NAMES = mapOf(
    28 to "Action", 12 to "Aventure", 16 to "Animation", 35 to "Comédie", 80 to "Crime",
    99 to "Documentaire", 18 to "Drame", 10751 to "Familial", 14 to "Fantastique", 36 to "Histoire",
    27 to "Horreur", 10402 to "Musique", 9648 to "Mystère", 10749 to "Romance",
    878 to "Science-fiction", 53 to "Thriller", 10752 to "Guerre", 37 to "Western",
    10759 to "Action & aventure", 10762 to "Jeunesse", 10765 to "Science-fiction & fantastique",
    10768 to "Guerre & politique", 10764 to "Téléréalité", 10766 to "Feuilleton"
)

/** Ce que le lecteur écrit en bas à gauche pour ce fichier. */
fun playerInfoOf(node: Node): PlayerInfo {
    val genre = node.genres.split(',').firstNotNullOfOrNull { id -> id.trim().toIntOrNull()?.let { GENRE_NAMES[it] } }
    val year = node.year.takeIf { it > 0 }?.toString()
    val episode = episodeOf(node)
    return if (episode != null) {
        val (season, number) = episode
        PlayerInfo(
            headline = "Épisode $number",
            overline = node.title.replace(EPISODE_SUFFIX, "").trim(),
            meta = listOfNotNull("Saison $season", year, genre).joinToString("  ·  "),
            synopsis = node.synopsis,
            chip = "Saison $season",
            genres = node.genres,
            isEpisode = true,
            logoUrl = node.logoUrl
        )
    } else {
        PlayerInfo(
            headline = node.displayTitle,
            overline = node.director.takeIf { it.isNotBlank() }?.let { "De $it" }.orEmpty(),
            meta = listOfNotNull(
                year, genre, node.rating.takeIf { it > 0f }?.let { "★ %.1f".format(it) }
            ).joinToString("  ·  "),
            synopsis = node.synopsis,
            genres = node.genres,
            logoUrl = node.logoUrl
        )
    }
}

private val SEASON_FOLDER = Regex("""^\s*(saison|season|s)\s*\d{1,2}\b""", RegexOption.IGNORE_CASE)

/** Ce dossier est-il un dossier de saison (« Saison 2 », « S02 »…) ? */
fun isSeasonFolder(name: String): Boolean = SEASON_FOLDER.containsMatchIn(name)

/**
 * Le menu des épisodes : les fichiers de la série, rangés par saison.
 * [pool] contient le dossier de l'épisode et, s'il s'agit d'un dossier de
 * saison, ceux des saisons voisines. Vide pour un film.
 */
fun playerSeasonsOf(current: Node, pool: List<Node>): List<PlayerSeason> {
    if (episodeOf(current) == null) return emptyList()
    return pool
        .mapNotNull { node -> episodeOf(node)?.let { it to node } }
        .groupBy({ it.first.first }, { it })
        .toSortedMap()
        .map { (season, entries) ->
            PlayerSeason(
                number = season,
                episodes = entries.sortedBy { it.first.second }.map { (tag, node) ->
                    val left = node.durationMs - node.lastPositionMs
                    PlayerEpisode(
                        id = node.id,
                        label = "${tag.second}. Épisode ${tag.second}",
                        note = when {
                            node.isWatched -> "Vu"
                            node.durationMs > 0 && node.lastPositionMs > 0 ->
                                "${(left / 60_000).coerceAtLeast(1)} min restantes"
                            node.durationMs > 0 -> minutesLabel(node.durationMs)
                            else -> ""
                        },
                        thumbUrl = node.backdropUrl.ifBlank { node.posterUrl },
                        progress = if (node.isWatched) 1f else node.progressFraction
                    )
                }
            )
        }
}

private fun minutesLabel(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
}


