package com.imashnake.animite.media.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Professional Video Progress Bar for Animite.
 *
 * CRITICAL RULE:
 * Enforces strict Left-to-Right (LTR) timeline and scrubbing behavior under ALL circumstances,
 * ensuring 00:00 is on the far left, duration is on the far right, dragging to the right advances,
 * and dragging to the left rewinds, even when the app language is Arabic (RTL).
 */
@Composable
fun VideoProgressBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF22C55E),
    accentGradientEnd: Color = Color(0xFF10B981),
) {
    // ENFORCE STRICT LTR SCOPE
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        var isDragging by remember { mutableStateOf(false) }
        var dragFraction by remember { mutableFloatStateOf(0f) }
        var trackWidthPx by remember { mutableFloatStateOf(0f) }

        val safeDuration = durationMs.coerceAtLeast(0L)
        val activeFraction = if (isDragging) {
            dragFraction
        } else {
            PlayerProgressState.calculateProgress(currentPositionMs, safeDuration)
        }
        val bufferedFraction = PlayerProgressState.calculateProgress(bufferedPositionMs, safeDuration)

        val trackHeightDp by animateDpAsState(
            targetValue = if (isDragging) 6.dp else 4.dp,
            label = "trackHeightAnimation"
        )
        val thumbRadiusDp by animateDpAsState(
            targetValue = if (isDragging) 8.dp else 6.dp,
            label = "thumbRadiusAnimation"
        )

        val density = LocalDensity.current

        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEFT TIMESTAMP: Current playing or scrubbed time
            val displayCurrentMs = if (isDragging) {
                PlayerProgressState.calculatePositionFromFraction(dragFraction, safeDuration)
            } else {
                currentPositionMs
            }
            Text(
                text = PlayerProgressState.formatPlayerTime(displayCurrentMs),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier.padding(end = 12.dp)
            )

            // CENTER TIMELINE TRACK WITH THUMB & TOOLTIP
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .onSizeChanged { size ->
                        trackWidthPx = size.width.toFloat()
                    }
                    .pointerInput(safeDuration) {
                        detectTapGestures { tapOffset ->
                            if (size.width > 0 && safeDuration > 0L) {
                                val frac = (tapOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val target = PlayerProgressState.calculatePositionFromFraction(frac, safeDuration)
                                onSeek(target)
                            }
                        }
                    }
                    .pointerInput(safeDuration) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                if (size.width > 0) {
                                    isDragging = true
                                    dragFraction = (startOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            },
                            onDragEnd = {
                                if (safeDuration > 0L) {
                                    val target = PlayerProgressState.calculatePositionFromFraction(dragFraction, safeDuration)
                                    onSeek(target)
                                }
                                isDragging = false
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (size.width > 0) {
                                    dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                val trackHeightPx = with(density) { trackHeightDp.toPx() }
                val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }

                // Canvas Track: Background + Buffer + Played
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(trackHeightDp)
                        .clip(RoundedCornerShape(3.dp))
                ) {
                    val w = size.width
                    val h = size.height

                    // 1. Inactive Track
                    drawRoundRect(
                        color = Color(0xFF222938),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(h / 2, h / 2),
                    )

                    // 2. Buffered Track (from left to buffered position)
                    if (bufferedFraction > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.28f),
                            size = Size(w * bufferedFraction, h),
                            cornerRadius = CornerRadius(h / 2, h / 2),
                        )
                    }

                    // 3. Played Track (from left to current progress with smooth gradient)
                    if (activeFraction > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(accentColor, accentGradientEnd),
                                startX = 0f,
                                endX = w * activeFraction
                            ),
                            size = Size(w * activeFraction, h),
                            cornerRadius = CornerRadius(h / 2, h / 2),
                        )
                    }
                }

                // Interactive Thumb Element
                val thumbOffsetPx = PlayerProgressState.calculateThumbOffset(activeFraction, trackWidthPx)
                val thumbDiameterDp = (thumbRadiusDp * 2)

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (thumbOffsetPx - thumbRadiusPx).roundToInt(),
                                y = 0
                            )
                        }
                        .size(thumbDiameterDp)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .background(Color.White, CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(thumbRadiusDp)
                            .background(accentColor, CircleShape)
                    )
                }

                // Floating Target Time Tooltip Preview during dragging
                if (isDragging) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xE6101626),
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x408B5CF6)),
                        modifier = Modifier
                            .offset {
                                val tooltipWidthPx = with(density) { 56.dp.toPx() }
                                val tooltipX = (thumbOffsetPx - tooltipWidthPx / 2)
                                    .coerceIn(0f, (trackWidthPx - tooltipWidthPx).coerceAtLeast(0f))
                                IntOffset(
                                    x = tooltipX.roundToInt(),
                                    y = with(density) { -34.dp.roundToPx() }
                                )
                            }
                    ) {
                        val previewMs = PlayerProgressState.calculatePositionFromFraction(dragFraction, safeDuration)
                        Text(
                            text = PlayerProgressState.formatPlayerTime(previewMs),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // RIGHT TIMESTAMP: Total Duration
            Text(
                text = PlayerProgressState.formatPlayerTime(safeDuration),
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}
