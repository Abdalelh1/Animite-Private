package com.imashnake.animite.media.source.classification

import android.util.Log
import com.imashnake.animite.media.source.EpisodeType
import com.imashnake.animite.media.source.HttpHelper
import com.imashnake.animite.media.source.SkipInterval
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

/**
 * Pluggable episode classification source interface.
 * Allows adding verified anime canon/filler datasets dynamically.
 */
interface EpisodeClassificationSource {
    fun appliesTo(animeTitles: List<String>): Boolean
    fun classifyEpisode(
        animeTitles: List<String>,
        episodeNumber: Float,
        rawTitle: String?,
        url: String?,
    ): EpisodeType?
}

/**
 * Verified classification for One Piece (MAL ID 21).
 * Strictly maps canonical manga episodes, filler arcs, mixed canon/filler,
 * TV specials, crossover specials, and recaps.
 */
object OnePieceClassificationSource : EpisodeClassificationSource {

    private val titlesMatchList = listOf("one piece", "ون بيس", "ワンピース")

    override fun appliesTo(animeTitles: List<String>): Boolean {
        return animeTitles.any { title ->
            val lower = title.lowercase(Locale.ROOT).trim()
            titlesMatchList.any { lower.contains(it) }
        }
    }

    override fun classifyEpisode(
        animeTitles: List<String>,
        episodeNumber: Float,
        rawTitle: String?,
        url: String?,
    ): EpisodeType {
        val numInt = episodeNumber.toInt()
        val isFraction = kotlin.math.abs(episodeNumber - numInt) > 0.05f

        // Recaps that have decimal numbering (e.g., 1035.5, 1073.5, 1088.5)
        if (isFraction) {
            return EpisodeType.RECAP
        }

        return when (numInt) {
            // Warship Island Arc (Filler)
            in 54..61 -> EpisodeType.FILLER

            // Arabasta filler / mixed
            98, 99 -> EpisodeType.FILLER
            101, 102 -> EpisodeType.MIXED

            // Post-Arabasta Arc (Filler)
            in 131..135 -> EpisodeType.FILLER

            // Goat Island Arc (Filler)
            in 136..138 -> EpisodeType.FILLER

            // Ruluka Island Arc (Filler)
            in 139..143 -> EpisodeType.FILLER

            // G-8 Arc - Navarone (Filler)
            in 196..206 -> EpisodeType.FILLER

            // Ocean's Dream Arc (Filler)
            in 220..224 -> EpisodeType.FILLER

            // Foxy's Return Arc (Filler)
            225, 226 -> EpisodeType.FILLER

            // Enies Lobby Retrospective Recaps
            in 279..283 -> EpisodeType.RECAP

            // Historical Boss Luffy TV Specials
            291, 292, 303 -> EpisodeType.SPECIAL

            // Post-Enies Lobby filler
            in 317..319 -> EpisodeType.FILLER

            // Lovely Land Arc (Filler)
            in 326..335 -> EpisodeType.FILLER

            // Chopperman Special
            336 -> EpisodeType.SPECIAL

            // Spa Island Arc (Filler)
            in 382..384 -> EpisodeType.FILLER

            // Boss Luffy Historical Special
            406, 407 -> EpisodeType.SPECIAL

            // Little East Blue (Film: Strong World Tie-in Filler)
            in 426..429 -> EpisodeType.FILLER

            // Marineford War Recap
            457, 458 -> EpisodeType.RECAP

            // Toriko Crossover Special 1
            492 -> EpisodeType.SPECIAL

            // Post-War Arc filler / mixed
            in 497..499 -> EpisodeType.FILLER
            506 -> EpisodeType.MIXED

            // Toriko Crossover Special 2
            542 -> EpisodeType.SPECIAL

            // Z's Ambition Arc (Film: Z Tie-in Filler)
            in 575..578 -> EpisodeType.FILLER

            // Toriko & Dragon Ball Z Crossover Special
            590 -> EpisodeType.SPECIAL

            // Caesar Retrieval Arc (Filler)
            in 626..628 -> EpisodeType.FILLER

            // Silver Mine Arc (Film: Gold Tie-in Filler)
            in 747..750 -> EpisodeType.FILLER

            // Marine Rookie Arc (Filler)
            in 780..782 -> EpisodeType.FILLER

            // Cidre Guild Arc (Film: Stampede Tie-in Filler)
            895, 896 -> EpisodeType.FILLER

            // Romance Dawn 20th Anniversary Special
            907 -> EpisodeType.SPECIAL

            // Uta's Past (Film: Red Tie-in Filler)
            1029, 1030 -> EpisodeType.FILLER

            // Default canon
            else -> EpisodeType.CANON
        }
    }
}

