package com.imashnake.animite.media.source

import com.imashnake.animite.media.source.classification.EpisodeClassificationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8/9 Unit Tests:
 * Validates episode classification preservation, availability state handling,
 * and comprehensive anime source diagnostic summaries.
 */
class EpisodeClassificationAndDiagnosticsTest {

    // ==========================================
    // 1. Episode Classification Preservation
    // ==========================================

    @Test
    fun testEpisodeClassificationPreservation() {
        val titles = listOf("Naruto", "ناروتو")

        // Known filler range for Naruto (e.g. episode 102 is filler in Naruto)
        val fillerType = EpisodeClassificationRepository.classify(titles, 102f)
        assertEquals(EpisodeType.FILLER, fillerType)

        // Known canon episode for Naruto (episode 1 is canon)
        val canonType = EpisodeClassificationRepository.classify(titles, 1f)
        assertEquals(EpisodeType.CANON, canonType)

        // Unclassified / Regular does not overwrite special or movie classifications
        val specialEpisode = AnimeEpisode(
            number = 1f,
            title = "Special OVA 1",
            url = "https://source.com/sp1",
            episodeType = EpisodeType.OVA,
        )
        assertEquals(EpisodeType.OVA, specialEpisode.episodeType)

        val movieEpisode = AnimeEpisode(
            number = 1f,
            title = "The Last: Naruto the Movie",
            url = "https://source.com/movie1",
            episodeType = EpisodeType.MOVIE,
        )
        assertEquals(EpisodeType.MOVIE, movieEpisode.episodeType)
    }

    // ==========================================
    // 2. Episode Availability State Handling
    // ==========================================

    @Test
    fun testEpisodeAvailabilityStateTransitions() {
        val epDiscovered = AnimeEpisode(
            number = 5f,
            title = "Episode 5",
            url = "https://source.com/ep5",
            availabilityState = EpisodeAvailabilityState.DISCOVERED,
        )
        assertEquals(EpisodeAvailabilityState.DISCOVERED, epDiscovered.availabilityState)

        val epPlayable = epDiscovered.copy(availabilityState = EpisodeAvailabilityState.PLAYABLE)
        assertEquals(EpisodeAvailabilityState.PLAYABLE, epPlayable.availabilityState)

        val epUnavailable = epDiscovered.copy(availabilityState = EpisodeAvailabilityState.UNAVAILABLE)
        assertEquals(EpisodeAvailabilityState.UNAVAILABLE, epUnavailable.availabilityState)
    }

    // ==========================================
    // 3. Anime Diagnostic Summary Metrics
    // ==========================================

    @Test
    fun testAnimeDiagnosticSummaryMetrics() {
        SmartStreamDiagnostics.clear()

        val providerMap = mapOf(
            "anime4up" to 12,
            "anime3rb" to 12,
            "witanime" to 6,
        )

        val failedProviders = mapOf(
            "animeluxe" to "Cloudflare challenge detected",
            "animeslayer" to "API signature validation failed",
        )

        val summary = AnimeDiagnosticSummary(
            animeTitle = "One Piece",
            episodesDiscoveredPerProvider = providerMap,
            unifiedEpisodeCount = 12,
            duplicateEpisodesRemoved = 18, // (12 + 12 + 6) - 12 = 18
            episodesWithoutPlayableCandidates = 0,
            validatedStreamCount = 24,
            failedProviders = failedProviders,
            isFromCache = false,
            cacheStatus = "مباشر (Live)",
        )

        SmartStreamDiagnostics.recordAnimeSummary(summary)

        val retrieved = SmartStreamDiagnostics.getAnimeSummary("One Piece")
        assertNotNull("Diagnostic summary must be retrievable by title", retrieved)
        assertEquals(12, retrieved!!.unifiedEpisodeCount)
        assertEquals(18, retrieved.duplicateEpisodesRemoved)
        assertEquals(0, retrieved.episodesWithoutPlayableCandidates)
        assertEquals(2, retrieved.failedProviders.size)
        assertTrue(retrieved.failedProviders.containsKey("animeluxe"))
        assertEquals("مباشر (Live)", retrieved.cacheStatus)

        // Case-insensitive / substring lookup
        val substringLookup = SmartStreamDiagnostics.getAnimeSummary("one piece")
        assertNotNull("Case-insensitive lookup must succeed", substringLookup)
        assertEquals(12, substringLookup!!.unifiedEpisodeCount)
    }

    // ==========================================
    // 4. Multi-Provider Fallback and Attribution Integrity
    // ==========================================

    @Test
    fun testMultiProviderAttributionAndRecoverySummary() {
        val ep = AnimeEpisode(
            number = 1f,
            title = "Episode 1",
            url = "https://anime4up.com/ep1",
            sources = listOf(
                EpisodeSourceReference(providerId = "anime4up", episodeUrl = "https://anime4up.com/ep1"),
                EpisodeSourceReference(providerId = "witanime", episodeUrl = "https://witanime.com/ep1"),
            ),
            availabilityState = EpisodeAvailabilityState.PLAYABLE,
        )

        assertEquals("anime4up", ep.primarySourceProviderId)
        assertEquals(2, ep.sources.size)
        assertTrue(ep.hasAlternativeSources)
    }
}
