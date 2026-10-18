package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Test

class NarutoDiagnosticTest {

    @Test
    fun diagnoseNaruto() = runBlocking {
        val narutoTitles = listOf("Naruto", "NARUTO", "ناروتو")
        println("==================================================")
        println("=== DIAGNOSING NARUTO (ORIGINAL) SEARCH ===")
        println("==================================================")

        for (provider in EpisodeSourcesManager.providers) {
            println("\n>>> Testing Provider: ${provider.id} (${provider.name}) for Naruto <<<")
            try {
                val (match, allResults) = EpisodeSourcesManager.autoFindAnime(provider, narutoTitles)
                println("  Matched Anime: ${match?.title} -> ${match?.url}")
                println("  Total Search Results: ${allResults.size}")
                for (r in allResults.take(4)) {
                    val score = AnimeTitleMatcher.matchScore(narutoTitles, r.title)
                    println("    - [Score: $score] ${r.title} (${r.url})")
                }

                if (match != null) {
                    val eps = provider.getEpisodes(match.url)
                    println("  Total Episodes Returned: ${eps.size}")
                    if (eps.isNotEmpty()) {
                        println("    First ep: ${eps.first().number} -> ${eps.first().url} (title: ${eps.first().title})")
                        println("    Last ep: ${eps.last().number} -> ${eps.last().url} (title: ${eps.last().title})")
                        val servers = provider.getStreamServers(eps.first().url)
                        println("    Servers for Ep ${eps.first().number}: count=${servers.size}")
                    }
                }
            } catch (e: Exception) {
                println("  FAILED: ${e.message}")
            }
        }

        println("\n==================================================")
        println("=== DIAGNOSING NARUTO SHIPPUDEN SEARCH ===")
        println("==================================================")
        val shippudenTitles = listOf("Naruto: Shippuuden", "Naruto Shippuden", "NARUTO疾風伝", "ناروتو شيبودن")

        for (provider in EpisodeSourcesManager.providers) {
            println("\n>>> Testing Provider: ${provider.id} (${provider.name}) for Naruto Shippuden <<<")
            try {
                val (match, allResults) = EpisodeSourcesManager.autoFindAnime(provider, shippudenTitles)
                println("  Matched Anime: ${match?.title} -> ${match?.url}")
                println("  Total Search Results: ${allResults.size}")
                for (r in allResults.take(4)) {
                    val score = AnimeTitleMatcher.matchScore(shippudenTitles, r.title)
                    println("    - [Score: $score] ${r.title} (${r.url})")
                }

                if (match != null) {
                    val eps = provider.getEpisodes(match.url)
                    println("  Total Episodes Returned: ${eps.size}")
                    if (eps.isNotEmpty()) {
                        println("    First ep: ${eps.first().number} -> ${eps.first().url} (title: ${eps.first().title})")
                        println("    Last ep: ${eps.last().number} -> ${eps.last().url} (title: ${eps.last().title})")
                        val servers = provider.getStreamServers(eps.first().url)
                        println("    Servers for Ep ${eps.first().number}: count=${servers.size}")
                    }
                }
            } catch (e: Exception) {
                println("  FAILED: ${e.message}")
            }
        }
    }

    @Test
    fun testDirectAnime3rbNaruto() = runBlocking {
        try {
            val doc = HttpHelper.getDocument("https://anime3rb.com/titles/naruto")
            val title = doc.selectFirst("h1, h2, .title")?.text()
            println("Anime3rb /titles/naruto title: $title")
            val episodeLinks = doc.select("a[href*=\"/episode/\"]").map { it.attr("abs:href") }
            println("Anime3rb /titles/naruto episode links count: ${episodeLinks.size}, first: ${episodeLinks.take(2)}")
            val allLinks = doc.select("a[href]").map { it.attr("href") }.filter { it.contains("naruto") || it.contains("episode") }
            println("Anime3rb /titles/naruto all related links count: ${allLinks.size}, sample: ${allLinks.take(5)}")

            val searchRes = Anime3rbProvider().search("Naruto")
            println("Anime3rbProvider().search(\"Naruto\") returned: ${searchRes.size}")
            for (r in searchRes) {
                println("  Search result: ${r.title} -> ${r.url}")
            }
        } catch (e: Exception) {
            println("Anime3rb direct naruto failed: ${e.message}")
        }
    }
}

