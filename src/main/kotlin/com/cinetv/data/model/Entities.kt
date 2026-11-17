package com.cinetv.data.model

/**
 * Thème visuel d'un disque. Deviné depuis son nom, modifiable par l'utilisateur.
 */
enum class DriveTheme(val label: String) {
    FILMS("Films"),
    SERIES("Séries"),
    ANIMATION("Animation"),
    DOCS("Documentaires"),
    CONCERTS("Concerts"),
    JEUNESSE("Jeunesse"),
    AUTRE("Autre");

    companion object {
        fun guessFrom(label: String): DriveTheme {
            val l = label.lowercase()
            return when {
                l.contains("anim") || l.contains("manga") || l.contains("cartoon") -> ANIMATION
                l.contains("serie") || l.contains("série") || l.contains("tv") || l.contains("show") -> SERIES
                l.contains("doc") -> DOCS
                l.contains("concert") || l.contains("live") || l.contains("music") -> CONCERTS
                l.contains("enfant") || l.contains("jeunesse") || l.contains("kids") -> JEUNESSE
                l.contains("film") || l.contains("movie") || l.contains("cine") -> FILMS
                else -> AUTRE
            }
        }

        fun parse(name: String?): DriveTheme =
            entries.firstOrNull { it.name == name } ?: AUTRE
    }
}

/**
 * Un disque connu de l'application.
 * `id` est le nom du volume tel que l'index média du système le désigne ;
 * il reste le même d'un branchement à l'autre, ce qui permet de retrouver la fiche.
 */
data class Volume(
    val id: String,
    val label: String,
    val theme: DriveTheme = DriveTheme.AUTRE,
    val isConnected: Boolean = false,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val videoCount: Int = 0,
    val folderCount: Int = 0,
    val orderIndex: Int = 0,

    // Espace disque, relevé à chaque branchement (0 = jamais mesuré)
    val freeBytes: Long = 0,
    val totalBytes: Long = 0
)

enum class MetaState {
    PENDING, RESOLVED, NOT_FOUND, SKIPPED;

