package com.imashnake.animite.social.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ParticipantInfo(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val photoUrl: String? = null,
)

@Immutable
@Serializable
data class Conversation(
    val id: String = "",
    val participants: List<String> = emptyList(),
    val participantDetails: Map<String, ParticipantInfo> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val lastMessageSenderId: String = "",
    val unreadCounts: Map<String, Int> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun getOtherParticipant(currentUid: String): ParticipantInfo? {
        val otherUid = participants.firstOrNull { it != currentUid } ?: return null
        return participantDetails[otherUid]
    }

    fun getUnreadCount(currentUid: String): Int {
        return unreadCounts[currentUid] ?: 0
    }
}

@Immutable
@Serializable
data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val read: Boolean = false,
)
