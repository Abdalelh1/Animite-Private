package com.imashnake.animite.raw.source

import com.imashnake.animite.raw.model.RawAnimeRelease
import com.imashnake.animite.raw.model.RawDownloadOption
import com.imashnake.animite.raw.model.RawPlaybackAvailability
import com.imashnake.animite.raw.model.RawQuality
import com.imashnake.animite.raw.model.RawSourceDiagnostic
import com.imashnake.animite.raw.model.RawSourceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class SubsPleaseAdapter : RawSourceAdapter {

    override val sourceId: RawSourceId = RawSourceId.SUBSPLEASE
    override val isSupported: Boolean = true

    private val userAgent = "Animite/1.0 (Android; RawModule)"
    private val connectTimeoutMs = 12000
    private val readTimeoutMs = 12000

    override suspend fun getLatestReleases(limit: Int): List<RawAnimeRelease> = withContext(Dispatchers.IO) {
        val apiUrl = "https://subsplease.org/api/?f=latest&tz=UTC"
        try {
            val jsonStr = fetchString(apiUrl)
            val parsed = parseSubsPleaseJson(jsonStr)
            if (parsed.isNotEmpty()) return@withContext parsed.take(limit)
        } catch (_: Exception) {}

        // Fallback to RSS feed
        try {
            val rssUrl = "https://subsplease.org/rss/"
            val rssStr = fetchString(rssUrl)
            return@withContext parseSubsPleaseRss(rssStr).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun searchReleases(query: String, limit: Int): List<RawAnimeRelease> = withContext(Dispatchers.IO) {
        val encodedQuery = withContext(Dispatchers.Default) {
            URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        }
        val apiUrl = "https://subsplease.org/api/?f=search&tz=UTC&s=$encodedQuery"
        try {
            val jsonStr = fetchString(apiUrl)
            val parsed = parseSubsPleaseJson(jsonStr)
            if (parsed.isNotEmpty()) return@withContext parsed.take(limit)
        } catch (_: Exception) {}

        // Fallback: search within RSS or show page
        try {
            val allLatest = getLatestReleases(60)
            val filtered = allLatest.filter {
                it.animeTitle.contains(query, ignoreCase = true) || it.rawTitle.contains(query, ignoreCase = true)
            }
            return@withContext filtered.take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getDiagnostics(): RawSourceDiagnostic = withContext(Dispatchers.IO) {
        val testUrl = "https://subsplease.org/api/?f=latest&tz=UTC"
        var conn: HttpURLConnection? = null
        try {
            val url = URL(testUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", userAgent)
            }
            val code = conn.responseCode
            val isSuccess = code in 200..299
            val itemsCount = if (isSuccess) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseSubsPleaseJson(body).size
            } else 0

            RawSourceDiagnostic(
                sourceId = sourceId,
                isReachable = isSuccess,
                httpStatusCode = code,
                statusMessage = if (isSuccess) "Operational ($itemsCount recent releases indexed)" else "HTTP $code",
                itemsFound = itemsCount
            )
        } catch (e: Exception) {
            RawSourceDiagnostic(
                sourceId = sourceId,
                isReachable = false,
                statusMessage = "Connection failed: ${e.message ?: "Unknown error"}"
            )
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseSubsPleaseJson(jsonString: String): List<RawAnimeRelease> {
        val results = mutableListOf<RawAnimeRelease>()
        val root = try {
            JSONObject(jsonString)
        } catch (_: Exception) {
            return emptyList()
        }

        val keys = root.keys()
        while (keys.hasNext()) {
            val releaseName = keys.next()
            val itemObj = root.optJSONObject(releaseName) ?: continue

            val show = itemObj.optString("show", "")
            val episode = itemObj.optString("episode", "").ifEmpty {
                RawReleaseParserUtils.extractEpisodeNumber(releaseName)
            }
            val time = itemObj.optString("time", "")
            val releaseDate = itemObj.optString("release_date", time)
            val page = itemObj.optString("page", "")
            val pageUrl = if (page.isNotEmpty()) "https://subsplease.org/shows/$page" else null
            val rawImage = itemObj.optString("image_url", "")
            val imageUrl = when {
                rawImage.startsWith("http") -> rawImage
                rawImage.isNotEmpty() -> "https://subsplease.org$rawImage"
                else -> null
            }
            val xdcc = itemObj.optString("xdcc", "")

            val downloadsArray = itemObj.optJSONArray("downloads")
            val downloadOptions = mutableListOf<RawDownloadOption>()
            if (downloadsArray != null) {
                for (i in 0 until downloadsArray.length()) {
                    val dObj = downloadsArray.optJSONObject(i) ?: continue
                    val res = dObj.optString("res", "1080")
                    val magnet = dObj.optString("magnet", "").ifEmpty { null }
                    val q = RawQuality.fromString(res)

                    // Extract file size from magnet link if present (&xl=...)
                    val sizeBytes = magnet?.let { extractBytesFromMagnet(it) }
                    val formattedSize = RawReleaseParserUtils.formatFileSize(sizeBytes)

                    downloadOptions.add(
                        RawDownloadOption(
                            quality = q,
                            resolutionLabel = "${res}p",
                            magnetUrl = magnet,
                            torrentUrl = null,
                            directStreamUrl = null,
                            fileSizeBytes = sizeBytes,
                            formattedSize = formattedSize
                        )
                    )
                }
            }

            val cleanShow = if (show.isNotEmpty()) show else RawReleaseParserUtils.extractCleanShowTitle(releaseName)

            results.add(
                RawAnimeRelease(
                    id = "subsplease_${releaseName.hashCode()}",
                    sourceId = RawSourceId.SUBSPLEASE,
                    rawTitle = releaseName,
                    animeTitle = cleanShow,
                    episodeNumber = episode,
                    releaseDate = releaseDate,
                    downloadOptions = downloadOptions,
                    releaseGroup = "SubsPlease",
                    pageUrl = pageUrl,
                    imageUrl = imageUrl,
                    xdccPrompt = if (xdcc.isNotEmpty()) xdcc else null,
                    directPlayableUrl = null,
                    availability = RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
                )
            )
        }
        return results
    }

    private fun parseSubsPleaseRss(rssContent: String): List<RawAnimeRelease> {
        val results = mutableListOf<RawAnimeRelease>()
        val doc = Jsoup.parse(rssContent, "", org.jsoup.parser.Parser.xmlParser())
        val items = doc.select("item")
        for (item in items) {
            val title = item.selectFirst("title")?.text().orEmpty()
            if (title.isEmpty()) continue
            val link = item.selectFirst("link")?.text().orEmpty()
            val pubDate = item.selectFirst("pubDate")?.text()
            val category = item.selectFirst("category")?.text()

            val cleanShow = RawReleaseParserUtils.extractCleanShowTitle(title)
            val epNumber = RawReleaseParserUtils.extractEpisodeNumber(title)
            val quality = RawReleaseParserUtils.extractQuality(title)

            val downloadOptions = mutableListOf<RawDownloadOption>()
            if (link.startsWith("magnet:", ignoreCase = true)) {
                val sizeBytes = extractBytesFromMagnet(link)
                downloadOptions.add(
                    RawDownloadOption(
                        quality = quality,
                        resolutionLabel = quality.label,
                        magnetUrl = link,
                        fileSizeBytes = sizeBytes,
                        formattedSize = RawReleaseParserUtils.formatFileSize(sizeBytes)
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
                    id = "subsplease_rss_${title.hashCode()}",
                    sourceId = RawSourceId.SUBSPLEASE,
                    rawTitle = title,
                    animeTitle = cleanShow,
                    episodeNumber = epNumber,
                    releaseDate = pubDate,
                    downloadOptions = downloadOptions,
                    releaseGroup = "SubsPlease",
                    directPlayableUrl = null,
                    availability = RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
                )
            )
        }
        return results
    }

    private fun extractBytesFromMagnet(magnet: String): Long? {
        val match = Regex("&xl=(\\d+)").find(magnet)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull()
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
                setRequestProperty("Accept", "application/json, text/xml, */*")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    }
}
