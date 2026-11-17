package com.cinetv.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import org.w3c.dom.Element
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Les ressources de l'application (icônes, polices, sons, planche du chat), rangées
 * dans le programme sous les mêmes noms que sur Android : drawable/…, font/…, raw/….
 * Les numéros [com.cinetv.R] y renvoient.
 */
object Res {
    fun path(id: Int): String = com.cinetv.R.PATHS.getOrNull(id - 1) ?: error("ressource inconnue : $id")

    fun stream(id: Int): InputStream? = stream(path(id))

    fun stream(path: String): InputStream? = Res::class.java.getResourceAsStream("/$path")

    fun bytes(id: Int): ByteArray = stream(id)?.use { it.readBytes() } ?: ByteArray(0)

    private val vectors = ConcurrentHashMap<Int, ImageVector>()
    private val bitmaps = ConcurrentHashMap<Int, ImageBitmap>()

    fun isVector(id: Int): Boolean = path(id).endsWith(".xml")

    fun vector(id: Int): ImageVector = vectors.getOrPut(id) {
        stream(id)?.use { VectorXml.parse(it) } ?: ImageVector.Builder(defaultWidth = 1.dp, defaultHeight = 1.dp, viewportWidth = 1f, viewportHeight = 1f).build()
    }

    fun bitmap(id: Int): ImageBitmap = bitmaps.getOrPut(id) {
        stream(id)?.use { ImageIO.read(it) }?.toComposeImageBitmap() ?: ImageBitmap(1, 1)
    }
}

/** Lit une icône au format vectoriel d'Android (balises <vector>, <group>, <path>, dégradés). */
internal object VectorXml {
    private const val NS = "http://schemas.android.com/apk/res/android"

