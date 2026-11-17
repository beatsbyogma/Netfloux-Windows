package android.content

/** Une ligne à écrire dans la base : des valeurs nommées. */
class ContentValues {
    internal val map = LinkedHashMap<String, Any?>()
    fun put(key: String, value: String?) { map[key] = value }
    fun put(key: String, value: Int?) { map[key] = value }
    fun put(key: String, value: Long?) { map[key] = value }
    fun put(key: String, value: Float?) { map[key] = value }
    fun put(key: String, value: Double?) { map[key] = value }
    fun put(key: String, value: Boolean?) { map[key] = value?.let { if (it) 1 else 0 } }
    fun put(key: String, value: ByteArray?) { map[key] = value }
    fun putNull(key: String) { map[key] = null }
    fun keySet(): Set<String> = map.keys
    fun get(key: String): Any? = map[key]
    fun size(): Int = map.size
}
