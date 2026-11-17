package com.cinetv.data.repo

import com.cinetv.data.db.LibraryStore
import com.cinetv.data.model.DriveTheme
import com.cinetv.data.model.MetaState
import com.cinetv.data.model.Node
import com.cinetv.data.model.Volume
import com.cinetv.data.network.NetworkShare
import com.cinetv.data.network.NetworkShares
import com.cinetv.data.storage.MediaLibraryScanner
import jcifs.smb.SmbFileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde de tout ce qui ne se retrouve pas par une simple analyse :
 * fiches reconnues ou corrigées, jaquettes choisies à la main, thèmes des
 * disques, films vus et positions de lecture.
 *
 * Le fichier « netfloux-sauvegarde.json » est écrit à la racine de chaque
 * dossier réseau en ligne. Les mots de passe des partages n'y figurent pas.
 */
class LibraryBackup(
    private val store: LibraryStore,
    private val shares: NetworkShares,
    /** Réglages d'apparence et d'organisation, sauvegardés avec le reste (sans les clés d'accès). */
    private val settings: com.cinetv.data.prefs.Settings? = null
) {

    companion object { const val FILE_NAME = "netfloux-sauvegarde.json" }

    private fun onlineShares(): List<NetworkShare> {
        val connected = store.volumes().filter { it.isConnected }.map { it.id }.toSet()
        return shares.all().filter { it.volumeId in connected }
    }

    /** Renvoie le compte rendu à afficher. */
    fun export(): String {
        val targets = onlineShares()
        if (targets.isEmpty()) return "Aucun dossier réseau en ligne : ajoutez-en un pour y sauvegarder"

        val nodes = store.nodesWorthSaving()
        val json = JSONObject()
            .put("version", 1)
            .put("savedAt", System.currentTimeMillis())
            .put("volumes", JSONArray().apply {
                store.volumes().forEach { v ->
                    put(
                        JSONObject().put("id", v.id).put("label", v.label)
                            .put("theme", v.theme.name).put("orderIndex", v.orderIndex)
                    )
                }
            })
            .put("shares", JSONArray().apply {
                shares.all().forEach { s ->
                    put(JSONObject().put("label", s.label).put("host", s.host).put("share", s.share).put("user", s.user))
                }
            })
            .put("nodes", JSONArray().apply { nodes.forEach { put(it.toJson()) } })
        settings?.let { json.put("settings", it.exportJson()) }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)

        var written = 0
        var lastError = ""
        for (share in targets) {
            try {
                NetworkShares.open(share, FILE_NAME).use { file ->
                    SmbFileOutputStream(file).use { it.write(bytes) }
                }
                written++
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        return if (written > 0) "${nodes.size} fiches sauvegardées sur $written dossier(s) réseau"
        else "Écriture refusée par le partage : $lastError"
    }

    /** Renvoie le compte rendu à afficher. */
    fun restore(): String {
        val candidates = shares.all()
        if (candidates.isEmpty()) return "Ajoutez d'abord le dossier réseau qui contient la sauvegarde"

        var text: String? = null
        for (share in candidates) {
            text = runCatching {
                NetworkShares.open(share, FILE_NAME).use { file ->
                    if (file.exists()) file.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } else null
                }
            }.getOrNull()
            if (text != null) break
        }
        val json = runCatching { JSONObject(text ?: return "Aucune sauvegarde trouvée sur vos dossiers réseau") }
            .getOrElse { return "Fichier de sauvegarde illisible" }

        // Disques : thème et nom retrouvés ; un disque absent est recréé « débranché »
        val volumes = json.optJSONArray("volumes") ?: JSONArray()
        for (i in 0 until volumes.length()) {
            val o = volumes.getJSONObject(i)
            val id = o.optString("id")
            if (id.isBlank()) continue
            val theme = runCatching { DriveTheme.valueOf(o.optString("theme")) }.getOrDefault(DriveTheme.AUTRE)
            val existing = store.volume(id)
            store.upsertVolume(
                (existing ?: Volume(
                    id = id, label = o.optString("label"), theme = theme,
                    orderIndex = o.optInt("orderIndex"), isConnected = false
                )).copy(label = o.optString("label").ifBlank { existing?.label ?: id }, theme = theme)
            )
        }

        // Partages : remis sans mot de passe, à compléter depuis l'accueil
        val known = shares.all().map { it.volumeId }.toSet()
        val saved = json.optJSONArray("shares") ?: JSONArray()
        for (i in 0 until saved.length()) {
            val o = saved.getJSONObject(i)
            val share = NetworkShare(o.optString("label"), o.optString("host"), o.optString("share"), o.optString("user"))
            if (share.host.isNotBlank() && share.volumeId !in known) shares.save(share)
        }

        // Fiches : mises à jour si le fichier est déjà connu, sinon posées en
        // attente — la prochaine analyse du disque les reliera à leur fichier.
        val nodes = json.optJSONArray("nodes") ?: JSONArray()
        var updated = 0
        val waiting = ArrayList<Node>()
        for (i in 0 until nodes.length()) {
            val node = runCatching { nodeFrom(nodes.getJSONObject(i)) }.getOrNull() ?: continue
            if (store.restoreOnto(node)) updated++ else waiting += node
        }
        store.insertNewNodes(waiting)
        // Réglages : thème de couleur, grille, ordre de l'accueil, dossiers affichés comme disques
        json.optJSONObject("settings")?.let { saved ->
            settings?.importJson(saved)
            settings?.let { com.cinetv.ui.theme.Ink.apply(it.accentTheme) }
        }
        store.notifyChanged()
        return "$updated fiches restaurées" +
            if (waiting.isNotEmpty()) ", ${waiting.size} en attente de l'analyse de leur disque" else ""
    }

    private fun Node.toJson() = JSONObject()
        .put("volumeId", volumeId).put("path", relativePath).put("dir", isDirectory)
        .put("state", metadataState.name).put("tmdbId", tmdbId).put("title", title)
        .put("year", year).put("releaseDate", releaseDate).put("synopsis", synopsis)
        .put("posterUrl", posterUrl).put("backdropUrl", backdropUrl).put("rating", rating.toDouble())
        .put("mediaType", mediaType).put("actors", actors).put("director", director)
        .put("creditsDone", creditsDone).put("collectionId", collectionId).put("collectionName", collectionName).put("genres", genres).put("logoUrl", logoUrl)
        .put("position", lastPositionMs).put("duration", durationMs)
        .put("watched", isWatched).put("playedAt", lastPlayedAt).put("listedAt", listedAt)

    private fun nodeFrom(o: JSONObject): Node {
        val volumeId = o.getString("volumeId")
        val path = o.getString("path")
        val parentPath = path.substringBeforeLast('/', "")
        return Node(
            id = MediaLibraryScanner.nodeId(volumeId, path),
            volumeId = volumeId,
            parentId = if (parentPath.isEmpty()) null else MediaLibraryScanner.nodeId(volumeId, parentPath),
            name = path.substringAfterLast('/'),
            relativePath = path,
            documentUri = "",
            isDirectory = o.optBoolean("dir"),
            isAvailable = false,
            metadataState = MetaState.parse(o.optString("state")),
            tmdbId = o.optInt("tmdbId"),
            title = o.optString("title"),
            year = o.optInt("year"),
            releaseDate = o.optString("releaseDate"),
            synopsis = o.optString("synopsis"),
            posterUrl = o.optString("posterUrl"),
            backdropUrl = o.optString("backdropUrl"),
            rating = o.optDouble("rating", 0.0).toFloat(),
            mediaType = o.optString("mediaType"),
            actors = o.optString("actors"),
            director = o.optString("director"),
            creditsDone = o.optBoolean("creditsDone"),
            collectionId = o.optInt("collectionId"),
            collectionName = o.optString("collectionName"),
            genres = o.optString("genres"),
            logoUrl = o.optString("logoUrl"),
            lastPositionMs = o.optLong("position"),
            durationMs = o.optLong("duration"),
            isWatched = o.optBoolean("watched"),
            lastPlayedAt = o.optLong("playedAt"),
            listedAt = o.optLong("listedAt")
        )
    }
}
