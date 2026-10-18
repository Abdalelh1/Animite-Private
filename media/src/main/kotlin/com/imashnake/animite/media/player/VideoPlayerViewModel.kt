package com.imashnake.animite.media.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.imashnake.animite.media.player.cache.MediaCacheManager
import com.imashnake.animite.media.source.AnimeEpisode
import com.imashnake.animite.media.source.AnimeStreamServer
import com.imashnake.animite.media.source.EpisodeSourcesManager
import com.imashnake.animite.media.source.HttpHelper
import com.imashnake.animite.media.source.SkipInterval
import com.imashnake.animite.media.source.SmartStreamEngine
import com.imashnake.animite.media.source.UnifiedQualityOption
import com.imashnake.animite.media.source.classification.EpisodeClassificationRepository
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class MediaTrackUiModel(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val language: String? = null,
    val isSelected: Boolean = false,
)

data class EpisodeUiModel(
    val number: String,
    val title: String,
    val url: String,
    val durationText: String = "24:00",
    val thumbnail: String? = null,
)

data class VideoTrackUiModel(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val height: Int,
    val isSelected: Boolean = false,
)

@OptIn(UnstableApi::class)
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    val cacheManager: MediaCacheManager,
) : ViewModel() {

    private var exoPlayerInstance: ExoPlayer? = null
    private var bandwidthMeterInstance: DefaultBandwidthMeter? = null
    var activeServerUrl: String? = null
        private set

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes = _cacheSizeBytes.asStateFlow()

    private val _cacheLimitBytes = MutableStateFlow(cacheManager.maxCacheBytes)
    val cacheLimitBytes = _cacheLimitBytes.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed = _playbackSpeed.asStateFlow()

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode = _resizeMode.asStateFlow()

    private val _subtitlesEnabled = MutableStateFlow(true)
    val subtitlesEnabled = _subtitlesEnabled.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<MediaTrackUiModel>>(emptyList())
    val subtitleTracks = _subtitleTracks.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<MediaTrackUiModel>>(emptyList())
    val audioTracks = _audioTracks.asStateFlow()

    private val _videoTracks = MutableStateFlow<List<VideoTrackUiModel>>(emptyList())
    val videoTracks = _videoTracks.asStateFlow()

    private val _isVideoAdaptiveAuto = MutableStateFlow(true)
    val isVideoAdaptiveAuto = _isVideoAdaptiveAuto.asStateFlow()

    private val _isAutoQualityMode = MutableStateFlow(true)
    val isAutoQualityMode = _isAutoQualityMode.asStateFlow()

    private val _effectiveQuality = MutableStateFlow("720p HD")
    val effectiveQuality = _effectiveQuality.asStateFlow()

    private val _skipIntervals = MutableStateFlow<List<SkipInterval>>(emptyList())
    val skipIntervals = _skipIntervals.asStateFlow()

    private val _isSkipIntroEnabled = MutableStateFlow(true)
    val isSkipIntroEnabled = _isSkipIntroEnabled.asStateFlow()

    private var lastRebufferTimestamp = 0L
    private var rebufferCountInWindow = 0
    private var lastQualitySwitchTimestamp = 0L

    private val _isScreenLocked = MutableStateFlow(false)
    val isScreenLocked = _isScreenLocked.asStateFlow()

    private var sleepTimerJob: Job? = null
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes = _sleepTimerMinutes.asStateFlow()

    private val _sleepTimerSecondsLeft = MutableStateFlow<Int?>(null)
    val sleepTimerSecondsLeft = _sleepTimerSecondsLeft.asStateFlow()

    fun setAutoQualityMode(enabled: Boolean) {
        _isAutoQualityMode.value = enabled
        if (enabled) {
            selectVideoTrack(null)
        }
    }

    fun toggleSkipIntro(enabled: Boolean) {
        _isSkipIntroEnabled.value = enabled
    }

    fun setSkipIntervals(intervals: List<SkipInterval>) {
        _skipIntervals.value = intervals
    }

    fun fetchAniSkipIntervals(malId: Int, episodeNumber: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = EpisodeClassificationRepository.fetchSkipIntervals(malId, episodeNumber)
            _skipIntervals.value = list
        }
    }

    fun loadSkipIntervals(
        animeTitles: List<String>,
        episodeNumber: Float,
        customInterval: Pair<Long, Long>? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val malId = EpisodeClassificationRepository.resolveMalId(animeTitles)
            val list = if (malId > 0) {
                EpisodeClassificationRepository.fetchSkipIntervals(malId, episodeNumber)
            } else {
                emptyList()
            }
            if (list.isNotEmpty()) {
                _skipIntervals.value = list
            } else if (customInterval != null && customInterval.second > customInterval.first) {
                _skipIntervals.value = listOf(
                    SkipInterval(
                        startTimeMs = customInterval.first,
                        endTimeMs = customInterval.second,
                        skipType = "op",
                    )
                )
            } else {
                _skipIntervals.value = emptyList()
            }
        }
    }

    fun recordPlaybackRebuffer() {
        val now = System.currentTimeMillis()
        if (now - lastRebufferTimestamp < 15_000L) {
            rebufferCountInWindow++
        } else {
            rebufferCountInWindow = 1
        }
        lastRebufferTimestamp = now
    }

    fun evaluateDiscreteQualityAdaptive(
        availableOptions: List<UnifiedQualityOption>,
        onSwitchStream: (AnimeStreamServer) -> Unit,
    ) {
        if (!_isAutoQualityMode.value || availableOptions.size <= 1) return
        val now = System.currentTimeMillis()
        if (now - lastQualitySwitchTimestamp < 15_000L) return

        val player = exoPlayerInstance ?: return
        val currentServerUrl = activeServerUrl ?: return
        val currentOption = availableOptions.firstOrNull { it.primaryServer.url == currentServerUrl || it.fallbackServers.any { f -> f.url == currentServerUrl } } ?: return
        val currentRank = currentOption.quality

        val currentBufferMs = player.totalBufferedDuration
        val isDepleted = currentBufferMs < 2000L && player.playbackState == Player.STATE_BUFFERING

        if (isDepleted || rebufferCountInWindow >= 2) {
            val lowerOptions = availableOptions.filter { it.quality.height < currentRank.height }.sortedByDescending { it.quality.height }
            val targetLower = lowerOptions.firstOrNull()
            if (targetLower != null) {
                lastQualitySwitchTimestamp = now
                rebufferCountInWindow = 0
                _effectiveQuality.value = targetLower.quality.canonicalName
                onSwitchStream(targetLower.primaryServer)
                return
            }
        }

        val estimateBps = bandwidthMeterInstance?.bitrateEstimate ?: 0L
        val isStable = currentBufferMs > 15_000L && player.playbackState == Player.STATE_READY
        if (isStable && estimateBps > 4_500_000L) {
            val higherOptions = availableOptions.filter { it.quality.height > currentRank.height }.sortedBy { it.quality.height }
            val targetHigher = higherOptions.firstOrNull()
            if (targetHigher != null) {
                lastQualitySwitchTimestamp = now
                _effectiveQuality.value = targetHigher.quality.canonicalName
                onSwitchStream(targetHigher.primaryServer)
                return
            }
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        _sleepTimerMinutes.value = minutes
        if (minutes == null) {
            _sleepTimerSecondsLeft.value = null
            return
        }
        if (minutes == -1) {
            _sleepTimerSecondsLeft.value = null
            return
        }
        _sleepTimerSecondsLeft.value = minutes * 60
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                delay(1000)
                remaining--
                _sleepTimerSecondsLeft.value = remaining
            }
            exoPlayerInstance?.pause()
            _sleepTimerMinutes.value = null
            _sleepTimerSecondsLeft.value = null
        }
    }

    fun loadEpisodeStreamWithFallback(
        episode: AnimeEpisode,
        preferredProviderId: String? = null,
        failedProviderIds: Set<String> = emptySet(),
        onSuccess: (List<AnimeStreamServer>, String) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = EpisodeSourcesManager.resolveStreamServersWithFallback(
                    episode = episode,
                    preferredProviderId = preferredProviderId,
                    failedProviderIds = failedProviderIds,
                )
                withContext(Dispatchers.Main) {
                    if (result.success && result.servers.isNotEmpty()) {
                        onSuccess(result.servers, result.winningProviderId)
                    } else {
                        onError(result.errorMessage ?: "تعذر تشغيل الحلقة من جميع المصادر المتاحة")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "حدث خطأ أثناء الاتصال بمصادر التشغيل")
                }
            }
        }
    }

    fun reExtractOrFallback(
        episode: AnimeEpisode,
        onSuccess: (List<AnimeStreamServer>, AnimeStreamServer) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val refreshed = SmartStreamEngine.reExtractExpiredStream(
                    episode = episode,
                    animeTitle = episode.title ?: "",
                )
                withContext(Dispatchers.Main) {
                    if (refreshed.success && refreshed.allDirectServers.isNotEmpty()) {
                        val chosen = refreshed.bestStream ?: refreshed.allDirectServers.first()
                        onSuccess(refreshed.allDirectServers, chosen)
                    } else {
                        onError(refreshed.errorMessage ?: "تعذر تجديد روابط التشغيل لهذه الحلقة")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "حدث خطأ أثناء تجديد روابط التشغيل")
                }
            }
        }
    }

    fun loadEpisodeStream(
        providerId: String,
        episodeUrl: String,
        onSuccess: (List<AnimeStreamServer>) -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val provider = EpisodeSourcesManager.getProvider(providerId)
                val servers = provider.getStreamServers(episodeUrl)
                withContext(Dispatchers.Main) {
                    onSuccess(servers)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.localizedMessage ?: "Failed to load episode stream")
                }
            }
        }
    }

    fun getOrCreatePlayer(): ExoPlayer {
        exoPlayerInstance?.let { return it }

        val cacheDataSourceFactory = cacheManager.createCacheDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(cacheDataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .setBackBuffer(30_000, /* retainBackBufferFromKeyframe = */ true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val bMeter = DefaultBandwidthMeter.Builder(appContext).build()
        bandwidthMeterInstance = bMeter

        val player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bMeter)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()

        exoPlayerInstance = player
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                updateTracksFromPlayer()
            }
        })
        refreshCacheInfo()
        return player
    }

    fun updateTracksFromPlayer() {
        val player = exoPlayerInstance ?: return
        val tracks = player.currentTracks
        val subs = mutableListOf<MediaTrackUiModel>()
        val audios = mutableListOf<MediaTrackUiModel>()
        val videos = mutableListOf<VideoTrackUiModel>()

        tracks.groups.forEachIndexed { groupIndex, group ->
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val height = format.height
                        val label = when {
                            height >= 2160 -> "4K (2160p)"
                            height >= 1440 -> "2K (1440p)"
                            height >= 1080 -> "1080p FHD"
                            height >= 720 -> "720p HD"
                            height >= 480 -> "480p SD"
                            height >= 360 -> "360p"
                            height > 0 -> "${height}p"
                            !format.label.isNullOrBlank() -> format.label!!
                            else -> "جودة ${videos.size + 1}"
                        }
                        videos.add(
                            VideoTrackUiModel(
                                groupIndex = groupIndex,
                                trackIndex = i,
                                name = label,
                                height = height,
                                isSelected = group.isTrackSelected(i),
                            )
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language
                        val name = format.label ?: lang?.uppercase() ?: "Subtitle ${subs.size + 1}"
                        subs.add(
                            MediaTrackUiModel(
                                groupIndex = groupIndex,
                                trackIndex = i,
                                name = name,
                                language = lang,
                                isSelected = group.isTrackSelected(i),
                            )
                        )
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language
                        val name = format.label ?: lang?.uppercase() ?: "Audio ${audios.size + 1}"
                        audios.add(
                            MediaTrackUiModel(
                                groupIndex = groupIndex,
                                trackIndex = i,
                                name = name,
                                language = lang,
                                isSelected = group.isTrackSelected(i),
                            )
                        )
                    }
                }
                else -> {}
            }
        }
        videos.sortBy { it.height }
        _videoTracks.value = videos.distinctBy { it.name }
        _isVideoAdaptiveAuto.value = videos.none { it.isSelected }
        _subtitleTracks.value = subs
        _audioTracks.value = audios
    }

    fun selectVideoTrack(track: VideoTrackUiModel?) {
        val player = exoPlayerInstance ?: return
        if (track == null) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
            _isVideoAdaptiveAuto.value = true
        } else {
            val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
            val override = TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(override)
                .build()
            _isVideoAdaptiveAuto.value = false
        }
        updateTracksFromPlayer()
    }

    fun selectSubtitleTrack(track: MediaTrackUiModel?) {
        val player = exoPlayerInstance ?: return
        if (track == null) {
            toggleSubtitles(false)
            return
        }
        _subtitlesEnabled.value = true
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(override)
            .build()
        updateTracksFromPlayer()
    }

    fun selectAudioTrack(track: MediaTrackUiModel) {
        val player = exoPlayerInstance ?: return
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        val override = TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .build()
        updateTracksFromPlayer()
    }

    fun prepareMedia(
        server: AnimeStreamServer,
        positionMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        val player = getOrCreatePlayer()
        if (activeServerUrl == server.url && player.playbackState != Player.STATE_IDLE) {
            return
        }
        activeServerUrl = server.url
        _effectiveQuality.value = server.quality

        val effectiveHeaders = server.headers.toMutableMap()
        if (!effectiveHeaders.containsKey("User-Agent")) {
            effectiveHeaders["User-Agent"] = HttpHelper.USER_AGENT
        }
        if (!effectiveHeaders.containsKey("Referer")) {
            val urlLower = server.url.lowercase(Locale.ROOT)
            when {
                urlLower.contains("mp4upload.com") -> effectiveHeaders["Referer"] = "https://www.mp4upload.com/"
                urlLower.contains("vid3rb.com") -> effectiveHeaders["Referer"] = "https://anime3rb.com/"
                urlLower.contains("anime4up") -> effectiveHeaders["Referer"] = "https://w1.anime4up.rest/"
            }
        }

        val cacheFactory = cacheManager.createCacheDataSourceFactory(effectiveHeaders)
        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(cacheFactory)

        val mimeType = when {
            server.url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            server.url.contains(".mpd", ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            server.url.contains(".mp4", ignoreCase = true) || server.url.contains("video.vid3rb.com/video/") -> MimeTypes.VIDEO_MP4
            else -> null
        }

        val mediaItemBuilder = MediaItem.Builder().setUri(server.url)
        if (mimeType != null) {
            mediaItemBuilder.setMimeType(mimeType)
        }
        val mediaItem = mediaItemBuilder.build()

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        player.setMediaSource(mediaSource, positionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
        refreshCacheInfo()
        updateTracksFromPlayer()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayerInstance?.playbackParameters = PlaybackParameters(speed)
    }

    fun setResizeMode(mode: Int) {
        _resizeMode.value = mode
    }

    fun toggleSubtitles(enabled: Boolean) {
        _subtitlesEnabled.value = enabled
        exoPlayerInstance?.let { player ->
            val parameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                .build()
            player.trackSelectionParameters = parameters
            updateTracksFromPlayer()
        }
    }

    fun toggleScreenLock() {
        _isScreenLocked.value = !_isScreenLocked.value
    }

    fun setScreenLocked(locked: Boolean) {
        _isScreenLocked.value = locked
    }

    fun refreshCacheInfo() {
        _cacheSizeBytes.value = cacheManager.getCurrentCacheSizeBytes()
        _cacheLimitBytes.value = cacheManager.maxCacheBytes
    }

    fun clearCache() {
        cacheManager.clearCache()
        refreshCacheInfo()
    }

    fun setCacheLimit(limitBytes: Long) {
        cacheManager.maxCacheBytes = limitBytes
        _cacheLimitBytes.value = limitBytes
    }

    fun seekRelative(offsetMs: Long) {
        exoPlayerInstance?.let { player ->
            val target = (player.currentPosition + offsetMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
            player.seekTo(target)
        }
    }

    fun releasePlayer() {
        exoPlayerInstance?.release()
        exoPlayerInstance = null
        activeServerUrl = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
