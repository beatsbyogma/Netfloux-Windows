@file:JvmName("NetflouxPainterResources")

package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.cinetv.desktop.Res

/** Une icône ou une image de l'application, désignée par son numéro [com.cinetv.R]. */
@Composable
fun painterResource(id: Int): Painter =
    if (Res.isVector(id)) rememberVectorPainter(Res.vector(id))
    else remember(id) { BitmapPainter(Res.bitmap(id)) }
