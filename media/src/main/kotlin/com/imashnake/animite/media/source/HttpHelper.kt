package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object HttpHelper {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SD1A.210817.036) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"

    private const val TAG = "HttpHelper"
    private const val DEFAULT_CONNECT_TIMEOUT_MS = 12000
    private const val DEFAULT_READ_TIMEOUT_MS = 12000
    private const val MAX_NETWORK_RETRIES = 2

    fun isCloudflareChallenge(html: String): Boolean {
        return html.contains("Just a moment...") ||
                html.contains("cf-browser-verification") ||
                html.contains("challenge-running") ||
                html.contains("turnstile") ||
                html.contains("Attention Required! | Cloudflare") ||
                html.contains("cf-chl-bypass")
    }

    suspend fun getHtml(
        urlString: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
        maxRetries: Int = MAX_NETWORK_RETRIES,
    ): String = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        var attempt = 0

        while (attempt <= maxRetries) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
                    readTimeout = DEFAULT_READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
                    referer?.let { setRequestProperty("Referer", it) }
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "HTTP_RESPONSE (attempt ${attempt + 1}): code=$responseCode for host=${conn.url.host}")

                // Don't retry on client errors (400..499) or Cloudflare
                if (responseCode in 400..499) {
                    val stream = conn.errorStream ?: conn.inputStream
                    val content = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (responseCode == 404) {
                        throw IOException("HTTP 404: Page not found on ${conn.url.host}")
                    }
                    if (isCloudflareChallenge(content)) {
                        return@withContext content
                    }
                    throw IOException("HTTP $responseCode from ${conn.url.host}")
                }

                // If transient server error (502, 503, 504), retry with backoff
                if (responseCode in 502..504 && attempt < maxRetries) {
                    attempt++
                    delay(350L * attempt)
                    continue
                }

                val stream = if (responseCode in 200..399) {
                    conn.inputStream
                } else {
                    conn.errorStream ?: throw IOException("HTTP $responseCode from ${conn.url.host}")
                }

                return@withContext stream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                lastException = e
                val isTransient = e is SocketTimeoutException || e is ConnectException || (e is IOException && e !is java.io.FileNotFoundException)
                if (isTransient && attempt < maxRetries) {
                    attempt++
                    Log.w(TAG, "Transient network issue on $urlString: ${e.message}, retrying attempt $attempt...")
                    delay(350L * attempt)
                } else {
                    throw e
                }
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {}
            }
        }

        throw lastException ?: IOException("Failed to load $urlString after $maxRetries retries")
    }

    suspend fun getDocument(
        urlString: String,
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): Document = withContext(Dispatchers.IO) {
        val html = getHtml(urlString, referer, headers)
        Jsoup.parse(html, urlString)
    }

    suspend fun postJson(
        urlString: String,
        jsonBody: String = "{}",
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = DEFAULT_CONNECT_TIMEOUT_MS
                readTimeout = DEFAULT_READ_TIMEOUT_MS
                instanceFollowRedirects = true
                doOutput = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Content-Type", "application/json")
                referer?.let { setRequestProperty("Referer", it) }
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..399) conn.inputStream else conn.errorStream ?: conn.inputStream
            stream.bufferedReader().use { it.readText() }
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }
}
