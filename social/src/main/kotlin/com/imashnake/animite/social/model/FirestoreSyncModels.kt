package com.imashnake.animite.social.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

/**
 * Lightweight, user-specific anime library entry stored in Cloud Firestore under:
 * `users/{userId}/library/{animeId}`.
 *
 * Stores only user-specific state (status, progress, rating, etc.)
 * and does not duplicate AniList's public anime catalog.
 */
@IgnoreExtraProperties
data class FirestoreLibraryEntry(
    @get:PropertyName("animeId") @set:PropertyName("animeId") var animeId: Int = 0,
    @get:PropertyName("status") @set:PropertyName("status") var status: String = "WATCHING",
    @get:PropertyName("currentEpisode") @set:PropertyName("currentEpisode") var currentEpisode: Int = 0,
    @get:PropertyName("score") @set:PropertyName("score") var score: Float? = null,
    @get:PropertyName("isFavorite") @set:PropertyName("isFavorite") var isFavorite: Boolean = false,
    @get:PropertyName("notes") @set:PropertyName("notes") var notes: String = "",
    @get:PropertyName("lastUpdated") @set:PropertyName("lastUpdated") var lastUpdated: Long = System.currentTimeMillis(),
)

/**
 * User-specific anime episode watch progress stored in Cloud Firestore under:
 * `users/{userId}/watch_progress/{animeId}`.
 */
@IgnoreExtraProperties
data class FirestoreWatchProgress(
    @get:PropertyName("animeId") @set:PropertyName("animeId") var animeId: Int = 0,
    @get:PropertyName("lastWatchedEpisode") @set:PropertyName("lastWatchedEpisode") var lastWatchedEpisode: Float = 0f,
    @get:PropertyName("episodesProgress") @set:PropertyName("episodesProgress") var episodesProgress: Map<String, Double> = emptyMap(),
    @get:PropertyName("lastPositionMs") @set:PropertyName("lastPositionMs") var lastPositionMs: Long = 0L,
    @get:PropertyName("durationMs") @set:PropertyName("durationMs") var durationMs: Long = 0L,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") var updatedAt: Long = System.currentTimeMillis(),
)
