package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class AggregatedEpisodesResult(
    val matchedAnime: AnimeSearchResult,
    val episodes: List<AnimeEpisode>,
    val provider: EpisodeSourceProvider,
    val participatingProviders: List<EpisodeSourceProvider> = emptyList(),
)

data class ResolvedPlaybackResult(
    val success: Boolean,
    val servers: List<AnimeStreamServer>,
    val winningProviderId: String,
    val episode: AnimeEpisode,
    val errorMessage: String? = null,
)

data class ProviderEpisodeBatch(
    val provider: EpisodeSourceProvider,
    val match: AnimeSearchResult,
    val episodes: List<AnimeEpisode>,
    val matchScore: Int,
)

object EpisodeSourcesManager {
    val providers: List<EpisodeSourceProvider> = listOf(
        Anime4upProvider(),
        Anime3rbProvider(),
        WitAnimeProvider(),
        AnimeLuxeProvider(),
        GogoanimeProvider(),
    )

    private val customProviders = java.util.concurrent.ConcurrentHashMap<String, EpisodeSourceProvider>()

    fun registerCustomProvider(provider: EpisodeSourceProvider) {
        customProviders[provider.id] = provider
    }

    fun unregisterCustomProvider(id: String) {
        customProviders.remove(id)
    }

    fun getProvider(id: String): EpisodeSourceProvider {
        return customProviders[id] ?: providers.firstOrNull { it.id == id } ?: providers.first()
    }

    /**
     * Concurrently queries all available providers, finds high-confidence matching anime,
     * extracts episodes, and unifies them into a single comprehensive episode list
     * with transparent multi-source fallback references.
     */
    suspend fun findBestAggregatedEpisodes(titles: List<String>): AggregatedEpisodesResult? = coroutineScope {
        val validTitles = titles.filter { it.isNotBlank() }.distinct()
        if (validTitles.isEmpty()) return@coroutineScope null

        Log.d("ANIMITE_DIAG", "AGGREGATOR_START: searching across ${providers.size} providers for titles: $validTitles")

        // 1. Concurrently query all providers with per-provider timeouts to prevent lagging
        val deferredBatches = providers.map { provider ->
            async(Dispatchers.IO) {
                try {
                    withTimeoutOrNull(8000L) {
                        Log.d("ANIMITE_DIAG", "AGGREGATOR_TRYING_PROVIDER: ${provider.id}")
                        val (match, _) = autoFindAnime(provider, validTitles)
                        if (match != null) {
                            val score = AnimeTitleMatcher.matchScore(validTitles, match.title)
                            Log.d("ANIMITE_DIAG", "AGGREGATOR_MATCH_FOUND: provider=${provider.id}, title=${match.title}, score=$score")

                            if (score >= AnimeTitleMatcher.MIN_MATCH_THRESHOLD) {
                                val fetchedEpisodes = provider.getEpisodes(match.url)
                                if (fetchedEpisodes.isNotEmpty()) {
                                    Log.d("ANIMITE_DIAG", "AGGREGATOR_EPISODES_FETCHED: count=${fetchedEpisodes.size} from ${provider.id}")
                                    return@withTimeoutOrNull ProviderEpisodeBatch(
                                        provider = provider,
                                        match = match,
                                        episodes = fetchedEpisodes,
                                        matchScore = score,
                                    )
                                }
                            }
                        }
                        null
                    }
                } catch (e: Exception) {
                    Log.e("ANIMITE_DIAG", "AGGREGATOR_ERROR for ${provider.id}: ${e.message}")
                    null
                }
            }
        }

        val successfulBatches = deferredBatches.awaitAll().filterNotNull()
        if (successfulBatches.isEmpty()) {
            Log.w("ANIMITE_DIAG", "AGGREGATOR_EMPTY: no provider returned valid episodes")
            return@coroutineScope null
        }

        // 2. Select primary matched anime (prefer highest score, then highest episode count)
        val bestBatch = successfulBatches.maxWithOrNull(
            compareBy<ProviderEpisodeBatch> { it.matchScore }
                .thenBy { it.episodes.size }
        ) ?: successfulBatches.first()

        // 3. Intelligently unify and deduplicate episodes across all responding providers
        val unifiedEpisodes = unifyAndDeduplicateEpisodes(successfulBatches)
        Log.d("ANIMITE_DIAG", "AGGREGATOR_MERGED: total unified episodes count=${unifiedEpisodes.size} across ${successfulBatches.size} sources")

        AggregatedEpisodesResult(
            matchedAnime = bestBatch.match,
            episodes = unifiedEpisodes,
            provider = bestBatch.provider,
            participatingProviders = successfulBatches.map { it.provider }.distinctBy { it.id },
        )
    }