/**
 * Verified classification for Naruto (Original 220 episodes).
 */
object NarutoClassificationSource : EpisodeClassificationSource {
    private val titlesMatchList = listOf("naruto", "ناروتو")

    override fun appliesTo(animeTitles: List<String>): Boolean {
        return animeTitles.any { raw ->
            val lower = raw.lowercase(Locale.ROOT).trim()
            titlesMatchList.any { lower.contains(it) } &&
                !lower.contains("shippu") && !lower.contains("شيبودن") &&
                !lower.contains("boruto") && !lower.contains("بوروتو")
        }
    }

    override fun classifyEpisode(
        animeTitles: List<String>,
        episodeNumber: Float,
        rawTitle: String?,
        url: String?,
    ): EpisodeType {
        val numInt = episodeNumber.toInt()
        return when (numInt) {
            26 -> EpisodeType.RECAP
            97 -> EpisodeType.FILLER
            in 101..106 -> EpisodeType.FILLER
            in 136..219 -> EpisodeType.FILLER
            220 -> EpisodeType.MIXED
            else -> EpisodeType.CANON
        }
    }
}

/**
 * Verified classification for Naruto Shippuden (500 episodes).
 */
object NarutoShippudenClassificationSource : EpisodeClassificationSource {
    private val titlesMatchList = listOf("shippuden", "shippuuden", "شيبودن", "疾風伝")

    override fun appliesTo(animeTitles: List<String>): Boolean {
        return animeTitles.any { raw ->
            val lower = raw.lowercase(Locale.ROOT).trim()
            titlesMatchList.any { lower.contains(it) }
        }
    }

    override fun classifyEpisode(
        animeTitles: List<String>,
        episodeNumber: Float,
        rawTitle: String?,
        url: String?,
    ): EpisodeType {
        val numInt = episodeNumber.toInt()
        return when (numInt) {
            in 57..71 -> EpisodeType.FILLER
            in 90..112 -> EpisodeType.FILLER
            in 144..151 -> EpisodeType.FILLER
            170, 171 -> EpisodeType.FILLER
            in 176..196 -> EpisodeType.FILLER
            in 223..242 -> EpisodeType.FILLER
            in 257..260 -> EpisodeType.RECAP
            271 -> EpisodeType.SPECIAL
            in 279..281 -> EpisodeType.FILLER
            in 284..295 -> EpisodeType.FILLER
            in 303..320 -> EpisodeType.FILLER
            in 347..361 -> EpisodeType.MIXED
            376, 377 -> EpisodeType.SPECIAL
            in 388..390 -> EpisodeType.FILLER
            in 394..413 -> EpisodeType.FILLER
            416, 417 -> EpisodeType.FILLER
            422, 423 -> EpisodeType.FILLER
            in 427..450 -> EpisodeType.FILLER
            in 451..458 -> EpisodeType.MIXED
            in 464..468 -> EpisodeType.MIXED
            in 480..483 -> EpisodeType.FILLER
            else -> EpisodeType.CANON
        }
    }
}

/**
 * Verified classification for Bleach (366 episodes).
 */
