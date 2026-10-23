package com.imashnake.animite.social.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.ChatMessage
import com.imashnake.animite.social.model.Conversation
import com.imashnake.animite.social.model.ParticipantInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun getConversationId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    fun getConversations(currentUid: String): Flow<List<Conversation>> = callbackFlow {
        if (currentUid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = firestore.collection("conversations")
            .whereArrayContains("participants", currentUid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                val id = doc.id
                val participants = (doc.get("participants") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                val detailsRaw = doc.get("participantDetails") as? Map<*, *>
                val details = detailsRaw?.mapNotNull { (k, v) ->
                    val uid = k?.toString() ?: return@mapNotNull null
                    val map = v as? Map<*, *> ?: return@mapNotNull null
                    uid to ParticipantInfo(
                        uid = uid,
                        displayName = map["displayName"]?.toString().orEmpty(),
                        username = map["username"]?.toString().orEmpty(),
                        photoUrl = map["photoUrl"]?.toString()
                    )
                }?.toMap().orEmpty()

                val lastText = doc.getString("lastMessageText").orEmpty()
                val lastTs = doc.getLong("lastMessageTimestamp") ?: 0L
                val lastSender = doc.getString("lastMessageSenderId").orEmpty()
                val unreadMap = (doc.get("unreadCounts") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    k?.toString()?.let { it to ((v as? Number)?.toInt() ?: 0) }
                }?.toMap().orEmpty()
                val updatedAt = doc.getLong("updatedAt") ?: 0L

                Conversation(
                    id = id,
                    participants = participants,
                    participantDetails = details,
                    lastMessageText = lastText,
                    lastMessageTimestamp = lastTs,
                    lastMessageSenderId = lastSender,
                    unreadCounts = unreadMap,
                    updatedAt = updatedAt
                )
            }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    fun getMessages(conversationId: String, limit: Long = 50): Flow<List<ChatMessage>> = callbackFlow {
        val query = firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(limit)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
            }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    suspend fun sendMessage(
        sender: AnimiteUser,
        recipient: AnimiteUser,
        text: String,
    ): Result<ChatMessage> = runCatching {
        val cleanText = text.trim()
        require(cleanText.isNotBlank()) { "Message cannot be empty" }
        require(cleanText.length <= 4000) { "Message exceeds 4000 character limit" }

        val settingsDoc = firestore.collection("system_settings").document("config").get().await()
        if (settingsDoc.exists()) {
            val isMaintenance = settingsDoc.getBoolean("maintenanceMode") ?: false
            if (isMaintenance && !sender.isOwner) {
                throw IllegalStateException("نظام Animeta في وضع الصيانة حالياً. المراسلة متوقفة.")
            }
            val dmsEnabled = settingsDoc.getBoolean("directMessagesEnabled") ?: true
            if (!dmsEnabled && !sender.isModerator && !sender.isOwner) {
                throw IllegalStateException("المراسلة الخاصة متوقفة مؤقتاً بأمر إدارة التطبيق.")
            }
        }

        val convId = getConversationId(sender.uid, recipient.uid)
        val convRef = firestore.collection("conversations").document(convId)
        val msgRef = convRef.collection("messages").document()

        val timestamp = System.currentTimeMillis()
        val chatMessage = ChatMessage(
            id = msgRef.id,
            senderId = sender.uid,
            receiverId = recipient.uid,
            text = cleanText,
            timestamp = timestamp,
            read = false
        )

        val batch = firestore.batch()

        // 1. Write the message
        batch.set(msgRef, chatMessage)

        // 2. Update or create the conversation document
        val convData = mapOf(
            "id" to convId,
            "participants" to listOf(sender.uid, recipient.uid),
            "participantDetails" to mapOf(
                sender.uid to mapOf(
                    "displayName" to sender.displayName,
                    "username" to sender.username,
                    "photoUrl" to sender.photoUrl
                ),
                recipient.uid to mapOf(
                    "displayName" to recipient.displayName,
                    "username" to recipient.username,
                    "photoUrl" to recipient.photoUrl
                )
            ),
            "lastMessageText" to cleanText,
            "lastMessageTimestamp" to timestamp,
            "lastMessageSenderId" to sender.uid,
            "unreadCounts.${recipient.uid}" to FieldValue.increment(1),
            "updatedAt" to timestamp
        )
        batch.set(convRef, convData, com.google.firebase.firestore.SetOptions.merge())

        batch.commit().await()
        chatMessage
    }

    suspend fun markConversationAsRead(conversationId: String, currentUid: String): Result<Unit> = runCatching {
        firestore.collection("conversations")
            .document(conversationId)
            .update("unreadCounts.$currentUid", 0)
            .await()
    }
}
