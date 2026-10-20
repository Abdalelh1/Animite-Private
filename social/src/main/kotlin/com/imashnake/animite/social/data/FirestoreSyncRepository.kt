package com.imashnake.animite.social.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.imashnake.animite.social.model.FirestoreLibraryEntry
import com.imashnake.animite.social.model.FirestoreWatchProgress
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles synchronizing user-specific anime library entries and episode watch progress
 * with Cloud Firestore under the user's secure subcollections:
 * - `users/{userId}/library/{animeId}`
 * - `users/{userId}/watch_progress/{animeId}`
 *
 * Firebase UID is strictly used as the stable owner identifier.
 * Firestore offline persistence caches these writes locally and syncs them automatically
 * once network connectivity is available.
 */
@Singleton
class FirestoreSyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    companion object {
        private const val TAG = "FirestoreSyncRepo"
        private const val USERS_COLLECTION = "users"
        private const val LIBRARY_COLLECTION = "library"
        private const val WATCH_PROGRESS_COLLECTION = "watch_progress"
    }

    private fun libraryCollection(uid: String) =
        firestore.collection(USERS_COLLECTION).document(uid).collection(LIBRARY_COLLECTION)

    private fun watchProgressCollection(uid: String) =
        firestore.collection(USERS_COLLECTION).document(uid).collection(WATCH_PROGRESS_COLLECTION)

    /**
     * Saves or updates a single user library entry in Firestore.
     */
    suspend fun saveLibraryEntry(uid: String, entry: FirestoreLibraryEntry): Result<Unit> = runCatching {
        require(uid.isNotBlank()) { "UID cannot be blank" }
        require(entry.animeId > 0) { "Anime ID must be greater than 0" }

        val docRef = libraryCollection(uid).document(entry.animeId.toString())
        docRef.set(entry, SetOptions.merge()).await()
        Unit
    }.onFailure { e ->
        Log.e(TAG, "Failed to save library entry for anime ${entry.animeId}: ${e.message}", e)
    }

    /**
     * Batch uploads multiple library entries (e.g., initial local-to-cloud migration).
     */
    suspend fun batchSaveLibraryEntries(uid: String, entries: List<FirestoreLibraryEntry>): Result<Unit> = runCatching {
        if (entries.isEmpty() || uid.isBlank()) return@runCatching

        // Firestore batches support up to 500 writes
        entries.chunked(450).forEach { chunk ->
            val batch = firestore.batch()
            for (entry in chunk) {
                if (entry.animeId <= 0) continue
                val docRef = libraryCollection(uid).document(entry.animeId.toString())
                batch.set(docRef, entry, SetOptions.merge())
            }
            batch.commit().await()
        }
        Unit
    }.onFailure { e ->
        Log.e(TAG, "Failed to batch save ${entries.size} library entries: ${e.message}", e)
    }

    /**
     * Deletes a library entry from Firestore.
     */
    suspend fun deleteLibraryEntry(uid: String, animeId: Int): Result<Unit> = runCatching {
        require(uid.isNotBlank()) { "UID cannot be blank" }
        require(animeId > 0) { "Anime ID must be greater than 0" }

        libraryCollection(uid).document(animeId.toString()).delete().await()
        Unit
    }.onFailure { e ->
        Log.e(TAG, "Failed to delete library entry for anime $animeId: ${e.message}", e)
    }

    /**
     * Returns a real-time Flow of the user's remote library entries.
     */
    fun getLibraryEntriesFlow(uid: String): Flow<List<FirestoreLibraryEntry>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = libraryCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error listening to library updates for $uid: ${error.message}")
                return@addSnapshotListener
            }
            val entries = snapshot?.documents?.mapNotNull { doc ->
                runCatching { doc.toObject(FirestoreLibraryEntry::class.java) }.getOrNull()
            }.orEmpty()
            trySend(entries)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Saves episode watch progress for a specific anime in Firestore.
     */
    suspend fun saveWatchProgress(uid: String, progress: FirestoreWatchProgress): Result<Unit> = runCatching {
        require(uid.isNotBlank()) { "UID cannot be blank" }
        require(progress.animeId > 0) { "Anime ID must be greater than 0" }

        val docRef = watchProgressCollection(uid).document(progress.animeId.toString())
        docRef.set(progress, SetOptions.merge()).await()
        Unit
    }.onFailure { e ->
        Log.e(TAG, "Failed to save watch progress for anime ${progress.animeId}: ${e.message}", e)
    }

    /**
     * Returns a real-time Flow of watch progress for a single anime.
     */
    fun getWatchProgressFlow(uid: String, animeId: Int): Flow<FirestoreWatchProgress?> = callbackFlow {
        if (uid.isBlank() || animeId <= 0) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = watchProgressCollection(uid).document(animeId.toString())
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error listening to watch progress for anime $animeId: ${error.message}")
                return@addSnapshotListener
            }
            val item = snapshot?.takeIf { it.exists() }?.let {
                runCatching { it.toObject(FirestoreWatchProgress::class.java) }.getOrNull()
            }
            trySend(item)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Returns a real-time Flow of all watch progress entries for the user.
     */
    fun getAllWatchProgressFlow(uid: String): Flow<List<FirestoreWatchProgress>> = callbackFlow {
        if (uid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = watchProgressCollection(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error listening to all watch progress for $uid: ${error.message}")
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { doc ->
                runCatching { doc.toObject(FirestoreWatchProgress::class.java) }.getOrNull()
            }.orEmpty()
            trySend(items)
        }

        awaitClose { listener.remove() }
    }
}
