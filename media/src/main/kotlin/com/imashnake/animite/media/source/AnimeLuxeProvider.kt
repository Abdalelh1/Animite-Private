package com.imashnake.animite.media.source

import android.util.Log
import org.json.JSONObject
import java.net.URLEncoder

class AnimeLuxeProvider : EpisodeSourceProvider {
    override val id: String = "animeluxe"
    override val name: String = "AnimeLuxe (عربي)"
    override val language: String = "ar"

    private val baseUrl = "https://vip.animeluxe.org"
    private val numberRegex = Regex("""(\d+(?:\.\d+)?)""")

    override suspend fun search(query: String): List<AnimeSearchResult> {
        val searchUrl = "$baseUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        return try {
            val doc = HttpHelper.getDocument(searchUrl)
            val results = mutableListOf<AnimeSearchResult>()

            doc.select("article.signal-anime-card, article.signal-card").forEach { article ->
                val linkTag = article.selectFirst("a.signal-card__poster, a[href*=\"/anime/\"]") ?: return@forEach
                val href = linkTag.attr("abs:href")
                if (href.isEmpty() || results.any { it.url == href }) return@forEach

                val title = linkTag.attr("aria-label").trim().ifEmpty {
                    article.selectFirst("h3, h2, .signal-card__title, .title")?.text()?.trim() ?: linkTag.text().trim()
                }

                val poster = article.selectFirst("picture source")?.let { s ->
                    val srcset = s.attr("srcset")
                    srcset.split(",").firstOrNull()?.trim()?.substringBefore(" ")
                } ?: article.selectFirst("img")?.let { img ->
                    img.attr("abs:src").ifEmpty { img.attr("src") }
                }

                if (title.isNotEmpty()) {
                    results.add(
                        AnimeSearchResult(
                            id = href,
                            title = title,
                            url = href,
                            poster = poster,
                        )
                    )
                }
            }

            // Fallback: any link to /anime/
            if (results.isEmpty()) {
                doc.select("a[href*=\"/anime/\"]").distinctBy { it.attr("abs:href") }.forEach { a ->
                    val href = a.attr("abs:href")
                    val title = a.attr("aria-label").ifEmpty { a.text().trim() }
                    if (title.length > 2 && !results.any { it.url == href } && !href.endsWith("/anime/")) {
                        results.add(
                            AnimeSearchResult(
                                id = href,
                                title = title,
                                url = href,
                            )
                        )
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "ANIMELUXE_SEARCH_ERROR: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> {
        return try {
            val doc = HttpHelper.getDocument(animeUrl)
            val episodes = mutableListOf<AnimeEpisode>()

            doc.select("a[href*=\"/episodes/\"], a[href*=\"/episode/\"]").forEach { link ->
                val href = link.attr("abs:href")
                if (href.isEmpty() || episodes.any { it.url == href }) return@forEach

                val text = link.text().trim()
                // Parse episode number from URL segment e.g. /episodes/...-الحلقة-1/ or /1/
                val lastSegment = href.trimEnd('/').substringAfterLast('/')
                val epNum = numberRegex.findAll(lastSegment).lastOrNull()?.value?.toFloatOrNull()
                    ?: numberRegex.findAll(text).lastOrNull()?.value?.toFloatOrNull()
                    ?: (episodes.size + 1).toFloat()

                val title = if (text.isNotEmpty() && !text.contains("http")) {
                    text
                } else {
                    "الحلقة ${epNum.toInt()}"
                }

                episodes.add(
                    AnimeEpisode(
                        number = epNum,
                        title = title,
                        url = href,
                    )
                )
            }

            episodes.sortedBy { it.number }
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "ANIMELUXE_EPISODES_ERROR: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
        return try {
            Log.e("ANIMITE_DIAG", "ANIMELUXE_GET_SERVERS: start $episodeUrl")
            val html = HttpHelper.getHtml(episodeUrl)
            val servers = mutableListOf<AnimeStreamServer>()

            // 1. Extract episode ID from data-episode-id="(\d+)"
            val episodeIdMatch = Regex("""data-episode-id=["'](\d+)["']""").find(html)
            val episodeId = episodeIdMatch?.groupValues?.get(1)

            // 2. Extract source indices from data-source-index="(\d+)"
            val sourceIndices = Regex("""data-source-index=["'](\d+)["']""")
                .findAll(html)
                .map { it.groupValues[1] }
                .distinct()
                .toList()

            Log.e("ANIMITE_DIAG", "ANIMELUXE_FOUND: epId=$episodeId, sources=${sourceIndices.size}")

            if (!episodeId.isNullOrEmpty() && sourceIndices.isNotEmpty()) {
                for (idx in sourceIndices) {
                    try {
                        val resolveUrl = "$baseUrl/wp-json/mirai-signal/v1/episodes/$episodeId/player/$idx/resolve"
                        val responseBody = HttpHelper.postJson(resolveUrl, "{}", referer = episodeUrl)
                        if (responseBody.isNotEmpty()) {
                            val json = JSONObject(responseBody)
                            val provider = json.optString("provider", "Server $idx")
                            val streams = json.optJSONArray("streams")

                            // Check direct video streams
                            if (streams != null && streams.length() > 0) {
                                for (i in 0 until streams.length()) {
                                    val streamObj = streams.getJSONObject(i)
                                    val streamUrl = streamObj.optString("url")
                                    val label = streamObj.optString("label", "HD")
                                    if (streamUrl.isNotEmpty() && !servers.any { it.url == streamUrl }) {
                                        servers.add(
                                            AnimeStreamServer(
                                                name = "AnimeLuxe ($provider $label)",
                                                url = streamUrl,
                                                isDirectVideo = true,
                                                quality = label,
                                                linkType = StreamLinkType.DIRECT_VIDEO,
                                                headers = mapOf(
                                                    "User-Agent" to HttpHelper.USER_AGENT,
                                                    "Referer" to episodeUrl,
                                                ),
                                            )
                                        )
                                    }
                                }
                            }

                            // Check fallback iframe/embed
                            val fallbackObj = json.optJSONObject("fallback")
                            val fallbackUrl = fallbackObj?.optString("url") ?: ""
                            if (fallbackUrl.isNotEmpty() && !servers.any { it.url == fallbackUrl }) {
                                servers.add(
                                    AnimeStreamServer(
                                        name = "AnimeLuxe ($provider ويب)",
                                        url = fallbackUrl,
                                        isDirectVideo = false,
                                        quality = "ويب",
                                        linkType = StreamLinkType.WEB_PAGE,
                                    )
                                )
                            }
                        }
                    } catch (se: Exception) {
                        Log.e("ANIMITE_DIAG", "ANIMELUXE_SOURCE_ERROR: idx=$idx: ${se.message}")
                    }
                }
            }

            // Fallback: Embed the episode page itself if no servers could be resolved
            if (servers.isEmpty()) {
                servers.add(
                    AnimeStreamServer(
                        name = "مشغل AnimeLuxe (ويب)",
                        url = episodeUrl,
                        isDirectVideo = false,
                    )
                )
            }

            Log.e("ANIMITE_DIAG", "ANIMELUXE_SERVERS_RESULT: count=${servers.size}")
            servers
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "ANIMELUXE_SERVERS_FATAL: ${e.message}")
            emptyList()
        }
    }
}
