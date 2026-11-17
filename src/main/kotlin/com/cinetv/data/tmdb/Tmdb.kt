package com.cinetv.data.tmdb

import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client TMDB minimal, bâti sur le client HTTP du framework Android.
 *
 * Trois requêtes GET suffisent à cette app ; une bibliothèque réseau tierce
 * n'apporterait rien ici et ajouterait des versions à maintenir.
 */
object Tmdb {

    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
    private const val TAG = "Tmdb"

    /**
     * Clé saisie dans les Paramètres de l'application. Elle passe avant celle
     * éventuellement fournie avec l'app (Secrets), ce qui permet de donner
     * l'APK à quelqu'un qui utilisera la sienne.
     */
    @Volatile var userKey: String = ""

    private val key: String get() = userKey.trim().ifBlank { Secrets.TMDB_API_KEY }

    /** TMDB délivre deux formes de clé : la courte (dans l'adresse) et le jeton long (en en-tête). */
    private val isLongToken: Boolean get() = key.length > 40

    val isConfigured: Boolean get() = key.isNotBlank()

    /** Vrai si l'app tourne avec une clé saisie par l'utilisateur. */
    val usesOwnKey: Boolean get() = userKey.isNotBlank()

    /** Interroge TMDB une fois pour savoir si la clé en place est acceptée. */
    fun keyWorks(): Boolean = get(buildUrl("/configuration") {}) != null

    data class Hit(
        val tmdbId: Int,
        val title: String,
        val overview: String,
        val posterPath: String?,
        val backdropPath: String?,
        val date: String,
        val rating: Float,
        val mediaType: String = "",     // "movie" ou "tv"
        /** Genres TMDB de la fiche ; renseignés seulement par les listes (recommandations, sorties). */
        val genreIds: List<Int> = emptyList(),
        /** Titre original ; renseigné seulement par les listes. */
        val originalTitle: String = ""
    ) {
        val year: Int get() = date.take(4).toIntOrNull() ?: 0
    }

    fun searchMovie(query: String, year: Int?): Hit? {
        val url = buildUrl("/search/movie") {
            appendQueryParameter("query", query)
            if (year != null && year > 1900) appendQueryParameter("year", year.toString())
            appendQueryParameter("include_adult", "false")
        }
        return firstHit(url, "movie")
    }

    fun searchTv(query: String): Hit? =
        firstHit(buildUrl("/search/tv") { appendQueryParameter("query", query) }, "tv")

    fun searchMulti(query: String): Hit? =
        firstHit(buildUrl("/search/multi") {
            appendQueryParameter("query", query)
            appendQueryParameter("include_adult", "false")
        })

