package com.imashnake.animite.media.source

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

enum class StreamMediaType {
    MP4,
    HLS,
    EMBED_WEB,
    UNKNOWN,
}

enum class StreamAvailabilityStatus {
    AVAILABLE_DIRECT,
    AVAILABLE_EMBED_ONLY,
    UNAVAILABLE,
}

data class EpisodeParseResult(
    val episodeTitle: String,
    val episodeNumber: Float,
    val pageUrl: String,
    val mediaUrl: String?,
    val mediaType: StreamMediaType,
    val availabilityStatus: StreamAvailabilityStatus,
    val failureReason: String? = null,
    val servers: List<AnimeStreamServer> = emptyList(),
)

object Anime3rbHtmlParser {

    private const val BASE_URL = "https://anime3rb.com"
    private val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
    private val videoUrlRegex = Regex("""(?:&quot;|")video_url(?:&quot;|")\s*:\s*(?:&quot;|")((?:https?:)?\\?/\\?/[^"]+?)(?:&quot;|")""")
    private val videoSourcesRegex = Regex("""var\s+video_sources\s*=\s*(\[\s*\{.*?\}\s*\]);""", RegexOption.DOT_MATCHES_ALL)

    fun resolveUrl(url: String, base: String = BASE_URL): String {
        return try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                url
            } else {
                val baseUri = URI(base)
                baseUri.resolve(url).toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Extracts the real Vid3rb player URL from the episode page HTML.
     * Searches inside Livewire snapshot attributes and embedded scripts.
     */
    fun extractPlayerUrl(html: String): String? {
        val match = videoUrlRegex.find(html) ?: return null
        val rawUrl = match.groupValues[1]
        val cleanUrl = rawUrl.replace("\\/", "/").replace("&amp;", "&")
        return when {
            cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") -> cleanUrl
            cleanUrl.startsWith("//") -> "https:$cleanUrl"
            else -> resolveUrl(cleanUrl)
        }
    }

    /**
     * Parses the video player page (Vid3rb) to extract direct MP4 streams
     * from the `var video_sources = [...]` JavaScript declaration.
     */
    fun parsePlayerSources(playerHtml: String, playerUrl: String): List<AnimeStreamServer> {
        val servers = mutableListOf<AnimeStreamServer>()

        val match = videoSourcesRegex.find(playerHtml)
        if (match != null) {
            try {
                val jsonArray = org.json.JSONArray(match.groupValues[1])
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val rawSrc = obj.optString("src").trim()
                    val label = obj.optString("label").ifEmpty {
                        val res = obj.optString("res")
                        if (res.isNotEmpty()) "${res}p" else "MP4"
                    }
                    val isPremium = obj.optBoolean("premium", false)

                    if (rawSrc.isNotEmpty() && !isPremium) {
                        val cleanSrc = rawSrc.replace("\\/", "/")
                        val headers = mapOf(
                            "User-Agent" to HttpHelper.USER_AGENT,
                            "Referer" to "https://video.vid3rb.com/",
                        )
                        servers.add(
                            AnimeStreamServer(
                                name = "Anime3rb ($label)",
                                url = cleanSrc,
                                isDirectVideo = true,
                                quality = label,
                                linkType = StreamLinkType.DIRECT_VIDEO,
                                headers = headers,
                            )
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Add the player URL itself as an embed fallback option
        if (!servers.any { it.url == playerUrl }) {
            servers.add(
                AnimeStreamServer(
                    name = "مشغل Vid3rb المدمج (ويب)",
                    url = playerUrl,
                    isDirectVideo = false,
                    quality = "ويب",
                    linkType = StreamLinkType.WEB_PAGE,
                )
            )
        }

        return servers
    }

    /**
     * Parse search results page from anime3rb.com/search?q=...
     */
    fun parseSearchResults(doc: Document): List<AnimeSearchResult> {
        val results = mutableListOf<AnimeSearchResult>()

        doc.select("a[href*=\"/titles/\"], a[href*=\"/anime/\"]").forEach { item ->
            val href = item.attr("abs:href").ifEmpty { resolveUrl(item.attr("href")) }
            if (href.contains("/titles/list") || href.contains("/episode/")) return@forEach

            val title = item.selectFirst("h3, h2, .title, p, span")?.text()?.trim()
                ?: item.text().trim()
            val poster = item.selectFirst("img")?.let { img ->
                val src = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
                if (src.isNotEmpty()) src else resolveUrl(img.attr("src"))
            }

            if (title.isNotEmpty() && !results.any { it.url == href }) {
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

        return results
    }

    /**
     * Parse episodes list from title page (e.g. anime3rb.com/titles/{slug})
     */
    fun parseEpisodesList(doc: Document): List<AnimeEpisode> {
        val episodes = mutableListOf<AnimeEpisode>()

        // Anime3rb title page contains anchor elements with class btn-light and href containing /episode/
        doc.select("a[href*=\"/episode/\"]").forEach { link ->
            val href = link.attr("abs:href").ifEmpty { resolveUrl(link.attr("href")) }
            if (href.isEmpty() || episodes.any { it.url == href }) return@forEach

            val epTitle = link.selectFirst(".video-data p, p")?.text()?.trim()
            val epText = link.selectFirst(".video-data span, span")?.text()?.trim() ?: link.text().trim()

            // Extract episode number directly from the URL trailing segment (/episode/.../{number})
            val urlNumber = href.trimEnd('/').substringAfterLast('/').toFloatOrNull()
                ?: numberRegex.findAll(href).lastOrNull()?.value?.toFloatOrNull()
            val epNum = urlNumber ?: (episodes.size + 1).toFloat()

            val fullTitle = when {
                !epTitle.isNullOrEmpty() && !epTitle.contains(":") -> epTitle
                epText.isNotEmpty() && !epText.contains(":") -> epText
                else -> "الحلقة ${epNum.toInt()}"
            }

            episodes.add(
                AnimeEpisode(
                    number = epNum,
                    title = fullTitle,
                    url = href,
                )
            )
        }

        return episodes.sortedBy { it.number }
    }

    /**
     * Parse episode page (e.g. anime3rb.com/episode/{slug}/{num})
     */
    fun parseEpisodePage(doc: Document, episodeUrl: String, rawHtml: String = doc.html()): EpisodeParseResult {
        val titleText = doc.title().trim().ifEmpty {
            doc.selectFirst("h1, h2, .episode-title")?.text()?.trim() ?: "حلقة أنمي"
        }

        val epNumber = numberRegex.findAll(episodeUrl).lastOrNull()?.value?.toFloatOrNull() ?: 1f

        val servers = mutableListOf<AnimeStreamServer>()

        // 0. Livewire Player URL if embedded in page
        val playerUrl = extractPlayerUrl(rawHtml)
        if (!playerUrl.isNullOrEmpty() && !servers.any { it.url == playerUrl }) {
            servers.add(
                AnimeStreamServer(
                    name = "مشغل Vid3rb المدمج (ويب)",
                    url = playerUrl,
                    isDirectVideo = false,
                    quality = "ويب",
                    linkType = StreamLinkType.WEB_PAGE,
                )
            )
        }

        // 1. Check for JSON-LD schema
        var schemaEmbedUrl: String? = null
        var schemaName: String? = null
        doc.select("script[type=\"application/ld+json\"]").forEach { script ->
            try {
                val json = JSONObject(script.data())
                if (json.optString("@type") == "Episode") {
                    val videoArray = json.optJSONArray("video")
                    if (videoArray != null && videoArray.length() > 0) {
                        val videoObj = videoArray.getJSONObject(0)
                        schemaEmbedUrl = videoObj.optString("embedUrl").replace("&amp;", "&")
                        schemaName = videoObj.optString("name")
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 2. Direct video tags if present
        doc.select("video source[src], video[src]").forEach { el ->
            val src = el.attr("abs:src").ifEmpty { resolveUrl(el.attr("src")) }
            if (src.isNotEmpty() && !servers.any { it.url == src }) {
                servers.add(
                    AnimeStreamServer(
                        name = "مشغل مباشر",
                        url = src,
                        isDirectVideo = true,
                        quality = "HD",
                        linkType = StreamLinkType.DIRECT_VIDEO,
                    )
                )
            }
        }

        // 3. Iframe embeds
        doc.select("iframe[src]").forEach { iframe ->
            val src = iframe.attr("abs:src").ifEmpty { resolveUrl(iframe.attr("src")) }
            if (src.isNotEmpty() && !src.startsWith("about:") && !servers.any { it.url == src }) {
                val isDirect = src.contains(".mp4") || src.contains(".m3u8")
                servers.add(
                    AnimeStreamServer(
                        name = "مشغل مضمن (Iframe)",
                        url = src,
                        isDirectVideo = isDirect,
                        quality = if (isDirect) "HD" else "ويب",
                        linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                    )
                )
            }
        }

        // 4. If schemaEmbedUrl was found and not already in servers
        schemaEmbedUrl?.let { embedUrl ->
            if (!servers.any { it.url == embedUrl }) {
                val isDirect = embedUrl.contains(".mp4") || embedUrl.contains(".m3u8")
                servers.add(
                    AnimeStreamServer(
                        name = schemaName ?: "مشغل Anime3rb الرسمي",
                        url = embedUrl,
                        isDirectVideo = isDirect,
                        quality = if (isDirect) "HD" else "ويب",
                        linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                    )
                )
            }
        }

        // 5. Download links (with clear labeling)
        doc.select("a[href*=\"/download/\"], a[href*=\"drive.google\"], a[href*=\"mega.nz\"], a[href*=\"mediafire\"]").forEach { a ->
            val href = a.attr("abs:href").ifEmpty { resolveUrl(a.attr("href")) }
            val text = a.text().trim().ifEmpty { "رابط تنزيل" }
            if (href.startsWith("http") && !servers.any { it.url == href }) {
                servers.add(
                    AnimeStreamServer(
                        name = text,
                        url = href,
                        isDirectVideo = false,
                        quality = "تحميل",
                        linkType = StreamLinkType.DOWNLOAD,
                    )
                )
            }
        }

        // 6. Determine media availability and direct stream suitability
        val directServer = servers.firstOrNull { it.isDirectVideo }
        val embedServer = servers.firstOrNull { !it.isDirectVideo }

        return when {
            directServer != null -> {
                val mediaType = if (directServer.url.contains(".m3u8")) StreamMediaType.HLS else StreamMediaType.MP4
                EpisodeParseResult(
                    episodeTitle = titleText,
                    episodeNumber = epNumber,
                    pageUrl = episodeUrl,
                    mediaUrl = directServer.url,
                    mediaType = mediaType,
                    availabilityStatus = StreamAvailabilityStatus.AVAILABLE_DIRECT,
                    servers = servers,
                )
            }
            embedServer != null -> {
                EpisodeParseResult(
                    episodeTitle = titleText,
                    episodeNumber = epNumber,
                    pageUrl = episodeUrl,
                    mediaUrl = embedServer.url,
                    mediaType = StreamMediaType.EMBED_WEB,
                    availabilityStatus = StreamAvailabilityStatus.AVAILABLE_EMBED_ONLY,
                    failureReason = "البث المباشر محمي بروابط موقّعة وجلسة ويب، متاح عبر المشغل المدمج/المتصفح",
                    servers = servers,
                )
            }
            else -> {
                EpisodeParseResult(
                    episodeTitle = titleText,
                    episodeNumber = epNumber,
                    pageUrl = episodeUrl,
                    mediaUrl = episodeUrl,
                    mediaType = StreamMediaType.EMBED_WEB,
                    availabilityStatus = StreamAvailabilityStatus.AVAILABLE_EMBED_ONLY,
                    failureReason = "متاح عبر صفحة المصدر في المتصفح المدمج",
                    servers = listOf(
                        AnimeStreamServer(
                            name = "صفحة المصدر الأصلية",
                            url = episodeUrl,
                            isDirectVideo = false,
                        )
                    ),
                )
            }
        }
    }
}
