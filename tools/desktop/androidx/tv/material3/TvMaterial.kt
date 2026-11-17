package androidx.tv.material3

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * L'équivalent Windows de la bibliothèque « Compose pour TV » : mêmes noms, mêmes
 * réglages, pour que les écrans de la version TV s'affichent tels quels.
 * Une surface s'active au clavier (Entrée), à la souris (clic gauche ; clic droit =
 * appui long) et s'éclaire au survol comme si elle avait le focus.
 */

// ------------------------------------------------------------------ couleurs et thème

@Immutable
class ColorScheme(
    val primary: Color, val onPrimary: Color, val secondary: Color, val onSecondary: Color,
    val background: Color, val onBackground: Color, val surface: Color, val onSurface: Color,
    val surfaceVariant: Color, val onSurfaceVariant: Color, val border: Color, val error: Color
)

fun darkColorScheme(
    primary: Color = Color(0xFFA8C7FA), onPrimary: Color = Color(0xFF062E6F),
    secondary: Color = Color(0xFFBEC6DC), onSecondary: Color = Color(0xFF283141),
    background: Color = Color(0xFF111318), onBackground: Color = Color(0xFFE2E2E9),
    surface: Color = Color(0xFF111318), onSurface: Color = Color(0xFFE2E2E9),
    surfaceVariant: Color = Color(0xFF43474E), onSurfaceVariant: Color = Color(0xFFC4C6D0),
    border: Color = Color(0xFF8E9099), error: Color = Color(0xFFFFB4AB)
) = ColorScheme(primary, onPrimary, secondary, onSecondary, background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, border, error)

@Immutable
class Typography(
    val displayLarge: TextStyle = M3.displayLarge,
    val displayMedium: TextStyle = M3.displayMedium,
    val displaySmall: TextStyle = M3.displaySmall,
    val headlineLarge: TextStyle = M3.headlineLarge,
    val headlineMedium: TextStyle = M3.headlineMedium,
    val headlineSmall: TextStyle = M3.headlineSmall,
    val titleLarge: TextStyle = M3.titleLarge,
    val titleMedium: TextStyle = M3.titleMedium,
    val titleSmall: TextStyle = M3.titleSmall,
    val bodyLarge: TextStyle = M3.bodyLarge,
    val bodyMedium: TextStyle = M3.bodyMedium,
    val bodySmall: TextStyle = M3.bodySmall,
    val labelLarge: TextStyle = M3.labelLarge,
    val labelMedium: TextStyle = M3.labelMedium,
    val labelSmall: TextStyle = M3.labelSmall
)

private val M3 = androidx.compose.material3.Typography()

@Immutable
class Shapes

private val LocalColorScheme = staticCompositionLocalOf { darkColorScheme() }
private val LocalTypography = staticCompositionLocalOf { Typography() }
val LocalContentColor = compositionLocalOf { Color.White }

object MaterialTheme {
    val colorScheme: ColorScheme @Composable get() = LocalColorScheme.current
    val typography: Typography @Composable get() = LocalTypography.current
    val shapes: Shapes get() = Shapes()
}

@Composable
fun MaterialTheme(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    shapes: Shapes = Shapes(),
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalColorScheme provides colorScheme,
        LocalTypography provides typography,
        LocalContentColor provides colorScheme.onSurface,
        content = content
    )
}

// ---------------------------------------------------------------------------- texte

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val textColor = color.takeIf { it.isSpecified } ?: style.color.takeIf { it.isSpecified } ?: LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            TextStyle(
                color = textColor, fontSize = fontSize, fontWeight = fontWeight, fontStyle = fontStyle,
                fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration,
                textAlign = textAlign ?: androidx.compose.ui.text.style.TextAlign.Unspecified, lineHeight = lineHeight
            )
        ),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines
    )
}

@Composable
fun Text(
    text: androidx.compose.ui.text.AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val textColor = color.takeIf { it.isSpecified } ?: style.color.takeIf { it.isSpecified } ?: LocalContentColor.current
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(
            TextStyle(
                color = textColor, fontSize = fontSize, fontWeight = fontWeight, fontFamily = fontFamily,
                letterSpacing = letterSpacing, textAlign = textAlign ?: TextAlign.Unspecified, lineHeight = lineHeight
            )
        ),
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines
    )
}

