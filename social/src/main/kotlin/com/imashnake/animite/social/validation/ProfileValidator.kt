package com.imashnake.animite.social.validation

import com.imashnake.animite.social.model.ProfileCustomization

/**
 * Validation result for profile fields.
 */
sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult

    val isValid: Boolean get() = this is Valid
}

/**
 * Strict profile validator enforcing production constraints:
 * - Display Name: 2..40 characters (excluding leading/trailing whitespace), no raw HTML/scripts
 * - Username: 3..30 characters, lowercase alphanumeric, underscore, dot
 * - Bio: <= 300 characters, no raw script tags
 * - Location: <= 100 characters
 * - Website: <= 120 characters, must start with https:// if present
 * - Accent Color: Must belong to curated WCAG palette
 */
object ProfileValidator {

    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val USERNAME_REGEX = Regex("^[a-z0-9_.]+$")

    fun validateDisplayName(displayName: String?): ValidationResult {
        if (displayName == null) return ValidationResult.Invalid("Display name cannot be null")
        val trimmed = displayName.trim()
        if (trimmed.length < 2) return ValidationResult.Invalid("Display name must be at least 2 characters")
        if (trimmed.length > 40) return ValidationResult.Invalid("Display name must not exceed 40 characters")
        if (HTML_TAG_REGEX.containsMatchIn(trimmed)) return ValidationResult.Invalid("Display name cannot contain HTML tags")
        return ValidationResult.Valid
    }

    fun validateUsername(username: String?): ValidationResult {
        if (username == null) return ValidationResult.Invalid("Username cannot be null")
        val trimmed = username.trim().lowercase()
        if (trimmed.length < 3) return ValidationResult.Invalid("Username must be at least 3 characters")
        if (trimmed.length > 30) return ValidationResult.Invalid("Username must not exceed 30 characters")
        if (!USERNAME_REGEX.matches(trimmed)) {
            return ValidationResult.Invalid("Username can only contain lowercase letters, numbers, underscores, and dots")
        }
        return ValidationResult.Valid
    }

    fun validateBio(bio: String?): ValidationResult {
        if (bio.isNullOrBlank()) return ValidationResult.Valid
        val trimmed = bio.trim()
        if (trimmed.length > 300) return ValidationResult.Invalid("Bio must not exceed 300 characters")
        if (HTML_TAG_REGEX.containsMatchIn(trimmed)) return ValidationResult.Invalid("Bio cannot contain HTML tags")
        return ValidationResult.Valid
    }

    fun validateLocation(location: String?): ValidationResult {
        if (location.isNullOrBlank()) return ValidationResult.Valid
        val trimmed = location.trim()
        if (trimmed.length > 100) return ValidationResult.Invalid("Location must not exceed 100 characters")
        if (HTML_TAG_REGEX.containsMatchIn(trimmed)) return ValidationResult.Invalid("Location cannot contain HTML tags")
        return ValidationResult.Valid
    }

    fun validateWebsite(websiteUrl: String?): ValidationResult {
        if (websiteUrl.isNullOrBlank()) return ValidationResult.Valid
        val trimmed = websiteUrl.trim()
        if (trimmed.length > 120) return ValidationResult.Invalid("Website URL is too long")
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            return ValidationResult.Invalid("Website URL must begin with https://")
        }
        return ValidationResult.Valid
    }

    fun validateAccentColor(hexColor: String?): ValidationResult {
        if (hexColor == null) return ValidationResult.Valid
        if (!ProfileCustomization.isValidAccentColor(hexColor)) {
            return ValidationResult.Invalid("Accent color must be an approved palette color")
        }
        return ValidationResult.Valid
    }
}