object BleachClassificationSource : EpisodeClassificationSource {
    private val titlesMatchList = listOf("bleach", "بليتش")

    override fun appliesTo(animeTitles: List<String>): Boolean {
        return animeTitles.any { raw ->
            val lower = raw.lowercase(Locale.ROOT).trim()
            titlesMatchList.any { lower.contains(it) }
        }
    }

    override fun classifyEpisode(
        animeTitles: List<String>,
        episodeNumber: Float,
        rawTitle: String?,
        url: String?,
    ): EpisodeType {
        val numInt = episodeNumber.toInt()
        return when (numInt) {
            33, 50 -> EpisodeType.FILLER
            in 64..109 -> EpisodeType.FILLER
            in 128..137 -> EpisodeType.FILLER
            in 147..149 -> EpisodeType.FILLER
            in 168..189 -> EpisodeType.FILLER
            204, 205 -> EpisodeType.FILLER
            213, 214 -> EpisodeType.FILLER
            in 227..265 -> EpisodeType.FILLER
            287, 298, 299 -> EpisodeType.FILLER
            in 303..305 -> EpisodeType.FILLER
            in 311..316 -> EpisodeType.FILLER
            in 317..342 -> EpisodeType.FILLER
            355 -> EpisodeType.FILLER
            else -> EpisodeType.CANON
        }
    }
}

/**
 * Keyword-based classifier for general anime when no specific canon/filler database is registered.
 */
object KeywordClassificationSource : EpisodeClassificationSource {
    override fun appliesTo(animeTitles: List<String>): Boolean = true

    override fun classifyEpisode(
        animeTitles: List<String>,
        episodeNumber: Float,
        rawTitle: String?,
        url: String?,
    ): EpisodeType? {
        val title = rawTitle?.lowercase(Locale.ROOT) ?: ""
        val link = url?.lowercase(Locale.ROOT) ?: ""
        val combined = "$title $link"

        return when {
            Regex("""\b(recap|ملخص)\b""").containsMatchIn(title) || link.contains("/recap/") -> EpisodeType.RECAP
            Regex("""\b(ova|أوفا|اوفا)\b""").containsMatchIn(title) || link.contains("/ova/") -> EpisodeType.OVA
            Regex("""\b(ona|أونا|اونا)\b""").containsMatchIn(title) || link.contains("/ona/") -> EpisodeType.ONA
            Regex("""\b(special|حلقة\s*خاصة|خاصة|sp)\b""").containsMatchIn(title) || link.contains("/special/") -> EpisodeType.SPECIAL
            Regex("""\b(movie|film|فيلم)\b""").containsMatchIn(title) || link.contains("/movie/") || link.contains("/film/") -> EpisodeType.MOVIE
            Regex("""\b(filler|فلر)\b""").containsMatchIn(title) -> EpisodeType.FILLER
            else -> null
        }
    }
}

/**
 * Central repository for episode classification and skip times.
 */
object EpisodeClassificationRepository {

    private const val TAG = "ANIMITE_CLASSIFIER"

    private val classificationSources: MutableList<EpisodeClassificationSource> = mutableListOf(
        OnePieceClassificationSource,
        NarutoClassificationSource,
        NarutoShippudenClassificationSource,
        BleachClassificationSource,
    )

    fun registerClassificationSource(source: EpisodeClassificationSource) {
        if (!classificationSources.contains(source)) {
            classificationSources.add(0, source)
        }
    }

