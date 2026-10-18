package com.imashnake.animite.media.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerProgressMathTest {

    @Test
    fun `calculateProgress normal ratio calculation`() {
        val position = 300_000L // 5 mins
        val duration = 1_200_000L // 20 mins
        val progress = PlayerProgressState.calculateProgress(position, duration)
        assertEquals(0.25f, progress, 0.001f)
    }

    @Test
    fun `calculateProgress handles zero and negative duration without division by zero`() {
        assertEquals(0f, PlayerProgressState.calculateProgress(1000L, 0L), 0.001f)
        assertEquals(0f, PlayerProgressState.calculateProgress(1000L, -1L), 0.001f)
        assertEquals(0f, PlayerProgressState.calculateProgress(0L, 0L), 0.001f)
    }

    @Test
    fun `calculateProgress clamps bounds when position out of range`() {
        val duration = 60_000L
        assertEquals(1.0f, PlayerProgressState.calculateProgress(100_000L, duration), 0.001f)
        assertEquals(0.0f, PlayerProgressState.calculateProgress(-5_000L, duration), 0.001f)
    }

    @Test
    fun `calculatePositionFromFraction maps correct milliseconds`() {
        val duration = 1_440_000L // 24 minutes
        assertEquals(0L, PlayerProgressState.calculatePositionFromFraction(0f, duration))
        assertEquals(720_000L, PlayerProgressState.calculatePositionFromFraction(0.5f, duration))
        assertEquals(duration, PlayerProgressState.calculatePositionFromFraction(1f, duration))
    }

    @Test
    fun `calculatePositionFromFraction forward and backward seek direction`() {
        val duration = 1_000_000L
        val pos1 = PlayerProgressState.calculatePositionFromFraction(0.3f, duration)
        val pos2 = PlayerProgressState.calculatePositionFromFraction(0.6f, duration)
        assertTrue("Dragging right increases timestamp (Forward seek)", pos2 > pos1)

        val pos3 = PlayerProgressState.calculatePositionFromFraction(0.2f, duration)
        assertTrue("Dragging left decreases timestamp (Backward seek)", pos3 < pos1)
    }

    @Test
    fun `calculateThumbOffset stays strictly LTR across track width`() {
        val width = 1000f
        assertEquals(0f, PlayerProgressState.calculateThumbOffset(0f, width), 0.001f)
        assertEquals(500f, PlayerProgressState.calculateThumbOffset(0.5f, width), 0.001f)
        assertEquals(1000f, PlayerProgressState.calculateThumbOffset(1f, width), 0.001f)
    }

    @Test
    fun `formatPlayerTime formats standard and hour-long media correctly`() {
        assertEquals("00:00", PlayerProgressState.formatPlayerTime(0L))
        assertEquals("00:00", PlayerProgressState.formatPlayerTime(-10L))
        assertEquals("00:45", PlayerProgressState.formatPlayerTime(45_000L))
        assertEquals("12:34", PlayerProgressState.formatPlayerTime(754_000L))
        assertEquals("23:50", PlayerProgressState.formatPlayerTime(1_430_000L))
        assertEquals("1:15:30", PlayerProgressState.formatPlayerTime(4_530_000L))
    }
}
