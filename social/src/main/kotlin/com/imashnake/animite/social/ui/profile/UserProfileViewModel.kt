package com.imashnake.animite.social.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.imashnake.animite.navigation.UserProfileRoute
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.SocialRelationshipRepository
import com.imashnake.animite.social.data.UserProfileRepository
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.util.AvatarValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val relationshipRepository: SocialRelationshipRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val navRoute = runCatching { savedStateHandle.toRoute<UserProfileRoute>() }.getOrNull()
    val targetUserId: String = navRoute?.userId ?: authRepository.currentUid.orEmpty()

    val currentUid: String? get() = authRepository.currentUid
    val isCurrentUser: Boolean get() = currentUid != null && currentUid == targetUserId

    val userProfile: StateFlow<AnimiteUser?> = userProfileRepository.getUserProfile(targetUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), null)

    val isFollowing: StateFlow<Boolean> = if (currentUid != null && !isCurrentUser) {
        relationshipRepository.isFollowing(currentUid!!, targetUserId)
    } else {
        flowOf(false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), false)

    val isBlocked: StateFlow<Boolean> = if (currentUid != null && !isCurrentUser) {
        relationshipRepository.isUserBlocked(currentUid!!, targetUserId)
    } else {
        flowOf(false)
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
        isPrivate: Boolean,
        allowMessagesFrom: String,
    ) {
        val current = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            userProfileRepository.updateUserProfile(
                uid = current,
                displayName = displayName,
                bio = bio,
                isPrivate = isPrivate,
                allowMessagesFrom = allowMessagesFrom,
            ).onSuccess { _uiState.value = ProfileUiMessage.Success("Profile updated") }
             .onFailure { _uiState.value = ProfileUiMessage.Error("Failed to update profile: ${it.localizedMessage}") }
        }
    }

    fun uploadAvatar(bytes: ByteArray, mimeType: String = "image/jpeg") {
        lastSelectedImage = Pair(bytes, mimeType)
        executeAvatarUpload(bytes, mimeType)
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

    fun clearUiMessage() {
        _uiState.value = null
    }
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
