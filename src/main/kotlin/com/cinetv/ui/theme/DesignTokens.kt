package com.cinetv.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinetv.R
import com.cinetv.data.model.DriveTheme

/*
 * ══════════════════════════════════════════════════════════════════════════
 *  NETFLOUX — RÉGLAGES DE DESIGN
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  Tout ce qui se règle à l'œil est ici, et nulle part ailleurs.
 *  Couleurs, tailles, arrondis, animations : un seul fichier à ouvrir.
 *
 *  POUR MODIFIER EN DIRECT, SANS RECOMPILER :
 *    1. Android Studio → Settings → Editor → Live Edit
 *    2. Cocher « Enable Live Edit » puis « Push Edits Automatically »
 *    3. Lancer l'app sur la TV, modifier une valeur ici, enregistrer :
 *       l'écran se met à jour en une seconde.
 *
 *  Les couleurs s'écrivent 0xAARRGGBB — les deux premiers caractères après
 *  le 0x sont l'opacité : FF = opaque, 80 = à moitié, 00 = invisible.
 */

// ─────────────────────────────────────────────────────────── TYPOGRAPHIE

/**
 * Archivo. Les titres en Expanded Black, large et massif ; le reste en
 * Archivo regular, plus neutre, pour que le visuel garde la vedette.
 */
object Fonts {
    val Display = FontFamily(
        Font(R.font.archivo_expanded_black, FontWeight.Black),
        Font(R.font.archivo_expanded_bold, FontWeight.Bold),
        Font(R.font.archivo_expanded_medium, FontWeight.Medium)
    )
    val Text = FontFamily(
        Font(R.font.archivo_regular, FontWeight.Normal),
        Font(R.font.archivo_regular, FontWeight.Medium)
    )
}

// ─────────────────────────────────────────────── POLICE SELON LE GENRE

/**
 * Habillage d'un titre : sa police, et ce qui va avec — chaque police a son
 * gabarit, il faut compenser pour qu'elles paraissent de la même force.
 *
 * scale       grossit (>1) ou réduit (<1) par rapport à la taille normale
 * uppercase   tout en capitales, ou tel qu'écrit
 * spacing     espace entre les lettres, en sp
 */
data class TitleFace(
    val family: FontFamily,
    val scale: Float = 1f,
    val uppercase: Boolean = true,
    val spacing: Float = 0f
)

/**
 * Les titres des bannières et des fiches changent de police selon le genre
 * du film. Les numéros sont les identifiants de genres de TMDB.
 */
object GenreType {
    /** false = tous les titres en Archivo, comme avant. */
    const val enabled = true

    val Default = TitleFace(Fonts.Display, scale = 1.00f, spacing = -0.5f)

    private val Action = TitleFace(FontFamily(Font(R.font.genre_anton, FontWeight.Black)), scale = 1.30f, spacing = 0.4f)
    private val SciFi = TitleFace(FontFamily(Font(R.font.genre_orbitron, FontWeight.Black)), scale = 0.98f, spacing = 0.6f)
    private val Horror = TitleFace(FontFamily(Font(R.font.genre_creepster, FontWeight.Black)), scale = 1.34f, spacing = 0.8f)
    private val Thriller = TitleFace(FontFamily(Font(R.font.genre_special_elite, FontWeight.Black)), scale = 1.10f, uppercase = false)
    private val Epic = TitleFace(FontFamily(Font(R.font.genre_cinzel, FontWeight.Black)), scale = 1.08f, spacing = 0.8f)
    private val Western = TitleFace(FontFamily(Font(R.font.genre_rye, FontWeight.Black)), scale = 1.12f, spacing = 0.3f)
    private val Comedy = TitleFace(FontFamily(Font(R.font.genre_lilita, FontWeight.Black)), scale = 1.26f, uppercase = false)
    private val Cartoon = TitleFace(FontFamily(Font(R.font.genre_fredoka, FontWeight.Black)), scale = 1.22f, uppercase = false)
    private val Romance = TitleFace(FontFamily(Font(R.font.genre_playfair, FontWeight.Black)), scale = 1.22f, uppercase = false)
    private val Music = TitleFace(FontFamily(Font(R.font.genre_righteous, FontWeight.Black)), scale = 1.16f, spacing = 0.4f)

    /** Quel habillage pour quel genre TMDB. */
    private val byGenre: Map<Int, TitleFace> = mapOf(
        28 to Action, 12 to Action, 10752 to Action,      // action, aventure, guerre
        10759 to Action, 10768 to Action,                 // séries : action & aventure, guerre & politique
        878 to SciFi, 10765 to SciFi,                     // science-fiction (films, séries)
        27 to Horror,                                     // horreur
        53 to Thriller, 80 to Thriller, 9648 to Thriller, // thriller, crime, mystère
        14 to Epic, 36 to Epic,                           // fantastique, histoire
        37 to Western,                                    // western
        35 to Comedy, 10751 to Comedy,                    // comédie, familial
        16 to Cartoon, 10762 to Cartoon,                  // animation, jeunesse
        10749 to Romance, 18 to Romance,                  // romance, drame
        10402 to Music                                    // musique
    )

