package com.imashnake.animite.social.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class AnimiteUser(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val photoUrl: String? = null,
    val bannerUrl: String? = null,
    val isPrivate: Boolean = false,
    val allowMessagesFrom: String = "everyone", // "everyone", "followers", "none"
    val anilistUserId: Int? = null,
    val anilistUsername: String? = null,
    val followersCount: Int = 0,
    val followingCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val handle: String get() = if (username.isNotBlank()) "@$username" else ""
    val isAnilistLinked: Boolean get() = anilistUserId != null
}

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: AnimiteUser) : AuthState
}
