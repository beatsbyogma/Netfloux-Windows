package com.cinetv.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cinetv.data.model.Node
import java.io.File
import java.security.MessageDigest

/**
 * Construit l'arborescence d'un disque en le parcourant dossier par dossier.
 * Les dossiers sont reconstruits à partir du chemin des vidéos, ce qui préserve
 * exactement le rangement de l'utilisateur.
 */
class MediaLibraryScanner(private val context: Context) {

    companion object {
        private const val TAG = "MediaScanner"
        private const val MIN_SIZE_BYTES = 20L * 1024 * 1024   // ignore les extraits
        private const val MAX_DEPTH = 12

        val VIDEO_EXTENSIONS = setOf(
            "mkv", "avi", "mp4", "m4v", "mov", "wmv", "flv",
            "ts", "m2ts", "mpg", "mpeg", "divx", "webm", "vob", "ogm", "rmvb", "asf", "3gp"
        )

        private val IGNORED_FOLDERS = setOf(
            "System Volume Information", "\$RECYCLE.BIN", "RECYCLER",
            "LOST.DIR", "Android", ".Trash-1000", "found.000",
            "Windows", "Program Files", "Program Files (x86)", "ProgramData", "AppData",
            "\$WinREAgent", "\$Windows.~BT", "\$Windows.~WS", "Recovery", "PerfLogs", "msdownld.tmp"
        )

        /** Identifiant déterministe : même disque + même chemin = même fiche. */
        fun nodeId(volumeId: String, relativePath: String): String =
            MessageDigest.getInstance("SHA-1")
                .digest("$volumeId|$relativePath".toByteArray())
                .joinToString("") { "%02x".format(it) }
    }

    data class Output(
        val nodes: List<Node>,
        val videoCount: Int,
        val folderCount: Int,
        val source: String
    )

    /** Un fichier trouvé, avant construction de l'arborescence. */
    data class Found(
        val name: String,
        val folderPath: String,      // "Films/Action", "" à la racine
        val uri: String,
        val size: Long,
        val durationMs: Long
    )

    fun scan(volumeId: String, onProgress: (Int) -> Unit = {}): Output {
        val root = StorageAccess.mountPointFor(volumeId) ?: run {
            Log.w(TAG, "NFX scan $volumeId : disque introuvable")
            return assemble(volumeId, emptyList(), "lecture directe")
        }
        Log.w(TAG, "NFX scan $volumeId : lecture de ${root.path}")
        val out = mutableListOf<Found>()

        fun walk(dir: File, relative: String, depth: Int) {
            if (depth > MAX_DEPTH) return
            val children = try { dir.listFiles() } catch (e: SecurityException) { null } ?: return
            for (child in children) {
                val name = child.name
                if (name.startsWith(".") || name.startsWith("~$")) continue
                if (child.isDirectory) {
                    if (name in IGNORED_FOLDERS || child.isHidden) continue
                    walk(child, if (relative.isEmpty()) name else "$relative/$name", depth + 1)
                } else {
                    if (child.extension.lowercase() !in VIDEO_EXTENSIONS) continue
                    val size = child.length()
                    if (size in 1 until MIN_SIZE_BYTES) continue
                    out += Found(
                        name = name,
                        folderPath = relative,
                        uri = Uri.fromFile(child).toString(),
                        size = size,
                        durationMs = 0L
                    )
                    if (out.size % 25 == 0) onProgress(out.size)
                }
            }
        }

        runCatching { walk(root, "", 0) }
            .onFailure { Log.w(TAG, "NFX scan $volumeId : lecture interrompue (${it.message})") }
        onProgress(out.size)
        Log.w(TAG, "NFX scan $volumeId : ${out.size} vidéos")
        return assemble(volumeId, out, "lecture directe")
    }

    // ------------------------------------------- construction de l'arborescence

    fun assemble(volumeId: String, files: List<Found>, source: String): Output {
        val nodes = LinkedHashMap<String, Node>()

        /** Crée les dossiers du chemin s'ils manquent, et renvoie l'id du dernier. */
        fun ensureFolders(path: String): String? {
            if (path.isEmpty()) return null
            var parentId: String? = null
            val builder = StringBuilder()
            for (segment in path.split('/').filter { it.isNotBlank() }) {
                if (builder.isNotEmpty()) builder.append('/')
                builder.append(segment)
                val current = builder.toString()
                val id = nodeId(volumeId, current)
                if (id !in nodes) {
                    nodes[id] = Node(
                        id = id,
                        volumeId = volumeId,
                        parentId = parentId,
                        name = segment,
                        relativePath = current,
                        documentUri = "",
                        isDirectory = true,
                        isAvailable = true
                    )
                }
                parentId = id
            }
            return parentId
        }

        for (f in files) {
            val parentId = ensureFolders(f.folderPath)
            val relative = if (f.folderPath.isEmpty()) f.name else "${f.folderPath}/${f.name}"
            val id = nodeId(volumeId, relative)
            nodes[id] = Node(
                id = id,
                volumeId = volumeId,
                parentId = parentId,
                name = f.name,
                relativePath = relative,
                documentUri = f.uri,
                isDirectory = false,
                sizeBytes = f.size,
                durationMs = f.durationMs,
                isAvailable = true
            )
        }

        // Nombre d'éléments directs par dossier, pour l'affichage des cartes
        val directChildren = nodes.values.groupingBy { it.parentId }.eachCount()
        val withCounts = nodes.values.map { node ->
            if (node.isDirectory) node.copy(childCount = directChildren[node.id] ?: 0) else node
        }

        return Output(
            nodes = withCounts,
            videoCount = withCounts.count { !it.isDirectory },
            folderCount = withCounts.count { it.isDirectory },
            source = source
        )
    }
}
