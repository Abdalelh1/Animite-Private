package com.imashnake.animite

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AnimiteApplication: Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        com.imashnake.animite.media.source.SourceRepositoryManager.initialize(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .logger(DebugLogger())
            .build()
    }
}
