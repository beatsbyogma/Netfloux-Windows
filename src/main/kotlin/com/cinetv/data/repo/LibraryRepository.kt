package com.cinetv.data.repo

import kotlinx.coroutines.sync.withLock
import com.cinetv.data.prefs.HiddenFolder
import com.cinetv.data.prefs.VirtualDrive
import android.content.Context
import android.util.Log
import com.cinetv.data.db.LibraryStore
import com.cinetv.data.model.LibraryStats
import com.cinetv.data.subs.OpenSubtitles
import com.cinetv.data.model.*
import com.cinetv.data.network.FoundHost
import com.cinetv.data.network.NetworkDiscovery
import com.cinetv.data.network.NetworkShare
import com.cinetv.data.network.NetworkShares
import com.cinetv.data.network.SmbScanner
import com.cinetv.data.storage.ExternalSubtitle
import com.cinetv.data.storage.ExternalSubtitles
import com.cinetv.data.storage.FileOps
import com.cinetv.data.storage.MediaLibraryScanner
import com.cinetv.data.storage.MediaProbe
import com.cinetv.data.storage.TechInfo
import com.cinetv.data.storage.StorageAccess
import com.cinetv.data.tmdb.Tmdb
import com.cinetv.util.FileNameParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Avancement de la récupération des jaquettes et synopsis. */
data class MetadataProgress(
    val done: Int,
    val total: Int,
    val current: String,
    val found: Int,
    val missed: Int,
    /** Ce qui est en cours, tel qu'affiché : « JAQUETTES », puis « LOGOS ET GENRES ». */
    val stage: String = "JAQUETTES"
) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

sealed interface ScanState {
    data object Idle : ScanState
    data class Running(val volumeLabel: String, val filesFound: Int) : ScanState
    data class Done(val volumeLabel: String, val videos: Int, val folders: Int, val source: String) : ScanState
    data class Failed(val reason: String) : ScanState
    data object NeedsPermission : ScanState
    data object NoDrives : ScanState
}

/** Version de la méthode de recherche des fiches ; l'augmenter fait retenter les fichiers non reconnus. */
private const val METADATA_PASS = 3

/** Durée pendant laquelle un fichier reste dans « Récemment ajoutés ». */
private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/** Propositions hors bibliothèque : rafraîchies une fois par jour. */
private const val DISCOVER_EVERY_MS = 24L * 60 * 60 * 1000
private const val DISCOVER_KEPT = 45        // sorties gardées en réserve (certaines seront retirées : possédées, écartées)
private const val DISCOVER_POOL = 180       // réserve de « À voir », dans laquelle chaque démarrage pioche
private const val DISCOVER_SHOWN = 20       // montrées par rangée
private const val DISCOVER_SEEDS = 16       // titres de la bibliothèque dont on part pour « À voir »
private const val DISCOVER_ANIMATED_SEEDS = 4   // dont, au plus, tant de films d'animation
private const val DISCOVER_PER_SEED = 12    // propositions retenues par titre de départ
private const val DISCOVER_MIN_RATING = 6.3f
private const val ANIMATION_GENRE = 16      // identifiant TMDB du genre Animation
private const val HORROR_GENRE = 27         // identifiant TMDB du genre Horreur
private val FRANCHISE_SMALL_WORDS = setOf("the", "les", "des", "une", "and", "aux", "del", "los", "las", "der", "die", "das")

class LibraryRepository(private val context: Context) {

    private val store = LibraryStore(context)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState = _scanState.asStateFlow()

    private val _metadata = MutableStateFlow<MetadataProgress?>(null)
    val metadataProgress = _metadata.asStateFlow()

    /** Émet à chaque écriture en base : les écrans s'en servent pour se rafraîchir. */
    val revision: Flow<Long> = store.revision

    // ---------------------------------------------------------------- lecture

    suspend fun volumes(): List<Volume> = withContext(Dispatchers.IO) { store.volumes() }
    suspend fun volume(id: String): Volume? = withContext(Dispatchers.IO) { store.volume(id) }
    suspend fun node(id: String): Node? = withContext(Dispatchers.IO) { store.node(id) }

    suspend fun children(volumeId: String, parentId: String?): List<Node> =
        withContext(Dispatchers.IO) {
            // Un dossier présenté comme un disque ne se montre plus dans son disque d'origine
            val apart = prefs.virtualDrives.mapTo(HashSet()) { it.folderId }
            apart += prefs.hiddenFolders.map { it.folderId }
            store.children(volumeId, parentId).filterNot { it.isDirectory && it.id in apart }
        }

    // -------------------------------------------------------- dossiers cachés

    fun hiddenFolders(): List<HiddenFolder> = prefs.hiddenFolders

    /** Cache ce dossier : il sort du disque, de l'accueil, de la recherche et des recherches de jaquettes. */
    fun hideFolder(folder: Node) {
        if (!folder.isDirectory || prefs.hiddenFolders.any { it.folderId == folder.id }) return
        prefs.hiddenFolders = prefs.hiddenFolders +
            HiddenFolder(folder.id, folder.volumeId, folder.name, folder.relativePath)
        store.notifyChanged()
    }

    /** Réaffiche un dossier caché ; ses fichiers laissés de côté repartent en recherche de jaquettes. */
    suspend fun showFolder(folderId: String) = withContext(Dispatchers.IO) {
        prefs.hiddenFolders = prefs.hiddenFolders.filterNot { it.folderId == folderId }
        runCatching { store.retryUnder(folderId) }
        store.notifyChanged()
    }

    // ------------------------------------------------------- à découvrir (hors bibliothèque)

    /** Les deux rangées de propositions : « À voir » et « Sorties récentes ». */
    data class Discover(
        val toWatch: List<Suggestion> = emptyList(),
        val releases: List<Suggestion> = emptyList(),
        /** Sorties de l'année, bien notées, pour la bannière : réserve dans laquelle on pioche. */
        val hero: List<Suggestion> = emptyList()
    )

    /**
     * Ce que TMDB a donné, avant tri : pour « À voir » c'est une réserve bien plus
     * large que la rangée, dans laquelle chaque démarrage pioche autrement.
     */
    @Volatile private var discoverRaw: Discover? = null

    /** Change à chaque démarrage de l'app et à chaque « Autre sélection » : décide du tirage dans la réserve. */
    @Volatile private var discoverDraw: Long = System.nanoTime()

    private fun Suggestion.toJson() = org.json.JSONObject()
        .put("id", tmdbId).put("type", mediaType).put("title", title).put("overview", overview)
        .put("poster", posterUrl).put("backdrop", backdropUrl).put("date", date).put("rating", rating.toDouble())

