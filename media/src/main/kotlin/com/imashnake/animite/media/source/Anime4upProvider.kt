package com.imashnake.animite.media.source

import android.util.Log
import org.jsoup.nodes.Document
import java.net.URLEncoder

class Anime4upProvider : EpisodeSourceProvider {
    override val id: String = "anime4up"
    override val name: String = "المصدر 1"
    override val language: String = "ar"

    private val baseUrl = "https://w1.anime4up.rest"
    private val numberRegex = Regex("""(\d+(?:\.\d+)?)""")

    override suspend fun search(query: String): List<AnimeSearchResult> {
        val results = mutableListOf<AnimeSearchResult>()

        try {
            val searchUrl = "$baseUrl/?search_param=animes&s=${URLEncoder.encode(query, "UTF-8")}"
            val html = HttpHelper.getHtml(searchUrl)
            if (HttpHelper.isCloudflareChallenge(html)) {
                Log.w("ANIMITE_DIAG", "ANIME4UP_SEARCH: Cloudflare challenge detected, skipping")
                return emptyList()
            }
            val doc = org.jsoup.Jsoup.parse(html, searchUrl)
            parseSearchResults(doc, results)

            // If empty, try simple query
            if (results.isEmpty()) {
                val simpleSearchUrl = "$baseUrl/?s=${URLEncoder.encode(query, "UTF-8")}"
                val html2 = HttpHelper.getHtml(simpleSearchUrl)
                if (!HttpHelper.isCloudflareChallenge(html2)) {
                    val doc2 = org.jsoup.Jsoup.parse(html2, simpleSearchUrl)
                    parseSearchResults(doc2, results)
                }
            }
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "ANIME4UP_SEARCH_ERROR: ${e.message}")
        }

