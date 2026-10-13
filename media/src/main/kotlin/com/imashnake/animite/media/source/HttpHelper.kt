package com.imashnake.animite.media.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object HttpHelper {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SD1A.210817.036) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36"

    suspend fun getHtml(urlString: String, referer: String? = null, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            setRequestProperty("Accept-Language", "ar,en-US;q=0.9,en;q=0.8")
            referer?.let { setRequestProperty("Referer", it) }
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

        val responseCode = conn.responseCode
        android.util.Log.d("ANIMITE_DIAG", "HTTP_RESPONSE: code=$responseCode for host=${url.host}")
        val stream = if (responseCode in 200..399) conn.inputStream else conn.errorStream ?: conn.inputStream
        BufferedReader(InputStreamReader(stream)).use { reader ->
            val sb = java.lang.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
            sb.toString()
        }
    }

    suspend fun getDocument(urlString: String, referer: String? = null): Document = withContext(Dispatchers.IO) {
        val html = getHtml(urlString, referer)
        Jsoup.parse(html, urlString)
    }

    suspend fun postJson(
        urlString: String,
        jsonBody: String = "{}",
        referer: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
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
        BufferedReader(InputStreamReader(stream)).use { reader ->
            val sb = java.lang.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append('\n')
            }
            sb.toString()
        }
    }
}
