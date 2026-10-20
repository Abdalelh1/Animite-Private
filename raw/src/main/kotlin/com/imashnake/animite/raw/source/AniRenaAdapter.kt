package com.imashnake.animite.raw.source

import com.imashnake.animite.raw.model.RawAnimeRelease
import com.imashnake.animite.raw.model.RawDownloadOption
import com.imashnake.animite.raw.model.RawPlaybackAvailability
import com.imashnake.animite.raw.model.RawQuality
import com.imashnake.animite.raw.model.RawSourceDiagnostic
import com.imashnake.animite.raw.model.RawSourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AniRenaAdapter : RawSourceAdapter {

    override val sourceId: RawSourceId = RawSourceId.ANIRENA
    override val isSupported: Boolean = false // Site is currently returning HTTP 404

    private val userAgent = "Animite/1.0 (Android; RawModule)"
    private val connectTimeoutMs = 8000
    private val readTimeoutMs = 8000

    override suspend fun getLatestReleases(limit: Int): List<RawAnimeRelease> = withContext(Dispatchers.IO) {
        val rssUrl = "https://www.anirena.com/rss.php"
        try {
            val content = fetchString(rssUrl)
            return@withContext parseAniRenaRss(content).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun searchReleases(query: String, limit: Int): List<RawAnimeRelease> = withContext(Dispatchers.IO) {
        val encoded = withContext(Dispatchers.Default) {
            URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        }
        val searchUrl = "https://www.anirena.com/?s=$encoded"
        try {
            val html = fetchString(searchUrl)
            return@withContext parseAniRenaHtml(html).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getDiagnostics(): RawSourceDiagnostic = withContext(Dispatchers.IO) {
        val testUrl = "https://www.anirena.com/"
        var conn: HttpURLConnection? = null
        try {
            val url = URL(testUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", userAgent)
            }
            val code = conn.responseCode
            val isSuccess = code in 200..299

            RawSourceDiagnostic(
                sourceId = sourceId,
                isReachable = isSuccess,
                httpStatusCode = code,
                statusMessage = when (code) {
                    404 -> "AniRena origin server returned HTTP 404 (endpoint unconfigured or defunct)"
                    503 -> "AniRena service unavailable (HTTP 503)"
                    else -> if (isSuccess) "Operational" else "HTTP $code"
                },
                isSiteDefunct = code == 404
            )
        } catch (e: Exception) {
            RawSourceDiagnostic(
                sourceId = sourceId,
                isReachable = false,
                statusMessage = "AniRena unreachable: ${e.message ?: "Connection failed"}",
                isSiteDefunct = true
            )
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseAniRenaRss(rssContent: String): List<RawAnimeRelease> {
        val results = mutableListOf<RawAnimeRelease>()
        val doc = Jsoup.parse(rssContent, "", org.jsoup.parser.Parser.xmlParser())
        val items = doc.select("item")
        for (item in items) {
            val title = item.selectFirst("title")?.text().orEmpty()
            if (title.isEmpty()) continue
            val link = item.selectFirst("link")?.text().orEmpty()
            val pubDate = item.selectFirst("pubDate")?.text()

            val cleanShow = RawReleaseParserUtils.extractCleanShowTitle(title)
            val ep = RawReleaseParserUtils.extractEpisodeNumber(title)
            val quality = RawReleaseParserUtils.extractQuality(title)

            val downloadOptions = mutableListOf<RawDownloadOption>()
            if (link.startsWith("magnet:", ignoreCase = true)) {
                downloadOptions.add(
                    RawDownloadOption(
                        quality = quality,
                        resolutionLabel = quality.label,
                        magnetUrl = link
                    )
                )
            } else if (link.isNotEmpty()) {
                downloadOptions.add(
                    RawDownloadOption(
                        quality = quality,
                        resolutionLabel = quality.label,
                        torrentUrl = link
                    )
                )
            }

            results.add(
                RawAnimeRelease(
                    id = "anirena_rss_${title.hashCode()}",
                    sourceId = RawSourceId.ANIRENA,
                    rawTitle = title,
                    animeTitle = cleanShow,
                    episodeNumber = ep,
                    releaseDate = pubDate,
                    downloadOptions = downloadOptions,
                    directPlayableUrl = null,
                    availability = RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
                )
            )
        }
        return results
    }

    internal fun parseAniRenaHtml(html: String): List<RawAnimeRelease> {
        val results = mutableListOf<RawAnimeRelease>()
        val doc = Jsoup.parse(html)
        val rows = doc.select("table tbody tr, table.torrents tr")
        for (row in rows) {
            val titleEl = row.selectFirst("a[href*='torrent'], a.title, td.name a") ?: continue
            val title = titleEl.text().trim()
            if (title.isEmpty()) continue
            val pageHref = titleEl.attr("abs:href")

            val magnetEl = row.selectFirst("a[href^='magnet:']")
            val torrentEl = row.selectFirst("a[href*='.torrent']")

            val magnetUrl = magnetEl?.attr("href")
            val torrentUrl = torrentEl?.attr("abs:href")

            val quality = RawReleaseParserUtils.extractQuality(title)
            val cleanShow = RawReleaseParserUtils.extractCleanShowTitle(title)
            val ep = RawReleaseParserUtils.extractEpisodeNumber(title)

            val downloadOptions = mutableListOf<RawDownloadOption>()
            if (!magnetUrl.isNullOrEmpty() || !torrentUrl.isNullOrEmpty()) {
                downloadOptions.add(
                    RawDownloadOption(
                        quality = quality,
                        resolutionLabel = quality.label,
                        magnetUrl = magnetUrl,
                        torrentUrl = torrentUrl
                    )
                )
            }

            results.add(
                RawAnimeRelease(
                    id = "anirena_${title.hashCode()}",
                    sourceId = RawSourceId.ANIRENA,
                    rawTitle = title,
                    animeTitle = cleanShow,
                    episodeNumber = ep,
                    downloadOptions = downloadOptions,
                    pageUrl = pageHref.ifEmpty { null },
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
