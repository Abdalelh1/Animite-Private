package com.imashnake.animite.social.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.Comment
import com.imashnake.animite.social.model.SocialNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val notificationRepository: NotificationRepository,
) {
    /**
     * Real-time stream of comments for a specific target (anime or episode).
     */
    fun getComments(targetId: String, limit: Long = 30): Flow<List<Comment>> = callbackFlow {
        val query = firestore.collection("comments")
            .whereEqualTo("targetId", targetId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.copy(id = doc.id)
            }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    /**
     * Community posts feed for the Social tab: top-level posts ordered newest first.
     */
    fun getCommunityFeed(limit: Long = 30): Flow<List<Comment>> = callbackFlow {
        val query = firestore.collection("comments")
            .whereEqualTo("targetType", "community")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.copy(id = doc.id)
            }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    /**
     * The author's own comments and posts, newest first.
     */
    fun getCommentsByUser(authorId: String, limit: Long = 50): Flow<List<Comment>> = callbackFlow {
        if (authorId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query = firestore.collection("comments")
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.copy(id = doc.id)
            }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    /**
     * Creates a new standalone community social post with optional media attachment.
     */
    suspend fun createPost(
        author: AnimiteUser,
        content: String,
        mediaUrl: String? = null,
    ): Result<Comment> = runCatching {
        val cleanContent = content.trim()
        require(cleanContent.isNotBlank() || !mediaUrl.isNullOrBlank()) { "Post content or media must not be empty" }
        require(cleanContent.length <= 2000) { "Post exceeds 2000 characters limit" }

        val settingsDoc = firestore.collection("system_settings").document("config").get().await()
        if (settingsDoc.exists()) {
            val isMaintenance = settingsDoc.getBoolean("maintenanceMode") ?: false
            if (isMaintenance && !author.isOwner) {
                throw IllegalStateException("نظام Animeta في وضع الصيانة حالياً. المنشورات متوقفة.")
            }
            val commentsEnabled = settingsDoc.getBoolean("commentsEnabled") ?: true
            if (!commentsEnabled && !author.isModerator && !author.isOwner) {
                throw IllegalStateException("المشاركات متوقفة مؤقتاً بأمر إدارة التطبيق.")
            }
        }

        val docRef = firestore.collection("comments").document()
        val post = Comment(
            id = docRef.id,
            targetType = "community",
            targetId = "community",
            animeId = 0,
            episodeNumber = null,
            authorId = author.uid,
            authorUsername = author.username,
            authorDisplayName = author.displayName,
            authorPhotoUrl = author.photoUrl,
            content = cleanContent,
            mediaUrl = mediaUrl,
            parentId = null,
            createdAt = System.currentTimeMillis(),
            authorRole = author.role,
            authorCustomBadge = author.customBadgeTitle,
        )

        docRef.set(post).await()
        post
    }

    /**
     * Edits an existing post or comment, verifying ownership.
     */
    suspend fun editComment(
        commentId: String,
        newContent: String,
        authorId: String,
    ): Result<Unit> = runCatching {
        val clean = newContent.trim()
        require(clean.isNotBlank()) { "Content cannot be empty" }
        require(clean.length <= 2000) { "Content exceeds 2000 characters limit" }

        val commentRef = firestore.collection("comments").document(commentId)
        val doc = commentRef.get().await()
        val author = doc.getString("authorId")
        require(author == authorId) { "Unauthorized to edit this post/comment" }

        commentRef.update(
            mapOf(
                "content" to clean,
                "isEdited" to true,
            )
        ).await()
    }

    suspend fun addComment(
        targetType: String,
        targetId: String,
        animeId: Int,
        episodeNumber: String?,
        author: AnimiteUser,
        content: String,
        parentId: String? = null,
    ): Result<Comment> = runCatching {
        val cleanContent = content.trim()
        require(cleanContent.isNotBlank()) { "Comment cannot be empty" }
        require(cleanContent.length <= 2000) { "Comment exceeds 2000 characters limit" }

        val settingsDoc = firestore.collection("system_settings").document("config").get().await()
        if (settingsDoc.exists()) {
            val isMaintenance = settingsDoc.getBoolean("maintenanceMode") ?: false
            if (isMaintenance && !author.isOwner) {
                throw IllegalStateException("نظام Animeta في وضع الصيانة حالياً. التعليقات متوقفة.")
            }
            val commentsEnabled = settingsDoc.getBoolean("commentsEnabled") ?: true
            if (!commentsEnabled && !author.isModerator && !author.isOwner) {
                throw IllegalStateException("التعليقات متوقفة مؤقتاً بأمر إدارة التطبيق.")
            }
        }

        val docRef = firestore.collection("comments").document()
        val comment = Comment(
            id = docRef.id,
            targetType = targetType,
            targetId = targetId,
            animeId = animeId,
            episodeNumber = episodeNumber,
            authorId = author.uid,
            authorUsername = author.username,
            authorDisplayName = author.displayName,
            authorPhotoUrl = author.photoUrl,
            content = cleanContent,
            parentId = parentId,
            createdAt = System.currentTimeMillis(),
            authorRole = author.role,
            authorCustomBadge = author.customBadgeTitle,
        )

        docRef.set(comment).await()

        if (parentId != null) {
            val parentRef = firestore.collection("comments").document(parentId)
            parentRef.update("replyCount", FieldValue.increment(1)).await()

            // Notify parent comment author
            runCatching {
                val parentDoc = parentRef.get().await()
                val parentAuthorId = parentDoc.getString("authorId")
                if (!parentAuthorId.isNullOrBlank() && parentAuthorId != author.uid) {
                    notificationRepository.createNotification(
                        SocialNotification(
                            recipientId = parentAuthorId,
                            senderId = author.uid,
                            senderUsername = author.username,
                            senderDisplayName = author.displayName,
                            senderPhotoUrl = author.photoUrl,
                            type = SocialNotification.TYPE_REPLY,
                            targetId = parentId,
                            targetTitle = cleanContent.take(80),
                            isRead = false,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
        } else if (targetType == "post" || targetType == "community") {
            // Top-level comment on a post: increment post commentCount and notify post author
            runCatching {
                val postRef = firestore.collection("posts").document(targetId)
                val postDoc = postRef.get().await()
                if (postDoc.exists()) {
                    postRef.update("commentCount", FieldValue.increment(1)).await()
                    val postAuthorId = postDoc.getString("authorId")
                    if (!postAuthorId.isNullOrBlank() && postAuthorId != author.uid) {
                        notificationRepository.createNotification(
                            SocialNotification(
                                recipientId = postAuthorId,
                                senderId = author.uid,
                                senderUsername = author.username,
                                senderDisplayName = author.displayName,
                                senderPhotoUrl = author.photoUrl,
                                type = SocialNotification.TYPE_COMMENT,
                                targetId = targetId,
                                targetTitle = cleanContent.take(80),
                                isRead = false,
                                createdAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
            }
        }

        comment
    }

    suspend fun toggleLike(
        commentId: String,
        user: AnimiteUser,
    ): Result<Boolean> = runCatching {
        val likeRef = firestore.collection("comments")
            .document(commentId)
            .collection("likes")
            .document(user.uid)

        val likeDoc = likeRef.get().await()
        val isLiked = likeDoc.exists()

        val commentRef = firestore.collection("comments").document(commentId)
        val batch = firestore.batch()

        if (isLiked) {
            batch.delete(likeRef)
            batch.update(commentRef, "likesCount", FieldValue.increment(-1))
            batch.commit().await()
            false
        } else {
            batch.set(likeRef, mapOf("likedAt" to System.currentTimeMillis()))
            batch.update(commentRef, "likesCount", FieldValue.increment(1))
            batch.commit().await()

            // Send like notification to comment author if not self-action
            runCatching {
                val commentDoc = commentRef.get().await()
                val authorId = commentDoc.getString("authorId")
                if (!authorId.isNullOrBlank() && authorId != user.uid) {
                    notificationRepository.createNotification(
                        SocialNotification(
                            recipientId = authorId,
                            senderId = user.uid,
                            senderUsername = user.username,
                            senderDisplayName = user.displayName,
                            senderPhotoUrl = user.photoUrl,
                            type = SocialNotification.TYPE_LIKE,
                            targetId = commentId,
                            targetTitle = commentDoc.getString("content")?.take(60),
                            isRead = false,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            true
        }
    }

    suspend fun toggleLike(commentId: String, userId: String): Result<Boolean> = runCatching {
        val userDoc = firestore.collection("users").document(userId).get().await()
        val user = userDoc.toObject(AnimiteUser::class.java) ?: AnimiteUser(uid = userId)
        toggleLike(commentId, user).getOrThrow()
    }

    fun isCommentLiked(commentId: String, userId: String): Flow<Boolean> = callbackFlow {
        if (userId.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val ref = firestore.collection("comments")
            .document(commentId)
            .collection("likes")
            .document(userId)

        val reg = ref.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.exists() == true)
        }
        awaitClose { reg.remove() }
    }

    suspend fun deleteComment(
        commentId: String,
        userId: String,
        isModerator: Boolean = false,
    ): Result<Unit> = runCatching {
        val commentRef = firestore.collection("comments").document(commentId)
        val doc = commentRef.get().await()
        val authorId = doc.getString("authorId")
        require(authorId == userId || isModerator) { "Unauthorized to delete this comment" }

        // Soft-delete to preserve replies tree
        commentRef.update(
            mapOf(
                "isDeleted" to true,
                "content" to ""
            )
        ).await()
    }
}
