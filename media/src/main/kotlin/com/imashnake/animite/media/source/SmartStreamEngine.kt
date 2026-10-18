package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * SmartStream Engine — Central unified engine for multi-source episode aggregation,
 * full server discovery, quality hierarchy organization, TTL caching, and failover management.
 */
object SmartStreamEngine {

    private const val TAG = "SMARTSTREAM_ENGINE"

    /**
     * Resolves and aggregates all playable stream qualities for an episode across all matching providers.
     * Integrates TTL caching, server health checks, parallel discovery, and diagnostic logging.
     *
     * @param episode The requested episode with source references.
     * @param animeTitle Anime title for cache keying and diagnostics.
     * @param forceRefresh When true, bypasses cache and re-extracts fresh streams (e.g. after expired tokens).
     */
    suspend fun resolveEpisodeStreams(
        episode: AnimeEpisode,
        animeTitle: String = "",
        preferredProviderId: String? = null,
        failedProviderIds: Set<String> = emptySet(),
        forceRefresh: Boolean = false,
    ): AggregatedPlaybackResult = coroutineScope {
        val title = animeTitle.ifBlank { episode.title ?: "Anime" }

        // 1. Check TTL cache if refresh not forced
        if (!forceRefresh) {
            val cached = SmartStreamCache.getValidStreamResult(title, episode.number)
            if (cached != null) {
                Log.d(TAG, "RESOLVE_EPISODE: using cached stream result for ep=${episode.number}")
                return@coroutineScope cached
            }
        } else {
            SmartStreamCache.invalidateStream(title, episode.number)
        }

        val startTime = System.currentTimeMillis()
        val candidates = mutableListOf<EpisodeSourceReference>()

        if (episode.sources.isNotEmpty()) {
            candidates.addAll(episode.sources)
        } else if (episode.url.isNotBlank()) {
            candidates.add(
                EpisodeSourceReference(
                    providerId = preferredProviderId ?: EpisodeSourcesManager.providers.first().id,
                    episodeUrl = episode.url,
                )
            )
        }

        val validCandidates = candidates.filter { it.providerId !in failedProviderIds }

        if (validCandidates.isEmpty()) {
            val failureReport = EpisodeDiagnosticReport(
                episodeNumber = episode.number,
                queriedSourcesCount = 0,
                respondingSourcesCount = 0,
                totalDiscoveredServers = 0,
                validDirectStreamsCount = 0,
                discoveredQualities = emptyList(),
                winningStreamUrl = null,
                winningQuality = null,
                failureReasons = listOf("لا توجد مراجع مصادر مسجلة لهذه الحلقة"),
            )
            SmartStreamDiagnostics.recordReport(failureReport)

            return@coroutineScope AggregatedPlaybackResult(
                success = false,
                episode = episode.copy(availabilityState = EpisodeAvailabilityState.UNAVAILABLE),
                errorMessage = "لا توجد مصادر متوفرة لهذه الحلقة",
            )
        }

        val serverDiagnostics = mutableListOf<ServerDiagnostic>()
        val failureReasons = mutableListOf<String>()

        // 2. Concurrently discover servers across all candidate sources with bounded timeouts
        val deferredResponses = validCandidates.map { source ->
            async(Dispatchers.IO) {
                val provider = EpisodeSourcesManager.getProvider(source.providerId)
                try {
                    withTimeoutOrNull(9000L) {
                        val sList = provider.getStreamServers(source.episodeUrl)
                        Pair(provider.id, sList)
                    } ?: run {
                        failureReasons.add("مهلة زمنية انتهت لمصدر ${provider.name}")
                        serverDiagnostics.add(
                            ServerDiagnostic(
                                providerId = provider.id,
                                serverName = provider.name,
                                url = source.episodeUrl,
                                quality = "Unknown",
                                status = ServerDiscoveryStatus.FAILED_TIMEOUT,
                                failureReason = "Timeout after 9.0s",
                            )
                        )
                        null
                    }
                } catch (e: Exception) {
                    failureReasons.add("خطأ اتصال في مصدر ${provider.name}: ${e.message}")
                    serverDiagnostics.add(
                        ServerDiagnostic(
                            providerId = provider.id,
                            serverName = provider.name,
                            url = source.episodeUrl,
                            quality = "Unknown",
                            status = ServerDiscoveryStatus.FAILED_NETWORK_ERROR,
                            failureReason = e.message,
                        )
                    )
                    null
                }
            }
        }

        val responses = deferredResponses.awaitAll().filterNotNull()
        val respondingProvidersCount = responses.filter { it.second.isNotEmpty() }.size
        val allRawServers = responses.flatMap { it.second }.toMutableList()
        val participating = responses.filter { it.second.isNotEmpty() }.map { it.first }.distinct()

        // 2.5 Extract direct playable video streams from supported third-party hosts
        val embedServers = allRawServers.filter { !it.isDirectVideo && DirectStreamExtractor.canExtract(it.url) }
        for (embedServer in embedServers) {
            try {
                val extracted = DirectStreamExtractor.extractDirectStreams(embedServer.url, referer = episode.url)
                for (directServer in extracted) {
                    if (directServer.isDirectVideo && allRawServers.none { it.url == directServer.url }) {
                        allRawServers.add(0, directServer)
                    }
                }
            } catch (ee: Exception) {
                Log.d(TAG, "EMBED_EXTRACT_SKIP: ${embedServer.url} (${ee.message})")
            }
        }

        // 3. Process raw servers into diagnostics
        for (server in allRawServers) {
            val isDirect = SourceLinkExtractor.isPlayableDirectStream(server)
            val rank = SourceLinkExtractor.classifyQuality(server)
            serverDiagnostics.add(
                ServerDiagnostic(
                    providerId = participating.firstOrNull() ?: "unknown",
                    serverName = server.name,
                    url = server.url,
                    quality = rank.canonicalName,
                    status = if (isDirect) ServerDiscoveryStatus.SUCCESS else ServerDiscoveryStatus.FAILED_NOT_DIRECT_VIDEO,
                    failureReason = if (!isDirect) "ليس رابط فيديو مباشر" else null,
                )
            )
        }

        // 4. Organize, deduplicate, and sort ascending via SourceLinkExtractor
        val baseAggregated = SourceLinkExtractor.aggregateAndGroupQualities(
            rawServers = allRawServers,
            episode = episode,
            participatingProviders = participating,
        )

        // 5. Adaptive Health Check: swap deprioritized primary servers with healthy fallbacks
        val healthyQualityOptions = baseAggregated.qualityOptions.map { option ->
            if (SmartStreamCache.isServerDeprioritized(option.primaryServer.url) && option.fallbackServers.isNotEmpty()) {
                val healthyFallback = option.fallbackServers.firstOrNull { !SmartStreamCache.isServerDeprioritized(it.url) }
                if (healthyFallback != null) {
                    val remainingFallbacks = listOf(option.primaryServer) + option.fallbackServers.filter { it.url != healthyFallback.url }
                    option.copy(
                        primaryServer = healthyFallback,
                        fallbackServers = remainingFallbacks,
                    )
                } else {
                    option
                }
            } else {
                option
            }
        }

        val finalAggregated = if (healthyQualityOptions != baseAggregated.qualityOptions) {
            val best = healthyQualityOptions.firstOrNull { it.quality.height == 1080 }?.primaryServer
                ?: healthyQualityOptions.firstOrNull { it.quality.height == 720 }?.primaryServer
                ?: healthyQualityOptions.firstOrNull()?.primaryServer
                ?: baseAggregated.bestStream
            baseAggregated.copy(
                qualityOptions = healthyQualityOptions,
                bestStream = best,
            )
        } else {
            baseAggregated
        }

        // 6. Build and record diagnostic report
        val report = EpisodeDiagnosticReport(
            episodeNumber = episode.number,
            queriedSourcesCount = validCandidates.size,
            respondingSourcesCount = respondingProvidersCount,
            totalDiscoveredServers = allRawServers.size,
            validDirectStreamsCount = finalAggregated.allDirectServers.size,
            discoveredQualities = finalAggregated.qualityOptions.map { it.quality.canonicalName },
            winningStreamUrl = finalAggregated.bestStream?.url,
            winningQuality = finalAggregated.bestStream?.quality,
            serverDetails = serverDiagnostics,
            failureReasons = failureReasons,
        )
        SmartStreamDiagnostics.recordReport(report)

        val finalEpisode = episode.copy(
            availabilityState = if (finalAggregated.success && finalAggregated.allDirectServers.isNotEmpty()) {
                EpisodeAvailabilityState.PLAYABLE
            } else {
                EpisodeAvailabilityState.UNAVAILABLE
            }
        )
        val resultWithState = finalAggregated.copy(episode = finalEpisode)

        // 7. Cache result if successful
        if (resultWithState.success && resultWithState.allDirectServers.isNotEmpty()) {
            SmartStreamCache.putStreamResult(title, episode.number, resultWithState)
            EpisodeSessionRepository.setActivePlaybackResult(resultWithState)
            EpisodeSessionRepository.setActiveServers(resultWithState.allDirectServers)
        }

        resultWithState
    }

