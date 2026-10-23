package com.imashnake.animite.social.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Post(
    val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val authorDisplayName: String = "",
    val authorPhotoUrl: String? = null,
    val authorRole: String = "user", // "owner", "admin", "moderator", "user"
    val authorCustomBadge: String? = null,
    val content: String = "",
    val mediaUrl: String? = null,
    val likesCount: Int = 0,
    val commentCount: Int = 0,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val displayContent: String
        get() = if (isDeleted) "[Post deleted by user]" else content

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
