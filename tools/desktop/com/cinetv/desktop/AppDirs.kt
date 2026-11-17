package com.cinetv.desktop

import java.io.File

/**
 * Où Netfloux range ses données sur Windows.
 *
 * Réglages, base de la bibliothèque et sous-titres : %APPDATA%\Netfloux.
 * Images en cache (jaquettes, fonds) : %LOCALAPPDATA%\Netfloux\cache — elles peuvent
 * être effacées sans rien perdre, elles reviennent toutes seules.
 */
object AppDirs {
    val data: File by lazy {
        val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + File.separator + ".netfloux")
        File(base, "Netfloux").also { it.mkdirs() }
    }

    val cache: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: data.path
        File(File(base, "Netfloux"), "cache").also { it.mkdirs() }
    }
}
