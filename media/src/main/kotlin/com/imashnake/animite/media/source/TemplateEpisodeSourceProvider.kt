package com.imashnake.animite.media.source

import android.util.Log
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

/**
 * Sandboxed, declarative source provider for user-configured template sources.
 * Executes purely via Jsoup and Regex — never runs untrusted scripts or arbitrary bytecode.
 */
class TemplateEpisodeSourceProvider(
    val definition: CustomSourceDefinition,
) : EpisodeSourceProvider {

    override val id: String = definition.id
    override val name: String = definition.name
    override val language: String = "ar"

    private val config: CustomSourceTemplateConfig = definition.templateConfig ?: CustomSourceTemplateConfig()
    private val numberRegex = Regex("""(\d+(?:\.\d+)?)""")

    override suspend fun search(query: String): List<AnimeSearchResult> {
        val results = mutableListOf<AnimeSearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val targetUrl = config.searchUrlPattern
                .replace("{baseUrl}", definition.baseUrl.trimEnd('/'))
                .replace("{query}", encodedQuery)

            val doc = HttpHelper.getDocument(targetUrl, headers = definition.customHeaders)
            val items = if (config.searchItemSelector.isNotBlank()) {
                doc.select(config.searchItemSelector)
            } else {
                doc.select("a[href*='/anime/'], div.anime-card")
            }

            for (item in items) {
                val linkTag = if (item.tagName() == "a") item else item.selectFirst(config.searchLinkSelector.ifBlank { "a" })
                val rawHref = linkTag?.attr("href") ?: continue
                val fullUrl = resolveAbsoluteUrl(rawHref, targetUrl)
                if (fullUrl.isBlank() || results.any { it.url == fullUrl }) continue

                val titleTag = if (config.searchTitleSelector.isNotBlank()) item.selectFirst(config.searchTitleSelector) else null
                val title = titleTag?.text()?.trim()
                    ?: linkTag.text().trim().lines().firstOrNull { it.isNotBlank() }
                    ?: linkTag.attr("title").ifBlank { linkTag.attr("aria-label") }

                val posterTag = if (config.searchPosterSelector.isNotBlank()) item.selectFirst(config.searchPosterSelector) else null
                val rawPoster = posterTag?.attr("abs:data-src")?.ifEmpty { posterTag.attr("abs:src") }
                    ?: posterTag?.attr("src")?.let { resolveAbsoluteUrl(it, targetUrl) }

                if (title.isNotBlank()) {
                    results.add(
                        AnimeSearchResult(
                            id = fullUrl,
                            title = title,
                            url = fullUrl,
                            poster = rawPoster,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("TEMPLATE_PROVIDER", "Search failed for ${definition.name}: ${e.message}")
        }
        return results
    }

    override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> {
        val episodes = mutableListOf<AnimeEpisode>()
        try {
            val doc = HttpHelper.getDocument(animeUrl, headers = definition.customHeaders)
            val epItems = if (config.episodeListSelector.isNotBlank()) {
                doc.select(config.episodeListSelector)
            } else {
                doc.select("a[href*='/episode/'], a[href*='/watch/']")
            }

            val poster = doc.selectFirst("img")?.let { img ->
                img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            }

            for (item in epItems) {
                val linkTag = if (item.tagName() == "a") item else item.selectFirst("a")
                val rawHref = linkTag?.attr("href") ?: continue
                val fullUrl = resolveAbsoluteUrl(rawHref, animeUrl)
                if (fullUrl.isBlank() || episodes.any { it.url == fullUrl }) continue

                val rawText = item.text().trim()
                val customRegex = if (config.episodeNumberRegex.isNotBlank()) Regex(config.episodeNumberRegex) else null
                val epNum = customRegex?.find(rawText)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    ?: customRegex?.find(fullUrl)?.groupValues?.getOrNull(1)?.toFloatOrNull()
                    ?: numberRegex.findAll(fullUrl).lastOrNull()?.value?.toFloatOrNull()
                    ?: numberRegex.findAll(rawText).lastOrNull()?.value?.toFloatOrNull()
                    ?: (episodes.size + 1).toFloat()

                val titleTag = if (config.episodeTitleSelector.isNotBlank()) item.selectFirst(config.episodeTitleSelector) else null
                val epTitle = titleTag?.text()?.trim() ?: rawText.ifBlank { "الحلقة ${epNum.toInt()}" }

                episodes.add(
                    AnimeEpisode(
                        number = epNum,
                        title = epTitle,
                        url = fullUrl,
                        thumbnail = poster,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("TEMPLATE_PROVIDER", "Episode extraction failed for ${definition.name}: ${e.message}")
        }
        return episodes.sortedBy { it.number }
    }

    override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
        val servers = mutableListOf<AnimeStreamServer>()
        try {
            val doc = HttpHelper.getDocument(episodeUrl, headers = definition.customHeaders)

            // 1. Selector-configured servers
            if (config.serverItemSelector.isNotBlank()) {
                doc.select(config.serverItemSelector).forEach { el ->
                    val attr = config.serverUrlAttribute.ifBlank { "href" }
                    val rawUrl = el.attr(attr).ifEmpty { el.attr("href") }.ifEmpty { el.attr("src") }
                    val fullUrl = resolveAbsoluteUrl(rawUrl, episodeUrl)
                    if (fullUrl.startsWith("http") && servers.none { it.url == fullUrl }) {
                        val name = el.text().trim().ifEmpty { "سيرفر ${servers.size + 1}" }
                        val isDirect = SourceLinkExtractor.isDirectVideoUrl(fullUrl)
                        servers.add(
                            AnimeStreamServer(
                                name = name,
                                url = fullUrl,
                                isDirectVideo = isDirect,
                                quality = if (isDirect) "HD" else "ويب",
                                linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                            )
                        )
                    }
                }
            }

            // 2. Embedded iframes fallback
            doc.select("iframe[src]").forEach { iframe ->
                val src = iframe.attr("abs:src")
                if (src.startsWith("http") && !src.contains("acceptable.a-ads") && servers.none { it.url == src }) {
                    val isDirect = SourceLinkExtractor.isDirectVideoUrl(src)
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

            // 3. Fallback: episode page itself
            if (servers.isEmpty()) {
                servers.add(
                    AnimeStreamServer(
                        name = "مشغل ويب",
                        url = episodeUrl,
                        isDirectVideo = false,
                        quality = "ويب",
                        linkType = StreamLinkType.WEB_PAGE,
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("TEMPLATE_PROVIDER", "Server discovery failed for ${definition.name}: ${e.message}")
        }
        return servers
    }

    private fun resolveAbsoluteUrl(url: String, base: String): String {
        val trimmed = url.trim()
        return try {
            when {
                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("/") -> {
                    val baseUri = URI(base)
                    "${baseUri.scheme}://${baseUri.host}$trimmed"
                }
                else -> {
                    val baseUri = URI(base)
                    baseUri.resolve(trimmed).toString()
                }
            }
        } catch (_: Exception) {
            trimmed
        }
    }
}
