package com.imashnake.animite.social.ui.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imashnake.animite.social.data.AuthRepository
import com.imashnake.animite.social.data.IOwnerRepository
import com.imashnake.animite.social.data.OwnerDashboardStats
import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuditLog
import com.imashnake.animite.social.model.AuthState
import com.imashnake.animite.social.model.SystemSettings
import com.imashnake.animite.social.model.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface OwnerAuthState {
    data object Loading : OwnerAuthState
    data object Unauthorized : OwnerAuthState
    data class Authorized(val ownerUser: AnimiteUser) : OwnerAuthState
}

sealed interface OwnerUiMessage {
    data class Success(val message: String) : OwnerUiMessage
    data class Error(val message: String) : OwnerUiMessage
}

@HiltViewModel
class OwnerControlCenterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val ownerRepository: IOwnerRepository,
) : ViewModel() {

    private val _ownerAuthState = MutableStateFlow<OwnerAuthState>(OwnerAuthState.Loading)
    val ownerAuthState: StateFlow<OwnerAuthState> = _ownerAuthState.asStateFlow()

    private val _uiMessage = MutableStateFlow<OwnerUiMessage?>(null)
    val uiMessage: StateFlow<OwnerUiMessage?> = _uiMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchedUsers = MutableStateFlow<List<AnimiteUser>>(emptyList())
    val searchedUsers: StateFlow<List<AnimiteUser>> = _searchedUsers.asStateFlow()

    val stats: StateFlow<OwnerDashboardStats> = ownerRepository.getDashboardStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OwnerDashboardStats())

    val recentUsers: StateFlow<List<AnimiteUser>> = ownerRepository.getRecentUsers(25)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = ownerRepository.getAuditLogs(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val systemSettings: StateFlow<SystemSettings> = ownerRepository.getSystemSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SystemSettings.DEFAULT)

    init {
        checkOwnerAuthorization()
    }

    fun checkOwnerAuthorization() {
        viewModelScope.launch {
            authRepository.authState.collect { authState ->
                when (authState) {
                    is AuthState.Loading -> {
                        _ownerAuthState.value = OwnerAuthState.Loading
                    }
                    is AuthState.Unauthenticated -> {
                        _ownerAuthState.value = OwnerAuthState.Unauthorized
                    }
                    is AuthState.Authenticated -> {
                        if (authState.user.isOwner) {
                            _ownerAuthState.value = OwnerAuthState.Authorized(authState.user)
                        } else {
                            _ownerAuthState.value = OwnerAuthState.Unauthorized
                        }
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchedUsers.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            ownerRepository.searchUsers(query, 25).collect { users ->
                _searchedUsers.value = users
            }
        }
    }

    fun updateUserRole(targetUser: AnimiteUser, newRole: UserRole, customBadgeTitle: String? = null, reason: String = "") {
        val currentOwner = (ownerAuthState.value as? OwnerAuthState.Authorized)?.ownerUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            ownerRepository.updateUserRole(
                actorUser = currentOwner,
                targetUser = targetUser,
                newRole = newRole,
                customBadgeTitle = customBadgeTitle,
                reason = reason
            ).onSuccess {
                _uiMessage.value = OwnerUiMessage.Success("تم تحديث رتبة المستخدم إلى ${newRole.titleAr} بنجاح")
            }.onFailure { error ->
                _uiMessage.value = OwnerUiMessage.Error(error.localizedMessage ?: "فشل تحديث الرتبة")
            }
        }
    }

    fun toggleVerification(targetUser: AnimiteUser, isVerified: Boolean, reason: String = "") {
        val currentOwner = (ownerAuthState.value as? OwnerAuthState.Authorized)?.ownerUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            ownerRepository.toggleUserVerification(
                actorUser = currentOwner,
                targetUser = targetUser,
                isVerified = isVerified,
                reason = reason
            ).onSuccess {
                _uiMessage.value = OwnerUiMessage.Success(
                    if (isVerified) "تم منح شارة التوثيق بنجاح" else "تم إلغاء شارة التوثيق بنجاح"
                )
            }.onFailure { error ->
                _uiMessage.value = OwnerUiMessage.Error(error.localizedMessage ?: "فشل تعديل التوثيق")
            }
        }
    }

    fun updatePremium(targetUser: AnimiteUser, durationDays: Int?, reason: String = "") {
        val currentOwner = (ownerAuthState.value as? OwnerAuthState.Authorized)?.ownerUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            ownerRepository.updatePremiumMembership(
                actorUser = currentOwner,
                targetUser = targetUser,
                durationDays = durationDays,
                reason = reason
            ).onSuccess {
                _uiMessage.value = OwnerUiMessage.Success("تم تحديث عضوية النخبة للمستخدم بنجاح")
            }.onFailure { error ->
                _uiMessage.value = OwnerUiMessage.Error(error.localizedMessage ?: "فشل تحديث عضوية النخبة")
            }
        }
    }

    fun toggleUserSuspension(targetUser: AnimiteUser, isSuspended: Boolean, reason: String = "") {
        val currentOwner = (ownerAuthState.value as? OwnerAuthState.Authorized)?.ownerUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            ownerRepository.setUserSuspended(
                actorUser = currentOwner,
                targetUser = targetUser,
                isSuspended = isSuspended,
                reason = reason
            ).onSuccess {
                _uiMessage.value = OwnerUiMessage.Success(
                    if (isSuspended) "تم حظر الحساب بنجاح" else "تم إلغاء حظر الحساب بنجاح"
                )
            }.onFailure { error ->
                _uiMessage.value = OwnerUiMessage.Error(error.localizedMessage ?: "فشل تحديث حالة الحساب")
            }
        }
    }

    fun updateSystemSettings(newSettings: SystemSettings, reason: String = "") {
        val currentOwner = (ownerAuthState.value as? OwnerAuthState.Authorized)?.ownerUser ?: return
        viewModelScope.launch(Dispatchers.IO) {
            ownerRepository.updateSystemSettings(
                actorUser = currentOwner,
                newSettings = newSettings,
                reason = reason
            ).onSuccess {
                _uiMessage.value = OwnerUiMessage.Success("تم حفظ إعدادات النظام وتسجيلها في سجل العمليات")
            }.onFailure { error ->
                _uiMessage.value = OwnerUiMessage.Error(error.localizedMessage ?: "فشل حفظ إعدادات النظام")
            }
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }
}
