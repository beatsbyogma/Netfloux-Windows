package com.cinetv.data.prefs

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinetv.ui.theme.AccentTheme
import com.cinetv.ui.theme.Grid
import com.cinetv.ui.theme.Tiles

/** Espace entre les vignettes de la grille des dossiers. */
enum class GridGap(val label: String, val size: Dp) {
    SERRE("Serré", 8.dp),
    NORMAL("Normal", 15.dp),
    AERE("Aéré", 24.dp),
    LARGE("Très aéré", 34.dp);

    fun next(): GridGap = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): GridGap = entries.firstOrNull { it.name == name } ?: NORMAL
    }
}

/** Ordre d'affichage dans un dossier. */
enum class SortOrder(val label: String) {
    NAME("Nom"),
    RECENT("Ajout récent"),
    YEAR("Année"),
    RATING("Note");

    fun next(): SortOrder = entries[(ordinal + 1) % entries.size]

    companion object {
        fun parse(name: String?): SortOrder = entries.firstOrNull { it.name == name } ?: NAME
    }
}

/**
 * Un dossier présenté sur l'accueil comme un disque à part entière :
 * son propre nom, son propre thème, et il n'apparaît plus dans son disque d'origine.
 */
data class VirtualDrive(
    val folderId: String,
    val volumeId: String,
    val label: String,
    val theme: com.cinetv.data.model.DriveTheme,
    /** Chemin du dossier dans son disque : tout ce qui est en dessous lui appartient. */
    val path: String = ""
) {
    /** L'identifiant sous lequel il se présente parmi les disques. */
    val cardId: String get() = PREFIX + folderId

    companion object {
        const val PREFIX = "virtual:"
    }
}

/**
 * Un dossier caché : il ne se montre plus nulle part (disque, accueil, recherche,
 * écran de veille) et ses fichiers ne sont plus cherchés sur TMDB.
 */
data class HiddenFolder(
    val folderId: String,
    val volumeId: String,
    val name: String,
    /** Chemin du dossier dans son disque : tout ce qui est en dessous est caché avec lui. */
    val path: String
)

/** Les rubriques de l'accueil, dans leur ordre d'origine, et leur nom à l'écran. */
/** Taille des cartes d'une rubrique de l'accueil. */
enum class HomeSize(val label: String, val scale: Float) {
    PETITE("Petite", 0.82f),
    NORMALE("Normale", 1f),
    GRANDE("Grande", 1.25f),
    TRES_GRANDE("Très grande", 1.55f);

    fun next(): HomeSize = entries[(ordinal + 1) % entries.size]
}

/** Les rubriques dont la taille se règle ; la bannière, les disques et le réseau gardent la leur. */
val HOME_RESIZABLE = setOf("list", "resume", "recent", "genres", "towatch", "releases")

val HOME_SECTIONS = listOf("drives", "network", "list", "resume", "recent", "genres", "towatch", "releases")

fun homeSectionLabel(key: String): String = when (key) {
    "drives" -> "Vos disques"
    "network" -> "Réseau"
    "list" -> "Ma liste"
    "resume" -> "Reprendre"
    "recent" -> "Récemment ajoutés"
    "genres" -> "Genres"
    "towatch" -> "À voir"
    "releases" -> "Sorties récentes"
    else -> key
}

