package com.cinetv.ui.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetv.R

/*
 * Le logo Netfloux, tel que dessiné : le tracé vient du fichier vectoriel
 * fourni (res/drawable/netfloux_logo.xml), rouge d'origine fondu vers le
 * violet de l'application. Pour changer le dégradé, c'est dans ce fichier
 * XML : les trois lignes <item android:color=…>.
 */

/** Le logo complet. On donne sa hauteur, la largeur suit. */
@Composable
fun NetflouxLogo(
    height: Dp,
    modifier: Modifier = Modifier,
    /** Une couleur unie à la place du dégradé rouge → violet (blanc sur l'écran de veille). */
    tint: Color? = null
) {
    Image(
        painter = painterResource(R.drawable.netfloux_logo),
        contentDescription = "Netfloux",
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.height(height)
    )
}

/** Version compacte, pour les en-têtes d'écran. */
@Composable
fun NetflouxMark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    tint: Color? = null
) {
    NetflouxLogo(height = (fontSize.value * 0.82f).dp, modifier = modifier, tint = tint)
}

/** Le N seul : signature discrète sur les cartes, le rail et la bannière. */
@Composable
fun NetflouxBadge(
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
    tint: Color? = null
) {
    Image(
        painter = painterResource(R.drawable.netfloux_n),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.height(size)
    )
}
