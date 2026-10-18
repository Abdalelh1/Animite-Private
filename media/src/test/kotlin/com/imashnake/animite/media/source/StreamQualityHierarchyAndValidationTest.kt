package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7: Stream Quality Hierarchy & Validation Engine Test Suite.
 * Validates strict ascending quality rankings, redundant stream grouping,
 * same-quality failover progression, and pre-validation dead stream filtering.
 */
class StreamQualityHierarchyAndValidationTest {

    // ==========================================
    // 1. Strict Ascending Quality Hierarchy
    // ==========================================

    @Test
    fun testQualityHierarchyStrictAscendingSorting() {
        val ep = AnimeEpisode(number = 1f, title = "Episode 1", url = "https://example.com/1")

        val servers = listOf(
            AnimeStreamServer(name = "Server 1080", url = "https://cdn.com/1080.mp4", quality = "1080p FHD", isDirectVideo = true),
            AnimeStreamServer(name = "Server 360", url = "https://cdn.com/360.mp4", quality = "360p", isDirectVideo = true),
            AnimeStreamServer(name = "Server 2160", url = "https://cdn.com/4k.mp4", quality = "4K (2160p)", isDirectVideo = true),
            AnimeStreamServer(name = "Server Auto", url = "https://cdn.com/master.m3u8", quality = "تلقائي (Auto)", isDirectVideo = true),
            AnimeStreamServer(name = "Server 480", url = "https://cdn.com/480.mp4", quality = "480p SD", isDirectVideo = true),
            AnimeStreamServer(name = "Server 720", url = "https://cdn.com/720.mp4", quality = "720p HD", isDirectVideo = true),
            AnimeStreamServer(name = "Server 1440", url = "https://cdn.com/1440.mp4", quality = "1440p 2K", isDirectVideo = true),
        )

        val aggregated = SourceLinkExtractor.aggregateAndGroupQualities(servers, ep)
        assertTrue(aggregated.success)

        val qualities = aggregated.qualityOptions.map { it.quality }
        assertEquals(7, qualities.size)

        // Must be strictly ascending in height: 360 -> 480 -> 720 -> 1080 -> 1440 -> 2160 -> 9999 (Auto)
        val expectedHeights = listOf(360, 480, 720, 1080, 1440, 2160, 9999)
        assertEquals(expectedHeights, qualities.map { it.height })

        assertEquals("360p", qualities[0].canonicalName)
        assertEquals("480p SD", qualities[1].canonicalName)
        assertEquals("720p HD", qualities[2].canonicalName)
        assertEquals("1080p FHD", qualities[3].canonicalName)
        assertEquals("1440p 2K", qualities[4].canonicalName)
        assertEquals("4K (2160p)", qualities[5].canonicalName)
        assertEquals("تلقائي (Auto)", qualities[6].canonicalName)
        assertTrue(qualities[6].isAdaptive)
    }

    // ==========================================
    // 2. Redundant Stream Grouping under Single Quality Option
    // ==========================================

    @Test
    fun testRedundantStreamGroupingUnderSingleQuality() {
        val ep = AnimeEpisode(number = 1f, title = "Episode 1", url = "https://example.com/1")

        // 3 providers offering 1080p, plus 2 providers offering 720p
        val servers = listOf(
            AnimeStreamServer(name = "Prov1 FHD", url = "https://prov1.com/1080.mp4", quality = "1080p", isDirectVideo = true),
            AnimeStreamServer(name = "Prov2 FHD", url = "https://prov2.com/1080.mp4", quality = "1080p", isDirectVideo = true),
            AnimeStreamServer(name = "Prov3 FHD", url = "https://prov3.com/1080.mp4", quality = "1080p", isDirectVideo = true),
            AnimeStreamServer(name = "Prov1 HD", url = "https://prov1.com/720.mp4", quality = "720p", isDirectVideo = true),
            AnimeStreamServer(name = "Prov2 HD", url = "https://prov2.com/720.mp4", quality = "720p", isDirectVideo = true),
        )

        val aggregated = SourceLinkExtractor.aggregateAndGroupQualities(servers, ep)
        assertTrue(aggregated.success)

        // Must have exactly 2 quality options in the UI (720p and 1080p), not 5!
        assertEquals("Must group redundant servers into 2 quality options", 2, aggregated.qualityOptions.size)

        val opt720 = aggregated.qualityOptions.first { it.quality.height == 720 }
        assertEquals("https://prov1.com/720.mp4", opt720.primaryServer.url)
        assertEquals(1, opt720.fallbackServers.size)
        assertEquals("https://prov2.com/720.mp4", opt720.fallbackServers[0].url)

        val opt1080 = aggregated.qualityOptions.first { it.quality.height == 1080 }
        assertEquals("https://prov1.com/1080.mp4", opt1080.primaryServer.url)
        assertEquals(2, opt1080.fallbackServers.size)
        assertEquals("https://prov2.com/1080.mp4", opt1080.fallbackServers[0].url)
        assertEquals("https://prov3.com/1080.mp4", opt1080.fallbackServers[1].url)
    }