        return results.distinctBy { it.url }
    }

    private fun parseSearchResults(doc: Document, results: MutableList<AnimeSearchResult>) {
        doc.select("div.anime-card-container, div.episodes-card, div.col-md-3, div.anime-item, a[href*=\"/anime/\"]").forEach { item ->
            val linkTag = if (item.tagName() == "a") item else item.selectFirst("a[href*=\"/anime/\"]")
            val href = linkTag?.attr("abs:href") ?: return@forEach
            if (href.isBlank() || href.endsWith("/anime/") || !href.contains("/anime/")) return@forEach
            if (href.contains("/anime-type/") || href.contains("/anime-genre/") || href.contains("/anime-season/")) return@forEach
            if (results.any { it.url == href }) return@forEach

            val title = linkTag.text().trim().lines().firstOrNull { it.isNotBlank() }
                ?: linkTag.attr("aria-label").trim().ifEmpty {
                    item.selectFirst(".anime-card-title, .title, h3, h2")?.text()?.trim() ?: ""
                }

            val poster = item.selectFirst("img")?.let { img ->
                val dataSrc = img.attr("abs:data-src")
                if (dataSrc.isNotBlank()) dataSrc else img.attr("abs:src")
            }

            if (title.isNotBlank()) {
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
    }

    override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> {
        val episodes = mutableListOf<AnimeEpisode>()
        try {
            val html = HttpHelper.getHtml(animeUrl)
            if (HttpHelper.isCloudflareChallenge(html)) {
                Log.w("ANIMITE_DIAG", "ANIME4UP_EPISODES: Cloudflare challenge detected, skipping")
                return emptyList()
            }
            val mainDoc = org.jsoup.Jsoup.parse(html, animeUrl)
            extractEpisodesFromDocument(mainDoc, episodes)

            // Check if there are other pages or order toggle (e.g., ?ep_order=asc)
            val ascUrl = if (animeUrl.contains("?")) "$animeUrl&ep_order=asc" else "$animeUrl?ep_order=asc"
            try {
                val ascDoc = HttpHelper.getDocument(ascUrl)
                extractEpisodesFromDocument(ascDoc, episodes)
            } catch (_: Exception) {}

            // Check pagination links if available (page 2, 3...)
            val pageLinks = mainDoc.select("a.page-numbers[href*=\"page/\"], a.page-link[href*=\"page/\"]")
                .map { it.attr("abs:href") }
                .distinct()
                .take(15) // Limit pagination requests to prevent lagging

            for (pUrl in pageLinks) {
                if (pUrl.isNotBlank() && pUrl != animeUrl) {
                    try {
                        val pageDoc = HttpHelper.getDocument(pUrl)
                        extractEpisodesFromDocument(pageDoc, episodes)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "ANIME4UP_EPISODES_ERROR: ${e.message}")
        }

        return episodes.distinctBy { it.number }.sortedBy { it.number }
    }

    private fun extractEpisodesFromDocument(doc: Document, episodes: MutableList<AnimeEpisode>) {
        // Anime4up episode cards typically have a link to /episode/...
        doc.select("div.episodes-card-container, div.anime-card-container, div.episodes-card, div.col-md-3, a[href*=\"/episode/\"]").forEach { item ->
            val linkTag = if (item.tagName() == "a") item else item.selectFirst("a[href*=\"/episode/\"]")
            val href = linkTag?.attr("abs:href") ?: return@forEach
            if (href.isBlank() || !href.contains("/episode/")) return@forEach

            val ariaLabel = linkTag.attr("aria-label").trim()
            val text = linkTag.text().trim()
            val rawTitle = ariaLabel.ifBlank { text }

            // Extract episode number
            val epNumber = parseEpisodeNumber(href, rawTitle, episodes.size + 1)

            // Extract episode thumbnail
            val thumbnail = item.selectFirst("img")?.let { img ->
                val dataSrc = img.attr("abs:data-src")
                if (dataSrc.isNotBlank()) dataSrc else img.attr("abs:src")
            } ?: doc.selectFirst("div.anime-thumbnail img, div.poster img, img[alt*=\"Poster\"]")?.let { img ->
                val dataSrc = img.attr("abs:data-src")
                if (dataSrc.isNotBlank()) dataSrc else img.attr("abs:src")
            }

            if (episodes.none { it.url == href }) {
                episodes.add(
                    AnimeEpisode(
                        number = epNumber,
                        title = "الحلقة ${if (epNumber % 1.0f == 0.0f) epNumber.toInt() else epNumber}",
                        url = href,
                        thumbnail = thumbnail,
                    )
                )
            }
        }
    }

    private fun parseEpisodeNumber(url: String, text: String, fallbackIndex: Int): Float {
        // Try Arabic regex: الحلقة 1180 or الحلقة-1180
        val arabicMatch = Regex("""الحلقة\s*(\d+(?:\.\d+)?)""").find(text)
        if (arabicMatch != null) {
            val num = arabicMatch.groupValues[1].toFloatOrNull()
            if (num != null) return num
        }

        // Try from URL: e.g., .../الحلقة-1180-مترجمة/ or .../episode-1180/
        val urlDecoded = java.net.URLDecoder.decode(url, "UTF-8")
        val urlMatch = Regex("""(?:الحلقة|episode)[^\d]*(\d+(?:\.\d+)?)""").find(urlDecoded)
        if (urlMatch != null) {
            val num = urlMatch.groupValues[1].toFloatOrNull()
            if (num != null) return num
        }

        // Fallback to any number in text
        val anyNum = numberRegex.findAll(text).lastOrNull()?.value?.toFloatOrNull()
        if (anyNum != null) return anyNum

        return fallbackIndex.toFloat()
    }

    override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
        val servers = mutableListOf<AnimeStreamServer>()

        try {
            val html = HttpHelper.getHtml(episodeUrl)
            if (HttpHelper.isCloudflareChallenge(html)) {
                Log.w("ANIMITE_DIAG", "ANIME4UP_SERVERS: Cloudflare challenge detected, skipping")
                return emptyList()
            }
            val doc = org.jsoup.Jsoup.parse(html, episodeUrl)

            // 1. Embedded iframes
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("abs:src")
                if (src.startsWith("http") && !src.contains("acceptable.a-ads") && !src.contains("google") && !src.contains("facebook")) {
                    val serverName = when {
                        src.contains("share4max") || src.contains("megamax") -> "سيرفر سريع 1 (1080p)"
                        src.contains("77mg02b") || src.contains("Anime4up-S1") -> "سيرفر رئيسي 1 (720p)"
                        src.contains("Anime4up-S2") -> "سيرفر رئيسي 2 (720p)"
                        src.contains("mp4upload") -> "سيرفر مباشر (1080p)"
                        src.contains("mega.nz") -> "سيرفر خارجي"
                        src.contains("voe.sx") -> "سيرفر بديل 1 (480p)"
                        src.contains("playmogo") || src.contains("dood") -> "سيرفر بديل 2"
                        src.contains("vidbom") -> "سيرفر بديل 3"
                        else -> "مشغل مدمج"
                    }

                    val isDirect = (src.endsWith(".mp4") || src.contains(".mp4?") || src.contains(".m3u8") || src.endsWith(".mpd")) && !src.contains("mp4upload.com")
                    servers.add(
                        AnimeStreamServer(
                            name = serverName,
                            url = src,
                            isDirectVideo = isDirect,
                            quality = if (isDirect) "1080p" else "متعدد الجودات",
                            linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                        )
                    )
                }
            }

            // 2. Download / direct links
            doc.select("a[href*=\"megamax.me/\"], a[href*=\"mp4upload.com/\"], a[href*=\"gofile.io/\"], a[href*=\"workupload.com/\"]").forEach { link ->
                val href = link.attr("abs:href")
                val text = link.text().trim()
                if (href.startsWith("http") && servers.none { it.url == href }) {
                    val isDirect = href.endsWith(".mp4") && !href.contains("mp4upload.com")
                    servers.add(
                        AnimeStreamServer(
                            name = "تحميل مباشر ($text)",
                            url = href,
                            isDirectVideo = isDirect,
                            quality = "HD",
                            linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.DOWNLOAD,
                        )
                    )
                }
            }

            // 3. Resolve Share4max / Megamax iframes into mirrors and direct streams
            val share4maxServer = servers.firstOrNull { it.url.contains("share4max.com") || it.url.contains("megamax.me") }
            if (share4maxServer != null) {
                try {
                    val resolvedMirrors = resolveShare4max(share4maxServer.url, episodeUrl)
                    if (resolvedMirrors.isNotEmpty()) {
                        servers.addAll(0, resolvedMirrors)
                    }
                } catch (se: Exception) {
                    Log.e("ANIMITE_DIAG", "SHARE4MAX_EXTRACT_ERROR: ${se.message}")
                }
            }

            // 4. Try to resolve direct video for mp4upload if present
            val mp4UploadServer = servers.firstOrNull { it.url.contains("mp4upload.com") }
            if (mp4UploadServer != null) {
                try {
                    val resolved = resolveMp4Upload(mp4UploadServer.url)
                    if (resolved != null) {
                        servers.add(
                            0,
                            AnimeStreamServer(
                                name = "Mp4Upload مباشر",
                                url = resolved,
                                isDirectVideo = true,
                                quality = "1080p FHD",
                                linkType = StreamLinkType.DIRECT_VIDEO,
                                headers = mapOf(
                                    "User-Agent" to HttpHelper.USER_AGENT,
                                    "Referer" to "https://www.mp4upload.com/",
                                ),
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "ANIME4UP_SERVERS_ERROR: ${e.message}")
        }

        return servers
    }

    private suspend fun resolveShare4max(iframeUrl: String, episodeUrl: String): List<AnimeStreamServer> {
        val mirrors = mutableListOf<AnimeStreamServer>()
        try {
            val html = HttpHelper.getHtml(
                urlString = iframeUrl,
                referer = episodeUrl,
                headers = mapOf(
                    "X-Inertia" to "true",
                    "X-Inertia-Partial-Component" to "files/mirror/video",
                    "X-Inertia-Partial-Data" to "streams",
                    "X-Inertia-Version" to "7a7eafc95b15a2110f8f43bd7706b997",
                    "X-Requested-With" to "XMLHttpRequest",
                )
            )

            val jsonText = if (html.trim().startsWith("{")) {
                html
            } else {
                Regex("""data-page=["']([^"']+)["']""").find(html)?.groupValues?.get(1)?.let {
                    org.jsoup.parser.Parser.unescapeEntities(it, false)
                } ?: ""
            }

            if (jsonText.isNotEmpty()) {
                val element = kotlinx.serialization.json.Json.parseToJsonElement(jsonText)
                val props = (element as? kotlinx.serialization.json.JsonObject)?.get("props") as? kotlinx.serialization.json.JsonObject
                val streams = props?.get("streams") as? kotlinx.serialization.json.JsonObject
                val data = streams?.get("data") as? kotlinx.serialization.json.JsonArray
                data?.forEach { streamItem ->
                    val sObj = streamItem as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val label = (sObj["label"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "1080p FHD"
                    val mirrorList = sObj["mirrors"] as? kotlinx.serialization.json.JsonArray
                    mirrorList?.forEach { mItem ->
                        val mObj = mItem as? kotlinx.serialization.json.JsonObject ?: return@forEach
                        val driver = (mObj["driver"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "mirror"
                        var link = (mObj["link"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                        if (link.startsWith("//")) link = "https:$link"

                        if (link.startsWith("http")) {
                            val isDirect = link.endsWith(".mp4") || link.contains(".m3u8")
                            mirrors.add(
                                AnimeStreamServer(
                                    name = "سيرفر $driver ($label)",
                                    url = link,
                                    isDirectVideo = isDirect,
                                    quality = label,
                                    linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                                    headers = mapOf("Referer" to iframeUrl),
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "SHARE4MAX_RESOLVE_FAIL: ${e.message}")
        }
        return mirrors
    }

    private suspend fun resolveMp4Upload(embedUrl: String): String? {
        val cleanUrl = if (embedUrl.contains("/embed-")) embedUrl else {
            val id = embedUrl.substringAfterLast('/').substringBefore(".html")
            "https://www.mp4upload.com/embed-$id.html"
        }
        val html = HttpHelper.getHtml(cleanUrl, referer = "https://www.mp4upload.com/")
        val match = Regex("""(?i)player\.src\(\s*\{\s*type:\s*["'][^"']+["'],\s*src:\s*["']([^"']+\.mp4[^"']*)["']""").find(html)
            ?: Regex("""(?i)src:\s*["'](https?://[^"']+\.mp4[^"']*)["']""").find(html)
            ?: Regex("""['"](https?://[^'"]*mp4upload[^'"]*\.mp4[^'"]*)['"]""").find(html)
        return match?.groupValues?.get(1)
    }
}
