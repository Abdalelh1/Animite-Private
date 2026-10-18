package com.imashnake.animite.media.source

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 6: Direct-Stream Extraction & Token Renewal Engine Test Suite.
 * Validates strict Direct vs Embed classification, hosting extractor decoders,
 * token expiry detection, and silent renewal mechanisms.
 */
class DirectStreamExtractionAndTokenRenewalTest {

    // ==========================================
    // 1. Direct vs. Web Embed Strict Classification
    // ==========================================

    @Test
    fun testDirectVsWebEmbedClassification() {
        val directMp4 = AnimeStreamServer(name = "Direct MP4", url = "https://cdn.example.com/stream/video.mp4", isDirectVideo = true)
        val directHls = AnimeStreamServer(name = "Direct HLS", url = "https://cdn.example.com/hls/master.m3u8", isDirectVideo = true)
        val directDash = AnimeStreamServer(name = "Direct DASH", url = "https://cdn.example.com/dash/manifest.mpd", isDirectVideo = true)

        val embedPage = AnimeStreamServer(name = "Embed Page", url = "https://embed.example.com/player/v123.html", isDirectVideo = false)
        val adTracker = AnimeStreamServer(name = "Ad Link", url = "https://acceptable.a-ads.com/track?id=99", isDirectVideo = false)
        val fileHost = AnimeStreamServer(name = "File Host", url = "https://gofile.io/d/xyz", isDirectVideo = false)

        assertTrue("Direct MP4 must be direct stream", SourceLinkExtractor.isPlayableDirectStream(directMp4))
        assertTrue("Direct HLS must be direct stream", SourceLinkExtractor.isPlayableDirectStream(directHls))
        assertTrue("Direct DASH must be direct stream", SourceLinkExtractor.isPlayableDirectStream(directDash))

        assertFalse("Embed HTML must not be direct stream", SourceLinkExtractor.isPlayableDirectStream(embedPage))
        assertFalse("Ad tracker must not be direct stream", SourceLinkExtractor.isPlayableDirectStream(adTracker))
        assertFalse("File host must not be direct stream", SourceLinkExtractor.isPlayableDirectStream(fileHost))

        assertEquals(MediaContainerType.MP4, SourceLinkExtractor.detectMediaType(directMp4.url))
        assertEquals(MediaContainerType.HLS, SourceLinkExtractor.detectMediaType(directHls.url))
        assertEquals(MediaContainerType.DASH, SourceLinkExtractor.detectMediaType(directDash.url))
        assertEquals(MediaContainerType.EMBED, SourceLinkExtractor.detectMediaType(embedPage.url))
    }

    // ==========================================
    // 2. Vid3rb Direct Stream Extraction
    // ==========================================

    @Test
    fun testVid3rbDirectStreamExtraction() = runBlocking {
        val playerHtml = """
            <script>
            var video_sources = [
                {"src":"https:\/\/video.vid3rb.com\/video\/1080p.mp4","res":"1080","label":"1080p FHD","premium":false},
                {"src":"https:\/\/video.vid3rb.com\/video\/720p.mp4","res":"720","label":"720p HD","premium":false}
            ];
            </script>
        """.trimIndent()

        val embedUrl = "https://video.vid3rb.com/embed/sample123"
        assertTrue(DirectStreamExtractor.canExtract(embedUrl))

        val directServers = DirectStreamExtractor.extractDirectStreams(
            embedUrl = embedUrl,
            htmlContent = playerHtml,
        )

        assertEquals(2, directServers.size)
        assertEquals("https://video.vid3rb.com/video/1080p.mp4", directServers[0].url)
        assertEquals("1080p FHD", directServers[0].quality)
        assertTrue(directServers[0].isDirectVideo)
        assertEquals("https://video.vid3rb.com/", directServers[0].headers["Referer"])
    }

    // ==========================================
    // 3. Share4max / Megamax Direct Extraction
    // ==========================================

    @Test
    fun testShare4maxDirectStreamExtraction() {
        val jsonProps = """
            {
                "props": {
                    "streams": {
                        "data": [
                            {
                                "label": "1080p FHD",
                                "mirrors": [
                                    {"driver": "streamtape", "link": "https://streamtape.com/get_video?id=123.mp4"},
                                    {"driver": "mp4upload", "link": "https://www.mp4upload.com/embed-abc.html"}
                                ]
                            }
                        ]
                    }
                }
            }
        """.trimIndent()

        val embedUrl = "https://share4max.com/embed/test"
        assertTrue(DirectStreamExtractor.canExtract(embedUrl))

        val extracted = DirectStreamExtractor.extractShare4max(
            embedUrl = embedUrl,
            htmlContent = jsonProps,
        )

        // Only direct mp4 link should be extracted, embed html is excluded
        assertEquals(1, extracted.size)
        assertEquals("https://streamtape.com/get_video?id=123.mp4", extracted[0].url)
        assertEquals("1080p FHD", extracted[0].quality)
        assertTrue(extracted[0].isDirectVideo)
    }

    // ==========================================
    // 4. Mp4Upload Direct Video Extraction
    // ==========================================

    @Test
    fun testMp4UploadDirectStreamExtraction() {
        val playerHtml = """
            <script type='text/javascript'>
                player.src({
                    type: "video/mp4",
                    src: "https://www4.mp4upload.com:282/d/samplevideo/video.mp4"
                });
            </script>
        """.trimIndent()

        val embedUrl = "https://www.mp4upload.com/embed-test123.html"
        assertTrue(DirectStreamExtractor.canExtract(embedUrl))

        val directUrl = DirectStreamExtractor.extractMp4Upload(embedUrl, playerHtml)
        assertNotNull(directUrl)
        assertEquals("https://www4.mp4upload.com:282/d/samplevideo/video.mp4", directUrl)
    }

