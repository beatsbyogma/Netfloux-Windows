package com.cinetv.ui.home

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import com.cinetv.ui.components.rememberColorFill
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.ui.brand.NetflouxBadge
import com.cinetv.ui.components.CineIcon
import com.cinetv.ui.components.FocusStyle
import com.cinetv.ui.theme.HomePage
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion

/*
 * Les commandes de l'accueil : rail de gauche et barre du haut.
 *
 * Aucun rond, aucun cadre autour des pictos : au repos ils sont posés
 * sur le fond, et la sélection se lit à un léger grossissement et au
 * passage au rouge du logo.
 */

/** Teinte d'un picto : blanc plein quand il est sélectionné, blanc transparent sinon. */
@Composable
private fun glyphTint(focused: Boolean, active: Boolean): Color {
    // Sélectionné : blanc. Rubrique où l'on se trouve : rouge, menu déroulé ou non. Le reste : blanc estompé.
    val target = when {
        focused -> Color.White
        active -> Ink.LogoRed
        else -> Color.White.copy(alpha = HomePage.railRestAlpha)
    }
    val color by animateColorAsState(target, tween(Motion.iconHoverMs), label = "glyphTint")
    return color
}

// ═══════════════════════════════════════════════════════ RAIL DE GAUCHE

/** Une entrée du rail : une icône, un nom, une action. */
data class RailEntry(
    val icon: Int,
    val label: String,
    val active: Boolean = false,
    val onClick: () -> Unit
)

/**
 * Rail d'icônes, collé au bord gauche.
 *
 * Au repos il ne montre que les icônes ; dès qu'une d'elles prend le focus
 * il s'élargit et affiche les noms, par-dessus la page — la mise en page
 * derrière ne bouge pas d'un pixel.
 */
