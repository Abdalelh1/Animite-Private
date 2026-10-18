package com.imashnake.animite.media.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeIdentityAndMatchingTest {

    private class TestProvider(
        override val id: String,
        override val name: String,
        override val language: String = "ar",
    ) : EpisodeSourceProvider {
        override suspend fun search(query: String): List<AnimeSearchResult> = emptyList()
        override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> = emptyList()
        override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> = emptyList()
    }

    /**
     * Requirement 1: English, Arabic, and Romaji title matches.
     */
    @Test
    fun testMultilingualTitleMatching() {
        val knownTitles = listOf(
            "Attack on Titan",
            "Shingeki no Kyojin",
            "هجوم العمالقة",
            "進撃の巨人",
        )

        // Exact match with English
        val engScore = AnimeTitleMatcher.matchScore(knownTitles, "Attack on Titan")
        assertEquals(100, engScore)

        // Exact match with Romaji
        val romajiScore = AnimeTitleMatcher.matchScore(knownTitles, "Shingeki no Kyojin")
        assertEquals(100, romajiScore)

        // Exact match with Arabic
        val arScore = AnimeTitleMatcher.matchScore(knownTitles, "هجوم العمالقة")
        assertEquals(100, arScore)

        // Match with typical Arabic web provider wrappers ("انمي ... مترجم")
        val webArScore = AnimeTitleMatcher.matchScore(knownTitles, "انمي هجوم العمالقة مترجم")
        assertTrue("Arabic web title with boilerplate should score >= 85, was $webArScore", webArScore >= 85)

        // English with punctuation differences
        val punctScore = AnimeTitleMatcher.matchScore(listOf("Kusuriya no Hitorigoto"), "kusuriya-no-hitorigoto")
        assertTrue("Normalized slug/hyphenated title should match", punctScore >= 85)
    }

    /**
     * Requirement 2: Sequel and season separation.
     */
    @Test
    fun testSeasonAndSequelStrictSeparation() {
        val s2Titles = listOf(
            "Attack on Titan Season 2",
            "Shingeki no Kyojin 2nd Season",
            "هجوم العمالقة الموسم الثاني",
        )

        // Same season matching across English, Romaji, and Arabic
        val matchS2Eng = AnimeTitleMatcher.matchScore(s2Titles, "Attack on Titan Season 2")
        assertEquals(100, matchS2Eng)

        val matchS2Ar = AnimeTitleMatcher.matchScore(s2Titles, "هجوم العمالقة الموسم 2")
        assertTrue("Season 2 Arabic should match Season 2, was $matchS2Ar", matchS2Ar >= 85)

        // Season 2 must NEVER match Season 1
        val mismatchS1 = AnimeTitleMatcher.matchScore(s2Titles, "Attack on Titan Season 1")
        assertEquals("Season 2 must never match Season 1", 0, mismatchS1)

        // Season 2 must NEVER match Season 3
        val mismatchS3 = AnimeTitleMatcher.matchScore(s2Titles, "Attack on Titan Season 3")
        assertEquals("Season 2 must never match Season 3", 0, mismatchS3)

        // Unnumbered known title (Season 1) must NEVER match Season 2 candidate
        val rootSeasonTitles = listOf("Jujutsu Kaisen", "جوجوتسو كايسن")
        val mismatchRootVsS2 = AnimeTitleMatcher.matchScore(rootSeasonTitles, "Jujutsu Kaisen 2nd Season")
        assertEquals("Root season must never match Season 2 candidate", 0, mismatchRootVsS2)

        val mismatchRootVsS2Ar = AnimeTitleMatcher.matchScore(rootSeasonTitles, "جوجوتسو كايسن الموسم الثاني")
        assertEquals("Root season must never match Season 2 Arabic candidate", 0, mismatchRootVsS2Ar)

        // Part and Cour strict separation
        val part1Titles = listOf("Spy x Family Part 1", "سباي x فاميلي البارت الاول")
        val mismatchPart2 = AnimeTitleMatcher.matchScore(part1Titles, "Spy x Family Part 2")
        assertEquals("Part 1 must never match Part 2", 0, mismatchPart2)

        val matchPart1 = AnimeTitleMatcher.matchScore(part1Titles, "Spy x Family Part 1")
        assertEquals(100, matchPart1)

        // Subseries and Sequel strict separation: Naruto vs Naruto Shippuden vs Boruto
        val narutoTitles = listOf("Naruto", "ناروتو")
        val mismatchShippuden = AnimeTitleMatcher.matchScore(narutoTitles, "TV Naruto Shippuden 2007 8.4")
        assertEquals("Naruto must never match Naruto Shippuden", 0, mismatchShippuden)

        val mismatchShippudenAr = AnimeTitleMatcher.matchScore(narutoTitles, "ناروتو شيبودن")
        assertEquals("Naruto must never match Arabic Naruto Shippuden", 0, mismatchShippudenAr)

        val mismatchBoruto = AnimeTitleMatcher.matchScore(narutoTitles, "Boruto: Naruto Next Generations")
        assertEquals("Naruto must never match Boruto", 0, mismatchBoruto)

        val shippudenTitles = listOf("Naruto Shippuden", "ناروتو شيبودن")
        val mismatchNarutoRoot = AnimeTitleMatcher.matchScore(shippudenTitles, "Naruto")
        assertEquals("Naruto Shippuden must never match root Naruto", 0, mismatchNarutoRoot)

        val matchShippuden = AnimeTitleMatcher.matchScore(shippudenTitles, "TV Naruto Shippuden")
        assertTrue("Naruto Shippuden must match Naruto Shippuden", matchShippuden >= 85)
    }

    /**
     * Requirement 3: Movie vs TV series matching and prequel "0" separation.
     */
    @Test
    fun testMovieVsTvSeriesAndZeroSeparation() {
        // TV series known titles
        val tvTitles = listOf("Demon Slayer: Kimetsu no Yaiba", "قاتل الشياطين")

        // Candidate is a Movie
        val movieCandidateMismatch = AnimeTitleMatcher.matchScore(tvTitles, "Demon Slayer: Kimetsu no Yaiba Movie - Mugen Train")
        assertEquals("TV Series must never match Movie", 0, movieCandidateMismatch)

        val movieArabicMismatch = AnimeTitleMatcher.matchScore(tvTitles, "فيلم قاتل الشياطين: قطار اللانهاية")
        assertEquals("TV Series must never match Arabic Movie", 0, movieArabicMismatch)

        // Known title is a Movie
        val movieKnownTitles = listOf("Demon Slayer The Movie: Mugen Train", "فيلم قاتل الشياطين قطار اللانهاية")
        val movieMatch = AnimeTitleMatcher.matchScore(movieKnownTitles, "Demon Slayer: Kimetsu no Yaiba Movie - Mugen Train")
        assertTrue("Movie should match candidate movie, was $movieMatch", movieMatch >= 80)

        // Prequel "0" separation (e.g. Jujutsu Kaisen TV vs Jujutsu Kaisen 0)
        val jjkTvTitles = listOf("Jujutsu Kaisen", "جوجوتسو كايسن")
        val jjk0Mismatch = AnimeTitleMatcher.matchScore(jjkTvTitles, "Jujutsu Kaisen 0")
        assertEquals("Main TV series must never match '0' prequel", 0, jjk0Mismatch)

        val jjk0KnownTitles = listOf("Jujutsu Kaisen 0", "جوجوتسو كايسن 0")
        val jjk0Match = AnimeTitleMatcher.matchScore(jjk0KnownTitles, "Jujutsu Kaisen 0")
        assertEquals(100, jjk0Match)
    }

    /**
     * Requirement 4: Continuous episode numbering normalization.
     * Season 2 episodes 13-24 mapping to 1-12 where appropriate and unifying across providers.
     */
    @Test
    fun testContinuousEpisodeNumberingNormalization() {
        val provA = TestProvider("provA", "Source A")
        val provB = TestProvider("provB", "Source B")

        val match = AnimeSearchResult("s2-id", "Attack on Titan Season 2", "https://example.com/aot-s2")

        // Provider A numbered Season 2 from 1 to 12
        val episodesA = (1..12).map { i ->
            AnimeEpisode(
                number = i.toFloat(),
                title = "الحلقة $i",
                url = "https://sourceA.com/s2/ep/$i",
                thumbnail = "https://sourceA.com/thumb/$i.jpg",
            )
        }

        // Provider B numbered Season 2 continuously from 13 to 24
        val episodesB = (13..24).map { i ->
            AnimeEpisode(
                number = i.toFloat(),
                title = "Episode $i",
                url = "https://sourceB.com/s2/ep/$i",
                thumbnail = "https://sourceB.com/thumb/$i.jpg",
            )
        }

        val batchA = ProviderEpisodeBatch(provA, match, episodesA, 95)
        val batchB = ProviderEpisodeBatch(provB, match, episodesB, 95)

        // Unify for Season 2 with totalEpisodes = 12
        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(
            batches = listOf(batchA, batchB),
            totalEpisodes = 12,
            titles = listOf("Attack on Titan Season 2", "Shingeki no Kyojin 2nd Season"),
        )

        assertEquals("Unified list must have exactly 12 episodes", 12, unified.size)
        for (i in 1..12) {
            val ep = unified[i - 1]
            assertEquals("Episode number must be normalized to season range 1..12", i.toFloat(), ep.number)
            assertEquals("Each episode must combine sources from both Provider A and Provider B", 2, ep.sources.size)
            assertTrue(ep.sources.any { it.providerId == "provA" })
            assertTrue(ep.sources.any { it.providerId == "provB" })
        }

        // Test single provider with continuous numbering 13..24: must map to 1..12 and not be dropped
        val singleBatchBUnified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(
            batches = listOf(batchB),
            totalEpisodes = 12,
            titles = listOf("Attack on Titan Season 2"),
        )
        assertEquals("Single continuous batch must map to 12 episodes without being filtered out", 12, singleBatchBUnified.size)
        assertEquals(1f, singleBatchBUnified.first().number)
        assertEquals(12f, singleBatchBUnified.last().number)
    }

    /**
     * Requirement 5: Decimal and special episode matching.
     * Decimals (e.g. 6.5, 12.5) are preserved as Float, classified as SPECIAL/RECAP,
     * and not filtered out by totalEpisodes = 12.
     */
    @Test
    fun testDecimalAndSpecialEpisodeMatching() {
        val prov = TestProvider("provA", "Source A")
        val match = AnimeSearchResult("s1-id", "Frieren", "https://example.com/frieren")

        val episodes = listOf(
            AnimeEpisode(number = 1f, title = "Episode 1", url = "https://example.com/ep1"),
            AnimeEpisode(number = 6f, title = "Episode 6", url = "https://example.com/ep6"),
            AnimeEpisode(number = 6.5f, title = "Episode 6.5 - Special Recap", url = "https://example.com/ep6.5"),
            AnimeEpisode(number = 7f, title = "Episode 7", url = "https://example.com/ep7"),
            AnimeEpisode(number = 12f, title = "Episode 12", url = "https://example.com/ep12"),
            AnimeEpisode(number = 12.5f, title = "حلقة خاصة 12.5", url = "https://example.com/ep12.5"),
        )

        val batch = ProviderEpisodeBatch(prov, match, episodes, 90)

        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(
            batches = listOf(batch),
            totalEpisodes = 12, // Regular season count is 12
            titles = listOf("Frieren: Beyond Journey's End"),
        )

        // 6.5 and 12.5 must both be preserved!
        assertEquals("All regular and decimal special episodes must be retained", 6, unified.size)

        val ep6Point5 = unified.firstOrNull { it.number == 6.5f }
        assertTrue("Episode 6.5 must be present in unified episodes", ep6Point5 != null)
        assertEquals(6.5f, ep6Point5!!.number)
        assertTrue(
            "Episode 6.5 with recap title must be classified as RECAP or SPECIAL",
            ep6Point5.episodeType == EpisodeType.RECAP || ep6Point5.episodeType == EpisodeType.SPECIAL
        )

        val ep12Point5 = unified.firstOrNull { it.number == 12.5f }
        assertTrue("Special recap 12.5 must be retained even when totalEpisodes = 12", ep12Point5 != null)
        assertEquals(12.5f, ep12Point5!!.number)
        assertTrue("Episode 12.5 must be classified as SPECIAL", ep12Point5.episodeType == EpisodeType.SPECIAL)
    }
}
