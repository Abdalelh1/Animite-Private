package com.imashnake.animite.media.source

import android.util.Log
import java.util.Locale

/**
 * Standardized link extractor and quality aggregator.
 * Normalizes quality levels, deduplicates streams, groups redundant servers as fallbacks,
 * and formats qualities in ascending order (lowest to highest + Auto).
 */
object SourceLinkExtractor {

    private const val TAG = "ANIMITE_EXTRACTOR"

    /**
     * Classifies a stream server into a canonical QualityRank.
     */
    fun classifyQuality(server: AnimeStreamServer): QualityRank {
        val q = server.quality.lowercase(Locale.ROOT)
        val name = server.name.lowercase(Locale.ROOT)
        val url = server.url.lowercase(Locale.ROOT)
        val combined = "$q $name $url"

        return when {
            // Adaptive / Multi-quality manifests
            isAdaptiveStream(server.url) && !containsExplicitResolution(combined) -> {
                QualityRank("تلقائي (Auto)", 9999, isAdaptive = true)
            }
            combined.contains("2160") || combined.contains("4k") -> {
                QualityRank("4K (2160p)", 2160)
            }
            combined.contains("1440") || combined.contains("2k") -> {
                QualityRank("1440p 2K", 1440)
            }
            combined.contains("1080") || combined.contains("fhd") || combined.contains("full hd") -> {
                QualityRank("1080p FHD", 1080)
            }
            combined.contains("720") || combined.contains("hd") -> {
                QualityRank("720p HD", 720)
            }
            combined.contains("480") || combined.contains("sd") -> {
                QualityRank("480p SD", 480)
            }
            combined.contains("360") -> {
                QualityRank("360p", 360)
            }
            combined.contains("240") -> {
                QualityRank("240p", 240)
            }
            isAdaptiveStream(server.url) -> {
                QualityRank("تلقائي (Auto)", 9999, isAdaptive = true)
            }
            else -> {
                // Fallback default
                QualityRank("720p HD", 720)
            }
        }
    }

    private fun containsExplicitResolution(text: String): Boolean {
        return text.contains("1080") || text.contains("720") || text.contains("480") || text.contains("360") || text.contains("2160")
    }

    fun isAdaptiveStream(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains(".m3u8") || lower.contains(".mpd")
    }

