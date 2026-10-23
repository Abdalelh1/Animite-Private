package com.imashnake.animite.social.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.CommentRepository
import com.imashnake.animite.social.data.SocialRelationshipRepository
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuthState
import com.imashnake.animite.social.model.Comment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val commentRepository: CommentRepository,
    private val authRepository: AuthRepository,
    private val relationshipRepository: SocialRelationshipRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), AuthState.Loading)

    val currentUid: String? get() = authRepository.currentUid

    private val _targetId = MutableStateFlow("")
    val comments: StateFlow<List<Comment>> = _targetId.flatMapLatest { id ->
        if (id.isBlank()) flowOf(emptyList())
        else commentRepository.getComments(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    fun setTarget(targetId: String) {
        if (_targetId.value != targetId) {
            _targetId.value = targetId
        }
    }

    fun submitComment(
        targetType: String,
        targetId: String,
        animeId: Int,
        episodeNumber: String?,
        content: String,
        parentId: String? = null,
    ) {
        val user = (authState.value as? AuthState.Authenticated)?.user ?: return
        if (content.isBlank() || _isSubmitting.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSubmitting.value = true
            commentRepository.addComment(
                targetType = targetType,
                targetId = targetId,
                animeId = animeId,
                episodeNumber = episodeNumber,
                author = user,
                content = content,
                parentId = parentId
            )
            _isSubmitting.value = false
        }
    }

    fun toggleLike(commentId: String) {
        val uid = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            commentRepository.toggleLike(commentId, uid)
        }
    }

    fun deleteComment(commentId: String) {
        val currentUser = (authState.value as? AuthState.Authenticated)?.user
        val uid = currentUser?.uid ?: currentUid ?: return
        val isModerator = currentUser?.isModerator ?: false
        viewModelScope.launch(Dispatchers.IO) {
            commentRepository.deleteComment(commentId, uid, isModerator = isModerator)
        }
    }

    fun reportComment(commentId: String, reason: String) {
        val uid = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            relationshipRepository.report(
                reporterId = uid,
                targetType = "comment",
                targetId = commentId,
                reason = reason
            )
        }
    }
}
