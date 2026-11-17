package android.content

import com.cinetv.desktop.AppDirs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Les réglages, à la manière d'Android : des valeurs nommées, gardées d'un lancement à l'autre. */
interface SharedPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun getInt(key: String, defValue: Int): Int
    fun getLong(key: String, defValue: Long): Long
    fun getFloat(key: String, defValue: Float): Float
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun contains(key: String): Boolean
    val all: Map<String, *>
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putLong(key: String, value: Long): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }
}

/**
 * Réglages rangés dans un fichier JSON par groupe, dans %APPDATA%\Netfloux\prefs.
 * L'écriture est faite tout de suite, en entier : les fichiers sont petits.
 */
class FilePreferences private constructor(private val file: File) : SharedPreferences {

    companion object {
        private val opened = ConcurrentHashMap<String, FilePreferences>()

        fun of(name: String): FilePreferences = opened.getOrPut(name) {
            val dir = File(AppDirs.data, "prefs").also { it.mkdirs() }
            FilePreferences(File(dir, "$name.json"))
        }
    }

    private val values: MutableMap<String, Any?> = ConcurrentHashMap<String, Any?>().apply {
        runCatching {
            if (file.exists()) {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                for (key in json.keys()) {
                    val v = json.get(key)
                    put(key, when (v) {
                        is JSONObject -> {
                            // Valeur typée : {"t": "set" | "long" | "float" | "int", "v": …}
                            when (v.optString("t")) {
                                "set" -> v.getJSONArray("v").let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() }
                                "long" -> v.getLong("v")
                                "float" -> v.getDouble("v").toFloat()
                                "int" -> v.getInt("v")
                                else -> null
                            }
                        }
                        JSONObject.NULL -> null
                        else -> v
                    } ?: continue)
                }
            }
        }
    }

    @Synchronized
    private fun save() {
        val json = JSONObject()
        for ((k, v) in values) {
            when (v) {
                null -> {}
                is Set<*> -> json.put(k, JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() })))
                is Long -> json.put(k, JSONObject().put("t", "long").put("v", v))
                is Float -> json.put(k, JSONObject().put("t", "float").put("v", v.toDouble()))
                is Int -> json.put(k, JSONObject().put("t", "int").put("v", v))
                else -> json.put(k, v)
            }
        }
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.toString(1), Charsets.UTF_8)
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    override fun getString(key: String, defValue: String?): String? = (values[key] as? String) ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = (values[key] as? Set<String>) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = (values[key] as? Number)?.toInt() ?: defValue
    override fun getLong(key: String, defValue: Long): Long = (values[key] as? Number)?.toLong() ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = (values[key] as? Number)?.toFloat() ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = (values[key] as? Boolean) ?: defValue
    override fun contains(key: String): Boolean = values.containsKey(key)
    override val all: Map<String, *> get() = HashMap(values)

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private val removed = LinkedHashSet<String>()
        private var clearAll = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { changes[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clearAll = true }
        override fun commit(): Boolean {
            synchronized(this@FilePreferences) {
                if (clearAll) values.clear()
                removed.forEach { values.remove(it) }
                for ((k, v) in changes) if (v == null) values.remove(k) else values[k] = v
                save()
            }
            return true
        }
        override fun apply() { commit() }
    }
}
