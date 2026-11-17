package com.cinetv.ui.screensaver

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cinetv.ui.brand.NetflouxMark
import com.cinetv.ui.components.KenBurnsImage
import com.cinetv.ui.components.TitleLogo
import com.cinetv.ui.splash.CatInWhite
import com.cinetv.ui.splash.CatSheet
import com.cinetv.ui.splash.LoadingCat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.TitleArt
import kotlinx.coroutines.delay

/**
 * Économiseur d'écran : les visuels de la bibliothèque défilent en plein écran.
 * Les images sont déjà en cache, l'affichage ne coûte donc presque rien.
 */
@Composable
fun Screensaver(
    backdrops: List<String>,
    titles: List<String>,
    onWake: () -> Unit,
    /** Logos des titres, dans le même ordre ; vide quand un titre n'en a pas. */
    logos: List<String> = emptyList()
) {
    var index by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    // La planche du chat est lue hors du fil d'affichage, une fois
    val context = LocalContext.current
    val catSheet by produceState<ImageBitmap?>(null) {
        value = withContext(Dispatchers.IO) { CatSheet.load(context) }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    LaunchedEffect(backdrops) {
        if (backdrops.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(14_000)
            index = (index + 1) % backdrops.size
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Void)
            .focusRequester(focus)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) { onWake(); true } else false
            }
            .focusable()
    ) {
        if (backdrops.isNotEmpty()) {
            Crossfade(
                targetState = index,
                animationSpec = tween(1400),
                label = "screensaver"
            ) { current ->
                KenBurnsImage(
                    url = backdrops[current % backdrops.size],
                    durationMs = 26_000,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink.Void.copy(alpha = 0.45f),
                        0.5f to Color.Transparent,
                        1f to Ink.Void.copy(alpha = 0.88f)
                    )
                )
        )

        // Le chat qui danse, en blanc, fixé dans le coin haut gauche
        LoadingCat(
            sheet = catSheet,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 60.dp, top = 44.dp).size(SAVER_CAT),
            colorFilter = CatInWhite
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 60.dp, bottom = 52.dp)
        ) {
            NetflouxMark(fontSize = 20.sp, tint = Color.White)
            if (titles.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                // Le logo du film s'il en a un, sinon son titre écrit
                TitleLogo(
                    url = logos.getOrNull(index % titles.size).orEmpty(),
                    maxWidth = TitleArt.saverWidth,
                    maxHeight = TitleArt.saverHeight
                ) {
                    Text(
                        titles[index % titles.size],
                        style = MaterialTheme.typography.headlineLarge,
                        color = Ink.TextHi
                    )
                }
            }
        }
    }
}

/** Taille du chat, dans le coin de l'écran de veille. */
private val SAVER_CAT = 40.dp