    /**
     * Unifies and deduplicates episodes across multiple provider batches.
     * Preserves all unique episodes, prevents duplicate items, links fallback source references,
     * and retains rich metadata (thumbnails and descriptive titles).
     */
    fun unifyAndDeduplicateEpisodes(batches: List<ProviderEpisodeBatch>): List<AnimeEpisode> {
        if (batches.isEmpty()) return emptyList()

        data class EpisodeIdentity(
            val seasonNumber: Int,
            val episodeType: EpisodeType,
            val normalizedNumber: Float,
        )

        val grouped = mutableMapOf<EpisodeIdentity, MutableList<Pair<AnimeEpisode, ProviderEpisodeBatch>>>()

        for (batch in batches) {
            val detectedSeason = AnimeTitleMatcher.extractSeasonNumber(batch.match.title) ?: 1

            for (ep in batch.episodes) {
                val epType = detectEpisodeType(ep)
                val epSeason = if (ep.seasonNumber > 1) ep.seasonNumber else detectedSeason
                val normalizedNum = normalizeEpisodeNumber(ep.number)

                val key = EpisodeIdentity(
                    seasonNumber = epSeason,
                    episodeType = epType,
                    normalizedNumber = normalizedNum,
                )

                val list = grouped.getOrPut(key) { mutableListOf() }
                list.add(Pair(ep, batch))
            }
        }

        val resultEpisodes = mutableListOf<AnimeEpisode>()

        for ((identity, items) in grouped) {
            // Aggregate source references from all providers for this episode
            val combinedSources = mutableListOf<EpisodeSourceReference>()
            for ((ep, batch) in items) {
                if (ep.sources.isNotEmpty()) {
                    for (src in ep.sources) {
                        if (combinedSources.none { it.episodeUrl == src.episodeUrl }) {
                            combinedSources.add(src)
                        }
                    }
                } else if (ep.url.isNotBlank()) {
                    if (combinedSources.none { it.episodeUrl == ep.url }) {
                        combinedSources.add(
                            EpisodeSourceReference(
                                providerId = batch.provider.id,
                                episodeUrl = ep.url,
                                sourceName = batch.provider.name,
                            )
                        )
                    }
                }
            }

            // Pick the highest quality thumbnail available
            val bestThumbnail = items.mapNotNull { it.first.thumbnail }
                .firstOrNull { it.isNotBlank() && !it.contains("default", ignoreCase = true) }
                ?: items.mapNotNull { it.first.thumbnail }.firstOrNull { it.isNotBlank() }

            // Pick the most informative title
            val bestTitle = items.mapNotNull { it.first.title }
                .filter { it.isNotBlank() }
                .maxByOrNull { it.length }
                ?: formatDefaultTitle(identity.normalizedNumber, identity.episodeType)

            // Primary URL (prefer primary batch URL or first available)
            val primaryUrl = combinedSources.firstOrNull()?.episodeUrl
                ?: items.first().first.url

            resultEpisodes.add(
                AnimeEpisode(
                    number = identity.normalizedNumber,
                    title = bestTitle,
                    url = primaryUrl,
                    thumbnail = bestThumbnail,
                    episodeType = identity.episodeType,
                    seasonNumber = identity.seasonNumber,
                    sources = combinedSources,
                )
            )
        }

        // Sort by season, then episode type (regular first, then specials), then episode number
        return resultEpisodes.sortedWith(
            compareBy<AnimeEpisode> { it.seasonNumber }
                .thenBy { it.episodeType.ordinal }
                .thenBy { it.number }
        )
    }

