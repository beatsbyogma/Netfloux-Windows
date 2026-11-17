package android.content

import com.cinetv.desktop.AppDirs
import java.io.File

/**
 * Le « contexte » d'Android, réduit à ce dont Netfloux se sert : des dossiers où ranger
 * ses fichiers, des réglages, et les ressources de l'application. Sur Windows il n'y en
 * a qu'un, partagé par toute l'application.
 */
open class Context {
    companion object {
        const val MODE_PRIVATE = 0
        const val STORAGE_SERVICE = "storage"
    }

    open val applicationContext: Context get() = this
    open val packageName: String get() = "com.cinetv"
    open val filesDir: File get() = File(AppDirs.data, "files").also { it.mkdirs() }
    open val cacheDir: File get() = AppDirs.cache
    val resources: android.content.res.Resources get() = android.content.res.Resources

    fun getDatabasePath(name: String): File = File(AppDirs.data, name)

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = FilePreferences.of(name)

    fun getSystemService(name: String): Any? = null
}

/** L'unique contexte de Netfloux sur Windows. */
object DesktopContext : Context()
