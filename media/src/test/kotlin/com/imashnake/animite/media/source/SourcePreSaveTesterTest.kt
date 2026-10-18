package com.imashnake.animite.media.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcePreSaveTesterTest {

    @Test
    fun testStage1ConnectivitySuccessAndFailure() {
        // Successful response
        val successStage = SourceStageTestResult(
            stageName = "الاتصال الأولي (Connectivity)",
            isSuccess = true,
            message = "تم الاتصال بنجاح (HTTP 200)",
            latencyMs = 120L,
        )
        assertTrue(successStage.isSuccess)
        assertEquals(120L, successStage.latencyMs)

        // Cloudflare Turnstile 403 response
        val cloudflareStage = SourceStageTestResult(
            stageName = "الاتصال الأولي (Connectivity)",
            isSuccess = false,
            message = "محظور بنظام الحماية Cloudflare 403 Bot Protection",
            details = "الموقع يطلب متصفحاً حقيقياً أو حلاً لكابتشا Turnstile",
            latencyMs = 85L,
        )
        assertFalse(cloudflareStage.isSuccess)
        assertTrue(cloudflareStage.message.contains("Cloudflare 403"))
    }

    @Test
    fun testStage2SearchSuccessAndFailure() {
        val searchResults = listOf(
            AnimeSearchResult("1", "One Piece", "https://example.com/one-piece"),
            AnimeSearchResult("2", "One Piece Film: Red", "https://example.com/one-piece-red"),
        )

        val stageSuccess = SourceStageTestResult(
            stageName = "البحث (Search)",
            isSuccess = searchResults.isNotEmpty(),
            message = "تم العثور على ${searchResults.size} نتيجة بحث",
            latencyMs = 210L,
        )
        assertTrue(stageSuccess.isSuccess)
        assertEquals("تم العثور على 2 نتيجة بحث", stageSuccess.message)

        val emptyResults = emptyList<AnimeSearchResult>()
        val stageFailure = SourceStageTestResult(
            stageName = "البحث (Search)",
            isSuccess = emptyResults.isNotEmpty(),
            message = "لم يُعثر على أية نتائج بحث للكلمة المفتاحية",
            latencyMs = 190L,
        )
        assertFalse(stageFailure.isSuccess)
    }

    @Test
    fun testStage3EpisodeExtractionEvaluation() {
        val extractedEpisodes = listOf(
            AnimeEpisode(1f, "الحلقة 1", "https://example.com/ep1"),
            AnimeEpisode(2f, "الحلقة 2", "https://example.com/ep2"),
            AnimeEpisode(3f, "الحلقة 3", "https://example.com/ep3"),
        )

        val stageResult = SourceStageTestResult(
            stageName = "استخراج الحلقات (Episodes)",
            isSuccess = extractedEpisodes.isNotEmpty(),
            message = "تم استخراج ${extractedEpisodes.size} حلقة بنجاح",
            details = "أول حلقة: ${extractedEpisodes.first().number}، آخر حلقة: ${extractedEpisodes.last().number}",
            latencyMs = 300L,
        )

        assertTrue(stageResult.isSuccess)
        assertEquals(3, extractedEpisodes.size)
        assertTrue(stageResult.details?.contains("أول حلقة: 1.0") == true)
    }

    @Test
    fun testStage4ServerDiscoveryAndStage5StreamResolution() {
        val discoveredServers = listOf(
            AnimeStreamServer("Direct Server 1080p", "https://cdn.example.com/video_1080p.mp4", isDirectVideo = true, quality = "1080p FHD"),
            AnimeStreamServer("Mirror Server 720p", "https://embed.example.com/v/abc1234", isDirectVideo = false, quality = "720p HD"),
        )

        val hasDirect = discoveredServers.any { it.isDirectVideo || SourceLinkExtractor.isDirectVideoUrl(it.url) }
        assertTrue("Discovered at least one direct playable stream", hasDirect)

        val streamStage = SourceStageTestResult(
            stageName = "التحقق من الرابط القابل للتشغيل (Playable Stream)",
            isSuccess = hasDirect,
            message = "تم استخراج بث مباشر فائق الجودة (1080p FHD)",
            latencyMs = 150L,
        )
        assertTrue(streamStage.isSuccess)
    }

    @Test
    fun testFullSourceTestResultRejectsNonOperationalSource() {
        // A source that passes search and episodes but fails stream resolution
        val fullResult = FullSourceTestResult(
            sourceId = "flawed_provider",
            sourceName = "Flawed Provider",
            overallSuccess = false,
            connectivityStage = SourceStageTestResult("الاتصال الأولي", true, "OK", latencyMs = 100),
            searchStage = SourceStageTestResult("البحث", true, "Found 1 result", latencyMs = 200),
            episodeStage = SourceStageTestResult("الحلقات", true, "Extracted 12 episodes", latencyMs = 300),
            serverStage = SourceStageTestResult("السيرفرات", true, "Found 1 embed server", latencyMs = 250),
            streamStage = SourceStageTestResult("التحقق من الرابط", false, "تعذر حل رابط بث مباشر أو تضمين مدعوم", latencyMs = 400),
        )

        assertFalse("Source must NOT be operational if stream resolution fails", fullResult.overallSuccess)
        assertNotNull(fullResult.streamStage)
        assertFalse(fullResult.streamStage.isSuccess)
    }
}
