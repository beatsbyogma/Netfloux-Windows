package com.cinetv.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.cinetv.data.model.DriveTheme
import com.cinetv.data.model.MetaState
import com.cinetv.data.model.LibraryStats
import com.cinetv.data.model.Node
import com.cinetv.data.model.Volume
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Stockage de la bibliothèque, en SQLite natif.
 *
 * Pas de Room : l'app n'a besoin que de deux tables et d'une poignée de requêtes,
 * et cela évite un processeur d'annotations dont la version doit suivre celle de
 * Kotlin à la révision près.
 *
 * Les écrans observent `revision` : chaque écriture l'incrémente, ce qui déclenche
 * une relecture. Toutes les écritures passant par ce fichier, l'invalidation est fiable.
 */
class LibraryStore(context: Context) {

    private val helper = Helper(context.applicationContext)

    /** Compteur de modifications — sert de signal de rafraîchissement aux écrans. */
    val revision = MutableStateFlow(0L)

    private fun touched() { revision.value = revision.value + 1 }

    private val db: SQLiteDatabase get() = helper.writableDatabase

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, "cinetv.db", null, 13) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE volumes (
                    id TEXT PRIMARY KEY NOT NULL,
                    label TEXT NOT NULL,
                    theme TEXT NOT NULL,
                    isConnected INTEGER NOT NULL DEFAULT 0,
                    lastSeenAt INTEGER NOT NULL DEFAULT 0,
                    videoCount INTEGER NOT NULL DEFAULT 0,
                    folderCount INTEGER NOT NULL DEFAULT 0,
                    orderIndex INTEGER NOT NULL DEFAULT 0,
                    freeBytes INTEGER NOT NULL DEFAULT 0,
                    totalBytes INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE nodes (
                    id TEXT PRIMARY KEY NOT NULL,
                    volumeId TEXT NOT NULL,
                    parentId TEXT,
                    name TEXT NOT NULL,
                    relativePath TEXT NOT NULL,
                    documentUri TEXT NOT NULL,
                    isDirectory INTEGER NOT NULL,
                    sizeBytes INTEGER NOT NULL DEFAULT 0,
                    childCount INTEGER NOT NULL DEFAULT 0,
                    isAvailable INTEGER NOT NULL DEFAULT 1,
                    metadataState TEXT NOT NULL DEFAULT 'PENDING',
                    tmdbId INTEGER NOT NULL DEFAULT 0,
                    title TEXT NOT NULL DEFAULT '',
                    year INTEGER NOT NULL DEFAULT 0,
                    releaseDate TEXT NOT NULL DEFAULT '',
                    synopsis TEXT NOT NULL DEFAULT '',
                    posterUrl TEXT NOT NULL DEFAULT '',
                    backdropUrl TEXT NOT NULL DEFAULT '',
                    rating REAL NOT NULL DEFAULT 0,
                    lastPositionMs INTEGER NOT NULL DEFAULT 0,
                    durationMs INTEGER NOT NULL DEFAULT 0,
                    isWatched INTEGER NOT NULL DEFAULT 0,
                    lastPlayedAt INTEGER NOT NULL DEFAULT 0,
                    addedAt INTEGER NOT NULL DEFAULT 0,
                    mediaType TEXT NOT NULL DEFAULT '',
                    actors TEXT NOT NULL DEFAULT '',
                    director TEXT NOT NULL DEFAULT '',
                    creditsDone INTEGER NOT NULL DEFAULT 0,
                    collectionId INTEGER NOT NULL DEFAULT 0,
                    collectionName TEXT NOT NULL DEFAULT '',
                    genres TEXT NOT NULL DEFAULT '',
                    logoUrl TEXT NOT NULL DEFAULT '',
                    listedAt INTEGER NOT NULL DEFAULT 0,
                    quality INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX idx_nodes_volume ON nodes(volumeId)")
            db.execSQL("CREATE INDEX idx_nodes_parent ON nodes(parentId)")
            db.execSQL("CREATE INDEX idx_nodes_meta ON nodes(metadataState)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("DROP TABLE IF EXISTS nodes")
                db.execSQL("DROP TABLE IF EXISTS volumes")
                onCreate(db)
                return
            }
            // 2 → 3 : on ajoute des colonnes, sans rien effacer de la bibliothèque
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE volumes ADD COLUMN freeBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE volumes ADD COLUMN totalBytes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE nodes ADD COLUMN mediaType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE nodes ADD COLUMN actors TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE nodes ADD COLUMN director TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE nodes ADD COLUMN creditsDone INTEGER NOT NULL DEFAULT 0")
            }
            // 3 → 4 : les sagas. Les films déjà complétés repassent une fois par
            // TMDB, qui donne la saga dans la même réponse que la distribution.
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE nodes ADD COLUMN collectionId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE nodes ADD COLUMN collectionName TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE nodes SET creditsDone = 0 WHERE tmdbId > 0 AND mediaType != 'tv'")
            }
            // 4 → 5 : les genres, qui décident de la police des titres. Toutes les
            // fiches reconnues repassent une fois par TMDB pour les récupérer.
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE nodes ADD COLUMN genres TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE nodes SET creditsDone = 0 WHERE tmdbId > 0")
            }
            // 5 → 6 : les logos de titre. Même principe : chaque fiche reconnue
            // repasse une fois par TMDB pour récupérer le sien.
            if (oldVersion < 6) {
                db.execSQL("ALTER TABLE nodes ADD COLUMN logoUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE nodes SET creditsDone = 0 WHERE tmdbId > 0")
            }
            // 6 → 7 : « Ma liste »
            if (oldVersion < 7) {
                db.execSQL("ALTER TABLE nodes ADD COLUMN listedAt INTEGER NOT NULL DEFAULT 0")
            }
            // 7 → 8 : la définition de chaque vidéo (4K, 1080p…), relevée en fond
            if (oldVersion < 8) {
                db.execSQL("ALTER TABLE nodes ADD COLUMN quality INTEGER NOT NULL DEFAULT 0")
            }
            // 8 → 9 : la définition se lit désormais d'abord dans le fichier ; tout est relevé à nouveau
            if (oldVersion < 9) {
                db.execSQL("UPDATE nodes SET quality = 0")
            }
            // 9 → 10 : les logos passent en version originale (anglaise) ; chaque fiche
            // reconnue repasse une fois par TMDB pour récupérer le sien.
            if (oldVersion < 10) {
                db.execSQL("UPDATE nodes SET creditsDone = 0 WHERE tmdbId > 0")
            }
            // 10 → 11 : le logo suit le nom sous lequel le titre est connu en France (français ou
            // original) ; chaque fiche reconnue repasse une fois par TMDB.
            if (oldVersion < 11) {
                db.execSQL("UPDATE nodes SET creditsDone = 0 WHERE tmdbId > 0")
            }
            // 11 → 12 : logos français pour les films français et l'animation, version en couleurs
            // préférée à la blanche ; chaque fiche reconnue repasse une fois par TMDB.
            if (oldVersion < 12) {
                db.execSQL("UPDATE nodes SET creditsDone = 0 WHERE tmdbId > 0")
            }
            // 12 → 13 : le logo s'accorde avec le nom du fichier (français ou original)
            if (oldVersion < 13) {
                db.execSQL("UPDATE nodes SET creditsDone = 0 WHERE tmdbId > 0")
            }
        }
    }

    // ------------------------------------------------------------- lecture

    fun volumes(): List<Volume> =
        db.query("volumes", null, null, null, null, null, "orderIndex ASC, label COLLATE NOCASE ASC")
            .use { it.mapAll(::readVolume) }

    fun volume(id: String): Volume? =
        db.query("volumes", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { if (it.moveToFirst()) readVolume(it) else null }

    fun children(volumeId: String, parentId: String?): List<Node> {
        val sql = if (parentId == null) {
            "SELECT * FROM nodes WHERE volumeId = ? AND parentId IS NULL " +
                "ORDER BY isDirectory DESC, name COLLATE NOCASE ASC"
        } else {
            "SELECT * FROM nodes WHERE volumeId = ? AND parentId = ? " +
                "ORDER BY isDirectory DESC, name COLLATE NOCASE ASC"
        }
        val args = if (parentId == null) arrayOf(volumeId) else arrayOf(volumeId, parentId)
        return db.rawQuery(sql, args).use { it.mapAll(::readNode) }
    }

    fun node(id: String): Node? =
        db.query("nodes", null, "id = ?", arrayOf(id), null, null, null, "1")
            .use { if (it.moveToFirst()) readNode(it) else null }

    fun postersInVolume(volumeId: String, limit: Int = 12): List<String> =
        db.rawQuery(
            // Une jaquette par titre, dans un ordre mélangé (la fin de l'adresse de
            // l'image sert de tirage, stable d'une lecture à l'autre) : sur un disque
            // de séries, tous les épisodes partagent la même et elle revenait en boucle.
            "SELECT posterUrl FROM nodes WHERE volumeId = ? AND isDirectory = 0 " +
                "AND posterUrl != '' GROUP BY posterUrl " +
                "ORDER BY substr(posterUrl, length(posterUrl) - 7) LIMIT ?",
            arrayOf(volumeId, limit.toString())
        ).use { it.mapAll { c -> c.getString(0) } }

    /** Jaquettes d'un disque, sans celles rangées sous certains dossiers (présentés à part). */
    fun postersInVolumeExcept(volumeId: String, excludedFolders: List<String>, limit: Int = 12): List<String> {
        if (excludedFolders.isEmpty()) return postersInVolume(volumeId, limit)
        val marks = excludedFolders.joinToString(",") { "?" }
        return db.rawQuery(
            "WITH RECURSIVE sub(id) AS (" +
                "SELECT id FROM nodes WHERE id IN ($marks) " +
                "UNION ALL SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id) " +
                "SELECT posterUrl FROM nodes WHERE volumeId = ? AND isDirectory = 0 AND posterUrl != '' " +
                "AND id NOT IN (SELECT id FROM sub) GROUP BY posterUrl " +
                "ORDER BY substr(posterUrl, length(posterUrl) - 7) LIMIT ?",
            (excludedFolders + volumeId + limit.toString()).toTypedArray()
        ).use { it.mapAll { c -> c.getString(0) } }
    }

    /** Nombre de sous-dossiers directs : pour une série, ses saisons. */
    fun childFolderCount(folderId: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM nodes WHERE parentId = ? AND isDirectory = 1", arrayOf(folderId))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Jaquettes de tout ce qui se trouve sous un dossier, sous-dossiers compris. */
    fun postersUnder(folderId: String, limit: Int = 12): List<String> =
        db.rawQuery(
            "WITH RECURSIVE sub(id) AS (" +
                "SELECT ? UNION ALL SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id) " +
                "SELECT posterUrl FROM nodes WHERE id IN (SELECT id FROM sub) AND isDirectory = 0 " +
                "AND posterUrl != '' GROUP BY posterUrl " +
                "ORDER BY substr(posterUrl, length(posterUrl) - 7) LIMIT ?",
            arrayOf(folderId, limit.toString())
        ).use { it.mapAll { c -> c.getString(0) } }

    /** Nombre de vidéos et de sous-dossiers sous un dossier, à toute profondeur. */
    fun countUnder(folderId: String): Pair<Int, Int> =
        db.rawQuery(
            "WITH RECURSIVE sub(id) AS (" +
                "SELECT ? UNION ALL SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id) " +
                "SELECT SUM(CASE WHEN isDirectory = 0 THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN isDirectory = 1 THEN 1 ELSE 0 END) FROM nodes WHERE id IN (SELECT id FROM sub)",
            arrayOf(folderId)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) to (c.getInt(1) - 1).coerceAtLeast(0) else 0 to 0 }

    fun postersInFolder(folderId: String, limit: Int = 8): List<String> =
        db.rawQuery(
            "SELECT posterUrl FROM nodes WHERE parentId = ? AND isDirectory = 0 " +
                "AND posterUrl != '' GROUP BY posterUrl LIMIT ?",
            arrayOf(folderId, limit.toString())
        ).use { it.mapAll { c -> c.getString(0) } }

    /**
     * Les titres qui ont une fiche avec des genres : un par film, un par série (et non un par épisode).
     */
    fun titlesWithGenres(): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND tmdbId > 0 AND genres != '' " +
                "GROUP BY mediaType, tmdbId",
            null
        ).use { it.mapAll(::readNode) }

    /**
     * Tout ce que la bibliothèque possède déjà, sous la forme "movie:123" ou "tv:456".
     * Une fiche dont on ignore le type compte pour les deux.
     */
    fun ownedTitles(): Set<String> =
        db.rawQuery("SELECT DISTINCT mediaType, tmdbId FROM nodes WHERE tmdbId > 0", null).use { c ->
            buildSet {
                while (c.moveToNext()) {
                    val type = c.getString(0).orEmpty()
                    val id = c.getInt(1)
                    if (type == "movie" || type == "tv") add("$type:$id")
                    else { add("movie:$id"); add("tv:$id") }
                }
            }
        }

    // ------------------------------------------------------------- définition des vidéos

    /** Les vidéos dont la définition n'a pas encore été regardée : identifiant et nom. */
    fun namesWithoutQuality(): List<Pair<String, String>> =
        db.rawQuery("SELECT id, name FROM nodes WHERE isDirectory = 0 AND quality = 0", null)
            .use { it.mapAll { c -> c.getString(0) to c.getString(1) } }

    /** Les vidéos joignables dont il faut ouvrir l'en-tête pour connaître la définition. */
    fun pendingQuality(limit: Int = 40): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND (quality = 0 OR quality % 10 = 1) AND isAvailable = 1 LIMIT ?",
            arrayOf(limit.toString())
        ).use { it.mapAll(::readNode) }

    fun countPendingQuality(): Int =
        db.rawQuery("SELECT COUNT(*) FROM nodes WHERE isDirectory = 0 AND (quality = 0 OR quality % 10 = 1) AND isAvailable = 1", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Note la définition de plusieurs vidéos d'un coup, sans réveiller l'écran à chaque ligne. */
    fun setQualities(values: List<Pair<String, Int>>) {
        if (values.isEmpty()) return
        db.beginTransaction()
        try {
            values.forEach { (id, quality) ->
                db.execSQL("UPDATE nodes SET quality = ? WHERE id = ?", arrayOf<Any>(quality, id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** La meilleure définition parmi les vidéos d'un dossier, à toute profondeur ; 0 si aucune n'est connue. */
    fun maxQualityUnder(folderId: String): Int =
        db.rawQuery(
            "WITH RECURSIVE sub(id) AS (" +
                "SELECT ? UNION ALL SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id) " +
                "SELECT MAX(quality) FROM nodes WHERE id IN (SELECT id FROM sub) AND isDirectory = 0",
            arrayOf(folderId)
        ).use { c -> if (c.moveToFirst()) c.getInt(0).coerceAtLeast(0) else 0 }

    /** Toutes les vidéos d'un dossier, à toute profondeur. */
    fun filesUnder(folderId: String): List<Node> =
        db.rawQuery(
            "WITH RECURSIVE sub(id) AS (" +
                "SELECT ? UNION ALL SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id) " +
                "SELECT * FROM nodes WHERE id IN (SELECT id FROM sub) AND isDirectory = 0",
            arrayOf(folderId)
        ).use { it.mapAll(::readNode) }

    /** Remet en attente les fichiers d'un dossier que la recherche avait laissés de côté. */
    fun retryUnder(folderId: String) {
        db.execSQL(
            "WITH RECURSIVE sub(id) AS (" +
                "SELECT ? UNION ALL SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id) " +
                "UPDATE nodes SET metadataState = 'PENDING' WHERE isDirectory = 0 " +
                "AND metadataState = 'SKIPPED' AND id IN (SELECT id FROM sub)",
            arrayOf(folderId)
        )
    }

    /** Remet en attente les fichiers restés sans fiche, pour une nouvelle recherche. */
    fun retryUnmatched() {
        db.execSQL(
            "UPDATE nodes SET metadataState = 'PENDING' WHERE isDirectory = 0 " +
                "AND metadataState IN ('NOT_FOUND', 'SKIPPED')"
        )
    }

    fun pendingMetadata(limit: Int = 30): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND metadataState = 'PENDING' " +
                "ORDER BY addedAt ASC LIMIT ?",
            arrayOf(limit.toString())
        ).use { it.mapAll(::readNode) }

    /** Fichiers commencés mais non terminés, les plus récents d'abord. */
    fun continueWatching(limit: Int = 20): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND lastPositionMs > 60000 " +
                "AND isWatched = 0 ORDER BY lastPlayedAt DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { it.mapAll(::readNode) }

    /** Tirage au hasard parmi les titres qui ont un grand visuel, pour la bannière. */
    fun randomSpotlight(limit: Int = 14): List<Node> =
        db.rawQuery(
            // Une seule entrée par œuvre : sans ça, une série de 60 épisodes
            // aurait 60 chances d'être tirée, contre une pour un film.
            "SELECT * FROM nodes WHERE isDirectory = 0 AND backdropUrl != '' " +
                "GROUP BY CASE WHEN tmdbId > 0 THEN CAST(tmdbId AS TEXT) || mediaType ELSE id END " +
                "ORDER BY RANDOM() LIMIT ?",
            arrayOf(limit.toString())
        ).use { it.mapAll(::readNode) }

    /**
     * Tirage au hasard dans un dossier, sous-dossiers compris — ou dans tout
     * le disque si aucun dossier n'est donné. Sert la bannière des pages disque.
     */
    fun randomUnder(volumeId: String, folderId: String?, limit: Int = 8): List<Node> =
        if (folderId == null) {
            db.rawQuery(
                "SELECT * FROM nodes WHERE volumeId = ? AND isDirectory = 0 AND backdropUrl != '' " +
                    "ORDER BY RANDOM() LIMIT ?",
                arrayOf(volumeId, limit.toString())
            ).use { it.mapAll(::readNode) }
        } else {
            db.rawQuery(
                "WITH RECURSIVE sub(id) AS (" +
                    "SELECT ? UNION ALL " +
                    "SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id WHERE n.isDirectory = 1) " +
                    "SELECT * FROM nodes WHERE parentId IN (SELECT id FROM sub) " +
                    "AND isDirectory = 0 AND backdropUrl != '' ORDER BY RANDOM() LIMIT ?",
                arrayOf(folderId, limit.toString())
            ).use { it.mapAll(::readNode) }
        }

    /** Fiches reconnues dont la distribution n'a pas encore été demandée. */
    fun countPendingCredits(): Int =
        db.rawQuery("SELECT COUNT(*) FROM nodes WHERE tmdbId > 0 AND creditsDone = 0", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun pendingCredits(limit: Int = 25): List<Node> =
        db.rawQuery(
            // Dossiers compris : une série dont on a choisi la fiche reçoit elle aussi son logo et ses genres
            "SELECT * FROM nodes WHERE tmdbId > 0 AND creditsDone = 0 LIMIT ?",
            arrayOf(limit.toString())
        ).use { it.mapAll(::readNode) }

    /** Derniers fichiers ajoutés à la bibliothèque, pour la rangée « Nouveautés ». */
    /** Les derniers fichiers ajoutés ; [since] écarte tout ce qui est plus ancien. */
    fun recentlyAdded(limit: Int = 24, since: Long = 0L): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND addedAt > ? " +
                "ORDER BY addedAt DESC, name COLLATE NOCASE ASC LIMIT ?",
            arrayOf(since.toString(), limit.toString())
        ).use { it.mapAll(::readNode) }

    /** Fichier suivant dans le même dossier, pour l'enchaînement des épisodes. */
    fun nextInFolder(node: Node): Node? {
        val sql = if (node.parentId == null) {
            "SELECT * FROM nodes WHERE volumeId = ? AND parentId IS NULL AND isDirectory = 0 " +
                "AND name COLLATE NOCASE > ? ORDER BY name COLLATE NOCASE ASC LIMIT 1"
        } else {
            "SELECT * FROM nodes WHERE volumeId = ? AND parentId = ? AND isDirectory = 0 " +
                "AND name COLLATE NOCASE > ? ORDER BY name COLLATE NOCASE ASC LIMIT 1"
        }
        val args = if (node.parentId == null) arrayOf(node.volumeId, node.name)
                   else arrayOf(node.volumeId, node.parentId, node.name)
        return db.rawQuery(sql, args).use { if (it.moveToFirst()) readNode(it) else null }
    }

    fun countPendingMetadata(): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM nodes WHERE isDirectory = 0 AND metadataState = 'PENDING'",
            null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun search(query: String, limit: Int = 200): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND (title LIKE ? OR name LIKE ?) " +
                "ORDER BY isAvailable DESC, name COLLATE NOCASE ASC LIMIT ?",
            arrayOf("%$query%", "%$query%", limit.toString())
        ).use { it.mapAll(::readNode) }

    fun nodeIds(volumeId: String): Set<String> =
        db.rawQuery("SELECT id FROM nodes WHERE volumeId = ?", arrayOf(volumeId))
            .use { it.mapAll { c -> c.getString(0) } }
            .toHashSet()

    fun countNodes(volumeId: String, directories: Boolean): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM nodes WHERE volumeId = ? AND isDirectory = ?",
            arrayOf(volumeId, if (directories) "1" else "0")
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ------------------------------------------------------------ écriture

    fun upsertVolume(volume: Volume) {
        db.insertWithOnConflict("volumes", null, volume.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
        touched()
    }

    fun setVolumeConnected(id: String, connected: Boolean) {
        db.execSQL(
            "UPDATE volumes SET isConnected = ?, lastSeenAt = ? WHERE id = ?",
            arrayOf(if (connected) 1 else 0, System.currentTimeMillis(), id)
        )
        touched()
    }

    fun setVolumeSpace(id: String, freeBytes: Long, totalBytes: Long) {
        db.execSQL(
            "UPDATE volumes SET freeBytes = ?, totalBytes = ? WHERE id = ?",
            arrayOf(freeBytes, totalBytes, id)
        )
    }

    fun setVolumeTheme(id: String, theme: DriveTheme) {
        db.execSQL("UPDATE volumes SET theme = ? WHERE id = ?", arrayOf(theme.name, id))
        touched()
    }

    fun renameVolume(id: String, label: String) {
        db.execSQL("UPDATE volumes SET label = ? WHERE id = ?", arrayOf(label, id))
        touched()
    }

    fun setVolumeCounts(id: String, videos: Int, folders: Int) {
        db.execSQL(
            "UPDATE volumes SET videoCount = ?, folderCount = ? WHERE id = ?",
            arrayOf(videos, folders, id)
        )
        touched()
    }

    /**
     * Supprime les fiches de disque en double.
     *
     * Les versions précédentes créaient une fiche par source de détection :
     * un même disque pouvait figurer deux fois, sous deux graphies. On ne garde
     * que celle qui porte le contenu.
     */
    fun pruneDuplicateVolumes(canonical: (String) -> String): Int {
        val all = volumes()
        val byCanonical = all.groupBy { canonical(it.id) }
        var removed = 0
        for ((_, group) in byCanonical) {
            if (group.size < 2) continue
            val keep = group.maxByOrNull { countNodes(it.id, false) } ?: continue
            for (volume in group) {
                if (volume.id != keep.id) { deleteVolume(volume.id); removed++ }
            }
        }
        // Fiches sans contenu et jamais rattachées à rien
        for (volume in volumes()) {
            if (countNodes(volume.id, false) == 0 && countNodes(volume.id, true) == 0 &&
                volume.id != canonical(volume.id)
            ) {
                deleteVolume(volume.id); removed++
            }
        }
        return removed
    }

    fun deleteVolume(id: String) {
        db.delete("nodes", "volumeId = ?", arrayOf(id))
        db.delete("volumes", "id = ?", arrayOf(id))
        touched()
    }

    /** Insère les nouveaux nœuds sans écraser ceux déjà connus (progression conservée). */
    fun insertNewNodes(nodes: List<Node>) {
        if (nodes.isEmpty()) return
        db.beginTransaction()
        try {
            for (n in nodes) {
                db.insertWithOnConflict("nodes", null, n.toValues(), SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        touched()
    }

    /** Rafraîchit l'URI d'un fichier connu : elle change au remontage du disque. */
    fun refreshFile(id: String, documentUri: String, sizeBytes: Long) {
        db.execSQL(
            "UPDATE nodes SET documentUri = ?, sizeBytes = ?, isAvailable = 1 WHERE id = ?",
            arrayOf(documentUri, sizeBytes, id)
        )
    }

    fun refreshFolder(id: String, documentUri: String, childCount: Int) {
        db.execSQL(
            "UPDATE nodes SET documentUri = ?, childCount = ?, isAvailable = 1 WHERE id = ?",
            arrayOf(documentUri, childCount, id)
        )
    }

    fun setVolumeAvailability(volumeId: String, available: Boolean) {
        db.execSQL(
            "UPDATE nodes SET isAvailable = ? WHERE volumeId = ?",
            arrayOf(if (available) 1 else 0, volumeId)
        )
        touched()
    }

    fun deleteNodes(ids: Collection<String>) {
        if (ids.isEmpty()) return
        db.beginTransaction()
        try {
            for (id in ids) db.delete("nodes", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        touched()
    }

    fun applyMetadata(
        id: String, state: MetaState, tmdbId: Int, title: String, year: Int,
        releaseDate: String, synopsis: String, posterUrl: String,
        backdropUrl: String, rating: Float, mediaType: String = ""
    ) {
        // Une nouvelle fiche remet la distribution à zéro : elle sera redemandée
        db.execSQL(
            """
            UPDATE nodes SET metadataState = ?, tmdbId = ?, title = ?, year = ?,
                releaseDate = ?, synopsis = ?, posterUrl = ?, backdropUrl = ?, rating = ?,
                mediaType = ?, actors = '', director = '', creditsDone = 0, logoUrl = ''
            WHERE id = ?
            """.trimIndent(),
            arrayOf(state.name, tmdbId, title, year, releaseDate, synopsis, posterUrl, backdropUrl, rating, mediaType, id)
        )
        touched()
    }

    /**
     * Enregistre acteurs et réalisation. Ne signale pas le changement : ces
     * écritures arrivent par dizaines, on prévient les écrans une fois par lot
     * avec [notifyChanged].
     */
    fun applyCredits(
        id: String, mediaType: String, actors: String, director: String,
        collectionId: Int = 0, collectionName: String = "", genres: String = "", logoUrl: String = ""
    ) {
        db.execSQL(
            "UPDATE nodes SET mediaType = ?, actors = ?, director = ?, creditsDone = 1, " +
                "collectionId = ?, collectionName = ?, genres = ?, logoUrl = ? WHERE id = ?",
            arrayOf(mediaType, actors, director, collectionId, collectionName, genres, logoUrl, id)
        )
    }

    /** Les autres volets d'une saga présents dans la bibliothèque, par ordre de sortie. */
    fun inCollection(collectionId: Int, exceptTmdbId: Int): List<Node> =
        if (collectionId <= 0) emptyList()
        else db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND collectionId = ? AND tmdbId != ? " +
                "GROUP BY tmdbId ORDER BY releaseDate ASC, year ASC",
            arrayOf(collectionId.toString(), exceptTmdbId.toString())
        ).use { it.mapAll(::readNode) }

    /** Épisodes vus et total d'un dossier, sous-dossiers compris. */
    fun folderProgress(folderId: String): Pair<Int, Int> =
        db.rawQuery(
            "WITH RECURSIVE sub(id) AS (" +
                "SELECT ? UNION ALL " +
                "SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id WHERE n.isDirectory = 1) " +
                "SELECT COALESCE(SUM(isWatched), 0), COUNT(*) FROM nodes " +
                "WHERE parentId IN (SELECT id FROM sub) AND isDirectory = 0",
            arrayOf(folderId)
        ).use { if (it.moveToFirst()) it.getInt(0) to it.getInt(1) else 0 to 0 }

    /** Derniers fichiers terminés : point de départ pour proposer l'épisode suivant. */
    fun recentlyFinished(limit: Int = 30): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND isWatched = 1 AND lastPlayedAt > 0 " +
                "ORDER BY lastPlayedAt DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { it.mapAll(::readNode) }

    /** Un dossier et tout ce qu'il contient (ou le fichier seul). */
    fun subtree(id: String): List<Node> =
        db.rawQuery(
            "WITH RECURSIVE sub(id) AS (" +
                "SELECT ? UNION ALL SELECT n.id FROM nodes n JOIN sub ON n.parentId = sub.id) " +
                "SELECT * FROM nodes WHERE id IN (SELECT id FROM sub)",
            arrayOf(id)
        ).use { it.mapAll(::readNode) }

    /** Retire un élément et son contenu de la bibliothèque, et recompte son dossier parent. */
    fun deleteSubtree(id: String) {
        val target = node(id) ?: return
        val ids = subtree(id).map { it.id }
        db.beginTransaction()
        try {
            for (each in ids) db.delete("nodes", "id = ?", arrayOf(each))
            target.parentId?.let { parent ->
                db.execSQL(
                    "UPDATE nodes SET childCount = (SELECT COUNT(*) FROM nodes c WHERE c.parentId = ?) WHERE id = ?",
                    arrayOf(parent, parent)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        touched()
    }

    /**
     * Applique un renommage : l'identifiant d'une fiche dépend de son chemin,
     * il change donc avec lui — pour l'élément et pour tout ce qu'il contient.
     * Les fiches gardent leurs jaquettes, résumés et positions de lecture.
     */
    fun applyRename(moves: List<Triple<Node, String, Node>>) {
        // (ancienne fiche, ancien id, nouvelle fiche)
        db.beginTransaction()
        try {
            for ((_, oldId, _) in moves) db.delete("nodes", "id = ?", arrayOf(oldId))
            for ((_, _, fresh) in moves) {
                db.insertWithOnConflict("nodes", null, fresh.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        touched()
    }

    /** Fiches qui portent quelque chose à sauvegarder : reconnues, vues, entamées, ou habillées à la main. */
    fun nodesWorthSaving(): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE (isDirectory = 0 AND (tmdbId > 0 OR posterUrl != '' " +
                "OR lastPositionMs > 0 OR isWatched = 1 OR listedAt > 0)) OR (isDirectory = 1 AND posterUrl != '')",
            null
        ).use { it.mapAll(::readNode) }

    /** Remet les informations sauvegardées sur une fiche existante. Renvoie vrai si elle existait. */
    fun restoreOnto(saved: Node): Boolean {
        val values = ContentValues().apply {
            put("metadataState", saved.metadataState.name)
            put("tmdbId", saved.tmdbId)
            put("title", saved.title)
            put("year", saved.year)
            put("releaseDate", saved.releaseDate)
            put("synopsis", saved.synopsis)
            put("posterUrl", saved.posterUrl)
            put("backdropUrl", saved.backdropUrl)
            put("rating", saved.rating)
            put("mediaType", saved.mediaType)
            put("actors", saved.actors)
            put("director", saved.director)
            put("creditsDone", if (saved.creditsDone) 1 else 0)
            put("collectionId", saved.collectionId)
            put("collectionName", saved.collectionName)
            put("genres", saved.genres)
            put("logoUrl", saved.logoUrl)
            put("lastPositionMs", saved.lastPositionMs)
            put("durationMs", saved.durationMs)
            put("isWatched", if (saved.isWatched) 1 else 0)
            put("lastPlayedAt", saved.lastPlayedAt)
            put("listedAt", saved.listedAt)
        }
        return db.update("nodes", values, "id = ?", arrayOf(saved.id)) > 0
    }

    fun notifyChanged() = touched()

    // ------------------------------------------------------------ ma liste

    /** Met un titre dans « Ma liste », ou l'en retire. */
    fun setListed(id: String, listed: Boolean) {
        db.execSQL(
            "UPDATE nodes SET listedAt = ? WHERE id = ?",
            arrayOf(if (listed) System.currentTimeMillis() else 0L, id)
        )
        touched()
    }

    /** Les titres mis de côté, le dernier ajouté en premier. */
    fun myList(limit: Int = 60): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE listedAt > 0 ORDER BY listedAt DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { it.mapAll(::readNode) }

    // ------------------------------------------- non reconnus, catalogue

    /** Les vidéos que TMDB n'a pas identifiées, rangées par disque puis par nom. */
    fun unmatched(): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE isDirectory = 0 AND tmdbId = 0 AND metadataState != 'PENDING' " +
                "ORDER BY volumeId, name COLLATE NOCASE ASC LIMIT 600",
            null
        ).use { it.mapAll(::readNode) }

    /** Toutes les vidéos d'un disque, dans l'ordre de leurs dossiers. */
    fun filesOf(volumeId: String): List<Node> =
        db.rawQuery(
            "SELECT * FROM nodes WHERE volumeId = ? AND isDirectory = 0 ORDER BY relativePath COLLATE NOCASE ASC",
            arrayOf(volumeId)
        ).use { it.mapAll(::readNode) }

    // -------------------------------------------------------- statistiques

    fun stats(): LibraryStats {
        fun long(sql: String): Long = db.rawQuery(sql, null).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        val files = long("SELECT COUNT(*) FROM nodes WHERE isDirectory = 0").toInt()
        val bytes = long("SELECT COALESCE(SUM(sizeBytes), 0) FROM nodes WHERE isDirectory = 0")
        val watched = long("SELECT COUNT(*) FROM nodes WHERE isDirectory = 0 AND isWatched = 1").toInt()
        val started = long("SELECT COUNT(*) FROM nodes WHERE isDirectory = 0 AND isWatched = 0 AND lastPositionMs > 0").toInt()
        val watchedMs = long("SELECT COALESCE(SUM(durationMs), 0) FROM nodes WHERE isDirectory = 0 AND isWatched = 1") +
            long("SELECT COALESCE(SUM(lastPositionMs), 0) FROM nodes WHERE isDirectory = 0 AND isWatched = 0")

        // Genres et réalisateurs des titres vus ou entamés
        val genreCount = HashMap<Int, Int>()
        val directorCount = HashMap<String, Int>()
        val monthCount = HashMap<String, Int>()
        val month = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.ROOT)
        db.rawQuery(
            "SELECT genres, director, lastPlayedAt FROM nodes WHERE isDirectory = 0 AND (isWatched = 1 OR lastPositionMs > 0)",
            null
        ).use { c ->
            while (c.moveToNext()) {
                c.getString(0).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }
                    .forEach { genreCount[it] = (genreCount[it] ?: 0) + 1 }
                c.getString(1).orEmpty().split(',').map { it.trim() }.filter { it.isNotBlank() }
                    .forEach { directorCount[it] = (directorCount[it] ?: 0) + 1 }
                val played = c.getLong(2)
                if (played > 0) {
                    val key = month.format(java.util.Date(played))
                    monthCount[key] = (monthCount[key] ?: 0) + 1
                }
            }
        }
        // Les six derniers mois, même vides, pour un graphique sans trous
        val calendar = java.util.Calendar.getInstance()
        val lastMonths = (0 until 6).map {
            val key = month.format(calendar.time)
            calendar.add(java.util.Calendar.MONTH, -1)
            key to (monthCount[key] ?: 0)
        }.reversed()

        return LibraryStats(
            files = files,
            totalBytes = bytes,
            watched = watched,
            inProgress = started,
            watchedMs = watchedMs,
            genres = genreCount.entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
            months = lastMonths,
            directors = directorCount.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        )
    }

    /**
     * Recopie sur un disque la config d'un autre : fiches reconnues ou
     * corrigées, jaquettes choisies, épisodes vus, positions de lecture.
     *
     * Les fichiers se retrouvent par leur chemin ; à défaut, par leur nom
     * s'il n'existe qu'une fois sur le nouveau disque. Renvoie le nombre de
     * fiches reprises et le nombre de fiches à reprendre.
     */
    fun adoptConfig(fromVolume: String, toVolume: String): Pair<Int, Int> {
        val saved = db.rawQuery(
            "SELECT * FROM nodes WHERE volumeId = ? AND ((isDirectory = 0 AND (tmdbId > 0 OR posterUrl != '' " +
                "OR lastPositionMs > 0 OR isWatched = 1 OR listedAt > 0)) OR (isDirectory = 1 AND posterUrl != ''))",
            arrayOf(fromVolume)
        ).use { it.mapAll(::readNode) }

        var done = 0
        db.beginTransaction()
        try {
            for (node in saved) {
                val samePath = com.cinetv.data.storage.MediaLibraryScanner.nodeId(toVolume, node.relativePath)
                if (restoreOnto(node.copy(id = samePath))) { done++; continue }
                // Rangé ailleurs sur le nouveau disque : on le cherche par son nom
                val byName = db.rawQuery(
                    "SELECT id FROM nodes WHERE volumeId = ? AND isDirectory = ? AND name = ? COLLATE NOCASE LIMIT 2",
                    arrayOf(toVolume, if (node.isDirectory) "1" else "0", node.name)
                ).use { cursor ->
                    val ids = ArrayList<String>()
                    while (cursor.moveToNext()) ids += cursor.getString(0)
                    ids
                }
                if (byName.size == 1 && restoreOnto(node.copy(id = byName[0]))) done++
            }
            // Le thème du disque suit aussi
            volume(fromVolume)?.let { old -> setVolumeTheme(toVolume, old.theme) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        touched()
        return done to saved.size
    }

    /** Retire un titre de « Reprendre » : sa position de lecture est oubliée. */
    fun clearResume(id: String) {
        db.execSQL("UPDATE nodes SET lastPositionMs = 0 WHERE id = ?", arrayOf(id))
        // Si c'était un « épisode suivant », on oublie aussi ce qui le faisait proposer
        node(id)?.parentId?.let { parent ->
            db.execSQL(
                "UPDATE nodes SET lastPlayedAt = 0 WHERE parentId = ? AND isWatched = 1",
                arrayOf(parent)
            )
        }
        touched()
    }

    /** Vide toute la rangée « Reprendre ». */
    fun clearAllResume() {
        db.execSQL("UPDATE nodes SET lastPositionMs = 0 WHERE isDirectory = 0 AND lastPositionMs > 0 AND isWatched = 0")
        db.execSQL("UPDATE nodes SET lastPlayedAt = 0 WHERE isDirectory = 0 AND isWatched = 1")
        touched()
    }

    /** Remplace uniquement les images, en laissant le reste de la fiche intact. */
    fun replaceArtwork(id: String, posterUrl: String, backdropUrl: String) {
        db.execSQL(
            "UPDATE nodes SET posterUrl = ?, backdropUrl = ?, metadataState = 'RESOLVED' WHERE id = ?",
            arrayOf(posterUrl, backdropUrl, id)
        )
        touched()
    }

    fun savePosition(id: String, positionMs: Long, durationMs: Long) {
        db.execSQL(
            "UPDATE nodes SET lastPositionMs = ?, durationMs = ?, lastPlayedAt = ? WHERE id = ?",
            arrayOf(positionMs, durationMs, System.currentTimeMillis(), id)
        )
        touched()
    }

    fun setWatched(id: String, watched: Boolean) {
        db.execSQL(
            "UPDATE nodes SET isWatched = ?, lastPositionMs = 0 WHERE id = ?",
            arrayOf(if (watched) 1 else 0, id)
        )
        touched()
    }

    // --------------------------------------------------------- conversions

    private fun Volume.toValues() = ContentValues().apply {
        put("id", id)
        put("label", label)
        put("theme", theme.name)
        put("isConnected", if (isConnected) 1 else 0)
        put("lastSeenAt", lastSeenAt)
        put("videoCount", videoCount)
        put("folderCount", folderCount)
        put("orderIndex", orderIndex)
        put("freeBytes", freeBytes)
        put("totalBytes", totalBytes)
    }

    private fun Node.toValues() = ContentValues().apply {
        put("id", id)
        put("volumeId", volumeId)
        put("parentId", parentId)
        put("name", name)
        put("relativePath", relativePath)
        put("documentUri", documentUri)
        put("isDirectory", if (isDirectory) 1 else 0)
        put("sizeBytes", sizeBytes)
        put("childCount", childCount)
        put("isAvailable", if (isAvailable) 1 else 0)
        put("metadataState", metadataState.name)
        put("tmdbId", tmdbId)
        put("title", title)
        put("year", year)
        put("releaseDate", releaseDate)
        put("synopsis", synopsis)
        put("posterUrl", posterUrl)
        put("backdropUrl", backdropUrl)
        put("rating", rating)
        put("lastPositionMs", lastPositionMs)
        put("durationMs", durationMs)
        put("isWatched", if (isWatched) 1 else 0)
        put("lastPlayedAt", lastPlayedAt)
        put("addedAt", addedAt)
        put("mediaType", mediaType)
        put("actors", actors)
        put("director", director)
        put("creditsDone", if (creditsDone) 1 else 0)
        put("collectionId", collectionId)
        put("collectionName", collectionName)
        put("genres", genres)
        put("logoUrl", logoUrl)
        put("listedAt", listedAt)
        put("quality", quality)
    }

    private fun readVolume(c: Cursor) = Volume(
        id = c.str("id"),
        label = c.str("label"),
        theme = DriveTheme.parse(c.str("theme")),
        isConnected = c.int("isConnected") == 1,
        lastSeenAt = c.long("lastSeenAt"),
        videoCount = c.int("videoCount"),
        folderCount = c.int("folderCount"),
        orderIndex = c.int("orderIndex"),
        freeBytes = c.long("freeBytes"),
        totalBytes = c.long("totalBytes")
    )

    private fun readNode(c: Cursor) = Node(
        id = c.str("id"),
        volumeId = c.str("volumeId"),
        parentId = c.strOrNull("parentId"),
        name = c.str("name"),
        relativePath = c.str("relativePath"),
        documentUri = c.str("documentUri"),
        isDirectory = c.int("isDirectory") == 1,
        sizeBytes = c.long("sizeBytes"),
        childCount = c.int("childCount"),
        isAvailable = c.int("isAvailable") == 1,
        metadataState = MetaState.parse(c.str("metadataState")),
        tmdbId = c.int("tmdbId"),
        title = c.str("title"),
        year = c.int("year"),
        releaseDate = c.str("releaseDate"),
        synopsis = c.str("synopsis"),
        posterUrl = c.str("posterUrl"),
        backdropUrl = c.str("backdropUrl"),
        rating = c.float("rating"),
        lastPositionMs = c.long("lastPositionMs"),
        durationMs = c.long("durationMs"),
        isWatched = c.int("isWatched") == 1,
        lastPlayedAt = c.long("lastPlayedAt"),
        addedAt = c.long("addedAt"),
        mediaType = c.str("mediaType"),
        actors = c.str("actors"),
        director = c.str("director"),
        creditsDone = c.int("creditsDone") == 1,
        collectionId = c.int("collectionId"),
        collectionName = c.str("collectionName"),
        genres = c.str("genres"),
        logoUrl = c.str("logoUrl"),
        listedAt = c.long("listedAt"),
        quality = c.int("quality")
    )
}

// --------------------------------------------------------- aides Cursor

private fun Cursor.str(column: String): String =
    getColumnIndex(column).let { if (it < 0 || isNull(it)) "" else getString(it) }

private fun Cursor.strOrNull(column: String): String? =
    getColumnIndex(column).let { if (it < 0 || isNull(it)) null else getString(it) }

private fun Cursor.int(column: String): Int =
    getColumnIndex(column).let { if (it < 0 || isNull(it)) 0 else getInt(it) }

private fun Cursor.long(column: String): Long =
    getColumnIndex(column).let { if (it < 0 || isNull(it)) 0L else getLong(it) }

private fun Cursor.float(column: String): Float =
    getColumnIndex(column).let { if (it < 0 || isNull(it)) 0f else getFloat(it) }

private fun <T> Cursor.mapAll(read: (Cursor) -> T): List<T> {
    val out = ArrayList<T>(count)
    while (moveToNext()) out.add(read(this))
    return out
}