/** Préférences locales de l'application. */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("netfloux_settings", Context.MODE_PRIVATE)

    /** Grille des dossiers : nombre de vignettes par ligne. */
    var gridColumns: Int
        get() = prefs.getInt("grid_columns", Grid.columnsDefault).coerceIn(Grid.columnsMin, Grid.columnsMax)
        set(value) { prefs.edit().putInt("grid_columns", value).apply() }

    /** Grille des dossiers : espace entre les vignettes. */
    var gridGap: GridGap
        get() = GridGap.parse(prefs.getString("grid_gap", null))
        set(value) { prefs.edit().putString("grid_gap", value.name).apply() }

    /** Couleur d'accent de toute l'application. */
    var accentTheme: AccentTheme
        get() = AccentTheme.parse(prefs.getString("accent_theme", null))
        set(value) { prefs.edit().putString("accent_theme", value.name).apply() }

    /** Accueil : ordre des rubriques sous la bannière, et celles qu'on a masquées. */
    var homeOrder: List<String>
        get() {
            val saved = prefs.getString("home_order", "").orEmpty().split(',').filter { it in HOME_SECTIONS }
            // Une rubrique apparue depuis (mise à jour de l'app) se range à la fin
            return (saved + HOME_SECTIONS).distinct()
        }
        set(value) { prefs.edit().putString("home_order", value.joinToString(",")).apply() }

    var homeHidden: Set<String>
        get() = prefs.getString("home_hidden", "").orEmpty().split(',').filter { it in HOME_SECTIONS }.toSet()
        set(value) { prefs.edit().putString("home_hidden", value.joinToString(",")).apply() }

    /** Les propositions de TMDB (« À voir », « Sorties récentes »), gardées d'un jour sur l'autre. */
    var discoverJson: String
        get() = prefs.getString("discover_json_v2", "").orEmpty()
        set(value) { prefs.edit().putString("discover_json_v2", value).apply() }

    /** Cartes de « À voir » et « Sorties récentes » : jaquettes verticales (par défaut) ou cartes horizontales. */
    var discoverPosters: Boolean
        get() = prefs.getBoolean("discover_posters", true)
        set(value) { prefs.edit().putBoolean("discover_posters", value).apply() }

    /** Instant de la dernière récupération de ces propositions. */
    var discoverAt: Long
        get() = prefs.getLong("discover_at", 0L)
        set(value) { prefs.edit().putLong("discover_at", value).apply() }

    /** Propositions écartées avec « Pas intéressé » : elles ne reviennent plus. */
    var discoverDismissed: Set<String>
        get() = prefs.getString("discover_dismissed", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        set(value) { prefs.edit().putString("discover_dismissed", value.toList().takeLast(600).joinToString(",")).apply() }

    /** Taille choisie pour chaque rubrique de l'accueil ; absente = normale. */
    var homeSizes: Map<String, HomeSize>
        get() = prefs.getString("home_sizes", "").orEmpty().split(',').mapNotNull { entry ->
            val key = entry.substringBefore('=')
            val size = HomeSize.entries.firstOrNull { it.name == entry.substringAfter('=', "") }
            if (key in HOME_RESIZABLE && size != null) key to size else null
        }.toMap()
        set(value) {
            prefs.edit().putString("home_sizes", value.entries.joinToString(",") { "${it.key}=${it.value.name}" }).apply()
        }

    /** Partages réseau rangés parmi « Vos disques » plutôt que dans « Réseau ». */
    var sharesAsDrives: Set<String>
        get() = prefs.getStringSet("shares_as_drives", emptySet()).orEmpty().toSet()
        set(value) { prefs.edit().putStringSet("shares_as_drives", value).apply() }

    /** Sortie du son : 0 = automatique (par défaut), 1 = toujours décodée par l'application, 2 = toujours directe. */
    var audioMode: Int
        get() = prefs.getInt("audio_mode", 0)
        set(value) { prefs.edit().putInt("audio_mode", value).apply() }

    /** Décalage du son quand il part directement vers l'ampli : ce chemin n'a pas le même retard que le son décodé. */
    var audioOffsetDirectMs: Long
        get() = prefs.getLong("audio_offset_direct_ms", 0L)
        set(value) { prefs.edit().putLong("audio_offset_direct_ms", value).apply() }

    /** Dossiers présentés comme des disques sur l'accueil. */
    var virtualDrives: List<VirtualDrive>
        get() = runCatching {
            val array = org.json.JSONArray(prefs.getString("virtual_drives", "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                VirtualDrive(
                    folderId = o.getString("folderId"),
                    volumeId = o.getString("volumeId"),
                    label = o.getString("label"),
                    theme = runCatching { com.cinetv.data.model.DriveTheme.valueOf(o.getString("theme")) }
                        .getOrDefault(com.cinetv.data.model.DriveTheme.AUTRE),
                    path = o.optString("path")
                )
            }
        }.getOrDefault(emptyList())
        set(value) {
            val array = org.json.JSONArray()
            value.forEach { v ->
                array.put(
                    org.json.JSONObject()
                        .put("folderId", v.folderId).put("volumeId", v.volumeId)
                        .put("label", v.label).put("theme", v.theme.name).put("path", v.path)
                )
            }
            prefs.edit().putString("virtual_drives", array.toString()).apply()
        }

    /** Version de la méthode de recherche des fiches déjà appliquée à la bibliothèque. */
    var metadataPass: Int
        get() = prefs.getInt("metadata_pass", 1)
        set(value) { prefs.edit().putInt("metadata_pass", value).apply() }

    /** Dossiers cachés de la bibliothèque. */
    var hiddenFolders: List<HiddenFolder>
        get() = runCatching {
            val array = org.json.JSONArray(prefs.getString("hidden_folders", "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                HiddenFolder(o.getString("folderId"), o.getString("volumeId"), o.optString("name"), o.optString("path"))
            }
        }.getOrDefault(emptyList())
        set(value) {
            val array = org.json.JSONArray()
            value.forEach { h ->
                array.put(
                    org.json.JSONObject().put("folderId", h.folderId).put("volumeId", h.volumeId)
                        .put("name", h.name).put("path", h.path)
                )
            }
            prefs.edit().putString("hidden_folders", array.toString()).apply()
        }

    /**
     * Lecteur : envoyer le son encodé (Dolby, DTS) tel quel à l'ampli — le réglage par défaut.
     * Faux : la TV le décode elle-même (ses décodeurs se figent parfois après une avance rapide).
     */
    var audioDirect: Boolean
        get() = prefs.getBoolean("audio_direct_v2", true)
        set(value) { prefs.edit().putBoolean("audio_direct_v2", value).apply() }

    /** Lecteur : décalage du son réglé une fois pour toutes (en millisecondes), repris à chaque film. */
    var audioOffsetMs: Long
        get() = prefs.getLong("audio_offset_ms", 0L)
        set(value) { prefs.edit().putLong("audio_offset_ms", value).apply() }

    /** « Récemment ajoutés » vidé à cette date : seuls les ajouts postérieurs s'y montrent. */
    var recentClearedAt: Long
        get() = prefs.getLong("recent_cleared_at", 0L)
        set(value) { prefs.edit().putLong("recent_cleared_at", value).apply() }

    var sortOrder: SortOrder
        get() = SortOrder.parse(prefs.getString(KEY_SORT, null))
        set(value) { prefs.edit().putString(KEY_SORT, value.name).apply() }

    /** Masquer les titres déjà vus. */
    var hideWatched: Boolean
        get() = prefs.getBoolean(KEY_HIDE_WATCHED, false)
        set(value) { prefs.edit().putBoolean(KEY_HIDE_WATCHED, value).apply() }

    /** Langue audio retenue d'une lecture à l'autre ; vide = pas de préférence. */
    var preferredAudioLanguage: String
        get() = prefs.getString(KEY_AUDIO_LANG, "fr").orEmpty()
        set(value) { prefs.edit().putString(KEY_AUDIO_LANG, value).apply() }

    var subtitlesEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUBS_ON, false)
        set(value) { prefs.edit().putBoolean(KEY_SUBS_ON, value).apply() }

    var preferredSubtitleLanguage: String
        get() = prefs.getString(KEY_SUBS_LANG, "fr").orEmpty()
        set(value) { prefs.edit().putString(KEY_SUBS_LANG, value).apply() }

    /** Enchaînement automatique sur le fichier suivant du dossier. */
    var autoPlayNext: Boolean
        get() = prefs.getBoolean(KEY_AUTONEXT, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTONEXT, value).apply() }

    /** Clé d'accès OpenSubtitles (sous-titres en ligne), saisie dans les Paramètres. */
    var subtitlesKey: String
        get() = prefs.getString("opensubtitles_key", "").orEmpty()
        set(value) { prefs.edit().putString("opensubtitles_key", value.trim()).apply() }

    /** Clé TMDB saisie dans les Paramètres ; vide = celle fournie avec l'app, s'il y en a une. */
    var tmdbKey: String
        get() = prefs.getString("tmdb_key", "").orEmpty()
        set(value) { prefs.edit().putString("tmdb_key", value.trim()).apply() }

    /** Apparence des sous-titres : taille, couleur et fond (rangs dans les listes du lecteur). */
    var subtitleSize: Int
        get() = prefs.getInt("subtitle_size", 1)
        set(value) { prefs.edit().putInt("subtitle_size", value).apply() }

    var subtitleColor: Int
        get() = prefs.getInt("subtitle_color", 0)
        set(value) { prefs.edit().putInt("subtitle_color", value).apply() }

    var subtitleBackground: Int
        get() = prefs.getInt("subtitle_background", 0)
        set(value) { prefs.edit().putInt("subtitle_background", value).apply() }

    /** Délai d'inactivité avant l'économiseur, en minutes ; 0 = désactivé. */
    var screensaverMinutes: Int
        get() = prefs.getInt(KEY_SCREENSAVER, 5)
        set(value) { prefs.edit().putInt(KEY_SCREENSAVER, value).apply() }

    /**
     * Les réglages d'apparence et d'organisation, pour la sauvegarde de l'app.
     * Les clés d'accès (TMDB, sous-titres) n'en font volontairement pas partie.
     */
    fun exportJson(): org.json.JSONObject = org.json.JSONObject()
        .put("accent", accentTheme.name)
        .put("gridColumns", gridColumns)
        .put("gridGap", gridGap.name)
        .put("sort", sortOrder.name)
        .put("hideWatched", hideWatched)
        .put("homeOrder", homeOrder.joinToString(","))
        .put("homeHidden", homeHidden.joinToString(","))
        .put("virtualDrives", prefs.getString("virtual_drives", "[]"))
        .put("hiddenFolders", prefs.getString("hidden_folders", "[]"))
        .put("audioLanguage", preferredAudioLanguage)
        .put("subtitlesEnabled", subtitlesEnabled)
        .put("subtitleLanguage", preferredSubtitleLanguage)
        .put("autoPlayNext", autoPlayNext)
        .put("screensaverMinutes", screensaverMinutes)
        .put("subtitleSize", subtitleSize)
        .put("subtitleColor", subtitleColor)
        .put("subtitleBackground", subtitleBackground)

    /** Remet les réglages d'une sauvegarde ; ce qui n'y figure pas garde sa valeur actuelle. */
    fun importJson(o: org.json.JSONObject) {
        if (o.has("accent")) accentTheme = AccentTheme.parse(o.optString("accent"))
        if (o.has("gridColumns")) gridColumns = o.optInt("gridColumns", gridColumns)
        if (o.has("gridGap")) gridGap = GridGap.parse(o.optString("gridGap"))
        if (o.has("sort")) sortOrder = SortOrder.parse(o.optString("sort"))
        if (o.has("hideWatched")) hideWatched = o.optBoolean("hideWatched")
        if (o.has("homeOrder")) homeOrder = o.optString("homeOrder").split(',').filter { it in HOME_SECTIONS }
        if (o.has("homeHidden")) homeHidden = o.optString("homeHidden").split(',').filter { it in HOME_SECTIONS }.toSet()
        if (o.has("virtualDrives")) prefs.edit().putString("virtual_drives", o.optString("virtualDrives", "[]")).apply()
        if (o.has("hiddenFolders")) prefs.edit().putString("hidden_folders", o.optString("hiddenFolders", "[]")).apply()
        if (o.has("audioLanguage")) preferredAudioLanguage = o.optString("audioLanguage")
        if (o.has("subtitlesEnabled")) subtitlesEnabled = o.optBoolean("subtitlesEnabled")
        if (o.has("subtitleLanguage")) preferredSubtitleLanguage = o.optString("subtitleLanguage")
        if (o.has("autoPlayNext")) autoPlayNext = o.optBoolean("autoPlayNext", true)
        if (o.has("screensaverMinutes")) screensaverMinutes = o.optInt("screensaverMinutes", screensaverMinutes)
        if (o.has("subtitleSize")) subtitleSize = o.optInt("subtitleSize", subtitleSize)
        if (o.has("subtitleColor")) subtitleColor = o.optInt("subtitleColor", subtitleColor)
        if (o.has("subtitleBackground")) subtitleBackground = o.optInt("subtitleBackground", subtitleBackground)
    }

    private companion object {
        const val KEY_SORT = "sort_order"
        const val KEY_HIDE_WATCHED = "hide_watched"
        const val KEY_AUDIO_LANG = "audio_language"
        const val KEY_SUBS_ON = "subtitles_enabled"
        const val KEY_SUBS_LANG = "subtitles_language"
        const val KEY_AUTONEXT = "auto_play_next"
        const val KEY_SCREENSAVER = "screensaver_minutes"
    }
}
