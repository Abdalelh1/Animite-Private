package com.imashnake.animite.raw.model

import kotlinx.serialization.Serializable

@Serializable
enum class RawSourceId(val displayName: String, val baseUrl: String) {
    SUBSPLEASE("SubsPlease", "https://subsplease.org/"),
    ANIRENA("AniRena", "https://www.anirena.com/"),
    NYAA("Nyaa", "https://nyaa.land/"),
}

@Serializable
enum class RawQuality(val label: String, val height: Int) {
    Q_1080P("1080p FHD", 1080),
    Q_720P("720p HD", 720),
    Q_480P("480p SD", 480),
    UNKNOWN("Unknown", 0);

    companion object {
        fun fromString(str: String?): RawQuality {
            if (str == null) return UNKNOWN
            val s = str.lowercase()
            return when {
                s.contains("1080") -> Q_1080P
                s.contains("720") -> Q_720P
                s.contains("480") || s.contains("sd") -> Q_480P
                else -> UNKNOWN
            }
        }
    }
}

@Serializable
enum class RawPlaybackAvailability {
    PLAYABLE_STREAM_RESOLVED,
    TORRENT_OR_MAGNET_ONLY,
    UNSUPPORTED_OR_BLOCKED,
}

@Serializable
data class RawDownloadOption(
    val quality: RawQuality,
    val resolutionLabel: String,
    val magnetUrl: String? = null,
    val torrentUrl: String? = null,
    val directStreamUrl: String? = null,
    val fileSizeBytes: Long? = null,
    val formattedSize: String? = null,
)

@Serializable
data class RawAnimeRelease(
    val id: String,
    val sourceId: RawSourceId,
    val rawTitle: String,
    val animeTitle: String,
    val episodeNumber: String? = null,
    val releaseDate: String? = null,
    val downloadOptions: List<RawDownloadOption> = emptyList(),
    val seeders: Int? = null,
    val leechers: Int? = null,
    val releaseGroup: String? = null,
    val pageUrl: String? = null,
    val imageUrl: String? = null,
    val xdccPrompt: String? = null,
    val directPlayableUrl: String? = null,
    val availability: RawPlaybackAvailability = if (directPlayableUrl != null) {
        RawPlaybackAvailability.PLAYABLE_STREAM_RESOLVED
    } else {
        RawPlaybackAvailability.TORRENT_OR_MAGNET_ONLY
    },
) {
    val bestQuality: RawQuality
        get() = downloadOptions.map { it.quality }.maxByOrNull { it.height } ?: RawQuality.UNKNOWN

    val primaryMagnet: String?
        get() = downloadOptions.firstOrNull { !it.magnetUrl.isNullOrEmpty() }?.magnetUrl

    val primaryTorrent: String?
        get() = downloadOptions.firstOrNull { !it.torrentUrl.isNullOrEmpty() }?.torrentUrl
}

@Serializable
data class RawSourceDiagnostic(
    val sourceId: RawSourceId,
    val isReachable: Boolean,
    val httpStatusCode: Int? = null,
    val statusMessage: String,
    val isBlockedByCloudflare: Boolean = false,
    val isSiteDefunct: Boolean = false,
    val itemsFound: Int = 0,
    val timestampMs: Long = System.currentTimeMillis(),
)
