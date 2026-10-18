package com.imashnake.animite.media.source

import java.net.URLEncoder

class Anime3rbProvider : EpisodeSourceProvider {
    override val id: String = "anime3rb"
    override val name: String = "المصدر 2"
    override val language: String = "ar"

    private val baseUrl = "https://anime3rb.com"

    override suspend fun search(query: String): List<AnimeSearchResult> {
        val results = mutableListOf<AnimeSearchResult>()

        // 1. Direct canonical slug resolution (e.g. "Sasaki to Pii-chan" -> "sasaki-to-pii-chan")
        val candidateSlugs = mutableListOf<String>()
        val mainSlug = query.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (mainSlug.isNotBlank()) candidateSlugs.add(mainSlug)

        // Without season qualifiers (e.g. "Jujutsu Kaisen 2nd Season" -> "jujutsu-kaisen-2nd-season", "jujutsu-kaisen")
        val withoutSeason = query.replace(Regex("(?i)\\b(season|part|cour)\\s*\\d+.*"), "").trim()
        val withoutSeasonSlug = withoutSeason.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        if (withoutSeasonSlug.isNotBlank() && withoutSeasonSlug !in candidateSlugs) {
            candidateSlugs.add(withoutSeasonSlug)
        }

        for (slug in candidateSlugs) {
            try {
                val directUrl = "$baseUrl/titles/$slug"
                val doc = HttpHelper.getDocument(directUrl)
                if (doc.select("a[href*=\"/episode/\"]").isNotEmpty()) {
                    val pageTitle = doc.selectFirst("h1, h2, .title")?.text()?.trim() ?: query
                    val poster = doc.selectFirst("img[src*=\"images.anime3rb.com\"], img")?.let { img ->
                        img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                    }
                    results.add(
                        AnimeSearchResult(
                            id = directUrl,
                            title = pageTitle,
                            url = directUrl,
                            poster = poster,
                        )
                    )
                    return results // Found direct title match with episodes!
                }
            } catch (_: Exception) {
                // Ignore 404 or network errors for slug attempt
            }
        }

        // 2. Search endpoint: /titles?search={query}
        try {
            val searchUrl = "$baseUrl/titles?search=${URLEncoder.encode(query, "UTF-8")}"
            val html = HttpHelper.getHtml(searchUrl)
            if (!HttpHelper.isCloudflareChallenge(html)) {
                val doc = org.jsoup.Jsoup.parse(html, searchUrl)
                val searchResults = Anime3rbHtmlParser.parseSearchResults(doc)
                if (searchResults.isNotEmpty()) {
                    results.addAll(searchResults)
                }
            }
        } catch (_: Exception) {
        }

        return results
    }

    override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> {
        return try {
            val html = HttpHelper.getHtml(animeUrl)
            if (HttpHelper.isCloudflareChallenge(html)) return emptyList()
            val doc = org.jsoup.Jsoup.parse(html, animeUrl)
            Anime3rbHtmlParser.parseEpisodesList(doc)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
        return try {
            android.util.Log.i("ANIMITE_DIAG", "ANIME3RB_GET_SERVERS_START: fetching episode page")
            val episodeHtml = HttpHelper.getHtml(episodeUrl)
            if (HttpHelper.isCloudflareChallenge(episodeHtml)) return emptyList()
            android.util.Log.i("ANIMITE_DIAG", "ANIME3RB_EPISODE_HTML: length=${episodeHtml.length}")
            val servers = mutableListOf<AnimeStreamServer>()

            // 1. Extract Vid3rb player URL and parse direct video sources
            val playerUrl = Anime3rbHtmlParser.extractPlayerUrl(episodeHtml)
            android.util.Log.i("ANIMITE_DIAG", "ANIME3RB_PLAYER_URL_FOUND: found=${!playerUrl.isNullOrEmpty()}")
            if (!playerUrl.isNullOrEmpty()) {
                try {
                    val playerHtml = HttpHelper.getHtml(playerUrl, referer = episodeUrl)
                    android.util.Log.i("ANIMITE_DIAG", "ANIME3RB_PLAYER_HTML: length=${playerHtml.length}")
                    val directServers = Anime3rbHtmlParser.parsePlayerSources(playerHtml, playerUrl)
                    android.util.Log.i("ANIMITE_DIAG", "ANIME3RB_PARSED_DIRECT_SERVERS: count=${directServers.size}")
                    servers.addAll(directServers)
                } catch (pe: Exception) {
                    android.util.Log.e("ANIMITE_DIAG", "ANIME3RB_PLAYER_FETCH_ERROR: ${pe.javaClass.simpleName}: ${pe.message}")
                    servers.add(
                        AnimeStreamServer(
                            name = "مشغل ويب",
                            url = playerUrl,
                            isDirectVideo = false,
                        )
                    )
                }
            }

            // 2. Parse episode page elements (download links, JSON-LD, etc.)
            val doc = org.jsoup.Jsoup.parse(episodeHtml, episodeUrl)
            val pageResult = Anime3rbHtmlParser.parseEpisodePage(doc, episodeUrl, episodeHtml)
            android.util.Log.i("ANIMITE_DIAG", "ANIME3RB_PAGE_RESULT_SERVERS: count=${pageResult.servers.size}")
            for (server in pageResult.servers) {
                if (!servers.any { it.url == server.url }) {
                    servers.add(server)
                }
            }

            android.util.Log.i("ANIMITE_DIAG", "ANIME3RB_FINAL_SERVERS_COUNT: count=${servers.size}")
            servers
        } catch (e: Exception) {
            android.util.Log.e("ANIMITE_DIAG", "ANIME3RB_GET_SERVERS_FATAL: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    suspend fun getEpisodeParseResult(episodeUrl: String): EpisodeParseResult? {
        return try {
            val episodeHtml = HttpHelper.getHtml(episodeUrl)
            val doc = org.jsoup.Jsoup.parse(episodeHtml, episodeUrl)
            Anime3rbHtmlParser.parseEpisodePage(doc, episodeUrl, episodeHtml)
        } catch (e: Exception) {
            null
        }
    }
}
