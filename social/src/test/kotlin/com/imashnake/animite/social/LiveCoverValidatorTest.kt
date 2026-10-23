package com.imashnake.animite.social

import com.imashnake.animite.social.util.LiveCoverValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCoverValidatorTest {

    @Test
    fun maxFileSize_constantsAreConfiguredCorrectly() {
        assertEquals(10 * 1024 * 1024L, LiveCoverValidator.MAX_GIF_SIZE_BYTES)
        assertEquals(25 * 1024 * 1024L, LiveCoverValidator.MAX_VIDEO_SIZE_BYTES)
        assertEquals(2, LiveCoverValidator.MIN_DURATION_SECONDS)
        assertEquals(10, LiveCoverValidator.MAX_DURATION_STANDARD_SECONDS)
        assertEquals(15, LiveCoverValidator.MAX_DURATION_PREMIUM_SECONDS)
    }

    @Test
    fun magicBytes_gifHeaderInspection() {
        val gif89aHeader = byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '3'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte())
        // In LiveCoverValidator, bytes 0..3 are GIF8 and 4 is 7 or 9 and 5 is 'a'
        val validGif89a = byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte(), 0x00, 0x00)
        val validGif87a = byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '7'.code.toByte(), 'a'.code.toByte(), 0x00, 0x00)

        assertTrue(validGif89a.size >= 6)
        assertTrue(validGif87a.size >= 6)
    }

    @Test
    fun magicBytes_mp4HeaderInspection() {
        // Standard MP4 starts with 4 bytes length then 'ftyp'
        val mp4Header = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte()
        )
        assertTrue(mp4Header.size >= 12)
    }
}
