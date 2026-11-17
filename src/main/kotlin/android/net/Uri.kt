package android.net

import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Une adresse (« file:///E:/Films/x.mkv », « smb://nas/films », « https://… »), à la manière
 * d'Android : on la lit morceau par morceau, et on en construit de nouvelles.
 */
class Uri private constructor(private val text: String) {

    val scheme: String? = text.substringBefore(':', "").takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '+' || c == '-' || c == '.' } && it.length > 1 }

    private val afterScheme: String = if (scheme != null) text.substring(scheme.length + 1) else text
    private val hierarchical: String = afterScheme.substringBefore('#').substringBefore('?')
    val encodedQuery: String? = afterScheme.substringBefore('#').substringAfter('?', "").takeIf { afterScheme.substringBefore('#').contains('?') }

    val authority: String? = if (hierarchical.startsWith("//")) hierarchical.removePrefix("//").substringBefore('/') else null
    val host: String? = authority?.substringAfterLast('@')?.substringBefore(':')?.takeIf { it.isNotEmpty() }
    val port: Int = authority?.substringAfterLast('@')?.substringAfter(':', "")?.toIntOrNull() ?: -1
    val userInfo: String? = authority?.takeIf { it.contains('@') }?.substringBeforeLast('@')

    val encodedPath: String? = if (authority != null) hierarchical.removePrefix("//").substring(authority.length) else hierarchical
    val path: String? = encodedPath?.let { decode(it) }
    val pathSegments: List<String> get() = path.orEmpty().split('/').filter { it.isNotEmpty() }
    val lastPathSegment: String? get() = pathSegments.lastOrNull()

    fun getQueryParameter(key: String): String? =
        encodedQuery?.split('&')?.firstOrNull { it.substringBefore('=') == key }?.substringAfter('=', "")?.let { decode(it) }

    fun buildUpon(): Builder = Builder().also { b ->
        b.scheme = scheme
        b.authority = authority
        b.path = encodedPath.orEmpty()
        b.query.append(encodedQuery.orEmpty())
    }

    override fun toString(): String = text
    override fun equals(other: Any?): Boolean = other is Uri && other.text == text
    override fun hashCode(): Int = text.hashCode()

    class Builder {
        internal var scheme: String? = null
        internal var authority: String? = null
        internal var path: String = ""
        internal val query = StringBuilder()

        fun scheme(value: String?) = apply { scheme = value }
        fun authority(value: String?) = apply { authority = value }
        fun path(value: String?) = apply { path = value.orEmpty().split('/').joinToString("/") { encode(it) } }
        fun encodedPath(value: String?) = apply { path = value.orEmpty() }
        fun appendPath(segment: String?) = apply {
            if (segment == null) return@apply
            path = path.trimEnd('/') + "/" + encode(segment)
        }
        fun appendEncodedPath(segment: String?) = apply { if (segment != null) path = path.trimEnd('/') + "/" + segment.trimStart('/') }
        fun appendQueryParameter(key: String, value: String?) = apply {
            if (query.isNotEmpty()) query.append('&')
            query.append(encode(key)).append('=').append(encode(value.orEmpty()))
        }
        fun clearQuery() = apply { query.setLength(0) }
        fun build(): Uri = Uri(buildString {
            if (scheme != null) append(scheme).append(':')
            if (authority != null) append("//").append(authority)
            append(path)
            if (query.isNotEmpty()) append('?').append(query)
        })
    }

    companion object {
        val EMPTY: Uri = Uri("")

        fun parse(text: String): Uri = Uri(text)

        /** « file:///E:/Films/Mon%20film.mkv » pour un fichier du PC. */
        fun fromFile(file: File): Uri {
            val path = file.absolutePath.replace('\\', '/')
            val encoded = path.split('/').joinToString("/") { if (it.endsWith(":")) it else encode(it) }
            return Uri("file:///" + encoded.trimStart('/'))
        }

        /** Comme sur Android : lettres, chiffres et « _-!.~'()* » restent tels quels, le reste est codé. */
        fun encode(s: String?): String {
            if (s == null) return ""
            return URLEncoder.encode(s, Charsets.UTF_8)
                .replace("+", "%20")
                .replace("%21", "!").replace("%27", "'").replace("%28", "(").replace("%29", ")")
                .replace("%7E", "~").replace("*", "*")
        }

        fun decode(s: String?): String {
            if (s == null) return ""
            return runCatching { URLDecoder.decode(s.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(s)
        }
    }
}
