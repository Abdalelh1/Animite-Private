package com.imashnake.animite.raw.source

import com.imashnake.animite.raw.model.RawAnimeRelease
import com.imashnake.animite.raw.model.RawDownloadOption
import com.imashnake.animite.raw.model.RawPlaybackAvailability
import com.imashnake.animite.raw.model.RawSourceDiagnostic
import com.imashnake.animite.raw.model.RawSourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class NyaaAdapter : RawSourceAdapter {

    override val sourceId: RawSourceId = RawSourceId.NYAA
    override val isSupported: Boolean = false // Protected by Cloudflare Turnstile challenge on nyaa.land

    private val userAgent = "Mozilla/5.0 (Android; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0"
    private val connectTimeoutMs = 8000
    private val readTimeoutMs = 8000

    override suspend fun getLatestReleases(limit: Int): List<RawAnimeRelease> = withContext(Dispatchers.IO) {
        val rssUrl = "https://nyaa.land/?page=rss&c=1_0"
        try {
            val content = fetchString(rssUrl)
            return@withContext parseNyaaRss(content).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun searchReleases(query: String, limit: Int): List<RawAnimeRelease> = withContext(Dispatchers.IO) {
        val encoded = withContext(Dispatchers.Default) {
            URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        }
        val rssUrl = "https://nyaa.land/?page=rss&q=$encoded"
        try {
            val content = fetchString(rssUrl)
            return@withContext parseNyaaRss(content).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getDiagnostics(): RawSourceDiagnostic = withContext(Dispatchers.IO) {
        val testUrl = "https://nyaa.land/?page=rss"
        var conn: HttpURLConnection? = null
        try {
            val url = URL(testUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
            }
            val code = conn.responseCode
            val isSuccess = code in 200..299
            val cfMitigated = conn.getHeaderField("cf-mitigated") ?: ""
            val isCfBlocked = code == 403 || cfMitigated.contains("challenge", ignoreCase = true)

            RawSourceDiagnostic(
                sourceId = sourceId,
                isReachable = isSuccess,
                httpStatusCode = code,
                statusMessage = when {
                    isCfBlocked -> "Nyaa.land is protected by Cloudflare bot protection (HTTP 403 Challenge)"
                    code == 404 -> "Nyaa endpoint not found (HTTP 404)"
                    isSuccess -> "Operational"
                    else -> "HTTP $code"
                },
                isBlockedByCloudflare = isCfBlocked
            )
        } catch (e: Exception) {
            RawSourceDiagnostic(
                sourceId = sourceId,
                isReachable = false,
                statusMessage = "Nyaa unreachable: ${e.message ?: "Connection failed"}"
            )
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseNyaaRss(rssContent: String): List<RawAnimeRelease> {
        val results = mutableListOf<RawAnimeRelease>()
        val doc = Jsoup.parse(rssContent, "", org.jsoup.parser.Parser.xmlParser())
        val items = doc.select("item")
        for (item in items) {
            val title = item.selectFirst("title")?.text().orEmpty()
            if (title.isEmpty()) continue
            val link = item.selectFirst("link")?.text().orEmpty()
            val pubDate = item.selectFirst("pubDate")?.text()

            // Nyaa namespace tags
            val seedersStr = item.selectFirst("nyaa|seeders, seeders")?.text()
            val leechersStr = item.selectFirst("nyaa|leechers, leechers")?.text()
            val sizeStr = item.selectFirst("nyaa|size, size")?.text()

            val seeders = seedersStr?.toIntOrNull()
            val leechers = leechersStr?.toIntOrNull()

            val cleanShow = RawReleaseParserUtils.extractCleanShowTitle(title)
            val ep = RawReleaseParserUtils.extractEpisodeNumber(title)
            val quality = RawReleaseParserUtils.extractQuality(title)
            val group = RawReleaseParserUtils.extractReleaseGroup(title)

            val downloadOptions = mutableListOf<RawDownloadOption>()
            val enclosureUrl = item.selectFirst("enclosure")?.attr("url")

            val magnetUrl = if (link.startsWith("magnet:", ignoreCase = true)) link else null
            val torrentUrl = if (enclosureUrl?.isNotEmpty() == true) enclosureUrl else if (!link.startsWith("magnet:")) link else null

            downloadOptions.add(
                RawDownloadOption(
                    quality = quality,
                    resolutionLabel = quality.label,
                    magnetUrl = magnetUrl,
                    torrentUrl = torrentUrl,
                    formattedSize = sizeStr
                )
            )

            results.add(
                RawAnimeRelease(
                    id = "nyaa_${title.hashCode()}",
                    sourceId = RawSourceId.NYAA,
                    rawTitle = title,
                    animeTitle = cleanShow,
                    episodeNumber = ep,
                    releaseDate = pubDate,
                    downloadOptions = downloadOptions,
                    seeders = seeders,
                    leechers = leechers,
                    releaseGroup = group,
                    pageUrl = if (!link.startsWith("magnet:")) link else null,
                    directPlayableUrl = null,
                    availability = RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
                )
            )
        }
        return results
    }

    private fun fetchString(urlString: String): String {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/rss+xml, text/xml, */*")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from $urlString")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    }
}
