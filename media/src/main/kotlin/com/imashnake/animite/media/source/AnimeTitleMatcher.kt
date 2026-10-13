package com.imashnake.animite.media.source

import java.util.Locale
import kotlin.math.max

object AnimeTitleMatcher {

    const val MIN_MATCH_THRESHOLD = 60

    /**
     * Scores how well a candidate title from a streaming source matches the anime's known titles.
     * Returns a score between 0 and 100.
     */
    fun matchScore(knownTitles: List<String>, candidateTitle: String): Int {
        if (knownTitles.isEmpty() || candidateTitle.isBlank()) return 0

        val cleanCandidate = normalize(candidateTitle)
        val candidateSeason = extractSeasonNumber(candidateTitle)
        val candidateIsMovie = isMovie(candidateTitle)

        var bestScore = 0

        for (rawTitle in knownTitles) {
            if (rawTitle.isBlank()) continue
            val cleanKnown = normalize(rawTitle)
            val knownSeason = extractSeasonNumber(rawTitle)
            val knownIsMovie = isMovie(rawTitle)

            // Strict Season check: if both specify a season and they differ, reject.
            if (knownSeason != null && candidateSeason != null && knownSeason != candidateSeason) {
                continue
            }

            // If one is clearly a movie and the other is a TV series, heavily penalize or reject
            if (knownIsMovie != candidateIsMovie && (knownIsMovie || candidateIsMovie)) {
                // If known is TV but candidate is Movie -> mismatch
                if (!knownIsMovie && candidateIsMovie) continue
            }

            // 1. Exact normalized match
            if (cleanKnown == cleanCandidate) {
                bestScore = max(bestScore, 100)
                continue
            }

            // 2. Base title similarity (without season markers)
            val baseKnown = stripSeason(cleanKnown)
            val baseCandidate = stripSeason(cleanCandidate)

            if (baseKnown.isNotBlank() && baseCandidate.isNotBlank()) {
                if (baseKnown == baseCandidate) {
                    val seasonBonus = if (knownSeason == candidateSeason) 15 else 0
                    bestScore = max(bestScore, 85 + seasonBonus)
                    continue
                }
            }

            // 3. Token-based similarity (Jaccard on words)
            val knownTokens = baseKnown.split(" ").filter { it.length > 1 }.toSet()
            val candidateTokens = baseCandidate.split(" ").filter { it.length > 1 }.toSet()

            if (knownTokens.isNotEmpty() && candidateTokens.isNotEmpty()) {
                val intersection = knownTokens.intersect(candidateTokens).size
                val union = knownTokens.union(candidateTokens).size
                val jaccard = (intersection.toFloat() / union.toFloat()) * 100f

                // If all tokens of the shorter title are contained in the longer title
                val isSubsetTitle = if (knownTokens.size <= candidateTokens.size) {
                    candidateTokens.containsAll(knownTokens)
                } else {
                    knownTokens.containsAll(candidateTokens)
                }

                var tokenScore = jaccard.toInt()
                if (isSubsetTitle && intersection >= 2) {
                    tokenScore = max(tokenScore, 75)
                }

                // Season agreement bonus
                if (knownSeason != null && candidateSeason != null && knownSeason == candidateSeason) {
                    tokenScore += 15
                }

                bestScore = max(bestScore, tokenScore.coerceIn(0, 95))
            }

            // 4. Levenshtein similarity on base title for small typos/differences
            val levSim = levenshteinSimilarity(baseKnown, baseCandidate)
            bestScore = max(bestScore, levSim)
        }

        return bestScore
    }

    fun isGoodMatch(knownTitles: List<String>, candidateTitle: String): Boolean {
        return matchScore(knownTitles, candidateTitle) >= MIN_MATCH_THRESHOLD
    }

    /**
     * Cleans titles, removes punctuation, normalizes Arabic and Roman letters.
     */
    fun normalize(title: String): String {
        return title
            .lowercase(Locale.ROOT)
            .replace(Regex("""[ًٌٍَُِّْ]"""), "") // Arabic diacritics
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace(Regex("""\b(ii|2nd|second)\b"""), "2")
            .replace(Regex("""\b(iii|3rd|third)\b"""), "3")
            .replace(Regex("""\b(iv|4th|fourth)\b"""), "4")
            .replace(Regex("""\b(v|5th|fifth)\b"""), "5")
            .replace(Regex("""الموسم\s*(الاول|١)"""), "s1")
            .replace(Regex("""الموسم\s*(الثاني|٢)"""), "s2")
            .replace(Regex("""الموسم\s*(الثالث|٣)"""), "s3")
            .replace(Regex("""الموسم\s*(الرابع|٤)"""), "s4")
            .replace(Regex("""الموسم\s*(الخامس|٥)"""), "s5")
            .replace(Regex("""الجزء\s*(الاول|١)"""), "s1")
            .replace(Regex("""الجزء\s*(الثاني|٢)"""), "s2")
            .replace(Regex("""الجزء\s*(الثالث|٣)"""), "s3")
            .replace(Regex("""الجزء\s*(الرابع|٤)"""), "s4")
            .replace(Regex("""[^a-z0-9\u0600-\u06FF\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun extractSeasonNumber(title: String): Int? {
        val lower = title.lowercase(Locale.ROOT)
        val sMatch = Regex("""(?i)(?:season|s|الموسم|الجزء)\s*(\d+)""").find(lower)
        if (sMatch != null) return sMatch.groupValues[1].toIntOrNull()

        if (lower.contains("2nd season") || lower.contains("الموسم الثاني") || lower.contains("الجزء الثاني") || lower.contains("season 2")) return 2
        if (lower.contains("3rd season") || lower.contains("الموسم الثالث") || lower.contains("الجزء الثالث") || lower.contains("season 3")) return 3
        if (lower.contains("4th season") || lower.contains("الموسم الرابع") || lower.contains("الجزء الرابع") || lower.contains("season 4")) return 4
        if (lower.contains("5th season") || lower.contains("الموسم الخامس") || lower.contains("الجزء الخامس") || lower.contains("season 5")) return 5

        return null
    }

    fun isMovie(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("movie") || lower.contains("film") || lower.contains("فيلم")
    }

    private fun stripSeason(title: String): String {
        return title
            .replace(Regex("""(?i)\b(season\s*\d+|s\d+|part\s*\d+|cour\s*\d+|tv)\b.*"""), "")
            .replace(Regex("""(?i)\b(الموسم\s*\d+|الجزء\s*\d+)\b.*"""), "")
            .trim()
    }

    private fun levenshteinSimilarity(s1: String, s2: String): Int {
        if (s1 == s2) return 100
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0

        val maxLen = max(len1, len2)
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        val distance = dp[len1][len2]
        val ratio = (1.0 - (distance.toDouble() / maxLen.toDouble())) * 100.0
        return ratio.toInt().coerceIn(0, 100)
    }
}
