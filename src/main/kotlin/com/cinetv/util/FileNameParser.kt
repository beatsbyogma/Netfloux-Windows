package com.cinetv.util

/**
 * Nettoie un nom de fichier pour en tirer un titre interrogeable sur TMDB.
 *
 * "Le.Seigneur.des.Anneaux.2001.MULTI.1080p.BluRay.x264-GROUPE.mkv"
 *   -> SearchTerms(title = "Le Seigneur des Anneaux", year = 2001)
 */
object FileNameParser {

    data class Parsed(
        val title: String,
        val year: Int?,
        val season: Int?,
        val episode: Int?
    ) {
        val isEpisode: Boolean get() = season != null && episode != null
    }

    private val SxxExx = Regex("""(?i)[\s._\-\[]S(\d{1,2})[\s._\-]?E(\d{1,3})""")
    private val NxNN = Regex("""(?i)[\s._\-\[](\d{1,2})x(\d{2,3})""")
    private val YEAR = Regex("""[\s._\-\[(](19\d{2}|20\d{2})[\s._\-\])]""")

    /** Tags techniques à retirer du titre. */
    private val JUNK = Regex(
        """(?i)\b(
            2160p|1080p|1080i|720p|480p|4k|uhd|hdr10\+?|hdr|dolby\.?vision|dv|sdr|
            bluray|blu-ray|brrip|bdrip|bdremux|remux|dvdrip|dvd5|dvd9|webrip|web-dl|webdl|web|
            hdtv|hdlight|hdrip|cam|ts|tc|r5|
            x264|x265|h264|h265|h\.264|h\.265|hevc|avc|xvid|divx|av1|
            aac|aac2\.0|ac3|eac3|dts(-hd)?|truehd|atmos|dd5\.1|dd\+|ddp5\.1|flac|mp3|
            multi|vostfr|vost|vf|vff|vfq|vfi|truefrench|french|english|subfrench|
            10bit|8bit|hi10p|
            extended|unrated|uncut|remastered|proper|repack|limited|integrale|integral|
            directors?\.?cut|theatrical|imax|
            complete|saison|season
        )\b""".trimIndent().replace("\n", "").replace(" ", "")
    )

    private val GROUP_TAIL = Regex("""[-–][A-Za-z0-9_]{2,20}$""")
    private val BRACKETS = Regex("""[\[(][^\])]*[\])]""")

    fun parse(fileName: String): Parsed {
        val base = fileName.substringBeforeLast('.')

        // Épisode ?
        val ep = SxxExx.find(base) ?: NxNN.find(base)
        val season = ep?.groupValues?.getOrNull(1)?.toIntOrNull()
        val episode = ep?.groupValues?.getOrNull(2)?.toIntOrNull()

        // Partie utile : ce qui précède le marqueur d'épisode, sinon tout
        var work = if (ep != null) base.substring(0, ep.range.first) else base

        // Année
        val yearMatch = YEAR.find(work)
        val year = yearMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (yearMatch != null) work = work.substring(0, yearMatch.range.first)

        val title = work
            .let { BRACKETS.replace(it, " ") }
            .replace('.', ' ')
            .replace('_', ' ')
            .let { JUNK.replace(it, " ") }
            .let { GROUP_TAIL.replace(it.trim(), "") }
            .replace(Regex("""\s{2,}"""), " ")
            .trim(' ', '-', '–', '_', '.')

        return Parsed(
            title = title.ifBlank { base },
            year = year,
            season = season,
            episode = episode
        )
    }

    /** Titre lisible immédiat, avant toute requête réseau. */
    fun prettyTitle(fileName: String): String {
        val p = parse(fileName)
        return if (p.isEpisode) {
            val s = p.season!!.toString().padStart(2, '0')
            val e = p.episode!!.toString().padStart(2, '0')
            if (p.title.isBlank()) "S${s}E$e" else "${p.title} · S${s}E$e"
        } else p.title
    }
}
