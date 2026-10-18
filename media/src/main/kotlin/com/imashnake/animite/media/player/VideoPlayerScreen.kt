package com.imashnake.animite.media.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Warning
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.imashnake.animite.media.EpisodeWatchManager
import com.imashnake.animite.media.R
import com.imashnake.animite.media.source.AnimeEpisode
import com.imashnake.animite.media.source.AnimeStreamServer
import com.imashnake.animite.media.source.EpisodeJsonCache
import com.imashnake.animite.media.source.EpisodeSessionRepository
import com.imashnake.animite.media.source.QualityRank
import com.imashnake.animite.media.source.SkipInterval
import com.imashnake.animite.media.source.SmartStreamDiagnostics
import com.imashnake.animite.media.source.SmartStreamEngine
import com.imashnake.animite.media.source.SourceJsonParser
import com.imashnake.animite.media.source.SourceLinkExtractor
import com.imashnake.animite.media.source.StreamLinkType
import com.imashnake.animite.media.source.UnifiedQualityOption
import com.imashnake.animite.navigation.VideoPlayerRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    route: VideoPlayerRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()

    val exoPlayer = remember { viewModel.getOrCreatePlayer() }

    val initialRouteUrl = remember { EpisodeSessionRepository.getActiveInitialUrl().ifEmpty { route.initialUrl } }
    val initialRouteQuality = remember { EpisodeSessionRepository.getActiveInitialQuality().ifEmpty { route.initialQuality } }

    // Parse direct servers from session repository or route JSON
    val parsedDirectServers = remember(route.serversJson, initialRouteUrl) {
        val sessionServers = EpisodeSessionRepository.getActiveServers()
        val serversList = if (sessionServers.isNotEmpty()) {
            sessionServers
        } else {
            SourceJsonParser.decodeServers(route.serversJson)
        }

        val directList = serversList.filter {
            it.url.isNotEmpty() && (it.isDirectVideo || it.linkType == StreamLinkType.DIRECT_VIDEO)
        }.toMutableList()

        if (directList.isEmpty() && initialRouteUrl.isNotEmpty()) {
            directList.add(
                AnimeStreamServer(
                    name = initialRouteQuality.ifEmpty { "HD" },
                    url = initialRouteUrl,
                    isDirectVideo = true,
                    quality = initialRouteQuality.ifEmpty { "HD" },
                    linkType = StreamLinkType.DIRECT_VIDEO,
                )
            )
        }

        directList.distinctBy { it.url }
    }

    var currentServers by remember { mutableStateOf(parsedDirectServers) }

    var selectedQuality by remember {
        mutableStateOf(initialRouteQuality.ifEmpty { currentServers.firstOrNull()?.quality ?: "HD" })
    }

    val activeServer = remember(selectedQuality, currentServers) {
        currentServers.firstOrNull { it.quality == selectedQuality }
            ?: currentServers.firstOrNull()
            ?: AnimeStreamServer(
                name = initialRouteQuality.ifEmpty { "HD" },
                url = initialRouteUrl,
                isDirectVideo = true,
                quality = initialRouteQuality.ifEmpty { "HD" },
            )
    }

    val aggregatedResult = remember { EpisodeSessionRepository.getActivePlaybackResult() }
    val qualityOptions = remember(currentServers, aggregatedResult) {
        if (aggregatedResult != null && aggregatedResult.qualityOptions.isNotEmpty()) {
            aggregatedResult.qualityOptions
        } else {
            val map = mutableMapOf<QualityRank, MutableList<AnimeStreamServer>>()
            currentServers.forEach { server ->
                val rank = SourceLinkExtractor.classifyQuality(server)
                map.getOrPut(rank) { mutableListOf() }.add(server)
            }
            map.map { (rank, sList) ->
                val primary = sList.first()
                val fallbacks = if (sList.size > 1) sList.subList(1, sList.size) else emptyList()
                UnifiedQualityOption(
                    quality = rank,
                    primaryServer = primary.copy(quality = rank.canonicalName),
                    fallbackServers = fallbacks.map { it.copy(quality = rank.canonicalName) },
                    mediaType = SourceLinkExtractor.detectMediaType(primary.url),
                )
            }.sortedBy { it.quality.height }
        }
    }

    // Parse Episodes list
    var activeEpisodeNumber by remember {
        mutableStateOf(route.episodeNumber.ifEmpty { "1" })
    }

    val episodesList = remember(route.episodesJson, activeEpisodeNumber) {
        val sessionEpisodes = EpisodeSessionRepository.getActiveEpisodes()
        val diskEpisodes = if (sessionEpisodes.isEmpty()) {
            EpisodeJsonCache.getCachedEpisodes(context, listOf(route.animeTitle))?.episodes ?: emptyList()
        } else emptyList()

        val parsedEpisodes = when {
            sessionEpisodes.isNotEmpty() -> sessionEpisodes
            diskEpisodes.isNotEmpty() -> diskEpisodes
            else -> SourceJsonParser.decodeEpisodes(route.episodesJson)
        }

        if (parsedEpisodes.isNotEmpty()) {
            parsedEpisodes.map { ep ->
                val epNum = if (ep.number % 1.0f == 0.0f) ep.number.toInt().toString() else ep.number.toString()
                EpisodeUiModel(
                    number = epNum,
                    title = ep.title ?: "Episode $epNum",
                    url = ep.url,
                    thumbnail = ep.thumbnail,
                )
            }
        } else {
            val baseNum = activeEpisodeNumber.toFloatOrNull()?.toInt() ?: 1
            val total = maxOf(12, baseNum + 5)
            (1..total).map { i ->
                EpisodeUiModel(
                    number = i.toString(),
                    title = "Episode $i",
                    url = "",
                )
            }
        }
    }

    // Playback state
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    val failedServerUrlsInEpisode = remember(activeEpisodeNumber) { mutableSetOf<String>() }
    var stallJob by remember { mutableStateOf<Job?>(null) }

    val configuration = LocalConfiguration.current
    val isDeviceInLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var isLandscape by remember { mutableStateOf(isDeviceInLandscape) }

    LaunchedEffect(isDeviceInLandscape) {
        isLandscape = isDeviceInLandscape
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (isDeviceInLandscape) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val handleBack = {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        viewModel.releasePlayer()
        onBack()
    }

    BackHandler {
        if (isLandscape && activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isLandscape = false
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            handleBack()
        }
    }

    var isMuted by remember { mutableStateOf(false) }

    // Dialog sheets
    var showQualitySheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    // Gestures HUD
    var showVolumeHud by remember { mutableStateOf(false) }
    var volumePercent by remember { mutableIntStateOf(50) }
    var volumeHudKey by remember { mutableIntStateOf(0) }

    var showBrightnessHud by remember { mutableStateOf(false) }
    var brightnessPercent by remember { mutableIntStateOf(50) }
    var brightnessHudKey by remember { mutableIntStateOf(0) }

    var showLeftSeekFeedback by remember { mutableStateOf(false) }
    var leftSeekKey by remember { mutableIntStateOf(0) }

    var showRightSeekFeedback by remember { mutableStateOf(false) }
    var rightSeekKey by remember { mutableIntStateOf(0) }

    var isFastForwarding2x by remember { mutableStateOf(false) }
    var speedBeforeFastForward by remember { mutableFloatStateOf(1.0f) }

    var showLockFloatingUnlock by remember { mutableStateOf(false) }
    var lockFloatingKey by remember { mutableIntStateOf(0) }

    // ViewModel states
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val resizeMode by viewModel.resizeMode.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val isAdaptiveAuto by viewModel.isVideoAdaptiveAuto.collectAsState()
    val isAutoQualityMode by viewModel.isAutoQualityMode.collectAsState()
    val effectiveQuality by viewModel.effectiveQuality.collectAsState()
    val skipIntervals by viewModel.skipIntervals.collectAsState()
    val isSkipIntroEnabled by viewModel.isSkipIntroEnabled.collectAsState()
    val isScreenLocked by viewModel.isScreenLocked.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()

    var hasSkippedCurrentInterval by remember { mutableStateOf(false) }

    var customIntroInterval by remember(route.animeTitle) {
        mutableStateOf(EpisodeWatchManager.getCustomIntro(context, route.animeTitle))
    }

    // Reset skipped flag when episode changes
    LaunchedEffect(activeEpisodeNumber) {
        hasSkippedCurrentInterval = false
    }

    // Skip Intro / Outro active detection
    val activeSkipInterval = remember(skipIntervals, currentPosition, isSkipIntroEnabled, hasSkippedCurrentInterval) {
        if (!isSkipIntroEnabled || hasSkippedCurrentInterval) null
        else skipIntervals.firstOrNull { interval ->
            currentPosition in interval.startTimeMs..interval.endTimeMs
        }
    }

    // Reset hasSkippedCurrentInterval when leaving interval
    LaunchedEffect(currentPosition, skipIntervals) {
        val inAnyInterval = skipIntervals.any { currentPosition in it.startTimeMs..it.endTimeMs }
        if (!inAnyInterval && hasSkippedCurrentInterval) {
            hasSkippedCurrentInterval = false
        }
    }

    // Fetch AniSkip intervals with custom intro fallback
    LaunchedEffect(activeEpisodeNumber, route.animeTitle) {
        val epNum = activeEpisodeNumber.toFloatOrNull() ?: 1.0f
        val customIntro = EpisodeWatchManager.getCustomIntro(context, route.animeTitle)
        viewModel.loadSkipIntervals(listOf(route.animeTitle), epNum, customIntro)
    }

    // Helper functions
    fun switchToEpisode(item: EpisodeUiModel) {
        activeEpisodeNumber = item.number
        isBuffering = true
        playerError = null

        val fullEp = EpisodeSessionRepository.getActiveEpisodes().firstOrNull {
            val nStr = if (it.number % 1.0f == 0.0f) it.number.toInt().toString() else it.number.toString()
            nStr == item.number
        } ?: AnimeEpisode(number = item.number.toFloatOrNull() ?: 1f, url = item.url)

        if (fullEp.skipIntervals.isNotEmpty()) {
            viewModel.setSkipIntervals(fullEp.skipIntervals)
        }

        viewModel.loadEpisodeStreamWithFallback(
            episode = fullEp,
            preferredProviderId = route.providerId,
            onSuccess = { servers, _ ->
                val direct = servers.filter { it.isDirectVideo && it.url.isNotEmpty() }
                val streamList = if (direct.isNotEmpty()) direct else servers
                if (streamList.isNotEmpty()) {
                    currentServers = streamList
                    val chosen = streamList.firstOrNull { it.quality == selectedQuality } ?: streamList.first()
                    viewModel.prepareMedia(chosen, positionMs = 0L, playWhenReady = true)
                    playerError = null
                } else {
                    playerError = "لا يوجد رابط تشغيل متاح لهذه الحلقة"
                    isBuffering = false
                }
            },
            onError = { err ->
                playerError = err
                isBuffering = false
            }
        )
    }

    fun hasPreviousEpisode(): Boolean {
        val idx = episodesList.indexOfFirst { it.number == activeEpisodeNumber }
        return idx > 0
    }

    fun hasNextEpisode(): Boolean {
        val idx = episodesList.indexOfFirst { it.number == activeEpisodeNumber }
        return idx in 0 until episodesList.lastIndex
    }

    fun playPreviousEpisode() {
        val idx = episodesList.indexOfFirst { it.number == activeEpisodeNumber }
        if (idx > 0) {
            switchToEpisode(episodesList[idx - 1])
        }
    }

    fun playNextEpisode() {
        val idx = episodesList.indexOfFirst { it.number == activeEpisodeNumber }
        if (idx in 0 until episodesList.lastIndex) {
            switchToEpisode(episodesList[idx + 1])
        }
    }

    // Attach ExoPlayer listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_BUFFERING) {
                    viewModel.recordPlaybackRebuffer()
                    stallJob?.cancel()
                    stallJob = coroutineScope.launch {
                        delay(8000L)
                        if (isBuffering && isPlaying) {
                            android.util.Log.w("ANIMITE_PLAYER", "Buffering stalled >8s on ${activeServer.url}. Triggering auto failover.")
                            val epNum = activeEpisodeNumber.toFloatOrNull() ?: 1.0f
                            SmartStreamDiagnostics.addFailoverEvent(epNum, "Buffering stalled >8s, switching to alternative stream")
                            failedServerUrlsInEpisode.add(activeServer.url)

                            val currentRank = SourceLinkExtractor.classifyQuality(activeServer)
                            val currentOption = qualityOptions.firstOrNull { it.quality.height == currentRank.height }
                            val fallbackServer = currentOption?.fallbackServers?.firstOrNull { it.url !in failedServerUrlsInEpisode }
                            if (fallbackServer != null) {
                                viewModel.prepareMedia(fallbackServer, positionMs = currentPosition, playWhenReady = true)
                                return@launch
                            }

                            val otherOptions = qualityOptions.filter { it.quality.height != currentRank.height }
                            val nearest = otherOptions
                                .mapNotNull { opt ->
                                    val valid = if (opt.primaryServer.url !in failedServerUrlsInEpisode) opt.primaryServer
                                    else opt.fallbackServers.firstOrNull { it.url !in failedServerUrlsInEpisode }
                                    if (valid != null) Pair(opt, valid) else null
                                }
                                .minByOrNull { kotlin.math.abs(it.first.quality.height - currentRank.height) }

                            if (nearest != null) {
                                selectedQuality = nearest.first.quality.canonicalName
                                viewModel.prepareMedia(nearest.second, positionMs = currentPosition, playWhenReady = true)
                            }
                        }
                    }
                }
                duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                bufferedPosition = exoPlayer.bufferedPosition
                if (playbackState == Player.STATE_READY) {
                    stallJob?.cancel()
                    stallJob = null
                    playerError = null
                    SmartStreamEngine.reportStreamSuccess(activeServer)
                }
                if (playbackState == Player.STATE_ENDED) {
                    stallJob?.cancel()
                    stallJob = null
                    showControls = true
                    if (sleepTimerMinutes == -1) {
                        exoPlayer.pause()
                        viewModel.setSleepTimer(null)
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                val errorMsg = error.localizedMessage ?: "Unknown stream error"
                android.util.Log.e("ANIMITE_PLAYER", "Stream error on ${activeServer.url}: $errorMsg")
                val epNum = activeEpisodeNumber.toFloatOrNull() ?: 1.0f
                SmartStreamEngine.reportStreamFailure(epNum, activeServer, errorMsg)

                // 0. Immediate Token Expiry Renewal: 401, 403, 410 or invalid signed tokens
                if (SmartStreamEngine.isTokenExpiredError(error)) {
                    android.util.Log.w("ANIMITE_PLAYER", "Token expired (401/403/410) on ${activeServer.url}. Triggering silent renewal.")
                    SmartStreamDiagnostics.addFailoverEvent(epNum, "Token expired (401/403/410) - triggering silent renewal")
                    val fullEp = EpisodeSessionRepository.getActiveEpisodes().firstOrNull {
                        val nStr = if (it.number % 1.0f == 0.0f) it.number.toInt().toString() else it.number.toString()
                        nStr == activeEpisodeNumber
                    }
                    if (fullEp != null) {
                        isBuffering = true
                        viewModel.reExtractOrFallback(
                            episode = fullEp,
                            onSuccess = { newServers, chosen ->
                                currentServers = newServers
                                selectedQuality = chosen.quality
                                viewModel.prepareMedia(chosen, positionMs = currentPosition, playWhenReady = true)
                                playerError = null
                            },
                            onError = { renewErr ->
                                playerError = renewErr
                                isBuffering = false
                            }
                        )
                        return
                    }
                }

                failedServerUrlsInEpisode.add(activeServer.url)

                // 1. Failover to backup server of the SAME quality (try all available servers of this quality first)
                val currentRank = SourceLinkExtractor.classifyQuality(activeServer)
                val currentOption = qualityOptions.firstOrNull { it.quality.height == currentRank.height }
                val fallbackServer = currentOption?.fallbackServers?.firstOrNull { it.url !in failedServerUrlsInEpisode }

                if (fallbackServer != null) {
                    SmartStreamDiagnostics.addFailoverEvent(epNum, "Failover to backup server for ${currentOption.quality.canonicalName}")
                    viewModel.prepareMedia(fallbackServer, positionMs = currentPosition, playWhenReady = true)
                    return
                }

                // 2. Failover to nearest adjacent quality (only after all servers of current quality are exhausted)
                val otherOptions = qualityOptions.filter { it.quality.height != currentRank.height }
                val nearestOption = otherOptions
                    .mapNotNull { opt ->
                        val validServer = if (opt.primaryServer.url !in failedServerUrlsInEpisode) opt.primaryServer
                                          else opt.fallbackServers.firstOrNull { it.url !in failedServerUrlsInEpisode }
                        if (validServer != null) Pair(opt, validServer) else null
                    }
                    .minByOrNull { kotlin.math.abs(it.first.quality.height - currentRank.height) }

                if (nearestOption != null) {
                    SmartStreamDiagnostics.addFailoverEvent(epNum, "Failover to nearest quality ${nearestOption.first.quality.canonicalName}")
                    selectedQuality = nearestOption.first.quality.canonicalName
                    viewModel.prepareMedia(nearestOption.second, positionMs = currentPosition, playWhenReady = true)
                    return
                }

                // 3. Fallback resolution across providers
                val fullEp = EpisodeSessionRepository.getActiveEpisodes().firstOrNull {
                    val nStr = if (it.number % 1.0f == 0.0f) it.number.toInt().toString() else it.number.toString()
                    nStr == activeEpisodeNumber
                }

                if (fullEp != null) {
                    isBuffering = true
                    SmartStreamDiagnostics.addFailoverEvent(epNum, "Silent re-extraction across providers")
                    viewModel.reExtractOrFallback(
                        episode = fullEp,
                        onSuccess = { newServers, chosen ->
                            currentServers = newServers
                            selectedQuality = chosen.quality
                            viewModel.prepareMedia(chosen, positionMs = currentPosition, playWhenReady = true)
                            playerError = null
                        },
                        onError = {
                            viewModel.loadEpisodeStreamWithFallback(
                                episode = fullEp,
                                failedProviderIds = setOf(route.providerId),
                                onSuccess = { newServers, _ ->
                                    val direct = newServers.filter { it.isDirectVideo && it.url.isNotEmpty() }
                                    val streamList = if (direct.isNotEmpty()) direct else newServers
                                    if (streamList.isNotEmpty()) {
                                        currentServers = streamList
                                        val chosen = streamList.firstOrNull { it.quality == selectedQuality } ?: streamList.first()
                                        viewModel.prepareMedia(chosen, positionMs = currentPosition, playWhenReady = true)
                                        playerError = null
                                    }
                                },
                                onError = { finalErr ->
                                    playerError = finalErr
                                    isBuffering = false
                                }
                            )
                        }
                    )
                } else {
                    playerError = errorMsg
                    isBuffering = false
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Lifecycle Observer and orientation management
    DisposableEffect(lifecycleOwner) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> {
                    if (window != null) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Prepare active media stream with intelligent resume
    LaunchedEffect(activeServer.url) {
        val epNum = activeEpisodeNumber.toFloatOrNull() ?: 1.0f
        val savedPos = EpisodeWatchManager.getLastPositionMs(context, route.animeTitle, epNum)
        val resumePos = when {
            savedPos > 0L -> savedPos
            currentPosition > 0L -> currentPosition
            else -> 0L
        }
        viewModel.prepareMedia(activeServer, positionMs = resumePos, playWhenReady = true)
    }

    // Progress ticker & auto-save & adaptive quality evaluation
    LaunchedEffect(isPlaying) {
        var tickerTicks = 0
        while (true) {
            if (exoPlayer.playbackState == Player.STATE_READY) {
                currentPosition = exoPlayer.currentPosition
                bufferedPosition = exoPlayer.bufferedPosition
                if (exoPlayer.duration > 0) {
                    duration = exoPlayer.duration
                }
                tickerTicks++
                if (tickerTicks % 6 == 0 && duration > 0L) {
                    val epNum = activeEpisodeNumber.toFloatOrNull() ?: 1.0f
                    EpisodeWatchManager.savePositionMs(
                        context = context,
                        animeIdentifier = route.animeTitle,
                        episodeNumber = epNum,
                        positionMs = currentPosition,
                        durationMs = duration,
                    )
                    viewModel.evaluateDiscreteQualityAdaptive(qualityOptions) { targetServer ->
                        selectedQuality = targetServer.quality
                        viewModel.prepareMedia(targetServer, positionMs = currentPosition, playWhenReady = true)
                    }
                }
            }
            delay(350)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying, isScreenLocked, showQualitySheet, showAudioSheet, showSubtitleSheet, showSpeedSheet, showEpisodesSheet, showMoreSheet) {
        val anySheetOpen = showQualitySheet || showAudioSheet || showSubtitleSheet || showSpeedSheet || showEpisodesSheet || showMoreSheet
        if (showControls && isPlaying && !isScreenLocked && !anySheetOpen) {
            delay(4500)
            showControls = false
        }
    }

    // Auto-hide HUD feedbacks
    LaunchedEffect(volumeHudKey) {
        if (showVolumeHud) {
            delay(1200)
            showVolumeHud = false
        }
    }

    LaunchedEffect(brightnessHudKey) {
        if (showBrightnessHud) {
            delay(1200)
            showBrightnessHud = false
        }
    }

    LaunchedEffect(leftSeekKey) {
        if (showLeftSeekFeedback) {
            delay(700)
            showLeftSeekFeedback = false
        }
    }

    LaunchedEffect(rightSeekKey) {
        if (showRightSeekFeedback) {
            delay(700)
            showRightSeekFeedback = false
        }
    }

    LaunchedEffect(lockFloatingKey) {
        if (showLockFloatingUnlock) {
            delay(3500)
            showLockFloatingUnlock = false
        }
    }

    fun toggleOrientation() {
        if (activity != null) {
            if (isLandscape) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                isLandscape = false
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                isLandscape = true
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPip = context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            if (hasPip && activity != null) {
                showControls = false
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    activity.enterPictureInPictureMode(params)
                } catch (_: Exception) {}
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF0B0F19),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // GESTURES: Double Tap, Single Tap, Long Press 2x
                .pointerInput(isScreenLocked) {
                    if (isScreenLocked) {
                        detectTapGestures(
                            onTap = {
                                showLockFloatingUnlock = true
                                lockFloatingKey++
                            }
                        )
                    } else {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val w = size.width
                                if (offset.x < w * 0.42f) {
                                    viewModel.seekRelative(-10000)
                                    showLeftSeekFeedback = true
                                    leftSeekKey++
                                } else if (offset.x > w * 0.58f) {
                                    viewModel.seekRelative(10000)
                                    showRightSeekFeedback = true
                                    rightSeekKey++
                                }
                            },
                            onLongPress = {
                                if (isPlaying && !isFastForwarding2x) {
                                    speedBeforeFastForward = playbackSpeed
                                    viewModel.setPlaybackSpeed(2.0f)
                                    isFastForwarding2x = true
                                }
                            },
                            onPress = {
                                tryAwaitRelease()
                                if (isFastForwarding2x) {
                                    viewModel.setPlaybackSpeed(speedBeforeFastForward)
                                    isFastForwarding2x = false
                                }
                            },
                            onTap = {
                                showControls = !showControls
                            }
                        )
                    }
                }
                // GESTURES: Vertical Swipes for Volume & Brightness
                .pointerInput(isScreenLocked) {
                    if (!isScreenLocked) {
                        var isDraggingRight = false
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDraggingRight = offset.x > size.width * 0.5f
                            },
                            onDragEnd = {
                                showVolumeHud = false
                                showBrightnessHud = false
                            },
                            onDragCancel = {
                                showVolumeHud = false
                                showBrightnessHud = false
                            },
                            onVerticalDrag = { _, dragAmount ->
                                val delta = -dragAmount / 350f
                                if (isDraggingRight) {
                                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                    val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val currV = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val newV = (currV + (delta * maxV)).toInt().coerceIn(0, maxV)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newV, 0)
                                    volumePercent = ((newV.toFloat() / maxV.coerceAtLeast(1)) * 100).toInt()
                                    isMuted = newV == 0
                                    showVolumeHud = true
                                    volumeHudKey++
                                } else {
                                    val currB = activity?.window?.attributes?.screenBrightness ?: 0.5f
                                    val baseB = if (currB < 0) 0.5f else currB
                                    val newB = (baseB + delta).coerceIn(0.01f, 1.0f)
                                    activity?.window?.let { w ->
                                        val lp = w.attributes
                                        lp.screenBrightness = newB
                                        w.attributes = lp
                                    }
                                    brightnessPercent = (newB * 100).toInt()
                                    showBrightnessHud = true
                                    brightnessHudKey++
                                }
                            }
                        )
                    }
                }
        ) {
            // 1. AndroidX Media3 PlayerView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        this.resizeMode = resizeMode
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    if (view.resizeMode != resizeMode) {
                        view.resizeMode = resizeMode
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 2. Buffering Spinner with Animite Violet
            if (isBuffering && playerError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(54.dp),
                        color = Color(0xFF8B5CF6),
                        strokeWidth = 3.5.dp,
                    )
                }
            }

            // 3. Double-tap Rewind Feedback Bubble (-10s)
            AnimatedVisibility(
                visible = showLeftSeekFeedback,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 54.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xE6101626),
                    modifier = Modifier.size(76.dp),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "-10s",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // 4. Double-tap Forward Feedback Bubble (+10s)
            AnimatedVisibility(
                visible = showRightSeekFeedback,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 54.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xE6101626),
                    modifier = Modifier.size(76.dp),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+10s",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // 5. Volume HUD Card (matches reference image)
            AnimatedVisibility(
                visible = showVolumeHud,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 40.dp),
            ) {
                GesturesFeedbackCard(isVolume = true, percent = volumePercent)
            }

            // 6. Brightness HUD Card (matches reference image)
            AnimatedVisibility(
                visible = showBrightnessHud,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 40.dp),
            ) {
                GesturesFeedbackCard(isVolume = false, percent = brightnessPercent)
            }

            // 7. Fast Forward 2.0x Long Press Indicator
            AnimatedVisibility(
                visible = isFastForwarding2x,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xE6101626),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6)),
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = "▶▶ ${stringResource(R.string.fast_forward_2x)}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // 8. Floating Unlock Prompt
            AnimatedVisibility(
                visible = isScreenLocked && showLockFloatingUnlock,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xF2101626),
                    shadowElevation = 10.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable {
                        viewModel.setScreenLocked(false)
                        showControls = true
                    },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.tap_to_unlock),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // 8.5 Skip Intro / Outro Floating Button (AniSkip / Chapter markers)
            AnimatedVisibility(
                visible = activeSkipInterval != null && !isScreenLocked,
                enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 96.dp),
            ) {
                activeSkipInterval?.let { interval ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xF2101626),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        shadowElevation = 10.dp,
                        modifier = Modifier.clickable {
                            exoPlayer.seekTo(interval.endTimeMs)
                            hasSkippedCurrentInterval = true
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (interval.skipType.equals("ed", ignoreCase = true)) {
                                    stringResource(R.string.skip_outro)
                                } else {
                                    stringResource(R.string.skip_intro)
                                },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // 9. Error Overlay with Retry Option
            if (playerError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.playback_failed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val friendlyErrorMessage = when {
                            playerError == null -> ""
                            playerError!!.contains("403") || playerError!!.contains("expired") || playerError!!.contains("token") ->
                                stringResource(R.string.stream_token_expired)
                            playerError!!.contains("timeout", ignoreCase = true) || playerError!!.contains("timed out", ignoreCase = true) ->
                                stringResource(R.string.stream_timeout_error)
                            playerError!!.contains("404") || playerError!!.contains("unavailable", ignoreCase = true) || playerError!!.contains("502") || playerError!!.contains("503") ->
                                stringResource(R.string.stream_unavailable_error)
                            else -> playerError!!
                        }

                        Text(
                            text = friendlyErrorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(modifier = Modifier.height(18.dp))

                        val nextAlternativeServer = remember(currentServers, failedServerUrlsInEpisode, activeServer) {
                            currentServers.firstOrNull { it.url !in failedServerUrlsInEpisode && it.url != activeServer.url }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (nextAlternativeServer != null) {
                                Button(
                                    onClick = {
                                        failedServerUrlsInEpisode.add(activeServer.url)
                                        playerError = null
                                        isBuffering = true
                                        selectedQuality = nextAlternativeServer.quality
                                        viewModel.prepareMedia(nextAlternativeServer, positionMs = currentPosition, playWhenReady = true)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp),
                                ) {
                                    Text(stringResource(R.string.try_next_server), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    playerError = null
                                    isBuffering = true
                                    val curEp = episodesList.firstOrNull { it.number == activeEpisodeNumber }
                                    if (curEp != null) {
                                        switchToEpisode(curEp)
                                    } else {
                                        viewModel.prepareMedia(activeServer, positionMs = currentPosition, playWhenReady = true)
                                    }
                                },
                                colors = if (nextAlternativeServer != null) {
                                    ButtonDefaults.outlinedButtonColors()
                                } else {
                                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(stringResource(R.string.retry), color = Color.White, fontWeight = FontWeight.SemiBold)
                            }

                            OutlinedButton(
                                onClick = {
                                    playerError = null
                                    showEpisodesSheet = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            ) {
                                Text(stringResource(R.string.change_source_or_episode), color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 10. CONTROLS OVERLAY (TOP, GESTURES, BOTTOM WITH LTR PROGRESS)
            AnimatedVisibility(
                visible = showControls && !isScreenLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                val currentEp = episodesList.firstOrNull { it.number == activeEpisodeNumber }
                VideoPlayerControls(
                    animeTitle = route.animeTitle,
                    episodeNumber = activeEpisodeNumber,
                    episodeTitle = currentEp?.title,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    isScreenLocked = isScreenLocked,
                    isFullscreen = isLandscape,
                    playbackSpeed = playbackSpeed,
                    currentPositionMs = currentPosition,
                    bufferedPositionMs = bufferedPosition,
                    durationMs = duration,
                    hasPreviousEpisode = hasPreviousEpisode(),
                    hasNextEpisode = hasNextEpisode(),
                    onBack = handleBack,
                    onTogglePlayPause = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onSeek = { targetMs ->
                        exoPlayer.seekTo(targetMs)
                    },
                    onSeekRelative = { offsetMs ->
                        viewModel.seekRelative(offsetMs)
                        if (offsetMs < 0) {
                            showLeftSeekFeedback = true
                            leftSeekKey++
                        } else {
                            showRightSeekFeedback = true
                            rightSeekKey++
                        }
                    },
                    onPreviousEpisode = { playPreviousEpisode() },
                    onNextEpisode = { playNextEpisode() },
                    onToggleFullscreen = { toggleOrientation() },
                    onOpenSpeedSheet = { showSpeedSheet = true },
                    onOpenQualitySheet = { showQualitySheet = true },
                    onOpenAudioSheet = { showAudioSheet = true },
                    onOpenSubtitlesSheet = { showSubtitleSheet = true },
                    hasAudioTracks = audioTracks.isNotEmpty(),
                    hasSubtitleTracks = subtitleTracks.isNotEmpty(),
                    isMuted = isMuted,
                    onToggleMute = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                    onOpenEpisodesSheet = { showEpisodesSheet = true },
                    onOpenMoreSheet = {
                        viewModel.refreshCacheInfo()
                        showMoreSheet = true
                    },
                    onToggleLock = {
                        viewModel.setScreenLocked(true)
                        showControls = false
                    },
                    onPip = { enterPip() },
                )
            }

            // 11. EPISODE SIDE DRAWER (Landscape mode - matching Frame 2 of reference design)
            AnimatedVisibility(
                visible = showEpisodesSheet && isLandscape,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                EpisodeSideDrawer(
                    episodes = episodesList,
                    activeEpisodeNumber = activeEpisodeNumber,
                    onSelectEpisode = { ep ->
                        switchToEpisode(ep)
                    },
                    onDismiss = { showEpisodesSheet = false },
                )
            }
        }
    }

    // MODAL BOTTOM SHEETS
    if (showQualitySheet) {
        QualitySelectionSheet(
            qualityOptions = qualityOptions,
            selectedQuality = selectedQuality,
            isAdaptiveAuto = isAdaptiveAuto,
            effectiveQuality = effectiveQuality,
            onSelectAuto = {
                viewModel.setAutoQualityMode(true)
            },
            onSelectQuality = { opt ->
                viewModel.setAutoQualityMode(false)
                selectedQuality = opt.quality.canonicalName
                val videoTrack = viewModel.videoTracks.value.firstOrNull { it.height == opt.quality.height }
                if (videoTrack != null) {
                    viewModel.selectVideoTrack(videoTrack)
                } else {
                    viewModel.prepareMedia(opt.primaryServer, positionMs = currentPosition, playWhenReady = true)
                }
            },
            onDismiss = { showQualitySheet = false }
        )
    }

    if (showAudioSheet) {
        AudioSelectionSheet(
            audioTracks = audioTracks,
            onSelectTrack = { track ->
                viewModel.selectAudioTrack(track)
            },
            onDismiss = { showAudioSheet = false }
        )
    }

    if (showSubtitleSheet) {
        SubtitleSelectionSheet(
            subtitleTracks = subtitleTracks,
            subtitlesEnabled = subtitlesEnabled,
            onSelectTrack = { track ->
                viewModel.selectSubtitleTrack(track)
            },
            onDismiss = { showSubtitleSheet = false }
        )
    }

    if (showSpeedSheet) {
        PlaybackSpeedSheet(
            currentSpeed = playbackSpeed,
            onSelectSpeed = { newSpeed ->
                viewModel.setPlaybackSpeed(newSpeed)
            },
            onDismiss = { showSpeedSheet = false }
        )
    }

    // Bottom sheet for portrait mode fallback
    if (showEpisodesSheet && !isLandscape) {
        EpisodeListSheet(
            episodes = episodesList,
            activeEpisodeNumber = activeEpisodeNumber,
            onSelectEpisode = { ep ->
                switchToEpisode(ep)
            },
            onDismiss = { showEpisodesSheet = false }
        )
    }

    if (showMoreSheet) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showMoreSheet = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF2111625),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.player_settings),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Aspect Ratio Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextMode = when (resizeMode) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                                viewModel.setResizeMode(nextMode)
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.aspect_ratio), color = Color.White, fontSize = 14.sp)
                        val modeLabel = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> stringResource(R.string.aspect_fill)
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> stringResource(R.string.aspect_zoom)
                            else -> stringResource(R.string.aspect_fit)
                        }
                        Text(text = modeLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Skip Intro & Outro Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleSkipIntro(!isSkipIntroEnabled)
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.skip_intro_settings), color = Color.White, fontSize = 14.sp)
                        val skipStatusLabel = if (isSkipIntroEnabled) stringResource(R.string.subtitles_enabled) else stringResource(R.string.subtitles_disabled)
                        Text(
                            text = skipStatusLabel,
                            color = if (isSkipIntroEnabled) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sleep Timer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nextTimer = when (sleepTimerMinutes) {
                                    null -> 15
                                    15 -> 30
                                    30 -> 60
                                    60 -> -1
                                    else -> null
                                }
                                viewModel.setSleepTimer(nextTimer)
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = stringResource(R.string.sleep_timer), color = Color.White, fontSize = 14.sp)
                        val timerLabel = when (sleepTimerMinutes) {
                            15 -> stringResource(R.string.timer_15_min)
                            30 -> stringResource(R.string.timer_30_min)
                            60 -> stringResource(R.string.timer_60_min)
                            -1 -> stringResource(R.string.timer_end_of_episode)
                            else -> stringResource(R.string.timer_off)
                        }
                        Text(text = timerLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Custom Intro Timing Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF161C2C))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.custom_intro_title),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                            val introDesc = if (customIntroInterval != null) {
                                "${PlayerProgressState.formatPlayerTime(customIntroInterval!!.first)} - ${PlayerProgressState.formatPlayerTime(customIntroInterval!!.second)}"
                            } else {
                                stringResource(R.string.no_intro_set)
                            }
                            Text(
                                text = introDesc,
                                color = if (customIntroInterval != null) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val endMs = customIntroInterval?.second ?: (currentPosition + 90000L)
                                    EpisodeWatchManager.saveCustomIntro(context, route.animeTitle, currentPosition, endMs)
                                    val updated = EpisodeWatchManager.getCustomIntro(context, route.animeTitle)
                                    customIntroInterval = updated
                                    val epNum = activeEpisodeNumber.toFloatOrNull() ?: 1.0f
                                    viewModel.loadSkipIntervals(listOf(route.animeTitle), epNum, updated)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "${stringResource(R.string.set_intro_start)} (${PlayerProgressState.formatPlayerTime(currentPosition)})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val startMs = customIntroInterval?.first ?: 0L
                                    if (currentPosition > startMs) {
                                        EpisodeWatchManager.saveCustomIntro(context, route.animeTitle, startMs, currentPosition)
                                        val updated = EpisodeWatchManager.getCustomIntro(context, route.animeTitle)
                                        customIntroInterval = updated
                                        val epNum = activeEpisodeNumber.toFloatOrNull() ?: 1.0f
                                        viewModel.loadSkipIntervals(listOf(route.animeTitle), epNum, updated)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "${stringResource(R.string.set_intro_end)} (${PlayerProgressState.formatPlayerTime(currentPosition)})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        if (customIntroInterval != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(
                                onClick = {
                                    EpisodeWatchManager.clearCustomIntro(context, route.animeTitle)
                                    customIntroInterval = null
                                    val epNum = activeEpisodeNumber.toFloatOrNull() ?: 1.0f
                                    viewModel.loadSkipIntervals(listOf(route.animeTitle), epNum, null)
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(
                                    text = stringResource(R.string.clear_custom_intro),
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Clear Cache Button
                    Button(
                        onClick = {
                            viewModel.cacheManager.clearCache()
                            viewModel.refreshCacheInfo()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2638)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = stringResource(R.string.clear_cache), color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
