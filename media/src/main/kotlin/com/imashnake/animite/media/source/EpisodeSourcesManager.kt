package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import com.imashnake.animite.media.source.classification.EpisodeClassificationRepository

data class AggregatedEpisodesResult(
    val matchedAnime: AnimeSearchResult,
    val episodes: List<AnimeEpisode>,
    val provider: EpisodeSourceProvider,
    val participatingProviders: List<EpisodeSourceProvider> = emptyList(),
    val providerEpisodesMap: Map<String, List<AnimeEpisode>> = emptyMap(),
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

data class MissingEpisodeRecoveryResult(
    val episodes: List<AnimeEpisode>,
    val allBatches: List<ProviderEpisodeBatch>,
    val recoveredEpisodeNumbers: List<Int>,
)

object EpisodeSourcesManager {
    /**
     * Returns the currently active, ordered list of providers.
     * Delegates to SourceRepositoryManager so user preferences (enabled/order) are respected.
     * Falls back to the full built-in list if the repository hasn't been initialized.
     */
    val providers: List<EpisodeSourceProvider>
        get() {
            val active = SourceRepositoryManager.orderedEnabledProviders.value
            return active.ifEmpty { SourceRepositoryManager.allBuiltInProviders }
        }

    private val customProviders = java.util.concurrent.ConcurrentHashMap<String, EpisodeSourceProvider>()

    fun registerCustomProvider(provider: EpisodeSourceProvider) {
        customProviders[provider.id] = provider
    }

    fun unregisterCustomProvider(id: String) {
        customProviders.remove(id)
    }

    fun getProvider(id: String): EpisodeSourceProvider {
        return customProviders[id]
            ?: providers.firstOrNull { it.id == id }
            ?: SourceRepositoryManager.allBuiltInProviders.firstOrNull { it.id == id }
            ?: providers.first()
    }

    fun getProviderDisplayName(id: String): String {
        if (id.lowercase() == "all") return "الكل (تلقائي)"
        val fromRegistry = SourceRepositoryManager.providerDisplayNames[id.lowercase()]
        return fromRegistry ?: customProviders[id]?.name ?: providers.firstOrNull { it.id == id }?.name ?: id
    }

    /**
     * Concurrently queries all available providers, finds high-confidence matching anime,
     * extracts episodes, and unifies them into a single comprehensive episode list
     * with transparent multi-source fallback references.
     */
    suspend fun findBestAggregatedEpisodes(
        titles: List<String>,
        maxAiredEpisode: Int? = null,
        totalEpisodes: Int? = null,
    ): AggregatedEpisodesResult? = coroutineScope {
        val validTitles = titles.filter { it.isNotBlank() }.distinct()
        if (validTitles.isEmpty()) return@coroutineScope null

        Log.d("ANIMITE_DIAG", "AGGREGATOR_START: searching across ${providers.size} providers for titles: $validTitles")

        // 1. Concurrently query all providers with per-provider timeouts to prevent lagging
        val deferredBatches = providers.map { provider ->
            async(Dispatchers.IO) {
                try {
                    withTimeoutOrNull(14000L) {
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
        val initialEpisodes = unifyAndDeduplicateEpisodes(
            batches = successfulBatches,
            maxAiredEpisode = maxAiredEpisode,
            totalEpisodes = totalEpisodes,
            titles = titles,
        )
        Log.d("ANIMITE_DIAG", "AGGREGATOR_MERGED: initial unified episodes count=${initialEpisodes.size} across ${successfulBatches.size} sources")

        // 4. Missing-Episode Recovery Engine:
        // Detect gaps in regular episodes and query alternative/fallback providers to recover missing episodes
        val recoveryResult = recoverMissingEpisodes(
            currentEpisodes = initialEpisodes,
            existingBatches = successfulBatches,
            candidateProviders = providers,
            titles = validTitles,
            maxAiredEpisode = maxAiredEpisode,
            totalEpisodes = totalEpisodes,
        )

        val finalEpisodes = recoveryResult.episodes
        val finalBatches = recoveryResult.allBatches

        val providerEpisodesMap = mutableMapOf<String, List<AnimeEpisode>>()
        for (batch in finalBatches) {
            providerEpisodesMap[batch.provider.id] = batch.episodes
        }

        val totalDiscovered = finalBatches.sumOf { it.episodes.size }
        val duplicatesRemoved = (totalDiscovered - finalEpisodes.size).coerceAtLeast(0)
        val episodesDiscoveredMap = finalBatches.associate { it.provider.id to it.episodes.size }
        val participatingIds = finalBatches.map { it.provider.id }.toSet()
        val failedProvidersMap = providers
            .filter { it.id !in participatingIds }
            .associate { it.id to "لم يتم العثور على عنوان مطابق أو حظر الحماية" }

        val providerDiagnostics = providers.map { p ->
            val discovered = episodesDiscoveredMap[p.id] ?: 0
            val isFailed = p.id !in participatingIds
            ProviderDiagnosticInfo(
                providerId = p.id,
                providerName = getProviderDisplayName(p.id),
                episodesDiscovered = discovered,
                isFailed = isFailed,
                failureReason = if (isFailed) (failedProvidersMap[p.id] ?: "فشل الاستجابة") else null
            )
        }

        val summary = AnimeDiagnosticSummary(
            animeTitle = bestBatch.match.title,
            episodesDiscoveredPerProvider = episodesDiscoveredMap,
            providerDiagnostics = providerDiagnostics,
            unifiedEpisodeCount = finalEpisodes.size,
            duplicateEpisodesRemoved = duplicatesRemoved,
            episodesWithoutPlayableCandidates = finalEpisodes.count { it.availabilityState == EpisodeAvailabilityState.UNAVAILABLE },
            validatedStreamCount = 0,
            failedProviders = failedProvidersMap,
            lastSuccessfulCheck = System.currentTimeMillis(),
            isFromCache = false,
            cacheStatus = "مباشر (Live)",
        )
        SmartStreamDiagnostics.recordAnimeSummary(summary)
        validTitles.forEach { title ->
            SmartStreamDiagnostics.recordAnimeSummary(summary.copy(animeTitle = title))
        }

        AggregatedEpisodesResult(
            matchedAnime = bestBatch.match,
            episodes = finalEpisodes,
            provider = bestBatch.provider,
            participatingProviders = finalBatches.map { it.provider }.distinctBy { it.id },
            providerEpisodesMap = providerEpisodesMap,
        )
    }

    /**
     * Unifies and deduplicates episodes across multiple provider batches.
     * Preserves all unique episodes, prevents duplicate items, links fallback source references,
     * normalizes continuous numbering offsets (e.g., 13..24 -> 1..12),
     * and strictly enforces official broadcast air dates.
     */
    fun unifyAndDeduplicateEpisodes(
        batches: List<ProviderEpisodeBatch>,
        maxAiredEpisode: Int? = null,
        totalEpisodes: Int? = null,
        titles: List<String> = emptyList(),
    ): List<AnimeEpisode> {
        if (batches.isEmpty()) return emptyList()

        data class EpisodeIdentity(
            val episodeType: EpisodeType,
            val normalizedNumber: Float,
        )

        val grouped = mutableMapOf<EpisodeIdentity, MutableList<Pair<AnimeEpisode, ProviderEpisodeBatch>>>()
        val animeTitles = if (titles.isNotEmpty()) titles else batches.map { it.match.title }
        val detectedSeason = animeTitles.firstNotNullOfOrNull { AnimeTitleMatcher.extractSeasonNumber(it) } ?: 1

        for (batch in batches) {
            val batchOffset = calculateBatchOffset(
                episodes = batch.episodes,
                seasonNumber = detectedSeason,
                totalEpisodes = totalEpisodes,
            )

            for (ep in batch.episodes) {
                val adjustedNum = if (batchOffset > 0 && ep.number > batchOffset) {
                    ep.number - batchOffset
                } else {
                    ep.number
                }
                val rawNum = normalizeEpisodeNumber(adjustedNum)
                val isDecimal = (kotlin.math.abs(rawNum - rawNum.toInt()) >= 0.05f)

                val baseEpType = detectEpisodeType(ep, animeTitles)
                val epType = if (isDecimal && (baseEpType == EpisodeType.REGULAR || baseEpType == EpisodeType.UNCLASSIFIED)) {
                    val rawTitle = ep.title?.lowercase(Locale.ROOT) ?: ""
                    if (rawTitle.contains("ملخص") || rawTitle.contains("recap")) {
                        EpisodeType.RECAP
                    } else {
                        EpisodeType.SPECIAL
                    }
                } else {
                    baseEpType
                }

                // If maxAiredEpisode is set, strictly skip future/un-aired/clickbait episodes (only for canon regular episodes)
                if (maxAiredEpisode != null && maxAiredEpisode >= 0 && rawNum > maxAiredEpisode && epType == EpisodeType.REGULAR) {
                    continue
                }

                // If totalEpisodes is set, skip episodes beyond the season total (only for canon regular episodes)
                if (totalEpisodes != null && totalEpisodes > 0 && rawNum > totalEpisodes && epType == EpisodeType.REGULAR) {
                    continue
                }

                val canonicalNum = if (!isDecimal) {
                    rawNum.toInt().toFloat()
                } else {
                    rawNum
                }

                val key = EpisodeIdentity(
                    episodeType = epType,
                    normalizedNumber = canonicalNum,
                )

                val list = grouped.getOrPut(key) { mutableListOf() }
                list.add(Pair(ep.copy(number = canonicalNum, episodeType = epType), batch))
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

            // Pick the highest quality thumbnail available (prefer actual episode screenshot over default/banner)
            val bestThumbnail = items.mapNotNull { it.first.thumbnail }
                .firstOrNull { it.isNotBlank() && !it.contains("default", ignoreCase = true) && !it.contains("banner", ignoreCase = true) }
                ?: items.mapNotNull { it.first.thumbnail }.firstOrNull { it.isNotBlank() }

            // Pick the most informative title
            val bestTitle = items.mapNotNull { it.first.title }
                .filter { it.isNotBlank() }
                .maxByOrNull { it.length }
                ?: formatDefaultTitle(identity.normalizedNumber, identity.episodeType)

            // Primary URL (prefer primary batch URL or first available)
            val primaryUrl = combinedSources.firstOrNull()?.episodeUrl
                ?: items.first().first.url

            val detectedSeason = items.firstNotNullOfOrNull { it.first.seasonNumber.takeIf { s -> s > 0 } }
                ?: items.firstNotNullOfOrNull { AnimeTitleMatcher.extractSeasonNumber(it.second.match.title) }
                ?: 1

            resultEpisodes.add(
                AnimeEpisode(
                    number = identity.normalizedNumber,
                    title = bestTitle,
                    url = primaryUrl,
                    thumbnail = bestThumbnail,
                    episodeType = identity.episodeType,
                    seasonNumber = detectedSeason,
                    sources = combinedSources,
                    availabilityState = if (combinedSources.isNotEmpty() || primaryUrl.isNotBlank()) {
                        EpisodeAvailabilityState.DISCOVERED
                    } else {
                        EpisodeAvailabilityState.UNAVAILABLE
                    },
                )
            )
        }

        // Sort by episode type (regular first, then specials), then episode number
        return resultEpisodes.sortedWith(
            compareBy<AnimeEpisode> { it.episodeType.ordinal }
                .thenBy { it.number }
        )
    }

    fun markEpisodeChecking(episode: AnimeEpisode): AnimeEpisode =
        episode.copy(availabilityState = EpisodeAvailabilityState.CHECKING)

    fun markEpisodePlayable(episode: AnimeEpisode): AnimeEpisode =
        episode.copy(availabilityState = EpisodeAvailabilityState.PLAYABLE)

    fun markEpisodeUnavailable(episode: AnimeEpisode): AnimeEpisode =
        episode.copy(availabilityState = EpisodeAvailabilityState.UNAVAILABLE)

    /**
     * Identifies missing episode numbers within the expected series range.
     * Considers totalEpisodes, maxAiredEpisode, or the highest discovered episode number.
     */
    fun detectMissingEpisodeNumbers(
        episodes: List<AnimeEpisode>,
        maxAiredEpisode: Int? = null,
        totalEpisodes: Int? = null,
    ): List<Int> {
        val seriesEps = episodes.filter { !it.episodeType.isSpecial && !it.episodeType.isMovie && !it.episodeType.isRecap }
        val presentNumbers = seriesEps.map { it.number.toInt() }.toSet()

        val targetMax = when {
            totalEpisodes != null && totalEpisodes > 0 -> totalEpisodes
            maxAiredEpisode != null && maxAiredEpisode > 0 -> maxAiredEpisode
            presentNumbers.isNotEmpty() -> presentNumbers.maxOrNull() ?: 0
            else -> 0
        }

        if (targetMax <= 1) return emptyList()

        val missing = mutableListOf<Int>()
        for (i in 1..targetMax) {
            if (i !in presentNumbers) {
                missing.add(i)
            }
        }
        return missing
    }

    /**
     * Executes automatic missing episode recovery across secondary and tertiary providers.
     * Takes the primary/current unified episode list, detects gaps, and queries alternative providers
     * to recover the missing episodes, merging them seamlessly into the canonical list with correct
     * numbering, provider attribution tags, and independent playable stream routing.
     */
    suspend fun recoverMissingEpisodes(
        currentEpisodes: List<AnimeEpisode>,
        existingBatches: List<ProviderEpisodeBatch>,
        candidateProviders: List<EpisodeSourceProvider>,
        titles: List<String>,
        maxAiredEpisode: Int? = null,
        totalEpisodes: Int? = null,
    ): MissingEpisodeRecoveryResult = coroutineScope {
        val missingNumbers = detectMissingEpisodeNumbers(
            episodes = currentEpisodes,
            maxAiredEpisode = maxAiredEpisode,
            totalEpisodes = totalEpisodes,
        )

        if (missingNumbers.isEmpty()) {
            return@coroutineScope MissingEpisodeRecoveryResult(
                episodes = currentEpisodes,
                allBatches = existingBatches,
                recoveredEpisodeNumbers = emptyList(),
            )
        }

        Log.d("ANIMITE_DIAG", "MISSING_RECOVERY_START: missing=${missingNumbers.size} episodes: $missingNumbers")

        val allBatches = existingBatches.toMutableList()
        val queriedProviderIds = existingBatches.map { it.provider.id }.toSet()
        val untriedProviders = candidateProviders.filter { it.id !in queriedProviderIds }

        if (untriedProviders.isNotEmpty()) {
            val deferredRecoveries = untriedProviders.map { provider ->
                async(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(10000L) {
                            val (match, _) = autoFindAnime(provider, titles)
                            if (match != null) {
                                val score = AnimeTitleMatcher.matchScore(titles, match.title)
                                if (score >= AnimeTitleMatcher.MIN_MATCH_THRESHOLD) {
                                    val eps = provider.getEpisodes(match.url)
                                    if (eps.isNotEmpty()) {
                                        return@withTimeoutOrNull ProviderEpisodeBatch(
                                            provider = provider,
                                            match = match,
                                            episodes = eps,
                                            matchScore = score,
                                        )
                                    }
                                }
                            }
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("ANIMITE_DIAG", "MISSING_RECOVERY_PROVIDER_FAIL for ${provider.id}: ${e.message}")
                        null
                    }
                }
            }

            val recoveredBatches = deferredRecoveries.awaitAll().filterNotNull()
            allBatches.addAll(recoveredBatches)
        }

        // Re-unify with the expanded batches to seamlessly merge recovered episodes
        val finalEpisodes = unifyAndDeduplicateEpisodes(
            batches = allBatches,
            maxAiredEpisode = maxAiredEpisode,
            totalEpisodes = totalEpisodes,
            titles = titles,
        )

        val remainingMissing = detectMissingEpisodeNumbers(
            episodes = finalEpisodes,
            maxAiredEpisode = maxAiredEpisode,
            totalEpisodes = totalEpisodes,
        )
        val recoveredNumbers = missingNumbers.filter { it !in remainingMissing }
        Log.i("ANIMITE_DIAG", "MISSING_RECOVERY_DONE: successfully recovered ${recoveredNumbers.size} episodes: $recoveredNumbers (remaining missing: ${remainingMissing.size})")

        MissingEpisodeRecoveryResult(
            episodes = finalEpisodes,
            allBatches = allBatches,
            recoveredEpisodeNumbers = recoveredNumbers,
        )
    }

    /**
     * Resolves playable streams for an episode by querying ALL attached providers concurrently,
     * discovering all servers and stream qualities, grouping duplicate qualities as failover fallbacks,
     * and sorting qualities in ascending order.
     */
    suspend fun resolveAllEpisodeQualities(
        episode: AnimeEpisode,
        preferredProviderId: String? = null,
        failedProviderIds: Set<String> = emptySet(),
    ): AggregatedPlaybackResult {
        return SmartStreamEngine.resolveEpisodeStreams(
            episode = episode,
            animeTitle = episode.title ?: "",
            preferredProviderId = preferredProviderId,
            failedProviderIds = failedProviderIds,
        )
    }

    /**
     * Resolves playable streams for an episode with automatic, silent fallback across all attached providers.
     * Delegates to multi-source aggregation so that all qualities and fallbacks are discovered.
     */
    suspend fun resolveStreamServersWithFallback(
        episode: AnimeEpisode,
        preferredProviderId: String? = null,
        failedProviderIds: Set<String> = emptySet(),
    ): ResolvedPlaybackResult {
        val aggregated = resolveAllEpisodeQualities(
            episode = markEpisodeChecking(episode),
            preferredProviderId = preferredProviderId,
            failedProviderIds = failedProviderIds,
        )

        if (aggregated.success && aggregated.allDirectServers.isNotEmpty()) {
            val winningId = aggregated.bestStream?.let { best ->
                // Identify winning provider from url or participating providers
                aggregated.participatingProviders.firstOrNull() ?: preferredProviderId ?: "anime4up"
            } ?: (aggregated.participatingProviders.firstOrNull() ?: "anime4up")

            return ResolvedPlaybackResult(
                success = true,
                servers = aggregated.allDirectServers,
                winningProviderId = winningId,
                episode = markEpisodePlayable(episode),
            )
        }

        return ResolvedPlaybackResult(
            success = false,
            servers = emptyList(),
            winningProviderId = "",
            episode = markEpisodeUnavailable(episode),
            errorMessage = aggregated.errorMessage ?: "تعذر تشغيل الحلقة من جميع المصادر المتاحة",
        )
    }

    private fun getProviderPriority(providerId: String): Int {
        return when (providerId.lowercase(Locale.ROOT)) {
            "anime3rb" -> 0
            "anime4up" -> 1
            "witanime" -> 2
            "animeslayer" -> 3
            "animeluxe" -> 4
            else -> 10
        }
    }

    private fun detectEpisodeType(ep: AnimeEpisode, animeTitles: List<String> = emptyList()): EpisodeType {
        if (ep.episodeType != EpisodeType.REGULAR && ep.episodeType != EpisodeType.UNCLASSIFIED) {
            return ep.episodeType
        }
        return EpisodeClassificationRepository.classify(
            animeTitles = animeTitles,
            episodeNumber = ep.number,
            rawTitle = ep.title,
            url = ep.url,
        )
    }

    private fun normalizeEpisodeNumber(number: Float): Float {
        // Strip floating-point noise e.g. 1.00001f -> 1.0f
        return Math.round(number * 10f) / 10f
    }

    private fun formatDefaultTitle(number: Float, type: EpisodeType): String {
        val numStr = if (number % 1.0f == 0.0f) number.toInt().toString() else number.toString()
        return when (type) {
            EpisodeType.CANON, EpisodeType.REGULAR -> "الحلقة $numStr"
            EpisodeType.FILLER -> "الحلقة $numStr (فلر)"
            EpisodeType.MIXED -> "الحلقة $numStr (مختلط)"
            EpisodeType.SPECIAL -> "حلقة خاصة $numStr"
            EpisodeType.OVA -> "أوفا $numStr"
            EpisodeType.ONA -> "أونا $numStr"
            EpisodeType.RECAP -> "ملخص $numStr"
            EpisodeType.MOVIE -> "فيلم $numStr"
            EpisodeType.UNCLASSIFIED -> "الحلقة $numStr"
        }
    }

    suspend fun fetchEpisodesForSingleProvider(
        provider: EpisodeSourceProvider,
        titles: List<String>
    ): Pair<AnimeSearchResult?, List<AnimeEpisode>> = withContext(Dispatchers.IO) {
        try {
            val (match, _) = autoFindAnime(provider, titles)
            if (match != null) {
                val eps = provider.getEpisodes(match.url)
                return@withContext Pair(match, eps)
            }
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "FETCH_SINGLE_PROVIDER_FAIL for ${provider.id}: ${e.message}")
        }
        Pair(null, emptyList())
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
            .replace(Regex("""(?i)\b(Season\s*\d+|S\d+|Part\s*\d+|Cour\s*\d+|TV|Final\s*Season)\b.*"""), "")
            .replace(Regex("""(?i)\b(الموسم\s*\d+|الجزء\s*\d+|البارت\s*\d+|القسم\s*\d+|كوار\s*\d+)\b.*"""), "")
            .replace(Regex("""(?i)\b(الموسم\s*(الاول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر|الاخير|النهائي))\b.*"""), "")
            .replace(Regex("""[:\-–—]"""), " ")
            .trim()
    }

    fun calculateBatchOffset(
        episodes: List<AnimeEpisode>,
        seasonNumber: Int,
        totalEpisodes: Int?,
    ): Int {
        if (seasonNumber <= 1 || episodes.isEmpty()) return 0
        val canonNumbers = episodes.map { it.number }.filter { it > 0f }
        if (canonNumbers.isEmpty()) return 0
        val minNum = canonNumbers.minOrNull()?.toInt() ?: 1
        if (minNum <= 1) return 0 // Already starts at 1, no offset needed

        val offsetCandidate = minNum - 1
        val count = canonNumbers.size

        // 1. If totalEpisodes is known and episodes are offset beyond it
        if (totalEpisodes != null && totalEpisodes > 0) {
            if (minNum > totalEpisodes && count <= totalEpisodes + 2) {
                return offsetCandidate
            }
        }

        // 2. Standard anime broadcast season length offsets (e.g. 10, 11, 12, 13, 22, 24, 25, 26, 36, 48, 50)
        val standardOffsets = setOf(10, 11, 12, 13, 22, 24, 25, 26, 36, 48, 50)
        if (offsetCandidate in standardOffsets) {
            return offsetCandidate
        }

        // 3. If count matches the continuous span [minNum .. maxNum]
        val maxNum = canonNumbers.maxOrNull()?.toInt() ?: minNum
        if ((maxNum - minNum + 1) == count && offsetCandidate >= 10) {
            return offsetCandidate
        }

        return 0
    }
}
