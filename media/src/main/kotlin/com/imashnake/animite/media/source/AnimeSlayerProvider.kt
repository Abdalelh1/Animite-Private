package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Provider for AnimeSlayer (https://animeslayer.to).
 * UI display name is obfuscated as "المصدر 5".
 * Note: Subject to Cloudflare Turnstile bot protection on certain routes.
 */
class AnimeSlayerProvider : EpisodeSourceProvider {
    override val id: String = "animeslayer"
    override val name: String = "المصدر 5"
    override val language: String = "ar"

    private val baseUrl = "https://animeslayer.to"

    override suspend fun search(query: String): List<AnimeSearchResult> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$baseUrl/api/search.php?q=$encodedQuery"
        return try {
            val responseText = HttpHelper.getHtml(searchUrl, referer = "$baseUrl/ios")
            if (isCloudflareChallenge(responseText)) {
                Log.w("ANIMITE_DIAG", "ANIMESLAYER_SEARCH_LIMITATION: animeslayer.to is protected by Cloudflare Bot Protection (403/Challenge). Interactive bypass required.")
                return emptyList()
            }
            val results = mutableListOf<AnimeSearchResult>()
            try {
                val element = Json.parseToJsonElement(responseText)
                val jsonArray = element as? JsonArray
                if (jsonArray != null) {
                    for (item in jsonArray) {
                        val obj = item as? JsonObject ?: continue
                        val title = obj["title"]?.jsonPrimitive?.content ?: obj["anime_name"]?.jsonPrimitive?.content ?: ""
                        val href = obj["href"]?.jsonPrimitive?.content ?: obj["url"]?.jsonPrimitive?.content ?: ""
                        val image = obj["image"]?.jsonPrimitive?.content ?: obj["anime_cover_image_url"]?.jsonPrimitive?.content ?: ""

                        if (title.isNotBlank() && href.isNotBlank()) {
                            val fullUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                            results.add(
                                AnimeSearchResult(
                                    id = fullUrl,
                                    title = title.trim(),
                                    url = fullUrl,
                                    poster = image.ifEmpty { null },
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
            results
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "ANIMESLAYER_SEARCH_ERROR: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode> {
        return try {
            val html = HttpHelper.getHtml(animeUrl, referer = "$baseUrl/ios")
            if (isCloudflareChallenge(html)) {
                Log.w("ANIMITE_DIAG", "ANIMESLAYER_EPISODES: Cloudflare challenge detected, skipping")
                return emptyList()
            }
            val episodes = mutableListOf<AnimeEpisode>()

            // Extract episodes list from script: const episodes = [ ... ];
            val episodesMatch = Regex("""const\s+episodes\s*=\s*\[(.*?)\];""", RegexOption.DOT_MATCHES_ALL).find(html)
            if (episodesMatch != null) {
                val block = episodesMatch.groupValues[1]
                val epItemRegex = Regex("""\{\s*n:\s*(\d+(?:\.\d+)?)\s*,\s*title:\s*["']([^"']*)["']\s*,\s*href:\s*["']([^"']*)["'](?:.*?thumb:\s*["']([^"']*)["'])?""")
                epItemRegex.findAll(block).forEach { match ->
                    val epNum = match.groupValues[1].toFloatOrNull() ?: 1f
                    val title = match.groupValues[2].trim()
                    val encHref = match.groupValues[3].trim()
                    val thumb = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }

                    val decryptedHref = decryptXor(encHref, "asxwqa147")
                    val fullEpUrl = when {
                        decryptedHref.startsWith("http") -> decryptedHref
                        decryptedHref.startsWith("/") -> "$baseUrl$decryptedHref"
                        else -> "$baseUrl/$decryptedHref"
                    }

                    episodes.add(
                        AnimeEpisode(
                            number = epNum,
                            title = title.ifEmpty { "الحلقة ${epNum.toInt()}" },
                            url = fullEpUrl,
                            thumbnail = thumb,
                        )
                    )
                }
            }

            episodes.sortedBy { it.number }
        } catch (e: Exception) {
            Log.e("ANIMITE_DIAG", "ANIMESLAYER_EPISODES_ERROR: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
        val servers = mutableListOf<AnimeStreamServer>()
        try {
            val cleanUrl = episodeUrl.substringBefore('#')
            val frag = episodeUrl.substringAfter('#', "a").ifEmpty { "a" }

            val pageHtml = HttpHelper.getHtml(cleanUrl, referer = "$baseUrl/ios")
            if (isCloudflareChallenge(pageHtml)) {
                Log.w("ANIMITE_DIAG", "ANIMESLAYER_SERVERS: Cloudflare challenge detected, skipping")
                return servers
            }

            val nameMatch = Regex("""const\s+name\s*=\s*["']([^"']+)["']""").find(pageHtml)
            val mwsemMatch = Regex("""const\s+mwsem\s*=\s*["']([^"']+)["']""").find(pageHtml)
            val secUrlMatch = Regex("""const\s+secUrl\s*=\s*["']([^"']+)["']""").find(pageHtml)

            val apiName = nameMatch?.groupValues?.get(1) ?: ""
            val mwsem = mwsemMatch?.groupValues?.get(1) ?: "1"
            val secRelUrl = secUrlMatch?.groupValues?.get(1) ?: "/api/v1/sec.php"

            if (apiName.isNotEmpty()) {
                val apiUrl = "$baseUrl$secRelUrl?name=$apiName&pe=$frag&bool=0&mwsem=$mwsem"
                val apiResponse = HttpHelper.getHtml(apiUrl, referer = cleanUrl)
                val jsonObj = try {
                    Json.parseToJsonElement(apiResponse) as? JsonObject
                } catch (_: Exception) {
                    null
                }

                val serversObj = jsonObj?.get("servers") as? JsonObject
                if (serversObj != null) {
                    for ((key, element) in serversObj) {
                        val encUrl = try { element.jsonPrimitive.content } catch (_: Exception) { "" }
                        if (encUrl.isNotEmpty()) {
                            val decrypted = decryptXor(encUrl, "AQWXZSCED@@POIUYTRR159")
                            if (decrypted.startsWith("http")) {
                                val quality = when {
                                    key in listOf("x4", "x14", "x15") -> "1080p FHD"
                                    key in listOf("x9", "x8") -> "720p HD"
                                    key in listOf("x13", "x16") -> "480p SD"
                                    decrypted.contains("FHD", true) || decrypted.contains("1080") -> "1080p FHD"
                                    decrypted.contains("HD", true) || decrypted.contains("720") -> "720p HD"
                                    decrypted.contains("SD", true) || decrypted.contains("480") -> "480p SD"
                                    else -> "HD"
                                }

                                val cleanServerName = when (key) {
                                    "x4" -> "سيرفر رئيسي (1080p)"
                                    "x14" -> "سيرفر سريع (1080p)"
                                    "x15" -> "سيرفر فائق (1080p)"
                                    "x9" -> "سيرفر أساسي (720p)"
                                    "x8" -> "سيرفر ثابت (720p)"
                                    "x13" -> "سيرفر خفيف (480p)"
                                    "x16" -> "سيرفر اقتصادي (480p)"
                                    else -> "سيرفر ${servers.size + 1} ($quality)"
                                }

                                if (decrypted.contains("__wit.php")) {
                                    try {
                                        val playerHtml = HttpHelper.getHtml(decrypted, referer = cleanUrl)
                                        val videoMatches = Regex("""src:\s*['"](https?://[^'"]+)['"]\s*,\s*type:\s*['"][^'"]*['"]\s*,\s*label:\s*['"]([^'"]+)['"]""", RegexOption.DOT_MATCHES_ALL).findAll(playerHtml)
                                        var foundDirect = false
                                        videoMatches.forEach { vm ->
                                            val vSrc = vm.groupValues[1]
                                            val vLabel = vm.groupValues[2]
                                            servers.add(
                                                AnimeStreamServer(
                                                    name = "سيرفر مباشر ($vLabel)",
                                                    url = vSrc,
                                                    isDirectVideo = true,
                                                    quality = vLabel,
                                                    linkType = StreamLinkType.DIRECT_VIDEO,
                                                )
                                            )
                                            foundDirect = true
                                        }
                                        if (!foundDirect) {
                                            val isDirect = decrypted.contains(".mp4") || decrypted.contains(".m3u8")
                                            servers.add(
                                                AnimeStreamServer(
                                                    name = cleanServerName,
                                                    url = decrypted,
                                                    isDirectVideo = isDirect,
                                                    quality = quality,
                                                    linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                                                )
                                            )
                                        }
                                    } catch (_: Exception) {
                                        servers.add(
                                            AnimeStreamServer(
                                                name = cleanServerName,
                                                url = decrypted,
                                                isDirectVideo = decrypted.contains(".mp4") || decrypted.contains(".m3u8"),
                                                quality = quality,
                                            )
                                        )
                                    }
                                } else {
                                    val isDirect = decrypted.contains(".mp4") || decrypted.contains(".m3u8")
                                    servers.add(
                                        AnimeStreamServer(
                                            name = cleanServerName,
                                            url = decrypted,
                                            isDirectVideo = isDirect,
                                            quality = quality,
                                            linkType = if (isDirect) StreamLinkType.DIRECT_VIDEO else StreamLinkType.WEB_PAGE,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
            Log.e("ANIMITE_DIAG", "ANIMESLAYER_SERVERS_ERROR: ${e.message}")
        }
        return servers
    }

    private fun decryptXor(encoded: String, key: String): String {
        return try {
            val decoded = Base64Compat.decode(encoded)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val result = StringBuilder()
            for (i in decoded.indices) {
                result.append((decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toChar())
            }
            result.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun isCloudflareChallenge(html: String): Boolean {
        return html.contains("Just a moment...", ignoreCase = true) ||
                html.contains("cf-chl-bypass", ignoreCase = true) ||
                html.contains("cloudflare", ignoreCase = true) && html.contains("turnstile", ignoreCase = true) ||
                html.contains("Enable JavaScript and cookies to continue", ignoreCase = true)
    }
}
