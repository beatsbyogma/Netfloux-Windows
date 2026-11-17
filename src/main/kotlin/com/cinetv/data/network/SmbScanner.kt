package com.cinetv.data.network

import android.content.Context
import android.util.Log
import com.cinetv.data.storage.MediaLibraryScanner
import jcifs.smb.SmbFile

/**
 * Parcourt un dossier partagé et en reconstruit l'arborescence, exactement
 * comme pour un disque USB : mêmes dossiers, mêmes fichiers, même rangement.
 */
class SmbScanner(private val context: Context) {

    companion object {
        private const val TAG = "SmbScanner"
        private const val MIN_SIZE_BYTES = 20L * 1024 * 1024
        private const val MAX_DEPTH = 12
        private val IGNORED = setOf(
            "System Volume Information", "\$RECYCLE.BIN", "RECYCLER", "#recycle",
            "@eaDir", "lost+found", ".Trash-1000"
        )
    }

    fun scan(share: NetworkShare, onProgress: (Int) -> Unit = {}): MediaLibraryScanner.Output {
        val found = ArrayList<MediaLibraryScanner.Found>()
        NetworkShares.open(share).use { root -> walk(share, root, "", 0, found, onProgress) }
        return MediaLibraryScanner(context).assemble(share.volumeId, found, "réseau")
    }

    private fun walk(
        share: NetworkShare,
        dir: SmbFile,
        path: String,
        depth: Int,
        out: MutableList<MediaLibraryScanner.Found>,
        onProgress: (Int) -> Unit
    ) {
        if (depth > MAX_DEPTH) return
        val children = try {
            dir.listFiles() ?: return
        } catch (e: Exception) {
            // Un dossier illisible ne doit pas faire échouer tout le reste
            Log.w(TAG, "Dossier illisible : $path (${e.message})")
            if (depth == 0) throw e
            return
        }

        for (child in children) {
            try {
                val name = child.name.trimEnd('/')
                if (name.isEmpty() || name.startsWith(".") || name in IGNORED) continue
                val relative = if (path.isEmpty()) name else "$path/$name"

                if (child.isDirectory) {
                    walk(share, child, relative, depth + 1, out, onProgress)
                } else {
                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (extension !in MediaLibraryScanner.VIDEO_EXTENSIONS) continue
                    val size = child.length()
                    if (size in 1 until MIN_SIZE_BYTES) continue
                    out += MediaLibraryScanner.Found(
                        name = name,
                        folderPath = path,
                        uri = share.rootUrl + relative,
                        size = size,
                        durationMs = 0
                    )
                    if (out.size % 10 == 0) onProgress(out.size)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Élément ignoré dans $path : ${e.message}")
            } finally {
                runCatching { child.close() }
            }
        }
    }
}
