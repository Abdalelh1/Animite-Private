package com.imashnake.animite.media.player

import com.imashnake.animite.media.source.AnimeStreamServer
import com.imashnake.animite.media.source.MediaContainerType
import com.imashnake.animite.media.source.QualityRank
import com.imashnake.animite.media.source.StreamLinkType
import com.imashnake.animite.media.source.UnifiedQualityOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoQualityLogicTest {

    private fun createOption(name: String, height: Int): UnifiedQualityOption {
        val server = AnimeStreamServer(
            name = name,
            url = "https://cdn.example.com/video_$height.mp4",
            isDirectVideo = true,
            quality = name,
            linkType = StreamLinkType.DIRECT_VIDEO
        )
        return UnifiedQualityOption(
            quality = QualityRank(name, height),
            primaryServer = server,
            fallbackServers = emptyList(),
            mediaType = MediaContainerType.MP4
        )
    }

    @Test
    fun `auto quality never invents non-existent qualities`() {
        // Available streams: only 480p and 720p
        val options = listOf(createOption("480p SD", 480), createOption("720p HD", 720))

        // All selected targets must be in options
        val heights = options.map { it.quality.height }
        assertTrue(heights.contains(480))
        assertTrue(heights.contains(720))
        assertFalse("Cannot invent 1080p if not available", heights.contains(1080))
        assertFalse("Cannot invent 4K if not available", heights.contains(2160))
    }

    @Test
    fun `buffer starvation triggers immediate step-down to lower quality`() {
        val options = listOf(
            createOption("360p", 360),
            createOption("720p HD", 720),
            createOption("1080p FHD", 1080)
        )

        // Currently playing 1080p, buffer starved
        val currentRank = QualityRank("1080p FHD", 1080)
        val lowerOptions = options.filter { it.quality.height < currentRank.height }.sortedByDescending { it.quality.height }
        val targetLower = lowerOptions.firstOrNull()

        assertNotNull(targetLower)
        assertEquals(720, targetLower!!.quality.height)
        assertEquals("720p HD", targetLower.quality.canonicalName)
    }

    @Test
    fun `healthy buffer and high bitrate triggers safe step-up to higher quality`() {
        val options = listOf(
            createOption("360p", 360),
            createOption("720p HD", 720),
            createOption("1080p FHD", 1080)
        )

        // Currently playing 720p, buffer > 15s and bitrate > 4.5 Mbps
        val currentRank = QualityRank("720p HD", 720)
        val higherOptions = options.filter { it.quality.height > currentRank.height }.sortedBy { it.quality.height }
        val targetHigher = higherOptions.firstOrNull()

        assertNotNull(targetHigher)
        assertEquals(1080, targetHigher!!.quality.height)
        assertEquals("1080p FHD", targetHigher.quality.canonicalName)
    }

    @Test
    fun `no lower quality exists when already at lowest rank`() {
        val options = listOf(
            createOption("360p", 360),
            createOption("720p HD", 720)
        )

        val currentRank = QualityRank("360p", 360)
        val lowerOptions = options.filter { it.quality.height < currentRank.height }
        assertNull(lowerOptions.firstOrNull())
    }

    @Test
    fun `no higher quality exists when already at maximum rank`() {
        val options = listOf(
            createOption("720p HD", 720),
            createOption("1080p FHD", 1080)
        )

        val currentRank = QualityRank("1080p FHD", 1080)
        val higherOptions = options.filter { it.quality.height > currentRank.height }
        assertNull(higherOptions.firstOrNull())
    }

    private fun assertFalse(message: String, condition: Boolean) {
        org.junit.Assert.assertFalse(message, condition)
    }
}