    companion object {
        fun parse(name: String?): MetaState =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}

/**
 * Un nœud de l'arborescence : dossier ou fichier vidéo.
 * Toute l'arborescence est mise en cache, pour rester navigable disque débranché.
 */
data class Node(
    val id: String,
    val volumeId: String,
    val parentId: String?,
    val name: String,
    val relativePath: String,
    val documentUri: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val childCount: Int = 0,
    val isAvailable: Boolean = true,

    // Métadonnées TMDB (fichiers vidéo uniquement)
    val metadataState: MetaState = MetaState.PENDING,
    val tmdbId: Int = 0,
    val title: String = "",
    val year: Int = 0,
    val releaseDate: String = "",
    val synopsis: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val rating: Float = 0f,
    val mediaType: String = "",      // "movie" ou "tv"
    val actors: String = "",         // têtes d'affiche, séparées par des virgules
    val director: String = "",       // réalisation (ou création, pour une série)
    val creditsDone: Boolean = false,
    val collectionId: Int = 0,       // saga TMDB à laquelle le film appartient
    val collectionName: String = "",
    val genres: String = "",         // identifiants de genres TMDB, séparés par des virgules
    val logoUrl: String = "",        // logo officiel du titre, à fond transparent
    val listedAt: Long = 0,          // mis dans « Ma liste » à cet instant ; 0 = n'y est pas
    /**
     * Définition de la vidéo : 2160, 1080, 720, 480 ou 360, lue dans le fichier lui-même.
     * 0 = pas encore regardée, -1 = illisible. Un chiffre des unités à 1 (1081, 721…) : valeur
     * provisoire tirée du nom, en attendant la lecture du fichier ; à 2 : le fichier n'a pas pu
     * être lu, la valeur du nom est gardée.
     */
    val quality: Int = 0,

    // État de lecture
    val lastPositionMs: Long = 0,
    val durationMs: Long = 0,
    val isWatched: Boolean = false,
    val lastPlayedAt: Long = 0,

    val addedAt: Long = System.currentTimeMillis()
) {
    val displayTitle: String
        // Un dossier garde son nom entier : « Mr. Robot » n'a pas d'extension à retirer
        get() = title.ifBlank { if (isDirectory) name else name.substringBeforeLast('.') }

    val progressFraction: Float
        get() = if (durationMs > 0) (lastPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Une étape du fil d'Ariane. */
data class Crumb(val id: String?, val label: String)


/**
 * Vrai si le logo du titre n'est pas dans la langue de son titre français (logo anglais d'un
 * film connu ici sous un nom français) : les bannières écrivent alors ce titre sous le logo.
 */
val Node.logoIsForeign: Boolean get() = logoUrl.endsWith("#vo")

/** Un titre proposé dans la bannière sans être sur les disques : son identifiant commence par « tmdb: ». */
val Node.isSuggestion: Boolean get() = id.startsWith("tmdb:")

/** « 4K », « 1080p », « 720p »… ; vide tant que la définition n'est pas connue. */
fun qualityLabel(quality: Int): String = when {
    quality >= 2160 -> "4K"
    quality >= 1080 -> "1080p"
    quality >= 720 -> "720p"
    quality >= 480 -> "480p"
    quality > 0 -> "360p"
    else -> ""
}

/** Range une image dans l'une des définitions affichées, d'après sa largeur et sa hauteur en pixels. */
fun qualityOf(width: Int, height: Int): Int = when {
    width >= 3200 || height >= 1700 -> 2160
    width >= 1800 || height >= 1000 -> 1080
    width >= 1200 || height >= 700 -> 720
    width >= 800 || height >= 440 -> 480
    width > 0 || height > 0 -> 360
    else -> 0
}

private val QUALITY_IN_NAME = Regex("""(?i)(?<![a-z0-9])(2160p|4k|uhd|1080[pi]|720p|576p|480p|360p)(?![a-z0-9])""")

/** La définition annoncée par le nom du fichier (« Film.2019.1080p.mkv »), ou 0 s'il n'en dit rien. */
fun qualityFromName(name: String): Int =
    when (QUALITY_IN_NAME.find(name)?.value?.lowercase()) {
        "2160p", "4k", "uhd" -> 2160
        "1080p", "1080i" -> 1080
        "720p" -> 720
        "576p", "480p" -> 480
        "360p" -> 360
        else -> 0
    }

/**
 * Un titre proposé par TMDB et absent des disques : on peut en lire la fiche
 * et voir sa bande-annonce, pas le lancer.
 */
data class Suggestion(
    val tmdbId: Int,
    val mediaType: String,          // "movie" ou "tv"
    val title: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String,
    val date: String,               // AAAA-MM-JJ
    val rating: Float
) {
    val key: String get() = "$mediaType:$tmdbId"
    val year: Int get() = date.take(4).toIntOrNull() ?: 0
    val isSeries: Boolean get() = mediaType == "tv"
}

/** Les chiffres de la page Statistiques. */
/** Un genre de la bibliothèque, tel que l'accueil le présente : son nom, son nombre de titres, une image. */
data class GenreShelf(
    val label: String,
    val count: Int,
    val backdropUrl: String
)

data class LibraryStats(
    val files: Int = 0,
    val totalBytes: Long = 0,
    val watched: Int = 0,
    val inProgress: Int = 0,
    val watchedMs: Long = 0,
    /** Identifiant de genre TMDB → nombre de titres vus. */
    val genres: List<Pair<Int, Int>> = emptyList(),
    /** Mois (« 2026-11 ») → nombre de titres regardés, du plus ancien au plus récent. */
    val months: List<Pair<String, Int>> = emptyList(),
    val directors: List<Pair<String, Int>> = emptyList()
)
