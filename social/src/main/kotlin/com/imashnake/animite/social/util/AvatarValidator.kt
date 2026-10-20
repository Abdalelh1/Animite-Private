package com.imashnake.animite.social.util

import android.content.Context
import android.net.Uri

/**
 * Validates selected image format and file size prior to upload.
 *
 * NOTE ON SECURITY:
 * Client-side validation is implemented strictly for user experience, immediate UI feedback,
 * and saving network bandwidth. It does NOT prevent malicious or crafted uploads by attackers
 * who can bypass the Android client and invoke unsigned endpoints directly.
 * Production systems must enforce server-side validation and signed upload constraints.
 */
object AvatarValidator {
    const val MAX_FILE_SIZE_BYTES: Long = 5 * 1024 * 1024 // 5 MB
    val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    sealed interface ValidationResult {
        data class Valid(val bytes: ByteArray, val mimeType: String) : ValidationResult
        data class Invalid(val reason: String) : ValidationResult
    }

    fun validateAndRead(context: Context, uri: Uri): ValidationResult {
        val contentResolver = context.contentResolver

        // Quick check via file descriptor if length is exposed
        val descriptorLength = runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L

        if (descriptorLength > MAX_FILE_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", descriptorLength / (1024.0 * 1024.0))
            return ValidationResult.Invalid("Image is too large (${sizeMb} MB). Maximum allowed size is 5 MB.")
        }

        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return ValidationResult.Invalid("Unable to read selected image.")

        if (bytes.isEmpty()) {
            return ValidationResult.Invalid("Selected image file is empty.")
        }

        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            val sizeMb = String.format("%.1f", bytes.size / (1024.0 * 1024.0))
            return ValidationResult.Invalid("Image is too large (${sizeMb} MB). Maximum allowed size is 5 MB.")
        }

        val detectedMime = detectMimeType(bytes) ?: contentResolver.getType(uri)
        if (detectedMime == null || detectedMime !in ALLOWED_MIME_TYPES) {
            return ValidationResult.Invalid("Unsupported format. Please select a JPEG, PNG, WEBP, or GIF image.")
        }

        return ValidationResult.Valid(bytes, detectedMime)
    }

    private fun detectMimeType(bytes: ByteArray): String? {
        if (bytes.size < 12) return null

        // JPEG: FF D8 FF
        if ((bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xD8 &&
            (bytes[2].toInt() and 0xFF) == 0xFF
        ) {
            return "image/jpeg"
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if ((bytes[0].toInt() and 0xFF) == 0x89 &&
            bytes[1].toInt() == 0x50 &&
            bytes[2].toInt() == 0x4E &&
            bytes[3].toInt() == 0x47
        ) {
            return "image/png"
        }

        // GIF: 47 49 46 38
        if (bytes[0].toInt() == 0x47 &&
            bytes[1].toInt() == 0x49 &&
            bytes[2].toInt() == 0x46 &&
            bytes[3].toInt() == 0x38
        ) {
            return "image/gif"
        }

        // WEBP: 'R' 'I' 'F' 'F' ... 'W' 'E' 'B' 'P'
        if (bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }

        return null
    }
}
