package com.cinetv.data.storage

import android.content.Context
import android.util.Log
import com.cinetv.data.model.Node
import com.cinetv.data.network.NetworkShare
import com.cinetv.data.network.NetworkShares
import java.awt.Desktop
import java.io.File

/**
 * Renommer et supprimer pour de vrai, sur le disque ou le partage.
 *
 * Chaque fonction renvoie null quand tout s'est bien passé, sinon la raison de l'échec
 * en clair. Sur un disque du PC, une suppression passe par la Corbeille de Windows
 * quand le disque en a une ; sur un partage réseau, elle est définitive.
 */
class FileOps(@Suppress("unused") private val context: Context, private val shares: NetworkShares) {

    companion object {
        private val FORBIDDEN = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
        const val NEEDS_ACCESS = "Windows n'autorise pas Netfloux à modifier ce disque"
        const val READ_ONLY = "Ce disque est en lecture seule, ou le fichier est ouvert dans un autre programme"
        private const val TAG = "NFX"

        fun checkName(name: String): String? = when {
            name.isBlank() -> "Le nom ne peut pas être vide"
            name.any { it in FORBIDDEN } -> "Le nom contient un caractère interdit ( \\ / : * ? \" < > | )"
            name.endsWith(".") || name.endsWith(" ") -> "Le nom ne peut pas finir par un point ou un espace"
            else -> null
        }

        fun canModifyLocal(): Boolean = true
    }

    private fun isNetwork(node: Node) = NetworkShare.isNetwork(node.volumeId)

    private fun localFile(node: Node): File? = StorageAccess.fileFor(node.volumeId, node.relativePath)

    private fun smbPath(node: Node, relative: String = node.relativePath) =
        if (node.isDirectory) "$relative/" else relative

    fun delete(node: Node): String? = try {
        if (isNetwork(node)) {
            val share = shares.find(node.volumeId) ?: return "Partage réseau oublié"
            NetworkShares.open(share, smbPath(node)).use { it.delete() }
            null
        } else {
            val file = localFile(node)
            when {
                file == null || !file.exists() -> "Fichier introuvable sur le disque"
                !deleteLocal(file) -> READ_ONLY
                else -> null
            }
        }
    } catch (e: Exception) {
        "Suppression impossible : ${e.message ?: e.javaClass.simpleName}"
    }

    private fun deleteLocal(file: File): Boolean {
        val trashed = runCatching {
            Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH) &&
                Desktop.getDesktop().moveToTrash(file)
        }.getOrDefault(false)
        if (!trashed && file.exists()) {
            val all = if (file.isDirectory) file.walkBottomUp().toList() else listOf(file)
            for (item in all) if (item.exists()) item.delete()
        }
        val gone = !file.exists()
        Log.w(TAG, "Suppression de ${file.path} : corbeille = $trashed, réussie = $gone")
        return gone
    }

    /** [newName] est le nom complet, extension comprise pour un fichier. */
    fun rename(node: Node, newName: String): String? {
        checkName(newName)?.let { return it }
        val parent = node.relativePath.substringBeforeLast('/', "")
        val target = if (parent.isEmpty()) newName else "$parent/$newName"
        return try {
            if (isNetwork(node)) {
                val share = shares.find(node.volumeId) ?: return "Partage réseau oublié"
                NetworkShares.open(share, smbPath(node)).use { source ->
                    NetworkShares.open(share, smbPath(node, target)).use { destination ->
                        if (destination.exists()) return "Un élément porte déjà ce nom"
                        source.renameTo(destination)
                    }
                }
                null
            } else {
                val file = localFile(node)
                val destination = file?.parentFile?.let { File(it, newName) }
                when {
                    file == null || destination == null || !file.exists() -> "Fichier introuvable sur le disque"
                    destination.exists() && !destination.path.equals(file.path, ignoreCase = true) -> "Un élément porte déjà ce nom"
                    !file.renameTo(destination) -> READ_ONLY
                    else -> null
                }
            }
        } catch (e: Exception) {
            "Renommage impossible : ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
