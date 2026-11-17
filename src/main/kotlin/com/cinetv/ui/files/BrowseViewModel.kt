package com.cinetv.ui.files

import kotlinx.coroutines.flow.conflate
import com.cinetv.data.model.DriveTheme
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinetv.data.model.Crumb
import com.cinetv.data.model.Node
import com.cinetv.data.model.Volume
import com.cinetv.ui.theme.Grid
import com.cinetv.data.prefs.Settings
import com.cinetv.data.prefs.SortOrder
import com.cinetv.data.repo.LibraryRepository
import com.cinetv.ui.theme.Motion
import com.cinetv.data.tmdb.Tmdb
import com.cinetv.util.FileNameParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseUi(
    val volume: Volume? = null,
    val crumbs: List<Crumb> = emptyList(),
    val folders: List<Node> = emptyList(),
    val files: List<Node> = emptyList(),
    val folderPosters: Map<String, List<String>> = emptyMap(),
    /** Épisodes vus / total par dossier. */
    val folderProgress: Map<String, Pair<Int, Int>> = emptyMap(),
    /** Nombre de saisons par dossier, sur un disque de séries. */
    val folderSeasons: Map<String, Int> = emptyMap(),
    val loading: Boolean = true
)

/** État de la correction manuelle d'une fiche. */
data class RelinkUi(
    val node: Node,
    val query: String,
    val results: List<Tmdb.Hit> = emptyList(),
    val searching: Boolean = false
)

