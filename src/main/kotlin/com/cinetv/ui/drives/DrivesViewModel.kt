package com.cinetv.ui.drives

import kotlinx.coroutines.flow.conflate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinetv.data.model.DriveTheme
import com.cinetv.data.model.Node
import com.cinetv.data.model.Volume
import com.cinetv.data.network.FoundHost
import com.cinetv.data.network.NetworkShare
import com.cinetv.data.model.isSuggestion
import com.cinetv.data.repo.LibraryRepository
import com.cinetv.ui.theme.HomePage
import com.cinetv.ui.theme.Motion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DrivesViewModel(private val repo: LibraryRepository) : ViewModel() {

    private val _volumes = MutableStateFlow<List<Volume>>(emptyList())
    val volumes = _volumes.asStateFlow()

    /** Dossiers présentés comme des disques, sous forme de fiches de disque. */
    private val _virtual = MutableStateFlow<List<Volume>>(emptyList())
    val virtual = _virtual.asStateFlow()

    /** Le dossier (et son disque) derrière une fiche de dossier-disque. */
    fun virtualTarget(card: Volume) = repo.virtualDrives().firstOrNull { it.cardId == card.id }

    fun cycleVirtualTheme(card: Volume) {
        val target = virtualTarget(card) ?: return
        val all = DriveTheme.entries
        repo.setVirtualTheme(target.folderId, all[(target.theme.ordinal + 1) % all.size])
    }

    fun removeVirtual(card: Volume) { virtualTarget(card)?.let { repo.removeVirtualDrive(it.folderId) } }

    private val _posters = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val posters = _posters.asStateFlow()

    private val _resume = MutableStateFlow<List<Node>>(emptyList())
    val resume = _resume.asStateFlow()

    private val _recent = MutableStateFlow<List<Node>>(emptyList())
    val recent = _recent.asStateFlow()

    /** Les titres mis de côté depuis leur fiche. */
    /** Les genres de la bibliothèque, pour la rubrique « Genres ». */
    private val _genres = MutableStateFlow<List<com.cinetv.data.model.GenreShelf>>(emptyList())
    val genres = _genres.asStateFlow()

    /** Propositions de TMDB absentes des disques : « À voir » et « Sorties récentes ». */
    private val _toWatch = MutableStateFlow<List<com.cinetv.data.model.Suggestion>>(emptyList())
    val toWatch = _toWatch.asStateFlow()
    private val _releases = MutableStateFlow<List<com.cinetv.data.model.Suggestion>>(emptyList())
    val releases = _releases.asStateFlow()

    /** Sorties chargées à la suite, au fil du défilement de la rangée. */
    private var moreReleases: List<com.cinetv.data.model.Suggestion> = emptyList()
    @Volatile private var loadingReleases = false

    private suspend fun loadDiscover() {
        val found = repo.discover()
        _toWatch.value = found.toWatch
        _releases.value = (found.releases + moreReleases).distinctBy { it.key }
    }

    /** La rangée « Sorties récentes » approche de sa fin : on va chercher la suite. */
    fun loadMoreReleases() {
        if (loadingReleases) return
        loadingReleases = true
        viewModelScope.launch {
            try {
                val next = repo.moreReleases()
                if (next.isNotEmpty()) {
                    moreReleases = moreReleases + next
                    _releases.value = (_releases.value + next).distinctBy { it.key }
                }
            } finally {
                loadingReleases = false
            }
        }
    }

    suspend fun trailerKey(item: com.cinetv.data.model.Suggestion): String? = repo.trailerKey(item)

    /**
     * « Autre sélection » : un nouveau tirage, tout de suite, dans la réserve de propositions.
     * La réserve elle-même est renouvelée en fond, pour le tirage suivant.
     */
    fun anotherSelection() = viewModelScope.launch {
        repo.redrawDiscover()
        loadDiscover()
        repo.refreshDiscover(force = true)
    }

    /** « Pas intéressé » : la proposition quitte les rangées pour de bon. */
    fun dismiss(item: com.cinetv.data.model.Suggestion) = viewModelScope.launch {
        repo.dismissSuggestion(item)
        // Écartée depuis la bannière : elle n'y repassera pas
        spotlight = spotlight.filterNot { it.id == "tmdb:${item.key}" }
        spotlightIndex = spotlightIndex.coerceIn(0, (spotlight.size - 1).coerceAtLeast(0))
        loadDiscover()
    }

    private val _myList = MutableStateFlow<List<Node>>(emptyList())
    val myList = _myList.asStateFlow()

    /** Titre mis en avant dans la bannière. Suit la carte survolée. */
    private val _featured = MutableStateFlow<Node?>(null)
    val featured = _featured.asStateFlow()

    /** Vrai quand la bannière tourne toute seule ; faux quand elle suit une carte survolée. */
    private val _spotlighting = MutableStateFlow(true)
    val spotlighting = _spotlighting.asStateFlow()

    val scanState = repo.scanState
    val metadataProgress = repo.metadataProgress

    /** Temporisation : traverser une rangée ne doit pas charger une image par case. */
    private var highlightJob: Job? = null

    /** Films tirés au hasard, que la bannière fait défiler. */
    private var spotlight: List<Node> = emptyList()
        set(value) {
            field = value
            _spotlightIds.value = value.map { it.id }
        }

    /** Les titres du tirage en cours, dans l'ordre : la bannière en fait son repère de défilement. */
    private val _spotlightIds = MutableStateFlow<List<String>>(emptyList())
    val spotlightIds = _spotlightIds.asStateFlow()
    private var spotlightIndex = 0
    private var lastManualAt = 0L
    /** Depuis quand le film actuel est à l'affiche. */
    private var shownAt = 0L

    /** Vrai tant qu'une fenêtre est ouverte par-dessus l'accueil : la bannière reste sur son titre. */
    @Volatile private var heroFrozen = false

    /**
     * Fige la bannière (fiche, bande-annonce ou menu ouvert) ou la relance. À la reprise,
     * le titre affiché a de nouveau droit à son temps complet avant de céder la place.
     */
    fun freezeHero(frozen: Boolean) {
        if (heroFrozen && !frozen) shownAt = System.currentTimeMillis()
        heroFrozen = frozen
    }

    init {
        viewModelScope.launch {
            // Pendant la recherche des jaquettes la bibliothèque change plusieurs fois par seconde :
            // on ne recharge alors l'écran que de temps en temps, pour qu'il reste fluide.
            repo.revision.conflate().collect {
                reload()
                if (repo.metadataProgress.value != null) delay(1_500)
            }
        }
        // Propositions hors bibliothèque : renouvelées une fois par jour, sans retarder l'ouverture
        viewModelScope.launch {
            delay(3_000)
            if (repo.refreshDiscover()) {
                loadDiscover()
                // Les sorties de l'année viennent d'arriver : la bannière en reçoit sans attendre le prochain tirage
                addIdeas()
            }
        }
        // Définition des vidéos (4K, 1080p…) : relevée une fois, en fond, quand le reste est au calme
        // Elle repasse ensuite de temps en temps : un disque rebranché ou de nouveaux fichiers sont pris au passage.
        viewModelScope.launch { delay(12_000); while (true) { repo.qualityPass(); delay(90_000) } }
        // Acteurs et réalisation des fiches déjà connues : rattrapés en fond
        viewModelScope.launch { delay(6_000); repo.enrichCredits() }
        // Nouveautés : chaque lancement repasse sur les disques et partages joignables
        viewModelScope.launch { delay(9_000); repo.autoRefresh() }
        // La bannière tourne toute seule, sauf juste après un survol de carte
        viewModelScope.launch {
            // On regarde chaque seconde, et on ne change que si le film affiché
            // a eu son temps complet à l'écran — quel que soit ce qui l'y a mis.
            _featured.collect { shownAt = System.currentTimeMillis() }
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val now = System.currentTimeMillis()
                val idle = now - lastManualAt > HomePage.heroPauseMs
                val hadItsTime = now - shownAt >= HomePage.heroRotateMs
                if (!heroFrozen && idle && hadItsTime && spotlight.size > 1) {
                    spotlightIndex = (spotlightIndex + 1) % spotlight.size
                    // Tour complet : on tire une nouvelle série de films
                    if (spotlightIndex == 0) {
                        drawSpotlight().takeIf { it.size > 1 }?.let { spotlight = it }
                    }
                    _spotlighting.value = true
                    _featured.value = spotlight[spotlightIndex]
                }
            }
        }
    }

    /**
     * Le tirage de la bannière : un titre de la bibliothèque, puis une sortie de l'année
     * absente des disques (« À télécharger »), et ainsi de suite — un sur deux.
     */
    private suspend fun drawSpotlight(): List<Node> = withIdeas(repo.randomSpotlight())

    private suspend fun withIdeas(owned: List<Node>): List<Node> {
        val ideas = repo.heroSuggestions(owned.size).iterator()
        if (!ideas.hasNext() || owned.isEmpty()) return owned
        return buildList {
            owned.forEach { node ->
                add(node)
                if (ideas.hasNext()) add(ideas.next())
            }
        }
    }

    /** Glisse les sorties de l'année dans un tirage qui n'en a pas encore, sans changer le titre affiché. */
    private suspend fun addIdeas() {
        if (spotlight.any { it.isSuggestion }) return
        val mixed = withIdeas(spotlight)
        if (mixed.size == spotlight.size) return
        val shown = _featured.value?.id
        spotlight = mixed
        spotlightIndex = mixed.indexOfFirst { it.id == shown }.coerceAtLeast(0)
    }

    private suspend fun reload() {
        val list = repo.volumes()
        // Les dossiers présentés comme des disques : leurs vidéos ne comptent plus dans le disque qui les porte
        val apart = repo.virtualDrives()
        apartDrives = apart
        val virtual = repo.virtualVolumes()
        val counted = virtual.associateBy { it.id }
        _volumes.value = list.map { vol ->
            val moved = apart.filter { it.volumeId == vol.id }.mapNotNull { counted[it.cardId] }
            val concealed = repo.hiddenCounts(vol.id)
            if (moved.isEmpty() && concealed.first == 0 && concealed.second == 0) vol else vol.copy(
                videoCount = (vol.videoCount - moved.sumOf { it.videoCount } - concealed.first).coerceAtLeast(0),
                folderCount = (vol.folderCount - moved.sumOf { it.folderCount + 1 } - concealed.second).coerceAtLeast(0)
            )
        }
        _virtual.value = virtual
        _posters.value = list.associate { it.id to repo.postersForVolume(it.id) } +
            apart.associate { it.cardId to repo.postersUnder(it.folderId) }

        val resuming = repo.continueWatching()
        val latest = repo.recentlyAdded()
        _resume.value = resuming
        _recent.value = latest
        _myList.value = repo.myList()
        _genres.value = repo.genreShelves()
        loadDiscover()

        // Le tirage est gardé tant qu'il reste valable, pour ne pas sauter à chaque mise à jour
        // Les fiches du tirage sont rafraîchies, mais le tirage lui-même ne change
        // pas en cours de route : le refaire à chaque mise à jour de la bibliothèque
        // faisait sauter la bannière d'un film à l'autre.
        spotlight = spotlight.mapNotNull { old -> if (old.isSuggestion) old else repo.node(old.id) }
        if (spotlight.isEmpty()) {
            // D'abord la bibliothèque, tout de suite ; les sorties de l'année s'y glissent juste après,
            // le temps d'aller chercher leurs logos, sans retarder l'ouverture de l'accueil.
            spotlight = repo.randomSpotlight()
            spotlightIndex = 0
            viewModelScope.launch { addIdeas() }
        } else {
            spotlightIndex = spotlightIndex.coerceIn(0, spotlight.lastIndex)
        }

        val fallback = spotlight.firstOrNull()
            ?: resuming.firstOrNull()
            ?: latest.firstOrNull()
        val current = _featured.value
        _featured.value = (spotlight + resuming + latest).firstOrNull { it.id == current?.id } ?: fallback
    }

    /** Le disque auquel appartient le titre mis en avant. */
    fun volumeOf(node: Node?): Volume? =
        node?.let { item ->
            // Un titre rangé dans un dossier-disque appartient à ce « disque »-là : son nom, son thème
            val apart = apartDrives.firstOrNull {
                it.volumeId == item.volumeId && it.path.isNotBlank() && item.relativePath.startsWith(it.path + "/")
            }
            apart?.let { v -> _virtual.value.firstOrNull { it.id == v.cardId } }
                ?: _volumes.value.firstOrNull { it.id == item.volumeId }
        }

    /** Les dossiers-disques, relus à chaque rechargement de la bibliothèque. */
    private var apartDrives: List<com.cinetv.data.prefs.VirtualDrive> = emptyList()

    fun highlight(node: Node) {
        lastManualAt = System.currentTimeMillis()
        if (_featured.value?.id == node.id) return
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            delay(Motion.highlightDelayMs)
            _spotlighting.value = false
            _featured.value = node
        }
    }

    /** Retire un titre de la rangée « Reprendre ». */
    fun clearResume(node: Node) = viewModelScope.launch { repo.clearResume(node.id) }

    /** Vide toute la rangée « Reprendre ». */
    fun clearAllResume() = viewModelScope.launch { repo.clearAllResume() }

    suspend fun trailerKey(node: Node): String? = repo.trailerKey(node)

    fun clearRecent() = viewModelScope.launch { repo.clearRecent(); _recent.value = repo.recentlyAdded() }

    /** Remet un titre au début avant de le relancer. */
    fun restart(node: Node, then: () -> Unit) = viewModelScope.launch {
        repo.clearResume(node.id)
        then()
    }

    // ------------------------------------------------------ partages réseau

    /** État de la boîte d'ajout : en cours de test, et dernière erreur. */
    private val _shareBusy = MutableStateFlow(false)
    val shareBusy = _shareBusy.asStateFlow()
    private val _shareError = MutableStateFlow<String?>(null)
    val shareError = _shareError.asStateFlow()

    fun clearShareError() { _shareError.value = null }

    /** Machines trouvées sur le réseau, et dossiers partagés de celle choisie. */
    private val _hosts = MutableStateFlow<List<FoundHost>>(emptyList())
    val hosts = _hosts.asStateFlow()
    private val _hostShares = MutableStateFlow<List<String>>(emptyList())
    val hostShares = _hostShares.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    fun findHosts() = viewModelScope.launch {
        _searching.value = true
        _hostShares.value = emptyList()
        _hosts.value = repo.findHosts()
        _searching.value = false
    }

    fun findShares(host: String, user: String, password: String) = viewModelScope.launch {
        if (host.isBlank()) {
            _shareError.value = "Choisissez ou saisissez d'abord la machine"
            return@launch
        }
        _shareBusy.value = true
        _shareError.value = null
        repo.findShares(host, user, password)
            .onSuccess {
                _hostShares.value = it
                if (it.isEmpty()) _shareError.value = "Cette machine ne partage aucun dossier avec ce compte"
            }
            .onFailure { _shareError.value = it.message }
        _shareBusy.value = false
    }

    /** Teste puis enregistre un partage ; [onAdded] est appelé s'il est accepté. */
    fun addShare(share: NetworkShare, onAdded: () -> Unit) = viewModelScope.launch {
        _shareBusy.value = true
        _shareError.value = null
        val failure = repo.addShare(share)
        _shareBusy.value = false
        if (failure != null) {
            _shareError.value = failure
            return@launch
        }
        onAdded()
        reload()
        repo.scanVolume(share.volumeId)
        repo.enrichPendingMetadata()
    }

    fun removeShare(volume: Volume) = viewModelScope.launch {
        repo.removeShare(volume.id)
        reload()
    }

    /** Compte rendu de la dernière reprise de config, à afficher puis effacer. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    fun clearNotice() { _notice.value = null }

    fun adoptConfig(from: Volume, to: Volume) = viewModelScope.launch {
        _notice.value = repo.adoptConfig(from.id, to.id)
        reload()
    }

    fun forgetVolume(volume: Volume) = viewModelScope.launch {
        repo.forgetVolume(volume.id)
        reload()
    }

    fun rescan(volume: Volume) = viewModelScope.launch {
        repo.scanVolume(volume.id)
        repo.enrichPendingMetadata()
    }

    fun hasPermission() = repo.hasStoragePermission()

    fun refresh() = viewModelScope.launch {
        repo.refreshVolumeAvailability()
        reload()
        // Les partages réseau sont vérifiés ensuite : s'ils tardent, rien n'attend
        repo.refreshNetworkAvailability()
        reload()
    }

    fun scanAll() = viewModelScope.launch {
        repo.scanAll()
        repo.enrichPendingMetadata()
    }

    fun cycleTheme(volume: Volume) = viewModelScope.launch {
        val all = DriveTheme.entries
        repo.setTheme(volume.id, all[(all.indexOf(volume.theme) + 1) % all.size])
    }
}
