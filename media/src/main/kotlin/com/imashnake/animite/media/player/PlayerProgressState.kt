package com.imashnake.animite.media.player

import java.util.Locale

/**
 * Pure calculation functions and state logic for the video player timeline and progress bar.
 * Enforces strict Left-to-Right (LTR) mathematical consistency regardless of UI layout direction.
 */
object PlayerProgressState {

    /**
     * Calculates the fractional playback progress in range [0.0f, 1.0f].
     * Safely handles unknown, negative, or zero duration without division by zero.
     */
    fun calculateProgress(positionMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Converts a fractional slider position [0.0f, 1.0f] into millisecond position.
     * Safely bounds the result within [0, durationMs].
     */
    fun calculatePositionFromFraction(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val safeFrac = fraction.coerceIn(0f, 1f)
        return (safeFrac * durationMs).toLong().coerceIn(0L, durationMs)
    }

    /**
     * Calculates the exact horizontal pixel offset of the thumb along the track width.
     * At fraction 0.0f -> 0.0f (Left).
     * At fraction 1.0f -> trackWidthPx (Right).
     */
    fun calculateThumbOffset(fraction: Float, trackWidthPx: Float): Float {
        if (trackWidthPx <= 0f) return 0f
        return fraction.coerceIn(0f, 1f) * trackWidthPx
    }

    /**
     * Formats milliseconds into standard media timestamp (MM:SS or H:MM:SS).
     * Uses Locale.US to ensure predictable, consistent digital clock display.
     */
    fun formatPlayerTime(timeMs: Long): String {
        if (timeMs <= 0L) return "00:00"
        val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
