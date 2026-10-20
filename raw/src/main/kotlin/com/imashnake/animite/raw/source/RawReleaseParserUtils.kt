package com.imashnake.animite.raw.source

import com.imashnake.animite.raw.model.RawQuality
import java.util.regex.Pattern

object RawReleaseParserUtils {

    private val GROUP_REGEX = Pattern.compile("^\\[([^\\]]+)\\]").toRegex()
    private val EPISODE_REGEX = Pattern.compile("(?i)(?:\\s-\\s|\\s+Episode\\s+|\\s+Ep\\.?\\s*)(\\d+(?:\\.\\d+)?)(?:v\\d+)?(?=[\\s\\(\\[]|\$)").toRegex()
    private val QUALITY_REGEX = Pattern.compile("(?i)\\b(2160p|1080p|720p|480p|540p)\\b").toRegex()
    private val CRC_REGEX = Pattern.compile("\\[[0-9a-fA-F]{8}\\]").toRegex()

    fun extractReleaseGroup(rawTitle: String): String? {
        val match = GROUP_REGEX.find(rawTitle.trim())
        return match?.groupValues?.getOrNull(1)
    }

    fun extractEpisodeNumber(rawTitle: String): String? {
        val clean = rawTitle.replace(CRC_REGEX, "").trim()
        val match = EPISODE_REGEX.find(clean)
        return match?.groupValues?.getOrNull(1)
    }

    fun extractQuality(rawTitle: String): RawQuality {
        val match = QUALITY_REGEX.find(rawTitle)
        return if (match != null) {
            RawQuality.fromString(match.value)
        } else {
            RawQuality.UNKNOWN
        }
    }

    fun extractCleanShowTitle(rawTitle: String): String {
        var clean = rawTitle.trim()
        // Remove leading group [Group]
        clean = GROUP_REGEX.replace(clean, "").trim()
        // Remove CRC [ABCD1234]
        clean = CRC_REGEX.replace(clean, "").trim()

        // Remove episode part and trailing stuff
        val epMatch = EPISODE_REGEX.find(clean)
        if (epMatch != null) {
            clean = clean.substring(0, epMatch.range.first).trim()
        }

        // Remove resolution tags like (1080p) or [720p]
        clean = clean.replace(Regex("(?i)[\\[\\(](?:2160p|1080p|720p|480p|batch|mkv|mp4)[^\\]\\)]*[\\]\\)]"), "").trim()
        return clean.replace(Regex("\\s+"), " ").trim(' ', '-', '_')
    }

    fun formatFileSize(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "Unknown"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            else -> String.format(java.util.Locale.US, "%.0f KB", kb)
        }
    }
}