    /**
     * Resolves playable streams for an episode with automatic, silent fallback across all attached providers.
     * Prevents endless retry loops, skips failing providers, and returns directly verified streams.
     */
    suspend fun resolveStreamServersWithFallback(
        episode: AnimeEpisode,
        preferredProviderId: String? = null,
        failedProviderIds: Set<String> = emptySet(),
    ): ResolvedPlaybackResult {
        // Collect candidate sources
        val candidates = mutableListOf<EpisodeSourceReference>()

        if (episode.sources.isNotEmpty()) {
            candidates.addAll(episode.sources)
        } else if (episode.url.isNotBlank()) {
            candidates.add(
                EpisodeSourceReference(
                    providerId = preferredProviderId ?: "anime4up",
                    episodeUrl = episode.url,
                )
            )
        }

        if (candidates.isEmpty()) {
            return ResolvedPlaybackResult(
                success = false,
                servers = emptyList(),
                winningProviderId = "",
                episode = episode,
                errorMessage = "No source references found for this episode",
            )
        }

        // Order candidates prioritizing preferredProviderId and filtering out known failed providers
        val orderedCandidates = candidates
            .filter { it.providerId !in failedProviderIds }
            .sortedWith(
                compareByDescending<EpisodeSourceReference> { it.providerId == preferredProviderId }
                    .thenBy { getProviderPriority(it.providerId) }
            )

        var fallbackNonDirectServers = emptyList<AnimeStreamServer>()
        var fallbackProviderId = ""

        for (source in orderedCandidates) {
            val provider = getProvider(source.providerId)
            Log.d("ANIMITE_DIAG", "FALLBACK_RESOLVING: trying provider=${provider.id} for ep=${episode.number}")

            val fetchedServers = try {
                withTimeoutOrNull(6500L) {
                    provider.getStreamServers(source.episodeUrl)
                }
            } catch (e: Exception) {
                Log.e("ANIMITE_DIAG", "FALLBACK_RESOLVE_FAIL: provider=${provider.id} threw ${e.message}")
                null
            } ?: emptyList()

            // Check for direct playable video streams
            val directStreams = fetchedServers.filter {
                it.isDirectVideo && it.linkType == StreamLinkType.DIRECT_VIDEO && it.url.isNotBlank()
            }

            if (directStreams.isNotEmpty()) {
                Log.d("ANIMITE_DIAG", "FALLBACK_RESOLVED_SUCCESS: provider=${provider.id} returned ${directStreams.size} direct streams for ep=${episode.number}")
                return ResolvedPlaybackResult(
                    success = true,
                    servers = directStreams,
                    winningProviderId = provider.id,
                    episode = episode,
                )
            } else if (fetchedServers.isNotEmpty() && fallbackNonDirectServers.isEmpty()) {
                fallbackNonDirectServers = fetchedServers
                fallbackProviderId = provider.id
            }
        }

        // If no direct video streams found across all candidates, return fallback non-direct if any exists
        if (fallbackNonDirectServers.isNotEmpty()) {
            Log.w("ANIMITE_DIAG", "FALLBACK_USING_NON_DIRECT: using fallback servers from $fallbackProviderId for ep=${episode.number}")
            return ResolvedPlaybackResult(
                success = true,
                servers = fallbackNonDirectServers,
                winningProviderId = fallbackProviderId,
                episode = episode,
            )
        }

        Log.e("ANIMITE_DIAG", "FALLBACK_ALL_FAILED for ep=${episode.number}")
        return ResolvedPlaybackResult(
            success = false,
            servers = emptyList(),
            winningProviderId = "",
            episode = episode,
            errorMessage = "تعذر تشغيل الحلقة من جميع المصادر المتاحة",
        )
    }

