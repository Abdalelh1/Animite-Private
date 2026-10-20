package com.imashnake.animite.social.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.imashnake.animite.social.model.AnimiteUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRelationshipRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun isFollowing(currentUid: String, targetUid: String): Flow<Boolean> = callbackFlow {
        if (currentUid.isBlank() || targetUid.isBlank() || currentUid == targetUid) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val docRef = firestore.collection("users")
            .document(currentUid)
            .collection("following")
            .document(targetUid)

        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(false)
                return@addSnapshotListener
            }
            trySend(snapshot?.exists() == true)
        }
        awaitClose { registration.remove() }
    }

    suspend fun followUser(
        currentUser: AnimiteUser,
        targetUser: AnimiteUser,
    ): Result<Unit> = runCatching {
        require(currentUser.uid != targetUser.uid) { "Cannot follow yourself" }

        val batch = firestore.batch()

        // 1. users/{currentUid}/following/{targetUid}
        val followingRef = firestore.collection("users")
            .document(currentUser.uid)
            .collection("following")
            .document(targetUser.uid)
        batch.set(
            followingRef,
            mapOf(
                "uid" to targetUser.uid,
                "username" to targetUser.username,
                "displayName" to targetUser.displayName,
                "photoUrl" to targetUser.photoUrl,
                "timestamp" to System.currentTimeMillis()
            )
        )

        // 2. users/{targetUid}/followers/{currentUid}
        val followerRef = firestore.collection("users")
            .document(targetUser.uid)
            .collection("followers")
            .document(currentUser.uid)
        batch.set(
            followerRef,
            mapOf(
                "uid" to currentUser.uid,
                "username" to currentUser.username,
                "displayName" to currentUser.displayName,
                "photoUrl" to currentUser.photoUrl,
                "timestamp" to System.currentTimeMillis()
            )
        )

        // 3. Increment counters
        val currentUserDoc = firestore.collection("users").document(currentUser.uid)
        batch.update(currentUserDoc, "followingCount", FieldValue.increment(1))

        val targetUserDoc = firestore.collection("users").document(targetUser.uid)
        batch.update(targetUserDoc, "followersCount", FieldValue.increment(1))

        batch.commit().await()
    }

    suspend fun unfollowUser(
        currentUid: String,
        targetUid: String,
    ): Result<Unit> = runCatching {
        val batch = firestore.batch()

        val followingRef = firestore.collection("users")
            .document(currentUid)
            .collection("following")
            .document(targetUid)
        batch.delete(followingRef)

        val followerRef = firestore.collection("users")
            .document(targetUid)
            .collection("followers")
            .document(currentUid)
        batch.delete(followerRef)

        val currentUserDoc = firestore.collection("users").document(currentUid)
        batch.update(currentUserDoc, "followingCount", FieldValue.increment(-1))

        val targetUserDoc = firestore.collection("users").document(targetUid)
        batch.update(targetUserDoc, "followersCount", FieldValue.increment(-1))

        batch.commit().await()
    }

    fun getFollowers(userId: String): Flow<List<AnimiteUser>> = callbackFlow {
        val ref = firestore.collection("users")
            .document(userId)
            .collection("followers")
            .orderBy("timestamp")

        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.toObject(AnimiteUser::class.java) }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    fun getFollowing(userId: String): Flow<List<AnimiteUser>> = callbackFlow {
        val ref = firestore.collection("users")
            .document(userId)
            .collection("following")
            .orderBy("timestamp")

        val registration = ref.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.toObject(AnimiteUser::class.java) }.orEmpty()
            trySend(list)
        }
        awaitClose { registration.remove() }
    }

    suspend fun blockUser(currentUid: String, blockedUid: String): Result<Unit> = runCatching {
        val blockRef = firestore.collection("users")
            .document(currentUid)
            .collection("blocks")
            .document(blockedUid)
        blockRef.set(mapOf("blockedAt" to System.currentTimeMillis())).await()

        // Also unfollow if previously following
        unfollowUser(currentUid, blockedUid)
    }

    suspend fun unblockUser(currentUid: String, blockedUid: String): Result<Unit> = runCatching {
        firestore.collection("users")
            .document(currentUid)
            .collection("blocks")
            .document(blockedUid)
            .delete()
            .await()
    }

    fun isUserBlocked(currentUid: String, targetUid: String): Flow<Boolean> = callbackFlow {
        if (currentUid.isBlank() || targetUid.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val ref = firestore.collection("users")
            .document(currentUid)
            .collection("blocks")
            .document(targetUid)

        val reg = ref.addSnapshotListener { snapshot, _ ->
            trySend(snapshot?.exists() == true)
        }
        awaitClose { reg.remove() }
    }

    suspend fun report(
        reporterId: String,
        targetType: String, // "user", "comment", "message"
        targetId: String,
        reason: String,
    ): Result<Unit> = runCatching {
        val reportData = mapOf(
            "reporterId" to reporterId,
            "targetType" to targetType,
            "targetId" to targetId,
            "reason" to reason,
            "createdAt" to System.currentTimeMillis(),
        )
        firestore.collection("reports").add(reportData).await()
    }
}
