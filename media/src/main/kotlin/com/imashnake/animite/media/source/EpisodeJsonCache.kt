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
    private const val CACHE_DIR_NAME = "episodes_json_cache_v3"
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
                        return cached
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

            val key = generateKey(title)
            val file = File(cacheDir, "$key.json")
            val cached = CachedAnimeEpisodes(
                animeTitle = title,
                providerId = providerId,
                matchedUrl = matchedUrl,
                episodes = episodes,
            )
            file.writeText(SourceJsonParser.json.encodeToString(cached))
        } catch (_: Exception) {}
    }

    fun isCacheExpired(cached: CachedAnimeEpisodes): Boolean {
        return (System.currentTimeMillis() - cached.timestampMs) > CACHE_EXPIRATION_MS
    }

    private fun generateKey(title: String): String {
        return title.lowercase().replace(Regex("[^a-z0-9\u0600-\u06FF]"), "_").take(60)
    }
}

object EpisodeSessionRepository {
    private var activeEpisodesList: List<AnimeEpisode> = emptyList()
    private var activeServersList: List<AnimeStreamServer> = emptyList()

    fun setActiveEpisodes(episodes: List<AnimeEpisode>) {
        activeEpisodesList = episodes
    }

    fun getActiveEpisodes(): List<AnimeEpisode> = activeEpisodesList

    fun setActiveServers(servers: List<AnimeStreamServer>) {
        activeServersList = servers
    }

    fun getActiveServers(): List<AnimeStreamServer> = activeServersList
}
