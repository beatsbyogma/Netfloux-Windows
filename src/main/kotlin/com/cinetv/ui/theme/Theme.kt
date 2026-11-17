package com.cinetv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

/*
 * Câblage du thème. Les valeurs réglables sont dans DesignTokens.kt :
 * ce fichier ne fait que les brancher sur Material.
 *
 * Titres : Archivo Expanded Black. Textes : Archivo regular.
 */

private fun netflouxColors() = darkColorScheme(
    primary = Ink.Violet,
    onPrimary = Ink.Void,
    secondary = Ink.Lilac,
    background = Ink.Void,
    onBackground = Ink.TextHi,
    surface = Ink.Surface,
    onSurface = Ink.TextHi,
    surfaceVariant = Ink.SurfaceHi,
    onSurfaceVariant = Ink.TextMid,
    border = Ink.Line
)

// Échelle pensée pour une lecture à trois mètres. Tailles dans Type.
private val NetflouxType = Typography(
    displayLarge = Typography().displayLarge.copy(
        fontFamily = Fonts.Display, fontWeight = FontWeight.Black,
        fontSize = Type.displayLarge, lineHeight = Type.displayLarge * Type.tightLine,
        letterSpacing = (-0.8).sp
    ),
    displayMedium = Typography().displayMedium.copy(
        fontFamily = Fonts.Display, fontWeight = FontWeight.Black,
        fontSize = Type.displayMedium, lineHeight = Type.displayMedium * Type.tightLine,
        letterSpacing = (-0.4).sp
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontFamily = Fonts.Display, fontWeight = FontWeight.Black,
        fontSize = Type.headlineLarge, lineHeight = Type.headlineLarge * Type.tightLine,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = Fonts.Display, fontWeight = FontWeight.Black,
        fontSize = Type.headlineSmall, lineHeight = Type.headlineSmall * Type.tightLine,
        letterSpacing = 0.6.sp
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = Fonts.Text, fontWeight = FontWeight.Medium,
        fontSize = Type.titleMedium, lineHeight = Type.titleMedium * Type.labelLine,
        letterSpacing = 0.2.sp
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = Fonts.Text, fontWeight = FontWeight.Normal,
        fontSize = Type.bodyLarge, lineHeight = Type.bodyLarge * 1.42f
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = Fonts.Text, fontWeight = FontWeight.Normal,
        fontSize = Type.bodyMedium, lineHeight = Type.bodyMedium * 1.42f
    ),
    labelLarge = Typography().labelLarge.copy(
        fontFamily = Fonts.Text, fontWeight = FontWeight.Medium,
        fontSize = Type.labelLarge, lineHeight = Type.labelLarge * Type.labelLine,
        letterSpacing = 0.4.sp
    ),
    labelSmall = Typography().labelSmall.copy(
        fontFamily = Fonts.Text, fontWeight = FontWeight.Medium,
        fontSize = Type.labelSmall, lineHeight = Type.labelSmall * Type.labelLine,
        letterSpacing = 1.3.sp
    )
)

@Composable
fun NetflouxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = netflouxColors(),
        typography = NetflouxType,
        content = content
    )
}
