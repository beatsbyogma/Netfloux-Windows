#!/usr/bin/env python3
"""Fabrique les sources de Netfloux pour Windows à partir de celles de la TV."""
import os, re, shutil, sys

APP = '/home/claude/CineTV3/app/src/main'
SRC = APP + '/java/com/cinetv'
DESK = '/home/claude/port-tools/desktop'
OUT = '/home/claude/NetflouxWindows/src/main/kotlin'
RES_OUT = '/home/claude/NetflouxWindows/src/main/resources'

EXCLUDE = {
    'CineTvApp.kt', 'MainActivity.kt',
    'data/network/SmbDataSource.kt', 'data/network/SchemeDataSource.kt',
}

problems = []

def must_replace(text, old, new, name, count=1):
    if old not in text:
        problems.append(f'{name}: motif introuvable: {old[:70]!r}')
        return text
    return text.replace(old, new, count)

def cut(text, start, end, name, keep_end=True, replacement=''):
    """Retire de start (inclus) jusqu'à end (exclu si keep_end)."""
    i = text.find(start)
    if i < 0:
        problems.append(f'{name}: début introuvable: {start[:60]!r}'); return text
    if end is None:
        return text[:i] + replacement
    j = text.find(end, i + len(start))
    if j < 0:
        problems.append(f'{name}: fin introuvable: {end[:60]!r}'); return text
    return text[:i] + replacement + (text[j:] if keep_end else text[j + len(end):])

def patch_all(rel, text):
    text = re.sub(r'^import androidx\.compose\.ui\.window\.Dialog$', 'import com.cinetv.desktop.Dialog', text, flags=re.M)
    text = re.sub(r'^import androidx\.media3\.(?!common\.util\.UnstableApi).*\n', '', text, flags=re.M)
    text = re.sub(r'^import androidx\.compose\.ui\.viewinterop\.AndroidView\n', '', text, flags=re.M)
    text = text.replace('event.nativeKeyEvent.repeatCount', 'com.cinetv.desktop.KeyRepeat.count')
    text = re.sub(r'^import com\.cinetv\.data\.network\.(SchemeDataSource|SmbDataSource)\n', '', text, flags=re.M)
    text = text.replace('AspectRatioFrameLayout.RESIZE_MODE_FIT', '0').replace('AspectRatioFrameLayout.RESIZE_MODE_ZOOM', '1').replace('AspectRatioFrameLayout.RESIZE_MODE_FILL', '2')
    return text

def patch(rel, text):
    n = rel
    if rel == 'ui/components/Focus.kt':
        text = must_replace(text, 'graphicsLayer { scaleX = squash; scaleY = squash }.onPreviewKeyEvent',
            'graphicsLayer { scaleX = squash; scaleY = squash }.then(com.cinetv.desktop.secondaryClick(onLongClick)).onPreviewKeyEvent', n)
    elif rel == 'ui/player/PlayerScreen.kt':
        text = cut(text, '/** Le fichier à lire, accompagné de ses sous-titres externes. */', '/** Ce que le lecteur affiche du titre en cours', n)
        text = must_replace(text, '    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }\n    val pendingCues = remember { mutableListOf<Job>() }\n', '', n)
        text = cut(text, '        AndroidView(\n            factory = { ctx ->\n                org.videolan.libvlc.util.VLCVideoLayout(ctx)',
                   '        if (showTech) {', n,
                   replacement='        LaunchedEffect(aspect) { player.setScale(aspect) }\n        VideoSurface(player, Modifier.fillMaxSize())\n        }\n\n')
        text = cut(text, '@UnstableApi\nprivate fun Tracks.optionsOfType(', 'internal fun formatTime(ms: Long)', n)
        text = cut(text, '/** Dans une réplique écrite sur une seule ligne,', None, n)
        for imp in ['import android.view.ViewGroup\n', 'import android.view.View\n']:
            text = text.replace(imp, '')
    elif rel == 'ui/player/PlayerExtras.kt':
        text = cut(text, '@UnstableApi\nclass SyncRenderers(', 'internal val SUB_SIZES', n)
        text = cut(text, '@UnstableApi\ninternal fun captionStyle(', '@Composable\ninternal fun SidePanel', n)
        text = cut(text, '@UnstableApi\ninternal fun techLines(player: ExoPlayer', None, n)
    elif rel == 'ui/drives/DrivesScreen.kt':
        text = cut(text, '    val permissionLauncher = rememberLauncherForActivityResult(', '    fun startScan() {', n)
        text = must_replace(text, '        else permissionLauncher.launch(StorageAccess.requiredPermission)', '        else viewModel.scanAll()', n)
        text = text.replace('import androidx.activity.compose.rememberLauncherForActivityResult\n', '')
        text = text.replace('import androidx.activity.result.contract.ActivityResultContracts\n', '')
    elif rel == 'ui/components/ContextMenu.kt':
        text = cut(text, 'private fun openAllFilesAccess(context: android.content.Context) {', '/**\n * « Quitter Netfloux ? »', n,
                   replacement='private fun openAllFilesAccess(context: android.content.Context) {\n'
                               '    android.widget.Toast.makeText(context, "Vérifiez que le disque n\'est pas en lecture seule", android.widget.Toast.LENGTH_LONG).show()\n}\n\n\n')
    elif rel == 'ui/components/DriveNotice.kt':
        text = cut(text, '    // Branchement ou retrait d\'un disque USB : on revérifie qui répond\n    DisposableEffect(Unit) {',
                   '    // À chaque changement de la bibliothèque', n)
        for imp in ['import android.content.BroadcastReceiver\n', 'import android.content.Intent\n',
                    'import android.content.IntentFilter\n', 'import androidx.core.content.ContextCompat\n']:
            text = text.replace(imp, '')
    elif rel == 'ui/settings/SettingsScreen.kt':
        text = must_replace(text, '            item("library") { Section("Bibliothèque") }\n',
            '            item("library") { Section("Bibliothèque") }\n'
            '            item("pcDrives") {\n'
            '                SettingRow(\n'
            '                    icon = R.drawable.ic_disc,\n'
            '                    label = "Disques affichés",\n'
            '                    value = com.cinetv.desktop.DrivePickerState.summary(),\n'
            '                    hint = "Choisissez les disques du PC que Netfloux montre",\n'
            '                    onClick = { com.cinetv.desktop.DrivePickerState.open = true }\n'
            '                )\n'
            '            }\n', n)
    return text

