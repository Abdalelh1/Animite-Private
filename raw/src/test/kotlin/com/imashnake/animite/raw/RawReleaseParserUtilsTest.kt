package com.imashnake.animite.raw

import com.imashnake.animite.raw.model.RawQuality
import com.imashnake.animite.raw.source.RawReleaseParserUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RawReleaseParserUtilsTest {

    @Test
    fun testExtractReleaseGroup() {
        assertEquals("SubsPlease", RawReleaseParserUtils.extractReleaseGroup("[SubsPlease] One Piece - 1155 (1080p) [83EB3203].mkv"))
        assertEquals("Erai-raws", RawReleaseParserUtils.extractReleaseGroup("[Erai-raws] DanDaDan - 03 [1080p][Multiple Subtitle].mkv"))
        assertNull(RawReleaseParserUtils.extractReleaseGroup("Bleach Thousand-Year Blood War - 28 (1080p)"))
    }

    @Test
    fun testExtractEpisodeNumber() {
        assertEquals("1155", RawReleaseParserUtils.extractEpisodeNumber("[SubsPlease] One Piece - 1155 (1080p) [83EB3203].mkv"))
        assertEquals("03", RawReleaseParserUtils.extractEpisodeNumber("[SubsPlease] Ao Ashi S2 - 03 (720p) [44F0BDBA].mkv"))
        assertEquals("12", RawReleaseParserUtils.extractEpisodeNumber("One-Punch Man S3 - 12 (1080p)"))
        assertEquals("05.5", RawReleaseParserUtils.extractEpisodeNumber("[SubsPlease] Show - 05.5 (1080p).mkv"))
    }

    @Test
    fun testExtractQuality() {
        assertEquals(RawQuality.Q_1080P, RawReleaseParserUtils.extractQuality("[SubsPlease] One Piece - 1155 (1080p) [83EB3203].mkv"))
        assertEquals(RawQuality.Q_720P, RawReleaseParserUtils.extractQuality("[SubsPlease] Ao Ashi S2 - 03 (720p) [44F0BDBA].mkv"))
        assertEquals(RawQuality.Q_480P, RawReleaseParserUtils.extractQuality("[SubsPlease] Show - 01 (480p).mkv"))
        assertEquals(RawQuality.UNKNOWN, RawReleaseParserUtils.extractQuality("Unknown Release Name.mkv"))
    }

    @Test
    fun testExtractCleanShowTitle() {
        assertEquals("One Piece", RawReleaseParserUtils.extractCleanShowTitle("[SubsPlease] One Piece - 1155 (1080p) [83EB3203].mkv"))
        assertEquals("Ao Ashi S2", RawReleaseParserUtils.extractCleanShowTitle("[SubsPlease] Ao Ashi S2 - 03 (720p) [44F0BDBA].mkv"))
        assertEquals("One-Punch Man S3", RawReleaseParserUtils.extractCleanShowTitle("One-Punch Man S3 - 12 (1080p)"))
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("1.40 GB", RawReleaseParserUtils.formatFileSize(1503238553L))
        assertEquals("720.5 MB", RawReleaseParserUtils.formatFileSize(755499008L))
        assertEquals("Unknown", RawReleaseParserUtils.formatFileSize(null))
        assertEquals("Unknown", RawReleaseParserUtils.formatFileSize(-1L))
    }
}
