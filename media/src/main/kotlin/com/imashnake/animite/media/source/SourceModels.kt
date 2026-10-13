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
    REGULAR,
    SPECIAL,
    OVA,
    RECAP,
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
)

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
