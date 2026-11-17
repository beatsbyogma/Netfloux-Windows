package com.cinetv.data.storage

import android.content.Context
import com.cinetv.data.model.Node
import com.cinetv.data.network.NetworkShares
import com.cinetv.desktop.Vlc
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaParsedStatus
import uk.co.caprica.vlcj.media.ParseFlag
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Ce que contient réellement le fichier : définition, HDR, pistes audio et sous-titres. */
data class TechInfo(
    val resolution: String,          // « 4K », « 1080p »…, vide si inconnue
    val hdr: String,                 // « HDR10 », « HLG », « Dolby Vision », ou vide
    val audio: List<String>,         // « FR 5.1 », « EN stéréo »…
    val subtitles: List<String>,     // « FR », « EN »…
    /** Définition rangée en 2160, 1080, 720, 480 ou 360 ; 0 si la vidéo n'a pas livré sa taille. */
    val quality: Int = 0
) {
    val isEmpty: Boolean get() = resolution.isBlank() && hdr.isBlank() && audio.isEmpty() && subtitles.isEmpty()
}

/**
 * Lit l'en-tête d'un fichier vidéo avec VLC, sans rien décoder : définition, HDR,
 * pistes audio et sous-titres. Résultats gardés en mémoire le temps de la session.
 */
object MediaProbe {

    private val cache = ConcurrentHashMap<String, TechInfo>()
    private val factory: MediaPlayerFactory by lazy {
        Vlc.ensureLoaded()
        MediaPlayerFactory("--quiet", "--no-video", "--no-audio")
    }

    @Synchronized
    fun probe(@Suppress("UNUSED_PARAMETER") context: Context, @Suppress("UNUSED_PARAMETER") shares: NetworkShares, node: Node): TechInfo? {
        cache[node.id]?.let { return it }
        if (node.isDirectory || !node.isAvailable) return null
        // Fichiers du PC seulement : sur un partage, lire l'en-tête coûterait un aller-retour réseau par film
        val file = StorageAccess.fileFor(node.volumeId, node.relativePath)?.takeIf { it.isFile } ?: return null
        val media = factory.media().newMedia(file.absolutePath) ?: return null
        return try {
            media.parsing().parse(8_000, ParseFlag.PARSE_LOCAL)
            var waited = 0
            while (waited < 9_000) {
                val status = media.parsing().status()
                if (status != null) break
                Thread.sleep(40); waited += 40
            }
            if (media.parsing().status() != MediaParsedStatus.DONE) return null
            read(media.info()).also { cache[node.id] = it }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { media.release() }
        }
    }

    private fun read(info: uk.co.caprica.vlcj.media.InfoApi): TechInfo {
        var resolution = ""
        var hdr = ""
        var quality = 0
        val audio = LinkedHashSet<String>()
        val subtitles = LinkedHashSet<String>()

        info.videoTracks().firstOrNull()?.let { video ->
            val width = video.width()
            val height = video.height()
            quality = com.cinetv.data.model.qualityOf(width, height)
            resolution = when {
                width >= 3200 || height >= 1700 -> "4K"
                width >= 1800 || height >= 1000 -> "1080p"
                width >= 1200 || height >= 700 -> "720p"
                width > 0 -> "SD"
                else -> ""
            }
            val described = "${video.description().orEmpty()} ${video.codecDescription().orEmpty()}".lowercase()
            hdr = when {
                "dolby vision" in described || "dovi" in described -> "Dolby Vision"
                "hdr" in described || "2084" in described -> "HDR10"
                "hlg" in described -> "HLG"
                else -> ""
            }
        }
        info.audioTracks().forEach { track ->
            val channels = when (track.channels()) { 1 -> "mono"; 2 -> "stéréo"; 6 -> "5.1"; 8 -> "7.1"; else -> "" }
            audio += listOf(languageOf(track.language()), channels).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Audio" }
        }
        info.textTracks().forEach { track ->
            languageOf(track.language()).takeIf { it.isNotBlank() }?.let { subtitles += it }
        }
        return TechInfo(resolution, hdr, audio.toList(), subtitles.toList(), quality)
    }

    /** « fre », « fra », « fr » → « FR ». */
    private fun languageOf(value: String?): String {
        val raw = value.orEmpty().trim().lowercase()
        if (raw.isBlank() || raw == "und") return ""
        val two = runCatching {
            Locale.getAvailableLocales().firstOrNull {
                it.language.isNotEmpty() && (it.language == raw || runCatching { it.isO3Language }.getOrNull() == raw)
            }?.language
        }.getOrNull()
        val code = when (raw) { "fre" -> "fr"; "ger" -> "de"; "dut" -> "nl"; "chi" -> "zh"; else -> two ?: raw }
        return code.take(3).uppercase()
    }
}