    // ==========================================
    // 5. VidBom & Packed JavaScript Extraction
    // ==========================================

    @Test
    fun testVidBomPackedJsExtraction() {
        // Sample unpacked script simulating VidBom jwplayer setup
        val unpackedScript = """
            jwplayer("vplayer").setup({
                sources: [
                    {file: "https://vidbom.com/cdn/hls/master.m3u8", label: "Auto"},
                    {file: "https://vidbom.com/cdn/video-720.mp4", label: "720p"}
                ]
            });
        """.trimIndent()

        val embedUrl = "https://vidbom.com/embed-xyz"
        assertTrue(DirectStreamExtractor.canExtract(embedUrl))

        val servers = DirectStreamExtractor.extractVidBom(unpackedScript, embedUrl)
        assertEquals(2, servers.size)
        assertEquals("https://vidbom.com/cdn/hls/master.m3u8", servers[0].url)
        assertTrue(servers[0].isDirectVideo)
        assertEquals("https://vidbom.com/cdn/video-720.mp4", servers[1].url)
        assertTrue(servers[1].isDirectVideo)
    }

    // ==========================================
    // 6. Token Expiration Error Detection
    // ==========================================

    @Test
    fun testTokenExpirationErrorDetection() {
        val http403 = RuntimeException("HTTP response code: 403 Forbidden")
        val http401 = RuntimeException("HTTP 401 Unauthorized")
        val http410 = RuntimeException("Server returned 410 Gone")
        val signedTokenExpired = RuntimeException("CDN URL signature token expired at 1700000000")
        val normalNetworkError = RuntimeException("Connection timed out after 10000ms")

        assertTrue(SmartStreamEngine.isTokenExpiredError(http403))
        assertTrue(SmartStreamEngine.isTokenExpiredError(http401))
        assertTrue(SmartStreamEngine.isTokenExpiredError(http410))
        assertTrue(SmartStreamEngine.isTokenExpiredError(signedTokenExpired))
        assertFalse(SmartStreamEngine.isTokenExpiredError(normalNetworkError))
        assertFalse(SmartStreamEngine.isTokenExpiredError(null))
    }

    // ==========================================
    // 7. Token Renewal Triggers Silent Re-extraction
    // ==========================================

    @Test
    fun testTokenRenewalEvictsCacheAndFetchesFreshStreams() = runBlocking {
        val animeTitle = "Attack on Titan"
        val epNumber = 1f

        val expiredServer = AnimeStreamServer(
            name = "Expired Server",
            url = "https://cdn.example.com/ep1.mp4?token=old_expired_token",
            isDirectVideo = true,
            quality = "1080p FHD",
        )

        val freshServer = AnimeStreamServer(
            name = "Fresh Server",
            url = "https://cdn.example.com/ep1.mp4?token=new_fresh_token_12345",
            isDirectVideo = true,
            quality = "1080p FHD",
        )

        var callCount = 0
        val mockProvider = object : EpisodeSourceProvider {
            override val id = "renewal_prov"
            override val name = "Renewal Source"
            override val language = "ar"
            override suspend fun search(query: String) = emptyList<AnimeSearchResult>()
            override suspend fun getEpisodes(animeUrl: String) = emptyList<AnimeEpisode>()
            override suspend fun getStreamServers(episodeUrl: String): List<AnimeStreamServer> {
                callCount++
                return if (callCount == 1) listOf(expiredServer) else listOf(freshServer)
            }
        }

        EpisodeSourcesManager.registerCustomProvider(mockProvider)

        try {
            val ep = AnimeEpisode(
                number = epNumber,
                title = "الحلقة 1",
                url = "https://renewal.com/ep1",
                sources = listOf(EpisodeSourceReference("renewal_prov", "https://renewal.com/ep1")),
            )

            // Initial resolution caches the result
            val initial = SmartStreamEngine.resolveEpisodeStreams(ep, animeTitle = animeTitle)
            assertTrue(initial.success)
            assertEquals("https://cdn.example.com/ep1.mp4?token=old_expired_token", initial.bestStream?.url)

            // Verify cache hit
            val cached = SmartStreamCache.getValidStreamResult(animeTitle, epNumber)
            assertNotNull(cached)

            // Simulate token expiry (HTTP 403 on playback) -> trigger renewExpiredToken
            val renewed = SmartStreamEngine.renewExpiredToken(
                episode = ep,
                failedServer = expiredServer,
                animeTitle = animeTitle,
            )

            assertTrue(renewed.success)
            assertNotNull(renewed.bestStream)
            assertEquals(
                "Must obtain newly generated fresh token after renewal",
                "https://cdn.example.com/ep1.mp4?token=new_fresh_token_12345",
                renewed.bestStream!!.url,
            )

            // Verify renewed stream is now stored in cache
            val newCached = SmartStreamCache.getValidStreamResult(animeTitle, epNumber)
            assertNotNull(newCached)
            assertEquals(renewed.bestStream!!.url, newCached!!.bestStream!!.url)
        } finally {
            EpisodeSourcesManager.unregisterCustomProvider("renewal_prov")
            SmartStreamCache.invalidateStream(animeTitle, epNumber)
        }
    }
}
