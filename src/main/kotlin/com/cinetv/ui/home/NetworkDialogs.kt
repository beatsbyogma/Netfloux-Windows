package com.cinetv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cinetv.desktop.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.cinetv.R
import com.cinetv.data.network.FoundHost
import com.cinetv.data.network.NetworkShare
import com.cinetv.ui.components.entrance
import com.cinetv.ui.theme.Ink
import com.cinetv.ui.theme.Motion
import com.cinetv.ui.theme.Radius
import kotlinx.coroutines.delay

/**
 * Ajout d'un dossier partagé du réseau.
 *
 * Quatre renseignements suffisent : la machine, le dossier qu'elle partage,
 * et le compte autorisé à y entrer. Le partage est testé avant d'être gardé.
 */
@Composable
fun AddShareDialog(
    busy: Boolean,
    error: String?,
    onSubmit: (NetworkShare) -> Unit,
    onDismiss: () -> Unit,
    /** Machines trouvées sur le réseau, et dossiers partagés de celle choisie. */
    hosts: List<FoundHost> = emptyList(),
    hostShares: List<String> = emptyList(),
    searching: Boolean = false,
    onFindHosts: () -> Unit = {},
    onFindShares: (host: String, user: String, password: String) -> Unit = { _, _, _ -> }
) {
    var label by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var share by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    fun cleanHost() = host.trim().removePrefix("\\\\").removePrefix("smb://").trim('/')

    val first = remember { FocusRequester() }
    // La recherche des machines part toute seule à l'ouverture
    LaunchedEffect(Unit) { onFindHosts() }
    LaunchedEffect(Unit) {
        repeat(12) {
            if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(40)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.cinetv.ui.components.SoftNavSound()
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.Void.copy(alpha = 0.72f)),
            contentAlignment = Alignment.CenterEnd
        ) {
            Column(
                Modifier
                    .entrance(fromX = Motion.panelSlide)
                    .width(440.dp)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Ink.Surface, Ink.Deep)))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 30.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("AJOUTER UN DOSSIER DU RÉSEAU", style = MaterialTheme.typography.headlineSmall, color = Ink.TextHi)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Un dossier partagé par un PC, un mini PC ou un NAS de la maison.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.TextMid
                )
                Spacer(Modifier.height(14.dp))

                Field(
                    "Adresse de la machine", host, { host = it },
                    hint = "ex. 192.168.1.20 ou le nom du PC",
                    keyboard = KeyboardType.Uri,
                    modifier = Modifier.focusRequester(first)
                )
                // Les machines qui partagent des dossiers : un appui remplit l'adresse
                PickRow(
                    caption = when {
                        searching -> "Recherche des machines du réseau…"
                        hosts.isEmpty() -> "Aucune machine trouvée — saisissez l'adresse"
                        else -> "Machines trouvées"
                    },
                    choices = hosts.map { it.label to it.address },
                    onPick = { host = it }
                )

                Field("Utilisateur", user, { user = it }, hint = "compte Windows — vide si accès libre")
                Field("Mot de passe", password, { password = it }, hint = "celui de ce compte", secret = true)

                Field("Dossier partagé", share, { share = it }, hint = "le nom du partage, ex. Films")
                // Les dossiers que la machine partage avec ce compte : un appui remplit le nom
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 9.dp)) {
                    PillButton(
                        icon = R.drawable.ic_folder_open,
                        label = "VOIR SES DOSSIERS",
                        onClick = { if (!busy) onFindShares(cleanHost(), user.trim(), password) }
                    )
                    Spacer(Modifier.width(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(hostShares) { name ->
                            PillButton(icon = R.drawable.ic_folder, label = name, onClick = { share = name })
                        }
                    }
                }

                Field("Nom affiché", label, { label = it }, hint = "facultatif — sinon le nom du dossier", last = true)

                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        busy -> "Connexion au partage…"
                        error != null -> error
                        else -> " "
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (error != null && !busy) Ink.Warn else Ink.Lilac,
                    maxLines = 2
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        icon = R.drawable.ic_check,
                        label = if (busy) "PATIENTEZ…" else "TESTER ET AJOUTER",
                        onClick = {
                            if (!busy) onSubmit(
                                NetworkShare(
                                    label = label.trim(),
                                    host = cleanHost(),
                                    share = share.trim().trim('/', '\\'),
                                    user = user.trim(),
                                    password = password
                                )
                            )
                        }
                    )
                    PillButton(icon = R.drawable.ic_close, label = "ANNULER", onClick = onDismiss)
                }
            }
        }
    }
}

/** Une rangée de choix à un appui, sous un champ. */
@Composable
private fun PickRow(
    caption: String,
    choices: List<Pair<String, String>>,     // (texte affiché, valeur)
    onPick: (String) -> Unit
) {
    Column(Modifier.padding(bottom = 9.dp)) {
        Text(caption.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.TextLow)
        if (choices.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(choices) { (text, value) ->
                    PillButton(icon = R.drawable.ic_network, label = text, onClick = { onPick(value) })
                }
            }
        }
    }
}

@Composable
private fun Field(
    title: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    last: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.control)

    Column(Modifier.padding(bottom = 9.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (focused) Ink.Lilac else Ink.TextLow
        )
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.TextHi),
            cursorBrush = SolidColor(Ink.Lilac),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else keyboard,
                imeAction = if (last) ImeAction.Done else ImeAction.Next,
                autoCorrectEnabled = false
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                onDone = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (focused) Ink.SurfaceHi else Ink.Surface)
                        .border(1.dp, if (focused) Ink.Violet else Ink.Line, shape)
                        .padding(horizontal = 11.dp, vertical = 8.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyMedium, color = Ink.TextLow, maxLines = 1)
                    }
                    inner()
                }
            }
        )
    }
}
