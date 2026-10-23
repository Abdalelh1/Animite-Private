package com.imashnake.animite.social.ui.profile

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage

/**
 * Premium, lifecycle-aware Live Cover Player supporting MP4 videos, animated GIFs,
 * and high-fidelity poster fallbacks with smooth crossfade and responsive 3:1 ratio.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveCoverPlayer(
    liveCoverUrl: String?,
    posterUrl: String?,
    mediaType: String?,
    fallbackBannerUrl: String?,
    isOwner: Boolean,
    modifier: Modifier = Modifier,
    isCoverVisible: Boolean = true,
    parallaxOffset: Float = 0f,
    onCoverClick: () -> Unit = {},
    onAddCoverClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isVideo = remember(liveCoverUrl, mediaType) {
        mediaType == "video/mp4" || (liveCoverUrl != null && liveCoverUrl.contains(".mp4", ignoreCase = true))
    }
    val isGif = remember(liveCoverUrl, mediaType) {
        mediaType == "image/gif" || (liveCoverUrl != null && liveCoverUrl.contains(".gif", ignoreCase = true))
    }
    val hasLiveCover = !liveCoverUrl.isNullOrBlank()

    // System Data-Saver and Reduced-Motion detection
    val isDataSaverActive = remember(context) { checkDataSaver(context) }
    val isReducedMotionActive = remember(context) { checkReducedMotion(context) }

    // Playback state
    var isPlaying by remember { mutableStateOf(!isDataSaverActive && !isReducedMotionActive) }
    var isMuted by remember { mutableStateOf(true) }
    var isVideoBuffering by remember { mutableStateOf(false) }
    var isVideoReady by remember { mutableStateOf(false) }
    var hasPlaybackError by remember { mutableStateOf(false) }

    // ExoPlayer management
    val exoPlayer = remember(context, liveCoverUrl, isVideo) {
        if (isVideo && !liveCoverUrl.isNullOrBlank()) {
            ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f
                setMediaItem(MediaItem.fromUri(liveCoverUrl))
                prepare()
            }
        } else {
            null
        }
    }

    // Attach ExoPlayer listeners
    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isVideoBuffering = true
                    }
                    Player.STATE_READY -> {
                        isVideoBuffering = false
                        isVideoReady = true
                        hasPlaybackError = false
                    }
                    Player.STATE_ENDED -> {
                        isVideoBuffering = false
                    }
                    Player.STATE_IDLE -> {}
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isVideoBuffering = false
                hasPlaybackError = true
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Play/Pause control based on visibility, user preference, and screen lifecycle
    LaunchedEffect(exoPlayer, isPlaying, isCoverVisible, isMuted) {
        exoPlayer?.let { player ->
            player.playWhenReady = isPlaying && isCoverVisible
            player.volume = if (isMuted) 0f else 1f
        }
    }

    // Observe Activity / Screen lifecycle to pause media in background
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    player.playWhenReady = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlaying && isCoverVisible) {
                        player.playWhenReady = true
                    }
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Subtle Live indicator pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "LivePulse")
    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LivePulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = parallaxOffset * 0.35f
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCoverClick() }
    ) {
        // LAYER 1: Base static fallback / Static banner / Gradient
        val effectivePoster = posterUrl ?: fallbackBannerUrl
        if (!effectivePoster.isNullOrBlank()) {
            AsyncImage(
                model = effectivePoster,
                contentDescription = "Profile Cover Poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!hasLiveCover) {
            // Modern Anime Glow Gradient fallback
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }

        // LAYER 2: Animated Media (GIF or MP4 Video)
        if (hasLiveCover && !hasPlaybackError) {
            if (isGif) {
                AsyncImage(
                    model = liveCoverUrl,
                    contentDescription = "Animated Live Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isVideo && exoPlayer != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            this.player = exoPlayer
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (isVideoReady) 1f else 0f
                        }
                )
            }
        }

        // LAYER 3: Tasteful loading indicator during buffering
        AnimatedVisibility(
            visible = isVideoBuffering,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                }
            }
        }

        // LAYER 4: Scrim Gradients for Contrast
        // Top scrim for status bar readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom scrim for avatar edge overlap contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        // LAYER 5: Live Cover Badge & Controls (Bottom-Right / Top-End)
        if (hasLiveCover) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Play / Pause Toggle Button
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(32.dp)
                ) {
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause cover" else "Play cover",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Audio Mute / Unmute Button (for videos)
                if (isVideo) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) "Unmute cover" else "Mute cover",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // "LIVE COVER" Pill Indicator
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPlaying) Color(0xFF00E676).copy(alpha = livePulseAlpha)
                                    else Color.White.copy(alpha = 0.6f)
                                )
                        )
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        } else if (isOwner && onAddCoverClick != null) {
            // Elegant "Add Cover" button for profile owner when no cover is set
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 44.dp)
                    .clickable { onAddCoverClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add Cover",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "غلاف متحرك",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Checks whether Android Data Saver or metered network constraints are active.
 */
private fun checkDataSaver(context: Context): Boolean {
    return runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null && cm.isActiveNetworkMetered) {
            cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } else {
            false
        }
    }.getOrDefault(false)
}

/**
 * Checks whether the user has enabled Reduced Motion in Android Accessibility Settings.
 */
private fun checkReducedMotion(context: Context): Boolean {
    return runCatching {
        val durationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        durationScale == 0f
    }.getOrDefault(false)
}
