package com.imashnake.animite.media.source

import kotlinx.serialization.Serializable

@Serializable
enum class StreamLinkType {
    DIRECT_VIDEO,
    WEB_PAGE,
    DOWNLOAD,
    UNKNOWN,
}

@Serializable
enum class EpisodeType {
    CANON,
    FILLER,
    MIXED,
    SPECIAL,
    OVA,
    ONA,
    MOVIE,
    RECAP,
    UNCLASSIFIED,
    REGULAR;

    val isCanon: Boolean get() = this == CANON || this == REGULAR
    val isFiller: Boolean get() = this == FILLER
    val isMixed: Boolean get() = this == MIXED
    val isSpecial: Boolean get() = this == SPECIAL || this == OVA || this == ONA || this == MOVIE
    val isMovie: Boolean get() = this == MOVIE
    val isRecap: Boolean get() = this == RECAP
    val isUnclassified: Boolean get() = this == UNCLASSIFIED
}

@Serializable
data class SkipInterval(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val skipType: String, // "op" or "ed"
)

@Serializable
enum class PlaybackResolutionState {
    IDLE,
    DISCOVERING,
    RESOLVING,
    READY,
    PLAYING,
    FAILED,
    RETRYING,
    COMPLETED,
}

@Serializable
enum class EpisodeAvailabilityState {
    DISCOVERED,
    CHECKING,
    PLAYABLE,
    UNAVAILABLE;

    val isPlayable: Boolean get() = this == PLAYABLE
    val isChecking: Boolean get() = this == CHECKING
    val isUnavailable: Boolean get() = this == UNAVAILABLE
    val isDiscovered: Boolean get() = this == DISCOVERED
}

@Serializable
data class AnimeSearchResult(
    val id: String,
    val title: String,
    val url: String,
    val poster: String? = null,
)

@Serializable
data class EpisodeSourceReference(
    val providerId: String,
    val episodeUrl: String,
    val qualityHint: String? = null,
    val sourceName: String? = null,
)

@Serializable
data class AnimeEpisode(
    val number: Float,
    val title: String? = null,
    val url: String = "",
    val thumbnail: String? = null,
    val episodeType: EpisodeType = EpisodeType.REGULAR,
    val seasonNumber: Int = 1,
    val sources: List<EpisodeSourceReference> = emptyList(),
    val skipIntervals: List<SkipInterval> = emptyList(),
    val availabilityState: EpisodeAvailabilityState = if (sources.isNotEmpty() || url.isNotBlank()) EpisodeAvailabilityState.DISCOVERED else EpisodeAvailabilityState.UNAVAILABLE,
) {
    val primarySourceProviderId: String? get() = sources.firstOrNull()?.providerId
    val primarySourceName: String? get() = sources.firstOrNull()?.sourceName
    val hasAlternativeSources: Boolean get() = sources.size > 1
}

@Serializable
data class AnimeStreamServer(
    val name: String,
    val url: String,
    val isDirectVideo: Boolean = false,
    val quality: String = "HD",
    val linkType: StreamLinkType = if (isDirectVideo) StreamLinkType.DIRECT_VIDEO else StreamLinkType.UNKNOWN,
    val headers: Map<String, String> = emptyMap(),
)

interface EpisodeSourceProvider {
    val id: String
    val name: String
    val language: String // "ar" or "en"

    suspend fun search(query: String): List<AnimeSearchResult>
    suspend fun getEpisodes(animeUrl: String): List<AnimeEpisode>
    suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer>
}

@Serializable
enum class MediaContainerType {
    MP4,
    HLS,
    DASH,
    EMBED,
    UNKNOWN,
}

@Serializable
data class QualityRank(
    val canonicalName: String,
    val height: Int,
    val isAdaptive: Boolean = false,
) : Comparable<QualityRank> {
    override fun compareTo(other: QualityRank): Int = this.height.compareTo(other.height)
}

@Serializable
data class UnifiedQualityOption(
    val quality: QualityRank,
    val primaryServer: AnimeStreamServer,
    val fallbackServers: List<AnimeStreamServer> = emptyList(),
    val mediaType: MediaContainerType = MediaContainerType.MP4,
)

@Serializable
data class AggregatedPlaybackResult(
    val success: Boolean,
    val episode: AnimeEpisode,
    val bestStream: AnimeStreamServer? = null,
    val qualityOptions: List<UnifiedQualityOption> = emptyList(),
    val allDirectServers: List<AnimeStreamServer> = emptyList(),
    val participatingProviders: List<String> = emptyList(),
    val errorMessage: String? = null,
    val discoveryTimestampMs: Long = 0L,
    val state: PlaybackResolutionState = if (success) PlaybackResolutionState.READY else PlaybackResolutionState.FAILED,
)
