package com.cinetv.data.network

import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/** Une machine du réseau qui partage des dossiers. */
data class FoundHost(val address: String, val name: String) {
    val label: String get() = if (name.isBlank()) address else "$name · $address"
}

/**
 * Trouve les machines de la maison qui partagent des dossiers, puis liste
 * leurs partages — pour choisir dans une liste plutôt que de tout taper.
 */
object NetworkDiscovery {

    private const val SMB_PORT = 445

    /** Adresse de la TV sur le réseau local, ex. « 192.168.1.17 ». */
    private fun ownAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /**
     * Frappe à la porte « partage de fichiers » des 254 adresses voisines.
     * Quelques secondes au plus : les essais partent par paquets.
     */
    suspend fun hosts(): List<FoundHost> = withContext(Dispatchers.IO) {
        val own = ownAddress() ?: return@withContext emptyList()
        val prefix = own.substringBeforeLast('.')
        val gate = Semaphore(64)

        val open = coroutineScope {
            (1..254).map { last ->
                async {
                    val candidate = "$prefix.$last"
                    if (candidate == own) return@async null
                    gate.withPermit {
                        runCatching {
                            Socket().use { it.connect(InetSocketAddress(candidate, SMB_PORT), 450) }
                            candidate
                        }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }

        open.map { address -> FoundHost(address, nameOf(address)) }
            .sortedBy { it.address.substringAfterLast('.').toIntOrNull() ?: 0 }
    }

    /** Nom de la machine, quand elle veut bien le donner. */
    private fun nameOf(address: String): String = runCatching {
        NetworkShares.baseContext.nameServiceClient
            .getNbtAllByAddress(address)
            ?.firstOrNull { it.hostName.isNotBlank() && !it.hostName.first().isDigit() }
            ?.hostName
            .orEmpty()
    }.getOrDefault("")

    /** Dossiers partagés par une machine, pour le compte donné. Lève une exception si elle refuse. */
    suspend fun shares(host: String, user: String, password: String): List<String> =
        withContext(Dispatchers.IO) {
            val context = if (user.isBlank()) {
                NetworkShares.baseContext.withGuestCrendentials()
            } else {
                val domain = user.substringBefore('\\', "")
                val name = user.substringAfter('\\')
                NetworkShares.baseContext.withCredentials(NtlmPasswordAuthenticator(domain, name, password))
            }
            SmbFile("smb://$host/", context).use { root ->
                root.listFiles().orEmpty()
                    .map { it.name.trimEnd('/') }
                    // Les partages finissant par $ sont ceux du système (C$, ADMIN$, IPC$)
                    .filter { it.isNotBlank() && !it.endsWith("$") }
                    .sortedBy { it.lowercase() }
            }
        }
}
