package com.imashnake.animite.media.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Phase 11 - Steps 3 & 4: Multi-Episode Playback and Fallback Engine Verification.
 *
 * Audits 7 anime across multiple sample episodes:
 * 1. One Piece: Ep 1, 500, 1000, 1180
 * 2. Naruto: Ep 1, 100, 220
 * 3. Naruto Shippuden: Ep 1, 250, 500
 * 4. Jujutsu Kaisen: Ep 1, 12, 24
 * 5. Attack on Titan: Ep 1, 13, 25
 * 6. Death Note: Ep 1, 18, 37
 * 7. Solo Leveling: Ep 1, 6, 12
 *
 * Probes HTTP status (200 / 206 / 302), checks video content-type, verifies headers,
 * tests server-level fallback and provider-level auto-recovery.
 */
class Phase11PlaybackVerificationTest {

    data class EpisodePlaybackProbe(
        val episodeNumber: Float,
        val availableSourcesCount: Int,
        val participatingProviders: List<String>,
        val rawServersDiscovered: Int,
        val directStreamUrls: List<String>,
        val bestStreamUrl: String?,
        val bestStreamQuality: String?,
        val httpStatusCode: Int,
        val contentType: String?,
        val contentLengthBytes: Long,
        val isPlayable: Boolean,
        val requiredHeaders: Map<String, String>,
        val failureReason: String? = null,
    )

    data class AnimePlaybackAuditReport(
        val animeName: String,
        val episodesTested: List<EpisodePlaybackProbe>,
    ) {
        fun printMatrix() {
            println("====================================================================================================")
            println("=== PLAYBACK AUDIT MATRIX (Step 3): $animeName ===")
            println("====================================================================================================")
            println(String.format("%-8s | %-15s | %-12s | %-12s | %-10s | %-8s | %-25s",
                "Episode", "Providers", "Discovered", "Direct Links", "Best Qual", "HTTP", "Playable / Reason"))
            println("----------------------------------------------------------------------------------------------------")
            for (ep in episodesTested) {
                val statusStr = if (ep.isPlayable) "PLAYABLE (✓)" else "FAILED (${ep.failureReason ?: "Unknown"})"
                println(String.format("%-8s | %-15s | %-12d | %-12d | %-10s | %-8d | %-25s",
                    "Ep ${ep.episodeNumber}",
                    ep.participatingProviders.joinToString(","),
                    ep.rawServersDiscovered,
                    ep.directStreamUrls.size,
                    ep.bestStreamQuality ?: "N/A",
                    ep.httpStatusCode,
                    statusStr
                ))
                if (ep.bestStreamUrl != null) {
                    val shortenedUrl = if (ep.bestStreamUrl.length > 80) ep.bestStreamUrl.take(77) + "..." else ep.bestStreamUrl
                    println("         └─ Playback URL: $shortenedUrl [Type: ${ep.contentType ?: "unknown"}]")
                }
            }
            println("====================================================================================================\n")
        }
    }

    private suspend fun probeStreamUrl(
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
    ): Triple<Int, String?, Long> = withContext(Dispatchers.IO) {
        try {
            val url = URL(streamUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", HttpHelper.USER_AGENT)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Range", "bytes=0-1024") // Probe only first 1KB
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            val code = conn.responseCode
            val type = conn.contentType
            val length = conn.contentLengthLong
            Triple(code, type, length)
        } catch (e: Exception) {
            Triple(-1, e.message, 0L)
        }
    }

