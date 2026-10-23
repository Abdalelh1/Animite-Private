package com.imashnake.animite.social.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SocialNotification(
    val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderUsername: String = "",
    val senderDisplayName: String = "",
    val senderPhotoUrl: String? = null,
    val type: String = "", // "like", "comment", "reply", "follow", "episode"
    val targetId: String = "", // postId, commentId, or animeId
    val targetTitle: String? = null, // Preview of post, comment, or anime title
    val episodeNumber: String? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_LIKE = "like"
        const val TYPE_COMMENT = "comment"
        const val TYPE_REPLY = "reply"
        const val TYPE_FOLLOW = "follow"
        const val TYPE_EPISODE = "episode"

        fun generateId(type: String, targetId: String, senderId: String): String {
            return "${type}_${targetId}_${senderId}"
        }

        fun generateEpisodeId(animeId: Int, episodeNumber: String): String {
            return "ep_${animeId}_$episodeNumber"
        }
    }
}
