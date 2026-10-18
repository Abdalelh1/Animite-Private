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
        val candidatePart = extractPartNumber(candidateTitle)
        val candidateIsMovie = isMovie(candidateTitle)
        val candidateIsOva = isOva(candidateTitle)
        val candidateIsSpecial = isSpecial(candidateTitle)

        // Strict Season check across known titles
        val explicitKnownSeason = knownTitles.mapNotNull { extractSeasonNumber(it) }.firstOrNull()
        if (explicitKnownSeason != null && candidateSeason != null && explicitKnownSeason != candidateSeason) {
            return 0
        }
        // If candidate explicitly specifies season > 1 but known title is season 1 or unnumbered root season
        if (candidateSeason != null && candidateSeason > 1 && explicitKnownSeason == null) {
            return 0
        }
        // If known title explicitly specifies season > 1 but candidate explicitly specifies season 1
        if (explicitKnownSeason != null && explicitKnownSeason > 1 && candidateSeason != null && candidateSeason == 1) {
            return 0
        }

        // Strict Part / Cour check
        val explicitKnownPart = knownTitles.mapNotNull { extractPartNumber(it) }.firstOrNull()
        if (explicitKnownPart != null && candidatePart != null && explicitKnownPart != candidatePart) {
            return 0
        }
        if (candidatePart != null && candidatePart > 1 && explicitKnownPart == null) {
            return 0
        }
        if (explicitKnownPart != null && explicitKnownPart > 1 && candidatePart != null && candidatePart == 1) {
            return 0
        }

        // Strict Movie vs TV check
        val overallKnownIsMovie = knownTitles.any { isMovie(it) }
        if (overallKnownIsMovie != candidateIsMovie) {
            return 0
        }

        // Strict OVA check
        val overallKnownIsOva = knownTitles.any { isOva(it) }
        if (overallKnownIsOva != candidateIsOva) {
            return 0
        }

        // Strict Special check
        val overallKnownIsSpecial = knownTitles.any { isSpecial(it) }
        if (overallKnownIsSpecial != candidateIsSpecial) {
            return 0
        }

        // Prequel / "0" / "zero" check (e.g. Jujutsu Kaisen vs Jujutsu Kaisen 0)
        val knownHasZero = knownTitles.any { hasZeroMarker(it) }
        val candidateHasZero = hasZeroMarker(candidateTitle)
        if (knownHasZero != candidateHasZero) {
            return 0
        }

        // Shippuden sequel check (e.g. Naruto vs Naruto Shippuden)
        val knownHasShippuden = knownTitles.any { isShippuden(it) }
        val candidateHasShippuden = isShippuden(candidateTitle)
        if (knownHasShippuden != candidateHasShippuden) {
            return 0
        }

        // Boruto sequel check (e.g. Naruto vs Boruto)
        val knownHasBoruto = knownTitles.any { isBoruto(it) }
        val candidateHasBoruto = isBoruto(candidateTitle)
        if (knownHasBoruto != candidateHasBoruto) {
            return 0
        }

        var bestScore = 0

        for (rawTitle in knownTitles) {
            if (rawTitle.isBlank()) continue
            val cleanKnown = normalize(rawTitle)
            val knownSeason = extractSeasonNumber(rawTitle)

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

            // 3. Token-based similarity (Jaccard on words without stop words)
            val stopWords = setOf("the", "a", "an", "of", "no", "to", "in", "on", "and", "la", "le", "el", "de", "من", "في", "على", "عن", "الى")
            val knownTokens = baseKnown.split(" ").filter { it.length > 1 && it !in stopWords }.toSet()
            val candidateTokens = baseCandidate.split(" ").filter { it.length > 1 && it !in stopWords }.toSet()

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
                if (isSubsetTitle && intersection >= 1) {
                    val firstKnown = baseKnown.split(" ").firstOrNull { it.isNotBlank() }
                    val firstCandidate = baseCandidate.split(" ").firstOrNull { it.isNotBlank() }
                    if (firstKnown != null && firstCandidate != null &&
                        (firstKnown == firstCandidate || baseCandidate.startsWith(firstKnown) || baseKnown.startsWith(firstCandidate))
                    ) {
                        val bonusScore = if (intersection >= 2 || knownTokens.size == 1) 85 else 75
                        tokenScore = max(tokenScore, bonusScore)
                    }
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
            .replace(Regex("""[ًٌٍَُِّْـ]"""), "") // Arabic diacritics & tatweel
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ة', 'ه')
            .replace('ى', 'ي')
            .replace(Regex("""\b(ii|2nd|second)\b"""), "2")
            .replace(Regex("""\b(iii|3rd|third)\b"""), "3")
            .replace(Regex("""\b(iv|4th|fourth)\b"""), "4")
            .replace(Regex("""\b(v|5th|fifth)\b"""), "5")
            .replace(Regex("""\b(vi|6th|sixth)\b"""), "6")
            .replace(Regex("""\b(vii|7th|seventh)\b"""), "7")
            .replace(Regex("""\b(viii|8th|eighth)\b"""), "8")
            .replace(Regex("""\b(ix|9th|ninth)\b"""), "9")
            .replace(Regex("""\b(x|10th|tenth)\b"""), "10")
            .replace(Regex("""الموسم\s*(الاول|١)"""), "s1")
            .replace(Regex("""الموسم\s*(الثاني|٢)"""), "s2")
            .replace(Regex("""الموسم\s*(الثالث|٣)"""), "s3")
            .replace(Regex("""الموسم\s*(الرابع|٤)"""), "s4")
            .replace(Regex("""الموسم\s*(الخامس|٥)"""), "s5")
            .replace(Regex("""الموسم\s*(السادس|٦)"""), "s6")
            .replace(Regex("""الموسم\s*(السابع|٧)"""), "s7")
            .replace(Regex("""الموسم\s*(الثامن|٨)"""), "s8")
            .replace(Regex("""الموسم\s*(التاسع|٩)"""), "s9")
            .replace(Regex("""الموسم\s*(العاشر|١٠)"""), "s10")
            .replace(Regex("""الجزء\s*(الاول|١)"""), "s1")
            .replace(Regex("""الجزء\s*(الثاني|٢)"""), "s2")
            .replace(Regex("""الجزء\s*(الثالث|٣)"""), "s3")
            .replace(Regex("""الجزء\s*(الرابع|٤)"""), "s4")
            .replace(Regex("""الجزء\s*(الخامس|٥)"""), "s5")
            .replace(Regex("""الجزء\s*(السادس|٦)"""), "s6")
            .replace(Regex("""الجزء\s*(السابع|٧)"""), "s7")
            .replace(Regex("""الجزء\s*(الثامن|٨)"""), "s8")
            .replace(Regex("""الجزء\s*(التاسع|٩)"""), "s9")
            .replace(Regex("""الجزء\s*(العاشر|١٠)"""), "s10")
            .replace(Regex("""[^a-z0-9\u0600-\u06FF\s]"""), " ")
            .replace(Regex("""(?i)(?:^|\s+)(?:مسلسل|انمي|مترجم|مدبلج|اون\s*لاين|اونلاين|حلقات|كامل)(?:\s+|$)"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun extractSeasonNumber(title: String): Int? {
        val lower = title.lowercase(Locale.ROOT)
        val sMatch = Regex("""(?i)(?:\b(?:season|s)\s*(\d{1,2})\b|(?:^|\s+)(?:الموسم|الجزء)\s*(\d{1,2}))""").find(lower)
        if (sMatch != null) {
            val numStr = sMatch.groupValues[1].ifEmpty { sMatch.groupValues[2] }
            val num = numStr.toIntOrNull()
            if (num != null && num in 1..50) return num
        }

        if (lower.contains("1st") || lower.contains("first season") || lower.contains("الموسم الاول") || lower.contains("الجزء الاول") || lower.contains("season 1") || lower.contains("s1")) return 1
        if (lower.contains("2nd") || lower.contains("second season") || lower.contains("الموسم الثاني") || lower.contains("الجزء الثاني") || lower.contains("season 2") || lower.contains("s2")) return 2
        if (lower.contains("3rd") || lower.contains("third season") || lower.contains("الموسم الثالث") || lower.contains("الجزء الثالث") || lower.contains("season 3") || lower.contains("s3")) return 3
        if (lower.contains("4th") || lower.contains("fourth season") || lower.contains("الموسم الرابع") || lower.contains("الجزء الرابع") || lower.contains("season 4") || lower.contains("s4")) return 4
        if (lower.contains("final season") || lower.contains("the final season") || lower.contains("الموسم الاخير") || lower.contains("الموسم النهائي")) return 4
        if (lower.contains("5th") || lower.contains("fifth season") || lower.contains("الموسم الخامس") || lower.contains("الجزء الخامس") || lower.contains("season 5") || lower.contains("s5")) return 5
        if (lower.contains("6th") || lower.contains("sixth season") || lower.contains("الموسم السادس") || lower.contains("الجزء السادس") || lower.contains("season 6") || lower.contains("s6")) return 6
        if (lower.contains("7th") || lower.contains("seventh season") || lower.contains("الموسم السابع") || lower.contains("الجزء السابع") || lower.contains("season 7") || lower.contains("s7")) return 7
        if (lower.contains("8th") || lower.contains("eighth season") || lower.contains("الموسم الثامن") || lower.contains("الجزء الثامن") || lower.contains("season 8") || lower.contains("s8")) return 8
        if (lower.contains("9th") || lower.contains("ninth season") || lower.contains("الموسم التاسع") || lower.contains("الجزء التاسع") || lower.contains("season 9") || lower.contains("s9")) return 9
        if (lower.contains("10th") || lower.contains("tenth season") || lower.contains("الموسم العاشر") || lower.contains("الجزء العاشر") || lower.contains("season 10") || lower.contains("s10")) return 10

        return null
    }

    fun extractPartNumber(title: String): Int? {
        val lower = title.lowercase(Locale.ROOT)
        val pMatch = Regex("""(?i)(?:\b(?:part|cour)\s*(\d{1,2})\b|(?:^|\s+)(?:البارت|كوار|الكوار|القسم)\s*(\d{1,2}))""").find(lower)
        if (pMatch != null) {
            val numStr = pMatch.groupValues[1].ifEmpty { pMatch.groupValues[2] }
            val num = numStr.toIntOrNull()
            if (num != null && num in 1..10) return num
        }

        if (lower.contains("part 1") || lower.contains("cour 1") || lower.contains("1st part") || lower.contains("1st cour") || lower.contains("البارت الاول") || lower.contains("القسم الاول") || lower.contains("كوار 1") || lower.contains("الكوار الاول")) return 1
        if (lower.contains("part 2") || lower.contains("cour 2") || lower.contains("2nd part") || lower.contains("2nd cour") || lower.contains("البارت الثاني") || lower.contains("القسم الثاني") || lower.contains("كوار 2") || lower.contains("الكوار الثاني")) return 2
        if (lower.contains("part 3") || lower.contains("cour 3") || lower.contains("3rd part") || lower.contains("3rd cour") || lower.contains("البارت الثالث") || lower.contains("القسم الثالث") || lower.contains("كوار 3") || lower.contains("الكوار الثالث")) return 3
        if (lower.contains("part 4") || lower.contains("cour 4") || lower.contains("4th part") || lower.contains("4th cour") || lower.contains("البارت الرابع") || lower.contains("القسم الرابع") || lower.contains("كوار 4") || lower.contains("الكوار الرابع")) return 4

        return null
    }

    fun isMovie(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("movie") || lower.contains("film") || lower.contains("فيلم")
    }

    fun isOva(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("أوفا") || lower.contains("اوفا") ||
                Regex("""\bova\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
    }

    fun isOna(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("أونا") || lower.contains("اونا") ||
                Regex("""\bona\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
    }

    fun isSpecial(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("حلقة خاصة") || lower.contains("حلقات خاصة") ||
                Regex("""\b(special|sp)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
    }

    private fun hasZeroMarker(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return Regex("""(?i)\b(0|zero)\b""").containsMatchIn(lower)
    }

    fun isShippuden(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("shippuden") || lower.contains("shippuuden") || lower.contains("شيبودن")
    }

    fun isBoruto(title: String): Boolean {
        val lower = title.lowercase(Locale.ROOT)
        return lower.contains("boruto") || lower.contains("بوروتو")
    }

    private fun stripSeason(title: String): String {
        return title
            .replace(Regex("""(?i)\b(season\s*\d+|s\d+|part\s*\d+|cour\s*\d+|1st|2nd|3rd|4th|5th|6th|7th|8th|9th|10th)\b"""), "")
            .replace(Regex("""(?i)\b(tv|final\s*season)\b"""), "")
            .replace(Regex("""(?i)\b(الموسم\s*\d+|الجزء\s*\d+|البارت\s*\d+|القسم\s*\d+|كوار\s*\d+|الكوار\s*\d+)\b"""), "")
            .replace(Regex("""(?i)\b(الموسم\s*(الاول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر|الاخير|النهائي))\b"""), "")
            .replace(Regex("""(?i)\b(الجزء\s*(الاول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر))\b"""), "")
            .replace(Regex("""(?i)\b(البارت\s*(الاول|الثاني|الثالث|الرابع)|القسم\s*(الاول|الثاني|الثالث|الرابع)|الكوار\s*(الاول|الثاني|الثالث|الرابع))\b"""), "")
            .replace(Regex("""(?i)(?:^|\s+)(?:مسلسل|انمي|مترجم|مدبلج|اون\s*لاين|اونلاين|حلقات|كامل)(?:\s+|$)"""), " ")
            .replace(Regex("""\s+"""), " ")
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