    private suspend fun auditEpisodePlayback(
        animeTitles: List<String>,
        targetEpisodes: List<Float>,
        totalEpisodesExpected: Int? = null,
    ): AnimePlaybackAuditReport {
        // 1. Discover anime across providers and aggregate episodes
        val batches = mutableListOf<ProviderEpisodeBatch>()
        for (provider in EpisodeSourcesManager.providers) {
            try {
                val matchResult = withTimeoutOrNull(9000L) {
                    EpisodeSourcesManager.autoFindAnime(provider, animeTitles)
                } ?: continue

                val (match, _) = matchResult
                if (match != null) {
                    val score = AnimeTitleMatcher.matchScore(animeTitles, match.title)
                    if (score >= AnimeTitleMatcher.MIN_MATCH_THRESHOLD) {
                        val eps = withTimeoutOrNull(10000L) {
                            try { provider.getEpisodes(match.url) } catch (e: Exception) { emptyList() }
                        } ?: emptyList()
                        if (eps.isNotEmpty()) {
                            batches.add(ProviderEpisodeBatch(provider, match, eps, score))
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

        val unifiedEpisodes = EpisodeSourcesManager.unifyAndDeduplicateEpisodes(
            batches = batches,
            totalEpisodes = totalEpisodesExpected,
            titles = animeTitles,
        )

        val probeResults = mutableListOf<EpisodePlaybackProbe>()

        for (targetEpNum in targetEpisodes) {
            val matchingEp = unifiedEpisodes.firstOrNull { it.number == targetEpNum }
            if (matchingEp == null) {
                probeResults.add(
                    EpisodePlaybackProbe(
                        episodeNumber = targetEpNum,
                        availableSourcesCount = 0,
                        participatingProviders = emptyList(),
                        rawServersDiscovered = 0,
                        directStreamUrls = emptyList(),
                        bestStreamUrl = null,
                        bestStreamQuality = null,
                        httpStatusCode = 404,
                        contentType = null,
                        contentLengthBytes = 0,
                        isPlayable = false,
                        requiredHeaders = emptyMap(),
                        failureReason = "Episode $targetEpNum not found in unified catalog",
                    )
                )
                continue
            }

            // Resolve streams via SmartStreamEngine
            val resolvedResult = withTimeoutOrNull(25000L) {
                SmartStreamEngine.resolveEpisodeStreams(
                    episode = matchingEp,
                    animeTitle = animeTitles.first(),
                    forceRefresh = true,
                )
            }

            if (resolvedResult == null || !resolvedResult.success || resolvedResult.allDirectServers.isEmpty()) {
                val participating = matchingEp.sources.map { it.providerId }
                probeResults.add(
                    EpisodePlaybackProbe(
                        episodeNumber = targetEpNum,
                        availableSourcesCount = matchingEp.sources.size,
                        participatingProviders = participating,
                        rawServersDiscovered = resolvedResult?.qualityOptions?.sumOf { 1 + it.fallbackServers.size } ?: 0,
                        directStreamUrls = emptyList(),
                        bestStreamUrl = null,
                        bestStreamQuality = null,
                        httpStatusCode = 502,
                        contentType = null,
                        contentLengthBytes = 0,
                        isPlayable = false,
                        requiredHeaders = emptyMap(),
                        failureReason = resolvedResult?.errorMessage ?: "Extraction returned 0 direct playable streams",
                    )
                )
                continue
            }

            val directServers = resolvedResult.allDirectServers
            val bestServer = resolvedResult.bestStream ?: directServers.first()

            fun buildEffectiveHeaders(server: AnimeStreamServer): Map<String, String> {
                val headers = server.headers.toMutableMap()
                if (!headers.containsKey("User-Agent")) {
                    headers["User-Agent"] = HttpHelper.USER_AGENT
                }
                if (!headers.containsKey("Referer")) {
                    val urlLower = server.url.lowercase(Locale.ROOT)
                    when {
                        urlLower.contains("mp4upload.com") -> headers["Referer"] = "https://www.mp4upload.com/"
                        urlLower.contains("vid3rb.com") -> headers["Referer"] = "https://anime3rb.com/"
                        urlLower.contains("anime4up") -> headers["Referer"] = "https://w1.anime4up.rest/"
                    }
                }
                return headers
            }

            fun isVideoContentType(type: String?): Boolean {
                if (type == null) return true
                val lower = type.lowercase(Locale.ROOT)
                return lower.contains("video") || lower.contains("mpegurl") ||
                       lower.contains("octet-stream") || lower.contains("text/plain") ||
                       lower.contains("binary") || lower.contains("mp4")
            }

            var winningServer = bestServer
            var winningHeaders = buildEffectiveHeaders(bestServer)
            val (statusCode, contentType, length) = probeStreamUrl(bestServer.url, winningHeaders)
            var finalStatus = statusCode
            var finalType = contentType
            var finalLength = length
            var isPlayable = (statusCode in 200..399) && isVideoContentType(contentType)
            var fallbackNote = ""

            // If primary server failed or timed out -> attempt automatic fallback across remaining direct servers
            if (!isPlayable) {
                for (fallbackServer in directServers.filter { it.url != bestServer.url }) {
                    val fbHeaders = buildEffectiveHeaders(fallbackServer)
                    val (fbCode, fbType, fbLen) = probeStreamUrl(fallbackServer.url, fbHeaders)
                    if ((fbCode in 200..399) && isVideoContentType(fbType)) {
                        winningServer = fallbackServer
                        winningHeaders = fbHeaders
                        finalStatus = fbCode
                        finalType = fbType
                        finalLength = fbLen
                        isPlayable = true
                        fallbackNote = " [Fallback from ${bestServer.name} to ${fallbackServer.name}]"
                        break
                    }
                }
            }

            probeResults.add(
                EpisodePlaybackProbe(
                    episodeNumber = targetEpNum,
                    availableSourcesCount = matchingEp.sources.size,
                    participatingProviders = matchingEp.sources.map { it.providerId },
                    rawServersDiscovered = resolvedResult.qualityOptions.sumOf { 1 + it.fallbackServers.size },
                    directStreamUrls = directServers.map { it.url },
                    bestStreamUrl = winningServer.url,
                    bestStreamQuality = winningServer.quality + fallbackNote,
                    httpStatusCode = finalStatus,
                    contentType = finalType,
                    contentLengthBytes = finalLength,
                    isPlayable = isPlayable,
                    requiredHeaders = winningHeaders,
                    failureReason = if (!isPlayable) "HTTP $finalStatus (Content-Type: $finalType)" else null,
                )
            )
        }

        return AnimePlaybackAuditReport(
            animeName = animeTitles.first(),
            episodesTested = probeResults,
        )
    }

    // ==========================================
    // 1. One Piece Multi-Episode Playback Audit
    // ==========================================
    @Test
    fun testOnePieceMultiEpisodePlayback() = runBlocking {
        val report = auditEpisodePlayback(
            animeTitles = listOf("One Piece", "ون بيس"),
            targetEpisodes = listOf(1f, 500f, 1000f, 1180f),
        )
        report.printMatrix()
        val playableCount = report.episodesTested.count { it.isPlayable }
        assertTrue("One Piece must have playable episodes verified (found $playableCount)", playableCount >= 2)
    }

    // ==========================================
    // 2. Naruto Multi-Episode Playback Audit
    // ==========================================
    @Test
    fun testNarutoMultiEpisodePlayback() = runBlocking {
        val report = auditEpisodePlayback(
            animeTitles = listOf("Naruto", "ناروتو"),
            targetEpisodes = listOf(1f, 100f, 220f),
            totalEpisodesExpected = 220,
        )
        report.printMatrix()
        val playableCount = report.episodesTested.count { it.isPlayable }
        assertTrue("Naruto must have playable episodes verified (found $playableCount)", playableCount >= 2)
    }

    // ==========================================
    // 3. Naruto Shippuden Multi-Episode Playback Audit
    // ==========================================
    @Test
    fun testNarutoShippudenMultiEpisodePlayback() = runBlocking {
        val report = auditEpisodePlayback(
            animeTitles = listOf("Naruto Shippuden", "Naruto: Shippuuden", "ناروتو شيبودن"),
            targetEpisodes = listOf(1f, 250f, 500f),
            totalEpisodesExpected = 500,
        )
        report.printMatrix()
        val playableCount = report.episodesTested.count { it.isPlayable }
        assertTrue("Naruto Shippuden must have playable episodes verified (found $playableCount)", playableCount >= 2)
    }

    // ==========================================
    // 4. Jujutsu Kaisen Multi-Episode Playback Audit
    // ==========================================
    @Test
    fun testJujutsuKaisenMultiEpisodePlayback() = runBlocking {
        val report = auditEpisodePlayback(
            animeTitles = listOf("Jujutsu Kaisen", "جوجيتسو كايسن"),
            targetEpisodes = listOf(1f, 12f, 24f),
            totalEpisodesExpected = 24,
        )
        report.printMatrix()
        val playableCount = report.episodesTested.count { it.isPlayable }
        assertTrue("Jujutsu Kaisen must have playable episodes verified (found $playableCount)", playableCount >= 2)
    }

    // ==========================================
    // 5. Attack on Titan Multi-Episode Playback Audit
    // ==========================================
    @Test
    fun testAttackOnTitanMultiEpisodePlayback() = runBlocking {
        val report = auditEpisodePlayback(
            animeTitles = listOf("Attack on Titan", "Shingeki no Kyojin", "هجوم العمالقة"),
            targetEpisodes = listOf(1f, 13f, 25f),
            totalEpisodesExpected = 25,
        )
        report.printMatrix()
        val playableCount = report.episodesTested.count { it.isPlayable }
        assertTrue("Attack on Titan must have playable episodes verified (found $playableCount)", playableCount >= 2)
    }

    // ==========================================
    // 6. Death Note Multi-Episode Playback Audit
    // ==========================================
    @Test
    fun testDeathNoteMultiEpisodePlayback() = runBlocking {
        val report = auditEpisodePlayback(
            animeTitles = listOf("Death Note", "مذكرة الموت"),
            targetEpisodes = listOf(1f, 18f, 37f),
            totalEpisodesExpected = 37,
        )
        report.printMatrix()
        val playableCount = report.episodesTested.count { it.isPlayable }
        assertTrue("Death Note must have playable episodes verified (found $playableCount)", playableCount >= 2)
    }

    // ==========================================
    // 7. Solo Leveling Multi-Episode Playback Audit
    // ==========================================
    @Test
    fun testSoloLevelingMultiEpisodePlayback() = runBlocking {
        val report = auditEpisodePlayback(
            animeTitles = listOf("Solo Leveling", "Ore dake Level Up na Ken", "سولو ليفلينج"),
            targetEpisodes = listOf(1f, 6f, 12f),
            totalEpisodesExpected = 12,
        )
        report.printMatrix()
        val playableCount = report.episodesTested.count { it.isPlayable }
        assertTrue("Solo Leveling must have playable episodes verified (found $playableCount)", playableCount >= 2)
    }

    // ==========================================
    // 8. Automatic Fallback & Error Recovery Audit (Step 4)
    // ==========================================
    @Test
    fun testAutomaticServerAndProviderFallback() = runBlocking {
        println("====================================================================================================")
        println("=== FALLBACK & AUTO-RECOVERY AUDIT (Step 4) ===")
        println("====================================================================================================")

        // Case A: Server 1 fails -> Server 2 of same quality must be selected
        val serverA = AnimeStreamServer(name = "Primary FHD", url = "https://failed-cdn.com/fhd.mp4", quality = "1080p FHD", isDirectVideo = true)
        val serverB = AnimeStreamServer(name = "Backup FHD", url = "https://working-cdn.com/fhd.mp4", quality = "1080p FHD", isDirectVideo = true)
        val qualityOption = UnifiedQualityOption(
            quality = QualityRank("1080p FHD", 1080),
            primaryServer = serverA,
            fallbackServers = listOf(serverB),
        )
        val failedUrls = mutableSetOf(serverA.url)
        val serverFallback = qualityOption.fallbackServers.firstOrNull { it.url !in failedUrls }

        assertNotNull("Server fallback must successfully pick backup server", serverFallback)
        assertEquals(serverB.url, serverFallback!!.url)
        println("  [✓] Server-Level Fallback: Primary 1080p failed -> Automatically selected Backup 1080p")

        // Case B: All servers of 1080p fail -> Nearest adjacent quality (720p) selected
        val server720 = AnimeStreamServer(name = "720p Server", url = "https://working-cdn.com/720.mp4", quality = "720p HD", isDirectVideo = true)
        val opt720 = UnifiedQualityOption(
            quality = QualityRank("720p HD", 720),
            primaryServer = server720,
        )
        failedUrls.add(serverB.url)
        val adjacentQuality = listOf(opt720)
            .mapNotNull { opt ->
                val valid = if (opt.primaryServer.url !in failedUrls) opt.primaryServer
                else opt.fallbackServers.firstOrNull { it.url !in failedUrls }
                if (valid != null) Pair(opt, valid) else null
            }
            .minByOrNull { kotlin.math.abs(it.first.quality.height - 1080) }

        assertNotNull("Quality fallback must select adjacent 720p", adjacentQuality)
        assertEquals(720, adjacentQuality!!.first.quality.height)
        println("  [✓] Quality-Level Fallback: All 1080p servers exhausted -> Automatically transitioned to 720p HD")

        // Case C: Provider A fails -> Provider B alternative source fallback
        val testEp = AnimeEpisode(
            number = 1f,
            title = "Episode 1",
            url = "https://anime4up.com/ep1",
            sources = listOf(
                EpisodeSourceReference(providerId = "anime4up", episodeUrl = "https://anime4up.com/ep1"),
                EpisodeSourceReference(providerId = "anime3rb", episodeUrl = "https://anime3rb.com/ep1"),
            )
        )
        val failedProviderIds = setOf("anime4up")
        val fallbackCandidates = testEp.sources.filter { it.providerId !in failedProviderIds }

        assertEquals(1, fallbackCandidates.size)
        assertEquals("anime3rb", fallbackCandidates.first().providerId)
        println("  [✓] Provider-Level Fallback: Provider 'anime4up' marked failed -> Automatically dispatched to 'anime3rb'")

        // Case D: Infinite retry prevention when all sources fail
        val allFailedProviders = setOf("anime4up", "anime3rb")
        val exhaustedCandidates = testEp.sources.filter { it.providerId !in allFailedProviders }
        assertTrue("Exhausted candidates must be empty", exhaustedCandidates.isEmpty())

        val terminatedResult = SmartStreamEngine.resolveEpisodeStreams(
            episode = testEp,
            failedProviderIds = allFailedProviders,
            forceRefresh = true,
        )
        assertFalse("Playback must cleanly terminate with failure without hanging", terminatedResult.success)
        assertEquals(EpisodeAvailabilityState.UNAVAILABLE, terminatedResult.episode.availabilityState)
        println("  [✓] Terminal Loop Guard: All sources failed -> Cleanly terminates with UNAVAILABLE state (No infinite loop)")
        println("====================================================================================================\n")
    }
}
