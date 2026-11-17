package com.cinetv.data.subs

import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sous-titres en ligne, par OpenSubtitles.com.
 *
 * Il faut une clé d'accès (gratuite, à créer sur opensubtitles.com, rubrique
 * « API consumers »), saisie dans les Paramètres. Sans compte, le service
 * n'accorde que quelques téléchargements par jour.
 */
object OpenSubtitles {

    private const val BASE = "https://api.opensubtitles.com/api/v1"
    private const val AGENT = "Netfloux v1.0"
    private const val TAG = "OpenSubtitles"

    /** Clé saisie dans les Paramètres. */
    @Volatile var apiKey: String = ""

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /** Dernière raison d'échec, en clair, pour l'afficher. */
    @Volatile var lastError: String = ""
        private set

    data class Found(
        val fileId: Long,
        val language: String,     // « fr », « en »…
        val release: String,      // nom de la version, pour reconnaître la bonne
        val downloads: Int,
        val hearingImpaired: Boolean
    )

    /**
     * Cherche des sous-titres français et anglais. Avec l'identifiant TMDB si
     * la fiche est reconnue (c'est le plus sûr), sinon avec le titre.
     */
    fun search(tmdbId: Int, isEpisode: Boolean, season: Int, episode: Int, title: String): List<Found> {
        lastError = ""
        // Le service veut ses paramètres rangés par ordre alphabétique, en minuscules
        val params = sortedMapOf<String, String>()
        params["languages"] = "en,fr"
        when {
            tmdbId > 0 && isEpisode -> {
                params["parent_tmdb_id"] = tmdbId.toString()
                if (season > 0) params["season_number"] = season.toString()
                if (episode > 0) params["episode_number"] = episode.toString()
            }
            tmdbId > 0 -> params["tmdb_id"] = tmdbId.toString()
            else -> params["query"] = title.lowercase()
        }
        val url = Uri.parse("$BASE/subtitles").buildUpon()
            .apply { params.forEach { (k, v) -> appendQueryParameter(k, v) } }
            .build().toString()

        val body = request(url, null) ?: return emptyList()
        return runCatching {
            val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
            buildList {
                for (i in 0 until data.length()) {
                    val attributes = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
                    val file = attributes.optJSONArray("files")?.optJSONObject(0) ?: continue
                    val id = file.optLong("file_id")
                    if (id <= 0) continue
                    add(
                        Found(
                            fileId = id,
                            language = attributes.optString("language").lowercase(),
                            release = attributes.optString("release").ifBlank { file.optString("file_name") },
                            downloads = attributes.optInt("download_count"),
                            hearingImpaired = attributes.optBoolean("hearing_impaired")
                        )
                    )
                }
            }
                // Le français d'abord, puis les plus téléchargés
                .sortedWith(compareBy<Found> { if (it.language == "fr") 0 else 1 }.thenByDescending { it.downloads })
                .take(30)
        }.getOrElse {
            lastError = "Réponse illisible du service"
            emptyList()
        }
    }

    /** Télécharge un sous-titre (au format .srt, en UTF-8). Null en cas d'échec : voir [lastError]. */
    fun download(found: Found): ByteArray? {
        lastError = ""
        val answer = request(
            "$BASE/download",
            JSONObject().put("file_id", found.fileId).put("sub_format", "srt").toString()
        ) ?: return null
        val link = runCatching { JSONObject(answer).optString("link") }.getOrDefault("")
        if (link.isBlank()) {
            lastError = "Le service n'a pas fourni le fichier (quota du jour atteint ?)"
            return null
        }
        return runCatching {
            (URL(link).openConnection() as HttpURLConnection).run {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", AGENT)
                try { inputStream.use { it.readBytes() } } finally { disconnect() }
            }
        }.getOrElse {
            lastError = "Téléchargement interrompu"
            null
        }
    }

    private fun request(url: String, jsonBody: String?): String? {
        if (!isConfigured) { lastError = "Clé OpenSubtitles absente : ajoutez-la dans les Paramètres"; return null }
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
                setRequestProperty("Api-Key", apiKey.trim())
                setRequestProperty("User-Agent", AGENT)
                setRequestProperty("Accept", "application/json")
                if (jsonBody != null) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                }
            }
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                lastError = when (code) {
                    401, 403 -> "Clé OpenSubtitles refusée : vérifiez-la dans les Paramètres"
                    406 -> "Quota de téléchargements du jour atteint"
                    429 -> "Trop de demandes : réessayez dans un instant"
                    else -> "Le service a répondu par une erreur ($code)"
                }
                Log.w(TAG, "HTTP $code")
                null
            }
        } catch (e: Exception) {
            lastError = "Pas de connexion au service de sous-titres"
            Log.w(TAG, "Requête échouée : ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }
}
