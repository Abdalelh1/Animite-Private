package com.imashnake.animite.media.player.cache

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object PlayerCacheModule {

    @Provides
    @Singleton
    fun provideSimpleCache(mediaCacheManager: MediaCacheManager): SimpleCache {
        return mediaCacheManager.simpleCache
    }
}
