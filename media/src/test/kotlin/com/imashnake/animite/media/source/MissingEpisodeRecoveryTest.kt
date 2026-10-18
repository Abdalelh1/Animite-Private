package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5: Missing-Episode Recovery Engine Test Suite.
 * Validates automatic gap detection, multi-provider fallback loop,
 * canonical ordering, source tagging, and independent stream resolution.
 */
class MissingEpisodeRecoveryTest {

    private class MockProvider(
        override val id: String,
        override val name: String,
        override val language: String = "ar",
        val searchResultsToReturn: List<AnimeSearchResult> = emptyList(),
        val episodesToReturn: List<AnimeEpisode> = emptyList(),
        val serversToReturn: List<AnimeStreamServer> = emptyList(),
    ) : EpisodeSourceProvider {
        override suspend fun search(query: String): List<AnimeSearchResult> = searchResultsToReturn
        override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> = episodesToReturn
        override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> = serversToReturn
    }

    /**
     * Requirement 1: Primary provider missing middle episodes (e.g. has 1, 2, 4; secondary fills 3).
     */
    @Test
    fun testPrimaryProviderMissingMiddleEpisodesRecovery() {
        val provA = MockProvider("provA", "Source A")
        val provB = MockProvider("provB", "Source B")
        val match = AnimeSearchResult("test-anime", "Jujutsu Kaisen", "https://example.com/jjk")

        val epsA = listOf(1, 2, 4).map { i ->
            AnimeEpisode(
                number = i.toFloat(),
                title = "الحلقة $i",
                url = "https://sourceA.com/ep/$i",
                sources = listOf(EpisodeSourceReference("provA", "https://sourceA.com/ep/$i", sourceName = "Source A")),
            )
        }

        val epsB = listOf(3, 4, 5).map { i ->
            AnimeEpisode(
                number = i.toFloat(),
                title = "الحلقة $i",
                url = "https://sourceB.com/ep/$i",
                sources = listOf(EpisodeSourceReference("provB", "https://sourceB.com/ep/$i", sourceName = "Source B")),
            )
        }

        val batchA = ProviderEpisodeBatch(provA, match, epsA, 95)
        val batchB = ProviderEpisodeBatch(provB, match, epsB, 90)

        // Verify gap detection
        val missingBefore = EpisodeSourcesManager.detectMissingEpisodeNumbers(epsA, totalEpisodes = 5)
        assertEquals(listOf(3, 5), missingBefore)

        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(listOf(batchA, batchB))

        assertEquals("Must unite to exactly 5 episodes without gaps", 5, unified.size)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f), unified.map { it.number })

        // Check source tagging
        assertEquals("provA", unified[0].primarySourceProviderId) // Ep 1
        assertEquals("provA", unified[1].primarySourceProviderId) // Ep 2
        assertEquals("provB", unified[2].primarySourceProviderId) // Ep 3 (recovered from provB)
        assertEquals("Source B", unified[2].primarySourceName)
        assertEquals(2, unified[3].sources.size) // Ep 4 (both provA & provB)
        assertEquals("provB", unified[4].primarySourceProviderId) // Ep 5 (recovered from provB)

        // Verify no gaps remain
        val missingAfter = EpisodeSourcesManager.detectMissingEpisodeNumbers(unified, totalEpisodes = 5)
        assertTrue(missingAfter.isEmpty())
    }

    /**
     * Requirement 2: Primary provider missing latest episodes (e.g. has 1..20; secondary fills 21..24).
     */
    @Test
    fun testPrimaryProviderMissingLatestEpisodesRecovery() {
        val provA = MockProvider("provA", "Source A")
        val provB = MockProvider("provB", "Source B")
        val match = AnimeSearchResult("test-anime", "Frieren", "https://example.com/frieren")

        val epsA = (1..20).map { i ->
            AnimeEpisode(
                number = i.toFloat(),
                title = "الحلقة $i",
                url = "https://sourceA.com/ep/$i",
                sources = listOf(EpisodeSourceReference("provA", "https://sourceA.com/ep/$i", sourceName = "Source A")),
            )
        }

        val epsB = (21..24).map { i ->
            AnimeEpisode(
                number = i.toFloat(),
                title = "الحلقة $i",
                url = "https://sourceB.com/ep/$i",
                sources = listOf(EpisodeSourceReference("provB", "https://sourceB.com/ep/$i", sourceName = "Source B")),
            )
        }

        val batchA = ProviderEpisodeBatch(provA, match, epsA, 95)
        val batchB = ProviderEpisodeBatch(provB, match, epsB, 90)

        val missingBefore = EpisodeSourcesManager.detectMissingEpisodeNumbers(epsA, totalEpisodes = 24)
        assertEquals((21..24).toList(), missingBefore)

        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(
            batches = listOf(batchA, batchB),
            totalEpisodes = 24,
        )

        assertEquals("Must unite into 24 episodes", 24, unified.size)
        assertEquals(1f, unified.first().number)
        assertEquals(24f, unified.last().number)

        // Episodes 21..24 recovered from provB
        for (i in 20 until 24) {
            val ep = unified[i]
            assertEquals("provB", ep.primarySourceProviderId)
            assertEquals("Source B", ep.primarySourceName)
            assertEquals(EpisodeAvailabilityState.DISCOVERED, ep.availabilityState)
        }
    }

    /**
     * Requirement 3: Multiple providers contributing disjoint episode sets (1..10, 11..20, 21..24)
     * to construct a complete series.
     */
    @Test
    fun testMultipleProvidersDisjointSetsConstruction() {
        val provA = MockProvider("provA", "Source A")
        val provB = MockProvider("provB", "Source B")
        val provC = MockProvider("provC", "Source C")
        val match = AnimeSearchResult("test-anime", "Vinland Saga", "https://example.com/vs")

        val epsA = (1..10).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://sourceA.com/ep/$i", sources = listOf(EpisodeSourceReference("provA", "https://sourceA.com/ep/$i")))
        }
        val epsB = (11..20).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://sourceB.com/ep/$i", sources = listOf(EpisodeSourceReference("provB", "https://sourceB.com/ep/$i")))
        }
        val epsC = (21..24).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://sourceC.com/ep/$i", sources = listOf(EpisodeSourceReference("provC", "https://sourceC.com/ep/$i")))
        }

        val batchA = ProviderEpisodeBatch(provA, match, epsA, 90)
        val batchB = ProviderEpisodeBatch(provB, match, epsB, 85)
        val batchC = ProviderEpisodeBatch(provC, match, epsC, 80)

        val unified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(
            batches = listOf(batchA, batchB, batchC),
            totalEpisodes = 24,
        )

        assertEquals(24, unified.size)
        assertEquals((1..24).map { it.toFloat() }, unified.map { it.number })

        assertEquals("provA", unified[0].primarySourceProviderId)
        assertEquals("provA", unified[9].primarySourceProviderId)
        assertEquals("provB", unified[10].primarySourceProviderId)
        assertEquals("provB", unified[19].primarySourceProviderId)
        assertEquals("provC", unified[20].primarySourceProviderId)
        assertEquals("provC", unified[23].primarySourceProviderId)
    }

    /**
     * Requirement 4: Dynamic recovery loop with untried fallback provider.
     * Primary provider had gaps; recoverMissingEpisodes automatically queries fallback provider
     * and recovers missing episodes into the list.
     */
    @Test
    fun testDynamicRecoveryLoopWithUntriedFallbackProvider() = runBlocking {
        val match = AnimeSearchResult("test-anime", "Chainsaw Man", "https://example.com/csm")

        // Provider A: initial batch with missing episode 3
        val epsA = listOf(1, 2, 4, 5).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://sourceA.com/ep/$i")
        }
        val provA = MockProvider("provA", "Source A")
        val batchA = ProviderEpisodeBatch(provA, match, epsA, 95)

        // Provider B: untried fallback candidate with all 1..5 episodes
        val epsB = (1..5).map { i ->
            AnimeEpisode(number = i.toFloat(), title = "الحلقة $i", url = "https://sourceB.com/ep/$i")
        }
        val provB = MockProvider(
            id = "provB",
            name = "Source B",
            searchResultsToReturn = listOf(match),
            episodesToReturn = epsB,
        )

        val initialUnified = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(listOf(batchA))
        assertEquals(listOf(1f, 2f, 4f, 5f), initialUnified.map { it.number })

        val recoveryResult = EpisodeSourcesManager.recoverMissingEpisodes(
            currentEpisodes = initialUnified,
            existingBatches = listOf(batchA),
            candidateProviders = listOf(provA, provB),
            titles = listOf("Chainsaw Man"),
            totalEpisodes = 5,
        )

        assertEquals(listOf(3), recoveryResult.recoveredEpisodeNumbers)
        assertEquals(5, recoveryResult.episodes.size)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f), recoveryResult.episodes.map { it.number })

        // Check recovered episode 3
        val ep3 = recoveryResult.episodes[2]
        assertEquals(3f, ep3.number)
        assertEquals("provB", ep3.primarySourceProviderId)
    }

    /**
     * Requirement 5: Recovered episode resolves playback independently from its own provider.
     */
    @Test
    fun testRecoveredEpisodeResolvesPlaybackIndependently() = runBlocking {
        val streamServerB = AnimeStreamServer(
            name = "Server B Direct",
            url = "https://cdn.sourceB.com/csm/ep3.mp4",
            isDirectVideo = true,
            quality = "1080p FHD",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )

        val provB = MockProvider(
            id = "provB_playback",
            name = "Source B Playback",
            serversToReturn = listOf(streamServerB),
        )

        EpisodeSourcesManager.registerCustomProvider(provB)

        try {
            val recoveredEp = AnimeEpisode(
                number = 3f,
                title = "الحلقة 3",
                url = "https://sourceB.com/ep/3",
                sources = listOf(
                    EpisodeSourceReference("provB_playback", "https://sourceB.com/ep/3", sourceName = "Source B Playback")
                ),
                availabilityState = EpisodeAvailabilityState.DISCOVERED,
            )

            val playbackResult = EpisodeSourcesManager.resolveStreamServersWithFallback(recoveredEp)

            assertTrue("Playback must succeed for recovered episode", playbackResult.success)
            assertEquals("provB_playback", playbackResult.winningProviderId)
            assertEquals(1, playbackResult.servers.size)
            assertEquals("https://cdn.sourceB.com/csm/ep3.mp4", playbackResult.servers.first().url)
            assertEquals(EpisodeAvailabilityState.PLAYABLE, playbackResult.episode.availabilityState)
        } finally {
            EpisodeSourcesManager.unregisterCustomProvider("provB_playback")
        }
    }
}
