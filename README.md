# Netfloux — Windows

La version PC de [Netfloux](https://github.com/beatsbyogma/Netfloux-AndroidTV) : médiathèque personnelle qui parcourt les disques du PC
et les partages réseau, récupère jaquettes, logos et résumés sur TMDB, et lit les vidéos avec VLC (embarqué).

## Ce qui change par rapport à la TV

- **Disques affichés** : seuls les disques cochés (Paramètres › Bibliothèque › Disques affichés) entrent dans
  Netfloux. Un disque est reconnu par son numéro de série de volume : il est retrouvé même si Windows lui
  donne une autre lettre. Un disque jamais vu ouvre la fenêtre de choix.
- **Clavier et souris** : flèches, Entrée, Échap / Retour arrière / bouton « précédent » de la souris pour revenir,
  F11 pour le plein écran ; survol, clic, clic droit = appui long.
- **Bibliothèque indépendante** de celle de la TV, rangée dans `%APPDATA%\Netfloux`.
- Bande-annonce ouverte dans le navigateur ; suppression via la Corbeille quand le disque en a une ;
  mots de passe réseau chiffrés par Windows (DPAPI) ; le PC ne se met pas en veille pendant un film.

## Technique

- Kotlin + Compose Multiplatform (desktop), Java 21, Gradle 9.4.1.
- Lecture : VLC 3.0.21 via vlcj 4.8.3, téléchargé et embarqué automatiquement à la compilation.
- Base SQLite (sqlite-jdbc), partages SMB (jcifs-ng).
- Les écrans et la logique sont **ceux de la version TV**, repris tels quels. `src/main/kotlin/android/…`,
  `androidx/…` et `coil/…` sont de petites couches de compatibilité qui imitent les bibliothèques Android ;
  `com/cinetv/desktop/…` et quelques fichiers remplacés (disques, lecteur, sons…) sont propres à Windows.

## Compiler

Ouvrir le dossier dans Android Studio ou IntelliJ, puis lancer une des configurations fournies :

| Configuration | Effet |
|---|---|
| Netfloux (lancer) | lance l'appli |
| Netfloux (créer l'appli Windows) | fabrique `build/compose/binaries/main/app/Netfloux/Netfloux.exe` (dossier complet, avec Java et VLC) |
| Netfloux (compiler) | compile seulement ; les erreurs vont aussi dans `build/compile-log.txt` |

En ligne de commande : `gradlew run` ou `gradlew createDistributable`.

## Clé TMDB

Le dépôt ne contient **aucune clé**. Chaque utilisateur colle la sienne dans Paramètres › Clé TMDB
(gratuite sur https://www.themoviedb.org/settings/api, type « Developer »). Pour une version perso avec
clé intégrée, la mettre dans `src/main/kotlin/com/cinetv/data/tmdb/Secrets.kt` sans la committer.

## Mettre à jour depuis la version TV

`tools/port.py` régénère `src/main/kotlin` à partir des sources Android (exclusions, petites retouches,
puis fichiers de `tools/desktop/` par-dessus, et génération de `R.kt`). Les chemins en tête du script
(`APP`, `DESK`, `OUT`) sont à adapter à votre machine. `NOKEY=1` produit une version sans clé.
