package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedEpisodeAggregationEngineTest {

    private class MockSourceProvider(
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
            if (shouldThrow) throw RuntimeException("Extraction error in provider $id")
            return serversToReturn
        }
    }

    /**
     * Requirement 1: Merging episodes from two sources with overlapping and non-overlapping episodes.
     * Provider A has [1, 2, 4, 5] (missing 3).
     * Provider B has [2, 3, 5, 6] (missing 1, 4).
     * Unified list must have [1, 2, 3, 4, 5, 6] in order with dual sources on [2, 5].
     */
    @Test
    fun testMergeOverlappingAndNonOverlappingEpisodes() {
        val provA = MockSourceProvider("provA", "Source A")
        val provB = MockSourceProvider("provB", "Source B")
        val match = AnimeSearchResult("test-anime", "Jujutsu Kaisen", "https://example.com/jjk")

        val epsA = listOf(1, 2, 4, 5).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://sourceA.com/ep/$i")
        }
        val epsB = listOf(2, 3, 5, 6).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "Episode $i", url = "https://sourceB.com/ep/$i")
        }

        val batchA = ProviderEpisodeBatch(provA, match, epsA, 90)
        val batchB = ProviderEpisodeBatch(provB, match, epsB, 90)

        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(listOf(batchA, batchB))

        assertEquals("Unified list must contain exactly 6 episodes", 6, unified.size)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f), unified.map { it.number })

        // Check source references
        // Ep 1: only provA
        assertEquals(1, unified[0].sources.size)
        assertEquals("provA", unified[0].sources.first().providerId)

        // Ep 2: both provA and provB
        assertEquals(2, unified[1].sources.size)
        assertTrue(unified[1].sources.any { it.providerId == "provA" })
        assertTrue(unified[1].sources.any { it.providerId == "provB" })

        // Ep 3: recovered seamlessly from provB
        assertEquals(1, unified[2].sources.size)
        assertEquals("provB", unified[2].sources.first().providerId)

        // Ep 4: only provA
        assertEquals(1, unified[3].sources.size)
        assertEquals("provA", unified[3].sources.first().providerId)

        // Ep 5: both provA and provB
        assertEquals(2, unified[4].sources.size)

        // Ep 6: recovered seamlessly from provB
        assertEquals(1, unified[5].sources.size)
        assertEquals("provB", unified[5].sources.first().providerId)

        // Availability state on all discovered episodes must be DISCOVERED
        for (ep in unified) {
            assertEquals(EpisodeAvailabilityState.DISCOVERED, ep.availabilityState)
        }
    }

    /**
     * Requirement 2: Triplicate episode deduplication with fallback retention.
     * Episode 1 is present in 3 providers; unified episode has 3 fallback sources.
     */
    @Test
    fun testTriplicateDeduplicationWithFallbackRetention() {
        val provA = MockSourceProvider("provA", "Source A")
        val provB = MockSourceProvider("provB", "Source B")
        val provC = MockSourceProvider("provC", "Source C")
        val match = AnimeSearchResult("test-id", "Attack on Titan", "https://example.com/aot")

        val epA = AnimeEpisode(number = 1f, title = "الحلقة 1", url = "https://sourceA.com/1", thumbnail = "https://sourceA.com/t.jpg")
        val epB = AnimeEpisode(number = 1f, title = "Episode 1 - To You, in 2000 Years", url = "https://sourceB.com/1", thumbnail = null)
        val epC = AnimeEpisode(number = 1f, title = "Ep 1", url = "https://sourceC.com/1", thumbnail = "https://sourceC.com/t.jpg")

        val batches = listOf(
            ProviderEpisodeBatch(provA, match, listOf(epA), 95),
            ProviderEpisodeBatch(provB, match, listOf(epB), 90),
            ProviderEpisodeBatch(provC, match, listOf(epC), 85),
        )

        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(batches)

        assertEquals("Must deduplicate to exactly 1 episode", 1, unified.size)
        val ep = unified.first()
        assertEquals(1f, ep.number)
        assertEquals("Episode 1 - To You, in 2000 Years", ep.title) // Longest informative title preserved
        assertEquals("https://sourceA.com/t.jpg", ep.thumbnail) // Valid thumbnail preserved
        assertEquals("Must retain all 3 fallback sources", 3, ep.sources.size)
        assertTrue(ep.sources.any { it.providerId == "provA" })
        assertTrue(ep.sources.any { it.providerId == "provB" })
        assertTrue(ep.sources.any { it.providerId == "provC" })
    }

    /**
     * Requirement 3: Single-source episode recovery.
     * Primary provider returns empty or fails; backup provider returns episodes.
     * Unified engine recovers all episodes seamlessly.
     */
    @Test
    fun testSingleSourceEpisodeRecovery() {
        val failingProv = MockSourceProvider("failingProv", "Failing Primary", shouldThrow = true)
        val backupProv = MockSourceProvider("backupProv", "Backup Secondary")
        val match = AnimeSearchResult("anime-id", "Demon Slayer", "https://example.com/ds")

        val backupEpisodes = (1..12).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://backup.com/ep/$i")
        }

        // Batch only contains responding backup batch
        val batch = ProviderEpisodeBatch(backupProv, match, backupEpisodes, 90)
        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(listOf(batch))

        assertEquals("Must recover all 12 episodes from alternative source", 12, unified.size)
        assertEquals(1f, unified.first().number)
        assertEquals(12f, unified.last().number)
        for (ep in unified) {
            assertEquals(1, ep.sources.size)
            assertEquals("backupProv", ep.sources.first().providerId)
            assertEquals(EpisodeAvailabilityState.DISCOVERED, ep.availabilityState)
        }
    }

    /**
     * Requirement 4: Episode availability state transitions.
     * DISCOVERED -> CHECKING -> PLAYABLE (on success)
     * DISCOVERED -> CHECKING -> UNAVAILABLE (on failure)
     */
    @Test
    fun testEpisodeAvailabilityStateTransitions() = runBlocking {
        val workingServer = AnimeStreamServer(
            name = "Direct CDN",
            url = "https://cdn.example.com/video/ep1.mp4",
            isDirectVideo = true,
            quality = "1080p",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )

        val workingProv = MockSourceProvider("workingProv", "Working Provider", serversToReturn = listOf(workingServer))
        val failingProv = MockSourceProvider("failingProv", "Failing Provider", shouldThrow = true)

        EpisodeSourcesManager.registerCustomProvider(workingProv)
        EpisodeSourcesManager.registerCustomProvider(failingProv)

        try {
            // 1. Initial State: DISCOVERED
            val initialEp = AnimeEpisode(
                number = 1f,
                title = "الحلقة 1",
                url = "https://cdn.example.com/ep1",
                sources = listOf(EpisodeSourceReference("workingProv", "https://cdn.example.com/ep1")),
            )
            assertEquals(EpisodeAvailabilityState.DISCOVERED, initialEp.availabilityState)

            // 2. Transition: CHECKING
            val checkingEp = EpisodeSourcesManager.markEpisodeChecking(initialEp)
            assertEquals(EpisodeAvailabilityState.CHECKING, checkingEp.availabilityState)

            // 3. Transition: PLAYABLE (successful stream resolution)
            val playResult = EpisodeSourcesManager.resolveStreamServersWithFallback(
                episode = initialEp,
                preferredProviderId = "workingProv",
            )
            assertTrue("Resolution must succeed", playResult.success)
            assertEquals("Availability state must transition to PLAYABLE", EpisodeAvailabilityState.PLAYABLE, playResult.episode.availabilityState)
            assertTrue("Playable helper property must return true", playResult.episode.availabilityState.isPlayable)

            // 4. Transition: UNAVAILABLE (failing source resolution)
            val brokenEp = AnimeEpisode(
                number = 2f,
                title = "الحلقة 2",
                url = "https://broken.com/ep2",
                sources = listOf(EpisodeSourceReference("failingProv", "https://broken.com/ep2")),
            )
            val failResult = EpisodeSourcesManager.resolveStreamServersWithFallback(
                episode = brokenEp,
                preferredProviderId = "failingProv",
            )
            assertFalse("Resolution must fail", failResult.success)
            assertEquals("Availability state must transition to UNAVAILABLE", EpisodeAvailabilityState.UNAVAILABLE, failResult.episode.availabilityState)
            assertTrue("Unavailable helper property must return true", failResult.episode.availabilityState.isUnavailable)
        } finally {
            EpisodeSourcesManager.unregisterCustomProvider("workingProv")
            EpisodeSourcesManager.unregisterCustomProvider("failingProv")
        }
    }
}
