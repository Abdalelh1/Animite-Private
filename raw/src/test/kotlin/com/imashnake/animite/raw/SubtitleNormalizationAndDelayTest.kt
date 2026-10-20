package com.imashnake.animite.raw

import android.content.Context
import com.imashnake.animite.raw.subtitle.SubtitleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class SubtitleNormalizationAndDelayTest {

    private val subtitleManager = SubtitleManager(
        // Context mock or dummy since normalize and shift do not need Android context
        object : android.content.ContextWrapper(null) {},
        com.imashnake.animite.raw.subtitle.OpenSubtitlesService()
    )

    @Test
    fun testSrtTimestampShiftPositive() {
        val originalSrt = """
            1
            00:01:20,000 --> 00:01:23,500
            مرحباً بك في أنيميت

            2
            00:01:25,100 --> 00:01:28,250
            مشاهدة ممتعة
        """.trimIndent()

        // Shift forward by 1500 ms (1.5 seconds)
        val shifted = subtitleManager.shiftSrtTimestamps(originalSrt, 1500L)

        assertTrue(shifted.contains("00:01:21,500 --> 00:01:25,000"))
        assertTrue(shifted.contains("00:01:26,600 --> 00:01:29,750"))
        assertTrue(shifted.contains("مرحباً بك في أنيميت"))
    }

    @Test
    fun testSrtTimestampShiftNegativeClamped() {
        val originalSrt = """
            1
            00:00:00,500 --> 00:00:02,000
            بداية الحلقة
        """.trimIndent()

        // Shift backwards by 1000 ms (-1.0 second) -> start should clamp to 00:00:00,000
        val shifted = subtitleManager.shiftSrtTimestamps(originalSrt, -1000L)

        assertTrue(shifted.contains("00:00:00,000 --> 00:00:01,000"))
    }

    @Test
    fun testAssTimestampShift() {
        val originalAss = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:01:10.50,0:01:14.20,Default,,0,0,0,,أنمي راو رائع
        """.trimIndent()

        // Shift forward by 2500 ms (2.5 seconds)
        val shifted = subtitleManager.shiftAssTimestamps(originalAss, 2500L)

        assertTrue(shifted.contains("Dialogue: 0,0:01:13.00,0:01:16.70,Default,,0,0,0,,أنمي راو رائع"))
    }

    @Test
    fun testWindows1256ArabicDetectionAndNormalization() {
        val arabicText = "هذه ترجمة عربية لملف أنمي راو بجودة عالية جداً مع توقيت مضبوط"
        val win1256Charset = Charset.forName("windows-1256")
        val win1256Bytes = arabicText.toByteArray(win1256Charset)

        val result = subtitleManager.normalizeSubtitleEncoding(win1256Bytes)

        assertEquals(arabicText, result.content)
        assertTrue(result.wasNormalizedToUtf8)
    }

    @Test
    fun testUtf8BomDetection() {
        val arabicText = "ترجمة بصيغة يوني كود"
        val textBytes = arabicText.toByteArray(StandardCharsets.UTF_8)
        val bomBytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + textBytes

        val result = subtitleManager.normalizeSubtitleEncoding(bomBytes)

        assertEquals(arabicText, result.content)
        assertTrue(result.wasNormalizedToUtf8)
    }
}
