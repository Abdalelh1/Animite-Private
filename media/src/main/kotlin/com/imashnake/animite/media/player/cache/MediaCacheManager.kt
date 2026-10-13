package com.imashnake.animite.media.player.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.imashnake.animite.media.source.HttpHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class MediaCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CACHE_DIR_NAME = "media3_video_cache"
        const val PREFS_NAME = "animite_player_cache_prefs"
        const val KEY_CACHE_MAX_BYTES = "cache_max_bytes"

        const val CACHE_LIMIT_250MB = 250L * 1024 * 1024
        const val CACHE_LIMIT_500MB = 500L * 1024 * 1024
        const val CACHE_LIMIT_1GB = 1024L * 1024 * 1024
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var maxCacheBytes: Long
        get() = prefs.getLong(KEY_CACHE_MAX_BYTES, CACHE_LIMIT_500MB)
        set(value) {
            prefs.edit().putLong(KEY_CACHE_MAX_BYTES, value).apply()
        }

    // Application-wide singleton SimpleCache kept open
    val simpleCache: SimpleCache by lazy {
        val cacheFolder = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheFolder.exists()) {
            cacheFolder.mkdirs()
        }
        val databaseProvider = StandaloneDatabaseProvider(context)
        val evictor = LeastRecentlyUsedCacheEvictor(maxCacheBytes)
        SimpleCache(cacheFolder, evictor, databaseProvider)
    }

    fun createCacheDataSourceFactory(customHeaders: Map<String, String> = emptyMap()): CacheDataSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(HttpHelper.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        if (customHeaders.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(customHeaders)
        }

        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun getCurrentCacheSizeBytes(): Long {
        return try {
            simpleCache.cacheSpace
        } catch (_: Exception) {
            0L
        }
    }

    fun clearCache() {
        try {
            val keys = simpleCache.keys.toList()
            for (key in keys) {
                try {
                    simpleCache.removeResource(key)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb < 0.1) {
            "0 MB"
        } else if (mb < 1000.0) {
            String.format(Locale.US, "%.1f MB", mb)
        } else {
            val gb = mb / 1024.0
            String.format(Locale.US, "%.2f GB", gb)
        }
    }
}
