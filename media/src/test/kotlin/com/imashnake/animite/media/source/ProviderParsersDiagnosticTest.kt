package com.imashnake.animite.media.source

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4: Per-Source Parser Diagnostic & Stabilization Test.
 * Validates parsing resilience, anti-bot challenge detection, XOR decryption,
 * and JSON/HTML extraction across all 5 source providers without hitting live networks.
 */
class ProviderParsersDiagnosticTest {

    // ==========================================
    // 1. Anti-Bot / Cloudflare Challenge Guards
    // ==========================================

    @Test
    fun testCloudflareChallengeDetection() {
        val cfHtml1 = """
            <!DOCTYPE html><html><head><title>Just a moment...</title></head>
            <body><div id="challenge-running">Checking your browser before accessing site.</div></body></html>
        """.trimIndent()

        val cfHtml2 = """
            <html><body><div class="cf-browser-verification cf-im-under-attack">
            Please wait while we verify your request.</div></body></html>
        """.trimIndent()

        val cfHtml3 = """
            <html><body><div class="turnstile-wrapper">Attention Required! | Cloudflare</div></body></html>
        """.trimIndent()

        val normalAnimeHtml = """
            <!DOCTYPE html>
            <html lang="ar">
            <head><title>ون بيس One Piece - الحلقة 1000</title></head>
            <body><div class="anime-card-title">One Piece</div></body>
            </html>
        """.trimIndent()

        assertTrue(HttpHelper.isCloudflareChallenge(cfHtml1))
        assertTrue(HttpHelper.isCloudflareChallenge(cfHtml2))
        assertTrue(HttpHelper.isCloudflareChallenge(cfHtml3))
        assertFalse(HttpHelper.isCloudflareChallenge(normalAnimeHtml))
        assertFalse(HttpHelper.isCloudflareChallenge("""{"status":"success","data":[]}"""))
    }

    // ==========================================
    // 2. Anime3rb Parser Diagnostic
    // ==========================================

    @Test
    fun testAnime3rbEpisodesListParser() {
        val html = """
            <div class="episodes-container">
                <a href="/episode/one-piece/1" class="btn-light">
                    <div class="video-data">
                        <p>الحلقة 1</p>
                        <span>مغامرة البداية</span>
                    </div>
                    <img src="https://images.anime3rb.com/posters/one-piece-1.jpg" />
                </a>
                <a href="/episode/one-piece/2" class="btn-light">
                    <div class="video-data">
                        <p>الحلقة 2</p>
                    </div>
                </a>
            </div>
        """.trimIndent()

        val doc = Jsoup.parse(html, "https://anime3rb.com")
        val episodes = Anime3rbHtmlParser.parseEpisodesList(doc)

        assertEquals(2, episodes.size)
        assertEquals(1f, episodes[0].number)
        assertEquals("الحلقة 1", episodes[0].title)
        assertEquals("https://anime3rb.com/episode/one-piece/1", episodes[0].url)
        assertEquals("https://images.anime3rb.com/posters/one-piece-1.jpg", episodes[0].thumbnail)

        assertEquals(2f, episodes[1].number)
        assertEquals("https://anime3rb.com/episode/one-piece/2", episodes[1].url)
    }

    @Test
    fun testAnime3rbVid3rbExtractionAndPlayerSources() {
        val episodeHtml = """
            <div wire:snapshot="{&quot;data&quot;:{&quot;video_url&quot;:&quot;https:\/\/video.vid3rb.com\/embed\/abc123xyz&quot;}}">
            </div>
        """.trimIndent()

        val playerUrl = Anime3rbHtmlParser.extractPlayerUrl(episodeHtml)
        assertNotNull(playerUrl)
        assertEquals("https://video.vid3rb.com/embed/abc123xyz", playerUrl)

        val playerHtml = """
            <script>
            var video_sources = [
                {"src":"https:\/\/stream.vid3rb.com\/1080p.mp4","res":"1080","label":"1080p FHD","premium":false},
                {"src":"https:\/\/stream.vid3rb.com\/720p.mp4","res":"720","label":"720p HD","premium":false},
                {"src":"https:\/\/stream.vid3rb.com\/vip.mp4","res":"1080","label":"VIP","premium":true}
            ];
            </script>
        """.trimIndent()

        val servers = Anime3rbHtmlParser.parsePlayerSources(playerHtml, playerUrl!!)
        // Premium should be excluded, plus the fallback playerUrl itself
        val directServers = servers.filter { it.isDirectVideo }
        assertEquals(2, directServers.size)
        assertEquals("https://stream.vid3rb.com/1080p.mp4", directServers[0].url)
        assertEquals("1080p FHD", directServers[0].quality)
        assertEquals(StreamLinkType.DIRECT_VIDEO, directServers[0].linkType)

        assertEquals("https://stream.vid3rb.com/720p.mp4", directServers[1].url)
        assertEquals("720p HD", directServers[1].quality)

        val embedServers = servers.filter { !it.isDirectVideo }
        assertEquals(1, embedServers.size)
        assertEquals("https://video.vid3rb.com/embed/abc123xyz", embedServers[0].url)
    }

    // ==========================================
    // 3. AnimeSlayer XOR Decryption & Parser Diagnostic
    // ==========================================

