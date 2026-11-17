package android.database.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.ListCursor
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * La base SQLite, à la manière d'Android, posée sur le pilote SQLite pour Java.
 *
 * Une seule connexion, protégée par un verrou : une transaction ouverte par un fil
 * le garde jusqu'à sa fin, les autres attendent — comme sur Android.
 */
class SQLiteDatabase internal constructor(private val connection: Connection) {

    companion object {
        const val CONFLICT_NONE = 0
        const val CONFLICT_ROLLBACK = 1
        const val CONFLICT_ABORT = 2
        const val CONFLICT_FAIL = 3
        const val CONFLICT_IGNORE = 4
        const val CONFLICT_REPLACE = 5
        private val CONFLICT_WORDS = arrayOf("", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE")
    }

    private val lock = ReentrantLock()
    private var depth = 0
    private var allSucceeded = true
    private var currentSucceeded = false

    var version: Int
        get() = lock.withLock { connection.createStatement().use { s -> s.executeQuery("PRAGMA user_version").use { if (it.next()) it.getInt(1) else 0 } } }
        set(value) = lock.withLock { connection.createStatement().use { it.execute("PRAGMA user_version = $value") } }

    val isOpen: Boolean get() = !connection.isClosed
    fun inTransaction(): Boolean = lock.isHeldByCurrentThread && depth > 0

    // ------------------------------------------------------------ transactions

    fun beginTransaction() {
        lock.lock()
        if (depth == 0) {
            connection.autoCommit = false
            allSucceeded = true
        }
        depth++
        currentSucceeded = false
    }

    fun setTransactionSuccessful() { currentSucceeded = true }

    fun endTransaction() {
        try {
            if (!currentSucceeded) allSucceeded = false
            depth--
            currentSucceeded = true
            if (depth == 0) {
                try {
                    if (allSucceeded) connection.commit() else connection.rollback()
                } finally {
                    connection.autoCommit = true
                }
            }
        } finally {
            lock.unlock()
        }
    }

    // -------------------------------------------------------------- écritures

    fun execSQL(sql: String) = lock.withLock {
        connection.createStatement().use { it.execute(sql) }
        Unit
    }

    fun execSQL(sql: String, bindArgs: Array<out Any?>) = lock.withLock {
        connection.prepareStatement(sql).use { st -> bind(st, bindArgs); st.execute() }
        Unit
    }

    fun insert(table: String, nullColumnHack: String?, values: ContentValues): Long =
        insertWithOnConflict(table, nullColumnHack, values, CONFLICT_NONE)

    fun insertWithOnConflict(table: String, nullColumnHack: String?, values: ContentValues, conflictAlgorithm: Int): Long =
        lock.withLock {
            val keys = values.keySet().toList()
            val sql = "INSERT${CONFLICT_WORDS[conflictAlgorithm]} INTO $table (" + keys.joinToString(",") +
                ") VALUES (" + keys.joinToString(",") { "?" } + ")"
            runCatching {
                connection.prepareStatement(sql).use { st ->
                    bind(st, keys.map { values.get(it) }.toTypedArray())
                    st.executeUpdate()
                    connection.createStatement().use { s -> s.executeQuery("SELECT last_insert_rowid()").use { if (it.next()) it.getLong(1) else -1L } }
                }
            }.getOrElse { -1L }
        }

    fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<out String?>?): Int =
        lock.withLock {
            val keys = values.keySet().toList()
            val sql = "UPDATE $table SET " + keys.joinToString(",") { "$it = ?" } +
                (if (whereClause.isNullOrBlank()) "" else " WHERE $whereClause")
            connection.prepareStatement(sql).use { st ->
                bind(st, (keys.map { values.get(it) } + whereArgs.orEmpty().toList()).toTypedArray())
                st.executeUpdate()
            }
        }

    fun delete(table: String, whereClause: String?, whereArgs: Array<out String?>?): Int =
        lock.withLock {
            val sql = "DELETE FROM $table" + (if (whereClause.isNullOrBlank()) "" else " WHERE $whereClause")
            connection.prepareStatement(sql).use { st ->
                bind(st, whereArgs.orEmpty().map { it as Any? }.toTypedArray())
                st.executeUpdate()
            }
        }

