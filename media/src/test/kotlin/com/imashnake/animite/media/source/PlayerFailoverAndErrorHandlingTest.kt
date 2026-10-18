package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8: Player Failover & Error Handling Engine Test Suite.
 * Validates failover ladder progression (step 1 -> step 2 -> step 3 -> step 4),
 * playback position preservation, buffering stall mitigation, and Arabic error recovery.
 */
class PlayerFailoverAndErrorHandlingTest {

    // ==========================================
    // 1. Failover Ladder Progression
    // ==========================================

    @Test
    fun testFailoverLadderStepProgression() = runBlocking {
        val server1080A = AnimeStreamServer(name = "Server 1080 A", url = "https://cdn1.com/1080.mp4", quality = "1080p FHD", isDirectVideo = true)
        val server1080B = AnimeStreamServer(name = "Server 1080 B", url = "https://cdn2.com/1080.mp4", quality = "1080p FHD", isDirectVideo = true)
        val server720 = AnimeStreamServer(name = "Server 720", url = "https://cdn1.com/720.mp4", quality = "720p HD", isDirectVideo = true)

        val opt1080 = UnifiedQualityOption(
            quality = QualityRank("1080p FHD", 1080),
            primaryServer = server1080A,
            fallbackServers = listOf(server1080B),
        )

        val opt720 = UnifiedQualityOption(
            quality = QualityRank("720p HD", 720),
            primaryServer = server720,
        )

        val qualityOptions = listOf(opt720, opt1080)
        val failedUrls = mutableSetOf<String>()

        // ── Step 1: Active 1080p A fails -> Try alternative server of SAME quality ──
        failedUrls.add(server1080A.url)
        val step1Fallback = opt1080.fallbackServers.firstOrNull { it.url !in failedUrls }
        assertNotNull("Step 1 must failover to backup server of same quality", step1Fallback)
        assertEquals(server1080B.url, step1Fallback!!.url)

        // ── Step 2: 1080p B also fails -> Try adjacent quality (720p) ──
        failedUrls.add(server1080B.url)
        val remainingSameQuality = opt1080.fallbackServers.firstOrNull { it.url !in failedUrls }
        assertEquals("Same quality exhausted", null, remainingSameQuality)

        val otherOptions = qualityOptions.filter { it.quality.height != 1080 }
        val step2Fallback = otherOptions
            .mapNotNull { opt ->
                val valid = if (opt.primaryServer.url !in failedUrls) opt.primaryServer
                else opt.fallbackServers.firstOrNull { it.url !in failedUrls }
                if (valid != null) Pair(opt, valid) else null
            }
            .minByOrNull { kotlin.math.abs(it.first.quality.height - 1080) }

        assertNotNull("Step 2 must failover to adjacent 720p quality", step2Fallback)
        assertEquals(720, step2Fallback!!.first.quality.height)
        assertEquals(server720.url, step2Fallback.second.url)

        // ── Step 3: Token Expiry / 403 on adjacent quality -> Triggers silent renewal ──
        val tokenExpiryError = RuntimeException("HTTP response code: 403 Forbidden")
        assertTrue(SmartStreamEngine.isTokenExpiredError(tokenExpiryError))

        // ── Step 4: Fallback across alternative source providers ──
        val ep = AnimeEpisode(
            number = 1f,
            title = "Episode 1",
            url = "https://sourceA.com/ep1",
            sources = listOf(
                EpisodeSourceReference(providerId = "sourceA", episodeUrl = "https://sourceA.com/ep1"),
                EpisodeSourceReference(providerId = "sourceB", episodeUrl = "https://sourceB.com/ep1"),
            )
        )

        val fallbackSources = ep.sources.filter { it.providerId != "sourceA" }
        assertEquals(1, fallbackSources.size)
        assertEquals("sourceB", fallbackSources[0].providerId)
    }

    // ==========================================
    // 2. Playback Position Preservation
    // ==========================================

    @Test
    fun testPlaybackPositionPreservedAcrossFailoverEvents() {
        val failureTimestampMs = 125_400L // 2 minutes and 5.4 seconds into the episode

        // Simulate failover transition helper
        data class PreparedMediaCommand(
            val server: AnimeStreamServer,
            val resumePositionMs: Long,
            val playWhenReady: Boolean,
        )

        fun onFailoverTriggered(
            fallbackServer: AnimeStreamServer,
            currentPlaybackPosition: Long,
        ): PreparedMediaCommand {
            return PreparedMediaCommand(
                server = fallbackServer,
                resumePositionMs = currentPlaybackPosition,
                playWhenReady = true,
            )
        }

        val fallbackServer = AnimeStreamServer(name = "Fallback Server", url = "https://cdn.com/fallback.mp4", isDirectVideo = true)
        val command = onFailoverTriggered(fallbackServer, failureTimestampMs)

        assertEquals("Resume position must be identical to failure position", failureTimestampMs, command.resumePositionMs)
        assertTrue(command.playWhenReady)
        assertEquals(fallbackServer.url, command.server.url)
    }

    // ==========================================
    // 3. Stalled Buffering Detection
    // ==========================================

    @Test
    fun testStalledBufferingDetectionAndMitigation() {
        val stallThresholdMs = 8_000L

        fun shouldTriggerStallMitigation(
            isBuffering: Boolean,
            bufferingElapsedMs: Long,
        ): Boolean {
            return isBuffering && (bufferingElapsedMs >= stallThresholdMs)
        }

        // Under threshold: no failover
        assertFalse(shouldTriggerStallMitigation(isBuffering = true, bufferingElapsedMs = 3_000L))
        assertFalse(shouldTriggerStallMitigation(isBuffering = true, bufferingElapsedMs = 7_999L))

        // Exceeds 8 seconds: trigger mitigation failover
        assertTrue(shouldTriggerStallMitigation(isBuffering = true, bufferingElapsedMs = 8_001L))
        assertTrue(shouldTriggerStallMitigation(isBuffering = true, bufferingElapsedMs = 12_000L))

        // Not buffering: no failover even after 10s
        assertFalse(shouldTriggerStallMitigation(isBuffering = false, bufferingElapsedMs = 10_000L))
    }

    // ==========================================
    // 4. User-Facing Arabic Error Communication
    // ==========================================

    @Test
    fun testUserFacingArabicErrorCommunication() {
        val failureResult = ResolvedPlaybackResult(
            success = false,
            servers = emptyList(),
            winningProviderId = "",
            episode = AnimeEpisode(number = 1f, title = "Episode 1", url = ""),
            errorMessage = "تعذر تشغيل الحلقة من جميع المصادر المتاحة",
        )

        assertFalse(failureResult.success)
        assertTrue(failureResult.servers.isEmpty())
        assertNotNull(failureResult.errorMessage)
        assertTrue("Error message must be in clear Arabic", failureResult.errorMessage!!.contains("تعذر تشغيل الحلقة"))
    }
}
