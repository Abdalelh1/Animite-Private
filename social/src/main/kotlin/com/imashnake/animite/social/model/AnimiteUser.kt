package com.imashnake.animite.social.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Visibility scope for specific profile fields.
 */
@Serializable
enum class FieldVisibility {
    PUBLIC,
    FRIENDS,
    PRIVATE;

    companion object {
        fun fromString(value: String?): FieldVisibility = when (value?.trim()?.uppercase()) {
            "FRIENDS", "FOLLOWERS" -> FRIENDS
            "PRIVATE" -> PRIVATE
            else -> PUBLIC
        }
    }
}

/**
 * Profile-wide accessibility mode.
 */
@Serializable
enum class ProfileVisibility {
    PUBLIC,
    FOLLOWERS_ONLY,
    PRIVATE;

    companion object {
        fun fromString(value: String?): ProfileVisibility = when (value?.trim()?.uppercase()) {
            "FOLLOWERS_ONLY", "FRIENDS" -> FOLLOWERS_ONLY
            "PRIVATE" -> PRIVATE
            else -> PUBLIC
        }
    }
}

/**
 * Theme preset customization options.
 */
@Serializable
enum class ThemePreset(val id: String) {
    DEFAULT_DARK("default_dark"),
    CYAN_GLOW("cyan_glow"),
    AMOLED_MIDNIGHT("amoled_midnight"),
    PURPLE_NEBULA("purple_nebula");

    companion object {
        fun fromId(id: String?): ThemePreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CYAN_GLOW
    }
}

/**
 * Profile layout organization presets.
 */
@Serializable
enum class LayoutPreset(val id: String) {
    STANDARD("standard"),
    COMPACT("compact"),
    EXPANDED("expanded");

    companion object {
        fun fromId(id: String?): LayoutPreset =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STANDARD
    }
}

/**
 * Theme & display customization preferences for a user profile.
 */
@Immutable
@Serializable
data class ProfileCustomization(
    val themePreset: String = ThemePreset.CYAN_GLOW.id,
    val accentColor: String = "#00D1FF",
    val layoutPreset: String = LayoutPreset.STANDARD.id,
    val visibleSections: List<String> = listOf("stats", "tabs", "bio"),
) {
    companion object {
        val DEFAULT = ProfileCustomization()

        // Curated safe accent palette (WCAG readable against dark surfaces)
        val ALLOWED_ACCENT_COLORS: Set<String> = setOf(
            "#00D1FF", // Neon Cyan
            "#22D3EE", // Sky Cyan
            "#818CF8", // Indigo Accent
            "#A78BFA", // Violet Pastel
            "#34D399", // Emerald Accent
            "#F472B6", // Rose Pastel
            "#FBBF24", // Amber Accent
        )

        fun isValidAccentColor(hex: String?): Boolean {
            if (hex.isNullOrBlank()) return false
            val normalized = hex.trim().uppercase()
            return ALLOWED_ACCENT_COLORS.any { it.equals(normalized, ignoreCase = true) }
        }
    }
}

/**
 * Granular field visibility settings for the user profile.
 */
@Immutable
@Serializable
data class ProfilePrivacy(
    val profileVisibility: String = ProfileVisibility.PUBLIC.name,
    val bioVisibility: String = FieldVisibility.PUBLIC.name,
    val locationVisibility: String = FieldVisibility.PUBLIC.name,
    val websiteVisibility: String = FieldVisibility.PUBLIC.name,
    val libraryVisibility: String = FieldVisibility.PUBLIC.name,
    val emailVisibility: String = FieldVisibility.PRIVATE.name,
    val phoneVisibility: String = FieldVisibility.PRIVATE.name,
) {
    companion object {
        val DEFAULT = ProfilePrivacy()
    }
}

/**
 * Fully backward-compatible Animite User model with schema versioning.
 *
 * Defaults ensure older documents without schemaVersion, customization, or privacy
 * deserialize seamlessly without breaking existing flows or crashes.
 */
@Immutable
@Serializable
data class AnimiteUser(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val bannerUrl: String? = null,
    val location: String? = null,
    val websiteUrl: String? = null,
    val isPrivate: Boolean = false,
    val allowMessagesFrom: String = "everyone", // "everyone", "followers", "none"
    val anilistUserId: Int? = null,
    val anilistUsername: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 2,
    val customization: ProfileCustomization = ProfileCustomization.DEFAULT,
    val privacy: ProfilePrivacy = ProfilePrivacy.DEFAULT,
    val role: String = "user", // "owner", "admin", "moderator", "user", "premium", "verified"
    val customBadgeTitle: String? = null, // e.g. "مالك التطبيق", "مشرف عام", "مشرف الفعاليات"
    val isVerified: Boolean = false,
    val premiumUntil: Long? = null,
    val isSuspended: Boolean = false,
    val liveCoverUrl: String? = null,
    val liveCoverPosterUrl: String? = null,
    val liveCoverMediaType: String? = null, // "video/mp4" or "image/gif"
) {
    // Live cover helpers
    val hasLiveCover: Boolean get() = !liveCoverUrl.isNullOrBlank()
    val isLiveCoverVideo: Boolean
        get() = liveCoverMediaType == "video/mp4" || liveCoverUrl?.contains(".mp4", ignoreCase = true) == true
    val isLiveCoverGif: Boolean
        get() = liveCoverMediaType == "image/gif" || liveCoverUrl?.contains(".gif", ignoreCase = true) == true

    // Backward-compatible alias for avatarUrl as specified in requirements
    val avatarUrl: String? get() = photoUrl
    val handle: String get() = if (username.isNotBlank()) "@$username" else ""
    val isAnilistLinked: Boolean get() = anilistUserId != null

    // Strongly-typed RBAC role representation (falls back to VERIFIED or MEMBER if premium expired)
    val userRole: UserRole
        get() {
            val parsed = UserRole.fromId(role)
            if (parsed == UserRole.PREMIUM && premiumUntil != null && premiumUntil <= System.currentTimeMillis()) {
                return if (isVerified) UserRole.VERIFIED else UserRole.MEMBER
            }
            return parsed
        }

    // Role & permissions helpers
    val isOwner: Boolean get() = userRole == UserRole.OWNER
    val isAdmin: Boolean get() = isOwner || role.equals("admin", ignoreCase = true)
    val isModerator: Boolean get() = isOwner || isAdmin || userRole == UserRole.MODERATOR
    val isPremium: Boolean get() = isOwner || (premiumUntil != null && premiumUntil > System.currentTimeMillis()) || (userRole == UserRole.PREMIUM && premiumUntil == null)
    val isVerifiedUser: Boolean get() = isVerified || userRole == UserRole.VERIFIED || isOwner

    val hasBadge: Boolean get() = isOwner || isModerator || isPremium || isVerifiedUser || !customBadgeTitle.isNullOrBlank()

    /**
     * Display label for user badge in Arabic.
     */
    val badgeLabel: String?
        get() = when {
            !customBadgeTitle.isNullOrBlank() -> customBadgeTitle
            isOwner -> "مالك التطبيق"
            role.equals("admin", ignoreCase = true) -> "مدير النظام"
            userRole == UserRole.MODERATOR -> "مشرف"
            userRole == UserRole.PREMIUM -> "نخبة"
            userRole == UserRole.VERIFIED -> "موثق"
            else -> null
        }
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: AnimiteUser) : AuthState
}
