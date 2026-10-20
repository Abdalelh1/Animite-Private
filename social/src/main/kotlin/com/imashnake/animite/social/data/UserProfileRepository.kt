package com.imashnake.animite.social.data

import com.google.firebase.firestore.FirebaseFirestore
import com.imashnake.animite.api.preferences.PreferencesRepository
import com.imashnake.animite.social.model.AnimiteUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val cloudinaryUploader: CloudinaryUploader,
    private val preferencesRepository: PreferencesRepository,
) {
    fun getUserProfile(uid: String): Flow<AnimiteUser?> = callbackFlow {
        val docRef = firestore.collection("users").document(uid)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }
            val user = snapshot?.toObject(AnimiteUser::class.java)
            trySend(user)
        }
        awaitClose { registration.remove() }
    }

    suspend fun getUserProfileOnce(uid: String): AnimiteUser? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toObject(AnimiteUser::class.java)
    }

    suspend fun updateUserProfile(
        uid: String,
        displayName: String,
        bio: String,
        isPrivate: Boolean,
        allowMessagesFrom: String,
    ): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).update(
            mapOf(
                "displayName" to displayName.trim(),
                "bio" to bio.trim(),
                "isPrivate" to isPrivate,
                "allowMessagesFrom" to allowMessagesFrom,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    suspend fun uploadAvatar(
        uid: String,
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
    ): Result<String> = runCatching {
        // Step 1: Upload to Cloudinary
        val downloadUrl = cloudinaryUploader.uploadImage(imageBytes, mimeType = mimeType)
            .getOrThrow()

        // Step 2: Save secure_url to user's existing Firestore document schema
        try {
            firestore.collection("users").document(uid).update(
                mapOf(
                    "photoUrl" to downloadUrl,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        } catch (e: Exception) {
            throw Exception("Failed to update profile in Firestore: ${e.localizedMessage}", e)
        }

        // Also update local preferences cache for app-wide avatar consistency
        runCatching {
            preferencesRepository.setViewerAvatar(downloadUrl)
        }

        downloadUrl
    }

    suspend fun uploadBanner(uid: String, imageBytes: ByteArray, mimeType: String = "image/jpeg"): Result<String> = runCatching {
        val downloadUrl = cloudinaryUploader.uploadImage(imageBytes, mimeType = mimeType)
            .getOrThrow()

        firestore.collection("users").document(uid).update(
            mapOf(
                "bannerUrl" to downloadUrl,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()

        downloadUrl
    }

    suspend fun linkAnilistAccount(uid: String, anilistUserId: Int, anilistUsername: String): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).update(
            mapOf(
                "anilistUserId" to anilistUserId,
                "anilistUsername" to anilistUsername,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    suspend fun unlinkAnilistAccount(uid: String): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).update(
            mapOf(
                "anilistUserId" to null,
                "anilistUsername" to null,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    fun searchUsers(query: String, limit: Long = 20): Flow<List<AnimiteUser>> = callbackFlow {
        val clean = query.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        // Query by username prefix
        val queryRef = firestore.collection("users")
            .orderBy("username")
            .startAt(clean)
            .endAt(clean + "\uf8ff")
            .limit(limit)

        val registration = queryRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val users = snapshot?.documents?.mapNotNull { it.toObject(AnimiteUser::class.java) }.orEmpty()
            trySend(users)
        }
        awaitClose { registration.remove() }
    }
}
