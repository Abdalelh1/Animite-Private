package com.imashnake.animite.raw.subtitle

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.imashnake.animite.raw.subtitle.SubtitleFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openSubtitlesService: OpenSubtitlesService,
) {
    private val subDir: File by lazy {
        File(context.filesDir, "raw_subtitles").apply { if (!exists()) mkdirs() }
    }
    private val registryFile: File by lazy {
        File(subDir, "imported_subtitles_registry.json")
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    suspend fun getImportedSubtitles(): List<ImportedSubtitle> = withContext(Dispatchers.IO) {
        if (!registryFile.exists()) return@withContext emptyList()
        try {
            val content = registryFile.readText(StandardCharsets.UTF_8)
            val list = json.decodeFromString<List<ImportedSubtitle>>(content)
            // Filter out files that might have been removed
            list.filter { File(it.localFilePath).exists() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun importSubtitleFromUri(
        uri: Uri,
        preferredLanguage: String = "ar", // default to Arabic as requested
    ): Result<ImportedSubtitle> = withContext(Dispatchers.IO) {
        try {
            val fileName = queryDisplayName(uri) ?: "imported_sub_${System.currentTimeMillis()}.srt"
            val format = SubtitleFormat.fromFileName(fileName)
            if (format == SubtitleFormat.UNKNOWN) {
                return@withContext Result.failure(
                    IllegalArgumentException("صيغة ملف الترجمة غير مدعومة. الصيغ المدعومة هي: .srt, .ass, .ssa")
                )
            }

            // Read raw bytes from SAF content resolver
            val bytes = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes()
            } ?: return@withContext Result.failure(IllegalStateException("تعذر قراءة ملف الترجمة المحدد"))

            // Detect encoding and normalize to UTF-8
            val encodingResult = normalizeSubtitleEncoding(bytes)

            // Determine if content looks predominantly Arabic
            val isArabic = isArabicText(encodingResult.content)
            val languageCode = if (isArabic) "ar" else preferredLanguage
            val languageName = when (languageCode) {
                "ar" -> "العربية"
                "en" -> "English"
                else -> languageCode.uppercase()
            }

            // Save normalized UTF-8 copy in app-managed storage
            val uniqueId = UUID.randomUUID().toString().take(8)
            val destFile = File(subDir, "sub_${uniqueId}_$fileName")
            destFile.writeText(encodingResult.content, StandardCharsets.UTF_8)

            val imported = ImportedSubtitle(
                id = uniqueId,
                name = fileName,
                languageCode = languageCode,
                languageName = languageName,
                format = format,
                localFilePath = destFile.absolutePath,
                originalUriString = uri.toString(),
                delayMs = 0L,
                isAutoDetectedArabic = isArabic,
            )

            // Persist to registry
            val current = getImportedSubtitles().toMutableList()
            current.add(0, imported)
            saveRegistry(current)

            Result.success(imported)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchOnlineSubtitles(
        query: String,
        episodeNumber: Int? = null,
        seasonNumber: Int? = null,
        languages: String = "ar,en",
    ): Result<List<OnlineSubtitle>> = openSubtitlesService.searchSubtitles(
        query = query,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        languages = languages,
    )

    suspend fun downloadAndImportOnlineSubtitle(
        onlineSub: OnlineSubtitle,
    ): Result<ImportedSubtitle> = withContext(Dispatchers.IO) {
        val fileId = onlineSub.fileId
            ?: return@withContext Result.failure(
                Exception("معرّف الملف غير متوفر في هذه النتيجة. يمكنك فتح صفحة الترجمة لتحميلها يدوياً.")
            )

        val downloadResult = openSubtitlesService.downloadSubtitleContent(fileId)
        if (downloadResult.isFailure) {
            val error = downloadResult.exceptionOrNull()
            return@withContext Result.failure(
                Exception(error?.message ?: "فشل تنزيل ملف الترجمة من OpenSubtitles")
            )
        }

        val rawBytes = downloadResult.getOrThrow()
        val encodingResult = normalizeSubtitleEncoding(rawBytes)
        val isArabic = isArabicText(encodingResult.content)
        val languageCode = if (isArabic) "ar" else onlineSub.languageCode
        val languageName = when (languageCode) {
            "ar" -> "العربية"
            "en" -> "English"
            else -> onlineSub.languageName
        }

        val uniqueId = UUID.randomUUID().toString().take(8)
        val safeName = onlineSub.fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destFile = File(subDir, "sub_${uniqueId}_$safeName")
        destFile.writeText(encodingResult.content, StandardCharsets.UTF_8)

        val imported = ImportedSubtitle(
            id = uniqueId,
            name = onlineSub.fileName,
            languageCode = languageCode,
            languageName = languageName,
            format = onlineSub.format,
            localFilePath = destFile.absolutePath,
            originalUriString = onlineSub.pageUrl,
            delayMs = 0L,
            isAutoDetectedArabic = isArabic,
        )

        val current = getImportedSubtitles().toMutableList()
        current.add(0, imported)
        saveRegistry(current)

        Result.success(imported)
    }

    suspend fun applySubtitleDelay(
        subtitle: ImportedSubtitle,
        delayDeltaMs: Long,
    ): Result<ImportedSubtitle> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(subtitle.localFilePath)
            if (!sourceFile.exists()) {
                return@withContext Result.failure(IllegalStateException("ملف الترجمة غير موجود"))
            }

            val newDelay = subtitle.delayMs + delayDeltaMs
            val content = sourceFile.readText(StandardCharsets.UTF_8)

            val adjustedContent = when (subtitle.format) {
                SubtitleFormat.SRT -> shiftSrtTimestamps(content, delayDeltaMs)
                SubtitleFormat.ASS, SubtitleFormat.SSA -> shiftAssTimestamps(content, delayDeltaMs)
                SubtitleFormat.UNKNOWN -> content
            }

            val shiftedFile = File(subDir, "sub_${subtitle.id}_shifted_${newDelay}ms_${subtitle.name}")
            shiftedFile.writeText(adjustedContent, StandardCharsets.UTF_8)

            val updated = subtitle.copy(
                delayMs = newDelay,
                localFilePath = shiftedFile.absolutePath
            )

            // Update in registry
            val current = getImportedSubtitles().map {
                if (it.id == subtitle.id) updated else it
            }
            saveRegistry(current)

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSubtitle(id: String) = withContext(Dispatchers.IO) {
        val current = getImportedSubtitles().toMutableList()
        val target = current.firstOrNull { it.id == id }
        if (target != null) {
            File(target.localFilePath).delete()
            current.removeAll { it.id == id }
            saveRegistry(current)
        }
    }

    private fun saveRegistry(list: List<ImportedSubtitle>) {
        try {
            val jsonStr = json.encodeToString(list)
            registryFile.writeText(jsonStr, StandardCharsets.UTF_8)
        } catch (_: Exception) {}
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        return name
    }

    internal fun normalizeSubtitleEncoding(bytes: ByteArray): SubtitleEncodingDetectionResult {
        // 1. Check for UTF-8 / UTF-16 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            val content = String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            return SubtitleEncodingDetectionResult(content, "UTF-8 (BOM)", true)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            val content = String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            return SubtitleEncodingDetectionResult(content, "UTF-16BE", true)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            val content = String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            return SubtitleEncodingDetectionResult(content, "UTF-16LE", true)
        }

        // 2. Validate strict UTF-8
        val isStrictUtf8 = isStrictUtf8(bytes)
        if (isStrictUtf8) {
            val utf8Str = String(bytes, StandardCharsets.UTF_8)
            // If it contains Arabic characters or standard ASCII, it's valid UTF-8
            return SubtitleEncodingDetectionResult(utf8Str, "UTF-8", false)
        }

        // 3. Try Windows-1256 (the standard Arabic subtitle encoding on Windows)
        try {
            val win1256 = Charset.forName("windows-1256")
            val decodedWin1256 = String(bytes, win1256)
            if (isArabicText(decodedWin1256)) {
                return SubtitleEncodingDetectionResult(decodedWin1256, "Windows-1256 (Arabic)", true)
            }
        } catch (_: Exception) {}

        // 4. Try ISO-8859-6
        try {
            val isoArabic = Charset.forName("ISO-8859-6")
            val decodedIso = String(bytes, isoArabic)
            if (isArabicText(decodedIso)) {
                return SubtitleEncodingDetectionResult(decodedIso, "ISO-8859-6 (Arabic)", true)
            }
        } catch (_: Exception) {}

        // Fallback to ISO-8859-1 or default
        return SubtitleEncodingDetectionResult(String(bytes, StandardCharsets.ISO_8859_1), "ISO-8859-1", true)
    }

    private fun isStrictUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b < 0x80 -> i++
                b in 0xC2..0xDF -> {
                    if (i + 1 >= bytes.size) return false
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    if (b2 !in 0x80..0xBF) return false
                    i += 2
                }
                b in 0xE0..0xEF -> {
                    if (i + 2 >= bytes.size) return false
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val b3 = bytes[i + 2].toInt() and 0xFF
                    if (b2 !in 0x80..0xBF || b3 !in 0x80..0xBF) return false
                    i += 3
                }
                b in 0xF0..0xF4 -> {
                    if (i + 3 >= bytes.size) return false
                    val b2 = bytes[i + 1].toInt() and 0xFF
                    val b3 = bytes[i + 2].toInt() and 0xFF
                    val b4 = bytes[i + 3].toInt() and 0xFF
                    if (b2 !in 0x80..0xBF || b3 !in 0x80..0xBF || b4 !in 0x80..0xBF) return false
                    i += 4
                }
                else -> return false
            }
        }
        return true
    }

    internal fun isArabicText(text: String): Boolean {
        // Arabic unicode block: \u0600 - \u06FF
        val arabicCharCount = text.count { it in '\u0600'..'\u06FF' }
        return arabicCharCount > 10
    }

    internal fun shiftSrtTimestamps(content: String, shiftMs: Long): String {
        val srtTimePattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})")
        val matcher = srtTimePattern.matcher(content)
        val sb = StringBuffer()

        while (matcher.find()) {
            val startMs = parseSrtTime(
                matcher.group(1)!!.toLong(),
                matcher.group(2)!!.toLong(),
                matcher.group(3)!!.toLong(),
                matcher.group(4)!!.toLong()
            )
            val endMs = parseSrtTime(
                matcher.group(5)!!.toLong(),
                matcher.group(6)!!.toLong(),
                matcher.group(7)!!.toLong(),
                matcher.group(8)!!.toLong()
            )

            val newStartMs = (startMs + shiftMs).coerceAtLeast(0L)
            val newEndMs = (endMs + shiftMs).coerceAtLeast(newStartMs)

            val replacement = "${formatSrtTime(newStartMs)} --> ${formatSrtTime(newEndMs)}"
            matcher.appendReplacement(sb, replacement)
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    internal fun shiftAssTimestamps(content: String, shiftMs: Long): String {
        // Dialogue: 0,0:01:23.45,0:01:26.78,...
        val assLinePattern = Pattern.compile("(?i)^(Dialogue:\\s*[^,]+,)(\\d+:\\d{2}:\\d{2}\\.\\d{2}),(\\d+:\\d{2}:\\d{2}\\.\\d{2})(.*)$", Pattern.MULTILINE)
        val matcher = assLinePattern.matcher(content)
        val sb = StringBuffer()

        while (matcher.find()) {
            val prefix = matcher.group(1)!!
            val startStr = matcher.group(2)!!
            val endStr = matcher.group(3)!!
            val rest = matcher.group(4)!!

            val startMs = parseAssTime(startStr)
            val endMs = parseAssTime(endStr)

            val newStartMs = (startMs + shiftMs).coerceAtLeast(0L)
            val newEndMs = (endMs + shiftMs).coerceAtLeast(newStartMs)

            val replacement = "${prefix}${formatAssTime(newStartMs)},${formatAssTime(newEndMs)}${rest}"
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(sb)
        return sb.toString()
    }

    private fun parseSrtTime(h: Long, m: Long, s: Long, ms: Long): Long =
        h * 3600000L + m * 60000L + s * 1000L + ms

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3600000L
        val minutes = (ms % 3600000L) / 60000L
        val seconds = (ms % 60000L) / 1000L
        val millis = ms % 1000L
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun parseAssTime(str: String): Long {
        val parts = str.split(":", ".")
        if (parts.size < 4) return 0L
        val h = parts[0].toLongOrNull() ?: 0L
        val m = parts[1].toLongOrNull() ?: 0L
        val s = parts[2].toLongOrNull() ?: 0L
        val cs = parts[3].toLongOrNull() ?: 0L
        return h * 3600000L + m * 60000L + s * 1000L + (cs * 10L)
    }

    private fun formatAssTime(ms: Long): String {
        val hours = ms / 3600000L
        val minutes = (ms % 3600000L) / 60000L
        val seconds = (ms % 60000L) / 1000L
        val centis = (ms % 1000L) / 10L
        return String.format(java.util.Locale.US, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
    }
}
