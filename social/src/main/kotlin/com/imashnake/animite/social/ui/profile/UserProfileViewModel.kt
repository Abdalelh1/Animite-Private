package com.imashnake.animite.social.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.imashnake.animite.navigation.UserProfileRoute
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.CommentRepository
import com.imashnake.animite.social.data.FirestoreSyncRepository
import com.imashnake.animite.social.data.SocialRelationshipRepository
import com.imashnake.animite.social.data.UserProfileRepository
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuthState
import com.imashnake.animite.social.model.Comment
import com.imashnake.animite.social.model.FirestoreLibraryEntry
import com.imashnake.animite.social.util.AvatarValidator
import com.imashnake.animite.social.util.LiveCoverValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserAnimeStats(
    val totalEntries: Int = 0,
    val watchingCount: Int = 0,
    val completedCount: Int = 0,
    val planToWatchCount: Int = 0,
    val favoritesCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val relationshipRepository: SocialRelationshipRepository,
    private val commentRepository: CommentRepository,
    private val authRepository: AuthRepository,
    private val firestoreSyncRepository: FirestoreSyncRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val navRoute = runCatching { savedStateHandle.toRoute<UserProfileRoute>() }.getOrNull()
    val isViewingOwnProfile: Boolean get() = navRoute?.userId == null || (currentUid != null && currentUid == navRoute?.userId)
    val targetUserId: String get() = navRoute?.userId ?: authRepository.currentUid.orEmpty()

    val currentUid: String? get() = authRepository.currentUid
    val isCurrentUser: Boolean get() = currentUid != null && currentUid == targetUserId

    /** Reactive authentication state of the viewer. */
    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), AuthState.Loading)

    /** The authenticated current viewer's profile. Used to verify permissions (e.g. Owner/Admin). */
    val currentUserProfile: StateFlow<AnimiteUser?> = authRepository.authState.flatMapLatest { authState ->
        when (authState) {
            is AuthState.Authenticated -> userProfileRepository.getUserProfile(authState.user.uid)
            else -> flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    val userProfile: StateFlow<AnimiteUser?> = if (navRoute?.userId != null) {
        userProfileRepository.getUserProfile(navRoute.userId)
    } else {
        authRepository.authState.flatMapLatest { authState ->
            when (authState) {
                is AuthState.Authenticated -> userProfileRepository.getUserProfile(authState.user.uid)
                    .map { profile -> profile ?: authState.user }
                else -> flowOf(null)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    /** The profile's library entries in Firestore. */
    val libraryEntries: StateFlow<List<FirestoreLibraryEntry>> = userProfile.flatMapLatest { profile ->
        val uid = profile?.uid ?: targetUserId
        if (uid.isNotBlank()) {
            firestoreSyncRepository.getLibraryEntriesFlow(uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    /** Derived actual anime statistics. Never fabricated or hardcoded. */
    val animeStats: StateFlow<UserAnimeStats> = libraryEntries.map { entries ->
        UserAnimeStats(
            totalEntries = entries.size,
            watchingCount = entries.count { it.status.equals("WATCHING", ignoreCase = true) },
            completedCount = entries.count { it.status.equals("COMPLETED", ignoreCase = true) },
            planToWatchCount = entries.count { it.status.equals("PLAN_TO_WATCH", ignoreCase = true) },
            favoritesCount = entries.count { it.isFavorite },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), UserAnimeStats())

    /** The profile's "posts": comments authored by this user. Null while loading. */
    val userPosts: StateFlow<List<Comment>?> = userProfile.flatMapLatest { profile ->
        val uid = profile?.uid ?: targetUserId
        if (uid.isNotBlank()) {
            commentRepository.getCommentsByUser(uid)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    val isFollowing: StateFlow<Boolean> = userProfile.flatMapLatest { profile ->
        val target = profile?.uid ?: targetUserId
        val current = currentUid
        if (!current.isNullOrBlank() && target.isNotBlank() && current != target) {
            relationshipRepository.isFollowing(current, target)
        } else {
            flowOf(false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), false)

    val isBlocked: StateFlow<Boolean> = userProfile.flatMapLatest { profile ->
        val target = profile?.uid ?: targetUserId
        val current = currentUid
        if (!current.isNullOrBlank() && target.isNotBlank() && current != target) {
            relationshipRepository.isUserBlocked(current, target)
        } else {
            flowOf(false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), false)

    private val _uiState = MutableStateFlow<ProfileUiMessage?>(null)
    val uiState: StateFlow<ProfileUiMessage?> = _uiState.asStateFlow()
    val uiMessage: StateFlow<ProfileUiMessage?> get() = uiState

    // Avatar upload state
    private val _avatarUploadState = MutableStateFlow<AvatarUploadState>(AvatarUploadState.Idle)
    val avatarUploadState: StateFlow<AvatarUploadState> = _avatarUploadState.asStateFlow()

    private var avatarUploadJob: Job? = null
    private var lastSelectedImage: Pair<ByteArray, String>? = null

    fun onImageSelected(context: Context, uri: Uri) {
        when (val validation = AvatarValidator.validateAndRead(context, uri)) {
            is AvatarValidator.ValidationResult.Invalid -> {
                _avatarUploadState.value = AvatarUploadState.Error(validation.reason, canRetry = false)
                _uiState.value = ProfileUiMessage.Error(validation.reason)
            }
            is AvatarValidator.ValidationResult.Valid -> {
                lastSelectedImage = Pair(validation.bytes, validation.mimeType)
                executeAvatarUpload(validation.bytes, validation.mimeType)
            }
        }
    }

    fun onPhotoPickerCancelled() {
        if (_avatarUploadState.value !is AvatarUploadState.Loading) {
            _avatarUploadState.value = AvatarUploadState.Idle
        }
    }

    fun retryAvatarUpload() {
        val cached = lastSelectedImage
        if (cached != null) {
            executeAvatarUpload(cached.first, cached.second)
        } else {
            _uiState.value = ProfileUiMessage.Error("No image selected to retry. Please pick an image again.")
        }
    }

    fun cancelAvatarUpload() {
        avatarUploadJob?.cancel()
        avatarUploadJob = null
        _avatarUploadState.value = AvatarUploadState.Idle
        _uiState.value = ProfileUiMessage.Success("Avatar upload cancelled.")
    }

    private fun executeAvatarUpload(bytes: ByteArray, mimeType: String) {
        val current = currentUid ?: run {
            _avatarUploadState.value = AvatarUploadState.Error("You must be logged in to upload an avatar.", canRetry = false)
            _uiState.value = ProfileUiMessage.Error("You must be logged in to upload an avatar.")
            return
        }

        avatarUploadJob?.cancel()
        avatarUploadJob = viewModelScope.launch(Dispatchers.IO) {
            _avatarUploadState.value = AvatarUploadState.Loading
            userProfileRepository.uploadAvatar(current, bytes, mimeType)
                .onSuccess { secureUrl ->
                    _avatarUploadState.value = AvatarUploadState.Success(secureUrl)
                    _uiState.value = ProfileUiMessage.Success("Profile picture updated successfully!")
                    lastSelectedImage = null
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        _avatarUploadState.value = AvatarUploadState.Idle
                        return@onFailure
                    }
                    val errorMsg = error.localizedMessage ?: "Failed to upload avatar"
                    _avatarUploadState.value = AvatarUploadState.Error(errorMsg, canRetry = true)
                    _uiState.value = ProfileUiMessage.Error(errorMsg)
                }
        }
    }

    fun toggleFollow() {
        val currentUser = authRepository.currentUid ?: return
        val target = userProfile.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val isCurrentlyFollowing = isFollowing.value
            if (isCurrentlyFollowing) {
                relationshipRepository.unfollowUser(currentUser, target.uid)
                    .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to unfollow") }
            } else {
                val currentProfile = userProfileRepository.getUserProfileOnce(currentUser) ?: return@launch
                relationshipRepository.followUser(currentProfile, target)
                    .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to follow: ${it.localizedMessage}") }
            }
        }
    }

    fun blockUser() {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            relationshipRepository.blockUser(current, targetUserId)
                .onSuccess { _uiState.value = ProfileUiMessage.Success("User blocked") }
                .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to block user") }
        }
    }

    fun unblockUser() {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            relationshipRepository.unblockUser(current, targetUserId)
                .onSuccess { _uiState.value = ProfileUiMessage.Success("User unblocked") }
                .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to unblock user") }
        }
    }

    fun reportUser(reason: String) {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            relationshipRepository.report(
                reporterId = current,
                targetType = "user",
                targetId = targetUserId,
                reason = reason,
            ).onSuccess { _uiState.value = ProfileUiMessage.Success("Report submitted. Thank you for keeping Animite safe.") }
             .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to submit report") }
        }
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        location: String? = null,
        websiteUrl: String? = null,
        isPrivate: Boolean,
        allowMessagesFrom: String,
        username: String? = null,
    ) {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepository.updateUserProfile(
                uid = current,
                displayName = displayName,
                bio = bio,
                location = location,
                websiteUrl = websiteUrl,
                isPrivate = isPrivate,
                allowMessagesFrom = allowMessagesFrom,
                username = username,
            ).onSuccess { _uiState.value = ProfileUiMessage.Success("Profile updated") }
             .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to update profile: ${it.localizedMessage}") }
        }
    }

    fun updateCustomization(customization: com.imashnake.animite.social.model.ProfileCustomization) {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepository.updateCustomization(current, customization)
                .onSuccess { _uiState.value = ProfileUiMessage.Success("Theme customization saved") }
                .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to save customization: ${it.localizedMessage}") }
        }
    }

    fun uploadAvatar(bytes: ByteArray, mimeType: String = "image/jpeg") {
        lastSelectedImage = Pair(bytes, mimeType)
        executeAvatarUpload(bytes, mimeType)
    }

    /** Banner counterpart of [onImageSelected], used by the cover photo tap. */
    fun onBannerImageSelected(context: Context, uri: Uri) {
        when (val validation = AvatarValidator.validateAndRead(context, uri)) {
            is AvatarValidator.ValidationResult.Invalid -> {
                _uiState.value = ProfileUiMessage.Error(validation.reason)
            }
            is AvatarValidator.ValidationResult.Valid -> {
                uploadBanner(validation.bytes)
            }
        }
    }

    fun uploadBanner(bytes: ByteArray) {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepository.uploadBanner(current, bytes)
                .onSuccess { _uiState.value = ProfileUiMessage.Success("Banner updated") }
                .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to upload banner") }
        }
    }

    fun linkAnilist(anilistId: Int, anilistUsername: String) {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepository.linkAnilistAccount(current, anilistId, anilistUsername)
                .onSuccess { _uiState.value = ProfileUiMessage.Success("Linked to AniList (@$anilistUsername)") }
                .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to link AniList account") }
        }
    }

    fun unlinkAnilist() {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepository.unlinkAnilistAccount(current)
                .onSuccess { _uiState.value = ProfileUiMessage.Success("AniList account unlinked") }
                .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to unlink AniList account") }
        }
    }

    fun updateUserRole(
        targetUid: String,
        role: String,
        customBadgeTitle: String?,
        isVerified: Boolean,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepository.updateUserRole(
                targetUid = targetUid,
                role = role,
                customBadgeTitle = customBadgeTitle,
                isVerified = isVerified,
            ).onSuccess {
                _uiState.value = ProfileUiMessage.Success("تم تحديث رتبة المستخدم بنجاح")
            }.onFailure {
                _uiState.value = ProfileUiMessage.Error("فشل تحديث الرتبة: ${it.localizedMessage}")
            }
        }
    }

    fun clearUiMessage() {
        _uiState.value = null
    }

    // Live Cover upload state & operations
    private val _liveCoverUploadState = MutableStateFlow<LiveCoverUploadState>(LiveCoverUploadState.Idle)
    val liveCoverUploadState: StateFlow<LiveCoverUploadState> = _liveCoverUploadState.asStateFlow()

    private var liveCoverUploadJob: Job? = null
    private var lastSelectedLiveCover: Triple<ByteArray, String, ByteArray?>? = null

    fun onLiveCoverSelected(context: Context, uri: Uri) {
        val isPremium = currentUserProfile.value?.isPremium == true
        _liveCoverUploadState.value = LiveCoverUploadState.Validating
        viewModelScope.launch(Dispatchers.IO) {
            when (val validation = LiveCoverValidator.validateAndRead(context, uri, isPremium)) {
                is LiveCoverValidator.ValidationResult.Invalid -> {
                    _liveCoverUploadState.value = LiveCoverUploadState.Error(validation.reason, canRetry = false)
                    _uiState.value = ProfileUiMessage.Error(validation.reason)
                }
                is LiveCoverValidator.ValidationResult.Valid -> {
                    lastSelectedLiveCover = Triple(validation.bytes, validation.mimeType, validation.posterBytes)
                    executeLiveCoverUpload(validation.bytes, validation.mimeType, validation.posterBytes)
                }
            }
        }
    }

    fun onLiveCoverPickerCancelled() {
        if (_liveCoverUploadState.value !is LiveCoverUploadState.Uploading &&
            _liveCoverUploadState.value !is LiveCoverUploadState.Validating
        ) {
            _liveCoverUploadState.value = LiveCoverUploadState.Idle
        }
    }

    fun retryLiveCoverUpload() {
        val cached = lastSelectedLiveCover
        if (cached != null) {
            executeLiveCoverUpload(cached.first, cached.second, cached.third)
        } else {
            _uiState.value = ProfileUiMessage.Error("No media selected to retry. Please pick a video or GIF again.")
        }
    }

    fun cancelLiveCoverUpload() {
        liveCoverUploadJob?.cancel()
        liveCoverUploadJob = null
        _liveCoverUploadState.value = LiveCoverUploadState.Idle
        _uiState.value = ProfileUiMessage.Success("Live cover upload cancelled.")
    }

    private fun executeLiveCoverUpload(bytes: ByteArray, mimeType: String, posterBytes: ByteArray?) {
        val current = currentUid ?: run {
            _liveCoverUploadState.value = LiveCoverUploadState.Error("You must be logged in to upload a cover.", canRetry = false)
            _uiState.value = ProfileUiMessage.Error("You must be logged in to upload a cover.")
            return
        }

        liveCoverUploadJob?.cancel()
        liveCoverUploadJob = viewModelScope.launch(Dispatchers.IO) {
            _liveCoverUploadState.value = LiveCoverUploadState.Uploading
            userProfileRepository.uploadLiveCover(current, bytes, mimeType, posterBytes)
                .onSuccess { (mediaUrl, posterUrl) ->
                    _liveCoverUploadState.value = LiveCoverUploadState.Success(mediaUrl, posterUrl)
                    _uiState.value = ProfileUiMessage.Success("Live cover updated successfully!")
                    lastSelectedLiveCover = null
                }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) {
                        _liveCoverUploadState.value = LiveCoverUploadState.Idle
                        return@onFailure
                    }
                    val errorMsg = error.localizedMessage ?: "Failed to upload live cover"
                    _liveCoverUploadState.value = LiveCoverUploadState.Error(errorMsg, canRetry = true)
                    _uiState.value = ProfileUiMessage.Error(errorMsg)
                }
        }
    }

    fun removeLiveCover() {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepository.removeLiveCover(current)
                .onSuccess {
                    _liveCoverUploadState.value = LiveCoverUploadState.Idle
                    _uiState.value = ProfileUiMessage.Success("Live cover removed.")
                }
                .onFailure {
                    _uiState.value = ProfileUiMessage.Error("Failed to remove live cover: ${it.localizedMessage}")
                }
        }
    }

    fun signOut() {
        authRepository.signOut()
    }
}

sealed interface LiveCoverUploadState {
    data object Idle : LiveCoverUploadState
    data object Validating : LiveCoverUploadState
    data object Uploading : LiveCoverUploadState
    data class Success(val mediaUrl: String, val posterUrl: String?) : LiveCoverUploadState
    data class Error(val message: String, val canRetry: Boolean = true) : LiveCoverUploadState
}

sealed interface AvatarUploadState {
    data object Idle : AvatarUploadState
    data object Loading : AvatarUploadState
    data class Success(val url: String) : AvatarUploadState
    data class Error(val message: String, val canRetry: Boolean = true) : AvatarUploadState
}

sealed interface ProfileUiMessage {
    data class Success(val message: String) : ProfileUiMessage
    data class Error(val message: String) : ProfileUiMessage
}