    /** Recherche large, tous types confondus, pour la correction manuelle. */
    fun searchAll(query: String, limit: Int = 20): List<Hit> {
        if (query.isBlank()) return emptyList()
        val url = buildUrl("/search/multi") {
            appendQueryParameter("query", query)
            appendQueryParameter("include_adult", "false")
        }
        val body = get(url) ?: return emptyList()
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    if (size >= limit) break
                    val o = results.optJSONObject(i) ?: continue
                    if (o.optString("media_type") == "person") continue
                    val title = o.optString("title").ifBlank { o.optString("name") }
                    if (title.isBlank()) continue
                    add(
                        Hit(
                            tmdbId = o.optInt("id"),
                            title = title,
                            overview = o.optString("overview"),
                            posterPath = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
                            backdropPath = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
                            date = o.optString("release_date").ifBlank { o.optString("first_air_date") },
                            rating = o.optDouble("vote_average", 0.0).toFloat(),
                            mediaType = o.optString("media_type")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Recherche illisible : ${e.message}")
            emptyList()
        }
    }

    // ------------------------------------------------------------------
    // Listes : ce que TMDB propose, pour les rubriques « À voir » et « Sorties récentes »

    /** Lit une liste TMDB de films ("movie") ou de séries ("tv"). Vide si la requête échoue. */
    private fun list(path: String, type: String, extra: Uri.Builder.() -> Unit = {}): List<Hit> {
        val body = get(buildUrl(path, extra)) ?: return emptyList()
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until results.length()) {
                    val o = results.optJSONObject(i) ?: continue
                    val title = o.optString("title").ifBlank { o.optString("name") }
                    val id = o.optInt("id")
                    if (title.isBlank() || id <= 0) continue
                    add(
                        Hit(
                            tmdbId = id,
                            title = title,
                            overview = o.optString("overview"),
                            posterPath = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
                            backdropPath = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
                            date = o.optString("release_date").ifBlank { o.optString("first_air_date") },
                            rating = o.optDouble("vote_average", 0.0).toFloat(),
                            mediaType = type,
                            genreIds = o.optJSONArray("genre_ids")?.let { ids ->
                                (0 until ids.length()).map { ids.optInt(it) }
                            }.orEmpty(),
                            originalTitle = o.optString("original_title").ifBlank { o.optString("original_name") }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Liste illisible : ${e.message}")
            emptyList()
        }
    }

    // ------------------------------------------------------------------
    // Recherche tolérante : quand le nom du fichier contient une faute

    /** Nombre de modifications (lettre changée, ajoutée ou retirée) pour passer d'un texte à l'autre. */
    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i
            for (j in 1..b.length) {
                current[j] = minOf(
                    previous[j] + 1, current[j - 1] + 1,
                    previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
            previous = current
        }
        return previous[b.length]
    }

    /** Ressemblance lettre à lettre entre deux titres, de 0 à 1. */
    private fun likeness(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        return 1f - editDistance(a, b).toFloat() / maxOf(a.length, b.length)
    }

    /**
     * Dernier recours quand la recherche habituelle ne trouve rien : le titre contient
     * peut-être une faute (« Games of Thrones »). On réessaie avec de petites variantes —
     * un « s » en moins ou en plus, un mot retiré, le début seul — puis, parmi tout ce que
     * TMDB renvoie, on garde le titre qui ressemble le plus à celui du fichier, à condition
     * qu'il lui ressemble vraiment : une faute de frappe passe, un autre film non.
     *
     * [series] : vrai pour ne chercher que des séries (épisodes, dossiers de série).
     */
    fun searchLoose(title: String, series: Boolean): Hit? {
        val wanted = plainTitle(title)
        val words = wanted.split(' ').filter { it.isNotEmpty() }
        if (wanted.length < 4 || words.isEmpty()) return null

        val variants = LinkedHashSet<String>()
        words.forEachIndexed { index, word ->
            if (word.length < 3) return@forEachIndexed
            // « games » → « game », « throne » → « thrones »
            val swapped = if (word.endsWith("s")) word.dropLast(1) else word + "s"
            variants += words.toMutableList().also { it[index] = swapped }.joinToString(" ")
        }
        if (words.size >= 3) {
            words.indices.forEach { skip -> variants += words.filterIndexed { i, _ -> i != skip }.joinToString(" ") }
            variants += words.take(words.size - 1).joinToString(" ")
        }
        variants -= wanted

        var best: Hit? = null
        var bestScore = 0f
        val types = if (series) listOf("tv") else listOf("movie", "tv")
        for (variant in variants.take(LOOSE_VARIANTS)) {
            for (type in types) {
                val found = list("/search/$type", type) {
                    appendQueryParameter("query", variant)
                    appendQueryParameter("include_adult", "false")
                }
                for (hit in found.take(6)) {
                    val score = maxOf(likeness(wanted, plainTitle(hit.title)), likeness(wanted, plainTitle(hit.originalTitle)))
                    if (score > bestScore) { bestScore = score; best = hit }
                }
            }
            if (bestScore >= 0.93f) break
        }
        return best?.takeIf { bestScore >= LOOSE_MIN_LIKENESS }
    }

    /** Variantes essayées au plus pour un titre, et ressemblance exigée pour accepter une fiche. */
    private const val LOOSE_VARIANTS = 7
    private const val LOOSE_MIN_LIKENESS = 0.84f

    /** Les titres que TMDB rapproche d'un film ou d'une série. */
    fun recommendations(tmdbId: Int, mediaType: String): List<Hit> {
        if (tmdbId <= 0) return emptyList()
        val type = if (mediaType == "tv") "tv" else "movie"
        return list("/$type/$tmdbId/recommendations", type)
    }

    /** Les films ou séries les mieux notés de TMDB : sert de complément quand la bibliothèque est petite. */
    fun topRated(mediaType: String): List<Hit> {
        val type = if (mediaType == "tv") "tv" else "movie"
        return list("/$type/top_rated", type) { appendQueryParameter("region", "FR") }
    }

    /** Films sortis entre deux dates (AAAA-MM-JJ), les plus suivis d'abord. */
    fun moviesReleased(from: String, to: String, page: Int = 1): List<Hit> =
        list("/discover/movie", "movie") {
            appendQueryParameter("primary_release_date.gte", from)
            appendQueryParameter("primary_release_date.lte", to)
            appendQueryParameter("sort_by", "popularity.desc")
            appendQueryParameter("vote_count.gte", "40")
            appendQueryParameter("include_adult", "false")
            appendQueryParameter("page", page.toString())
        }

    /** Séries apparues entre deux dates, les plus suivies d'abord ; ni débats, ni télé-réalité, ni feuilletons. */
    fun showsReleased(from: String, to: String, page: Int = 1): List<Hit> =
        list("/discover/tv", "tv") {
            appendQueryParameter("first_air_date.gte", from)
            appendQueryParameter("first_air_date.lte", to)
            appendQueryParameter("sort_by", "popularity.desc")
            appendQueryParameter("vote_count.gte", "15")
            appendQueryParameter("without_genres", "10763,10764,10766,10767")
            appendQueryParameter("include_adult", "false")
            appendQueryParameter("page", page.toString())
        }

    /**
     * Films ou séries sortis entre deux dates, bien notés et suivis, pour la bannière de l'accueil.
     * Ni films d'horreur, ni animation ; côté séries, ni débats, ni télé-réalité, ni feuilletons.
     */
    fun acclaimed(mediaType: String, from: String, to: String, page: Int = 1): List<Hit> {
        val tv = mediaType == "tv"
        return list(if (tv) "/discover/tv" else "/discover/movie", if (tv) "tv" else "movie") {
            appendQueryParameter(if (tv) "first_air_date.gte" else "primary_release_date.gte", from)
            appendQueryParameter(if (tv) "first_air_date.lte" else "primary_release_date.lte", to)
            appendQueryParameter("sort_by", "popularity.desc")
            appendQueryParameter("vote_average.gte", "6.5")
            appendQueryParameter("vote_count.gte", if (tv) "40" else "120")
            appendQueryParameter("without_genres", if (tv) "16,10763,10764,10766,10767" else "27,16")
            appendQueryParameter("include_adult", "false")
            appendQueryParameter("page", page.toString())
        }
    }

    /** Têtes d'affiche et réalisation d'une fiche. */
    data class Credits(
        val actors: List<String>,
        val director: String,
        val collectionId: Int = 0,
        val collectionName: String = "",
        val genres: List<Int> = emptyList(),
        /** Logo officiel du titre (image à fond transparent), ou vide : celui qu'on prend sans nom de fichier. */
        val logoUrl: String = "",
        /** Le logo en français et le logo en version originale (anglais), chacun vide s'il n'existe pas. */
        val logoFrench: String = "",
        val logoOriginal: String = "",
        /** Le titre d'exploitation en France et le titre original. */
        val frenchTitle: String = "",
        val originalTitle: String = "",
        /** Faute d'indice dans le nom du fichier : français pour un film français, l'animation, un titre sorti sous un nom français. */
        val frenchByDefault: Boolean = false
    ) {
        /**
         * Le logo qui s'accorde avec le nom du fichier : si le fichier porte le titre français,
         * le logo français ; s'il porte le titre original, le logo original. Quand le nom ne
         * tranche pas (les deux titres sont identiques, ou il ne ressemble à aucun), on suit
         * [frenchByDefault]. Si le logo voulu n'existe pas, on prend l'autre — marqué, pour que
         * l'écran écrive le titre du fichier dessous.
         */
        fun logoFor(fileTitle: String): String {
            val fr = plainTitle(frenchTitle)
            val original = plainTitle(originalTitle)
            val file = plainTitle(fileTitle)
            val same = fr == original
            val wantFrench = when {
                same || file.isEmpty() -> frenchByDefault
                else -> {
                    val towardFrench = closeness(file, fr)
                    val towardOriginal = closeness(file, original)
                    if (towardFrench == towardOriginal) frenchByDefault else towardFrench > towardOriginal
                }
            }
            val wanted = if (wantFrench) logoFrench else logoOriginal
            val other = if (wantFrench) logoOriginal else logoFrench
            return when {
                wanted.isNotBlank() -> wanted
                other.isBlank() -> ""
                same -> other
                else -> other + LOGO_FOREIGN_MARK
            }
        }
    }

    /** Un titre réduit à ses lettres et chiffres, sans accents ni majuscules, mots séparés d'une espace. */
    private fun plainTitle(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("[^a-z0-9]+"), " ").trim()

    /** Ressemblance entre deux titres, de 0 à 1 : la part de mots qu'ils ont en commun. */
    private fun closeness(a: String, b: String): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        if (a == b) return 1f
        val wordsA = a.split(' ').toSet()
        val wordsB = b.split(' ').toSet()
        val shared = wordsA.intersect(wordsB).size.toFloat()
        return shared / (wordsA.size + wordsB.size - shared)
    }

    /**
     * Identifiant YouTube de la bande-annonce d'un film ou d'une série :
     * en français si elle existe, sinon en anglais. Null s'il n'y en a pas.
     */
    fun trailerKey(tmdbId: Int, mediaType: String): String? {
        if (tmdbId <= 0) return null
        val url = buildUrl(if (mediaType == "tv") "/tv/$tmdbId/videos" else "/movie/$tmdbId/videos") {
            appendQueryParameter("include_video_language", "fr,en")
        }
        val body = get(url) ?: return null
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return null
            var best: String? = null
            var bestScore = -1
            for (i in 0 until results.length()) {
                val video = results.optJSONObject(i) ?: continue
                if (video.optString("site") != "YouTube") continue
                val kind = video.optString("type")
                if (kind != "Trailer" && kind != "Teaser") continue
                val score = (if (kind == "Trailer") 4 else 0) +
                    (if (video.optString("iso_639_1") == "fr") 2 else 0) +
                    (if (video.optBoolean("official")) 1 else 0)
                if (score > bestScore) { bestScore = score; best = video.optString("key").takeIf { it.isNotBlank() } }
            }
            best
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Distribution d'un film ou d'une série. Pour une série, « réalisation »
     * désigne ses créateurs. Renvoie null si la fiche est introuvable.
     */
    fun credits(tmdbId: Int, mediaType: String, maxActors: Int = 4): Credits? {
        if (tmdbId <= 0) return null
        val tv = mediaType == "tv"
        val url = buildUrl(if (tv) "/tv/$tmdbId" else "/movie/$tmdbId") {
            appendQueryParameter("append_to_response", "credits,images")
            // Logos : français d'abord, puis anglais, puis ceux sans langue
            appendQueryParameter("include_image_language", "fr,en,null")
        }
        val body = get(url) ?: return null
        return try {
            val root = JSONObject(body)
            val credits = root.optJSONObject("credits")
            val cast = credits?.optJSONArray("cast")
            val actors = buildList {
                if (cast != null) for (i in 0 until cast.length()) {
                    if (size >= maxActors) break
                    cast.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            val director = if (tv) {
                val creators = root.optJSONArray("created_by")
                buildList {
                    if (creators != null) for (i in 0 until creators.length()) {
                        if (size >= 2) break
                        creators.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }.joinToString(", ")
            } else {
                val crew = credits?.optJSONArray("crew")
                buildList {
                    if (crew != null) for (i in 0 until crew.length()) {
                        val member = crew.optJSONObject(i) ?: continue
                        if (member.optString("job") == "Director" && size < 2) {
                            member.optString("name").takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }
                }.joinToString(", ")
            }
            val saga = if (tv) null else root.optJSONObject("belongs_to_collection")
            val logos = root.optJSONObject("images")?.optJSONArray("logos")
            val french = bestLogo(logos, "fr")
            val originalLogo = bestLogo(logos, "en")
            val frenchTitle = root.optString(if (tv) "name" else "title")
            val originalTitle = root.optString(if (tv) "original_name" else "original_title")
            // Sans indice dans le nom du fichier : français pour un film français, pour l'animation,
            // et pour un titre sorti en France sous un nom français.
            val frenchByDefault = root.optString("original_language") == "fr" ||
                root.optJSONArray("genres")?.let { list ->
                    (0 until list.length()).any { list.optJSONObject(it)?.optInt("id") == 16 }
                } == true ||
                plainTitle(frenchTitle).let { it.isNotEmpty() && it != plainTitle(originalTitle) }
            Credits(
                actors, director,
                collectionId = saga?.optInt("id", 0) ?: 0,
                collectionName = saga?.optString("name").orEmpty(),
                genres = root.optJSONArray("genres")?.let { list ->
                    (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optInt("id", 0)?.takeIf { id -> id > 0 } }
                }.orEmpty(),
                logoUrl = if (frenchByDefault) french.ifBlank { originalLogo } else originalLogo.ifBlank { french },
                logoFrench = french,
                logoOriginal = originalLogo,
                frenchTitle = frenchTitle,
                originalTitle = originalTitle,
                frenchByDefault = frenchByDefault
            )
        } catch (e: Exception) {
            Log.w(TAG, "Distribution illisible : ${e.message}")
            null
        }
    }

    /**
     * Le meilleur logo d'une fiche dans une langue : "fr" pour le français, "en" pour la version
     * originale (anglais, ou à défaut un logo sans langue). Vide s'il n'y en a pas. Parmi les
     * mieux notés, on garde celui qui a ses couleurs plutôt que sa version blanche. Les logos
     * vectoriels sont écartés, l'application ne sait afficher que des images.
     */
    private fun bestLogo(logos: org.json.JSONArray?, language: String): String {
        if (logos == null) return ""
        class Candidate(val path: String, val rank: Int, val votes: Double)
        val all = buildList {
            for (i in 0 until logos.length()) {
                val logo = logos.optJSONObject(i) ?: continue
                val path = logo.optString("file_path")
                if (path.isBlank() || path.endsWith(".svg", ignoreCase = true)) continue
                val its = if (logo.isNull("iso_639_1")) "" else logo.optString("iso_639_1")
                val rank = when {
                    its == language -> 2
                    language == "en" && its.isEmpty() -> 1
                    else -> continue
                }
                add(Candidate(path, rank, logo.optDouble("vote_average", 0.0)))
            }
        }.sortedWith(compareByDescending<Candidate> { it.rank }.thenByDescending { it.votes })
        val first = all.firstOrNull() ?: return ""
        val tier = all.filter { it.rank == first.rank }.take(LOGO_TRIES)
        val chosen = if (tier.size < 2) first else {
            tier.map { it to colourShare(it.path) }
                .filter { it.second >= LOGO_MIN_COLOUR }
                .maxByOrNull { it.second }?.first ?: first
        }
        return poster(chosen.path, "w500")
    }

    /** Nombre de logos comparés pour trouver une version en couleurs. */
    private const val LOGO_TRIES = 3
    /** Part de pixels colorés à partir de laquelle un logo « a ses couleurs ». */
    private const val LOGO_MIN_COLOUR = 0.12f
    /** Marque posée à la fin de l'adresse d'un logo qui n'est pas dans la langue du titre français. */
    const val LOGO_FOREIGN_MARK = "#vo"

    /** Part des pixels visibles d'un logo qui portent une vraie couleur (ni blanc, ni gris, ni noir). */
    private fun colourShare(path: String): Float {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("${IMAGE_BASE}w92$path").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            val bitmap = connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return 0f
            val hsv = FloatArray(3)
            var visible = 0
            var vivid = 0
            for (y in 0 until bitmap.height step 2) {
                for (x in 0 until bitmap.width step 2) {
                    val pixel = bitmap.getPixel(x, y)
                    if ((pixel ushr 24) <= 96) continue
                    visible++
                    android.graphics.Color.colorToHSV(pixel, hsv)
                    if (hsv[1] > 0.30f && hsv[2] > 0.25f) vivid++
                }
            }
            bitmap.recycle()
            if (visible == 0) 0f else vivid.toFloat() / visible
        } catch (e: Exception) {
            0f
        } finally {
            connection?.disconnect()
        }
    }

    fun poster(path: String?, size: String = "w500"): String =
        if (path.isNullOrBlank()) "" else "$IMAGE_BASE$size$path"

    fun backdrop(path: String?, size: String = "w1280"): String =
        if (path.isNullOrBlank()) "" else "$IMAGE_BASE$size$path"

    // ------------------------------------------------------------------

    private fun buildUrl(path: String, extra: Uri.Builder.() -> Unit): String =
        Uri.parse(BASE + path).buildUpon()
            .apply { if (!isLongToken) appendQueryParameter("api_key", key) }
            .appendQueryParameter("language", "fr-FR")
            .apply(extra)
            .build()
            .toString()

    private fun firstHit(url: String, knownType: String = ""): Hit? {
        val body = get(url) ?: return null
        return try {
            val results = JSONObject(body).optJSONArray("results") ?: return null
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val type = o.optString("media_type", "")
                if (type == "person") continue
                return Hit(
                    tmdbId = o.optInt("id"),
                    title = o.optString("title").ifBlank { o.optString("name") },
                    overview = o.optString("overview"),
                    posterPath = o.optString("poster_path").takeIf { it.isNotBlank() && it != "null" },
                    backdropPath = o.optString("backdrop_path").takeIf { it.isNotBlank() && it != "null" },
                    date = o.optString("release_date").ifBlank { o.optString("first_air_date") },
                    rating = o.optDouble("vote_average", 0.0).toFloat(),
                    mediaType = knownType.ifBlank { type }
                )
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Réponse illisible : ${e.message}")
            null
        }
    }

    /** Appelé depuis un contexte d'arrière-plan uniquement. */
    /**
     * Vrai si la dernière requête a échoué faute de réseau (et non parce que
     * TMDB a répondu « inconnu ») : permet de ne pas confondre les deux.
     */
    @Volatile var lastFailureWasNetwork = false
        private set

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                if (isLongToken) setRequestProperty("Authorization", "Bearer $key")
            }
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${connection.responseCode}")
                lastFailureWasNetwork = false
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Requête échouée : ${e.message}")
            lastFailureWasNetwork = true
            null
        } finally {
            connection?.disconnect()
        }
    }
}