    /**
     * Determines the exact classification for an episode:
     * 1. Checks specific verified classification sources (e.g. One Piece).
     * 2. Checks keyword classification for OVA, ONA, MOVIE, SPECIAL, RECAP.
     * 3. Falls back to CANON if recognized regular series, or UNCLASSIFIED when unknown.
     */
    fun classify(
        animeTitles: List<String>,
        episodeNumber: Float,
        rawTitle: String? = null,
        url: String? = null,
    ): EpisodeType {
        // 1. Keyword check for explicit format markers (OVA, Movie, Special take precedence)
        val keywordType = KeywordClassificationSource.classifyEpisode(animeTitles, episodeNumber, rawTitle, url)
        if (keywordType != null && keywordType != EpisodeType.CANON) {
            return keywordType
        }

        // 2. Verified series classification source
        for (source in classificationSources) {
            if (source.appliesTo(animeTitles)) {
                val classified = source.classifyEpisode(animeTitles, episodeNumber, rawTitle, url)
                if (classified != null) {
                    return classified
                }
            }
        }

        // 3. Fallback when no verified classification exists:
        // Do NOT guess filler. Use UNCLASSIFIED or regular CANON.
        return if (keywordType != null) keywordType else EpisodeType.UNCLASSIFIED
    }

    /**
     * Resolves known anime franchise MAL IDs for AniSkip integration.
     */
    fun resolveMalId(animeTitles: List<String>): Int {
        for (raw in animeTitles) {
            val lower = raw.lowercase(Locale.ROOT).trim()
            when {
                lower.contains("one piece") || lower.contains("ون بيس") -> return 21
                lower.contains("shippuden") || lower.contains("شيبودن") -> return 1735
                lower.contains("naruto") || lower.contains("ناروتو") -> return 20
                lower.contains("bleach") || lower.contains("بليتش") -> return 269
                lower.contains("attack on titan") || lower.contains("shingeki") || lower.contains("هجوم العمالقة") -> return 16498
                lower.contains("jujutsu") || lower.contains("جوجوتسو") -> return 40748
                lower.contains("demon slayer") || lower.contains("kimetsu") || lower.contains("قاتل الشياطين") -> return 38000
                lower.contains("hunter x hunter") || lower.contains("هنتر") || lower.contains("القناص") -> return 11061
                lower.contains("death note") || lower.contains("مذكرة الموت") -> return 1535
                lower.contains("fullmetal") || lower.contains("الكيميائي المعدني") -> return 5114
                lower.contains("my hero academia") || lower.contains("boku no hero") || lower.contains("أكاديمية بطلي") -> return 31964
                lower.contains("black clover") || lower.contains("بلاك كلوفر") -> return 34572
            }
        }
        return 0
    }

    /**
     * AniSkip API Client:
     * Queries accurate skip times (opening and ending) for supported anime episodes.
     */
    suspend fun fetchSkipIntervals(
        malId: Int,
        episodeNumber: Float,
        episodeLengthSeconds: Double = 1440.0,
    ): List<SkipInterval> = withContext(Dispatchers.IO) {
        if (malId <= 0 || episodeNumber <= 0f) return@withContext emptyList()

        val epNumInt = if (episodeNumber % 1.0f == 0.0f) episodeNumber.toInt().toString() else episodeNumber.toString()
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$epNumInt?types=op&types=ed&episodeLength=$episodeLengthSeconds"

        try {
            val responseText = HttpHelper.getHtml(url)
            val json = JSONObject(responseText)
            if (!json.optBoolean("found", false)) {
                return@withContext emptyList()
            }

            val resultsArray = json.optJSONArray("results") ?: return@withContext emptyList()
            val intervals = mutableListOf<SkipInterval>()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val skipType = item.optString("skipType", "op")
                val intervalObj = item.optJSONObject("interval")
                if (intervalObj != null) {
                    val startSec = intervalObj.optDouble("startTime", 0.0)
                    val endSec = intervalObj.optDouble("endTime", 0.0)
                    if (endSec > startSec) {
                        intervals.add(
                            SkipInterval(
                                startTimeMs = (startSec * 1000).toLong(),
                                endTimeMs = (endSec * 1000).toLong(),
                                skipType = skipType,
                            )
                        )
                    }
                }
            }
            intervals
        } catch (e: Exception) {
            Log.d(TAG, "AniSkip query failed or unavailable: ${e.message}")
            emptyList()
        }
    }
}
