package com.imashnake.animite.media.player

import com.imashnake.animite.media.source.SkipInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipIntroTest {

    private val sampleOpening = SkipInterval(
        startTimeMs = 30_000L,  // 00:30
        endTimeMs = 120_000L,   // 02:00
        skipType = "op"
    )

    private val sampleEnding = SkipInterval(
        startTimeMs = 1_350_000L, // 22:30
        endTimeMs = 1_440_000L,   // 24:00
        skipType = "ed"
    )

    private fun findActiveInterval(intervals: List<SkipInterval>, positionMs: Long, isEnabled: Boolean): SkipInterval? {
        if (!isEnabled) return null
        return intervals.firstOrNull { positionMs in it.startTimeMs..it.endTimeMs }
    }

    @Test
    fun `intro is not active before start time`() {
        val active = findActiveInterval(listOf(sampleOpening), positionMs = 25_000L, isEnabled = true)
        assertNull(active)
    }

    @Test
    fun `intro is active exactly within boundaries`() {
        val atStart = findActiveInterval(listOf(sampleOpening), positionMs = 30_000L, isEnabled = true)
        assertNotNull(atStart)
        assertEquals("op", atStart?.skipType)

        val inMiddle = findActiveInterval(listOf(sampleOpening), positionMs = 75_000L, isEnabled = true)
        assertNotNull(inMiddle)

        val atEnd = findActiveInterval(listOf(sampleOpening), positionMs = 120_000L, isEnabled = true)
        assertNotNull(atEnd)
    }

    @Test
    fun `intro is not active after end time`() {
        val active = findActiveInterval(listOf(sampleOpening), positionMs = 120_500L, isEnabled = true)
        assertNull(active)
    }

    @Test
    fun `seeking forward to end time skips entire intro interval`() {
        val currentPosition = 45_000L
        val active = findActiveInterval(listOf(sampleOpening), positionMs = currentPosition, isEnabled = true)
        assertNotNull(active)

        val targetSeek = active!!.endTimeMs
        assertEquals(120_000L, targetSeek)
        assertTrue("Seek target is past current position", targetSeek > currentPosition)
    }

    @Test
    fun `outro ending is detected correctly near end of episode`() {
        val intervals = listOf(sampleOpening, sampleEnding)

        val activeEnding = findActiveInterval(intervals, positionMs = 1_380_000L, isEnabled = true)
        assertNotNull(activeEnding)
        assertEquals("ed", activeEnding?.skipType)
    }

    @Test
    fun `disabled skip intro setting suppresses active interval`() {
        val inMiddle = findActiveInterval(listOf(sampleOpening), positionMs = 75_000L, isEnabled = false)
        assertNull("Should be null when user disabled skip intro", inMiddle)
    }
}
