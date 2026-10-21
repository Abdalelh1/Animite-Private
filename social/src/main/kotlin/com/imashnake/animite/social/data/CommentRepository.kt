package com.imashnake.animite.social.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.Comment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
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
     * The author's own comments, newest first — these are the "posts" shown on
     * the social profile's Posts/Replies tabs. Needs the composite index
     * (authorId ASC, createdAt DESC) declared in firestore.indexes.json.
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
        )

        docRef.set(comment).await()

        if (parentId != null) {
            firestore.collection("comments").document(parentId)
                .update("replyCount", FieldValue.increment(1))
                .await()
        }

        comment
    }

    suspend fun toggleLike(commentId: String, userId: String): Result<Boolean> = runCatching {
        val likeRef = firestore.collection("comments")
            .document(commentId)
            .collection("likes")
            .document(userId)

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
            true
        }
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

    suspend fun deleteComment(commentId: String, userId: String): Result<Unit> = runCatching {
        val commentRef = firestore.collection("comments").document(commentId)
        val doc = commentRef.get().await()
        val authorId = doc.getString("authorId")
        require(authorId == userId) { "Unauthorized to delete this comment" }

        // Soft-delete to preserve replies tree
        commentRef.update(
            mapOf(
                "isDeleted" to true,
                "content" to ""
            )
        ).await()
    }
}
