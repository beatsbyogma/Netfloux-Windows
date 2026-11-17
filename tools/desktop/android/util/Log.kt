package android.util

import com.cinetv.desktop.AppDirs
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Le journal de Netfloux : la console, et le fichier %APPDATA%\Netfloux\netfloux.log
 * (repris à zéro à chaque lancement). C'est là qu'on lit ce qui s'est passé en cas de souci.
 */
object Log {
    private val file: File by lazy {
        File(AppDirs.data, "netfloux.log").also { runCatching { it.writeText("") } }
    }
    /** Lancé depuis Android Studio : une copie du journal dans build/, à côté du projet. */
    private val devCopy: File? by lazy {
        File(System.getProperty("user.dir"), "build").takeIf { File(System.getProperty("user.dir"), "build.gradle.kts").isFile && it.isDirectory }
            ?.let { File(it, "netfloux.log") }?.also { runCatching { it.writeText("") } }
    }
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    @Synchronized
    private fun write(level: String, tag: String?, msg: String?, tr: Throwable? = null): Int {
        val line = "${LocalTime.now().format(time)} $level/${tag.orEmpty()}: ${msg.orEmpty()}" +
            (tr?.let { "\n" + it.stackTraceToString() } ?: "")
        System.err.println(line)
        runCatching { file.appendText(line + "\n") }
        runCatching { devCopy?.appendText(line + "\n") }
        return 0
    }

    fun v(tag: String?, msg: String?): Int = write("V", tag, msg)
    fun d(tag: String?, msg: String?): Int = write("D", tag, msg)
    fun i(tag: String?, msg: String?): Int = write("I", tag, msg)
    fun w(tag: String?, msg: String?): Int = write("W", tag, msg)
    fun w(tag: String?, msg: String?, tr: Throwable?): Int = write("W", tag, msg, tr)
    fun e(tag: String?, msg: String?): Int = write("E", tag, msg)
    fun e(tag: String?, msg: String?, tr: Throwable?): Int = write("E", tag, msg, tr)
}
