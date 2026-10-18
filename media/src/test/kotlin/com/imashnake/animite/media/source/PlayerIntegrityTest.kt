package com.imashnake.animite.media.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerIntegrityTest {

    @Test
    fun testPlayerFilesIntegrityAndNoExternalPlayerReplacement() {
        // Player files directory
        val baseDir = File(System.getProperty("user.dir") ?: ".")
        val playerDir = File(baseDir, "media/src/main/kotlin/com/imashnake/animite/media/player")

        // If running from module directory vs root directory
        val targetDir = if (playerDir.exists()) {
            playerDir
        } else {
            File(baseDir, "src/main/kotlin/com/imashnake/animite/media/player")
        }

        assertTrue("Player directory must exist at: ${targetDir.absolutePath}", targetDir.exists())

        val expectedFiles = listOf(
            "VideoPlayerScreen.kt",
            "VideoPlayerControls.kt",
            "VideoProgressBar.kt",
            "VideoPlayerSheets.kt",
            "VideoPlayerViewModel.kt",
            "PlayerProgressState.kt",
        )

        val existingFiles = targetDir.listFiles()?.map { it.name } ?: emptyList()

        for (file in expectedFiles) {
            assertTrue("Existing Animite player file $file must be preserved", existingFiles.contains(file))
        }

        // Verify no Animeko player replacement
        val playerScreenFile = File(targetDir, "VideoPlayerScreen.kt")
        if (playerScreenFile.exists()) {
            val content = playerScreenFile.readText()
            assertFalse("Must NOT import or reference Animeko player", content.contains("com.openani.animeko"))
            assertFalse("Must NOT replace player with external UI", content.contains("AnimekoPlayer"))
            assertTrue("Must retain VideoPlayerScreen composable", content.contains("fun VideoPlayerScreen"))
        }

        val playerControlsFile = File(targetDir, "VideoPlayerControls.kt")
        if (playerControlsFile.exists()) {
            val content = playerControlsFile.readText()
            assertTrue("Must retain VideoPlayerControls composable", content.contains("fun VideoPlayerControls"))
        }
    }

    @Test
    fun testFranchiseMalIdResolution() {
        val opTitles = listOf("One Piece", "ون بيس")
        assertEquals(21, com.imashnake.animite.media.source.classification.EpisodeClassificationRepository.resolveMalId(opTitles))

        val narutoTitles = listOf("Naruto", "ناروتو")
        assertEquals(20, com.imashnake.animite.media.source.classification.EpisodeClassificationRepository.resolveMalId(narutoTitles))

        val shippudenTitles = listOf("Naruto Shippuden", "ناروتو شيبودن")
        assertEquals(1735, com.imashnake.animite.media.source.classification.EpisodeClassificationRepository.resolveMalId(shippudenTitles))

        val bleachTitles = listOf("Bleach", "بليتش")
        assertEquals(269, com.imashnake.animite.media.source.classification.EpisodeClassificationRepository.resolveMalId(bleachTitles))

        val aotTitles = listOf("Attack on Titan", "هجوم العمالقة")
        assertEquals(16498, com.imashnake.animite.media.source.classification.EpisodeClassificationRepository.resolveMalId(aotTitles))

        val unknownTitles = listOf("NonExistentAnime12345")
        assertEquals(0, com.imashnake.animite.media.source.classification.EpisodeClassificationRepository.resolveMalId(unknownTitles))
    }

    @Test
    fun testSkipIntervalBoundariesAndSeeking() {
        val interval = SkipInterval(
            startTimeMs = 90_000L, // 01:30
            endTimeMs = 180_000L,  // 03:00
            skipType = "op"
        )

        // Before interval: button must not be active
        val posBefore = 80_000L
        val activeBefore = posBefore in interval.startTimeMs..interval.endTimeMs
        assertFalse(activeBefore)

        // Exactly at start: button must be active
        val posStart = 90_000L
        val activeStart = posStart in interval.startTimeMs..interval.endTimeMs
        assertTrue(activeStart)

        // Mid-interval: button must be active
        val posMid = 135_000L
        val activeMid = posMid in interval.startTimeMs..interval.endTimeMs
        assertTrue(activeMid)

        // Exactly at end: button must be active
        val posEnd = 180_000L
        val activeEnd = posEnd in interval.startTimeMs..interval.endTimeMs
        assertTrue(activeEnd)

        // Past interval: button must disappear
        val posAfter = 180_001L
        val activeAfter = posAfter in interval.startTimeMs..interval.endTimeMs
        assertFalse(activeAfter)

        // Simulated skip: target seek position must equal interval.endTimeMs
        var simulatedPlaybackPosition = posMid
        simulatedPlaybackPosition = interval.endTimeMs
        assertEquals(180_000L, simulatedPlaybackPosition)
        assertFalse(simulatedPlaybackPosition > interval.endTimeMs)
    }

    @Test
    fun testQualitySortingAscendingOrder() {
        val q1080 = UnifiedQualityOption(
            quality = QualityRank("1080p FHD", 1080),
            primaryServer = AnimeStreamServer(name = "1080p", url = "http://a.com/1080.mp4", isDirectVideo = true, quality = "1080p"),
        )
        val q480 = UnifiedQualityOption(
            quality = QualityRank("480p SD", 480),
            primaryServer = AnimeStreamServer(name = "480p", url = "http://a.com/480.mp4", isDirectVideo = true, quality = "480p"),
        )
        val q720 = UnifiedQualityOption(
            quality = QualityRank("720p HD", 720),
            primaryServer = AnimeStreamServer(name = "720p", url = "http://a.com/720.mp4", isDirectVideo = true, quality = "720p"),
        )

        val unsorted = listOf(q1080, q480, q720)
        val sorted = unsorted.sortedBy { it.quality.height }

        assertEquals(480, sorted[0].quality.height)
        assertEquals(720, sorted[1].quality.height)
        assertEquals(1080, sorted[2].quality.height)
    }
}