    fun detectMediaType(url: String): MediaContainerType {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.contains(".m3u8") -> MediaContainerType.HLS
            lower.contains(".mpd") -> MediaContainerType.DASH
            lower.contains(".mp4") || lower.contains("video.vid3rb.com/video/") -> MediaContainerType.MP4
            lower.contains("embed") || lower.contains("player") || lower.contains("iframe") -> MediaContainerType.EMBED
            else -> MediaContainerType.UNKNOWN
        }
    }

    fun isDirectVideoUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.contains("google.com") || lower.contains("facebook.com") ||
            lower.contains("gofile.io") || lower.contains("mega.nz") ||
            lower.contains("workupload.com") || lower.contains("a-ads.com") ||
            lower.contains("acceptable.a-ads") || lower.contains("doubleclick.net")
        ) {
            return false
        }
        return lower.endsWith(".mp4") || lower.contains(".mp4?") || lower.contains(".mp4#") ||
                lower.contains(".m3u8") || lower.endsWith(".mpd") || lower.contains(".mpd?") ||
                lower.contains("video.vid3rb.com/video/")
    }

    fun isPlayableDirectStream(server: AnimeStreamServer): Boolean {
        val url = server.url.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return false
        }

        val lower = url.lowercase(Locale.ROOT)

        // Block known ad trackers and non-video cloud file hosting sites
        if (lower.contains("google.com") || lower.contains("facebook.com") ||
            lower.contains("gofile.io") || lower.contains("mega.nz") ||
            lower.contains("workupload.com") || lower.contains("a-ads.com") ||
            lower.contains("acceptable.a-ads") || lower.contains("doubleclick.net")
        ) {
            return false
        }

        // Direct stream extensions
        val hasDirectExtension = lower.endsWith(".mp4") || lower.contains(".mp4?") || lower.contains(".mp4#") ||
                lower.contains(".m3u8") || lower.endsWith(".mpd") || lower.contains(".mpd?") ||
                lower.contains("video.vid3rb.com/video/")

        // If declared direct video or matches direct extension
        if (server.isDirectVideo || server.linkType == StreamLinkType.DIRECT_VIDEO) {
            // Ensure not a raw mp4upload embed page
            if (lower.contains("mp4upload.com") && !lower.contains("/video.mp4") && !lower.endsWith(".mp4") && !lower.contains(".mp4?")) {
                return false
            }
            return true
        }

        // Block generic embed/player HTML pages if not direct video
        if (lower.contains("/iframe/") || lower.contains("/player/") ||
            lower.contains("share4max.com/iframe") || lower.contains("/embed") ||
            lower.endsWith(".html") || lower.endsWith(".htm")
        ) {
            return false
        }

        return hasDirectExtension
    }

    /**
     * Aggregates servers from multiple providers, normalizes qualities,
     * groups duplicate qualities together with failover fallbacks,
     * and sorts qualities ascending (lowest to highest + Auto).
     */
    fun aggregateAndGroupQualities(
        rawServers: List<AnimeStreamServer>,
        episode: AnimeEpisode,
        participatingProviders: List<String> = emptyList(),
    ): AggregatedPlaybackResult {
        // 1. Deduplicate by exact URL
        val uniqueServers = rawServers.distinctBy { it.url.trim() }

        // 2. Filter direct playable video streams
        val directServers = uniqueServers.filter { isPlayableDirectStream(it) }

        if (directServers.isEmpty()) {
            Log.w(TAG, "No direct playable video streams found out of ${rawServers.size} raw servers")
            val fallbackNonDirect = uniqueServers.filter { it.url.startsWith("http") }
            return AggregatedPlaybackResult(
                success = fallbackNonDirect.isNotEmpty(),
                episode = episode.copy(
                    availabilityState = if (fallbackNonDirect.isNotEmpty()) EpisodeAvailabilityState.DISCOVERED else EpisodeAvailabilityState.UNAVAILABLE
                ),
                bestStream = fallbackNonDirect.firstOrNull(),
                qualityOptions = emptyList(),
                allDirectServers = fallbackNonDirect,
                participatingProviders = participatingProviders,
                errorMessage = if (fallbackNonDirect.isEmpty()) "لم يتم العثور على أي رابط تشغيل صالح" else null,
                discoveryTimestampMs = System.currentTimeMillis(),
            )
        }

        // 3. Group streams by canonical QualityRank
        val groupedByQuality = mutableMapOf<QualityRank, MutableList<AnimeStreamServer>>()
        for (server in directServers) {
            val rank = classifyQuality(server)
            groupedByQuality.getOrPut(rank) { mutableListOf() }.add(server)
        }

        // 4. Build UnifiedQualityOption list
        val qualityOptions = groupedByQuality.map { (rank, servers) ->
            val primary = servers.first()
            val fallbacks = if (servers.size > 1) servers.subList(1, servers.size) else emptyList()
            UnifiedQualityOption(
                quality = rank,
                primaryServer = primary.copy(quality = rank.canonicalName),
                fallbackServers = fallbacks.map { it.copy(quality = rank.canonicalName) },
                mediaType = detectMediaType(primary.url),
            )
        }.sortedBy { it.quality.height } // Strictly ascending: 360p -> 480p -> 720p -> 1080p -> 1440p -> 4K -> Auto (9999)

        // 5. Select best stream for default crisp initial playback
        // Preferred: 1080p FHD, then 720p HD, then Auto, then highest available quality
        val bestOption = qualityOptions.firstOrNull { it.quality.height == 1080 }
            ?: qualityOptions.firstOrNull { it.quality.height == 720 }
            ?: qualityOptions.firstOrNull { it.quality.isAdaptive }
            ?: qualityOptions.maxByOrNull { it.quality.height }
            ?: qualityOptions.first()

        val bestStream = bestOption.primaryServer

        // Flatten all direct servers with standardized quality labels
        val standardizedServers = qualityOptions.flatMap { option ->
            listOf(option.primaryServer) + option.fallbackServers
        }

        Log.i(
            TAG,
            "AGGREGATION_SUCCESS: ep=${episode.number}, total=${standardizedServers.size}, qualities=${qualityOptions.map { it.quality.canonicalName }}"
        )

        return AggregatedPlaybackResult(
            success = true,
            episode = episode.copy(availabilityState = EpisodeAvailabilityState.PLAYABLE),
            bestStream = bestStream,
            qualityOptions = qualityOptions,
            allDirectServers = standardizedServers,
            participatingProviders = participatingProviders,
            discoveryTimestampMs = System.currentTimeMillis(),
        )
    }
}