    fun parse(input: InputStream): ImageVector {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(input).documentElement
        if (root.localName != "vector" && root.tagName != "vector") {
            return ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).build()
        }
        fun dim(name: String, fallback: Float) = attr(root, name)?.removeSuffix("dp")?.removeSuffix("dip")?.toFloatOrNull() ?: fallback
        val width = dim("width", 24f)
        val height = dim("height", 24f)
        val builder = ImageVector.Builder(
            defaultWidth = width.dp,
            defaultHeight = height.dp,
            viewportWidth = attr(root, "viewportWidth")?.toFloatOrNull() ?: width,
            viewportHeight = attr(root, "viewportHeight")?.toFloatOrNull() ?: height
        )
        children(root).forEach { add(builder, it) }
        return builder.build()
    }

    private fun children(e: Element): List<Element> =
        (0 until e.childNodes.length).mapNotNull { e.childNodes.item(it) as? Element }

    private fun attr(e: Element, name: String): String? =
        e.getAttributeNS(NS, name).takeIf { it.isNotEmpty() } ?: e.getAttribute("android:$name").takeIf { it.isNotEmpty() }

    private fun name(e: Element) = e.localName ?: e.tagName.substringAfter(':')

    private fun add(builder: ImageVector.Builder, e: Element) {
        when (name(e)) {
            "group" -> {
                builder.addGroup(
                    name = attr(e, "name") ?: "",
                    rotate = attr(e, "rotation")?.toFloatOrNull() ?: 0f,
                    pivotX = attr(e, "pivotX")?.toFloatOrNull() ?: 0f,
                    pivotY = attr(e, "pivotY")?.toFloatOrNull() ?: 0f,
                    scaleX = attr(e, "scaleX")?.toFloatOrNull() ?: 1f,
                    scaleY = attr(e, "scaleY")?.toFloatOrNull() ?: 1f,
                    translationX = attr(e, "translateX")?.toFloatOrNull() ?: 0f,
                    translationY = attr(e, "translateY")?.toFloatOrNull() ?: 0f,
                    clipPathData = children(e).firstOrNull { name(it) == "clip-path" }
                        ?.let { attr(it, "pathData") }?.let { addPathNodes(it) } ?: emptyList()
                )
                children(e).filter { name(it) != "clip-path" }.forEach { add(builder, it) }
                builder.clearGroup()
            }
            "path" -> {
                val data = attr(e, "pathData") ?: return
                builder.addPath(
                    pathData = addPathNodes(data),
                    pathFillType = if (attr(e, "fillType") == "evenOdd") PathFillType.EvenOdd else PathFillType.NonZero,
                    name = attr(e, "name") ?: "",
                    fill = brush(e, "fillColor"),
                    fillAlpha = attr(e, "fillAlpha")?.toFloatOrNull() ?: 1f,
                    stroke = brush(e, "strokeColor"),
                    strokeAlpha = attr(e, "strokeAlpha")?.toFloatOrNull() ?: 1f,
                    strokeLineWidth = attr(e, "strokeWidth")?.toFloatOrNull() ?: 0f,
                    strokeLineCap = when (attr(e, "strokeLineCap")) { "round" -> StrokeCap.Round; "square" -> StrokeCap.Square; else -> StrokeCap.Butt },
                    strokeLineJoin = when (attr(e, "strokeLineJoin")) { "round" -> StrokeJoin.Round; "bevel" -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
                    strokeLineMiter = attr(e, "strokeMiterLimit")?.toFloatOrNull() ?: 4f
                )
            }
        }
    }

    /** La couleur (ou le dégradé) d'un tracé : attribut direct, ou balise <aapt:attr> contenant un <gradient>. */
    private fun brush(e: Element, attribute: String): Brush? {
        attr(e, attribute)?.let { value -> return color(value)?.let { SolidColor(it) } }
        val nested = children(e).firstOrNull { name(it) == "attr" && it.getAttribute("name").endsWith(attribute) } ?: return null
        val gradient = children(nested).firstOrNull { name(it) == "gradient" } ?: return null
        val stops = children(gradient).filter { name(it) == "item" }.mapNotNull { item ->
            val offset = attr(item, "offset")?.toFloatOrNull() ?: return@mapNotNull null
            val c = attr(item, "color")?.let { color(it) } ?: return@mapNotNull null
            offset to c
        }.ifEmpty {
            listOfNotNull(
                attr(gradient, "startColor")?.let { color(it) }?.let { 0f to it },
                attr(gradient, "centerColor")?.let { color(it) }?.let { 0.5f to it },
                attr(gradient, "endColor")?.let { color(it) }?.let { 1f to it }
            )
        }
        if (stops.size < 2) return stops.firstOrNull()?.let { SolidColor(it.second) }
        return when (attr(gradient, "type")) {
            "radial" -> Brush.radialGradient(
                *stops.toTypedArray(),
                center = Offset(attr(gradient, "centerX")?.toFloatOrNull() ?: 0f, attr(gradient, "centerY")?.toFloatOrNull() ?: 0f),
                radius = attr(gradient, "gradientRadius")?.toFloatOrNull() ?: 1f
            )
            else -> Brush.linearGradient(
                *stops.toTypedArray(),
                start = Offset(attr(gradient, "startX")?.toFloatOrNull() ?: 0f, attr(gradient, "startY")?.toFloatOrNull() ?: 0f),
                end = Offset(attr(gradient, "endX")?.toFloatOrNull() ?: 0f, attr(gradient, "endY")?.toFloatOrNull() ?: 0f)
            )
        }
    }

    private fun color(value: String): Color? {
        val v = value.trim()
        return when {
            v == "@android:color/white" -> Color.White
            v == "@android:color/black" -> Color.Black
            v == "@android:color/transparent" -> Color.Transparent
            v.startsWith("#") -> {
                val hex = v.substring(1)
                val argb = when (hex.length) {
                    3 -> "FF" + hex.map { "$it$it" }.joinToString("")
                    4 -> hex.map { "$it$it" }.joinToString("")
                    6 -> "FF$hex"
                    8 -> hex
                    else -> return null
                }
                Color(argb.toLong(16).toInt())
            }
            else -> Color.White
        }
    }
}
