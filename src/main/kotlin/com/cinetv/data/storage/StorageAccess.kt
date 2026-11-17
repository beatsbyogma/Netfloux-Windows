package com.cinetv.data.storage

import android.content.Context
import android.content.FilePreferences
import java.io.File
import java.nio.file.Files
import javax.swing.filechooser.FileSystemView

/**
 * Les disques du PC.
 *
 * Chaque disque est reconnu par son numéro de série de volume (« drv-1a2b3c4d »), qui ne
 * change pas quand Windows lui attribue une autre lettre. Seuls les disques que
 * l'utilisateur a choisis (Paramètres › Disques affichés) entrent dans Netfloux ;
 * les autres — disque système, disques de travail… — restent invisibles.
 */
object StorageAccess {

    /** Un disque vu par Windows, choisi ou non. */
    data class Drive(
        val id: String,
        val root: File,
        val label: String,
        val letter: String,
        val fileSystem: String,
        val total: Long,
        val free: Long,
        val isSystem: Boolean
    )

    private val prefs by lazy { FilePreferences.of("drives") }

    // ---------------------------------------------------------------- choix

    /** Les disques à afficher, par identifiant. */
    var shown: Set<String>
        get() = prefs.getStringSet("shown", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("shown", value).apply() }

    /** Les disques déjà proposés à l'utilisateur (un nouveau disque est signalé une seule fois). */
    var known: Set<String>
        get() = prefs.getStringSet("known", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("known", value).apply() }

    /** Vrai tant que l'utilisateur n'a jamais choisi ses disques. */
    var firstChoiceDone: Boolean
        get() = prefs.getBoolean("chosen", false)
        set(value) { prefs.edit().putBoolean("chosen", value).apply() }

    fun isShown(volumeId: String): Boolean = volumeId in shown

    // ------------------------------------------------------------ détection

    @Volatile private var cache: Pair<Long, List<Drive>> = 0L to emptyList()

    /** Tous les disques branchés au PC (relus au plus toutes les 1,5 s). */
    fun drives(force: Boolean = false): List<Drive> {
        val now = System.currentTimeMillis()
        cache.let { (at, list) -> if (!force && now - at < 1_500) return list }
        val system = System.getenv("SystemDrive")?.uppercase()?.trimEnd('\\')?.plus("\\") ?: "C:\\"
        val view = runCatching { FileSystemView.getFileSystemView() }.getOrNull()
        val found = File.listRoots().orEmpty().mapNotNull { root ->
            runCatching {
                if (view?.isFloppyDrive(root) == true) return@runCatching null
                if (!root.exists()) return@runCatching null
                val store = Files.getFileStore(root.toPath())
                val serial = (store.getAttribute("volume:vsn") as? Number)?.toInt() ?: return@runCatching null
                val letter = root.path.trimEnd('\\', '/')
                val name = store.name().orEmpty().ifBlank {
                    view?.getSystemDisplayName(root)?.substringBefore(" (")?.takeIf { it.isNotBlank() } ?: "Disque $letter"
                }
                Drive(
                    id = "drv-%08x".format(serial),
                    root = root,
                    label = name,
                    letter = letter,
                    fileSystem = store.type().orEmpty(),
                    total = runCatching { store.totalSpace }.getOrDefault(0L),
                    free = runCatching { store.usableSpace }.getOrDefault(0L),
                    isSystem = root.path.equals(system, ignoreCase = true)
                )
            }.getOrNull()
        }.distinctBy { it.id }
        cache = now to found
        return found
    }

    private fun drive(volumeId: String): Drive? = drives().firstOrNull { it.id == canonical(volumeId) }

    // ------------------------------------------------- ce que le code partagé appelle

    val requiredPermission: String = ""

    /** Sur le PC, aucune autorisation à demander. */
    fun hasPermission(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = true

    fun canonical(volumeName: String): String = volumeName.trim().lowercase()

    fun mediaVolumes(@Suppress("UNUSED_PARAMETER") context: Context): List<String> = drives().map { it.id }

    /** Les disques choisis et branchés. */
    fun removableMediaVolumes(@Suppress("UNUSED_PARAMETER") context: Context): List<String> {
        val chosen = shown
        return drives(force = true).map { it.id }.filter { it in chosen }
    }

    fun describeVolume(@Suppress("UNUSED_PARAMETER") context: Context, volumeName: String): String =
        drive(volumeName)?.label ?: "Disque"

    fun spaceOf(@Suppress("UNUSED_PARAMETER") context: Context, volumeName: String): Pair<Long, Long>? =
        drive(volumeName)?.takeIf { it.total > 0 }?.let { it.free to it.total }

    fun isVolumePresent(@Suppress("UNUSED_PARAMETER") context: Context, volumeName: String): Boolean =
        drive(volumeName) != null

    /** La racine actuelle du disque (« E:\ »), quelle que soit la lettre que Windows lui a donnée aujourd'hui. */
    fun mountPointFor(volumeName: String): File? = drive(volumeName)?.root

    /** Sert de repli sur la TV ; sur le PC, la liste des disques choisis suffit. */
    fun readableMountPoints(@Suppress("UNUSED_PARAMETER") context: Context): List<File> = emptyList()

    /** Le fichier d'un élément de la bibliothèque, là où il se trouve aujourd'hui. */
    fun fileFor(volumeId: String, relativePath: String): File? =
        mountPointFor(volumeId)?.let { File(it, relativePath.replace('/', File.separatorChar)) }
}