    /**
     * Genres qui l'emportent quand un film en a plusieurs : un film d'animation
     * ou d'horreur se reconnaît d'abord à ça, quel que soit l'ordre donné par TMDB.
     */
    private val priority = listOf(27, 37, 16, 10762, 878, 10765, 10402)

    /** [genres] : les identifiants TMDB de la fiche, séparés par des virgules. */
    fun faceFor(genres: String): TitleFace {
        if (!enabled || genres.isBlank()) return Default
        val ids = genres.split(',').mapNotNull { it.trim().toIntOrNull() }
        val chosen = priority.firstOrNull { it in ids } ?: ids.firstOrNull { it in byGenre }
        return chosen?.let { byGenre[it] } ?: Default
    }
}

// ─────────────────────────────────────────────────────────────── COULEURS

object Ink {

    // ── Fonds, du plus sombre au plus clair : des noirs neutres, sans teinte
    val Void = Color(0xFF000000)        // fond général de l'application
    val Deep = Color(0xFF070707)        // dégradés de fond
    val Surface = Color(0xFF0D0D0E)     // cartes disque et dossier, menus
    val SurfaceHi = Color(0xFF171718)   // vignettes, champs, boutons
    val Line = Color(0xFF28282B)        // filets et séparateurs

    // ── Textes, du plus affirmé au plus discret : des blancs et gris neutres
    val TextHi = Color(0xFFF5F5F7)      // titres et élément sélectionné
    val TextMid = Color(0xFFA6A6AD)     // textes courants
    val TextLow = Color(0xFF6B6B72)     // mentions secondaires

    // ── La couleur d'accent : une seule famille, du plus profond au plus clair.
    //    Elle se choisit dans Paramètres › Affichage (voir AccentTheme plus bas) ;
    //    les noms datent du premier thème, violet.
    var Plum by mutableStateOf(AccentTheme.DEFAULT.tint)          // fonds teintés, aplats
    var VioletDeep by mutableStateOf(AccentTheme.DEFAULT.deep)    // accent sombre
    var Violet by mutableStateOf(AccentTheme.DEFAULT.main)        // accent principal
    var Lilac by mutableStateOf(AccentTheme.DEFAULT.light)        // accent de survol
    var Mauve by mutableStateOf(AccentTheme.DEFAULT.pale)         // pointes claires, étoiles

    /** Rouge du dédoublement du logo. */
    val LogoRed = Color(0xFFFF3B3F)   // un peu éclairci pour mieux ressortir sur le noir

    /** Dégradé du logo, repris sur l'accueil : bouton principal, accents. */
    val Brand: Brush get() = Brush.horizontalGradient(listOf(LogoRed, lerp(LogoRed, Violet, 0.5f), Violet))

    // ── Signalétique
    val Good: Color get() = Lilac       // vu, disque connecté
    val Warn = Color(0xFFE0A15C)        // avertissement
    val Online = Color(0xFF2ED573)      // pastille d'un disque en ligne
    val Offline = Color(0xFFFF3B30)     // pastille d'un disque hors ligne

    /** Dégradé signature : barres de progression, accents actifs. */
    val Accent: Brush get() = Brush.linearGradient(listOf(Violet, Lilac))

    /** Ancien dégradé du halo de sélection (le halo est maintenant une lueur unie). */
    val Halo: Brush get() = Brush.linearGradient(listOf(Mauve, Violet, VioletDeep))

    // ── Halos de fond de l'écran d'ouverture
    val GlowViolet: Color get() = VioletDeep.copy(alpha = 0.24f)
    val GlowEmber: Color get() = Lilac.copy(alpha = 0.18f)

    /** Applique un thème de couleur à toute l'application, immédiatement. */
    fun apply(theme: AccentTheme) {
        Plum = theme.tint
        VioletDeep = theme.deep
        Violet = theme.main
        Lilac = theme.light
        Mauve = theme.pale
    }
}

/**
 * Les thèmes de couleur proposés dans les Paramètres.
 * Pour en ajouter un : une ligne ici, avec ses cinq nuances
 * (principale, claire, très claire, sombre, fond teinté).
 */
