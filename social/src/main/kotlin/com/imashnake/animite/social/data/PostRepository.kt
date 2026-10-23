package com.imashnake.animite.social.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.Post
import com.imashnake.animite.social.model.SocialNotification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val notificationRepository: NotificationRepository,
) {
    companion object {
        private const val POSTS_COLLECTION = "posts"
        private const val LIKES_COLLECTION = "likes"
    }

    private fun postDoc(postId: String) =
        firestore.collection(POSTS_COLLECTION).document(postId)

    private fun postLikesCollection(postId: String) =
        postDoc(postId).collection(LIKES_COLLECTION)

    /**
     * Real-time stream of community posts, newest first.
     */
    fun getFeedPosts(limit: Long = 40): Flow<List<Post>> = callbackFlow {
        val query = firestore.collection(POSTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Post::class.java)?.copy(id = doc.id)
            }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    /**
     * The author's own posts, newest first.
     */
    fun getPostsByUser(authorId: String, limit: Long = 50): Flow<List<Post>> = callbackFlow {
        if (authorId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query = firestore.collection(POSTS_COLLECTION)
            .whereEqualTo("authorId", authorId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Post::class.java)?.copy(id = doc.id)
            }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    /**
     * Loads a single post once.
     */
    suspend fun getPost(postId: String): Result<Post?> = runCatching {
        val doc = postDoc(postId).get().await()
        if (doc.exists()) {
            doc.toObject(Post::class.java)?.copy(id = doc.id)
        } else {
            null
        }
    }

    /**
     * Creates a new social post in the dedicated posts collection.
     */
    suspend fun createPost(
        author: AnimiteUser,
        content: String,
        mediaUrl: String? = null,
    ): Result<Post> = runCatching {
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

        val docRef = firestore.collection(POSTS_COLLECTION).document()
        val post = Post(
            id = docRef.id,
            authorId = author.uid,
            authorUsername = author.username,
            authorDisplayName = author.displayName,
            authorPhotoUrl = author.photoUrl,
            authorRole = author.role,
            authorCustomBadge = author.customBadgeTitle,
            content = cleanContent,
            mediaUrl = mediaUrl,
            likesCount = 0,
            commentCount = 0,
            createdAt = System.currentTimeMillis(),
        )

        docRef.set(post).await()
        post
    }

    /**
     * Likes or unlikes a post using the DEDICATED post-likes subcollection:
     * `posts/{postId}/likes/{userId}`.
     * Prevents duplicate likes and maintains consistent likesCount.
     */
    suspend fun toggleLike(
        postId: String,
        user: AnimiteUser,
    ): Result<Boolean> = runCatching {
        val likeRef = postLikesCollection(postId).document(user.uid)
        val likeDoc = likeRef.get().await()
        val isLiked = likeDoc.exists()

        val postDocumentRef = postDoc(postId)
        val batch = firestore.batch()

        if (isLiked) {
            batch.delete(likeRef)
            batch.update(postDocumentRef, "likesCount", FieldValue.increment(-1))
            batch.commit().await()
            false
        } else {
            batch.set(likeRef, mapOf("likedAt" to System.currentTimeMillis()))
            batch.update(postDocumentRef, "likesCount", FieldValue.increment(1))
            batch.commit().await()

            // Send notification to author if not self-like
            runCatching {
                val postSnapshot = postDocumentRef.get().await()
                val authorId = postSnapshot.getString("authorId")
                if (!authorId.isNullOrBlank() && authorId != user.uid) {
                    notificationRepository.createNotification(
                        SocialNotification(
                            recipientId = authorId,
                            senderId = user.uid,
                            senderUsername = user.username,
                            senderDisplayName = user.displayName,
                            senderPhotoUrl = user.photoUrl,
                            type = SocialNotification.TYPE_LIKE,
                            targetId = postId,
                            targetTitle = postSnapshot.getString("content")?.take(60),
                            isRead = false,
                            createdAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            true
        }
    }

    fun isPostLiked(postId: String, userId: String): Flow<Boolean> = callbackFlow {
        if (userId.isBlank() || postId.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val ref = postLikesCollection(postId).document(userId)
        val reg = ref.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.exists() == true)
        }
        awaitClose { reg.remove() }
    }

    suspend fun editPost(
        postId: String,
        newContent: String,
        authorId: String,
    ): Result<Unit> = runCatching {
        val clean = newContent.trim()
        require(clean.isNotBlank()) { "Content cannot be empty" }
        require(clean.length <= 2000) { "Content exceeds 2000 characters limit" }

        val ref = postDoc(postId)
        val doc = ref.get().await()
        val author = doc.getString("authorId")
        require(author == authorId) { "Unauthorized to edit this post" }

        ref.update(
            mapOf(
                "content" to clean,
                "isEdited" to true,
            )
        ).await()
    }

    suspend fun deletePost(
        postId: String,
        userId: String,
        isModerator: Boolean = false,
    ): Result<Unit> = runCatching {
        val ref = postDoc(postId)
        val doc = ref.get().await()
        val authorId = doc.getString("authorId")
        require(authorId == userId || isModerator) { "Unauthorized to delete this post" }

        // Soft-delete to preserve replies/comment count
        ref.update(
            mapOf(
                "isDeleted" to true,
                "content" to ""
            )
        ).await()
    }
}
