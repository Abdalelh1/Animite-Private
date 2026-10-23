package com.imashnake.animite.social.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Comment(
    val id: String = "",
    val targetType: String = "anime", // "anime", "episode"
    val targetId: String = "", // e.g. "anime_12345" or "episode_12345_1"
    val animeId: Int = 0,
    val episodeNumber: String? = null,
    val authorId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorPhotoUrl: String? = null,
    val content: String = "",
    val mediaUrl: String? = null,
    val parentId: String? = null,
    val replyCount: Int = 0,
    val likesCount: Int = 0,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val reportCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val authorRole: String = "user", // "owner", "admin", "moderator", "user"
    val authorCustomBadge: String? = null,
) {
    val displayContent: String
        get() = if (isDeleted) "[Comment deleted by user]" else content

    val isAuthorOwner: Boolean get() = authorRole.equals("owner", ignoreCase = true)
    val isAuthorAdmin: Boolean get() = isAuthorOwner || authorRole.equals("admin", ignoreCase = true)
    val isAuthorModerator: Boolean get() = isAuthorAdmin || authorRole.equals("moderator", ignoreCase = true)

    val authorBadgeLabel: String?
        get() = when {
            !authorCustomBadge.isNullOrBlank() -> authorCustomBadge
            isAuthorOwner -> "مالك التطبيق"
            authorRole.equals("admin", ignoreCase = true) -> "مدير النظام"
            authorRole.equals("moderator", ignoreCase = true) -> "مشرف"
            else -> null
        }
}
