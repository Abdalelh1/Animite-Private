package com.imashnake.animite.social.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.imashnake.animite.navigation.ChatRoute
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.MessagingRepository
import com.imashnake.animite.social.data.UserProfileRepository
import com.imashnake.animite.social.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messagingRepository: MessagingRepository,
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val chatRoute = savedStateHandle.toRoute<ChatRoute>()
    val currentUid: String get() = authRepository.currentUid.orEmpty()

    val messages: StateFlow<List<ChatMessage>> = messagingRepository.getMessages(chatRoute.conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    init {
        if (currentUid.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                messagingRepository.markConversationAsRead(chatRoute.conversationId, currentUid)
            }
        }
    }

    fun sendMessage(text: String) {
        val cleanText = text.trim()
        if (cleanText.isBlank() || _isSending.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isSending.value = true
            val currentUser = userProfileRepository.getUserProfileOnce(currentUid) ?: return@launch
            val recipientUser = userProfileRepository.getUserProfileOnce(chatRoute.recipientId) ?: return@launch

            messagingRepository.sendMessage(currentUser, recipientUser, cleanText)
            _isSending.value = false
        }
    }
}
