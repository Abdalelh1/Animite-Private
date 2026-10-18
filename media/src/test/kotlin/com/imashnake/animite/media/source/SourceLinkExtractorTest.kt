package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLinkExtractorTest {

    private val testEpisode = AnimeEpisode(
        number = 1f,
        title = "One Piece - Episode 1",
        url = "https://example.com/ep1",
    )

    /**
     * Requirement 1: Episode available in 3 or more qualities across multiple sources.
     * Expected: Qualities are discovered, grouped, and sorted ascending (480p -> 720p -> 1080p).
     */
    @Test
    fun testMultipleQualitiesFromDifferentSourcesSortedAscending() {
        val serverA = AnimeStreamServer(
            name = "سيرفر 1 (480p)",
            url = "https://sourceA.com/ep1_480p.mp4",
            isDirectVideo = true,
            quality = "480p",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )
        val serverB = AnimeStreamServer(
            name = "سيرفر 2 (720p)",
            url = "https://sourceB.com/ep1_720p.mp4",
            isDirectVideo = true,
            quality = "720p",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )
        val serverC = AnimeStreamServer(
            name = "سيرفر 3 (1080p)",
            url = "https://sourceC.com/ep1_1080p.mp4",
            isDirectVideo = true,
            quality = "1080p",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )

        val result = SourceLinkExtractor.aggregateAndGroupQualities(
            rawServers = listOf(serverC, serverA, serverB), // Intentionally unordered
            episode = testEpisode,
            participatingProviders = listOf("sourceA", "sourceB", "sourceC"),
        )

        assertTrue("Aggregation must succeed", result.success)
        assertEquals("Must discover exactly 3 distinct qualities", 3, result.qualityOptions.size)

        // Strict Ascending order verification
        assertEquals("480p SD", result.qualityOptions[0].quality.canonicalName)
        assertEquals("720p HD", result.qualityOptions[1].quality.canonicalName)
        assertEquals("1080p FHD", result.qualityOptions[2].quality.canonicalName)

        // Default best stream should be 1080p FHD
        assertNotNull(result.bestStream)
        assertEquals("1080p FHD", result.bestStream?.quality)
    }

    /**
     * Requirement 2: Same quality available from 2 distinct servers/sources.
     * Expected: Merged into 1 single quality option in the UI list, with the other retained as internal failover fallback.
     */
    @Test
    fun testDuplicateQualityDeduplicationWithFallbackPreserved() {
        val server1 = AnimeStreamServer(
            name = "سيرفر 1 (720p)",
            url = "https://cdn1.com/ep1.mp4",
            isDirectVideo = true,
            quality = "720p HD",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )
        val server2 = AnimeStreamServer(
            name = "سيرفر 2 (720p)",
            url = "https://cdn2.com/ep1.mp4",
            isDirectVideo = true,
            quality = "720p HD",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )
        val server3 = AnimeStreamServer(
            name = "سيرفر 3 (1080p)",
            url = "https://cdn1.com/ep1_1080p.mp4",
            isDirectVideo = true,
            quality = "1080p FHD",
            linkType = StreamLinkType.DIRECT_VIDEO,
        )

        val result = SourceLinkExtractor.aggregateAndGroupQualities(
            rawServers = listOf(server1, server2, server3),
            episode = testEpisode,
        )

        assertTrue(result.success)
        assertEquals("Must deduplicate to 2 quality options", 2, result.qualityOptions.size)

        val q720 = result.qualityOptions.first { it.quality.height == 720 }
        assertEquals("https://cdn1.com/ep1.mp4", q720.primaryServer.url)
        assertEquals(1, q720.fallbackServers.size)
        assertEquals("https://cdn2.com/ep1.mp4", q720.fallbackServers.first().url)
    }

    /**
     * Requirement 3: Full ascending sort test covering 360p, 480p, 720p, 1080p, 4K, and Auto.
     */
    @Test
    fun testAscendingQualitySortingCompleteHierarchy() {
        val servers = listOf(
            AnimeStreamServer(name = "Auto", url = "https://stream.com/master.m3u8", quality = "Auto", isDirectVideo = true),
            AnimeStreamServer(name = "4K", url = "https://stream.com/4k.mp4", quality = "2160p 4K", isDirectVideo = true),
            AnimeStreamServer(name = "1080p", url = "https://stream.com/1080p.mp4", quality = "1080p", isDirectVideo = true),
            AnimeStreamServer(name = "360p", url = "https://stream.com/360p.mp4", quality = "360p", isDirectVideo = true),
            AnimeStreamServer(name = "720p", url = "https://stream.com/720p.mp4", quality = "720p", isDirectVideo = true),
            AnimeStreamServer(name = "480p", url = "https://stream.com/480p.mp4", quality = "480p", isDirectVideo = true),
        )

        val result = SourceLinkExtractor.aggregateAndGroupQualities(
            rawServers = servers,
            episode = testEpisode,
        )

        val qualityNames = result.qualityOptions.map { it.quality.canonicalName }
        assertEquals(
            listOf("360p", "480p SD", "720p HD", "1080p FHD", "4K (2160p)", "تلقائي (Auto)"),
            qualityNames
        )
    }

    /**
     * Requirement 4: Filter out non-video links, web embeds, and ad pages from direct streams.
     */
    @Test
    fun testNonVideoAndAdLinksFilteredOut() {
        val validMp4 = AnimeStreamServer(name = "Direct", url = "https://valid.com/video.mp4", isDirectVideo = true)
        val validHls = AnimeStreamServer(name = "HLS", url = "https://valid.com/video.m3u8", isDirectVideo = true)
        val adLink = AnimeStreamServer(name = "Ad", url = "https://acceptable.a-ads.com/banner", isDirectVideo = false)
        val fileHost = AnimeStreamServer(name = "Download", url = "https://gofile.io/d/xyz", isDirectVideo = false)
        val invalidScheme = AnimeStreamServer(name = "Bad", url = "javascript:void(0)", isDirectVideo = false)

        val result = SourceLinkExtractor.aggregateAndGroupQualities(
            rawServers = listOf(validMp4, validHls, adLink, fileHost, invalidScheme),
            episode = testEpisode,
        )

        assertTrue(result.success)
        assertEquals(2, result.qualityOptions.size)
        assertTrue(result.qualityOptions.any { it.primaryServer.url == validMp4.url })
        assertTrue(result.qualityOptions.any { it.primaryServer.url == validHls.url })
        assertFalse(result.allDirectServers.any { it.url == adLink.url })
        assertFalse(result.allDirectServers.any { it.url == fileHost.url })
    }

    /**
     * Requirement 5: When no direct video streams exist, graceful error handling.
     */
    @Test
    fun testNoDirectStreamsHandledGracefully() {
        val brokenList = listOf(
            AnimeStreamServer(name = "None", url = "invalid_url", isDirectVideo = false)
        )

        val result = SourceLinkExtractor.aggregateAndGroupQualities(
            rawServers = brokenList,
            episode = testEpisode,
        )

        assertFalse(result.success)
        assertTrue(result.qualityOptions.isEmpty())
        assertNotNull(result.errorMessage)
    }

    /**
     * Requirement 6: Concurrent multi-source resolution through EpisodeSourcesManager.
     */
    @Test
    fun testConcurrentMultiSourceResolutionWithFailures() = runBlocking {
        val s480 = AnimeStreamServer("S1", "https://cdn1.com/ep.mp4", isDirectVideo = true, quality = "480p")
        val s1080 = AnimeStreamServer("S2", "https://cdn2.com/ep.mp4", isDirectVideo = true, quality = "1080p")

        val p1 = object : EpisodeSourceProvider {
            override val id: String = "mock_p1"
            override val name: String = "P1"
            override val language: String = "ar"
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun getEpisodes(animeUrl: String) = emptyList<AnimeEpisode>()
            override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
                throw RuntimeException("Network timeout simulation")
            }
        }

        val p2 = object : EpisodeSourceProvider {
            override val id: String = "mock_p2"
            override val name: String = "P2"
            override val language: String = "ar"
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun getEpisodes(animeUrl: String) = emptyList<AnimeEpisode>()
            override suspend fun getStreamServers(episodeUrl: String) = listOf(s480, s1080)
        }

        EpisodeSourcesManager.registerCustomProvider(p1)
        EpisodeSourcesManager.registerCustomProvider(p2)

        try {
            val ep = AnimeEpisode(
                number = 1f,
                sources = listOf(
                    EpisodeSourceReference("mock_p1", "https://p1.com/ep1"),
                    EpisodeSourceReference("mock_p2", "https://p2.com/ep1"),
                )
            )

            val resolved = EpisodeSourcesManager.resolveAllEpisodeQualities(ep)
            assertTrue("Should succeed despite mock_p1 throwing", resolved.success)
            assertEquals("Must discover 2 qualities from mock_p2", 2, resolved.qualityOptions.size)
            assertEquals("480p SD", resolved.qualityOptions[0].quality.canonicalName)
            assertEquals("1080p FHD", resolved.qualityOptions[1].quality.canonicalName)
            assertEquals("1080p FHD", resolved.bestStream?.quality)
        } finally {
            EpisodeSourcesManager.unregisterCustomProvider("mock_p1")
            EpisodeSourcesManager.unregisterCustomProvider("mock_p2")
        }
    }
}
