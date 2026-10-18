package com.imashnake.animite.media.source

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CachedAnimeEpisodes(
    val animeTitle: String,
    val providerId: String,
    val matchedUrl: String,
    val episodes: List<AnimeEpisode>,
    val timestampMs: Long = System.currentTimeMillis(),
)

object SourceJsonParser {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encodeServers(servers: List<AnimeStreamServer>): String =
        json.encodeToString(servers)

    fun decodeServers(jsonStr: String): List<AnimeStreamServer> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeEpisodes(episodes: List<AnimeEpisode>): String =
        json.encodeToString(episodes)

    fun decodeEpisodes(jsonStr: String): List<AnimeEpisode> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        return try {
            json.decodeFromString(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

object EpisodeJsonCache {
    private const val CACHE_DIR_NAME = "episodes_json_cache_v4"
    private const val CACHE_EXPIRATION_MS = 6 * 60 * 60 * 1000L // 6 hours

    fun getCachedEpisodes(context: Context, titles: List<String>): CachedAnimeEpisodes? {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) return null

        for (title in titles) {
            if (title.isBlank()) continue
            val key = generateKey(title)
            val file = File(cacheDir, "$key.json")
            if (file.exists() && file.length() > 0) {
                try {
                    val content = file.readText()
                    val cached = SourceJsonParser.json.decodeFromString<CachedAnimeEpisodes>(content)
                    if (cached.episodes.isNotEmpty()) {
                        val sanitized = sanitizeEpisodes(cached.episodes)
                        return cached.copy(episodes = sanitized)
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    fun saveEpisodes(
        context: Context,
        title: String,
        providerId: String,
        matchedUrl: String,
        episodes: List<AnimeEpisode>,
    ) {
        if (episodes.isEmpty()) return
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val sanitized = sanitizeEpisodes(episodes)
            val key = generateKey(title)
            val file = File(cacheDir, "$key.json")
            val cached = CachedAnimeEpisodes(
                animeTitle = title,
                providerId = providerId,
                matchedUrl = matchedUrl,
                episodes = sanitized,
            )
            file.writeText(SourceJsonParser.json.encodeToString(cached))
        } catch (_: Exception) {}
    }

    private fun sanitizeEpisodes(list: List<AnimeEpisode>): List<AnimeEpisode> {
        return list.groupBy { it.number }.map { (_, items) ->
            if (items.size == 1) items.first()
            else {
                val combinedSources = items.flatMap { it.sources }.distinctBy { it.episodeUrl }
                val bestThumb = items.mapNotNull { it.thumbnail }
                    .firstOrNull { it.isNotBlank() && !it.contains("default") && !it.contains("banner") }
                    ?: items.firstNotNullOfOrNull { it.thumbnail }
                val bestTitle = items.mapNotNull { it.title }.maxByOrNull { it.length } ?: items.first().title
                items.first().copy(sources = combinedSources, thumbnail = bestThumb, title = bestTitle)
            }
        }.sortedBy { it.number }
    }

    fun isCacheExpired(cached: CachedAnimeEpisodes): Boolean {
        return (System.currentTimeMillis() - cached.timestampMs) > CACHE_EXPIRATION_MS
    }

    fun clearAnimeCache(context: Context, titles: List<String>) {
        try {
            val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) return
            for (title in titles) {
                if (title.isBlank()) continue
                val key = generateKey(title)
                val file = File(cacheDir, "$key.json")
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun generateKey(title: String): String {
        return title.lowercase().replace(Regex("[^a-z0-9\u0600-\u06FF]"), "_").take(60)
    }
}

object EpisodeSessionRepository {
    private var activeEpisodesList: List<AnimeEpisode> = emptyList()
    private var activeServersList: List<AnimeStreamServer> = emptyList()
    private var activePlaybackResult: AggregatedPlaybackResult? = null
    private var activeInitialUrl: String = ""
    private var activeInitialQuality: String = ""

    fun setActiveEpisodes(episodes: List<AnimeEpisode>) {
        activeEpisodesList = episodes
    }

    fun getActiveEpisodes(): List<AnimeEpisode> = activeEpisodesList

    fun setActiveServers(servers: List<AnimeStreamServer>) {
        activeServersList = servers
    }

    fun getActiveServers(): List<AnimeStreamServer> = activeServersList

    fun setActivePlaybackResult(result: AggregatedPlaybackResult?) {
        activePlaybackResult = result
    }

    fun getActivePlaybackResult(): AggregatedPlaybackResult? = activePlaybackResult

    fun setActiveInitialUrl(url: String) {
        activeInitialUrl = url
    }

    fun getActiveInitialUrl(): String = activeInitialUrl

    fun setActiveInitialQuality(quality: String) {
        activeInitialQuality = quality
    }

    fun getActiveInitialQuality(): String = activeInitialQuality
}
