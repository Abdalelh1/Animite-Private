package com.imashnake.animite.media.source

import java.net.URLEncoder

class GogoanimeProvider : EpisodeSourceProvider {
    override val id: String = "gogoanime"
    override val name: String = "Gogoanime (English)"
    override val language: String = "en"

    private val baseUrl = "https://anitaku.to"
    private val ajaxUrl = "https://ajax.gogocdn.net/ajax/load-list-episode"

    override suspend fun search(query: String): List<AnimeSearchResult> {
        val searchUrl = "$baseUrl/search.html?keyword=${URLEncoder.encode(query, "UTF-8")}"
        return try {
            val doc = HttpHelper.getDocument(searchUrl)
            val results = mutableListOf<AnimeSearchResult>()

            doc.select(".last_episodes ul.items li").forEach { item ->
                val linkTag = item.selectFirst(".name a") ?: return@forEach
                val href = linkTag.attr("abs:href")
                val title = linkTag.text().trim()
                val poster = item.selectFirst("img")?.attr("abs:src")

                results.add(
                    AnimeSearchResult(
                        id = href,
                        title = title,
                        url = href,
                        poster = poster
                    )
                )
            }

            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> {
        return try {
            val doc = HttpHelper.getDocument(animeUrl)
            val movieId = doc.selectFirst("input#movie_id")?.attr("value") ?: return emptyList()

            val epDoc = HttpHelper.getDocument("$ajaxUrl?ep_start=0&ep_end=9999&id=$movieId&default_ep=0")
            val episodes = mutableListOf<AnimeEpisode>()
            val numberRegex = Regex("""(\d+(?:\.\d+)?)""")

            epDoc.select("ul#episode_related li a").forEach { a ->
                val href = a.attr("abs:href")
                val name = a.selectFirst(".name")?.text()?.trim() ?: a.text().trim()
                val match = numberRegex.find(name)?.value?.toFloatOrNull() ?: (episodes.size + 1).toFloat()

                episodes.add(
                    AnimeEpisode(
                        number = match,
                        title = name,
                        url = href
                    )
                )
            }

            episodes.sortedBy { it.number }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
        return try {
            val doc = HttpHelper.getDocument(episodeUrl)
            val servers = mutableListOf<AnimeStreamServer>()

            // 1. Vidstreaming / standard players in multi link
            doc.select(".anime_muti_link ul li a").forEach { a ->
                val videoUrl = a.attr("data-video").ifEmpty { a.attr("abs:href") }
                val text = a.text().replace("Choose this server", "").trim()
                val fixedUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl

                if (fixedUrl.startsWith("http")) {
                    servers.add(
                        AnimeStreamServer(
                            name = text.ifEmpty { "Server ${servers.size + 1}" },
                            url = fixedUrl,
                            isDirectVideo = fixedUrl.contains(".mp4") || fixedUrl.contains(".m3u8")
                        )
                    )
                }
            }

            // 2. Main embedded iframe
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("abs:src")
                if (src.startsWith("http") && !servers.any { it.url == src }) {
                    servers.add(
                        AnimeStreamServer(
                            name = "Main Player",
                            url = src,
                            isDirectVideo = src.contains(".mp4") || src.contains(".m3u8")
                        )
                    )
                }
            }

            servers
        } catch (e: Exception) {
            emptyList()
        }
    }
}
