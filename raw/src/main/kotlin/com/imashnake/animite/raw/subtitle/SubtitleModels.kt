package com.imashnake.animite.raw.subtitle

import kotlinx.serialization.Serializable

@Serializable
enum class SubtitleFormat(val extension: String, val mimeType: String) {
    SRT(".srt", "application/x-subrip"),
    ASS(".ass", "text/x-ssa"),
    SSA(".ssa", "text/x-ssa"),
    UNKNOWN("", "text/plain");

    companion object {
        fun fromFileName(name: String?): SubtitleFormat {
            if (name == null) return UNKNOWN
            val lower = name.lowercase()
            return when {
                lower.endsWith(".srt") -> SRT
                lower.endsWith(".ass") -> ASS
                lower.endsWith(".ssa") -> SSA
                else -> UNKNOWN
            }
        }
    }
}

@Serializable
data class ImportedSubtitle(
    val id: String,
    val name: String,
    val languageCode: String = "ar", // "ar" for Arabic, "en" for English, etc.
    val languageName: String = "العربية",
    val format: SubtitleFormat,
    val localFilePath: String,
    val originalUriString: String? = null,
    val delayMs: Long = 0L,
    val isAutoDetectedArabic: Boolean = false,
    val addedTimestampMs: Long = System.currentTimeMillis(),
)

data class SubtitleEncodingDetectionResult(
    val content: String,
    val encodingName: String,
    val wasNormalizedToUtf8: Boolean,
)

@Serializable
data class OnlineSubtitle(
    val id: String,
    val fileId: Long?,
    val fileName: String,
    val languageCode: String, // "ar", "en", etc.
    val languageName: String,
    val release: String,
    val downloadCount: Int = 0,
    val hearingImpaired: Boolean = false,
    val fps: Double = 0.0,
    val pageUrl: String? = null,
    val format: SubtitleFormat = SubtitleFormat.fromFileName(fileName),
)
