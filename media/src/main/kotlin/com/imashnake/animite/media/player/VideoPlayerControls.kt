package com.imashnake.animite.media.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imashnake.animite.media.R

private val DarkOverlayTop = Color(0xD90B0F19)
private val DarkOverlayBottom = Color(0xF20B0F19)
private val ButtonBackground = Color.Black.copy(alpha = 0.35f)

@Composable
fun VideoPlayerControls(
    animeTitle: String,
    episodeNumber: String,
    episodeTitle: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isScreenLocked: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    isMuted: Boolean = false,
    hasAudioTracks: Boolean = false,
    hasSubtitleTracks: Boolean = false,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleMute: () -> Unit = {},
    onOpenSpeedSheet: () -> Unit,
    onOpenQualitySheet: () -> Unit,
    onOpenAudioSheet: () -> Unit = {},
    onOpenSubtitlesSheet: () -> Unit = {},
    onOpenEpisodesSheet: () -> Unit,
    onOpenMoreSheet: () -> Unit,
    onOpenSleepTimer: () -> Unit = onOpenMoreSheet,
    onCast: () -> Unit = onOpenMoreSheet,
    onToggleLock: () -> Unit,
    onPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    // Strict LTR layout scope for media timeline and action controls
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            DarkOverlayTop,
                            Color.Transparent,
                            DarkOverlayBottom
                        )
                    )
                )
        ) {
            // ==========================================
            // 1. TOP BAR
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left: Back button + Anime / Episode Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ButtonBackground)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = animeTitle.ifEmpty { "Animite" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val subtitleText = buildString {
                            if (episodeNumber.isNotEmpty()) {
                                append(stringResource(R.string.episode_label, episodeNumber))
                            }
                            if (!episodeTitle.isNullOrEmpty()) {
                                if (isNotEmpty()) append(" • ")
                                append(episodeTitle)
                            }
                        }

                        if (subtitleText.isNotEmpty()) {
                            Text(
                                text = subtitleText,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right Action Icons (Speed, Quality, Subtitles, Audio, PIP, Settings)
                // Strictly using real functional endpoints without dummy buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Playback Speed
                    IconButton(
                        onClick = onOpenSpeedSheet,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ButtonBackground)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = stringResource(R.string.playback_speed_title),
                            tint = if (playbackSpeed != 1.0f) primaryColor else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Video Quality Selector
                    IconButton(
                        onClick = onOpenQualitySheet,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ButtonBackground)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = stringResource(R.string.quality_title),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Subtitles (only if subtitle tracks are available)
                    if (hasSubtitleTracks) {
                        IconButton(
                            onClick = onOpenSubtitlesSheet,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = stringResource(R.string.subtitles),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Audio Track (only if multiple audio tracks are available)
                    if (hasAudioTracks) {
                        IconButton(
                            onClick = onOpenAudioSheet,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Audiotrack,
                                contentDescription = stringResource(R.string.audio_track),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Picture-in-Picture
                    IconButton(
                        onClick = onPip,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ButtonBackground)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureInPictureAlt,
                            contentDescription = stringResource(R.string.pip_mode),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Player Settings (Aspect Ratio, Sleep Timer, Cache)
                    IconButton(
                        onClick = onOpenMoreSheet,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ButtonBackground)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.player_settings),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ==========================================
            // 2. BOTTOM CONTROLS PANEL
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            ) {
                // PROGRESS BAR (MANDATORY STRICT LTR)
                VideoProgressBar(
                    currentPositionMs = currentPositionMs,
                    bufferedPositionMs = bufferedPositionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    accentColor = primaryColor,
                    accentGradientEnd = primaryColor.copy(alpha = 0.85f),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // ACTION CONTROLS ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // LEFT GROUP: Screen Lock + Mute/Volume
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Screen Lock
                        IconButton(
                            onClick = onToggleLock,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground)
                        ) {
                            Icon(
                                imageVector = if (isScreenLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                contentDescription = stringResource(R.string.screen_locked),
                                tint = if (isScreenLocked) primaryColor else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Mute / Unmute
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(R.string.volume),
                                tint = if (isMuted) Color(0xFFEF4444) else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // CENTER PLAYBACK GROUP:
                    // [Rewind 10s] -> [Previous Ep] -> [Play/Pause / Buffering] -> [Next Ep] -> [Forward 10s]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // Rewind 10s
                        IconButton(
                            onClick = { onSeekRelative(-10000) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Previous Episode
                        IconButton(
                            onClick = onPreviousEpisode,
                            enabled = hasPreviousEpisode,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.28f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Main Play / Pause Button with Buffering Indicator
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                                .clickable(onClick = onTogglePlayPause),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    color = onPrimaryColor,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = onPrimaryColor,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        // Next Episode
                        IconButton(
                            onClick = onNextEpisode,
                            enabled = hasNextEpisode,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.28f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = { onSeekRelative(10000) },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward10,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // RIGHT GROUP: Episodes Drawer + Fullscreen Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Episodes Drawer (AutoMirrored Playlist icon)
                        IconButton(
                            onClick = onOpenEpisodesSheet,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = stringResource(R.string.episodes_title),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Fullscreen Toggle
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ButtonBackground)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating Gestures Feedback HUD (Brightness & Volume).
 * Uses official Material Icons and integrates with Animite's theme.
 */
@Composable
fun GesturesFeedbackCard(
    isVolume: Boolean,
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF0101626),
        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.25f)),
        shadowElevation = 12.dp,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Icon(
                imageVector = if (isVolume) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.BrightnessMedium,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.swipe_to_adjust),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Text(
                text = if (isVolume) stringResource(R.string.volume) else stringResource(R.string.brightness),
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage bar
            val animatedProgress by animateFloatAsState(targetValue = percent / 100f, label = "hudProgress")
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = primaryColor,
                trackColor = Color(0xFF222938),
                modifier = Modifier
                    .width(100.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$percent%",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
    }
}
