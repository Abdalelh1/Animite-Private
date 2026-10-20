package com.imashnake.animite.raw.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenSubtitlesService @Inject constructor() {

    companion object {
        private const val BASE_URL = "https://api.opensubtitles.com/api/v1"
        const val API_KEY = "2aeTbHmlMFvorPJAKPVvxLW8MGOEwhAA"
        private const val USER_AGENT = "Animeta v1.0"
        private const val TIMEOUT_MS = 15000
    }

    suspend fun searchSubtitles(
        query: String,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
        languages: String = "ar,en",
    ): Result<List<OnlineSubtitle>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlBuilder = StringBuilder("$BASE_URL/subtitles?query=$encodedQuery&languages=$languages")
            if (episodeNumber != null && episodeNumber > 0) {
                urlBuilder.append("&episode_number=$episodeNumber")
            }
            if (seasonNumber != null && seasonNumber > 0) {
                urlBuilder.append("&season_number=$seasonNumber")
            }

            val bodyString = executeGet(urlBuilder.toString())
            val json = JSONObject(bodyString)
            val dataArray = json.optJSONArray("data") ?: return@withContext Result.success(emptyList())

            val results = mutableListOf<OnlineSubtitle>()
            for (i in 0 until dataArray.length()) {
                val item = dataArray.optJSONObject(i) ?: continue
                val id = item.optString("id", "")
                val attributes = item.optJSONObject("attributes") ?: continue

                val languageCode = attributes.optString("language", "ar")
                val languageName = when (languageCode.lowercase()) {
                    "ar" -> "العربية"
                    "en" -> "English"
                    "ja" -> "日本語"
                    else -> languageCode.uppercase()
                }

                val release = attributes.optString("release", attributes.optString("slug", "إصدار غير محدد"))
                val downloadCount = attributes.optInt("download_count", 0)
                val hearingImpaired = attributes.optBoolean("hearing_impaired", false)
                val fps = attributes.optDouble("fps", 0.0)
                val pageUrl = if (attributes.has("url") && !attributes.isNull("url")) {
                    attributes.getString("url")
                } else null

                val filesArray = attributes.optJSONArray("files")
                var fileId: Long? = null
                var fileName = "$release.srt"

                if (filesArray != null && filesArray.length() > 0) {
                    val firstFile = filesArray.getJSONObject(0)
                    fileId = firstFile.optLong("file_id", 0L).takeIf { it > 0 }
                    val reportedName = firstFile.optString("file_name", "")
                    if (reportedName.isNotBlank()) {
                        fileName = reportedName
                        if (!fileName.contains(".")) {
                            fileName = "$fileName.srt"
                        }
                    }
                }

                results.add(
                    OnlineSubtitle(
                        id = id,
                        fileId = fileId,
                        fileName = fileName,
                        languageCode = languageCode,
                        languageName = languageName,
                        release = release,
                        downloadCount = downloadCount,
                        hearingImpaired = hearingImpaired,
                        fps = fps,
                        pageUrl = pageUrl,
                        format = SubtitleFormat.fromFileName(fileName),
                    )
                )
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadSubtitleContent(fileId: Long): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("file_id", fileId)
            }
            val downloadJsonStr = executePost("$BASE_URL/download", payload.toString())
            val json = JSONObject(downloadJsonStr)
            val directLink = json.optString("link", "")
            if (directLink.isBlank()) {
                return@withContext Result.failure(Exception("لم يوفر OpenSubtitles رابط تنزيل مباشر"))
            }

            val bytes = executeGetBytes(directLink)
            Result.success(bytes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGet(urlString: String, redirectCount: Int = 0): String {
        if (redirectCount > 5) throw IllegalStateException("تجاوز عدد عمليات إعادة التوجيه المسموح بها")
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Api-Key", API_KEY)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == 307 || responseCode == 308
            ) {
                var newUrl = conn.getHeaderField("Location") ?: ""
                if (newUrl.startsWith("/")) {
                    newUrl = "https://api.opensubtitles.com$newUrl"
                }
                return executeGet(newUrl, redirectCount + 1)
            }

            if (responseCode !in 200..299) {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Exception("خطأ من سيرفر OpenSubtitles ($responseCode): $errorMsg")
            }

            return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    }

    private fun executePost(urlString: String, jsonBody: String): String {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Api-Key", API_KEY)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Exception("فشل طلب التنزيل ($responseCode): $errorBody")
            }

            return conn.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    }

    private fun executeGetBytes(urlString: String): ByteArray {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn?.disconnect()
        }
    }
}