    private fun suggestionsOf(array: org.json.JSONArray?): List<Suggestion> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            Suggestion(
                tmdbId = o.optInt("id"), mediaType = o.optString("type"), title = o.optString("title"),
                overview = o.optString("overview"), posterUrl = o.optString("poster"),
                backdropUrl = o.optString("backdrop"), date = o.optString("date"),
                rating = o.optDouble("rating", 0.0).toFloat()
            ).takeIf { it.tmdbId > 0 && it.title.isNotBlank() }
        }

    private fun Tmdb.Hit.toSuggestion() = Suggestion(
        tmdbId = tmdbId, mediaType = mediaType, title = title, overview = overview,
        posterUrl = Tmdb.poster(posterPath), backdropUrl = Tmdb.backdrop(backdropPath, "w780"),
        date = date, rating = rating
    )

    private fun discoverStored(): Discover =
        discoverRaw ?: runCatching {
            val o = org.json.JSONObject(prefs.discoverJson.ifBlank { "{}" })
            Discover(
                suggestionsOf(o.optJSONArray("toWatch")), suggestionsOf(o.optJSONArray("releases")),
                suggestionsOf(o.optJSONArray("hero"))
            )
        }.getOrDefault(Discover()).also { discoverRaw = it }

    /**
     * Le premier mot qui compte dans un titre (« Scooby-Doo et le fantôme… » → « scooby ») :
     * deux titres qui le partagent sont presque toujours de la même franchise, et la
     * rangée n'en garde qu'un.
     */
    private fun franchiseOf(title: String): String =
        title.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
            .firstOrNull { it.length > 2 && it !in FRANCHISE_SMALL_WORDS }
            ?: title.lowercase()

    /**
     * Les propositions à afficher : jamais un titre déjà présent sur un disque,
     * jamais un titre écarté, jamais le même titre dans les deux rangées.
     * « À voir » est tiré au hasard dans la réserve, une seule fois par franchise.
     */
    suspend fun discover(): Discover = withContext(Dispatchers.IO) {
        val raw = discoverStored()
        if (raw.toWatch.isEmpty() && raw.releases.isEmpty()) return@withContext raw
        val skip = runCatching { store.ownedTitles() }.getOrDefault(emptySet()) + prefs.discoverDismissed
        val releases = raw.releases.filter { it.key !in skip }
        val shownAsRelease = releases.mapTo(HashSet()) { it.key }
        val franchises = HashSet<String>()
        val toWatch = raw.toWatch
            .filter { it.key !in skip && it.key !in shownAsRelease }
            .shuffled(java.util.Random(discoverDraw))
            .filter { franchises.add(franchiseOf(it.title)) }
            .take(DISCOVER_SHOWN)
        Discover(toWatch = toWatch, releases = releases)
    }

    /** Logo et genres des titres proposés dans la bannière, demandés une seule fois chacun. */
    private val heroExtras = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    /**
     * Quelques titres sortis cette année et absents des disques, habillés comme des titres
     * de la bibliothèque pour passer dans la bannière. Ils ne se lisent pas : la bannière
     * propose leur bande-annonce.
     */
    suspend fun heroSuggestions(count: Int): List<Node> = withContext(Dispatchers.IO) {
        val skip = runCatching { store.ownedTitles() }.getOrDefault(emptySet()) + prefs.discoverDismissed
        discoverStored().hero.filter { it.key !in skip && it.backdropUrl.isNotBlank() }.shuffled().take(count).map { pick ->
            val (logo, genres) = heroExtras.getOrPut(pick.key) {
                val credits = runCatching { Tmdb.credits(pick.tmdbId, pick.mediaType) }.getOrNull()
                credits?.logoUrl.orEmpty() to credits?.genres?.joinToString(",").orEmpty()
            }
            Node(
                id = "tmdb:${pick.key}", volumeId = "", parentId = null, name = pick.title,
                relativePath = "", documentUri = "", isDirectory = false,
                metadataState = MetaState.RESOLVED, tmdbId = pick.tmdbId, title = pick.title,
                year = pick.year, releaseDate = pick.date, synopsis = pick.overview,
                posterUrl = pick.posterUrl,
                // La réserve garde une image moyenne ; la bannière, plein écran, en demande une grande
                backdropUrl = pick.backdropUrl.replace("/w780/", "/w1280/"),
                rating = pick.rating, mediaType = pick.mediaType, creditsDone = true,
                genres = genres, logoUrl = logo
            )
        }
    }

    /** Pages TMDB déjà demandées pour « Sorties récentes » : la réserve du jour couvre les films 1-2 et les séries 1. */
    private var releaseMoviePage = 2
    private var releaseShowPage = 1
    @Volatile private var releasesExhausted = false

    /**
     * La suite de « Sorties récentes » : la page suivante des sorties des trois derniers mois,
     * demandée quand la rangée approche de sa fin. Vide quand TMDB n'a plus rien à proposer.
     */
    suspend fun moreReleases(): List<Suggestion> = withContext(Dispatchers.IO) {
        if (!Tmdb.isConfigured || releasesExhausted) return@withContext emptyList()
        val today = java.time.LocalDate.now()
        val from = today.minusMonths(3).toString()
        val to = today.toString()
        val movies = Tmdb.moviesReleased(from, to, ++releaseMoviePage)
        val shows = Tmdb.showsReleased(from, to, ++releaseShowPage)
        if (movies.isEmpty() && shows.isEmpty()) {
            // Fin de liste — ou réseau coupé : dans ce cas on réessaiera la même page
            if (Tmdb.lastFailureWasNetwork) { releaseMoviePage--; releaseShowPage-- } else releasesExhausted = true
            return@withContext emptyList()
        }
        val skip = runCatching { store.ownedTitles() }.getOrDefault(emptySet()) + prefs.discoverDismissed
        buildList {
            val m = movies.iterator(); val t = shows.iterator()
            while (m.hasNext() || t.hasNext()) {
                repeat(2) { if (m.hasNext()) add(m.next()) }
                if (t.hasNext()) add(t.next())
            }
        }.filter { (it.backdropPath != null || it.posterPath != null) && "${it.mediaType}:${it.tmdbId}" !in skip }
            .map { it.toSuggestion() }
    }

    /** « Autre sélection » : un nouveau tirage dans la réserve, tout de suite. */
    fun redrawDiscover() { discoverDraw = System.nanoTime() }

    /** « Pas intéressé » : le titre ne sera plus proposé. */
    suspend fun dismissSuggestion(item: Suggestion) = withContext(Dispatchers.IO) {
        prefs.discoverDismissed = prefs.discoverDismissed + item.key
    }

    suspend fun trailerKey(item: Suggestion): String? = withContext(Dispatchers.IO) {
        trailers.getOrPut(item.key) {
            runCatching { Tmdb.trailerKey(item.tmdbId, item.mediaType) }.getOrNull().orEmpty()
        }.takeIf { it.isNotBlank() }
    }

    /**
     * Va chercher de nouvelles propositions sur TMDB, une fois par jour au plus
     * (ou tout de suite si [force]). Renvoie vrai si la réserve a changé.
     *
     * « À voir » part de la bibliothèque : des titres pris au hasard, de tous les
     * genres, sans laisser l'animation prendre toute la place. TMDB donne pour chacun
     * les titres qui s'en rapprochent. Les séries d'animation (dessins animés) sont
     * écartées ; les films d'animation restent.
     * « Sorties récentes » : films et séries parus dans les trois derniers mois.
     */
    suspend fun refreshDiscover(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!Tmdb.isConfigured) return@withContext false
        val stored = discoverStored()
        val fresh = System.currentTimeMillis() - prefs.discoverAt < DISCOVER_EVERY_MS
        if (!force && fresh && stored.hero.isNotEmpty() &&
            (stored.toWatch.isNotEmpty() || stored.releases.isNotEmpty())) return@withContext false

        val owned = runCatching { store.ownedTitles() }.getOrDefault(emptySet())
        fun Tmdb.Hit.id() = "$mediaType:$tmdbId"

        // ── Sorties des trois derniers mois : deux films pour une série.
        //    Elles ne bougent pas d'une heure à l'autre : « Autre sélection » ne les redemande pas.
        val releases = if (force && fresh && stored.releases.isNotEmpty()) stored.releases else {
            val today = java.time.LocalDate.now()
            val from = today.minusMonths(3).toString()
            val to = today.toString()
            val movies = (Tmdb.moviesReleased(from, to, 1) + Tmdb.moviesReleased(from, to, 2))
                .filter { it.backdropPath != null || it.posterPath != null }
            val shows = Tmdb.showsReleased(from, to, 1).filter { it.backdropPath != null || it.posterPath != null }
            buildList {
                val m = movies.iterator(); val t = shows.iterator()
                while (m.hasNext() || t.hasNext()) {
                    repeat(2) { if (m.hasNext()) add(m.next()) }
                    if (t.hasNext()) add(t.next())
                }
            }.distinctBy { it.id() }.filter { it.id() !in owned }.take(DISCOVER_KEPT).map { it.toSuggestion() }
        }

        // ── Bannière : les sorties de l'année, bien notées — ni horreur, ni animation
        val hero = if (force && fresh && stored.hero.isNotEmpty()) stored.hero else {
            val today = java.time.LocalDate.now()
            val from = today.withDayOfYear(1).toString()
            val to = today.toString()
            (Tmdb.acclaimed("movie", from, to, 1) + Tmdb.acclaimed("movie", from, to, 2) + Tmdb.acclaimed("tv", from, to, 1))
                .filter { it.backdropPath != null && it.overview.isNotBlank() && it.id() !in owned }
                .filter { ANIMATION_GENRE !in it.genreIds && HORROR_GENRE !in it.genreIds }
                .distinctBy { it.id() }.take(DISCOVER_KEPT).map { it.toSuggestion() }
        }

        // ── À voir : ce qui ressemble à la bibliothèque, pris un peu partout
        val library = runCatching { store.titlesWithGenres() }.getOrDefault(emptyList())
            .distinctBy { "${it.mediaType}:${it.tmdbId}" }.shuffled()
        fun Node.animated() = ANIMATION_GENRE.toString() in genres.split(',')
        val seeds = (
            library.filter { !it.animated() }.take(DISCOVER_SEEDS - DISCOVER_ANIMATED_SEEDS) +
                library.filter { it.animated() && it.mediaType != "tv" }.take(DISCOVER_ANIMATED_SEEDS)
            ).shuffled()
        val pool = LinkedHashMap<String, Tmdb.Hit>()
        for (seed in seeds) {
            Tmdb.recommendations(seed.tmdbId, seed.mediaType)
                .filter { hit ->
                    (hit.backdropPath != null || hit.posterPath != null) &&
                        hit.rating >= DISCOVER_MIN_RATING &&
                        hit.id() !in owned &&
                        // Pas de dessins animés : une série d'animation est écartée, un film d'animation reste
                        !(hit.mediaType == "tv" && ANIMATION_GENRE in hit.genreIds)
                }
                .shuffled().take(DISCOVER_PER_SEED)
                .forEach { pool.putIfAbsent(it.id(), it) }
        }
        var toWatch = pool.values.toList()
        // Bibliothèque trop petite pour inspirer assez de titres : les mieux notés de TMDB complètent
        if (toWatch.size < 30) {
            toWatch = (toWatch + Tmdb.topRated("movie") + Tmdb.topRated("tv").filter { ANIMATION_GENRE !in it.genreIds })
                .filter { (it.backdropPath != null || it.posterPath != null) && it.id() !in owned }
                .distinctBy { it.id() }
        }
        val watch = toWatch.take(DISCOVER_POOL).map { it.toSuggestion() }

        // Rien reçu (réseau coupé, clé refusée) : on garde ce qu'on avait et on réessaiera
        if (releases.isEmpty() && watch.isEmpty() && hero.isEmpty()) return@withContext false
        val next = Discover(
            toWatch = watch.ifEmpty { stored.toWatch },
            releases = releases.ifEmpty { stored.releases },
            hero = hero.ifEmpty { stored.hero }
        )
        discoverRaw = next
        prefs.discoverJson = org.json.JSONObject()
            .put("toWatch", org.json.JSONArray(next.toWatch.map { it.toJson() }))
            .put("releases", org.json.JSONArray(next.releases.map { it.toJson() }))
            .put("hero", org.json.JSONArray(next.hero.map { it.toJson() }))
            .toString()
        if (!(force && fresh)) prefs.discoverAt = System.currentTimeMillis()
        true
    }

    // ------------------------------------------------------- définition des vidéos (4K, 1080p…)

    @Volatile private var gauging = false

    /** Vrai tant qu'une vidéo est en lecture : les relevés de fond attendent, pour ne pas disputer le disque au lecteur. */
    @Volatile var playbackActive = false

    /** La meilleure définition disponible sous un dossier ; 0 si aucune n'est encore connue. */
    suspend fun maxQualityUnder(folderId: String): Int =
        withContext(Dispatchers.IO) { runCatching { store.maxQualityUnder(folderId) }.getOrDefault(0) }

    /**
     * Relève la définition de chaque vidéo, une fois pour toutes.
     *
     * La vraie valeur est lue dans le fichier : on ouvre son en-tête — quelques
     * kilo-octets, rien n'est décodé. En attendant son tour, un fichier affiche la
     * définition annoncée par son nom (« 1080p », « 4K »…) ; elle n'est gardée pour de
     * bon que si le fichier ne se laisse pas lire. Un disque débranché sera lu à son retour.
     * La passe attend la fin d'une analyse, d'une recherche de jaquettes ou d'une lecture.
     */
    suspend fun qualityPass() = withContext(Dispatchers.IO) {
        if (gauging) return@withContext
        gauging = true
        try {
            // Provisoire, tout de suite : ce que dit le nom (chiffre des unités à 1)
            val named = store.namesWithoutQuality().mapNotNull { (id, name) ->
                qualityFromName(name).takeIf { it > 0 }?.let { id to it + 1 }
            }
            if (named.isNotEmpty()) { store.setQualities(named); store.notifyChanged() }

            val total = store.countPendingQuality()
            if (total == 0) return@withContext
            var done = 0
            while (true) {
                val batch = store.pendingQuality()
                if (batch.isEmpty()) break
                val found = ArrayList<Pair<String, Int>>(batch.size)
                for (node in batch) {
                    while (playbackActive || enriching || crediting || _scanState.value is ScanState.Running) delay(2_000)
                    val probed = runCatching { MediaProbe.probe(context, shares, node)?.quality }.getOrNull() ?: 0
                    found += node.id to when {
                        probed > 0 -> probed                        // lu dans le fichier : fait foi
                        node.quality > 0 -> node.quality + 1        // illisible : on garde ce que dit le nom
                        else -> -1
                    }
                    done++
                    _metadata.value = MetadataProgress(
                        done.coerceAtMost(total), total, node.name, 0, 0, stage = "QUALITÉ DES FICHIERS"
                    )
                }
                store.setQualities(found)
                store.notifyChanged()
            }
            _metadata.value = null
        } finally {
            gauging = false
        }
    }

    // ------------------------------------------------------- classement par genre

    /** Les titres classables par genre : dossiers cachés exclus, une série présentée sous son seul nom. */
    private fun genreTitles(): List<Node> =
        store.titlesWithGenres().shown().map { node ->
            if (node.mediaType == "tv") node.copy(title = node.title.substringBefore(" · "), durationMs = 0, lastPositionMs = 0)
            else node
        }

    /** Les genres de la bibliothèque, du plus fourni au moins fourni, chacun avec une image qui lui est propre. */
    suspend fun genreShelves(): List<GenreShelf> = withContext(Dispatchers.IO) {
        val byGenre = LinkedHashMap<String, MutableList<Node>>()
        genreTitles().forEach { node ->
            com.cinetv.ui.components.genreShelves(node.genres).forEach { label ->
                byGenre.getOrPut(label) { ArrayList() } += node
            }
        }
        val used = HashSet<String>()
        byGenre.entries.sortedByDescending { it.value.size }.map { (label, nodes) ->
            // L'image : le titre le mieux noté du genre, sans reprendre celle d'un autre genre
            val best = nodes.filter { it.backdropUrl.isNotBlank() }.sortedByDescending { it.rating }
            val art = (best.firstOrNull { it.backdropUrl !in used } ?: best.firstOrNull())?.backdropUrl.orEmpty()
            if (art.isNotBlank()) used += art
            GenreShelf(label, nodes.size, art)
        }
    }

    /** Tous les titres d'un genre, les mieux notés d'abord. */
    suspend fun titlesOfGenre(label: String): List<Node> = withContext(Dispatchers.IO) {
        genreTitles()
            .filter { label in com.cinetv.ui.components.genreShelves(it.genres) }
            .sortedWith(compareByDescending<Node> { it.rating }.thenBy { it.displayTitle.lowercase() })
    }

    /** Vidéos et dossiers cachés sur un disque, pour les retirer du compte de sa carte. */
    suspend fun hiddenCounts(volumeId: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var videos = 0
        var folders = 0
        prefs.hiddenFolders.filter { it.volumeId == volumeId }.forEach { h ->
            val (v, f) = store.countUnder(h.folderId)
            videos += v
            folders += f + 1
        }
        videos to folders
    }

    /** Vrai si ce fichier ou dossier se trouve dans un dossier caché (ou en est un). */
    private fun isHidden(node: Node, hidden: List<HiddenFolder> = prefs.hiddenFolders): Boolean =
        hidden.any {
            it.volumeId == node.volumeId && it.path.isNotBlank() &&
                (node.relativePath == it.path || node.relativePath.startsWith(it.path + "/"))
        }

    /** Retire d'une liste tout ce qui vient d'un dossier caché. */
    private fun List<Node>.shown(): List<Node> {
        val hidden = prefs.hiddenFolders
        return if (hidden.isEmpty()) this else filterNot { isHidden(it, hidden) }
    }

    // ------------------------------------------ dossiers présentés en disques

    private val prefs by lazy { com.cinetv.data.prefs.Settings(context) }

    fun virtualDrives(): List<VirtualDrive> = prefs.virtualDrives

    fun isVirtualDrive(folderId: String): Boolean = prefs.virtualDrives.any { it.folderId == folderId }

    /** Présente ce dossier comme un disque sur l'accueil ; le thème est deviné d'après son nom. */
    fun addVirtualDrive(folder: Node) {
        if (!folder.isDirectory || isVirtualDrive(folder.id)) return
        prefs.virtualDrives = prefs.virtualDrives +
            VirtualDrive(folder.id, folder.volumeId, folder.name, DriveTheme.guessFrom(folder.name), folder.relativePath)
        store.notifyChanged()
    }

    fun removeVirtualDrive(folderId: String) {
        prefs.virtualDrives = prefs.virtualDrives.filterNot { it.folderId == folderId }
        store.notifyChanged()
    }

    fun setVirtualTheme(folderId: String, theme: DriveTheme) {
        prefs.virtualDrives = prefs.virtualDrives.map { if (it.folderId == folderId) it.copy(theme = theme) else it }
        store.notifyChanged()
    }

    /**
     * Les dossiers-disques tels que l'accueil les affiche : une fiche de disque chacun,
     * avec le compte de ses vidéos et l'espace libre du disque qui le porte.
     * Ceux dont le dossier a disparu de la bibliothèque sont laissés de côté.
     */
    suspend fun virtualVolumes(): List<Volume> = withContext(Dispatchers.IO) {
        prefs.virtualDrives.mapNotNull { v ->
            if (store.node(v.folderId) == null) return@mapNotNull null
            val host = store.volume(v.volumeId)
            val (videos, folders) = store.countUnder(v.folderId)
            Volume(
                id = v.cardId, label = v.label, theme = v.theme,
                isConnected = host?.isConnected == true,
                videoCount = videos, folderCount = folders,
                freeBytes = host?.freeBytes ?: 0, totalBytes = host?.totalBytes ?: 0
            )
        }
    }

    suspend fun postersUnder(folderId: String): List<String> =
        withContext(Dispatchers.IO) { store.postersUnder(folderId) }

    /** Le dossier-disque dans lequel on se trouve (lui-même ou un de ses sous-dossiers), s'il y en a un. */
    private fun virtualAbove(folderId: String?): VirtualDrive? {
        val all = prefs.virtualDrives
        if (all.isEmpty()) return null
        var cursor = folderId
        var guard = 0
        while (cursor != null && guard++ < 64) {
            all.firstOrNull { it.folderId == cursor }?.let { return it }
            cursor = store.node(cursor)?.parentId
        }
        return null
    }

    /** Le disque tel qu'il doit se présenter à cet endroit : dans un dossier-disque, son nom et son thème. */
    suspend fun volumeAt(volumeId: String, folderId: String?): Volume? = withContext(Dispatchers.IO) {
        val real = store.volume(volumeId)
        val virtual = virtualAbove(folderId) ?: return@withContext real
        real?.copy(label = virtual.label, theme = virtual.theme)
    }

    suspend fun postersForVolume(id: String): List<String> =
        withContext(Dispatchers.IO) {
            // Les dossiers présentés comme des disques gardent leurs jaquettes pour eux
            store.postersInVolumeExcept(
                id,
                prefs.virtualDrives.filter { it.volumeId == id }.map { it.folderId } +
                    prefs.hiddenFolders.filter { it.volumeId == id }.map { it.folderId }
            )
        }

    /** Nombre de saisons d'une série : ses sous-dossiers, ou une seule si les épisodes sont en vrac. */
    suspend fun seasonCount(folderId: String): Int =
        withContext(Dispatchers.IO) { store.childFolderCount(folderId).coerceAtLeast(1) }

    suspend fun postersForFolder(folderId: String): List<String> =
        withContext(Dispatchers.IO) { store.postersInFolder(folderId) }

    suspend fun search(query: String): List<Node> =
        withContext(Dispatchers.IO) { store.search(query).shown() }

    suspend fun breadcrumb(volumeId: String, folderId: String?): List<Crumb> =
        withContext(Dispatchers.IO) {
            val trail = ArrayDeque<Crumb>()
            var cursor = folderId
            var guard = 0
            val apart = prefs.virtualDrives.associateBy { it.folderId }
            while (cursor != null && guard++ < 64) {
                val n = store.node(cursor) ?: break
                // Un dossier-disque est le début du chemin : on ne remonte pas au-delà
                apart[n.id]?.let { v ->
                    trail.addFirst(Crumb(n.id, v.label))
                    return@withContext trail.toList()
                }
                trail.addFirst(Crumb(n.id, n.name))
                cursor = n.parentId
            }
            trail.addFirst(Crumb(null, store.volume(volumeId)?.label ?: "Disque"))
            trail.toList()
        }

    // ------------------------------------------------------- gestion disques

    fun hasStoragePermission(): Boolean = StorageAccess.hasPermission(context)

    /**
     * Recalcule quels disques répondent. Rien n'est effacé : un disque débranché
     * reste consultable, ses fichiers simplement marqués indisponibles.
     */
    suspend fun refreshVolumeAvailability() = withContext(Dispatchers.IO) {
        if (!StorageAccess.hasPermission(context)) return@withContext

        // Nettoyage des doublons hérités des versions précédentes
        store.pruneDuplicateVolumes(StorageAccess::canonical)

        // Une seule source de vérité : l'index média. Les points de montage ne
        // servent que s'il ne connaît aucun disque — les interroger en parallèle
        // faisait apparaître le même disque deux fois.
        val fromIndex = StorageAccess.removableMediaVolumes(context)
        val candidates = fromIndex.ifEmpty {
            StorageAccess.readableMountPoints(context)
                .map { StorageAccess.canonical(it.name) }
                .filter { it != "emulated" && it != "external_primary" }
                .distinct()
        }

        val known = store.volumes().map { it.id }.toMutableSet()
        for (id in candidates) {
            if (known.add(id)) registerVolume(id)
        }

        for (vol in store.volumes()) {
            if (NetworkShare.isNetwork(vol.id)) continue      // vérifiés à part, par le réseau
            val present = StorageAccess.isVolumePresent(context, vol.id)
            // L'espace n'est relevé que disque branché ; débranché, on garde le dernier relevé
            if (present) {
                StorageAccess.spaceOf(context, vol.id)?.let { (free, total) ->
                    store.setVolumeSpace(vol.id, free, total)
                }
            }
            store.setVolumeConnected(vol.id, present)
            store.setVolumeAvailability(vol.id, present)
        }
    }

    private fun registerVolume(rawName: String): Volume {
        val volumeName = StorageAccess.canonical(rawName)
        val existing = store.volume(volumeName)
        val label = existing?.label ?: StorageAccess.describeVolume(context, volumeName)
        val volume = (existing ?: Volume(
            id = volumeName,
            label = label,
            theme = DriveTheme.guessFrom(label),
            orderIndex = store.volumes().size
        )).copy(isConnected = true, lastSeenAt = System.currentTimeMillis())
        store.upsertVolume(volume)
        return volume
    }

    suspend fun setTheme(volumeId: String, theme: DriveTheme) =
        withContext(Dispatchers.IO) { store.setVolumeTheme(volumeId, theme) }

    suspend fun renameVolume(volumeId: String, label: String) =
        withContext(Dispatchers.IO) { store.renameVolume(volumeId, label) }

    suspend fun forgetVolume(volumeId: String) =
        withContext(Dispatchers.IO) { store.deleteVolume(volumeId) }

    // ------------------------------------------------------------------ scan

    /** Analyse tous les disques branchés. */
    suspend fun scanAll() = withContext(Dispatchers.IO) {
        val allowed = StorageAccess.hasPermission(context)
        if (allowed) refreshVolumeAvailability()
        refreshNetworkAvailability()

        // Les partages réseau n'ont pas besoin de l'autorisation d'accès aux fichiers
        val targets = store.volumes()
            .filter { it.isConnected && (allowed || NetworkShare.isNetwork(it.id)) }
            .map { it.id }
        if (targets.isEmpty()) {
            _scanState.value = if (allowed) ScanState.NoDrives else ScanState.NeedsPermission
            return@withContext
        }
        targets.forEach { scanVolume(it) }
    }

    // ------------------------------------------------------ partages réseau

    private val shares = NetworkShares(context)

    /**
     * Les partages répondent-ils ? Fait à part des disques USB : un partage
     * éteint met plusieurs secondes à ne pas répondre, et l'accueil n'a pas à
     * attendre ça pour afficher le reste.
     */
    suspend fun refreshNetworkAvailability() = withContext(Dispatchers.IO) {
        for (share in shares.all()) {
            val id = share.volumeId
            if (store.volume(id) == null) continue
            val online = runCatching {
                NetworkShares.open(share).use { root ->
                    val reachable = root.exists()
                    if (reachable) {
                        val total = runCatching { root.length() }.getOrDefault(0L)
                        val free = runCatching { root.diskFreeSpace }.getOrDefault(0L)
                        if (total > 0) store.setVolumeSpace(id, free, total)
                    }
                    reachable
                }
            }.getOrElse {
                NetworkShares.reset(id)
                false
            }
            store.setVolumeConnected(id, online)
            store.setVolumeAvailability(id, online)
        }
    }

    /**
     * Enregistre un partage après l'avoir testé. Renvoie null si tout va bien,
     * sinon la raison de l'échec, en clair.
     */
    suspend fun addShare(share: NetworkShare): String? = withContext(Dispatchers.IO) {
        if (share.host.isBlank() || share.share.isBlank()) {
            return@withContext "Indiquez l'adresse de la machine et le nom du dossier partagé"
        }
        NetworkShares.reset(share.volumeId)
        shares.save(share)
        val failure = try {
            NetworkShares.open(share).use { root ->
                if (!root.exists()) "Dossier partagé introuvable sur cette machine"
                else { root.listFiles(); null }
            }
        } catch (e: Exception) {
            Log.w("LibraryRepository", "Partage refusé", e)
            explain(e)
        }
        if (failure != null) {
            // On ne garde pas un partage qui ne fonctionne pas — sauf s'il existait déjà
            if (store.volume(share.volumeId) == null) shares.remove(share.volumeId)
            NetworkShares.reset(share.volumeId)
            return@withContext failure
        }

        val existing = store.volume(share.volumeId)
        val label = share.label.ifBlank { share.share }
        store.upsertVolume(
            (existing ?: Volume(
                id = share.volumeId,
                label = label,
                theme = DriveTheme.guessFrom(label),
                orderIndex = store.volumes().size
            )).copy(label = label, isConnected = true, lastSeenAt = System.currentTimeMillis())
        )
        null
    }

    /** Reprend sur [toVolume] la config d'un ancien disque. Renvoie le compte rendu à afficher. */
    suspend fun adoptConfig(fromVolume: String, toVolume: String): String = withContext(Dispatchers.IO) {
        val (done, total) = store.adoptConfig(fromVolume, toVolume)
        when {
            total == 0 -> "L'ancien disque n'avait aucune config à reprendre"
            done == 0 -> "Aucun fichier retrouvé : analysez d'abord le nouveau disque, avec les mêmes dossiers et noms de fichiers"
            done == total -> "$done fiches reprises, tout a été retrouvé"
            else -> "$done fiches reprises sur $total — les autres fichiers sont absents ou renommés"
        }
    }

    suspend fun removeShare(volumeId: String) = withContext(Dispatchers.IO) {
        shares.remove(volumeId)
        store.deleteVolume(volumeId)
    }

    /** Traduit une erreur réseau en phrase utile. */
    private fun explain(e: Exception): String {
        val text = generateSequence<Throwable>(e) { it.cause }.joinToString(" ") { it.message.orEmpty() }
        return when {
            "LOGON_FAILURE" in text || "logon failure" in text.lowercase() ||
                "ACCESS_DENIED" in text || "Access is denied" in text ->
                "Identifiant ou mot de passe refusé par la machine"
            "BAD_NETWORK_NAME" in text -> "Ce dossier partagé n'existe pas sur cette machine"
            "UnknownHost" in text || "Failed to connect" in text || "timed out" in text ||
                "Connection refused" in text || "No route" in text ->
                "Machine injoignable : vérifiez l'adresse et qu'elle est allumée"
            else -> "Connexion impossible : ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /** Une seule analyse à la fois : relancer pendant qu'une autre tourne la met simplement à la suite. */
    private val scanLock = kotlinx.coroutines.sync.Mutex()

    suspend fun scanVolume(volumeId: String) = withContext(Dispatchers.IO) { scanLock.withLock { scanOne(volumeId) } }

    private suspend fun scanOne(volumeId: String) {
        val vol = store.volume(volumeId) ?: run {
            _scanState.value = ScanState.Failed("Disque inconnu")
            return
        }

        _scanState.value = ScanState.Running(vol.label, 0)

        try {
            val network = NetworkShare.isNetwork(volumeId)
            val out = if (network) {
                val share = shares.find(volumeId) ?: run {
                    _scanState.value = ScanState.Failed("Partage réseau oublié : ajoutez-le de nouveau")
                    return
                }
                SmbScanner(context).scan(share) { count ->
                    _scanState.value = ScanState.Running(vol.label, count)
                }
            } else {
                MediaLibraryScanner(context).scan(volumeId) { count ->
                    _scanState.value = ScanState.Running(vol.label, count)
                }
            }

            if (out.nodes.isEmpty()) {
                store.setVolumeCounts(volumeId, 0, 0)
                _scanState.value = ScanState.Failed(
                    if (network) "Aucune vidéo trouvée dans le partage ${vol.label}."
                    else "Aucune vidéo trouvée sur ${vol.label}. " +
                        "Si le disque vient d'être branché, laissez à la TV le temps de l'indexer."
                )
                return
            }

            val known = store.nodeIds(volumeId)
            val fresh = out.nodes.map { it.id }.toHashSet()

            store.insertNewNodes(out.nodes.filter { it.id !in known })

            // Les fiches déjà connues : l'adresse du fichier change au remontage
            for (n in out.nodes) {
                if (n.id in known) {
                    if (n.isDirectory) store.refreshFolder(n.id, n.documentUri, n.childCount)
                    else store.refreshFile(n.id, n.documentUri, n.sizeBytes)
                }
            }

            store.deleteNodes(known - fresh)
            store.setVolumeAvailability(volumeId, true)
            store.setVolumeConnected(volumeId, true)
            store.setVolumeCounts(volumeId, out.videoCount, out.folderCount)

            _scanState.value = ScanState.Done(vol.label, out.videoCount, out.folderCount, out.source)
        } catch (e: Exception) {
            Log.e("LibraryRepository", "Scan échoué", e)
            _scanState.value = ScanState.Failed(e.message ?: "Erreur pendant le scan")
        }
    }

    fun clearScanState() { _scanState.value = ScanState.Idle }

    /**
     * La rangée « Reprendre » : les titres entamés, et pour les séries
     * l'épisode qui suit le dernier terminé.
     */
    suspend fun continueWatching(): List<Node> = withContext(Dispatchers.IO) {
        val started = store.continueWatching().shown()
        val taken = started.map { it.id }.toMutableSet()
        val folders = started.mapNotNull { it.parentId }.toMutableSet()

        val upNext = ArrayList<Node>()
        for (finished in store.recentlyFinished()) {
            if (!FileNameParser.parse(finished.name).isEpisode) continue
            val folder = finished.parentId ?: continue
            if (folder in folders) continue                 // la série a déjà sa carte
            folders += folder
            val next = store.nextInFolder(finished) ?: continue
            if (next.isWatched || next.id in taken) continue
            taken += next.id
            // Classé à la date où l'épisode précédent a été terminé
            upNext += next.copy(lastPlayedAt = finished.lastPlayedAt)
        }
        (started + upNext).sortedByDescending { it.lastPlayedAt }.take(20)
    }

    /** Épisodes vus / total d'un dossier de série. */
    suspend fun folderProgress(folderId: String): Pair<Int, Int> =
        withContext(Dispatchers.IO) { store.folderProgress(folderId) }

    /** Les autres volets de la saga présents sur les disques. */
    suspend fun saga(node: Node): List<Node> =
        withContext(Dispatchers.IO) { store.inCollection(node.collectionId, node.tmdbId) }

    // ----------------------------------------------------- fichiers eux-mêmes

    private val fileOps by lazy { FileOps(context, shares) }

    /** Définition, HDR, pistes audio : lus dans l'en-tête du fichier. */
    suspend fun techInfo(node: Node): TechInfo? =
        withContext(Dispatchers.IO) { MediaProbe.probe(context, shares, node) }

    /** Sous-titres posés à côté du film. */
    suspend fun externalSubtitles(node: Node): List<ExternalSubtitle> =
        withContext(Dispatchers.IO) {
            runCatching { ExternalSubtitles.find(context, shares, node) }.getOrDefault(emptyList()) +
                downloadedSubtitles(node)
        }

    // ------------------------------------------------- sous-titres en ligne

    /** Où l'app range les sous-titres téléchargés pour ce fichier. */
    private fun subtitleFolder(node: Node) = java.io.File(context.filesDir, "subs/${node.id}")

    /** Les sous-titres déjà téléchargés pour ce fichier : ils restent d'une lecture à l'autre. */
    private fun downloadedSubtitles(node: Node): List<ExternalSubtitle> =
        subtitleFolder(node).listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".srt") }
            .sortedBy { it.name }
            .map { file ->
                val language = file.name.substringBefore('-')
                ExternalSubtitle(file, "application/x-subrip", language, "En ligne · ${language.uppercase()}")
            }

    /** Cherche des sous-titres pour ce fichier. Vide en cas d'échec : la raison est dans le second élément. */
    suspend fun findOnlineSubtitles(node: Node): Pair<List<OpenSubtitles.Found>, String> =
        withContext(Dispatchers.IO) {
            val tag = Regex("""S(\d{1,2})\s?E(\d{1,3})""", RegexOption.IGNORE_CASE)
                .let { it.find(node.title) ?: it.find(node.name) }
            val found = OpenSubtitles.search(
                tmdbId = node.tmdbId,
                isEpisode = tag != null,
                season = tag?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                episode = tag?.groupValues?.get(2)?.toIntOrNull() ?: 0,
                title = node.displayTitle
            )
            found to OpenSubtitles.lastError
        }

    /** Télécharge un sous-titre et le range avec le fichier. Renvoie le sous-titre prêt à lire, ou la raison de l'échec. */
    suspend fun fetchOnlineSubtitle(node: Node, found: OpenSubtitles.Found): Pair<ExternalSubtitle?, String> =
        withContext(Dispatchers.IO) {
            val bytes = OpenSubtitles.download(found)
                ?: return@withContext null to OpenSubtitles.lastError.ifBlank { "Téléchargement impossible" }
            runCatching {
                val folder = subtitleFolder(node).apply { mkdirs() }
                val language = found.language.ifBlank { "xx" }
                val file = java.io.File(folder, "$language-${found.fileId}.srt")
                file.writeBytes(bytes)
                ExternalSubtitle(file, "application/x-subrip", language, "En ligne · ${language.uppercase()}") to ""
            }.getOrElse { null to "Impossible d'enregistrer le sous-titre" }
        }

    // ------------------------------------- ma liste, non reconnus, chiffres

    suspend fun setListed(nodeId: String, listed: Boolean) = withContext(Dispatchers.IO) { store.setListed(nodeId, listed) }
    suspend fun myList(): List<Node> = withContext(Dispatchers.IO) { store.myList().shown() }
    suspend fun unmatched(): List<Node> = withContext(Dispatchers.IO) { store.unmatched().shown() }
    suspend fun stats(): LibraryStats = withContext(Dispatchers.IO) { store.stats() }

    /**
     * Écrit la liste complète des vidéos de tous les disques dans un tableau
     * (ouvrable dans Excel), sur chaque dossier réseau branché. Si un disque
     * meurt, on sait ce qu'il contenait.
     */
    suspend fun exportCatalogue(): String = withContext(Dispatchers.IO) {
        runCatching {
            val online = store.volumes().filter { it.isConnected }.map { it.id }.toSet()
            val targets = shares.all().filter { it.volumeId in online }
            if (targets.isEmpty()) return@runCatching "Branchez d'abord un dossier réseau : le catalogue s'y enregistre"

            fun cell(text: String) = '"' + text.replace("\"", "\"\"") + '"'
            val lines = StringBuilder("\uFEFF")
            lines.append("Disque;Dossier;Fichier;Titre;Année;Taille (Go);Durée (min);Vu;Reconnu\r\n")
            var count = 0
            for (volume in store.volumes()) {
                for (node in store.filesOf(volume.id)) {
                    lines.append(
                        listOf(
                            cell(volume.label),
                            cell(node.relativePath.substringBeforeLast('/', "")),
                            cell(node.name),
                            cell(node.title),
                            if (node.year > 0) node.year.toString() else "",
                            String.format(java.util.Locale.FRANCE, "%.2f", node.sizeBytes / 1e9),
                            if (node.durationMs > 0) (node.durationMs / 60_000).toString() else "",
                            if (node.isWatched) "oui" else "non",
                            if (node.tmdbId > 0) "oui" else "non"
                        ).joinToString(";")
                    ).append("\r\n")
                    count++
                }
            }
            val bytes = lines.toString().toByteArray(Charsets.UTF_8)
            var written = 0
            var lastError = ""
            for (share in targets) {
                try {
                    NetworkShares.open(share, CATALOGUE_FILE).use { file ->
                        jcifs.smb.SmbFileOutputStream(file).use { it.write(bytes) }
                    }
                    written++
                } catch (e: Exception) {
                    lastError = e.message ?: e.javaClass.simpleName
                }
            }
            if (written > 0) "$count vidéos listées dans $CATALOGUE_FILE, sur $written dossier(s) réseau"
            else "Écriture refusée par le partage : $lastError"
        }.getOrElse { "Catalogue impossible : ${it.message}" }
    }

    /** Supprime pour de vrai, puis retire de la bibliothèque. Renvoie null ou la raison de l'échec. */
    suspend fun deleteForReal(node: Node): String? = withContext(Dispatchers.IO) {
        val failure = fileOps.delete(node)
        if (failure == null) store.deleteSubtree(node.id)
        failure
    }

    /**
     * Renomme pour de vrai. Pour un fichier, l'extension est conservée :
     * [newBaseName] est le nom sans elle. Renvoie null ou la raison de l'échec.
     */
    suspend fun renameForReal(node: Node, newBaseName: String): String? = withContext(Dispatchers.IO) {
        val clean = newBaseName.trim()
        val extension = if (node.isDirectory) "" else node.name.substringAfterLast('.', "")
        val newName = if (extension.isEmpty()) clean else "$clean.$extension"
        if (newName == node.name) return@withContext null

        fileOps.rename(node, newName)?.let { return@withContext it }

        // La bibliothèque suit : même fiches, nouveaux chemins
        val oldPrefix = node.relativePath
        val parentPath = oldPrefix.substringBeforeLast('/', "")
        val newPrefix = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
        val share = shares.find(node.volumeId)
        val tree = store.subtree(node.id)
        val newIds = tree.associate { old ->
            old.id to MediaLibraryScanner.nodeId(old.volumeId, newPrefix + old.relativePath.removePrefix(oldPrefix))
        }
        val moves = tree.map { old ->
            val path = newPrefix + old.relativePath.removePrefix(oldPrefix)
            val fresh = old.copy(
                id = newIds.getValue(old.id),
                parentId = old.parentId?.let { newIds[it] ?: it },
                name = if (old.id == node.id) newName else old.name,
                relativePath = path,
                documentUri = when {
                    old.isDirectory -> old.documentUri
                    share != null -> share.rootUrl + path
                    else -> old.documentUri
                }
            )
            Triple(old, old.id, fresh)
        }
        store.applyRename(moves)
        null
    }

    // ----------------------------------------------------------- sauvegarde

    private val backup by lazy { LibraryBackup(store, shares, prefs) }

    private companion object { const val CATALOGUE_FILE = "netfloux-catalogue.csv" }

    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        runCatching { backup.export() }.getOrElse { "Sauvegarde impossible : ${it.message}" }
    }

    suspend fun restoreBackup(): String = withContext(Dispatchers.IO) {
        refreshNetworkAvailability()
        runCatching { backup.restore() }.getOrElse { "Restauration impossible : ${it.message}" }
    }

    // ----------------------------------------------------- recherche réseau

    suspend fun findHosts(): List<FoundHost> =
        runCatching { NetworkDiscovery.hosts() }.getOrDefault(emptyList())

    /** Dossiers partagés d'une machine, ou la raison du refus. */
    suspend fun findShares(host: String, user: String, password: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(NetworkDiscovery.shares(host, user, password))
            } catch (e: Exception) {
                Result.failure(Exception(explain(e)))
            }
        }

    // -------------------------------------------- nouveautés, au lancement

    @Volatile private var refreshing = false

    /**
     * Passe sur tous les disques et partages joignables et n'ajoute que ce qui
     * est nouveau. Silencieux : un disque vide ou injoignable ne lève pas d'alerte.
     */
    suspend fun autoRefresh() = withContext(Dispatchers.IO) {
        if (refreshing || _scanState.value is ScanState.Running) return@withContext
        refreshing = true
        try {
            val allowed = StorageAccess.hasPermission(context)
            if (allowed) refreshVolumeAvailability()
            refreshNetworkAvailability()
            val targets = store.volumes()
                .filter { it.isConnected && (allowed || NetworkShare.isNetwork(it.id)) }
            for (volume in targets) {
                scanVolume(volume.id)
                if (_scanState.value is ScanState.Failed) _scanState.value = ScanState.Idle
            }
            if (_scanState.value !is ScanState.Running) _scanState.value = ScanState.Idle
        } finally {
            refreshing = false
        }
        enrichPendingMetadata()
    }

    /** Sélection aléatoire pour la bannière d'accueil. */
    suspend fun randomSpotlight(limit: Int = 14): List<Node> =
        withContext(Dispatchers.IO) { store.randomSpotlight(limit).shown() }

    /** Derniers titres ajoutés, pour la rangée « Nouveautés » de l'accueil. */
    /**
     * Les ajouts récents : ceux de la dernière semaine seulement, et rien
     * d'antérieur au dernier « Vider » de la rangée.
     */
    suspend fun recentlyAdded(limit: Int = 24): List<Node> =
        withContext(Dispatchers.IO) {
            val weekAgo = System.currentTimeMillis() - RECENT_WINDOW_MS
            val cleared = com.cinetv.data.prefs.Settings(context).recentClearedAt
            store.recentlyAdded(limit, since = maxOf(weekAgo, cleared)).shown()
        }

    /** Bandes-annonces déjà cherchées pendant cette session (chaîne vide = aucune). */
    private val trailers = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Identifiant YouTube de la bande-annonce d'un titre, ou null. */
    suspend fun trailerKey(node: Node): String? = withContext(Dispatchers.IO) {
        if (node.tmdbId <= 0) return@withContext null
        val id = "${node.mediaType}:${node.tmdbId}"
        trailers.getOrPut(id) {
            runCatching { com.cinetv.data.tmdb.Tmdb.trailerKey(node.tmdbId, node.mediaType) }.getOrNull().orEmpty()
        }.takeIf { it.isNotBlank() }
    }

    /** Vide la rangée « Récemment ajoutés » ; les fichiers eux-mêmes ne sont pas touchés. */
    fun clearRecent() {
        com.cinetv.data.prefs.Settings(context).recentClearedAt = System.currentTimeMillis()
    }

    /**
     * Visuels de l'économiseur d'écran : titre, image, logo. Une seule entrée par œuvre —
     * une série compte pour une, quel que soit son nombre d'épisodes, et se présente sous son nom.
     */
    suspend fun recentBackdrops(limit: Int = 30): List<Triple<String, String, String>> =
        withContext(Dispatchers.IO) {
            store.randomSpotlight(limit).shown()
                .distinctBy { it.backdropUrl }
                .map { Triple(it.displayTitle.substringBefore(" · "), it.backdropUrl, it.logoUrl) }
        }

    /** Films au hasard sous un dossier (ou dans tout le disque), pour la bannière. */
    suspend fun randomUnder(volumeId: String, folderId: String?, limit: Int = 8): List<Node> =
        withContext(Dispatchers.IO) { store.randomUnder(volumeId, folderId, limit).shown() }

    suspend fun clearResume(nodeId: String) =
        withContext(Dispatchers.IO) { store.clearResume(nodeId) }

    suspend fun clearAllResume() =
        withContext(Dispatchers.IO) { store.clearAllResume() }

    suspend fun nextInFolder(node: Node): Node? =
        withContext(Dispatchers.IO) { store.nextInFolder(node) }

    // ------------------------------------------------- correction manuelle

    /** Recherche libre dans TMDB, pour corriger un appariement raté. */
    suspend fun searchTmdb(query: String): List<Tmdb.Hit> =
        withContext(Dispatchers.IO) { Tmdb.searchAll(query) }

    /**
     * Rattache un fichier ou un dossier à une autre fiche TMDB.
     * `artworkOnly` ne remplace que les images, si seul le visuel est en cause.
     *
     * Sur un dossier, quand la fiche choisie est celle d'une série, elle descend dans tout
     * ce qu'il contient : chaque épisode de chaque saison prend la nouvelle série (son titre,
     * ses images, son résumé) en gardant son numéro. Les saisons suivent donc le dossier.
     */
    suspend fun relink(nodeId: String, hit: Tmdb.Hit, artworkOnly: Boolean) =
        withContext(Dispatchers.IO) {
            val poster = Tmdb.poster(hit.posterPath)
            val backdrop = Tmdb.backdrop(hit.backdropPath)
            val target = store.node(nodeId)
            val episodes = if (target != null && target.isDirectory && hit.mediaType == "tv") {
                runCatching { store.filesUnder(nodeId) }.getOrDefault(emptyList())
            } else emptyList()

            if (artworkOnly) {
                store.replaceArtwork(nodeId, poster, backdrop)
                episodes.forEach { store.replaceArtwork(it.id, poster, backdrop) }
            } else {
                fun fill(id: String, title: String) = store.applyMetadata(
                    id = id,
                    state = MetaState.RESOLVED,
                    tmdbId = hit.tmdbId,
                    title = title,
                    year = hit.year,
                    releaseDate = hit.date,
                    synopsis = hit.overview,
                    posterUrl = poster,
                    backdropUrl = backdrop,
                    rating = hit.rating,
                    mediaType = hit.mediaType
                )
                fill(nodeId, hit.title)
                episodes.forEach { file ->
                    val parsed = FileNameParser.parse(file.name)
                    val label = when {
                        parsed.isEpisode -> {
                            val s = parsed.season!!.toString().padStart(2, '0')
                            val e = parsed.episode!!.toString().padStart(2, '0')
                            "${hit.title} · S${s}E$e"
                        }
                        parsed.title.isNotBlank() -> "${hit.title} · ${parsed.title}"
                        else -> hit.title
                    }
                    fill(file.id, label)
                }
                // La fiche du dossier a changé : ce qu'on avait retenu pour lui ne vaut plus
                folderHits.remove(nodeId)
                store.notifyChanged()
                enrichCredits()
            }
        }

    // ------------------------------------------------------- métadonnées TMDB

    /**
     * Récupère les métadonnées manquantes, par lots, jusqu'à épuisement.
     *
     * L'avancement est publié au fur et à mesure pour que l'interface puisse
     * montrer où en est le travail : TMDB est interrogé fichier par fichier,
     * et sur une grosse bibliothèque cela prend plusieurs minutes.
     */
    suspend fun enrichPendingMetadata(limit: Int = 0) = withContext(Dispatchers.IO) {
        if (!Tmdb.isConfigured) return@withContext
        if (enriching) return@withContext
        enriching = true

        try {
            // La recherche sait maintenant s'appuyer sur le nom du dossier : les fichiers restés
            // sans fiche avec l'ancienne méthode sont retentés, une seule fois.
            if (prefs.metadataPass < METADATA_PASS) {
                store.retryUnmatched()
                prefs.metadataPass = METADATA_PASS
            }
            folderHits.clear()
            val total = store.countPendingMetadata()
            if (total == 0) { _metadata.value = null; return@withContext }

            var done = 0
            var found = 0
            var missed = 0
            val ceiling = if (limit > 0) minOf(limit, total) else total

            while (done < ceiling) {
                val batch = store.pendingMetadata(minOf(25, ceiling - done))
                if (batch.isEmpty()) break

                for (node in batch) {
                    _metadata.value = MetadataProgress(done, ceiling, node.name, found, missed)
                    val ok = runCatching { enrichOne(node) }
                        .onFailure { Log.w("LibraryRepository", "TMDB ${node.name} : ${it.message}") }
                        .getOrDefault(false)
                    if (ok) found++ else missed++
                    done++
                    delay(60)   // on ménage l'API
                }
            }

            _metadata.value = MetadataProgress(done, ceiling, "", found, missed)
            delay(2500)         // le résultat reste lisible un instant
            _metadata.value = null
        } finally {
            enriching = false
        }
        enrichCredits()
        qualityPass()
    }

    @Volatile private var enriching = false

    /** Fiches déjà trouvées pour un dossier pendant cette session (null = cherchée, introuvable). */
    private val folderHits = HashMap<String, Tmdb.Hit?>()

    private val SEASON_FOLDER = Regex("""(?i)^\s*(saison|season|series|s|specials?|spéciaux|bonus|extras?)[\s._-]*\d*\s*$""")

    /** Les dossiers au-dessus d'un fichier, du plus proche au plus lointain, sans dépasser un dossier-disque. */
    private fun foldersAbove(node: Node): List<Node> {
        val apart = prefs.virtualDrives.mapTo(HashSet()) { it.folderId }
        val chain = ArrayList<Node>()
        var cursor = node.parentId
        var guard = 0
        while (cursor != null && cursor !in apart && guard++ < 64) {
            val folder = store.node(cursor) ?: break
            chain += folder
            cursor = folder.parentId
        }
        return chain
    }

    /** La fiche TMDB d'un dossier de série : celle choisie à la main, sinon cherchée d'après son nom. */
    private fun hitForFolder(folder: Node): Tmdb.Hit? {
        if (folderHits.containsKey(folder.id)) return folderHits[folder.id]
        val hit = if (folder.tmdbId > 0) {
            Tmdb.Hit(
                tmdbId = folder.tmdbId, title = folder.title.ifBlank { folder.name }, overview = folder.synopsis,
                posterPath = null, backdropPath = null, date = folder.releaseDate.ifBlank { folder.year.toString() },
                rating = folder.rating, mediaType = folder.mediaType.ifBlank { "tv" }
            )
        } else {
            val name = FileNameParser.parse(folder.name + ".x").title
            if (name.isBlank()) null else (Tmdb.searchTv(name) ?: Tmdb.searchMulti(name) ?: looseHit(name, series = true))
        }
        folderHits[folder.id] = hit
        return hit
    }

    /** Recherches tolérantes déjà faites pendant cette session : les 60 épisodes d'une série n'en coûtent qu'une. */
    private val looseHits = HashMap<String, Tmdb.Hit?>()

    private fun looseHit(title: String, series: Boolean): Tmdb.Hit? {
        val key = "${title.lowercase()}|$series"
        if (looseHits.containsKey(key)) return looseHits[key]
        val hit = runCatching { Tmdb.searchLoose(title, series) }.getOrNull()
        looseHits[key] = hit
        return hit
    }

    private fun enrichOne(node: Node): Boolean {
        // Un dossier caché ne part jamais sur TMDB : ses fichiers sont laissés de côté, sans requête
        if (isHidden(node)) {
            store.applyMetadata(
                node.id, MetaState.SKIPPED, 0, FileNameParser.prettyTitle(node.name),
                0, "", "", "", "", 0f
            )
            return false
        }
        val parsed = FileNameParser.parse(node.name)
        val above = foldersAbove(node)
        val theme = virtualAbove(node.parentId)?.theme ?: store.volume(node.volumeId)?.theme
        // Sur un disque de séries, le dossier de premier niveau EST la série : son nom vaut mieux
        // que celui du fichier, souvent réduit à « S02E05 » ou au titre de l'épisode.
        val seriesFolder = if (theme == DriveTheme.SERIES) above.lastOrNull() else null
        var fromFolder: Node? = seriesFolder
        var hit: Tmdb.Hit? = seriesFolder?.let { hitForFolder(it) }

        if (hit == null && parsed.title.isNotBlank()) {
            hit = if (parsed.isEpisode) {
                Tmdb.searchTv(parsed.title)
            } else {
                Tmdb.searchMovie(parsed.title, parsed.year) ?: Tmdb.searchMulti(parsed.title)
            }
            if (hit != null) fromFolder = null
        }

        // Toujours rien : le nom contient peut-être une faute de frappe (« Games of Thrones »)
        if (hit == null && parsed.title.isNotBlank()) {
            hit = looseHit(parsed.title, series = parsed.isEpisode)
            if (hit != null) fromFolder = null
        }

        // Un épisode que son nom de fichier ne suffit pas à identifier (série animée rangée
        // parmi des films, par exemple) : on essaie le dossier qui le contient, saison exclue.
        if (hit == null && parsed.isEpisode) {
            val folder = above.firstOrNull { !SEASON_FOLDER.matches(it.name) }
            if (folder != null) { hit = hitForFolder(folder); fromFolder = folder }
        }

        if (hit == null && parsed.title.isBlank()) {
            store.applyMetadata(
                node.id, MetaState.SKIPPED, 0, FileNameParser.prettyTitle(node.name),
                0, "", "", "", "", 0f
            )
            return false
        }

        if (hit == null) {
            store.applyMetadata(
                node.id, MetaState.NOT_FOUND, 0, FileNameParser.prettyTitle(node.name),
                parsed.year ?: 0, "", "", "", "", 0f
            )
            return false
        }

        val label = when {
            parsed.isEpisode -> {
                val s = parsed.season!!.toString().padStart(2, '0')
                val e = parsed.episode!!.toString().padStart(2, '0')
                "${hit.title} · S${s}E$e"
            }
            // Épisode sans numéro lisible : la série, puis ce que dit le nom du fichier
            seriesFolder != null -> "${hit.title} · ${parsed.title}"
            else -> hit.title
        }
        // Fiche héritée d'un dossier réglé à la main : ses images sont celles du dossier
        val folderArt = fromFolder?.takeIf { it.tmdbId > 0 }

        store.applyMetadata(
            id = node.id,
            state = MetaState.RESOLVED,
            tmdbId = hit.tmdbId,
            title = label,
            year = hit.year,
            releaseDate = hit.date,
            synopsis = hit.overview,
            posterUrl = folderArt?.posterUrl?.takeIf { it.isNotBlank() } ?: Tmdb.poster(hit.posterPath),
            backdropUrl = folderArt?.backdropUrl?.takeIf { it.isNotBlank() } ?: Tmdb.backdrop(hit.backdropPath),
            rating = hit.rating,
            mediaType = hit.mediaType.ifBlank { if (parsed.isEpisode || seriesFolder != null) "tv" else "movie" }
        )
        return true
    }

    // ------------------------------------------------ acteurs et réalisation

    @Volatile private var crediting = false

    /** Le réseau ne répond plus : inutile d'insister. */
    private class OfflineException : Exception()

    /**
     * Complète les fiches avec leurs acteurs et leur réalisation.
     *
     * Tourne en fond, sans rien afficher : les fiches déjà reconnues avant
     * cette fonction sont rattrapées ici, au fil des lancements. Les épisodes
     * d'une même série partagent une seule requête.
     */
    suspend fun enrichCredits() = withContext(Dispatchers.IO) {
        if (!Tmdb.isConfigured || crediting) return@withContext
        crediting = true
        try {
            // Réponses déjà obtenues, absences comprises : les 60 épisodes d'une
            // série ne coûtent qu'une requête, qu'elle aboutisse ou non.
            val cache = HashMap<String, Tmdb.Credits?>()
            var offline = 0

            /** null = TMDB ne connaît pas cette fiche sous ce type. Lève [OfflineException] sans réseau. */
            suspend fun fetch(id: Int, type: String): Tmdb.Credits? {
                val key = "$type:$id"
                if (cache.containsKey(key)) return cache[key]
                delay(40)
                val result = runCatching { Tmdb.credits(id, type) }.getOrNull()
                if (result == null && Tmdb.lastFailureWasNetwork) {
                    // Pas de réponse du tout : on ne conclut rien, et on ne garde rien
                    if (++offline >= 4) throw OfflineException()
                    return null
                }
                offline = 0
                cache[key] = result
                return result
            }

            val creditsTotal = store.countPendingCredits()
            var creditsDone = 0
            try {
                while (true) {
                    val batch = store.pendingCredits()
                    if (batch.isEmpty()) break
                    var progressed = false
                    for (node in batch) {
                        // Cette seconde passe (logos, genres, distribution) s'affiche elle aussi
                        if (creditsTotal > 0) {
                            _metadata.value = MetadataProgress(
                                minOf(creditsDone, creditsTotal), creditsTotal, node.displayTitle, 0, 0,
                                stage = "LOGOS ET GENRES"
                            )
                        }
                        creditsDone++
                        // Le nom du fichier est le meilleur indice : « S02E05 » = série.
                        // À défaut, le type déjà enregistré, puis film.
                        val guess = when {
                            FileNameParser.parse(node.name).isEpisode -> "tv"
                            node.mediaType.isNotBlank() -> node.mediaType
                            else -> "movie"
                        }
                        var type = guess
                        val before = offline
                        var credits = fetch(node.tmdbId, guess)
                        if (credits == null && offline == before) {
                            // Inconnu sous ce type : on tente l'autre
                            type = if (guess == "tv") "movie" else "tv"
                            credits = fetch(node.tmdbId, type)
                        }
                        if (credits == null && offline > before) continue   // réseau : on y reviendra

                        // Réponse obtenue — trouvée ou réellement absente : la fiche est réglée
                        progressed = true
                        store.applyCredits(
                            id = node.id,
                            mediaType = if (credits != null) type else node.mediaType,
                            actors = credits?.actors?.joinToString(", ").orEmpty(),
                            director = credits?.director.orEmpty(),
                            collectionId = credits?.collectionId ?: 0,
                            collectionName = credits?.collectionName.orEmpty(),
                            genres = credits?.genres?.joinToString(",").orEmpty(),
                            // Le logo suit le nom du fichier : titre français → logo français, titre original → logo original
                            logoUrl = credits?.logoFor(
                                if (node.isDirectory) node.name else FileNameParser.parse(node.name).title
                            ).orEmpty()
                        )
                    }
                    store.notifyChanged()
                    if (!progressed) break                      // rien n'avance : on s'arrête là
                }
            } catch (e: OfflineException) {
                // Hors ligne : rien n'est marqué, tout sera repris au prochain lancement
            }
            if (creditsTotal > 0) _metadata.value = null
            store.notifyChanged()
        } finally {
            crediting = false
        }
    }

    // ----------------------------------------------------------- état lecture

    suspend fun savePosition(nodeId: String, positionMs: Long, durationMs: Long) =
        withContext(Dispatchers.IO) { store.savePosition(nodeId, positionMs, durationMs) }

    suspend fun setWatched(nodeId: String, watched: Boolean) =
        withContext(Dispatchers.IO) { store.setWatched(nodeId, watched) }
}
