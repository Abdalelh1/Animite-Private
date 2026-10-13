package com.imashnake.animite.media.source

object EpisodeSourcesManager {
    val providers: List<EpisodeSourceProvider> = listOf(
        Anime3rbProvider(),
        AnimeLuxeProvider(),
        WitAnimeProvider(),
        GogoanimeProvider(),
    )

    fun getProvider(id: String): EpisodeSourceProvider {
        return providers.firstOrNull { it.id == id } ?: providers.first()
    }

    suspend fun autoFindAnime(provider: EpisodeSourceProvider, titles: List<String>): Pair<AnimeSearchResult?, List<AnimeSearchResult>> {
        val cleanTitles = titles.map { cleanQuery(it) }.filter { it.isNotBlank() }.distinct()
        var allResults = emptyList<AnimeSearchResult>()

        for (query in cleanTitles) {
            val results = provider.search(query)
            if (results.isNotEmpty()) {
                allResults = results
                // Try to find exact or closest match
                val bestMatch = results.firstOrNull { result ->
                    cleanTitles.any { q ->
                        result.title.contains(q, ignoreCase = true) || q.contains(result.title, ignoreCase = true)
                    }
                } ?: results.first()
                return Pair(bestMatch, results)
            }
        }
        return Pair(null, allResults)
    }

    private fun cleanQuery(title: String): String {
        return title
            .replace(Regex("""(?i)\b(Season|S\d+|Part|Cour|TV)\b.*"""), "")
            .replace(Regex("""[:\-–—]"""), " ")
            .trim()
    }
}
