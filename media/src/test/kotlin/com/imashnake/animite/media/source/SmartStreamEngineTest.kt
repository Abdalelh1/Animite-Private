package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmartStreamEngineTest {

    @Before
    fun setUp() {
        SmartStreamCache.clearAll()
        SmartStreamDiagnostics.clear()
    }

    @After
    fun tearDown() {
        SmartStreamCache.clearAll()
        SmartStreamDiagnostics.clear()
    }

    @Test
    fun testStreamCachingAndTTLInvalidation() {
        val title = "Attack on Titan"
        val epNum = 5f
        val mockEpisode = AnimeEpisode(number = epNum, title = title, url = "https://example.com/ep5")

        val result = AggregatedPlaybackResult(
            success = true,
            episode = mockEpisode,
            bestStream = AnimeStreamServer("Server 1", "https://cdn.example.com/video.mp4", isDirectVideo = true),
            allDirectServers = listOf(AnimeStreamServer("Server 1", "https://cdn.example.com/video.mp4", isDirectVideo = true)),
        )

        // Store in cache
        SmartStreamCache.putStreamResult(title, epNum, result)

        // Verify cache hit
        val cached = SmartStreamCache.getValidStreamResult(title, epNum)
        assertNotNull("Should retrieve valid cached result", cached)
        assertEquals(result.bestStream?.url, cached?.bestStream?.url)

        // Invalidate cache
        SmartStreamCache.invalidateStream(title, epNum)
        val afterInvalidation = SmartStreamCache.getValidStreamResult(title, epNum)
        assertNull("Should be null after invalidation", afterInvalidation)
    }

    @Test
    fun testServerHealthPenaltiesAndRestoration() {
        val failingHost = "https://badcdn.com/stream.mp4"

        assertFalse("Server initially healthy", SmartStreamCache.isServerDeprioritized(failingHost))

        // Record 2 failures
        SmartStreamCache.recordServerFailure(failingHost, "HTTP 500")
        SmartStreamCache.recordServerFailure(failingHost, "HTTP 502")
        assertFalse("Server still not deprioritized after 2 failures", SmartStreamCache.isServerDeprioritized(failingHost))

        // 3rd failure deprioritizes server
        SmartStreamCache.recordServerFailure(failingHost, "Connection Timeout")
        assertTrue("Server must be deprioritized after 3 consecutive failures", SmartStreamCache.isServerDeprioritized(failingHost))

        // Successful playback restores server health
        SmartStreamCache.recordServerSuccess(failingHost)
        assertFalse("Server must be restored to healthy status after successful playback", SmartStreamCache.isServerDeprioritized(failingHost))
    }

    @Test
    fun testAdaptiveFallbackSwappingForDeprioritizedServers() = runBlocking {
        val badUrl = "https://failing-cdn.com/1080p.mp4"
        val goodUrl = "https://healthy-cdn.com/1080p.mp4"

        // Mark badUrl as deprioritized
        SmartStreamCache.recordServerFailure(badUrl, "403 Forbidden")
        SmartStreamCache.recordServerFailure(badUrl, "403 Forbidden")
        SmartStreamCache.recordServerFailure(badUrl, "403 Forbidden")
        assertTrue(SmartStreamCache.isServerDeprioritized(badUrl))

        val sBad = AnimeStreamServer(name = "Bad Server", url = badUrl, quality = "1080p", isDirectVideo = true)
        val sGood = AnimeStreamServer(name = "Good Server", url = goodUrl, quality = "1080p", isDirectVideo = true)

        val provider = object : EpisodeSourceProvider {
            override val id: String = "mock_adaptive"
            override val name: String = "Mock Adaptive"
            override val language: String = "ar"
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun getEpisodes(animeUrl: String) = emptyList<AnimeEpisode>()
            override suspend fun getStreamServers(episodeUrl: String) = listOf(sBad, sGood)
        }

        EpisodeSourcesManager.registerCustomProvider(provider)
        try {
            val ep = AnimeEpisode(
                number = 1f,
                sources = listOf(EpisodeSourceReference("mock_adaptive", "https://adaptive.com/ep1"))
            )

            val resolved = SmartStreamEngine.resolveEpisodeStreams(ep, animeTitle = "Naruto", forceRefresh = true)
            assertTrue(resolved.success)

            val q1080 = resolved.qualityOptions.first { it.quality.height == 1080 }
            // The healthy server must be swapped in as primary!
            assertEquals("Healthy server must be primary", goodUrl, q1080.primaryServer.url)
            assertTrue("Failing server moved to fallback", q1080.fallbackServers.any { it.url == badUrl })
        } finally {
            EpisodeSourcesManager.unregisterCustomProvider("mock_adaptive")
        }
    }

    @Test
    fun testDiagnosticReportGeneration() = runBlocking {
        val s720 = AnimeStreamServer(name = "Direct 720", url = "https://cdn.com/720.mp4", quality = "720p", isDirectVideo = true)
        val s1080 = AnimeStreamServer(name = "Direct 1080", url = "https://cdn.com/1080.mp4", quality = "1080p", isDirectVideo = true)

        val provider = object : EpisodeSourceProvider {
            override val id: String = "mock_diag"
            override val name: String = "Mock Diag Provider"
            override val language: String = "ar"
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun getEpisodes(animeUrl: String) = emptyList<AnimeEpisode>()
            override suspend fun getStreamServers(episodeUrl: String) = listOf(s720, s1080)
        }

        EpisodeSourcesManager.registerCustomProvider(provider)
        try {
            val ep = AnimeEpisode(
                number = 12f,
                sources = listOf(EpisodeSourceReference("mock_diag", "https://diag.com/ep12"))
            )

            val resolved = SmartStreamEngine.resolveEpisodeStreams(ep, animeTitle = "Bleach", forceRefresh = true)
            assertTrue(resolved.success)

            val report = SmartStreamDiagnostics.getReport(12f)
            assertNotNull("Diagnostic report must be recorded", report)
            assertEquals(12f, report!!.episodeNumber)
            assertEquals(1, report.queriedSourcesCount)
            assertEquals(1, report.respondingSourcesCount)
            assertEquals(2, report.totalDiscoveredServers)
            assertEquals(2, report.validDirectStreamsCount)
            assertEquals(listOf("720p HD", "1080p FHD"), report.discoveredQualities)
            assertEquals("https://cdn.com/1080.mp4", report.winningStreamUrl)

            // Test failover history tracking
            SmartStreamDiagnostics.addFailoverEvent(12f, "Test Failover Event 1")
            assertEquals(1, report.failoverHistory.size)
            assertEquals("Test Failover Event 1", report.failoverHistory.first())
        } finally {
            EpisodeSourcesManager.unregisterCustomProvider("mock_diag")
        }
    }

    @Test
    fun testMediaContainerTypeDetection() {
        assertEquals(MediaContainerType.HLS, SourceLinkExtractor.detectMediaType("https://video.com/master.m3u8"))
        assertEquals(MediaContainerType.DASH, SourceLinkExtractor.detectMediaType("https://video.com/manifest.mpd"))
        assertEquals(MediaContainerType.MP4, SourceLinkExtractor.detectMediaType("https://video.com/file.mp4"))
        assertEquals(MediaContainerType.EMBED, SourceLinkExtractor.detectMediaType("https://video.com/embed/player"))
        assertEquals(MediaContainerType.UNKNOWN, SourceLinkExtractor.detectMediaType("https://video.com/watch?id=123"))
    }
}
