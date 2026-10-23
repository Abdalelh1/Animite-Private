package com.imashnake.animite.social.data

import com.imashnake.animite.social.model.AnimiteUser
import com.imashnake.animite.social.model.AuditLog
import com.imashnake.animite.social.model.SystemSettings
import com.imashnake.animite.social.model.UserRole
import kotlinx.coroutines.flow.Flow

/**
 * Metric summary for the Owner Control Center dashboard.
 */
data class OwnerDashboardStats(
    val totalUsers: Long = 0,
    val ownersCount: Long = 0,
    val moderatorsCount: Long = 0,
    val premiumCount: Long = 0,
    val verifiedCount: Long = 0,
    val membersCount: Long = 0,
    val suspendedCount: Long = 0,
    val totalComments: Long = 0,
    val totalReports: Long = 0,
)

/**
 * Contract for protected Owner administrative operations.
 */
interface IOwnerRepository {
    fun getDashboardStats(): Flow<OwnerDashboardStats>

    fun getRecentUsers(limit: Long = 25): Flow<List<AnimiteUser>>

    fun searchUsers(query: String, limit: Long = 25): Flow<List<AnimiteUser>>

    suspend fun updateUserRole(
        actorUser: AnimiteUser,
        targetUser: AnimiteUser,
        newRole: UserRole,
        customBadgeTitle: String? = null,
        reason: String = "",
    ): Result<Unit>

    suspend fun toggleUserVerification(
        actorUser: AnimiteUser,
        targetUser: AnimiteUser,
        isVerified: Boolean,
        reason: String = "",
    ): Result<Unit>

    suspend fun updatePremiumMembership(
        actorUser: AnimiteUser,
        targetUser: AnimiteUser,
        durationDays: Int?, // null = permanent, 0 = revoke, >0 = days
        reason: String = "",
    ): Result<Unit>

    suspend fun setUserSuspended(
        actorUser: AnimiteUser,
        targetUser: AnimiteUser,
        isSuspended: Boolean,
        reason: String = "",
    ): Result<Unit>

    fun getAuditLogs(limit: Long = 50): Flow<List<AuditLog>>

    fun getSystemSettings(): Flow<SystemSettings>

    suspend fun updateSystemSettings(
        actorUser: AnimiteUser,
        newSettings: SystemSettings,
        reason: String = "",
    ): Result<Unit>
}
