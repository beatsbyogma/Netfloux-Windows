package com.cinetv.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.model.Volume
import com.cinetv.data.prefs.GridGap
import com.cinetv.data.prefs.HiddenFolder
import com.cinetv.ui.theme.AccentTheme
import com.cinetv.ui.theme.Grid
import com.cinetv.data.prefs.Settings
import com.cinetv.data.repo.LibraryRepository
import com.cinetv.data.tmdb.Tmdb
import com.cinetv.data.subs.OpenSubtitles
import com.cinetv.ui.home.PillButton
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.cinetv.desktop.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.cinetv.ui.components.CineIcon
import com.cinetv.ui.components.MenuAction
import com.cinetv.ui.components.SimpleMenu
import com.cinetv.data.prefs.homeSectionLabel
import com.cinetv.ui.components.FocusStyle
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Radius
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: LibraryRepository,
    context: Context
) : ViewModel() {

    private val settings = Settings(context)

    private val _state = MutableStateFlow(snapshot())
    val state = _state.asStateFlow()

    private val _volumes = MutableStateFlow<List<Volume>>(emptyList())
    val volumes = _volumes.asStateFlow()

    data class State(
        val columns: Int,
        val gap: GridGap,
        val discoverPosters: Boolean,
        val accent: AccentTheme,
        val homeOrder: List<String>,
        val homeHidden: Set<String>,
        val homeSizes: Map<String, com.cinetv.data.prefs.HomeSize>,
        val hiddenFolders: List<HiddenFolder>,
        val audioLanguage: String,
        val subtitlesEnabled: Boolean,
        val subtitleLanguage: String,
        val autoPlayNext: Boolean,
        val screensaverMinutes: Int
    )

    private fun snapshot() = State(
        columns = settings.gridColumns,
        gap = settings.gridGap,
        discoverPosters = settings.discoverPosters,
        accent = settings.accentTheme,
        homeOrder = settings.homeOrder,
        homeHidden = settings.homeHidden,
        homeSizes = settings.homeSizes,
        hiddenFolders = settings.hiddenFolders,
        audioLanguage = settings.preferredAudioLanguage,
        subtitlesEnabled = settings.subtitlesEnabled,
        subtitleLanguage = settings.preferredSubtitleLanguage,
        autoPlayNext = settings.autoPlayNext,
        screensaverMinutes = settings.screensaverMinutes
    )

    init {
        viewModelScope.launch {
            repo.revision.collect {
                _volumes.value = repo.volumes()
                _unmatched.value = repo.unmatched().size
            }
        }
    }

    private val languages = listOf("fr", "en", "es", "de", "it", "ja", "")

    fun cycleColumns() {
        val now = settings.gridColumns
        settings.gridColumns = if (now >= Grid.columnsMax) Grid.columnsMin else now + 1
        _state.value = snapshot()
    }

    /** Accueil : taille suivante pour les cartes d'une rubrique (petite, normale, grande, très grande). */
    fun cycleHomeSize(key: String) {
        val sizes = settings.homeSizes
        settings.homeSizes = sizes + (key to (sizes[key] ?: com.cinetv.data.prefs.HomeSize.NORMALE).next())
        _state.value = snapshot()
    }

    /** Accueil : monte (-1) ou descend (+1) une rubrique dans l'ordre d'affichage. */
    fun moveHomeSection(key: String, step: Int) {
        val order = settings.homeOrder.toMutableList()
        val from = order.indexOf(key)
        val to = from + step
        if (from < 0 || to !in order.indices) return
        order.removeAt(from)
        order.add(to, key)
        settings.homeOrder = order
        _state.value = snapshot()
    }

    /** Réaffiche un dossier caché. */
    fun showFolder(folderId: String) = viewModelScope.launch {
        repo.showFolder(folderId)
        _state.value = snapshot()
    }

    fun toggleHomeSection(key: String) {
        val hidden = settings.homeHidden
        settings.homeHidden = if (key in hidden) hidden - key else hidden + key
        _state.value = snapshot()
    }

    fun toggleDiscoverPosters() { settings.discoverPosters = !settings.discoverPosters; _state.value = snapshot() }

    fun cycleGap() { settings.gridGap = settings.gridGap.next(); _state.value = snapshot() }

    /** Change la couleur d'accent : toute l'application la prend aussitôt. */
    fun cycleAccent() {
        val next = settings.accentTheme.next()
        settings.accentTheme = next
        Ink.apply(next)
        _state.value = snapshot()
    }

    fun cycleAudioLanguage() {
        val i = languages.indexOf(settings.preferredAudioLanguage).coerceAtLeast(0)
        settings.preferredAudioLanguage = languages[(i + 1) % languages.size]
        _state.value = snapshot()
    }

    fun toggleSubtitles() { settings.subtitlesEnabled = !settings.subtitlesEnabled; _state.value = snapshot() }

    fun cycleSubtitleLanguage() {
        val i = languages.indexOf(settings.preferredSubtitleLanguage).coerceAtLeast(0)
        settings.preferredSubtitleLanguage = languages[(i + 1) % languages.size]
        _state.value = snapshot()
    }

    fun toggleAutoNext() { settings.autoPlayNext = !settings.autoPlayNext; _state.value = snapshot() }

    fun cycleScreensaver() {
        val steps = listOf(0, 3, 5, 10, 20)
        val i = steps.indexOf(settings.screensaverMinutes).coerceAtLeast(0)
        settings.screensaverMinutes = steps[(i + 1) % steps.size]
        _state.value = snapshot()
    }

    /** Compte rendu de la dernière sauvegarde ou restauration. */
    private val _backupStatus = MutableStateFlow("")
    val backupStatus = _backupStatus.asStateFlow()

    fun exportBackup() = viewModelScope.launch {
        _backupStatus.value = "Sauvegarde en cours…"
        _backupStatus.value = repo.exportBackup()
    }

    fun restoreBackup() = viewModelScope.launch {
        _backupStatus.value = "Restauration en cours…"
        _backupStatus.value = repo.restoreBackup()
    }

    // ── Bibliothèque : catalogue, fichiers non reconnus
    private val _catalogueStatus = MutableStateFlow("")
    val catalogueStatus = _catalogueStatus.asStateFlow()
    private val _unmatched = MutableStateFlow(0)
    val unmatched = _unmatched.asStateFlow()

    fun exportCatalogue() = viewModelScope.launch {
        _catalogueStatus.value = "Écriture du catalogue…"
        _catalogueStatus.value = repo.exportCatalogue()
    }

    // ── Sous-titres en ligne
    private val _subtitlesStatus = MutableStateFlow(subtitlesSummary())
    val subtitlesStatus = _subtitlesStatus.asStateFlow()
    val subtitlesKey: String get() = settings.subtitlesKey

    private fun subtitlesSummary(): String =
        if (OpenSubtitles.isConfigured) "Clé enregistrée : la recherche est disponible dans le lecteur, rubrique Réglages"
        else "Clé gratuite sur opensubtitles.com (compte, puis « API consumers »). Sans elle, pas de recherche en ligne"

    fun saveSubtitlesKey(value: String) {
        settings.subtitlesKey = value
        OpenSubtitles.apiKey = settings.subtitlesKey
        _subtitlesStatus.value = subtitlesSummary()
    }

    // ── Clé TMDB
    private val _tmdbStatus = MutableStateFlow(tmdbSummary())
    val tmdbStatus = _tmdbStatus.asStateFlow()
    val tmdbKey: String get() = settings.tmdbKey

    private fun tmdbSummary(): String = when {
        Tmdb.usesOwnKey -> "Votre clé personnelle est utilisée"
        Tmdb.isConfigured -> "Clé fournie avec l'application — vous pouvez la remplacer par la vôtre"
        else -> "Aucune clé : pas de jaquettes ni de résumés. Clé gratuite sur themoviedb.org, rubrique API"
    }

    /** Enregistre la clé (vide = retour à celle de l'app), la vérifie, puis relance la reconnaissance. */
    fun saveTmdbKey(value: String) = viewModelScope.launch {
        settings.tmdbKey = value
        Tmdb.userKey = settings.tmdbKey
        if (!Tmdb.isConfigured) { _tmdbStatus.value = tmdbSummary(); return@launch }
        _tmdbStatus.value = "Vérification de la clé…"
        val accepted = withContext(Dispatchers.IO) { Tmdb.keyWorks() }
        _tmdbStatus.value = if (accepted) {
            tmdbSummary() + " · clé acceptée"
        } else {
            "Clé refusée par TMDB, ou pas d'internet : vérifiez la saisie"
        }
        if (accepted) {
            repo.enrichPendingMetadata()
            repo.enrichCredits()
        }
    }

    fun rescan() = viewModelScope.launch {
        repo.scanAll()
        repo.enrichPendingMetadata()
    }

    fun forget(volume: Volume) = viewModelScope.launch { repo.forgetVolume(volume.id) }
}

