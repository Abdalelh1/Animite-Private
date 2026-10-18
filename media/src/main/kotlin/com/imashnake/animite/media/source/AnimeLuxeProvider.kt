package com.imashnake.animite.media.source

import android.util.Log
import org.json.JSONObject
import java.net.URLEncoder

class AnimeLuxeProvider : EpisodeSourceProvider {
    override val id: String = "animeluxe"
    override val name: String = "المصدر 4"
    override val language: String = "ar"

    private val baseUrl = "https://ww1.animeluxe.com"
    private val numberRegex = Regex("""(\d+(?:\.\d+)?)""")

    override suspend fun search(query: String): List<AnimeSearchResult> {
        val searchUrl = "$baseUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
        return try {
            val html = HttpHelper.getHtml(searchUrl)
            if (HttpHelper.isCloudflareChallenge(html)) {
                Log.w("ANIMITE_DIAG", "ANIMELUXE_SEARCH: Cloudflare challenge detected, skipping")
                return emptyList()
            }
            val doc = org.jsoup.Jsoup.parse(html, searchUrl)
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
            val html = HttpHelper.getHtml(animeUrl)
            if (HttpHelper.isCloudflareChallenge(html)) {
                Log.w("ANIMITE_DIAG", "ANIMELUXE_EPISODES: Cloudflare challenge detected, skipping")
                return emptyList()
            }
            val doc = org.jsoup.Jsoup.parse(html, animeUrl)
            val episodes = mutableListOf<AnimeEpisode>()

            doc.select("a[href*=\"/episodes/\"], a[href*=\"/episode/\"]").forEach { link ->
                val href = link.attr("abs:href")
                if (href.isEmpty() || href.trimEnd('/') == "$baseUrl/episodes" || href.trimEnd('/').endsWith("/episodes") || episodes.any { it.url == href }) return@forEach

                val text = link.text().trim()
                // Parse episode number from URL segment e.g. /episodes/...-الحلقة-1/ or /1/
                val lastSegment = href.trimEnd('/').substringAfterLast('/')
                val epNum = numberRegex.findAll(lastSegment).lastOrNull()?.value?.toFloatOrNull()
                    ?: numberRegex.findAll(text).lastOrNull()?.value?.toFloatOrNull()
                    ?: return@forEach

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
            Log.d("ANIMITE_DIAG", "ANIMELUXE_GET_SERVERS: start $episodeUrl")
            val html = HttpHelper.getHtml(episodeUrl)
            if (HttpHelper.isCloudflareChallenge(html)) {
                Log.w("ANIMITE_DIAG", "ANIMELUXE_SERVERS: Cloudflare challenge detected, skipping")
                return emptyList()
            }
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

            Log.d("ANIMITE_DIAG", "ANIMELUXE_FOUND: epId=$episodeId, sources=${sourceIndices.size}")

            if (!episodeId.isNullOrEmpty() && sourceIndices.isNotEmpty()) {
                for (idx in sourceIndices) {
                    try {
                        val resolveUrl = "$baseUrl/wp-json/mirai-signal/v1/episodes/$episodeId/player/$idx/resolve"
                        val responseBody = HttpHelper.postJson(resolveUrl, "{}", referer = episodeUrl)
                        if (responseBody.isNotEmpty()) {
                            try {
                                val element = kotlinx.serialization.json.Json.parseToJsonElement(responseBody)
                                val jsonObj = element as? kotlinx.serialization.json.JsonObject
                                val provider = (jsonObj?.get("provider") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "سيرفر $idx"
                                val streams = jsonObj?.get("streams") as? kotlinx.serialization.json.JsonArray

                                // Check direct video streams
                                if (streams != null && streams.isNotEmpty()) {
                                    for (item in streams) {
                                        val streamObj = item as? kotlinx.serialization.json.JsonObject ?: continue
                                        val streamUrl = (streamObj["url"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                                        val label = (streamObj["label"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "HD"
                                        if (streamUrl.isNotEmpty() && !servers.any { it.url == streamUrl }) {
                                            servers.add(
                                                AnimeStreamServer(
                                                    name = "سيرفر مباشر ($label)",
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
                                val fallbackObj = jsonObj?.get("fallback") as? kotlinx.serialization.json.JsonObject
                                val fallbackUrl = (fallbackObj?.get("url") as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                                if (fallbackUrl.isNotEmpty() && !servers.any { it.url == fallbackUrl }) {
                                    val isDirect = fallbackUrl.endsWith(".mp4") || fallbackUrl.contains(".m3u8")
                                    servers.add(
                                        AnimeStreamServer(
                                            name = "مشغل ويب ($provider)",
                                            url = fallbackUrl,
                                            isDirectVideo = isDirect,
                                            quality = if (isDirect) "HD" else "ويب",
                                            linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                                        )
                                    )
                                }
                            } catch (je: Exception) {
                                Log.e("ANIMITE_DIAG", "ANIMELUXE_JSON_PARSE_ERROR: ${je.message}")
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
                        name = "مشغل ويب",
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
