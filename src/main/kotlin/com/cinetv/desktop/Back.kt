package com.cinetv.desktop

/** Les gestionnaires de « Retour », du plus récent au plus ancien. */
object Back {
    class Entry {
        @Volatile var enabled: Boolean = true
        @Volatile var action: () -> Unit = {}
    }

    private val entries = ArrayList<Entry>()
    /** Ce qui se passe quand plus personne ne gère Retour : revenir à l'écran précédent. */
    @Volatile var fallback: () -> Boolean = { false }

    @Synchronized fun register(entry: Entry) { entries.remove(entry); entries.add(entry) }
    @Synchronized fun unregister(entry: Entry) { entries.remove(entry) }

    /** Vrai si quelqu'un a pris le Retour en charge. */
    fun dispatch(): Boolean {
        val handler = synchronized(this) { entries.lastOrNull { it.enabled } }
        if (handler != null) { handler.action(); return true }
        return fallback()
    }
}
