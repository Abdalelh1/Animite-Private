package com.imashnake.animite.media.source

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

data class CachedStreamResult(
    val result: AggregatedPlaybackResult,
    val cachedAtMs: Long = System.currentTimeMillis(),
    val ttlMs: Long = 60 * 60 * 1000L, // 1 hour TTL for temporary stream tokens
) {
    val isExpired: Boolean
        get() = (System.currentTimeMillis() - cachedAtMs) > ttlMs
}

data class ServerHealthRecord(
    var consecutiveFailures: Int = 0,
    var lastFailureTimeMs: Long = 0L,
    var lastSuccessTimeMs: Long = 0L,
    var lastFailureReason: String? = null,
)

/**
 * Manages caching of stable data and stream results with proper TTL expiration,
 * alongside tracking server failure/success history for adaptive prioritization.
 */
object SmartStreamCache {

    private const val TAG = "SMARTSTREAM_CACHE"

    // Episode stream cache: key = animeTitle + episodeNumber
    private val streamCache = ConcurrentHashMap<String, CachedStreamResult>()

    // Health tracker: key = host/domain or providerId
    private val healthMap = ConcurrentHashMap<String, ServerHealthRecord>()

    private const val FAILURE_PENALTY_WINDOW_MS = 5 * 60 * 1000L // 5 minutes penalty window
    private const val MAX_CONSECUTIVE_FAILURES_BEFORE_SKIP = 3

    private fun buildKey(animeTitle: String, episodeNumber: Float): String {
        return "${animeTitle.trim().lowercase()}_ep_$episodeNumber"
    }

    private fun extractDomain(url: String): String {
        return try {
            val withoutScheme = if (url.contains("://")) url.substringAfter("://") else url
            val hostPort = withoutScheme.substringBefore('/').substringBefore('?')
            hostPort.ifBlank { url }
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Retrieves valid cached stream result if not expired.
     */
    fun getValidStreamResult(animeTitle: String, episodeNumber: Float): AggregatedPlaybackResult? {
        val key = buildKey(animeTitle, episodeNumber)
        val cached = streamCache[key] ?: return null

        if (cached.isExpired) {
            Log.d(TAG, "CACHE_EXPIRED: Stream for $key has expired (${cached.cachedAtMs}). Evicting.")
            streamCache.remove(key)
            return null
        }

        Log.d(TAG, "CACHE_HIT: Returning valid stream result for $key")
        return cached.result
    }

    /**
     * Stores stream result with 1-hour TTL.
     */
    fun putStreamResult(animeTitle: String, episodeNumber: Float, result: AggregatedPlaybackResult) {
        val key = buildKey(animeTitle, episodeNumber)
        streamCache[key] = CachedStreamResult(result = result)
        Log.d(TAG, "CACHE_PUT: Stored stream result for $key (TTL: 1 hour)")
    }

    /**
     * Explicitly invalidates a stream result (e.g. after playback 403/expired link error).
     */
    fun invalidateStream(animeTitle: String, episodeNumber: Float) {
        val key = buildKey(animeTitle, episodeNumber)
        streamCache.remove(key)
        Log.i(TAG, "CACHE_INVALIDATED: Evicted stream for $key")
    }

    /**
     * Records a server failure to track server health.
     */
    fun recordServerFailure(urlOrProvider: String, reason: String) {
        val domain = extractDomain(urlOrProvider)
        val record = healthMap.getOrPut(domain) { ServerHealthRecord() }
        record.consecutiveFailures++
        record.lastFailureTimeMs = System.currentTimeMillis()
        record.lastFailureReason = reason
        Log.w(TAG, "HEALTH_FAIL: domain=$domain, fails=${record.consecutiveFailures}, reason=$reason")
    }

    /**
     * Records a server success to restore its priority.
     */
    fun recordServerSuccess(urlOrProvider: String) {
        val domain = extractDomain(urlOrProvider)
        val record = healthMap.getOrPut(domain) { ServerHealthRecord() }
        record.consecutiveFailures = 0
        record.lastSuccessTimeMs = System.currentTimeMillis()
        record.lastFailureReason = null
        Log.d(TAG, "HEALTH_SUCCESS: domain=$domain restored")
    }

    /**
     * Checks whether a server should be deprioritized due to recent repeated failures.
     */
    fun isServerDeprioritized(urlOrProvider: String): Boolean {
        val domain = extractDomain(urlOrProvider)
        val record = healthMap[domain] ?: return false
        val now = System.currentTimeMillis()
        return record.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES_BEFORE_SKIP &&
                (now - record.lastFailureTimeMs) < FAILURE_PENALTY_WINDOW_MS
    }

    fun clearAll() {
        streamCache.clear()
        healthMap.clear()
    }
}
