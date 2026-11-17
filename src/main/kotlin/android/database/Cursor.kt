package android.database

import java.io.Closeable

/** Le résultat d'une requête, ligne par ligne, à la manière d'Android. */
interface Cursor : Closeable {
    val count: Int
    val columnCount: Int
    val position: Int
    fun moveToNext(): Boolean
    fun moveToFirst(): Boolean
    fun moveToPosition(position: Int): Boolean
    fun getColumnIndex(columnName: String): Int
    fun getColumnIndexOrThrow(columnName: String): Int
    fun getColumnName(columnIndex: Int): String
    fun isNull(columnIndex: Int): Boolean
    /** Le texte d'une colonne ; vide si elle est nulle. */
    fun getString(columnIndex: Int): String
    fun getInt(columnIndex: Int): Int
    fun getLong(columnIndex: Int): Long
    fun getFloat(columnIndex: Int): Float
    fun getDouble(columnIndex: Int): Double
    fun getBlob(columnIndex: Int): ByteArray?
    val isClosed: Boolean
}

/** Un résultat lu en entier en mémoire : simple, et la connexion est aussitôt libérée. */
class ListCursor(private val columns: List<String>, private val rows: List<Array<Any?>>) : Cursor {
    private var index = -1
    private var closed = false
    private val lookup = columns.withIndex().associate { it.value.lowercase() to it.index }

    override val count: Int get() = rows.size
    override val columnCount: Int get() = columns.size
    override val position: Int get() = index
    override fun moveToNext(): Boolean { if (index < rows.size) index++; return index < rows.size }
    override fun moveToFirst(): Boolean { index = 0; return rows.isNotEmpty() }
    override fun moveToPosition(position: Int): Boolean { index = position; return position in rows.indices }
    override fun getColumnIndex(columnName: String): Int = lookup[columnName.lowercase()] ?: -1
    override fun getColumnIndexOrThrow(columnName: String): Int =
        getColumnIndex(columnName).takeIf { it >= 0 } ?: throw IllegalArgumentException("colonne inconnue : $columnName")
    override fun getColumnName(columnIndex: Int): String = columns[columnIndex]

    private fun value(i: Int): Any? = rows[index][i]

    override fun isNull(columnIndex: Int): Boolean = value(columnIndex) == null
    override fun getString(columnIndex: Int): String = when (val v = value(columnIndex)) {
        null -> ""
        is ByteArray -> String(v, Charsets.UTF_8)
        is Double -> if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        else -> v.toString()
    }
    override fun getInt(columnIndex: Int): Int = number(columnIndex).toInt()
    override fun getLong(columnIndex: Int): Long = number(columnIndex).toLong()
    override fun getFloat(columnIndex: Int): Float = number(columnIndex).toFloat()
    override fun getDouble(columnIndex: Int): Double = number(columnIndex).toDouble()
    override fun getBlob(columnIndex: Int): ByteArray? = value(columnIndex) as? ByteArray

    private fun number(i: Int): Number = when (val v = value(i)) {
        null -> 0
        is Number -> v
        is Boolean -> if (v) 1 else 0
        is String -> v.toLongOrNull() ?: v.toDoubleOrNull() ?: 0
        else -> 0
    }

    override val isClosed: Boolean get() = closed
    override fun close() { closed = true }
}