enum class AccentTheme(
    val label: String,
    val main: Color,
    val light: Color,
    val pale: Color,
    val deep: Color,
    val tint: Color
) {
    ROUGE("Rouge Netfloux", Color(0xFFFF3B3F), Color(0xFFFF7C7F), Color(0xFFFFC7C9), Color(0xFFB30D13), Color(0xFF2B0A0C)),
    VIOLET("Violet", Color(0xFF8257FF), Color(0xFFB49BFF), Color(0xFFD9CCFF), Color(0xFF4C22C9), Color(0xFF1C1233)),
    BLEU("Bleu nuit", Color(0xFF3D8BFF), Color(0xFF8DBBFF), Color(0xFFCFE2FF), Color(0xFF1B55C4), Color(0xFF0B1A33)),
    TURQUOISE("Turquoise", Color(0xFF1FC8C8), Color(0xFF7FE3E3), Color(0xFFCDF5F5), Color(0xFF0E8585), Color(0xFF082626)),
    EMERAUDE("Émeraude", Color(0xFF2ECC8F), Color(0xFF7FE3BD), Color(0xFFCDF5E4), Color(0xFF12805A), Color(0xFF0A261C)),
    AMBRE("Ambre", Color(0xFFFFA928), Color(0xFFFFCB7A), Color(0xFFFFE9C2), Color(0xFFB87100), Color(0xFF2B1D07)),
    ROSE("Rose", Color(0xFFFF4F9A), Color(0xFFFF93C0), Color(0xFFFFD3E5), Color(0xFFB81E63), Color(0xFF2B0C1B));

    fun next(): AccentTheme = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = ROUGE
        fun parse(name: String?): AccentTheme = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

// ──────────────────────────────────────────────────────────── ARRONDIS

/** Volontairement bas : des angles francs tiennent la géométrie penchée du logo. */
object Radius {
    val tile = 3.dp       // vignettes et cartes
    val control = 5.dp    // boutons et pastilles
    val panel = 7.dp      // panneaux et boîtes de dialogue
}

// ─────────────────────────────────────────────── HALO DE SÉLECTION

/**
 * Le halo remplace le contour autour de l'élément sélectionné.
 * C'est un calque flouté, un peu plus grand que la vignette.
 */
object Halo {
    /** Opacité de la lueur au plus fort, de 0 à 1. */
    const val intensity = 0.36f

    /** Largeur de la lueur, en part de la largeur de l'élément (1 = aussi large que lui). */
    const val width = 1.0f

    /** Jusqu'où la lueur descend sous l'élément. */
    val reach = 20.dp

    /** Sur un petit élément (bouton), la lueur ne descend pas plus bas que cette part de sa hauteur. */
    const val reachMaxFraction = 0.50f

    /** Décalage du cœur de la lueur sous le bord bas (négatif = remonte derrière l'élément). */
    val drop = (-5).dp

    // Anciennes valeurs, gardées pour les écrans qui les lisent encore
    val blur = 8.dp
    const val spread = 1.016f
    const val spreadExtraY = 0.010f
}

// ──────────────────────────────────────────────────────── MOUVEMENT

object Motion {
    /** Apparition et disparition du halo. */
    const val haloMs = 220

    /** Atténuation des éléments non sélectionnés. */
    const val dimMs = 200

    /** Fondu entre deux visuels de bannière. */
    const val heroCrossfadeMs = 400

    /** Pages disque : sur un dossier, la bannière change de film à ce rythme. */
    const val folderSpotlightMs = 6_000L

    /**
     * Bannière d'accueil : raccords « cinéma » entre deux images (zoom avant,
     * zoom arrière, volet, glissé, fondu au noir, rideau). false = simple fondu.
     */
    const val heroCuts = true
    const val heroCutMs = 950
    /** Le texte s'efface, puis le nouveau monte à sa place. */
    const val heroTextOutMs = 220
    const val heroTextInMs = 480

    /** Fondu du texte de bannière. */
    const val heroTextMs = 280

    /** Délai avant que la bannière suive le focus (évite une image par touche). */
    const val highlightDelayMs = 140L

    /** Durée d'un aller-retour du lent zoom sur la bannière. */
    const val kenBurnsMs = 18_000

    /** Amplitude du zoom : départ et arrivée. */
    const val kenBurnsFrom = 1.04f
    const val kenBurnsTo = 1.14f

    /** Survol d'un picto sans fond : grossissement et passage à la couleur d'accent. */
    const val iconHover = 1.18f
    const val iconHoverMs = 180

    // ── Transitions et clics, pour toute l'application

    /** Changement de page : durée d'arrivée, durée de départ, amplitude. */
    const val pageInMs = 420

    /** Accueil : la couleur du film « remplit » le bouton Lire et le N du menu. */
    const val fillMs = 620
    const val fillDelayMs = 160
    const val pageOutMs = 300
    const val pageRise = 22          // la page monte de 1/22e de l'écran en arrivant
    const val pageShrink = 0.96f     // la page quittée recule légèrement
    /**
     * Entrer dans un disque, un dossier ou une catégorie : on « plonge » dedans.
     * La page qui arrive grandit depuis [pageDeep], celle qu'on quitte grossit jusqu'à
     * [pageNear] en s'effaçant. Retour : le mouvement inverse.
     */
    const val pageDeep = 0.90f       // plus bas = plongée plus marquée
    const val pageNear = 1.08f
    const val pageInDelayMs = 70     // la page qui arrive attend que l'autre ait commencé à partir

    /** Entrée et sortie du lecteur vidéo : un fondu plus posé. */
    const val playerFadeMs = 420

    /** Clic : la carte s'enfonce, puis rebondit au relâchement. */
    const val pressScale = 0.93f     // 1 = pas d'enfoncement
    const val pressInMs = 90
    const val pressBounce = 0.42f    // plus bas = rebond plus marqué
    const val pressStiffness = 520f

    /** Apparition des rangées, panneaux et lignes de menu. Mettre false pour tout couper. */
    const val entrances = true
    const val enterMs = 340
    val rowRise = 18.dp              // les rangées montent de cette distance
    val panelSlide = 46.dp           // les panneaux glissent depuis la droite
    val menuSlide = 16.dp            // les lignes de menu aussi, l'une après l'autre
    const val menuStaggerMs = 38
}

// ─────────────────────────────────────────────────── VIGNETTES & GRILLE

object Tiles {
    /** Pastille de type en haut à gauche des cartes : rond blanc, picto plein noir. */
    val badge = 18.dp
    val badgeGlyph = 10.dp

    /** Agrandissement au focus, par type de carte. */
    const val fileScale = 1.06f
    const val folderScale = 1.05f
    const val driveScale = 1.04f

    /** Opacité des éléments non sélectionnés : plus bas = contraste plus marqué. */
    const val restingDim = 0.64f
    const val restingDimDrive = 0.70f

    /** Format d'affiche : hauteur = largeur × ce facteur. */
    const val posterAspect = 1.5f

    /** Format des cartes disque. */
    const val driveAspect = 1.42f
    val driveWidth = 196.dp

    /** Espace entre une vignette et son titre. */
    val titleGap = 6.dp

    /** Toutes petites cartouches posées sur les vignettes : définition (« 1080p ») et « VU ». */
    val tagText = 6.5.sp
    val tagRadius = 3.dp
    val tagInset = 6.dp                // distance au bord de la vignette
    const val tagBackdrop = 0.68f      // opacité du fond noir de la cartouche de définition
}

object Grid {
    /** Marge latérale de tous les écrans. */
    val side = 44.dp

    /** Largeur des vignettes selon la densité choisie par l'utilisateur. */
    val widthLarge = 178.dp
    val widthMedium = 136.dp
    val widthCompact = 106.dp

    /** Écart entre vignettes, par densité. */
    val gapLarge = 18.dp
    val gapMedium = 15.dp
    val gapCompact = 12.dp

    /** Grille des dossiers : nombre de colonnes proposé, du plus grand au plus dense. */
    const val columnsMin = 4
    const val columnsMax = 9
    const val columnsDefault = 6

    /** Au-delà de ce nombre de fichiers, le bandeau alphabétique apparaît. */
    const val alphabetThreshold = 60
}

// ──────────────────────────────────────────────────────────── BANNIÈRE

object Hero {
    /** Part de la hauteur d'écran occupée par la bannière. */
    const val heightFraction = 0.54f
    val minHeight = 260.dp
    val maxHeight = 420.dp

    /** Cadrage de la photo de fond : -1 = haut de l'image, 0 = centre. Les visages sont en haut. */
    const val artBias = -0.7f

    /** Largeur de la colonne de texte, en part de la largeur d'écran. */
    const val textWidthFraction = 0.50f

    /** Le texte : écart entre les étages, taille du titre écrit, lignes de résumé. */
    val gap = 12.dp                    // air entre provenance, titre, cartouches et résumé
    const val titleScale = 0.70f       // le titre écrit, par rapport aux titres d'écran
    const val synopsisLines = 3
    const val synopsisLine = 1.45f     // interligne du résumé
    const val textAlpha = 0.66f        // le résumé : blanc légèrement transparent

    /** Filet qui sépare la bannière de la grille. */
    val ruleHeight = 0.5.dp
    const val ruleAlpha = 0.10f

    /**
     * Dégradé horizontal qui assombrit la gauche pour le texte.
     * Chaque paire : position de 0 (gauche) à 1 (droite), puis opacité du noir.
     */
    val scrimLeft = listOf(
        0.00f to 0.96f,
        0.28f to 0.84f,
        0.58f to 0.34f,
        1.00f to 0.04f
    )

    /** Dégradé vertical, qui fond la bannière dans la grille du dessous. */
    val scrimVertical = listOf(
        0.00f to 0.46f,
        0.24f to 0.08f,
        0.36f to 0.04f,
        0.48f to 0.12f,     // le noir monte dès le milieu…
        0.60f to 0.30f,
        0.71f to 0.52f,
        0.82f to 0.74f,
        0.92f to 0.92f,     // …et s'installe très progressivement
        1.00f to 1.00f
    )
}

// ────────────────────────────────────────────────────── PAGE D'ACCUEIL

/**
 * L'accueil : rail d'icônes à gauche, barre de navigation en haut,
 * grande bannière, puis les rangées de cartes.
 */
object HomePage {

    /**
     * Menu de gauche. Au repos : une colonne de pictos fins posés sur le noir.
     * Au focus : un panneau flottant qui se déroule, avec les noms.
     */
    val railWidth = 58.dp          // retrait des rangées, pour ne pas passer sous le menu
    val railOpen = 204.dp          // largeur du panneau déroulé
    val railIcon = 28.dp           // zone d'un picto
    val railGlyph = 13.dp          // taille du picto lui-même
    const val railRestAlpha = 0.42f    // opacité des pictos non sélectionnés (le sélectionné est blanc plein)
    val railGap = 3.dp             // écart entre pictos, au repos
    val railGapOpen = 13.dp        // écart une fois déroulé : plus d'air
    val railLabel = 9.5.sp         // taille des noms
    const val railLabelSpacing = 0.5f
    val railInset = 29.dp          // même marge à gauche du menu qu'entre le menu et les infos du film
    val railPad = 16.dp            // marge intérieure haute et basse, menu replié
    val railPadOpen = 22.dp        // la même, menu déroulé
    val railPadSide = 8.dp         // marge intérieure gauche et droite
    val railBadge = 20.dp          // le N, posé au-dessus de la boîte
    val railBadgeGap = 22.dp
    val railTop = 28.dp            // le menu est calé en haut, à cette distance du bord
    val railRadius = 12.dp
    /** Le panneau montre ce qu'il y a derrière lui, flouté puis assombri. */
    val railBlur = 22.dp               // force du flou
    const val railDim = 0.36f          // assombrissement par-dessus le flou
    const val railBlurDownscale = 10f  // le fond est d'abord réduit de ce facteur : c'est ce qui le floute sur toutes les TV
    const val railDimNoBlur = 0.90f    // si la TV ne sait pas flouter : simple voile sombre
    const val railBorder = 0.07f       // opacité du liseré blanc
    val railBorderWidth = 0.5.dp
    val railOpenMs = 300

    /** Hauteur de la barre de navigation, posée par-dessus le visuel. */
    val navHeight = 50.dp

    /** La bannière occupe cette part de la hauteur de l'écran. */
    const val heroScreenFraction = 0.80f

    /** Largeur de la colonne de texte de la bannière, en part de la largeur. */
    const val heroTextFraction = 0.40f

    /** La bannière change de film toute seule, au hasard, à ce rythme. */
    const val heroRotateMs = 12_000L

    /** Après un survol de carte, la rotation attend ce délai avant de reprendre. */
    const val heroPauseMs = 20_000L

    /** Titre de la bannière : taille maximale, réduite d'elle-même si le titre est long. */
    val heroTitle = 30.sp
    const val heroTitleUppercase = true

    /** Marge intérieure gauche du contenu (après le rail). */
    val side = 44.dp

    /** Cartes paysage des rangées : largeur, le reste suit en 16/9. */
    val wideCard = 170.dp
    const val wideAspect = 0.5625f
    val wideGap = 13.dp
    /** Jaquettes verticales des rubriques « À voir » et « Sorties récentes ». */
    val posterCard = 112.dp
    /** Défilement automatique de « À voir » et « Sorties récentes » quand on n'est pas dessus. */
    val driftSpeed = 9.dp              // distance parcourue par seconde : plus haut = plus rapide
    const val driftPauseMs = 2_500L    // pause avant de partir, et à chaque bout de la rangée

    /** Cartes disque, plus compactes qu'ailleurs pour tenir dans la page. */
    val driveCard = 134.dp

    /** Espacements verticaux entre les rangées. */
    val rowTitleGap = 11.dp
    val sectionGap = 22.dp

    /** Dégradé qui assombrit la gauche du visuel de bannière. */
    val scrimLeft = listOf(
        0.00f to 0.80f,     // moins opaque au bord : l'image se devine derrière le menu
        0.30f to 0.76f,
        0.60f to 0.30f,
        1.00f to 0.02f
    )

    /** Dégradé vertical : fond la bannière dans la première rangée. */
    val scrimVertical = listOf(
        0.00f to 0.10f,
        0.22f to 0.00f,
        0.44f to 0.06f,     // le noir commence à monter dès le milieu de l'image…
        0.58f to 0.22f,
        0.70f to 0.44f,
        0.81f to 0.68f,
        0.91f to 0.88f,     // …et s'installe très progressivement
        1.00f to 1.00f
    )
}

// ───────────────────────────────────────────────── FICHE D'UN TITRE

/** La fenêtre qui s'ouvre avant la lecture : résumé, cartouches, actions. */
object Sheet {
    /** Taille de la fenêtre, centrée à l'écran. */
    val width = 620.dp
    val maxHeight = 480.dp
    val radius = 14.dp
    /** La jaquette, à gauche, entière ; le reste s'organise à sa droite. */
    val posterWidth = 128.dp
    const val synopsisLines = 5

    /** Ouverture en zoom avec léger rebond, fermeture en dézoom. */
    const val zoomFrom = 0.72f
    const val zoomBounce = 0.74f       // plus bas = rebond plus marqué
    const val zoomStiffness = 320f
    const val zoomOutMs = 200

    /** Derrière : la page très floutée, puis assombrie. */
    const val blurWidth = 72           // largeur de la vignette de travail : plus petit = plus flou
    const val blurRadius = 3           // rayon de lissage, en pixels de cette vignette
    const val blurPasses = 3           // nombre de lissages
    val blur = 18.dp                   // flou supplémentaire, sur les TV qui le permettent
    const val pageDim = 0.34f
    /** Opacité du noir de la fenêtre elle-même. */
    const val panelAlpha = 0.62f
    /** Liseré des cartouches. */
    const val tagBorder = 0.26f
    /** Grossissement de l'action sélectionnée (elle se remplit aussi de couleur). */
    const val rowFocusScale = 1.045f
}

// ──────────────────────────────────────── INFORMATIONS DE LA BANNIÈRE

/** Le bloc de texte de la bannière d'accueil : fin, aéré, en transparences. */
object HeroInfo {
    /** Espaces entre les étages : surtitre, titre, pastilles, résumé, distribution, boutons. */
    val gapSmall = 12.dp
    /** Repère de défilement de la bannière (les petits traits en bas à droite). */
    val stepWidth = 7.dp                  // un trait au repos
    val stepActive = 26.dp                // le trait du titre affiché
    val stepHeight = 3.dp
    val stepGap = 4.dp
    val stepBottom = 16.dp                // distance au bas de la bannière
    const val stepRestAlpha = 0.22f       // opacité des traits au repos
    /** Reflet qui traverse le bouton « Lire ». */
    const val glintEveryMs = 4200         // un passage toutes les…
    const val glintShare = 0.24f          // part du cycle occupée par le passage : plus bas = reflet plus vif
    const val glintAlpha = 0.34f          // éclat du reflet
    const val writtenTitleAlpha = 0.84f   // opacité du titre français écrit sous le logo
    val writtenTitleSize = 12.sp          // sa taille (police de l'application, comme les titres de pages)
    const val synopsisScale = 0.9f        // taille du résumé dans les bannières : 1 = taille normale du texte courant
    val gap = 16.dp
    val gapButtons = 22.dp

    /** Surtitre : petit filet rouge, puis le texte très espacé. */
    val ruleWidth = 22.dp
    const val overlineSpacing = 2.4f
    const val overlineAlpha = 0.78f

    /** Pastilles de verre : fond et liseré blancs, très transparents. */
    const val glassFill = 0.09f
    const val glassBorder = 0.20f
    const val chipSpacing = 1.1f

    /** Résumé et distribution : blancs adoucis. */
    const val synopsisAlpha = 0.70f
    const val synopsisLine = 1.5f      // interligne, en multiples de la taille du texte
    const val synopsisLines = 3
    const val creditLabelAlpha = 0.42f

    /** Boutons : verre au repos, blanc plein une fois sélectionnés. */
    const val buttonFill = 0.11f
    const val buttonFillSecondary = 0.035f   // « Plus d'infos » : presque seulement son liseré
    const val buttonBorder = 0.24f
    /** Bouton « Lire » : saturation minimale et clarté maximale de sa couleur. */
    const val playSaturation = 0.66f
    const val playBrightness = 0.90f
    /** Sélectionné, « Lire » projette une ombre de sa couleur : intensité et étendue. */
    const val playShadow = 0.42f
    val playShadowSize = 10.dp
}

// ─────────────────────────────────────────────────── TITRES EN IMAGE

/**
 * Quand un film ou une série a un logo officiel, il remplace le titre écrit
 * sur les bannières, les fiches et le lecteur.
 */
object TitleArt {
    /** false = toujours le titre écrit. */
    const val enabled = true

    /** En dessous de cette clarté (0 à 1), le logo est passé en blanc pour rester lisible. */
    const val minLuminance = 0.30

    /** Part minimale de pixels colorés pour qu'un logo donne sa couleur au bouton « Lire ». */
    const val accentMinShare = 0.14f

    /** Cadre maximal du logo, par endroit : largeur × hauteur. */
    val heroWidth = 300.dp
    val heroHeight = 104.dp
    val browseWidth = 170.dp
    val browseHeight = 42.dp
    val sheetWidth = 220.dp
    val sheetHeight = 54.dp
    val playerWidth = 290.dp
    val playerHeight = 88.dp
    val playerSeriesWidth = 200.dp     // logo de la série, au-dessus de « Épisode 11 »
    val playerSeriesHeight = 54.dp
    val saverWidth = 330.dp            // économiseur d'écran
    val saverHeight = 112.dp
}

// ───────────────────────────────────────── COULEUR DES TITRES DE BANNIÈRE

/**
 * Les titres de bannière se teintent d'après leur visuel : un dégradé entre
 * les deux couleurs vives dominantes de l'image.
 */
object TitleTint {
    /** false = tous les titres restent blancs. */
    const val enabled = true

    /** Clarté minimale, de 0 à 1 : plus haut = titres plus clairs, plus lisibles, moins colorés. */
    const val minLuminance = 0.52f

    /** Intensité des couleurs : plancher et plafond de saturation. */
    const val minSaturation = 0.30f
    const val maxSaturation = 0.78f

    /** Fondu d'une couleur à l'autre quand le visuel change. */
    const val fadeMs = 600
}

// ─────────────────────────────────────────────── FOND DES CARTES

/**
 * Cartes de dossier « films » : une seule jaquette tient le fond, puis
 * glisse vers le haut pour laisser monter la suivante.
 */
object FolderArt {
    /** Temps d'affichage d'une jaquette avant qu'elle ne cède la place. */
    const val holdMs = 6500L

    /** Durée du glissement d'une jaquette à l'autre : lent, avec départ et arrivée en douceur. */
    const val slideMs = 1700

    /** Taille maximale du nom de dossier ; réduite d'elle-même s'il est long. */
    val title = 10.5.sp

    /** Opacité du voile noir, du haut vers le bas de la carte. */
    val scrim = listOf(
        0.00f to 0.46f,
        0.30f to 0.10f,
        0.55f to 0.62f,
        0.78f to 0.93f,
        1.00f to 1.00f
    )

    /** Dossiers de séries (affiche seule) : hauteur et force du voile du bas. */
    const val posterScrimHeight = 0.46f
    const val posterScrim = 0.96f
}

/** Cartes disque : taille du nom, et voile noir plus appuyé que sur les dossiers. */
object DriveArt {
    /** Opacité du voile noir posé sur la carte d'un disque débranché (0 = aucun, 1 = carte noire). */
    const val offlineVeil = 0.58f

    val title = 11.sp

    /** Hauteur réservée à la jauge d'espace libre, au-dessus de la carte. */
    val gaugeHeight = 22.dp

    /** Sous cette part d'espace libre, la jauge passe en couleur d'alerte. */
    const val lowSpace = 0.10f

    /** Opacité du voile, du haut vers le bas. */
    val scrim = listOf(
        0.00f to 0.66f,
        0.30f to 0.30f,
        0.58f to 0.74f,
        1.00f to 0.98f
    )
}

object PosterWall {
    /** Nombre de colonnes du mur de jaquettes animé. */
    const val columns = 3

    /** Jaquettes par colonne. */
    const val perColumn = 4

    val itemHeight = 118.dp
    val spacing = 7.dp

    /** Inclinaison du mur, en degrés. */
    const val tiltDegrees = -12f

    /** Durée d'un cycle de défilement. */
    const val driftMs = 24_000

    /** Grossissement, pour que l'inclinaison ne laisse pas de coins vides. */
    const val zoom = 1.7f

    /** Assombrissement : haut, milieu, bas. */
    val scrim = listOf(0.52f, 0.76f, 0.94f)
}

// ──────────────────────────────────────────────── ÉCRAN D'OUVERTURE

/** Calé sur le jingle : accents à 0 s, 1,5 s et 3,25 s, fin du son vers 4,6 s. */
object Splash {

    /**
     * Le fond de l'ouverture, calé sur le jingle : deux anneaux de lumière qui tournent en sens
     * inverse autour du chat et du logo, et battent sur les temps forts. Ils sont peints une seule
     * fois, puis seulement tournés : rien ne ralentit l'animation. [party] à false les retire.
     */
    const val party = true
    const val beatDecay = 0.16f          // durée d'un battement, en secondes : plus haut = plus mou
    const val glowKick = 0.26f           // gonflement des deux grandes lueurs de fond sur un temps fort
    const val glowRest = 0.50f           // leur éclat entre deux temps (1 = toujours à fond)
    val ringInner = 760.dp               // diamètre de l'anneau intérieur (l'écran fait 540 de haut)
    val ringOuter = 1180.dp              // diamètre de l'anneau extérieur
    const val ringHole = 0.34f           // part du rayon laissée noire au centre : la place du chat et du logo
    const val ringAlpha = 0.42f          // éclat des traînées de lumière
    const val ringRest = 0.55f           // éclat entre deux temps (1 = toujours à fond)
    const val ringKick = 0.07f           // gonflement sur un temps fort
    const val ringSpeed = 34f            // rotation, en degrés par seconde

    val logoHeight = 62.dp

    /** Le chat animé, au-dessus du logo : sa taille et l'écart avec le logo. */
    val catSize = 104.dp
    val catGap = 16.dp
    const val catFadeMs = 420

    /** Volume du jingle, de 0 à 1. */
    const val jingleVolume = 0.9f

    /**
     * Entrée du logo, lettre par lettre : chacune monte d'en dessous, écartée
     * des autres, et vient se caler avec un léger rebond.
     */
    const val lettersSplit = true        // false = le logo arrive d'un bloc
    const val letterStaggerMs = 80L      // délai entre deux lettres
    const val letterDrop = 0.95f         // hauteur de départ, en hauteurs de logo
    const val letterSpread = 0.045f      // écartement de départ, en largeurs de logo par lettre
    const val letterScaleFrom = 0.55f
    const val letterBounce = 0.52f       // plus bas = rebond plus marqué
    const val letterStiffness = 210f

    /** Le chat arrive avec le logo : il monte et grossit en rebondissant. */
    const val catScaleFrom = 0.6f
    val catRise = 26.dp

    /** Fond : deux lueurs très sombres qui dérivent lentement. */
    const val glowRed = 0.11f            // intensité, de 0 (noir pur) à 1
    const val glowViolet = 0.15f
    val glowSize = 620.dp
    const val glowDriftMs = 5200         // durée d'un aller
    const val glowTravel = 0.34f         // amplitude du déplacement, en tailles de lueur
    const val glowGrow = 0.30f           // grossissement en cours de route

    /** Pulsation sur le dernier accent du jingle. */
    const val pulseAtMs = 3200L
    const val pulseMs = 520
    const val pulseScale = 1.05f

    /**
     * Avant le jingle, une barre de chargement : le son, la bibliothèque et
     * l'accueil se préparent pendant ce temps, pour que l'animation parte
     * sur une machine au repos. settleMs = temps laissé à l'accueil pour
     * finir de s'afficher et charger ses images.
     */
    const val settleMs = 2200
    val loaderWidth = 170.dp
    const val loaderFadeMs = 300

    /** Temps total avant le fondu vers l'application. */
    const val holdMs = 4350L
    const val fadeOutMs = 450
}

// ───────────────────────────────────────────────────── TAILLES DE TEXTE

object Type {
    val displayLarge = 38.sp      // titres d'écran
    val displayMedium = 25.sp
    val headlineLarge = 19.sp     // titre du lecteur
    val headlineSmall = 14.sp     // titres de section, nom de disque
    val titleMedium = 12.sp
    val bodyLarge = 13.sp         // synopsis
    val bodyMedium = 11.5.sp
    val labelLarge = 10.5.sp      // titres de vignette, boutons
    val labelSmall = 9.sp         // mentions, pastilles

    /** Interlignage, en multiple de la taille : titres serrés, mentions à peine aérées. */
    const val tightLine = 1.10f
    const val labelLine = 1.22f
}

// ─────────────────────────────────────────── COULEURS PAR THÈME DE DISQUE

/** Habillage d'un disque : couleur dominante, couleur de fond, teinte du halo. */
data class ThemeSkin(
    val primary: Color,
    val secondary: Color,
    val glow: Color
) {
    val gradient: Brush get() = Brush.linearGradient(listOf(primary, secondary))
}

/**
 * Chaque disque a son dégradé : primary → secondary.
 * Films rouge orangé, séries violet-bleu, animation vert-jaune.
 */
fun skinFor(theme: DriveTheme): ThemeSkin = when (theme) {
    DriveTheme.FILMS -> ThemeSkin(Color(0xFFFF3D1F), Color(0xFFFF9A3D), Color(0x55FF3D1F))
    DriveTheme.SERIES -> ThemeSkin(Color(0xFF9A5BFF), Color(0xFF4C8DFF), Color(0x559A5BFF))
    DriveTheme.ANIMATION -> ThemeSkin(Color(0xFF3DDC6E), Color(0xFFF4E04B), Color(0x553DDC6E))
    DriveTheme.DOCS -> ThemeSkin(Color(0xFF4CC9F0), Color(0xFF5B6BFF), Color(0x554CC9F0))
    DriveTheme.CONCERTS -> ThemeSkin(Color(0xFFE05BD6), Color(0xFFFF6B9A), Color(0x55E05BD6))
    DriveTheme.JEUNESSE -> ThemeSkin(Color(0xFFFFC24B), Color(0xFFFF8A5B), Color(0x55FFC24B))
    DriveTheme.AUTRE -> ThemeSkin(Color(0xFFE8E8EC), Color(0xFF9A9AA2), Color(0x55E8E8EC))
}
