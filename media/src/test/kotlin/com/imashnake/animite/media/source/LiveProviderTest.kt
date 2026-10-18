package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Test

class LiveProviderTest {

    @Test
    fun testSearchKusuriya() = runBlocking {
        val titles = listOf("Kusuriya no Hitorigoto 3rd Season", "Kusuriya no Hitorigoto", "The Apothecary Diaries")
        println("=== TESTING PROVIDERS SEARCH FOR $titles ===")

        for (provider in EpisodeSourcesManager.providers) {
            println("--- Provider: ${provider.id} (${provider.name}) ---")
            try {
                for (query in titles) {
                    val results = provider.search(query)
                    println("  Query '$query' -> ${results.size} results:")
                    for (r in results.take(5)) {
                        val season = AnimeTitleMatcher.extractSeasonNumber(r.title)
                        val score = AnimeTitleMatcher.matchScore(titles, r.title)
                        println("    [Score: $score, Season: $season] ${r.title} -> ${r.url}")
                    }
                }
            } catch (e: Exception) {
                println("  Error: ${e.message}")
            }
        }

        println("=== AGGREGATED SEARCH ===")
        val best = EpisodeSourcesManager.findBestAggregatedEpisodes(titles)
        if (best != null) {
            println("Best Match: ${best.matchedAnime.title} from ${best.provider.name}")
            println("Unified Episodes Count: ${best.episodes.size}")
            for (ep in best.episodes) {
                println("  Ep ${ep.number}: ${ep.title} -> ${ep.url} (sources: ${ep.sources.size})")
            }
        } else {
            println("No aggregated result found!")
        }
    }
}
