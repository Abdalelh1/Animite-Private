package com.imashnake.animite.social.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Validates animated profile cover media (MP4 video or animated GIF).
 * Enforces duration, file size, MIME type, dimensions, and generates a static poster frame.
 */
object LiveCoverValidator {
    const val MAX_GIF_SIZE_BYTES: Long = 10 * 1024 * 1024 // 10 MB
    const val MAX_VIDEO_SIZE_BYTES: Long = 25 * 1024 * 1024 // 25 MB

    const val MIN_DURATION_SECONDS = 2
    const val MAX_DURATION_STANDARD_SECONDS = 10
    const val MAX_DURATION_PREMIUM_SECONDS = 15

    sealed interface ValidationResult {
        data class Valid(
            val bytes: ByteArray,
            val mimeType: String,
            val durationMs: Long,
            val posterBytes: ByteArray?,
            val width: Int,
            val height: Int,
        ) : ValidationResult

        data class Invalid(val reason: String) : ValidationResult
    }

    fun validateAndRead(
        context: Context,
        uri: Uri,
        isPremium: Boolean = false,
    ): ValidationResult {
        val contentResolver = context.contentResolver

        // Check file descriptor length
        val descriptorLength = runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L

        val maxAllowedBytes = if (isPremium) 35 * 1024 * 1024L else MAX_VIDEO_SIZE_BYTES
        if (descriptorLength > maxAllowedBytes) {
            val sizeMb = String.format("%.1f", descriptorLength / (1024.0 * 1024.0))
            val limitMb = maxAllowedBytes / (1024 * 1024)
            return ValidationResult.Invalid("File is too large (${sizeMb} MB). Maximum allowed size is ${limitMb} MB.")
        }

        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return ValidationResult.Invalid("Unable to read selected media file.")

        if (bytes.isEmpty()) {
            return ValidationResult.Invalid("Selected file is empty.")
        }

        if (bytes.size > maxAllowedBytes) {
            val sizeMb = String.format("%.1f", bytes.size / (1024.0 * 1024.0))
            val limitMb = maxAllowedBytes / (1024 * 1024)
            return ValidationResult.Invalid("File is too large (${sizeMb} MB). Maximum allowed size is ${limitMb} MB.")
        }

        // Detect format via magic bytes
        val isGif = isGifBytes(bytes)
        val isMp4 = isMp4Bytes(bytes)

        if (!isGif && !isMp4) {
            val reportedType = contentResolver.getType(uri).orEmpty()
            return ValidationResult.Invalid(
                "Unsupported format ($reportedType). Please select an MP4 video or an animated GIF."
            )
        }

        if (isGif) {
            if (bytes.size > MAX_GIF_SIZE_BYTES) {
                val sizeMb = String.format("%.1f", bytes.size / (1024.0 * 1024.0))
                return ValidationResult.Invalid("GIF is too large (${sizeMb} MB). Maximum allowed GIF size is 10 MB.")
            }
            return ValidationResult.Valid(
                bytes = bytes,
                mimeType = "image/gif",
                durationMs = 0L,
                posterBytes = null, // GIF can serve as its own image or first frame
                width = 0,
                height = 0,
            )
        }

        // Process MP4 Video
        return processVideo(context, uri, bytes, isPremium)
    }

    private fun processVideo(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        isPremium: Boolean,
    ): ValidationResult {
        val retriever = MediaMetadataRetriever()
        var tempFile: File? = null

        return try {
            // Write to a temporary file if needed for reliable retriever access
            tempFile = File.createTempFile("animeta_cover_temp", ".mp4", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }
            retriever.setDataSource(tempFile.absolutePath)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationSec = durationMs / 1000.0

            val maxDuration = if (isPremium) MAX_DURATION_PREMIUM_SECONDS else MAX_DURATION_STANDARD_SECONDS
            if (durationSec < MIN_DURATION_SECONDS) {
                return ValidationResult.Invalid("Video duration is too short (${String.format("%.1f", durationSec)}s). Minimum duration is $MIN_DURATION_SECONDS seconds.")
            }
            if (durationSec > maxDuration) {
                return ValidationResult.Invalid(
                    "Video duration is too long (${String.format("%.1f", durationSec)}s). Maximum duration is $maxDuration seconds." +
                        if (!isPremium) " Upgrade to Premium for up to ${MAX_DURATION_PREMIUM_SECONDS}s." else ""
                )
            }

            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0

            // Extract first frame as poster
            val frameBitmap: Bitmap? = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime

            val posterBytes: ByteArray? = frameBitmap?.let { bmp ->
                ByteArrayOutputStream().use { stream ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                    stream.toByteArray()
                }
            }

            ValidationResult.Valid(
                bytes = bytes,
                mimeType = "video/mp4",
                durationMs = durationMs,
                posterBytes = posterBytes,
                width = width,
                height = height,
            )
        } catch (e: Exception) {
            ValidationResult.Invalid("Unable to process video: ${e.localizedMessage ?: "Unknown media error"}")
        } finally {
            runCatching { retriever.release() }
            tempFile?.delete()
        }
    }

    private fun isGifBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 6) return false
        return bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte() &&
            (bytes[4] == '7'.code.toByte() || bytes[4] == '9'.code.toByte()) &&
            bytes[5] == 'a'.code.toByte()
    }

    private fun isMp4Bytes(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        // MP4 ISO base media format has "ftyp" box at offset 4..7
        for (i in 4..minOf(bytes.size - 4, 32)) {
            if (bytes[i] == 'f'.code.toByte() &&
                bytes[i + 1] == 't'.code.toByte() &&
                bytes[i + 2] == 'y'.code.toByte() &&
                bytes[i + 3] == 'p'.code.toByte()
            ) {
                return true
            }
        }
        return false
    }
}
