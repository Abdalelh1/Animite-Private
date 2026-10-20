package com.imashnake.animite.raw

import com.imashnake.animite.raw.subtitle.OnlineSubtitle
import com.imashnake.animite.raw.subtitle.SubtitleFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSubtitlesParsingTest {

    @Test
    fun testOpenSubtitlesJsonResponseParsing() {
        val sampleJson = """
        {
            "total_pages": 1,
            "total_count": 2,
            "per_page": 50,
            "page": 1,
            "data": [
                {
                    "id": "10900888",
                    "type": "subtitle",
                    "attributes": {
                        "subtitle_id": "10900888",
                        "language": "ar",
                        "download_count": 1221,
                        "hearing_impaired": false,
                        "hd": true,
                        "fps": 23.976,
                        "release": "Jujutsu.Kaisen.S03E01.1080p.CR.WEB-DL.AAC2.0.H.264-VARYG",
                        "url": "https://www.opensubtitles.com/ar/subtitles/13483275-jujutsu-kaisen-s03e01",
                        "files": [
                            {
                                "file_id": 11801410,
                                "cd_number": 1,
                                "file_name": "Jujutsu.Kaisen.S03E01.1080p.CR.WEB-DL.AAC2.0.H.264-VARYG.srt"
                            }
                        ]
                    }
                },
                {
                    "id": "8533706",
                    "type": "subtitle",
                    "attributes": {
                        "subtitle_id": "8533706",
                        "language": "en",
                        "download_count": 13761,
                        "hearing_impaired": false,
                        "hd": true,
                        "fps": 24.0,
                        "release": "Jujutsu Kaisen 2nd Season - 01",
                        "url": "https://www.opensubtitles.com/en/subtitles/jujutsu-kaisen-2nd-season-01",
                        "files": [
                            {
                                "file_id": 9456954,
                                "cd_number": 1,
                                "file_name": "[Erai-raws] Jujutsu Kaisen 2nd Season - 01 [1080p].ass"
                            }
                        ]
                    }
                }
            ]
        }
        """.trimIndent()

        val json = JSONObject(sampleJson)
        val dataArray = json.getJSONArray("data")
        assertNotNull(dataArray)
        assertEquals(2, dataArray.length())

        val results = mutableListOf<OnlineSubtitle>()
        for (i in 0 until dataArray.length()) {
            val item = dataArray.getJSONObject(i)
            val id = item.getString("id")
            val attributes = item.getJSONObject("attributes")
            val languageCode = attributes.getString("language")
            val languageName = when (languageCode.lowercase()) {
                "ar" -> "العربية"
                "en" -> "English"
                else -> languageCode.uppercase()
            }
            val release = attributes.optString("release", "")
            val downloadCount = attributes.optInt("download_count", 0)
            val fps = attributes.optDouble("fps", 0.0)
            val pageUrl = attributes.optString("url", null)

            val filesArray = attributes.optJSONArray("files")
            var fileId: Long? = null
            var fileName = "$release.srt"
            if (filesArray != null && filesArray.length() > 0) {
                val f = filesArray.getJSONObject(0)
                fileId = f.optLong("file_id", 0L).takeIf { it > 0 }
                val fn = f.optString("file_name", "")
                if (fn.isNotBlank()) fileName = fn
            }

            results.add(
                OnlineSubtitle(
                    id = id,
                    fileId = fileId,
                    fileName = fileName,
                    languageCode = languageCode,
                    languageName = languageName,
                    release = release,
                    downloadCount = downloadCount,
                    fps = fps,
                    pageUrl = pageUrl,
                    format = SubtitleFormat.fromFileName(fileName)
                )
            )
        }

        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("10900888", first.id)
        assertEquals(11801410L, first.fileId)
        assertEquals("ar", first.languageCode)
        assertEquals("العربية", first.languageName)
        assertEquals(SubtitleFormat.SRT, first.format)
        assertEquals(23.976, first.fps, 0.001)
        assertEquals(1221, first.downloadCount)
        assertTrue(first.fileName.endsWith(".srt"))

        val second = results[1]
        assertEquals("8533706", second.id)
        assertEquals(9456954L, second.fileId)
        assertEquals("en", second.languageCode)
        assertEquals("English", second.languageName)
        assertEquals(SubtitleFormat.ASS, second.format)
        assertEquals(24.0, second.fps, 0.001)
        assertTrue(second.fileName.endsWith(".ass"))
    }
}
