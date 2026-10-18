package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import java.util.Locale

/**
 * Phase 6: Direct-Stream Extraction & Token Renewal Engine.
 * Extracts direct playable video streams (MP4, HLS/m3u8, DASH/mpd)
 * from popular third-party hosting services used by anime providers,
 * ensuring no raw web embed pages or expired tokens are passed to ExoPlayer.
 */
object DirectStreamExtractor {

    private const val TAG = "DIRECT_EXTRACTOR"

    /**
     * Identifies if a URL belongs to a supported embed video host.
     */
    fun canExtract(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("vid3rb.com") ||
                lower.contains("megamax.me") ||
                lower.contains("share4max.com") ||
                lower.contains("mp4upload.com") ||
                lower.contains("vidbom.com") ||
                lower.contains("vidshare.com") ||
                lower.contains("voe.sx") ||
                lower.contains("__wit.php")
    }

    /**
     * Extracts direct playable video servers from an embed host URL or provided HTML content.
     */
    suspend fun extractDirectStreams(
        embedUrl: String,
        referer: String? = null,
        htmlContent: String? = null,
    ): List<AnimeStreamServer> {
        val servers = mutableListOf<AnimeStreamServer>()
        val lower = embedUrl.lowercase(Locale.ROOT)

        try {
            when {
                // 1. Vid3rb Player
                lower.contains("vid3rb.com") -> {
                    val html = htmlContent ?: HttpHelper.getHtml(embedUrl, referer = referer)
                    servers.addAll(Anime3rbHtmlParser.parsePlayerSources(html, embedUrl))
                }

                // 2. Share4max / Megamax
                lower.contains("share4max.com") || lower.contains("megamax.me") -> {
                    servers.addAll(extractShare4max(embedUrl, referer, htmlContent))
                }

                // 3. Mp4Upload
                lower.contains("mp4upload.com") -> {
                    val directUrl = extractMp4Upload(embedUrl, htmlContent)
                    if (!directUrl.isNullOrEmpty()) {
                        servers.add(
                            AnimeStreamServer(
                                name = "سيرفر مباشر (Mp4Upload)",
                                url = directUrl,
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
                }

                // 4. VidBom / VidShare
                lower.contains("vidbom.com") || lower.contains("vidshare.com") -> {
                    val html = htmlContent ?: HttpHelper.getHtml(embedUrl, referer = referer)
                    servers.addAll(extractVidBom(html, embedUrl))
                }

                // 5. Generic HTML5 / VideoJS Embed
                else -> {
                    val html = htmlContent ?: HttpHelper.getHtml(embedUrl, referer = referer)
                    servers.addAll(extractGenericHtml5(html, embedUrl))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "EXTRACTION_FAIL for $embedUrl: ${e.message}")
        }

        return servers.filter { SourceLinkExtractor.isPlayableDirectStream(it) }
    }

    /**
     * Extracts direct video mirrors from Megamax / Share4max Inertia props or HTML.
     */
    fun extractShare4max(
        embedUrl: String,
        referer: String? = null,
        htmlContent: String? = null,
    ): List<AnimeStreamServer> {
        val mirrors = mutableListOf<AnimeStreamServer>()
        try {
            val html = htmlContent ?: ""
            val jsonText = if (html.trim().startsWith("{")) {
                html
            } else {
                Regex("""data-page=["']([^"']+)["']""").find(html)?.groupValues?.get(1)?.let {
                    org.jsoup.parser.Parser.unescapeEntities(it, false)
                } ?: ""
            }

            if (jsonText.isNotEmpty()) {
                val element = Json.parseToJsonElement(jsonText)
                val props = (element as? JsonObject)?.get("props") as? JsonObject
                val streams = props?.get("streams") as? JsonObject
                val data = streams?.get("data") as? JsonArray
                data?.forEach { streamItem ->
                    val sObj = streamItem as? JsonObject ?: return@forEach
                    val label = (sObj["label"] as? JsonPrimitive)?.content ?: "1080p FHD"
                    val mirrorList = sObj["mirrors"] as? JsonArray
                    mirrorList?.forEach { mItem ->
                        val mObj = mItem as? JsonObject ?: return@forEach
                        val driver = (mObj["driver"] as? JsonPrimitive)?.content ?: "mirror"
                        var link = (mObj["link"] as? JsonPrimitive)?.content ?: ""
                        if (link.startsWith("//")) link = "https:$link"

                        if (link.startsWith("http")) {
                            val isDirect = link.endsWith(".mp4") || link.contains(".m3u8")
                            if (isDirect) {
                                mirrors.add(
                                    AnimeStreamServer(
                                        name = "سيرفر $driver ($label)",
                                        url = link,
                                        isDirectVideo = true,
                                        quality = label,
                                        linkType = StreamLinkType.DIRECT_VIDEO,
                                        headers = mapOf("Referer" to embedUrl),
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SHARE4MAX_EXTRACT_ERROR: ${e.message}")
        }
        return mirrors
    }

    /**
     * Extracts direct .mp4 from Mp4Upload embed HTML or URL.
     */
    fun extractMp4Upload(embedUrl: String, htmlContent: String? = null): String? {
        val html = htmlContent ?: return null
        val match = Regex("""(?i)player\.src\(\s*\{\s*type:\s*["'][^"']+["'],\s*src:\s*["']([^"']+\.mp4[^"']*)["']""").find(html)
            ?: Regex("""(?i)src:\s*["'](https?://[^"']+\.mp4[^"']*)["']""").find(html)
            ?: Regex("""['"](https?://[^'"]*mp4upload[^'"]*\.mp4[^'"]*)['"]""").find(html)
        return match?.groupValues?.get(1)
    }

    /**
     * Extracts direct video streams from VidBom / VidShare embed HTML.
     */
    fun extractVidBom(html: String, embedUrl: String): List<AnimeStreamServer> {
        val servers = mutableListOf<AnimeStreamServer>()

        // Check for packed javascript
        val unpackedHtml = if (html.contains("eval(function(p,a,c,k,e,d)")) {
            unpackJavaScript(html)
        } else {
            html
        }

        // Search for file: "..." or src: "..." in jwplayer/video setup
        val sourceMatches = Regex("""(?:file|src|url)\s*:\s*["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""").findAll(unpackedHtml)
        for (m in sourceMatches) {
            val streamUrl = m.groupValues[1]
            if (streamUrl.isNotEmpty() && !servers.any { it.url == streamUrl }) {
                val isHls = streamUrl.contains(".m3u8")
                servers.add(
                    AnimeStreamServer(
                        name = if (isHls) "VidBom HLS" else "VidBom مباشر",
                        url = streamUrl,
                        isDirectVideo = true,
                        quality = if (isHls) "Auto" else "HD",
                        linkType = StreamLinkType.DIRECT_VIDEO,
                        headers = mapOf("Referer" to embedUrl),
                    )
                )
            }
        }

        // Direct regex for mp4/m3u8 in unpacked script
        if (servers.isEmpty()) {
            val directMatches = Regex("""["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""").findAll(unpackedHtml)
            for (m in directMatches) {
                val streamUrl = m.groupValues[1]
                if (!streamUrl.contains("google") && !streamUrl.contains("analytics") && !servers.any { it.url == streamUrl }) {
                    servers.add(
                        AnimeStreamServer(
                            name = "VidBom مباشر",
                            url = streamUrl,
                            isDirectVideo = true,
                            quality = "HD",
                            linkType = StreamLinkType.DIRECT_VIDEO,
                            headers = mapOf("Referer" to embedUrl),
                        )
                    )
                }
            }
        }

        return servers
    }

    /**
     * Extracts direct streams from generic HTML5 video or source tags.
     */
    fun extractGenericHtml5(html: String, embedUrl: String): List<AnimeStreamServer> {
        val servers = mutableListOf<AnimeStreamServer>()
        val doc = Jsoup.parse(html, embedUrl)

        doc.select("video source[src], video[src]").forEach { el ->
            val src = el.attr("abs:src").ifEmpty { el.attr("src") }
            if (SourceLinkExtractor.isDirectVideoUrl(src) && !servers.any { it.url == src }) {
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

        return servers
    }

    /**
     * Unpacks Dean Edwards packed JavaScript: eval(function(p,a,c,k,e,d)...)
     */
    fun unpackJavaScript(script: String): String {
        return try {
            val pattern = Regex("""eval\(function\(p,a,c,k,e,[rd]\)\{.*\}\('((?:[^'\\]|\\.)*)',(\d+),(\d+),'((?:[^'\\]|\\.)*)'\.split\('\|'\)""")
            val match = pattern.find(script) ?: return script

            val p = match.groupValues[1].replace("\\'", "'")
            val a = match.groupValues[2].toInt()
            val c = match.groupValues[3].toInt()
            val k = match.groupValues[4].split('|')

            fun baseN(num: Int, radix: Int): String {
                val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                if (num == 0) return "0"
                var n = num
                val sb = StringBuilder()
                while (n > 0) {
                    sb.append(digits[n % radix])
                    n /= radix
                }
                return sb.reverse().toString()
            }

            var result = p
            for (i in c - 1 downTo 0) {
                val key = if (i < k.size && k[i].isNotEmpty()) k[i] else baseN(i, a)
                val wordRegex = Regex("""\b${baseN(i, a)}\b""")
                result = result.replace(wordRegex, key)
            }
            result
        } catch (_: Exception) {
            script
        }
    }
}
