package com.imashnake.animite.media.source

import android.util.Base64
import android.util.Log
import java.net.URLEncoder

class WitAnimeProvider : EpisodeSourceProvider {
    override val id: String = "witanime"
    override val name: String = "WitAnime (عربي)"
    override val language: String = "ar"

    private val baseUrl = "https://witanime.site"
    private val numberRegex = Regex("""(\d+(?:\.\d+)?)""")

    override suspend fun search(query: String): List<AnimeSearchResult> {
        val searchUrl = "$baseUrl/search?q=${URLEncoder.encode(query, "UTF-8")}"
        return try {
            val doc = HttpHelper.getDocument(searchUrl)
            val results = mutableListOf<AnimeSearchResult>()

            doc.select("a[href*=\"/anime/\"], .anime-card-container, .episodes-card, div.anime-item").forEach { item ->
                val linkTag = if (item.tagName() == "a") item else item.selectFirst("a[href*=\"/anime/\"]")
                val href = linkTag?.attr("abs:href") ?: return@forEach
                if (href.isEmpty() || href.endsWith("/anime/") || results.any { it.url == href }) return@forEach

                val title = linkTag.text().trim().lines().firstOrNull { it.isNotBlank() } ?: linkTag.attr("aria-label").trim()
                val poster = item.selectFirst("img")?.let { img ->
                    img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                }

                if (title.isNotEmpty() && !href.contains("/watch/") && !href.contains("/episode/")) {
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

            results
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "WITANIME_SEARCH_ERROR: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> {
        return try {
            val doc = HttpHelper.getDocument(animeUrl)
            val episodes = mutableListOf<AnimeEpisode>()

            val poster = doc.selectFirst(".anime-thumbnail img, .poster img, img")?.let { img ->
                img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            }

            // WitAnime episodes use /watch/{slug}/{num} or /episode/{slug}/{num}
            doc.select("a[href*=\"/watch/\"], a[href*=\"/episode/\"]").forEach { link ->
                val href = link.attr("abs:href")
                if (href.isEmpty() || episodes.any { it.url == href }) return@forEach

                val text = link.text().trim()
                val lastSegment = href.trimEnd('/').substringAfterLast('/')
                val epNumber = lastSegment.toFloatOrNull()
                    ?: numberRegex.findAll(text).lastOrNull()?.value?.toFloatOrNull()
                    ?: (episodes.size + 1).toFloat()

                val epThumb = link.selectFirst("img")?.let { img ->
                    img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                } ?: poster

                episodes.add(
                    AnimeEpisode(
                        number = epNumber,
                        title = if (text.isNotEmpty() && !text.contains("http")) "الحلقة $text" else "الحلقة ${epNumber.toInt()}",
                        url = href,
                        thumbnail = epThumb,
                    )
                )
            }

            episodes.sortedBy { it.number }
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "WITANIME_EPISODES_ERROR: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
        return try {
            val doc = HttpHelper.getDocument(episodeUrl)
            val servers = mutableListOf<AnimeStreamServer>()

            // 1. Check server tab buttons with data-url
            doc.select("ul#episode-servers li a, ul.nav-tabs li a, a[data-url]").forEach { el ->
                val rawUrl = el.attr("data-url").ifEmpty { el.attr("href") }
                val serverName = el.text().trim().ifEmpty { "سيرفر ${servers.size + 1}" }

                val decodedUrl = decodeDataUrl(rawUrl)
                if (decodedUrl.startsWith("http")) {
                    val isDirect = decodedUrl.contains(".mp4") || decodedUrl.contains(".m3u8")
                    val quality = when {
                        serverName.contains("1080", ignoreCase = true) || decodedUrl.contains("1080") -> "1080p"
                        serverName.contains("720", ignoreCase = true) || decodedUrl.contains("720") -> "720p"
                        serverName.contains("480", ignoreCase = true) || decodedUrl.contains("480") -> "480p"
                        else -> "HD"
                    }
                    servers.add(
                        AnimeStreamServer(
                            name = serverName,
                            url = decodedUrl,
                            isDirectVideo = isDirect,
                            quality = if (isDirect) quality else "ويب",
                            linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                        )
                    )
                }
            }

            // 2. Check embedded iframes
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("abs:src")
                if (src.startsWith("http") && !src.contains("acceptable.a-ads") && !servers.any { it.url == src }) {
                    val isDirect = src.contains(".mp4") || src.contains(".m3u8")
                    servers.add(
                        AnimeStreamServer(
                            name = "مشغل رئيسي",
                            url = src,
                            isDirectVideo = isDirect,
                            quality = if (isDirect) "HD" else "ويب",
                            linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                        )
                    )
                }
            }

            // Fallback: Embed the episode watch page
            if (servers.isEmpty()) {
                servers.add(
                    AnimeStreamServer(
                        name = "مشغل WitAnime (ويب)",
                        url = episodeUrl,
                        isDirectVideo = false,
                        quality = "ويب",
                        linkType = StreamLinkType.WEB_PAGE,
                    )
                )
            }

            servers
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "WITANIME_SERVERS_ERROR: ${e.message}")
            emptyList()
        }
    }

    private fun decodeDataUrl(url: String): String {
        return try {
            if (url.startsWith("http")) {
                url
            } else {
                val decoded = String(Base64.decode(url, Base64.DEFAULT), Charsets.UTF_8).trim()
                if (decoded.startsWith("http")) decoded else url
            }
        } catch (_: Exception) {
            url
        }
    }
}

