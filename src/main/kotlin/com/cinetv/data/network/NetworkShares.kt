package com.cinetv.data.network

import android.content.Context
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import org.json.JSONArray
import org.json.JSONObject
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Un dossier partagé sur le réseau local (partage Windows, NAS, mini PC…).
 * Pour l'application c'est un disque comme un autre : seule la façon d'y
 * accéder change.
 */
data class NetworkShare(
    val label: String,
    val host: String,          // adresse IP ou nom de la machine
    val share: String,         // nom du dossier partagé
    val user: String = "",     // vide = accès invité
    val password: String = ""
) {
    /** Identifiant du disque dans la bibliothèque. */
    val volumeId: String get() = volumeIdOf(host, share)

    /** Adresse de la racine du partage, toujours terminée par une barre. */
    val rootUrl: String get() = "smb://$host/$share/"

    companion object {
        const val PREFIX = "smb:"
        fun volumeIdOf(host: String, share: String) =
            PREFIX + host.trim().lowercase() + "/" + share.trim().trim('/').lowercase()
        fun isNetwork(volumeId: String) = volumeId.startsWith(PREFIX)
    }
}

/**
 * Les partages enregistrés et leurs connexions.
 *
 * Les identifiants sont gardés dans les préférences privées de l'application,
 * sur la TV uniquement ; les mots de passe y sont chiffrés (voir [SecretBox]).
 */
class NetworkShares(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("netfloux_shares", Context.MODE_PRIVATE)

    init {
        // Les mots de passe rangés en clair par les versions précédentes sont chiffrés au passage
        runCatching {
            val array = JSONArray(prefs.getString(KEY, "[]"))
            val plain = (0 until array.length()).any { i ->
                val stored = array.getJSONObject(i).optString("password")
                stored.isNotEmpty() && !SecretBox.isSealed(stored)
            }
            if (plain) write(all())
        }
    }

    fun all(): List<NetworkShare> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            NetworkShare(
                label = o.optString("label"),
                host = o.optString("host"),
                share = o.optString("share"),
                user = o.optString("user"),
                password = SecretBox.open(o.optString("password"))
            )
        }
    }.getOrDefault(emptyList())

    fun find(volumeId: String): NetworkShare? = all().firstOrNull { it.volumeId == volumeId }

    fun save(share: NetworkShare) {
        write(all().filterNot { it.volumeId == share.volumeId } + share)
        contexts.remove(share.volumeId)
    }

    fun remove(volumeId: String) {
        write(all().filterNot { it.volumeId == volumeId })
        contexts.remove(volumeId)
    }

    private fun write(shares: List<NetworkShare>) {
        val array = JSONArray()
        shares.forEach { s ->
            array.put(
                JSONObject()
                    .put("label", s.label).put("host", s.host).put("share", s.share)
                    .put("user", s.user).put("password", runCatching { SecretBox.seal(s.password) }.getOrDefault(s.password))
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "shares"

        /** Une connexion par partage, gardée ouverte : la rouvrir à chaque fichier serait lent. */
        private val contexts = ConcurrentHashMap<String, CIFSContext>()

        /** Connexion de base, sans identifiants : sert aussi à explorer le réseau. */
        val baseContext: BaseContext get() = base

        private val base: BaseContext by lazy {
            val props = Properties().apply {
                // Protocoles récents uniquement : l'ancien SMB1 est lent et désactivé sur Windows
                setProperty("jcifs.smb.client.minVersion", "SMB202")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                setProperty("jcifs.smb.client.connTimeout", "5000")
                setProperty("jcifs.smb.client.responseTimeout", "20000")
                setProperty("jcifs.smb.client.soTimeout", "30000")
                setProperty("jcifs.resolveOrder", "DNS,BCAST")
                setProperty("jcifs.smb.client.dfs.disabled", "true")
                // Recherche du nom des machines : on n'attend pas celles qui ne répondent pas
                setProperty("jcifs.netbios.soTimeout", "700")
                setProperty("jcifs.netbios.retryCount", "1")
                setProperty("jcifs.netbios.retryTimeout", "500")
            }
            BaseContext(PropertyConfiguration(props))
        }

        fun contextFor(share: NetworkShare): CIFSContext =
            contexts.getOrPut(share.volumeId) { newContext(share) }

        private fun newContext(share: NetworkShare): CIFSContext =
            if (share.user.isBlank()) {
                base.withGuestCrendentials()
            } else {
                // « DOMAINE\utilisateur » est accepté, le domaine est facultatif
                val domain = share.user.substringBefore('\\', "")
                val name = share.user.substringAfter('\\')
                base.withCredentials(NtlmPasswordAuthenticator(domain, name, share.password))
            }

        /** Ouvre un fichier ou un dossier du partage, à partir de son chemin relatif. */
        fun open(share: NetworkShare, relativePath: String = ""): SmbFile =
            SmbFile(share.rootUrl + relativePath, contextFor(share))

        /** Oublie la connexion d'un partage, pour repartir de zéro après une erreur. */
        fun reset(volumeId: String) { contexts.remove(volumeId) }
    }
}
