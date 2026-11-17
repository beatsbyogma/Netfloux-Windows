@file:JvmName("NetflouxFontResources")

package androidx.compose.ui.text.font

import com.cinetv.desktop.Res

/** Une police de l'application, désignée par son numéro [com.cinetv.R.font]. */
fun Font(resId: Int, weight: FontWeight = FontWeight.Normal, style: FontStyle = FontStyle.Normal): Font =
    androidx.compose.ui.text.platform.Font(
        identity = Res.path(resId),
        data = Res.bytes(resId),
        weight = weight,
        style = style
    )
