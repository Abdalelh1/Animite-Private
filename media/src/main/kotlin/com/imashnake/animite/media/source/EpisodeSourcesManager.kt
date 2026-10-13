package com.imashnake.animite.media.source

import android.util.Log

data class AggregatedEpisodesResult(
    val matchedAnime: AnimeSearchResult,
    val episodes: List<AnimeEpisode>,
    val provider: EpisodeSourceProvider,
)

object EpisodeSourcesManager {
    val providers: List<EpisodeSourceProvider> = listOf(
        Anime4upProvider(),
        Anime3rbProvider(),
        WitAnimeProvider(),
        AnimeLuxeProvider(),
        GogoanimeProvider(),
    )

    fun getProvider(id: String): EpisodeSourceProvider {
        return providers.firstOrNull { it.id == id } ?: providers.first()
    }

    /**
     * Intelligently queries multiple sources behind the scenes, finds the exact matching anime
     * with high confidence using AnimeTitleMatcher, and fetches its episodes.
     * Prevents mismatched animes completely.
     */
    suspend fun findBestAggregatedEpisodes(titles: List<String>): AggregatedEpisodesResult? {
        val validTitles = titles.filter { it.isNotBlank() }.distinct()
        if (validTitles.isEmpty()) return null

        var bestOverallResult: AggregatedEpisodesResult? = null
        var highestEpisodeCount = 0

        for (provider in providers) {
            try {
                Log.d("ANIMITE_DIAG", "AGGREGATOR_TRYING_PROVIDER: ${provider.id}")
                val (match, _) = autoFindAnime(provider, validTitles)
                if (match != null) {
                    val score = AnimeTitleMatcher.matchScore(validTitles, match.title)
                    Log.d("ANIMITE_DIAG", "AGGREGATOR_MATCH_FOUND: provider=${provider.id}, title=${match.title}, score=$score")

                    if (score >= AnimeTitleMatcher.MIN_MATCH_THRESHOLD) {
                        val episodes = provider.getEpisodes(match.url)
                        if (episodes.isNotEmpty()) {
                            Log.d("ANIMITE_DIAG", "AGGREGATOR_EPISODES_FETCHED: count=${episodes.size} from ${provider.id}")
                            val result = AggregatedEpisodesResult(match, episodes, provider)

                            // Prioritize providers that have the highest number of episodes
                            if (episodes.size > highestEpisodeCount) {
                                highestEpisodeCount = episodes.size
                                bestOverallResult = result
                            }

                            // If we have an Arabic provider with full episodes or a very high score, use it
                            if (score >= 80 && episodes.isNotEmpty()) {
                                return result
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ANIMITE_DIAG", "AGGREGATOR_ERROR for ${provider.id}: ${e.message}")
            }
        }

        return bestOverallResult
    }

    suspend fun autoFindAnime(provider: EpisodeSourceProvider, titles: List<String>): Pair<AnimeSearchResult?, List<AnimeSearchResult>> {
        val cleanTitles = titles.filter { it.isNotBlank() }.distinct()
        var allResults = emptyList<AnimeSearchResult>()

        for (query in cleanTitles) {
            val results = provider.search(query)
            if (results.isNotEmpty()) {
                allResults = results

                // Strictly evaluate each result with AnimeTitleMatcher
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

        // NEVER fall back to results.first() if the score is low!
        return Pair(null, allResults)
    }

    private fun cleanQuery(title: String): String {
        return title
            .replace(Regex("""(?i)\b(Season\s*\d+|S\d+|Part\s*\d+|Cour\s*\d+|TV)\b.*"""), "")
            .replace(Regex("""[:\-–—]"""), " ")
            .trim()
    }
}