private val SIDE = Grid.side

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenStats: () -> Unit = {},
    onOpenUnmatched: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val volumes by viewModel.volumes.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val tmdbStatus by viewModel.tmdbStatus.collectAsStateWithLifecycle()
    var editingKey by remember { mutableStateOf(false) }
    val catalogueStatus by viewModel.catalogueStatus.collectAsStateWithLifecycle()
    val unmatched by viewModel.unmatched.collectAsStateWithLifecycle()
    val subtitlesStatus by viewModel.subtitlesStatus.collectAsStateWithLifecycle()
    var editingSubtitlesKey by remember { mutableStateOf(false) }
    // Rubrique de l'accueil en cours de réglage (déplacer, masquer)
    var homeSection by remember { mutableStateOf<String?>(null) }
    var showingHidden by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Ink.Void)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item("title") {
                Column(Modifier.padding(start = SIDE, end = SIDE, top = 48.dp, bottom = 24.dp)) {
                    Text("Paramètres", style = MaterialTheme.typography.displayLarge, color = Ink.TextHi)
                }
            }

            item("display") { Section("Affichage") }
            item("accent") {
                SettingRow(
                    icon = R.drawable.ic_sparkle,
                    label = "Couleur du thème",
                    value = state.accent.label,
                    hint = "Lueurs de sélection, barres de progression, accents des menus",
                    onClick = viewModel::cycleAccent
                )
            }
            item("columns") {
                SettingRow(
                    icon = R.drawable.ic_grid,
                    label = "Grille des dossiers",
                    value = "${state.columns} vignettes par ligne",
                    onClick = viewModel::cycleColumns
                )
            }
            item("gap") {
                SettingRow(
                    icon = R.drawable.ic_aspect,
                    label = "Espace entre les vignettes",
                    value = state.gap.label,
                    onClick = viewModel::cycleGap
                )
            }
            item("discoverCards") {
                SettingRow(
                    icon = R.drawable.ic_image,
                    label = "Cartes de « À voir » et « Sorties récentes »",
                    value = if (state.discoverPosters) "Jaquettes verticales" else "Cartes horizontales",
                    hint = "Pris en compte au prochain retour sur l'accueil",
                    onClick = viewModel::toggleDiscoverPosters
                )
            }
            item("screensaver") {
                SettingRow(
                    icon = R.drawable.ic_image,
                    label = "Économiseur d'écran",
                    value = if (state.screensaverMinutes == 0) "Désactivé"
                            else "Après ${state.screensaverMinutes} min",
                    hint = "Fait défiler les visuels de votre bibliothèque",
                    onClick = viewModel::cycleScreensaver
                )
            }

            item("home") { Section("Accueil") }
            state.homeOrder.forEachIndexed { index, key ->
                item("home_$key") {
                    SettingRow(
                        icon = R.drawable.ic_home,
                        label = "${index + 1}. ${homeSectionLabel(key)}",
                        value = (if (key in state.homeHidden) "Masquée" else "Affichée") +
                            (state.homeSizes[key]?.let { " · ${it.label}" } ?: ""),
                        hint = if (index == 0) "La bannière reste toujours en haut — OK pour déplacer, masquer ou changer la taille" else "",
                        onClick = { homeSection = key }
                    )
                }
            }

            item("playback") { Section("Lecture") }
            item("audio") {
                SettingRow(
                    icon = R.drawable.ic_audio,
                    label = "Langue audio préférée",
                    value = languageLabel(state.audioLanguage),
                    hint = "Appliquée d'office si la piste existe",
                    onClick = viewModel::cycleAudioLanguage
                )
            }
            item("subs") {
                SettingRow(
                    icon = R.drawable.ic_subtitles,
                    label = "Sous-titres",
                    value = if (state.subtitlesEnabled) "Activés" else "Désactivés",
                    onClick = viewModel::toggleSubtitles
                )
            }
            if (state.subtitlesEnabled) {
                item("subsLang") {
                    SettingRow(
                        icon = R.drawable.ic_subtitles,
                        label = "Langue des sous-titres",
                        value = languageLabel(state.subtitleLanguage),
                        onClick = viewModel::cycleSubtitleLanguage
                    )
                }
            }
            item("autonext") {
                SettingRow(
                    icon = R.drawable.ic_play,
                    label = "Enchaîner l'épisode suivant",
                    value = if (state.autoPlayNext) "Oui" else "Non",
                    hint = "À la fin d'un fichier, propose le suivant du dossier",
                    onClick = viewModel::toggleAutoNext
                )
            }

            item("library") { Section("Bibliothèque") }
            item("pcDrives") {
                SettingRow(
                    icon = R.drawable.ic_disc,
                    label = "Disques affichés",
                    value = com.cinetv.desktop.DrivePickerState.summary(),
                    hint = "Choisissez les disques du PC que Netfloux montre",
                    onClick = { com.cinetv.desktop.DrivePickerState.open = true }
                )
            }
            item("rescan") {
                SettingRow(
                    icon = R.drawable.ic_disc,
                    label = "Relancer l'analyse",
                    value = "",
                    hint = "Prend en compte les fichiers ajoutés",
                    onClick = viewModel::rescan
                )
            }
            items(volumes.size, key = { "vol_${volumes[it].id}" }) { index ->
                val volume = volumes[index]
                SettingRow(
                    icon = R.drawable.ic_close,
                    label = "Oublier « ${volume.label} »",
                    value = "",
                    hint = "Retire la fiche et son cache ; le disque n'est pas touché",
                    onClick = { viewModel.forget(volume) }
                )
            }

            item("backupTitle") { Section("Sauvegarde") }
            item("backupExport") {
                SettingRow(
                    icon = R.drawable.ic_check,
                    label = "Sauvegarder la bibliothèque",
                    value = "",
                    hint = backupStatus.ifBlank {
                        "Fiches, jaquettes choisies, thèmes, films vus et reprises — écrits sur vos dossiers réseau"
                    },
                    onClick = viewModel::exportBackup
                )
            }
            item("backupRestore") {
                SettingRow(
                    icon = R.drawable.ic_replay,
                    label = "Restaurer une sauvegarde",
                    value = "",
                    hint = "Relit le fichier netfloux-sauvegarde.json d'un dossier réseau",
                    onClick = viewModel::restoreBackup
                )
            }

            item("libraryTitle") { Section("Bibliothèque") }
            item("stats") {
                SettingRow(
                    icon = R.drawable.ic_sparkle,
                    label = "Statistiques",
                    value = "",
                    hint = "Heures regardées, titres vus, genres préférés, mois par mois",
                    onClick = onOpenStats
                )
            }
            item("unmatched") {
                SettingRow(
                    icon = R.drawable.ic_image,
                    label = "Fichiers non reconnus",
                    value = if (unmatched > 0) "$unmatched" else "AUCUN",
                    hint = "Les vidéos sans fiche, réunies sur une page pour les corriger à la suite",
                    onClick = onOpenUnmatched
                )
            }
            item("hiddenFolders") {
                SettingRow(
                    icon = R.drawable.ic_eye,
                    label = "Dossiers cachés",
                    value = if (state.hiddenFolders.isEmpty()) "AUCUN" else "${state.hiddenFolders.size}",
                    hint = "Pour cacher un dossier : appui long dessus, dans son disque",
                    onClick = { if (state.hiddenFolders.isNotEmpty()) showingHidden = true }
                )
            }
            item("catalogue") {
                SettingRow(
                    icon = R.drawable.ic_sort,
                    label = "Exporter le catalogue",
                    value = "",
                    hint = catalogueStatus.ifBlank {
                        "La liste de toutes vos vidéos, disque par disque, dans un tableau enregistré sur vos dossiers réseau"
                    },
                    onClick = viewModel::exportCatalogue
                )
            }

            item("subsTitle") { Section("Sous-titres en ligne") }
            item("subsKey") {
                SettingRow(
                    icon = R.drawable.ic_subtitles,
                    label = "Clé OpenSubtitles",
                    value = if (viewModel.subtitlesKey.isNotBlank()) "ENREGISTRÉE" else "ABSENTE",
                    hint = subtitlesStatus,
                    onClick = { editingSubtitlesKey = true }
                )
            }

            item("tmdbTitle") { Section("Jaquettes et résumés") }
            item("tmdbKey") {
                SettingRow(
                    icon = R.drawable.ic_image,
                    label = "Clé TMDB",
                    value = if (Tmdb.usesOwnKey) "PERSONNELLE" else if (Tmdb.isConfigured) "FOURNIE" else "ABSENTE",
                    hint = tmdbStatus,
                    onClick = { editingKey = true }
                )
            }

            item("tail") { Spacer(Modifier.height(56.dp)) }
        }
    }

    if (editingKey) {
        KeyDialog(
            title = "Clé TMDB",
            help = "Gratuite : créez un compte sur themoviedb.org, puis Paramètres → API. " +
                "La clé courte (32 caractères) et le jeton long sont tous deux acceptés.",
            current = viewModel.tmdbKey,
            onSubmit = { editingKey = false; viewModel.saveTmdbKey(it) },
            onDismiss = { editingKey = false }
        )
    }
    if (showingHidden && state.hiddenFolders.isNotEmpty()) {
        SimpleMenu(
            title = "Dossiers cachés",
            subtitle = "OK sur un dossier pour le réafficher",
            actions = state.hiddenFolders.map { hidden ->
                MenuAction(R.drawable.ic_eye, hidden.name, hidden.path) { viewModel.showFolder(hidden.folderId) }
            },
            onDismiss = { showingHidden = false }
        )
    }
    homeSection?.let { key ->
        val index = state.homeOrder.indexOf(key)
        val hidden = key in state.homeHidden
        SimpleMenu(
            title = homeSectionLabel(key),
            subtitle = "Position ${index + 1} sur ${state.homeOrder.size} · ${if (hidden) "masquée" else "affichée"}",
            actions = buildList {
                if (index > 0) add(MenuAction(R.drawable.ic_chevron, "Monter") { viewModel.moveHomeSection(key, -1) })
                if (index < state.homeOrder.lastIndex) add(MenuAction(R.drawable.ic_chevron, "Descendre") { viewModel.moveHomeSection(key, 1) })
                add(MenuAction(R.drawable.ic_eye, if (hidden) "Afficher sur l'accueil" else "Masquer de l'accueil") {
                    viewModel.toggleHomeSection(key)
                })
                if (key in com.cinetv.data.prefs.HOME_RESIZABLE) {
                    val size = state.homeSizes[key] ?: com.cinetv.data.prefs.HomeSize.NORMALE
                    add(MenuAction(R.drawable.ic_aspect, "Taille des cartes", size.label) { viewModel.cycleHomeSize(key) })
                }
                add(MenuAction(R.drawable.ic_check, "Terminé") { homeSection = null })
            },
            onDismiss = { homeSection = null }
        )
    }
    if (editingSubtitlesKey) {
        KeyDialog(
            title = "Clé OpenSubtitles",
            help = "Gratuite : créez un compte sur opensubtitles.com, puis ouvrez la rubrique « API consumers » " +
                "et créez un accès. Recopiez ici la clé obtenue.",
            current = viewModel.subtitlesKey,
            onSubmit = { editingSubtitlesKey = false; viewModel.saveSubtitlesKey(it) },
            onDismiss = { editingSubtitlesKey = false }
        )
    }
}

