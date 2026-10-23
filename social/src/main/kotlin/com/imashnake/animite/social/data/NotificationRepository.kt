package com.imashnake.animite.social.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.imashnake.animite.social.model.SocialNotification
import com.imashnake.animite.social.notification.AnimiteNotificationHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val notificationHelper: AnimiteNotificationHelper,
) {
    companion object {
        private const val TAG = "NotificationRepo"
        private const val USERS_COLLECTION = "users"
        private const val NOTIFICATIONS_COLLECTION = "notifications"
    }

    private fun notificationsCollection(userId: String) =
        firestore.collection(USERS_COLLECTION)
            .document(userId)
            .collection(NOTIFICATIONS_COLLECTION)

    fun getNotifications(userId: String, limit: Long = 50): Flow<List<SocialNotification>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val query = notificationsCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Error listening to notifications for $userId: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }

            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(SocialNotification::class.java)?.copy(id = doc.id)
            }.orEmpty()

            trySend(list)
        }

        awaitClose { registration.remove() }
    }

    fun getUnreadCount(userId: String): Flow<Int> = callbackFlow {
        if (userId.isBlank()) {
            trySend(0)
            close()
            return@callbackFlow
        }

        val query = notificationsCollection(userId)
            .whereEqualTo("read", false)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(0)
                return@addSnapshotListener
            }
            trySend(snapshot?.size() ?: 0)
        }

        awaitClose { registration.remove() }
    }

    suspend fun createNotification(notification: SocialNotification): Result<Unit> = runCatching {
        // 1. Never notify user about their own actions
        if (notification.recipientId.isBlank() || notification.recipientId == notification.senderId) {
            return@runCatching
        }

        val notificationId = if (notification.id.isNotBlank()) {
            notification.id
        } else {
            SocialNotification.generateId(
                type = notification.type,
                targetId = notification.targetId,
                senderId = notification.senderId
            )
        }

        val docRef = notificationsCollection(notification.recipientId).document(notificationId)
        val data = notification.copy(id = notificationId, isRead = false, createdAt = System.currentTimeMillis())

        docRef.set(data, SetOptions.merge()).await()

        // Post system notification via channel if permitted
        val (channel, title, body) = when (notification.type) {
            SocialNotification.TYPE_LIKE -> Triple(
                AnimiteNotificationHelper.CHANNEL_SOCIAL,
                "New Like",
                "${notification.senderDisplayName} liked your post"
            )
            SocialNotification.TYPE_COMMENT -> Triple(
                AnimiteNotificationHelper.CHANNEL_SOCIAL,
                "New Comment",
                "${notification.senderDisplayName} commented on your post"
            )
            SocialNotification.TYPE_REPLY -> Triple(
                AnimiteNotificationHelper.CHANNEL_SOCIAL,
                "New Reply",
                "${notification.senderDisplayName} replied to your comment"
            )
            SocialNotification.TYPE_FOLLOW -> Triple(
                AnimiteNotificationHelper.CHANNEL_SOCIAL,
                "New Follower",
                "${notification.senderDisplayName} started following you"
            )
            SocialNotification.TYPE_EPISODE -> Triple(
                AnimiteNotificationHelper.CHANNEL_EPISODES,
                "New Episode Released!",
                "Episode ${notification.episodeNumber ?: ""} of ${notification.targetTitle ?: "Anime"} is now out!"
            )
            else -> Triple(
                AnimiteNotificationHelper.CHANNEL_SOCIAL,
                "Animeta Notification",
                notification.targetTitle.orEmpty()
            )
        }

        notificationHelper.postLocalNotification(
            notificationId = notificationId.hashCode(),
            channelId = channel,
            title = title,
            body = body
        )
    }

    suspend fun createEpisodeNotification(
        recipientId: String,
        animeId: Int,
        animeTitle: String,
        episodeNumber: String,
    ): Result<Unit> = runCatching {
        if (recipientId.isBlank() || animeId <= 0 || episodeNumber.isBlank()) return@runCatching

        val stableId = SocialNotification.generateEpisodeId(animeId, episodeNumber)
        val notification = SocialNotification(
            id = stableId,
            recipientId = recipientId,
            senderId = "system",
            senderUsername = "Animeta",
            senderDisplayName = "Animeta",
            type = SocialNotification.TYPE_EPISODE,
            targetId = animeId.toString(),
            targetTitle = animeTitle,
            episodeNumber = episodeNumber,
            isRead = false,
            createdAt = System.currentTimeMillis()
        )

        createNotification(notification).getOrThrow()
    }

    suspend fun markAsRead(userId: String, notificationId: String): Result<Unit> = runCatching {
        if (userId.isBlank() || notificationId.isBlank()) return@runCatching
        notificationsCollection(userId)
            .document(notificationId)
            .update("read", true)
            .await()
    }

    suspend fun markAllAsRead(userId: String): Result<Unit> = runCatching {
        if (userId.isBlank()) return@runCatching
        val unreadDocs = notificationsCollection(userId)
            .whereEqualTo("read", false)
            .get()
            .await()

        if (unreadDocs.isEmpty) return@runCatching

        val batch = firestore.batch()
        unreadDocs.documents.forEach { doc ->
            batch.update(doc.reference, "read", true)
        }
        batch.commit().await()
    }
}
