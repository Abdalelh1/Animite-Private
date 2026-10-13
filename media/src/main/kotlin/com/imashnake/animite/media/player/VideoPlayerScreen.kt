package com.imashnake.animite.media.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
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
import com.imashnake.animite.core.ui.LocalPaddings
import com.imashnake.animite.media.R
import com.imashnake.animite.media.player.cache.MediaCacheManager
import com.imashnake.animite.media.source.AnimeStreamServer
import com.imashnake.animite.media.source.StreamLinkType
import com.imashnake.animite.navigation.VideoPlayerRoute
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.util.Locale

private data class EpisodeUiModel(
    val number: String,
    val title: String,
    val url: String,
    val durationText: String = "24:00",
    val thumbnail: String? = null,
)

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

    val exoPlayer = remember { viewModel.getOrCreatePlayer() }

    // Parse direct servers from route
    val parsedDirectServers = remember(route.serversJson, route.initialUrl) {
        val parsedList = mutableListOf<AnimeStreamServer>()
        try {
            if (route.serversJson.isNotEmpty() && route.serversJson != "[]") {
                val jsonArr = JSONArray(route.serversJson)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val name = obj.optString("name", "HD")
                    val url = obj.optString("url")
                    val quality = obj.optString("quality", "HD")
                    val isDirect = obj.optBoolean("isDirectVideo", true)
                    val linkTypeName = obj.optString("linkType", StreamLinkType.DIRECT_VIDEO.name)
                    val linkType = try {
                        StreamLinkType.valueOf(linkTypeName)
                    } catch (_: Exception) {
                        StreamLinkType.DIRECT_VIDEO
                    }

                    if (url.isNotEmpty() && linkType == StreamLinkType.DIRECT_VIDEO) {
                        val headersMap = mutableMapOf<String, String>()
                        val headersObj = obj.optJSONObject("headers")
                        headersObj?.keys()?.forEach { key ->
                            headersMap[key] = headersObj.getString(key)
                        }

                        parsedList.add(
                            AnimeStreamServer(
                                name = name,
                                url = url,
                                isDirectVideo = isDirect,
                                quality = quality,
                                linkType = linkType,
                                headers = headersMap,
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        if (parsedList.isEmpty() && route.initialUrl.isNotEmpty()) {
            parsedList.add(
                AnimeStreamServer(
                    name = route.initialQuality.ifEmpty { "HD" },
                    url = route.initialUrl,
                    isDirectVideo = true,
                    quality = route.initialQuality.ifEmpty { "HD" },
                    linkType = StreamLinkType.DIRECT_VIDEO,
                )
            )
        }

        parsedList.distinctBy { it.quality }
    }

    var currentServers by remember { mutableStateOf(parsedDirectServers) }

    var selectedQuality by remember {
        mutableStateOf(route.initialQuality.ifEmpty { currentServers.firstOrNull()?.quality ?: "HD" })
    }

    val activeServer = remember(selectedQuality, currentServers) {
        currentServers.firstOrNull { it.quality == selectedQuality }
            ?: currentServers.firstOrNull()
            ?: AnimeStreamServer(
                name = route.initialQuality.ifEmpty { "HD" },
                url = route.initialUrl,
                isDirectVideo = true,
                quality = route.initialQuality.ifEmpty { "HD" },
            )
    }

    // Parse Episodes list from route
    var activeEpisodeNumber by remember {
        mutableStateOf(route.episodeNumber.ifEmpty { "1" })
    }

    val episodesList = remember(route.episodesJson, activeEpisodeNumber) {
        val list = mutableListOf<EpisodeUiModel>()
        try {
            if (route.episodesJson.isNotEmpty() && route.episodesJson != "[]") {
                val jsonArr = JSONArray(route.episodesJson)
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    val epNum = obj.optString("number", "${i + 1}").removeSuffix(".0")
                    val title = obj.optString("title", "Episode $epNum")
                    val url = obj.optString("url", "")
                    val thumb = if (obj.has("thumbnail")) obj.optString("thumbnail").takeIf { it.isNotBlank() } else null
                    list.add(EpisodeUiModel(number = epNum, title = title, url = url, thumbnail = thumb))
                }
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            val baseNum = activeEpisodeNumber.toFloatOrNull()?.toInt() ?: 1
            val total = maxOf(12, baseNum + 5)
            for (i in 1..total) {
                list.add(
                    EpisodeUiModel(
                        number = i.toString(),
                        title = "Episode $i",
                        url = "",
                    )
                )
            }
        }
        list
    }

    // Playback states
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // Slider scrubbing state
    var isUserDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    // Dialogs & Drawer overlays
    var showEpisodeDrawer by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitlesDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var showMoreDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }

    // Aspect ratio HUD
    var aspectRatioToastText by remember { mutableStateOf<String?>(null) }

    // Gestures HUD feedback
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

    var showLockFloatingUnlock by remember { mutableStateOf(false) }
    var lockFloatingKey by remember { mutableIntStateOf(0) }

    // ViewModel states
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsState()
    val cacheLimitBytes by viewModel.cacheLimitBytes.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val resizeMode by viewModel.resizeMode.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val isScreenLocked by viewModel.isScreenLocked.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val sleepTimerSecondsLeft by viewModel.sleepTimerSecondsLeft.collectAsState()

    // Helper to switch to episode
    fun switchToEpisode(item: EpisodeUiModel) {
        activeEpisodeNumber = item.number
        if (route.providerId.isNotEmpty() && item.url.isNotEmpty()) {
            isBuffering = true
            viewModel.loadEpisodeStream(
                providerId = route.providerId,
                episodeUrl = item.url,
                onSuccess = { servers ->
                    val direct = servers.filter { it.isDirectVideo && it.url.isNotEmpty() }
                    if (direct.isNotEmpty()) {
                        currentServers = direct
                        val chosen = direct.firstOrNull { it.quality == selectedQuality } ?: direct.first()
                        viewModel.prepareMedia(chosen, positionMs = 0L, playWhenReady = true)
                    } else {
                        isBuffering = false
                    }
                },
                onError = { err ->
                    playerError = err
                    isBuffering = false
                }
            )
        } else {
            exoPlayer.seekTo(0L)
        }
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
                duration = if (exoPlayer.duration > 0) exoPlayer.duration else 0L
                bufferedPosition = exoPlayer.bufferedPosition
                if (playbackState == Player.STATE_ENDED) {
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
                playerError = error.localizedMessage ?: "تعذر تشغيل الفيديو، تحقق من الرابط أو الاتصال"
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Activity window setup, Landscape by default, Fullscreen insets & lifecycle
    DisposableEffect(lifecycleOwner, activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (activity != null) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isLandscape = true
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    exoPlayer.pause()
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
            viewModel.releasePlayer()
        }
    }

    // Prepare active server media
    LaunchedEffect(activeServer.url) {
        val resumePos = if (currentPosition > 0) currentPosition else 0L
        viewModel.prepareMedia(activeServer, positionMs = resumePos, playWhenReady = true)
    }

    // Progress ticker
    LaunchedEffect(isPlaying, isUserDraggingSlider) {
        while (true) {
            if (!isUserDraggingSlider && exoPlayer.playbackState == Player.STATE_READY) {
                currentPosition = exoPlayer.currentPosition
                bufferedPosition = exoPlayer.bufferedPosition
                if (exoPlayer.duration > 0) {
                    duration = exoPlayer.duration
                }
            }
            delay(400)
        }
    }

    // Auto-hide controls after 4 seconds of playback
    LaunchedEffect(showControls, isPlaying, isUserDraggingSlider, isScreenLocked, showEpisodeDrawer) {
        if (showControls && isPlaying && !isUserDraggingSlider && !isScreenLocked && !showEpisodeDrawer) {
            delay(4000)
            showControls = false
        }
    }

    // Auto-hide gestures feedback
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

    LaunchedEffect(aspectRatioToastText) {
        if (aspectRatioToastText != null) {
            delay(1500)
            aspectRatioToastText = null
        }
    }

    // Fullscreen / Orientation toggle
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

    // Picture-in-Picture
    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPip = context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
            if (hasPip && activity != null) {
                showControls = false
                showEpisodeDrawer = false
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
        color = Color.Black,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isScreenLocked, showEpisodeDrawer) {
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
                            onTap = {
                                if (showEpisodeDrawer) {
                                    showEpisodeDrawer = false
                                } else {
                                    showControls = !showControls
                                }
                            }
                        )
                    }
                }
                .pointerInput(isScreenLocked, showEpisodeDrawer) {
                    if (!isScreenLocked && !showEpisodeDrawer) {
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
                },
        ) {
            // AndroidX Media3 PlayerView
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

            // Buffering Spinner with Animite Primary Token
            if (isBuffering && playerError == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(52.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                }
            }

            // Left seek feedback bubble (-10s)
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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(76.dp),
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_replay_10),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "-10s",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Right seek feedback bubble (+10s)
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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(76.dp),
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_forward_10),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "+10s",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Volume HUD Overlay
            AnimatedVisibility(
                visible = showVolumeHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 36.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(8.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (isMuted) R.drawable.ic_player_volume_off else R.drawable.ic_player_volume
                            ),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = "$volumePercent%",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Brightness HUD Overlay
            AnimatedVisibility(
                visible = showBrightnessHud,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 36.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.padding(8.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_brightness),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = "$brightnessPercent%",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // Aspect Ratio HUD Toast
            AnimatedVisibility(
                visible = aspectRatioToastText != null,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_aspect_ratio),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = aspectRatioToastText ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Floating Unlock Button when screen is locked
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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shadowElevation = 8.dp,
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
                            painter = painterResource(R.drawable.ic_player_unlock),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = stringResource(R.string.tap_to_unlock),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // CONTROLS OVERLAY
            AnimatedVisibility(
                visible = showControls && !isScreenLocked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.82f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.86f),
                                )
                            )
                        ),
                ) {
                    // TOP BAR
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Left: Back button + Anime Title + Episode Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Text(
                                text = route.animeTitle.ifEmpty { "Animite" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            ) {
                                Text(
                                    text = stringResource(R.string.episode_label, activeEpisodeNumber),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }

                        // Right: Top Control Icons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // 1. Playback Speed
                            IconButton(
                                onClick = { showSpeedDialog = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_speed),
                                    contentDescription = stringResource(R.string.playback_speed),
                                    tint = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // 2. Sleep Timer
                            IconButton(
                                onClick = { showTimerDialog = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_timer),
                                    contentDescription = stringResource(R.string.sleep_timer),
                                    tint = if (sleepTimerMinutes != null) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // 3. Aspect Ratio
                            IconButton(
                                onClick = {
                                    val (nextMode, label) = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT ->
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL ->
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
                                        else ->
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit"
                                    }
                                    viewModel.setResizeMode(nextMode)
                                    aspectRatioToastText = label
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_aspect_ratio),
                                    contentDescription = stringResource(R.string.aspect_ratio),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // 4. Quality Selector
                            IconButton(
                                onClick = { showQualityDialog = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_settings),
                                    contentDescription = stringResource(R.string.select_quality),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // 5. Subtitles
                            IconButton(
                                onClick = { showSubtitlesDialog = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_subtitles),
                                    contentDescription = stringResource(R.string.subtitles),
                                    tint = if (subtitlesEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // 6. Audio Track
                            IconButton(
                                onClick = { showAudioDialog = true },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_audio_track),
                                    contentDescription = stringResource(R.string.audio_track),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            // 7. PiP
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                IconButton(
                                    onClick = { enterPip() },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_player_pip),
                                        contentDescription = stringResource(R.string.pip_mode),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }

                            // 8. More Settings
                            IconButton(
                                onClick = {
                                    viewModel.refreshCacheInfo()
                                    showMoreDialog = true
                                },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_player_more),
                                    contentDescription = stringResource(R.string.more_options),
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // CENTER PLAYBACK CONTROLS
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        // Rewind -10s
                        IconButton(
                            onClick = {
                                viewModel.seekRelative(-10000)
                                showLeftSeekFeedback = true
                                leftSeekKey++
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_player_replay_10),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        // Previous Episode
                        IconButton(
                            onClick = { playPreviousEpisode() },
                            enabled = hasPreviousEpisode(),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_player_prev_ep),
                                contentDescription = null,
                                tint = if (hasPreviousEpisode()) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Main Play / Pause Button with Animite Primary Token
                        IconButton(
                            onClick = {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            },
                            modifier = Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .shadow(8.dp, CircleShape),
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play
                                ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp),
                            )
                        }

                        // Next Episode
                        IconButton(
                            onClick = { playNextEpisode() },
                            enabled = hasNextEpisode(),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_player_next_ep),
                                contentDescription = null,
                                tint = if (hasNextEpisode()) Color.White else Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        // Forward +10s
                        IconButton(
                            onClick = {
                                viewModel.seekRelative(10000)
                                showRightSeekFeedback = true
                                rightSeekKey++
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_player_forward_10),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    // BOTTOM PANEL: Progress Timeline + Action Row
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // PROGRESS TIMELINE WITH TIMESTAMPS
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = formatDuration(currentPosition),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                val total = duration.coerceAtLeast(1L).toFloat()
                                val playedFrac = if (isUserDraggingSlider) sliderPosition else (currentPosition.toFloat() / total).coerceIn(0f, 1f)
                                val bufferedFrac = (bufferedPosition.toFloat() / total).coerceIn(0f, 1f)

                                // Progress Canvas
                                val primaryColor = MaterialTheme.colorScheme.primary
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                ) {
                                    // Inactive Track
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.2f),
                                        size = size,
                                    )
                                    // Buffered Track
                                    drawRect(
                                        color = Color.White.copy(alpha = 0.45f),
                                        size = size.copy(width = size.width * bufferedFrac),
                                    )
                                    // Played Track (Animite Primary)
                                    drawRect(
                                        color = primaryColor,
                                        size = size.copy(width = size.width * playedFrac),
                                    )
                                }

                                // Interactive Slider with Native Animite Thumb
                                Slider(
                                    value = if (isUserDraggingSlider) sliderPosition else if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { frac ->
                                        isUserDraggingSlider = true
                                        sliderPosition = frac
                                    },
                                    onValueChangeFinished = {
                                        val targetMs = (sliderPosition * duration).toLong()
                                        exoPlayer.seekTo(targetMs)
                                        isUserDraggingSlider = false
                                    },
                                    thumb = {
                                        Box(
                                            modifier = Modifier
                                                .size(15.dp)
                                                .shadow(4.dp, CircleShape)
                                                .background(primaryColor, CircleShape)
                                                .clip(CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(Color.White, CircleShape)
                                            )
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color.Transparent,
                                        inactiveTrackColor = Color.Transparent,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = formatDuration(duration),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        // BOTTOM ACTIONS ROW
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Left actions: Lock & Volume
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                IconButton(
                                    onClick = { viewModel.setScreenLocked(true) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_player_lock),
                                        contentDescription = stringResource(R.string.screen_locked),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isMuted) R.drawable.ic_player_volume_off else R.drawable.ic_player_volume
                                        ),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }

                            // Right actions: Episodes Drawer & Fullscreen Orientation Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        showEpisodeDrawer = !showEpisodeDrawer
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.episodes),
                                        contentDescription = stringResource(R.string.episodes_drawer),
                                        tint = if (showEpisodeDrawer) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }

                                IconButton(
                                    onClick = { toggleOrientation() },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (isLandscape) R.drawable.ic_player_fullscreen_exit else R.drawable.ic_player_fullscreen
                                        ),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // RIGHT-SIDE EPISODE DRAWER PANEL
            AnimatedVisibility(
                visible = showEpisodeDrawer,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(320.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxSize(),
                    shadowElevation = 12.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 14.dp, horizontal = 16.dp)
                    ) {
                        // Drawer Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            IconButton(
                                onClick = { showEpisodeDrawer = false },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.episodes_drawer),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        // Episodes List
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(episodesList) { epItem ->
                                val isCurrent = epItem.number == activeEpisodeNumber
                                val epNumFloat = epItem.number.toFloatOrNull() ?: 1f
                                val currentEpFloat = activeEpisodeNumber.toFloatOrNull() ?: 1f
                                val isWatched = epNumFloat < currentEpFloat

                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                        }
                                    ),
                                    border = if (isCurrent) {
                                        BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isCurrent) {
                                                switchToEpisode(epItem)
                                            }
                                        },
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = "Episode ${epItem.number.removeSuffix(".0")}",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = if (isCurrent && duration > 0) formatDuration(duration) else epItem.durationText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Progress indicator line
                                        val primaryColor = MaterialTheme.colorScheme.primary
                                        Canvas(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                        ) {
                                            drawRect(
                                                color = Color.White.copy(alpha = 0.12f),
                                                size = size,
                                            )
                                            val progressFraction = when {
                                                isCurrent -> if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0.85f
                                                isWatched -> 1.0f
                                                else -> 0.0f
                                            }
                                            if (progressFraction > 0f) {
                                                drawRect(
                                                    color = primaryColor,
                                                    size = size.copy(width = size.width * progressFraction)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Error Overlay with Retry
            if (playerError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = stringResource(R.string.playback_failed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = playerError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = {
                                    playerError = null
                                    viewModel.prepareMedia(activeServer, positionMs = currentPosition, playWhenReady = true)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.retry), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 1) PLAYBACK SPEED DIALOG
    if (showSpeedDialog) {
        Dialog(onDismissRequest = { showSpeedDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.playback_speed),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { showSpeedDialog = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${speed}x",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 2) QUALITY SELECTION DIALOG
    if (showQualityDialog) {
        Dialog(onDismissRequest = { showQualityDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.select_quality),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { showQualityDialog = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (currentServers.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_playable_quality),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        currentServers.forEach { server ->
                            val isSelected = server.quality == selectedQuality
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedQuality = server.quality
                                        val resumePos = exoPlayer.currentPosition
                                        viewModel.prepareMedia(server, positionMs = resumePos, playWhenReady = true)
                                        showQualityDialog = false
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = server.quality,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3) SUBTITLES DIALOG
    if (showSubtitlesDialog) {
        Dialog(onDismissRequest = { showSubtitlesDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.subtitles),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { showSubtitlesDialog = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // Toggle Subtitles On/Off
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (subtitlesEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = if (subtitlesEnabled) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleSubtitles(!subtitlesEnabled)
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (subtitlesEnabled) stringResource(R.string.subtitles_enabled) else stringResource(R.string.subtitles_disabled),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (subtitlesEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (subtitlesEnabled) "ON" else "OFF",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (subtitlesEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Subtitle Tracks if present
                    if (subtitleTracks.isNotEmpty() && subtitlesEnabled) {
                        Text(
                            text = "Tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        subtitleTracks.forEach { track ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (track.isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectSubtitleTrack(track)
                                        showSubtitlesDialog = false
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (track.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (track.isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 4) AUDIO TRACK DIALOG
    if (showAudioDialog) {
        Dialog(onDismissRequest = { showAudioDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.audio_track),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { showAudioDialog = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (audioTracks.isNotEmpty()) {
                        audioTracks.forEach { track ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (track.isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                },
                                border = if (track.isSelected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectAudioTrack(track)
                                        showAudioDialog = false
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = track.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (track.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (track.isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Default Japanese stereo fallback
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Default Audio (Stereo)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 5) SLEEP TIMER DIALOG
    if (showTimerDialog) {
        Dialog(onDismissRequest = { showTimerDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.sleep_timer),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { showTimerDialog = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    val timerOptions = listOf(
                        null to stringResource(R.string.timer_off),
                        15 to stringResource(R.string.timer_15_min),
                        30 to stringResource(R.string.timer_30_min),
                        45 to stringResource(R.string.timer_45_min),
                        60 to stringResource(R.string.timer_60_min),
                        -1 to stringResource(R.string.timer_end_of_episode),
                    )

                    timerOptions.forEach { (mins, label) ->
                        val isSelected = sleepTimerMinutes == mins
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            border = if (isSelected) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSleepTimer(mins)
                                    showTimerDialog = false
                                },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isSelected) {
                                    val statusText = if (mins != null && mins > 0 && sleepTimerSecondsLeft != null) {
                                        "${(sleepTimerSecondsLeft ?: 0) / 60}m"
                                    } else null
                                    if (statusText != null) {
                                        Text(text = statusText, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 6) MORE OPTIONS / CACHE SETTINGS DIALOG
    if (showMoreDialog) {
        Dialog(onDismissRequest = { showMoreDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.player_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(onClick = { showMoreDialog = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    // CACHE SECTION
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.cache_settings),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.current_cache_size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = viewModel.cacheManager.formatBytes(cacheSizeBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Button(
                            onClick = { viewModel.clearCache() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.clear_cache),
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = stringResource(R.string.cache_limit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            listOf(
                                MediaCacheManager.CACHE_LIMIT_250MB to "250 MB",
                                MediaCacheManager.CACHE_LIMIT_500MB to "500 MB",
                                MediaCacheManager.CACHE_LIMIT_1GB to "1 GB",
                            ).forEach { (limitBytes, label) ->
                                val isSelected = cacheLimitBytes == limitBytes
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = if (isSelected) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    } else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setCacheLimit(limitBytes) },
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(vertical = 10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