/** Saisie d'une clé d'accès. Elle n'est jamais réaffichée en clair une fois enregistrée. */
@Composable
private fun KeyDialog(
    title: String,
    help: String,
    current: String,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(12) {
            if (runCatching { field.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.cinetv.ui.components.SoftNavSound()
        Box(Modifier.fillMaxSize().background(Ink.Void.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Ink.Surface)
                    .padding(28.dp)
            ) {
                Text(title.uppercase(), style = MaterialTheme.typography.headlineSmall, color = Ink.TextHi)
                Spacer(Modifier.height(6.dp))
                Text(
                    help + if (current.isNotBlank()) " Une clé est déjà enregistrée (…${current.takeLast(4)})." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.TextMid
                )
                Spacer(Modifier.height(14.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.trim() },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.TextHi),
                    cursorBrush = SolidColor(Ink.Lilac),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done, autoCorrectEnabled = false
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(field)
                        .clip(RoundedCornerShape(Radius.control))
                        .background(Ink.SurfaceHi)
                        .border(1.dp, Ink.Violet, RoundedCornerShape(Radius.control))
                        .padding(horizontal = 11.dp, vertical = 9.dp)
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(icon = R.drawable.ic_check, label = "ENREGISTRER", onClick = { if (text.isNotBlank()) onSubmit(text) })
                    if (current.isNotBlank()) {
                        PillButton(icon = R.drawable.ic_replay, label = "RETIRER MA CLÉ", onClick = { onSubmit("") })
                    }
                    PillButton(icon = R.drawable.ic_close, label = "ANNULER", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun Section(label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.Lilac,
        modifier = Modifier.padding(start = SIDE, top = 26.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingRow(
    icon: Int,
    label: String,
    value: String,
    hint: String = "",
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = FocusStyle.shape(Radius.control),
        colors = FocusStyle.colors(container = Color.Transparent, content = Ink.TextHi),
        scale = FocusStyle.lift(1.01f),
        glow = FocusStyle.glow(Ink.Violet, 12.dp),
        border = FocusStyle.noBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SIDE, vertical = 3.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CineIcon(icon, tint = Ink.TextMid, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink.TextHi)
                if (hint.isNotBlank()) {
                    Text(hint, style = MaterialTheme.typography.labelSmall, color = Ink.TextLow)
                }
            }
            if (value.isNotBlank()) {
                Text(value, style = MaterialTheme.typography.labelLarge, color = Ink.Lilac)
            }
        }
    }
}

private fun languageLabel(code: String): String = when (code) {
    "" -> "Aucune préférence"
    "fr" -> "Français"
    "en" -> "Anglais"
    "es" -> "Espagnol"
    "de" -> "Allemand"
    "it" -> "Italien"
    "ja" -> "Japonais"
    else -> code.uppercase()
}