    private fun getProviderPriority(providerId: String): Int {
        return when (providerId.lowercase(Locale.ROOT)) {
            "anime3rb" -> 0
            "anime4up" -> 1
            "witanime" -> 2
            "animeluxe" -> 3
            "gogoanime" -> 4
            else -> 10
        }
    }

    private fun detectEpisodeType(ep: AnimeEpisode): EpisodeType {
        val title = ep.title?.lowercase(Locale.ROOT) ?: ""
        val url = ep.url.lowercase(Locale.ROOT)
        return when {
            title.contains("ova") || url.contains("ova") || title.contains("أوفا") -> EpisodeType.OVA
            title.contains("special") || url.contains("special") || title.contains("خاصة") || title.contains("حلقة خاصة") -> EpisodeType.SPECIAL
            title.contains("recap") || url.contains("recap") || title.contains("ملخص") -> EpisodeType.RECAP
            else -> ep.episodeType
        }
    }

    private fun normalizeEpisodeNumber(number: Float): Float {
        // Strip floating-point noise e.g. 1.00001f -> 1.0f
        return Math.round(number * 10f) / 10f
    }

    private fun formatDefaultTitle(number: Float, type: EpisodeType): String {
        val numStr = if (number % 1.0f == 0.0f) number.toInt().toString() else number.toString()
        return when (type) {
            EpisodeType.REGULAR -> "الحلقة $numStr"
            EpisodeType.SPECIAL -> "حلقة خاصة $numStr"
            EpisodeType.OVA -> "أوفا $numStr"
            EpisodeType.RECAP -> "ملخص $numStr"
        }
    }

    suspend fun autoFindAnime(provider: EpisodeSourceProvider, titles: List<String>): Pair<AnimeSearchResult?, List<AnimeSearchResult>> {
        val cleanTitles = titles.filter { it.isNotBlank() }.distinct()
        var allResults = emptyList<AnimeSearchResult>()

        for (query in cleanTitles) {
            val results = provider.search(query)
            if (results.isNotEmpty()) {
                allResults = results

                val scoredResults = results.map { res ->
                    val score = AnimeTitleMatcher.matchScore(cleanTitles, res.title)
                    Pair(res, score)
                }.filter { it.second >= AnimeTitleMatcher.MIN_MATCH_THRESHOLD }
                 .sortedByDescending { it.second }

                val bestMatch = scoredResults.firstOrNull()?.first
                if (bestMatch != null) {
                    return Pair(bestMatch, results)
                }
            }
        }

        // Try searching without season qualifiers if no match found
        val strippedTitles = cleanTitles.map { cleanQuery(it) }.filter { it.isNotBlank() && it !in cleanTitles }.distinct()
        for (query in strippedTitles) {
            val results = provider.search(query)
            if (results.isNotEmpty()) {
                allResults = results
                val scoredResults = results.map { res ->
                    val score = AnimeTitleMatcher.matchScore(cleanTitles, res.title)
                    Pair(res, score)
                }.filter { it.second >= AnimeTitleMatcher.MIN_MATCH_THRESHOLD }
                 .sortedByDescending { it.second }

                val bestMatch = scoredResults.firstOrNull()?.first
                if (bestMatch != null) {
                    return Pair(bestMatch, results)
                }
            }
        }

        return Pair(null, allResults)
    }

    private fun cleanQuery(title: String): String {
        return title
            .replace(Regex("""(?i)\b(Season\s*\d+|S\d+|Part\s*\d+|Cour\s*\d+|TV)\b.*"""), "")
            .replace(Regex("""[:\-–—]"""), " ")
            .trim()
    }
}
