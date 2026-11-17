package com.cinetv.data.storage

import android.content.Context
import com.cinetv.data.model.Node
import com.cinetv.data.network.NetworkShares
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

/** Un fichier de sous-titres posé à côté du film, prêt à être lu. */
data class ExternalSubtitle(
    val file: File,          // copie locale, en UTF-8
    val mimeType: String,
    val language: String,    // « fr », « en »… ou vide
    val label: String
)

/**
 * Cherche les sous-titres qui accompagnent un film : même dossier, même nom,
 * extension .srt, .ass, .ssa ou .vtt — « Film.srt », « Film.fr.srt »…
 *
 * Chaque sous-titre est recopié dans le cache de l'app en UTF-8 : beaucoup de
 * .srt français sont encodés à l'ancienne, et s'afficheraient avec des
 * accents cassés.
 */
object ExternalSubtitles {

    private val TYPES = mapOf(
        "srt" to "application/x-subrip",
        "ass" to "text/x-ssa",
        "ssa" to "text/x-ssa",
        "vtt" to "text/vtt"
    )
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val LANGUAGES = mapOf(
        "fr" to "fr", "fre" to "fr", "fra" to "fr", "french" to "fr", "vf" to "fr", "vff" to "fr",
        "en" to "en", "eng" to "en", "english" to "en", "vo" to "en",
        "es" to "es", "spa" to "es", "de" to "de", "ger" to "de", "it" to "it", "ita" to "it"
    )

    fun find(context: Context, shares: NetworkShares, node: Node): List<ExternalSubtitle> {
        val videoName = node.name
        val base = videoName.substringBeforeLast('.')
        val folder = node.relativePath.substringBeforeLast('/', "")

        // (nom du fichier, lecture de son contenu)
        val candidates: List<Pair<String, () -> ByteArray?>> = try {
            if (node.documentUri.startsWith("smb://")) {
                val share = shares.find(node.volumeId) ?: return emptyList()
                NetworkShares.open(share, if (folder.isEmpty()) "" else "$folder/").use { dir ->
                    dir.listFiles().orEmpty().map { it.name.trimEnd('/') }
                }.filter { matches(it, base) }.map { name ->
                    name to {
                        runCatching {
                            NetworkShares.open(share, if (folder.isEmpty()) name else "$folder/$name").use { f ->
                                if (f.length() > MAX_BYTES) null else f.inputStream.use { it.readBytes() }
                            }
                        }.getOrNull()
                    }
                }
            } else {
                val mount = StorageAccess.mountPointFor(node.volumeId) ?: return emptyList()
                val dir = if (folder.isEmpty()) mount else File(mount, folder)
                dir.listFiles().orEmpty().filter { it.isFile && matches(it.name, base) }.map { f ->
                    f.name to { runCatching { if (f.length() > MAX_BYTES) null else f.readBytes() }.getOrNull() }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }

        val cacheDir = File(context.cacheDir, "subtitles").apply { mkdirs() }
        return candidates.sortedBy { it.first.lowercase() }.mapNotNull { (name, read) ->
            val bytes = read() ?: return@mapNotNull null
            val extension = name.substringAfterLast('.').lowercase()
            val mime = TYPES[extension] ?: return@mapNotNull null
            val copy = File(cacheDir, hash("${node.id}/$name") + "." + extension)
            runCatching { copy.writeText(decode(bytes), Charsets.UTF_8) }.getOrNull() ?: return@mapNotNull null

            // « Film.fr.srt » → fr ; « Film.srt » → langue inconnue
            val tag = name.substringBeforeLast('.').removePrefix(base).trim('.', '-', '_', ' ').lowercase()
            val language = LANGUAGES[tag.substringAfterLast('.')] ?: ""
            ExternalSubtitle(
                file = copy,
                mimeType = mime,
                language = language,
                label = "Fichier " + (if (tag.isBlank()) extension.uppercase() else tag.uppercase())
            )
        }
    }

    private fun matches(name: String, base: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in TYPES && name.startsWith(base, ignoreCase = true)
    }

    /** UTF-8 si le fichier en est vraiment, sinon l'encodage Windows d'Europe de l'Ouest. */
    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) ||
                (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))
        ) return String(bytes, Charsets.UTF_16)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("﻿")
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
}