class BrowseViewModel(
    private val repo: LibraryRepository,
    context: Context,
    private val volumeId: String,
    private val folderId: String?
) : ViewModel() {

    private val settings = Settings(context)

    private val _ui = MutableStateFlow(BrowseUi())
    val ui = _ui.asStateFlow()

    private val _columns = MutableStateFlow(settings.gridColumns)
    val columns = _columns.asStateFlow()

    private val _gap = MutableStateFlow(settings.gridGap)
    val gap = _gap.asStateFlow()

    private val _sort = MutableStateFlow(settings.sortOrder)
    val sort = _sort.asStateFlow()

    private val _hideWatched = MutableStateFlow(settings.hideWatched)
    val hideWatched = _hideWatched.asStateFlow()

    private val _highlighted = MutableStateFlow<Node?>(null)
    val highlighted = _highlighted.asStateFlow()

    /** Titre dont le menu contextuel est ouvert. */
    private val _menuTarget = MutableStateFlow<Node?>(null)
    val menuTarget = _menuTarget.asStateFlow()

    private val _relink = MutableStateFlow<RelinkUi?>(null)
    val relink = _relink.asStateFlow()

    val metadataProgress = repo.metadataProgress

    /** Nom du dossier dans lequel la bannière pioche au hasard, ou null. */
    private val _spotlightOf = MutableStateFlow<String?>(null)
    val spotlightOf = _spotlightOf.asStateFlow()

    /** Courte temporisation : traverser une rangée ne doit pas charger une image par case. */
    private var highlightJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            // Pendant la recherche des jaquettes la bibliothèque change plusieurs fois par seconde :
            // on ne recharge alors l'écran que de temps en temps, pour qu'il reste fluide.
            repo.revision.conflate().collect {
                reload()
                if (repo.metadataProgress.value != null) delay(1_500)
            }
        }
        viewModelScope.launch { repo.enrichPendingMetadata() }
    }

    private suspend fun reload() {
        val volume = repo.volumeAt(volumeId, folderId)
        val crumbs = repo.breadcrumb(volumeId, folderId)
        val children = repo.children(volumeId, folderId)
        val folders = children.filter { it.isDirectory }
        val files = applyView(children.filterNot { it.isDirectory })

        _ui.update {
            it.copy(
                volume = volume,
                crumbs = crumbs,
                folders = folders,
                files = files,
                // Une série range souvent ses épisodes par saison : on cherche l'affiche dans tout le dossier
                folderPosters = folders.associate { f ->
                    f.id to if (volume?.theme == DriveTheme.SERIES) repo.postersUnder(f.id) else repo.postersForFolder(f.id)
                },
                folderProgress = folders.associate { f -> f.id to repo.folderProgress(f.id) },
                folderSeasons = if (volume?.theme == DriveTheme.SERIES) folders.associate { f -> f.id to repo.seasonCount(f.id) } else emptyMap(),
                loading = false
            )
        }

        val current = _highlighted.value
        if (_spotlightOf.value == null) {
            _highlighted.value = files.firstOrNull { it.id == current?.id } ?: files.firstOrNull()
        }
        // Rien que des dossiers ici : la bannière pioche dans tout ce niveau
        if (_highlighted.value == null && highlightJob?.isActive != true) {
            spotlight(folderId, crumbs.lastOrNull()?.label ?: volume?.label.orEmpty(), immediate = true)
        }
    }

    /**
     * La bannière fait défiler des films pris au hasard sous un dossier,
     * sous-dossiers compris, tant que ce dossier garde le focus.
     */
    private fun spotlight(
        folder: String?,
        label: String,
        immediate: Boolean = false,
        /** Sur un disque de séries : le dossier de la série, que la bannière présente à la place de l'épisode. */
        series: Node? = null
    ) {
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            if (!immediate) delay(Motion.highlightDelayMs)
            val picks = repo.randomUnder(volumeId, folder)
            if (picks.isEmpty()) {
                if (series != null) {
                    // Aucun épisode reconnu pour l'instant : la bannière passe quand même à cette série
                    _spotlightOf.value = label
                    _highlighted.value = series.copy(
                        title = series.title.ifBlank { series.name },
                        quality = repo.maxQualityUnder(series.id)
                    )
                } else {
                    _spotlightOf.value = null
                }
                return@launch
            }
            _spotlightOf.value = label
            if (series != null) {
                // La bannière d'une série annonce la meilleure définition disponible parmi ses épisodes
                _highlighted.value = seriesFiche(series, picks).copy(quality = repo.maxQualityUnder(series.id))
                return@launch
            }
            var index = 0
            while (true) {
                _highlighted.value = picks[index % picks.size]
                if (picks.size < 2) break
                delay(Motion.folderSpotlightMs)
                index++
            }
        }
    }

    /**
     * La fiche d'une série, vue dans son ensemble : logo, pastilles et résumé comme pour un film,
     * mais ni épisode, ni saison, ni durée, ni reprise.
     *
     * Les épisodes portent déjà les informations de leur série ; on prend celles de la série la
     * plus représentée dans le dossier. Si une fiche a été choisie à la main pour le dossier,
     * c'est elle qui fait foi — son image, son titre, son résumé.
     */
    private fun seriesFiche(series: Node, episodes: List<Node>): Node {
        fun Node.whole() = copy(
            title = title.substringBefore(" · ").ifBlank { series.name },
            durationMs = 0, lastPositionMs = 0
        )
        val usual = (episodes.filter { it.tmdbId > 0 }.groupBy { it.tmdbId }.maxByOrNull { it.value.size }?.value?.first()
            ?: episodes.first()).whole()
        val chosen = series.backdropUrl.isNotBlank() || series.posterUrl.isNotBlank()
        if (!chosen) return usual

        // Un épisode rattaché à la même fiche que le dossier : il en a le logo et les genres
        val twin = episodes.firstOrNull {
            (series.tmdbId > 0 && it.tmdbId == series.tmdbId) ||
                (it.posterUrl.isNotBlank() && it.posterUrl == series.posterUrl) ||
                (it.backdropUrl.isNotBlank() && it.backdropUrl == series.backdropUrl)
        }?.whole()
        // Fiche complète sur le dossier : elle suffit, complétée par l'épisode jumeau s'il existe.
        // Image seule et aucun épisode qui lui corresponde : on ne montre que l'image et le nom,
        // plutôt que les informations d'une autre série.
        val base = twin ?: series.copy(durationMs = 0, lastPositionMs = 0)
        return base.copy(
            title = series.title.ifBlank { if (twin != null) base.title else series.name },
            synopsis = series.synopsis.ifBlank { base.synopsis },
            posterUrl = series.posterUrl.ifBlank { base.posterUrl },
            backdropUrl = series.backdropUrl.ifBlank { base.backdropUrl },
            year = if (series.year > 0) series.year else base.year,
            rating = if (series.rating > 0f) series.rating else base.rating,
            logoUrl = series.logoUrl.ifBlank { base.logoUrl },
            genres = series.genres.ifBlank { base.genres }
        )
    }

    fun highlightFolder(folder: Node) = spotlight(
        folder.id, folder.name,
        series = folder.takeIf { _ui.value.volume?.theme == DriveTheme.SERIES }
    )

    /** Tri et filtre, appliqués à l'affichage sans toucher au contenu en base. */
    private fun applyView(files: List<Node>): List<Node> {
        val visible = if (_hideWatched.value) files.filterNot { it.isWatched } else files
        return when (_sort.value) {
            SortOrder.NAME -> {
                // Alphabétique, mais les volets d'une même saga restent groupés et
                // dans l'ordre de sortie : la saga se range à son nom, comme un seul titre.
                val sagaSizes = visible.filter { it.collectionId > 0 }
                    .groupingBy { it.collectionId }.eachCount()
                fun inSaga(node: Node) = node.collectionId > 0 && (sagaSizes[node.collectionId] ?: 0) >= 2
                visible.sortedWith(
                    compareBy<Node>(
                        { if (inSaga(it)) sagaKey(it.collectionName) else it.displayTitle.lowercase() },
                        { if (inSaga(it)) it.releaseDate.ifBlank { it.year.toString() } else "" },
                        { it.displayTitle.lowercase() }
                    )
                )
            }
            SortOrder.RECENT -> visible.sortedByDescending { it.addedAt }
            SortOrder.YEAR -> visible.sortedByDescending { it.year }
            SortOrder.RATING -> visible.sortedByDescending { it.rating }
        }
    }

    /** « Harry Potter - Saga », « Collection Alien » → « harry potter », « alien ». */
    private fun sagaKey(name: String): String =
        name.lowercase()
            .replace(Regex("""\s*[-–:(]?\s*\b(saga|collection|la collection|trilogie|l'intégrale)\b\s*\)?"""), " ")
            .trim().ifBlank { name.lowercase() }

    private fun refreshView() = viewModelScope.launch { reload() }

    fun highlight(node: Node) {
        if (_spotlightOf.value == null && _highlighted.value?.id == node.id) return
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            delay(Motion.highlightDelayMs)
            _spotlightOf.value = null
            _highlighted.value = node
        }
    }

    /** Une colonne de plus à chaque appui ; après la plus dense, on revient à la plus large. */
    fun cycleColumns() {
        val next = if (_columns.value >= Grid.columnsMax) Grid.columnsMin else _columns.value + 1
        _columns.value = next
        settings.gridColumns = next
    }

    fun cycleGap() {
        val next = _gap.value.next()
        _gap.value = next
        settings.gridGap = next
    }

    /** Présente ce dossier comme un disque sur l'accueil, ou l'en retire. */
    fun isShownAsDrive(folder: Node): Boolean = repo.isVirtualDrive(folder.id)

    fun toggleShownAsDrive(folder: Node) {
        if (repo.isVirtualDrive(folder.id)) repo.removeVirtualDrive(folder.id) else repo.addVirtualDrive(folder)
    }

    /** Cache ce dossier : il disparaît du disque, de l'accueil et des recherches de jaquettes. */
    fun hideFolder(folder: Node) = repo.hideFolder(folder)

    fun cycleSort() {
        val next = _sort.value.next()
        _sort.value = next
        settings.sortOrder = next
        refreshView()
    }

    fun toggleHideWatched() {
        val next = !_hideWatched.value
        _hideWatched.value = next
        settings.hideWatched = next
        refreshView()
    }

    // ------------------------------------------------------- menu contextuel

    fun openMenu(node: Node) { _menuTarget.value = node }
    fun closeMenu() { _menuTarget.value = null }

    fun toggleWatched(node: Node) = viewModelScope.launch {
        repo.setWatched(node.id, !node.isWatched)
        closeMenu()
    }

    /** Remet le titre au début, puis enchaîne (la lecture, en pratique). */
    fun restartFrom(node: Node, then: () -> Unit = {}) = viewModelScope.launch {
        repo.clearResume(node.id)
        closeMenu()
        then()
    }

    /** Retire le titre de « Reprendre », sans le lancer. */
    fun forgetResume(node: Node) = viewModelScope.launch {
        repo.clearResume(node.id)
        closeMenu()
    }

    // --------------------------------------------------- correction de fiche

    fun openRelink(node: Node) {
        closeMenu()
        val guess = FileNameParser.parse(node.name).title
        _relink.value = RelinkUi(node = node, query = guess)
        search(guess)
    }

    fun closeRelink() { _relink.value = null }

    fun search(query: String) {
        val current = _relink.value ?: return
        _relink.value = current.copy(query = query, searching = true)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val hits = repo.searchTmdb(query)
            _relink.update { it?.copy(results = hits, searching = false) }
        }
    }

    fun applyRelink(hit: Tmdb.Hit, artworkOnly: Boolean) {
        val target = _relink.value?.node ?: return
        // Un dossier reçoit la fiche entière : sur un disque de séries, c'est elle que montre la bannière
        val onlyArt = artworkOnly
        viewModelScope.launch {
            repo.relink(target.id, hit, onlyArt)
            _relink.value = null
        }
    }
}
