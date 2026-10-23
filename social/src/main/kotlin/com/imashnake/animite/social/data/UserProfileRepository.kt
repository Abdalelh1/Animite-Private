package com.imashnake.animite.social.data

import com.google.firebase.firestore.FirebaseFirestore
import com.imashnake.animite.api.preferences.PreferencesRepository
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.ProfileCustomization
import com.imashnake.animite.social.model.ProfilePrivacy
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
) : IUserProfileRepository {

    override fun getUserProfile(uid: String): Flow<AnimiteUser?> = callbackFlow {
        if (uid.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
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

    override suspend fun getUserProfileOnce(uid: String): AnimiteUser? {
        if (uid.isBlank()) return null
        val snapshot = firestore.collection("users").document(uid).get().await()
        return snapshot.toObject(AnimiteUser::class.java)
    }

    override suspend fun updateUserProfile(
        uid: String,
        displayName: String,
        bio: String,
        location: String?,
        websiteUrl: String?,
        isPrivate: Boolean,
        allowMessagesFrom: String,
        username: String?,
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any?>(
            "displayName" to displayName.trim(),
            "bio" to bio.trim(),
            "isPrivate" to isPrivate,
            "allowMessagesFrom" to allowMessagesFrom,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (!username.isNullOrBlank()) updates["username"] = username.trim().removePrefix("@")
        if (location != null) updates["location"] = location.trim()
        if (websiteUrl != null) updates["websiteUrl"] = websiteUrl.trim()

        firestore.collection("users").document(uid).update(updates).await()
    }

    override suspend fun updateCustomization(
        uid: String,
        customization: ProfileCustomization,
    ): Result<Unit> = runCatching {
        val updates = mapOf<String, Any>(
            "customization" to customization,
            "updatedAt" to System.currentTimeMillis(),
        )
        firestore.collection("users").document(uid).update(updates).await()
    }

    override suspend fun updatePrivacy(
        uid: String,
        privacy: ProfilePrivacy,
    ): Result<Unit> = runCatching {
        val updates = mapOf<String, Any>(
            "privacy" to privacy,
            "isPrivate" to (privacy.profileVisibility == "PRIVATE"),
            "updatedAt" to System.currentTimeMillis(),
        )
        firestore.collection("users").document(uid).update(updates).await()
    }

    override suspend fun uploadAvatar(
        uid: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): Result<String> = runCatching {
        val downloadUrl = cloudinaryUploader.uploadImage(imageBytes, mimeType = mimeType)
            .getOrThrow()

        try {
            firestore.collection("users").document(uid).update(
                mapOf(
                    "photoUrl" to downloadUrl,
                    "updatedAt" to System.currentTimeMillis(),
                )
            ).await()
        } catch (e: Exception) {
            throw Exception("Failed to update profile in Firestore: ${e.localizedMessage}", e)
        }

        runCatching {
            preferencesRepository.setViewerAvatar(downloadUrl)
        }

        downloadUrl
    }

    override suspend fun uploadBanner(
        uid: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): Result<String> = runCatching {
        val downloadUrl = cloudinaryUploader.uploadImage(imageBytes, mimeType = mimeType)
            .getOrThrow()

        firestore.collection("users").document(uid).update(
            mapOf(
                "bannerUrl" to downloadUrl,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()

        downloadUrl
    }

    override suspend fun uploadLiveCover(
        uid: String,
        mediaBytes: ByteArray,
        mimeType: String,
        posterBytes: ByteArray?,
    ): Result<Pair<String, String?>> = runCatching {
        val mediaUrl = cloudinaryUploader.uploadMedia(mediaBytes, mimeType = mimeType).getOrThrow()

        val posterUrl: String? = if (posterBytes != null && posterBytes.isNotEmpty()) {
            cloudinaryUploader.uploadImage(posterBytes, mimeType = "image/jpeg").getOrNull()
                ?: cloudinaryUploader.derivePosterUrl(mediaUrl)
        } else {
            cloudinaryUploader.derivePosterUrl(mediaUrl)
        }

        val updates = mapOf(
            "liveCoverUrl" to mediaUrl,
            "liveCoverPosterUrl" to posterUrl,
            "liveCoverMediaType" to mimeType,
            "updatedAt" to System.currentTimeMillis(),
        )

        firestore.collection("users").document(uid).update(updates).await()

        Pair(mediaUrl, posterUrl)
    }

    override suspend fun removeLiveCover(uid: String): Result<Unit> = runCatching {
        val updates = mapOf<String, Any?>(
            "liveCoverUrl" to null,
            "liveCoverPosterUrl" to null,
            "liveCoverMediaType" to null,
            "updatedAt" to System.currentTimeMillis(),
        )
        firestore.collection("users").document(uid).update(updates).await()
    }

    override suspend fun linkAnilistAccount(
        uid: String,
        anilistUserId: Int,
        anilistUsername: String,
    ): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).update(
            mapOf(
                "anilistUserId" to anilistUserId,
                "anilistUsername" to anilistUsername,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    override suspend fun unlinkAnilistAccount(uid: String): Result<Unit> = runCatching {
        firestore.collection("users").document(uid).update(
            mapOf(
                "anilistUserId" to null,
                "anilistUsername" to null,
                "updatedAt" to System.currentTimeMillis(),
            )
        ).await()
    }

    override suspend fun updateUserRole(
        targetUid: String,
        role: String,
        customBadgeTitle: String?,
        isVerified: Boolean,
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any?>(
            "role" to role.trim().lowercase(Locale.ROOT),
            "isVerified" to isVerified,
            "updatedAt" to System.currentTimeMillis(),
        )
        if (customBadgeTitle != null) {
            updates["customBadgeTitle"] = customBadgeTitle.trim()
        }
        firestore.collection("users").document(targetUid).update(updates).await()
    }

    override fun searchUsers(query: String, limit: Long): Flow<List<AnimiteUser>> = callbackFlow {
        val clean = query.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

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