    /**
     * Re-extracts expired temporary streams silently for the given episode.
     */
    suspend fun reExtractExpiredStream(
        episode: AnimeEpisode,
        animeTitle: String = "",
    ): AggregatedPlaybackResult {
        Log.i(TAG, "RE_EXTRACT: re-extracting expired streams for ep=${episode.number}")
        SmartStreamDiagnostics.addFailoverEvent(episode.number, "Re-extracting expired/403 stream tokens")
        return resolveEpisodeStreams(episode = episode, animeTitle = animeTitle, forceRefresh = true)
    }

    /**
     * Reports a stream failure, registers it for diagnostics, and flags the failing host in the health tracker.
     */
    fun reportStreamFailure(episodeNumber: Float, failedServer: AnimeStreamServer, error: String) {
        SmartStreamDiagnostics.addFailoverEvent(
            episodeNumber = episodeNumber,
            event = "Server '${failedServer.name}' failed on ${failedServer.quality}: $error"
        )
        SmartStreamCache.recordServerFailure(failedServer.url, error)
    }

    /**
     * Inspects whether a playback error represents an expired CDN token or forbidden access (401, 403, 410).
     */
    fun isTokenExpiredError(error: Throwable?): Boolean {
        var current = error
        while (current != null) {
            val msg = current.message?.lowercase(java.util.Locale.ROOT) ?: ""
            if (msg.contains("403") || msg.contains("401") || msg.contains("410") ||
                msg.contains("expired") || msg.contains("forbidden") || msg.contains("token") ||
                msg.contains("response code: 403") || msg.contains("response code: 401") || msg.contains("response code: 410")
            ) {
                return true
            }
            val className = current.javaClass.simpleName
            if (className.contains("InvalidResponseCodeException") &&
                (msg.contains("403") || msg.contains("401") || msg.contains("410"))
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    /**
     * Handles expired token failover by invalidating caches and extracting fresh tokens silently.
     */
    suspend fun renewExpiredToken(
        episode: AnimeEpisode,
        failedServer: AnimeStreamServer,
        animeTitle: String = "",
    ): AggregatedPlaybackResult {
        Log.w(TAG, "RENEW_TOKEN: Stream token expired on ${failedServer.url}. Invalidating and renewing...")
        val title = animeTitle.ifBlank { episode.title ?: "Anime" }
        SmartStreamCache.invalidateStream(title, episode.number)
        SmartStreamCache.recordServerFailure(failedServer.url, "Token expired (401/403/410)")
        return resolveEpisodeStreams(episode = episode, animeTitle = title, forceRefresh = true)
    }

    /**
     * Reports a successful stream playback to restore server health.
     */
    fun reportStreamSuccess(server: AnimeStreamServer) {
        SmartStreamCache.recordServerSuccess(server.url)
    }
}