val LocalTextStyle = compositionLocalOf { TextStyle.Default }

// ------------------------------------------------------------------------ surfaces

@Immutable
class Border(val border: BorderStroke, val inset: Dp = 0.dp, val shape: Shape? = null) {
    companion object { val None = Border(BorderStroke(0.dp, Color.Transparent)) }
}

@Immutable
class Glow(val elevationColor: Color, val elevation: Dp) {
    companion object { val None = Glow(Color.Transparent, 0.dp) }
}

@Immutable class ClickableSurfaceShape(val shape: Shape, val focusedShape: Shape, val pressedShape: Shape)
@Immutable class ClickableSurfaceColors(
    val containerColor: Color, val contentColor: Color,
    val focusedContainerColor: Color, val focusedContentColor: Color,
    val pressedContainerColor: Color, val pressedContentColor: Color
)
@Immutable class ClickableSurfaceScale(val scale: Float, val focusedScale: Float, val pressedScale: Float)
@Immutable class ClickableSurfaceBorder(val border: Border, val focusedBorder: Border, val pressedBorder: Border)
@Immutable class ClickableSurfaceGlow(val glow: Glow, val focusedGlow: Glow, val pressedGlow: Glow)

object ClickableSurfaceDefaults {
    fun shape(shape: Shape = RoundedCornerShape(8.dp), focusedShape: Shape = shape, pressedShape: Shape = shape) =
        ClickableSurfaceShape(shape, focusedShape, pressedShape)

    @Composable
    fun colors(
        containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor: Color = MaterialTheme.colorScheme.onSurface,
        focusedContentColor: Color = MaterialTheme.colorScheme.surface,
        pressedContainerColor: Color = focusedContainerColor,
        pressedContentColor: Color = focusedContentColor,
        disabledContainerColor: Color = containerColor,
        disabledContentColor: Color = contentColor
    ) = ClickableSurfaceColors(containerColor, contentColor, focusedContainerColor, focusedContentColor, pressedContainerColor, pressedContentColor)

    fun scale(scale: Float = 1f, focusedScale: Float = 1.1f, pressedScale: Float = scale, disabledScale: Float = scale, focusedDisabledScale: Float = scale) =
        ClickableSurfaceScale(scale, focusedScale, pressedScale)

    fun border(
        border: Border = Border.None, focusedBorder: Border = border, pressedBorder: Border = focusedBorder,
        disabledBorder: Border = border, focusedDisabledBorder: Border = border
    ) = ClickableSurfaceBorder(border, focusedBorder, pressedBorder)

    fun glow(glow: Glow = Glow.None, focusedGlow: Glow = glow, pressedGlow: Glow = glow) =
        ClickableSurfaceGlow(glow, focusedGlow, pressedGlow)
}

/** Une surface simple, sans action. */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    colors: SurfaceColors = SurfaceDefaults.colors(),
    border: Border = Border.None,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(shape)
            .background(colors.containerColor, shape)
            .then(if (border.border.width > 0.dp) Modifier.border(border.border, border.shape ?: shape) else Modifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.contentColor) { content() }
    }
}

@Immutable class SurfaceColors(val containerColor: Color, val contentColor: Color)
object SurfaceDefaults {
    @Composable
    fun colors(containerColor: Color = MaterialTheme.colorScheme.surface, contentColor: Color = MaterialTheme.colorScheme.onSurface) =
        SurfaceColors(containerColor, contentColor)
}