    // ==========================================
    // 3. Same-Quality Failover Progression
    // ==========================================

    @Test
    fun testSameQualityFailoverProgression() {
        // Option with 1 primary and 2 fallback servers for 1080p
        val opt1080 = UnifiedQualityOption(
            quality = QualityRank("1080p FHD", 1080),
            primaryServer = AnimeStreamServer(name = "Server A", url = "https://prov1.com/video.mp4", quality = "1080p FHD", isDirectVideo = true),
            fallbackServers = listOf(
                AnimeStreamServer(name = "Server B", url = "https://prov2.com/video.mp4", quality = "1080p FHD", isDirectVideo = true),
                AnimeStreamServer(name = "Server C", url = "https://prov3.com/video.mp4", quality = "1080p FHD", isDirectVideo = true),
            )
        )

        val opt720 = UnifiedQualityOption(
            quality = QualityRank("720p HD", 720),
            primaryServer = AnimeStreamServer(name = "Server 720", url = "https://prov1.com/video720.mp4", quality = "720p HD", isDirectVideo = true),
        )

        val qualityOptions = listOf(opt720, opt1080)
        val failedUrls = mutableSetOf<String>()

        // 1. Initial active is Server A
        var activeServer = opt1080.primaryServer
        assertEquals("https://prov1.com/video.mp4", activeServer.url)

        // Simulate Server A failure
        failedUrls.add(activeServer.url)

        // Try same quality fallback
        val sameQualityFallback1 = opt1080.fallbackServers.firstOrNull { it.url !in failedUrls }
        assertNotNull("Must failover to Server B of same quality", sameQualityFallback1)
        assertEquals("https://prov2.com/video.mp4", sameQualityFallback1!!.url)

        // 2. Active is now Server B
        activeServer = sameQualityFallback1
        failedUrls.add(activeServer.url)

        // Simulate Server B failure -> Try same quality fallback again
        val sameQualityFallback2 = opt1080.fallbackServers.firstOrNull { it.url !in failedUrls }
        assertNotNull("Must failover to Server C of same quality", sameQualityFallback2)
        assertEquals("https://prov3.com/video.mp4", sameQualityFallback2!!.url)

        // 3. Active is now Server C
        activeServer = sameQualityFallback2
        failedUrls.add(activeServer.url)

        // Simulate Server C failure -> All 1080p servers exhausted!
        val sameQualityFallback3 = opt1080.fallbackServers.firstOrNull { it.url !in failedUrls }
        assertEquals("No more 1080p servers remain", null, sameQualityFallback3)

        // 4. Must now fall back to adjacent 720p quality
        val otherOptions = qualityOptions.filter { it.quality.height != 1080 }
        val nearest = otherOptions
            .mapNotNull { opt ->
                val valid = if (opt.primaryServer.url !in failedUrls) opt.primaryServer
                else opt.fallbackServers.firstOrNull { it.url !in failedUrls }
                if (valid != null) Pair(opt, valid) else null
            }
            .minByOrNull { kotlin.math.abs(it.first.quality.height - 1080) }

        assertNotNull(nearest)
        assertEquals(720, nearest!!.first.quality.height)
        assertEquals("https://prov1.com/video720.mp4", nearest.second.url)
    }

    // ==========================================
    // 4. Stream Pre-Validation Logic
    // ==========================================

    @Test
    fun testPreValidationPromotesHealthyFallbackWhenPrimaryDead() = runBlocking {
        val deadPrimary = AnimeStreamServer(
            name = "Dead Primary",
            url = "https://invalid-dead-domain-9999.xyz/video.mp4",
            quality = "1080p FHD",
            isDirectVideo = true,
        )

        val workingFallback = AnimeStreamServer(
            name = "Working Fallback",
            url = "https://vid3rb.com/video/1080p.mp4",
            quality = "1080p FHD",
            isDirectVideo = true,
        )

        val option = UnifiedQualityOption(
            quality = QualityRank("1080p FHD", 1080),
            primaryServer = deadPrimary,
            fallbackServers = listOf(workingFallback),
        )

        // Probe on invalid domain returns false
        val isPrimaryAlive = StreamPreValidator.probeStream(deadPrimary, timeoutMs = 500L)
        assertFalse(isPrimaryAlive)
    }
}