def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    count = 0
    for root, _, files in os.walk(SRC):
        for f in files:
            if not f.endswith('.kt'):
                continue
            full = os.path.join(root, f)
            rel = os.path.relpath(full, SRC)
            if rel in EXCLUDE:
                continue
            text = open(full, encoding='utf-8').read()
            text = patch(rel, patch_all(rel, text))
            dest = os.path.join(OUT, 'com/cinetv', rel)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            open(dest, 'w', encoding='utf-8').write(text)
            count += 1
    # Les fichiers propres à Windows, par-dessus
    over = 0
    for root, _, files in os.walk(DESK):
        for f in files:
            full = os.path.join(root, f)
            rel = os.path.relpath(full, DESK)
            dest = os.path.join(OUT, rel)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            shutil.copyfile(full, dest)
            over += 1
    if os.environ.get('NOKEY') == '1':
        # Version à partager : aucune clé personnelle dans le programme
        sec = os.path.join(OUT, 'com/cinetv/data/tmdb/Secrets.kt')
        t = open(sec, encoding='utf-8').read()
        t = re.sub(r'TMDB_API_KEY = "[^"]*"', 'TMDB_API_KEY = ""', t)
        open(sec, 'w', encoding='utf-8').write(t)
        print('version sans clé')
    write_r()
    print(f'{count} fichiers repris, {over} fichiers Windows')
    if problems:
        print('PROBLÈMES :'); [print(' -', p) for p in problems]; sys.exit(1)

def ident(name):
    return re.sub(r'[^A-Za-z0-9_]', '_', name)

def write_r():
    groups = {}
    paths = []
    for folder in sorted(os.listdir(RES_OUT)):
        d = os.path.join(RES_OUT, folder)
        if not os.path.isdir(d) or folder not in ('drawable', 'font', 'raw', 'mipmap'):
            continue
        for f in sorted(os.listdir(d)):
            paths.append(f'{folder}/{f}')
            groups.setdefault(folder, []).append((ident(f.rsplit('.', 1)[0]), len(paths)))
    lines = ['package com.cinetv', '', '/** Les ressources de l\'application, désignées par un numéro comme sur Android (fichier généré). */', 'object R {']
    for g, items in groups.items():
        lines.append(f'    object {g} {{')
        for name, i in items:
            lines.append(f'        const val {name}: Int = {i}')
        lines.append('    }')
    lines.append('    val PATHS: List<String> = listOf(')
    for p in paths:
        lines.append(f'        "{p}",')
    lines.append('    )')
    lines.append('}')
    open(os.path.join(OUT, 'com/cinetv/R.kt'), 'w', encoding='utf-8').write('\n'.join(lines) + '\n')

main()
