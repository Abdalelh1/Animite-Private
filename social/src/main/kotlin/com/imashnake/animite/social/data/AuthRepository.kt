package com.imashnake.animite.social.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuthState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) {
    val currentUid: String? get() = auth.currentUser?.uid

    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.flatMapLatest { firebaseUser ->
        if (firebaseUser == null) {
            flowOf(AuthState.Unauthenticated)
        } else {
            callbackFlow<AuthState> {
                val docRef = firestore.collection("users").document(firebaseUser.uid)
                val registration = docRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(AuthState.Unauthenticated)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val user = snapshot.toObject(AnimiteUser::class.java)
                        if (user != null) {
                            trySend(AuthState.Authenticated(user))
                        } else {
                            trySend(AuthState.Unauthenticated)
                        }
                    } else {
                        // User authenticated in Firebase Auth but profile not yet written
                        val fallback = AnimiteUser(
                            uid = firebaseUser.uid,
                            displayName = firebaseUser.displayName.orEmpty(),
                            photoUrl = firebaseUser.photoUrl?.toString(),
                        )
                        trySend(AuthState.Authenticated(fallback))
                    }
                }
                awaitClose { registration.remove() }
            }
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean = runCatching {
        val clean = username.trim().lowercase(Locale.ROOT)
        if (clean.length < 3 || !clean.matches(Regex("^[a-z0-9_.]+$"))) return false
        val doc = firestore.collection("usernames").document(clean).get().await()
        !doc.exists()
    }.getOrDefault(true)

    suspend fun signUpWithEmail(
        email: String,
        pass: String,
        username: String,
        displayName: String,
    ): Result<AnimiteUser> = runCatching {
        val cleanUsername = username.trim().lowercase(Locale.ROOT)
        require(cleanUsername.length in 3..30) { "Username must be between 3 and 30 characters" }
        require(cleanUsername.matches(Regex("^[a-z0-9_.]+$"))) {
            "Username can only contain letters, numbers, underscores, and dots"
        }
        require(isUsernameAvailable(cleanUsername)) { "Username is already taken" }

        val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
        val firebaseUser = checkNotNull(authResult.user) { "Failed to create user account" }

        val avatarUrl = firebaseUser.photoUrl?.toString()
            ?: "https://api.dicebear.com/7.x/identicon/png?seed=$cleanUsername"

        val user = AnimiteUser(
            uid = firebaseUser.uid,
            username = cleanUsername,
            displayName = displayName.trim().ifBlank { cleanUsername },
            photoUrl = avatarUrl,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )

        val batch = firestore.batch()
        val userDoc = firestore.collection("users").document(firebaseUser.uid)
        val usernameDoc = firestore.collection("usernames").document(cleanUsername)
        batch.set(userDoc, user)
        batch.set(usernameDoc, mapOf("uid" to firebaseUser.uid, "createdAt" to System.currentTimeMillis()))
        batch.commit().await()

        user
    }

    suspend fun signInWithEmail(email: String, pass: String): Result<AnimiteUser> = runCatching {
        val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
        val firebaseUser = checkNotNull(authResult.user) { "Authentication failed" }
        val doc = firestore.collection("users").document(firebaseUser.uid).get().await()
        val user = doc.toObject(AnimiteUser::class.java)
        checkNotNull(user) { "User profile not found in database" }
    }

    suspend fun signInWithGoogle(idToken: String): Result<AnimiteUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(credential).await()
        val firebaseUser = checkNotNull(authResult.user) { "Google sign in failed" }

        val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
        if (userDoc.exists()) {
            val existing = userDoc.toObject(AnimiteUser::class.java)
            checkNotNull(existing) { "Failed to parse user profile" }
        } else {
            // First time Google sign in: generate a safe base username
            val baseName = (firebaseUser.displayName?.replace("\\s+".toRegex(), "")?.lowercase(Locale.ROOT)
                ?: "user").filter { it.isLetterOrDigit() }.take(15).ifBlank { "user" }
            var uniqueUsername = baseName
            var counter = 1
            while (!isUsernameAvailable(uniqueUsername)) {
                uniqueUsername = "$baseName$counter"
                counter++
            }

            val newUser = AnimiteUser(
                uid = firebaseUser.uid,
                username = uniqueUsername,
                displayName = firebaseUser.displayName.orEmpty().ifBlank { uniqueUsername },
                photoUrl = firebaseUser.photoUrl?.toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )

            val batch = firestore.batch()
            batch.set(firestore.collection("users").document(firebaseUser.uid), newUser)
            batch.set(
                firestore.collection("usernames").document(uniqueUsername),
                mapOf("uid" to firebaseUser.uid, "createdAt" to System.currentTimeMillis())
            )
            batch.commit().await()
            newUser
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() {
        auth.signOut()
    }
}
