package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeAggregationTest {

    private class MockProvider(
        override val id: String,
        override val name: String,
        override val language: String = "ar",
        val episodesToReturn: List<AnimeEpisode> = emptyList(),
        val serversToReturn: List<AnimeStreamServer> = emptyList(),
        val shouldThrow: Boolean = false,
    ) : EpisodeSourceProvider {
        override suspend fun search(query: String): List<AnimeSearchResult> = emptyList()
        override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> {
            if (shouldThrow) throw RuntimeException("Network timeout in provider $id")
            return episodesToReturn
        }
        override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
            if (shouldThrow) throw RuntimeException("Failed to extract streams in provider $id")
            return serversToReturn
        }
    }

    /**
     * Requirement 1:
     * Source A provides 1-12, Source B provides 1-24.
     * Expected: unified list has 1-24 without duplicates.
     */
    @Test
    fun testMergeSource1To12AndSource1To24() {
        val provA = MockProvider("provA", "Source A")
        val provB = MockProvider("provB", "Source B")

        val match = AnimeSearchResult("test-id", "Attack on Titan", "https://example.com/aot")

        val episodesA = (1..12).map { i ->
            AnimeEpisode(
                number = i.toFloat(),
                title = "الحلقة $i",
                url = "https://sourceA.com/ep/$i",
                thumbnail = "https://sourceA.com/thumb/$i.jpg",
            )
        }

        val episodesB = (1..24).map { i ->
            AnimeEpisode(
                number = i.toFloat(),
                title = "Episode $i",
                url = "https://sourceB.com/ep/$i",
                thumbnail = "https://sourceB.com/thumb/$i.jpg",
            )
        }

        val batchA = ProviderEpisodeBatch(provA, match, episodesA, 90)
        val batchB = ProviderEpisodeBatch(provB, match, episodesB, 90)

        val merged = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(listOf(batchA, batchB))

        assertEquals("Merged episodes count must be 24", 24, merged.size)
        for (i in 1..24) {
            val ep = merged[i - 1]
            assertEquals(i.toFloat(), ep.number)
            if (i <= 12) {
                // Must have both source references
                assertEquals(2, ep.sources.size)
                assertTrue(ep.sources.any { it.providerId == "provA" })
                assertTrue(ep.sources.any { it.providerId == "provB" })
            } else {
                // Must have single source reference from provB
                assertEquals(1, ep.sources.size)
                assertEquals("provB", ep.sources.first().providerId)
            }
        }
    }

    /**
     * Requirement 2:
     * Source A provides odd episodes (1, 3, 5), Source B provides even episodes (2, 4, 6).
     * Expected: All episodes 1..6 are preserved in correct order.
     */
    @Test
    fun testMergeOddAndEvenEpisodes() {
        val provA = MockProvider("provA", "Source A")
        val provB = MockProvider("provB", "Source B")
        val match = AnimeSearchResult("test-id", "Jujutsu Kaisen", "https://example.com/jjk")

        val oddEpisodes = listOf(1, 3, 5).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://sourceA.com/ep/$i")
        }
        val evenEpisodes = listOf(2, 4, 6).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://sourceB.com/ep/$i")
        }

        val batchA = ProviderEpisodeBatch(provA, match, oddEpisodes, 85)
        val batchB = ProviderEpisodeBatch(provB, match, evenEpisodes, 85)

        val merged = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(listOf(batchA, batchB))

        assertEquals(6, merged.size)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f), merged.map { it.number })
    }

    /**
     * Requirement 3:
     * Same episode in 3 different sources.
     * Expected: Exactly 1 episode item in list, retaining 3 playback source references.
     */
    @Test
    fun testTriplicateEpisodeDeduplicationWithFallbackPreserved() {
        val provA = MockProvider("provA", "Source A")
        val provB = MockProvider("provB", "Source B")
        val provC = MockProvider("provC", "Source C")
        val match = AnimeSearchResult("test-id", "Demon Slayer", "https://example.com/ds")

        val epA = AnimeEpisode(number = 1f, title = "الحلقة 1", url = "https://sourceA.com/ep/1", thumbnail = "https://sourceA.com/thumb.jpg")
        val epB = AnimeEpisode(number = 1f, title = "Episode 1 - Cruelty", url = "https://sourceB.com/ep/1", thumbnail = null)
        val epC = AnimeEpisode(number = 1f, title = "Ep 1", url = "https://sourceC.com/ep/1", thumbnail = "https://sourceC.com/thumb.jpg")

        val batches = listOf(
            ProviderEpisodeBatch(provA, match, listOf(epA), 90),
            ProviderEpisodeBatch(provB, match, listOf(epB), 90),
            ProviderEpisodeBatch(provC, match, listOf(epC), 90),
        )

        val merged = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(batches)

        assertEquals("Must be deduplicated to exactly 1 episode", 1, merged.size)
        val unified = merged.first()
        assertEquals(1f, unified.number)
        assertEquals("Episode 1 - Cruelty", unified.title) // Most informative title preserved
        assertEquals("https://sourceA.com/thumb.jpg", unified.thumbnail) // Non-null thumbnail preserved
        assertEquals(3, unified.sources.size)
        assertTrue(unified.sources.any { it.providerId == "provA" })
        assertTrue(unified.sources.any { it.providerId == "provB" })
        assertTrue(unified.sources.any { it.providerId == "provC" })
    }

    /**
     * Requirement:
     * When multiple sources return Episode 1 (even with differing metadata or season tags),
     * they must be unified and deduplicated into 1 single episode with combined sources.
     */
    @Test
    fun testEpisodeDeduplicationAcrossSources() {
        val provA = MockProvider("provA", "Source A")
        val provB = MockProvider("provB", "Source B")
        val matchA = AnimeSearchResult("s1", "Kusuriya no Hitorigoto", "https://example.com/kh1")
        val matchB = AnimeSearchResult("s2", "Kusuriya no Hitorigoto 2nd Season", "https://example.com/kh2")

        val ep1 = AnimeEpisode(number = 1f, title = "Episode 1", url = "https://sourceA.com/1", seasonNumber = 1)
        val ep2 = AnimeEpisode(number = 1f, title = "الحلقة 1", url = "https://sourceB.com/1", seasonNumber = 2)

        val batches = listOf(
            ProviderEpisodeBatch(provA, matchA, listOf(ep1), 90),
            ProviderEpisodeBatch(provB, matchB, listOf(ep2), 95),
        )

        val merged = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(batches)

        assertEquals("Must merge duplicate episode 1 into exactly 1 episode", 1, merged.size)
        assertEquals(1f, merged[0].number)
        assertEquals("Must combine source references from both providers", 2, merged[0].sources.size)
    }

    /**
     * Requirement 5:
     * Episode has primary source that fails, but backup source is valid.
     * Expected: Transparent fallback returns playable streams from backup provider for the same episode.
     */
    @Test
    fun testFallbackResolutionWhenPrimarySourceFails() = runBlocking {
        val fallbackServer = AnimeStreamServer(
            name = "Backup Stream",
            url = "https://backup-cdn.com/ep8.mp4",
            isDirectVideo = true,
            quality = "1080p",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )

        val failingProv = MockProvider("failingMock", "Failing Source", shouldThrow = true)
        val workingProv = MockProvider("workingMock", "Working Source", serversToReturn = listOf(fallbackServer))

        EpisodeSourcesManager.registerCustomProvider(failingProv)
        EpisodeSourcesManager.registerCustomProvider(workingProv)

        try {
            // Episode has two source references: "failingMock" and "workingMock"
            val episode = AnimeEpisode(
                number = 8f,
                title = "الحلقة 8",
                url = "https://failing.com/ep8",
                sources = listOf(
                    EpisodeSourceReference("failingMock", "https://failing.com/ep8"),
                    EpisodeSourceReference("workingMock", "https://working.com/ep8"),
                ),
            )

            // Resolve with fallback starting with failingMock
            val result = EpisodeSourcesManager.resolveStreamServersWithFallback(
                episode = episode,
                preferredProviderId = "failingMock",
            )

            assertTrue("Fallback must succeed", result.success)
            assertEquals("Must resolve to workingMock", "workingMock", result.winningProviderId)
            assertEquals("Must keep episode 8", 8f, result.episode.number)
            assertEquals(1, result.servers.size)
            assertEquals("https://backup-cdn.com/ep8.mp4", result.servers.first().url)
        } finally {
            EpisodeSourcesManager.unregisterCustomProvider("failingMock")
            EpisodeSourcesManager.unregisterCustomProvider("workingMock")
        }
    }

    /**
     * Requirement 6:
     * Episode with special / OVA tags should not be confused with regular episodes.
     */
    @Test
    fun testSpecialAndOvaDistinction() {
        val provA = MockProvider("provA", "Source A")
        val match = AnimeSearchResult("id", "One Piece", "https://example.com/op")

        val regularEp1 = AnimeEpisode(number = 1f, title = "الحلقة 1", url = "https://sourceA.com/ep1", episodeType = EpisodeType.REGULAR)
        val specialEp1 = AnimeEpisode(number = 1f, title = "حلقة خاصة 1", url = "https://sourceA.com/sp1", episodeType = EpisodeType.SPECIAL)
        val ovaEp1 = AnimeEpisode(number = 1f, title = "OVA 1", url = "https://sourceA.com/ova1", episodeType = EpisodeType.OVA)

        val batch = ProviderEpisodeBatch(provA, match, listOf(regularEp1, specialEp1, ovaEp1), 90)
        val merged = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(listOf(batch))

        assertEquals("Must retain 3 distinct items: regular, special, and ova", 3, merged.size)
        assertTrue(merged.any { it.episodeType.isCanon })
        assertTrue(merged.any { it.episodeType == EpisodeType.SPECIAL })
        assertTrue(merged.any { it.episodeType == EpisodeType.OVA })
    }

    /**
     * Requirement 8 & 10:
     * Anime with a single episode / single source works normally without errors.
     */
    @Test
    fun testSingleEpisodeAnimeHandlesGracefully() {
        val provA = MockProvider("provA", "Source A")
        val match = AnimeSearchResult("movie-id", "Your Name", "https://example.com/your-name")
        val movieEp = AnimeEpisode(number = 1f, title = "Full Movie", url = "https://example.com/movie/1")

        val batch = ProviderEpisodeBatch(provA, match, listOf(movieEp), 95)
        val merged = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(listOf(batch))

        assertEquals(1, merged.size)
        assertEquals(1f, merged[0].number)
        assertEquals(1, merged[0].sources.size)
    }

    /**
     * Requirement 11:
     * When no valid sources exist for an episode, returns failure message without crashing.
     */
    @Test
    fun testNoValidSourcesReturnsFailureGracefully() = runBlocking {
        val brokenEp = AnimeEpisode(
            number = 5f,
            title = "الحلقة 5",
            url = "",
            sources = emptyList(),
        )

        val result = EpisodeSourcesManager.resolveStreamServersWithFallback(brokenEp)
        assertFalse(result.success)
        assertTrue(result.servers.isEmpty())
        assertNotNull(result.errorMessage)
    }
}