    // ---------------------------------------------------------------- lectures

    fun rawQuery(sql: String, selectionArgs: Array<out String?>?): Cursor = lock.withLock {
        connection.prepareStatement(sql).use { st ->
            bind(st, selectionArgs.orEmpty().map { it as Any? }.toTypedArray())
            st.executeQuery().use { rs ->
                val meta = rs.metaData
                val columns = (1..meta.columnCount).map { meta.getColumnLabel(it) }
                val rows = ArrayList<Array<Any?>>()
                while (rs.next()) {
                    rows += Array(columns.size) { i ->
                        val v = rs.getObject(i + 1)
                        if (rs.wasNull()) null else v
                    }
                }
                ListCursor(columns, rows)
            }
        }
    }

    fun query(
        table: String, columns: Array<out String>?, selection: String?, selectionArgs: Array<out String?>?,
        groupBy: String?, having: String?, orderBy: String?
    ): Cursor = query(table, columns, selection, selectionArgs, groupBy, having, orderBy, null)

    fun query(
        table: String, columns: Array<out String>?, selection: String?, selectionArgs: Array<out String?>?,
        groupBy: String?, having: String?, orderBy: String?, limit: String?
    ): Cursor {
        val sql = buildString {
            append("SELECT ").append(columns?.joinToString(",") ?: "*").append(" FROM ").append(table)
            if (!selection.isNullOrBlank()) append(" WHERE ").append(selection)
            if (!groupBy.isNullOrBlank()) append(" GROUP BY ").append(groupBy)
            if (!having.isNullOrBlank()) append(" HAVING ").append(having)
            if (!orderBy.isNullOrBlank()) append(" ORDER BY ").append(orderBy)
            if (!limit.isNullOrBlank()) append(" LIMIT ").append(limit)
        }
        return rawQuery(sql, selectionArgs)
    }

    fun close() = lock.withLock { runCatching { connection.close() }; Unit }

    private fun bind(st: PreparedStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, v ->
            val at = i + 1
            when (v) {
                null -> st.setNull(at, Types.NULL)
                is String -> st.setString(at, v)
                is Int -> st.setInt(at, v)
                is Long -> st.setLong(at, v)
                is Float -> st.setDouble(at, v.toDouble())
                is Double -> st.setDouble(at, v)
                is Boolean -> st.setInt(at, if (v) 1 else 0)
                is ByteArray -> st.setBytes(at, v)
                else -> st.setString(at, v.toString())
            }
        }
    }
}

/** Ouvre la base, la crée ou la met à niveau, comme sur Android. */
abstract class SQLiteOpenHelper(
    context: Context?,
    private val name: String?,
    @Suppress("UNUSED_PARAMETER") factory: Any?,
    private val version: Int
) {
    private val file: File? = name?.let { (context ?: android.content.DesktopContext).getDatabasePath(it) }
    private var database: SQLiteDatabase? = null

    abstract fun onCreate(db: SQLiteDatabase)
    abstract fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int)
    open fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    open fun onOpen(db: SQLiteDatabase) {}
    open fun onConfigure(db: SQLiteDatabase) {}

    val writableDatabase: SQLiteDatabase
        @Synchronized get() = database ?: open().also { database = it }

    val readableDatabase: SQLiteDatabase get() = writableDatabase

    private fun open(): SQLiteDatabase {
        Class.forName("org.sqlite.JDBC")
        val url = if (file == null) "jdbc:sqlite::memory:" else {
            file.parentFile?.mkdirs()
            "jdbc:sqlite:${file.absolutePath}"
        }
        val connection = DriverManager.getConnection(url)
        connection.createStatement().use {
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA synchronous=NORMAL")
            it.execute("PRAGMA busy_timeout=5000")
        }
        val db = SQLiteDatabase(connection)
        onConfigure(db)
        val current = db.version
        if (current != version) {
            db.beginTransaction()
            try {
                when {
                    current == 0 -> onCreate(db)
                    current < version -> onUpgrade(db, current, version)
                    else -> onDowngrade(db, current, version)
                }
                db.version = version
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        onOpen(db)
        return db
    }

    @Synchronized
    fun close() { database?.close(); database = null }
}
