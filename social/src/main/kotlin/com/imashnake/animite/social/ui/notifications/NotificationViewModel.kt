package com.imashnake.animite.social.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.FirestoreSyncRepository
import com.imashnake.animite.social.data.NotificationRepository
import com.imashnake.animite.social.model.AuthState
import com.imashnake.animite.social.model.SocialNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository,
    private val firestoreSyncRepository: FirestoreSyncRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), AuthState.Loading)

    val currentUid: String? get() = authRepository.currentUid

    val unreadCount: StateFlow<Int> = authRepository.authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            notificationRepository.getUnreadCount(state.user.uid)
        } else {
            flowOf(0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), 0)

    private val _selectedFilter = MutableStateFlow("all") // "all", "interactions", "episodes"
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val rawNotifications: StateFlow<List<SocialNotification>> = authRepository.authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            notificationRepository.getNotifications(state.user.uid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    val filteredNotifications: StateFlow<List<SocialNotification>> = combine(
        rawNotifications,
        _selectedFilter
    ) { list, filter ->
        when (filter) {
            "interactions" -> list.filter {
                it.type in listOf(
                    SocialNotification.TYPE_LIKE,
                    SocialNotification.TYPE_COMMENT,
                    SocialNotification.TYPE_REPLY,
                    SocialNotification.TYPE_FOLLOW
                )
            }
            "episodes" -> list.filter { it.type == SocialNotification.TYPE_EPISODE }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(1000), emptyList())

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun markAsRead(notificationId: String) {
        val uid = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            notificationRepository.markAsRead(uid, notificationId)
        }
    }

    fun markAllAsRead() {
        val uid = currentUid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            notificationRepository.markAllAsRead(uid)
        }
    }
}