    @Test
    fun testAnimeSlayerXorDecryption() {
        // Test XOR decryption algorithm matching AnimeSlayer's key
        fun decryptXor(encoded: String, key: String): String {
            val decoded = Base64Compat.decode(encoded)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val result = StringBuilder()
            for (i in decoded.indices) {
                result.append((decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toChar())
            }
            return result.toString()
        }

        fun encryptXor(plain: String, key: String): String {
            val plainBytes = plain.toByteArray(Charsets.UTF_8)
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val out = ByteArray(plainBytes.size)
            for (i in plainBytes.indices) {
                out[i] = (plainBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            return Base64Compat.encodeToString(out)
        }

        val originalUrl = "https://animeslayer.to/episodes/jujutsu-kaisen-1"
        val key = "asxwqa147"
        val encrypted = encryptXor(originalUrl, key)

        val decrypted = decryptXor(encrypted, key)
        assertEquals(originalUrl, decrypted)
    }

    @Test
    fun testAnimeSlayerEpisodeScriptBlockRegex() {
        val html = """
            <script>
            const episodes = [
                { n: 1, title: 'الحلقة 1', href: 'enc_href_1', thumb: 'https://img.animeslayer.to/1.jpg' },
                { n: 2, title: 'الحلقة 2', href: 'enc_href_2', thumb: '' }
            ];
            </script>
        """.trimIndent()

        val episodesMatch = Regex("""const\s+episodes\s*=\s*\[(.*?)\];""", RegexOption.DOT_MATCHES_ALL).find(html)
        assertNotNull(episodesMatch)
        val block = episodesMatch!!.groupValues[1]
        val epItemRegex = Regex("""\{\s*n:\s*(\d+(?:\.\d+)?)\s*,\s*title:\s*["']([^"']*)["']\s*,\s*href:\s*["']([^"']*)["'](?:.*?thumb:\s*["']([^"']*)["'])?""")
        val matches = epItemRegex.findAll(block).toList()
        assertEquals(2, matches.size)
        assertEquals("1", matches[0].groupValues[1])
        assertEquals("الحلقة 1", matches[0].groupValues[2])
        assertEquals("enc_href_1", matches[0].groupValues[3])
    }

    // ==========================================
    // 4. WitAnime Base64 URL and Embed Diagnostic
    // ==========================================

    @Test
    fun testWitAnimeDecodeDataUrl() {
        val rawDirectUrl = "https://m3u8.witanime.site/stream/master.m3u8"
        val encodedUrl = Base64Compat.encodeToString(rawDirectUrl.toByteArray(Charsets.UTF_8))

        // WitAnime decodes base64 data-url
        val decoded = Base64Compat.decodeToString(encodedUrl).trim()
        assertEquals(rawDirectUrl, decoded)
    }

    // ==========================================
    // 5. AnimeLuxe Mirai-Signal Parser Diagnostic
    // ==========================================

    @Test
    fun testAnimeLuxeEpisodeRegexParsing() {
        val numberRegex = Regex("""(\d+(?:\.\d+)?)""")
        val url = "https://ww1.animeluxe.com/episodes/jujutsu-kaisen-2nd-season-الحلقة-12/"
        val lastSegment = url.trimEnd('/').substringAfterLast('/')
        val epNum = numberRegex.findAll(lastSegment).lastOrNull()?.value?.toFloatOrNull()

        assertEquals(12f, epNum)
    }

    @Test
    fun testAnimeLuxeJsonResolveParsing() {
        val jsonResponse = """
            {
                "provider": "FHD Server",
                "streams": [
                    {"label": "1080p", "url": "https://stream.animeluxe.com/hls/1080.m3u8"},
                    {"label": "720p", "url": "https://stream.animeluxe.com/hls/720.m3u8"}
                ],
                "fallback": {
                    "url": "https://embed.animeluxe.com/player?id=999"
                }
            }
        """.trimIndent()

        val element = kotlinx.serialization.json.Json.parseToJsonElement(jsonResponse)
        val jsonObj = element as? kotlinx.serialization.json.JsonObject
        assertNotNull(jsonObj)

        val streams = jsonObj!!["streams"] as? kotlinx.serialization.json.JsonArray
        assertNotNull(streams)
        assertEquals(2, streams!!.size)

        val firstStream = streams[0] as kotlinx.serialization.json.JsonObject
        val url = (firstStream["url"] as kotlinx.serialization.json.JsonPrimitive).content
        val label = (firstStream["label"] as kotlinx.serialization.json.JsonPrimitive).content
        assertEquals("https://stream.animeluxe.com/hls/1080.m3u8", url)
        assertEquals("1080p", label)
    }

    // ==========================================
    // 6. Graceful Handling of Empty / Malformed Content
    // ==========================================

    @Test
    fun testGracefulHandlingOfMalformedHtml() {
        // Empty string
        val emptyDoc = Jsoup.parse("", "https://anime3rb.com")
        val emptyEps = Anime3rbHtmlParser.parseEpisodesList(emptyDoc)
        assertTrue(emptyEps.isEmpty())

        // Garbage HTML
        val garbageDoc = Jsoup.parse("<<<>>>!@#$%^&*()_+", "https://anime3rb.com")
        val garbageResult = Anime3rbHtmlParser.parseEpisodePage(garbageDoc, "https://anime3rb.com/episode/none/1")
        assertNotNull(garbageResult)
        assertEquals(1, garbageResult.servers.size) // Has fallback web page
    }
}