/**
 * La tuile cliquable : grandit, change de couleur et de contour quand elle est choisie.
 * Entrée (ou OK) déclenche [onClick] au relâchement, un appui de plus d'une demi-seconde
 * [onLongClick] ; à la souris, clic gauche et clic droit.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    tonalElevation: Dp = 0.dp,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactions = interactionSource ?: remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val lit by interactions.collectIsFocusedAsState()
    var pressed by remember { mutableStateOf(false) }
    var pressedAt by remember { mutableLongStateOf(0L) }
    // Survol de la souris : la tuile s'éclaire comme si elle avait le focus
    var hoverFocus by remember { mutableStateOf<FocusInteraction.Focus?>(null) }

    val current = when {
        pressed -> 2
        lit -> 1
        else -> 0
    }
    val targetScale = when (current) { 2 -> scale.pressedScale; 1 -> scale.focusedScale; else -> scale.scale }
    val animatedScale by animateFloatAsState(targetScale, tween(150), label = "surfaceScale")
    val container by animateColorAsState(
        when (current) { 2 -> colors.pressedContainerColor; 1 -> colors.focusedContainerColor; else -> colors.containerColor },
        tween(150), label = "surfaceContainer"
    )
    val contentColor = when (current) { 2 -> colors.pressedContentColor; 1 -> colors.focusedContentColor; else -> colors.contentColor }
    val currentShape = when (current) { 2 -> shape.pressedShape; 1 -> shape.focusedShape; else -> shape.shape }
    val currentBorder = when (current) { 2 -> border.pressedBorder; 1 -> border.focusedBorder; else -> border.border }
    val currentGlow = when (current) { 2 -> glow.pressedGlow; 1 -> glow.focusedGlow; else -> glow.glow }

    Box(
        modifier
            .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
            .then(
                if (currentGlow.elevation > 0.dp && currentGlow.elevationColor.alpha > 0f) Modifier.drawBehind {
                    val r = currentGlow.elevation.toPx()
                    drawRect(
                        Brush.radialGradient(
                            listOf(currentGlow.elevationColor.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f + r / 3f),
                            radius = maxOf(size.width, size.height) / 2f + r
                        ),
                        topLeft = Offset(-r, -r),
                        size = androidx.compose.ui.geometry.Size(size.width + 2 * r, size.height + 2 * r)
                    )
                } else Modifier
            )
            .semantics { role = Role.Button }
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (!enabled) return@onKeyEvent false
                val select = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter || event.key == Key.Spacebar
                if (!select) return@onKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (pressedAt == 0L) { pressedAt = System.currentTimeMillis(); pressed = true }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val held = System.currentTimeMillis() - pressedAt
                        val wasDown = pressedAt != 0L
                        pressedAt = 0L
                        pressed = false
                        if (wasDown) {
                            if (held >= 500 && onLongClick != null) onLongClick() else onClick()
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusable(enabled, interactions)
            .hoverable(interactions, enabled)
            .onPointerEvent(PointerEventType.Enter) {
                if (enabled && hoverFocus == null) {
                    val f = FocusInteraction.Focus()
                    hoverFocus = f
                    scope.launch { interactions.emit(f) }
                }
            }
            .onPointerEvent(PointerEventType.Exit) {
                hoverFocus?.let { f ->
                    hoverFocus = null
                    scope.launch { interactions.emit(FocusInteraction.Unfocus(f)) }
                }
            }
            .onPointerEvent(PointerEventType.Press) { e ->
                if (!enabled) return@onPointerEvent
                if (e.button == PointerButton.Primary) pressed = true
            }
            .onPointerEvent(PointerEventType.Release) { e ->
                if (!enabled) return@onPointerEvent
                when (e.button) {
                    PointerButton.Primary -> {
                        if (pressed) {
                            pressed = false
                            runCatching { focusRequester.requestFocus() }
                            com.cinetv.desktop.Pointer.clicked()
                            onClick()
                        }
                    }
                    PointerButton.Secondary -> {
                        runCatching { focusRequester.requestFocus() }
                        onLongClick?.invoke()
                    }
                    else -> Unit
                }
            }
            .then(if (currentBorder.border.width > 0.dp) Modifier.border(currentBorder.border, currentBorder.shape ?: currentShape) else Modifier)
            .clip(currentShape)
            .background(container, currentShape)
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }

    // Une tuile retirée de l'écran pendant le survol ne garde pas une lumière fantôme
    LaunchedEffect(enabled) {
        if (!enabled) hoverFocus?.let { interactions.emit(FocusInteraction.Unfocus(it)); hoverFocus = null }
    }
}

