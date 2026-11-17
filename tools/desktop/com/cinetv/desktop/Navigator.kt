package com.cinetv.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * La navigation entre écrans : une pile, comme sur la TV.
 * Chaque écran garde son état (position dans les listes…) tant qu'il est dans la pile.
 */
class Navigator(start: String) {
    class Entry(val route: String, val id: Long) {
        internal val models = androidx.lifecycle.ViewModelStore()
        /** « browse/drv-1a2b?folderId=xyz » → volumeId, folderId… */
        val path: String get() = route.substringBefore('?')
        fun arg(name: String): String? {
            val query = route.substringAfter('?', "")
            return query.split('&').firstOrNull { it.substringBefore('=') == name }
                ?.substringAfter('=', "")?.let { android.net.Uri.decode(it) }?.takeIf { it.isNotEmpty() }
        }
        fun segment(index: Int): String = android.net.Uri.decode(path.split('/').getOrNull(index).orEmpty())
    }

    private var counter = 0L
    val stack = mutableStateListOf(Entry(start, counter++))
    /** Vrai si le dernier mouvement était un retour (pour le sens de l'animation). */
    var popping by mutableStateOf(false)
        private set

    val current: Entry get() = stack.last()

    fun navigate(route: String, replaceFrom: String? = null) {
        popping = false
        if (replaceFrom != null) {
            val index = stack.indexOfLast { it.route == replaceFrom }
            if (index >= 0) {
                val removed = stack.subList(index, stack.size).toList()
                removed.forEach { clear(it) }
                repeat(stack.size - index) { stack.removeAt(stack.lastIndex) }
            }
        }
        stack.add(Entry(route, counter++))
    }

    fun popBackStack(): Boolean {
        if (stack.size <= 1) return false
        popping = true
        clear(stack.removeAt(stack.lastIndex))
        return true
    }

    private fun clear(entry: Entry) {
        runCatching { entry.models.clear() }
    }
}

/** Le « cerveau » d'un écran, créé une fois par passage dans la pile. */
@Suppress("UNCHECKED_CAST")
fun <T : ViewModel> Navigator.Entry.viewModel(key: String = "main", create: () -> T): T =
    (models.get(key) as T?) ?: create().also { models.put(key, it) }
