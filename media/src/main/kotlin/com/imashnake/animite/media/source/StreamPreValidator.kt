package com.imashnake.animite.media.source

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 7: Stream Pre-Validation Engine.
 * Validates stream reachability and availability using lightweight HTTP byte-range probes
 * (checking for 200/206 vs. 403/404/dead links) before presenting streams to the player,
 * silently eliminating broken mirrors and promoting working fallbacks.
 */
object StreamPreValidator {

    private const val TAG = "STREAM_PRE_VALIDATOR"

    /**
     * Probes an individual stream server via HTTP HEAD or GET byte-range (0-1).
     * Returns true if the server responds with 200 OK, 206 Partial Content, or 3xx redirect.
     */
    suspend fun probeStream(
        server: AnimeStreamServer,
        timeoutMs: Long = 3500L,
    ): Boolean = withContext(Dispatchers.IO) {
        val urlStr = server.url.trim()
        if (!urlStr.startsWith("http://", ignoreCase = true) && !urlStr.startsWith("https://", ignoreCase = true)) {
            return@withContext false
        }

        // Web embed pages are assumed valid without byte-range probing
        if (!server.isDirectVideo) {
            return@withContext true
        }

        try {
            withTimeoutOrNull(timeoutMs) {
                var code = executeProbe(urlStr, "HEAD", server.headers, timeoutMs)
                // Fallback to GET range 0-1 if HEAD is disallowed or returns 405/403/400
                if (code == 405 || code == 403 || code == 400 || code == 404) {
                    val rangeHeaders = server.headers.toMutableMap().apply {
                        put("Range", "bytes=0-1")
                    }
                    code = executeProbe(urlStr, "GET", rangeHeaders, timeoutMs)
                }
                code in 200..399
            } ?: false
        } catch (e: Exception) {
            Log.d(TAG, "PROBE_FAIL for ${server.url}: ${e.message}")
            false
        }
    }

    private fun executeProbe(
        urlStr: String,
        method: String,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): Int {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = timeoutMs.toInt()
            readTimeout = timeoutMs.toInt()
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", HttpHelper.USER_AGENT)
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val responseCode = conn.responseCode
        conn.disconnect()
        return responseCode
    }

    /**
     * Pre-validates a collection of stream servers concurrently, filtering out dead links.
     */
    suspend fun preValidateServers(
        servers: List<AnimeStreamServer>,
        timeoutMs: Long = 3500L,
    ): List<AnimeStreamServer> = coroutineScope {
        if (servers.isEmpty()) return@coroutineScope emptyList()

        val deferredProbes = servers.map { server ->
            async(Dispatchers.IO) {
                val alive = probeStream(server, timeoutMs)
                if (alive) server else null
            }
        }

        val aliveServers = deferredProbes.awaitAll().filterNotNull()
        // If all probes failed (e.g. firewall in restricted network or test harness),
        // gracefully retain original servers to prevent blocking valid playback.
        if (aliveServers.isNotEmpty()) aliveServers else servers
    }

    /**
     * Validates a UnifiedQualityOption: if the primary server is dead but a fallback is alive,
     * promotes the healthy fallback to primary and filters out the dead mirror.
     */
    suspend fun preValidateQualityOption(
        option: UnifiedQualityOption,
        timeoutMs: Long = 3500L,
    ): UnifiedQualityOption? {
        val allServers = listOf(option.primaryServer) + option.fallbackServers
        val aliveServers = preValidateServers(allServers, timeoutMs)

        if (aliveServers.isEmpty()) {
            return null
        }

        val newPrimary = aliveServers.first()
        val newFallbacks = if (aliveServers.size > 1) aliveServers.subList(1, aliveServers.size) else emptyList()

        return option.copy(
            primaryServer = newPrimary,
            fallbackServers = newFallbacks,
        )
    }
}