@Composable
fun NavRail(
    top: List<RailEntry>,
    bottom: List<RailEntry>,
    connected: Boolean,
    modifier: Modifier = Modifier,
    /** Ce que la page dessine derrière le menu : il le montre flouté et assombri. */
    backdrop: GraphicsLayer? = null,
    /**
     * Le menu n'est accessible que lorsque la page le décide (flèche gauche) :
     * hors de ce moment, aucune de ses entrées ne peut recevoir le focus.
     */
    active: Boolean = false,
    /** Point d'entrée : la page lui donne le focus en ouvrant le menu. */
    entry: FocusRequester = remember { FocusRequester() },
    /** Flèche droite ou Retour : rend la main à la page. */
    onLeave: () -> Unit = {},
    /** Couleur du N : celle du bouton « Lire » de la bannière. */
    badgeTint: Color = Color.White
) {
    // Ouvert à l'entrée dans le menu. Refermé sur ses pictos dès qu'on choisit
    // une entrée ; flèche gauche le rouvre.
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(active) { open = active }
    BackHandler(enabled = active) { onLeave() }
    // 0 = replié, 1 = déroulé : tout le panneau suit cette seule valeur
    val unroll by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(HomePage.railOpenMs, easing = FastOutSlowInEasing),
        label = "railUnroll"
    )
    val shape = RoundedCornerShape(HomePage.railRadius)
    val gap = lerp(HomePage.railGap, HomePage.railGapOpen, unroll)
    val pad = lerp(HomePage.railPad, HomePage.railPadOpen, unroll)
    // Largeur repliée : un picto et ses marges, pour que tout soit centré sur le même axe
    val closedWidth = HomePage.railIcon + HomePage.railPadSide * 2

    // Le fond « verre » : la page de derrière, redessinée ici avec un flou
    val canBlur = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val frosted = rememberGraphicsLayer()
    val blurPx = with(LocalDensity.current) { HomePage.railBlur.toPx() }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier.fillMaxHeight().padding(start = HomePage.railInset, top = HomePage.railTop),
        contentAlignment = Alignment.TopStart
    ) {
      Column {
        // Le N reste hors de la boîte, centré sur l'axe des pictos
        Box(
            Modifier.width(closedWidth).padding(bottom = HomePage.railBadgeGap),
            contentAlignment = Alignment.Center
        ) {
            // À chaque nouveau film, sa couleur remplit le N de bas en haut
            val fill = rememberColorFill(badgeTint)
            NetflouxBadge(size = HomePage.railBadge, tint = fill.from)
            NetflouxBadge(
                size = HomePage.railBadge,
                tint = fill.to,
                modifier = Modifier.drawWithContent {
                    val p = fill.progress.value
                    clipRect(top = size.height * (1f - p)) { this@drawWithContent.drawContent() }
                }
            )
        }
        Column(
            Modifier
                .clip(shape)
                // Déroulé, le panneau prend juste la largeur de ses noms : la même marge
                // à droite qu'à gauche. La largeur visible suit l'animation.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                    val closedPx = closedWidth.roundToPx()
                    val shown = (closedPx + (placeable.width - closedPx) * unroll).toInt().coerceAtLeast(closedPx)
                    layout(shown, placeable.height) { placeable.place(0, 0) }
                }
                .width(IntrinsicSize.Max)
                // Aucune entrée ne prend le focus tant que le menu n'est pas ouvert par la page
                .focusProperties { canFocus = active }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { open = true; true }
                        Key.DirectionRight -> { onLeave(); true }
                        else -> false
                    }
                }
                .onGloballyPositioned { origin = it.positionInRoot() }
                .drawBehind {
                    if (backdrop != null) {
                        // Le fond est redessiné en tout petit, puis ré-agrandi : cette
                        // réduction le floute sur n'importe quelle TV. Les plus récentes
                        // ajoutent un vrai flou par-dessus.
                        val shrink = HomePage.railBlurDownscale
                        frosted.compositingStrategy = CompositingStrategy.Offscreen
                        if (canBlur) {
                            frosted.renderEffect = BlurEffect(blurPx / shrink, blurPx / shrink, TileMode.Clamp)
                        }
                        frosted.record(
                            size = IntSize(
                                (size.width / shrink).toInt().coerceAtLeast(1),
                                (size.height / shrink).toInt().coerceAtLeast(1)
                            )
                        ) {
                            scale(1f / shrink, 1f / shrink, pivot = Offset.Zero) {
                                translate(-origin.x, -origin.y) { drawLayer(backdrop) }
                            }
                        }
                        scale(shrink, shrink, pivot = Offset.Zero) { drawLayer(frosted) }
                        drawRect(Ink.Void.copy(alpha = HomePage.railDim))
                    } else {
                        drawRect(Ink.Surface.copy(alpha = HomePage.railDimNoBlur))
                    }
                }
                .padding(horizontal = HomePage.railPadSide, vertical = pad),
            horizontalAlignment = Alignment.Start
        ) {
            // Choisir une entrée referme le menu sur ses seuls pictos
            val pick: (RailEntry) -> RailEntry = { entry ->
                entry.copy(onClick = { open = false; entry.onClick() })
            }
            top.forEachIndexed { index, item ->
                RailButton(
                    pick(item), unroll,
                    if (index == 0) Modifier.focusRequester(entry) else Modifier
                )
                Spacer(Modifier.height(gap))
            }

            // Un filet sépare les réglages du reste, une fois le panneau ouvert
            Box(
                Modifier
                    .padding(vertical = gap)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Ink.Line.copy(alpha = unroll))
            )

            bottom.forEach { entry ->
                RailButton(pick(entry), unroll)
                Spacer(Modifier.height(gap))
            }
        }
      }
    }
}

@Composable
private fun RailButton(entry: RailEntry, unroll: Float, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tint = glyphTint(focused, entry.active)

    Surface(
        onClick = entry.onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = FocusStyle.colors(container = Color.Transparent, content = tint),
        scale = FocusStyle.lift(Motion.iconHover),
        glow = FocusStyle.glow(),
        border = FocusStyle.noBorder(),
        modifier = modifier.height(HomePage.railIcon)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(HomePage.railIcon),
                contentAlignment = Alignment.Center
            ) {
                CineIcon(entry.icon, tint = tint, modifier = Modifier.size(HomePage.railGlyph))
            }
            if (unroll > 0.01f) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = HomePage.railLabel,
                        letterSpacing = HomePage.railLabelSpacing.sp
                    ),
                    color = tint,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .alpha(unroll)
                        .padding(start = 8.dp, end = (HomePage.railIcon - HomePage.railGlyph) / 2)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════ BOUTONS

/** Pastille arrondie avec icône et texte — le bouton « Rechercher ». */
@Composable
fun PillButton(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val container by animateColorAsState(
        if (focused) Ink.LogoRed else Ink.SurfaceHi.copy(alpha = 0.72f),
        tween(Motion.iconHoverMs),
        label = "pillBg"
    )
    val content = if (focused) Color.White else Ink.TextMid

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = FocusStyle.colors(container = container, content = content),
        scale = FocusStyle.lift(1.06f),
        glow = FocusStyle.glow(),
        border = FocusStyle.noBorder(),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            CineIcon(icon, tint = content, modifier = Modifier.size(12.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = content)
        }
    }
}
