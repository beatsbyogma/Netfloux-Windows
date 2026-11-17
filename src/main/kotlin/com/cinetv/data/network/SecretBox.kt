package com.cinetv.data.network

import com.sun.jna.platform.win32.Crypt32Util
import java.util.Base64

/**
 * Chiffre les mots de passe des partages réseau avant de les ranger, avec la protection
 * de Windows liée au compte de l'utilisateur (DPAPI) : copié sur un autre PC ou un autre
 * compte, le mot de passe rangé est illisible.
 */
object SecretBox {

    private const val MARK = "dpapi:"

    fun isSealed(stored: String): Boolean = stored.startsWith(MARK)

    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        val sealed = Crypt32Util.cryptProtectData(plain.toByteArray(Charsets.UTF_8))
        return MARK + Base64.getEncoder().encodeToString(sealed)
    }

    fun open(stored: String): String {
        if (!isSealed(stored)) return stored
        return runCatching {
            String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(stored.removePrefix(MARK))), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
